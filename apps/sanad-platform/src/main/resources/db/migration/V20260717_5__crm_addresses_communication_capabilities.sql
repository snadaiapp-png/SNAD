-- EXEC-PROMPT-CRM-007 capability baseline.

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), capability.code, capability.name, capability.description,
       'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (VALUES
    ('CRM.ADDRESS.READ', 'Read CRM addresses', 'View tenant-scoped account and person addresses'),
    ('CRM.ADDRESS.WRITE', 'Write CRM addresses', 'Create and update tenant-scoped account and person addresses'),
    ('CRM.ADDRESS.ADMIN', 'Administer CRM addresses', 'Archive, reactivate and govern primary CRM addresses'),
    ('CRM.ADDRESS.EXPORT', 'Export CRM addresses', 'Export tenant-scoped CRM addresses subject to access controls'),
    ('CRM.COMMUNICATION.READ', 'Read CRM communication methods', 'View masked tenant-scoped communication methods'),
    ('CRM.COMMUNICATION.WRITE', 'Write CRM communication methods', 'Create and update communication methods'),
    ('CRM.COMMUNICATION.ADMIN', 'Administer CRM communication methods', 'Archive, reactivate and govern verification and preferred channels'),
    ('CRM.COMMUNICATION.SENSITIVE.READ', 'Read sensitive CRM communication values', 'View unmasked confidential and restricted communication values'),
    ('CRM.COMMUNICATION.EXPORT', 'Export CRM communication values', 'Export communication values subject to field-level privacy controls')
) AS capability(code, name, description)
WHERE NOT EXISTS (
    SELECT 1 FROM access_capabilities existing WHERE existing.code=capability.code
);

-- Existing ADMIN roles receive the new governed capabilities. No other role is broadened implicitly.
INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), role.tenant_id, role.id, capability.id, CURRENT_TIMESTAMP
FROM roles role
JOIN access_capabilities capability
  ON capability.code IN (
      'CRM.ADDRESS.READ','CRM.ADDRESS.WRITE','CRM.ADDRESS.ADMIN','CRM.ADDRESS.EXPORT',
      'CRM.COMMUNICATION.READ','CRM.COMMUNICATION.WRITE','CRM.COMMUNICATION.ADMIN',
      'CRM.COMMUNICATION.SENSITIVE.READ','CRM.COMMUNICATION.EXPORT'
  )
 AND capability.status='ACTIVE'
WHERE role.code='ADMIN'
  AND role.status='ACTIVE'
  AND NOT EXISTS (
      SELECT 1 FROM role_capabilities existing
      WHERE existing.tenant_id=role.tenant_id
        AND existing.role_id=role.id
        AND existing.capability_id=capability.id
  );
