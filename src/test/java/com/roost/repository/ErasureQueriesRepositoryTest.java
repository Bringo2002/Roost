package com.roost.repository;

import com.roost.model.ChatVisibility;
import com.roost.model.Message;
import com.roost.model.Role;
import com.roost.model.User;
import com.roost.model.VerificationDocument;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The queries account erasure relies on to find everything that belongs to
 * a user: their verification documents, their messages, and their chat
 * visibility rows. Run against a real PostgreSQL, so the OR conditions are
 * checked where they are evaluated.
 */
@DataJpaTest(properties = "spring.sql.init.mode=never")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ErasureQueriesRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    private VerificationDocumentRepository documents;

    @Autowired
    private MessageRepository messages;

    @Autowired
    private ChatVisibilityRepository chatVisibility;

    @Autowired
    private TestEntityManager em;

    private User ada;
    private User grace;
    private User linus;

    @BeforeEach
    void setUp() {
        ada = persistUser("ada");
        grace = persistUser("grace");
        linus = persistUser("linus");
    }

    private User persistUser(String prefix) {
        User u = new User();
        u.setName(prefix);
        u.setEmail(EmailNormalizer.normalize(prefix + "+" + System.nanoTime() + "@example.com"));
        u.setPassword("hashed-password");
        u.setRole(Role.LANDLORD);
        return em.persist(u);
    }

    private VerificationDocument document(User owner, LocalDateTime deletedAt) {
        VerificationDocument d = new VerificationDocument();
        d.setOwner(owner);
        d.setDocType(VerificationDocument.DocType.NATIONAL_ID_FRONT);
        d.setStorageKey("verification/" + UUID.randomUUID() + ".jpg");
        d.setContentType("image/jpeg");
        d.setSizeBytes(1234);
        d.setSha256("a".repeat(64));
        d.setDeletedAt(deletedAt);
        return em.persist(d);
    }

    private Message message(User from, User to) {
        Message m = new Message();
        m.setSender(from);
        m.setRecipient(to);
        m.setContent("hello");
        return em.persist(m);
    }

    private ChatVisibility visibility(User user, User partner) {
        ChatVisibility c = new ChatVisibility();
        c.setUser(user);
        c.setPartner(partner);
        return em.persist(c);
    }

    @Test
    @DisplayName("findByOwnerId returns all of the owner's documents, including ones marked deleted")
    void findByOwnerIdIncludesDeletedDocuments() {
        VerificationDocument live = document(ada, null);
        VerificationDocument deleted = document(ada, LocalDateTime.now().minusDays(1));
        document(grace, null);
        em.flush();

        List<VerificationDocument> found = documents.findByOwnerId(ada.getId());

        assertEquals(2, found.size());
        assertTrue(found.contains(live));
        assertTrue(found.contains(deleted));
    }

    @Test
    @DisplayName("findAllInvolving returns messages the user sent or received, and no others")
    void findAllInvolvingReturnsSentAndReceivedMessages() {
        Message sent = message(ada, grace);
        Message received = message(linus, ada);
        message(grace, linus);
        em.flush();

        List<Message> found = messages.findAllInvolving(ada);

        assertEquals(2, found.size());
        assertTrue(found.contains(sent));
        assertTrue(found.contains(received));
    }

    @Test
    @DisplayName("deleteAllInvolving removes the user's visibility rows on either side and keeps the rest")
    void deleteAllInvolvingRemovesRowsOnEitherSide() {
        visibility(ada, grace);
        visibility(grace, ada);
        ChatVisibility unrelated = visibility(grace, linus);
        em.flush();

        int removed = chatVisibility.deleteAllInvolving(ada);

        assertEquals(2, removed);
        assertEquals(List.of(unrelated.getId()), chatVisibility.findAll().stream().map(ChatVisibility::getId).toList());
    }
}
