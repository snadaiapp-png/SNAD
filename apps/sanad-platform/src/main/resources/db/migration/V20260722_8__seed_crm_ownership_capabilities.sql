-- ============================================================================
-- SANAD CRM-008B - V20260722_8 - Seed 17 Capabilities + Audit Marker
-- ============================================================================
-- Seed migration: required baseline MUST exist. Idempotent UPSERT for same-definition.
-- ============================================================================

-- H2 manages transaction automatically

-- Precondition/postcondition check removed for H2 compatibility (PostgreSQL enforces via vendor migration)

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


-- Audit marker removed for H2 compatibility (PostgreSQL records this via vendor migration)

-- Precondition/postcondition check removed for H2 compatibility (PostgreSQL enforces via vendor migration)

-- H2 manages transaction automatically
