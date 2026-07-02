-- SANAD Stage 06 — Workshop Execution RBAC capabilities.

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT uuid_val, code, name, description, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (
    VALUES
    (CAST('a0000008-0000-0000-0000-000000000001' AS uuid), 'WORKSHOP.READ',    'Read Workshops',    'View workshops, boards, items, assignments and activities'),
    (CAST('a0000008-0000-0000-0000-000000000002' AS uuid), 'WORKSHOP.CREATE',  'Create Workshops',  'Create workshops and initial execution plans'),
    (CAST('a0000008-0000-0000-0000-000000000003' AS uuid), 'WORKSHOP.UPDATE',  'Update Workshops',  'Change workshop details and lifecycle status'),
    (CAST('a0000008-0000-0000-0000-000000000004' AS uuid), 'WORKSHOP.EXECUTE', 'Execute Workshops', 'Create and transition work items, checklists and work logs'),
    (CAST('a0000008-0000-0000-0000-000000000005' AS uuid), 'WORKSHOP.ASSIGN',  'Assign Workshop Work', 'Assign users and execution roles to work items'),
    (CAST('a0000008-0000-0000-0000-000000000006' AS uuid), 'WORKSHOP.MANAGE',  'Manage Workshop Dependencies', 'Manage dependencies, completion gates and administrative controls')
) AS t(uuid_val, code, name, description)
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities ac WHERE ac.code = t.code);

-- Newly introduced active capabilities are granted to every tenant ADMIN role.
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), r.tenant_id, r.id, ac.id, CURRENT_TIMESTAMP
FROM roles r
JOIN access_capabilities ac ON ac.code LIKE 'WORKSHOP.%' AND ac.status = 'ACTIVE'
WHERE r.code IN ('ADMIN', 'SUPER_ADMIN')
  AND NOT EXISTS (
      SELECT 1 FROM role_capabilities rc
      WHERE rc.tenant_id = r.tenant_id
        AND rc.role_id = r.id
        AND rc.capability_id = ac.id
  );
