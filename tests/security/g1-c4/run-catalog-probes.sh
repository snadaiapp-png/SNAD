#!/usr/bin/env bash
# SNAD G1 C4 — read-only catalog assertions + denied CREATE probe on
# disposable separated-topology PG17 only. No production access.
set -euo pipefail
export LC_ALL=C.UTF-8
: "${C4_ISOLATED_CLUSTER:?C4_ISOLATED_CLUSTER=1 required}"
[[ "$C4_ISOLATED_CLUSTER" == "1" ]] || { echo "::error::Not an isolated certification cluster"; exit 1; }
DB="${C4_DB:-separation_cert}"
[[ "$DB" == "separation_cert" ]] || { echo "::error::Non-certification DB rejected"; exit 1; }
ART="${C4_ARTIFACTS:-/tmp/c4-catalog-artifacts}"
umask 077
[[ ! -L "$ART" ]] || { echo "::error::Artifact path is symlink"; exit 1; }
mkdir -p "$ART"
psql_cert() { sudo -u postgres psql -X -v ON_ERROR_STOP=1 -v VERBOSITY=verbose -d "$DB" "$@"; }
# Never run catalog probes on a previously populated non-disposable database.
other_dbs="$(sudo -u postgres psql -X -A -t -v ON_ERROR_STOP=1 -d postgres -c "SELECT count(*) FROM pg_database WHERE datistemplate=false AND datname NOT IN ('postgres','separation_cert')")"
[[ "$other_dbs" == "0" ]] || { echo "::error::Other user databases found"; exit 1; }
test "$(psql_cert -A -t -c "SELECT current_setting('server_version_num')::int/10000")" = "17" || { echo "::error::PostgreSQL 17 required"; exit 1; }
test "$(psql_cert -A -t -c "SELECT pg_get_userbyid(datdba) FROM pg_database WHERE datname=current_database()")" = "sanad_migrator" || { echo "::error::Certification database is not migrator-owned"; exit 1; }
test "$(psql_cert -A -t -c "SELECT EXISTS (SELECT 1 FROM public.flyway_schema_history WHERE version='20261111.1' AND success)")" = "t" || { echo "::error::Flyway chain head not found"; exit 1; }

# Catalog inventory: no secrets or tenant row contents. Keep full output as evidence.
psql_cert -P pager=off -f - >"$ART/catalog.txt" <<'SQL'
SELECT current_database() AS database, current_setting('server_version') AS postgres, current_user;
SELECT rolname,rolcanlogin,rolsuper,rolbypassrls,rolcreatedb,rolcreaterole,rolinherit
 FROM pg_roles WHERE rolname IN ('sanad','sanad_migrator','sanad_callback_replay_cleanup') ORDER BY 1;
SELECT pg_has_role('sanad','sanad_migrator','MEMBER') AS runtime_migrator_membership;
SELECT n.nspname,pg_get_userbyid(n.nspowner) AS owner,
       has_schema_privilege('sanad',n.oid,'CREATE') AS runtime_can_create,
       has_schema_privilege('sanad_migrator',n.oid,'CREATE') AS migrator_can_create,
       n.nspacl
 FROM pg_namespace n WHERE n.nspname='public';
SELECT p.oid::regprocedure AS routine,pg_get_userbyid(p.proowner) AS owner,p.proconfig,p.proacl,
       p.prolang::regtype::text AS language_oid
 FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
 WHERE n.nspname='public' AND p.prosecdef ORDER BY 1;
SELECT p.oid::regprocedure AS security_definer_routine
 FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
 WHERE n.nspname='public' AND p.prosecdef
   AND p.proconfig::text LIKE '%search_path=public%'
 ORDER BY 1;
SELECT p.oid::regprocedure AS routine
 FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
 WHERE n.nspname='public' AND p.prosecdef AND
       EXISTS (SELECT 1 FROM aclexplode(coalesce(p.proacl,acldefault('f',p.proowner))) a
               WHERE a.grantee=0 AND a.privilege_type='EXECUTE')
 ORDER BY 1;
SELECT c.oid::regclass AS relation,c.relkind,pg_get_userbyid(c.relowner) AS owner,
       c.relrowsecurity,c.relforcerowsecurity,
       has_table_privilege('sanad',c.oid,'TRUNCATE') AS runtime_truncate
 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
 WHERE n.nspname='public' AND c.relkind IN ('r','p','v','m','S') ORDER BY 1;
SELECT defaclrole::regrole,defaclnamespace::regnamespace,defaclobjtype,defaclacl FROM pg_default_acl ORDER BY 1,2,3;
SELECT version,description,success,installed_rank FROM public.flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;
SQL

