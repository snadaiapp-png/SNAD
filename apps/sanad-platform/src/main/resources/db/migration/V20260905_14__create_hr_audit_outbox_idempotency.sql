-- ============================================================
-- HRM-G0 / Master Task 4 / Task 3 (WS4)
-- Immutable HR audit ledger + delivery state + domain event
-- outbox + durable idempotency + HR-managed IAM access bindings
-- ============================================================
-- Forward-only. Never modify after apply.
--
-- Immutability contract: hr_audit_ledger is an append-only FACT
-- table. PostgreSQL itself rejects UPDATE/DELETE via trigger
-- (the mandated contract; module test harnesses rely on TRUNCATE
-- for cross-table cleanup). Mutable delivery state lives ONLY in
-- hr_audit_delivery.
--
-- Redaction contract: audit states, outbox payloads and idempotency
-- response metadata reject raw secret/PII keys at the database level
-- (defense in depth; service-level redaction belongs to later tasks).
--
-- Tenant isolation: every tenant-owned table gets ENABLE + FORCE
-- ROW LEVEL SECURITY with a fail-closed current_setting policy.
-- ============================================================

-- ============================================================
-- 1. hr_audit_ledger (immutable fact table, tenant-owned)
-- ============================================================

CREATE TABLE hr_audit_ledger (
    id                  UUID           NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID           NOT NULL REFERENCES tenants(id),
    actor_user_id       UUID,
    action              VARCHAR(150)   NOT NULL,
    resource_type       VARCHAR(100)   NOT NULL,
    resource_id         UUID,
    organization_id     UUID,
    legal_entity_id     UUID,
    data_classification VARCHAR(40)    NOT NULL,
    reason              VARCHAR(500),
    before_state        JSONB,
    after_state         JSONB,
    result              VARCHAR(20)    NOT NULL,
    correlation_id      UUID,
    request_id          UUID,
    occurred_at         TIMESTAMPTZ    NOT NULL,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_hr_audit_ledger PRIMARY KEY (id),
    CONSTRAINT fk_hr_audit_ledger_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT ck_hr_audit_result CHECK (result IN ('SUCCESS','FAILURE')),
    CONSTRAINT ck_hr_audit_ledger_no_raw_secrets CHECK (
        (before_state IS NULL OR NOT (before_state ?| ARRAY[
            'password','secret','token','api_key','apikey','authorization','cookie','jwt',
            'national_id','iqama','passport','bank_account','bank_iban',
            'encryption_key','blind_index_key']))
        AND
        (after_state IS NULL OR NOT (after_state ?| ARRAY[
            'password','secret','token','api_key','apikey','authorization','cookie','jwt',
            'national_id','iqama','passport','bank_account','bank_iban',
            'encryption_key','blind_index_key']))
    )
);

ALTER TABLE hr_audit_ledger ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_audit_ledger FORCE  ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON hr_audit_ledger;
CREATE POLICY tenant_isolation ON hr_audit_ledger FOR ALL
    USING (tenant_id::text = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true));

CREATE INDEX IF NOT EXISTS idx_hr_audit_ledger_tenant_time
    ON hr_audit_ledger (tenant_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_hr_audit_ledger_resource
    ON hr_audit_ledger (tenant_id, resource_type, resource_id);

-- Immutability guard: PostgreSQL rejects UPDATE / DELETE / TRUNCATE.
CREATE OR REPLACE FUNCTION hr_audit_ledger_immutable_guard() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'HRM_AUDIT_IMMUTABLE: % on hr_audit_ledger is forbidden', TG_OP
        USING ERRCODE = 'P0001';
END;
$$;

CREATE TRIGGER trg_hr_audit_ledger_no_update_delete
    BEFORE UPDATE OR DELETE ON hr_audit_ledger
    FOR EACH ROW EXECUTE FUNCTION hr_audit_ledger_immutable_guard();

-- ============================================================
-- 2. hr_audit_delivery (mutable delivery state, tenant-owned)
-- ============================================================

CREATE TABLE hr_audit_delivery (
    audit_id           UUID        NOT NULL REFERENCES hr_audit_ledger(id),
    tenant_id          UUID        NOT NULL REFERENCES tenants(id),
    status             VARCHAR(20) NOT NULL,
    attempt_count      INT         NOT NULL DEFAULT 0,
    max_attempts       INT         NOT NULL DEFAULT 8,
    available_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    delivered_at       TIMESTAMPTZ,
    last_error_code    VARCHAR(80),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_hr_audit_delivery PRIMARY KEY (audit_id),
    CONSTRAINT fk_hr_audit_delivery_audit FOREIGN KEY (audit_id) REFERENCES hr_audit_ledger(id),
    CONSTRAINT fk_hr_audit_delivery_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT ck_hr_audit_delivery_status CHECK (status IN ('PENDING','DELIVERED','FAILED','DEAD_LETTER'))
);

ALTER TABLE hr_audit_delivery ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_audit_delivery FORCE  ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON hr_audit_delivery;
CREATE POLICY tenant_isolation ON hr_audit_delivery FOR ALL
    USING (tenant_id::text = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true));

CREATE INDEX IF NOT EXISTS idx_hr_audit_delivery_pending
    ON hr_audit_delivery (status, available_at) WHERE status <> 'DELIVERED';

-- ============================================================
-- 3. hr_domain_event_outbox (producer-local, at-least-once)
-- ============================================================

