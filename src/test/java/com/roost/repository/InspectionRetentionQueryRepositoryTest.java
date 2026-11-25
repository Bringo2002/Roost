package com.roost.repository;

import com.roost.model.MoveInInspection;
import com.roost.model.Property;
import com.roost.model.Role;
import com.roost.model.User;
import com.roost.util.EmailNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Which inspections are past their retention period: the clock runs from
 * when the inspection was completed, or from when it was created if it was
 * never completed. Run against a real PostgreSQL so the COALESCE is checked
 * where it is evaluated.
 */
@DataJpaTest(properties = "spring.sql.init.mode=never")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class InspectionRetentionQueryRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    private static final LocalDateTime CUTOFF = LocalDateTime.of(2026, 12, 1, 0, 0);

    @Autowired private MoveInInspectionRepository inspections;
    @Autowired private TestEntityManager em;

    private User tenant;
    private Property listing;

    @BeforeEach
    void setUp() {
        User landlord = persistUser("landlord", Role.LANDLORD);
        tenant = persistUser("tenant", Role.TENANT);
        Property p = new Property();
        p.setTitle("a listing");
        p.setDescription("desc");
        p.setLocation("Nairobi");
        p.setPrice(10000);
        p.setBedrooms(1);
        p.setType("RENTAL");
        p.setLandlordPhone("+254700000000");
        p.setStatus("PUBLISHED");
        p.setAvailable(true);
        p.setOwner(landlord);
        listing = em.persist(p);
    }

    private User persistUser(String prefix, Role role) {
        User u = new User();
        u.setName(prefix);
        u.setEmail(EmailNormalizer.normalize(prefix + "+" + System.nanoTime() + "@example.com"));
        u.setPassword("hashed-password");
        u.setRole(role);
        return em.persist(u);
    }

    private MoveInInspection inspection(LocalDateTime createdAt, LocalDateTime completedAt) {
        MoveInInspection i = new MoveInInspection();
        i.setProperty(listing);
        i.setTenant(tenant);
        i.setRoomsJson("[]");
        i.setCreatedAt(createdAt);
        i.setCompletedAt(completedAt);
        return em.persist(i);
    }

    @Test
    @DisplayName("an inspection completed before the cutoff is expired")
    void completedBeforeCutoffIsExpired() {
        MoveInInspection old = inspection(CUTOFF.minusMonths(30), CUTOFF.minusMonths(25));
        em.flush();

        assertEquals(List.of(old.getId()), inspections.findExpired(CUTOFF).stream().map(MoveInInspection::getId).toList());
    }

    @Test
    @DisplayName("an inspection completed after the cutoff is kept, even if it was created long before")
    void completionDateDecidesNotCreationDate() {
        inspection(CUTOFF.minusMonths(40), CUTOFF.plusDays(5));
        em.flush();

        assertEquals(0, inspections.findExpired(CUTOFF).size());
    }

    @Test
    @DisplayName("an inspection never completed expires from its creation date")
    void neverCompletedExpiresFromCreation() {
        MoveInInspection abandoned = inspection(CUTOFF.minusMonths(26), null);
        inspection(CUTOFF.plusDays(1), null);
        em.flush();

        assertEquals(List.of(abandoned.getId()), inspections.findExpired(CUTOFF).stream().map(MoveInInspection::getId).toList());
    }

    @Test
    @DisplayName("an inspection completed exactly at the cutoff is kept")
    void completedExactlyAtCutoffIsKept() {
        inspection(CUTOFF.minusMonths(30), CUTOFF);
        em.flush();

        assertEquals(0, inspections.findExpired(CUTOFF).size());
    }
}
