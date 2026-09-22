-- ============================================================================
-- V20260922_3 — Subscription operating-unit / branch attribution
--
-- Forward-only additive model for #1055 WS8. Existing tenant-wide subscriptions
-- remain valid. Organizations become the canonical operating-unit identities;
-- branch attribution is optional and cannot cross tenant boundaries.
-- ============================================================================

ALTER TABLE organizations
    ADD COLUMN IF NOT EXISTS unit_type VARCHAR(24) NOT NULL DEFAULT 'GENERAL';

ALTER TABLE organizations DROP CONSTRAINT IF EXISTS ck_organizations_unit_type;
ALTER TABLE organizations
    ADD CONSTRAINT ck_organizations_unit_type
    CHECK (unit_type IN ('GENERAL','LEGAL_ENTITY','BRANCH','DEPARTMENT','LOCATION'));

CREATE INDEX IF NOT EXISTS idx_organizations_tenant_unit_type
    ON organizations (tenant_id, unit_type, status);

CREATE TABLE IF NOT EXISTS subscription_operating_units (
    id                UUID NOT NULL,
    tenant_id         UUID NOT NULL,
    subscription_id   UUID NOT NULL,
    organization_id   UUID NOT NULL,
    status            VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    billing_mode      VARCHAR(16) NOT NULL DEFAULT 'CONSOLIDATED',
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_subscription_operating_units PRIMARY KEY (id),
    CONSTRAINT fk_sub_operating_unit_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_sub_operating_unit_subscription
        FOREIGN KEY (subscription_id) REFERENCES tenant_subscriptions(id),
    CONSTRAINT fk_sub_operating_unit_organization
        FOREIGN KEY (tenant_id, organization_id) REFERENCES organizations(tenant_id, id),
    CONSTRAINT uk_sub_operating_unit UNIQUE (subscription_id, organization_id),
    CONSTRAINT ck_sub_operating_unit_status CHECK (status IN ('ACTIVE','INACTIVE')),
    CONSTRAINT ck_sub_operating_unit_billing CHECK (billing_mode IN ('CONSOLIDATED','SEPARATE'))
);

CREATE INDEX IF NOT EXISTS idx_sub_operating_units_tenant_org
    ON subscription_operating_units (tenant_id, organization_id, status);

CREATE TABLE IF NOT EXISTS subscription_unit_applications (
    id                UUID NOT NULL,
    tenant_id         UUID NOT NULL,
    subscription_id   UUID NOT NULL,
    organization_id   UUID NOT NULL,
    application_id    UUID NOT NULL,
    enabled           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_subscription_unit_applications PRIMARY KEY (id),
    CONSTRAINT fk_sub_unit_app_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_sub_unit_app_subscription
        FOREIGN KEY (subscription_id) REFERENCES tenant_subscriptions(id),
    CONSTRAINT fk_sub_unit_app_organization
        FOREIGN KEY (tenant_id, organization_id) REFERENCES organizations(tenant_id, id),
    CONSTRAINT fk_sub_unit_app_application
        FOREIGN KEY (application_id) REFERENCES applications(id),
    CONSTRAINT uk_sub_unit_app UNIQUE (subscription_id, organization_id, application_id)
);

CREATE INDEX IF NOT EXISTS idx_sub_unit_apps_tenant_org
    ON subscription_unit_applications (tenant_id, organization_id, enabled);

