-- ============================================================
-- HRM-G1 T8 — hire conversion governance (directive T8.2–T8.12)
-- Forward-only, additive:
--   1. hr_hire_conversions.person_reused  — §7.3 case 1 requires the ledger
--      to record that an existing Person was linked instead of created.
--   2. hr_tenant_policies — the authoritative tenant policy store for G1
--      business gates (§T8.8: hire approval is ON only when this table says
--      so; a missing policy row resolves to the documented default OFF —
--      never inferred). FORCE RLS like every G1 tenant table (fail-closed).
-- ============================================================

ALTER TABLE hr_hire_conversions
    ADD COLUMN person_reused BOOLEAN NOT NULL DEFAULT FALSE;

-- §7.3 cases 3/4: replays return the ORIGINAL result — the ledger therefore
-- persists the employee number assigned by the conversion.
ALTER TABLE hr_hire_conversions
    ADD COLUMN employee_number VARCHAR(80);

-- T8-MIG-001 (MIGRATION_DEFECT, discovered by the T8 RED suite): the shipped
-- fk_hr_hire_conversions_person constraint paired its columns positionally
-- against hr_people (tenant_id, id) — i.e. person_id↔tenant_id and
-- tenant_id↔id — a congruence that can never hold for real rows. Forward-only
-- rebuild: person_id↔id, tenant_id↔tenant_id (same protection, correct pairs).
ALTER TABLE hr_hire_conversions
    DROP CONSTRAINT fk_hr_hire_conversions_person;
ALTER TABLE hr_hire_conversions
    ADD CONSTRAINT fk_hr_hire_conversions_person
    FOREIGN KEY (person_id, tenant_id)
    REFERENCES hr_people (id, tenant_id);

-- ------------------------------------------------------------
-- hr_tenant_policies — authoritative tenant configuration (§T8.8)
-- ------------------------------------------------------------
CREATE TABLE hr_tenant_policies (
    id           UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id    UUID           NOT NULL REFERENCES tenants(id),
    policy_code  VARCHAR(120)   NOT NULL,
    policy_value VARCHAR(120)   NOT NULL,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_hr_tenant_policies PRIMARY KEY (id),
    CONSTRAINT uq_hr_tenant_policies_tenant_code UNIQUE (tenant_id, policy_code),
    CONSTRAINT fk_hr_tenant_policies_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenants (id)
);

ALTER TABLE hr_tenant_policies ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_tenant_policies FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON hr_tenant_policies FOR ALL
    USING (tenant_id::text = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true));

CREATE INDEX idx_hr_tenant_policies_tenant_code
    ON hr_tenant_policies (tenant_id, policy_code);
