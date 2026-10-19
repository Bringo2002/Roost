-- V2: Add all columns that DatabaseSchemaMigrator was applying on every boot.
-- Now tracked as an explicit, versioned, one-time migration.

-- ── Users: extra columns ──────────────────────────────────────────────────────
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone_verified    BOOLEAN   DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified    BOOLEAN   DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS enabled           BOOLEAN   DEFAULT TRUE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS account_non_locked BOOLEAN  DEFAULT TRUE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS public_key        TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_active_at    TIMESTAMP;

-- ── Users: backfill nulls then enforce NOT NULL ───────────────────────────────
UPDATE users SET phone_verified     = FALSE WHERE phone_verified     IS NULL;
UPDATE users SET email_verified     = FALSE WHERE email_verified     IS NULL;
UPDATE users SET enabled            = TRUE  WHERE enabled            IS NULL;
UPDATE users SET account_non_locked = TRUE  WHERE account_non_locked IS NULL;
ALTER TABLE users ALTER COLUMN phone_verified     SET NOT NULL;
ALTER TABLE users ALTER COLUMN email_verified     SET NOT NULL;
ALTER TABLE users ALTER COLUMN enabled            SET NOT NULL;
ALTER TABLE users ALTER COLUMN account_non_locked SET NOT NULL;

-- ── Users: role constraint (includes ADMIN) ───────────────────────────────────
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users ADD  CONSTRAINT users_role_check
    CHECK (role IN ('TENANT', 'LANDLORD', 'ADMIN'));

-- ── Properties: core extra columns ───────────────────────────────────────────
ALTER TABLE properties ADD COLUMN IF NOT EXISTS landlord_phone   VARCHAR(255);
ALTER TABLE properties ADD COLUMN IF NOT EXISTS landlord_name    VARCHAR(255);
ALTER TABLE properties ADD COLUMN IF NOT EXISTS landlord_id      VARCHAR(255);
ALTER TABLE properties ADD COLUMN IF NOT EXISTS video_url        VARCHAR(255);
ALTER TABLE properties ADD COLUMN IF NOT EXISTS house_type       VARCHAR(255) DEFAULT 'BEDSITTER';
ALTER TABLE properties ADD COLUMN IF NOT EXISTS bathrooms        INT          DEFAULT 1;
ALTER TABLE properties ADD COLUMN IF NOT EXISTS furnished        BOOLEAN      DEFAULT FALSE;
ALTER TABLE properties ADD COLUMN IF NOT EXISTS parking         BOOLEAN      DEFAULT FALSE;
ALTER TABLE properties ADD COLUMN IF NOT EXISTS water           BOOLEAN      DEFAULT TRUE;
ALTER TABLE properties ADD COLUMN IF NOT EXISTS wifi            BOOLEAN      DEFAULT FALSE;
ALTER TABLE properties ADD COLUMN IF NOT EXISTS security        BOOLEAN      DEFAULT TRUE;
ALTER TABLE properties ADD COLUMN IF NOT EXISTS pet_friendly    BOOLEAN      DEFAULT FALSE;
ALTER TABLE properties ADD COLUMN IF NOT EXISTS balcony         BOOLEAN      DEFAULT FALSE;

-- ── Properties: extended amenity columns ─────────────────────────────────────
ALTER TABLE properties ADD COLUMN IF NOT EXISTS ac              BOOLEAN DEFAULT FALSE;
ALTER TABLE properties ADD COLUMN IF NOT EXISTS heating         BOOLEAN DEFAULT FALSE;
ALTER TABLE properties ADD COLUMN IF NOT EXISTS laundry         BOOLEAN DEFAULT FALSE;
ALTER TABLE properties ADD COLUMN IF NOT EXISTS dstv            BOOLEAN DEFAULT FALSE;
ALTER TABLE properties ADD COLUMN IF NOT EXISTS fence           BOOLEAN DEFAULT FALSE;
ALTER TABLE properties ADD COLUMN IF NOT EXISTS intercom        BOOLEAN DEFAULT FALSE;
ALTER TABLE properties ADD COLUMN IF NOT EXISTS elevator        BOOLEAN DEFAULT FALSE;
ALTER TABLE properties ADD COLUMN IF NOT EXISTS caretaker       BOOLEAN DEFAULT FALSE;
ALTER TABLE properties ADD COLUMN IF NOT EXISTS rooftop         BOOLEAN DEFAULT FALSE;
ALTER TABLE properties ADD COLUMN IF NOT EXISTS garden          BOOLEAN DEFAULT FALSE;
ALTER TABLE properties ADD COLUMN IF NOT EXISTS storage         BOOLEAN DEFAULT FALSE;
ALTER TABLE properties ADD COLUMN IF NOT EXISTS pool            BOOLEAN DEFAULT FALSE;
ALTER TABLE properties ADD COLUMN IF NOT EXISTS gym             BOOLEAN DEFAULT FALSE;
ALTER TABLE properties ADD COLUMN IF NOT EXISTS play_area       BOOLEAN DEFAULT FALSE;
ALTER TABLE properties ADD COLUMN IF NOT EXISTS cleaning        BOOLEAN DEFAULT FALSE;
ALTER TABLE properties ADD COLUMN IF NOT EXISTS garbage         BOOLEAN DEFAULT FALSE;
ALTER TABLE properties ADD COLUMN IF NOT EXISTS wheelchair      BOOLEAN DEFAULT FALSE;
ALTER TABLE properties ADD COLUMN IF NOT EXISTS solar           BOOLEAN DEFAULT FALSE;
ALTER TABLE properties ADD COLUMN IF NOT EXISTS generator       BOOLEAN DEFAULT FALSE;

