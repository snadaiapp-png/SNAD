-- ============================================================================
-- V20261124_1 — G1 P0-B (F-DB-07 / RT-01): SIGNED TENANT-CONTEXT BINDING.
--
-- Root-cause closure for doc 18 F-RT-01 (RT-01 CONFIRMED(1), independently
-- re-reproduced on the fresh isolated chain head 20261118.2: a hostile
-- runtime SQL session forges `SET app.tenant_id='<tenant B>'` and reads the
-- foreign-tenant row) — the tenant GUC is application-trusted, not
-- DB-authenticated. 305 of the 313 RLS policies compare row tenant columns
-- against the raw `current_setting('app.tenant_id', true)` value; any
-- session holding runtime credentials can forge it (fail-closed protects
-- only the UNSET case, never the FORGED case).
--
-- REMEDIATION DESIGN (doc 20 P0-2 option 3 — signed session context —
-- hardened; options 1/2 adjudicated in the design record):
--   * The backend derives the tenant from the AUTHENTICATED PRINCIPAL (JWT),
--     then mints mac = HMAC-SHA256(key, tenant || ':' || txid || ':' || pid)
--     and calls app_bind_tenant_ctx(tenant, mac) inside the SAME transaction.
--   * The HMAC signing key lives ONLY in (a) the backend environment
--     (SANAD_TENANT_CTX_HMAC_KEY) and (b) the migrator-owned, zero-grant
--     table platform_tenant_ctx_keys provisioned by the operator. It NEVER
--     appears in the repository, in migration SQL, or in any log.
--   * app_tenant_ctx_text() re-verifies the HMAC against the caller's OWN
--     txid_current() + pg_backend_pid() on every evaluation. A hostile
--     session that merely set_config()s app.tenant_id to a foreign tenant
--     CANNOT produce a matching mac: the mac binds to the caller's current
--     transaction id and backend pid, both of which differ for the hostile
--     session, and computing a fresh mac requires the signing key.
--     Replay of a captured mac (query text is visible only to the session
--     itself under default privileges) fails the txid/pid equality.
--   * THE GUC REMAINS UNTRUSTED — policies must read
--     public.app_tenant_ctx_text() (V20261124_2 rewrites the 305 policies
--     and the 6 GUC-reading function bodies). THIS MIGRATION CHANGES NO
--     POLICY AND NO EXISTING BEHAVIOR: it is deployable ahead of the
--     backend change with zero functional impact (zero-downtime sequencing:
--     V20261124_1 -> operator key provisioning -> backend bind deploy ->
--     ops verification -> V20261124_2 enforcement, guarded by
--     platform_ctx_enforce).
--   * Per-tenant DB roles (doc 20 option 2) remain the strongest future
--     control; they are NOT chosen now because sanad-membership in tenant
--     roles reopens the SET ROLE forgery path and per-tenant credentials
--     require pooler re-architecture. Recorded as future hardening, not a
--     rejected control.
--
-- RESIDUAL TRUST BOUNDARY (documented, accepted): an actor holding BOTH the
-- runtime DB credentials AND the backend signing key controls the backend
-- itself and can bind any tenant; that is backend compromise, outside the
-- tenant-isolation threat model. HMAC comparison is not constant-time;
-- timing side-channels are impractical across SQL round-trips, and bind
-- guesses are bounded by a 256-bit mac space.
--
-- TOPOLOGY: inert until V20261124_2 flips enforcement, so CI fresh chains
-- and local fixture databases carry the same objects. Key provisioning is
-- ALWAYS an operator action from the environment (V20261103_1 precedent:
-- the chain never creates credentials). The bind function FAILS CLOSED
-- (exception) when no ACTIVE key row exists. Supabase-only Data API roles
-- (anon/authenticated/service_role) are addressed role-existence-guarded
-- (V20261020_1 pattern) so the chain replays on stock PostgreSQL.
-- pgcrypto.hmac is resolved to its installation schema (Supabase keeps it
-- in `extensions`; stock keeps it in `public`) and the function bodies are
-- generated schema-qualified; the migration fails closed without it.
--
-- OBJECTS:
--   platform_tenant_ctx_keys  — migrator-owned; zero grants to runtime,
--                               PUBLIC and every Data-API role; PostgREST
--                               cannot expose a table with no SELECT grant.
--   platform_ctx_enforce      — migrator-owned switch consumed by
--                               V20261124_2 (fail-closed guard).
--   app_tenant_ctx_slot()     — one round trip: (txid, pid) for mac minting.
--   app_bind_tenant_ctx(u,t)  — verifies mac (all ACTIVE keys, latest
--                               first), sets app.tenant_id + app.ctx_mac
--                               transaction-local; raises 22000 on failure.
--   app_tenant_ctx_text()     — STABLE validator consumed by policies:
--                               returns the validated tenant text or NULL.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. pgcrypto resolution + object creation + ACL hardening.
-- ----------------------------------------------------------------------------
DO $main$
DECLARE
    v_api_role text;
    v_hmac_schema text;
    v_vec text;
    v_topology text := current_setting('sanad.deployment_topology', true);
    v_separated boolean;
