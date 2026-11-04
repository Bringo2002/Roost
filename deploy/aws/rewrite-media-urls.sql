-- ==============================================================================
-- Roost Media URL Rewrite: R2 → S3
-- ==============================================================================
-- Run this AFTER:
--   1. Database has been restored from Railway into RDS
--   2. Media files have been copied from R2 to S3 (rclone sync)
--
-- Usage (on the EC2 server):
--   PGPASSWORD='<RDS_PASSWORD>' psql \
--     -h <RDS_ENDPOINT> -U roost_admin -d roost \
--     -v old_base='https://YOUR-OLD-R2-DOMAIN.r2.dev' \
--     -v new_base='https://YOUR-S3-BUCKET.s3.eu-north-1.amazonaws.com' \
--     -f rewrite-media-urls.sql
--
-- DRY RUN BY DEFAULT: the script prints before/after counts and then ROLLS
-- BACK, so nothing is saved. Review the counts, then run the same command
-- again with  -v commit=1  to save the changes.
-- (psql exits at the end of a -f file, which discards any open transaction;
-- an earlier version of this script relied on typing COMMIT; by hand and so
-- never saved anything when run with -f.)
--
-- Columns rewritten:
--   properties.image_url, properties.video_url
--   property_image_urls.image_url, property_document_urls.document_url
--   users.avatar_url
--   media_hashes.public_url               (upload dedup lookups)
--   move_in_inspections.tenant_signature / landlord_signature
--   move_in_inspections.rooms_json        (photo URLs embedded in JSON text)
--
-- IMPORTANT: Replace the -v values with your actual domains:
--   old_base = your R2 public URL (check R2_PUBLIC_BASE_URL from Railway env vars)
--   new_base = your S3 public URL (e.g. https://roost-media-public.s3.eu-north-1.amazonaws.com)
-- ==============================================================================

\set ON_ERROR_STOP on

-- Show what we're replacing
\echo ''
\echo '================================================================='
\echo ' Media URL Rewrite: R2 → S3'
\echo ' Old base: ' :old_base
\echo ' New base: ' :new_base
\echo '================================================================='
\echo ''

-- Preview counts BEFORE
\echo '--- BEFORE: Rows containing the old URL ---'
SELECT 'properties.image_url' AS column_name,
       COUNT(*) AS matching_rows
FROM properties WHERE image_url LIKE :'old_base' || '%'
UNION ALL
SELECT 'properties.video_url',
       COUNT(*)
FROM properties WHERE video_url LIKE :'old_base' || '%'
UNION ALL
SELECT 'property_image_urls.image_url',
       COUNT(*)
FROM property_image_urls WHERE image_url LIKE :'old_base' || '%'
UNION ALL
SELECT 'users.avatar_url',
       COUNT(*)
FROM users WHERE avatar_url LIKE :'old_base' || '%'
UNION ALL
SELECT 'property_document_urls.document_url',
       COUNT(*)
FROM property_document_urls WHERE document_url LIKE :'old_base' || '%'
UNION ALL
SELECT 'media_hashes.public_url',
       COUNT(*)
FROM media_hashes WHERE public_url LIKE :'old_base' || '%'
UNION ALL
SELECT 'move_in_inspections.tenant_signature',
       COUNT(*)
FROM move_in_inspections WHERE tenant_signature LIKE :'old_base' || '%'
UNION ALL
SELECT 'move_in_inspections.landlord_signature',
       COUNT(*)
FROM move_in_inspections WHERE landlord_signature LIKE :'old_base' || '%'
UNION ALL
SELECT 'move_in_inspections.rooms_json',
       COUNT(*)
FROM move_in_inspections WHERE rooms_json LIKE '%' || :'old_base' || '%';

\echo ''
\echo 'Starting transaction...'

BEGIN;

-- 1. Properties cover photo (single URL)
UPDATE properties
SET image_url = REPLACE(image_url, :'old_base', :'new_base')
WHERE image_url LIKE :'old_base' || '%';

\echo 'Updated properties.image_url:'
SELECT COUNT(*) AS rows_updated FROM properties WHERE image_url LIKE :'new_base' || '%';

-- 2. Properties video (single URL)
UPDATE properties
SET video_url = REPLACE(video_url, :'old_base', :'new_base')
WHERE video_url LIKE :'old_base' || '%';

\echo 'Updated properties.video_url:'
SELECT COUNT(*) AS rows_updated FROM properties WHERE video_url LIKE :'new_base' || '%';

-- 3. Property image gallery (collection table, multiple rows per property)
UPDATE property_image_urls
SET image_url = REPLACE(image_url, :'old_base', :'new_base')
WHERE image_url LIKE :'old_base' || '%';

\echo 'Updated property_image_urls.image_url:'
SELECT COUNT(*) AS rows_updated FROM property_image_urls WHERE image_url LIKE :'new_base' || '%';

-- 4. User avatars
UPDATE users
SET avatar_url = REPLACE(avatar_url, :'old_base', :'new_base')
WHERE avatar_url LIKE :'old_base' || '%';

\echo 'Updated users.avatar_url:'
SELECT COUNT(*) AS rows_updated FROM users WHERE avatar_url LIKE :'new_base' || '%';

-- 5. Property documents (collection table)
UPDATE property_document_urls
SET document_url = REPLACE(document_url, :'old_base', :'new_base')
WHERE document_url LIKE :'old_base' || '%';

\echo 'Updated property_document_urls.document_url:'
SELECT COUNT(*) AS rows_updated FROM property_document_urls WHERE document_url LIKE :'new_base' || '%';

-- 6. Upload dedup table: a dedup hit returns public_url straight to the client
UPDATE media_hashes
SET public_url = REPLACE(public_url, :'old_base', :'new_base')
WHERE public_url LIKE :'old_base' || '%';

\echo 'Updated media_hashes.public_url:'
SELECT COUNT(*) AS rows_updated FROM media_hashes WHERE public_url LIKE :'new_base' || '%';

-- 7. Move-in inspection signatures (single URLs)
UPDATE move_in_inspections
SET tenant_signature = REPLACE(tenant_signature, :'old_base', :'new_base')
WHERE tenant_signature LIKE :'old_base' || '%';

UPDATE move_in_inspections
SET landlord_signature = REPLACE(landlord_signature, :'old_base', :'new_base')
WHERE landlord_signature LIKE :'old_base' || '%';

\echo 'Updated move_in_inspections signatures:'
SELECT COUNT(*) AS rows_with_new_signature FROM move_in_inspections
WHERE tenant_signature LIKE :'new_base' || '%' OR landlord_signature LIKE :'new_base' || '%';

-- 8. Move-in inspection photos live inside the rooms_json text, so match anywhere
UPDATE move_in_inspections
SET rooms_json = REPLACE(rooms_json, :'old_base', :'new_base')
WHERE rooms_json LIKE '%' || :'old_base' || '%';

\echo 'Updated move_in_inspections.rooms_json:'
SELECT COUNT(*) AS rows_updated FROM move_in_inspections WHERE rooms_json LIKE '%' || :'new_base' || '%';

-- Sanity check: nothing should still reference the old base
\echo ''
\echo '--- AFTER: Remaining rows with old URL (should all be 0) ---'
SELECT 'properties.image_url' AS column_name,
       COUNT(*) AS remaining
FROM properties WHERE image_url LIKE :'old_base' || '%'
UNION ALL
SELECT 'properties.video_url',
       COUNT(*)
FROM properties WHERE video_url LIKE :'old_base' || '%'
UNION ALL
SELECT 'property_image_urls.image_url',
       COUNT(*)
FROM property_image_urls WHERE image_url LIKE :'old_base' || '%'
UNION ALL
SELECT 'users.avatar_url',
       COUNT(*)
FROM users WHERE avatar_url LIKE :'old_base' || '%'
UNION ALL
SELECT 'property_document_urls.document_url',
       COUNT(*)
FROM property_document_urls WHERE document_url LIKE :'old_base' || '%'
UNION ALL
SELECT 'media_hashes.public_url',
       COUNT(*)
FROM media_hashes WHERE public_url LIKE :'old_base' || '%'
UNION ALL
SELECT 'move_in_inspections.tenant_signature',
       COUNT(*)
FROM move_in_inspections WHERE tenant_signature LIKE :'old_base' || '%'
UNION ALL
SELECT 'move_in_inspections.landlord_signature',
       COUNT(*)
FROM move_in_inspections WHERE landlord_signature LIKE :'old_base' || '%'
UNION ALL
SELECT 'move_in_inspections.rooms_json',
       COUNT(*)
FROM move_in_inspections WHERE rooms_json LIKE '%' || :'old_base' || '%';

\echo ''
\if :{?commit}
  COMMIT;
  \echo 'COMMITTED: changes saved.'
\else
  ROLLBACK;
  \echo 'DRY RUN: nothing was saved. If the counts above look right, re-run with -v commit=1'
\endif
\echo ''
