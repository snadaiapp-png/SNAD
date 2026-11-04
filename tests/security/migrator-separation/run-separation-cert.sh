#!/usr/bin/env bash
# ============================================================================
# SANAD P0 (Issue #1462) — MIGRATOR/RUNTIME SEPARATION ACCEPTANCE MATRIX
# Article 4 of the P0 final remediation executive order.
#
# Real connections as the intended roles on an isolated host-native
# PostgreSQL 17.6 cluster with the project-pinned Flyway CLI 11.7.2:
#   * Flyway runs as sanad_migrator on a database declaring
#     sanad.deployment_topology='separated';
#   * every runtime probe connects as the restricted role sanad
#     (NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE);
#   * SQLSTATE + exit codes are recorded for every probe; any unexpected
#     outcome fails the gate (fail closed). No simulation, no mocks.
#
# Environment (all optional; defaults target a local isolated cluster):
#   SEP_PGHOST (127.0.0.1)  SEP_PGPORT (55432)
#   SEP_FLYWAY (/tmp/flyway/flyway)  SEP_REPO (repo root, auto-detected)
#   SEP_ADMIN_PSQL (psql command with superuser access; default
#                   "psql -h $SEP_PGHOST -p $SEP_PGPORT -U postgres")
#   SEP_KEEP_DB=1 (mandatory; certification DB is retained for forensic inspection).
# ============================================================================
set -uo pipefail
export LC_ALL=C.UTF-8

SEP_PGHOST="${SEP_PGHOST:-127.0.0.1}"
SEP_PGPORT="${SEP_PGPORT:-55432}"
SEP_FLYWAY="${SEP_FLYWAY:-/tmp/flyway/flyway}"
SEP_ADMIN_PSQL="${SEP_ADMIN_PSQL:-psql -h ${SEP_PGHOST} -p ${SEP_PGPORT} -U postgres}"
SEP_DB="${SEP_DB:-separation_cert}"
SEP_KEEP_DB="${SEP_KEEP_DB:-1}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SEP_REPO="${SEP_REPO:-$(cd "$SCRIPT_DIR/../../.." && pwd)}"
MIG_LOC="$SEP_REPO/apps/sanad-platform/src/main/resources/db/migration"
VEN_LOC="$SEP_REPO/apps/sanad-platform/src/main/resources/db/vendor/postgresql"
LOCS="filesystem:$MIG_LOC,filesystem:$VEN_LOC"
EXPECTED_HEAD="20261104.1"
ART="${SEP_ARTIFACTS:-/tmp/separation-cert-artifacts}"
mkdir -p "$ART"
TSV="$ART/matrix.tsv"; : > "$TSV"
SUMMARY="$ART/summary.md"
FAILURES=0

gate() { echo "GATE [$1] $2"; echo "GATE [$1] $2" >> "$SUMMARY"; }
die()  { echo "::error::GATE [$1] FAILED: $2"; echo "GATE [$1] FAILED: $2" >> "$SUMMARY";
         echo "FAILURES=$FAILURES" >> "$SUMMARY"; exit 1; }