CREATE TABLE hr_domain_event_outbox (
    event_id            UUID           NOT NULL,
    tenant_id           UUID           NOT NULL REFERENCES tenants(id),
    event_type          VARCHAR(150)   NOT NULL,
    event_version       INT            NOT NULL DEFAULT 1,
    aggregate_type      VARCHAR(100)   NOT NULL,
    aggregate_id        UUID,
    organization_id     UUID,
    actor_user_id       UUID,
    occurred_at         TIMESTAMPTZ    NOT NULL,
    correlation_id      UUID,
    causation_id        UUID,
    idempotency_key     VARCHAR(200),
    data_classification VARCHAR(40)    NOT NULL DEFAULT 'OPERATIONAL',
    payload             JSONB          NOT NULL DEFAULT '{}'::jsonb,
    status              VARCHAR(20)    NOT NULL DEFAULT 'READY',
    attempt_count       INT            NOT NULL DEFAULT 0,
    max_attempts        INT            NOT NULL DEFAULT 8,
    available_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    claim_token         UUID,
    claimed_by          VARCHAR(200),
    claim_expires_at    TIMESTAMPTZ,
    delivered_at        TIMESTAMPTZ,
    last_error_code     VARCHAR(80),
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_hr_domain_event_outbox PRIMARY KEY (event_id),
    CONSTRAINT fk_hr_domain_event_outbox_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT ck_hr_domain_event_outbox_status CHECK (status IN ('READY','CLAIMED','DELIVERED','DEAD_LETTER')),
    CONSTRAINT ck_hr_domain_event_outbox_no_raw_secrets CHECK (
        NOT (payload ?| ARRAY[
            'password','secret','token','api_key','apikey','authorization','cookie','jwt',
            'national_id','iqama','passport','bank_account','bank_iban',
            'encryption_key','blind_index_key'])
    )
);

ALTER TABLE hr_domain_event_outbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_domain_event_outbox FORCE  ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON hr_domain_event_outbox;
CREATE POLICY tenant_isolation ON hr_domain_event_outbox FOR ALL
    USING (tenant_id::text = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true));

CREATE INDEX IF NOT EXISTS idx_hr_domain_event_outbox_claim
    ON hr_domain_event_outbox (status, available_at) WHERE status = 'READY';
CREATE INDEX IF NOT EXISTS idx_hr_domain_event_outbox_tenant
    ON hr_domain_event_outbox (tenant_id, occurred_at DESC);

-- ============================================================
-- 4. hr_idempotency_records (durable, producer-local)
-- ============================================================

CREATE TABLE hr_idempotency_records (
    id                 UUID          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id          UUID          NOT NULL REFERENCES tenants(id),
    principal_id       UUID          NOT NULL,
    operation_code     VARCHAR(120)  NOT NULL,
    idempotency_key    VARCHAR(200)  NOT NULL,
    request_fingerprint CHAR(64)     NOT NULL,
    operation_reference UUID,
    response_status    INT,
    response_body      JSONB,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    expires_at         TIMESTAMPTZ   NOT NULL DEFAULT (NOW() + INTERVAL '24 hours'),
    CONSTRAINT pk_hr_idempotency_records PRIMARY KEY (id),
    CONSTRAINT fk_hr_idempotency_records_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT uq_hr_idempotency_boundary UNIQUE (tenant_id, principal_id, operation_code, idempotency_key),
    CONSTRAINT ck_hr_idempotency_no_raw_secrets CHECK (
        response_body IS NULL OR NOT (response_body ?| ARRAY[
            'password','secret','token','api_key','apikey','authorization','cookie','jwt',
            'national_id','iqama','passport','bank_account','bank_iban',
            'encryption_key','blind_index_key'])
    )
);

ALTER TABLE hr_idempotency_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_idempotency_records FORCE  ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON hr_idempotency_records;
CREATE POLICY tenant_isolation ON hr_idempotency_records FOR ALL
    USING (tenant_id::text = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true));

CREATE INDEX IF NOT EXISTS idx_hr_idempotency_records_expiry
    ON hr_idempotency_records (expires_at);

-- ============================================================
-- 5. hr_iam_access_bindings (persistence only; consumer = WS4 Task 7)
-- ============================================================

CREATE TABLE hr_iam_access_bindings (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID        NOT NULL REFERENCES tenants(id),
    person_id       UUID,
    user_id         UUID,
    access_mode     VARCHAR(30) NOT NULL DEFAULT 'NON_HR_MANAGED',
    reason          VARCHAR(500),
    granted_by      UUID,
    effective_from  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    effective_to    TIMESTAMPTZ,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_hr_iam_access_bindings PRIMARY KEY (id),
    CONSTRAINT fk_hr_iam_access_bindings_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT ck_hr_iam_access_mode CHECK (access_mode IN ('HR_MANAGED','NON_HR_MANAGED')),
    CONSTRAINT ck_hr_iam_access_status CHECK (status IN ('ACTIVE','REVOKED','EXPIRED')),
    CONSTRAINT ck_hr_iam_access_dates CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT ck_hr_iam_access_subject CHECK (num_nonnulls(person_id, user_id) >= 1)
);

ALTER TABLE hr_iam_access_bindings ENABLE ROW LEVEL SECURITY;
ALTER TABLE hr_iam_access_bindings FORCE  ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON hr_iam_access_bindings;
CREATE POLICY tenant_isolation ON hr_iam_access_bindings FOR ALL
    USING (tenant_id::text = current_setting('app.tenant_id', true))
    WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true));

CREATE INDEX IF NOT EXISTS idx_hr_iam_access_bindings_user
    ON hr_iam_access_bindings (tenant_id, user_id, status);
CREATE INDEX IF NOT EXISTS idx_hr_iam_access_bindings_person
    ON hr_iam_access_bindings (tenant_id, person_id, status);
