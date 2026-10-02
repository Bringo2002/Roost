package com.roost.repository;

import com.roost.model.ListingEvent;
import com.roost.model.ListingEventType;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link ListingEvent} persistence and the grouped
 * aggregate the ranking job reads, against a real PostgreSQL
 * (Testcontainers) -- same pattern as PropertyReportRepositoryTest.
 *
 * spring.sql.init.mode=never: same reasoning as PropertyRepositoryTest.
 */
@DataJpaTest(properties = "spring.sql.init.mode=never")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ListingEventRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    private ListingEventRepository listingEventRepository;

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

    private void event(Property property, ListingEventType type, Instant at) {
        em.persist(new ListingEvent(property, null, "device-1", type, at));
    }

    @Test
    @DisplayName("countByPropertyAndTypeSince: groups per listing and type, ignoring events before the window")
    void countByPropertyAndTypeSince_groupsWithinWindow() {
        Instant now = Instant.now();
        Instant since = now.minus(7, ChronoUnit.DAYS);
        Property a = listing("a");
        Property b = listing("b");

        event(a, ListingEventType.IMPRESSION, now.minus(1, ChronoUnit.DAYS));
        event(a, ListingEventType.IMPRESSION, now.minus(2, ChronoUnit.DAYS));
        event(a, ListingEventType.CLICK, now.minus(2, ChronoUnit.DAYS));
        event(b, ListingEventType.IMPRESSION, now.minus(3, ChronoUnit.DAYS));
        event(a, ListingEventType.IMPRESSION, now.minus(30, ChronoUnit.DAYS)); // outside window
        em.flush();
        em.clear();

        Map<String, Long> counts = listingEventRepository.countByPropertyAndTypeSince(since).stream()
                .collect(Collectors.toMap(
                        c -> c.getPropertyId() + ":" + c.getEventType(),
                        ListingEventRepository.EventCount::getEventCount));

        assertEquals(3, counts.size());
        assertEquals(2L, counts.get(a.getId() + ":IMPRESSION"));
        assertEquals(1L, counts.get(a.getId() + ":CLICK"));
        assertEquals(1L, counts.get(b.getId() + ":IMPRESSION"));
    }

    @Test
    @DisplayName("countByPropertyAndTypeSince: empty window yields an empty list")
    void countByPropertyAndTypeSince_emptyWindow() {
        listing("quiet");
        em.flush();
        em.clear();

        List<ListingEventRepository.EventCount> counts =
                listingEventRepository.countByPropertyAndTypeSince(Instant.now());

        assertTrue(counts.isEmpty());
    }

    @Test
    @DisplayName("deleting a property removes its events at the database level")
    void deletingProperty_cascadesToEvents() {
        Property p = listing("doomed");
        event(p, ListingEventType.CLICK, Instant.now());
        em.flush();
        em.clear();

        em.getEntityManager().createQuery("DELETE FROM Property p WHERE p.id = :id")
                .setParameter("id", p.getId())
                .executeUpdate();
        em.clear();

        assertEquals(0, listingEventRepository.count());
    }

    @Test
    @DisplayName("constructor rejects an event with no actor")
    void constructor_requiresAnActor() {
        Property p = listing("p");
        assertThrows(IllegalArgumentException.class,
                () -> new ListingEvent(p, null, "  ", ListingEventType.CLICK, Instant.now()));
    }

    @Test
    @DisplayName("constructor rejects an over-long anonymous id instead of failing at the database")
    void constructor_rejectsOverlongAnonymousId() {
        Property p = listing("p");
        String tooLong = "x".repeat(ListingEvent.ANONYMOUS_ID_MAX_LENGTH + 1);
        assertThrows(IllegalArgumentException.class,
                () -> new ListingEvent(p, null, tooLong, ListingEventType.CLICK, Instant.now()));
    }
}
