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
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
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

    // ─── Public listing queries: owner fetch-join + @BatchSize collections ───
    //
    // The queries below back the public feed, search/filter, nearby, and
    // saved-properties endpoints. Every one of them is mapped through
    // PropertyResponseDto, which reads both p.getOwner() (a plain @ManyToOne,
    // EAGER by default) and several @ElementCollection fields (imageUrls,
    // customAmenities, riskFlags, documentUrls). Before the N+1 fix, each of
    // those was a separate query per row; see Property.java (@BatchSize) and
    // PropertyRepository.java (fetch-joined/`@EntityGraph`'d owner).

    /** A published, available listing with an owner, two image URLs, and
     *  one custom amenity -- enough for a DTO conversion to touch both the
     *  fetch-joined association and a @BatchSize'd collection. */
    private Property publishedListingWithOwnerAndCollections(User o, String title) {
        Property p = listing(o, title, "PUBLISHED", true, false);
        p.setImageUrls(List.of(title + "-img1.jpg", title + "-img2.jpg"));
        p.setCustomAmenities(List.of("Backup generator"));
        return p;
    }

    /** Same as above, but also pinned near Nairobi CBD for the distance
     *  queries (findNearby, *SortedByDistance). */
    private Property publishedListingNearNairobi(User o, String title, double latOffset, double lngOffset) {
        Property p = publishedListingWithOwnerAndCollections(o, title);
        p.setLatitude(-1.2921 + latOffset);
        p.setLongitude(36.8219 + lngOffset);
        return p;
    }

    /** Runs [query], touching owner + every @ElementCollection PropertyResponseDto
     *  reads on each result, and returns how many SQL statements that cost. */
    private long statementsFor(java.util.function.Supplier<List<Property>> query) {
        em.clear();
        SessionFactory sf = em.getEntityManager().getEntityManagerFactory().unwrap(SessionFactory.class);
        Statistics stats = sf.getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        List<Property> results = query.get();
        for (Property p : results) {
            if (p.getOwner() != null) {
                p.getOwner().getEmail();
            }
            p.getImageUrls().size();
            p.getCustomAmenities().size();
            p.getRiskFlags().size();
            p.getDocumentUrls().size();
        }
        return stats.getPrepareStatementCount();
    }

    @Test
    @DisplayName("findByStatus (unpaginated feed): statement count does not grow with row count")
    void findByStatus_doesNotGrowWithRowCount() {
        for (int i = 0; i < 3; i++) publishedListingWithOwnerAndCollections(owner, "few-" + i);
        flushAndClear();
        long forThree = statementsFor(() -> propertyRepository.findByStatus("PUBLISHED"));

        for (int i = 0; i < 12; i++) publishedListingWithOwnerAndCollections(owner, "more-" + i);
        flushAndClear();
        long forFifteen = statementsFor(() -> propertyRepository.findByStatus("PUBLISHED"));

        assertTrue(forFifteen <= forThree + 1,
                "statement count grew with row count (3 rows: " + forThree
                        + ", 15 rows: " + forFifteen + ") -- an N+1 crept back in");
    }

    @Test
    @DisplayName("findByStatusPublishedPaged: owner is fetch-joined, no per-row owner query")
    void findByStatusPublishedPaged_fetchJoinsOwner() {
        for (int i = 0; i < 10; i++) publishedListingWithOwnerAndCollections(owner, "pub-" + i);
        flushAndClear();

        long statements = statementsFor(
                () -> propertyRepository.findByStatusPublishedPaged(PageRequest.of(0, 10)));

        // 1 for the page + up to a few for the @BatchSize'd collections
        // (each distinct collection type batches in one query regardless
        // of row count) -- nowhere near 10 rows x 5 fields if this regressed.
        assertTrue(statements <= 6,
                "expected a small, row-count-independent number of statements, got " + statements);
    }

    @Test
    @DisplayName("findByOwnerIdAndStatusPublishedPaged (host profile): only that owner's PUBLISHED listings, fetch-joins owner")
    void findByOwnerIdAndStatusPublishedPaged_scopesToOwnerAndPublishedOnly() {
        for (int i = 0; i < 10; i++) publishedListingWithOwnerAndCollections(owner, "pub-" + i);
        listing(owner, "draft", "DRAFT", true, false);
        listing(otherOwner, "not-mine", "PUBLISHED", true, true);
        flushAndClear();

        long statements = statementsFor(
                () -> propertyRepository.findByOwnerIdAndStatusPublishedPaged(owner.getId(), PageRequest.of(0, 10)));

        List<Property> results =
                propertyRepository.findByOwnerIdAndStatusPublishedPaged(owner.getId(), PageRequest.of(0, 10));
        assertEquals(10, results.size());
        assertTrue(results.stream().allMatch(p -> p.getOwner().getId().equals(owner.getId())));
        assertTrue(results.stream().noneMatch(p -> p.getTitle().equals("draft")),
                "a draft listing must never appear on another visitor's view of this owner's profile");
        assertTrue(results.stream().noneMatch(p -> p.getTitle().equals("not-mine")));
        // Same bound/reasoning as findByStatusPublishedPaged_fetchJoinsOwner:
        // owner is fetch-joined, so this stays small instead of growing
        // with row count.
        assertTrue(statements <= 6,
                "expected a small, row-count-independent number of statements, got " + statements);
    }

    @Test
    @DisplayName("filterProperties (paginated): returns only matching rows and does not N+1")
    void filterProperties_paginated_matchesAndDoesNotGrow() {
        for (int i = 0; i < 10; i++) {
            Property p = publishedListingWithOwnerAndCollections(owner, "flat-" + i);
            p.setHouseType("APARTMENT");
            p.setBedrooms(2);
        }
        Property studio = publishedListingWithOwnerAndCollections(owner, "studio");
        studio.setHouseType("APARTMENT");
        studio.setBedrooms(0);
        Property house = publishedListingWithOwnerAndCollections(owner, "house");
        house.setHouseType("HOUSE");
        house.setBedrooms(3);
        flushAndClear();

        long statements = statementsFor(() -> propertyRepository.filterProperties(
                "APARTMENT", null, null, 2, null, null, null, null, null, null,
                null, null, null, null, null, null,
                PageRequest.of(0, 20)));
        assertTrue(statements <= 6,
                "expected a small, row-count-independent number of statements, got " + statements);

        // houseType=APARTMENT, bedrooms>=2 -- matches the 10 flats, not the
        // 0-bedroom studio (wrong bedroom count) or the house (wrong type).
        List<Property> results = propertyRepository.filterProperties(
                "APARTMENT", null, null, 2, null, null, null, null, null, null,
                null, null, null, null, null, null,
                PageRequest.of(0, 20));
        assertEquals(10, results.size());
        assertTrue(results.stream().noneMatch(p -> p.getTitle().equals("studio")));
        assertTrue(results.stream().noneMatch(p -> p.getTitle().equals("house")));
    }

    @Test
    @DisplayName("findNearby: only returns listings within the radius, fetch-joins owner")
    void findNearby_returnsOnlyWithinRadiusAndDoesNotN1() {
        // ~1km north of CBD -- inside a 5km radius.
        publishedListingNearNairobi(owner, "close", 0.009, 0);
        // ~50km away -- outside a 5km radius.
        publishedListingNearNairobi(owner, "far", 0.45, 0);
        flushAndClear();

        double lat = -1.2921, lng = 36.8219, radiusKm = 5;
        double latDelta = radiusKm / 111.0;
        double lngDelta = radiusKm / (111.0 * Math.cos(Math.toRadians(lat)));

        long statements = statementsFor(() -> propertyRepository.findNearby(
                lat, lng, radiusKm,
                lat - latDelta, lat + latDelta,
                lng - lngDelta, lng + lngDelta));

        List<Property> results = propertyRepository.findNearby(
                lat, lng, radiusKm,
                lat - latDelta, lat + latDelta,
                lng - lngDelta, lng + lngDelta);

        assertEquals(List.of("close"), results.stream().map(Property::getTitle).toList());
        assertTrue(statements <= 6,
                "expected a small, row-count-independent number of statements, got " + statements);
    }

    @Test
    @DisplayName("findByOwnerOrderByIdDesc (my-listings): fetch-joins owner, newest first")
    void findByOwnerOrderByIdDesc_fetchJoinsOwnerNewestFirst() {
        for (int i = 0; i < 8; i++) publishedListingWithOwnerAndCollections(owner, "l-" + i);
        publishedListingWithOwnerAndCollections(otherOwner, "not-mine");
        flushAndClear();

        long statements = statementsFor(() -> propertyRepository.findByOwnerOrderByIdDesc(owner));

        List<Property> results = propertyRepository.findByOwnerOrderByIdDesc(owner);
        assertEquals(8, results.size());
        assertTrue(results.stream().noneMatch(p -> p.getTitle().equals("not-mine")));
        assertEquals("l-7", results.get(0).getTitle(), "expected newest (highest id) first");
        assertTrue(statements <= 6,
                "expected a small, row-count-independent number of statements, got " + statements);
    }

    @Test
    @DisplayName("findByIdIn (saved properties): fetch-joins owner, does not N+1 across many ids")
    void findByIdIn_fetchJoinsOwnerAndDoesNotN1() {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            ids.add(publishedListingWithOwnerAndCollections(owner, "saved-" + i).getId());
        }
        flushAndClear();

        long statements = statementsFor(() -> propertyRepository.findByIdIn(ids));

        assertEquals(12, propertyRepository.findByIdIn(ids).size());
        assertTrue(statements <= 6,
                "expected a small, row-count-independent number of statements, got " + statements);
    }

    @Test
    @DisplayName("findExistingIds: returns only ids that exist, ignoring unknown ones")
    void findExistingIds_returnsOnlyExistingIds() {
        Property a = publishedListingWithOwnerAndCollections(owner, "exists-a");
        Property b = publishedListingWithOwnerAndCollections(owner, "exists-b");
        flushAndClear();

        List<Long> found = propertyRepository.findExistingIds(List.of(a.getId(), b.getId(), -1L));

        assertEquals(Set.of(a.getId(), b.getId()), new HashSet<>(found));
    }

    @Test
    @DisplayName("findRankingInputs: only published, available listings, with the attributes the formula reads")
    void findRankingInputs_onlyVisibleListings() {
        Property visible = listing(owner, "visible", "PUBLISHED", true, true);
        visible.setPhotoApproved(true);
        visible.setGpsVerified(true);
        visible.setCommunityVerified(false);
        listing(owner, "draft", "DRAFT", true, true);
        listing(owner, "rented", "PUBLISHED", false, true);
        flushAndClear();

        List<PropertyRepository.RankingInput> inputs = propertyRepository.findRankingInputs();

        assertEquals(1, inputs.size());
        PropertyRepository.RankingInput in = inputs.get(0);
        assertEquals(visible.getId(), in.getId());
        assertTrue(in.getPhotoApproved());
        assertTrue(in.getVerified());
        assertTrue(in.getGpsVerified());
        assertFalse(in.getCommunityVerified());
        assertTrue(in.getListedAt() != null);
    }

    @Test
    @DisplayName("countRiskFlagsOfPublished: counts per visible listing, omits listings with none and hidden ones")
    void countRiskFlagsOfPublished_countsPerVisibleListing() {
        Property flagged = listing(owner, "flagged", "PUBLISHED", true, false);
        flagged.getRiskFlags().add("PRICE_ANOMALY");
        flagged.getRiskFlags().add("DUPLICATE_PHOTOS");
        listing(owner, "clean", "PUBLISHED", true, false);
        Property hidden = listing(owner, "hidden-flagged", "DRAFT", true, false);
        hidden.getRiskFlags().add("PRICE_ANOMALY");
        flushAndClear();

        List<PropertyRepository.RiskFlagCount> counts = propertyRepository.countRiskFlagsOfPublished();

        assertEquals(1, counts.size());
        assertEquals(flagged.getId(), counts.get(0).getPropertyId());
        assertEquals(2L, counts.get(0).getFlagCount());
    }
}
