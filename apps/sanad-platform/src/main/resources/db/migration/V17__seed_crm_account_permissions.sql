-- CRM account permission catalog.
INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT CAST('a0000010-0000-0000-0000-000000000001' AS uuid),
       'CRM.ACCOUNT.READ', 'Read CRM Accounts', 'View CRM accounts',
       'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'CRM.ACCOUNT.READ');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT CAST('a0000010-0000-0000-0000-000000000002' AS uuid),
       'CRM.ACCOUNT.CREATE', 'Create CRM Accounts', 'Create CRM accounts',
       'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'CRM.ACCOUNT.CREATE');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT CAST('a0000010-0000-0000-0000-000000000003' AS uuid),
       'CRM.ACCOUNT.WRITE', 'Update CRM Accounts', 'Update CRM accounts',
       'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'CRM.ACCOUNT.WRITE');

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
SELECT CAST('a0000010-0000-0000-0000-000000000004' AS uuid),
       'CRM.ACCOUNT.ARCHIVE', 'Archive CRM Accounts', 'Archive CRM accounts',
       'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM access_capabilities WHERE code = 'CRM.ACCOUNT.ARCHIVE');
