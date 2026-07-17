-- Ensure existing active tenant administrators receive the capabilities added by
-- V20260717_4. New grants remain deny-by-default for every non-admin role.

INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), role.tenant_id, role.id, capability.id, CURRENT_TIMESTAMP
FROM roles role
JOIN access_capabilities capability
  ON capability.code IN (
      'BUSINESS_PROCESS.READ',
      'BUSINESS_PROCESS.EXECUTE'
  )
 AND capability.status = 'ACTIVE'
WHERE role.code = 'ADMIN'
  AND role.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1
      FROM role_capabilities existing
      WHERE existing.tenant_id = role.tenant_id
        AND existing.role_id = role.id
        AND existing.capability_id = capability.id
  );
