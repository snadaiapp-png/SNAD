-- ============================================================
-- HRM-G0 WS2 Task 2 GREEN — Employment Expansion
-- ------------------------------------------------------------
-- Non-destructively expands hr_employees with canonical Task 2
-- columns and creates three new tables:
--   hr_employment_status_periods
--   hr_migration_tenant_state
--   hr_legacy_employee_mappings
--
-- Design contracts:
--   1. EXPAND-ONLY: no legacy columns dropped, no legacy constraints
--      dropped unsafely. Existing uq_hr_employees_number_tenant
--      is preserved alongside the new target uniqueness.
--   2. Person ↔ Employment: composite FK (tenant_id, person_id)
--      → hr_people(tenant_id, id) — tenant-safe.
--   3. Legal Entity ↔ Employment: composite FK (tenant_id, legal_entity_id)
--      → legal_entities(tenant_id, id) — requires adding UNIQUE(tenant_id, id)
--      to legal_entities.
--   4. Employee-number target uniqueness:
--      UNIQUE (tenant_id, legal_entity_id, employee_number) — added
--      alongside the legacy UNIQUE (tenant_id, employee_number).
--   5. Max-one-non-terminal Employment:
--      Partial UNIQUE INDEX on (tenant_id, person_id, legal_entity_id)
--      WHERE status IN ('DRAFT','PENDING_ONBOARDING','ACTIVE','ON_LEAVE','SUSPENDED').
--   6. Employment status CHECK expanded to include DRAFT, PENDING_ONBOARDING, VOIDED
--      while retaining existing values.
--   7. Status history: hr_employment_status_periods with EXCLUDE constraint
--      (partial, WHERE effective_to IS NULL) preventing overlapping open periods.
--   8. Trigger: AFTER INSERT on hr_employees creates initial status period.
--   9. New tables: ENABLE + FORCE RLS with fail-closed policy.
--
-- Legacy hr_employees RLS (fail-open: "current_setting(...) IS NULL OR ...")
-- is NOT modified — Task 5 will handle legacy HR RLS hardening.
-- ============================================================

CREATE EXTENSION IF NOT EXISTS btree_gist;

-- ------------------------------------------------------------
-- 1. Expand hr_employees with canonical Task 2 columns
-- ------------------------------------------------------------
ALTER TABLE hr_employees ADD COLUMN IF NOT EXISTS person_id UUID;
ALTER TABLE hr_employees ADD COLUMN IF NOT EXISTS legal_entity_id UUID;
ALTER TABLE hr_employees ADD COLUMN IF NOT EXISTS worker_classification_code VARCHAR(60);
ALTER TABLE hr_employees ADD COLUMN IF NOT EXISTS rehire_of_employee_id UUID;
ALTER TABLE hr_employees ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- Add UNIQUE(tenant_id, id) to legal_entities to permit composite FK.
-- This is safe because PK on (id) already guarantees uniqueness of id;
-- the composite unique is a strict superset constraint.
ALTER TABLE legal_entities ADD CONSTRAINT uq_legal_entities_tenant_id UNIQUE (tenant_id, id);

-- Person ↔ Employment: tenant-safe composite FK.
ALTER TABLE hr_employees
    ADD CONSTRAINT fk_hr_employees_tenant_person
    FOREIGN KEY (tenant_id, person_id) REFERENCES hr_people (tenant_id, id);

-- Legal Entity ↔ Employment: tenant-safe composite FK.
ALTER TABLE hr_employees
    ADD CONSTRAINT fk_hr_employees_tenant_legal_entity
    FOREIGN KEY (tenant_id, legal_entity_id) REFERENCES legal_entities (tenant_id, id);

-- Self-reference for rehire chain.
ALTER TABLE hr_employees
    ADD CONSTRAINT fk_hr_employees_rehire_of
    FOREIGN KEY (rehire_of_employee_id) REFERENCES hr_employees (id);

-- Expand the status CHECK to include DRAFT, PENDING_ONBOARDING, VOIDED.
-- Drop old constraint and add expanded one.
ALTER TABLE hr_employees DROP CONSTRAINT IF EXISTS ck_hr_employees_status;
ALTER TABLE hr_employees
    ADD CONSTRAINT ck_hr_employees_status
    CHECK (status IN ('DRAFT','PENDING_ONBOARDING','ACTIVE','ON_LEAVE','SUSPENDED','TERMINATED','VOIDED'));

-- Target uniqueness: (tenant_id, legal_entity_id, employee_number).
-- Legacy uniqueness (tenant_id, employee_number) is PRESERVED.
CREATE UNIQUE INDEX IF NOT EXISTS uq_hr_employees_tenant_le_number
    ON hr_employees (tenant_id, legal_entity_id, employee_number);

-- Max-one-non-terminal Employment per (tenant, person, legal_entity).
CREATE UNIQUE INDEX IF NOT EXISTS uq_hr_employees_one_non_terminal
    ON hr_employees (tenant_id, person_id, legal_entity_id)
    WHERE status IN ('DRAFT','PENDING_ONBOARDING','ACTIVE','ON_LEAVE','SUSPENDED');

CREATE INDEX IF NOT EXISTS idx_hr_employees_person ON hr_employees (tenant_id, person_id);
CREATE INDEX IF NOT EXISTS idx_hr_employees_legal_entity ON hr_employees (tenant_id, legal_entity_id);

