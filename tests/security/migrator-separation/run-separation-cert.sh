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
#   SEP_KEEP_DB=1 to keep the certification database for inspection.
# ============================================================================
set -uo pipefail
export LC_ALL=C.UTF-8

SEP_PGHOST="${SEP_PGHOST:-127.0.0.1}"
SEP_PGPORT="${SEP_PGPORT:-55432}"
SEP_FLYWAY="${SEP_FLYWAY:-/tmp/flyway/flyway}"
SEP_ADMIN_PSQL="${SEP_ADMIN_PSQL:-psql -h ${SEP_PGHOST} -p ${SEP_PGPORT} -U postgres}"
SEP_DB="${SEP_DB:-separation_cert}"
SEP_KEEP_DB="${SEP_KEEP_DB:-0}"
# The database name is interpolated into administrative SQL statements.
# Reject arbitrary SQL fragments and non-certification database names.
if [[ ! "$SEP_DB" =~ ^separation_cert(_[a-z0-9_]+)?$ ]] || [ "${#SEP_DB}" -gt 63 ]; then
  echo "::error::Unsafe SEP_DB: expected separation_cert or separation_cert_<suffix>" >&2
  exit 1
fi
if [[ "$SEP_KEEP_DB" != "0" && "$SEP_KEEP_DB" != "1" ]]; then
  echo "::error::SEP_KEEP_DB must be 0 or 1" >&2
  exit 1
