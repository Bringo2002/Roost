-- V12: Formalize messaging schema
--
-- The messages table in V1 has only the original 7 columns. All other
-- columns (nonce, reply_to_message_id, edited, edited_at, attachment_*)
-- and the message_reactions / chat_visibility tables were added by
-- Hibernate's ddl-auto=update and have no migration history. This
-- migration formalizes them so the schema is fully version-controlled.
--
-- Every statement is idempotent (ADD COLUMN IF NOT EXISTS, CREATE TABLE
-- IF NOT EXISTS) so it's safe regardless of what Hibernate already
-- created.

-- ── Messages: add columns that V1 didn't have ──────────────────────────

ALTER TABLE messages ADD COLUMN IF NOT EXISTS nonce TEXT;
ALTER TABLE messages ADD COLUMN IF NOT EXISTS reply_to_message_id BIGINT;
ALTER TABLE messages ADD COLUMN IF NOT EXISTS edited BOOLEAN DEFAULT false;
ALTER TABLE messages ADD COLUMN IF NOT EXISTS edited_at TIMESTAMP;
ALTER TABLE messages ADD COLUMN IF NOT EXISTS attachment_storage_key TEXT;
ALTER TABLE messages ADD COLUMN IF NOT EXISTS attachment_nonce TEXT;
ALTER TABLE messages ADD COLUMN IF NOT EXISTS attachment_meta TEXT;
ALTER TABLE messages ADD COLUMN IF NOT EXISTS attachment_meta_nonce TEXT;

-- Hibernate maps Message.timestamp (no @Column name override) to a
-- column called "timestamp".  V1 created "created_at" for a different
-- purpose; the two coexist harmlessly.
ALTER TABLE messages ADD COLUMN IF NOT EXISTS "timestamp" TIMESTAMP;
ALTER TABLE messages ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;

-- ── Message reactions ──────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS message_reactions (
    id          BIGSERIAL PRIMARY KEY,
    message_id  BIGINT NOT NULL REFERENCES messages(id),
    user_id     BIGINT NOT NULL REFERENCES users(id),
    emoji       VARCHAR(10) NOT NULL,
    UNIQUE (message_id, user_id, emoji)
);

-- ── Chat visibility ───────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS chat_visibility (
    id                          BIGSERIAL PRIMARY KEY,
    user_id                     BIGINT NOT NULL REFERENCES users(id),
    partner_id                  BIGINT NOT NULL REFERENCES users(id),
    cleared_before_message_id   BIGINT,
    hidden_since_message_id     BIGINT,
    UNIQUE (user_id, partner_id)
);

-- ── Performance indexes ───────────────────────────────────────────────

-- Covers all chat history queries (sender + recipient filtered by id).
CREATE INDEX IF NOT EXISTS idx_messages_sender_recipient_id
    ON messages (sender_id, recipient_id, id DESC);

-- Covers unread-count queries (partial index: only unread rows).
CREATE INDEX IF NOT EXISTS idx_messages_recipient_unread
    ON messages (recipient_id) WHERE read = false;

-- Covers batch-loading reactions for a message page.
CREATE INDEX IF NOT EXISTS idx_message_reactions_message
    ON message_reactions (message_id);

-- Covers visibility lookups by (user, partner).
CREATE INDEX IF NOT EXISTS idx_chat_visibility_user_partner
    ON chat_visibility (user_id, partner_id);
