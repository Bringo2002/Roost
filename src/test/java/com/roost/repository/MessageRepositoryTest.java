package com.roost.repository;

import com.roost.model.Message;
import com.roost.model.MessageReaction;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for the chat-history queries, run against a real
 * PostgreSQL (Testcontainers) per CLAUDE.md, same pattern as
 * PropertyRepositoryTest / PropertyReportRepositoryTest.
 *
 * Message.reactions is a @OneToMany(fetch = EAGER) with no @BatchSize,
 * and MessageResponseDto reads it for every message in a chat-history
 * page -- without batching that's one extra SELECT per message. This
 * guards the @BatchSize fix on Message.reactions.
 *
 * spring.sql.init.mode=never: same reasoning as PropertyRepositoryTest.
 */
@DataJpaTest(properties = "spring.sql.init.mode=never")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class MessageRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private TestEntityManager em;

    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        userA = persistUser("userA");
        userB = persistUser("userB");
    }

    private User persistUser(String prefix) {
        User u = new User();
        u.setName(prefix);
        u.setEmail(prefix + "+" + System.nanoTime() + "@example.com");
        u.setPassword("hashed-password");
        u.setRole(Role.TENANT);
        return em.persist(u);
    }

    /** A message from userA to userB, with two reactions from userB --
     *  enough for a DTO conversion to touch the EAGER reactions collection. */
    private Message messageWithReactions(LocalDateTime timestamp) {
        Message m = new Message();
        m.setSender(userA);
        m.setRecipient(userB);
        m.setContent("hello");
        m.setTimestamp(timestamp);
        em.persist(m);

        MessageReaction r1 = new MessageReaction();
        r1.setMessage(m);
        r1.setUser(userB);
        r1.setEmoji("👍");
        em.persist(r1);

        MessageReaction r2 = new MessageReaction();
        r2.setMessage(m);
        r2.setUser(userB);
        r2.setEmoji("❤️");
        em.persist(r2);

        return m;
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private long statementsForPageOfSize(int messageCount) {
        LocalDateTime base = LocalDateTime.now().minusDays(1);
        for (int i = 0; i < messageCount; i++) {
            messageWithReactions(base.plusMinutes(i));
        }
        flushAndClear();

        SessionFactory sf = em.getEntityManager().getEntityManagerFactory().unwrap(SessionFactory.class);
        Statistics stats = sf.getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        List<Message> page = messageRepository.findChatHistoryLatestDesc(
                userA, userB, null, PageRequest.of(0, messageCount));
        // Touch reactions on every message; a working batch-fetch means
        // this costs one additional statement total, not one per message.
        for (Message m : page) {
            m.getReactions().size();
        }
        return stats.getPrepareStatementCount();
    }

    @Test
    @DisplayName("findChatHistoryLatestDesc: reactions load in a small, row-count-independent number of statements")
    void findChatHistoryLatestDesc_doesNotGrowWithMessageCount() {
        long forThree = statementsForPageOfSize(3);

        userA = em.find(User.class, userA.getId());
        userB = em.find(User.class, userB.getId());
        long forFifteen = statementsForPageOfSize(15);

        // A per-row reactions lookup (the N+1 this guards against) would
        // make the 15-message page cost ~12 more statements than the
        // 3-message page.
        assertEquals(forThree, forFifteen,
                "statement count changed with message count (3 messages: " + forThree
                        + ", 15 messages: " + forFifteen + ") -- an N+1 crept in");

        // Expected: 1 for the page itself + 1 batched query for every
        // pending message's reactions (the fix under test) + 2 more for
        // Message.sender/recipient, which are also default-EAGER
        // @ManyToOne with no fetch-join or @BatchSize of their own --
        // each resolves in exactly 1 query here only because every
        // message in a single conversation shares the same two users,
        // so it doesn't grow with message count either. (Batching those
        // two is a separate, smaller fix -- tracked, not done here.)
        // Anything above 4 means something new is being loaded per row.
        assertTrue(forFifteen <= 4,
                "expected at most 4 statements per page (page + sender + recipient + batched reactions), got " + forFifteen);
    }

    @Test
    @DisplayName("findChatHistoryLatestDesc: returns the conversation newest-first with reactions attached")
    void findChatHistoryLatestDesc_returnsCorrectContentAndReactions() {
        Message first = messageWithReactions(LocalDateTime.now().minusMinutes(10));
        Message second = messageWithReactions(LocalDateTime.now().minusMinutes(5));
        flushAndClear();

        List<Message> page = messageRepository.findChatHistoryLatestDesc(
                userA, userB, null, PageRequest.of(0, 10));

        assertEquals(2, page.size());
        assertEquals(second.getId(), page.get(0).getId(), "newest message must come first");
        assertEquals(first.getId(), page.get(1).getId());
        assertEquals(2, page.get(0).getReactions().size());
    }
}