-- ── Properties: listing-lifecycle columns ────────────────────────────────────
ALTER TABLE properties ADD COLUMN IF NOT EXISTS deposit            VARCHAR(255);
ALTER TABLE properties ADD COLUMN IF NOT EXISTS move_in_date       VARCHAR(255);
ALTER TABLE properties ADD COLUMN IF NOT EXISTS listed_at          TIMESTAMP;
ALTER TABLE properties ADD COLUMN IF NOT EXISTS last_confirmed_at  TIMESTAMP;
ALTER TABLE properties ADD COLUMN IF NOT EXISTS view_count         INT     DEFAULT 0;
ALTER TABLE properties ADD COLUMN IF NOT EXISTS save_count         INT     DEFAULT 0;
ALTER TABLE properties ADD COLUMN IF NOT EXISTS status             VARCHAR(255) DEFAULT 'PUBLISHED';
ALTER TABLE properties ADD COLUMN IF NOT EXISTS photo_approved     BOOLEAN DEFAULT FALSE;
ALTER TABLE properties ADD COLUMN IF NOT EXISTS building_name      VARCHAR(255);
ALTER TABLE properties ADD COLUMN IF NOT EXISTS gps_verified       BOOLEAN DEFAULT FALSE;
ALTER TABLE properties ADD COLUMN IF NOT EXISTS gps_verified_at    TIMESTAMP;
ALTER TABLE properties ADD COLUMN IF NOT EXISTS community_verified BOOLEAN DEFAULT FALSE;
ALTER TABLE properties ADD COLUMN IF NOT EXISTS reports_reviewed_at TIMESTAMP;

-- ── Properties: landlord / caretaker / endorsement columns ───────────────────
ALTER TABLE properties ADD COLUMN IF NOT EXISTS is_direct_landlord       BOOLEAN DEFAULT FALSE;
ALTER TABLE properties ADD COLUMN IF NOT EXISTS landlord_endorsed         BOOLEAN DEFAULT FALSE;
ALTER TABLE properties ADD COLUMN IF NOT EXISTS endorsement_token         VARCHAR(255);
ALTER TABLE properties ADD COLUMN IF NOT EXISTS owner_verify_name         VARCHAR(255);
ALTER TABLE properties ADD COLUMN IF NOT EXISTS owner_verify_phone        VARCHAR(255);
ALTER TABLE properties ADD COLUMN IF NOT EXISTS manager_role              VARCHAR(255) DEFAULT 'LANDLORD';
ALTER TABLE properties ADD COLUMN IF NOT EXISTS caretaker_name            VARCHAR(255);
ALTER TABLE properties ADD COLUMN IF NOT EXISTS caretaker_phone           VARCHAR(255);
ALTER TABLE properties ADD COLUMN IF NOT EXISTS caretaker_lives_on_site   BOOLEAN DEFAULT FALSE;
ALTER TABLE properties ADD COLUMN IF NOT EXISTS deposit_months            INT     DEFAULT 1;
ALTER TABLE properties ADD COLUMN IF NOT EXISTS water_fee                 DOUBLE PRECISION DEFAULT 0.0;
ALTER TABLE properties ADD COLUMN IF NOT EXISTS garbage_fee               DOUBLE PRECISION DEFAULT 0.0;
ALTER TABLE properties ADD COLUMN IF NOT EXISTS service_charge            DOUBLE PRECISION DEFAULT 0.0;
ALTER TABLE properties ADD COLUMN IF NOT EXISTS electricity_type          VARCHAR(255) DEFAULT 'TOKEN';
ALTER TABLE properties ADD COLUMN IF NOT EXISTS nearby_facilities         TEXT;

-- ── Properties: drop obsolete holding_fee columns ────────────────────────────
ALTER TABLE properties DROP COLUMN IF EXISTS holding_fee_paid;
ALTER TABLE properties DROP COLUMN IF EXISTS holding_fee;

