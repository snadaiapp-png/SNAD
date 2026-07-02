CREATE TABLE saas_plans (
    id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(1000),
    status VARCHAR(20) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    monthly_price_minor BIGINT NOT NULL,
    annual_price_minor BIGINT NOT NULL,
    trial_days INTEGER NOT NULL,
    max_users INTEGER NOT NULL,
    max_organizations INTEGER NOT NULL,
    storage_mb BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_saas_plans PRIMARY KEY (id),
    CONSTRAINT uk_saas_plans_code UNIQUE (code),
    CONSTRAINT ck_saas_plans_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_saas_plans_prices CHECK (monthly_price_minor >= 0 AND annual_price_minor >= 0),
    CONSTRAINT ck_saas_plans_trial CHECK (trial_days BETWEEN 0 AND 365),
    CONSTRAINT ck_saas_plans_limits CHECK (max_users > 0 AND max_organizations > 0 AND storage_mb >= 0)
);

CREATE TABLE saas_plan_entitlements (
    id UUID NOT NULL,
    plan_id UUID NOT NULL,
    feature_code VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL,
    limit_value BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_saas_plan_entitlements PRIMARY KEY (id),
    CONSTRAINT fk_saas_plan_entitlements_plan FOREIGN KEY (plan_id) REFERENCES saas_plans (id) ON DELETE CASCADE,
    CONSTRAINT uk_saas_plan_entitlements_feature UNIQUE (plan_id, feature_code),
    CONSTRAINT ck_saas_plan_entitlements_limit CHECK (limit_value IS NULL OR limit_value >= 0)
);

CREATE TABLE tenant_subscriptions (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    plan_id UUID NOT NULL,
    pending_plan_id UUID,
    status VARCHAR(24) NOT NULL,
    billing_cycle VARCHAR(16) NOT NULL,
    pending_billing_cycle VARCHAR(16),
    seat_quantity INTEGER NOT NULL,
    credit_balance_minor BIGINT NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    trial_ends_at TIMESTAMP WITH TIME ZONE,
    current_period_start TIMESTAMP WITH TIME ZONE NOT NULL,
    current_period_end TIMESTAMP WITH TIME ZONE NOT NULL,
    cancel_at_period_end BOOLEAN NOT NULL,
    cancelled_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_tenant_subscriptions PRIMARY KEY (id),
    CONSTRAINT uk_tenant_subscriptions_tenant UNIQUE (tenant_id),
    CONSTRAINT fk_tenant_subscriptions_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_tenant_subscriptions_plan FOREIGN KEY (plan_id) REFERENCES saas_plans (id),
    CONSTRAINT fk_tenant_subscriptions_pending_plan FOREIGN KEY (pending_plan_id) REFERENCES saas_plans (id),
    CONSTRAINT ck_tenant_subscriptions_status CHECK (status IN ('TRIALING', 'ACTIVE', 'PAST_DUE', 'SUSPENDED', 'CANCELLED')),
    CONSTRAINT ck_tenant_subscriptions_cycle CHECK (billing_cycle IN ('MONTHLY', 'ANNUAL')),
    CONSTRAINT ck_tenant_subscriptions_pending_cycle CHECK (pending_billing_cycle IS NULL OR pending_billing_cycle IN ('MONTHLY', 'ANNUAL')),
    CONSTRAINT ck_tenant_subscriptions_seats CHECK (seat_quantity > 0),
    CONSTRAINT ck_tenant_subscriptions_credit CHECK (credit_balance_minor >= 0)
);

CREATE TABLE subscription_change_events (
    id UUID NOT NULL,
    subscription_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    action VARCHAR(50) NOT NULL,
    old_plan_id UUID,
    new_plan_id UUID,
    effective_mode VARCHAR(20),
    adjustment_minor BIGINT NOT NULL,
    reason VARCHAR(500),
    actor_tenant_id UUID,
    actor_user_id UUID,
    effective_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_subscription_change_events PRIMARY KEY (id),
    CONSTRAINT fk_subscription_change_events_subscription FOREIGN KEY (subscription_id) REFERENCES tenant_subscriptions (id) ON DELETE CASCADE,
    CONSTRAINT fk_subscription_change_events_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_subscription_change_events_old_plan FOREIGN KEY (old_plan_id) REFERENCES saas_plans (id),
    CONSTRAINT fk_subscription_change_events_new_plan FOREIGN KEY (new_plan_id) REFERENCES saas_plans (id)
);

