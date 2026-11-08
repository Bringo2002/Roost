-- V3: Add a partial B-tree index covering the latitude/longitude columns
-- used by every nearby/distance-sorted query.
--
-- Every spatial query in PropertyRepository pre-filters on
--   status = 'PUBLISHED' AND available = true
--   AND latitude IS NOT NULL AND longitude IS NOT NULL
-- followed by either a bounding-box BETWEEN or trig-based distance calc.
-- Without an index the planner must sequential-scan the full properties
-- table for each request.  This partial index restricts the index to
-- exactly the rows those queries care about, keeping it small and fast.
--
-- A composite (latitude, longitude) B-tree index lets PostgreSQL range-
-- scan the bounding-box BETWEEN predicates efficiently.  The partial
-- WHERE clause matches the invariant predicates shared by findNearby,
-- filterPropertiesSortedByDistance, findByStatusPublishedPagedSortedByDistance,
-- and the comparable-price distance queries.

-- The index below needs these two columns. V1/V2 don't create them (Hibernate
-- added them in existing databases), so a brand-new database would fail here
-- without this. IF NOT EXISTS makes it a no-op wherever they already exist.
ALTER TABLE properties ADD COLUMN IF NOT EXISTS latitude  DOUBLE PRECISION;
ALTER TABLE properties ADD COLUMN IF NOT EXISTS longitude DOUBLE PRECISION;

CREATE INDEX IF NOT EXISTS idx_properties_geo_bbox
    ON properties (latitude, longitude)
    WHERE status = 'PUBLISHED' AND available = true
      AND latitude IS NOT NULL AND longitude IS NOT NULL;
