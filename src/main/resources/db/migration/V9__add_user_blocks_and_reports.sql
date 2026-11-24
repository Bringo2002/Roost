-- V9: Blocking and reporting users.
--
-- user_blocks: one row per (blocker, blocked) pair. Blocking is one-way, so
-- two people who block each other have two rows. Chat uses it to hide a
-- blocked person's messages and to refuse messages in either direction.
--
-- user_reports: a report one user files against another. Reports are stored
-- for review only; nothing here acts on them automatically. Chat messages
-- are end-to-end encrypted, so the server cannot read them and a report
-- carries only the reason and the reporter's optional note.
--
-- Account deletion keeps the user row (it is anonymized, not removed), so
-- these foreign keys stay valid after a deletion.
--
-- aws runs with ddl-auto=update, so Hibernate may already have created these
-- tables from the entities (without the CHECK constraints below); every
-- statement therefore tolerates that, and the constraints are added in
-- separate steps that run whether or not the table was just created.

CREATE TABLE IF NOT EXISTS user_blocks (
    id         BIGSERIAL PRIMARY KEY,
    blocker_id BIGINT    NOT NULL REFERENCES users(id),
    blocked_id BIGINT    NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_user_blocks_not_self') THEN
        ALTER TABLE user_blocks
            ADD CONSTRAINT ck_user_blocks_not_self CHECK (blocker_id <> blocked_id);
    END IF;
END $$;

-- A person is blocked once per blocker; also serves lookups by blocker.
CREATE UNIQUE INDEX IF NOT EXISTS uq_user_blocks_pair
    ON user_blocks (blocker_id, blocked_id);

-- "Who has blocked this user" lookups.
CREATE INDEX IF NOT EXISTS idx_user_blocks_blocked
    ON user_blocks (blocked_id);

CREATE TABLE IF NOT EXISTS user_reports (
    id          BIGSERIAL PRIMARY KEY,
    reporter_id BIGINT       NOT NULL REFERENCES users(id),
    reported_id BIGINT       NOT NULL REFERENCES users(id),
    reason      VARCHAR(30)  NOT NULL,
    note        VARCHAR(500),
    status      VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    created_at  TIMESTAMP    NOT NULL DEFAULT now()
);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_user_reports_not_self') THEN
        ALTER TABLE user_reports
            ADD CONSTRAINT ck_user_reports_not_self CHECK (reporter_id <> reported_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_user_reports_reason') THEN
        ALTER TABLE user_reports
            ADD CONSTRAINT ck_user_reports_reason CHECK (reason IN
                ('SPAM', 'HARASSMENT', 'INAPPROPRIATE_CONTENT', 'SCAM_OR_FRAUD', 'OTHER'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_user_reports_status') THEN
        ALTER TABLE user_reports
            ADD CONSTRAINT ck_user_reports_status CHECK (status IN ('OPEN', 'RESOLVED'));
    END IF;
END $$;

-- The review queue: open reports, oldest first.
CREATE INDEX IF NOT EXISTS idx_user_reports_status_created
    ON user_reports (status, created_at);

-- Duplicate-report check and "reports against this user".
CREATE INDEX IF NOT EXISTS idx_user_reports_reporter_reported
    ON user_reports (reporter_id, reported_id, created_at);
CREATE INDEX IF NOT EXISTS idx_user_reports_reported
    ON user_reports (reported_id);
