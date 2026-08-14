-- ============================================================
-- V20260813_1: Seed Control Plane tenant + ADMIN role + admin user + capabilities
--
-- This migration ensures the platform always has a working admin user
-- even after Render free tier restarts (which can wipe data).
--
-- Idempotent: uses WHERE NOT EXISTS to prevent duplicate inserts.
-- Safe to re-run: all statements are no-ops if data already exists.
--
-- Credentials (after migration):
--   Email:    admin@snad.ai
--   Password: Senen1985
--   (BCrypt $2a$ hash - Spring Security compatible)
-- ============================================================

-- ============================================================
-- STEP 1: Add EXECUTIVE_* and SYSTEM_HEALTH_* capabilities
-- Both dot-version (EXECUTIVE.VIEW) and underscore-version (EXECUTIVE_VIEW)
-- are added because the Java code uses underscores in @RequireCapability
-- annotations while some legacy code may use dots.
-- ============================================================

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'EXECUTIVE.VIEW', 'Executive View', 'View executive-level dashboards and metrics', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'EXECUTIVE.VIEW');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'EXECUTIVE.MANAGE', 'Executive Manage', 'Manage platform-wide executive operations', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'EXECUTIVE.MANAGE');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'EXECUTIVE.BILLING', 'Executive Billing', 'Manage billing, subscriptions, and financial operations', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'EXECUTIVE.BILLING');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'SYSTEM_HEALTH.VIEW', 'System Health View', 'View system health and operational status', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'SYSTEM_HEALTH.VIEW');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'SYSTEM_HEALTH.MONITOR', 'System Health Monitor', 'Monitor system health metrics and alerts', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'SYSTEM_HEALTH.MONITOR');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'SYSTEM_HEALTH.ALERTS', 'System Health Alerts', 'Manage and respond to system health alerts', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'SYSTEM_HEALTH.ALERTS');

-- Underscore versions (to match @RequireCapability("EXECUTIVE_VIEW") in Java code)
INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'EXECUTIVE_VIEW', 'Executive View (Underscore)', 'View executive-level dashboards and metrics', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'EXECUTIVE_VIEW');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'EXECUTIVE_MANAGE', 'Executive Manage (Underscore)', 'Manage platform-wide executive operations', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'EXECUTIVE_MANAGE');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'EXECUTIVE_BILLING', 'Executive Billing (Underscore)', 'Manage billing, subscriptions, and financial operations', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'EXECUTIVE_BILLING');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'SYSTEM_HEALTH_VIEW', 'System Health View (Underscore)', 'View system health and operational status', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'SYSTEM_HEALTH_VIEW');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'SYSTEM_HEALTH_MONITOR', 'System Health Monitor (Underscore)', 'Monitor system health metrics and alerts', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'SYSTEM_HEALTH_MONITOR');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), 'SYSTEM_HEALTH_ALERTS', 'System Health Alerts (Underscore)', 'Manage and respond to system health alerts', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'SYSTEM_HEALTH_ALERTS');

-- ============================================================
-- STEP 2: Insert SNAD Control Plane tenant (deterministic UUID)
-- ============================================================

INSERT INTO tenants (id, name, subdomain, status, locale, timezone, currency_code, created_at, updated_at)
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'SNAD Control Plane', 'control-plane', 'ACTIVE', 'ar-SA', 'Asia/Riyadh', 'SAR', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM tenants WHERE id = '00000000-0000-0000-0000-000000000001'::uuid);

-- ============================================================
-- STEP 3: Insert ADMIN role (deterministic UUID)
-- ============================================================

INSERT INTO roles (id, tenant_id, code, name, description, status, created_at, updated_at)
SELECT '00000000-0000-0000-0000-000000000100'::uuid,
       '00000000-0000-0000-0000-000000000001'::uuid,
       'ADMIN', 'Administrator', 'Full administrative access', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM roles
    WHERE tenant_id = '00000000-0000-0000-0000-000000000001'::uuid AND code = 'ADMIN'
);

