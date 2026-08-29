-- ============================================================
-- V20260829_2: SCP-G1 — Products + Plan versioning foundation
--
-- 1. `products` — billable product catalog (APPLICATION / ADD_ON / METERED /
--    OTHER). Platform-scoped catalog, like `applications`.
-- 2. `plan_versions` — versioned plan contracts. Editing a plan never mutates
--    what existing subscribers contracted: they stay pinned to the version
--    recorded on their subscription/item until an explicit change/renewal.
--    Backfill: one ACTIVE `v1` per existing saas_plans row.
-- 3. `tenant_subscriptions.plan_version_id` — additive nullable column with
--    backfill to each subscription plan's ACTIVE version. `plan_id` remains
--    (dual-compatible read); no destructive change.
--
-- Money columns follow the repo standard: BIGINT minor units + currency code.
-- Forward-only, additive, idempotent.
-- ============================================================

-- ============================================================
-- STEP 1: products catalog
-- ============================================================
CREATE TABLE IF NOT EXISTS products (
    id              UUID            NOT NULL,
    code            VARCHAR(50)     NOT NULL,
    name            VARCHAR(200)    NOT NULL,
    description     VARCHAR(1000),
    application_id  UUID,
    product_type    VARCHAR(20)     NOT NULL DEFAULT 'OTHER',
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_products PRIMARY KEY (id),
    CONSTRAINT uk_products_code UNIQUE (code),
    CONSTRAINT fk_products_application FOREIGN KEY (application_id) REFERENCES applications (id),
    CONSTRAINT ck_products_type CHECK (product_type IN ('APPLICATION', 'ADD_ON', 'METERED', 'OTHER')),
    CONSTRAINT ck_products_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED'))
);

CREATE INDEX IF NOT EXISTS idx_products_application ON products (application_id);
CREATE INDEX IF NOT EXISTS idx_products_status ON products (status);

-- ============================================================
-- STEP 2: plan_versions
-- ============================================================
CREATE TABLE IF NOT EXISTS plan_versions (
    id                  UUID            NOT NULL,
    plan_id             UUID            NOT NULL,
    version_number      INTEGER         NOT NULL,
    status              VARCHAR(20)     NOT NULL,
    effective_from      TIMESTAMP WITH TIME ZONE,
    effective_to        TIMESTAMP WITH TIME ZONE,
    currency_code       VARCHAR(3)      NOT NULL,
    monthly_price_minor BIGINT          NOT NULL,
    annual_price_minor  BIGINT          NOT NULL,
    trial_days          INTEGER         NOT NULL,
    max_users           INTEGER         NOT NULL,
    max_organizations   INTEGER         NOT NULL,
    storage_mb          BIGINT          NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_plan_versions PRIMARY KEY (id),
    CONSTRAINT uk_plan_versions_number UNIQUE (plan_id, version_number),
    CONSTRAINT fk_plan_versions_plan FOREIGN KEY (plan_id) REFERENCES saas_plans (id) ON DELETE CASCADE,
    CONSTRAINT ck_plan_versions_status CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED')),
    CONSTRAINT ck_plan_versions_prices CHECK (monthly_price_minor >= 0 AND annual_price_minor >= 0),
    CONSTRAINT ck_plan_versions_trial CHECK (trial_days BETWEEN 0 AND 365),
    CONSTRAINT ck_plan_versions_limits CHECK (max_users > 0 AND max_organizations > 0 AND storage_mb >= 0)
);

-- At most one ACTIVE version per plan (partial unique index).
CREATE UNIQUE INDEX IF NOT EXISTS uk_plan_versions_one_active
    ON plan_versions (plan_id) WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_plan_versions_plan ON plan_versions (plan_id, version_number DESC);

-- Backfill: clone every existing plan into an initial ACTIVE v1 (idempotent).
INSERT INTO plan_versions (
    id, plan_id, version_number, status, effective_from, effective_to,
    currency_code, monthly_price_minor, annual_price_minor, trial_days,
    max_users, max_organizations, storage_mb, created_at, updated_at
)
SELECT gen_random_uuid(), p.id, 1, 'ACTIVE', p.created_at, NULL,
       p.currency_code, p.monthly_price_minor, p.annual_price_minor, p.trial_days,
       p.max_users, p.max_organizations, p.storage_mb, NOW(), NOW()
FROM saas_plans p
WHERE NOT EXISTS (SELECT 1 FROM plan_versions pv WHERE pv.plan_id = p.id);

-- ============================================================
-- STEP 3: tenant_subscriptions.plan_version_id (additive, dual-compatible)
-- ============================================================
ALTER TABLE tenant_subscriptions
    ADD COLUMN IF NOT EXISTS plan_version_id UUID;

-- Re-assert the FK (idempotent-safe: only added when missing).
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_tenant_subscriptions_plan_version'
    ) THEN
        ALTER TABLE tenant_subscriptions
            ADD CONSTRAINT fk_tenant_subscriptions_plan_version
            FOREIGN KEY (plan_version_id) REFERENCES plan_versions (id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_tenant_subscriptions_plan_version
    ON tenant_subscriptions (plan_version_id);

-- Backfill: pin every existing subscription to its plan's ACTIVE version.
UPDATE tenant_subscriptions ts
SET plan_version_id = sub.id
FROM (
    SELECT DISTINCT ON (pv.plan_id) pv.id, pv.plan_id
    FROM plan_versions pv
    WHERE pv.status = 'ACTIVE'
    ORDER BY pv.plan_id, pv.version_number DESC
) sub
WHERE ts.plan_version_id IS NULL
  AND sub.plan_id = ts.plan_id;
