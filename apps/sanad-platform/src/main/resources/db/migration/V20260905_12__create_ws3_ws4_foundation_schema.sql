-- ============================================================
-- HRM-G0 / Master Task 4 / WS3+WS4 Foundation Schema
-- ============================================================
-- Creates the minimum schema required by Task 1 RED contracts:
--   WS3: hr_country_packs, hr_compliance_rules,
--        hr_compliance_decisions, hr_compliance_override_requests
--   WS4: access_scope_grants
--
-- All tenant-owned tables have FORCE RLS + fail-closed policy.
-- Seeds 6 DRAFT GCC pack shells (SA, AE, QA, BH, KW, OM).
-- ============================================================

-- ============================================================
-- WS3: hr_country_packs
-- ============================================================

CREATE TABLE IF NOT EXISTS hr_country_packs (
    id                      UUID           NOT NULL DEFAULT gen_random_uuid(),
    country_code            CHAR(2)        NOT NULL REFERENCES platform_countries(country_code),
    pack_code               VARCHAR(80)    NOT NULL,
    pack_version            VARCHAR(40)    NOT NULL,
    status                  VARCHAR(30)    NOT NULL,
    effective_from          DATE           NOT NULL,
    effective_to            DATE,
    legal_reviewed_at       TIMESTAMPTZ,
    legal_reviewed_by       VARCHAR(200),
    certification_reference VARCHAR(500),
    created_at              TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_hr_country_packs PRIMARY KEY (id),
    CONSTRAINT uq_hr_country_pack_version UNIQUE (country_code, pack_code, pack_version),
    CONSTRAINT ck_hr_country_pack_status CHECK (status IN (
        'DRAFT','SOURCE_VERIFIED','LEGAL_REVIEWED','TESTED','CERTIFIED','ACTIVE','SUSPENDED','RETIRED')),
    CONSTRAINT ck_hr_country_pack_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE INDEX IF NOT EXISTS idx_hr_country_packs_country ON hr_country_packs (country_code, status);

-- Exclude overlapping ACTIVE/CERTIFIED intervals per (country_code, pack_code)
ALTER TABLE hr_country_packs
    ADD CONSTRAINT ex_hr_country_pack_no_overlap EXCLUDE USING gist (
        country_code WITH =,
        pack_code WITH =,
        daterange(effective_from, COALESCE(effective_to + 1, 'infinity'::date), '[)') WITH &&
    ) WHERE (status IN ('ACTIVE', 'CERTIFIED'));

-- ============================================================
-- WS3: hr_compliance_rules
-- ============================================================

CREATE TABLE IF NOT EXISTS hr_compliance_rules (
    id                      UUID           NOT NULL DEFAULT gen_random_uuid(),
    country_pack_id         UUID           NOT NULL REFERENCES hr_country_packs(id),
    rule_code               VARCHAR(120)   NOT NULL,
    rule_version            VARCHAR(40)    NOT NULL,
    operation_code          VARCHAR(120)   NOT NULL,
    enforcement_level       VARCHAR(40)    NOT NULL,
    exception_allowed       BOOLEAN        NOT NULL DEFAULT FALSE,
    parameters              JSONB          NOT NULL DEFAULT '{}'::jsonb,
    official_source_uri     VARCHAR(1000)  NOT NULL,
    legal_citation          VARCHAR(1000)  NOT NULL,
    source_snapshot_sha256  CHAR(64)       NOT NULL,
    effective_from          DATE           NOT NULL,
    effective_to            DATE,
    last_legal_review_at    TIMESTAMPTZ    NOT NULL,
    reviewed_by             VARCHAR(200)   NOT NULL,
    status                  VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_at              TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_hr_compliance_rules PRIMARY KEY (id),
    CONSTRAINT uq_hr_compliance_rule_version UNIQUE (country_pack_id, rule_code, rule_version),
    CONSTRAINT ck_hr_compliance_enforcement CHECK (enforcement_level IN (
        'MANDATORY_HARD','MANDATORY_WITH_EXCEPTION','REGULATORY_GUIDANCE','TENANT_POLICY')),
    CONSTRAINT ck_hr_compliance_rule_status CHECK (status IN ('ACTIVE','SUSPENDED','RETIRED')),
    CONSTRAINT ck_hr_compliance_rule_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE INDEX IF NOT EXISTS idx_hr_compliance_rules_pack ON hr_compliance_rules (country_pack_id, status);

-- ============================================================
-- WS3: hr_compliance_decisions (tenant-owned)
-- ============================================================

CREATE TABLE IF NOT EXISTS hr_compliance_decisions (
    id                      UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id               UUID           NOT NULL REFERENCES tenants(id),
    employment_id           UUID,
    resource_type           VARCHAR(40)    NOT NULL,
    resource_id             UUID,
    operation_code          VARCHAR(120)   NOT NULL,
    operation_type          VARCHAR(40),
    effective_date          DATE           NOT NULL,
    labor_jurisdiction      CHAR(2),
    operating_mode          VARCHAR(20)    NOT NULL,
    pack_code               VARCHAR(80),
    pack_version            VARCHAR(40),
    rule_code               VARCHAR(120),
    rule_version            VARCHAR(40),
    decision_type           VARCHAR(40)    NOT NULL,
    reason                  TEXT,
    created_at              TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_hr_compliance_decisions PRIMARY KEY (id),
    CONSTRAINT fk_hr_compliance_decisions_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT ck_hr_compliance_decision_type CHECK (decision_type IN (
        'COMPLIANT','BLOCKED','CONTROLLED_EXCEPTION_REQUIRED','LEGAL_REVIEW_REQUIRED','GLOBAL_MODE_ALLOWED')),
    CONSTRAINT ck_hr_compliance_operating_mode CHECK (operating_mode IN ('LOCALIZED','GLOBAL'))
);

ALTER TABLE hr_compliance_decisions ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_compliance_decisions FORCE  ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON hr_compliance_decisions;
CREATE POLICY tenant_isolation ON hr_compliance_decisions FOR ALL
    USING (tenant_id::text = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true));

CREATE INDEX IF NOT EXISTS idx_hr_compliance_decisions_tenant ON hr_compliance_decisions (tenant_id, effective_date);

-- ============================================================
-- WS3: hr_compliance_override_requests (tenant-owned)
-- ============================================================

CREATE TABLE IF NOT EXISTS hr_compliance_override_requests (
    id                          UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id                   UUID           NOT NULL REFERENCES tenants(id),
    compliance_rule_id          UUID           NOT NULL,
    resource_type               VARCHAR(40)    NOT NULL,
    resource_id                 UUID,
    requested_value_redacted    JSONB          NOT NULL DEFAULT '{}'::jsonb,
    compliant_value_redacted    JSONB          NOT NULL DEFAULT '{}'::jsonb,
    requester_user_id           UUID           NOT NULL,
    justification               TEXT           NOT NULL,
    evidence_reference          VARCHAR(500),
    approved_by                 UUID,
    approval_comment            TEXT,
    valid_from                  DATE           NOT NULL,
    valid_until                 DATE,
    status                      VARCHAR(30)    NOT NULL DEFAULT 'PENDING_APPROVAL',
    executed_at                 TIMESTAMPTZ,
    audit_reference             VARCHAR(500),
    created_at                  TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_hr_compliance_override_requests PRIMARY KEY (id),
    CONSTRAINT fk_hr_compliance_override_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT ck_hr_compliance_override_status CHECK (status IN (
        'PENDING_APPROVAL','APPROVED','REJECTED','EXECUTED','REVOKED','EXPIRED')),
    CONSTRAINT ck_hr_compliance_override_dates CHECK (valid_until IS NULL OR valid_until >= valid_from),
    CONSTRAINT ck_hr_compliance_four_eyes CHECK (approved_by IS NULL OR approved_by <> requester_user_id)
);

ALTER TABLE hr_compliance_override_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_compliance_override_requests FORCE  ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON hr_compliance_override_requests;
CREATE POLICY tenant_isolation ON hr_compliance_override_requests FOR ALL
    USING (tenant_id::text = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true));

