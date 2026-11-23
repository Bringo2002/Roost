package com.roost.repository;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the V8 migration against a real PostgreSQL, in both situations it
 * meets: a database that already has inspections (production, where Hibernate
 * created the table earlier) and a brand-new database (where the table does
 * not exist yet when Flyway runs).
 *
 * <p>Each test gets its own database so the two cases cannot affect each other.
 */
@Testcontainers
class InspectionTenantNameMigrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    private String createDatabase(String name) throws SQLException {
        try (Connection admin = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE " + name);
        }
        return postgres.getJdbcUrl().replaceFirst("/[^/?]+(?=\\?|$)", "/" + name);
    }

    private Flyway flyway(String url, String targetVersion) {
        FluentConfiguration configuration = Flyway.configure()
                .dataSource(url, postgres.getUsername(), postgres.getPassword());
        if (targetVersion != null) {
            configuration.target(MigrationVersion.fromVersion(targetVersion));
        }
        return configuration.load();
    }

    @Test
    @DisplayName("existing inspections get their tenant's current name")
    void existingInspectionsAreBackfilledWithTheTenantName() throws Exception {
        String url = createDatabase("inspection_backfill");
        flyway(url, "7").migrate();

        try (Connection connection = DriverManager.getConnection(
                url, postgres.getUsername(), postgres.getPassword());
             Statement statement = connection.createStatement()) {
            // The table as Hibernate created it before V8.
            statement.execute("""
                    CREATE TABLE move_in_inspections (
                        id BIGSERIAL PRIMARY KEY,
                        tenant_id BIGINT NOT NULL,
                        landlord_name VARCHAR(255),
                        rooms_json TEXT NOT NULL,
                        created_at TIMESTAMP NOT NULL
                    )""");
            long tenantId;
            try (ResultSet rs = statement.executeQuery(
                    "INSERT INTO users (name, email, role) "
                            + "VALUES ('Ada Lovelace', 'ada@example.com', 'TENANT') RETURNING id")) {
                rs.next();
                tenantId = rs.getLong(1);
            }
            statement.execute("INSERT INTO move_in_inspections (tenant_id, rooms_json, created_at) "
                    + "VALUES (" + tenantId + ", '{}', now())");
        }

        flyway(url, null).migrate();

        try (Connection connection = DriverManager.getConnection(
                url, postgres.getUsername(), postgres.getPassword());
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT tenant_name FROM move_in_inspections")) {
            assertTrue(rs.next());
            assertEquals("Ada Lovelace", rs.getString(1));
        }
    }

    @Test
    @DisplayName("a brand-new database migrates cleanly, before the inspections table exists")
    void freshDatabaseMigratesWithoutTheInspectionsTable() throws Exception {
        String url = createDatabase("inspection_fresh");

        MigrateResult result = flyway(url, null).migrate();

        assertTrue(result.success);
        assertEquals("8", result.targetSchemaVersion);
    }
}
