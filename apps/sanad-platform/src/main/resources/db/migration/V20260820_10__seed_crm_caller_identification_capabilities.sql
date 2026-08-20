-- G8 EXECUTION 02 — Caller identification capabilities (Track B RBAC gate).
--
-- Forward-only migration. Idempotent seeding following the established
-- pattern (V20260717_101): capabilities are inserted WHERE NOT EXISTS and
-- granted to every ACTIVE ADMIN role (all tenants) without duplication.
--
-- No applied migration is modified; if this migration is ever superseded a
-- NEW migration must be added (Flyway checksum governance).

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), capability.code, capability.name, capability.description,
       'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (VALUES
    ('CRM.CALLER_ID.READ',
     'Read caller identification card',
     'Look up an inbound phone number and read the minimal caller card within the tenant.'),
    ('CRM.CALLER_ID.READ_RESTRICTED',
     'Read restricted caller identity',
     'Disclose RESTRICTED and CONFIDENTIAL caller identity during caller identification.')
) AS capability(code, name, description)
WHERE NOT EXISTS (
    SELECT 1 FROM access_capabilities existing WHERE existing.code = capability.code
);

INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), role.tenant_id, role.id, capability.id, CURRENT_TIMESTAMP
FROM roles role
JOIN access_capabilities capability
  ON capability.code IN ('CRM.CALLER_ID.READ', 'CRM.CALLER_ID.READ_RESTRICTED')
 AND capability.status = 'ACTIVE'
WHERE role.code = 'ADMIN'
  AND role.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1 FROM role_capabilities existing
      WHERE existing.tenant_id = role.tenant_id
        AND existing.role_id = role.id
        AND existing.capability_id = capability.id
  );
