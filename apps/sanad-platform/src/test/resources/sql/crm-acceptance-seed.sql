-- ============================================================
-- SNAD Platform — CRM Authenticated Acceptance Seed
-- ------------------------------------------------------------
-- Branch: crm/002d-authenticated-acceptance-environment
--
-- Idempotent seed data for the CRM authenticated acceptance
-- workflow (.github/workflows/crm-authenticated-acceptance.yml).
--
-- Run order:
--   1. Flyway migrations (V1 ... V20260711_1) — schema + base
--      capability catalog + auto-created ADMIN roles per tenant.
--   2. This file (crm-acceptance-seed.sql) — test tenants, users,
--      organizations, memberships, and per-tenant RBAC roles with
--      distinct capability sets.
--
-- Tenants created:
--   • Tenant A — "Tenant A (Acceptance)" — subdomain tenant-a-acceptance
--   • Tenant B — "Tenant B (Acceptance)" — subdomain tenant-b-acceptance
--
-- Users created (5 total, all with password "TestPass123!"):
--   Tenant A:
--     • tenant-a-admin@snad-crm-acceptance.example      — ADMIN (full CRM access)
--     • tenant-a-readonly@snad-crm-acceptance.example   — CRM_READ_ONLY (all *.READ)
--     • tenant-a-lead-writer@snad-crm-acceptance.example — CRM_LEAD_WRITER
--         (CRM.LEAD.READ + CRM.LEAD.WRITE, NO CRM.LEAD.CONVERT)
--     • tenant-a-import-reader@snad-crm-acceptance.example — CRM_IMPORT_READER
--         (CRM.IMPORT.READ only — upload button must be hidden)
--   Tenant B:
--     • tenant-b-admin@snad-crm-acceptance.example      — ADMIN (full CRM access)
--
-- The seed is safe to re-run: every INSERT uses WHERE NOT EXISTS
-- guards or ON CONFLICT DO NOTHING.
-- ============================================================

-- pgcrypto provides crypt() and gen_salt() for bcrypt hashing.
-- PostgreSQL 16 includes gen_random_uuid() natively, but crypt()
-- still requires the pgcrypto extension.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ----------------------------------------------------------------------------
-- 1. Tenants
-- ----------------------------------------------------------------------------
-- Stable UUIDs so re-runs are deterministic and downstream tests can
-- hard-code references if needed.
-- ----------------------------------------------------------------------------

INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at)
VALUES (
    '11111111-1111-4111-8111-111111111111',
    'Tenant A (Acceptance)',
    'tenant-a-acceptance',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at)
