-- ============================================================================
-- V20261028_2 — D6 Batch 1: pre-auth tenant discovery for auth token tables
--               + fail-closed FORCE RLS on refresh_tokens / password_reset_tokens.
--
-- Mandate:  SANAD-SEC-G1-D6-CLOSURE (D6 — §5.2 priority: identity/auth plane)
-- Base:     main@71e4371ffdbd91f7bf26f4c445ccc72ce4f362be
--
-- PROBLEM (D6):
--   refresh_tokens and password_reset_tokens carry tenant_id but had
--   relrowsecurity=false: any session without a tenant context (or any
--   future cross-tenant query path) could read or mutate token rows of
--   every tenant. Token theft = identity compromise, so this is the P0
--   slice of the D6 gap inventory (97 tenant_id tables without RLS,
--   catalog evidence 2026-10-28).
--
-- WHY A DEDICATED PRE-AUTH INDEX (mirrors V20261011_4 login_tenant_index):
--   The refresh and password-reset flows look tokens up by hash ONLY,
--   before any tenant is known (AuthService#refresh L373,
--   AuthService#completePasswordReset L659,
--   PasswordRecoveryNotificationCoordinator L62/L88). Enabling fail-closed
--   RLS directly would make every refresh/reset fail (policy returns no
--   rows with an unset GUC) — breaking authentication itself. The fix
--   order mandated by SANAD-SEC-G1-D6-CLOSURE §5.3 is: repair access
--   paths FIRST, then enforce RLS. This migration therefore:
--     1. creates auth_token_tenant_index — a minimal pre-auth discovery
--        table mapping (kind, token_hash) -> (tenant_id, user_id). It
--        holds NO token material, NO user PII beyond the two UUIDs the
--        caller can already derive from a possessed token, and is NOT
--        subject to RLS by documented design (same contract as
--        login_tenant_index: possession of the hash == possession of the
--        bearer secret; the hash itself is not guessable);
--     2. keeps it in sync with SECURITY INVOKER triggers (no
--        SECURITY DEFINER, no role creation, no BYPASSRLS — the trigger
--        only mirrors NEW/OLD and writes the non-RLS index);
--     3. backfills existing rows while the token tables are still
--        un-RLS'd (fresh installs have empty tables — no-op);
--     4. THEN enables + forces fail-closed tenant isolation RLS on both
--        token tables using the exact policy language of V20261021_1.
--
-- APP CONTRACT (implemented in the same change set):
--   AuthTokenTenantScopeResolver reads ONLY auth_token_tenant_index
--   (pre-context), then the caller applies TenantRlsTransactionContext /
--   applyTrustedUserScope(tenant) BEFORE the scoped
--   findByTokenHashForUpdate lookup. Tenant identity is derived from a
--   DB-verified hash mapping — never from client-supplied parameters.
--
-- Validation guards fail closed (RAISE EXCEPTION) so Flyway aborts.
-- Forward-only: no historical migration is modified; no data outside the
-- new index is written (backfill is additive and derived).
-- ============================================================================

-- ---------------------------------------------------------------------------
-- Preconditions
-- ---------------------------------------------------------------------------
DO $$
BEGIN
  IF to_regclass('public.refresh_tokens') IS NULL THEN
    RAISE EXCEPTION 'D6_AUTH_TOKEN_RLS_PRECONDITION: public.refresh_tokens missing';
  END IF;
  IF to_regclass('public.password_reset_tokens') IS NULL THEN
    RAISE EXCEPTION 'D6_AUTH_TOKEN_RLS_PRECONDITION: public.password_reset_tokens missing';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='refresh_tokens'
      AND column_name='token_hash' AND column_name IS NOT NULL
  ) THEN
    RAISE EXCEPTION 'D6_AUTH_TOKEN_RLS_PRECONDITION: refresh_tokens.token_hash missing';
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='password_reset_tokens'
      AND column_name='token_hash'
  ) THEN
    RAISE EXCEPTION 'D6_AUTH_TOKEN_RLS_PRECONDITION: password_reset_tokens.token_hash missing';
  END IF;
  -- Runtime role must exist and stay least-privileged (same gate as V20261011_3).
  IF NOT EXISTS (
    SELECT 1 FROM pg_roles WHERE rolname = 'sanad'
      AND NOT rolsuper AND NOT rolbypassrls
  ) THEN
    RAISE EXCEPTION 'D6_AUTH_TOKEN_RLS_RUNTIME_ROLE_UNAVAILABLE: provision restricted sanad role before token-table RLS';
  END IF;
END $$;

-- ---------------------------------------------------------------------------
-- 1. Pre-auth discovery table (NO RLS by documented design — see header)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.auth_token_tenant_index (
    kind       text        NOT NULL CHECK (kind IN ('refresh', 'password_reset')),
    token_hash text        NOT NULL,
    tenant_id  uuid        NOT NULL,
    user_id    uuid        NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (kind, token_hash)
);

CREATE INDEX IF NOT EXISTS idx_auth_token_tenant_index_hash
    ON public.auth_token_tenant_index (token_hash);

COMMENT ON TABLE public.auth_token_tenant_index IS
'G1/D6 pre-auth tenant discovery for refresh/password-reset token flows. '
'Maps (kind, token_hash) -> (tenant_id, user_id). Intentionally NOT under RLS '
'(same contract as login_tenant_index, V20261011_4): callers possess the raw '
'token, hence its hash; the table stores no token material and no PII beyond '
'two UUIDs. Application must still apply the tenant GUC before touching '
'refresh_tokens/password_reset_tokens (fail-closed FORCE RLS).';