-- Insert SALES_MANAGER role for the control plane tenant.
-- V20260722.8 (which runs BEFORE this migration) only seeds SALES_MANAGER
-- for tenants existing at THAT time. The control plane tenant is created
-- in this migration (STEP 2 above), so it would otherwise miss the
-- SALES_MANAGER role. CrmPostgresMigrationTest asserts that every active
-- tenant has SALES_MANAGER + SALES_REPRESENTATIVE roles.
INSERT INTO roles (id, tenant_id, code, name, description, status, created_at, updated_at)
SELECT '00000000-0000-0000-0000-000000000101'::uuid,
       '00000000-0000-0000-0000-000000000001'::uuid,
       'SALES_MANAGER', 'Sales Manager', 'CRM sales manager role', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM roles
    WHERE tenant_id = '00000000-0000-0000-0000-000000000001'::uuid AND code = 'SALES_MANAGER'
);

INSERT INTO roles (id, tenant_id, code, name, description, status, created_at, updated_at)
SELECT '00000000-0000-0000-0000-000000000102'::uuid,
       '00000000-0000-0000-0000-000000000001'::uuid,
       'SALES_REPRESENTATIVE', 'Sales Representative', 'CRM sales rep role', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM roles
    WHERE tenant_id = '00000000-0000-0000-0000-000000000001'::uuid AND code = 'SALES_REPRESENTATIVE'
);

-- ============================================================
-- STEP 4: Insert admin user (admin@snad.ai / Senen1985)
-- BCrypt hash uses $2a$ prefix for Spring Security compatibility
-- ============================================================

INSERT INTO users (id, tenant_id, email, display_name, status, password_hash, password_set_at, must_change_password, platform_admin, created_at, updated_at)
SELECT '00000000-0000-0000-0000-000000000010'::uuid,
       '00000000-0000-0000-0000-000000000001'::uuid,
       'admin@snad.ai',
       'SNAD Administrator',
       'ACTIVE',
       '$2a$10$bCDvhC15J/dMe93PkDHYn.chmhliABhbcmCVc.ACI6qc2.0IlsCti',
       NOW(),
       false,
       true,
       NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'admin@snad.ai');

-- Force update password hash (in case user exists with old/different hash)
UPDATE users
SET password_hash = '$2a$10$bCDvhC15J/dMe93PkDHYn.chmhliABhbcmCVc.ACI6qc2.0IlsCti',
    status = 'ACTIVE',
    platform_admin = true,
    must_change_password = false,
    password_set_at = NOW(),
    updated_at = NOW()
WHERE email = 'admin@snad.ai';

-- ============================================================
-- STEP 5: Grant the 12 new EXECUTIVE_* / SYSTEM_HEALTH_* capabilities
-- to ALL existing ADMIN roles (not just the seed tenant).
--
-- This preserves backward compatibility: V15 (Java migration) bound
-- ALL existing active capabilities to each tenant's ADMIN role. After
-- V15 runs, new capabilities added by later migrations (like this one)
-- must also be bound to ADMIN, otherwise ADMIN loses its "all caps"
-- guarantee (FlywayV15ProductionUpgradeTest verifies this invariant).
--
-- The previous version targeted only tenant_id='00000000-...'
-- which left test tenants (and any production tenants created after
-- V15) without the new capabilities.
-- ============================================================

INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), t.id, r.id, ac.id, NOW()
FROM tenants t
JOIN roles r ON r.tenant_id = t.id AND r.code = 'ADMIN'
JOIN access_capabilities ac ON ac.status = 'ACTIVE'
   AND ac.code IN (
       'EXECUTIVE.VIEW','EXECUTIVE.MANAGE','EXECUTIVE.BILLING',
       'SYSTEM_HEALTH.VIEW','SYSTEM_HEALTH.MONITOR','SYSTEM_HEALTH.ALERTS',
       'EXECUTIVE_VIEW','EXECUTIVE_MANAGE','EXECUTIVE_BILLING',
       'SYSTEM_HEALTH_VIEW','SYSTEM_HEALTH_MONITOR','SYSTEM_HEALTH_ALERTS'
   )
