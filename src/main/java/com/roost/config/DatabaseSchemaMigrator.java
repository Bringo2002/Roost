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

            // Extended amenity columns
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS ac BOOLEAN DEFAULT FALSE;");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS heating BOOLEAN DEFAULT FALSE;");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS laundry BOOLEAN DEFAULT FALSE;");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS dstv BOOLEAN DEFAULT FALSE;");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS fence BOOLEAN DEFAULT FALSE;");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS intercom BOOLEAN DEFAULT FALSE;");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS elevator BOOLEAN DEFAULT FALSE;");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS caretaker BOOLEAN DEFAULT FALSE;");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS rooftop BOOLEAN DEFAULT FALSE;");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS garden BOOLEAN DEFAULT FALSE;");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS storage BOOLEAN DEFAULT FALSE;");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS pool BOOLEAN DEFAULT FALSE;");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS gym BOOLEAN DEFAULT FALSE;");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS play_area BOOLEAN DEFAULT FALSE;");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS cleaning BOOLEAN DEFAULT FALSE;");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS garbage BOOLEAN DEFAULT FALSE;");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS wheelchair BOOLEAN DEFAULT FALSE;");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS solar BOOLEAN DEFAULT FALSE;");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS generator BOOLEAN DEFAULT FALSE;");

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
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS reports_reviewed_at TIMESTAMP;");

            // Direct Landlord, Caretaker, Utility & Endorsement columns
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS is_direct_landlord BOOLEAN DEFAULT FALSE;");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS landlord_endorsed BOOLEAN DEFAULT FALSE;");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS endorsement_token VARCHAR(255);");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS owner_verify_name VARCHAR(255);");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS owner_verify_phone VARCHAR(255);");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS manager_role VARCHAR(255) DEFAULT 'LANDLORD';");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS caretaker_name VARCHAR(255);");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS caretaker_phone VARCHAR(255);");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS caretaker_lives_on_site BOOLEAN DEFAULT FALSE;");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS deposit_months INT DEFAULT 1;");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS water_fee DOUBLE PRECISION DEFAULT 0.0;");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS garbage_fee DOUBLE PRECISION DEFAULT 0.0;");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS service_charge DOUBLE PRECISION DEFAULT 0.0;");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS electricity_type VARCHAR(255) DEFAULT 'TOKEN';");
            jdbcTemplate.execute("ALTER TABLE properties ADD COLUMN IF NOT EXISTS nearby_facilities TEXT;");

            // Permanently drop obsolete holding_fee columns if present in properties table
            try {
                jdbcTemplate.execute("ALTER TABLE properties DROP COLUMN IF EXISTS holding_fee_paid;");
                jdbcTemplate.execute("ALTER TABLE properties DROP COLUMN IF EXISTS holding_fee;");
            } catch (Exception e) {
                log.info("Legacy column drop notice: " + e.getMessage());
            }

            log.info("Database schema migration completed successfully!");
        } catch (Exception e) {
            log.warn("Database schema migration notice: " + e.getMessage());
        }

        // ── Media deduplication table ────────────────────────────────────
        // Maps SHA-256 content hashes to existing R2 objects so the presign
        // endpoint can short-circuit when the same file is uploaded again
        // (across listings or re-uploads of the same photo). The table is
        // append-only — rows are never updated, only inserted on first
        // upload of a given hash.
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS media_hashes (
                    content_hash  VARCHAR(64)   PRIMARY KEY,
                    public_url    VARCHAR(512)  NOT NULL,
                    r2_key        VARCHAR(255)  NOT NULL,
                    content_type  VARCHAR(100)  NOT NULL,
                    size_bytes    BIGINT        NOT NULL,
                    created_at    TIMESTAMP     DEFAULT NOW()
                );
            """);
        } catch (Exception e) {
            log.warn("Could not create media_hashes table: " + e.getMessage());
        }

        createSearchIndexes();

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
        enforceNotNull("properties", "is_direct_landlord", "FALSE");
        enforceNotNull("properties", "landlord_endorsed", "FALSE");
        enforceNotNull("properties", "caretaker_lives_on_site", "FALSE");
        enforceNotNull("properties", "deposit_months", "1");
        enforceNotNull("properties", "water_fee", "0.0");
        enforceNotNull("properties", "garbage_fee", "0.0");
        enforceNotNull("properties", "service_charge", "0.0");
    }

    /**
     * pg_trgm GIN indexes backing PropertyRepository's `q` free-text
     * search (see that class's doc for the query itself). A plain
     * `LIKE '%word%'` can't use a normal btree index -- the leading
     * wildcard means Postgres has to scan every row -- but a trigram
     * GIN index can, which is the difference between search staying
     * fast at scale and slowly turning into a full table scan on every
     * keystroke as the listings table grows.
     *
     * pg_trgm is a "trusted" extension as of Postgres 13, so this
     * generally doesn't need superuser -- but some managed hosting
     * providers restrict extensions regardless, so this is wrapped the
     * same fail-open way as the rest of this file: if it can't be
     * created, search still works, just via a sequential scan, exactly
     * as it did before this method existed.
     *
     * Each expression index below has to match, verbatim, the exact
     * expression PropertyRepository's query applies to that column
     * (e.g. `lower(title)`, `lower(coalesce(building_name, ''))`) --
     * that's what lets Postgres recognise the query can use it. If a
     * future change to that query's WHERE clause changes how a column
     * is wrapped, the matching index here needs to change with it, or
     * it silently stops being used (search stays correct, just slow
     * again).
     */
    private void createSearchIndexes() {
        try {
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm;");
        } catch (Exception e) {
            log.warn("Could not create pg_trgm extension (search will still work, just without " +
                    "trigram-indexed acceleration): " + e.getMessage());
            return;
        }

        createTrigramIndex("idx_properties_title_trgm", "properties", "lower(title)");
        createTrigramIndex("idx_properties_building_name_trgm", "properties", "lower(coalesce(building_name, ''))");
        createTrigramIndex("idx_properties_location_trgm", "properties", "lower(location)");
        createTrigramIndex("idx_properties_description_trgm", "properties", "lower(coalesce(description, ''))");
        createTrigramIndex("idx_properties_nearby_facilities_trgm", "properties", "lower(coalesce(nearby_facilities, ''))");
        createTrigramIndex("idx_property_custom_amenities_trgm", "property_custom_amenities", "lower(amenity)");

        // Every filter/feed/search query filters on these two columns
        // (status = 'PUBLISHED' AND available = true) before anything
        // else -- this is the single highest-value index in the app,
        // not specific to text search, but it belongs alongside the
        // trigram indexes since it's what everything above narrows
        // down from. Partial on status = 'PUBLISHED' since that's the
        // only value every public-facing query filters on; DRAFT/
        // UNDER_REVIEW rows never benefit from this index and there's
        // no reason to pay to maintain entries for them.
        try {
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_properties_published_available " +
                    "ON properties (available) WHERE status = 'PUBLISHED';");
        } catch (Exception e) {
            log.warn("Could not create idx_properties_published_available: " + e.getMessage());
        }
    }

    /** One GIN trigram index, isolated in its own try/catch so one
     *  column's index failing (e.g. a lock held by another connection)
     *  doesn't prevent the others from being created. */
    private void createTrigramIndex(String indexName, String table, String expression) {
        try {
            jdbcTemplate.execute(String.format(
                    "CREATE INDEX IF NOT EXISTS %s ON %s USING GIN (%s gin_trgm_ops);",
                    indexName, table, expression));
        } catch (Exception e) {
            log.warn("Could not create {}: {}", indexName, e.getMessage());
        }
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