CREATE TABLE billing_invoices (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    subscription_id UUID NOT NULL,
    invoice_number VARCHAR(60) NOT NULL,
    status VARCHAR(20) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    subtotal_minor BIGINT NOT NULL,
    credit_applied_minor BIGINT NOT NULL,
    tax_minor BIGINT NOT NULL,
    total_minor BIGINT NOT NULL,
    amount_paid_minor BIGINT NOT NULL,
    description VARCHAR(500),
    period_start TIMESTAMP WITH TIME ZONE NOT NULL,
    period_end TIMESTAMP WITH TIME ZONE NOT NULL,
    due_at TIMESTAMP WITH TIME ZONE NOT NULL,
    paid_at TIMESTAMP WITH TIME ZONE,
    payment_reference VARCHAR(200),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_billing_invoices PRIMARY KEY (id),
    CONSTRAINT uk_billing_invoices_number UNIQUE (invoice_number),
    CONSTRAINT fk_billing_invoices_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_billing_invoices_subscription FOREIGN KEY (subscription_id) REFERENCES tenant_subscriptions (id),
    CONSTRAINT ck_billing_invoices_status CHECK (status IN ('DRAFT', 'OPEN', 'PAID', 'VOID')),
    CONSTRAINT ck_billing_invoices_amounts CHECK (
        subtotal_minor >= 0 AND credit_applied_minor >= 0 AND tax_minor >= 0 AND total_minor >= 0 AND amount_paid_minor >= 0
    )
);

ALTER TABLE organization_memberships
    ADD COLUMN role_code VARCHAR(100) NOT NULL DEFAULT 'MEMBER';

CREATE INDEX idx_saas_plan_entitlements_plan ON saas_plan_entitlements (plan_id);
CREATE INDEX idx_tenant_subscriptions_status ON tenant_subscriptions (status);
CREATE INDEX idx_subscription_change_events_subscription ON subscription_change_events (subscription_id, created_at DESC);
CREATE INDEX idx_billing_invoices_tenant ON billing_invoices (tenant_id, created_at DESC);
CREATE INDEX idx_billing_invoices_status ON billing_invoices (status, due_at);

INSERT INTO saas_plans (
    id, code, name, description, status, currency_code,
    monthly_price_minor, annual_price_minor, trial_days,
    max_users, max_organizations, storage_mb, created_at, updated_at
) VALUES
    (CAST('c3000000-0000-0000-0000-000000000001' AS uuid), 'STARTER', 'البداية', 'للمنشآت الصغيرة التي تبدأ تشغيل أعمالها على سند', 'ACTIVE', 'SAR', 9900, 99000, 14, 5, 1, 5120, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (CAST('c3000000-0000-0000-0000-000000000002' AS uuid), 'GROWTH', 'النمو', 'للشركات النامية متعددة الفرق والعمليات', 'ACTIVE', 'SAR', 29900, 299000, 14, 25, 5, 51200, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (CAST('c3000000-0000-0000-0000-000000000003' AS uuid), 'ENTERPRISE', 'المؤسسات', 'للشركات الكبيرة ومتعددة الكيانات مع حوكمة متقدمة', 'ACTIVE', 'SAR', 99900, 999000, 30, 250, 50, 512000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO saas_plan_entitlements (id, plan_id, feature_code, enabled, limit_value, created_at, updated_at) VALUES
    (CAST('c3100000-0000-0000-0000-000000000001' AS uuid), CAST('c3000000-0000-0000-0000-000000000001' AS uuid), 'CRM', TRUE, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (CAST('c3100000-0000-0000-0000-000000000002' AS uuid), CAST('c3000000-0000-0000-0000-000000000001' AS uuid), 'WORKFLOW', TRUE, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (CAST('c3100000-0000-0000-0000-000000000003' AS uuid), CAST('c3000000-0000-0000-0000-000000000002' AS uuid), 'CRM', TRUE, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (CAST('c3100000-0000-0000-0000-000000000004' AS uuid), CAST('c3000000-0000-0000-0000-000000000002' AS uuid), 'ERP', TRUE, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (CAST('c3100000-0000-0000-0000-000000000005' AS uuid), CAST('c3000000-0000-0000-0000-000000000002' AS uuid), 'WORKFLOW', TRUE, 100, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (CAST('c3100000-0000-0000-0000-000000000006' AS uuid), CAST('c3000000-0000-0000-0000-000000000003' AS uuid), 'CRM', TRUE, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (CAST('c3100000-0000-0000-0000-000000000007' AS uuid), CAST('c3000000-0000-0000-0000-000000000003' AS uuid), 'ERP', TRUE, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (CAST('c3100000-0000-0000-0000-000000000008' AS uuid), CAST('c3000000-0000-0000-0000-000000000003' AS uuid), 'HRM', TRUE, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (CAST('c3100000-0000-0000-0000-000000000009' AS uuid), CAST('c3000000-0000-0000-0000-000000000003' AS uuid), 'ACCOUNTING', TRUE, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (CAST('c3100000-0000-0000-0000-000000000010' AS uuid), CAST('c3000000-0000-0000-0000-000000000003' AS uuid), 'WORKFLOW', TRUE, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (CAST('c3100000-0000-0000-0000-000000000011' AS uuid), CAST('c3000000-0000-0000-0000-000000000003' AS uuid), 'AI', TRUE, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);