VALUES (
    '22222222-2222-4222-8222-222222222222',
    'Tenant B (Acceptance)',
    'tenant-b-acceptance',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 2. Organizations (one per tenant)
-- ----------------------------------------------------------------------------

INSERT INTO organizations (id, tenant_id, name, description, status, created_at, updated_at)
VALUES (
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    '11111111-1111-4111-8111-111111111111',
    'Tenant A Org',
    'Primary organization for Tenant A acceptance tests',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO organizations (id, tenant_id, name, description, status, created_at, updated_at)
VALUES (
    'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
    '22222222-2222-4222-8222-222222222222',
    'Tenant B Org',
    'Primary organization for Tenant B acceptance tests',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 3. Users (5 total, bcrypt-hashed password "TestPass123!")
-- ----------------------------------------------------------------------------
-- crypt('TestPass123!', gen_salt('bf', 10)) yields a $2a$10$... hash that is
-- directly verifiable by Spring's BCryptPasswordEncoder(10).
-- ----------------------------------------------------------------------------

INSERT INTO users (id, tenant_id, email, display_name, status, password_hash, created_at, updated_at)
VALUES (
    'a1a1a1a1-a1a1-4a1a-8a1a-a1a1a1a1a1a1',
    '11111111-1111-4111-8111-111111111111',
    'tenant-a-admin@snad-crm-acceptance.example',
    'Tenant A Admin',
    'ACTIVE',
    crypt('TestPass123!', gen_salt('bf', 10)),
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO users (id, tenant_id, email, display_name, status, password_hash, created_at, updated_at)
VALUES (
    'a2a2a2a2-a2a2-4a2a-8a2a-a2a2a2a2a2a2',
    '11111111-1111-4111-8111-111111111111',
    'tenant-a-readonly@snad-crm-acceptance.example',
    'Tenant A Read-Only',
    'ACTIVE',
    crypt('TestPass123!', gen_salt('bf', 10)),
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO users (id, tenant_id, email, display_name, status, password_hash, created_at, updated_at)
VALUES (
    'a3a3a3a3-a3a3-4a3a-8a3a-a3a3a3a3a3a3',
    '11111111-1111-4111-8111-111111111111',
    'tenant-a-lead-writer@snad-crm-acceptance.example',
    'Tenant A Lead Writer',
    'ACTIVE',
    crypt('TestPass123!', gen_salt('bf', 10)),
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO users (id, tenant_id, email, display_name, status, password_hash, created_at, updated_at)
VALUES (
    'a4a4a4a4-a4a4-4a4a-8a4a-a4a4a4a4a4a4',
    '11111111-1111-4111-8111-111111111111',
    'tenant-a-import-reader@snad-crm-acceptance.example',
    'Tenant A Import Reader',
    'ACTIVE',
    crypt('TestPass123!', gen_salt('bf', 10)),
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO users (id, tenant_id, email, display_name, status, password_hash, created_at, updated_at)
VALUES (
    'b1b1b1b1-b1b1-4b1b-8b1b-b1b1b1b1b1b1',
    '22222222-2222-4222-8222-222222222222',
    'tenant-b-admin@snad-crm-acceptance.example',
    'Tenant B Admin',
    'ACTIVE',
    crypt('TestPass123!', gen_salt('bf', 10)),
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 4. Organization memberships (link each user to their org)
-- ----------------------------------------------------------------------------

INSERT INTO organization_memberships (id, tenant_id, organization_id, user_id, email, display_name, status, created_at, updated_at)
VALUES (
    'c0c0c0c0-c0c0-4c0c-8c0c-c0c0c0c0c0c1',
    '11111111-1111-4111-8111-111111111111',
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    'a1a1a1a1-a1a1-4a1a-8a1a-a1a1a1a1a1a1',
    'tenant-a-admin@snad-crm-acceptance.example',
    'Tenant A Admin',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO organization_memberships (id, tenant_id, organization_id, user_id, email, display_name, status, created_at, updated_at)
VALUES (
    'c0c0c0c0-c0c0-4c0c-8c0c-c0c0c0c0c0c2',
    '11111111-1111-4111-8111-111111111111',
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    'a2a2a2a2-a2a2-4a2a-8a2a-a2a2a2a2a2a2',
    'tenant-a-readonly@snad-crm-acceptance.example',
    'Tenant A Read-Only',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO organization_memberships (id, tenant_id, organization_id, user_id, email, display_name, status, created_at, updated_at)
VALUES (
    'c0c0c0c0-c0c0-4c0c-8c0c-c0c0c0c0c0c3',
    '11111111-1111-4111-8111-111111111111',
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    'a3a3a3a3-a3a3-4a3a-8a3a-a3a3a3a3a3a3',
    'tenant-a-lead-writer@snad-crm-acceptance.example',
    'Tenant A Lead Writer',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO organization_memberships (id, tenant_id, organization_id, user_id, email, display_name, status, created_at, updated_at)
VALUES (
    'c0c0c0c0-c0c0-4c0c-8c0c-c0c0c0c0c0c4',
    '11111111-1111-4111-8111-111111111111',
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    'a4a4a4a4-a4a4-4a4a-8a4a-a4a4a4a4a4a4',
    'tenant-a-import-reader@snad-crm-acceptance.example',
    'Tenant A Import Reader',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO organization_memberships (id, tenant_id, organization_id, user_id, email, display_name, status, created_at, updated_at)
VALUES (
    'd0d0d0d0-d0d0-4d0d-8d0d-d0d0d0d0d0d1',
    '22222222-2222-4222-8222-222222222222',
    'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
    'b1b1b1b1-b1b1-4b1b-8b1b-b1b1b1b1b1b1',
    'tenant-b-admin@snad-crm-acceptance.example',
    'Tenant B Admin',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 5. Roles
-- ----------------------------------------------------------------------------
-- The V20260702_2 migration already created an ADMIN role for every
-- tenant and assigned every ACTIVE capability to it. So the Tenant A
-- ADMIN and Tenant B ADMIN roles exist by the time this seed runs.
--
-- We create three additional tenant-scoped roles for the Tenant A
-- restricted users:
--   • CRM_READ_ONLY      — every CRM.*.READ capability
--   • CRM_LEAD_WRITER    — CRM.LEAD.READ + CRM.LEAD.WRITE only
--                          (deliberately omits CRM.LEAD.CONVERT)
--   • CRM_IMPORT_READER  — CRM.IMPORT.READ only
-- ----------------------------------------------------------------------------

-- Defensive: ensure ADMIN roles exist for both tenants (V20260702_2 should
-- have created them already, but this guards against a future migration
-- ordering change).
INSERT INTO roles (id, tenant_id, code, name, description, status, created_at, updated_at)
SELECT
    'e1e1e1e1-e1e1-4e1e-8e1e-e1e1e1e1e1e1',
    tenant.id,
    'ADMIN',
    'Administrator',
    'Tenant-wide administrative access (acceptance seed)',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM tenants tenant
WHERE tenant.id = '11111111-1111-4111-8111-111111111111'
  AND NOT EXISTS (
      SELECT 1 FROM roles role
      WHERE role.tenant_id = tenant.id AND role.code = 'ADMIN'
  )
ON CONFLICT (id) DO NOTHING;

INSERT INTO roles (id, tenant_id, code, name, description, status, created_at, updated_at)
SELECT
    'e2e2e2e2-e2e2-4e2e-8e2e-e2e2e2e2e2e2',
    tenant.id,
    'ADMIN',
    'Administrator',
    'Tenant-wide administrative access (acceptance seed)',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM tenants tenant
WHERE tenant.id = '22222222-2222-4222-8222-222222222222'
  AND NOT EXISTS (
      SELECT 1 FROM roles role
      WHERE role.tenant_id = tenant.id AND role.code = 'ADMIN'
  )
ON CONFLICT (id) DO NOTHING;

-- Tenant A — CRM_READ_ONLY
INSERT INTO roles (id, tenant_id, code, name, description, status, created_at, updated_at)
VALUES (
    'f1f1f1f1-f1f1-4f1f-8f1f-f1f1f1f1f1f1',
    '11111111-1111-4111-8111-111111111111',
    'CRM_READ_ONLY',
    'CRM Read-Only',
    'Tenant A acceptance role — every CRM.*.READ capability, no writes',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO NOTHING;

-- Tenant A — CRM_LEAD_WRITER
INSERT INTO roles (id, tenant_id, code, name, description, status, created_at, updated_at)
VALUES (
    'f2f2f2f2-f2f2-4f2f-8f2f-f2f2f2f2f2f2',
    '11111111-1111-4111-8111-111111111111',
    'CRM_LEAD_WRITER',
    'CRM Lead Writer (no convert)',
    'Tenant A acceptance role — CRM.LEAD.READ + CRM.LEAD.WRITE, deliberately omits CRM.LEAD.CONVERT',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO NOTHING;

-- Tenant A — CRM_IMPORT_READER
INSERT INTO roles (id, tenant_id, code, name, description, status, created_at, updated_at)
VALUES (
    'f3f3f3f3-f3f3-4f3f-8f3f-f3f3f3f3f3f3',
    '11111111-1111-4111-8111-111111111111',
    'CRM_IMPORT_READER',
    'CRM Import Reader',
    'Tenant A acceptance role — CRM.IMPORT.READ only (upload button must be hidden)',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 6. Role capabilities (for the three non-admin Tenant A roles)
-- ----------------------------------------------------------------------------
-- The ADMIN roles already receive every active capability via V20260702_2.
-- Here we grant selective capabilities to the restricted roles.

-- CRM_READ_ONLY → every CRM.*.READ capability
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT
    gen_random_uuid(),
    '11111111-1111-4111-8111-111111111111',
    'f1f1f1f1-f1f1-4f1f-8f1f-f1f1f1f1f1f1',
    cap.id,
    CURRENT_TIMESTAMP
FROM access_capabilities cap
WHERE cap.code IN (
    'CRM.ACCOUNT.READ',
    'CRM.CONTACT.READ',
    'CRM.LEAD.READ',
    'CRM.OPPORTUNITY.READ',
    'CRM.ACTIVITY.READ',
    'CRM.CUSTOM_FIELD.READ',
    'CRM.IMPORT.READ'
)
AND NOT EXISTS (
    SELECT 1
    FROM role_capabilities existing
    WHERE existing.tenant_id = '11111111-1111-4111-8111-111111111111'
      AND existing.role_id = 'f1f1f1f1-f1f1-4f1f-8f1f-f1f1f1f1f1f1'
      AND existing.capability_id = cap.id
);

-- CRM_LEAD_WRITER → CRM.LEAD.READ + CRM.LEAD.WRITE only
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT
    gen_random_uuid(),
    '11111111-1111-4111-8111-111111111111',
    'f2f2f2f2-f2f2-4f2f-8f2f-f2f2f2f2f2f2',
    cap.id,
    CURRENT_TIMESTAMP
FROM access_capabilities cap
WHERE cap.code IN ('CRM.LEAD.READ', 'CRM.LEAD.WRITE')
AND NOT EXISTS (
    SELECT 1
    FROM role_capabilities existing
    WHERE existing.tenant_id = '11111111-1111-4111-8111-111111111111'
      AND existing.role_id = 'f2f2f2f2-f2f2-4f2f-8f2f-f2f2f2f2f2f2'
      AND existing.capability_id = cap.id
);

-- CRM_IMPORT_READER → CRM.IMPORT.READ only
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT
    gen_random_uuid(),
    '11111111-1111-4111-8111-111111111111',
    'f3f3f3f3-f3f3-4f3f-8f3f-f3f3f3f3f3f3',
    cap.id,
    CURRENT_TIMESTAMP
FROM access_capabilities cap
WHERE cap.code = 'CRM.IMPORT.READ'
AND NOT EXISTS (
    SELECT 1
    FROM role_capabilities existing
    WHERE existing.tenant_id = '11111111-1111-4111-8111-111111111111'
      AND existing.role_id = 'f3f3f3f3-f3f3-4f3f-8f3f-f3f3f3f3f3f3'
      AND existing.capability_id = cap.id
);

-- ----------------------------------------------------------------------------
-- 7. User-role assignments
-- ----------------------------------------------------------------------------
-- The user_role_assignments UNIQUE constraint is on
-- (tenant_id, user_id, role_id, organization_id), so we scope each
-- assignment to the user's primary organization.

-- Tenant A admin → ADMIN
INSERT INTO user_role_assignments (id, tenant_id, user_id, role_id, organization_id, status, created_at, updated_at)
SELECT
    'a0000000-0000-4000-8000-000000000001',
    '11111111-1111-4111-8111-111111111111',
    'a1a1a1a1-a1a1-4a1a-8a1a-a1a1a1a1a1a1',
    role.id,
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM roles role
WHERE role.tenant_id = '11111111-1111-4111-8111-111111111111'
  AND role.code = 'ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM user_role_assignments existing
      WHERE existing.tenant_id = '11111111-1111-4111-8111-111111111111'
        AND existing.user_id = 'a1a1a1a1-a1a1-4a1a-8a1a-a1a1a1a1a1a1'
        AND existing.role_id = role.id
        AND existing.organization_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'
  )
ON CONFLICT (id) DO NOTHING;

-- Tenant A read-only → CRM_READ_ONLY
INSERT INTO user_role_assignments (id, tenant_id, user_id, role_id, organization_id, status, created_at, updated_at)
VALUES (
    'a0000000-0000-4000-8000-000000000002',
    '11111111-1111-4111-8111-111111111111',
    'a2a2a2a2-a2a2-4a2a-8a2a-a2a2a2a2a2a2',
    'f1f1f1f1-f1f1-4f1f-8f1f-f1f1f1f1f1f1',
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO NOTHING;

-- Tenant A lead writer → CRM_LEAD_WRITER
INSERT INTO user_role_assignments (id, tenant_id, user_id, role_id, organization_id, status, created_at, updated_at)
VALUES (
    'a0000000-0000-4000-8000-000000000003',
    '11111111-1111-4111-8111-111111111111',
    'a3a3a3a3-a3a3-4a3a-8a3a-a3a3a3a3a3a3',
    'f2f2f2f2-f2f2-4f2f-8f2f-f2f2f2f2f2f2',
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO NOTHING;

-- Tenant A import reader → CRM_IMPORT_READER
INSERT INTO user_role_assignments (id, tenant_id, user_id, role_id, organization_id, status, created_at, updated_at)
VALUES (
    'a0000000-0000-4000-8000-000000000004',
    '11111111-1111-4111-8111-111111111111',
    'a4a4a4a4-a4a4-4a4a-8a4a-a4a4a4a4a4a4',
    'f3f3f3f3-f3f3-4f3f-8f3f-f3f3f3f3f3f3',
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO NOTHING;

-- Tenant B admin → ADMIN
INSERT INTO user_role_assignments (id, tenant_id, user_id, role_id, organization_id, status, created_at, updated_at)
SELECT
    'a0000000-0000-4000-8000-000000000005',
    '22222222-2222-4222-8222-222222222222',
    'b1b1b1b1-b1b1-4b1b-8b1b-b1b1b1b1b1b1',
    role.id,
    'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM roles role
WHERE role.tenant_id = '22222222-2222-4222-8222-222222222222'
  AND role.code = 'ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM user_role_assignments existing
      WHERE existing.tenant_id = '22222222-2222-4222-8222-222222222222'
        AND existing.user_id = 'b1b1b1b1-b1b1-4b1b-8b1b-b1b1b1b1b1b1'
        AND existing.role_id = role.id
        AND existing.organization_id = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'
  )
ON CONFLICT (id) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 8. Seed a single Tenant A CRM account, contact, lead, pipeline, opportunity,
--    and activity so the Playwright tenant-isolation spec has a stable target
--    to attempt to reach from Tenant B (each attempt must fail).
-- ----------------------------------------------------------------------------
-- These rows belong to Tenant A only — Tenant B must never see them.

INSERT INTO crm_accounts (
    id, tenant_id, version, display_name, normalized_name, account_type,
    lifecycle_status, primary_currency_code, preferred_locale, time_zone,
    source, created_by, updated_by, created_at, updated_at
)
VALUES (
    'aa00aa00-aa00-4aa0-8aa0-aa00aa00aa01',
    '11111111-1111-4111-8111-111111111111',
    0,
    'Tenant A Sample Account',
    'tenant a sample account',
    'BUSINESS',
    'ACTIVE',
    'SAR',
    'ar-SA',
    'Asia/Riyadh',
    'CRM_SEED',
    'a1a1a1a1-a1a1-4a1a-8a1a-a1a1a1a1a1a1',
    'a1a1a1a1-a1a1-4a1a-8a1a-a1a1a1a1a1a1',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO crm_contacts (
    id, tenant_id, version, account_id, given_name, family_name, display_name,
    normalized_name, primary_email, normalized_email, primary_phone,
    preferred_locale, time_zone, lifecycle_status, owner_user_id,
    consent_summary, created_by, updated_by, created_at, updated_at
)
VALUES (
    'cc00cc00-cc00-4cc0-8cc0-cc00cc00cc01',
    '11111111-1111-4111-8111-111111111111',
    0,
    'aa00aa00-aa00-4aa0-8aa0-aa00aa00aa01',
    'Aisha',
    'Al-Saud',
    'Aisha Al-Saud',
    'aisha al-saud',
    'aisha.tenant-a@snad-crm-acceptance.example',
    'aisha.tenant-a@snad-crm-acceptance.example',
    '+966500000001',
    'ar-SA',
    'Asia/Riyadh',
    'ACTIVE',
    'a1a1a1a1-a1a1-4a1a-8a1a-a1a1a1a1a1a1',
    'GRANTED',
    'a1a1a1a1-a1a1-4a1a-8a1a-a1a1a1a1a1a1',
    'a1a1a1a1-a1a1-4a1a-8a1a-a1a1a1a1a1a1',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO crm_pipelines (
    id, tenant_id, name, currency_code, active,
    created_by, created_at, updated_at
)
VALUES (
    '0b00b000-0b00-4b00-8b00-0b00b000b001',
    '11111111-1111-4111-8111-111111111111',
    'Tenant A Default Pipeline',
    'SAR',
    TRUE,
    'a1a1a1a1-a1a1-4a1a-8a1a-a1a1a1a1a1a1',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO crm_pipeline_stages (
    id, tenant_id, pipeline_id, name, sequence, probability, terminal_state, active
)
VALUES (
    '55f0b55f-d0a9-5448-b513-5b933efe1df2',
    '11111111-1111-4111-8111-111111111111',
    '0b00b000-0b00-4b00-8b00-0b00b000b001',
    'New',
    1,
    10,
    NULL,
    TRUE
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO crm_pipeline_stages (
    id, tenant_id, pipeline_id, name, sequence, probability, terminal_state, active
)
VALUES (
    'f587e27b-482d-5b92-8cb2-feb623bc9595',
    '11111111-1111-4111-8111-111111111111',
    '0b00b000-0b00-4b00-8b00-0b00b000b001',
    'Won',
    2,
    100,
    'WON',
    TRUE
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO crm_leads (
    id, tenant_id, version, display_name, normalized_name, company_name,
    email, normalized_email, phone, source, status, owner_user_id, score,
    created_by, updated_by, created_at, updated_at
)
VALUES (
    'ee964f6d-cff1-502b-a687-ae61611761de',
    '11111111-1111-4111-8111-111111111111',
    0,
    'Tenant A Sample Lead',
    'tenant a sample lead',
    'Sample Co.',
    'lead.tenant-a@snad-crm-acceptance.example',
    'lead.tenant-a@snad-crm-acceptance.example',
    '+966500000002',
    'WEB_FORM',
    'NEW',
    'a1a1a1a1-a1a1-4a1a-8a1a-a1a1a1a1a1a1',
    50.000,
    'a1a1a1a1-a1a1-4a1a-8a1a-a1a1a1a1a1a1',
    'a1a1a1a1-a1a1-4a1a-8a1a-a1a1a1a1a1a1',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO crm_opportunities (
    id, tenant_id, version, account_id, contact_id, pipeline_id, stage_id,
    name, amount, currency_code, probability, status, owner_user_id,
    created_by, updated_by, created_at, updated_at
)
VALUES (
    '5ff572da-a04a-5893-be50-d50e5ea64165',
    '11111111-1111-4111-8111-111111111111',
    0,
    'aa00aa00-aa00-4aa0-8aa0-aa00aa00aa01',
    'cc00cc00-cc00-4cc0-8cc0-cc00cc00cc01',
    '0b00b000-0b00-4b00-8b00-0b00b000b001',
    '55f0b55f-d0a9-5448-b513-5b933efe1df2',
    'Tenant A Sample Opportunity',
    25000.000000,
    'SAR',
    10.00,
    'OPEN',
    'a1a1a1a1-a1a1-4a1a-8a1a-a1a1a1a1a1a1',
    'a1a1a1a1-a1a1-4a1a-8a1a-a1a1a1a1a1a1',
    'a1a1a1a1-a1a1-4a1a-8a1a-a1a1a1a1a1a1',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO crm_activities (
    id, tenant_id, version, activity_type, subject, body, related_type,
    related_id, owner_user_id, status, priority, due_at, created_by,
    updated_by, created_at, updated_at
)
VALUES (
    'c296c51d-fc46-5076-8f59-599ef2aaaa97',
    '11111111-1111-4111-8111-111111111111',
    0,
    'TASK',
    'Tenant A Sample Follow-up',
    'Sample follow-up task for acceptance tests.',
    'ACCOUNT',
    'aa00aa00-aa00-4aa0-8aa0-aa00aa00aa01',
    'a1a1a1a1-a1a1-4a1a-8a1a-a1a1a1a1a1a1',
    'OPEN',
    50,
    CURRENT_TIMESTAMP + INTERVAL '2 days',
    'a1a1a1a1-a1a1-4a1a-8a1a-a1a1a1a1a1a1',
    'a1a1a1a1-a1a1-4a1a-8a1a-a1a1a1a1a1a1',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 9. Seed summary (visible in psql output for diagnostic purposes)
-- ----------------------------------------------------------------------------
SELECT 'crm-acceptance-seed: tenants=' || count(*) AS summary
FROM tenants
WHERE id IN ('11111111-1111-4111-8111-111111111111','22222222-2222-4222-8222-222222222222');
-- ----------------------------------------------------------------------------
-- 10. CRM-005 enterprise Account Master acceptance extension
-- ----------------------------------------------------------------------------
-- ADMIN roles are created by this seed after Flyway has completed, so grant
-- every ACTIVE capability after role creation. This preserves the documented
-- tenant-admin contract without bypassing capability evaluation.
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), role.tenant_id, role.id, capability.id, CURRENT_TIMESTAMP
FROM roles role
CROSS JOIN access_capabilities capability
WHERE role.code = 'ADMIN'
  AND role.status = 'ACTIVE'
  AND role.tenant_id IN (
    '11111111-1111-4111-8111-111111111111',
    '22222222-2222-4222-8222-222222222222'
  )
  AND capability.status = 'ACTIVE'
  AND NOT EXISTS (
    SELECT 1 FROM role_capabilities existing
    WHERE existing.tenant_id = role.tenant_id
      AND existing.role_id = role.id
      AND existing.capability_id = capability.id
  );

-- The CRM_READ_ONLY acceptance role represents all CRM read capabilities.
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), role.tenant_id, role.id, capability.id, CURRENT_TIMESTAMP
FROM roles role
JOIN access_capabilities capability ON capability.code IN (
  'CRM.ACCOUNT.MASTER.READ',
  'CRM.ACCOUNT.RELATIONSHIP.READ',
  'CRM.ACCOUNT.IDENTIFIER.READ',
  'CRM.ACCOUNT.HISTORY.READ',
  'CRM.ACCOUNT.RISK.READ'
) AND capability.status = 'ACTIVE'
WHERE role.tenant_id = '11111111-1111-4111-8111-111111111111'
  AND role.code = 'CRM_READ_ONLY'
  AND NOT EXISTS (
    SELECT 1 FROM role_capabilities existing
    WHERE existing.tenant_id = role.tenant_id
      AND existing.role_id = role.id
      AND existing.capability_id = capability.id
  );

-- The stable Tenant A sample account is inserted after Flyway's profile
-- backfill, therefore seed its Account Master extension explicitly.
INSERT INTO crm_account_profiles (
    account_id, tenant_id, version, legal_name, trade_name, risk_level,
    merge_candidate, created_by, updated_by, created_at, updated_at
)
SELECT account.id, account.tenant_id, 0, account.display_name, account.display_name,
       'UNKNOWN', FALSE, account.created_by, account.updated_by,
       account.created_at, account.updated_at
FROM crm_accounts account
WHERE account.id = 'aa00aa00-aa00-4aa0-8aa0-aa00aa00aa01'
  AND account.tenant_id = '11111111-1111-4111-8111-111111111111'
  AND NOT EXISTS (
    SELECT 1 FROM crm_account_profiles profile
    WHERE profile.tenant_id = account.tenant_id
      AND profile.account_id = account.id
  );

INSERT INTO crm_account_status_history (
    id, tenant_id, account_id, from_status, to_status, reason, changed_by, changed_at
)
SELECT gen_random_uuid(), account.tenant_id, account.id, NULL,
       account.lifecycle_status, 'CRM-005 acceptance seed',
       account.created_by, account.created_at
FROM crm_accounts account
WHERE account.id = 'aa00aa00-aa00-4aa0-8aa0-aa00aa00aa01'
  AND account.tenant_id = '11111111-1111-4111-8111-111111111111'
  AND NOT EXISTS (
    SELECT 1 FROM crm_account_status_history history
    WHERE history.tenant_id = account.tenant_id
      AND history.account_id = account.id
  );

INSERT INTO crm_account_ownership_history (
    id, tenant_id, account_id, from_owner_user_id, to_owner_user_id,
    reason, changed_by, changed_at
)
SELECT gen_random_uuid(), account.tenant_id, account.id, NULL,
       account.owner_user_id, 'CRM-005 acceptance seed',
       account.created_by, account.created_at
FROM crm_accounts account
WHERE account.id = 'aa00aa00-aa00-4aa0-8aa0-aa00aa00aa01'
  AND account.tenant_id = '11111111-1111-4111-8111-111111111111'
  AND account.owner_user_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM crm_account_ownership_history history
    WHERE history.tenant_id = account.tenant_id
      AND history.account_id = account.id
  );
