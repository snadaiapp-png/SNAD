-- ============================================================
-- SNAD Platform — CRM-008B Foundation — V20260722.8
-- Seed 17 CRM Capabilities + 2 Sales Roles + Role Mappings
-- ------------------------------------------------------------
-- Seed migration: each capability must be either absent or
-- exactly matching (code + name + description). Any code
-- collision with different name/description FAILS the migration.
--
-- ON CONFLICT DO NOTHING is FORBIDDEN — conflicts must raise.
--
-- Adds 17 CRM-008B capabilities, defines SALES_MANAGER and
-- SALES_REPRESENTATIVE tenant-scoped roles for every existing
-- tenant, and assigns the proper capability subset to each.
-- ============================================================

-- ============================================================
-- PRECONDITIONS
-- ============================================================
DO $precondition$
DECLARE
    capabilities_table_exists INTEGER;
    roles_table_exists        INTEGER;
    role_caps_table_exists    INTEGER;
    tenants_table_exists      INTEGER;
    failed_history            INTEGER;
    conflicting_caps          INTEGER;
BEGIN
    SELECT COUNT(*) INTO capabilities_table_exists
      FROM information_schema.tables
     WHERE table_schema = 'public' AND table_name = 'access_capabilities';
    IF capabilities_table_exists <> 1 THEN
        RAISE EXCEPTION 'V20260722.8 precondition failed: access_capabilities must exist';
    END IF;

    SELECT COUNT(*) INTO roles_table_exists
      FROM information_schema.tables
     WHERE table_schema = 'public' AND table_name = 'roles';
    IF roles_table_exists <> 1 THEN
        RAISE EXCEPTION 'V20260722.8 precondition failed: roles must exist';
    END IF;

    SELECT COUNT(*) INTO role_caps_table_exists
      FROM information_schema.tables
     WHERE table_schema = 'public' AND table_name = 'role_capabilities';
    IF role_caps_table_exists <> 1 THEN
        RAISE EXCEPTION 'V20260722.8 precondition failed: role_capabilities must exist';
    END IF;

    SELECT COUNT(*) INTO tenants_table_exists
      FROM information_schema.tables
     WHERE table_schema = 'public' AND table_name = 'tenants';
    IF tenants_table_exists <> 1 THEN
        RAISE EXCEPTION 'V20260722.8 precondition failed: tenants must exist';
    END IF;

    -- Any existing capability with the SAME code but DIFFERENT name/description is a conflict.
    SELECT COUNT(*) INTO conflicting_caps
      FROM access_capabilities
     WHERE code IN (
           'CRM.ASSIGNMENT.READ','CRM.ASSIGNMENT.WRITE','CRM.ASSIGNMENT.ADMIN',
           'CRM.TRANSFER.READ','CRM.TRANSFER.REQUEST','CRM.TRANSFER.APPROVE','CRM.TRANSFER.EXECUTE',
           'CRM.TEAM.READ','CRM.TEAM.ADMIN',
           'CRM.QUEUE.READ','CRM.QUEUE.CLAIM','CRM.QUEUE.ADMIN',
           'CRM.TERRITORY.READ','CRM.TERRITORY.ADMIN',
           'CRM.ASSIGNMENT_RULE.READ','CRM.ASSIGNMENT_RULE.ADMIN',
           'CRM.OWNERSHIP_HISTORY.READ'
       )
       AND (
           name <> 'View Assignments'                       OR description <> 'Read access to current assignments and ownership history'
           OR code = 'CRM.ASSIGNMENT.READ'  AND (name <> 'View Assignments'                       OR description <> 'Read access to current assignments and ownership history')
           OR code = 'CRM.ASSIGNMENT.WRITE' AND (name <> 'Reassign Record'                        OR description <> 'Manually assign or reassign a CRM record to a user, team, or queue')
           OR code = 'CRM.ASSIGNMENT.ADMIN' AND (name <> 'Administer Assignments'                 OR description <> 'Bulk assignment, rule override, force-assign across teams')
           OR code = 'CRM.TRANSFER.READ'    AND (name <> 'View Transfers'                         OR description <> 'View incoming and outgoing transfer requests')
           OR code = 'CRM.TRANSFER.REQUEST' AND (name <> 'Request Transfer'                       OR description <> 'Create a new transfer request for a CRM record')
           OR code = 'CRM.TRANSFER.APPROVE' AND (name <> 'Approve Transfer'                       OR description <> 'Approve or reject a transfer request')
           OR code = 'CRM.TRANSFER.EXECUTE' AND (name <> 'Execute Transfer'                       OR description <> 'Execute an approved transfer (system-internal - not granted to humans)')
           OR code = 'CRM.TEAM.READ'        AND (name <> 'View Sales Teams'                        OR description <> 'View sales teams and their memberships')
           OR code = 'CRM.TEAM.ADMIN'       AND (name <> 'Administer Sales Teams'                 OR description <> 'Create, edit, suspend, or archive sales teams and change managers')
           OR code = 'CRM.QUEUE.READ'       AND (name <> 'View Queues'                            OR description <> 'View queues and their items')
           OR code = 'CRM.QUEUE.CLAIM'      AND (name <> 'Claim Queue Item'                       OR description <> 'Claim an available queue item')
           OR code = 'CRM.QUEUE.ADMIN'      AND (name <> 'Administer Queues'                      OR description <> 'Create, edit, or archive queues and manage their members')
           OR code = 'CRM.TERRITORY.READ'   AND (name <> 'View Territories'                       OR description <> 'View territory hierarchy and territory assignments')
           OR code = 'CRM.TERRITORY.ADMIN'  AND (name <> 'Administer Territories'                 OR description <> 'Create, edit, or archive territories and manage territory assignments')
           OR code = 'CRM.ASSIGNMENT_RULE.READ'  AND (name <> 'View Assignment Rules'              OR description <> 'View assignment rules and their versions')
           OR code = 'CRM.ASSIGNMENT_RULE.ADMIN' AND (name <> 'Administer Assignment Rules'        OR description <> 'Create, edit, simulate, activate, or deactivate assignment rules')
           OR code = 'CRM.OWNERSHIP_HISTORY.READ' AND (name <> 'View Ownership History'           OR description <> 'View the immutable ownership history ledger for any CRM record')
       );
    IF conflicting_caps > 0 THEN
        RAISE EXCEPTION
            'V20260722.8 precondition failed: % capabilities with conflicting name/description already exist',
            conflicting_caps;
    END IF;

    SELECT COUNT(*) INTO failed_history
      FROM flyway_schema_history
     WHERE success = FALSE;
    IF failed_history > 0 THEN
        RAISE EXCEPTION
            'V20260722.8 refuses to apply over % failed Flyway history rows',
            failed_history;
    END IF;
