-- ============================================================
-- HRM-G0 / Master Task 5 / WS6 Task 1
-- V20260904_2__create_hr_contract_compensation_foundation.sql
-- ============================================================
-- Effective-dated contract and compensation persistence:
--   hr_employment_contracts          — contract identity (primary flag)
--   hr_employment_contract_versions  — immutable effective-dated terms
--   hr_compensation_packages         — effective-dated package windows
--   hr_compensation_components       — structural package components
--
-- Temporal guarantees enforced AT THE DATABASE:
--   * btree_gist exclusion: at most one overlapping ACTIVE primary
--     contract version per employment (denormalized employment_id +
--     is_primary on the version row so the exclusion applies within
--     one table).
--   * btree_gist exclusion: versions of one contract never overlap.
--   * btree_gist exclusion: at most one overlapping ACTIVE compensation
--     package per employment.
--   * Structural CHECK constraints per the WS6 plan.
--   * FORCE (ENABLE+FORCE) fail-closed RLS with tenant_isolation on
--     every table; runtime role is NOSUPERUSER NOBYPASSRLS.
--
-- Next collision-free version discovered at write time: 20260904.2
-- (latest was 20260904.1).
-- ============================================================

CREATE EXTENSION IF NOT EXISTS btree_gist;

-- ------------------------------------------------------------
-- 1. hr_employment_contracts
-- ------------------------------------------------------------
CREATE TABLE hr_employment_contracts (
    id                     UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id              UUID           NOT NULL REFERENCES tenants(id),
    employment_id          UUID           NOT NULL REFERENCES hr_employees(id),
    contract_number        VARCHAR(100)   NOT NULL,
    is_primary             BOOLEAN        NOT NULL DEFAULT TRUE,
    predecessor_contract_id UUID          REFERENCES hr_employment_contracts(id),
    created_at             TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_hr_employment_contracts PRIMARY KEY (id),
    CONSTRAINT uq_hr_employment_contracts_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT uq_hr_contract_number UNIQUE (tenant_id, employment_id, contract_number)
);

ALTER TABLE hr_employment_contracts ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_employment_contracts FORCE  ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON hr_employment_contracts;
CREATE POLICY tenant_isolation ON hr_employment_contracts FOR ALL
    USING (tenant_id::text = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true));

CREATE INDEX IF NOT EXISTS idx_hr_employment_contracts_employment
    ON hr_employment_contracts (tenant_id, employment_id);

-- ------------------------------------------------------------
-- 2. hr_employment_contract_versions (immutable effective-dated terms)
-- ------------------------------------------------------------
CREATE TABLE hr_employment_contract_versions (
    id                  UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID           NOT NULL REFERENCES tenants(id),
    contract_id         UUID           NOT NULL REFERENCES hr_employment_contracts(id),
    employment_id       UUID           NOT NULL REFERENCES hr_employees(id),
    version_number      INT            NOT NULL,
    status              VARCHAR(30)    NOT NULL,
    is_primary          BOOLEAN        NOT NULL DEFAULT TRUE,
    contract_term_type  VARCHAR(30)    NOT NULL,
    contract_start_date DATE           NOT NULL,
    contract_end_date   DATE,
    effective_from      DATE           NOT NULL,
    effective_to        DATE,
    document_reference  VARCHAR(500),
    country_terms       JSONB          NOT NULL DEFAULT '{}'::jsonb,
    created_by          UUID,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_hr_employment_contract_versions PRIMARY KEY (id),
    CONSTRAINT fk_hr_contract_versions_contract FOREIGN KEY (contract_id, tenant_id)
        REFERENCES hr_employment_contracts (id, tenant_id) NOT VALID,
    CONSTRAINT uq_hr_contract_version UNIQUE (contract_id, version_number),
    CONSTRAINT ck_hr_contract_status CHECK (status IN (
        'DRAFT','PENDING_SIGNATURE','ACTIVE','EXPIRED','TERMINATED','SUPERSEDED','VOIDED')),
    CONSTRAINT ck_hr_contract_term_type CHECK (contract_term_type IN ('FIXED_TERM','INDEFINITE','OTHER')),
    CONSTRAINT ck_hr_contract_dates CHECK (
        (contract_end_date IS NULL OR contract_end_date >= contract_start_date)
        AND (effective_to IS NULL OR effective_to >= effective_from)
    ),
    -- Versions of ONE contract never overlap.
    CONSTRAINT ex_hr_contract_version_no_overlap
        EXCLUDE USING gist (
            contract_id WITH =,
            daterange(effective_from, effective_to, '[)') WITH &&
        ),
    -- At most one overlapping ACTIVE primary contract window per employment.
    CONSTRAINT ex_hr_contract_active_primary_no_overlap
        EXCLUDE USING gist (
            employment_id WITH =,
            daterange(effective_from, effective_to, '[)') WITH &&
        ) WHERE (is_primary AND status = 'ACTIVE')
);

ALTER TABLE hr_employment_contract_versions ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_employment_contract_versions FORCE  ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON hr_employment_contract_versions;
CREATE POLICY tenant_isolation ON hr_employment_contract_versions FOR ALL
    USING (tenant_id::text = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true));

