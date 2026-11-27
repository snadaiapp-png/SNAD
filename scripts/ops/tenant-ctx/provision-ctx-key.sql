-- ============================================================================
-- provision-ctx-key.sql — Operator provisioning of the signed tenant-context
-- HMAC key into platform_tenant_ctx_keys (V20261124_1 deployment sequence,
-- step 2 — G1 F-DB-07 / F-INT-04; referenced by the V20261124_2 header).
--
-- RUN AS: the migrator/operator identity (sanad_migrator on the separated
-- topology) — the runtime role sanad has ZERO grants on this table by design.
--
-- KEY SOURCE: the operator environment variable SANAD_TENANT_CTX_HMAC_KEY,
-- read with psql's \getenv (psql >= 15). The key NEVER appears in this file,
-- in migration SQL, or in server statement logs — every server round trip
-- that touches key material uses \bind, so the value travels as a protocol
-- parameter, not as inlined query text.
--
-- RECOMMENDED GENERATION (operator change window):
--     export SANAD_TENANT_CTX_HMAC_KEY="$(openssl rand -hex 32)"
--
-- USAGE:
--     psql "$SANAD_MIGRATOR_URL" -f scripts/ops/tenant-ctx/provision-ctx-key.sql
--
-- BEHAVIOR (fail-closed at every step):
--   1. Aborts when the variable is unset/blank.
--   2. Validates >= 64 hex chars (32 bytes, matching
--      platform_tenant_ctx_keys.secret CHECK and TenantRlsSecurityConfig) —
--      rejects anything else.
--   3. Rejects the documented fixture key UNCONDITIONALLY (F-INT-05).
--   4. Retires prior ACTIVE keys (rotation: the verifier accepts any ACTIVE
--      key, the backend signs with the environment key — keep them in sync
--      within the same change window, otherwise binds fail closed).
--   5. Inserts the new key row key_id = 'ctx-hmac-<yyyymmddhhmi>' (idempotent:
--      an identical ACTIVE key is not duplicated).
--   6. Prints a rotation summary WITHOUT any key material.
-- ============================================================================

\set ON_ERROR_STOP on
\getenv ctx_key SANAD_TENANT_CTX_HMAC_KEY

\if :{?ctx_key}
\else
\echo 'TENANT_CTX_KEY_UNSET: SANAD_TENANT_CTX_HMAC_KEY is not set in the operator environment — provisioning refused (fail-closed).'
\quit
\endif

-- Presence precheck: the binding objects must exist (V20261124_1 applied).
SELECT CASE
         WHEN to_regclass('public.platform_tenant_ctx_keys') IS NULL THEN
           'TENANT_CTX_KEYS_TABLE_MISSING: apply V20261124_1 first.'
         ELSE 'KEYS_TABLE_PRESENT'
       END AS precheck
\gset
\if :precheck = 'KEYS_TABLE_PRESENT'
\else
\echo :precheck
\quit
\endif

-- Key validation as protocol parameters (never inlined into query text).
SELECT length($1) >= 64
   AND $1 ~ '^[0-9a-fA-F]{64,}$'
   AND lower($1) <> '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef'
   AS key_ok
\bind :'ctx_key'
\gset

\if :key_ok
\else
\echo 'TENANT_CTX_KEY_INVALID: SANAD_TENANT_CTX_HMAC_KEY must be hex-encoded, at least 64 hex chars (32 bytes), and must not be the documented fixture key — provisioning refused.'
\quit
\endif

-- Rotation: retire prior ACTIVE keys.
UPDATE public.platform_tenant_ctx_keys
   SET status = 'RETIRED', retired_at = now()
 WHERE status = 'ACTIVE';

-- Insert the new key as a bound parameter (idempotent on identical key).
INSERT INTO public.platform_tenant_ctx_keys (key_id, secret, status)
SELECT 'ctx-hmac-' || to_char(now(), 'YYYYMMDDHH24MI'), decode($1, 'hex'), 'ACTIVE'
 WHERE NOT EXISTS (SELECT 1 FROM public.platform_tenant_ctx_keys
                    WHERE secret = decode($1, 'hex') AND status = 'ACTIVE')
\bind :'ctx_key'

-- Rotation summary — key material is never printed.
SELECT k.key_id,
       k.status,
       k.created_at,
       octet_length(k.secret) AS secret_bytes
  FROM public.platform_tenant_ctx_keys k
 ORDER BY k.created_at DESC
 LIMIT 5;

\echo 'TENANT_CTX_KEY_PROVISIONED: ensure the backend environment carries the SAME key (SANAD_TENANT_CTX_HMAC_KEY) before the enforcement flag/step — binds fail closed on mismatch.'
