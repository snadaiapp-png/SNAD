-- Users Module closure seed extension.
-- Run only after Flyway + g2-acceptance-seed.sql on a disposable CI database.
-- `users_e2e_password` is supplied at runtime by psql -v and is never committed.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Tenant B fixture: extend the existing G2_HR role with read-only Users/Access
-- capabilities. Deliberately DO NOT grant ROLE.MANAGE/CAPABILITY.MANAGE or any
-- user mutation capability; the browser contract proves a direct mutation is 403.
BEGIN;
DO $users_tenant_b$
DECLARE
    v_tenant uuid := '33333333-3333-4333-8333-333333333331'::uuid;
    v_mac text;
BEGIN
    v_mac := encode(hmac(
        convert_to(v_tenant::text || ':' || txid_current()::text || ':' || pg_backend_pid()::text, 'UTF8'),
        decode(repeat('0123456789abcdef', 4), 'hex'), 'sha256'), 'hex');
    IF NOT public.app_bind_tenant_ctx(v_tenant, v_mac)
       OR public.app_tenant_ctx_text() IS DISTINCT FROM v_tenant::text THEN
        RAISE EXCEPTION 'USERS_CLOSURE_SIGNED_BIND_REFUSED';
    END IF;
END $users_tenant_b$;

INSERT INTO role_capabilities (id, tenant_id, role_id, capability_id, created_at)
SELECT
    gen_random_uuid(),
    '33333333-3333-4333-8333-333333333331',
    '33333333-3333-4333-8333-333333333373',
    cap.id,
    NOW()
FROM access_capabilities cap
WHERE cap.code IN ('USER.READ', 'MEMBERSHIP.READ', 'ROLE.READ', 'CAPABILITY.READ')
  AND NOT EXISTS (
      SELECT 1
      FROM role_capabilities rc
      WHERE rc.tenant_id = '33333333-3333-4333-8333-333333333331'
        AND rc.role_id = '33333333-3333-4333-8333-333333333373'
        AND rc.capability_id = cap.id
  );

DO $verify_tenant_b$
DECLARE read_cap_count integer;
BEGIN
    SELECT COUNT(*) INTO read_cap_count
    FROM role_capabilities rc
    JOIN access_capabilities cap ON cap.id = rc.capability_id
    WHERE rc.tenant_id = '33333333-3333-4333-8333-333333333331'
      AND rc.role_id = '33333333-3333-4333-8333-333333333373'
      AND cap.code IN ('USER.READ', 'MEMBERSHIP.READ', 'ROLE.READ', 'CAPABILITY.READ');
    IF read_cap_count <> 4 THEN
        RAISE EXCEPTION 'Users closure read capability count=% expected=4', read_cap_count;
    END IF;
END $verify_tenant_b$;
COMMIT;

-- Canonical control-plane owner is provisioned by Flyway migrations. Rotate only
-- the disposable CI credential to the same per-run ephemeral password used by
-- the Tenant B fixture; production data is never touched.
BEGIN;
DO $users_control_plane$
DECLARE
    v_tenant uuid := '00000000-0000-0000-0000-000000000001'::uuid;
    v_mac text;
BEGIN
    v_mac := encode(hmac(
        convert_to(v_tenant::text || ':' || txid_current()::text || ':' || pg_backend_pid()::text, 'UTF8'),
        decode(repeat('0123456789abcdef', 4), 'hex'), 'sha256'), 'hex');
    IF NOT public.app_bind_tenant_ctx(v_tenant, v_mac)
       OR public.app_tenant_ctx_text() IS DISTINCT FROM v_tenant::text THEN
        RAISE EXCEPTION 'USERS_CLOSURE_SIGNED_BIND_REFUSED';
    END IF;
END $users_control_plane$;
UPDATE users
SET password_hash = crypt(:'users_e2e_password', gen_salt('bf', 10)),
    must_change_password = false,
    status = 'ACTIVE',
    session_version = 0,
    updated_at = NOW()
WHERE id = '00000000-0000-0000-0000-000000000010'
  AND tenant_id = '00000000-0000-0000-0000-000000000001'
  AND lower(email) = 'snad.ai.app@gmail.com';

DO $verify_control_plane$
DECLARE owner_count integer;
BEGIN
    SELECT COUNT(*) INTO owner_count
    FROM users
    WHERE id = '00000000-0000-0000-0000-000000000010'
      AND tenant_id = '00000000-0000-0000-0000-000000000001'
      AND lower(email) = 'snad.ai.app@gmail.com'
      AND status = 'ACTIVE'
      AND password_hash IS NOT NULL;
    IF owner_count <> 1 THEN
        RAISE EXCEPTION 'Canonical control-plane owner is not provisioned exactly once';
    END IF;
END $verify_control_plane$;
COMMIT;