-- ── Properties: backfill nulls then enforce NOT NULL ─────────────────────────
UPDATE properties SET bathrooms          = 1           WHERE bathrooms          IS NULL;
UPDATE properties SET furnished          = FALSE        WHERE furnished          IS NULL;
UPDATE properties SET parking            = FALSE        WHERE parking            IS NULL;
UPDATE properties SET water              = TRUE         WHERE water              IS NULL;
UPDATE properties SET wifi               = FALSE        WHERE wifi               IS NULL;
UPDATE properties SET security           = TRUE         WHERE security           IS NULL;
UPDATE properties SET pet_friendly       = FALSE        WHERE pet_friendly       IS NULL;
UPDATE properties SET balcony            = FALSE        WHERE balcony            IS NULL;
UPDATE properties SET photo_approved     = FALSE        WHERE photo_approved     IS NULL;
UPDATE properties SET gps_verified       = FALSE        WHERE gps_verified       IS NULL;
UPDATE properties SET community_verified = FALSE        WHERE community_verified IS NULL;
UPDATE properties SET view_count         = 0            WHERE view_count         IS NULL;
UPDATE properties SET save_count         = 0            WHERE save_count         IS NULL;
UPDATE properties SET status             = 'PUBLISHED'  WHERE status             IS NULL;
UPDATE properties SET is_direct_landlord       = FALSE  WHERE is_direct_landlord       IS NULL;
UPDATE properties SET landlord_endorsed        = FALSE  WHERE landlord_endorsed        IS NULL;
UPDATE properties SET caretaker_lives_on_site  = FALSE  WHERE caretaker_lives_on_site  IS NULL;
UPDATE properties SET deposit_months           = 1      WHERE deposit_months           IS NULL;
UPDATE properties SET water_fee                = 0.0    WHERE water_fee                IS NULL;
UPDATE properties SET garbage_fee              = 0.0    WHERE garbage_fee              IS NULL;
UPDATE properties SET service_charge           = 0.0    WHERE service_charge           IS NULL;
ALTER TABLE properties ALTER COLUMN bathrooms          SET NOT NULL;
ALTER TABLE properties ALTER COLUMN furnished          SET NOT NULL;
ALTER TABLE properties ALTER COLUMN parking            SET NOT NULL;
ALTER TABLE properties ALTER COLUMN water              SET NOT NULL;
ALTER TABLE properties ALTER COLUMN wifi               SET NOT NULL;
ALTER TABLE properties ALTER COLUMN security           SET NOT NULL;
ALTER TABLE properties ALTER COLUMN pet_friendly       SET NOT NULL;
ALTER TABLE properties ALTER COLUMN balcony            SET NOT NULL;
ALTER TABLE properties ALTER COLUMN photo_approved     SET NOT NULL;
ALTER TABLE properties ALTER COLUMN gps_verified       SET NOT NULL;
ALTER TABLE properties ALTER COLUMN community_verified SET NOT NULL;
ALTER TABLE properties ALTER COLUMN view_count         SET NOT NULL;
ALTER TABLE properties ALTER COLUMN save_count         SET NOT NULL;
ALTER TABLE properties ALTER COLUMN status             SET NOT NULL;
ALTER TABLE properties ALTER COLUMN is_direct_landlord      SET NOT NULL;
ALTER TABLE properties ALTER COLUMN landlord_endorsed       SET NOT NULL;
ALTER TABLE properties ALTER COLUMN caretaker_lives_on_site SET NOT NULL;
ALTER TABLE properties ALTER COLUMN deposit_months          SET NOT NULL;
ALTER TABLE properties ALTER COLUMN water_fee               SET NOT NULL;
ALTER TABLE properties ALTER COLUMN garbage_fee             SET NOT NULL;
ALTER TABLE properties ALTER COLUMN service_charge          SET NOT NULL;

-- ── Media deduplication table ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS media_hashes (
    content_hash  VARCHAR(64)   PRIMARY KEY,
    public_url    VARCHAR(512)  NOT NULL,
    r2_key        VARCHAR(255)  NOT NULL,
    content_type  VARCHAR(100)  NOT NULL,
    size_bytes    BIGINT        NOT NULL,
    created_at    TIMESTAMP     DEFAULT NOW()
);

-- ── Search indexes (pg_trgm GIN + partial btree) ─────────────────────────────
-- These are best-effort: if pg_trgm is unavailable the app still works,
-- just without trigram-accelerated full-text search. Flyway will fail if
-- the extension isn't available, so we keep the extension creation here
-- (already done in V1) and only create the indexes — which are safe to
-- re-run via IF NOT EXISTS.
CREATE INDEX IF NOT EXISTS idx_properties_title_trgm
    ON properties USING GIN (lower(title) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_properties_building_name_trgm
    ON properties USING GIN (lower(coalesce(building_name, '')) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_properties_location_trgm
    ON properties USING GIN (lower(location) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_properties_description_trgm
    ON properties USING GIN (lower(coalesce(description, '')) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_properties_nearby_facilities_trgm
    ON properties USING GIN (lower(coalesce(nearby_facilities, '')) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_property_custom_amenities_trgm
    ON property_custom_amenities USING GIN (lower(amenity) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_properties_published_available
    ON properties (available) WHERE status = 'PUBLISHED';
