-- ============================================================
-- V20260814_1: Module Registry — Centralized catalog of SNAD modules
--
-- Creates the `modules` table representing official SNAD modules:
--   AI, WORKFLOW, ERP, CRM, FINANCE, ANALYTICS, HRM, POS,
--   ECOMMERCE_CX, INDUSTRY_SOLUTIONS
--
-- This registry is NOT tenant-scoped — it is a global catalog.
-- Tenant-specific entitlements are linked via plan_module_entitlements.
--
-- Idempotent: uses IF NOT EXISTS / WHERE NOT EXISTS.
-- Backward compatible: does not touch existing saas_plans or entitlements.
-- ============================================================

-- ============================================================
-- STEP 1: Create modules table (global catalog)
-- ============================================================
CREATE TABLE IF NOT EXISTS modules (
    id              UUID            NOT NULL,
    code            VARCHAR(50)     NOT NULL,
    name            VARCHAR(200)   NOT NULL,
    description     VARCHAR(1000),
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    display_order   INTEGER         NOT NULL DEFAULT 0,
    version         VARCHAR(20),
    enabled         BOOLEAN         NOT NULL DEFAULT true,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_modules PRIMARY KEY (id),
    CONSTRAINT uk_modules_code UNIQUE (code),
    CONSTRAINT ck_modules_status CHECK (status IN ('ACTIVE','INACTIVE','DEPRECATED'))
);

CREATE INDEX IF NOT EXISTS idx_modules_status ON modules(status);
CREATE INDEX IF NOT EXISTS idx_modules_display_order ON modules(display_order);

-- ============================================================
-- STEP 2: Seed default modules (idempotent)
-- Ordered by display_order. Codes are uppercase with underscores.
--
-- NOTE: Use explicit INSERT...SELECT with explicit column list (not VALUES tuple)
-- to avoid PostgreSQL type inference issues (gen_random_uuid() in VALUES
-- causes ambiguous type inference for the seed tuple).
-- ============================================================
INSERT INTO modules (id, code, name, description, status, display_order, version, enabled, created_at, updated_at)
SELECT gen_random_uuid(), v.code, v.name, v.description, v.status, v.display_order, v.version, v.enabled, NOW(), NOW()
FROM (VALUES
    ('CRM',               'CRM',               'Customer Relationship Management — accounts, contacts, leads, opportunities, pipeline, tasks, activities', 'ACTIVE', 10, '1.0', true),
    ('AI',                'AI',                'Artificial Intelligence — agents, inference, automation, recommendations',                              'ACTIVE', 20, '1.0', true),
    ('WORKFLOW',          'Workflow',          'Business process orchestration — workflows, approvals, executions',                                      'ACTIVE', 30, '1.0', true),
    ('ERP',               'ERP',               'Enterprise Resource Planning — inventory, purchasing, supply chain',                                    'ACTIVE', 40, '1.0', true),
    ('FINANCE',           'Finance',           'Financial management — invoices, payments, accounting, ledgers',                                        'ACTIVE', 50, '1.0', true),
    ('ANALYTICS',         'Analytics',         'Business intelligence — dashboards, reports, KPIs, data visualization',                                  'ACTIVE', 60, '1.0', true),
    ('HRM',               'HRM',               'Human Resource Management — employees, payroll, attendance, recruitment',                              'ACTIVE', 70, '1.0', true),
    ('POS',               'POS',               'Point of Sale — terminals, transactions, receipts',                                                     'ACTIVE', 80, '1.0', true),
    ('ECOMMERCE_CX',      'Ecommerce/CX',     'E-commerce and Customer Experience — storefronts, carts, checkout, CX',                                  'ACTIVE', 90, '1.0', true),
    ('INDUSTRY_SOLUTIONS','Industry Solutions','Industry-specific vertical solutions — healthcare, retail, manufacturing, government',                   'ACTIVE', 100,'1.0', true)
) AS v(code, name, description, status, display_order, version, enabled)
WHERE NOT EXISTS (SELECT 1 FROM modules m WHERE m.code = v.code);

-- ============================================================
-- STEP 3: (Removed — Flyway automatically records the migration
--          in flyway_schema_history on successful commit.)
--
-- The previous version of this migration included a DO block that
-- manually INSERTed into flyway_schema_history. This caused CI to
-- hang for 30+ minutes during Spring Boot context startup because
-- the migration was holding a RowExclusiveLock on flyway_schema_history
-- while Flyway's outer transaction was also trying to manage the same
-- table — a classic anti-pattern of a Flyway migration manipulating
-- the Flyway tracking table from within itself.
--
-- Removing this block lets Flyway handle schema_history insertion
-- normally (which it does after the migration script completes).
-- ============================================================
