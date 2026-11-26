-- V11: Major roads, for "how far is this listing from the main road".
--
-- major_road_segment: motorways, trunk and primary roads (and any other
-- classes a country's import chooses to keep), cut into short straight
-- segments from OpenStreetMap's country extracts. Rows are loaded by the
-- import script, one country at a time, and never edited by the app.
--
-- Each segment carries its own bounding box, so "which roads are near this
-- point" is a plain range query on btree indexes and no map extension is
-- needed. A segment needs a name or a reference (such as A2) to be worth
-- showing; roads with neither are not stored.
--
-- major_road_import: one row per country, saying when its roads were last
-- loaded and from what, so stale data is visible.
--
-- Nothing here is mapped by a Hibernate entity: the app reads these tables
-- with plain SQL, so aws's ddl-auto=update leaves them alone.
--
-- Data from OpenStreetMap is © OpenStreetMap contributors (ODbL).

CREATE TABLE IF NOT EXISTS major_road_segment (
    id           BIGSERIAL PRIMARY KEY,
    country_code CHAR(2)          NOT NULL,
    osm_way_id   BIGINT           NOT NULL,
    name         VARCHAR(255),
    ref          VARCHAR(64),
    road_class   VARCHAR(16)      NOT NULL,
    start_lat    DOUBLE PRECISION NOT NULL,
    start_lng    DOUBLE PRECISION NOT NULL,
    end_lat      DOUBLE PRECISION NOT NULL,
    end_lng      DOUBLE PRECISION NOT NULL,
    min_lat      DOUBLE PRECISION NOT NULL,
    max_lat      DOUBLE PRECISION NOT NULL,
    min_lng      DOUBLE PRECISION NOT NULL,
    max_lng      DOUBLE PRECISION NOT NULL,
    CONSTRAINT ck_major_road_segment_country
        CHECK (country_code ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_major_road_segment_class
        CHECK (road_class IN ('motorway', 'trunk', 'primary', 'secondary')),
    CONSTRAINT ck_major_road_segment_labelled
        CHECK (COALESCE(btrim(name), '') <> '' OR COALESCE(btrim(ref), '') <> ''),
    CONSTRAINT ck_major_road_segment_lat
        CHECK (start_lat BETWEEN -90 AND 90 AND end_lat BETWEEN -90 AND 90
               AND min_lat BETWEEN -90 AND 90 AND max_lat BETWEEN -90 AND 90),
    CONSTRAINT ck_major_road_segment_lng
        CHECK (start_lng BETWEEN -180 AND 180 AND end_lng BETWEEN -180 AND 180
               AND min_lng BETWEEN -180 AND 180 AND max_lng BETWEEN -180 AND 180),
    CONSTRAINT ck_major_road_segment_box
        CHECK (min_lat <= max_lat AND min_lng <= max_lng)
);

-- "Segments whose box overlaps this area", the query behind the nearest-road lookup.
CREATE INDEX IF NOT EXISTS idx_major_road_segment_lat
    ON major_road_segment (country_code, min_lat, max_lat);
CREATE INDEX IF NOT EXISTS idx_major_road_segment_lng
    ON major_road_segment (country_code, min_lng, max_lng);

-- Replacing a country's roads on re-import, and tracing a segment back to its way.
CREATE INDEX IF NOT EXISTS idx_major_road_segment_way
    ON major_road_segment (country_code, osm_way_id);

CREATE TABLE IF NOT EXISTS major_road_import (
    country_code     CHAR(2)      PRIMARY KEY,
    imported_at      TIMESTAMP    NOT NULL DEFAULT now(),
    source           VARCHAR(255) NOT NULL,
    source_timestamp TIMESTAMP,
    road_classes     VARCHAR(100) NOT NULL,
    segment_count    INTEGER      NOT NULL,
    CONSTRAINT ck_major_road_import_country CHECK (country_code ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_major_road_import_count CHECK (segment_count >= 0)
);
