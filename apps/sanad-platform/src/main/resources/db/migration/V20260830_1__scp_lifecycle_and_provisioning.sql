-- ============================================================
-- V20260830_1: SCP-G3 — Subscription lifecycle, command ledger,
--               provisioning jobs
--
-- 1. Widens the tenant_subscriptions.status CHECK additively: the five
--    legacy values stay valid; new lifecycle states are added. Transitions
--    are enforced in the domain (SubscriptionLifecycle), not by the UI.
-- 2. `subscription_commands` — audit-friendly ledger of every lifecycle
--    command (from/to status, actor, reason, correlation).
-- 3. `provisioning_jobs` + `provisioning_job_steps` — a subscription becomes
--    ACTIVE only after its provisioning job succeeds. Steps are keyed
--    (UNIQUE job+step) so retries are safe/idempotent.
--
-- Forward-only, additive; no column is dropped or renamed.
-- ============================================================

-- ============================================================
-- STEP 1: widen the status CHECK (additive)
-- ============================================================
ALTER TABLE tenant_subscriptions DROP CONSTRAINT IF EXISTS ck_tenant_subscriptions_status;
ALTER TABLE tenant_subscriptions ADD CONSTRAINT ck_tenant_subscriptions_status
    CHECK (status IN (
        -- legacy values (kept for backward compatibility)
        'TRIALING', 'ACTIVE', 'PAST_DUE', 'SUSPENDED', 'CANCELLED',
        -- full lifecycle
        'DRAFT', 'PENDING_ACTIVATION', 'PENDING_PAYMENT', 'TRIAL',
        'GRACE_PERIOD', 'PAUSED', 'EXPIRED', 'TERMINATED'
    ));

-- ============================================================
-- STEP 2: subscription_commands ledger
-- ============================================================
CREATE TABLE IF NOT EXISTS subscription_commands (
    id               UUID            NOT NULL,
    subscription_id  UUID            NOT NULL,
    tenant_id        UUID            NOT NULL,
    command          VARCHAR(40)     NOT NULL,
    from_status      VARCHAR(24)     NOT NULL,
    to_status        VARCHAR(24)     NOT NULL,
    reason           VARCHAR(500),
    actor_tenant_id  UUID,
    actor_user_id    UUID,
    correlation_id   VARCHAR(100),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_subscription_commands PRIMARY KEY (id),
    CONSTRAINT fk_sub_commands_subscription FOREIGN KEY (subscription_id) REFERENCES tenant_subscriptions (id) ON DELETE CASCADE,
    CONSTRAINT fk_sub_commands_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);

CREATE INDEX IF NOT EXISTS idx_sub_commands_subscription
    ON subscription_commands (subscription_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_sub_commands_tenant
    ON subscription_commands (tenant_id, created_at DESC);

-- ============================================================
-- STEP 3: provisioning jobs + keyed steps
-- ============================================================
CREATE TABLE IF NOT EXISTS provisioning_jobs (
    id               UUID            NOT NULL,
    tenant_id        UUID            NOT NULL,
    subscription_id  UUID            NOT NULL,
    action           VARCHAR(40)     NOT NULL,
    status           VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    attempts         INTEGER         NOT NULL DEFAULT 0,
    started_at       TIMESTAMP WITH TIME ZONE,
    completed_at     TIMESTAMP WITH TIME ZONE,
    error_code       VARCHAR(100),
    error_message    VARCHAR(1000),
    correlation_id   VARCHAR(100),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_provisioning_jobs PRIMARY KEY (id),
    CONSTRAINT fk_prov_jobs_subscription FOREIGN KEY (subscription_id) REFERENCES tenant_subscriptions (id) ON DELETE CASCADE,
    CONSTRAINT fk_prov_jobs_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT ck_prov_jobs_action CHECK (action IN ('PROVISION_SUBSCRIPTION', 'DEPROVISION_SUBSCRIPTION', 'REPROVISION_SUBSCRIPTION')),
    CONSTRAINT ck_prov_jobs_status CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'RETRYING'))
);

CREATE INDEX IF NOT EXISTS idx_prov_jobs_tenant ON provisioning_jobs (tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_prov_jobs_subscription ON provisioning_jobs (subscription_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_prov_jobs_status ON provisioning_jobs (status, created_at DESC);

CREATE TABLE IF NOT EXISTS provisioning_job_steps (
    id           UUID            NOT NULL,
    job_id       UUID            NOT NULL,
    step_key     VARCHAR(60)     NOT NULL,
    status       VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    detail       VARCHAR(1000),
    started_at   TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_provisioning_job_steps PRIMARY KEY (id),
    CONSTRAINT fk_prov_steps_job FOREIGN KEY (job_id) REFERENCES provisioning_jobs (id) ON DELETE CASCADE,
    CONSTRAINT uk_prov_steps_job_key UNIQUE (job_id, step_key),
    CONSTRAINT ck_prov_steps_status CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'SKIPPED'))
);

CREATE INDEX IF NOT EXISTS idx_prov_steps_job ON provisioning_job_steps (job_id, created_at);
