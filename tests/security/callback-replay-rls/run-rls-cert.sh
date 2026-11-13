#!/usr/bin/env bash
# ============================================================================
# SANAD G1 / P0 (executive order 2026-11-11) — service_callback_replay
# RLS CLOSURE ACCEPTANCE MATRIX.
#
# Real connections as the intended roles on an isolated host-native
# PostgreSQL 17.x cluster with the project-pinned Flyway CLI 11.7.2:
#   * Flyway runs as sanad_migrator on a database declaring
#     sanad.deployment_topology='separated' (the production model);
#   * every runtime probe connects as the restricted role sanad
#     (NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE);
#   * the dedicated expired-row cleanup role sanad_callback_replay_cleanup
#     (V20261111_1) is provisioned with a NON-INHERITING membership for sanad;
#   * SQLSTATE + exit codes are recorded for every probe; any unexpected
#     outcome fails the gate (fail closed). No simulation, no mocks.
#
# Mandatory matrix (executive order phase 3):
#   M1  Tenant A cannot read or modify Tenant B rows.
#   M2  Tenant B cannot access Tenant A rows.
#   M3  Missing tenant context denies every access (fail-closed).
#   M4  INSERT with a mismatched tenant id is REJECTED (42501).
#   M5  DELETE outside the tenant is rejected / zero-effect, row preserved.
#   M6  The cleanup worker path grants no unauthorized access: the runtime
#       identity (non-inheriting member) never sees cross-tenant rows; the
#       dedicated role sees and deletes ONLY expired rows; INSERT/UPDATE
#       stay denied for it; non-members cannot SET ROLE.
#   M7  Callback functions do not regress: the consume INSERT works under
#       the tenant policy and the replay uniqueness (23505) still fires.
#
# Environment (all optional; defaults target a local isolated cluster):
#   CRL_PGHOST (127.0.0.1)  CRL_PGPORT (55433)
#   CRL_FLYWAY (/tmp/flyway/flyway)  CRL_REPO (repo root, auto-detected)
#   CRL_ADMIN_PSQL (psql command with superuser access; default
#                   "psql -h $CRL_PGHOST -p $CRL_PGPORT -U postgres")
#   CRL_DB (must match ^callback_replay_rls_cert(_[a-z0-9_]+)?$)
#   CRL_KEEP_DB=1 to keep the certification database for inspection.
#   CRL_ISOLATED_CLUSTER=1 — required before any cluster-wide role change.
# ============================================================================
set -uo pipefail
export LC_ALL=C.UTF-8

CRL_PGHOST="${CRL_PGHOST:-127.0.0.1}"
CRL_PGPORT="${CRL_PGPORT:-55433}"
CRL_FLYWAY="${CRL_FLYWAY:-/tmp/flyway/flyway}"
CRL_ADMIN_PSQL="${CRL_ADMIN_PSQL:-psql -h ${CRL_PGHOST} -p ${CRL_PGPORT} -U postgres}"
CRL_DB="${CRL_DB:-callback_replay_rls_cert}"
CRL_KEEP_DB="${CRL_KEEP_DB:-0}"
if [[ ! "$CRL_DB" =~ ^callback_replay_rls_cert(_[a-z0-9_]+)?$ ]] || [ "${#CRL_DB}" -gt 63 ]; then
  echo "::error::Unsafe CRL_DB: expected callback_replay_rls_cert or callback_replay_rls_cert_<suffix>" >&2
  exit 1
fi
if [[ "$CRL_KEEP_DB" != "0" && "$CRL_KEEP_DB" != "1" ]]; then
  echo "::error::CRL_KEEP_DB must be 0 or 1" >&2
  exit 1
fi
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
command -v flock >/dev/null 2>&1 || { echo "::error::flock is required" >&2; exit 1; }
exec 9< "${BASH_SOURCE[0]}" || exit 1
flock -n 9 || { echo "::error::Another certification is running from this checkout" >&2; exit 1; }
CRL_REPO="${CRL_REPO:-$(cd "$SCRIPT_DIR/../../.." && pwd)}"
MIG_LOC="$CRL_REPO/apps/sanad-platform/src/main/resources/db/migration"
VEN_LOC="$CRL_REPO/apps/sanad-platform/src/main/resources/db/vendor/postgresql"
LOCS="filesystem:$MIG_LOC,filesystem:$VEN_LOC"
EXPECTED_HEAD="20261113.1"
ART="${CRL_ARTIFACTS:-/tmp/callback-replay-rls-cert-artifacts}"
umask 077
[ ! -L "$ART" ] || { echo "::error::Evidence directory is a symlink" >&2; exit 1; }
if [ -e "$ART" ]; then
  [ -d "$ART" ] && [ -z "$(ls -A "$ART")" ] || {
    echo "::error::Evidence directory already contains data; choose a fresh CRL_ARTIFACTS" >&2
    exit 1
  }
else
  mkdir -m 700 -p "$ART" || exit 1