# Fail-closed source-driven contract: note that these checks do not prove every
# SECURITY DEFINER function safe or every fixture-limited case covered.
psql_cert -f - >"$ART/assertions.txt" <<'SQL'
DO $c4$
DECLARE bad integer;
BEGIN
 IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='sanad' AND rolcanlogin
   AND NOT rolsuper AND NOT rolbypassrls AND NOT rolcreatedb AND NOT rolcreaterole) THEN
   RAISE EXCEPTION 'C4_RUNTIME_ROLE_CONTRACT' USING ERRCODE='42501';
 END IF;
 IF pg_has_role('sanad','sanad_migrator','MEMBER') THEN
   RAISE EXCEPTION 'C4_RUNTIME_MIGRATOR_MEMBERSHIP' USING ERRCODE='42501';
 END IF;
 IF has_schema_privilege('sanad','public','CREATE') THEN
   RAISE EXCEPTION 'C4_RUNTIME_PUBLIC_SCHEMA_CREATE' USING ERRCODE='42501';
 END IF;
 SELECT count(*) INTO bad FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
 WHERE n.nspname='public' AND p.prosecdef AND
       pg_get_userbyid(p.proowner) <> 'sanad_migrator'
       AND NOT EXISTS (SELECT 1 FROM pg_depend d WHERE d.objid=p.oid AND d.deptype='e');
 IF bad<>0 THEN RAISE EXCEPTION 'C4_DEFINER_OWNER_MISMATCH count=%',bad USING ERRCODE='42501'; END IF;
 SELECT count(*) INTO bad FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
 WHERE n.nspname='public' AND p.prosecdef
   AND p.proname IN ('hr_assert_migration_tenant_scope','hr_backfill_tenant','hr_precheck_tenant','hr_reconcile_tenant')
   AND EXISTS (SELECT 1 FROM aclexplode(coalesce(p.proacl,acldefault('f',p.proowner))) a
               WHERE a.grantee=0 AND a.privilege_type='EXECUTE');
 IF bad<>0 THEN RAISE EXCEPTION 'C4_HR_PUBLIC_EXECUTE count=%',bad USING ERRCODE='42501'; END IF;
 RAISE NOTICE 'C4_ROLE_OWNER_HR_PUBLIC_EXECUTE_ASSERTIONS=PASS';
END $c4$;
SQL

# Mandatory HR definer pin: pg_temp must be explicitly LAST to prevent
# temporary relation shadowing. This is a regression gate, not just inventory.
psql_cert -f - >"$ART/hr-search-path-gate.txt" <<'SQL'
DO $c4_path$
DECLARE cnt integer;
BEGIN
 SELECT count(*) INTO cnt FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
 WHERE n.nspname='public' AND p.prosecdef AND p.proname LIKE 'hr_%'
   AND p.proconfig @> ARRAY['search_path=pg_catalog, public, pg_temp']::text[];
 IF cnt <> 10 THEN
   RAISE EXCEPTION 'C4_HR_DEFINER_PIN_COUNT=% expected=10',cnt USING ERRCODE='42501';
 END IF;
 RAISE NOTICE 'C4_HR_DEFINER_SEARCH_PATH_PINNED=10/10';
END $c4_path$;
SQL

# Controlled negative test against pg_temp shadowing with an artificial
# tenant that has no rows in the database. The definer must resolve public.
# All temp objects and GUC changes are rolled back at the end of this txn.
psql_cert -f - >"$ART/hr-temp-shadow-negative.txt" <<'SQL'
BEGIN;
SET LOCAL ROLE sanad;
SELECT set_config('app.tenant_id','aaaaaaaa-0000-0000-0000-000000000004',true);
CREATE TEMP TABLE hr_employees (tenant_id uuid, hire_date date);
INSERT INTO hr_employees VALUES ('aaaaaaaa-0000-0000-0000-000000000004','2099-12-31'::date);
DO $c4_shadow$
DECLARE got date;
BEGIN
 SELECT public.hr_default_migration_as_of_date('aaaaaaaa-0000-0000-0000-000000000004') INTO got;
 IF got IS DISTINCT FROM DATE '1970-01-01' THEN
    RAISE EXCEPTION 'C4_TEMP_SHADOWED_HR_EMPLOYEES got=%',got USING ERRCODE='42501';
 END IF;
 RAISE NOTICE 'C4_HR_TEMP_SHADOW_NEGATIVE=PASS expected=1970-01-01';
END $c4_shadow$;
ROLLBACK;
SQL

# Real attempted DDL under SET LOCAL ROLE sanad, never commits anything. A
# PostgreSQL privilege denial must be 42501; any other error fails the gate.
set +e
psql_cert -c "BEGIN; SET LOCAL ROLE sanad; CREATE TABLE public.c4_denied_ddl_probe (id integer);" >"$ART/negative-create.log" 2>&1
rc=$?
set -e
if [[ "$rc" -eq 0 ]] || ! grep -q '42501' "$ART/negative-create.log"; then
  echo "::error::C4 runtime CREATE denial not proven; rc=$rc"
  cat "$ART/negative-create.log"
  exit 1
fi
echo "C4_RUNTIME_CREATE_DENIED_42501=PASS" | tee -a "$ART/assertions.txt"
echo "C4_CATALOG_CAPTURE=PASS; C4_PARTIAL_CONTRACT=PASS; C4_FULL_APPROVAL=HOLD"
echo "C4_FINDINGS: search_path, PUBLIC EXECUTE other than HR, and effective ACL inventory require independent adjudication."
