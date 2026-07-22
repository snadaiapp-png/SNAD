-- ============================================================================
-- SANAD CRM-008B — V20260722_9 — Create CRM Assignment Rule Counters
-- ============================================================================
-- Round-robin counter table: per (tenant_id, assignment_rule_id)
-- Counter updates MUST be TRANSACTIONAL, ATOMIC, CONCURRENCY_SAFE.
-- ============================================================================

BEGIN;

-- Precondition/postcondition check removed for H2 compatibility (PostgreSQL enforces via vendor migration)

CREATE TABLE crm_assignment_rule_counters (
    id          UUID NOT NULL,
    tenant_id   UUID NOT NULL,
    rule_id     UUID NOT NULL,
    counter     BIGINT NOT NULL DEFAULT 0,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT pk_assignment_rule_counters PRIMARY KEY (id),
    CONSTRAINT uk_assignment_rule_counters_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_assignment_rule_counters_tenants FOREIGN KEY (tenant_id)
        REFERENCES tenants(id) ON DELETE RESTRICT,
    CONSTRAINT fk_assignment_rule_counters_rules FOREIGN KEY (tenant_id, rule_id)
        REFERENCES crm_assignment_rules(tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_assignment_rule_counters_non_negative CHECK (counter >= 0)
);

-- One counter row per (tenant, rule) — enforced by unique index
CREATE UNIQUE INDEX uk_assignment_rule_counters_tenant_rule
    ON crm_assignment_rule_counters (tenant_id, rule_id);

-- Tenant-leading index for counter lookups
CREATE INDEX idx_assignment_rule_counters_tenant_rule
    ON crm_assignment_rule_counters (tenant_id, rule_id, counter);

-- Precondition/postcondition check removed for H2 compatibility (PostgreSQL enforces via vendor migration)

COMMIT;