fi
TSV="$ART/matrix.tsv"; : > "$TSV"
SUMMARY="$ART/summary.md"
FAILURES=0

gate() { echo "GATE [$1] $2"; echo "GATE [$1] $2" >> "$SUMMARY"; }
die()  { echo "::error::GATE [$1] FAILED: $2"; echo "GATE [$1] FAILED: $2" >> "$SUMMARY";
         echo "FAILURES=$FAILURES" >> "$SUMMARY"; exit 1; }
on_exit() {
  local status=$?
  trap - EXIT
  unset CRL_MIG_PW
  if [ "$status" -ne 0 ]; then
    printf 'EXIT_STATUS=%s\nAUTO_CLEANUP_ON_FAILURE=SKIPPED\nRESOURCE_EXISTENCE=UNVERIFIED\n' "$status" >> "$SUMMARY"
    printf '::error::Certification exited %s; no automatic cleanup attempted; inspect resources before recovery\n' "$status" >&2
  fi
}
trap on_exit EXIT

# --- connection helpers -----------------------------------------------------
admin()   { $CRL_ADMIN_PSQL -X -v ON_ERROR_STOP=1 -d "$2" -Atc "$3"; }
admin_secret() { printf '%s\n' "$1" | $CRL_ADMIN_PSQL -X -v ON_ERROR_STOP=1 -d postgres -At >/dev/null; }
runtime_out=""; runtime_rc=0; runtime_err=""; runtime_sqlstate=""
runtime_capture() { # runtime_capture <db> <sql> [tenant_guc|__EMPTY_GUC__]
  local guc_args=()
  if [ $# -ge 3 ] && [ "$3" = "__EMPTY_GUC__" ]; then
    guc_args=(PGOPTIONS="-c app.tenant_id=")
  elif [ $# -ge 3 ] && [ -n "$3" ]; then
    guc_args=(PGOPTIONS="-c app.tenant_id=$3")
  fi
  runtime_out=$(env -u PGOPTIONS -u PGHOSTADDR -u PGSERVICE -u PGSERVICEFILE PGPASSWORD="$CRL_RUN_PW" "${guc_args[@]}" psql -X -h "$CRL_PGHOST" -p "$CRL_PGPORT" -U sanad -d "$1" \
       -v ON_ERROR_STOP=1 -v VERBOSITY=verbose -qAtc "$2" 2>"$ART/last-err.txt")
  runtime_rc=$?
  runtime_err=$(cat "$ART/last-err.txt")
  runtime_sqlstate=$(printf '%s\n' "$runtime_err" | sed -nE 's/^.*ERROR:[[:space:]]+([0-9A-Z]{5}):.*$/\1/p' | head -1)
  [ -z "$runtime_sqlstate" ] && runtime_sqlstate=$(printf '%s\n' "$runtime_err" | sed -nE 's/^.*SQLSTATE:[[:space:]]*([0-9A-Z]{5})([^0-9A-Z]|$).*$/\1/p' | head -1)
}
record() { printf '%s\t%s\t%s\t%s\n' "$1" "$2" "$3" "$(echo "$4" | tr '\n' ' ' | cut -c1-160)" >> "$TSV";
           echo "  [$1] expected=$2 got=$3 | $(echo "$4" | head -1 | cut -c1-120)"; }
fail()  { FAILURES=$((FAILURES+1)); }
expect_pass() { runtime_capture "$2" "$3" "${4:-}"
  if [ $runtime_rc -eq 0 ]; then record "$1" PASS PASS "$runtime_out"
  else record "$1" PASS "FAIL(rc=$runtime_rc,$runtime_sqlstate)" "$runtime_err"; fail; fi }
expect_deny() { local want_state="${5:-42501}"
  runtime_capture "$2" "$3" "${4:-}"
  if [ $runtime_rc -ne 0 ] && [ "$runtime_sqlstate" = "$want_state" ]; then
    record "$1" "DENY($want_state)" "DENY($runtime_sqlstate)" "$runtime_err"
  else
    record "$1" "DENY($want_state)" "FAIL(rc=$runtime_rc,$runtime_sqlstate)" "$runtime_out $runtime_err"; fail; fi }
expect_value() { runtime_capture "$2" "$3" "${5:-}"
  if [ $runtime_rc -eq 0 ] && [ "$runtime_out" = "$4" ]; then record "$1" "$4" "$runtime_out" ok
  else record "$1" "$4" "FAIL(rc=$runtime_rc out='$runtime_out')" "$runtime_err"; fail; fi }
expect_zero_effect() { runtime_capture "$2" "$3" "${4:-}"
  if [ "$runtime_rc" -eq 0 ] && { [ "$runtime_out" = "UPDATE 0" ] || [ "$runtime_out" = "DELETE 0" ]; }; then
    record "$1" "ZERO_EFFECT" "$runtime_out" ok
  else record "$1" "ZERO_EFFECT" "FAIL(rc=$runtime_rc out='$runtime_out')" "$runtime_err"; fail; fi }

# ============================================================================
# S0 — provision roles + separated database (admin actor)
# ============================================================================
: > "$SUMMARY"
gate S0 "provision sanad_migrator + restricted sanad + cleanup role + database $CRL_DB (separated topology)"
[ "${CRL_ISOLATED_CLUSTER:-0}" = "1" ] || die S0 "CRL_ISOLATED_CLUSTER=1 required before cluster-wide role changes"
case "$CRL_PGHOST" in
  127.0.0.1|localhost|::1) ;;
  *) die S0 "refusing role mutations on non-loopback CRL_PGHOST=$CRL_PGHOST" ;;
