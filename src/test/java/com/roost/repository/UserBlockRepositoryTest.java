package com.roost.repository;

import com.roost.model.Message;
import com.roost.model.Role;
import com.roost.model.User;
import com.roost.model.UserBlock;
import com.roost.util.EmailNormalizer;
import jakarta.persistence.PersistenceException;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the V9 {@code user_blocks} schema and the block queries against a
 * real PostgreSQL, so the rules chat relies on (one block per pair, either
 * direction counts, unread counts skip blocked senders) are tested where the
 * database enforces them.
 */
@DataJpaTest(properties = "spring.sql.init.mode=never")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class UserBlockRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    private UserBlockRepository blocks;

    @Autowired
    private MessageRepository messages;

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

    private void unreadMessage(User from, User to) {
        Message m = new Message();
        m.setSender(from);
        m.setRecipient(to);
        m.setContent("hi");
        m.setTimestamp(LocalDateTime.now());
        em.persist(m);
    }

    @Test
    @DisplayName("the same person cannot be blocked twice by the same user")
    void samePairCannotBeBlockedTwice() {
        em.persistAndFlush(new UserBlock(alice, bob));

        assertThrows(PersistenceException.class,
                () -> em.persistAndFlush(new UserBlock(alice, bob)));
    }

    @Test
    @DisplayName("two people can block each other; those are two separate rows")
    void blockingBackIsADifferentRow() {
        em.persistAndFlush(new UserBlock(alice, bob));
        em.persistAndFlush(new UserBlock(bob, alice));

        assertEquals(2, blocks.count());
    }

    @Test
    @DisplayName("findBlockedIdsByBlocker lists who this user blocked, not who blocked them")
    void blockedIdsAreOnlyTheOnesThisUserBlocked() {
        em.persist(new UserBlock(alice, bob));
        em.persist(new UserBlock(carol, alice));
        em.flush();

        assertEquals(List.of(bob.getId()), blocks.findBlockedIdsByBlocker(alice));
        assertEquals(List.of(alice.getId()), blocks.findBlockedIdsByBlocker(carol));
        assertTrue(blocks.findBlockedIdsByBlocker(bob).isEmpty());
    }

    @Test
    @DisplayName("existsBetween is true when either person blocked the other, false for strangers")
    void existsBetweenCountsEitherDirection() {
        em.persistAndFlush(new UserBlock(alice, bob));

        assertTrue(blocks.existsBetween(alice, bob));
        assertTrue(blocks.existsBetween(bob, alice));
        assertFalse(blocks.existsBetween(alice, carol));
        assertFalse(blocks.existsBetween(carol, bob));
    }

    @Test
    @DisplayName("existsByBlockerAndBlocked is directional")
    void existsByBlockerAndBlockedIsDirectional() {
        em.persistAndFlush(new UserBlock(alice, bob));

        assertTrue(blocks.existsByBlockerAndBlocked(alice, bob));
        assertFalse(blocks.existsByBlockerAndBlocked(bob, alice));
    }

    @Test
    @DisplayName("findWithBlockedByBlocker returns the blocked users, newest block first")
    void blockListIsNewestFirstWithUsersLoaded() {
        UserBlock older = new UserBlock(alice, bob);
        older.setCreatedAt(LocalDateTime.now().minusDays(2));
        UserBlock newer = new UserBlock(alice, carol);
        newer.setCreatedAt(LocalDateTime.now().minusDays(1));
        em.persist(older);
        em.persist(newer);
        em.flush();
        em.clear();

        List<UserBlock> list = blocks.findWithBlockedByBlocker(alice);

        assertEquals(List.of("carol", "bob"),
                list.stream().map(b -> b.getBlocked().getName()).toList());
    }

    @Test
    @DisplayName("deleteByPair removes only that block and reports how many rows went")
    void deleteByPairRemovesOnlyThatBlock() {
        em.persist(new UserBlock(alice, bob));
        em.persist(new UserBlock(alice, carol));
        em.persist(new UserBlock(bob, alice));
        em.flush();

        assertEquals(1, blocks.deleteByPair(alice, bob));
        assertEquals(0, blocks.deleteByPair(alice, bob));

        assertEquals(List.of(carol.getId()), blocks.findBlockedIdsByBlocker(alice));
        assertTrue(blocks.existsByBlockerAndBlocked(bob, alice));
    }

    @Test
    @DisplayName("countUnreadNotBlocked skips unread messages from people the user blocked")
    void unreadCountSkipsBlockedSenders() {
        unreadMessage(bob, alice);
        unreadMessage(bob, alice);
        unreadMessage(carol, alice);
        em.persist(new UserBlock(alice, bob));
        em.flush();

        assertEquals(1L, messages.countUnreadNotBlocked(alice));
        // The existing query is untouched: it still counts everything.
        assertEquals(3L, messages.countUnreadMessages(alice));
    }

    @Test
    @DisplayName("a block in the other direction does not hide messages the blocker is receiving")
    void unreadCountIgnoresBlocksAgainstTheUser() {
        unreadMessage(bob, alice);
        em.persist(new UserBlock(bob, alice));
        em.flush();

        assertEquals(1L, messages.countUnreadNotBlocked(alice));
    }

    @Test
    @DisplayName("countUnreadNotBlocked is zero with no messages")
    void unreadCountIsZeroWithNoMessages() {
        assertEquals(0L, messages.countUnreadNotBlocked(alice));
    }
}