# --- connection helpers -----------------------------------------------------
admin()   { $SEP_ADMIN_PSQL -X -v ON_ERROR_STOP=1 -d "$2" -Atc "$3"; }
runtime_out=""; runtime_rc=0; runtime_err=""; runtime_sqlstate=""
runtime_capture() { # runtime_capture <db> <sql> [tenant_guc]
  local guc_args=()
  if [ $# -ge 3 ] && [ "$3" = "__G1_EXPLICIT_EMPTY_GUC__" ]; then
    guc_args=(PGOPTIONS="-c app.tenant_id=")
  elif [ $# -ge 3 ] && [ -n "$3" ]; then
    guc_args=(PGOPTIONS="-c app.tenant_id=$3")
  fi
  # Clear inherited PGOPTIONS so missing-GUC and explicit-empty-GUC probes differ.
  runtime_out=$(env -u PGOPTIONS "${guc_args[@]}" psql -X -h "$SEP_PGHOST" -p "$SEP_PGPORT" -U sanad -d "$1" \
       -v ON_ERROR_STOP=1 -v VERBOSITY=verbose -qAtc "$2" 2>"$ART/last-err.txt")
  runtime_rc=$?
  runtime_err=$(cat "$ART/last-err.txt")
  runtime_sqlstate=$(printf '%s' "$runtime_err" | grep -oE 'ERROR: [0-9A-Z]{5}:' | head -1 | grep -oE '[0-9A-Z]{5}')
  [ -z "$runtime_sqlstate" ] && runtime_sqlstate=$(printf '%s' "$runtime_err" | grep -oE 'SQLSTATE: [0-9A-Z]+' | head -1 | sed 's/SQLSTATE: //')
  [ -z "$runtime_sqlstate" ] && printf '%s' "$runtime_err" | grep -q "permission denied" && runtime_sqlstate=42501
  [ -z "$runtime_sqlstate" ] && printf '%s' "$runtime_err" | grep -q "must be owner" && runtime_sqlstate=42501
  [ -z "$runtime_sqlstate" ] && printf '%s' "$runtime_err" | grep -q "violates row-level security" && runtime_sqlstate=42501
}

record() { # record <id> <expected> <actual_outcome> <detail>
  printf '%s\t%s\t%s\t%s\n' "$1" "$2" "$3" "$(echo "$4" | tr '\n' ' ' | cut -c1-160)" >> "$TSV"
  echo "  [$1] expected=$2 got=$3 | $(echo "$4" | head -1 | cut -c1-120)"
}
fail()  { FAILURES=$((FAILURES+1)); }

expect_pass() { # expect_pass <id> <db> <sql> [guc]
  runtime_capture "$2" "$3" "${4:-}"
  if [ $runtime_rc -eq 0 ]; then record "$1" PASS PASS "$runtime_out"
  else record "$1" PASS "FAIL(rc=$runtime_rc,$runtime_sqlstate)" "$runtime_err"; fail; fi
}
expect_deny() { # expect_deny <id> <db> <sql> [guc] [expected_sqlstate]
  local want_state="${5:-42501}"
  runtime_capture "$2" "$3" "${4:-}"
  if [ $runtime_rc -ne 0 ] && [ "$runtime_sqlstate" = "$want_state" ]; then
    record "$1" "DENY($want_state)" "DENY($runtime_sqlstate)" "$runtime_err"
  else
    record "$1" "DENY($want_state)" "FAIL(rc=$runtime_rc,$runtime_sqlstate)" "$runtime_out $runtime_err"; fail; fi
}
expect_value() { # expect_value <id> <db> <sql> <expected> [guc]
  runtime_capture "$2" "$3" "${5:-}"
  if [ $runtime_rc -eq 0 ] && [ "$runtime_out" = "$4" ]; then record "$1" "$4" "$runtime_out" ok
  else record "$1" "$4" "FAIL(rc=$runtime_rc out='$runtime_out')" "$runtime_err"; fail; fi
}
expect_zero_effect() { # DML that must affect ZERO rows (fail-closed invisibility)
  runtime_capture "$2" "$3" "${4:-}"
  if [ $runtime_rc -eq 0 ] && [ "$runtime_out" = "UPDATE 0" -o "$runtime_out" = "DELETE 0" ]; then
    record "$1" "ZERO_EFFECT" "$runtime_out" ok
  else record "$1" "ZERO_EFFECT" "FAIL(rc=$runtime_rc out='$runtime_out')" "$runtime_err"; fail; fi
}

# ============================================================================
# S0 — provision roles + separated database (admin actor)
# ============================================================================
: > "$SUMMARY"
gate S0 "provision sanad_migrator + restricted sanad + database $SEP_DB (separated topology)"
[ "${SEP_ROLE_MUTATION_APPROVED:-}" = "isolated-certification-only" ] || die S0 "BLOCKED: cluster-wide role changes require explicit isolated certification approval"
[ "$SEP_KEEP_DB" = "1" ] || die S0 "BLOCKED: automatic database deletion is disabled; SEP_KEEP_DB must be 1"
admin postgres postgres "SELECT 1" >/dev/null 2>&1 || die S0 "admin psql unavailable: $SEP_ADMIN_PSQL"
# Cluster roles are global: refuse any cluster containing other application DBs.
other_databases=$(admin postgres postgres "SELECT count(*) FROM pg_database WHERE datistemplate=false AND datname<>'postgres'")
[ "$other_databases" = "0" ] || die S0 "BLOCKED: shared PostgreSQL cluster has $other_databases non-template application databases"
[[ "$SEP_DB" =~ ^(separation_cert|g1_sep_cert_[a-z0-9_]+)$ ]] || die S0 "unsafe certification database name: $SEP_DB"
preexisting=$(admin postgres postgres "SELECT count(*) FROM pg_database WHERE datname='$SEP_DB'")
[ "$preexisting" = "0" ] || die S0 "certification database already exists; refusing destructive reset: $SEP_DB"
# Validate any injected credential BEFORE modifying global PostgreSQL roles.
SEP_RUN_PW="${SEP_RUN_PW:-$(openssl rand -hex 24)}"
[[ "$SEP_RUN_PW" =~ ^[0-9a-f]{48}$ ]] || die S0 "BLOCKED: runtime credential must be 48 lowercase hex characters"
admin postgres postgres "DO \$\$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='sanad_migrator') THEN
    CREATE ROLE sanad_migrator LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='sanad') THEN
    CREATE ROLE sanad LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS;
  END IF;
END \$\$;" >/dev/null || die S0 "role provisioning failed"
admin postgres postgres "ALTER ROLE sanad_migrator NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS" >/dev/null || die S0 "migrator contract"
admin postgres postgres "ALTER ROLE sanad NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS" >/dev/null || die S0 "runtime contract"
# Ephemeral per-run runtime password (hex, never logged) — required on hosts
# using password auth for TCP connections (GitHub runners use scram-sha-256;
# trust-auth local clusters simply ignore it).
admin postgres postgres "ALTER ROLE sanad PASSWORD '$SEP_RUN_PW'" >/dev/null || die S0 "runtime password"
export PGPASSWORD="$SEP_RUN_PW"
admin postgres postgres "CREATE DATABASE $SEP_DB OWNER sanad_migrator" >/dev/null || die S0 "database creation"
admin postgres "$SEP_DB" "ALTER DATABASE $SEP_DB SET sanad.deployment_topology = 'separated'" >/dev/null || die S0 "topology GUC"
m_contract=$(admin postgres postgres "SELECT rolcanlogin||'|'||rolsuper||'|'||rolbypassrls||'|'||rolcreatedb||'|'||rolcreaterole FROM pg_roles WHERE rolname='sanad_migrator'")
r_contract=$(admin postgres postgres "SELECT rolcanlogin||'|'||rolsuper||'|'||rolbypassrls||'|'||rolcreatedb||'|'||rolcreaterole FROM pg_roles WHERE rolname='sanad'")
[ "$m_contract" = "true|false|false|false|false" ] || die S0 "sanad_migrator contract: $m_contract"
[ "$r_contract" = "true|false|false|false|false" ] || die S0 "sanad contract: $r_contract"
gate S0 "roles OK: sanad_migrator=$m_contract sanad=$r_contract; topology=separated; DB owner=sanad_migrator"

# ============================================================================
# S1 — Flyway as sanad_migrator (fresh chain, separated topology)
# ============================================================================
gate S1 "Flyway migrate + validate as sanad_migrator (expect head $EXPECTED_HEAD)"
# Use Flyway's environment credential channel: never place the password in
# CLI arguments (which may be visible in process listings and scanner matches).
: "${SEP_MIG_PW:?SEP_MIG_PW must be supplied from the isolated test secret store}"
printf -v FLYWAY_PASSWORD '%s' "$SEP_MIG_PW"
export FLYWAY_PASSWORD
"$SEP_FLYWAY" -url="jdbc:postgresql://$SEP_PGHOST:$SEP_PGPORT/$SEP_DB" \
  -user=sanad_migrator \
  -locations="$LOCS" migrate > "$ART/S1-migrate.log" 2>&1 || die S1 "migrate failed (see $ART/S1-migrate.log)"
"$SEP_FLYWAY" -url="jdbc:postgresql://$SEP_PGHOST:$SEP_PGPORT/$SEP_DB" \
  -user=sanad_migrator \
  -locations="$LOCS" validate > "$ART/S1-validate.log" 2>&1 || die S1 "validate failed"
unset FLYWAY_PASSWORD
head_now=$(admin postgres "$SEP_DB" "SELECT version FROM flyway_schema_history WHERE success=true AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1")
[ "$head_now" = "$EXPECTED_HEAD" ] || die S1 "head=$head_now expected=$EXPECTED_HEAD"
gate S1 "PASS: full chain applied as sanad_migrator, head=$head_now, validate OK"
record FLYWAY_AS_MIGRATOR PASS PASS "head=$head_now"

# ============================================================================
# S2 — runtime positive matrix (ordinary DML as sanad)
# ============================================================================
gate S2 "runtime DML on the general set (as sanad)"
T1=11111111-1111-1111-1111-111111111111
expect_pass S2_SELECT "$SEP_DB" "SELECT count(*) FROM public.saas_plans"
expect_value S2_INSERT "$SEP_DB" "WITH inserted AS (INSERT INTO public.tenants (id,name,subdomain,status,created_at,updated_at) VALUES ('$T1','Sep Cert Tenant','sepcert','ACTIVE',now(),now()) RETURNING 1) SELECT count(*) FROM inserted" "1"
expect_value S2_UPDATE "$SEP_DB" "WITH updated AS (UPDATE public.tenants SET name='Sep Cert Tenant 2' WHERE id='$T1' RETURNING 1) SELECT count(*) FROM updated" "1"
expect_value S2_DELETE "$SEP_DB" "WITH deleted AS (DELETE FROM public.tenants WHERE id='$T1' RETURNING 1) SELECT count(*) FROM deleted" "1"
[ $FAILURES -eq 0 ] || die S2 "positive DML matrix failed"
gate S2 "PASS: SELECT/INSERT/UPDATE/DELETE as restricted runtime role"

# ============================================================================
# S3 — runtime negative matrix (DDL / ledger / role escalation)
# ============================================================================
gate S3 "runtime DDL + ledger tamper + escalation must be DENIED (42501)"
expect_deny S3_ALTER   "$SEP_DB" "ALTER TABLE public.tenants ADD COLUMN sep_probe int"
expect_deny S3_DROP    "$SEP_DB" "DROP TABLE public.saas_plans"
expect_deny S3_TRUNCATE "$SEP_DB" "TRUNCATE TABLE public.subscription_commands"
expect_deny S3_HIST_SEL "$SEP_DB" "SELECT count(*) FROM public.flyway_schema_history"
expect_deny S3_HIST_UPD "$SEP_DB" "UPDATE public.flyway_schema_history SET description='tampered' WHERE installed_rank=1"
expect_deny S3_HIST_DEL "$SEP_DB" "DELETE FROM public.flyway_schema_history WHERE installed_rank=1"
expect_deny S3_HIST_INS "$SEP_DB" "INSERT INTO public.flyway_schema_history (installed_rank,version,description,type,script,checksum,installed_by,execution_time,success) VALUES (9999,'9.9.9','x','SQL','x.sql',0,'sanad',0,true)"
expect_deny S3_SETROLE "$SEP_DB" "SET ROLE sanad_migrator"
expect_deny S3_BYPASS  "$SEP_DB" "ALTER ROLE sanad BYPASSRLS"
expect_deny S3_CREATE_TBL "$SEP_DB" "CREATE TABLE public.sep_probe(id int)"
[ $FAILURES -eq 0 ] || die S3 "negative matrix failed"
gate S3 "PASS: ALTER/DROP/TRUNCATE/ledger/SET ROLE/BYPASSRLS/CREATE all DENIED for runtime"

# ============================================================================
# S4 — tenant isolation A/B + missing/invalid GUC (fail closed)
# ============================================================================
gate S4 "tenant isolation + fail-closed GUC as sanad"
TA=22222222-2222-2222-2222-222222222222
TB=33333333-3333-3333-3333-333333333333
UA=44444444-4444-4444-4444-444444444444
UB=55555555-5555-5555-5555-555555555555
admin postgres "$SEP_DB" "INSERT INTO public.tenants (id,name,subdomain,status,created_at,updated_at) VALUES ('$TA','Tenant A','sepa','ACTIVE',now(),now()), ('$TB','Tenant B','sepb','ACTIVE',now(),now())" >/dev/null || die S4 "tenant seeding"
admin postgres "$SEP_DB" "SET app.tenant_id='$TA'; INSERT INTO public.users (id,tenant_id,email,status,created_at,updated_at) VALUES ('$UA','$TA','a@sepcert.test','ACTIVE',now(),now())" >/dev/null || die S4 "user A seeding"
admin postgres "$SEP_DB" "SET app.tenant_id='$TB'; INSERT INTO public.users (id,tenant_id,email,status,created_at,updated_at) VALUES ('$UB','$TB','b@sepcert.test','ACTIVE',now(),now())" >/dev/null || die S4 "user B seeding"
expect_value S4_A_SEES_A "$SEP_DB" "SELECT count(*) FROM public.users" "1" "$TA"
expect_value S4_B_SEES_B "$SEP_DB" "SELECT count(*) FROM public.users WHERE id='$UB'" "1" "$TB"
expect_value S4_B_CANNOT_SEE_A "$SEP_DB" "SELECT count(*) FROM public.users WHERE id='$UA'" "0" "$TB"
expect_value S4_CROSS_UPDATE "$SEP_DB" "WITH upd AS (UPDATE public.users SET display_name='x' WHERE id='$UB' RETURNING 1) SELECT count(*) FROM upd" "0" "$TA"
expect_deny S4_CROSS_INSERT "$SEP_DB" "INSERT INTO public.users (id,tenant_id,email,status,created_at,updated_at) VALUES ('66666666-6666-6666-6666-666666666666','$TB','c@sepcert.test','ACTIVE',now(),now())" "$TA"
expect_value S4_NO_GUC_EFFECTIVE "$SEP_DB" "SELECT CASE WHEN current_setting('app.tenant_id',true) IS NULL THEN 'ABSENT' ELSE 'UNEXPECTED' END" "ABSENT"
expect_value S4_NO_GUC "$SEP_DB" "SELECT count(*) FROM public.users" "0"
expect_value S4_EMPTY_GUC_EFFECTIVE "$SEP_DB" "SELECT CASE WHEN current_setting('app.tenant_id',true)='' THEN 'EXPLICIT_EMPTY' ELSE 'UNEXPECTED' END" "EXPLICIT_EMPTY" "__G1_EXPLICIT_EMPTY_GUC__"
expect_value S4_EMPTY_GUC "$SEP_DB" "SELECT count(*) FROM public.users" "0" "__G1_EXPLICIT_EMPTY_GUC__"
# Regression for the CRM tables that historically used fail-open tenant policies.
# Fixture INSERT is performed by the isolated cluster's admin; assertions are
# performed through a separate restricted runtime connection.
admin postgres "$SEP_DB" "INSERT INTO public.crm_customer_segments(tenant_id,segment_code,segment_name,segment_type) VALUES ('$TA','g1-cert-segment-a','G1 cert segment A','MANUAL'),('$TB','g1-cert-segment-b','G1 cert segment B','MANUAL')" >/dev/null || die S4 "CRM A/B fixture seed failed"
expect_value S4_CRM_A_OWN "$SEP_DB" "SELECT count(*) FROM public.crm_customer_segments WHERE segment_code='g1-cert-segment-a'" "1" "$TA"
expect_value S4_CRM_A_CANNOT_SEE_B "$SEP_DB" "SELECT count(*) FROM public.crm_customer_segments WHERE segment_code='g1-cert-segment-b'" "0" "$TA"
expect_value S4_CRM_CROSS_UPDATE "$SEP_DB" "WITH upd AS (UPDATE public.crm_customer_segments SET segment_name='G1 unauthorized' WHERE segment_code='g1-cert-segment-b' RETURNING 1) SELECT count(*) FROM upd" "0" "$TA"
expect_deny S4_CRM_CROSS_INSERT "$SEP_DB" "INSERT INTO public.crm_customer_segments (tenant_id,segment_code,segment_name,segment_type) VALUES ('$TB','g1-cert-cross-insert','G1 blocked','MANUAL')" "$TA"
expect_value S4_CRM_NO_GUC "$SEP_DB" "SELECT count(*) FROM public.crm_customer_segments WHERE segment_code IN ('g1-cert-segment-a','g1-cert-segment-b')" "0"
expect_value S4_CRM_EMPTY_GUC "$SEP_DB" "SELECT count(*) FROM public.crm_customer_segments WHERE segment_code IN ('g1-cert-segment-a','g1-cert-segment-b')" "0" "__G1_EXPLICIT_EMPTY_GUC__"
[ $FAILURES -eq 0 ] || die S4 "tenant isolation matrix failed"
gate S4 "PASS: Users + CRM Tenant A/B access isolated; denied cross-tenant INSERT; missing/empty-GUC both fail-closed"

# ============================================================================
# S5 — auth plane: user + refresh tokens + trigger + password reset + pre-auth
# ============================================================================
gate S5 "auth plane as sanad (user creation, refresh token lifecycle, pre-auth index)"
UC=77777777-7777-7777-7777-777777777777
RT=88888888-8888-8888-8888-888888888888
PRT=99999999-9999-9999-9999-999999999999
expect_pass S5_USER_CREATE "$SEP_DB" "INSERT INTO public.users (id,tenant_id,email,status,created_at,updated_at) VALUES ('$UC','$TA','u2@sepcert.test','ACTIVE',now(),now())" "$TA"
expect_pass S5_REFRESH_INS "$SEP_DB" "INSERT INTO public.refresh_tokens (id,tenant_id,user_id,token_hash,status,expires_at,created_at) VALUES ('$RT','$TA','$UC','sepcert-hash-1','ACTIVE',now()+interval '1 day',now())" "$TA"
expect_value S5_TRIGGER_INDEX "$SEP_DB" "SELECT count(*) FROM public.auth_token_tenant_index WHERE token_hash='sepcert-hash-1' AND tenant_id='$TA'" "1"
expect_value S5_REFRESH_UPD "$SEP_DB" "WITH updated AS (UPDATE public.refresh_tokens SET status='REVOKED' WHERE id='$RT' RETURNING 1) SELECT count(*) FROM updated" "1" "$TA"
expect_value S5_REFRESH_DEL "$SEP_DB" "WITH deleted AS (DELETE FROM public.refresh_tokens WHERE id='$RT' RETURNING 1) SELECT count(*) FROM deleted" "1" "$TA"
expect_pass S5_RESET_INS "$SEP_DB" "INSERT INTO public.password_reset_tokens (id,tenant_id,user_id,token_hash,expires_at,created_at) VALUES ('$PRT','$TA','$UC','sepcert-hash-2',now()+interval '1 hour',now())" "$TA"
expect_value S5_RESET_DEL "$SEP_DB" "WITH deleted AS (DELETE FROM public.password_reset_tokens WHERE id='$PRT' RETURNING 1) SELECT count(*) FROM deleted" "1" "$TA"
expect_value S5_PREAUTH_RESOLVE "$SEP_DB" "SELECT count(*) FROM public.g1_resolve_login_tenants('email','a@sepcert.test')" "1"
expect_value S5_PREAUTH_INDEX "$SEP_DB" "SELECT count(*) FROM public.login_tenant_index WHERE kind='email' AND identifier='a@sepcert.test' AND tenant_id='$TA'" "1"
[ $FAILURES -eq 0 ] || die S5 "auth plane matrix failed"
gate S5 "PASS: user creation, refresh/reset token lifecycle, trigger-maintained pre-auth index, pre-auth resolver (no GUC)"

# ============================================================================
# S6 — subscriptions (append-only ledger) + provisioning as sanad
# ============================================================================
gate S6 "subscription_commands append-only + provisioning as sanad"
SC=aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa
SUB=dddddddd-dddd-dddd-dddd-dddddddddddd
PLAN=$(admin postgres "$SEP_DB" "SELECT id FROM public.saas_plans LIMIT 1")
PLANVER=$(admin postgres "$SEP_DB" "SELECT id FROM public.plan_versions LIMIT 1")
[ -n "$PLAN" ] && [ -n "$PLANVER" ] || die S6 "BLOCKED: plan and plan-version fixtures must exist before subscription acceptance"
# F-01 closure evidence: the runtime role itself creates the subscription row
expect_value S6_TENANT_SUB_INS "$SEP_DB" "WITH created AS (INSERT INTO public.tenant_subscriptions (id,tenant_id,plan_id,plan_version_id,status,billing_cycle,seat_quantity,credit_balance_minor,started_at,current_period_start,current_period_end,cancel_at_period_end,created_at,updated_at) VALUES ('$SUB','$TA','$PLAN','$PLANVER','TRIALING','MONTHLY',1,0,now(),now(),now()+interval '30 days',false,now(),now()) RETURNING 1) SELECT count(*) FROM created" "1" "$TA"
expect_value S6_SUB_INS "$SEP_DB" "WITH created AS (INSERT INTO public.subscription_commands (id,subscription_id,tenant_id,command,from_status,to_status,reason,created_at) VALUES ('$SC','$SUB','$TA','CHANGE_PLAN','TRIALING','ACTIVE','separation-cert probe',now()) RETURNING 1) SELECT count(*) FROM created" "1" "$TA"
expect_deny  S6_SUB_UPD "$SEP_DB" "UPDATE public.subscription_commands SET reason='FORCED' WHERE id='$SC'" "$TA"
expect_deny  S6_SUB_DEL "$SEP_DB" "DELETE FROM public.subscription_commands WHERE id='$SC'" "$TA"
[ $FAILURES -eq 0 ] || die S6 "subscription plane matrix failed"
gate S6 "PASS: subscription_commands INSERT ok; UPDATE/DELETE DENIED (append-only enforced for runtime)"

# ============================================================================
# S7 — ownership + privilege snapshot (catalog-level evidence)
# ============================================================================
gate S7 "catalog snapshot: ownership + effective ACL + FORCE RLS"
own_stats=$(admin postgres "$SEP_DB" "SELECT pg_get_userbyid(c.relowner)||':'||count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind IN ('r','p','S','v','m') GROUP BY pg_get_userbyid(c.relowner) ORDER BY count(*) DESC" | tr '\n' ' ')
echo "OWNERSHIP: $own_stats" >> "$SUMMARY"
echo "$own_stats" | grep -q "sanad:" && die S7 "runtime role still owns objects: $own_stats"
hist_owner=$(admin postgres "$SEP_DB" "SELECT pg_get_userbyid(relowner) FROM pg_class WHERE oid='public.flyway_schema_history'::regclass")
[ "$hist_owner" = "sanad_migrator" ] || die S7 "flyway_schema_history owner=$hist_owner"
runtime_create=$(admin postgres "$SEP_DB" "SELECT has_schema_privilege('sanad','public','CREATE')")
[ "$runtime_create" = "f" ] || die S7 "sanad still holds CREATE on public"
rls_bad=$(admin postgres "$SEP_DB" "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relname IN ('users','refresh_tokens','password_reset_tokens','provisioning_jobs','provisioning_job_steps','subscription_commands') AND NOT (relrowsecurity AND relforcerowsecurity)")
[ "$rls_bad" = "0" ] || die S7 "FORCE RLS missing on $rls_bad guarded tables"
tenant_force_bad=$(admin postgres "$SEP_DB" "SELECT count(*) FROM pg_class c JOIN pg_attribute a ON a.attrelid=c.oid AND a.attname='tenant_id' WHERE c.relnamespace='public'::regnamespace AND c.relkind IN ('r','p') AND c.relrowsecurity AND NOT c.relforcerowsecurity")
[ "$tenant_force_bad" = "0" ] || die S7 "tenant-scoped tables missing FORCE RLS: $tenant_force_bad"
failopen_bad=$(admin postgres "$SEP_DB" "SELECT count(*) FROM pg_policy p JOIN pg_class c ON c.oid=p.polrelid WHERE c.relnamespace='public'::regnamespace AND c.relrowsecurity AND (pg_get_expr(p.polqual,p.polrelid) LIKE '%current_setting(''app.tenant_id''%IS NULL) OR%' OR pg_get_expr(p.polwithcheck,p.polrelid) LIKE '%current_setting(''app.tenant_id''%IS NULL) OR%')")
[ "$failopen_bad" = "0" ] || die S7 "fail-open tenant policies detected: $failopen_bad"
seq_ok=$(admin postgres "$SEP_DB" "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind='S' AND NOT (has_sequence_privilege('sanad',quote_ident(n.nspname)||'.'||quote_ident(c.relname),'USAGE') AND has_sequence_privilege('sanad',quote_ident(n.nspname)||'.'||quote_ident(c.relname),'SELECT'))")
[ "$seq_ok" = "0" ] || die S7 "$seq_ok sequence(s) missing USAGE/SELECT for sanad"
record S7_CATALOG PASS PASS "owners=[$own_stats] history=$hist_owner sanadCREATE=$runtime_create forceRLS=6/6 seqOK"
gate S7 "PASS: $own_stats; history owner=$hist_owner; runtime CREATE=false; FORCE RLS 6/6; sequences OK"

# ============================================================================
# S8 — verdict
# ============================================================================
[ "$SEP_KEEP_DB" = "1" ] || die S8 "BLOCKED: automatic database deletion disabled; preserve forensic evidence and clean up separately after review"
echo "EVIDENCE_DATABASE_RETAINED=$SEP_DB" >> "$SUMMARY"
{
  echo ""
  echo "## Separation acceptance matrix — $(date -u +'%Y-%m-%dT%H:%M:%SZ')"
  echo ""
  echo "| id | expected | actual | detail |"
  echo "|---|---|---|---|"
  awk -F'\t' '{printf "| %s | %s | %s | %s |\n", $1,$2,$3,$4}' "$TSV"
  echo ""
  echo "PROBES=$(wc -l < "$TSV") FAILURES=$FAILURES"
} >> "$SUMMARY"

if [ $FAILURES -gt 0 ]; then
  die VERDICT "$FAILURES probe(s) failed — see $TSV"
fi
echo "=================================================================="
echo "SEPARATION ACCEPTANCE MATRIX: ALL PROBES PASSED ($(wc -l < "$TSV") probes)"
echo "Artifacts: $ART (matrix.tsv, S1-migrate.log, S1-validate.log, summary.md)"
echo "=================================================================="
