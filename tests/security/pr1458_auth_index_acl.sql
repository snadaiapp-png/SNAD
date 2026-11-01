-- Disposable CI database only. Catches missing runtime grants hidden by owner tests.
\set ON_ERROR_STOP on
CREATE ROLE sanad LOGIN NOSUPERUSER NOBYPASSRLS NOCREATEROLE NOCREATEDB;
CREATE ROLE anon NOLOGIN;
CREATE ROLE authenticated NOLOGIN;
CREATE TABLE public.refresh_tokens (
  id uuid PRIMARY KEY, token_hash text NOT NULL UNIQUE,
  tenant_id uuid NOT NULL, user_id uuid NOT NULL, status text NOT NULL
);
CREATE TABLE public.password_reset_tokens (LIKE public.refresh_tokens INCLUDING ALL);
GRANT USAGE ON SCHEMA public TO sanad;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.refresh_tokens, public.password_reset_tokens TO sanad;
\ir ../../apps/sanad-platform/src/main/resources/db/migration/V20261028_2__d6_auth_token_tenant_index_and_rls.sql
-- Apply only later auth-index corrective migrations, when present. The harness
-- supplies this path; the original historical migration above stays immutable.
\if :apply_correction
\ir ../../apps/sanad-platform/src/main/resources/db/migration/V20261101_1__auth_token_index_acl_and_identity.sql
\endif
SET ROLE sanad;
DO $$ BEGIN
  IF EXISTS (SELECT FROM pg_roles WHERE rolname=current_user AND (rolsuper OR rolbypassrls)) THEN
    RAISE EXCEPTION 'runtime role bypasses RLS';
  END IF;
  IF (SELECT pg_get_userbyid(relowner) FROM pg_class WHERE oid='public.auth_token_tenant_index'::regclass)=current_user THEN
    RAISE EXCEPTION 'test requires a distinct migration owner';
  END IF;
END $$;
-- This must work without tenant GUC: the resolver's pre-auth contract.
SELECT count(*) FROM public.auth_token_tenant_index;
BEGIN;
SELECT set_config('app.tenant_id','00000000-0000-0000-0000-000000000001',true);
INSERT INTO public.refresh_tokens VALUES
 ('10000000-0000-0000-0000-000000000001','refresh-A','00000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000001','ACTIVE');
INSERT INTO public.password_reset_tokens VALUES
 ('10000000-0000-0000-0000-000000000002','reset-A','00000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000001','ACTIVE');
DO $$ BEGIN
  IF (SELECT count(*) FROM public.auth_token_tenant_index) <> 2 THEN RAISE EXCEPTION 'trigger insert sync failed'; END IF;
END $$;
UPDATE public.refresh_tokens SET status='REVOKED';
UPDATE public.password_reset_tokens SET status='USED';
DO $$ BEGIN
  BEGIN
    UPDATE public.refresh_tokens SET token_hash='changed';
    RAISE EXCEPTION 'immutable token hash update accepted';
  EXCEPTION WHEN check_violation THEN NULL; END;
  BEGIN
    UPDATE public.password_reset_tokens SET user_id='20000000-0000-0000-0000-000000000002';
    RAISE EXCEPTION 'immutable token identity update accepted';
  EXCEPTION WHEN check_violation THEN NULL; END;
END $$;
COMMIT;
BEGIN;
SELECT set_config('app.tenant_id','00000000-0000-0000-0000-000000000002',true);
DO $$ BEGIN
  IF EXISTS (SELECT FROM public.refresh_tokens) OR EXISTS (SELECT FROM public.password_reset_tokens) THEN RAISE EXCEPTION 'cross-tenant read'; END IF;
  UPDATE public.refresh_tokens SET status='ACTIVE';
  IF FOUND THEN RAISE EXCEPTION 'cross-tenant update'; END IF;
  DELETE FROM public.password_reset_tokens;
  IF FOUND THEN RAISE EXCEPTION 'cross-tenant delete'; END IF;
END $$;
COMMIT;
DO $$ BEGIN
  IF EXISTS (SELECT FROM public.refresh_tokens) OR EXISTS (SELECT FROM public.password_reset_tokens) THEN RAISE EXCEPTION 'missing GUC read'; END IF;
END $$;
BEGIN;
SELECT set_config('app.tenant_id','00000000-0000-0000-0000-000000000001',true);
DELETE FROM public.refresh_tokens;
DELETE FROM public.password_reset_tokens;
DO $$ BEGIN
  IF EXISTS (SELECT FROM public.auth_token_tenant_index) THEN RAISE EXCEPTION 'trigger delete sync failed'; END IF;
END $$;
COMMIT;
RESET ROLE;
DO $$ BEGIN
  IF has_table_privilege('anon','public.auth_token_tenant_index','SELECT') OR
     has_table_privilege('authenticated','public.auth_token_tenant_index','SELECT') THEN
    RAISE EXCEPTION 'Data API role can read pre-auth index';
  END IF;
  IF (SELECT prosecdef FROM pg_proc WHERE oid='public.sync_auth_token_tenant_index()'::regprocedure) THEN
    RAISE EXCEPTION 'sync trigger must remain SECURITY INVOKER';
  END IF;
END $$;
