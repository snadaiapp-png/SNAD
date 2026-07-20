-- ============================================================================
-- SANAD CRM-008 — V20260720_8 — Seed 17 Capabilities + 2 Roles + Role Grants
-- ============================================================================
-- Forward-only. Idempotent (ON CONFLICT DO NOTHING).
-- Matches the existing capability seeding pattern (V7 + V15 + V20260717_5).
-- REVIEW ONLY — DO NOT EXECUTE in this design phase.
-- ============================================================================

BEGIN;

-- ----------------------------------------------------------------------------
-- 1. 17 new capabilities
-- ----------------------------------------------------------------------------
-- UUIDs use the reserved range a0000007-0000-0000-0000-000000000800..1F
-- (a0000007-...-08xx..08xx for the 17 CRM-008 capabilities)
INSERT INTO access_capabilities (id, code, display_name, description, status, created_at, updated_at)
VALUES
    ('a0000007-0000-0000-0000-000000000801', 'CRM.ASSIGNMENT.READ',         'View Assignments',           'Read access to current assignments and ownership history',                  'ACTIVE', now(), now()),
    ('a0000007-0000-0000-0000-000000000802', 'CRM.ASSIGNMENT.WRITE',        'Reassign Record',             'Manually assign or reassign a CRM record to a user, team, or queue',         'ACTIVE', now(), now()),
    ('a0000007-0000-0000-0000-000000000803', 'CRM.ASSIGNMENT.ADMIN',        'Administer Assignments',      'Bulk assignment, rule override, force-assign across teams',                 'ACTIVE', now(), now()),
    ('a0000007-0000-0000-0000-000000000804', 'CRM.TRANSFER.READ',           'View Transfers',              'View incoming and outgoing transfer requests',                              'ACTIVE', now(), now()),
    ('a0000007-0000-0000-0000-000000000805', 'CRM.TRANSFER.REQUEST',        'Request Transfer',            'Create a new transfer request for a CRM record',                             'ACTIVE', now(), now()),
    ('a0000007-0000-0000-0000-000000000806', 'CRM.TRANSFER.APPROVE',        'Approve Transfer',            'Approve or reject a transfer request',                                       'ACTIVE', now(), now()),
    ('a0000007-0000-0000-0000-000000000807', 'CRM.TRANSFER.EXECUTE',        'Execute Transfer',            'Execute an approved transfer (system-internal — not granted to humans)',     'ACTIVE', now(), now()),
    ('a0000007-0000-0000-0000-000000000808', 'CRM.TEAM.READ',               'View Sales Teams',             'View sales teams and their memberships',                                      'ACTIVE', now(), now()),
    ('a0000007-0000-0000-0000-000000000809', 'CRM.TEAM.ADMIN',              'Administer Sales Teams',      'Create, edit, suspend, or archive sales teams and change managers',          'ACTIVE', now(), now()),
    ('a0000007-0000-0000-0000-00000000080a', 'CRM.QUEUE.READ',              'View Queues',                 'View queues and their items',                                                'ACTIVE', now(), now()),
    ('a0000007-0000-0000-0000-00000000080b', 'CRM.QUEUE.CLAIM',             'Claim Queue Item',            'Claim an available queue item',                                              'ACTIVE', now(), now()),
    ('a0000007-0000-0000-0000-00000000080c', 'CRM.QUEUE.ADMIN',             'Administer Queues',           'Create, edit, or archive queues and manage their members',                   'ACTIVE', now(), now()),
    ('a0000007-0000-0000-0000-00000000080d', 'CRM.TERRITORY.READ',          'View Territories',            'View territory hierarchy and territory assignments',                         'ACTIVE', now(), now()),
    ('a0000007-0000-0000-0000-00000000080e', 'CRM.TERRITORY.ADMIN',         'Administer Territories',      'Create, edit, or archive territories and manage territory assignments',     'ACTIVE', now(), now()),
    ('a0000007-0000-0000-0000-00000000080f', 'CRM.ASSIGNMENT_RULE.READ',    'View Assignment Rules',        'View assignment rules and their versions',                                   'ACTIVE', now(), now()),
    ('a0000007-0000-0000-0000-000000000810', 'CRM.ASSIGNMENT_RULE.ADMIN',   'Administer Assignment Rules',  'Create, edit, simulate, activate, or deactivate assignment rules',           'ACTIVE', now(), now()),
    ('a0000007-0000-0000-0000-000000000811', 'CRM.OWNERSHIP_HISTORY.READ',  'View Ownership History',      'View the immutable ownership history ledger for any CRM record',             'ACTIVE', now(), now())
