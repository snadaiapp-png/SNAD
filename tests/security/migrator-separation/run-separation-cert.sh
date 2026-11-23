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
# G1 P0-B: shared signed-tenant-context bind-protocol helper (probe runners
# translate the legacy PGOPTIONS tenant argument into the bind protocol).
# shellcheck source=../lib/tenant-ctx-bind.sh
. "$SCRIPT_DIR/../lib/tenant-ctx-bind.sh"
# V20261115_1 (platform_audit_logs append-only runtime ACL, C4 audit-ledger
# hardening) advanced the chain head, aligned 2026-11-17 with S9/S10;
# SANAD scope-audit remediation — reviewer conditional approval for remediation only
# (2026-11-16) advances the watermark: 20261113.1 -> 20261116.2 (V20261116_1 global
# hostname uniqueness index + V20261116_2 platform subscription-owner resolver with
# S1 per-tenant FORCE-RLS backfill / S3 delete-cycle trigger / S6 default-deny ACL).
# Recorded per watermark policy; no silent value bump.
# Session 12 (2026-11-20) recovery: V20261117_1 (WS7 website tenant-scoped
# integrity) + V20261117_2 (platform subscription owner runtime grants) restored
# from certified 10c evidence (head 767ed0ee manifest watermark 20261117.2);
# watermark advances 20261116.2 -> 20261117.2. Recorded here, not silent.
# G1/C4 PR-2 remediation (2026-11-21, doc 20 P0-1/P1-3): V20261118_1 (tenants
# control-plane ACL) + V20261118_2 (access_capabilities control-plane ACL)
# advance the watermark 20261117.2 -> 20261118.2. Recorded per watermark
# policy; no silent value bump.
# G1 P0-B (2026-11-23, F-DB-07/RT-01 signed tenant-context remediation):
# V20261124_1 (signed binding objects) + V20261124_2 (policy enforcement)
# advance the watermark 20261118.2 -> 20261124.2. Recorded per watermark
# policy; no silent value bump. S1 reproduces the PRODUCTION SEQUENCE
# exactly: migrate to V20261124_1 -> operator key provisioning (ephemeral
# SEP_CTX_KEY, never logged) -> enforcement flag -> migrate V20261124_2.
EXPECTED_HEAD="20261124.2"
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
# G1 P0-B: an ephemeral ACTIVE signing key is provisioned during S1 (operator
# step of the production sequence) and consumed ONLY through this variable;
# it is never logged and never appears in probe expectations.
TENANT_CTX_KEY_HEX="${SEP_CTX_KEY:-}"
export TENANT_CTX_KEY_HEX
runtime_capture() { # runtime_capture <db> <sql> [tenant_guc]
  local probe_sql="$2"
  local guc_args=()
  if [ $# -ge 3 ] && [ "$3" = "__EMPTY_GUC__" ]; then
    # Explicit empty GUC is distinct from an unset GUC; exercise both paths.
    # Post-enforcement both are UNBOUND sessions: the validator returns NULL
    # regardless; the PGOPTIONS form is kept to exercise the raw-empty path.
    guc_args=(PGOPTIONS="-c app.tenant_id=")
  elif [ $# -ge 3 ] && [ -n "$3" ]; then
    # LEGITIMATE tenant context: reproduce the BACKEND BIND PROTOCOL in the
    # SAME transaction (one psql -c argument = one implicit transaction).
    # The legacy PGOPTIONS raw-GUC mechanism is retired: after V20261124_2 a
    # raw GUC alone is not DB-authenticated and must yield zero rows.
    probe_sql="$(tenant_ctx_prefix_sql "$probe_sql" "$3")"
  fi
  # Never inherit an ambient PGOPTIONS tenant context into an unset-GUC probe.
  # Explicit tenant probes supply their own controlled PGOPTIONS value.
  runtime_out=$(env -u PGOPTIONS -u PGHOSTADDR -u PGSERVICE -u PGSERVICEFILE PGPASSWORD="$SEP_RUN_PW" "${guc_args[@]}" psql -X -h "$SEP_PGHOST" -p "$SEP_PGPORT" -U sanad -d "$1" \
       -v ON_ERROR_STOP=1 -v VERBOSITY=verbose -qAtc "$probe_sql" 2>"$ART/last-err.txt")
  runtime_rc=$?
  runtime_err=$(cat "$ART/last-err.txt")
  # VERBOSITY=verbose emits "ERROR:  42501: ..."; whitespace varies.
  # Extract only an explicit five-character SQLSTATE attached to ERROR.
  runtime_sqlstate=$(printf '%s\n' "$runtime_err" | sed -nE 's/^.*ERROR:[[:space:]]+([0-9A-Z]{5}):.*$/\1/p' | head -1)
  [ -z "$runtime_sqlstate" ] && runtime_sqlstate=$(printf '%s\n' "$runtime_err" | sed -nE 's/^.*SQLSTATE:[[:space:]]*([0-9A-Z]{5})([^0-9A-Z]|$).*$/\1/p' | head -1)
  # Never infer SQLSTATE from localized/free-form error messages.
  # A negative security probe passes only with a real PostgreSQL SQLSTATE.
}

cp_out=""; cp_rc=0; cp_err=""
cp_capture() { # cp_capture <db> <sql> — dedicated control-plane connection
  cp_out=$(env -u PGOPTIONS -u PGHOSTADDR -u PGSERVICE -u PGSERVICEFILE PGPASSWORD="$SEP_CP_PW" \
       psql -X -h "$SEP_PGHOST" -p "$SEP_PGPORT" -U sanad_control_plane -d "$1" \
       -v ON_ERROR_STOP=1 -qAtc "$2" 2>"$ART/last-cp-err.txt")
  cp_rc=$?
  cp_err=$(cat "$ART/last-cp-err.txt")
}
expect_cp_pass() { # expect_cp_pass <id> <db> <sql>
  cp_capture "$2" "$3"
  if [ $cp_rc -eq 0 ]; then record "$1" PASS PASS "$cp_out"
  else record "$1" PASS "FAIL(rc=$cp_rc)" "$cp_err"; fail; fi
}
expect_cp_value() { # expect_cp_value <id> <db> <sql> <expected>
  cp_capture "$2" "$3"
  if [ $cp_rc -eq 0 ] && [ "$cp_out" = "$4" ]; then record "$1" "$4" "$cp_out" ok
  else record "$1" "$4" "FAIL(rc=$cp_rc out='$cp_out')" "$cp_err"; fail; fi
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

# P0 V20261111_1: dedicated expired-row cleanup role + NON-INHERITING (SET-only)
# membership for the runtime identity — the chain requires it cluster-wide.
admin postgres postgres "DO \$\$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='sanad_callback_replay_cleanup') THEN
    CREATE ROLE sanad_callback_replay_cleanup NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
  END IF;
END \$\$;" >/dev/null || die S0 "cleanup role provisioning failed"
admin postgres postgres "GRANT sanad_callback_replay_cleanup TO sanad WITH INHERIT FALSE" >/dev/null || die S0 "cleanup role membership failed"
# ORDER 11 / G1-C4 PR-2 (V20261118_1/V20261118_2): dedicated control-plane
# CONNECTION identity holding the tenants + access_capabilities lifecycle
# DML. LOGIN with a per-run ephemeral password; NO membership with the
# runtime in either direction (the forbidden SET ROLE escalation path is
# structurally absent). Fail closed on any pre-existing membership.
SEP_CP_PW="${SEP_CP_PW:-$(openssl rand -hex 24)}"
[[ "$SEP_CP_PW" =~ ^[0-9a-fA-F]{48}$ ]] || die S0 "SEP_CP_PW must be 48 hex characters"
admin postgres postgres "DO \$\$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='sanad_control_plane') THEN
    CREATE ROLE sanad_control_plane LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
  END IF;
END \$\$;" >/dev/null || die S0 "control-plane role provisioning failed"
admin_secret "ALTER ROLE sanad_control_plane PASSWORD '$SEP_CP_PW';" || die S0 "control-plane password"
admin postgres postgres "ALTER ROLE sanad_control_plane NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS LOGIN" >/dev/null || die S0 "control-plane role contract"
admin postgres postgres "DO \$\$ BEGIN
  IF EXISTS (SELECT 1 FROM pg_auth_members m
               JOIN pg_roles g ON g.oid=m.roleid JOIN pg_roles r ON r.oid=m.member
              WHERE (g.rolname='sanad_control_plane' AND r.rolname='sanad')
                 OR (g.rolname='sanad' AND r.rolname='sanad_control_plane')) THEN
    RAISE EXCEPTION 'ORDER11_FORBIDDEN_MEMBERSHIP present';
  END IF;
END \$\$;" >/dev/null || die S0 "forbidden control-plane membership detected (ORDER 11 contract)"
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
# G1 P0-B: S1 reproduces the PRODUCTION DEPLOYMENT SEQUENCE exactly
# (docs/security/migrator-runtime-separation/RUNBOOK.md + V20261124_1 header):
#   S1a  migrate to V20261124_1 (binding objects exist; enforcement off),
#   S1b  OPERATOR step: provision the ephemeral signing key + enforcement
#        flag (in production these come from the operator secret store via
#        scripts/ops/tenant-ctx/provision-ctx-key.sql and the change window),
#   S1c  full migrate (V20261124_2 rewrites the policies fail-closed-guarded)
#        + flyway validate.
gate S1 "Flyway migrate + operator ctx-key provisioning + validate as sanad_migrator (expect head $EXPECTED_HEAD)"
# Use Flyway's environment credential channel: never place the password in
# CLI arguments (which may be visible in process listings and scanner matches).
: "${SEP_MIG_PW:?SEP_MIG_PW must be supplied from the isolated test secret store}"
# Ephemeral per-run signing key (operator channel; never logged, never
# persisted outside this certification run). SEP_CTX_KEY may pin it for the
# battery handoff; default to an ephemeral random value.
SEP_CTX_KEY="${SEP_CTX_KEY:-$(openssl rand -hex 32)}"
[[ "$SEP_CTX_KEY" =~ ^[0-9a-fA-F]{64}$ ]] || die S1 "SEP_CTX_KEY must be 64 hex characters"
# The probe runner consumes the SAME key through the shared helper's channel.
TENANT_CTX_KEY_HEX="$SEP_CTX_KEY"
export TENANT_CTX_KEY_HEX
# Scope the secret to each Flyway child; never export it in the parent shell.
env FLYWAY_PASSWORD="$SEP_MIG_PW" "$SEP_FLYWAY" -url="jdbc:postgresql://$SEP_PGHOST:$SEP_PGPORT/$SEP_DB" \
  -user=sanad_migrator \
  -locations="$LOCS" -target=20261124.1 migrate > "$ART/S1a-migrate-ctx-bindings.log" 2>&1 || die S1 "S1a migrate (to V20261124_1) failed (see $ART/S1a-migrate-ctx-bindings.log)"
head_a=$(admin postgres "$SEP_DB" "SELECT version FROM flyway_schema_history WHERE success=true AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1")
[ "$head_a" = "20261124.1" ] || die S1 "S1a head=$head_a expected=20261124.1"
# --- S1b: operator provisioning (key material ONLY through stdin) ----------
admin postgres "$SEP_DB" "SELECT 1 FROM public.platform_tenant_ctx_keys LIMIT 1" >/dev/null 2>&1 || die S1 "S1b preflight: platform_tenant_ctx_keys missing after S1a"
# Key material flows through stdin (never argv) and targets the CERT DATABASE
# (admin_secret is reserved for cluster-level role statements in postgres).
printf '%s\n' "INSERT INTO public.platform_tenant_ctx_keys (key_id, secret, status) VALUES ('cert-ephemeral', decode('$SEP_CTX_KEY','hex'), 'ACTIVE') ON CONFLICT (key_id) DO UPDATE SET status='ACTIVE', retired_at=NULL;" | $SEP_ADMIN_PSQL -X -v ON_ERROR_STOP=1 -d "$SEP_DB" -At >/dev/null || die S1 "S1b key provisioning failed"
admin "$SEP_DB" "$SEP_DB" "INSERT INTO public.platform_ctx_enforce (flag, enabled_at, enabled_by) VALUES ('ON', now(), 'cert-script-operator-step') ON CONFLICT (flag) DO NOTHING" || die S1 "S1b enforcement flag failed"
# --- S1c: full migrate + validate -------------------------------------------
env FLYWAY_PASSWORD="$SEP_MIG_PW" "$SEP_FLYWAY" -url="jdbc:postgresql://$SEP_PGHOST:$SEP_PGPORT/$SEP_DB" \
  -user=sanad_migrator \
  -locations="$LOCS" migrate > "$ART/S1-migrate.log" 2>&1 || die S1 "migrate failed (see $ART/S1-migrate.log)"
env FLYWAY_PASSWORD="$SEP_MIG_PW" "$SEP_FLYWAY" -url="jdbc:postgresql://$SEP_PGHOST:$SEP_PGPORT/$SEP_DB" \
  -user=sanad_migrator \
  -locations="$LOCS" validate > "$ART/S1-validate.log" 2>&1 || die S1 "validate failed"
head_now=$(admin postgres "$SEP_DB" "SELECT version FROM flyway_schema_history WHERE success=true AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1")
[ "$head_now" = "$EXPECTED_HEAD" ] || die S1 "head=$head_now expected=$EXPECTED_HEAD"
# Enforcement invariant: after V20261124_2 NO policy and NO public function
# body may read the raw tenant GUC (fail-closed proof at the certification
# level, independent of the migration's own postconditions).
ctx_left=$(admin postgres "$SEP_DB" "SELECT
  (SELECT count(*) FROM pg_policies WHERE qual LIKE '%current_setting(''app.tenant_id''%' OR with_check LIKE '%current_setting(''app.tenant_id''%')
  + (SELECT count(*) FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND p.prosrc LIKE '%current_setting(''app.tenant_id''%' AND p.proname NOT IN ('app_tenant_ctx_text','app_bind_tenant_ctx','app_tenant_ctx_slot'))") || die S1 "ctx-invariant query failed"
[ "$ctx_left" = "0" ] || die S1 "raw tenant GUC still referenced by $ctx_left catalog surfaces after enforcement"
gate S1 "PASS: full chain applied as sanad_migrator, head=$head_now, validate OK, ctx-enforcement invariant=0"
record FLYWAY_AS_MIGRATOR PASS PASS "head=$head_now"
record CTX_KEY_PROVISIONED PASS PASS "ephemeral ACTIVE key + enforcement flag provisioned (operator sequence S1b)"

# ============================================================================
# S2 — runtime positive matrix (ordinary DML as sanad) + control-plane ACL
#      (V20261118_1/V20261118_2, ORDER 11): plain runtime DENIED the tenants
#      and access_capabilities lifecycle DML AND denied SET ROLE to the
#      control-plane identity (no membership); the DEDICATED control-plane
#      connection performs the exact lifecycle with read-backs; effective
#      ACL matrices and connection identities asserted per table.
# ============================================================================
gate S2 "runtime DML on the general set + control-plane ACL (as sanad + dedicated connection)"
T1=11111111-1111-1111-1111-111111111111
C1=c0c0c0c0-c0c0-c0c0-c0c0-c0c0c0c0c0c0
expect_pass S2_SELECT "$SEP_DB" "SELECT count(*) FROM public.saas_plans"
# V20261118_1: the plain runtime role must be DENIED tenant lifecycle DML.
expect_deny S2_TENANTS_INSERT_DENIED "$SEP_DB" "INSERT INTO public.tenants (id,name,subdomain,status,created_at,updated_at) VALUES ('$T1','Sep Cert Tenant','sepcert','ACTIVE',now(),now())"
expect_deny S2_TENANTS_UPDATE_DENIED "$SEP_DB" "UPDATE public.tenants SET name='Nope' WHERE id='$T1'"
expect_deny S2_TENANTS_DELETE_DENIED "$SEP_DB" "DELETE FROM public.tenants WHERE id='$T1'"
# V20261118_2: the plain runtime role must be DENIED capability-registry DML.
expect_deny S2_CAPS_INSERT_DENIED "$SEP_DB" "INSERT INTO public.access_capabilities (id,code,name,created_at,updated_at) VALUES ('$C1','SEP.CERT.PROBE','Sep Cert probe',now(),now())"
expect_deny S2_CAPS_UPDATE_DENIED "$SEP_DB" "UPDATE public.access_capabilities SET status='INACTIVE' WHERE code='SEP.CERT.PROBE'"
expect_deny S2_CAPS_DELETE_DENIED "$SEP_DB" "DELETE FROM public.access_capabilities WHERE code='SEP.CERT.PROBE'"
# S9-style effective ACL matrices (SELECT|INSERT|UPDATE|DELETE|TRUNCATE):
# runtime keeps SELECT only; TRUNCATE is revoked with the lifecycle DML.
expect_value S2_ACL_TENANTS "$SEP_DB" "SELECT has_table_privilege('sanad','public.tenants','SELECT')::text||'|'||has_table_privilege('sanad','public.tenants','INSERT')::text||'|'||has_table_privilege('sanad','public.tenants','UPDATE')::text||'|'||has_table_privilege('sanad','public.tenants','DELETE')::text||'|'||has_table_privilege('sanad','public.tenants','TRUNCATE')::text" "true|false|false|false|false"
expect_value S2_ACL_CAPS "$SEP_DB" "SELECT has_table_privilege('sanad','public.access_capabilities','SELECT')::text||'|'||has_table_privilege('sanad','public.access_capabilities','INSERT')::text||'|'||has_table_privilege('sanad','public.access_capabilities','UPDATE')::text||'|'||has_table_privilege('sanad','public.access_capabilities','DELETE')::text||'|'||has_table_privilege('sanad','public.access_capabilities','TRUNCATE')::text" "true|false|false|false|false"
expect_value S2_CP_ACL_TENANTS "$SEP_DB" "SELECT has_table_privilege('sanad_control_plane','public.tenants','INSERT')::text||'|'||has_table_privilege('sanad_control_plane','public.tenants','UPDATE')::text||'|'||has_table_privilege('sanad_control_plane','public.tenants','DELETE')::text" "true|true|true"
expect_value S2_CP_ACL_CAPS "$SEP_DB" "SELECT has_table_privilege('sanad_control_plane','public.access_capabilities','INSERT')::text||'|'||has_table_privilege('sanad_control_plane','public.access_capabilities','UPDATE')::text||'|'||has_table_privilege('sanad_control_plane','public.access_capabilities','DELETE')::text" "true|true|true"
# ORDER 11: the runtime is DENIED SET ROLE to the control-plane identity —
# no membership exists, so the escalation path is structurally absent.
expect_deny S2_SETROLE_CP_DENIED "$SEP_DB" "SET ROLE sanad_control_plane"
# ORDER 11: connection identity isolation. The dedicated control-plane
# connection authenticates as sanad_control_plane (both current_user and
# session_user); the runtime connection never carries that identity (there
# is no SET ROLE anywhere in the approved path — pool leakage is
# structurally impossible and this asserts it live).
expect_cp_value S2CP_CONNECTION_IDENTITY "$SEP_DB" "SELECT current_user||'|'||session_user" "sanad_control_plane|sanad_control_plane"
expect_value S2CP_RUNTIME_IDENTITY_UNCHANGED "$SEP_DB" "SELECT current_user||'|'||session_user" "sanad|sanad"
# ORDER 11 compensating control: the DEDICATED control-plane connection
# performs the EXACT tenant lifecycle (INSERT -> read-back -> UPDATE ->
# DELETE). Read-backs run on the RUNTIME connection: the runtime keeps
# SELECT, so the rows are provably in storage, not hidden by policy.
expect_cp_value S2CP_TENANTS_INSERT "$SEP_DB" "WITH ins AS (INSERT INTO public.tenants (id,name,subdomain,status,created_at,updated_at) VALUES ('$T1','Sep Cert Tenant','sepcert','ACTIVE',now(),now()) RETURNING id) SELECT count(*) FROM ins" "1"
expect_value S2CP_TENANTS_READBACK "$SEP_DB" "SELECT count(*) FROM public.tenants WHERE id='$T1' AND name='Sep Cert Tenant' AND status='ACTIVE'" "1"
expect_cp_value S2CP_TENANTS_UPDATE "$SEP_DB" "WITH changed AS (UPDATE public.tenants SET name='Sep Cert Tenant 2' WHERE id='$T1' RETURNING 1) SELECT count(*) FROM changed" "1"
expect_cp_value S2CP_TENANTS_DELETE "$SEP_DB" "WITH deleted AS (DELETE FROM public.tenants WHERE id='$T1' RETURNING 1) SELECT count(*) FROM deleted" "1"
# ORDER 11 compensating control on the capability registry: full lifecycle
# via the dedicated connection + runtime read-backs.
expect_cp_value S2CP_CAPS_INSERT "$SEP_DB" "WITH ins AS (INSERT INTO public.access_capabilities (id,code,name,created_at,updated_at) VALUES ('$C1','SEP.CERT.PROBE','Sep Cert probe',now(),now()) RETURNING code) SELECT count(*)||'|'||max(code) FROM ins" "1|SEP.CERT.PROBE"
expect_value S2CP_CAPS_READBACK "$SEP_DB" "SELECT count(*) FROM public.access_capabilities WHERE id='$C1' AND code='SEP.CERT.PROBE'" "1"
expect_cp_value S2CP_CAPS_UPDATE "$SEP_DB" "WITH changed AS (UPDATE public.access_capabilities SET status='INACTIVE' WHERE id='$C1' RETURNING status) SELECT count(*)||'|'||max(status) FROM changed" "1|INACTIVE"
expect_cp_value S2CP_CAPS_DELETE "$SEP_DB" "WITH deleted AS (DELETE FROM public.access_capabilities WHERE id='$C1' RETURNING 1) SELECT count(*) FROM deleted" "1"
expect_value S2CP_ROLE_RESTORED "$SEP_DB" "SELECT current_user" "sanad"
# Confirm the row is absent in storage, not merely hidden by runtime policies.
s2_remaining=$(admin postgres "$SEP_DB" "SELECT count(*) FROM public.tenants WHERE id='$T1'") || die S2 "admin tenant deletion inventory failed"
if [ "$s2_remaining" = "0" ]; then
  record S2_ADMIN_TENANT_DELETION "0" "$s2_remaining" "admin-confirmed tenant removal"
else
  record S2_ADMIN_TENANT_DELETION "0" "$s2_remaining" "tenant deletion integrity mismatch"
  fail
fi
s2_caps_remaining=$(admin postgres "$SEP_DB" "SELECT count(*) FROM public.access_capabilities WHERE id='$C1'") || die S2 "admin capability deletion inventory failed"
if [ "$s2_caps_remaining" = "0" ]; then
  record S2_ADMIN_CAPS_DELETION "0" "$s2_caps_remaining" "admin-confirmed capability probe removal"
else
  record S2_ADMIN_CAPS_DELETION "0" "$s2_caps_remaining" "capability deletion integrity mismatch"
  fail
fi
[ $FAILURES -eq 0 ] || die S2 "positive DML / control-plane ACL matrix failed"
gate S2 "PASS: ordinary DML as restricted runtime; tenants + access_capabilities lifecycle DENIED for plain runtime (42501); runtime SET ROLE sanad_control_plane DENIED (42501, no membership); dedicated-connection lifecycle + identity isolation proven exactly; ACL matrices exact"

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
# ============================================================================
# S4-CTX — SIGNED TENANT-CONTEXT probes (G1 P0-B, V20261124_1/V20261124_2).
# The RT-01 attack class is now asserted BLOCKED at certification level.
# ============================================================================
# The RT-01 attack, verbatim: a raw forged GUC (session-level SET, no bind)
# must yield ZERO rows — the GUC alone is no longer DB-authenticated.
expect_value S4_CTX_RT01_FORGE_BLOCKED "$SEP_DB" "SET app.tenant_id='$TB'; SELECT count(*) FROM public.users WHERE tenant_id='$TB'" "0"
# Forged GUC + a hand-crafted mac must fail the HMAC re-verification.
expect_value S4_CTX_FORGED_MAC_BLOCKED "$SEP_DB" "SET app.tenant_id='$TB'; SET app.ctx_mac='deadbeef'; SELECT count(*) FROM public.users WHERE tenant_id='$TB'" "0"
# A forged GUC + a mac captured from ANOTHER transaction's bind (the replay
# attack: the mac is minted in its own autocommit transaction, then replayed
# by hand on the probe connection) yields 0 rows — txid/pid binding kills it.
ctx_stolen_mac=$(env -u PGOPTIONS PGPASSWORD="$SEP_RUN_PW" psql -X -h "$SEP_PGHOST" -p "$SEP_PGPORT" -U sanad -d "$SEP_DB" -Atc "SELECT encode(hmac(('$TA:'||s.ctx_txid||':'||s.ctx_pid::text)::bytea, decode('$SEP_CTX_KEY','hex'),'sha256'),'hex') FROM public.app_tenant_ctx_slot() s") || die S4 "stolen-mac mint failed"
expect_value S4_CTX_STOLEN_MAC_REPLAY_BLOCKED "$SEP_DB" "SET app.tenant_id='$TB'; SET app.ctx_mac='$ctx_stolen_mac'; SELECT count(*) FROM public.users WHERE tenant_id='$TB'" "0"
# Replaying the stolen mac with the CORRECT tenant still fails: the mac is
# bound to the ORIGINAL transaction's txid/pid, never the replay session's.
expect_value S4_CTX_STOLEN_MAC_SAME_TENANT_REPLAY "$SEP_DB" "SET app.tenant_id='$TA'; SET app.ctx_mac='$ctx_stolen_mac'; SELECT count(*) FROM public.users WHERE id='$UA'" "0"
# app_bind_tenant_ctx with an invalid mac must raise 22000 (fail closed).
expect_deny S4_CTX_BIND_GARBAGE_MAC "$SEP_DB" "SELECT public.app_bind_tenant_ctx('$TA'::uuid, '00000000000000000000000000000000')" "" "22000"
# The bind function requires an ACTIVE key; bind for tenant A then read.
expect_value S4_CTX_BIND_LEGIT "$SEP_DB" "SELECT count(*) FROM public.users WHERE id='$UA'" "1" "$TA"
# Nested re-scope (the documented 'last call wins' contract) for the trusted
# caller: bind A then bind B as sequential statements of ONE transaction —
# the B binding wins and B rows become visible (A rows no longer are).
expect_value S4_CTX_RESCOPE_LAST_WINS "$SEP_DB" "$(tenant_ctx_bind_sql "$TA") $(tenant_ctx_bind_sql "$TB") SELECT (SELECT count(*) FROM public.users WHERE id='$UB') AS sees_b, (SELECT count(*) FROM public.users WHERE id='$UA') AS sees_a" "1|0"
# The validator itself: NULL on an unbound session; the bound tenant when bound.
expect_value S4_CTX_VALIDATOR_NULL_UNBOUND "$SEP_DB" "SELECT public.app_tenant_ctx_text() IS NULL" "t"
expect_value S4_CTX_VALIDATOR_BOUND "$SEP_DB" "SELECT public.app_tenant_ctx_text()" "$TA" "$TA"
# Keys table is invisible to the runtime (F-DB-05 containment on new objects).
expect_deny S4_CTX_KEYS_TABLE_INACCESSIBLE "$SEP_DB" "SELECT count(*) FROM public.platform_tenant_ctx_keys"
# The enforcement flag table is likewise invisible.
expect_deny S4_CTX_FLAG_TABLE_INACCESSIBLE "$SEP_DB" "SELECT count(*) FROM public.platform_ctx_enforce"
# A hostile session CANNOT flip the enforcement switch (zero grants).
expect_deny S4_CTX_FLAG_WRITE_DENIED "$SEP_DB" "INSERT INTO public.platform_ctx_enforce (flag, enabled_at, enabled_by) VALUES ('ON', now(), 'hostile')"
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
own_stats=$(admin postgres "$SEP_DB" "SELECT pg_get_userbyid(c.relowner)||':'||count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind IN ('r','p','S','v','m') GROUP BY pg_get_userbyid(c.relowner) ORDER BY count(*) DESC" | tr '\n' ' ') || die S7 "ownership summary query failed"
echo "OWNERSHIP: $own_stats" >> "$SUMMARY"
runtime_owned=$(admin postgres "$SEP_DB" "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind IN ('r','p','S','v','m') AND pg_get_userbyid(c.relowner)='sanad'") || die S7 "runtime ownership inventory failed"
[ "$runtime_owned" = "0" ] || die S7 "runtime role owns $runtime_owned public objects: $own_stats"
hist_owner=$(admin postgres "$SEP_DB" "SELECT pg_get_userbyid(relowner) FROM pg_class WHERE oid='public.flyway_schema_history'::regclass") || die S7 "Flyway history ownership query failed"
[ "$hist_owner" = "sanad_migrator" ] || die S7 "flyway_schema_history owner=$hist_owner"
runtime_create=$(admin postgres "$SEP_DB" "SELECT has_schema_privilege('sanad','public','CREATE')") || die S7 "runtime schema CREATE privilege query failed"
[ "$runtime_create" = "f" ] || die S7 "sanad still holds CREATE on public"
public_create=$(admin postgres "$SEP_DB" "SELECT EXISTS (SELECT 1 FROM pg_namespace n CROSS JOIN LATERAL aclexplode(coalesce(n.nspacl,acldefault('n',n.nspowner))) a WHERE n.nspname='public' AND a.grantee=0 AND a.privilege_type='CREATE')") || die S7 "PUBLIC CREATE privilege check failed"
[ "$public_create" = "f" ] || die S7 "PUBLIC has CREATE privilege on public schema"
rls_coverage=$(admin postgres "$SEP_DB" "SELECT count(*)||'|'||count(*) FILTER (WHERE c.relrowsecurity AND c.relforcerowsecurity) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind IN ('r','p') AND c.relname IN ('users','refresh_tokens','password_reset_tokens','provisioning_jobs','provisioning_job_steps','subscription_commands')") || die S7 "FORCE RLS coverage query failed"
[ "$rls_coverage" = "6|6" ] || die S7 "expected six existing FORCE RLS tables; observed $rls_coverage"
seq_ok=$(admin postgres "$SEP_DB" "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind='S' AND NOT (has_sequence_privilege('sanad',quote_ident(n.nspname)||'.'||quote_ident(c.relname),'USAGE') AND has_sequence_privilege('sanad',quote_ident(n.nspname)||'.'||quote_ident(c.relname),'SELECT'))") || die S7 "sequence privilege inventory query failed"
[ "$seq_ok" = "0" ] || die S7 "$seq_ok sequence(s) missing USAGE/SELECT for sanad"
record S7_CATALOG PASS PASS "owners=[$own_stats] history=$hist_owner sanadCREATE=$runtime_create forceRLS=6/6 seqOK"
gate S7 "PASS: $own_stats; history owner=$hist_owner; runtime CREATE=false; FORCE RLS 6/6; sequences OK"

# ============================================================================
# S9 — runtime append-only enforcement: real identity, effective + inherited
#      privileges, and live DML denial on every append-only ledger table
#      (subscription_commands + platform_audit_logs / V20261115_1)
# ============================================================================
gate S9 "runtime append-only enforcement (identity + effective/inherited ACL + live DML)"
AL=eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee
# 9.1 real runtime identity contract, proven on the live connection itself
expect_value S9_IDENTITY "$SEP_DB" "SELECT current_user||'|'||session_user" "sanad|sanad"
expect_value S9_ROLE_CONTRACT "$SEP_DB" "SELECT rolsuper::text||'|'||rolbypassrls::text||'|'||rolcreatedb::text||'|'||rolcreaterole::text||'|'||rolinherit::text FROM pg_roles WHERE rolname=current_user" "false|false|false|false|false"
# 9.2 membership inventory: every role sanad belongs to. INHERIT FALSE
#     memberships still matter: SET ROLE reaches those grants, so the set
#     must match the documented contract exactly (fail closed on drift).
s9_members=$(admin postgres "$SEP_DB" "SELECT coalesce(string_agg(r.rolname, ',' ORDER BY r.rolname),'') FROM pg_auth_members m JOIN pg_roles r ON r.oid=m.roleid JOIN pg_roles u ON u.oid=m.member WHERE u.rolname='sanad'") || die S9 "runtime membership inventory failed"
[ "$s9_members" = "sanad_callback_replay_cleanup" ] || die S9 "unexpected runtime membership set: '$s9_members' (expected exactly sanad_callback_replay_cleanup — ORDER 11: NO sanad_control_plane membership may exist)"
record S9_MEMBERSHIP "sanad_callback_replay_cleanup" "$s9_members" "runtime membership inventory (NOINHERIT cleanup role only; ORDER 11 forbids control-plane membership)"
# ORDER 11: assert the absence of every membership direction around the
# control-plane identity, live, on the certified database.
s9_cp_member_of=$(admin postgres "$SEP_DB" "SELECT coalesce(string_agg(r.rolname, ',' ORDER BY r.rolname),'') FROM pg_auth_members m JOIN pg_roles r ON r.oid=m.roleid JOIN pg_roles u ON u.oid=m.member WHERE u.rolname='sanad_control_plane'")
[ "$s9_cp_member_of" = "" ] || die S9 "control-plane identity is member of: '$s9_cp_member_of' (must be member of NOTHING)"
s9_cp_members=$(admin postgres "$SEP_DB" "SELECT coalesce(string_agg(u.rolname, ',' ORDER BY u.rolname),'') FROM pg_auth_members m JOIN pg_roles r ON r.oid=m.roleid JOIN pg_roles u ON u.oid=m.member WHERE r.rolname='sanad_control_plane'")
[ "$s9_cp_members" = "" ] || die S9 "control-plane identity has members: '$s9_cp_members' (must have NO members)"
s9_cp_login=$(admin postgres "$SEP_DB" "SELECT rolcanlogin::text||'|'||rolsuper::text||'|'||rolbypassrls::text FROM pg_roles WHERE rolname='sanad_control_plane'")
[ "$s9_cp_login" = "true|false|false" ] || die S9 "control-plane identity contract: '$s9_cp_login' (expected LOGIN, NOSUPERUSER, NOBYPASSRLS)"
record S9_CONTROL_PLANE_IDENTITY "LOGIN|NOSUPERUSER|NOBYPASSRLS|NO_MEMBERS_BOTH_DIRECTIONS" "$s9_cp_login" "ORDER 11 dedicated connection identity contract"
# 9.3 effective ACL matrix asserted by the runtime itself (has_table_privilege
#     with no role argument = current_user effective privileges, including
#     any inherited paths): SELECT/INSERT allowed, UPDATE/DELETE/TRUNCATE denied
expect_value S9_ACL_SUBSCRIPTION_COMMANDS "$SEP_DB" "SELECT has_table_privilege('public.subscription_commands','SELECT')::text||'|'||has_table_privilege('public.subscription_commands','INSERT')::text||'|'||has_table_privilege('public.subscription_commands','UPDATE')::text||'|'||has_table_privilege('public.subscription_commands','DELETE')::text||'|'||has_table_privilege('public.subscription_commands','TRUNCATE')::text" "true|true|false|false|false"
expect_value S9_ACL_PLATFORM_AUDIT_LOGS "$SEP_DB" "SELECT has_table_privilege('public.platform_audit_logs','SELECT')::text||'|'||has_table_privilege('public.platform_audit_logs','INSERT')::text||'|'||has_table_privilege('public.platform_audit_logs','UPDATE')::text||'|'||has_table_privilege('public.platform_audit_logs','DELETE')::text||'|'||has_table_privilege('public.platform_audit_logs','TRUNCATE')::text" "true|true|false|false|false"
# 9.4 inherited-privilege bypass: no member role of sanad may hold
#     UPDATE/DELETE/TRUNCATE on either append-only ledger (catalog check
#     as admin; catches hostile grants made to any sanad-reachable role)
s9_bypass=$(admin postgres "$SEP_DB" "SELECT count(*) FROM pg_auth_members m JOIN pg_roles r ON r.oid=m.roleid JOIN pg_roles u ON u.oid=m.member WHERE u.rolname='sanad' AND r.rolname<>'sanad' AND (has_table_privilege(r.rolname,'public.subscription_commands','UPDATE') OR has_table_privilege(r.rolname,'public.subscription_commands','DELETE') OR has_table_privilege(r.rolname,'public.subscription_commands','TRUNCATE') OR has_table_privilege(r.rolname,'public.platform_audit_logs','UPDATE') OR has_table_privilege(r.rolname,'public.platform_audit_logs','DELETE') OR has_table_privilege(r.rolname,'public.platform_audit_logs','TRUNCATE'))") || die S9 "inherited-privilege bypass inventory failed"
if [ "$s9_bypass" = "0" ]; then
  record S9_NO_INHERITED_BYPASS "0" "$s9_bypass" "no member role holds ledger UPDATE/DELETE/TRUNCATE"
else
  record S9_NO_INHERITED_BYPASS "0" "$s9_bypass" "member-role privilege bypass detected"
  fail
fi
# 9.5 live DML proof on the audit ledger as the runtime: INSERT allowed,
#     UPDATE/DELETE/TRUNCATE denied with a real SQLSTATE
expect_pass  S9_AUDIT_INSERT "$SEP_DB" "INSERT INTO public.platform_audit_logs (id,actor_tenant_id,action,resource_type,resource_id,result,created_at) VALUES ('$AL','$TA','SEP_CERT_PROBE','separation_cert','s9','SUCCESS',now())" "$TA"
expect_value S9_AUDIT_PRESENT "$SEP_DB" "SELECT count(*) FROM public.platform_audit_logs WHERE id='$AL'" "1" "$TA"
expect_deny  S9_AUDIT_UPDATE "$SEP_DB" "UPDATE public.platform_audit_logs SET reason='FORCED' WHERE id='$AL'" "$TA"
expect_deny  S9_AUDIT_DELETE "$SEP_DB" "DELETE FROM public.platform_audit_logs WHERE id='$AL'" "$TA"
expect_deny  S9_AUDIT_TRUNCATE "$SEP_DB" "TRUNCATE TABLE public.platform_audit_logs" "$TA"
# 9.6 live TRUNCATE denial on the subscription ledger (complements S6)
expect_deny  S9_SUB_TRUNCATE "$SEP_DB" "TRUNCATE TABLE public.subscription_commands" "$TA"
# 9.7 admin-confirmed ledger integrity: the audit row is preserved verbatim
s9_integrity=$(admin postgres "$SEP_DB" "SELECT (SELECT count(*) FROM public.platform_audit_logs WHERE id='$AL' AND action='SEP_CERT_PROBE' AND result='SUCCESS' AND reason IS NULL)||'|'||(SELECT count(*) FROM public.platform_audit_logs)") || die S9 "admin audit-ledger integrity query failed"
if [ "$s9_integrity" = "1|1" ]; then
  record S9_ADMIN_AUDIT_INTEGRITY "1|1" "$s9_integrity" "admin-confirmed append-only audit row preserved verbatim"
else
  record S9_ADMIN_AUDIT_INTEGRITY "1|1" "$s9_integrity" "audit row missing, modified or extra rows present"
  fail
fi
[ $FAILURES -eq 0 ] || die S9 "append-only enforcement matrix failed"
gate S9 "PASS: runtime identity+membership contract; effective+inherited ACL append-only on both ledgers; live INSERT allowed, UPDATE/DELETE/TRUNCATE denied (42501)"

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
