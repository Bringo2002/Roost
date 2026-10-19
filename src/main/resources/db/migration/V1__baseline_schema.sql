-- V1: Baseline schema — columns created by Hibernate ddl-auto on first deploy.
-- This migration represents the initial state of the database before any
-- DatabaseSchemaMigrator patches were applied. It uses IF NOT EXISTS / IF EXISTS
-- guards everywhere so it is safe to apply against an existing live database
-- that already has these objects (Flyway's baseline strategy).
--
-- NOTE: Flyway is introduced via M9. The existing production database is
-- baselined at V1 using `flyway baseline` so this script is NOT re-executed
-- against it; only V2+ scripts run. For fresh environments this creates the
-- full initial schema from scratch.

-- ── Extensions ────────────────────────────────────────────────────────────────
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ── Users ────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255),
    email       VARCHAR(255) UNIQUE,
    phone       VARCHAR(255),
    password    VARCHAR(255),
    role        VARCHAR(50) CHECK (role IN ('TENANT', 'LANDLORD', 'ADMIN')),
    avatar_url  VARCHAR(512),
    fcm_token   VARCHAR(512),
    created_at  TIMESTAMP DEFAULT NOW()
);

-- ── Properties ───────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS properties (
    id              BIGSERIAL PRIMARY KEY,
    title           VARCHAR(255),
    description     TEXT,
    location        VARCHAR(255),
    price           DOUBLE PRECISION,
    bedrooms        INT,
    available       BOOLEAN DEFAULT TRUE,
    image_urls      TEXT,
    owner_id        BIGINT REFERENCES users(id),
    created_at      TIMESTAMP DEFAULT NOW()
);

-- ── Reviews ──────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS reviews (
    id          BIGSERIAL PRIMARY KEY,
    rating      INT,
    comment     TEXT,
    property_id BIGINT REFERENCES properties(id),
    reviewer_id BIGINT REFERENCES users(id),
    created_at  TIMESTAMP DEFAULT NOW()
);

-- ── Property reports ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS property_reports (
    id          BIGSERIAL PRIMARY KEY,
    property_id BIGINT REFERENCES properties(id),
    reporter_id BIGINT REFERENCES users(id),
    reason      VARCHAR(255),
    created_at  TIMESTAMP DEFAULT NOW()
);

-- ── Community checks ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS community_checks (
    id          BIGSERIAL PRIMARY KEY,
    property_id BIGINT REFERENCES properties(id),
    checker_id  BIGINT REFERENCES users(id),
    status      VARCHAR(50),
    created_at  TIMESTAMP DEFAULT NOW()
);

-- ── Applications ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS applications (
    id          BIGSERIAL PRIMARY KEY,
    property_id BIGINT REFERENCES properties(id),
    applicant_id BIGINT REFERENCES users(id),
    status      VARCHAR(50),
    created_at  TIMESTAMP DEFAULT NOW()
);

-- ── Messages ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS messages (
    id          BIGSERIAL PRIMARY KEY,
    content     TEXT,
    sender_id   BIGINT REFERENCES users(id),
    recipient_id BIGINT REFERENCES users(id),
    property_id BIGINT REFERENCES properties(id),
    read        BOOLEAN DEFAULT FALSE,
    created_at  TIMESTAMP DEFAULT NOW()
);

-- ── Listing events ───────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS listing_events (
    id          BIGSERIAL PRIMARY KEY,
    property_id BIGINT REFERENCES properties(id),
    user_id     BIGINT REFERENCES users(id),
    event_type  VARCHAR(50),
    created_at  TIMESTAMP DEFAULT NOW()
);

-- ── Custom amenities ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS property_custom_amenities (
    property_id BIGINT REFERENCES properties(id),
    amenity     VARCHAR(255)
);

-- ── Saved properties (many-to-many) ─────────────────────────────────────────
CREATE TABLE IF NOT EXISTS saved_properties (
    user_id     BIGINT REFERENCES users(id),
    property_id BIGINT REFERENCES properties(id),
    PRIMARY KEY (user_id, property_id)
);
