package com.roost.repository;

import com.roost.model.ChatVisibility;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the batched "which chats has this user deleted" query against a real
 * PostgreSQL, so the chat list can look up every hidden conversation in one
 * query instead of one query per partner.
 */
@DataJpaTest(properties = "spring.sql.init.mode=never")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ChatVisibilityRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    private ChatVisibilityRepository visibilities;

    @Autowired
    private TestEntityManager em;

    private User alice;
    private User bob;
    private User carol;

    @BeforeEach
    void setUp() {
        alice = persistUser("alice");
        bob = persistUser("bob");
        carol = persistUser("carol");
    }

    private User persistUser(String prefix) {
        User u = new User();
        u.setName(prefix);
        u.setEmail(EmailNormalizer.normalize(prefix + "+" + System.nanoTime() + "@example.com"));
        u.setPassword("hashed-password");
        u.setRole(Role.TENANT);
        return em.persist(u);
    }

    private void persistVisibility(User user, User partner, Long clearedBefore, Long hiddenSince) {
        ChatVisibility v = new ChatVisibility();
        v.setUser(user);
        v.setPartner(partner);
        v.setClearedBeforeMessageId(clearedBefore);
        v.setHiddenSinceMessageId(hiddenSince);
        em.persist(v);
    }

    @Test
    @DisplayName("findHiddenChats returns the partner and cutoff of every chat this user deleted")
    void returnsEveryDeletedChat() {
        persistVisibility(alice, bob, null, 5L);
        persistVisibility(alice, carol, null, 0L);
        em.flush();

        List<Object[]> rows = visibilities.findHiddenChats(alice);

        assertEquals(2, rows.size());
        assertTrue(rows.stream().anyMatch(r -> r[0].equals(bob.getId()) && r[1].equals(5L)));
        assertTrue(rows.stream().anyMatch(r -> r[0].equals(carol.getId()) && r[1].equals(0L)));
    }

    @Test
    @DisplayName("a chat that was only cleared, not deleted, is not reported as hidden")
    void clearedOnlyChatsAreNotHidden() {
        persistVisibility(alice, bob, 7L, null);
        em.flush();

        assertTrue(visibilities.findHiddenChats(alice).isEmpty());
    }

    @Test
    @DisplayName("the other person's deleted chat does not show up for this user")
    void onlyThisUsersRowsAreReturned() {
        persistVisibility(bob, alice, null, 9L);
        em.flush();

        assertTrue(visibilities.findHiddenChats(alice).isEmpty());
        assertEquals(1, visibilities.findHiddenChats(bob).size());
    }
}
