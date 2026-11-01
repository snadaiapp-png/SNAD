-- Forward-only correction for V20261028_2. Never rewrite its published checksum.
-- A separate migrator owns the index in deployed environments: invoker-trigger
-- DML and the pre-auth resolver need explicit runtime grants, not owner bypass.
DO $$
BEGIN
  IF to_regclass('public.auth_token_tenant_index') IS NULL OR
     to_regprocedure('public.sync_auth_token_tenant_index()') IS NULL THEN
    RAISE EXCEPTION 'AUTH_TOKEN_INDEX_PRECONDITION: reconcile V20261028_2 first';
  END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='sanad' AND NOT rolsuper AND NOT rolbypassrls) THEN
    RAISE EXCEPTION 'AUTH_TOKEN_INDEX_PRECONDITION: restricted runtime role required';
  END IF;
END $$;

REVOKE ALL ON TABLE public.auth_token_tenant_index FROM PUBLIC;
REVOKE ALL ON FUNCTION public.sync_auth_token_tenant_index() FROM PUBLIC;
DO $$
DECLARE api_role text;
BEGIN
  FOREACH api_role IN ARRAY ARRAY['anon','authenticated','service_role'] LOOP
    IF EXISTS (SELECT FROM pg_roles WHERE rolname=api_role) THEN
      EXECUTE format('REVOKE ALL ON TABLE public.auth_token_tenant_index FROM %I',api_role);
      EXECUTE format('REVOKE ALL ON FUNCTION public.sync_auth_token_tenant_index() FROM %I',api_role);
    END IF;
  END LOOP;
END $$;

-- SELECT is used before tenant discovery. INSERT/UPDATE/DELETE are required
-- by the existing SECURITY INVOKER trigger, and confer no token-table bypass.
-- No TRUNCATE, REFERENCES, TRIGGER, ownership or role privilege is granted.
REVOKE ALL ON TABLE public.auth_token_tenant_index FROM sanad;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.auth_token_tenant_index TO sanad;

CREATE OR REPLACE FUNCTION public.sync_auth_token_tenant_index()
RETURNS trigger
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = pg_catalog, pg_temp
AS $fn$
BEGIN
  IF TG_TABLE_SCHEMA <> 'public' OR TG_NARGS <> 1 OR
     NOT ((TG_TABLE_NAME='refresh_tokens' AND TG_ARGV[0]='refresh') OR
          (TG_TABLE_NAME='password_reset_tokens' AND TG_ARGV[0]='password_reset')) THEN
    RAISE EXCEPTION 'AUTH_TOKEN_INDEX_TRIGGER_CONTRACT' USING ERRCODE='23514';
  END IF;
  IF TG_OP='INSERT' THEN
    INSERT INTO public.auth_token_tenant_index(kind,token_hash,tenant_id,user_id)
    VALUES(TG_ARGV[0],NEW.token_hash,NEW.tenant_id,NEW.user_id)
    ON CONFLICT(kind,token_hash) DO UPDATE
      SET tenant_id=EXCLUDED.tenant_id,user_id=EXCLUDED.user_id;
    RETURN NEW;
  ELSIF TG_OP='DELETE' THEN
    DELETE FROM public.auth_token_tenant_index
      WHERE kind=TG_ARGV[0] AND token_hash=OLD.token_hash;
    RETURN OLD;
  ELSIF TG_OP='UPDATE' THEN
    -- Rotation creates a new token row. Status/expiry/revocation may change;
    -- its hash and trusted identity must not change or leave orphan pointers.
    IF ROW(NEW.token_hash,NEW.tenant_id,NEW.user_id) IS DISTINCT FROM
       ROW(OLD.token_hash,OLD.tenant_id,OLD.user_id) THEN
      RAISE EXCEPTION 'AUTH_TOKEN_IDENTITY_IMMUTABLE' USING ERRCODE='23514';
    END IF;
    UPDATE public.auth_token_tenant_index
      SET tenant_id=NEW.tenant_id,user_id=NEW.user_id
      WHERE kind=TG_ARGV[0] AND token_hash=NEW.token_hash;
    RETURN NEW;
  END IF;
  RAISE EXCEPTION 'AUTH_TOKEN_INDEX_TRIGGER_OPERATION' USING ERRCODE='23514';
END
$fn$;

DO $$
DECLARE tbl text;
BEGIN
  IF NOT has_table_privilege('sanad','public.auth_token_tenant_index','SELECT') OR
     NOT has_table_privilege('sanad','public.auth_token_tenant_index','INSERT') OR
     NOT has_table_privilege('sanad','public.auth_token_tenant_index','UPDATE') OR
     NOT has_table_privilege('sanad','public.auth_token_tenant_index','DELETE') THEN
    RAISE EXCEPTION 'AUTH_TOKEN_INDEX_VERIFY: runtime ACL incomplete';
  END IF;
  IF NOT EXISTS (SELECT FROM pg_proc WHERE oid='public.sync_auth_token_tenant_index()'::regprocedure
                 AND NOT prosecdef AND proconfig @> ARRAY['search_path=pg_catalog, pg_temp']) THEN
    RAISE EXCEPTION 'AUTH_TOKEN_INDEX_VERIFY: invoker/search_path contract';
  END IF;
  FOREACH tbl IN ARRAY ARRAY['refresh_tokens','password_reset_tokens'] LOOP
    IF NOT EXISTS (SELECT FROM pg_class WHERE oid=to_regclass('public.'||tbl)
                   AND relrowsecurity AND relforcerowsecurity) THEN
      RAISE EXCEPTION 'AUTH_TOKEN_INDEX_VERIFY: token FORCE RLS missing on %',tbl;
    END IF;
  END LOOP;
END $$;
