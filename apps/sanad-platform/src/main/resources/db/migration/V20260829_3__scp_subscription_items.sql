-- ============================================================
-- V20260829_3: SCP-G1 — Subscription items (multi-product subscriptions)
--
-- A subscription is no longer assumed to be a single product/plan:
--   Subscription ── 1..N subscription_items
--     (PLAN | ADD_ON | METERED | OTHER)
--
-- Existing single-plan subscriptions are backfilled as exactly one ACTIVE
-- PLAN item, so the legacy model and the new item model stay dual-compatible.
-- `tenant_subscriptions.plan_id` is NOT dropped — deprecation is a separate
-- future effort.
--
-- tenant_id is mandatory (repo standard for tenant-owned data) + indexed.
-- Forward-only, additive, idempotent.
-- ============================================================

CREATE TABLE IF NOT EXISTS subscription_items (
    id                UUID            NOT NULL,
    tenant_id         UUID            NOT NULL,
    subscription_id   UUID            NOT NULL,
    item_type         VARCHAR(16)     NOT NULL,
    application_id    UUID,
    product_id        UUID,
    plan_id           UUID,
    plan_version_id   UUID,
    name_snapshot     VARCHAR(200),
    quantity          INTEGER         NOT NULL DEFAULT 1,
    unit_amount_minor BIGINT,
    currency_code     VARCHAR(3)      NOT NULL,
    status            VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE',
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_subscription_items PRIMARY KEY (id),
    CONSTRAINT fk_subscription_items_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_subscription_items_subscription FOREIGN KEY (subscription_id) REFERENCES tenant_subscriptions (id) ON DELETE CASCADE,
    CONSTRAINT fk_subscription_items_application FOREIGN KEY (application_id) REFERENCES applications (id),
    CONSTRAINT fk_subscription_items_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_subscription_items_plan FOREIGN KEY (plan_id) REFERENCES saas_plans (id),
    CONSTRAINT fk_subscription_items_plan_version FOREIGN KEY (plan_version_id) REFERENCES plan_versions (id),
    CONSTRAINT ck_subscription_items_type CHECK (item_type IN ('PLAN', 'ADD_ON', 'METERED', 'OTHER')),
    CONSTRAINT ck_subscription_items_status CHECK (status IN ('ACTIVE', 'PENDING', 'CANCELLED')),
    CONSTRAINT ck_subscription_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_subscription_items_unit_amount CHECK (unit_amount_minor IS NULL OR unit_amount_minor >= 0),
    CONSTRAINT ck_subscription_items_product_ref CHECK (
        plan_id IS NOT NULL OR product_id IS NOT NULL OR application_id IS NOT NULL
    )
);

CREATE INDEX IF NOT EXISTS idx_subscription_items_subscription
    ON subscription_items (subscription_id);
CREATE INDEX IF NOT EXISTS idx_subscription_items_tenant
    ON subscription_items (tenant_id);
CREATE INDEX IF NOT EXISTS idx_subscription_items_status
    ON subscription_items (status);

-- No duplicate ACTIVE PLAN item for the same plan on one subscription
-- (a subscription may carry ERP + HRM + CRM plans, but never the same plan twice).
CREATE UNIQUE INDEX IF NOT EXISTS uk_subscription_items_active_plan
    ON subscription_items (subscription_id, plan_id) WHERE item_type = 'PLAN' AND status = 'ACTIVE';

-- Backfill: one ACTIVE PLAN item per existing subscription (idempotent).
INSERT INTO subscription_items (
    id, tenant_id, subscription_id, item_type,
    application_id, product_id, plan_id, plan_version_id,
    name_snapshot, quantity, unit_amount_minor, currency_code,
    status, created_at, updated_at
)
SELECT gen_random_uuid(), ts.tenant_id, ts.id, 'PLAN',
       NULL, NULL, ts.plan_id, ts.plan_version_id,
       p.name, 1,
       CASE ts.billing_cycle WHEN 'ANNUAL' THEN p.annual_price_minor ELSE p.monthly_price_minor END,
       p.currency_code,
       'ACTIVE', ts.created_at, ts.updated_at
FROM tenant_subscriptions ts
JOIN saas_plans p ON p.id = ts.plan_id
WHERE NOT EXISTS (
    SELECT 1 FROM subscription_items si
    WHERE si.subscription_id = ts.id AND si.item_type = 'PLAN'
);
