-- V7: Queue of stored files waiting to be deleted.
--
-- When an account is erased, the files it uploaded (verification documents,
-- profile photo, chat attachments, listing photos) are not removed on the
-- spot. They are queued here with a delete_after date 30 days out, and a
-- nightly job removes them once that date passes. The wait gives backups and
-- in-flight requests time to settle and matches the retention period stated
-- in the privacy policy.
--
-- aws runs with ddl-auto=update, so Hibernate may already have created the
-- table from the entity; every statement therefore tolerates that.

CREATE TABLE IF NOT EXISTS pending_file_deletion (
    id           BIGSERIAL PRIMARY KEY,
    bucket       VARCHAR(10)  NOT NULL,
    storage_key  VARCHAR(512) NOT NULL,
    delete_after TIMESTAMP    NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT now()
);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_pending_file_deletion_bucket') THEN
        ALTER TABLE pending_file_deletion
            ADD CONSTRAINT ck_pending_file_deletion_bucket CHECK (bucket IN ('PUBLIC', 'PRIVATE'));
    END IF;
END $$;

-- A file is queued once, however many records pointed at it.
CREATE UNIQUE INDEX IF NOT EXISTS uq_pending_file_deletion_file
    ON pending_file_deletion (bucket, storage_key);

-- The nightly job reads everything that is due.
CREATE INDEX IF NOT EXISTS idx_pending_file_deletion_due
    ON pending_file_deletion (delete_after);
