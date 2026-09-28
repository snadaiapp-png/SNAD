-- ============================================================================
-- V20261001_9 — Wave 1 / Task 3: canonical protected-role registry.
--
-- Revision D semantic authority:
--   * The UAC protected set is EXACTLY four codes:
--       PLATFORM_OWNER, PLATFORM_ADMIN, AGENT_SUPER_ADMIN, TENANT_ADMIN.
--   * AGENT_CUSTOM_ADMIN is deliberately NOT protected. Reclassification
--     requires a new owner-approved design decision.
--   * This registry is mutation-invariant metadata only; it does not replace
--     roles, platform_role_metadata, RBAC, or the runtime decision engine.
--
-- Execution overlay:
--   Original Task 3 stamp V20260924_3 is occupied by HRM-G2 on current main.
--   V20261001_9 follows the Task 2 overlay migration V20261001_8.
-- ============================================================================

CREATE TABLE protected_system_roles (
    code               TEXT    NOT NULL,
    description        TEXT    NULL,
    immutability_level TEXT    NOT NULL CHECK (immutability_level IN ('LOCKED', 'MANAGED')),
    min_authority      INTEGER NOT NULL,

    CONSTRAINT pk_protected_system_roles PRIMARY KEY (code),
    CONSTRAINT ck_psr_code CHECK (
        code IN ('PLATFORM_OWNER', 'PLATFORM_ADMIN', 'AGENT_SUPER_ADMIN', 'TENANT_ADMIN')
    )
);

INSERT INTO protected_system_roles (code, description, immutability_level, min_authority)
VALUES
    ('PLATFORM_OWNER', 'Canonical platform owner role', 'LOCKED', 1000),
    ('PLATFORM_ADMIN', 'Canonical platform administrator role', 'LOCKED', 900),
    ('AGENT_SUPER_ADMIN', 'Canonical agent super administrator role', 'MANAGED', 800),
    ('TENANT_ADMIN', 'Canonical tenant administrator role', 'MANAGED', 700);

-- Existing tenants receive a canonical tenant-admin role without replacing or
-- disabling the legacy ADMIN role. Existing ADMIN capabilities are copied when
-- present so the new protected role preserves current tenant administration
-- authority during the compatibility period.
DO $$
DECLARE
    tenant_row RECORD;
    tenant_admin_role_id UUID;
    legacy_admin_role_id UUID;
BEGIN
    FOR tenant_row IN SELECT id FROM tenants ORDER BY id LOOP
        PERFORM set_config('app.tenant_id', tenant_row.id::text, TRUE);

        SELECT id INTO tenant_admin_role_id
          FROM roles
         WHERE tenant_id = tenant_row.id
           AND code = 'TENANT_ADMIN'
         LIMIT 1;

        IF tenant_admin_role_id IS NULL THEN
            tenant_admin_role_id := gen_random_uuid();

            INSERT INTO roles (
                id, tenant_id, code, name, description, status,
                is_system_managed, role_origin, template_key, template_version,
                created_at, updated_at
            ) VALUES (
                tenant_admin_role_id,
                tenant_row.id,
                'TENANT_ADMIN',
                'Tenant Admin',
                'Canonical protected tenant administrator role',
                'ACTIVE',
                TRUE,
                'SNAD_TEMPLATE',
                'TENANT_ADMIN',
                'V20261001_9',
                CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP
            );
        ELSE
            UPDATE roles
               SET is_system_managed = TRUE,
                   role_origin = 'SNAD_TEMPLATE',
                   template_key = 'TENANT_ADMIN',
                   template_version = 'V20261001_9',
                   status = 'ACTIVE',
                   updated_at = CURRENT_TIMESTAMP
             WHERE tenant_id = tenant_row.id
               AND id = tenant_admin_role_id;
        END IF;

        SELECT id INTO legacy_admin_role_id
          FROM roles
         WHERE tenant_id = tenant_row.id
           AND code = 'ADMIN'
           AND status = 'ACTIVE'
         LIMIT 1;

        IF legacy_admin_role_id IS NOT NULL THEN
            INSERT INTO role_capabilities (
                id, tenant_id, role_id, capability_id, created_at
            )
            SELECT gen_random_uuid(),
                   tenant_row.id,
                   tenant_admin_role_id,
                   rc.capability_id,
                   CURRENT_TIMESTAMP
              FROM role_capabilities rc
             WHERE rc.tenant_id = tenant_row.id
               AND rc.role_id = legacy_admin_role_id
               AND NOT EXISTS (
                   SELECT 1
                     FROM role_capabilities existing
                    WHERE existing.tenant_id = tenant_row.id
                      AND existing.role_id = tenant_admin_role_id
                      AND existing.capability_id = rc.capability_id
               );
        END IF;
    END LOOP;
END $$;
