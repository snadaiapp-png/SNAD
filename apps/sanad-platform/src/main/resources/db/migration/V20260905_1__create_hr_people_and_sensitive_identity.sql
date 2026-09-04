-- ============================================================
-- HRM-G0 WS2 Task 1A GREEN — Person Identity Schema
-- ------------------------------------------------------------
-- Creates three canonical tables backing the HR Person identity
-- model:
--
--   hr_people              — core Person record (1:1 to users)
--   hr_person_private     — PII fields (DoB, nationality, etc.)
--   hr_person_identifiers — encrypted identity documents
--
-- Design contracts enforced at the DB boundary:
--
--   1. Person ↔ User uniqueness
--      At most one non-null user_id per tenant.
--      Implemented via partial UNIQUE INDEX on (tenant_id, user_id)
--      WHERE user_id IS NOT NULL.
--      SQLSTATE 23505 on duplicate.
--
--   2. Cross-tenant User congruence
--      hr_people.tenant_id == users.tenant_id (when user_id is set).
--      Implemented via composite FK
--        (tenant_id, user_id) REFERENCES users(tenant_id, id)
--      leveraging the pre-existing uk_users_tenant_id unique
--      constraint added in V5. PostgreSQL skips composite FK
--      validation when any column is NULL — so a Person without
--      a User is allowed (PERSON-02). When user_id is non-NULL,
--      the composite FK requires the user to belong to the same
--      tenant. SQLSTATE 23503 on violation.
--
--   3. Cross-tenant Person private congruence
--      hr_person_private.tenant_id == hr_people.tenant_id.
--      Implemented via composite FK
--        (tenant_id, person_id) REFERENCES hr_people(tenant_id, id)
--      backed by uq_hr_people_tenant_id. SQLSTATE 23503 on violation.
--
--   4. Cross-tenant Identifier congruence
--      hr_person_identifiers.tenant_id == hr_people.tenant_id.
--      Implemented via composite FK
--        (tenant_id, person_id) REFERENCES hr_people(tenant_id, id).
--      SQLSTATE 23503 on violation.
--
--   5. ACTIVE identifier uniqueness with NULL-issuer equivalence
--      UNIQUE INDEX on
--        (tenant_id, identifier_type, issuing_country_code, blind_index)
--      WHERE status = 'ACTIVE'
--      NULLS NOT DISTINCT — so two NULL issuing_country_codes are
--      treated as equal for uniqueness (PERSON-05).
--      EXPIRED / REVOKED historical identifiers are excluded from
--      the partial index, so they do NOT participate in uniqueness
--      enforcement (preserves historical audit trail).
--      SQLSTATE 23505 on duplicate ACTIVE identifier.
--
--   6. RLS fail-closed on all three tables
--      ENABLE ROW LEVEL SECURITY + FORCE ROW LEVEL SECURITY.
--      Policy:
--        USING (tenant_id::text = current_setting('app.tenant_id', true))
--        WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true))
--      When app.tenant_id is unset (NULL), the comparison evaluates
--      to NULL (not true) → fail-closed.
--      Owner (sanad) is subject to FORCE RLS — does not bypass.
--
-- Canonical column names (per approved plan):
--   identifier_ciphertext        — AES-256-GCM ciphertext (base64)
--   blind_index                  — HMAC-SHA-256 blind index (hex)
--   encryption_key_version       — active ciphertext key version
--   blind_index_key_version      — blind index key version (separate)
--
-- PostgreSQL 15+ required for NULLS NOT DISTINCT.
-- (Supabase prod is on PG 17 — confirmed.)
-- ============================================================

-- ------------------------------------------------------------
-- 1. hr_people — core Person record
-- ------------------------------------------------------------
CREATE TABLE hr_people (
    id              UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID           NOT NULL,
    user_id         UUID,                          -- nullable (Person may have no User)
    first_name      VARCHAR(200)   NOT NULL,
    middle_name     VARCHAR(200),
    last_name       VARCHAR(200)   NOT NULL,
    display_name    VARCHAR(400)   NOT NULL,
    version         INTEGER        NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_hr_people PRIMARY KEY (id),
    CONSTRAINT fk_hr_people_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    -- Composite FK enforcing tenant congruence between hr_people and users.
    -- Uses pre-existing uk_users_tenant_id unique constraint (V5).
    -- PostgreSQL skips FK validation when user_id IS NULL, so a Person
    -- without a User is allowed.
    CONSTRAINT fk_hr_people_tenant_user
        FOREIGN KEY (tenant_id, user_id) REFERENCES users (tenant_id, id),
    -- Unique constraint on (tenant_id, id) to permit composite FK from
    -- hr_person_private and hr_person_identifiers (cross-tenant congruence).
    CONSTRAINT uq_hr_people_tenant_id UNIQUE (tenant_id, id)
);

-- Partial UNIQUE INDEX: at most one non-null user_id per tenant.
-- NULL user_ids are excluded (Person may have no User).
CREATE UNIQUE INDEX uq_hr_people_tenant_user
    ON hr_people (tenant_id, user_id)
    WHERE user_id IS NOT NULL;