CREATE INDEX IF NOT EXISTS idx_hr_compliance_override_tenant ON hr_compliance_override_requests (tenant_id, status);

-- ============================================================
-- WS3: Seed 6 DRAFT GCC pack shells (no fabricated legal rules)
-- ============================================================

INSERT INTO hr_country_packs (country_code, pack_code, pack_version, status, effective_from)
VALUES
    ('SA', 'HR_FOUNDATION', '1', 'DRAFT', DATE '2026-01-01'),
    ('AE', 'HR_FOUNDATION', '1', 'DRAFT', DATE '2026-01-01'),
    ('QA', 'HR_FOUNDATION', '1', 'DRAFT', DATE '2026-01-01'),
    ('BH', 'HR_FOUNDATION', '1', 'DRAFT', DATE '2026-01-01'),
    ('KW', 'HR_FOUNDATION', '1', 'DRAFT', DATE '2026-01-01'),
    ('OM', 'HR_FOUNDATION', '1', 'DRAFT', DATE '2026-01-01')
ON CONFLICT (country_code, pack_code, pack_version) DO NOTHING;

-- ============================================================
-- WS4: access_scope_grants (tenant-owned)
-- ============================================================

CREATE TABLE IF NOT EXISTS access_scope_grants (
    id                  UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID           NOT NULL REFERENCES tenants(id),
    role_id             UUID,
    user_id             UUID,
    capability_id       UUID           NOT NULL REFERENCES access_capabilities(id),
    scope_type          VARCHAR(30)    NOT NULL,
    organization_id     UUID,
    org_unit_id         UUID,
    legal_entity_id     UUID,
    is_direct_exception BOOLEAN        NOT NULL DEFAULT FALSE,
    reason              VARCHAR(500),
    granted_by          UUID,
    effective_from      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    effective_to        TIMESTAMPTZ,
    status              VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_access_scope_grants PRIMARY KEY (id),
    CONSTRAINT fk_access_scope_grants_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_access_scope_grants_capability FOREIGN KEY (capability_id) REFERENCES access_capabilities(id),
    CONSTRAINT ck_access_scope_principal CHECK ((role_id IS NULL) <> (user_id IS NULL)),
    CONSTRAINT ck_access_scope_type CHECK (scope_type IN (
        'SELF','DIRECT_REPORTS','REPORTING_TREE','ORG_UNIT','ORGANIZATION','TENANT')),
    CONSTRAINT ck_access_scope_dates CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT ck_access_scope_status CHECK (status IN ('ACTIVE','REVOKED','EXPIRED')),
    CONSTRAINT ck_access_scope_direct_exception CHECK (
        is_direct_exception = FALSE OR
        (user_id IS NOT NULL AND reason IS NOT NULL AND effective_to IS NOT NULL AND granted_by IS NOT NULL)
    )
);

ALTER TABLE access_scope_grants ENABLE ROW LEVEL SECURITY;
ALTER TABLE access_scope_grants FORCE  ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON access_scope_grants;
CREATE POLICY tenant_isolation ON access_scope_grants FOR ALL
    USING (tenant_id::text = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true));

CREATE INDEX IF NOT EXISTS idx_access_scope_grants_tenant ON access_scope_grants (tenant_id, capability_id, status);
CREATE INDEX IF NOT EXISTS idx_access_scope_grants_principal ON access_scope_grants (tenant_id, COALESCE(role_id, user_id), status);