WHERE NOT EXISTS (
    SELECT 1 FROM role_capabilities rc
    WHERE rc.tenant_id = t.id AND rc.role_id = r.id AND rc.capability_id = ac.id
);

-- ============================================================
-- STEP 6: Assign ADMIN role to admin user
-- (delete first, then re-insert, to ensure idempotency)
-- ============================================================

DELETE FROM user_role_assignments
WHERE tenant_id = '00000000-0000-0000-0000-000000000001'::uuid
  AND user_id = '00000000-0000-0000-0000-000000000010'::uuid;

INSERT INTO user_role_assignments (id, tenant_id, user_id, role_id, organization_id, status, created_at, updated_at)
SELECT gen_random_uuid(),
       '00000000-0000-0000-0000-000000000001'::uuid,
       '00000000-0000-0000-0000-000000000010'::uuid,
       '00000000-0000-0000-0000-000000000100'::uuid,
       NULL, 'ACTIVE', NOW(), NOW();

-- ============================================================
-- STEP 7: Seed default system services for health monitoring
-- (Only if system_services table exists and is empty)
-- ============================================================
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'system_services') THEN
        IF NOT EXISTS (SELECT 1 FROM system_services LIMIT 1) THEN
            INSERT INTO system_services (id, code, name, description, environment, status, criticality, owner_name, dependencies, health_url, created_at, updated_at)
            VALUES
                ('b2000000-0000-0000-0000-000000000001'::uuid, 'WEB', 'SNAD Web', 'Next.js administration and tenant workspace', 'pilot', 'OPERATIONAL', 'HIGH', 'Platform Engineering', 'API', NULL, NOW(), NOW()),
                ('b2000000-0000-0000-0000-000000000002'::uuid, 'API', 'SNAD Platform API', 'Spring Boot application service', 'pilot', 'OPERATIONAL', 'CRITICAL', 'Platform Engineering', 'DATABASE', '/actuator/health', NOW(), NOW()),
                ('b2000000-0000-0000-0000-000000000003'::uuid, 'DATABASE', 'SNAD PostgreSQL', 'Multi-tenant transactional database', 'pilot', 'OPERATIONAL', 'CRITICAL', 'Data Engineering', NULL, NULL, NOW(), NOW()),
                ('b2000000-0000-0000-0000-000000000004'::uuid, 'NOTIFICATIONS', 'Security Notifications', 'Account recovery and security notification gateway', 'pilot', 'DISABLED', 'HIGH', 'Platform Engineering', 'EXTERNAL_PROVIDER', NULL, NOW(), NOW());
        END IF;
    END IF;
END $$;

-- ============================================================
-- STEP 8: Seed default SaaS plans (Starter, Growth, Enterprise)
-- (Only if saas_plans table exists and is empty)
-- ============================================================
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'saas_plans') THEN
        IF NOT EXISTS (SELECT 1 FROM saas_plans LIMIT 1) THEN
            INSERT INTO saas_plans (id, code, name, description, status, currency_code, monthly_price_minor, annual_price_minor, trial_days, max_users, max_organizations, storage_mb, created_at, updated_at)
            VALUES
                ('c3000000-0000-0000-0000-000000000001'::uuid, 'STARTER', 'البداية', 'للمنشآت الصغيرة التي تبدأ تشغيل أعمالها على سند', 'ACTIVE', 'SAR', 9900, 99000, 14, 5, 1, 5120, NOW(), NOW()),
                ('c3000000-0000-0000-0000-000000000002'::uuid, 'GROWTH', 'النمو', 'للشركات النامية متعددة الفرق والعمليات', 'ACTIVE', 'SAR', 29900, 299000, 14, 25, 5, 51200, NOW(), NOW()),
                ('c3000000-0000-0000-0000-000000000003'::uuid, 'ENTERPRISE', 'المؤسسات', 'للشركات الكبيرة ومتعددة الكيانات مع حوكمة متقدمة', 'ACTIVE', 'SAR', 99900, 999000, 30, 250, 50, 512000, NOW(), NOW());
        END IF;
    END IF;
END $$;
