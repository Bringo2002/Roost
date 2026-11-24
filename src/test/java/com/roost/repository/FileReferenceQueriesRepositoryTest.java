package com.roost.repository;

import com.roost.model.MediaHash;
import com.roost.model.Message;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The checks the nightly file deletion makes before it removes a file that
 * was queued when an account was erased: is anything else still using it?
 * Run against a real PostgreSQL so the collection-membership query is
 * checked where it is evaluated.
 */
@DataJpaTest(properties = "spring.sql.init.mode=never")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class FileReferenceQueriesRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired private PropertyRepository properties;
    @Autowired private UserRepository users;
    @Autowired private MessageRepository messages;
    @Autowired private MediaHashRepository mediaHashes;
    @Autowired private TestEntityManager em;

    private User landlord;

    @BeforeEach
    void setUp() {
        landlord = persistUser("landlord");
    }

    private User persistUser(String prefix) {
        User u = new User();
        u.setName(prefix);
        u.setEmail(EmailNormalizer.normalize(prefix + "+" + System.nanoTime() + "@example.com"));
        u.setPassword("hashed-password");
        u.setRole(Role.LANDLORD);
        return em.persist(u);
    }

    private Property listing() {
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
        return p;
    }

    @Test
    @DisplayName("isMediaUrlInUse finds a file used as a cover photo, gallery photo, document or video")
    void mediaUrlInUseFindsEveryKindOfReference() {
        Property p = listing();
        p.setImageUrl("https://files.example/cover.jpg");
        p.setImageUrls(new ArrayList<>(List.of("https://files.example/gallery.jpg")));
        p.setDocumentUrls(new ArrayList<>(List.of("https://files.example/deed.pdf")));
        p.setVideoUrl("https://files.example/tour.mp4");
        em.persist(p);
        em.flush();

        assertTrue(properties.isMediaUrlInUse("https://files.example/cover.jpg"));
        assertTrue(properties.isMediaUrlInUse("https://files.example/gallery.jpg"));
        assertTrue(properties.isMediaUrlInUse("https://files.example/deed.pdf"));
        assertTrue(properties.isMediaUrlInUse("https://files.example/tour.mp4"));
    }

    @Test
    @DisplayName("isMediaUrlInUse is false for a file no listing uses")
    void mediaUrlNotInUseWhenNoListingUsesIt() {
        Property p = listing();
        p.setImageUrl("https://files.example/cover.jpg");
        em.persist(p);
        em.flush();

        assertFalse(properties.isMediaUrlInUse("https://files.example/other.jpg"));
    }

    @Test
    @DisplayName("existsByAvatarUrl tells whether an account still uses the file")
    void avatarUrlInUse() {
        User withAvatar = persistUser("ada");
        withAvatar.setAvatarUrl("https://files.example/avatars/ada.jpg");
        em.flush();

        assertTrue(users.existsByAvatarUrl("https://files.example/avatars/ada.jpg"));
        assertFalse(users.existsByAvatarUrl("https://files.example/avatars/other.jpg"));
    }

    @Test
    @DisplayName("existsByAttachmentStorageKey tells whether a message still has the file")
    void attachmentKeyInUse() {
        Message m = new Message();
        m.setSender(landlord);
        m.setRecipient(persistUser("tenant"));
        m.setContent("see attached");
        m.setAttachmentStorageKey("chat/photo-1.jpg");
        em.persist(m);
        em.flush();

        assertTrue(messages.existsByAttachmentStorageKey("chat/photo-1.jpg"));
        assertFalse(messages.existsByAttachmentStorageKey("chat/photo-2.jpg"));
    }

    @Test
    @DisplayName("deleteByR2Key forgets only the upload stored under that key")
    void deleteByR2KeyRemovesOnlyThatUpload() {
        em.persist(new MediaHash("a".repeat(64), "https://files.example/x/one.jpg", "x/one.jpg", "image/jpeg", 10));
        em.persist(new MediaHash("b".repeat(64), "https://files.example/x/two.jpg", "x/two.jpg", "image/jpeg", 20));
        em.flush();

        int removed = mediaHashes.deleteByR2Key("x/one.jpg");

        assertEquals(1, removed);
        assertEquals(List.of("x/two.jpg"), mediaHashes.findAll().stream().map(MediaHash::getR2Key).toList());
    }
}
