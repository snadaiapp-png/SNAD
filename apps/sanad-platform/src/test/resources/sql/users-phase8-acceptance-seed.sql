-- Users Phase 8 verification/release acceptance fixtures.
-- Disposable CI only. This file extends the existing Users closure seed with:
--   * one mutation-capable tenant admin for authenticated Phase 8 E2E;
--   * one synthetic conforming application registered through the canonical
--     application_iam_contracts registry (never named in Users source);
--   * one tenant role carrying one synthetic capability.
-- Runtime password is supplied by psql variable and never committed.

CREATE EXTENSION IF NOT EXISTS pgcrypto;
SELECT set_config('app.tenant_id', '33333333-3333-4333-8333-333333333331', false);

INSERT INTO users (
    id, tenant_id, email, username, display_name, status, password_hash,
    must_change_password, platform_admin, session_version, created_at, updated_at
) VALUES (
    '88888888-8888-4888-8888-888888888801',
    '33333333-3333-4333-8333-333333333331',
    'phase8-admin@users-acceptance.example',
    'phase8.admin',
    'Users Phase 8 Admin',
    'ACTIVE',
    crypt(:'users_phase8_password', gen_salt('bf', 10)),
    false,
    false,
    0,
    NOW(),
    NOW()
)
ON CONFLICT (id) DO UPDATE
SET password_hash = EXCLUDED.password_hash,
    status = 'ACTIVE',
    must_change_password = false,
    session_version = 0,
    updated_at = NOW();

INSERT INTO organization_memberships (
    id, tenant_id, organization_id, user_id, email, display_name, status, created_at, updated_at
) VALUES (
    '88888888-8888-4888-8888-888888888802',
    '33333333-3333-4333-8333-333333333331',
    '33333333-3333-4333-8333-333333333335',
    '88888888-8888-4888-8888-888888888801',
    'phase8-admin@users-acceptance.example',
    'Users Phase 8 Admin',
    'ACTIVE',
    NOW(),
    NOW()
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO roles (id, tenant_id, code, name, description, status, created_at, updated_at)
VALUES (
    '88888888-8888-4888-8888-888888888803',
    '33333333-3333-4333-8333-333333333331',
    'USERS_PHASE8_ADMIN',
    'Users Phase 8 Admin',
    'Disposable CI-only mutation role for Phase 8 acceptance',
    'ACTIVE',
    NOW(),
    NOW()
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT gen_random_uuid(),
       '33333333-3333-4333-8333-333333333331',
       '88888888-8888-4888-8888-888888888803',
       cap.id,
       NOW()
FROM access_capabilities cap
WHERE cap.code IN (
    'USER.CREATE','USER.READ','USER.WRITE','USER.DELETE',
    'ROLE.READ','CAPABILITY.READ','USER.GRANT_ROLE','USER.REVOKE_ROLE',
    'ORGANIZATION.READ'
)
AND NOT EXISTS (
    SELECT 1 FROM role_capabilities rc
    WHERE rc.tenant_id = '33333333-3333-4333-8333-333333333331'
      AND rc.role_id = '88888888-8888-4888-8888-888888888803'
      AND rc.capability_id = cap.id
);

INSERT INTO user_role_assignments (
    id, tenant_id, user_id, role_id, organization_id, status, created_at, updated_at
) VALUES (
    '88888888-8888-4888-8888-888888888804',
    '33333333-3333-4333-8333-333333333331',
    '88888888-8888-4888-8888-888888888801',
    '88888888-8888-4888-8888-888888888803',
    NULL,
    'ACTIVE',
    NOW(),
    NOW()
)
ON CONFLICT (id) DO UPDATE SET status='ACTIVE', updated_at=NOW();

INSERT INTO access_capabilities (id, code, name, description, status, created_at, updated_at)
VALUES (
    '88888888-8888-4888-8888-888888888805',
    'FUTURELAB.REPORT.READ',
    'FutureLab Report Read',
    'Synthetic Phase 8 capability proving application-agnostic Users onboarding',
    'ACTIVE',
    NOW(),
    NOW()
)
ON CONFLICT (code) DO NOTHING;

INSERT INTO applications (
    id, code, name, localized_name, description, category, status, version,
    display_order, icon_key, provisioning_mode, supported_countries, dependencies,
    created_at, updated_at
) VALUES (
    '88888888-8888-4888-8888-888888888806',
    'FUTURELAB',
    'FutureLab',
    'مختبر المستقبل',
    'Synthetic conforming application for Users Phase 8 verification',
    'MODULE',
    'ACTIVE',
    '1',
    999,
    'futurelab',
    'IMMEDIATE',
    '["GLOBAL"]'::jsonb,
    '[]'::jsonb,
    NOW(),
    NOW()
)
ON CONFLICT (code) DO NOTHING;

INSERT INTO application_iam_contracts (
    application_code, contract_version, status, capability_namespaces,
    supported_scopes, declared_capabilities, role_templates,
    compatibility_metadata, created_at, updated_at
) VALUES (
    'FUTURELAB',
    '1',
    'ACTIVE',
    '["FUTURELAB"]'::jsonb,
    '["ORGANIZATION"]'::jsonb,
    '["FUTURELAB.REPORT.READ"]'::jsonb,
    '[]'::jsonb,
    '{"fixture":"USERS_PHASE8"}'::jsonb,
    NOW(),
    NOW()
)
ON CONFLICT (application_code) DO UPDATE
SET contract_version=EXCLUDED.contract_version,
    status='ACTIVE',
    capability_namespaces=EXCLUDED.capability_namespaces,
    supported_scopes=EXCLUDED.supported_scopes,
    declared_capabilities=EXCLUDED.declared_capabilities,
    compatibility_metadata=EXCLUDED.compatibility_metadata,
    updated_at=NOW();

INSERT INTO roles (id, tenant_id, code, name, description, status, created_at, updated_at)
VALUES (
    '88888888-8888-4888-8888-888888888807',
    '33333333-3333-4333-8333-333333333331',
    'FUTURELAB_REPORT_READER',
    'FutureLab Report Reader',
    'Synthetic discovered-application role for Phase 8',
    'ACTIVE',
    NOW(),
    NOW()
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT
    '88888888-8888-4888-8888-888888888808',
    '33333333-3333-4333-8333-333333333331',
    '88888888-8888-4888-8888-888888888807',
    cap.id,
    NOW()
FROM access_capabilities cap
WHERE cap.code='FUTURELAB.REPORT.READ'
ON CONFLICT DO NOTHING;

DO $$
DECLARE
  admin_cap_count integer;
BEGIN
  SELECT COUNT(DISTINCT cap.code) INTO admin_cap_count
  FROM role_capabilities rc
  JOIN access_capabilities cap ON cap.id=rc.capability_id
  WHERE rc.tenant_id='33333333-3333-4333-8333-333333333331'
    AND rc.role_id='88888888-8888-4888-8888-888888888803'
    AND cap.code IN (
      'USER.CREATE','USER.READ','USER.WRITE','USER.DELETE',
      'ROLE.READ','CAPABILITY.READ','USER.GRANT_ROLE','USER.REVOKE_ROLE',
      'ORGANIZATION.READ'
    );
  IF admin_cap_count <> 9 THEN
    RAISE EXCEPTION 'Phase 8 admin capability count=% expected=9', admin_cap_count;
  END IF;
END $$;

SELECT set_config('app.tenant_id', '', false);