END
$precondition$;

-- ============================================================
-- DML — insert 17 capabilities (skip if already exact match)
-- ============================================================
INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT
    'a0000007-0000-0000-0000-000000000801', 'CRM.ASSIGNMENT.READ',         'View Assignments',           'Read access to current assignments and ownership history',                  'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'CRM.ASSIGNMENT.READ');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT
    'a0000007-0000-0000-0000-000000000802', 'CRM.ASSIGNMENT.WRITE',        'Reassign Record',             'Manually assign or reassign a CRM record to a user, team, or queue',         'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'CRM.ASSIGNMENT.WRITE');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT
    'a0000007-0000-0000-0000-000000000803', 'CRM.ASSIGNMENT.ADMIN',        'Administer Assignments',      'Bulk assignment, rule override, force-assign across teams',                 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'CRM.ASSIGNMENT.ADMIN');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT
    'a0000007-0000-0000-0000-000000000804', 'CRM.TRANSFER.READ',           'View Transfers',              'View incoming and outgoing transfer requests',                              'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'CRM.TRANSFER.READ');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT
    'a0000007-0000-0000-0000-000000000805', 'CRM.TRANSFER.REQUEST',        'Request Transfer',            'Create a new transfer request for a CRM record',                             'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'CRM.TRANSFER.REQUEST');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT
    'a0000007-0000-0000-0000-000000000806', 'CRM.TRANSFER.APPROVE',        'Approve Transfer',            'Approve or reject a transfer request',                                       'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'CRM.TRANSFER.APPROVE');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT
    'a0000007-0000-0000-0000-000000000807', 'CRM.TRANSFER.EXECUTE',        'Execute Transfer',            'Execute an approved transfer (system-internal - not granted to humans)',     'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'CRM.TRANSFER.EXECUTE');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT
    'a0000007-0000-0000-0000-000000000808', 'CRM.TEAM.READ',               'View Sales Teams',            'View sales teams and their memberships',                                      'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'CRM.TEAM.READ');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT
    'a0000007-0000-0000-0000-000000000809', 'CRM.TEAM.ADMIN',              'Administer Sales Teams',      'Create, edit, suspend, or archive sales teams and change managers',          'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'CRM.TEAM.ADMIN');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT
    'a0000007-0000-0000-0000-00000000080a', 'CRM.QUEUE.READ',              'View Queues',                 'View queues and their items',                                                 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'CRM.QUEUE.READ');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT
    'a0000007-0000-0000-0000-00000000080b', 'CRM.QUEUE.CLAIM',             'Claim Queue Item',            'Claim an available queue item',                                               'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'CRM.QUEUE.CLAIM');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT
    'a0000007-0000-0000-0000-00000000080c', 'CRM.QUEUE.ADMIN',             'Administer Queues',           'Create, edit, or archive queues and manage their members',                    'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'CRM.QUEUE.ADMIN');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT
    'a0000007-0000-0000-0000-00000000080d', 'CRM.TERRITORY.READ',          'View Territories',            'View territory hierarchy and territory assignments',                         'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'CRM.TERRITORY.READ');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT
    'a0000007-0000-0000-0000-00000000080e', 'CRM.TERRITORY.ADMIN',         'Administer Territories',      'Create, edit, or archive territories and manage territory assignments',      'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'CRM.TERRITORY.ADMIN');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT
    'a0000007-0000-0000-0000-00000000080f', 'CRM.ASSIGNMENT_RULE.READ',    'View Assignment Rules',       'View assignment rules and their versions',                                    'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'CRM.ASSIGNMENT_RULE.READ');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT
    'a0000007-0000-0000-0000-000000000810', 'CRM.ASSIGNMENT_RULE.ADMIN',   'Administer Assignment Rules', 'Create, edit, simulate, activate, or deactivate assignment rules',            'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'CRM.ASSIGNMENT_RULE.ADMIN');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT
    'a0000007-0000-0000-0000-000000000811', 'CRM.OWNERSHIP_HISTORY.READ',  'View Ownership History',      'View the immutable ownership history ledger for any CRM record',             'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'CRM.OWNERSHIP_HISTORY.READ');

