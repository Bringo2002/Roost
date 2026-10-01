package com.roost.repository;

import com.roost.model.Property;
import com.roost.model.Review;
import com.roost.model.Role;
import com.roost.model.User;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration test for the public property-reviews query, run against a
 * real PostgreSQL (Testcontainers) per CLAUDE.md, same pattern as
 * PropertyRepositoryTest / PropertyReportRepositoryTest / MessageRepositoryTest.
 *
 * Review.reviewer is a plain (EAGER) @ManyToOne with no fetch-join, and
 * ReviewResponseDto resolves it for every row via getPropertyReviews --
 * public and unauthenticated, so the most exposed of this class of bug.
 * This guards the @EntityGraph fetch-join fix on
 * ReviewRepository.findByPropertyOrderByCreatedAtDesc.
 *
 * spring.sql.init.mode=never: same reasoning as PropertyRepositoryTest.
 */
@DataJpaTest(properties = "spring.sql.init.mode=never")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ReviewRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private TestEntityManager em;

    private User owner;
    private Property property;

    @BeforeEach
    void setUp() {
        owner = persistUser("owner");
        property = listing("the listing");
    }

    private User persistUser(String prefix) {
        User u = new User();
        u.setName(prefix);
        u.setEmail(prefix + "+" + System.nanoTime() + "@example.com");
        u.setPassword("hashed-password");
        u.setRole(Role.TENANT);
        return em.persist(u);
    }

    private Property listing(String title) {
        Property p = new Property();
        p.setTitle(title);
        p.setDescription("desc");
        p.setLocation("Nairobi");
        p.setPrice(10000);
        p.setBedrooms(1);
        p.setType("RENTAL");
        p.setLandlordPhone("+254700000000");
        p.setStatus("PUBLISHED");
        p.setAvailable(true);
        p.setOwner(owner);
        return em.persist(p);
    }

    /** A review from a distinct reviewer, backdated to [createdAt].
     *  Review#onCreate() is @PrePersist and unconditionally stamps
     *  createdAt with the real clock (same as PropertyReport), so this
     *  persists then bulk-UPDATEs the timestamp directly in the DB. */
    private Review review(Property property, User reviewer, int rating, LocalDateTime createdAt) {
        Review r = new Review();
        r.setProperty(property);
        r.setReviewer(reviewer);
        r.setRating(rating);
        r.setComment("comment");
        em.persist(r);
        em.flush();
        em.getEntityManager()
                .createQuery("UPDATE Review rv SET rv.createdAt = :createdAt WHERE rv.id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", r.getId())
                .executeUpdate();
        return r;
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("findByPropertyOrderByCreatedAtDesc: touching reviewer on every row costs nothing extra once fetch-joined")
    void findByPropertyOrderByCreatedAtDesc_reviewerFetchJoinCostsNothingExtra() {
        // 10 distinct reviewers -- the property_id/reviewer_id unique
        // constraint means one review per reviewer, so there's no way
        // to grow row count without also growing distinct-user count.
        // Note: User.savedPropertyIds is a separate EAGER collection
        // with no @BatchSize of its own (same gap already documented in
        // PropertyReportRepositoryTest), so it genuinely costs one
        // query per distinct reviewer regardless of this fix -- that's
        // why this test checks the MARGINAL cost of touching reviewer,
        // not the total statement count, which would be misleading here.
        LocalDateTime base = LocalDateTime.now().minusDays(1);
        for (int i = 0; i < 10; i++) {
            User reviewer = persistUser("reviewer" + i);
            review(property, reviewer, 5, base.plusMinutes(i));
        }
        flushAndClear();

        SessionFactory sf = em.getEntityManager().getEntityManagerFactory().unwrap(SessionFactory.class);
        Statistics stats = sf.getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        // Re-fetch property -- it's detached after flushAndClear(), and
        // findByPropertyOrderByCreatedAtDesc needs a managed/usable
        // reference to bind as the query parameter.
        Property managedProperty = em.find(Property.class, property.getId());
        List<Review> reviews = reviewRepository.findByPropertyOrderByCreatedAtDesc(managedProperty);
        long afterQuery = stats.getPrepareStatementCount();

        // Touch the reviewer association on every row. A working
        // fetch-join means reviewer is already fully loaded, so this
        // costs zero additional statements -- the N+1 this guards
        // against would instead add one SELECT per review here.
        for (Review r : reviews) {
            r.getReviewer().getName();
        }
        long afterTouchingReviewer = stats.getPrepareStatementCount();

        assertEquals(10, reviews.size());
        assertEquals(afterQuery, afterTouchingReviewer,
                "touching reviewer added " + (afterTouchingReviewer - afterQuery)
                        + " statement(s) -- reviewer wasn't actually fetch-joined");
    }

    @Test
    @DisplayName("findByPropertyOrderByCreatedAtDesc: returns reviews newest-first with reviewer attached")
    void findByPropertyOrderByCreatedAtDesc_returnsCorrectOrderAndReviewer() {
        User reviewerA = persistUser("reviewerA");
        User reviewerB = persistUser("reviewerB");
        LocalDateTime now = LocalDateTime.now();

        Review older = review(property, reviewerA, 3, now.minusDays(2));
        Review newer = review(property, reviewerB, 5, now.minusDays(1));
        flushAndClear();

        Property managedProperty = em.find(Property.class, property.getId());
        List<Review> reviews = reviewRepository.findByPropertyOrderByCreatedAtDesc(managedProperty);

        assertEquals(2, reviews.size());
        assertEquals(newer.getId(), reviews.get(0).getId(), "newest review must come first");
        assertEquals(older.getId(), reviews.get(1).getId());
        assertEquals("reviewerB", reviews.get(0).getReviewer().getName());
        assertEquals(5, reviews.get(0).getRating());
    }
}