esac
admin postgres postgres "SELECT 1" >/dev/null 2>&1 || die S0 "admin psql unavailable: $CRL_ADMIN_PSQL"
other_dbs=$(admin postgres postgres "SELECT count(*) FROM pg_database WHERE datistemplate=false AND datname <> 'postgres'")
[ "$other_dbs" = "0" ] || die S0 "cluster contains $other_dbs non-system database(s); refusing role mutations"
[ "$(admin postgres postgres "SELECT count(*) FROM pg_database WHERE datname='$CRL_DB'")" = "0" ] \
  || die S0 "database $CRL_DB already exists; refusing destructive replacement"
existing_roles=$(admin postgres postgres "SELECT count(*) FROM pg_roles WHERE rolname IN ('sanad','sanad_migrator','sanad_callback_replay_cleanup')")
[ "$existing_roles" = "0" ] || die S0 "existing sanad/sanad_migrator/sanad_callback_replay_cleanup role detected; refusing shared cluster identity changes"
: "${CRL_MIG_PW:?CRL_MIG_PW must be supplied for disposable migrator provisioning}"
[[ "$CRL_MIG_PW" =~ ^[0-9a-fA-F]{48}$ ]] || die S0 "CRL_MIG_PW must be 48 hex characters"
CRL_RUN_PW="${CRL_RUN_PW:-$(openssl rand -hex 24)}"
[[ "$CRL_RUN_PW" =~ ^[0-9a-fA-F]{48}$ ]] || die S0 "CRL_RUN_PW must be 48 hex characters"
admin postgres postgres "DO \$\$ BEGIN
  CREATE ROLE sanad_migrator LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
  CREATE ROLE sanad LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS;
  CREATE ROLE sanad_callback_replay_cleanup NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
  GRANT sanad_callback_replay_cleanup TO sanad WITH INHERIT FALSE;
END \$\$;" >/dev/null || die S0 "role provisioning failed"
admin_secret "ALTER ROLE sanad_migrator PASSWORD '$CRL_MIG_PW';" || die S0 "migrator password"
admin_secret "ALTER ROLE sanad PASSWORD '$CRL_RUN_PW';" || die S0 "runtime password"
admin_instance=$(admin postgres postgres "SELECT pg_postmaster_start_time()::text")
runtime_instance=$(env -u PGOPTIONS PGPASSWORD="$CRL_RUN_PW" psql -X -h "$CRL_PGHOST" -p "$CRL_PGPORT" -U sanad -d postgres -At -c "SELECT pg_postmaster_start_time()::text") \
  || die S0 "runtime TCP identity check failed"
[ "$admin_instance" = "$runtime_instance" ] || die S0 "admin and runtime connections target different PostgreSQL postmasters"
admin postgres postgres "CREATE DATABASE $CRL_DB OWNER sanad_migrator" >/dev/null || die S0 "database creation"
admin postgres "$CRL_DB" "ALTER DATABASE $CRL_DB SET sanad.deployment_topology = 'separated'" >/dev/null || die S0 "topology GUC"
m_contract=$(admin postgres postgres "SELECT rolcanlogin||'|'||rolsuper||'|'||rolbypassrls||'|'||rolcreatedb||'|'||rolcreaterole FROM pg_roles WHERE rolname='sanad_migrator'")
r_contract=$(admin postgres postgres "SELECT rolcanlogin||'|'||rolsuper||'|'||rolbypassrls||'|'||rolcreatedb||'|'||rolcreaterole||'|'||rolinherit FROM pg_roles WHERE rolname='sanad'")
c_contract=$(admin postgres postgres "SELECT rolcanlogin||'|'||rolsuper||'|'||rolbypassrls||'|'||rolcreatedb||'|'||rolcreaterole FROM pg_roles WHERE rolname='sanad_callback_replay_cleanup'")
memb_contract=$(admin postgres postgres "SELECT pg_has_role('sanad','sanad_callback_replay_cleanup','MEMBER')||'|'||pg_has_role('sanad','sanad_callback_replay_cleanup','USAGE')||'|'||pg_has_role('sanad','sanad_callback_replay_cleanup','SET')||'|'||pg_has_role('sanad','sanad_migrator','MEMBER')||'|'||pg_has_role('sanad_callback_replay_cleanup','sanad','MEMBER')")
[ "$m_contract" = "true|false|false|false|false" ] || die S0 "sanad_migrator contract: $m_contract"
[ "$r_contract" = "true|false|false|false|false|false" ] || die S0 "sanad contract: $r_contract"
[ "$c_contract" = "false|false|false|false|false" ] || die S0 "cleanup role contract: $c_contract"
[ "$memb_contract" = "true|false|true|false|false" ] || die S0 "membership contract (MEMBER|USAGE|SET|migrator-member|escalation): $memb_contract"
gate S0 "roles OK (sanad_migrator=$m_contract sanad=$r_contract cleanup=$c_contract); membership MEMBER=yes USAGE=no SET=yes; topology=separated; DB owner=sanad_migrator"
record S0_MEMBERSHIP_SET_ONLY "t|f|t" "$memb_contract" "non-inheriting SET-only membership for the runtime identity"