CREATE TABLE IF NOT EXISTS subscription_billing_profiles (
    id                UUID NOT NULL,
    tenant_id         UUID NOT NULL,
    subscription_id   UUID NOT NULL,
    organization_id   UUID,
    profile_name      VARCHAR(160) NOT NULL,
    billing_email     VARCHAR(255),
    currency_code     VARCHAR(3) NOT NULL,
    billing_mode      VARCHAR(16) NOT NULL DEFAULT 'CONSOLIDATED',
    status            VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_subscription_billing_profiles PRIMARY KEY (id),
    CONSTRAINT fk_sub_billing_profile_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_sub_billing_profile_subscription
        FOREIGN KEY (subscription_id) REFERENCES tenant_subscriptions(id),
    CONSTRAINT fk_sub_billing_profile_organization
        FOREIGN KEY (tenant_id, organization_id) REFERENCES organizations(tenant_id, id),
    CONSTRAINT ck_sub_billing_profile_mode CHECK (billing_mode IN ('CONSOLIDATED','SEPARATE')),
    CONSTRAINT ck_sub_billing_profile_status CHECK (status IN ('ACTIVE','INACTIVE')),
    CONSTRAINT ck_sub_billing_profile_currency CHECK (currency_code ~ '^[A-Z]{3}$')
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sub_billing_profile_tenant_scope
    ON subscription_billing_profiles(subscription_id)
    WHERE organization_id IS NULL AND status = 'ACTIVE';

CREATE UNIQUE INDEX IF NOT EXISTS uk_sub_billing_profile_org_scope
    ON subscription_billing_profiles(subscription_id, organization_id)
    WHERE organization_id IS NOT NULL AND status = 'ACTIVE';

ALTER TABLE subscription_items ADD COLUMN IF NOT EXISTS organization_id UUID;
ALTER TABLE usage_events ADD COLUMN IF NOT EXISTS organization_id UUID;
ALTER TABLE websites ADD COLUMN IF NOT EXISTS organization_id UUID;
ALTER TABLE commerce_stores ADD COLUMN IF NOT EXISTS organization_id UUID;

ALTER TABLE subscription_items DROP CONSTRAINT IF EXISTS fk_subscription_items_organization;
ALTER TABLE subscription_items
    ADD CONSTRAINT fk_subscription_items_organization
    FOREIGN KEY (tenant_id, organization_id) REFERENCES organizations(tenant_id, id);

ALTER TABLE usage_events DROP CONSTRAINT IF EXISTS fk_usage_events_organization;
ALTER TABLE usage_events
    ADD CONSTRAINT fk_usage_events_organization
    FOREIGN KEY (tenant_id, organization_id) REFERENCES organizations(tenant_id, id);

ALTER TABLE websites DROP CONSTRAINT IF EXISTS fk_websites_organization;
ALTER TABLE websites
    ADD CONSTRAINT fk_websites_organization
    FOREIGN KEY (tenant_id, organization_id) REFERENCES organizations(tenant_id, id);

ALTER TABLE commerce_stores DROP CONSTRAINT IF EXISTS fk_commerce_stores_organization;
ALTER TABLE commerce_stores
    ADD CONSTRAINT fk_commerce_stores_organization
    FOREIGN KEY (tenant_id, organization_id) REFERENCES organizations(tenant_id, id);

CREATE INDEX IF NOT EXISTS idx_subscription_items_tenant_org
    ON subscription_items(tenant_id, organization_id) WHERE organization_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_usage_events_tenant_org_period
    ON usage_events(tenant_id, organization_id, metric_code, occurred_at DESC)
    WHERE organization_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_websites_tenant_org
    ON websites(tenant_id, organization_id) WHERE organization_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_commerce_stores_tenant_org
    ON commerce_stores(tenant_id, organization_id) WHERE organization_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS subscription_resource_bindings (
    id                UUID NOT NULL,
    tenant_id         UUID NOT NULL,
    subscription_id   UUID NOT NULL,
    organization_id   UUID NOT NULL,
    resource_type     VARCHAR(24) NOT NULL,
    resource_id       UUID NOT NULL,
    status            VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_subscription_resource_bindings PRIMARY KEY (id),
    CONSTRAINT fk_sub_resource_binding_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_sub_resource_binding_subscription
        FOREIGN KEY (subscription_id) REFERENCES tenant_subscriptions(id),
    CONSTRAINT fk_sub_resource_binding_organization
        FOREIGN KEY (tenant_id, organization_id) REFERENCES organizations(tenant_id, id),
    CONSTRAINT uk_sub_resource_binding UNIQUE (subscription_id, resource_type, resource_id),
    CONSTRAINT ck_sub_resource_binding_type
        CHECK (resource_type IN ('WEBSITE','STORE','POS_LOCATION')),
    CONSTRAINT ck_sub_resource_binding_status CHECK (status IN ('ACTIVE','INACTIVE'))
);

CREATE INDEX IF NOT EXISTS idx_sub_resource_binding_org
    ON subscription_resource_bindings(tenant_id, organization_id, resource_type, status);
