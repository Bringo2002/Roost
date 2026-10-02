package com.roost.repository;

import com.roost.model.ListingRankScore;
import com.roost.model.Property;
import com.roost.model.Role;
import com.roost.model.User;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link ListingRankScore} persistence against a
 * real PostgreSQL (Testcontainers), same pattern as
 * PropertyReportRepositoryTest. The shared-primary-key mapping and the
 * database-level cascade are the parts a mock cannot check.
 *
 * spring.sql.init.mode=never: same reasoning as PropertyRepositoryTest.
 */
@DataJpaTest(properties = "spring.sql.init.mode=never")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ListingRankScoreRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    private ListingRankScoreRepository listingRankScoreRepository;

    @Autowired
    private TestEntityManager em;

    private User owner;

    @BeforeEach
    void setUp() {
        User u = new User();
        u.setName("owner");
        u.setEmail("owner+" + System.nanoTime() + "@example.com");
        u.setPassword("hashed-password");
        u.setRole(Role.LANDLORD);
        owner = em.persist(u);
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

    @Test
    @DisplayName("a saved score is keyed by its property's id and round-trips")
    void save_usesPropertyIdAsKey() {
        Property p = listing("p");
        Instant at = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        listingRankScoreRepository.saveAndFlush(new ListingRankScore(p, 1.5, 0.25, at));
        em.clear();

        ListingRankScore loaded = listingRankScoreRepository.findById(p.getId()).orElseThrow();
        assertEquals(p.getId(), loaded.getPropertyId());
        assertEquals(1.5, loaded.getScore());
        assertEquals(0.25, loaded.getExposureBoost());
        assertEquals(at, loaded.getComputedAt());
    }

    @Test
    @DisplayName("update() overwrites the stored values for the next refresh")
    void update_overwritesValues() {
        Property p = listing("p");
        Instant first = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        Instant second = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        listingRankScoreRepository.saveAndFlush(new ListingRankScore(p, 1.0, 0.0, first));

        ListingRankScore row = listingRankScoreRepository.findById(p.getId()).orElseThrow();
        row.update(2.0, 0.5, second);
        listingRankScoreRepository.saveAndFlush(row);
        em.clear();

        ListingRankScore loaded = listingRankScoreRepository.findById(p.getId()).orElseThrow();
        assertEquals(2.0, loaded.getScore());
        assertEquals(0.5, loaded.getExposureBoost());
        assertEquals(second, loaded.getComputedAt());
    }

    @Test
    @DisplayName("deleting a property removes its score at the database level")
    void deletingProperty_cascadesToScore() {
        Property p = listing("doomed");
        listingRankScoreRepository.saveAndFlush(new ListingRankScore(p, 1.0, 0.0, Instant.now()));
        em.clear();

        em.getEntityManager().createQuery("DELETE FROM Property p WHERE p.id = :id")
                .setParameter("id", p.getId())
                .executeUpdate();
        em.clear();

        assertTrue(listingRankScoreRepository.findById(p.getId()).isEmpty());
    }

    @Test
    @DisplayName("rejects NaN/infinite scores and negative boosts before they can reach ORDER BY")
    void rejectsInvalidValues() {
        Property p = listing("p");
        Instant now = Instant.now();
        assertThrows(IllegalArgumentException.class,
                () -> new ListingRankScore(p, Double.NaN, 0.0, now));
        assertThrows(IllegalArgumentException.class,
                () -> new ListingRankScore(p, Double.POSITIVE_INFINITY, 0.0, now));
        assertThrows(IllegalArgumentException.class,
                () -> new ListingRankScore(p, 1.0, -0.1, now));
    }
}
