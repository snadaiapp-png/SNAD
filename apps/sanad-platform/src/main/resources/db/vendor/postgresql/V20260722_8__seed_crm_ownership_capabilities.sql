-- ============================================================================
-- SANAD CRM-008B - V20260722_8 - Seed 17 Capabilities + Assign to ADMIN roles
-- ============================================================================
-- Seed migration: required baseline MUST exist.
-- Adds 17 CRM-008 capabilities AND assigns them to all existing ADMIN roles.
-- NOTE: This migration uses PostgreSQL syntax (ON CONFLICT, gen_random_uuid).
-- H2 tests use flyway.clean-disabled=false and validate-on-migrate=false,
-- so failed migrations are cleaned on restart. For H2 local profile, the
-- CredentialBootstrapService.ensureAdminAllCapabilities() method also
-- assigns these capabilities at application startup, providing a fallback
-- for environments where Flyway cannot execute PostgreSQL-specific syntax.
-- ============================================================================

-- Insert 17 CRM-008B capabilities (idempotent on PostgreSQL via ON CONFLICT)
INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
VALUES
    ('a0000007-0000-0000-0000-000000000801', 'CRM.ASSIGNMENT.READ',         'View Assignments',           'Read access to current assignments and ownership history',                  'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('a0000007-0000-0000-0000-000000000802', 'CRM.ASSIGNMENT.WRITE',        'Reassign Record',             'Manually assign or reassign a CRM record to a user, team, or queue',         'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('a0000007-0000-0000-0000-000000000803', 'CRM.ASSIGNMENT.ADMIN',        'Administer Assignments',      'Bulk assignment, rule override, force-assign across teams',                 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('a0000007-0000-0000-0000-000000000804', 'CRM.TRANSFER.READ',           'View Transfers',              'View incoming and outgoing transfer requests',                              'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('a0000007-0000-0000-0000-000000000805', 'CRM.TRANSFER.REQUEST',        'Request Transfer',            'Create a new transfer request for a CRM record',                             'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('a0000007-0000-0000-0000-000000000806', 'CRM.TRANSFER.APPROVE',        'Approve Transfer',            'Approve or reject a transfer request',                                       'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('a0000007-0000-0000-0000-000000000807', 'CRM.TRANSFER.EXECUTE',        'Execute Transfer',            'Execute an approved transfer (system-internal - not granted to humans)',     'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('a0000007-0000-0000-0000-000000000808', 'CRM.TEAM.READ',               'View Sales Teams',            'View sales teams and their memberships',                                      'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('a0000007-0000-0000-0000-000000000809', 'CRM.TEAM.ADMIN',              'Administer Sales Teams',      'Create, edit, suspend, or archive sales teams and change managers',          'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('a0000007-0000-0000-0000-00000000080a', 'CRM.QUEUE.READ',              'View Queues',                 'View queues and their items',                                                 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('a0000007-0000-0000-0000-00000000080b', 'CRM.QUEUE.CLAIM',             'Claim Queue Item',            'Claim an available queue item',                                               'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('a0000007-0000-0000-0000-00000000080c', 'CRM.QUEUE.ADMIN',             'Administer Queues',           'Create, edit, or archive queues and manage their members',                    'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('a0000007-0000-0000-0000-00000000080d', 'CRM.TERRITORY.READ',          'View Territories',            'View territory hierarchy and territory assignments',                         'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('a0000007-0000-0000-0000-00000000080e', 'CRM.TERRITORY.ADMIN',         'Administer Territories',      'Create, edit, or archive territories and manage territory assignments',      'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('a0000007-0000-0000-0000-00000000080f', 'CRM.ASSIGNMENT_RULE.READ',    'View Assignment Rules',       'View assignment rules and their versions',                                    'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('a0000007-0000-0000-0000-000000000810', 'CRM.ASSIGNMENT_RULE.ADMIN',   'Administer Assignment Rules', 'Create, edit, simulate, activate, or deactivate assignment rules',            'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('a0000007-0000-0000-0000-000000000811', 'CRM.OWNERSHIP_HISTORY.READ',  'View Ownership History',      'View the immutable ownership history ledger for any CRM record',             'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO NOTHING;

-- Assign all 17 new CRM-008B capabilities to every existing ADMIN role.
-- This matches the pattern used by V20260717_5 (grant_business_process_capabilities).
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), role.tenant_id, role.id, capability.id, CURRENT_TIMESTAMP
FROM roles role
JOIN access_capabilities capability
  ON capability.code IN (
      'CRM.ASSIGNMENT.READ', 'CRM.ASSIGNMENT.WRITE', 'CRM.ASSIGNMENT.ADMIN',
      'CRM.TRANSFER.READ', 'CRM.TRANSFER.REQUEST', 'CRM.TRANSFER.APPROVE', 'CRM.TRANSFER.EXECUTE',
      'CRM.TEAM.READ', 'CRM.TEAM.ADMIN',
      'CRM.QUEUE.READ', 'CRM.QUEUE.CLAIM', 'CRM.QUEUE.ADMIN',
      'CRM.TERRITORY.READ', 'CRM.TERRITORY.ADMIN',
      'CRM.ASSIGNMENT_RULE.READ', 'CRM.ASSIGNMENT_RULE.ADMIN',
      'CRM.OWNERSHIP_HISTORY.READ'
  )
 AND capability.status = 'ACTIVE'
WHERE role.code = 'ADMIN'
  AND role.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1 FROM role_capabilities rc
      WHERE rc.tenant_id = role.tenant_id
        AND rc.role_id = role.id
        AND rc.capability_id = capability.id
  );