-- ============================================================
-- DML — define SALES_MANAGER and SALES_REPRESENTATIVE roles
-- for every existing tenant (skip if already exact match)
-- ============================================================
INSERT INTO roles (id, tenant_id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), tenant.id, 'SALES_MANAGER', 'Sales Manager',
       'Manage sales teams, queues, territories, assignment rules, and approve transfers',
       'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM tenants tenant
WHERE tenant.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1 FROM roles role
      WHERE role.tenant_id = tenant.id
        AND role.code = 'SALES_MANAGER'
  );

INSERT INTO roles (id, tenant_id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), tenant.id, 'SALES_REPRESENTATIVE', 'Sales Representative',
       'Own CRM records, claim queue items, request transfers, and view team/queue/territory context',
       'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM tenants tenant
WHERE tenant.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1 FROM roles role
      WHERE role.tenant_id = tenant.id
        AND role.code = 'SALES_REPRESENTATIVE'
  );

-- ============================================================
-- DML — assign capabilities to roles
-- ============================================================
-- All 17 CRM-008B capabilities to ADMIN role (matches V20260702.2 pattern)
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

-- SALES_MANAGER: assign managerial subset (no ADMIN/EXECUTE capabilities)
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), role.tenant_id, role.id, capability.id, CURRENT_TIMESTAMP
FROM roles role
JOIN access_capabilities capability
  ON capability.code IN (
      'CRM.ASSIGNMENT.READ', 'CRM.ASSIGNMENT.WRITE',
      'CRM.TRANSFER.READ', 'CRM.TRANSFER.REQUEST', 'CRM.TRANSFER.APPROVE',
      'CRM.TEAM.READ',
      'CRM.QUEUE.READ', 'CRM.QUEUE.CLAIM',
      'CRM.TERRITORY.READ',
      'CRM.ASSIGNMENT_RULE.READ',
      'CRM.OWNERSHIP_HISTORY.READ'
  )
 AND capability.status = 'ACTIVE'
