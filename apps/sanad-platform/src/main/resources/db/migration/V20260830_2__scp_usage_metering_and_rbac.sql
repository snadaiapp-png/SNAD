-- ============================================================
-- V20260830_2: SCP-G4 — Usage metering foundation + granular RBAC
--
-- 1. `usage_metrics` / `usage_events` / `usage_aggregates` — metering
--    foundation. Ingestion is idempotent per (tenant, metric,
--    idempotency_key); aggregation is period-scoped; tables are
--    tenant-scoped and RLS-protected following the repo convention.
-- 2. Granular control-plane capability codes seeded additively (existing
--    EXECUTIVE_* capabilities stay valid); new codes granted to roles that
--    already hold the corresponding broad EXECUTIVE_* capability so current
--    administrators keep working (zero regression).
--
-- Forward-only, additive, idempotent.
-- ============================================================

-- ============================================================
-- STEP 1: usage metrics catalog
-- ============================================================
CREATE TABLE IF NOT EXISTS usage_metrics (
    id           UUID            NOT NULL,
    code         VARCHAR(80)     NOT NULL,
    name         VARCHAR(200)    NOT NULL,
    unit         VARCHAR(30)     NOT NULL,
    aggregation  VARCHAR(20)     NOT NULL DEFAULT 'SUM',
    limit_kind   VARCHAR(20)     NOT NULL DEFAULT 'HARD_LIMIT',
    description  VARCHAR(1000),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_usage_metrics PRIMARY KEY (id),
    CONSTRAINT uk_usage_metrics_code UNIQUE (code),
    CONSTRAINT ck_usage_metrics_agg CHECK (aggregation IN ('SUM', 'MAX', 'LAST')),
    CONSTRAINT ck_usage_metrics_limit_kind CHECK (limit_kind IN ('UNLIMITED', 'SOFT_LIMIT', 'HARD_LIMIT', 'OVERAGE', 'PAY_AS_YOU_GO'))
);

INSERT INTO usage_metrics (id, code, name, unit, aggregation, limit_kind, description, created_at, updated_at)
SELECT gen_random_uuid(), v.code, v.name, v.unit, v.aggregation, v.limit_kind, v.description, NOW(), NOW()
FROM (VALUES
    ('users',          'Licensed Users',      'count',   'SUM', 'HARD_LIMIT', 'Number of licensed user seats'),
    ('employees',      'Employees',           'count',   'SUM', 'HARD_LIMIT', 'Number of active employees (HRM)'),
    ('branches',       'Branches',            'count',   'MAX', 'HARD_LIMIT', 'Number of active branches (ERP)'),
    ('storage_gb',     'Storage',             'gigabyte','MAX', 'SOFT_LIMIT', 'Stored data volume'),
    ('api_requests',   'API Requests',        'count',   'SUM', 'OVERAGE',    'API requests in period'),
    ('ai_tokens',      'AI Tokens',           'token',   'SUM', 'HARD_LIMIT', 'AI tokens consumed in period'),
    ('pos_locations',  'POS Locations',       'count',   'MAX', 'HARD_LIMIT', 'Active POS terminals/locations'),
    ('invoices',       'Invoices',            'count',   'SUM', 'PAY_AS_YOU_GO', 'Invoices issued in period')
) AS v(code, name, unit, aggregation, limit_kind, description)
WHERE NOT EXISTS (SELECT 1 FROM usage_metrics m WHERE m.code = v.code);

-- ============================================================
-- STEP 2: usage events (idempotent ingestion)
-- ============================================================
CREATE TABLE IF NOT EXISTS usage_events (
    id               UUID            NOT NULL,
    tenant_id        UUID            NOT NULL,
    metric_code      VARCHAR(80)     NOT NULL,
    quantity         BIGINT          NOT NULL,
    source           VARCHAR(60),
    idempotency_key  VARCHAR(200)    NOT NULL,
    occurred_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_usage_events PRIMARY KEY (id),
    CONSTRAINT fk_usage_events_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE,
    CONSTRAINT uk_usage_events_idempotent UNIQUE (tenant_id, metric_code, idempotency_key),
    CONSTRAINT ck_usage_events_quantity CHECK (quantity >= 0)
);

CREATE INDEX IF NOT EXISTS idx_usage_events_tenant_period
    ON usage_events (tenant_id, metric_code, occurred_at DESC);

-- ============================================================
-- STEP 3: usage aggregates (period rollups)
-- ============================================================
CREATE TABLE IF NOT EXISTS usage_aggregates (
    id            UUID            NOT NULL,
    tenant_id     UUID            NOT NULL,
    metric_code   VARCHAR(80)     NOT NULL,
    period_type   VARCHAR(20)     NOT NULL,
    period_start  TIMESTAMP WITH TIME ZONE NOT NULL,
    total         BIGINT          NOT NULL DEFAULT 0,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_usage_aggregates PRIMARY KEY (id),
    CONSTRAINT fk_usage_agg_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE,
    CONSTRAINT uk_usage_agg_unique UNIQUE (tenant_id, metric_code, period_type, period_start),
    CONSTRAINT ck_usage_agg_period CHECK (period_type IN ('DAILY', 'MONTHLY', 'YEARLY', 'TOTAL')),
    CONSTRAINT ck_usage_agg_total CHECK (total >= 0)
);