# ============================================================================
# S1 — Flyway as sanad_migrator (fresh chain, separated topology)
# ============================================================================
gate S1 "Flyway migrate + validate as sanad_migrator (expect head $EXPECTED_HEAD)"
env FLYWAY_PASSWORD="$CRL_MIG_PW" "$CRL_FLYWAY" -url="jdbc:postgresql://$CRL_PGHOST:$CRL_PGPORT/$CRL_DB" \
  -user=sanad_migrator -locations="$LOCS" migrate > "$ART/S1-migrate.log" 2>&1 || die S1 "migrate failed (see $ART/S1-migrate.log)"
env FLYWAY_PASSWORD="$CRL_MIG_PW" "$CRL_FLYWAY" -url="jdbc:postgresql://$CRL_PGHOST:$CRL_PGPORT/$CRL_DB" \
  -user=sanad_migrator -locations="$LOCS" validate > "$ART/S1-validate.log" 2>&1 || die S1 "validate failed"
head_now=$(admin postgres "$CRL_DB" "SELECT version FROM flyway_schema_history WHERE success=true AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1")
[ "$head_now" = "$EXPECTED_HEAD" ] || die S1 "head=$head_now expected=$EXPECTED_HEAD"
gate S1 "PASS: full chain applied as sanad_migrator, head=$head_now, validate OK"
record FLYWAY_AS_MIGRATOR PASS PASS "head=$head_now"

# ============================================================================
# S2 — catalog contract for public.service_callback_replay
# ============================================================================
gate S2 "catalog: ENABLE+FORCE RLS, five bounded policies, cleanup role ACL"
rls_flags=$(admin postgres "$CRL_DB" "SELECT relrowsecurity||'|'||relforcerowsecurity FROM pg_class WHERE oid='public.service_callback_replay'::regclass")
[ "$rls_flags" = "true|true" ] || die S2 "RLS flags: $rls_flags (expected true|true)"
policies=$(admin postgres "$CRL_DB" "SELECT policyname||':'||cmd FROM pg_policies WHERE schemaname='public' AND tablename='service_callback_replay' ORDER BY policyname")
expected_policies="expired_cleanup_delete:DELETE
expired_cleanup_select:SELECT
tenant_isolation_delete:DELETE
tenant_isolation_insert:INSERT
tenant_isolation_select:SELECT"
[ "$policies" = "$expected_policies" ] || die S2 "policy set mismatch:
$policies"
update_policies=$(admin postgres "$CRL_DB" "SELECT count(*) FROM pg_policy WHERE polrelid='public.service_callback_replay'::regclass AND polcmd='U'")
[ "$update_policies" = "0" ] || die S2 "UPDATE policy exists (must be default-denied)"
tenant_pred=$(admin postgres "$CRL_DB" "SELECT count(*) FROM pg_policies WHERE schemaname='public' AND tablename='service_callback_replay' AND policyname LIKE 'tenant_isolation%' AND (COALESCE(qual,'')||COALESCE(with_check,'')) LIKE '%tenant_id%text = NULLIF(current_setting%app.tenant_id%'")
[ "$tenant_pred" = "3" ] || die S2 "tenant predicate contract on $tenant_pred/3 policies"
expiry_pred=$(admin postgres "$CRL_DB" "SELECT count(*) FROM pg_policies WHERE schemaname='public' AND tablename='service_callback_replay' AND policyname LIKE 'expired_cleanup%' AND qual LIKE '%expires_at <= CURRENT_TIMESTAMP%'")
[ "$expiry_pred" = "2" ] || die S2 "expiry-bound predicate contract on $expiry_pred/2 policies"
cleanup_acl=$(admin postgres "$CRL_DB" "SELECT has_table_privilege('sanad_callback_replay_cleanup','public.service_callback_replay','SELECT')||'|'||has_table_privilege('sanad_callback_replay_cleanup','public.service_callback_replay','DELETE')||'|'||has_table_privilege('sanad_callback_replay_cleanup','public.service_callback_replay','INSERT')||'|'||has_table_privilege('sanad_callback_replay_cleanup','public.service_callback_replay','UPDATE')||'|'||has_table_privilege('sanad_callback_replay_cleanup','public.service_callback_replay','TRUNCATE')||'|'||has_table_privilege('sanad_callback_replay_cleanup','public.service_callback_replay','REFERENCES')||'|'||has_table_privilege('sanad_callback_replay_cleanup','public.service_callback_replay','TRIGGER')")
[ "$cleanup_acl" = "true|true|false|false|false|false|false" ] || die S2 "cleanup role ACL: $cleanup_acl (expected SELECT|DELETE only)"
sanad_acl=$(admin postgres "$CRL_DB" "SELECT has_table_privilege('sanad','public.service_callback_replay','SELECT')||'|'||has_table_privilege('sanad','public.service_callback_replay','INSERT')||'|'||has_table_privilege('sanad','public.service_callback_replay','DELETE')||'|'||has_table_privilege('sanad','public.service_callback_replay','UPDATE')")
[ "$sanad_acl" = "true|true|true|false" ] || die S2 "sanad runtime ACL: $sanad_acl (expected SELECT|INSERT|DELETE, no UPDATE)"
gate S2 "PASS: rls=$rls_flags; 5 policies (3 tenant TO PUBLIC, 2 expired-bounded TO cleanup); no UPDATE policy; cleanup ACL=$cleanup_acl; sanad ACL=$sanad_acl"
record S2_CATALOG "rls=t|t policies=5" "rls=$rls_flags" "cleanup_acl=$cleanup_acl sanad_acl=$sanad_acl"

