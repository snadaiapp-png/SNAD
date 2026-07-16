-- ============================================================
-- SNAD Platform — Create CRM Products table
-- ------------------------------------------------------------
-- Branch: feature/crm-products
-- Gate:   CRM Phase 4 — Products/Services (#9)
--
-- Creates crm_products for product/service catalog management.
-- Products can be linked to opportunities (for quotations) and
-- have optional category, SKU, pricing, and tax info.
--
-- Seeds CRM.PRODUCT.READ and CRM.PRODUCT.WRITE capabilities.
--
-- Portable SQL — works on PostgreSQL 16 and H2 (PostgreSQL mode).
-- ============================================================

CREATE TABLE IF NOT EXISTS crm_products (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    name VARCHAR(240) NOT NULL,
    sku VARCHAR(80),
    description TEXT,

    product_type VARCHAR(24) NOT NULL DEFAULT 'PRODUCT',
    category VARCHAR(120),

    unit_price DECIMAL(15, 2) NOT NULL DEFAULT 0,
    currency_code VARCHAR(3) NOT NULL DEFAULT 'USD',
    tax_rate DECIMAL(5, 2) NOT NULL DEFAULT 0,

    unit VARCHAR(40) NOT NULL DEFAULT 'EA',
    active BOOLEAN NOT NULL DEFAULT TRUE,

    created_by UUID NOT NULL,
    updated_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_crm_products PRIMARY KEY (id),
    CONSTRAINT uk_crm_products_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_crm_products_tenant_sku UNIQUE (tenant_id, sku),
    CONSTRAINT fk_crm_products_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT ck_crm_products_type CHECK (product_type IN ('PRODUCT', 'SERVICE')),
    CONSTRAINT ck_crm_products_unit_price CHECK (unit_price >= 0),
    CONSTRAINT ck_crm_products_tax_rate CHECK (tax_rate >= 0 AND tax_rate <= 100)
);

CREATE INDEX IF NOT EXISTS idx_crm_products_tenant_active
    ON crm_products (tenant_id, active, name);

CREATE INDEX IF NOT EXISTS idx_crm_products_tenant_category
    ON crm_products (tenant_id, category);

-- ============================================================
-- Seed CRM.PRODUCT.READ and CRM.PRODUCT.WRITE capabilities
-- ============================================================

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), capability.code, capability.name, capability.description, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (VALUES
    ('CRM.PRODUCT.READ',  'Read CRM Products',  'View tenant product catalog'),
    ('CRM.PRODUCT.WRITE', 'Write CRM Products', 'Create and update products')
) AS capability(code, name, description)
WHERE NOT EXISTS (
    SELECT 1 FROM access_capabilities existing WHERE existing.code = capability.code
);

INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), role.tenant_id, role.id, capability.id, CURRENT_TIMESTAMP
FROM roles role
JOIN access_capabilities capability
    ON capability.code IN ('CRM.PRODUCT.READ', 'CRM.PRODUCT.WRITE')
    AND capability.status = 'ACTIVE'
WHERE role.code = 'ADMIN'
  AND role.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1 FROM role_capabilities existing
      WHERE existing.tenant_id = role.tenant_id
        AND existing.role_id = role.id
        AND existing.capability_id = capability.id
  );