ON CONFLICT (code) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 2. Two new roles (template — actual role rows are created per-tenant by
--    CredentialBootstrapService when a new tenant is provisioned, OR by an
--    authenticated admin operation. Here we only insert into a role template
--    table if one exists, OR we skip and rely on the bootstrap flow.)
-- ----------------------------------------------------------------------------
-- Note: roles are tenant-scoped (per CONSTITUTION §3.4). There is no global
-- roles table — only the per-tenant roles table. Seeding here would create
-- roles in a specific tenant, which is wrong for a migration.
--
-- Instead, the bootstrap flow (CredentialBootstrapService) is extended in
-- CRM-008B to also seed the SALES_MANAGER and SALES_REPRESENTATIVE roles
-- for each tenant during tenant provisioning, and to seed them retroactively
-- for existing tenants via a one-time admin operation.
--
-- This migration ONLY:
--   1. Seeds the 17 capabilities (above)
--   2. Records a marker in platform_audit_logs that CRM-008 capabilities
--      are now available — the role seeding is a separate operational step.

INSERT INTO platform_audit_logs (id, tenant_id, actor_user_id, action, result, details, created_at)
SELECT
    gen_random_uuid(),
    '00000000-0000-0000-0000-000000000000'::uuid,
    '00000000-0000-0000-0000-000000000000'::uuid,
    'CRM-008.CAPABILITIES.SEEDED',
    'SUCCESS',
    jsonb_build_object(
        'migration', 'V20260720_8',
        'capabilities_seeded', 17,
        'capability_codes', jsonb_build_array(
            'CRM.ASSIGNMENT.READ','CRM.ASSIGNMENT.WRITE','CRM.ASSIGNMENT.ADMIN',
            'CRM.TRANSFER.READ','CRM.TRANSFER.REQUEST','CRM.TRANSFER.APPROVE','CRM.TRANSFER.EXECUTE',
            'CRM.TEAM.READ','CRM.TEAM.ADMIN',
            'CRM.QUEUE.READ','CRM.QUEUE.CLAIM','CRM.QUEUE.ADMIN',
            'CRM.TERRITORY.READ','CRM.TERRITORY.ADMIN',
            'CRM.ASSIGNMENT_RULE.READ','CRM.ASSIGNMENT_RULE.ADMIN',
            'CRM.OWNERSHIP_HISTORY.READ'
        ),
        'note', 'Roles SALES_MANAGER and SALES_REPRESENTATIVE must be seeded per-tenant via bootstrap.'
    ),
    now()
WHERE NOT EXISTS (
    SELECT 1 FROM platform_audit_logs
    WHERE action = 'CRM-008.CAPABILITIES.SEEDED'
);

COMMIT;

-- Validation:
-- SELECT count(*) FROM access_capabilities WHERE code LIKE 'CRM.ASSIGNMENT%'
--    OR code LIKE 'CRM.TRANSFER%' OR code LIKE 'CRM.TEAM%'
--    OR code LIKE 'CRM.QUEUE%' OR code LIKE 'CRM.TERRITORY%'
--    OR code LIKE 'CRM.ASSIGNMENT_RULE%' OR code LIKE 'CRM.OWNERSHIP_HISTORY%';
-- Expected: 17
