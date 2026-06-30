-- Assign CRM account permissions to every tenant administrator.
INSERT INTO role_capabilities (id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), role.id, capability.id, CURRENT_TIMESTAMP
FROM roles role
JOIN access_capabilities capability
  ON capability.code IN (
      'CRM.ACCOUNT.READ',
      'CRM.ACCOUNT.CREATE',
      'CRM.ACCOUNT.WRITE',
      'CRM.ACCOUNT.ARCHIVE'
  )
WHERE role.code = 'ADMIN'
  AND role.status = 'ACTIVE'
  AND capability.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1
      FROM role_capabilities existing
      WHERE existing.role_id = role.id
        AND existing.capability_id = capability.id
  );