CREATE INDEX IF NOT EXISTS idx_usage_agg_tenant ON usage_aggregates (tenant_id, metric_code, period_start DESC);

-- ============================================================
-- STEP 4: granular control-plane capability codes (additive RBAC)
-- ============================================================
INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), v.code, v.name, v.description, 'ACTIVE', NOW(), NOW()
FROM (VALUES
    ('subscription.read',        'Read subscriptions',            'View subscription data'),
    ('subscription.create',      'Create subscriptions',          'Create new subscriptions'),
    ('subscription.change_plan', 'Change subscription plans',     'Upgrade/downgrade subscription items'),
    ('subscription.cancel',      'Cancel subscriptions',          'Cancel subscriptions'),
    ('subscription.suspend',     'Suspend subscriptions',         'Suspend/resume subscriptions'),
    ('catalog.read',             'Read catalogs',                 'View application/product catalogs'),
    ('catalog.manage',           'Manage catalogs',               'Create/update catalog entries'),
    ('application.read',         'Read applications',             'View application catalog'),
    ('application.manage',       'Manage applications',           'Create/update applications'),
    ('plan.read',                'Read plans',                    'View plans and versions'),
    ('plan.manage',              'Manage plans',                  'Create/update plans and versions'),
    ('pricing.read',             'Read pricing',                  'View prices'),
    ('pricing.manage',           'Manage pricing',                'Create/update prices'),
    ('entitlement.read',         'Read entitlements',             'View effective entitlements'),
    ('entitlement.manage',       'Manage entitlements',           'Update plan/product entitlements'),
    ('entitlement.override',     'Override entitlements',         'Apply tenant-level overrides'),
    ('usage.read',               'Read usage',                    'View usage metering data'),
    ('billing.read',             'Read billing',                  'View invoices and billing data'),
    ('billing.adjust',           'Adjust billing',                'Billing adjustments'),
    ('provisioning.read',        'Read provisioning',             'View provisioning jobs'),
    ('provisioning.retry',       'Retry provisioning',            'Re-run failed provisioning jobs'),
    ('audit.read',               'Read audit',                    'View platform audit history')
) AS v(code, name, description)
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities c WHERE c.code = v.code);

-- Grant every granular capability to any role that already holds the broad
-- EXECUTIVE_* capabilities (backward compatibility: current admins keep all
-- current powers). role_capabilities rows are tenant-scoped — tenant_id is
-- carried from the existing grant.
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), rc.tenant_id, rc.role_id, c.id, NOW()
FROM access_capabilities c
JOIN access_capabilities broad ON broad.code = 'EXECUTIVE_MANAGE'
JOIN role_capabilities rc ON rc.capability_id = broad.id
WHERE (c.code LIKE '%.read' OR c.code LIKE '%.manage'
   OR c.code IN ('subscription.create', 'subscription.change_plan',
                 'subscription.cancel', 'subscription.suspend',
                 'entitlement.override', 'billing.adjust', 'provisioning.retry'))
  AND NOT EXISTS (
      SELECT 1 FROM role_capabilities x
      WHERE x.tenant_id = rc.tenant_id AND x.role_id = rc.role_id
        AND x.capability_id = c.id);

-- Read-only mirror: roles with EXECUTIVE_VIEW get the *.read codes only.
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), rc.tenant_id, rc.role_id, c.id, NOW()
FROM access_capabilities c
JOIN access_capabilities broad ON broad.code = 'EXECUTIVE_VIEW'
JOIN role_capabilities rc ON rc.capability_id = broad.id
WHERE c.code LIKE '%.read'
  AND NOT EXISTS (
      SELECT 1 FROM role_capabilities x
      WHERE x.tenant_id = rc.tenant_id AND x.role_id = rc.role_id
        AND x.capability_id = c.id);

-- ============================================================
-- STEP 5: RLS on tenant-scoped usage tables (fail-closed, repo convention)
-- ============================================================
ALTER TABLE usage_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE usage_events FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS usage_events_tenant_isolation ON usage_events;
CREATE POLICY usage_events_tenant_isolation ON usage_events
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE usage_aggregates ENABLE ROW LEVEL SECURITY;
ALTER TABLE usage_aggregates FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS usage_aggregates_tenant_isolation ON usage_aggregates;
CREATE POLICY usage_aggregates_tenant_isolation ON usage_aggregates
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);
