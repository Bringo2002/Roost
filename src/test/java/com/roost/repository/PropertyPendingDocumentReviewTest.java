package com.roost.repository;

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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Runs the pending-document-review query against a real PostgreSQL. The
 * three-valued flag (null, false, true) and the element-collection check are
 * exactly the parts a mock cannot prove.
 */
@DataJpaTest(properties = "spring.sql.init.mode=never")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class PropertyPendingDocumentReviewTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private TestEntityManager em;

    private User lister;

    @BeforeEach
    void setUp() {
        User u = new User();
        u.setName("lister");
        u.setEmail(EmailNormalizer.normalize("lister+" + System.nanoTime() + "@example.com"));
        u.setPassword("hashed-password");
        u.setRole(Role.LANDLORD);
        lister = em.persist(u);
    }

    private Property persist(List<String> documentUrls, Boolean documentVerified) {
        Property p = new Property();
        p.setTitle("Flat");
        p.setDescription("desc");
        p.setLocation("Nairobi");
        p.setPrice(10000);
        p.setBedrooms(1);
        p.setType("RENTAL");
        p.setLandlordPhone("+254700000000");
        p.setStatus("PUBLISHED");
        p.setAvailable(true);
        p.setOwner(lister);
        p.setDocumentUrls(new ArrayList<>(documentUrls));
        p.setDocumentVerified(documentVerified);
        return em.persist(p);
    }

    private List<Long> pendingIds() {
        em.flush();
        em.clear();
        return propertyRepository.findPendingDocumentReview().stream().map(Property::getId).toList();
    }

    @Test
    @DisplayName("a listing with documents and a null flag is pending")
    void nullFlagIsPending() {
        Property p = persist(List.of("https://docs.example.com/a.jpg"), null);

        assertEquals(List.of(p.getId()), pendingIds());
    }

    @Test
    @DisplayName("a listing with documents and a false flag is pending")
    void falseFlagIsPending() {
        Property p = persist(List.of("https://docs.example.com/a.jpg"), false);

        assertEquals(List.of(p.getId()), pendingIds());
    }

    @Test
    @DisplayName("an accepted listing is not pending")
    void acceptedIsNotPending() {
        persist(List.of("https://docs.example.com/a.jpg"), true);

        assertEquals(List.of(), pendingIds());
    }

    @Test
    @DisplayName("a listing with no documents is not pending")
    void noDocumentsIsNotPending() {
        persist(List.of(), false);

        assertEquals(List.of(), pendingIds());
    }

    @Test
    @DisplayName("a listing with several documents appears once, and the queue is oldest first")
    void appearsOnceAndOrderedById() {
        Property first = persist(List.of("https://docs.example.com/a.jpg", "https://docs.example.com/b.jpg"), false);
        Property second = persist(List.of("https://docs.example.com/c.jpg"), null);

        assertEquals(List.of(first.getId(), second.getId()), pendingIds());
    }
}
