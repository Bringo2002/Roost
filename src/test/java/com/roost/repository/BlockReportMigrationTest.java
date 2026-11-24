package com.roost.repository;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the V9 migration (block and report tables) on a brand-new database
 * and checks the rules the database itself enforces: nobody blocks or
 * reports themselves, and a report must use a known reason.
 */
@Testcontainers
class BlockReportMigrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    private Connection migratedDatabase(String name) throws SQLException {
        try (Connection admin = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE " + name);
        }
        String url = postgres.getJdbcUrl().replaceFirst("/[^/?]+(?=\\?|$)", "/" + name);
        Flyway.configure()
                .dataSource(url, postgres.getUsername(), postgres.getPassword())
                .load()
                .migrate();
        return DriverManager.getConnection(url, postgres.getUsername(), postgres.getPassword());
    }

    /**
     * A database where Hibernate created the two tables from the entities
     * before V9 ran (what aws does with ddl-auto=update): same columns, no
     * CHECK constraints and no defaults.
     */
    private Connection databaseWithHibernateCreatedTables(String name) throws SQLException {
        try (Connection admin = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE " + name);
        }
        String url = postgres.getJdbcUrl().replaceFirst("/[^/?]+(?=\\?|$)", "/" + name);
        Flyway.configure()
                .dataSource(url, postgres.getUsername(), postgres.getPassword())
                .target(MigrationVersion.fromVersion("8"))
                .load()
                .migrate();
        try (Connection connection = DriverManager.getConnection(
                url, postgres.getUsername(), postgres.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE user_blocks (
                        id BIGSERIAL PRIMARY KEY,
                        blocker_id BIGINT NOT NULL REFERENCES users(id),
                        blocked_id BIGINT NOT NULL REFERENCES users(id),
                        created_at TIMESTAMP NOT NULL
                    )""");
            statement.execute("""
                    CREATE TABLE user_reports (
                        id BIGSERIAL PRIMARY KEY,
                        reporter_id BIGINT NOT NULL REFERENCES users(id),
                        reported_id BIGINT NOT NULL REFERENCES users(id),
                        reason VARCHAR(255) NOT NULL,
                        note VARCHAR(500),
                        status VARCHAR(255) NOT NULL,
                        created_at TIMESTAMP NOT NULL
                    )""");
        }
        Flyway.configure()
                .dataSource(url, postgres.getUsername(), postgres.getPassword())
                .load()
                .migrate();
        return DriverManager.getConnection(url, postgres.getUsername(), postgres.getPassword());
    }

    private long insertUser(Statement statement, String email) throws SQLException {
        try (var rs = statement.executeQuery(
                "INSERT INTO users (name, email, role) VALUES ('" + email + "', '" + email
                        + "', 'TENANT') RETURNING id")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    @Test
    @DisplayName("a user cannot block themselves")
    void selfBlockIsRefused() throws Exception {
        try (Connection connection = migratedDatabase("block_self");
             Statement statement = connection.createStatement()) {
            long id = insertUser(statement, "a@example.com");

            assertThrows(SQLException.class, () -> statement.execute(
                    "INSERT INTO user_blocks (blocker_id, blocked_id) VALUES (" + id + ", " + id + ")"));
        }
    }

    @Test
    @DisplayName("the same block cannot be stored twice")
    void duplicateBlockIsRefused() throws Exception {
        try (Connection connection = migratedDatabase("block_dup");
             Statement statement = connection.createStatement()) {
            long a = insertUser(statement, "a@example.com");
            long b = insertUser(statement, "b@example.com");
            statement.execute("INSERT INTO user_blocks (blocker_id, blocked_id) VALUES (" + a + ", " + b + ")");

            assertThrows(SQLException.class, () -> statement.execute(
                    "INSERT INTO user_blocks (blocker_id, blocked_id) VALUES (" + a + ", " + b + ")"));
        }
    }

    @Test
    @DisplayName("a user cannot report themselves")
    void selfReportIsRefused() throws Exception {
        try (Connection connection = migratedDatabase("report_self");
             Statement statement = connection.createStatement()) {
            long id = insertUser(statement, "a@example.com");

            assertThrows(SQLException.class, () -> statement.execute(
                    "INSERT INTO user_reports (reporter_id, reported_id, reason) VALUES ("
                            + id + ", " + id + ", 'SPAM')"));
        }
    }

    @Test
    @DisplayName("a report must use a known reason, and starts open")
    void reportReasonIsChecked() throws Exception {
        try (Connection connection = migratedDatabase("report_reason");
             Statement statement = connection.createStatement()) {
            long a = insertUser(statement, "a@example.com");
            long b = insertUser(statement, "b@example.com");

            assertThrows(SQLException.class, () -> statement.execute(
                    "INSERT INTO user_reports (reporter_id, reported_id, reason) VALUES ("
                            + a + ", " + b + ", 'BECAUSE')"));

            statement.execute("INSERT INTO user_reports (reporter_id, reported_id, reason) VALUES ("
                    + a + ", " + b + ", 'SPAM')");
            try (var rs = statement.executeQuery("SELECT status FROM user_reports")) {
                assertTrue(rs.next());
                org.junit.jupiter.api.Assertions.assertEquals("OPEN", rs.getString(1));
            }
        }
    }

    @Test
    @DisplayName("tables Hibernate created first still get the self-block and duplicate-block rules")
    void blockRulesApplyWhenHibernateCreatedTheTableFirst() throws Exception {
        try (Connection connection = databaseWithHibernateCreatedTables("block_hibernate_first");
             Statement statement = connection.createStatement()) {
            long a = insertUser(statement, "a@example.com");
            long b = insertUser(statement, "b@example.com");

            assertThrows(SQLException.class, () -> statement.execute(
                    "INSERT INTO user_blocks (blocker_id, blocked_id, created_at) VALUES ("
                            + a + ", " + a + ", now())"));

            statement.execute("INSERT INTO user_blocks (blocker_id, blocked_id, created_at) VALUES ("
                    + a + ", " + b + ", now())");
            assertThrows(SQLException.class, () -> statement.execute(
                    "INSERT INTO user_blocks (blocker_id, blocked_id, created_at) VALUES ("
                            + a + ", " + b + ", now())"));
        }
    }

    @Test
    @DisplayName("tables Hibernate created first still get the report rules")
    void reportRulesApplyWhenHibernateCreatedTheTableFirst() throws Exception {
        try (Connection connection = databaseWithHibernateCreatedTables("report_hibernate_first");
             Statement statement = connection.createStatement()) {
            long a = insertUser(statement, "a@example.com");
            long b = insertUser(statement, "b@example.com");

            assertThrows(SQLException.class, () -> statement.execute(
                    "INSERT INTO user_reports (reporter_id, reported_id, reason, status, created_at) VALUES ("
                            + a + ", " + a + ", 'SPAM', 'OPEN', now())"));
            assertThrows(SQLException.class, () -> statement.execute(
                    "INSERT INTO user_reports (reporter_id, reported_id, reason, status, created_at) VALUES ("
                            + a + ", " + b + ", 'BECAUSE', 'OPEN', now())"));
            assertThrows(SQLException.class, () -> statement.execute(
                    "INSERT INTO user_reports (reporter_id, reported_id, reason, status, created_at) VALUES ("
                            + a + ", " + b + ", 'SPAM', 'MAYBE', now())"));

            statement.execute("INSERT INTO user_reports (reporter_id, reported_id, reason, status, created_at) VALUES ("
                    + a + ", " + b + ", 'SPAM', 'OPEN', now())");
        }
    }
}