# ============================================================================
# S3 — mandatory isolation matrix as sanad (separated topology)
# ============================================================================
gate S3 "tenant isolation A/B + fail-closed + cleanup-path bounds (as sanad)"
TA=c1a1a1a1-1111-4111-8111-1a1a1a1a1a1a
TB=c2b2b2b2-2222-4222-8222-2b2b2b2b2b2b
admin postgres "$CRL_DB" "INSERT INTO public.tenants (id,name,subdomain,status,created_at,updated_at) VALUES ('$TA','RLS Tenant A','rls-a','ACTIVE',now(),now()), ('$TB','RLS Tenant B','rls-b','ACTIVE',now(),now())" >/dev/null || die S3 "tenant seeding"

# Seed one row per tenant as the runtime identity (the consume path semantics).
expect_pass S3_SEED_A "$CRL_DB" "INSERT INTO public.service_callback_replay (tenant_id,service_name,jti,nonce,correlation_id,received_at,expires_at) VALUES ('$TA','rls-cert-service','jti-a-1','nonce-a-1','corr-a-1',now(),now()+interval '1 hour')" "$TA"
expect_pass S3_SEED_B "$CRL_DB" "INSERT INTO public.service_callback_replay (tenant_id,service_name,jti,nonce,correlation_id,received_at,expires_at) VALUES ('$TB','rls-cert-service','jti-b-1','nonce-b-1','corr-b-1',now(),now()+interval '1 hour')" "$TB"

# M1 — Tenant A cannot read or modify Tenant B rows.
expect_value  M1_A_SEES_OWN "$CRL_DB" "SELECT count(*) FROM public.service_callback_replay" "1" "$TA"
expect_value  M1_A_CANNOT_SEE_B "$CRL_DB" "SELECT count(*) FROM public.service_callback_replay WHERE tenant_id='$TB'" "0" "$TA"
# Cross-tenant UPDATE is double-denied for the runtime identity: V20261105_1
# revoked the UPDATE privilege in the separated topology AND no UPDATE policy
# exists (default-denied for every role). Both denial layers are 42501.
expect_deny   M1_A_UPDATE_B "$CRL_DB" "UPDATE public.service_callback_replay SET correlation_id='x' WHERE tenant_id='$TB'" "$TA"
expect_value  M1_A_DELETE_B "$CRL_DB" "WITH deleted AS (DELETE FROM public.service_callback_replay WHERE tenant_id='$TB' RETURNING 1) SELECT count(*) FROM deleted" "0" "$TA"

# M2 — Tenant B cannot access Tenant A rows.
expect_value  M2_B_SEES_OWN "$CRL_DB" "SELECT count(*) FROM public.service_callback_replay" "1" "$TB"
expect_value  M2_B_CANNOT_SEE_A "$CRL_DB" "SELECT count(*) FROM public.service_callback_replay WHERE tenant_id='$TA'" "0" "$TB"
expect_value  M2_B_DELETE_A "$CRL_DB" "WITH deleted AS (DELETE FROM public.service_callback_replay WHERE tenant_id='$TA' RETURNING 1) SELECT count(*) FROM deleted" "0" "$TB"

