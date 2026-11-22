package com.roost.repository;

import com.roost.model.PendingFileDeletion;
import com.roost.model.PendingFileDeletion.Bucket;
import jakarta.persistence.PersistenceException;
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
 * Runs the V7 schema (the queue of files waiting to be deleted) against a
 * real PostgreSQL, so the rules the nightly job relies on (a file is queued
 * once, only known buckets) are tested where the database enforces them.
 */
@DataJpaTest(properties = "spring.sql.init.mode=never")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class PendingFileDeletionRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    private PendingFileDeletionRepository queue;

    @Autowired
    private TestEntityManager em;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 12, 1, 3, 0);

    @Test
    @DisplayName("due returns only entries whose delete-after date has passed, oldest first")
    void dueReturnsOnlyPassedEntriesOldestFirst() {
        queue.save(new PendingFileDeletion(Bucket.PUBLIC, "properties/newer.jpg", NOW.minusDays(1)));
        queue.save(new PendingFileDeletion(Bucket.PRIVATE, "verification/older.pdf", NOW.minusDays(10)));
        queue.save(new PendingFileDeletion(Bucket.PUBLIC, "properties/later.jpg", NOW.plusDays(5)));
        em.flush();

        List<PendingFileDeletion> due = queue.findByDeleteAfterLessThanEqualOrderByDeleteAfterAscIdAsc(NOW);

        assertEquals(List.of("verification/older.pdf", "properties/newer.jpg"),
                due.stream().map(PendingFileDeletion::getStorageKey).toList());
    }

    @Test
    @DisplayName("an entry due exactly now is returned")
    void entryDueExactlyNowIsReturned() {
        queue.save(new PendingFileDeletion(Bucket.PUBLIC, "properties/edge.jpg", NOW));
        em.flush();

        assertEquals(1, queue.findByDeleteAfterLessThanEqualOrderByDeleteAfterAscIdAsc(NOW).size());
    }

    @Test
    @DisplayName("the same file cannot be queued twice")
    void sameFileCannotBeQueuedTwice() {
        em.persistAndFlush(new PendingFileDeletion(Bucket.PUBLIC, "properties/dup.jpg", NOW));

        assertThrows(PersistenceException.class,
                () -> em.persistAndFlush(new PendingFileDeletion(Bucket.PUBLIC, "properties/dup.jpg", NOW.plusDays(1))));
    }

    @Test
    @DisplayName("the same key in the other bucket is a different file")
    void sameKeyInOtherBucketIsDifferentFile() {
        em.persistAndFlush(new PendingFileDeletion(Bucket.PUBLIC, "shared/key.jpg", NOW));

        em.persistAndFlush(new PendingFileDeletion(Bucket.PRIVATE, "shared/key.jpg", NOW));

        assertEquals(2, queue.count());
    }

    @Test
    @DisplayName("existsByBucketAndStorageKey tells whether a file is already queued")
    void existsTellsWhetherFileIsQueued() {
        em.persistAndFlush(new PendingFileDeletion(Bucket.PRIVATE, "verification/x.pdf", NOW));

        assertTrue(queue.existsByBucketAndStorageKey(Bucket.PRIVATE, "verification/x.pdf"));
        assertFalse(queue.existsByBucketAndStorageKey(Bucket.PUBLIC, "verification/x.pdf"));
        assertFalse(queue.existsByBucketAndStorageKey(Bucket.PRIVATE, "verification/y.pdf"));
    }
}
