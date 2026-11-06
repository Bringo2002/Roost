package com.roost.repository;

import com.roost.model.Role;
import com.roost.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the V4 migration: user emails must be stored trimmed and lowercase,
 * so exact-match lookups can't miss an account (or create a duplicate) over
 * capitalization. Runs against a real PostgreSQL (Testcontainers) with the
 * Flyway migrations applied, same pattern as the other repository tests.
 */
@DataJpaTest(properties = "spring.sql.init.mode=never")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class UserRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    private UserRepository userRepository;

    private static User user(String email) {
        User u = new User();
        u.setName("Test User");
        u.setEmail(email);
        u.setPassword("hashed-password");
        u.setRole(Role.TENANT);
        return u;
    }

    @Test
    @DisplayName("stores and finds a canonical (lowercase) email")
    void acceptsCanonicalEmail() {
        userRepository.saveAndFlush(user("ada@example.com"));

        assertTrue(userRepository.findByEmail("ada@example.com").isPresent());
    }

    @Test
    @DisplayName("rejects an email containing uppercase letters")
    void rejectsMixedCaseEmail() {
        assertThrows(DataIntegrityViolationException.class,
                () -> userRepository.saveAndFlush(user("Ada@Example.com")));
    }

    @Test
    @DisplayName("rejects an email with leading or trailing whitespace")
    void rejectsPaddedEmail() {
        assertThrows(DataIntegrityViolationException.class,
                () -> userRepository.saveAndFlush(user(" ada@example.com ")));
    }
}