# M3 — missing / empty tenant context is fail-closed.
expect_value  M3_NO_GUC_SELECT "$CRL_DB" "SELECT count(*) FROM public.service_callback_replay" "0"
expect_value  M3_EMPTY_GUC_SELECT "$CRL_DB" "SELECT count(*) FROM public.service_callback_replay" "0" "__EMPTY_GUC__"
expect_deny   M3_NO_GUC_INSERT "$CRL_DB" "INSERT INTO public.service_callback_replay (tenant_id,service_name,jti,nonce,correlation_id,received_at,expires_at) VALUES ('$TA','rls-cert-service','jti-noguc','nonce-noguc','corr-noguc',now(),now()+interval '1 hour')"
expect_value  M3_NO_GUC_DELETE "$CRL_DB" "WITH deleted AS (DELETE FROM public.service_callback_replay WHERE tenant_id='$TA' RETURNING 1) SELECT count(*) FROM deleted" "0"
# UPDATE is default-denied for every role: no UPDATE policy exists by design,
# and the separated topology additionally revokes the privilege from sanad.
expect_deny   M3_OWN_ROW_UPDATE "$CRL_DB" "UPDATE public.service_callback_replay SET correlation_id='x' WHERE tenant_id='$TA'" "$TA"

# M4 — INSERT with a mismatched tenant id is REJECTED.
expect_deny   M4_A_INSERT_AS_B "$CRL_DB" "INSERT INTO public.service_callback_replay (tenant_id,service_name,jti,nonce,correlation_id,received_at,expires_at) VALUES ('$TB','rls-cert-service','jti-mis-1','nonce-mis-1','corr-mis-1',now(),now()+interval '1 hour')" "$TA"
expect_deny   M4_B_INSERT_AS_A "$CRL_DB" "INSERT INTO public.service_callback_replay (tenant_id,service_name,jti,nonce,correlation_id,received_at,expires_at) VALUES ('$TA','rls-cert-service','jti-mis-2','nonce-mis-2','corr-mis-2',now(),now()+interval '1 hour')" "$TB"

# M5 — cross-tenant DELETE is inert; the physical rows are preserved.
expect_value  M5_A_DELETE_B_AGAIN "$CRL_DB" "WITH deleted AS (DELETE FROM public.service_callback_replay WHERE tenant_id='$TB' RETURNING 1) SELECT count(*) FROM deleted" "0" "$TA"
s3_m5_integrity=$(admin postgres "$CRL_DB" "SELECT (SELECT count(*) FROM public.service_callback_replay WHERE tenant_id='$TA')||'|'||(SELECT count(*) FROM public.service_callback_replay WHERE tenant_id='$TB')")
[ "$s3_m5_integrity" = "1|1" ] || die S3 "M5 integrity: rows lost ($s3_m5_integrity)"
record M5_ADMIN_INTEGRITY "1|1" "$s3_m5_integrity" "admin-confirmed rows preserved"

# ============================================================================
# S4 — cleanup-path bounds (mandatory M6)
# ============================================================================
gate S4 "cleanup path: dedicated role, expired-only, no unauthorized access"
# Seed expired + fresh rows for both tenants (admin seeding for determinism).
admin postgres "$CRL_DB" "INSERT INTO public.service_callback_replay (tenant_id,service_name,jti,nonce,correlation_id,received_at,expires_at) VALUES
  ('$TA','rls-cert-service','jti-a-exp','nonce-a-exp','corr-a-exp',now()-interval '2 hours',now()-interval '1 hour'),
  ('$TB','rls-cert-service','jti-b-exp','nonce-b-exp','corr-b-exp',now()-interval '2 hours',now()-interval '1 hour')" >/dev/null || die S4 "expired-row seeding"

# The runtime identity holds a NON-INHERITING membership: it must never see
# the other tenant's expired rows through the cleanup policies.
expect_value  S4_A_SEES_OWN_ONLY "$CRL_DB" "SELECT count(*) FROM public.service_callback_replay" "2" "$TA"
expect_value  S4_A_CANNOT_SEE_B_EXPIRED "$CRL_DB" "SELECT count(*) FROM public.service_callback_replay WHERE expires_at <= CURRENT_TIMESTAMP" "1" "$TA"

# The exact scheduled-sweep sequence the store executes (SET ROLE inside a
# transaction, DELETE expired only, RESET ROLE) — as the runtime identity.
# The three SELECTs report: visible-expired-before / deleted-count / visible-after.
S4_SWEEP_SQL="BEGIN; SET ROLE sanad_callback_replay_cleanup; SELECT count(*) FROM public.service_callback_replay; WITH deleted AS (DELETE FROM public.service_callback_replay WHERE expires_at <= CURRENT_TIMESTAMP RETURNING 1) SELECT count(*) FROM deleted; SELECT count(*) FROM public.service_callback_replay; RESET ROLE; COMMIT;"
runtime_capture "$CRL_DB" "$S4_SWEEP_SQL"
if [ $runtime_rc -eq 0 ]; then
  sweep_result=$(printf '%s\n' "$runtime_out" | tr '\n' ' ' | sed 's/  */ /g' | sed 's/^ //;s/ $//')
  case "$sweep_result" in
    "2 2 0") record S4_SWEEP "2 2 0" "$sweep_result" "cleanup role saw 2 expired, deleted 2, saw 0 after" ;;
    *) record S4_SWEEP "2 2 0" "FAIL(out='$sweep_result')" "$runtime_err"; fail ;;
  esac
else
  record S4_SWEEP "2 2 0" "FAIL(rc=$runtime_rc,$runtime_sqlstate)" "$runtime_err"; fail