BEGIN
    -- ---- topology preflight (V20261103_2 discipline) -----------------------
    IF v_topology IS NULL OR v_topology = 'migration-fixture' THEN
        v_separated := false;
        RAISE WARNING 'SANAD_TOPOLOGY_FIXTURE: sanad.deployment_topology is "%" - disposable migration-mechanics topology (owner-as-runner): the ctx objects remain owned by the running role and the zero-grant runtime assertions apply to the separated topology only.', coalesce(v_topology, 'unset');
    ELSIF v_topology = 'separated' THEN
        v_separated := true;
    ELSE
        RAISE EXCEPTION 'SANAD_TOPOLOGY_UNKNOWN: sanad.deployment_topology="%" (expected separated|migration-fixture|unset)', coalesce(v_topology, 'unset')
            USING ERRCODE = '22023';
    END IF;

    -- ---- pgcrypto presence + hmac resolution (fail closed) ----------------
    -- Production (Supabase) ships pgcrypto 1.3 preinstalled (evidence
    -- g1b/q_extensions.json); stock PostgreSQL cert clusters install it here
    -- as a TRUSTED extension (the migrator owns the public schema — no
    -- superuser required, same mechanics as the chain's btree_gist in
    -- V20260904_2).
    CREATE EXTENSION IF NOT EXISTS pgcrypto;

    SELECT n.nspname INTO v_hmac_schema
    FROM pg_proc p
    JOIN pg_namespace n ON n.oid = p.pronamespace
    WHERE p.proname = 'hmac'
      AND pg_get_function_identity_arguments(p.oid) = 'bytea, bytea, text'
    LIMIT 1;

    IF v_hmac_schema IS NULL THEN
        RAISE EXCEPTION 'TENANT_CTX_PGPCRYPTO_MISSING: pgcrypto.hmac(bytea,bytea,text) is required and absent'
            USING ERRCODE = '0A000';
    END IF;

    -- Self-verification vector of the resolved implementation.
    EXECUTE format('SELECT encode(%I.hmac(''sanad-ctx-selftest''::bytea, ''sanad-ctx-key''::bytea, ''sha256''), ''hex'')',
                   v_hmac_schema) INTO v_vec;
    IF v_vec IS NULL OR length(v_vec) <> 64 THEN
        RAISE EXCEPTION 'TENANT_CTX_HMAC_SELFTEST_FAILED: resolved %.hmac produced unexpected output', v_hmac_schema
            USING ERRCODE = '0A000';
    END IF;

    -- ---- Keys table + enforcement switch ----------------------------------
    CREATE TABLE IF NOT EXISTS public.platform_tenant_ctx_keys (
        key_id      text PRIMARY KEY,
        secret      bytea NOT NULL CHECK (octet_length(secret) >= 32),
        status      text  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'RETIRED')),
        created_at  timestamptz NOT NULL DEFAULT now(),
        retired_at  timestamptz NULL,
        CONSTRAINT platform_tenant_ctx_keys_retired_state CHECK (
            (status = 'ACTIVE' AND retired_at IS NULL) OR (status = 'RETIRED' AND retired_at IS NOT NULL))
    );

    CREATE TABLE IF NOT EXISTS public.platform_ctx_enforce (
        flag        text PRIMARY KEY CHECK (flag = 'ON'),
        enabled_at  timestamptz NOT NULL DEFAULT now(),
        enabled_by  text NOT NULL
    );

    -- SEPARATED ONLY (V20261103_2 ownership discipline): in the separated
    -- topology Flyway runs AS sanad_migrator, so the ownership assignment is
    -- an owner self-affirmation. In fixture topology the objects stay owned
    -- by the running role (owner-as-runner) — the owner is also the signer,
    -- acceptable on disposable databases holding no real data only.
    IF v_separated THEN
        ALTER TABLE public.platform_tenant_ctx_keys OWNER TO sanad_migrator;
        ALTER TABLE public.platform_ctx_enforce     OWNER TO sanad_migrator;
    END IF;

    -- Zero-grant hardening: default ACLs auto-grant the runtime role on new
    -- objects (finding F-DB-05) and Supabase exposes tables via the Data API
    -- when a SELECT grant exists. Revoke from PUBLIC, the runtime role and
    -- every Data API role that exists in this topology.
    REVOKE ALL ON public.platform_tenant_ctx_keys FROM PUBLIC, sanad;
    REVOKE ALL ON public.platform_ctx_enforce     FROM PUBLIC, sanad;
    FOREACH v_api_role IN ARRAY ARRAY['anon', 'authenticated', 'service_role'] LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = v_api_role) THEN
            EXECUTE format('REVOKE ALL ON public.platform_tenant_ctx_keys FROM %I', v_api_role);
            EXECUTE format('REVOKE ALL ON public.platform_ctx_enforce FROM %I', v_api_role);
        END IF;
    END LOOP;

    -- OWNER RESTORE (G1 F-INT chain-apply defect, fixed 2026-11-25):
    -- REVOKE ALL ... FROM a role that IS the table owner materializes an EMPTY
    -- ACL (relacl = '{}') and on PostgreSQL an empty ACL strips EVEN THE
    -- OWNER's access. On the fixture topology (owner-as-runner) the runner
    -- itself lost access and V20261124_2 could not read the keys table; on the
    -- separated topology the migrator-owned verifiers would lose access at the
    -- first bind. Restore FULL access to each table's OWNER role ONLY — the
    -- zero-grant contract for PUBLIC / sanad / Data-API roles is unchanged
    -- (verified by the D6 zero-grant probes and the catalog inspection).
    EXECUTE format('GRANT ALL ON public.platform_tenant_ctx_keys TO %I',
                   (SELECT pg_get_userbyid(relowner) FROM pg_class
                     WHERE oid = 'public.platform_tenant_ctx_keys'::regclass));
    EXECUTE format('GRANT ALL ON public.platform_ctx_enforce TO %I',
                   (SELECT pg_get_userbyid(relowner) FROM pg_class
                     WHERE oid = 'public.platform_ctx_enforce'::regclass));

    -- ---- Slot helper -------------------------------------------------------
    -- One round trip (txid, pid) for backend mac minting. SECURITY INVOKER:
    -- needs no elevated privileges.
    CREATE OR REPLACE FUNCTION public.app_tenant_ctx_slot()
    RETURNS TABLE (ctx_txid text, ctx_pid integer)
    LANGUAGE sql VOLATILE
    SET search_path = pg_catalog, public
    AS $fn$
        SELECT txid_current()::text, pg_backend_pid()
    $fn$;

    IF v_separated THEN
        ALTER FUNCTION public.app_tenant_ctx_slot() OWNER TO sanad_migrator;
    END IF;

    -- ---- Bind --------------------------------------------------------------
    -- Verify the HMAC; ONLY on success set the transaction-local GUCs.
    -- SECURITY DEFINER (reads the zero-grant keys table). Fails closed.
    EXECUTE format($fn$
CREATE OR REPLACE FUNCTION public.app_bind_tenant_ctx(p_tenant uuid, p_mac text)
RETURNS boolean
LANGUAGE plpgsql VOLATILE SECURITY DEFINER
SET search_path = pg_catalog, public
AS $body$
DECLARE
    v_row record;
    v_expected text;
    v_mac_norm text;
    v_verified boolean := false;
BEGIN
    IF p_tenant IS NULL OR p_mac IS NULL OR btrim(p_mac) = '' THEN
        RAISE EXCEPTION 'TENANT_CTX_BIND_INVALID_PARAMS'
            USING ERRCODE = '22000';
    END IF;
    v_mac_norm := lower(btrim(p_mac));

    FOR v_row IN
        SELECT key_id, secret FROM public.platform_tenant_ctx_keys
        WHERE status = 'ACTIVE'
        ORDER BY created_at DESC
    LOOP
        v_expected := encode(
            %I.hmac(
                (p_tenant::text || ':' || txid_current()::text || ':' || pg_backend_pid()::text)::bytea,
                v_row.secret, 'sha256'), 'hex');
        IF v_expected = v_mac_norm THEN
            v_verified := true;
            EXIT;
        END IF;
    END LOOP;

    IF NOT v_verified THEN
        RAISE EXCEPTION 'TENANT_CTX_BIND_REJECTED: no ACTIVE key validates the supplied mac for the caller txid/pid'
            USING ERRCODE = '22000';
    END IF;

    PERFORM set_config('app.tenant_id', p_tenant::text, true);
    PERFORM set_config('app.ctx_mac', v_mac_norm, true);
    RETURN true;
END;
$body$;
$fn$, v_hmac_schema);

    -- ---- Validator ---------------------------------------------------------
    -- The single expression policies will read. Returns the validated tenant
    -- as text, or NULL. STABLE; policies consume it via an uncorrelated
    -- scalar subquery so PostgreSQL evaluates it once per statement
    -- (InitPlan) instead of per row. Re-verifies the HMAC with the caller's
    -- CURRENT txid/pid — a raw GUC forgery cannot match.
    EXECUTE format($fn$
CREATE OR REPLACE FUNCTION public.app_tenant_ctx_text()
RETURNS text
LANGUAGE plpgsql STABLE SECURITY DEFINER
SET search_path = pg_catalog, public
AS $body$
DECLARE
    v_tenant text;
    v_mac text;
    v_row record;
    v_expected text;
BEGIN
    v_tenant := current_setting('app.tenant_id', true);
    IF v_tenant IS NULL OR v_tenant = '' THEN
        RETURN NULL;
    END IF;
    v_mac := current_setting('app.ctx_mac', true);
    IF v_mac IS NULL OR v_mac = '' THEN
        RETURN NULL;
    END IF;

    FOR v_row IN
        SELECT key_id, secret FROM public.platform_tenant_ctx_keys
        WHERE status = 'ACTIVE'
        ORDER BY created_at DESC
    LOOP
        v_expected := encode(
            %I.hmac(
                (v_tenant || ':' || txid_current()::text || ':' || pg_backend_pid()::text)::bytea,
                v_row.secret, 'sha256'), 'hex');
        IF v_expected = lower(v_mac) THEN
            RETURN v_tenant;
        END IF;
    END LOOP;

    RETURN NULL;
END;
$body$;
$fn$, v_hmac_schema);

    -- ---- Function privileges ----------------------------------------------
    -- Default-deny (V20261020_1 pattern) for the BIND PROTOCOL: app_tenant_ctx_slot()
    -- and app_bind_tenant_ctx() execute ONLY for the runtime identity; PUBLIC and
    -- every existing Data API role receive nothing.
    --
    -- VALIDATOR EVALUATION CONTRACT (G1 F-INT chain-apply defect, fixed 2026-11-25):
    -- V20261124_2 rewrites the TO-PUBLIC tenant policies to read
    -- public.app_tenant_ctx_text(). PostgreSQL evaluates policy expressions with
    -- the QUERYING role's privileges, so EVERY role that touches a protected
    -- table needs EXECUTE on the validator — otherwise non-runtime roles (probe
    -- roles, Data API roles, maintenance identities) receive a policy-evaluation
    -- ERROR instead of the designed deterministic zero rows. The validator is
    -- safe by construction: it is SECURITY DEFINER, it returns ONLY the caller's
    -- OWN HMAC-verified tenant (or NULL), and producing a matching mac requires
    -- the signing key — PUBLIC EXECUTE leaks nothing and enables no forgery.
    -- slot()/bind() remain default-deny.
    REVOKE ALL ON FUNCTION public.app_tenant_ctx_slot()           FROM PUBLIC, sanad;
    REVOKE ALL ON FUNCTION public.app_bind_tenant_ctx(uuid, text) FROM PUBLIC, sanad;
    REVOKE ALL ON FUNCTION public.app_tenant_ctx_text()           FROM PUBLIC, sanad;
    FOREACH v_api_role IN ARRAY ARRAY['anon', 'authenticated', 'service_role'] LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = v_api_role) THEN
            EXECUTE format('REVOKE ALL ON FUNCTION public.app_tenant_ctx_slot() FROM %I', v_api_role);
            EXECUTE format('REVOKE ALL ON FUNCTION public.app_bind_tenant_ctx(uuid, text) FROM %I', v_api_role);
            EXECUTE format('REVOKE ALL ON FUNCTION public.app_tenant_ctx_text() FROM %I', v_api_role);
        END IF;
    END LOOP;
    GRANT EXECUTE ON FUNCTION public.app_tenant_ctx_slot()           TO sanad;
    GRANT EXECUTE ON FUNCTION public.app_bind_tenant_ctx(uuid, text) TO sanad;
    GRANT EXECUTE ON FUNCTION public.app_tenant_ctx_text()           TO sanad;
    GRANT EXECUTE ON FUNCTION public.app_tenant_ctx_text()           TO PUBLIC;

    -- SEPARATED ONLY: migrator ownership of the SECURITY DEFINER validators
    -- (fixture topology keeps owner-as-runner; see the table block above).
    IF v_separated THEN
        ALTER FUNCTION public.app_bind_tenant_ctx(uuid, text) OWNER TO sanad_migrator;
        ALTER FUNCTION public.app_tenant_ctx_text()           OWNER TO sanad_migrator;
    END IF;
END $main$
