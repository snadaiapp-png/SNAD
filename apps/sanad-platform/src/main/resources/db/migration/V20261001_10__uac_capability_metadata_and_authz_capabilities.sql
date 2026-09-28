-- ============================================================================
-- V20261001_10 — Wave 1 / Task 4: capability metadata and canonical
-- AUTHORIZATION.* capability seeds.
--
-- Execution overlay:
--   Original Task 4 stamp V20260924_4 is occupied by HRM-G2 on current main.
--   V20261001_10 follows the UAC Task 2/3 overlay stamps _8/_9.
-- ============================================================================

ALTER TABLE access_capabilities
    ADD COLUMN application TEXT NOT NULL DEFAULT 'PLATFORM',
    ADD COLUMN module TEXT NULL,
    ADD COLUMN resource TEXT NULL,
    ADD COLUMN action TEXT NULL,
    ADD COLUMN risk_level TEXT NOT NULL DEFAULT 'MEDIUM'
        CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    ADD COLUMN supports_scope BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN system_protected BOOLEAN NOT NULL DEFAULT FALSE;

-- Preserve canonical uppercase codes; metadata is a derived search/explainability
-- projection and is not an independent authority.
UPDATE access_capabilities
   SET module = NULLIF(split_part(code, '.', 1), ''),
       resource = NULLIF(split_part(code, '.', 2), ''),
       action = NULLIF(split_part(code, '.', 3), '')
 WHERE module IS NULL
    OR resource IS NULL
    OR action IS NULL;

INSERT INTO access_capabilities (
    id, code, name, description, status,
    created_at, updated_at,
    application, module, resource, action,
    risk_level, supports_scope, system_protected
)
VALUES
    (gen_random_uuid(), 'AUTHORIZATION.OVERRIDE.MANAGE',
     'Manage Authorization Overrides', 'Create, revoke, and inspect explicit authorization overrides',
     'ACTIVE', NOW(), NOW(), 'PLATFORM', 'AUTHORIZATION', 'OVERRIDE', 'MANAGE', 'CRITICAL', TRUE, TRUE),
    (gen_random_uuid(), 'AUTHORIZATION.RELATIONSHIP.MANAGE',
     'Manage Authorization Relationships', 'Manage authorization subject relationships',
     'ACTIVE', NOW(), NOW(), 'PLATFORM', 'AUTHORIZATION', 'RELATIONSHIP', 'MANAGE', 'CRITICAL', TRUE, TRUE),
    (gen_random_uuid(), 'AUTHORIZATION.RESYNC',
     'Resynchronize Authorization', 'Recovery-only resynchronization of effective authorization state',
     'ACTIVE', NOW(), NOW(), 'PLATFORM', 'AUTHORIZATION', 'RESYNC', NULL, 'CRITICAL', FALSE, TRUE),
    (gen_random_uuid(), 'AUTHORIZATION.BREAK_GLASS',
     'Break Glass Authorization', 'Create governed time-bounded emergency authorization grants',
     'ACTIVE', NOW(), NOW(), 'PLATFORM', 'AUTHORIZATION', 'BREAK_GLASS', NULL, 'CRITICAL', TRUE, TRUE),
    (gen_random_uuid(), 'AUTHORIZATION.RECOVER',
     'Recover Authorization', 'Perform governed authorization recovery operations',
     'ACTIVE', NOW(), NOW(), 'PLATFORM', 'AUTHORIZATION', 'RECOVER', NULL, 'CRITICAL', FALSE, TRUE),
    (gen_random_uuid(), 'AUTHORIZATION.PLATFORM.MANAGE',
     'Manage Platform Authorization', 'Administer platform-scoped authorization configuration',
     'ACTIVE', NOW(), NOW(), 'PLATFORM', 'AUTHORIZATION', 'PLATFORM', 'MANAGE', 'CRITICAL', TRUE, TRUE)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    description = EXCLUDED.description,
    status = 'ACTIVE',
    updated_at = NOW(),
    application = EXCLUDED.application,
    module = EXCLUDED.module,
    resource = EXCLUDED.resource,
    action = EXCLUDED.action,
    risk_level = 'CRITICAL',
    supports_scope = EXCLUDED.supports_scope,
    system_protected = TRUE;