fi

# Only non-expired rows remain; both tenants' fresh rows are preserved.
s4_remaining=$(admin postgres "$CRL_DB" "SELECT count(*)||'|'||count(*) FILTER (WHERE expires_at > CURRENT_TIMESTAMP) FROM public.service_callback_replay")
[ "$s4_remaining" = "2|2" ] || die S4 "post-sweep inventory: $s4_remaining (expected 2|2)"
record S4_ADMIN_POST_SWEEP "2|2" "$s4_remaining" "admin-confirmed only fresh rows remain"

# The cleanup role can never INSERT (privilege denial) — beyond policies.
expect_deny   S4_CLEANUP_INSERT "$CRL_DB" "BEGIN; SET ROLE sanad_callback_replay_cleanup; INSERT INTO public.service_callback_replay (tenant_id,service_name,jti,nonce,correlation_id,received_at,expires_at) VALUES ('$TA','rls-cert-service','jti-cln-x','nonce-cln-x','corr-cln-x',now(),now()+interval '1 hour'); COMMIT;"
# After the sweep no expired rows remain: the cleanup role sees nothing.
expect_value  S4_CLEANUP_SEES_NOTHING_FRESH "$CRL_DB" "BEGIN; SET ROLE sanad_callback_replay_cleanup; SELECT count(*) FROM public.service_callback_replay; RESET ROLE; COMMIT;" "0"
expect_value  S4_CLEANUP_DELETE_FRESH "$CRL_DB" "BEGIN; SET ROLE sanad_callback_replay_cleanup; WITH deleted AS (DELETE FROM public.service_callback_replay WHERE expires_at > CURRENT_TIMESTAMP RETURNING 1) SELECT count(*) FROM deleted; RESET ROLE; COMMIT;" "0"
# Non-members (the migrator) cannot assume the cleanup identity.
mig_probe_rc=0
mig_probe=$(env -u PGOPTIONS PGPASSWORD="$CRL_MIG_PW" psql -X -h "$CRL_PGHOST" -p "$CRL_PGPORT" -U sanad_migrator -d "$CRL_DB" \
  -v ON_ERROR_STOP=1 -v VERBOSITY=verbose -qAtc "BEGIN; SET ROLE sanad_callback_replay_cleanup; COMMIT;" 2>"$ART/last-mig-err.txt") || mig_probe_rc=$?
mig_sqlstate=$(sed -nE 's/^.*ERROR:[[:space:]]+([0-9A-Z]{5}):.*$/\1/p' "$ART/last-mig-err.txt" | head -1)
if [ "$mig_probe_rc" -ne 0 ] && [ "$mig_sqlstate" = "42501" ]; then
  record S4_MIGRATOR_SETROLE_DENIED "DENY(42501)" "DENY($mig_sqlstate)" "migrator is not a member of the cleanup role"
else
  record S4_MIGRATOR_SETROLE_DENIED "DENY(42501)" "FAIL(rc=$mig_probe_rc,$mig_sqlstate)" "$mig_probe"; fail
fi
# The owner (sanad_migrator, FORCE-bound, NOBYPASSRLS) has no tenant context:
# it can neither see nor write rows (proves FORCE + no BYPASSRLS).
mig_read=$(env -u PGOPTIONS PGPASSWORD="$CRL_MIG_PW" psql -X -h "$CRL_PGHOST" -p "$CRL_PGPORT" -U sanad_migrator -d "$CRL_DB" \
  -v ON_ERROR_STOP=1 -qAtc "SELECT count(*) FROM public.service_callback_replay" 2>/dev/null)
[ "$mig_read" = "0" ] || die S4 "owner (FORCE-bound) read $mig_read rows without tenant context"
record S4_OWNER_FORCE_BOUND "0" "$mig_read" "owner sees zero rows without tenant context (FORCE RLS)"
# The owner HOLDS the UPDATE privilege, but no UPDATE policy exists: with
# no applicable UPDATE policy PostgreSQL matches no rows — the UPDATE has
# zero effect (verified on PG 17.11: silent UPDATE 0, no error, row value
# unchanged). UPDATE is therefore impossible for every role, including the
# most privileged non-bypass identity (fail-closed for every role).
mig_upd=$(env -u PGOPTIONS PGOPTIONS="-c app.tenant_id=$TA" PGPASSWORD="$CRL_MIG_PW" psql -X -h "$CRL_PGHOST" -p "$CRL_PGPORT" -U sanad_migrator -d "$CRL_DB" \
  -v ON_ERROR_STOP=1 -qAtc "WITH upd AS (UPDATE public.service_callback_replay SET correlation_id='x' WHERE tenant_id='$TA' RETURNING 1) SELECT count(*) FROM upd" 2>"$ART/last-mig-upd-err.txt") || true
