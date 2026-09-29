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

-- V20261001_7 reconciled PLATFORM_OWNER against every capability that existed
-- at that point. The six AUTHORIZATION.* capabilities above are created later,
-- so this migration must extend the same canonical owner invariant instead of
-- leaving a temporal authorization gap. Keep Platform IAM as the authority:
-- this is a reconciliation of its protected owner role, not a second role model.
DO $$
DECLARE
    control_tenant CONSTANT UUID := '00000000-0000-0000-0000-000000000001'::uuid;
    owner_id CONSTANT UUID := '00000000-0000-0000-0000-000000000010'::uuid;
    platform_owner_role_id UUID;
    missing_capability_count INTEGER;
    missing_scope_count INTEGER;
BEGIN
    PERFORM set_config('app.tenant_id', control_tenant::text, TRUE);

    SELECT id INTO platform_owner_role_id
      FROM roles
     WHERE tenant_id = control_tenant
       AND code = 'PLATFORM_OWNER'
       AND status = 'ACTIVE';

    IF platform_owner_role_id IS NULL THEN
        RAISE EXCEPTION 'ACTIVE PLATFORM_OWNER prerequisite missing for UAC capability reconciliation';
    END IF;

    INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
    SELECT gen_random_uuid(), control_tenant, platform_owner_role_id, ac.id, NOW()
      FROM access_capabilities ac
     WHERE ac.code IN (
         'AUTHORIZATION.OVERRIDE.MANAGE',
         'AUTHORIZATION.RELATIONSHIP.MANAGE',
         'AUTHORIZATION.RESYNC',
         'AUTHORIZATION.BREAK_GLASS',
         'AUTHORIZATION.RECOVER',
         'AUTHORIZATION.PLATFORM.MANAGE'
     )
       AND ac.status = 'ACTIVE'
       AND NOT EXISTS (
           SELECT 1
             FROM role_capabilities rc
            WHERE rc.tenant_id = control_tenant
              AND rc.role_id = platform_owner_role_id
              AND rc.capability_id = ac.id
       );

    INSERT INTO access_scope_grants (
        id, tenant_id, role_id, user_id, capability_id, scope_type,
        organization_id, org_unit_id, legal_entity_id,
        is_direct_exception, reason, granted_by,
        effective_from, effective_to, status, created_at
    )
    SELECT gen_random_uuid(), control_tenant, platform_owner_role_id, NULL, ac.id, 'TENANT',
           NULL, NULL, NULL, FALSE, 'Canonical Platform Owner UAC tenant scope', owner_id,
           NOW(), NULL, 'ACTIVE', NOW()
      FROM access_capabilities ac
     WHERE ac.code IN (
         'AUTHORIZATION.OVERRIDE.MANAGE',
         'AUTHORIZATION.RELATIONSHIP.MANAGE',
         'AUTHORIZATION.RESYNC',
         'AUTHORIZATION.BREAK_GLASS',
         'AUTHORIZATION.RECOVER',
         'AUTHORIZATION.PLATFORM.MANAGE'
     )
       AND ac.status = 'ACTIVE'
       AND NOT EXISTS (
           SELECT 1
             FROM access_scope_grants asg
            WHERE asg.tenant_id = control_tenant
              AND asg.role_id = platform_owner_role_id
              AND asg.user_id IS NULL
              AND asg.capability_id = ac.id
              AND asg.scope_type = 'TENANT'
              AND asg.status = 'ACTIVE'
       );

    SELECT COUNT(*) INTO missing_capability_count
      FROM access_capabilities ac
     WHERE ac.code IN (
         'AUTHORIZATION.OVERRIDE.MANAGE',
         'AUTHORIZATION.RELATIONSHIP.MANAGE',
         'AUTHORIZATION.RESYNC',
         'AUTHORIZATION.BREAK_GLASS',
         'AUTHORIZATION.RECOVER',
         'AUTHORIZATION.PLATFORM.MANAGE'
     )
       AND ac.status = 'ACTIVE'
       AND NOT EXISTS (
           SELECT 1
             FROM role_capabilities rc
            WHERE rc.tenant_id = control_tenant
              AND rc.role_id = platform_owner_role_id
              AND rc.capability_id = ac.id
       );

    IF missing_capability_count <> 0 THEN
        RAISE EXCEPTION 'PLATFORM_OWNER UAC capability reconciliation failed';
    END IF;

    SELECT COUNT(*) INTO missing_scope_count
      FROM access_capabilities ac
     WHERE ac.code IN (
         'AUTHORIZATION.OVERRIDE.MANAGE',
         'AUTHORIZATION.RELATIONSHIP.MANAGE',
         'AUTHORIZATION.RESYNC',
         'AUTHORIZATION.BREAK_GLASS',
         'AUTHORIZATION.RECOVER',
         'AUTHORIZATION.PLATFORM.MANAGE'
     )
       AND ac.status = 'ACTIVE'
       AND NOT EXISTS (
           SELECT 1
             FROM access_scope_grants asg
            WHERE asg.tenant_id = control_tenant
              AND asg.role_id = platform_owner_role_id
              AND asg.user_id IS NULL
              AND asg.capability_id = ac.id
              AND asg.scope_type = 'TENANT'
              AND asg.status = 'ACTIVE'
       );

    IF missing_scope_count <> 0 THEN
        RAISE EXCEPTION 'PLATFORM_OWNER UAC scope reconciliation failed';
    END IF;
END $$;