-- Lookup indexes
CREATE INDEX idx_hr_people_tenant ON hr_people (tenant_id);
CREATE INDEX idx_hr_people_user ON hr_people (user_id) WHERE user_id IS NOT NULL;
CREATE INDEX idx_hr_people_tenant_status ON hr_people (tenant_id);

-- ------------------------------------------------------------
-- 2. hr_person_private — PII fields (1:1 with hr_people)
-- ------------------------------------------------------------
CREATE TABLE hr_person_private (
    person_id                 UUID           NOT NULL,
    tenant_id                 UUID           NOT NULL,
    date_of_birth             DATE,
    nationality_country_code  CHAR(2),
    marital_status            VARCHAR(30),
    version                   INTEGER        NOT NULL DEFAULT 0,
    updated_at                TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_hr_person_private PRIMARY KEY (person_id),
    -- Composite FK enforcing tenant congruence:
    -- hr_person_private.tenant_id == hr_people.tenant_id.
    -- A Person from Tenant A cannot have a private row under Tenant B
    -- because (tenantB, personInA) does not exist in hr_people.
    CONSTRAINT fk_hr_person_private_person
        FOREIGN KEY (tenant_id, person_id) REFERENCES hr_people (tenant_id, id),
    CONSTRAINT fk_hr_person_private_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_hr_person_private_nationality
        FOREIGN KEY (nationality_country_code) REFERENCES platform_countries (country_code),
    CONSTRAINT ck_hr_person_private_marital_status
        CHECK (marital_status IS NULL OR marital_status IN
            ('SINGLE', 'MARRIED', 'DIVORCED', 'WIDOWED', 'SEPARATED'))
);

CREATE INDEX idx_hr_person_private_tenant ON hr_person_private (tenant_id);

-- ------------------------------------------------------------
-- 3. hr_person_identifiers — encrypted identity documents
-- ------------------------------------------------------------
CREATE TABLE hr_person_identifiers (
    id                          UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id                   UUID           NOT NULL,
    person_id                   UUID           NOT NULL,
    identifier_type             VARCHAR(50)    NOT NULL,
    issuing_country_code        CHAR(2),
    -- Canonical ciphertext + blind index columns (per approved plan)
    identifier_ciphertext       VARCHAR(1024)  NOT NULL,
    blind_index                 VARCHAR(128)   NOT NULL,
    encryption_key_version     VARCHAR(40)    NOT NULL,
    blind_index_key_version     VARCHAR(40)    NOT NULL,
    status                      VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_at                  TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_hr_person_identifiers PRIMARY KEY (id),
    -- Composite FK enforcing tenant congruence:
    -- hr_person_identifiers.tenant_id == hr_people.tenant_id.
    CONSTRAINT fk_hr_person_identifiers_person
        FOREIGN KEY (tenant_id, person_id) REFERENCES hr_people (tenant_id, id),
    CONSTRAINT fk_hr_person_identifiers_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_hr_person_identifiers_issuing_country
        FOREIGN KEY (issuing_country_code) REFERENCES platform_countries (country_code),
    CONSTRAINT ck_hr_person_identifiers_type
        CHECK (identifier_type IN
            ('NATIONAL_ID', 'PASSPORT', 'IQAMA', 'RESIDENCE_PERMIT',
             'VISA', 'DRIVING_LICENSE', 'BIRTH_CERTIFICATE', 'OTHER')),
    CONSTRAINT ck_hr_person_identifiers_status
        CHECK (status IN ('ACTIVE', 'EXPIRED', 'REVOKED', 'PENDING_VERIFICATION'))
);

-- Partial UNIQUE INDEX: ACTIVE identifier uniqueness with NULL-issuer equivalence.
-- NULLS NOT DISTINCT ensures two NULL issuing_country_codes are treated as equal
-- for uniqueness purposes (PERSON-05).
-- EXPIRED / REVOKED identifiers are excluded — historical audit trail preserved.
CREATE UNIQUE INDEX uq_hr_person_identifiers_active
    ON hr_person_identifiers (tenant_id, identifier_type, issuing_country_code, blind_index)
    NULLS NOT DISTINCT
    WHERE (status = 'ACTIVE');

-- Lookup indexes
CREATE INDEX idx_hr_person_identifiers_tenant ON hr_person_identifiers (tenant_id);
CREATE INDEX idx_hr_person_identifiers_person ON hr_person_identifiers (tenant_id, person_id);
CREATE INDEX idx_hr_person_identifiers_blind ON hr_person_identifiers (tenant_id, blind_index);

-- ------------------------------------------------------------
-- 4. RLS: ENABLE + FORCE + fail-closed policy on all 3 tables
-- ------------------------------------------------------------
ALTER TABLE hr_people ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_people FORCE ROW LEVEL SECURITY;
CREATE POLICY hr_people_tenant_isolation ON hr_people
    USING (tenant_id::text = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true));

ALTER TABLE hr_person_private ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_person_private FORCE ROW LEVEL SECURITY;
CREATE POLICY hr_person_private_tenant_isolation ON hr_person_private
    USING (tenant_id::text = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true));

ALTER TABLE hr_person_identifiers ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_person_identifiers FORCE ROW LEVEL SECURITY;
CREATE POLICY hr_person_identifiers_tenant_isolation ON hr_person_identifiers
    USING (tenant_id::text = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true));
