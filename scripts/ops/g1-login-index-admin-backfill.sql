-- Controlled ADMIN-ONLY historical reconciliation. NOT a Flyway migration.
-- Deployment requires verified backup, approved change window, and independent review.
-- Must be executed transactionally with -v ON_ERROR_STOP=1 and --single-transaction.
-- The caller must explicitly set sanad.g1_backfill_approved=yes in that session.
-- Never grant BYPASSRLS or broad SELECT to the runtime/migrator as a workaround.
DO $admin_gate$
BEGIN
 IF NOT (SELECT rolsuper FROM pg_roles WHERE rolname=current_user) OR
    current_setting('sanad.g1_backfill_approved',true) IS DISTINCT FROM 'yes'
 THEN RAISE EXCEPTION 'G1_ADMIN_BACKFILL_NOT_APPROVED_OR_NOT_SUPERUSER'; END IF;
END $admin_gate$;
INSERT INTO public.login_tenant_index(kind,identifier,tenant_id)
SELECT 'email',u.email,u.tenant_id
FROM public.users u
WHERE NOT EXISTS (
 SELECT 1 FROM public.login_tenant_index i
 WHERE i.kind='email' AND i.identifier=u.email AND i.tenant_id=u.tenant_id
)
ON CONFLICT(kind,identifier,tenant_id) DO NOTHING;

INSERT INTO public.login_tenant_index(kind,identifier,tenant_id)
SELECT 'username',u.username,u.tenant_id
FROM public.users u
WHERE u.username IS NOT NULL AND u.username <> u.email
AND NOT EXISTS (
 SELECT 1 FROM public.login_tenant_index i
 WHERE i.kind='username' AND i.identifier=u.username AND i.tenant_id=u.tenant_id
)
ON CONFLICT(kind,identifier,tenant_id) DO NOTHING;

DO $verify$
DECLARE missing_email bigint; missing_username bigint;
BEGIN
 SELECT count(*) INTO missing_email FROM public.users u
 WHERE NOT EXISTS (SELECT 1 FROM public.login_tenant_index i
   WHERE i.kind='email' AND i.identifier=u.email AND i.tenant_id=u.tenant_id);
 SELECT count(*) INTO missing_username FROM public.users u
 WHERE u.username IS NOT NULL AND u.username<>u.email
 AND NOT EXISTS (SELECT 1 FROM public.login_tenant_index i
   WHERE i.kind='username' AND i.identifier=u.username AND i.tenant_id=u.tenant_id);
 IF missing_email<>0 OR missing_username<>0 THEN
   RAISE EXCEPTION 'G1_LOGIN_INDEX_BACKFILL_INCOMPLETE email=% username=%',missing_email,missing_username;
 END IF;
END $verify$;