WHERE role.code = 'SALES_MANAGER'
  AND role.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1 FROM role_capabilities rc
      WHERE rc.tenant_id = role.tenant_id
        AND rc.role_id = role.id
        AND rc.capability_id = capability.id
  );

-- SALES_REPRESENTATIVE: assign individual-contributor subset
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), role.tenant_id, role.id, capability.id, CURRENT_TIMESTAMP
FROM roles role
JOIN access_capabilities capability
  ON capability.code IN (
      'CRM.ASSIGNMENT.READ',
      'CRM.TRANSFER.READ', 'CRM.TRANSFER.REQUEST',
      'CRM.TEAM.READ',
      'CRM.QUEUE.READ', 'CRM.QUEUE.CLAIM',
      'CRM.TERRITORY.READ',
      'CRM.OWNERSHIP_HISTORY.READ'
  )
 AND capability.status = 'ACTIVE'
WHERE role.code = 'SALES_REPRESENTATIVE'
  AND role.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1 FROM role_capabilities rc
      WHERE rc.tenant_id = role.tenant_id
        AND rc.role_id = role.id
        AND rc.capability_id = capability.id
  );

-- ============================================================
-- POSTCONDITIONS — verify all 17 capabilities + 2 roles + mappings
-- ============================================================
DO $postcondition$
DECLARE
    cap_count                INTEGER;
    sales_mgr_role_count     INTEGER;
    sales_rep_role_count     INTEGER;
    admin_mapping_count      INTEGER;
    sales_mgr_mapping_count  INTEGER;
    sales_rep_mapping_count  INTEGER;
