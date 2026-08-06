package com.roost.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(1) // Run BEFORE DataSeeder
public class DatabaseSchemaMigrator implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSchemaMigrator.class);
    private final JdbcTemplate jdbcTemplate;

    public DatabaseSchemaMigrator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        log.info("Checking & migrating PostgreSQL database schema...");
        try {
            // User table columns
            jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS phone_verified BOOLEAN DEFAULT FALSE;");
            jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS public_key TEXT;");
            jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS last_active_at TIMESTAMP;");

            // Property table columns
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS landlord_phone VARCHAR(255);");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS landlord_name VARCHAR(255);");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS landlord_id VARCHAR(255);");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS video_url VARCHAR(255);");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS house_type VARCHAR(255) DEFAULT 'BEDSITTER';");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS bathrooms INT DEFAULT 1;");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS furnished BOOLEAN DEFAULT FALSE;");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS parking BOOLEAN DEFAULT FALSE;");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS water BOOLEAN DEFAULT TRUE;");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS wifi BOOLEAN DEFAULT FALSE;");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS security BOOLEAN DEFAULT TRUE;");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS pet_friendly BOOLEAN DEFAULT FALSE;");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS balcony BOOLEAN DEFAULT FALSE;");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS deposit VARCHAR(255);");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS move_in_date VARCHAR(255);");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS listed_at TIMESTAMP;");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS last_confirmed_at TIMESTAMP;");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS view_count INT DEFAULT 0;");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS save_count INT DEFAULT 0;");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS status VARCHAR(255) DEFAULT 'PUBLISHED';");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS photo_approved BOOLEAN DEFAULT FALSE;");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS building_name VARCHAR(255);");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS gps_verified BOOLEAN DEFAULT FALSE;");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS gps_verified_at TIMESTAMP;");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS community_verified BOOLEAN DEFAULT FALSE;");

            log.info("Database schema migration completed successfully!");
        } catch (Exception e) {
            log.warn("Database schema migration notice: " + e.getMessage());
        }

        // This constraint predates ADMIN being added to the Role enum and
        // was only ever fixed by hand directly on the live database, which
        // meant it would silently reappear in its old, stricter form on any
        // fresh environment built from schema alone. Tracking it here makes
        // it reproducible and self-healing instead of tribal knowledge.
        // Isolated in its own try/catch, same reasoning as enforceNotNull
        // below -- one constraint failing to update shouldn't block the
        // rest of startup.
        try {
            jdbcTemplate.execute("ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;");
            jdbcTemplate.execute("ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN ('TENANT', 'LANDLORD', 'ADMIN'));");
        } catch (Exception e) {
            log.warn("Could not update users_role_check constraint: " + e.getMessage());
        }

        // The entity fields for these columns are primitives (boolean/int),
        // so Hibernate's own ddl-auto=update tries to mark them NOT NULL on
        // every boot. That silently fails on any row still holding a null
        // from before the column existed, which is why the same warning
        // reappears on every startup without actually fixing anything.
        // Backfill first, then enforce the constraint at the DB level so
        // Hibernate's check becomes a no-op going forward.
        enforceNotNull("properties", "bathrooms", "1");
        enforceNotNull("properties", "furnished", "FALSE");
        enforceNotNull("properties", "parking", "FALSE");
        enforceNotNull("properties", "water", "TRUE");
        enforceNotNull("properties", "wifi", "FALSE");
        enforceNotNull("properties", "security", "TRUE");
        enforceNotNull("properties", "pet_friendly", "FALSE");
        enforceNotNull("properties", "balcony", "FALSE");
        enforceNotNull("properties", "photo_approved", "FALSE");
        enforceNotNull("properties", "gps_verified", "FALSE");
        enforceNotNull("properties", "community_verified", "FALSE");
        enforceNotNull("properties", "view_count", "0");
        enforceNotNull("properties", "save_count", "0");
        enforceNotNull("users", "phone_verified", "FALSE");
        // Unlike the primitive boolean/int columns above, `status` is a
        // String field in the entity, so Hibernate never tried (and
        // failed) to enforce NOT NULL on it -- which is exactly why this
        // one got missed. The ADD COLUMN ... DEFAULT above is a no-op
        // here since the column already existed without a default before
        // this migrator was written, so any row created before status
        // existed is still sitting on NULL and invisible to every query
        // that filters on status = 'PUBLISHED' (i.e. all of them).
        enforceNotNull("properties", "status", "'PUBLISHED'");
    }

    /**
     * Backfills any existing null values in {@code table.column} with
     * {@code defaultValue}, then applies a NOT NULL constraint. Each column
     * is isolated in its own try/catch -- one failing column (e.g. a
     * permissions issue, or a lock held by another connection) must not
     * prevent the rest from being enforced.
     */
    private void enforceNotNull(String table, String column, String defaultValue) {
        try {
            jdbcTemplate.execute(String.format(
                    "UPDATE %s SET %s = %s WHERE %s IS NULL;", table, column, defaultValue, column));
            jdbcTemplate.execute(String.format(
                    "ALTER TABLE %s ALTER COLUMN %s SET NOT NULL;", table, column));
        } catch (Exception e) {
            log.warn("Could not enforce NOT NULL on {}.{}: {}", table, column, e.getMessage());
        }
    }
}
