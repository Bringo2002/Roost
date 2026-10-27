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
-- IMPORTANT: Replace the -v values with your actual domains:
--   old_base = your R2 public URL (check R2_PUBLIC_BASE_URL from Railway env vars)
--   new_base = your S3 public URL (e.g. https://roost-media-public.s3.eu-north-1.amazonaws.com)
-- ==============================================================================

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
FROM users WHERE avatar_url LIKE :'old_base' || '%';

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
FROM users WHERE avatar_url LIKE :'old_base' || '%';

\echo ''
\echo 'If the counts above look correct, type COMMIT; to finalize.'
\echo 'If anything looks wrong, type ROLLBACK; to undo all changes.'
\echo ''
