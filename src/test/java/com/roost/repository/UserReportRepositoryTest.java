package com.roost.repository;

import com.roost.model.Role;
import com.roost.model.User;
import com.roost.model.UserReport;
import com.roost.model.UserReport.Reason;
import com.roost.model.UserReport.Status;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Runs the V9 {@code user_reports} schema against a real PostgreSQL: what a
 * report stores, and the query that lets the service refuse the same person
 * reporting the same user over and over.
 */
@DataJpaTest(properties = "spring.sql.init.mode=never")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class UserReportRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    private UserReportRepository reports;

    @Autowired
    private TestEntityManager em;

    private User reporter;
    private User reported;

    @BeforeEach
    void setUp() {
        reporter = persistUser("reporter");
        reported = persistUser("reported");
    }

    private User persistUser(String prefix) {
        User u = new User();
        u.setName(prefix);
        u.setEmail(EmailNormalizer.normalize(prefix + "+" + System.nanoTime() + "@example.com"));
        u.setPassword("hashed-password");
        u.setRole(Role.TENANT);
        return em.persist(u);
    }

    @Test
    @DisplayName("a new report is stored open, with its reason, note and a creation time")
    void newReportIsOpenWithItsDetails() {
        UserReport saved = reports.save(new UserReport(reporter, reported, Reason.HARASSMENT, "keeps insulting me"));
        em.flush();
        em.clear();

        UserReport loaded = reports.findById(saved.getId()).orElseThrow();

        assertEquals(Status.OPEN, loaded.getStatus());
        assertEquals(Reason.HARASSMENT, loaded.getReason());
        assertEquals("keeps insulting me", loaded.getNote());
        assertEquals(reporter.getId(), loaded.getReporter().getId());
        assertEquals(reported.getId(), loaded.getReported().getId());
        assertEquals(LocalDateTime.now().getYear(), loaded.getCreatedAt().getYear());
    }

    @Test
    @DisplayName("the note is optional")
    void noteIsOptional() {
        UserReport saved = reports.save(new UserReport(reporter, reported, Reason.SPAM, null));
        em.flush();
        em.clear();

        assertEquals(null, reports.findById(saved.getId()).orElseThrow().getNote());
    }

    @Test
    @DisplayName("every reason in the list can be stored")
    void everyReasonCanBeStored() {
        for (Reason reason : Reason.values()) {
            reports.save(new UserReport(reporter, reported, reason, null));
        }
        em.flush();

        assertEquals(Reason.values().length, reports.count());
    }

    @Test
    @DisplayName("the database refuses a report of yourself")
    void selfReportIsRefused() {
        assertThrows(PersistenceException.class,
                () -> em.persistAndFlush(new UserReport(reporter, reporter, Reason.OTHER, null)));
    }

    @Test
    @DisplayName("countByReporterAndReportedAndCreatedAtAfter counts only recent reports of that pair")
    void recentReportsOfThePairAreCounted() {
        User other = persistUser("other");
        LocalDateTime now = LocalDateTime.now();

        UserReport old = new UserReport(reporter, reported, Reason.SPAM, null);
        old.setCreatedAt(now.minusDays(3));
        UserReport recent = new UserReport(reporter, reported, Reason.SCAM_OR_FRAUD, null);
        recent.setCreatedAt(now.minusHours(2));
        UserReport otherTarget = new UserReport(reporter, other, Reason.SPAM, null);
        otherTarget.setCreatedAt(now.minusHours(1));
        UserReport otherReporter = new UserReport(other, reported, Reason.SPAM, null);
        otherReporter.setCreatedAt(now.minusHours(1));
        reports.save(old);
        reports.save(recent);
        reports.save(otherTarget);
        reports.save(otherReporter);
        em.flush();

        assertEquals(1, reports.countByReporterAndReportedAndCreatedAtAfter(reporter, reported, now.minusDays(1)));
        assertEquals(2, reports.countByReporterAndReportedAndCreatedAtAfter(reporter, reported, now.minusDays(7)));
    }
}