fi
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# Serialize invocations of this checkout before touching cluster-wide roles.
# Lock the existing script inode (read-only) rather than a writable /tmp path.
command -v flock >/dev/null 2>&1 || { echo "::error::flock is required" >&2; exit 1; }
exec 9< "${BASH_SOURCE[0]}" || exit 1
flock -n 9 || { echo "::error::Another certification is running from this checkout" >&2; exit 1; }
SEP_REPO="${SEP_REPO:-$(cd "$SCRIPT_DIR/../../.." && pwd)}"
MIG_LOC="$SEP_REPO/apps/sanad-platform/src/main/resources/db/migration"
VEN_LOC="$SEP_REPO/apps/sanad-platform/src/main/resources/db/vendor/postgresql"
LOCS="filesystem:$MIG_LOC,filesystem:$VEN_LOC"
EXPECTED_HEAD="20261103.3"
ART="${SEP_ARTIFACTS:-/tmp/separation-cert-artifacts}"
# Evidence must be isolated to this run; never follow an existing symlink
# or overwrite previous certification evidence on a reused host.
umask 077
[ ! -L "$ART" ] || { echo "::error::Evidence directory is a symlink" >&2; exit 1; }
if [ -e "$ART" ]; then
  [ -d "$ART" ] && [ -z "$(ls -A "$ART")" ] || {
    echo "::error::Evidence directory already contains data; choose a fresh SEP_ARTIFACTS" >&2
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
# On any failure preserve the isolated database and all evidence for forensics.
# Never attempt automatic role/database cleanup: it can erase the root cause.
on_exit() {
  local status=$?
  trap - EXIT
  # S1 can exit before its normal unset; clear Flyway credentials on all exits.
  unset FLYWAY_PASSWORD
  if [ "$status" -ne 0 ]; then
    # A failure can occur before provisioning; do not claim resources exist.
    printf 'EXIT_STATUS=%s\nAUTO_CLEANUP_ON_FAILURE=SKIPPED\nRESOURCE_EXISTENCE=UNVERIFIED\n' "$status" >> "$SUMMARY"
    printf '::error::Separation certification exited %s; no automatic cleanup attempted; inspect resources before recovery\n' "$status" >&2
  fi
}
trap on_exit EXIT

# --- connection helpers -----------------------------------------------------
admin()   { $SEP_ADMIN_PSQL -X -v ON_ERROR_STOP=1 -d "$2" -Atc "$3"; }
# Never expose SQL containing ephemeral passwords in the psql -c process argv.
admin_secret() { printf '%s\n' "$1" | $SEP_ADMIN_PSQL -X -v ON_ERROR_STOP=1 -d postgres -At >/dev/null; }
runtime_out=""; runtime_rc=0; runtime_err=""; runtime_sqlstate=""
runtime_capture() { # runtime_capture <db> <sql> [tenant_guc]
  local guc_args=()
  if [ $# -ge 3 ] && [ "$3" = "__EMPTY_GUC__" ]; then
    # Explicit empty GUC is distinct from an unset GUC; exercise both paths.
    guc_args=(PGOPTIONS="-c app.tenant_id=")
  elif [ $# -ge 3 ] && [ -n "$3" ]; then
    guc_args=(PGOPTIONS="-c app.tenant_id=$3")
  fi
  # Never inherit an ambient PGOPTIONS tenant context into an unset-GUC probe.
  # Explicit tenant probes supply their own controlled PGOPTIONS value.
  runtime_out=$(env -u PGOPTIONS -u PGHOSTADDR -u PGSERVICE -u PGSERVICEFILE PGPASSWORD="$SEP_RUN_PW" "${guc_args[@]}" psql -X -h "$SEP_PGHOST" -p "$SEP_PGPORT" -U sanad -d "$1" \
       -v ON_ERROR_STOP=1 -v VERBOSITY=verbose -qAtc "$2" 2>"$ART/last-err.txt")
  runtime_rc=$?
  runtime_err=$(cat "$ART/last-err.txt")
  # VERBOSITY=verbose emits "ERROR:  42501: ..."; whitespace varies.
  # Extract only an explicit five-character SQLSTATE attached to ERROR.
  runtime_sqlstate=$(printf '%s\n' "$runtime_err" | sed -nE 's/^.*ERROR:[[:space:]]+([0-9A-Z]{5}):.*$/\1/p' | head -1)
  [ -z "$runtime_sqlstate" ] && runtime_sqlstate=$(printf '%s\n' "$runtime_err" | sed -nE 's/^.*SQLSTATE:[[:space:]]*([0-9A-Z]{5})([^0-9A-Z]|$).*$/\1/p' | head -1)
  # Never infer SQLSTATE from localized/free-form error messages.
  # A negative security probe passes only with a real PostgreSQL SQLSTATE.
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
  if [ "$runtime_rc" -eq 0 ] && { [ "$runtime_out" = "UPDATE 0" ] || [ "$runtime_out" = "DELETE 0" ]; }; then
    record "$1" "ZERO_EFFECT" "$runtime_out" ok
  else record "$1" "ZERO_EFFECT" "FAIL(rc=$runtime_rc out='$runtime_out')" "$runtime_err"; fail; fi
}

# ============================================================================
# S0 — provision roles + separated database (admin actor)
# ============================================================================
: > "$SUMMARY"
gate S0 "provision sanad_migrator + restricted sanad + database $SEP_DB (separated topology)"
# This script changes cluster-wide roles and passwords. Refuse execution unless
# explicitly authorized for an isolated, disposable PostgreSQL cluster.
[ "${SEP_ISOLATED_CLUSTER:-0}" = "1" ] || die S0 "SEP_ISOLATED_CLUSTER=1 required before cluster-wide role changes"
case "$SEP_PGHOST" in
  127.0.0.1|localhost|::1) ;;
  *) die S0 "refusing role mutations on non-loopback SEP_PGHOST=$SEP_PGHOST" ;;
esac
admin postgres postgres "SELECT 1" >/dev/null 2>&1 || die S0 "admin psql unavailable: $SEP_ADMIN_PSQL"
# A disposable certification cluster must have no other user databases.
# Check this BEFORE touching existing roles, even when SEP_DB is absent.
other_dbs=$(admin postgres postgres "SELECT count(*) FROM pg_database WHERE datistemplate=false AND datname <> 'postgres'")
[ "$other_dbs" = "0" ] || die S0 "cluster contains $other_dbs non-system database(s); refusing role mutations"
# Never destroy a pre-existing database, even on a mistakenly targeted host.
# Certification requires a fresh disposable database provisioned by this run.
if [ "$(admin postgres postgres "SELECT count(*) FROM pg_database WHERE datname='$SEP_DB'")" != "0" ]; then
  die S0 "database $SEP_DB already exists; refusing destructive replacement"
fi
# An existing runtime role may belong to a different application database or
# carry grants not visible from the database-count preflight. Never reset it.
existing_roles=$(admin postgres postgres "SELECT count(*) FROM pg_roles WHERE rolname IN ('sanad','sanad_migrator')")
[ "$existing_roles" = "0" ] || die S0 "existing sanad or sanad_migrator role detected; refusing shared cluster identity changes"
# Passwords are embedded in administrative SQL; accept only fixed-length hex.
: "${SEP_MIG_PW:?SEP_MIG_PW must be supplied for disposable migrator provisioning}"
[[ "$SEP_MIG_PW" =~ ^[0-9a-fA-F]{48}$ ]] || die S0 "SEP_MIG_PW must be 48 hex characters"
SEP_RUN_PW="${SEP_RUN_PW:-$(openssl rand -hex 24)}"
[[ "$SEP_RUN_PW" =~ ^[0-9a-fA-F]{48}$ ]] || die S0 "SEP_RUN_PW must be 48 hex characters"
admin postgres postgres "DO \$\$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='sanad_migrator') THEN
    CREATE ROLE sanad_migrator LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='sanad') THEN
    CREATE ROLE sanad LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS;
  END IF;
