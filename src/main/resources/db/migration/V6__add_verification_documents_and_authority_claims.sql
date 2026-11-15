-- V6: verification documents, authority claims and an audit trail.
--
-- Listers submit identity documents (national ID, passport) once, and per
-- listing they claim authority over the property (owner, or agent for the
-- owner) with supporting evidence. Files live in a private bucket; the
-- database holds only an opaque storage key, never a URL. A reviewer
-- decides every claim and every document, and every decision and every
-- admin view of a document is recorded in an append-only audit table.
--
-- Additive only: new tables and two new columns with defaults, so the
-- previous application version keeps working if a rollback is needed.
--
-- Safe to run in any order relative to Hibernate's ddl-auto=update. If the
-- tables already exist (Hibernate created them first), CREATE TABLE IF NOT
-- EXISTS skips them, so every rule is added afterwards as a named constraint
-- or index that is created only when missing.

-- ── Tables ───────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS authority_claim (
    id              BIGSERIAL PRIMARY KEY,
    property_id     BIGINT      NOT NULL REFERENCES properties(id),
    claimant_id     BIGINT      NOT NULL REFERENCES users(id),
    claim_type      VARCHAR(30) NOT NULL,
    owner_name      VARCHAR(255),
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    submitted_at    TIMESTAMP   NOT NULL DEFAULT NOW(),
    decided_at      TIMESTAMP,
    decided_by      BIGINT REFERENCES users(id),
    decision_reason VARCHAR(1000),
    expires_at      TIMESTAMP
);

CREATE TABLE IF NOT EXISTS verification_document (
    id            BIGSERIAL PRIMARY KEY,
    owner_user_id BIGINT       NOT NULL REFERENCES users(id),
    claim_id      BIGINT REFERENCES authority_claim(id),
    doc_type      VARCHAR(30)  NOT NULL,
    storage_key   VARCHAR(255) NOT NULL,
    content_type  VARCHAR(100) NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    sha256        VARCHAR(64)  NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'SUBMITTED',
    uploaded_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    reviewed_by   BIGINT REFERENCES users(id),
    reviewed_at   TIMESTAMP,
    reject_reason VARCHAR(1000),
    ai_opinion    TEXT,
    deleted_at    TIMESTAMP
);

CREATE TABLE IF NOT EXISTS verification_audit_event (
    id          BIGSERIAL PRIMARY KEY,
    actor_id    BIGINT REFERENCES users(id),   -- null for system actions such as expiry
    action      VARCHAR(30) NOT NULL,
    document_id BIGINT REFERENCES verification_document(id),
    claim_id    BIGINT REFERENCES authority_claim(id),
    note        VARCHAR(1000),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ── Rules ────────────────────────────────────────────────────────────────────
DO $$
DECLARE
    rule record;
BEGIN
    FOR rule IN
        SELECT * FROM (VALUES
            ('authority_claim', 'ck_authority_claim_type',
                $c$CHECK (claim_type IN ('OWNER', 'AGENT_FOR_OWNER'))$c$),
            ('authority_claim', 'ck_authority_claim_status',
                $c$CHECK (status IN ('PENDING', 'CONFIRMED', 'REJECTED', 'REVOKED'))$c$),
            -- An agent must name the owner they act for.
            ('authority_claim', 'ck_authority_claim_agent_names_owner',
                $c$CHECK (claim_type <> 'AGENT_FOR_OWNER' OR (owner_name IS NOT NULL AND owner_name <> ''))$c$),
            -- Anything other than PENDING has been decided.
            ('authority_claim', 'ck_authority_claim_decided',
                $c$CHECK (status = 'PENDING' OR decided_at IS NOT NULL)$c$),
            ('verification_document', 'ck_verification_document_type',
                $c$CHECK (doc_type IN ('NATIONAL_ID_FRONT', 'NATIONAL_ID_BACK', 'PASSPORT',
                    'TITLE_DEED', 'LAND_RATES_RECEIPT', 'UTILITY_BILL',
                    'LETTER_OF_AUTHORITY', 'TENANCY_AGREEMENT', 'OTHER'))$c$),
            ('verification_document', 'ck_verification_document_size',
                $c$CHECK (size_bytes > 0)$c$),
            ('verification_document', 'ck_verification_document_status',
                $c$CHECK (status IN ('SUBMITTED', 'ACCEPTED', 'REJECTED'))$c$),
            -- Identity documents belong to the person; property evidence belongs to a claim.
            ('verification_document', 'ck_verification_document_scope',
                $c$CHECK ((doc_type IN ('NATIONAL_ID_FRONT', 'NATIONAL_ID_BACK', 'PASSPORT')) = (claim_id IS NULL))$c$),
            ('verification_audit_event', 'ck_verification_audit_event_action',
                $c$CHECK (action IN ('SUBMITTED', 'ACCEPTED', 'REJECTED', 'CONFIRMED', 'REVOKED',
                    'EXPIRED', 'DISPUTED', 'VIEWED', 'DELETED'))$c$),
            ('verification_audit_event', 'ck_verification_audit_event_subject',
                $c$CHECK (document_id IS NOT NULL OR claim_id IS NOT NULL)$c$)
        ) AS t(tbl, name, definition)
    LOOP
        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conname = rule.name AND conrelid = rule.tbl::regclass
        ) THEN
            EXECUTE format('ALTER TABLE %I ADD CONSTRAINT %I %s', rule.tbl, rule.name, rule.definition);
        END IF;
    END LOOP;
