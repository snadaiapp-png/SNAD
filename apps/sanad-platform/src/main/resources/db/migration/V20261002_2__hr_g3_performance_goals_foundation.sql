-- HRM G3 — first GREEN slice only: performance goals persistence.
-- PostgreSQL Direct. No Docker/Testcontainers.
-- Scope intentionally limited to the G3 performance-goal persistence contracts.
--
-- Canonical identity invariant:
--   goal.tenant_id + goal.employment_id + goal.person_id must resolve to the
--   same canonical hr_employees row. This prevents cross-tenant references and
--   prevents attaching a goal to a Person different from the Employment owner.

-- hr_employees already has a tenant-safe (tenant_id, id) unique index. G3 needs
-- the Person binding in the FK target as well, so add the exact additive target
-- required by the canonical relationship.
CREATE UNIQUE INDEX IF NOT EXISTS uq_hr_employees_tenant_id_person
    ON hr_employees (tenant_id, id, person_id);

CREATE TABLE hr_performance_goals (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID         NOT NULL,
    person_id       UUID         NOT NULL,
    employment_id   UUID         NOT NULL,
    title           VARCHAR(255) NOT NULL,
    metric          VARCHAR(128) NOT NULL,
    target_value    VARCHAR(255) NOT NULL,
    progress        INTEGER      NOT NULL DEFAULT 0,
    status          VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    starts_on       DATE         NOT NULL,
    ends_on         DATE         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_hr_performance_goals PRIMARY KEY (id),
    CONSTRAINT fk_hr_performance_goals_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_hr_performance_goals_canonical_employment
        FOREIGN KEY (tenant_id, employment_id, person_id)
        REFERENCES hr_employees (tenant_id, id, person_id),
    CONSTRAINT ck_hr_performance_goals_progress
        CHECK (progress BETWEEN 0 AND 100),
    CONSTRAINT ck_hr_performance_goals_period
        CHECK (ends_on >= starts_on)
);

CREATE INDEX ix_hr_performance_goals_tenant_employment
    ON hr_performance_goals (tenant_id, employment_id);

CREATE INDEX ix_hr_performance_goals_tenant_person
    ON hr_performance_goals (tenant_id, person_id);

ALTER TABLE hr_performance_goals ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_performance_goals FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON hr_performance_goals FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