END \$\$;" >/dev/null || die S0 "role provisioning failed"
admin_secret "ALTER ROLE sanad_migrator PASSWORD '$SEP_MIG_PW';" || die S0 "migrator password"
admin postgres postgres "ALTER ROLE sanad_migrator NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS" >/dev/null || die S0 "migrator contract"
admin postgres postgres "ALTER ROLE sanad NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS" >/dev/null || die S0 "runtime contract"
# Ephemeral per-run runtime password (hex, never logged) — required on hosts
# using password auth for TCP connections (GitHub runners use scram-sha-256;
# trust-auth local clusters simply ignore it).

admin_secret "ALTER ROLE sanad PASSWORD '$SEP_RUN_PW';" || die S0 "runtime password"
# Supply the runtime password only to runtime psql subprocesses.
# Admin may connect via Unix socket while runtime uses TCP. Require both
# connections to reach the same PostgreSQL postmaster before creating a DB.
admin_instance=$(admin postgres postgres "SELECT pg_postmaster_start_time()::text")
runtime_instance=$(env -u PGOPTIONS -u PGHOSTADDR -u PGSERVICE -u PGSERVICEFILE PGPASSWORD="$SEP_RUN_PW" psql -X -h "$SEP_PGHOST" -p "$SEP_PGPORT" -U sanad -d postgres -At -v ON_ERROR_STOP=1 -c "SELECT pg_postmaster_start_time()::text") || die S0 "runtime TCP identity check failed"
[ -n "$admin_instance" ] && [ "$admin_instance" = "$runtime_instance" ] || die S0 "admin and runtime connections target different PostgreSQL postmasters"
admin_port=$(admin postgres postgres "SHOW port")
[ "$admin_port" = "$SEP_PGPORT" ] || die S0 "admin cluster port $admin_port differs from runtime port $SEP_PGPORT"
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
# Scope the secret to each Flyway child; never export it in the parent shell.
env FLYWAY_PASSWORD="$SEP_MIG_PW" "$SEP_FLYWAY" -url="jdbc:postgresql://$SEP_PGHOST:$SEP_PGPORT/$SEP_DB" \
  -user=sanad_migrator \
  -locations="$LOCS" migrate > "$ART/S1-migrate.log" 2>&1 || die S1 "migrate failed (see $ART/S1-migrate.log)"
env FLYWAY_PASSWORD="$SEP_MIG_PW" "$SEP_FLYWAY" -url="jdbc:postgresql://$SEP_PGHOST:$SEP_PGPORT/$SEP_DB" \
  -user=sanad_migrator \
  -locations="$LOCS" validate > "$ART/S1-validate.log" 2>&1 || die S1 "validate failed"
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
expect_pass S2_INSERT "$SEP_DB" "INSERT INTO public.tenants (id,name,subdomain,status,created_at,updated_at) VALUES ('$T1','Sep Cert Tenant','sepcert','ACTIVE',now(),now())"
expect_value S2_UPDATE "$SEP_DB" "WITH changed AS (UPDATE public.tenants SET name='Sep Cert Tenant 2' WHERE id='$T1' RETURNING 1) SELECT count(*) FROM changed" "1"
expect_value S2_DELETE "$SEP_DB" "WITH deleted AS (DELETE FROM public.tenants WHERE id='$T1' RETURNING 1) SELECT count(*) FROM deleted" "1"
# Confirm the row is absent in storage, not merely hidden by runtime policies.
s2_remaining=$(admin postgres "$SEP_DB" "SELECT count(*) FROM public.tenants WHERE id='$T1'") || die S2 "admin tenant deletion inventory failed"
if [ "$s2_remaining" = "0" ]; then
  record S2_ADMIN_TENANT_DELETION "0" "$s2_remaining" "admin-confirmed tenant removal"
