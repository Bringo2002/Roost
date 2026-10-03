package com.roost.repository;

import com.roost.model.Property;
import com.roost.model.PropertyReport;
import com.roost.model.Role;
import com.roost.model.User;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the admin flagged-listings queue queries, run
 * against a real PostgreSQL (Testcontainers) per CLAUDE.md, same pattern
 * as PropertyRepositoryTest. PropertyReportTest (Mockito-based) already
 * covers PropertyService.getFlaggedForReview's own logic -- sorting,
 * defaulting a missing count to 0 -- against a mocked repository; this
 * class instead exercises the two real @Query methods behind it
 * (findPropertiesWithUnreviewedReports, countReportsByPropertyIds)
 * against actual JPQL and a real schema, which a mock can't catch a
 * typo or a bad JOIN in.
 *
 * spring.sql.init.mode=never: same reasoning as PropertyRepositoryTest.
 */
@DataJpaTest(properties = "spring.sql.init.mode=never")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class PropertyReportRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    private PropertyReportRepository propertyReportRepository;

    @Autowired
    private TestEntityManager em;

    private User owner;
    private User reporterA;
    private User reporterB;

    @BeforeEach
    void setUp() {
        owner = persistUser("owner");
        reporterA = persistUser("reporterA");
        reporterB = persistUser("reporterB");
    }

    private User persistUser(String prefix) {
        User u = new User();
        u.setName(prefix);
        u.setEmail(prefix + "+" + System.nanoTime() + "@example.com");
        u.setPassword("hashed-password");
        u.setRole(Role.LANDLORD);
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

    private void report(Property property, User reporter, LocalDateTime createdAt) {
        PropertyReport r = new PropertyReport();
        r.setProperty(property);
        r.setReportedBy(reporter);
        r.setReason("Fraudulent listing");
        r.setCreatedAt(createdAt);
        em.persist(r);
        em.flush();
        // PropertyReport#onCreate() is @PrePersist and unconditionally stamps
        // createdAt with the real clock, discarding the value set above --
        // correct for production (reports are always "now"), but it means
        // tests can't backdate a report through the entity alone. Overwrite
        // it directly in the DB afterward so findPropertiesWithUnreviewedReports
        // can be tested against reports from the past.
        em.getEntityManager()
                .createQuery("UPDATE PropertyReport pr SET pr.createdAt = :createdAt WHERE pr.id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", r.getId())
                .executeUpdate();
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("findPropertiesWithUnreviewedReports: includes never-reviewed and newly-reported, excludes reviewed-and-quiet")
    void findPropertiesWithUnreviewedReports_selectsCorrectSet() {
        LocalDateTime now = LocalDateTime.now();

        Property neverReviewed = listing("never-reviewed");
        report(neverReviewed, reporterA, now.minusDays(1));
        // reportsReviewedAt left null -- must show up.

        Property reviewedThenReReported = listing("reviewed-then-reported-again");
        reviewedThenReReported.setReportsReviewedAt(now.minusDays(5));
        report(reviewedThenReReported, reporterA, now.minusDays(1)); // after review -- must show up.

        Property reviewedAndQuiet = listing("reviewed-and-quiet");
        reviewedAndQuiet.setReportsReviewedAt(now);
        report(reviewedAndQuiet, reporterA, now.minusDays(10)); // before review -- must NOT show up.

        Property neverReported = listing("never-reported");
        // No PropertyReport row at all -- must NOT show up (the join finds nothing to match).

        flushAndClear();

        List<String> flagged = propertyReportRepository.findPropertiesWithUnreviewedReports()
                .stream().map(Property::getTitle).toList();

        assertEquals(2, flagged.size());
        assertTrue(flagged.contains("never-reviewed"));
        assertTrue(flagged.contains("reviewed-then-reported-again"));
        assertTrue(flagged.stream().noneMatch(t -> t.equals("reviewed-and-quiet") || t.equals("never-reported")));
    }

    @Test
    @DisplayName("findPropertiesWithUnreviewedReports: a listing reported by two people appears once, and fetch-joins owner")
    void findPropertiesWithUnreviewedReports_deduplicatesAndFetchJoinsOwner() {
        Property p = listing("double-reported");
        report(p, reporterA, LocalDateTime.now().minusHours(2));
        report(p, reporterB, LocalDateTime.now().minusHours(1));
        flushAndClear();

        em.clear();
        SessionFactory sf = em.getEntityManager().getEntityManagerFactory().unwrap(SessionFactory.class);
        Statistics stats = sf.getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        List<Property> flagged = propertyReportRepository.findPropertiesWithUnreviewedReports();
        // Touch the owner association; a working fetch-join means this
        // costs zero additional statements.
        for (Property property : flagged) {
            property.getOwner().getEmail();
        }
        long statements = stats.getPrepareStatementCount();

        assertEquals(1, flagged.size(), "the same listing reported twice must appear once, not twice");
        assertEquals("owner", flagged.get(0).getOwner().getName());
        // 1 for the fetch-joined page itself + 1 for the owner's EAGER
        // User.savedPropertyIds collection, which isn't fetch-joined here and
        // loads once per distinct owner -- same accounting as
        // PropertyRepositoryTest's owner-fetch-join assertions.
        assertEquals(2, statements,
                "expected 2 statements (owner fetch-joined + owner's saved ids), got " + statements);
    }

    @Test
    @DisplayName("countReportsByPropertyIds: totals every property in one query, in a single round trip")
    void countReportsByPropertyIds_batchesAcrossManyProperties() {
        Property single = listing("single-report");
        report(single, reporterA, LocalDateTime.now());

        Property triple = listing("triple-report");
        report(triple, reporterA, LocalDateTime.now().minusHours(1));
        report(triple, reporterB, LocalDateTime.now().minusHours(2));
        User reporterC = persistUser("reporterC");
        report(triple, reporterC, LocalDateTime.now().minusHours(3));

        Property zero = listing("zero-reports"); // no PropertyReport row.

        flushAndClear();

        em.clear();
        SessionFactory sf = em.getEntityManager().getEntityManagerFactory().unwrap(SessionFactory.class);
        Statistics stats = sf.getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        List<PropertyReportRepository.PropertyReportCount> counts =
                propertyReportRepository.countReportsByPropertyIds(
                        List.of(single.getId(), triple.getId(), zero.getId()));

        assertEquals(1, stats.getPrepareStatementCount(),
                "counting reports across 3 properties must be exactly 1 query, not one per property");

        Map<Long, Long> byId = counts.stream().collect(Collectors.toMap(
                PropertyReportRepository.PropertyReportCount::getPropertyId,
                PropertyReportRepository.PropertyReportCount::getReportCount));

        assertEquals(1L, byId.get(single.getId()));
        assertEquals(3L, byId.get(triple.getId()));
        // A property with zero reports gets no row at all -- the caller
        // (PropertyService.getFlaggedForReview) is responsible for
        // defaulting a missing id to 0, which PropertyReportTest covers.
        assertTrue(!byId.containsKey(zero.getId()));
    }
}