-- ---------------------------------------------------------------------------
-- 2. Sync trigger (SECURITY INVOKER — no definer, no new roles, no bypass)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.sync_auth_token_tenant_index()
RETURNS trigger
LANGUAGE plpgsql
SECURITY INVOKER
AS $fn$
BEGIN
    IF TG_OP = 'INSERT' THEN
        INSERT INTO public.auth_token_tenant_index (kind, token_hash, tenant_id, user_id)
        VALUES (TG_ARGV[0], NEW.token_hash, NEW.tenant_id, NEW.user_id)
        ON CONFLICT (kind, token_hash) DO UPDATE
            SET tenant_id = EXCLUDED.tenant_id,
                user_id   = EXCLUDED.user_id;
        RETURN NEW;
    ELSIF TG_OP = 'DELETE' THEN
        DELETE FROM public.auth_token_tenant_index
        WHERE kind = TG_ARGV[0] AND token_hash = OLD.token_hash;
        RETURN OLD;
    ELSE
        -- UPDATE (status transitions). token_hash is immutable upstream;
        -- mirror the row anyway so the index can never drift.
        UPDATE public.auth_token_tenant_index
        SET tenant_id = NEW.tenant_id, user_id = NEW.user_id
        WHERE kind = TG_ARGV[0] AND token_hash = NEW.token_hash;
        RETURN NEW;
    END IF;
END
$fn$;

REVOKE ALL ON FUNCTION public.sync_auth_token_tenant_index() FROM PUBLIC;

DROP TRIGGER IF EXISTS refresh_tokens_tenant_index_sync ON public.refresh_tokens;
CREATE TRIGGER refresh_tokens_tenant_index_sync
AFTER INSERT OR UPDATE OR DELETE ON public.refresh_tokens
FOR EACH ROW EXECUTE FUNCTION public.sync_auth_token_tenant_index('refresh');

DROP TRIGGER IF EXISTS password_reset_tokens_tenant_index_sync ON public.password_reset_tokens;
CREATE TRIGGER password_reset_tokens_tenant_index_sync
AFTER INSERT OR UPDATE OR DELETE ON public.password_reset_tokens
FOR EACH ROW EXECUTE FUNCTION public.sync_auth_token_tenant_index('password_reset');

-- ---------------------------------------------------------------------------
-- 3. Backfill existing token rows (runs BEFORE RLS is enabled — additive)
-- ---------------------------------------------------------------------------
INSERT INTO public.auth_token_tenant_index (kind, token_hash, tenant_id, user_id)
SELECT 'refresh', t.token_hash, t.tenant_id, t.user_id
FROM public.refresh_tokens t
ON CONFLICT (kind, token_hash) DO NOTHING;

INSERT INTO public.auth_token_tenant_index (kind, token_hash, tenant_id, user_id)
SELECT 'password_reset', t.token_hash, t.tenant_id, t.user_id
FROM public.password_reset_tokens t
ON CONFLICT (kind, token_hash) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 4. Fail-closed FORCE RLS on both token tables (V20261021_1 policy language)
-- ---------------------------------------------------------------------------
ALTER TABLE public.refresh_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.refresh_tokens FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON public.refresh_tokens;
CREATE POLICY tenant_isolation ON public.refresh_tokens FOR ALL
USING (
    NULLIF(current_setting('app.tenant_id', true), '') IS NOT NULL
    AND tenant_id::text = NULLIF(current_setting('app.tenant_id', true), '')
)
WITH CHECK (
    NULLIF(current_setting('app.tenant_id', true), '') IS NOT NULL
    AND tenant_id::text = NULLIF(current_setting('app.tenant_id', true), '')
);

ALTER TABLE public.password_reset_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.password_reset_tokens FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON public.password_reset_tokens;
CREATE POLICY tenant_isolation ON public.password_reset_tokens FOR ALL
USING (
    NULLIF(current_setting('app.tenant_id', true), '') IS NOT NULL
    AND tenant_id::text = NULLIF(current_setting('app.tenant_id', true), '')
)
WITH CHECK (
    NULLIF(current_setting('app.tenant_id', true), '') IS NOT NULL
    AND tenant_id::text = NULLIF(current_setting('app.tenant_id', true), '')
);

-- ---------------------------------------------------------------------------
-- 5. Exit guard: RLS flags and policies must be in place or the run fails
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    tbl text;
BEGIN
    FOREACH tbl IN ARRAY ARRAY['refresh_tokens', 'password_reset_tokens']
    LOOP
        IF NOT EXISTS (
            SELECT 1 FROM pg_class c
            JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE n.nspname = 'public' AND c.relname = tbl
              AND c.relrowsecurity AND c.relforcerowsecurity
        ) THEN
            RAISE EXCEPTION 'D6_AUTH_TOKEN_RLS_VERIFY: ENABLE+FORCE RLS missing on %', tbl;
        END IF;
        IF NOT EXISTS (
            SELECT 1 FROM pg_policies
            WHERE schemaname = 'public' AND tablename = tbl
              AND policyname = 'tenant_isolation'
        ) THEN
            RAISE EXCEPTION 'D6_AUTH_TOKEN_RLS_VERIFY: tenant_isolation policy missing on %', tbl;
        END IF;
    END LOOP;
END $$;