CREATE INDEX IF NOT EXISTS idx_hr_contract_versions_employment
    ON hr_employment_contract_versions (tenant_id, employment_id, effective_from);

-- ------------------------------------------------------------
-- 3. hr_compensation_packages
-- ------------------------------------------------------------
CREATE TABLE hr_compensation_packages (
    id                    UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id             UUID           NOT NULL REFERENCES tenants(id),
    employment_id         UUID           NOT NULL REFERENCES hr_employees(id),
    currency_code         CHAR(3)        NOT NULL,
    pay_frequency         VARCHAR(30)    NOT NULL,
    effective_from        DATE           NOT NULL,
    effective_to          DATE,
    status                VARCHAR(20)    NOT NULL,
    predecessor_package_id UUID          REFERENCES hr_compensation_packages(id),
    version               BIGINT         NOT NULL DEFAULT 0,
    created_by            UUID,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_hr_compensation_packages PRIMARY KEY (id),
    CONSTRAINT uq_hr_compensation_packages_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT ck_hr_compensation_dates CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT ck_hr_compensation_status CHECK (status IN ('DRAFT','ACTIVE','SUPERSEDED','ENDED','VOIDED')),
    CONSTRAINT ck_hr_compensation_pay_frequency CHECK (pay_frequency IN
        ('MONTHLY','BIWEEKLY','WEEKLY','SEMI_MONTHLY','ANNUAL','OTHER')),
    -- At most one overlapping ACTIVE package per employment.
    CONSTRAINT ex_hr_compensation_active_no_overlap
        EXCLUDE USING gist (
            employment_id WITH =,
            daterange(effective_from, effective_to, '[)') WITH &&
        ) WHERE (status = 'ACTIVE')
);

ALTER TABLE hr_compensation_packages ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_compensation_packages FORCE  ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON hr_compensation_packages;
CREATE POLICY tenant_isolation ON hr_compensation_packages FOR ALL
    USING (tenant_id::text = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true));

CREATE INDEX IF NOT EXISTS idx_hr_compensation_packages_employment
    ON hr_compensation_packages (tenant_id, employment_id, effective_from);

-- ------------------------------------------------------------
-- 4. hr_compensation_components
-- ------------------------------------------------------------
CREATE TABLE hr_compensation_components (
    id             UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id      UUID           NOT NULL REFERENCES tenants(id),
    package_id     UUID           NOT NULL REFERENCES hr_compensation_packages(id),
    component_type VARCHAR(40)    NOT NULL,
    code           VARCHAR(80)    NOT NULL,
    amount         NUMERIC(19,4),
    percentage     NUMERIC(9,4),
    metadata       JSONB          NOT NULL DEFAULT '{}'::jsonb,
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_hr_compensation_components PRIMARY KEY (id),
    CONSTRAINT fk_hr_comp_components_package FOREIGN KEY (package_id, tenant_id)
        REFERENCES hr_compensation_packages (id, tenant_id) NOT VALID,
    CONSTRAINT ck_hr_comp_component_type CHECK (component_type IN (
        'BASE_SALARY','ALLOWANCE','BENEFIT','VARIABLE_TARGET','OTHER')),
    CONSTRAINT ck_hr_comp_amount_or_percentage CHECK (
        (amount IS NOT NULL AND percentage IS NULL)
        OR (amount IS NULL AND percentage IS NOT NULL)
    ),
    CONSTRAINT ck_hr_comp_amount_positive CHECK (amount IS NULL OR amount > 0),
    CONSTRAINT ck_hr_comp_percentage_positive CHECK (percentage IS NULL OR percentage > 0)
);

ALTER TABLE hr_compensation_components ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_compensation_components FORCE  ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON hr_compensation_components;
CREATE POLICY tenant_isolation ON hr_compensation_components FOR ALL
    USING (tenant_id::text = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true));

CREATE INDEX IF NOT EXISTS idx_hr_compensation_components_package
    ON hr_compensation_components (tenant_id, package_id);

-- ------------------------------------------------------------
-- 5. Immutable terms guard (WS6 Task 2)
--    Lifecycle fields (status, effective windows) may transition;
--    TERM columns are immutable — an amendment creates a NEW version.
-- ------------------------------------------------------------
CREATE OR REPLACE FUNCTION hr_contract_versions_immutable_terms() RETURNS trigger AS $$
BEGIN
    IF NEW.contract_term_type IS DISTINCT FROM OLD.contract_term_type
       OR NEW.contract_start_date IS DISTINCT FROM OLD.contract_start_date
       OR NEW.contract_end_date IS DISTINCT FROM OLD.contract_end_date
       OR NEW.document_reference IS DISTINCT FROM OLD.document_reference
       OR NEW.country_terms IS DISTINCT FROM OLD.country_terms
       OR NEW.employment_id IS DISTINCT FROM OLD.employment_id
       OR NEW.is_primary IS DISTINCT FROM OLD.is_primary THEN
        RAISE EXCEPTION 'HRM_CONTRACT_VERSION_IMMUTABLE: effective contract terms are immutable (amendment creates a new version)';
    END IF;
    RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_hr_contract_versions_immutable
BEFORE UPDATE ON hr_employment_contract_versions
FOR EACH ROW EXECUTE FUNCTION hr_contract_versions_immutable_terms();