[ "$mig_upd" = "0" ] || { record S4_OWNER_UPDATE_ZERO_EFFECT "0" "$mig_upd" "owner UPDATE was not inert"; fail; }
record S4_OWNER_UPDATE_ZERO_EFFECT "0" "$mig_upd" "no UPDATE policy: owner UPDATE affects zero rows"
s4_value_unchanged=$(admin postgres "$CRL_DB" "SELECT count(*) FROM public.service_callback_replay WHERE tenant_id='$TA' AND correlation_id='corr-a-1'")
[ "$s4_value_unchanged" = "1" ] || { record S4_OWNER_UPDATE_VALUE_UNCHANGED "1" "$s4_value_unchanged" "owner UPDATE modified a row"; fail; }
record S4_OWNER_UPDATE_VALUE_UNCHANGED "1" "$s4_value_unchanged" "admin-confirmed row values unchanged after owner UPDATE attempt"
[ $FAILURES -eq 0 ] || die S4 "cleanup-path matrix failed"
gate S4 "PASS: non-inheriting runtime membership; sweep deleted exactly the expired rows; cleanup role cannot INSERT/read fresh rows; migrator cannot SET ROLE; owner FORCE-bound"

# ============================================================================
# S5 — callback regression (mandatory M7)
# ============================================================================
gate S5 "callback consume path works under RLS; replay uniqueness intact"
expect_pass S5_CONSUME_INSERT "$CRL_DB" "INSERT INTO public.service_callback_replay (tenant_id,service_name,jti,nonce,correlation_id,received_at,expires_at) VALUES ('$TA','rls-cert-service','jti-reg-1','nonce-reg-1','corr-reg-1',now(),now()+interval '1 hour')" "$TA"
expect_deny   S5_REPLAY_SAME_TENANT "$CRL_DB" "INSERT INTO public.service_callback_replay (tenant_id,service_name,jti,nonce,correlation_id,received_at,expires_at) VALUES ('$TA','rls-cert-service','jti-reg-1','nonce-reg-1','corr-reg-1',now(),now()+interval '1 hour')" "$TA" "23505"
expect_deny   S5_REPLAY_CROSS_TENANT "$CRL_DB" "INSERT INTO public.service_callback_replay (tenant_id,service_name,jti,nonce,correlation_id,received_at,expires_at) VALUES ('$TB','rls-cert-service','jti-reg-1','nonce-reg-1','corr-reg-1',now(),now()+interval '1 hour')" "$TB" "23505"
expect_deny   S5_NONCE_UNIQUENESS "$CRL_DB" "INSERT INTO public.service_callback_replay (tenant_id,service_name,jti,nonce,correlation_id,received_at,expires_at) VALUES ('$TA','rls-cert-service','jti-reg-2','nonce-reg-1','corr-reg-2',now(),now()+interval '1 hour')" "$TA" "23505"
# FK cascade documentation probe: a SUPERUSER tenant deletion still cascades
# the tenant's replay rows (referential integrity is not blocked by RLS for
# the bypass-capable actor; restricted identities must bind the tenant GUC
# first — the ModuleResetService contract).
cascade_probe=$(admin postgres "$CRL_DB" "DELETE FROM public.tenants WHERE id='$TB'; SELECT count(*) FROM public.service_callback_replay WHERE tenant_id='$TB';")
[ "$cascade_probe" = "DELETE 1
0" ] || die S5 "superuser tenant-deletion cascade probe: $cascade_probe"
record S5_SUPERUSER_CASCADE "DELETE 1|0" "$(printf '%s' "$cascade_probe" | tr '\n' ' ')" "RI cascade under RLS (superuser actor)"
[ $FAILURES -eq 0 ] || die S5 "callback regression matrix failed"
gate S5 "PASS: consume INSERT ok under tenant policy; jti/nonce uniqueness (23505) intact in-tenant and cross-tenant; superuser cascade documented"

# ============================================================================
# S6 — verdict
# ============================================================================
{
  echo ""
  echo "## service_callback_replay P0 RLS closure matrix — $(date -u +'%Y-%m-%dT%H:%M:%SZ')"
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
if [ "$CRL_KEEP_DB" != "1" ]; then
  admin postgres postgres "DROP DATABASE $CRL_DB" >/dev/null || die S6 "certification database cleanup failed"
  printf 'S6_DATABASE_CLEANUP=COMPLETED\nS6_ROLE_CLEANUP=NOT_PERFORMED\n' >> "$SUMMARY"
  echo "::warning::S6: database removed; certification roles remain and require isolated-cluster disposal" >&2
else
  printf 'S6_DATABASE_CLEANUP=SKIPPED_BY_CRL_KEEP_DB\nS6_ROLE_CLEANUP=NOT_PERFORMED\n' >> "$SUMMARY"
fi
echo "=================================================================="
echo "CALLBACK-REPLAY RLS CLOSURE PROBES: PASSED ($(wc -l < "$TSV") probes)"
echo "Certification is NOT a production authorization; P0_SECURITY_HOLD stays ACTIVE."
echo "Artifacts: $ART (matrix.tsv, S1-migrate.log, S1-validate.log, summary.md)"
echo "=================================================================="
