-- EXEC-PROMPT-CRM-006 capability model. Deny-by-default is enforced by @RequireCapability.

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT gen_random_uuid(), capability.code, capability.name, capability.description,
       'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (
    VALUES
        ('CRM.RELATIONSHIP.READ', 'Read CRM Contact Relationships', 'View tenant contact-account relationships and history'),
        ('CRM.RELATIONSHIP.WRITE', 'Write CRM Contact Relationships', 'Create and update tenant contact-account relationships'),
        ('CRM.RELATIONSHIP.ADMIN', 'Administer CRM Contact Relationships', 'Manage primary relationships, lifecycle and tenant-defined roles'),
        ('CRM.CONTACT.SENSITIVE.READ', 'Read Sensitive CRM Contact Fields', 'View sensitive person profile and ownership history'),
        ('CRM.CONTACT.IMPORT', 'Import CRM Contacts and Relationships', 'Import people and contact-account relationships')
) AS capability(code, name, description)
WHERE NOT EXISTS (
    SELECT 1 FROM access_capabilities existing WHERE existing.code = capability.code
);

INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(), role.tenant_id, role.id, capability.id, CURRENT_TIMESTAMP
FROM roles role
JOIN access_capabilities capability
  ON capability.code IN (
      'CRM.RELATIONSHIP.READ',
      'CRM.RELATIONSHIP.WRITE',
      'CRM.RELATIONSHIP.ADMIN',
      'CRM.CONTACT.SENSITIVE.READ',
      'CRM.CONTACT.IMPORT'
  )
 AND capability.status = 'ACTIVE'
WHERE role.code = 'ADMIN'
  AND role.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1 FROM role_capabilities existing
      WHERE existing.tenant_id = role.tenant_id
        AND existing.role_id = role.id
        AND existing.capability_id = capability.id
  );