else
  record S2_ADMIN_TENANT_DELETION "0" "$s2_remaining" "tenant deletion integrity mismatch"
  fail
fi
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
expect_value S4_A_SEES_A "$SEP_DB" "SELECT count(*) FROM public.users WHERE id='$UA'" "1" "$TA"
expect_value S4_A_CANNOT_SEE_B "$SEP_DB" "SELECT count(*) FROM public.users WHERE id='$UB'" "0" "$TA"
expect_value S4_B_SEES_B "$SEP_DB" "SELECT count(*) FROM public.users WHERE id='$UB'" "1" "$TB"
expect_value S4_B_CANNOT_SEE_A "$SEP_DB" "SELECT count(*) FROM public.users WHERE id='$UA'" "0" "$TB"
expect_value S4_A_SEES_TOTAL "$SEP_DB" "SELECT count(*) FROM public.users" "1" "$TA"
expect_value S4_B_SEES_TOTAL "$SEP_DB" "SELECT count(*) FROM public.users" "1" "$TB"
expect_value S4_CROSS_UPDATE "$SEP_DB" "WITH upd AS (UPDATE public.users SET display_name='x' WHERE id='$UB' RETURNING 1) SELECT count(*) FROM upd" "0" "$TA"
# A/B isolation must also reject destructive cross-tenant operations.
expect_value S4_CROSS_DELETE "$SEP_DB" "WITH deleted AS (DELETE FROM public.users WHERE id='$UB' RETURNING 1) SELECT count(*) FROM deleted" "0" "$TA"
# Verify both directions, and verify the protected rows still exist afterward.
expect_value S4_REVERSE_CROSS_UPDATE "$SEP_DB" "WITH upd AS (UPDATE public.users SET display_name='x' WHERE id='$UA' RETURNING 1) SELECT count(*) FROM upd" "0" "$TB"
expect_value S4_REVERSE_CROSS_DELETE "$SEP_DB" "WITH deleted AS (DELETE FROM public.users WHERE id='$UA' RETURNING 1) SELECT count(*) FROM deleted" "0" "$TB"
expect_value S4_A_ROW_PRESERVED "$SEP_DB" "SELECT count(*) FROM public.users WHERE id='$UA'" "1" "$TA"
# RLS WITH CHECK must reject changing an owned row into another tenant.
# A visible row cannot be reassigned across tenant boundaries by UPDATE.
expect_deny S4_A_TENANT_REASSIGN "$SEP_DB" "UPDATE public.users SET tenant_id='$TB' WHERE id='$UA'" "$TA"
expect_deny S4_B_TENANT_REASSIGN "$SEP_DB" "UPDATE public.users SET tenant_id='$TA' WHERE id='$UB'" "$TB"
expect_value S4_B_ROW_PRESERVED "$SEP_DB" "SELECT count(*) FROM public.users WHERE id='$UB'" "1" "$TB"
expect_value S4_A_ROW_PRESERVED_AFTER_REASSIGN "$SEP_DB" "SELECT count(*) FROM public.users WHERE id='$UA' AND tenant_id='$TA'" "1" "$TA"
expect_value S4_B_ROW_PRESERVED_AFTER_REASSIGN "$SEP_DB" "SELECT count(*) FROM public.users WHERE id='$UB' AND tenant_id='$TB'" "1" "$TB"
expect_deny S4_CROSS_INSERT "$SEP_DB" "INSERT INTO public.users (id,tenant_id,email,status,created_at,updated_at) VALUES ('66666666-6666-6666-6666-666666666666','$TB','c@sepcert.test','ACTIVE',now(),now())" "$TA"
# Cross-tenant INSERT must fail in both directions, and must leave no row.
expect_deny S4_REVERSE_CROSS_INSERT "$SEP_DB" "INSERT INTO public.users (id,tenant_id,email,status,created_at,updated_at) VALUES ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb','$TA','reverse@sepcert.test','ACTIVE',now(),now())" "$TB"
expect_value S4_CROSS_INSERT_ABSENT "$SEP_DB" "SELECT count(*) FROM public.users WHERE id='66666666-6666-6666-6666-666666666666'" "0" "$TB"
expect_value S4_REVERSE_CROSS_INSERT_ABSENT "$SEP_DB" "SELECT count(*) FROM public.users WHERE id='bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'" "0" "$TA"
expect_value S4_NO_GUC "$SEP_DB" "SELECT count(*) FROM public.users" "0"
expect_value S4_EMPTY_GUC "$SEP_DB" "SELECT count(*) FROM public.users" "0" "__EMPTY_GUC__"
# Fail-closed isolation must cover writes, not only empty SELECT results.
# Use a CTE so the result reports affected-row counts without relying on psql tags.
expect_value S4_NO_GUC_UPDATE "$SEP_DB" "WITH changed AS (UPDATE public.users SET display_name='no-guc' WHERE id='$UA' RETURNING 1) SELECT count(*) FROM changed" "0"
expect_value S4_NO_GUC_DELETE "$SEP_DB" "WITH deleted AS (DELETE FROM public.users WHERE id='$UA' RETURNING 1) SELECT count(*) FROM deleted" "0"
expect_value S4_EMPTY_GUC_UPDATE "$SEP_DB" "WITH changed AS (UPDATE public.users SET display_name='empty-guc' WHERE id='$UB' RETURNING 1) SELECT count(*) FROM changed" "0" "__EMPTY_GUC__"
expect_value S4_EMPTY_GUC_DELETE "$SEP_DB" "WITH deleted AS (DELETE FROM public.users WHERE id='$UB' RETURNING 1) SELECT count(*) FROM deleted" "0" "__EMPTY_GUC__"
expect_value S4_A_PRESERVED_AFTER_NO_GUC "$SEP_DB" "SELECT count(*) FROM public.users WHERE id='$UA'" "1" "$TA"
expect_value S4_B_PRESERVED_AFTER_NO_GUC "$SEP_DB" "SELECT count(*) FROM public.users WHERE id='$UB'" "1" "$TB"
# Independently inspect the physical rows as admin: runtime RLS visibility
# alone cannot prove that no hidden cross-tenant row was inserted or reassigned.
s4_integrity=$(admin postgres "$SEP_DB" "SELECT
  (SELECT count(*) FROM public.users WHERE id='$UA' AND tenant_id='$TA')||'|'||
  (SELECT count(*) FROM public.users WHERE id='$UB' AND tenant_id='$TB')||'|'||
  (SELECT count(*) FROM public.users WHERE id IN ('66666666-6666-6666-6666-666666666666','bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'))") || die S4 "admin integrity inventory failed"
[ "$s4_integrity" = "1|1|0" ] || die S4 "admin row-integrity mismatch: $s4_integrity (expected 1|1|0)"
record S4_ADMIN_ROW_INTEGRITY "1|1|0" "$s4_integrity" "admin-confirmed ownership and no injected rows"
[ $FAILURES -eq 0 ] || die S4 "tenant isolation matrix failed"
gate S4 "PASS: Tenant A sees 1 (own) / 0 cross-tenant; cross-tenant INSERT 42501; no-GUC and empty-GUC fail-closed (0 rows)"

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
expect_value S5_REFRESH_UPD "$SEP_DB" "WITH changed AS (UPDATE public.refresh_tokens SET status='REVOKED' WHERE id='$RT' RETURNING 1) SELECT count(*) FROM changed" "1" "$TA"
expect_value S5_REFRESH_DEL "$SEP_DB" "WITH deleted AS (DELETE FROM public.refresh_tokens WHERE id='$RT' RETURNING 1) SELECT count(*) FROM deleted" "1" "$TA"
expect_pass S5_RESET_INS "$SEP_DB" "INSERT INTO public.password_reset_tokens (id,tenant_id,user_id,token_hash,expires_at,created_at) VALUES ('$PRT','$TA','$UC','sepcert-hash-2',now()+interval '1 hour',now())" "$TA"
expect_value S5_RESET_DEL "$SEP_DB" "WITH deleted AS (DELETE FROM public.password_reset_tokens WHERE id='$PRT' RETURNING 1) SELECT count(*) FROM deleted" "1" "$TA"
# Independently verify token deletion at the storage layer. A tenant-scoped
# SELECT returning zero could otherwise be a false positive caused by RLS.
s5_deleted_tokens=$(admin postgres "$SEP_DB" "SELECT
  (SELECT count(*) FROM public.refresh_tokens WHERE id='$RT')||'|'||
  (SELECT count(*) FROM public.password_reset_tokens WHERE id='$PRT')") || die S5 "admin token deletion inventory failed"
if [ "$s5_deleted_tokens" = "0|0" ]; then
  record S5_ADMIN_TOKEN_DELETION "0|0" "$s5_deleted_tokens" "admin-confirmed refresh/reset tokens removed"
else
  record S5_ADMIN_TOKEN_DELETION "0|0" "$s5_deleted_tokens" "token deletion integrity mismatch"
  fail
fi
expect_pass S5_PREAUTH_RESOLVE "$SEP_DB" "SELECT count(*) FROM public.g1_resolve_login_tenants('a@sepcert.test','')"
expect_pass S5_PREAUTH_INDEX "$SEP_DB" "SELECT count(*) FROM public.login_tenant_index"
[ $FAILURES -eq 0 ] || die S5 "auth plane matrix failed"
gate S5 "PASS: user creation, refresh/reset token lifecycle, trigger-maintained pre-auth index, pre-auth resolver (no GUC)"

# ============================================================================
# S6 — subscriptions (append-only ledger) + provisioning as sanad
# ============================================================================
gate S6 "subscription_commands append-only + provisioning as sanad"
SC=aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa
SUB=dddddddd-dddd-dddd-dddd-dddddddddddd
PLAN=$(admin postgres "$SEP_DB" "SELECT p.id FROM public.saas_plans p WHERE EXISTS (SELECT 1 FROM public.plan_versions v WHERE v.plan_id=p.id) ORDER BY p.id LIMIT 1") || die S6 "plan fixture lookup failed"
PLANVER=$(admin postgres "$SEP_DB" "SELECT id FROM public.plan_versions WHERE plan_id='$PLAN' LIMIT 1") || die S6 "plan version lookup failed"
[ -n "$PLAN" ] || die S6 "no SaaS plan available for subscription probe"
[ -n "$PLANVER" ] || die S6 "no plan version available for subscription probe"
# F-01 closure evidence: the runtime role itself creates the subscription row
expect_pass S6_TENANT_SUB_INS "$SEP_DB" "INSERT INTO public.tenant_subscriptions (id,tenant_id,plan_id,plan_version_id,status,billing_cycle,seat_quantity,credit_balance_minor,started_at,current_period_start,current_period_end,cancel_at_period_end,created_at,updated_at) VALUES ('$SUB','$TA','$PLAN','$PLANVER','TRIALING','MONTHLY',1,0,now(),now(),now()+interval '30 days',false,now(),now())" "$TA"
expect_pass S6_SUB_INS "$SEP_DB" "INSERT INTO public.subscription_commands (id,subscription_id,tenant_id,command,from_status,to_status,reason,created_at) VALUES ('$SC','$SUB','$TA','CHANGE_PLAN','TRIALING','ACTIVE','separation-cert probe',now())" "$TA"
expect_value S6_SUB_PRESENT "$SEP_DB" "SELECT count(*) FROM public.subscription_commands WHERE id='$SC' AND tenant_id='$TA'" "1" "$TA"
expect_deny  S6_SUB_UPD "$SEP_DB" "UPDATE public.subscription_commands SET reason='FORCED' WHERE id='$SC'" "$TA"
expect_deny  S6_SUB_DEL "$SEP_DB" "DELETE FROM public.subscription_commands WHERE id='$SC'" "$TA"
ledger_rows=$(admin postgres "$SEP_DB" "SELECT count(*)||'|'||count(*) FILTER (WHERE subscription_id='$SUB' AND tenant_id='$TA' AND command='CHANGE_PLAN' AND from_status='TRIALING' AND to_status='ACTIVE' AND reason='separation-cert probe') FROM public.subscription_commands WHERE id='$SC'") || die S6 "ledger verification query failed"
if [ "$ledger_rows" = "1|1" ]; then
  record S6_ADMIN_LEDGER_INTEGRITY "1|1" "$ledger_rows" "admin-confirmed original subscription command preserved"
else
  record S6_ADMIN_LEDGER_INTEGRITY "1|1" "$ledger_rows" "subscription command missing or modified"
  fail
fi
[ $FAILURES -eq 0 ] || die S6 "subscription plane matrix failed"
gate S6 "PASS: subscription_commands INSERT ok; UPDATE/DELETE DENIED (append-only enforced for runtime)"

# ============================================================================
# S7 — ownership + privilege snapshot (catalog-level evidence)
# ============================================================================
gate S7 "catalog snapshot: ownership + effective ACL + FORCE RLS"
own_stats=$(admin postgres "$SEP_DB" "SELECT pg_get_userbyid(c.relowner)||':'||count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind IN ('r','p','S','v','m') GROUP BY pg_get_userbyid(c.relowner) ORDER BY count(*) DESC" | tr '\n' ' ')
echo "OWNERSHIP: $own_stats" >> "$SUMMARY"
runtime_owned=$(admin postgres "$SEP_DB" "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind IN ('r','p','S','v','m') AND pg_get_userbyid(c.relowner)='sanad'") || die S7 "runtime ownership inventory failed"
[ "$runtime_owned" = "0" ] || die S7 "runtime role owns $runtime_owned public objects: $own_stats"
hist_owner=$(admin postgres "$SEP_DB" "SELECT pg_get_userbyid(relowner) FROM pg_class WHERE oid='public.flyway_schema_history'::regclass")
[ "$hist_owner" = "sanad_migrator" ] || die S7 "flyway_schema_history owner=$hist_owner"
runtime_create=$(admin postgres "$SEP_DB" "SELECT has_schema_privilege('sanad','public','CREATE')")
[ "$runtime_create" = "f" ] || die S7 "sanad still holds CREATE on public"
public_create=$(admin postgres "$SEP_DB" "SELECT has_schema_privilege('public','public','CREATE')") || die S7 "PUBLIC CREATE privilege check failed"
[ "$public_create" = "f" ] || die S7 "PUBLIC has CREATE privilege on public schema"
rls_coverage=$(admin postgres "$SEP_DB" "SELECT count(*)||'|'||count(*) FILTER (WHERE c.relrowsecurity AND c.relforcerowsecurity) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind IN ('r','p') AND c.relname IN ('users','refresh_tokens','password_reset_tokens','provisioning_jobs','provisioning_job_steps','subscription_commands')")
[ "$rls_coverage" = "6|6" ] || die S7 "expected six existing FORCE RLS tables; observed $rls_coverage"
seq_ok=$(admin postgres "$SEP_DB" "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind='S' AND NOT (has_sequence_privilege('sanad',quote_ident(n.nspname)||'.'||quote_ident(c.relname),'USAGE') AND has_sequence_privilege('sanad',quote_ident(n.nspname)||'.'||quote_ident(c.relname),'SELECT'))")
[ "$seq_ok" = "0" ] || die S7 "$seq_ok sequence(s) missing USAGE/SELECT for sanad"
record S7_CATALOG PASS PASS "owners=[$own_stats] history=$hist_owner sanadCREATE=$runtime_create forceRLS=6/6 seqOK"
gate S7 "PASS: $own_stats; history owner=$hist_owner; runtime CREATE=false; FORCE RLS 6/6; sequences OK"

# ============================================================================
# S8 — verdict
# ============================================================================
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
# Only remove an explicitly created disposable database after every probe passes.
# A cleanup failure is a failed certification step, never a silent success.
if [ "$SEP_KEEP_DB" != "1" ]; then
  admin postgres postgres "DROP DATABASE $SEP_DB" >/dev/null || die S8 "certification database cleanup failed"
  # The database is removed, but cluster-wide login roles are intentionally
  # NOT deleted by this script. Do not misrepresent this as full cleanup.
  remaining_roles=$(admin postgres postgres "SELECT count(*) FROM pg_roles WHERE rolname IN ('sanad','sanad_migrator')") || die S8 "unable to inventory retained roles"
  printf 'S8_DATABASE_CLEANUP=COMPLETED\nS8_ROLE_CLEANUP=NOT_PERFORMED\nS8_RETAINED_ROLE_COUNT=%s\n' "$remaining_roles" >> "$SUMMARY"
  echo "::warning::S8: database removed; $remaining_roles certification login roles remain and require isolated-cluster disposal" >&2
else
  printf 'S8_DATABASE_CLEANUP=SKIPPED_BY_SEP_KEEP_DB\nS8_ROLE_CLEANUP=NOT_PERFORMED\n' >> "$SUMMARY"
fi
echo "=================================================================="
echo "SEPARATION PROBES: PASSED ($(wc -l < "$TSV") probes); CLUSTER CLEANUP: INCOMPLETE"
echo "Certification is NOT a production authorization; ephemeral roles remain pending isolated-cluster disposal."
echo "Artifacts: $ART (matrix.tsv, S1-migrate.log, S1-validate.log, summary.md)"
echo "=================================================================="