-- ------------------------------------------------------------
-- 2. hr_employment_status_periods — immutable history
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS hr_employment_status_periods (
    id                  UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID           NOT NULL,
    employment_id       UUID           NOT NULL,
    status              VARCHAR(30)    NOT NULL,
    effective_from      DATE           NOT NULL,
    effective_to        DATE,
    reason_code         VARCHAR(80),
    reason_text         VARCHAR(500),
    changed_by          UUID,
    transition_event_id UUID,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_hr_employment_status_periods PRIMARY KEY (id),
    CONSTRAINT fk_hr_employment_status_periods_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_hr_employment_status_periods_employment
        FOREIGN KEY (employment_id) REFERENCES hr_employees (id),
    CONSTRAINT ck_hr_employment_status CHECK (status IN
        ('DRAFT','PENDING_ONBOARDING','ACTIVE','ON_LEAVE','SUSPENDED','TERMINATED','VOIDED')),
    CONSTRAINT ck_hr_employment_status_dates
        CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

-- Partial EXCLUDE: only OPEN periods (effective_to IS NULL) cannot overlap.
-- CLOSED periods may coexist with anything (immutability model).
ALTER TABLE hr_employment_status_periods
    DROP CONSTRAINT IF EXISTS ex_hr_employment_status_no_overlap;
ALTER TABLE hr_employment_status_periods
    ADD CONSTRAINT ex_hr_employment_status_no_overlap
    EXCLUDE USING gist (
        employment_id WITH =,
        daterange(effective_from, COALESCE(effective_to + 1, 'infinity'::date), '[)') WITH &&
    ) WHERE (effective_to IS NULL);

CREATE INDEX IF NOT EXISTS idx_hr_employment_status_periods_employment
    ON hr_employment_status_periods (tenant_id, employment_id);

-- Trigger: create initial status period when an Employment row is inserted.
CREATE OR REPLACE FUNCTION create_initial_employment_status_period()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO hr_employment_status_periods
        (id, tenant_id, employment_id, status, effective_from, effective_to, created_at)
    VALUES
        (gen_random_uuid(), NEW.tenant_id, NEW.id, NEW.status,
         COALESCE(NEW.hire_date, CURRENT_DATE) - 1, NULL, NOW());
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS hr_employees_initial_status_period ON hr_employees;
CREATE TRIGGER hr_employees_initial_status_period
    AFTER INSERT ON hr_employees
    FOR EACH ROW
    EXECUTE FUNCTION create_initial_employment_status_period();

-- ------------------------------------------------------------
-- 3. hr_migration_tenant_state — migration cutover gate
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS hr_migration_tenant_state (
    tenant_id   UUID           NOT NULL,
    state       VARCHAR(20)    NOT NULL DEFAULT 'LEGACY',
    updated_at  TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_by  UUID,
    CONSTRAINT pk_hr_migration_tenant_state PRIMARY KEY (tenant_id),
    CONSTRAINT fk_hr_migration_tenant_state_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT ck_hr_migration_tenant_state
        CHECK (state IN ('LEGACY','MIGRATING','CANONICAL','BLOCKED'))
);

-- ------------------------------------------------------------
-- 4. hr_legacy_employee_mappings — migration mapping/review
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS hr_legacy_employee_mappings (
    id                      UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id               UUID           NOT NULL,
    legacy_employee_id      UUID           NOT NULL,
    canonical_person_id     UUID,
    canonical_employment_id UUID,
    classification          VARCHAR(40)    NOT NULL,
    review_reason           TEXT,
    created_at              TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_hr_legacy_employee_mappings PRIMARY KEY (id),
    CONSTRAINT fk_hr_legacy_employee_mappings_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_hr_legacy_employee_mappings_legacy
        FOREIGN KEY (legacy_employee_id) REFERENCES hr_employees (id),
    CONSTRAINT fk_hr_legacy_employee_mappings_person
        FOREIGN KEY (canonical_person_id) REFERENCES hr_people (id),
    CONSTRAINT fk_hr_legacy_employee_mappings_employment
        FOREIGN KEY (canonical_employment_id) REFERENCES hr_employees (id),
    CONSTRAINT ck_hr_legacy_employee_mappings_class
        CHECK (classification IN ('AUTO_MIGRATE','MIGRATION_REVIEW_REQUIRED','MIGRATION_BLOCKED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_hr_legacy_employee_mappings_tenant_legacy
    ON hr_legacy_employee_mappings (tenant_id, legacy_employee_id);

CREATE INDEX IF NOT EXISTS idx_hr_legacy_employee_mappings_tenant
    ON hr_legacy_employee_mappings (tenant_id);

-- ------------------------------------------------------------
-- 5. RLS: ENABLE + FORCE + fail-closed on all 3 new tables
-- ------------------------------------------------------------
ALTER TABLE hr_employment_status_periods ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_employment_status_periods FORCE ROW LEVEL SECURITY;
CREATE POLICY hr_employment_status_periods_tenant_isolation ON hr_employment_status_periods
    USING (tenant_id::text = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true));

ALTER TABLE hr_migration_tenant_state ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_migration_tenant_state FORCE ROW LEVEL SECURITY;
CREATE POLICY hr_migration_tenant_state_tenant_isolation ON hr_migration_tenant_state
    USING (tenant_id::text = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true));

ALTER TABLE hr_legacy_employee_mappings ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_legacy_employee_mappings FORCE ROW LEVEL SECURITY;
CREATE POLICY hr_legacy_employee_mappings_tenant_isolation ON hr_legacy_employee_mappings
    USING (tenant_id::text = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true));