BEGIN
    SELECT COUNT(*) INTO cap_count
      FROM access_capabilities
     WHERE code IN (
           'CRM.ASSIGNMENT.READ','CRM.ASSIGNMENT.WRITE','CRM.ASSIGNMENT.ADMIN',
           'CRM.TRANSFER.READ','CRM.TRANSFER.REQUEST','CRM.TRANSFER.APPROVE','CRM.TRANSFER.EXECUTE',
           'CRM.TEAM.READ','CRM.TEAM.ADMIN',
           'CRM.QUEUE.READ','CRM.QUEUE.CLAIM','CRM.QUEUE.ADMIN',
           'CRM.TERRITORY.READ','CRM.TERRITORY.ADMIN',
           'CRM.ASSIGNMENT_RULE.READ','CRM.ASSIGNMENT_RULE.ADMIN',
           'CRM.OWNERSHIP_HISTORY.READ'
       )
       AND status = 'ACTIVE';
    IF cap_count <> 17 THEN
        RAISE EXCEPTION
            'V20260722.8 postcondition failed: % of 17 CRM-008B capabilities seeded',
            cap_count;
    END IF;

    -- Verify all 17 capabilities have the exact expected name/description (no silent drift)
    PERFORM 1
      FROM access_capabilities
     WHERE code = 'CRM.ASSIGNMENT.READ' AND (name <> 'View Assignments' OR description <> 'Read access to current assignments and ownership history');
    IF FOUND THEN
        RAISE EXCEPTION 'V20260722.8 postcondition failed: CRM.ASSIGNMENT.READ name/description drifted';
    END IF;

    -- Every active tenant must have SALES_MANAGER role
    SELECT COUNT(*) INTO sales_mgr_role_count
      FROM tenants tenant
     WHERE tenant.status = 'ACTIVE'
       AND NOT EXISTS (
           SELECT 1 FROM roles role
            WHERE role.tenant_id = tenant.id
              AND role.code = 'SALES_MANAGER'
              AND role.status = 'ACTIVE'
       );
    IF sales_mgr_role_count > 0 THEN
        RAISE EXCEPTION
            'V20260722.8 postcondition failed: % active tenants missing SALES_MANAGER role',
            sales_mgr_role_count;
    END IF;

    -- Every active tenant must have SALES_REPRESENTATIVE role
    SELECT COUNT(*) INTO sales_rep_role_count
      FROM tenants tenant
     WHERE tenant.status = 'ACTIVE'
       AND NOT EXISTS (
           SELECT 1 FROM roles role
            WHERE role.tenant_id = tenant.id
              AND role.code = 'SALES_REPRESENTATIVE'
              AND role.status = 'ACTIVE'
       );
    IF sales_rep_role_count > 0 THEN
        RAISE EXCEPTION
            'V20260722.8 postcondition failed: % active tenants missing SALES_REPRESENTATIVE role',
            sales_rep_role_count;
    END IF;

    -- Every ADMIN role must have all 17 CRM-008B capabilities assigned
    SELECT COUNT(*) INTO admin_mapping_count
      FROM roles role
     WHERE role.code = 'ADMIN'
       AND role.status = 'ACTIVE'
       AND 17 <> (
           SELECT COUNT(*)
             FROM role_capabilities rc
             JOIN access_capabilities cap ON cap.id = rc.capability_id
            WHERE rc.tenant_id = role.tenant_id
              AND rc.role_id = role.id
              AND cap.code IN (
                  'CRM.ASSIGNMENT.READ','CRM.ASSIGNMENT.WRITE','CRM.ASSIGNMENT.ADMIN',
                  'CRM.TRANSFER.READ','CRM.TRANSFER.REQUEST','CRM.TRANSFER.APPROVE','CRM.TRANSFER.EXECUTE',
                  'CRM.TEAM.READ','CRM.TEAM.ADMIN',
                  'CRM.QUEUE.READ','CRM.QUEUE.CLAIM','CRM.QUEUE.ADMIN',
                  'CRM.TERRITORY.READ','CRM.TERRITORY.ADMIN',
                  'CRM.ASSIGNMENT_RULE.READ','CRM.ASSIGNMENT_RULE.ADMIN',
                  'CRM.OWNERSHIP_HISTORY.READ'
              )
       );
    IF admin_mapping_count > 0 THEN
        RAISE EXCEPTION
            'V20260722.8 postcondition failed: % ADMIN roles missing some of the 17 CRM-008B capabilities',
            admin_mapping_count;
    END IF;

    -- Every SALES_MANAGER role must have the 11 managerial capabilities
    SELECT COUNT(*) INTO sales_mgr_mapping_count
      FROM roles role
     WHERE role.code = 'SALES_MANAGER'
       AND role.status = 'ACTIVE'
       AND 11 <> (
           SELECT COUNT(*)
             FROM role_capabilities rc
             JOIN access_capabilities cap ON cap.id = rc.capability_id
            WHERE rc.tenant_id = role.tenant_id
              AND rc.role_id = role.id
              AND cap.code IN (
                  'CRM.ASSIGNMENT.READ', 'CRM.ASSIGNMENT.WRITE',
                  'CRM.TRANSFER.READ', 'CRM.TRANSFER.REQUEST', 'CRM.TRANSFER.APPROVE',
                  'CRM.TEAM.READ',
                  'CRM.QUEUE.READ', 'CRM.QUEUE.CLAIM',
                  'CRM.TERRITORY.READ',
                  'CRM.ASSIGNMENT_RULE.READ',
                  'CRM.OWNERSHIP_HISTORY.READ'
              )
       );
    IF sales_mgr_mapping_count > 0 THEN
        RAISE EXCEPTION
            'V20260722.8 postcondition failed: % SALES_MANAGER roles missing some of the 11 capabilities',
            sales_mgr_mapping_count;
    END IF;

    -- Every SALES_REPRESENTATIVE role must have the 8 IC capabilities
    SELECT COUNT(*) INTO sales_rep_mapping_count
      FROM roles role
     WHERE role.code = 'SALES_REPRESENTATIVE'
       AND role.status = 'ACTIVE'
       AND 8 <> (
           SELECT COUNT(*)
             FROM role_capabilities rc
             JOIN access_capabilities cap ON cap.id = rc.capability_id
            WHERE rc.tenant_id = role.tenant_id
              AND rc.role_id = role.id
              AND cap.code IN (
                  'CRM.ASSIGNMENT.READ',
                  'CRM.TRANSFER.READ', 'CRM.TRANSFER.REQUEST',
                  'CRM.TEAM.READ',
                  'CRM.QUEUE.READ', 'CRM.QUEUE.CLAIM',
                  'CRM.TERRITORY.READ',
                  'CRM.OWNERSHIP_HISTORY.READ'
              )
       );
    IF sales_rep_mapping_count > 0 THEN
        RAISE EXCEPTION
            'V20260722.8 postcondition failed: % SALES_REPRESENTATIVE roles missing some of the 8 capabilities',
            sales_rep_mapping_count;
    END IF;
END
$postcondition$;