END
$$;

-- One open claim per listing. A rejected or revoked claim does not block a new one.
CREATE UNIQUE INDEX IF NOT EXISTS uq_authority_claim_open_per_property
    ON authority_claim (property_id) WHERE status IN ('PENDING', 'CONFIRMED');
CREATE UNIQUE INDEX IF NOT EXISTS uq_verification_document_storage_key
    ON verification_document (storage_key);

CREATE INDEX IF NOT EXISTS idx_authority_claim_claimant ON authority_claim (claimant_id);
CREATE INDEX IF NOT EXISTS idx_authority_claim_status   ON authority_claim (status);
CREATE INDEX IF NOT EXISTS idx_verification_document_owner ON verification_document (owner_user_id);
CREATE INDEX IF NOT EXISTS idx_verification_document_claim ON verification_document (claim_id);
-- Finds files whose retention period has run out.
CREATE INDEX IF NOT EXISTS idx_verification_document_retention
    ON verification_document (reviewed_at) WHERE deleted_at IS NULL AND reviewed_at IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_verification_audit_event_document ON verification_audit_event (document_id);
CREATE INDEX IF NOT EXISTS idx_verification_audit_event_claim    ON verification_audit_event (claim_id);

-- ── Audit trail is append-only ───────────────────────────────────────────────
CREATE OR REPLACE FUNCTION verification_audit_event_append_only() RETURNS trigger AS $fn$
BEGIN
    RAISE EXCEPTION 'verification_audit_event is append-only';
END;
$fn$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_verification_audit_event_append_only ON verification_audit_event;
CREATE TRIGGER trg_verification_audit_event_append_only
    BEFORE UPDATE OR DELETE ON verification_audit_event
    FOR EACH ROW EXECUTE FUNCTION verification_audit_event_append_only();

-- ── Denormalised status columns ──────────────────────────────────────────────
-- Written only by the review service, in the same transaction as the audit
-- event for the decision, so the badge code can read them without a join.
ALTER TABLE users ADD COLUMN IF NOT EXISTS identity_status VARCHAR(20) NOT NULL DEFAULT 'NOT_SUBMITTED'
    CHECK (identity_status IN ('NOT_SUBMITTED', 'PENDING', 'VERIFIED', 'REJECTED'));
ALTER TABLE properties ADD COLUMN IF NOT EXISTS authority_status VARCHAR(20) NOT NULL DEFAULT 'NOT_ESTABLISHED'
    CHECK (authority_status IN ('NOT_ESTABLISHED', 'PENDING', 'CONFIRMED', 'REJECTED'));
