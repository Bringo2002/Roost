package com.roost.repository;

import com.roost.model.Property;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the queries behind GET /api/v2/properties/my-listings,
 * run against a real PostgreSQL (Testcontainers) per CLAUDE.md -- JPQL with
 * CASE/ORDER BY/JOIN FETCH is exactly the kind of thing an H2 or mocked test
 * would pass while Postgres rejects it.
 *
 * spring.sql.init.mode=never: the app's data.sql seed script is not part of
 * what's under test and must not run against this throwaway database.
 */
@DataJpaTest(properties = "spring.sql.init.mode=never")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class PropertyRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private TestEntityManager em;

    private User owner;
    private User otherOwner;

    @BeforeEach
    void setUp() {
        owner = persistUser("owner");
        otherOwner = persistUser("other");
    }

    private User persistUser(String prefix) {
        User u = new User();
        u.setName(prefix);
        u.setEmail(prefix + "+" + System.nanoTime() + "@example.com");
        u.setPassword("hashed-password");
        u.setRole(Role.LANDLORD);
        return em.persist(u);
    }

    /** Persists a listing and returns it (id assigned). */
    private Property listing(User o, String title, String status, boolean available, boolean verified) {
        Property p = new Property();
        p.setTitle(title);
        p.setDescription("desc");
        p.setLocation("Nairobi");
        p.setPrice(10000);
        p.setBedrooms(1);
        p.setType("RENTAL");
        p.setLandlordPhone("+254700000000");
        p.setStatus(status);
        p.setAvailable(available);
        p.setVerified(verified);
        p.setOwner(o);
        return em.persist(p);
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private List<String> titles(Slice<Property> slice) {
        return slice.getContent().stream().map(Property::getTitle).toList();
    }

    /** published-available x2, published-rented x1, draft x2 (one older, one newer). */
    private void seedMixedListings() {
        listing(owner, "pub-1", "PUBLISHED", true, true);
        listing(owner, "draft-old", "DRAFT", true, false);
        listing(owner, "rented-1", "PUBLISHED", false, false);
        listing(owner, "pub-2", "PUBLISHED", true, false);
        listing(owner, "draft-new", "DRAFT", true, false);
        // Someone else's listing must never show up in this owner's results.
        listing(otherOwner, "not-mine", "PUBLISHED", true, true);
        flushAndClear();
    }

    @Test
    @DisplayName("ALL returns every one of the owner's listings, drafts first then newest first")
    void allFilterOrdersDraftsFirstThenNewestFirst() {
        seedMixedListings();

        Slice<Property> result =
                propertyRepository.findOwnerListingsPage(owner, "ALL", PageRequest.of(0, 20));

        assertEquals(
                List.of("draft-new", "draft-old", "pub-2", "rented-1", "pub-1"),
                titles(result));
        assertFalse(result.hasNext());
    }

    @Test
    @DisplayName("PUBLISHED returns only published listings that are still available")
    void publishedFilterExcludesRentedAndDrafts() {
        seedMixedListings();

        Slice<Property> result =
                propertyRepository.findOwnerListingsPage(owner, "PUBLISHED", PageRequest.of(0, 20));

        assertEquals(List.of("pub-2", "pub-1"), titles(result));
    }

    @Test
    @DisplayName("DRAFT returns only drafts")
    void draftFilterReturnsOnlyDrafts() {
        seedMixedListings();

        Slice<Property> result =
                propertyRepository.findOwnerListingsPage(owner, "DRAFT", PageRequest.of(0, 20));

        assertEquals(List.of("draft-new", "draft-old"), titles(result));
    }

    @Test
    @DisplayName("RENTED returns every unavailable listing")
    void rentedFilterReturnsUnavailableListings() {
        seedMixedListings();

        Slice<Property> result =
                propertyRepository.findOwnerListingsPage(owner, "RENTED", PageRequest.of(0, 20));

        assertEquals(List.of("rented-1"), titles(result));
    }

    @Test
    @DisplayName("an unrecognised filter matches nothing rather than leaking rows")
    void unknownFilterMatchesNothing() {
        seedMixedListings();

        Slice<Property> result =
                propertyRepository.findOwnerListingsPage(owner, "BOGUS", PageRequest.of(0, 20));

        assertTrue(result.getContent().isEmpty());
    }

    @Test
    @DisplayName("paging walks the whole list exactly once with no overlap, and hasNext is accurate")
    void pagingCoversEveryRowOnceAndReportsHasNext() {
        for (int i = 0; i < 7; i++) {
            listing(owner, "pub-" + i, "PUBLISHED", true, false);
        }
        flushAndClear();

        List<String> seen = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        int page = 0;
        Slice<Property> slice;
        do {
            slice = propertyRepository.findOwnerListingsPage(owner, "ALL", PageRequest.of(page, 3));
            assertTrue(slice.getContent().size() <= 3);
            for (String t : titles(slice)) {
                seen.add(t);
                unique.add(t);
            }
            // hasNext must be false exactly on the last page (3 + 3 + 1).
            assertEquals(page < 2, slice.hasNext(), "hasNext wrong on page " + page);
            page++;
        } while (slice.hasNext());

        assertEquals(7, seen.size());
        assertEquals(7, unique.size(), "a row appeared on more than one page");
        assertEquals(3, page);
    }

    @Test
    @DisplayName("counts cover ALL the owner's listings, match each filter's predicate, and ignore other owners")
    void countsMatchFilterPredicates() {
        seedMixedListings();

        PropertyRepository.OwnerListingCounts counts = propertyRepository.countOwnerListings(owner);

        assertEquals(5L, counts.getTotal());
        assertEquals(2L, counts.getDrafts());
        assertEquals(2L, counts.getAvailable());
        assertEquals(1L, counts.getRented());
        assertEquals(1L, counts.getVerified());

        // The badge on each chip must equal the number of rows that chip lists.
        assertEquals(counts.getAvailable().intValue(),
                propertyRepository.findOwnerListingsPage(owner, "PUBLISHED", PageRequest.of(0, 50)).getContent().size());
        assertEquals(counts.getDrafts().intValue(),
                propertyRepository.findOwnerListingsPage(owner, "DRAFT", PageRequest.of(0, 50)).getContent().size());
        assertEquals(counts.getRented().intValue(),
                propertyRepository.findOwnerListingsPage(owner, "RENTED", PageRequest.of(0, 50)).getContent().size());
    }

    @Test
    @DisplayName("an owner with no listings gets a zero/null-safe count row, not an exception")
    void countsForOwnerWithNoListings() {
        flushAndClear();

        PropertyRepository.OwnerListingCounts counts = propertyRepository.countOwnerListings(owner);

        assertEquals(0L, counts.getTotal());
        // SUM over zero rows is NULL in SQL; callers must treat null as 0.
        assertTrue(counts.getDrafts() == null || counts.getDrafts() == 0L);
    }

    /**
     * Runs one cold page fetch and returns how many SQL statements it cost,
     * touching everything the slim DTO reads (including the fetch-joined owner).
     */
    private long statementsForPageOfSize(int size) {
        em.clear();
        SessionFactory sf = em.getEntityManager().getEntityManagerFactory().unwrap(SessionFactory.class);
        Statistics stats = sf.getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        Slice<Property> slice = propertyRepository.findOwnerListingsPage(owner, "ALL", PageRequest.of(0, size));
        for (Property p : slice.getContent()) {
            p.getTitle();
            p.getOwner().getEmail();
        }
        return stats.getPrepareStatementCount();
    }

    @Test
    @DisplayName("statements per page are constant -- they do not grow with page size (no per-row lookups)")
    void statementCountDoesNotGrowWithPageSize() {
        for (int i = 0; i < 15; i++) {
            listing(owner, "pub-" + i, "PUBLISHED", true, false);
        }
        flushAndClear();

        long forThree = statementsForPageOfSize(3);
        long forFifteen = statementsForPageOfSize(15);

        // A per-row lookup (the N+1 this guards against) would make the
        // 15-row page cost ~12 more statements than the 3-row page.
        assertEquals(forThree, forFifteen,
                "statement count changed with page size (3 rows: " + forThree
                        + ", 15 rows: " + forFifteen + ") -- an N+1 crept in");

        // Expected: 1 for the page itself + 1 for the owner's EAGER
        // User.savedPropertyIds collection, which loads once per distinct
        // owner (i.e. once per page), not once per row. Anything above 2
        // means something new is being loaded.
        assertTrue(forFifteen <= 2,
                "expected at most 2 statements per page (page + owner's saved ids), got " + forFifteen);
    }
}
