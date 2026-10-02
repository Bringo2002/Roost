package com.roost.repository;

import com.roost.model.MoveInInspection;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration test for the move-in-inspection list queries, run against
 * a real PostgreSQL (Testcontainers) per CLAUDE.md, same pattern as
 * PropertyRepositoryTest / ReviewRepositoryTest.
 *
 * MoveInInspection.property and .tenant are both plain (EAGER)
 * @ManyToOne with no fetch-join, and MoveInInspectionResponseDto reads
 * both (propertyTitle, tenantName) for every row -- whichever side
 * isn't the query parameter varies per row, so both list queries were
 * N+1-prone on the other association. This guards the @EntityGraph
 * fetch-join fix on both findByPropertyOrderByCreatedAtDesc and
 * findByTenantOrderByCreatedAtDesc.
 *
 * spring.sql.init.mode=never: same reasoning as PropertyRepositoryTest.
 */
@DataJpaTest(properties = "spring.sql.init.mode=never")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class MoveInInspectionRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    private MoveInInspectionRepository moveInInspectionRepository;

    @Autowired
    private TestEntityManager em;

    private User landlord;

    @BeforeEach
    void setUp() {
        landlord = persistUser("landlord");
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
        p.setOwner(landlord);
        return em.persist(p);
    }

    private MoveInInspection inspection(Property property, User tenant, LocalDateTime createdAt) {
        MoveInInspection i = new MoveInInspection();
        i.setProperty(property);
        i.setTenant(tenant);
        i.setLandlordName("landlord");
        i.setRoomsJson("{}");
        i.setCreatedAt(createdAt);
        return em.persist(i);
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("findByPropertyOrderByCreatedAtDesc: touching property and tenant on every row costs nothing extra once fetch-joined")
    void findByPropertyOrderByCreatedAtDesc_fetchJoinCostsNothingExtra() {
        Property property = listing("the listing");
        LocalDateTime base = LocalDateTime.now().minusDays(1);
        // Distinct tenants inspecting the same property at different
        // times -- the dimension that varies per row for this query.
        for (int i = 0; i < 8; i++) {
            inspection(property, persistUser("tenant" + i), base.plusMinutes(i));
        }
        flushAndClear();

        SessionFactory sf = em.getEntityManager().getEntityManagerFactory().unwrap(SessionFactory.class);
        Statistics stats = sf.getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        Property managedProperty = em.find(Property.class, property.getId());
        List<MoveInInspection> inspections =
                moveInInspectionRepository.findByPropertyOrderByCreatedAtDesc(managedProperty);
        long afterQuery = stats.getPrepareStatementCount();

        for (MoveInInspection i : inspections) {
            i.getProperty().getTitle();
            i.getTenant().getName();
        }
        long afterTouching = stats.getPrepareStatementCount();

        assertEquals(8, inspections.size());
        assertEquals(afterQuery, afterTouching,
                "touching property/tenant added " + (afterTouching - afterQuery)
                        + " statement(s) -- they weren't actually fetch-joined");
    }

    @Test
    @DisplayName("findByTenantOrderByCreatedAtDesc: touching property and tenant on every row costs nothing extra once fetch-joined")
    void findByTenantOrderByCreatedAtDesc_fetchJoinCostsNothingExtra() {
        User tenant = persistUser("tenant");
        LocalDateTime base = LocalDateTime.now().minusDays(1);
        // Distinct properties the same tenant has inspected -- the
        // dimension that varies per row for this query.
        for (int i = 0; i < 8; i++) {
            inspection(listing("listing" + i), tenant, base.plusMinutes(i));
        }
        flushAndClear();

        SessionFactory sf = em.getEntityManager().getEntityManagerFactory().unwrap(SessionFactory.class);
        Statistics stats = sf.getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        User managedTenant = em.find(User.class, tenant.getId());
        List<MoveInInspection> inspections =
                moveInInspectionRepository.findByTenantOrderByCreatedAtDesc(managedTenant);
        long afterQuery = stats.getPrepareStatementCount();

        for (MoveInInspection i : inspections) {
            i.getProperty().getTitle();
            i.getTenant().getName();
        }
        long afterTouching = stats.getPrepareStatementCount();

        assertEquals(8, inspections.size());
        assertEquals(afterQuery, afterTouching,
                "touching property/tenant added " + (afterTouching - afterQuery)
                        + " statement(s) -- they weren't actually fetch-joined");
    }

    @Test
    @DisplayName("findByPropertyOrderByCreatedAtDesc: returns inspections newest-first with property and tenant attached")
    void findByPropertyOrderByCreatedAtDesc_returnsCorrectOrderAndAssociations() {
        Property property = listing("the listing");
        User tenantA = persistUser("tenantA");
        User tenantB = persistUser("tenantB");
        LocalDateTime now = LocalDateTime.now();

        MoveInInspection older = inspection(property, tenantA, now.minusDays(2));
        MoveInInspection newer = inspection(property, tenantB, now.minusDays(1));
        flushAndClear();

        Property managedProperty = em.find(Property.class, property.getId());
        List<MoveInInspection> inspections =
                moveInInspectionRepository.findByPropertyOrderByCreatedAtDesc(managedProperty);

        assertEquals(2, inspections.size());
        assertEquals(newer.getId(), inspections.get(0).getId(), "newest inspection must come first");
        assertEquals(older.getId(), inspections.get(1).getId());
        assertEquals("tenantB", inspections.get(0).getTenant().getName());
        assertEquals("the listing", inspections.get(0).getProperty().getTitle());
    }
}
