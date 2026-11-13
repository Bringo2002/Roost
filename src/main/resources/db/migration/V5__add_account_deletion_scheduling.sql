-- V5: Add columns for scheduled account deletion with grace period.
--
-- Account deletion now follows a two-phase model: the user requests
-- deletion, which sets a scheduled execution date 14 days out. During
-- the grace period the user can cancel. A nightly scheduled task
-- processes expired grace periods by running the existing anonymization
-- logic.

ALTER TABLE users ADD COLUMN IF NOT EXISTS deletion_requested_at TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS deletion_scheduled_at TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS deletion_reason VARCHAR(500);
