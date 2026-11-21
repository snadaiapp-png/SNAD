#!/usr/bin/env bash
# ============================================================================
# SNAD G1/C4 (PR-2) — RED TEAM attack-surface confirmation battery (v2).
#
# Live SQL probes against the DISPOSABLE separated-topology separation_cert
# database certified by tests/security/migrator-separation/run-separation-cert.sh.
# Every probe connects as the restricted runtime role `sanad`; SQLSTATEs, exit
# codes, affected-row counts and read-back values are recorded per probe.
#
# v2 remediation (this version) of the v1 battery reviewed by the owner:
#   1. ISOLATION PROOFS (IP-01..IP-12): the Admin connection and the Runtime
#      connection are proven to target the SAME isolated cluster before any
#      attack probe runs — identical pg_control_system().system_identifier on
#      both connections, identical postmaster start time, identical port,
#      and a database-level sentinel written by admin and read back by the
#      runtime (then RESET and proven absent). Cluster purity, migrator DB
#      ownership, Flyway head, runtime role contract and admin superuser
#      identity are all asserted exactly. Any mismatch aborts the battery
#      (fail closed).
#   2. POSITIVE PROBES ASSERT EXACT EFFECTS: every allowed (CONFIRMED)
#      operation must report the exact affected-row count (RETURNING CTE)
#      AND the actual result values are read back and compared — as the
#      runtime role and, where RLS could mask storage truth, by the admin
#      connection. No "rc=0 therefore confirmed" reasoning remains.
#   3. ALL PROBES FAIL CLOSED: expected outcomes are exact values or exact
#      SQLSTATEs; an unexpected rc, an empty/unparseable output, a missing
#      SQLSTATE, a hung statement (statement_timeout + PGCONNECT_TIMEOUT)
#      or a skipped probe (final probe-count assertion) fails the battery.
#   4. service_role is classified precisely: the LOCAL isolated cluster is
#      ASSERTED (count=0, exact) and the PRODUCTION classification is
#      recorded as UNVERIFIED — no authorized production connection exists
#      in this engagement, so no production probe is executed or claimed
#      (see service-role-classification.txt; it is not counted as a pass).
#
# v3 (2026-11-21, G1/C4 PR-2 remediation delivery): RT-07/07B/07C, RT-08/08B
#   and RT-09/09B flip from CONFIRMED to DENIED(42501) — V20261118_1 revoked
#   the tenants lifecycle DML from the plain runtime role in the separated
#   topology; RT-12/13/13B/14/14B flip likewise for V20261118_2
#   (access_capabilities).
# v4 (2026-11-21, ORDER 11 — Control Plane Security & PostgreSQL Root-Cause
#   Remediation): the SET ROLE compensating control is FORBIDDEN and removed.
#   sanad_control_plane is a dedicated LOGIN connection identity with NO
#   membership toward the runtime in any direction. RT-36/RT-37 prove the
#   DEDICATED-CONNECTION lifecycle with exact read-backs, RT-38 verifies the
#   effective control-plane ACL matrix (including the SELECT privilege
#   required by WHERE-keyed DML — the 42501 root cause), RT-39 proves the
#   runtime SET ROLE escalation attempt is DENIED, and RT-40 proves
#   connection-identity isolation (no pool privilege leakage, in either
#   direction, live).
#
# Outcome classes:
#   DENIED(sqlstate)  — attack correctly refused by PostgreSQL;
#   CONFIRMED(value)  — exposure/design-trust behaviour confirmed live WITH
#                       the exact observed value (recorded as a finding,
#                       never as a security pass);
#   value / count     — fail-closed exact expectations.
#
# Environment:
#   RT_PGHOST (127.0.0.1)  RT_PGPORT (55432)
#   RT_EXPECTED_SYSID (required: system identifier from fresh disposable
#                      initdb/provisioning evidence, not discovered on demand)
#   RT_BOOTSTRAP_PW (required: existing disposable runtime password, 48 hex)
#   Admin uses the same explicit loopback host/port as runtime; overrides refused.
#   RT_ARTIFACTS (fresh evidence directory; must be empty or nonexistent)
#
# This battery is an attack-surface CONFIRMATION exercise on an isolated
# cluster. It is NOT a penetration test of production, NOT a production
# authorization, and NOT a security PASS decision.
# ============================================================================
set -uo pipefail
export LC_ALL=C.UTF-8
# Fail closed on unreachable servers and on runaway statements.
export PGCONNECT_TIMEOUT=15

RT_PGHOST="${RT_PGHOST:-127.0.0.1}"
RT_PGPORT="${RT_PGPORT:-55432}"
[ -z "${RT_ADMIN_PSQL:-}" ] || { echo "::error::RT_ADMIN_PSQL override refused" >&2; exit 1; }
[[ "${RT_EXPECTED_SYSID:-}" =~ ^[0-9]+$ ]] || { echo "::error::RT_EXPECTED_SYSID from disposable provisioning required" >&2; exit 1; }
# Do not allow implicit libpq settings to redirect either connection.
unset PGHOSTADDR PGSERVICE PGSERVICEFILE PGOPTIONS PGDATABASE PGHOST PGPORT PGUSER
RT_DB="${RT_DB:-separation_cert}"
ART="${RT_ARTIFACTS:-/tmp/redteam-artifacts}"
umask 077

[ "${RT_ISOLATED_CLUSTER:-0}" = "1" ] || { echo "::error::RT_ISOLATED_CLUSTER=1 required" >&2; exit 1; }
case "$RT_PGHOST" in
  127.0.0.1|::1) ;;
  *) echo "::error::refusing non-loopback RT_PGHOST=$RT_PGHOST" >&2; exit 1 ;;
esac
[ "$RT_DB" = "separation_cert" ] || { echo "::error::non-certification DB rejected" >&2; exit 1; }
command -v flock >/dev/null 2>&1 || { echo "::error::flock is required" >&2; exit 1; }
exec 9< "${BASH_SOURCE[0]}" || exit 1
flock -n 9 || { echo "::error::another battery is running from this checkout" >&2; exit 1; }
[ ! -L "$ART" ] || { echo "::error::evidence path is a symlink" >&2; exit 1; }
if [ -e "$ART" ]; then
  [ -d "$ART" ] && [ -z "$(ls -A "$ART")" ] || { echo "::error::evidence dir not empty; choose fresh RT_ARTIFACTS" >&2; exit 1; }
else
  mkdir -m 700 -p "$ART" || exit 1
fi

TSV="$ART/redteam-matrix.tsv"; : > "$TSV"
LOG="$ART/redteam-run.log"
ISO="$ART/isolation-proof.txt"
SRC="$ART/service-role-classification.txt"
: > "$ISO"
FAILURES=0
# A skipped probe (script bug, early exit, silent fall-through) must fail the
# battery: the final assertion requires exactly this many TSV records.
# v4 (ORDER 11): the 5 v3 compensating-control records become 16
# dedicated-connection records (RT-36..RT-36E, RT-37..RT-37E, RT-38,
# RT-39/RT-39B/RT-39C, RT-40/RT-40B).
EXPECTED_PROBES=79

TA=22222222-2222-2222-2222-222222222222
TB=33333333-3333-3333-3333-333333333333
UA=44444444-4444-4444-4444-444444444444   # users row of tenant A (S4 fixture)
UB=55555555-5555-5555-5555-555555555555   # users row of tenant B (S4 fixture)
TT=44444444-4444-4444-4444-444444444444   # sacrificial tenants row (same UUID as S2; deleted there)
CAP=55555555-5555-5555-5555-555555555555  # sacrificial access_capabilities row
AUD=77777777-7777-7777-7777-777777777777  # forged audit row id

admin() { PGOPTIONS="-c statement_timeout=20000" psql -X -w -h "$RT_PGHOST" -p "$RT_PGPORT" -U postgres -v ON_ERROR_STOP=1 -d "$2" -Atc "$3"; }
admin_expect() { # admin_expect <id> <db> <sql> <expected>
  local out rc; out=$(admin postgres "$2" "$3"); rc=$?
  if [ $rc -eq 0 ] && [ "$out" = "$4" ]; then record "$1" "$4" "$out" "admin-verified"
  else record "$1" "$4" "FAIL(rc=$rc,out=$out)"; FAILURES=$((FAILURES+1)); fi
}
admin_secret() { printf '%s\n' "$1" | PGOPTIONS="-c statement_timeout=20000" psql -X -w -h "$RT_PGHOST" -p "$RT_PGPORT" -U postgres -v ON_ERROR_STOP=1 -d postgres -At >/dev/null; }

# Authenticate read-only isolation proofs using the existing disposable runtime
# credential. Generate a NEW password only after every isolation proof succeeds.
RT_PW="${RT_BOOTSTRAP_PW:-}"
unset RT_BOOTSTRAP_PW
[[ "$RT_PW" =~ ^[0-9a-fA-F]{48}$ ]] || { echo "::error::RT_BOOTSTRAP_PW from disposable provisioning required" >&2; exit 1; }

# ORDER 11: dedicated control-plane connection credential (disposable,
# provisioned by the separation matrix S0 on the same isolated cluster).
RT_CP_PW="${RT_BOOTSTRAP_CP_PW:-}"
unset RT_BOOTSTRAP_CP_PW
[[ "$RT_CP_PW" =~ ^[0-9a-fA-F]{48}$ ]] || { echo "::error::RT_BOOTSTRAP_CP_PW from disposable provisioning required" >&2; exit 1; }

cp() { # cp <sql> — dedicated control-plane probe as sanad_control_plane
  out=$(env -u PGOPTIONS PGOPTIONS="-c statement_timeout=20000" PGPASSWORD="$RT_CP_PW" \
    psql -X -w -h "$RT_PGHOST" -p "$RT_PGPORT" -U sanad_control_plane -d "$RT_DB" \
    -v ON_ERROR_STOP=1 -v VERBOSITY=verbose -qAtc "$1" 2>"$ART/last-cp-err.txt")
  rc=$?
  err=$(cat "$ART/last-cp-err.txt")
  state=$(printf '%s\n' "$err" | sed -nE 's/^.*ERROR:[[:space:]]+([0-9A-Z]{5}):.*$/\1/p' | head -1)
}
expect_cp_value() { # expect_cp_value <id> <sql> <expected>
  cp "$2"
  if [ "$rc" -eq 0 ] && [ "$out" = "$3" ]; then record "$1" "$3" "$out" "dedicated-connection exact-match"
  else record "$1" "$3" "FAIL(rc=$rc,state=$state,out=$out)"; FAILURES=$((FAILURES+1)); fi
}
rt() { # rt <sql> [guc] — runtime probe as sanad; sets out/rc/state
  local opts="-c statement_timeout=20000"
  if [ $# -ge 2 ] && [ -n "$2" ]; then opts="$opts -c app.tenant_id=$2"; fi
  out=$(env -u PGOPTIONS PGOPTIONS="$opts" PGPASSWORD="$RT_PW" psql -X -w -h "$RT_PGHOST" -p "$RT_PGPORT" -U sanad -d "$RT_DB" \
    -v ON_ERROR_STOP=1 -v VERBOSITY=verbose -qAtc "$1" 2>"$ART/last-err.txt")
  rc=$?
  err=$(cat "$ART/last-err.txt")
  state=$(printf '%s\n' "$err" | sed -nE 's/^.*ERROR:[[:space:]]+([0-9A-Z]{5}):.*$/\1/p' | head -1)
}
require_isolation() {
  [ "$FAILURES" -eq 0 ] || { echo "::error::isolation proof failed; refusing further SQL" >&2; exit 1; }
}
record() { printf '%s\t%s\t%s\t%s\n' "$1" "$2" "$3" "$(printf '%s' "${4:-}" | tr '\n' ' ' | cut -c1-160)" >> "$TSV" || exit 1
           printf '  [%s] %s -> %s\n' "$1" "$2" "$3"; }
expect_value() { # expect_value <id> <sql> <expected> [guc] — exact output match
  rt "$2" "${4:-}"
  if [ "$rc" -eq 0 ] && [ "$out" = "$3" ]; then record "$1" "$3" "$out" "exact-match"
  else record "$1" "$3" "FAIL(rc=$rc,state=$state,out=$out)"; FAILURES=$((FAILURES+1)); fi
}
expect_denied() { # expect_denied <id> <sql> <sqlstate> [guc]
  rt "$2" "${4:-}"
  if [ "$rc" -ne 0 ] && [ "$state" = "$3" ]; then record "$1" "DENIED($3)" "DENIED($state)"
  else record "$1" "DENIED($3)" "FAIL(rc=$rc,state=$state,out=$out)"; FAILURES=$((FAILURES+1)); fi
}
expect_confirmed_value() { # expect_confirmed_value <id> <sql> <expected> [guc]
  rt "$2" "${4:-}"
  if [ "$rc" -eq 0 ] && [ "$out" = "$3" ]; then record "$1" "CONFIRMED($3)" "CONFIRMED($out)" "exposure with exact value"
  else record "$1" "CONFIRMED($3)" "FAIL(rc=$rc,state=$state,out=$out)"; FAILURES=$((FAILURES+1)); fi
}
expect_not_value() { # expect_not_value <id> <sql> <forbidden> [guc] — non-empty AND != forbidden
  rt "$2" "${4:-}"
  if [ "$rc" -eq 0 ] && [ -n "$out" ] && [ "$out" != "$3" ]; then record "$1" "NOT($3)" "$out" "non-empty and differs"
  else record "$1" "NOT($3)" "FAIL(rc=$rc,state=$state,out=$out)"; FAILURES=$((FAILURES+1)); fi
}

echo "== RED TEAM BATTERY v2 — $(date -u +%Y-%m-%dT%H:%M:%SZ) — runtime role sanad on $RT_DB ($RT_PGHOST:$RT_PGPORT) ==" | tee "$LOG"

# ============================================================================
# ISOLATION PROOFS — admin and runtime connections are the SAME isolated
# cluster; failures here abort before any attack probe runs.
# ============================================================================
echo "-- 0. Isolation proofs (admin/runtime same-cluster; fail closed) --" | tee -a "$LOG"

admin postgres postgres "SELECT 1" >/dev/null 2>&1 || { echo "::error::admin psql unavailable" >&2; exit 1; }

# IP-01: the admin connection really is the postgres superuser.
admin_expect IP-01_ADMIN_IS_SUPERUSER postgres "SELECT rolsuper::text FROM pg_roles WHERE rolname=current_user AND current_user='postgres' AND session_user='postgres'" "true"
require_isolation

# IP-02/03: identical cluster system identifier on BOTH connections.
admin_sysid=$(admin postgres postgres "SELECT system_identifier::text FROM pg_control_system()") || { echo "::error::admin system_identifier read failed" >&2; exit 1; }
[ "$admin_sysid" = "$RT_EXPECTED_SYSID" ] || { echo "::error::admin system_identifier does not match disposable provisioning" >&2; exit 1; }
expect_value IP-02_RUNTIME_SYSID "SELECT system_identifier::text FROM pg_control_system()" "$admin_sysid"
require_isolation

# IP-04: identical postmaster start time on both connections.
admin_start=$(admin postgres postgres "SELECT pg_postmaster_start_time()::text") || { echo "::error::admin start time read failed" >&2; exit 1; }
expect_value IP-03_POSTMASTER_START "SELECT pg_postmaster_start_time()::text" "$admin_start"
require_isolation

# IP-04: both connections serve exactly RT_PGPORT.
admin_port=$(admin postgres postgres "SHOW port") || { echo "::error::admin port read failed" >&2; exit 1; }
[ "$admin_port" = "$RT_PGPORT" ] || { echo "::error::admin cluster port $admin_port != $RT_PGPORT" >&2; exit 1; }
expect_value IP-04_PORT_CONSISTENT "SELECT current_setting('port')" "$RT_PGPORT"

require_isolation

# IP-07: runtime connection identity is exactly the restricted role.
expect_value IP-07_RUNTIME_IDENTITY "SELECT current_user||'|'||session_user" "sanad|sanad"

# IP-08: runtime role privilege contract (all five flags false).
expect_value IP-08_RUNTIME_CONTRACT "SELECT rolsuper::text||'|'||rolbypassrls::text||'|'||rolcreatedb::text||'|'||rolcreaterole::text||'|'||rolinherit::text FROM pg_roles WHERE rolname=current_user" "false|false|false|false|false"

# IP-09: cluster purity — no user databases other than postgres + separation_cert.
admin_expect IP-09_CLUSTER_PURITY postgres "SELECT count(*) FROM pg_database WHERE datistemplate=false AND datname NOT IN ('postgres','separation_cert')" "0"

# IP-10: certification database is migrator-owned.
admin_expect IP-10_DB_OWNER_MIGRATOR postgres "SELECT pg_get_userbyid(datdba) FROM pg_database WHERE datname='$RT_DB'" "sanad_migrator"

# IP-11: separation-certification roles present (battery runs AFTER S0-S9).
admin_expect IP-11_SEP_ROLES_PRESENT postgres "SELECT count(*) FROM pg_roles WHERE rolname IN ('sanad','sanad_migrator','sanad_callback_replay_cleanup','sanad_control_plane')" "4"

# IP-12: Flyway head is the certified head.
admin_expect IP-12_FLYWAY_HEAD "$RT_DB" "SELECT CASE WHEN EXISTS (SELECT 1 FROM public.flyway_schema_history WHERE NOT success) THEN 'FAILED_MIGRATION' ELSE coalesce((SELECT version FROM public.flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1),'MISSING') END" "20261118.2"
require_isolation

# IP-05/06: database-level sentinel — written by admin, read back by the
# runtime on a NEW connection, then RESET and proven absent. Proves both
# connections address the same database object, not merely the same port.
SENTINEL="rt-$(openssl rand -hex 16)"
[[ "$SENTINEL" =~ ^rt-[0-9a-f]{32}$ ]] || exit 1
admin postgres "$RT_DB" "ALTER DATABASE $RT_DB SET snad.rt_battery_sentinel = '$SENTINEL'" >/dev/null || { echo "::error::sentinel set failed" >&2; exit 1; }
expect_value IP-05_SENTINEL_READBACK "SELECT current_setting('snad.rt_battery_sentinel', true)" "$SENTINEL"
admin postgres "$RT_DB" "ALTER DATABASE $RT_DB RESET snad.rt_battery_sentinel" >/dev/null || { echo "::error::sentinel reset failed" >&2; exit 1; }
expect_value IP-06_SENTINEL_CLEARED "SELECT coalesce(current_setting('snad.rt_battery_sentinel', true),'')" ""

require_isolation

# Record the isolation facts for the evidence package (facts, not passes).
{
  echo "battery=v2"
  echo "started_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "admin_sysid=$admin_sysid"
  echo "runtime_sysid=verified-identical (IP-02)"
  echo "postmaster_start=$admin_start"
  echo "port=$RT_PGPORT (admin and runtime)"
  echo "sentinel=$SENTINEL (set, runtime-read-back, reset, absence proven)"
  echo "admin_superuser=postgres (IP-01)"
  echo "runtime_role=sanad (IP-07, IP-08)"
  echo "db=$RT_DB owner=sanad_migrator (IP-10)"
  echo "flyway_head=20261118.2 (IP-12)"
  echo "server_version=$(admin postgres postgres 'SELECT version()')"
  echo "psql_version=$(psql --version)"
} >> "$ISO"

# Ephemeral runtime password: rotate AFTER every isolation proof, then prove
# authentication works before any attack probe depends on it.
RT_PW="$(openssl rand -hex 24)"
[[ "$RT_PW" =~ ^[0-9a-fA-F]{48}$ ]] || { echo "::error::password generation failed" >&2; exit 1; }
admin_secret "ALTER ROLE sanad PASSWORD '$RT_PW';" || { echo "::error::runtime password setup failed" >&2; exit 1; }
rt "SELECT 1" ""
[ "$rc" -eq 0 ] && [ "$out" = "1" ] || { echo "::error::runtime authentication smoke test failed (rc=$rc,state=$state)" >&2; exit 1; }

# ============================================================================
# A. RLS trust boundary and bypass attempts
# ============================================================================
echo "-- A. RLS trust boundary and bypass attempts --" | tee -a "$LOG"

# RT-01: hostile session SETs the tenant GUC. Expected value is computed by
# the admin on the same cluster — the exposure is confirmed WITH the exact
# visible row count, not merely by rc=0.
expected_rt01=$(admin postgres "$RT_DB" "SELECT count(*) FROM public.users WHERE tenant_id='$TB'") || { echo "::error::RT-01 expected-value query failed" >&2; exit 1; }
[[ "$expected_rt01" =~ ^[1-9][0-9]*$ ]] || { echo "::error::RT-01 foreign tenant fixture missing" >&2; exit 1; }
expect_confirmed_value RT-01_GUC_NOT_AUTHENTICATED "SET app.tenant_id='$TB'; SELECT count(*) FROM public.users WHERE tenant_id='$TB'" "$expected_rt01"

expect_denied RT-02_RLS_TENANT_SWAP "UPDATE public.users SET tenant_id='$TB' WHERE tenant_id='$TA'" 42501 "$TA"
# Storage-level proof the swap attempt changed nothing (RLS could mask it).
admin_expect RT-02B_SWAP_NO_EFFECT "$RT_DB" "SELECT count(*) FROM public.users WHERE id='$UA' AND tenant_id='$TA'" "1"

expect_value RT-03_RLS_CROSS_UPDATE "WITH changed AS (UPDATE public.users SET display_name='RT' WHERE tenant_id='$TB' RETURNING 1) SELECT count(*) FROM changed" "0" "$TA"
admin_expect RT-03B_CROSS_UPDATE_NO_EFFECT "$RT_DB" "SELECT count(*) FROM public.users WHERE id='$UB' AND tenant_id='$TB' AND display_name IS NULL" "1"

expected_rt04=$(admin postgres "$RT_DB" "SELECT count(DISTINCT tenant_id) FROM public.login_tenant_index") || { echo "::error::RT-04 expected-value query failed" >&2; exit 1; }
expect_confirmed_value RT-04_PREAUTH_ENUM "SELECT count(DISTINCT tenant_id) FROM public.login_tenant_index" "$expected_rt04"

expected_rt05=$(admin postgres "$RT_DB" "SELECT count(*) FROM public.auth_token_tenant_index") || { echo "::error::RT-05 expected-value query failed" >&2; exit 1; }
expect_confirmed_value RT-05_PREAUTH_TOKEN_INDEX "SELECT count(*) FROM public.auth_token_tenant_index" "$expected_rt05"

expect_denied RT-06_PREAUTH_TOKEN_READONLY "INSERT INTO public.workflow_external_action_token_index (token_hash,action_id,tenant_id,created_at) VALUES ('deadbeef','88888888-8888-8888-8888-888888888888','$TA',now())" 42501
admin_expect RT-06B_TOKEN_INDEX_UNCHANGED "$RT_DB" "SELECT count(*) FROM public.workflow_external_action_token_index WHERE token_hash='deadbeef'" "0"

# ============================================================================
# B. Control-plane write surface (RLS-off tables) — V20261118_1/V20261118_2
#    closed the tenants and access_capabilities lifecycle DML for the plain
#    runtime role: every former CONFIRMED exposure is now asserted DENIED
#    (42501) with storage-level absence proofs. The compensating control
#    (SET ROLE sanad_control_plane) is verified positively by RT-36/RT-37.
# ============================================================================
echo "-- B. Control-plane write surface (RLS-off tables; lifecycle DML denied for plain runtime) --" | tee -a "$LOG"

expect_denied RT-07_TENANTS_INSERT "WITH ins AS (INSERT INTO public.tenants (id,name,subdomain,status,created_at,updated_at) VALUES ('$TT','RT-PROBE','rt-probe','ACTIVE',now(),now()) RETURNING id) SELECT count(*) FROM ins" 42501
expect_value RT-07B_TENANTS_READBACK_ABSENT "SELECT coalesce((SELECT name||'|'||status FROM public.tenants WHERE id='$TT'),'ABSENT')" "ABSENT"
admin_expect RT-07C_TENANTS_ADMINPROOF "$RT_DB" "SELECT count(*) FROM public.tenants WHERE id='$TT' AND name='RT-PROBE' AND status='ACTIVE'" "0"

expect_denied RT-08_TENANTS_UPDATE "WITH upd AS (UPDATE public.tenants SET status='SUSPENDED', suspension_reason='RT-PROBE' WHERE id='$TT' RETURNING status) SELECT count(*)||'|'||max(status) FROM upd" 42501
admin_expect RT-08B_TENANTS_UPDATE_ADMINPROOF "$RT_DB" "SELECT coalesce((SELECT status||'|'||suspension_reason FROM public.tenants WHERE id='$TT'),'ROW_ABSENT')" "ROW_ABSENT"

expect_denied RT-09_TENANTS_DELETE "WITH del AS (DELETE FROM public.tenants WHERE id='$TT' RETURNING 1) SELECT count(*) FROM del" 42501
admin_expect RT-09B_TENANTS_DELETE_ADMINPROOF "$RT_DB" "SELECT count(*) FROM public.tenants WHERE id='$TT'" "0"

expect_confirmed_value RT-10_QUOTA_INSERT_OWN "WITH ins AS (INSERT INTO public.tenant_quota (tenant_id,dimension,limit_value,reset_at) VALUES ('$TA','API_RPM',1,now()+interval '30 days') RETURNING tenant_id) SELECT count(*)||'|'||max(tenant_id) FROM ins" "1|$TA" "$TA"
expect_denied RT-10B_QUOTA_INSERT_FOREIGN "INSERT INTO public.tenant_quota (tenant_id,dimension,limit_value,reset_at) VALUES ('$TB','API_RPM',1,now()+interval '30 days')" 42501 "$TA"
admin_expect RT-10C_QUOTA_FOREIGN_ABSENT "$RT_DB" "SELECT count(*) FROM public.tenant_quota WHERE tenant_id='$TB' AND dimension='API_RPM'" "0"

expect_confirmed_value RT-11_QUOTA_DELETE_OWN "WITH del AS (DELETE FROM public.tenant_quota WHERE tenant_id='$TA' AND dimension='API_RPM' RETURNING 1) SELECT count(*) FROM del" "1" "$TA"
admin_expect RT-11B_QUOTA_DELETE_ADMINPROOF "$RT_DB" "SELECT count(*) FROM public.tenant_quota WHERE tenant_id='$TA' AND dimension='API_RPM'" "0"

expect_denied RT-12_CAPS_INSERT "WITH ins AS (INSERT INTO public.access_capabilities (id,code,name,created_at,updated_at) VALUES ('$CAP','RT.FAKE','RT probe',now(),now()) RETURNING code) SELECT count(*)||'|'||max(code) FROM ins" 42501
expect_denied RT-13_CAPS_UPDATE "WITH upd AS (UPDATE public.access_capabilities SET status='INACTIVE' WHERE code='RT.FAKE' RETURNING status) SELECT count(*)||'|'||max(status) FROM upd" 42501
admin_expect RT-13B_CAPS_UPDATE_ADMINPROOF "$RT_DB" "SELECT count(*) FROM public.access_capabilities WHERE code='RT.FAKE'" "0"
expect_denied RT-14_CAPS_DELETE "WITH del AS (DELETE FROM public.access_capabilities WHERE code='RT.FAKE' RETURNING 1) SELECT count(*) FROM del" 42501
admin_expect RT-14B_CAPS_DELETE_ADMINPROOF "$RT_DB" "SELECT count(*) FROM public.access_capabilities WHERE code='RT.FAKE'" "0"

expected_rt15=$(admin postgres "$RT_DB" "SELECT count(*) FROM public.modules") || { echo "::error::RT-15 expected-value query failed" >&2; exit 1; }
expect_confirmed_value RT-15_GLOBAL_REGISTRY_READ "SELECT count(*) FROM public.modules" "$expected_rt15"

# ============================================================================
# C. Privilege escalation
# ============================================================================
echo "-- C. Privilege escalation --" | tee -a "$LOG"
expect_denied RT-16_CREATE_ROLE "CREATE ROLE rt_escalate" 42501
expect_denied RT-17_GRANT_MIGRATOR "GRANT sanad_migrator TO sanad" 42501
expect_denied RT-18_SET_ROLE_MIGRATOR "SET ROLE sanad_migrator" 42501
expect_denied RT-19_ALTER_DATABASE "ALTER DATABASE $RT_DB SET sanad.deployment_topology='fixture'" 42501
expect_denied RT-20_CREATE_FUNCTION "CREATE FUNCTION public.rt_f() RETURNS void LANGUAGE sql AS \$\$ SELECT 1 \$\$" 42501
# pg_temp shadow (CI parity): create, seed, call and drop in ONE fail-closed
# statement batch — any step failing fails the probe (no swallowed errors).
expect_not_value RT-21_TEMP_SHADOW_NEGATIVE "CREATE TEMP TABLE hr_employees (tenant_id uuid, hire_date date); INSERT INTO pg_temp.hr_employees VALUES ('$TA','2099-12-31'::date); SELECT public.hr_default_migration_as_of_date('$TA')::text; DROP TABLE pg_temp.hr_employees" "2099-12-31" "$TA"

# ============================================================================
# D. Audit-ledger tampering
# ============================================================================
echo "-- D. Audit-ledger tampering --" | tee -a "$LOG"
ledger_before=$(admin postgres "$RT_DB" "SELECT count(*) FROM public.platform_audit_logs") || { echo "::error::ledger baseline query failed" >&2; exit 1; }
expect_denied RT-22_AUDIT_UPDATE "UPDATE public.platform_audit_logs SET reason='FORGED'" 42501 "$TA"
expect_denied RT-23_AUDIT_DELETE "DELETE FROM public.platform_audit_logs" 42501 "$TA"
expect_denied RT-24_AUDIT_TRUNCATE "TRUNCATE TABLE public.platform_audit_logs" 42501 "$TA"
expect_denied RT-25_AUDIT_ADD_TRIGGER "CREATE TRIGGER rt_t BEFORE UPDATE ON public.platform_audit_logs FOR EACH ROW EXECUTE FUNCTION public.sync_login_tenant_index()" 42501 "$TA"
admin_expect RT-24B_LEDGER_COUNT_STABLE "$RT_DB" "SELECT count(*) FROM public.platform_audit_logs" "$ledger_before"

# RT-26: forged INSERT — exact affected-row count AND field-level read-back.
expect_confirmed_value RT-26_AUDIT_FORGED_INSERT "WITH ins AS (INSERT INTO public.platform_audit_logs (id,actor_tenant_id,action,resource_type,resource_id,result,created_at) VALUES ('$AUD','$TA','RT_FORGED_PROBE','redteam','rt','SUCCESS',now()) RETURNING action) SELECT count(*)||'|'||max(action) FROM ins" "1|RT_FORGED_PROBE" "$TA"
admin_expect RT-26B_ADMIN_VERIFIED "$RT_DB" "SELECT id||'|'||actor_tenant_id||'|'||action||'|'||result FROM public.platform_audit_logs WHERE id='$AUD'" "$AUD|$TA|RT_FORGED_PROBE|SUCCESS"

expect_denied RT-27_CLEANUP_ROLE_SELECT "SET ROLE sanad_callback_replay_cleanup; SELECT count(*) FROM public.platform_audit_logs" 42501
expect_denied RT-28_CLEANUP_ROLE_UPDATE "SET ROLE sanad_callback_replay_cleanup; UPDATE public.platform_audit_logs SET reason='X'" 42501

# ============================================================================
# E. Fake-success / migration-gate forgery
# ============================================================================
echo "-- E. Fake-success / migration-gate forgery --" | tee -a "$LOG"
expect_denied RT-29_FLYWAY_HISTORY_INSERT "INSERT INTO public.flyway_schema_history (installed_rank,version,description,type,script,success) VALUES (9999,'20990101.1','rt','SQL','rt.sql',true)" 42501
admin_expect RT-29B_FAKE_MIGRATION_ABSENT "$RT_DB" "SELECT count(*) FROM public.flyway_schema_history WHERE version='20990101.1'" "0"
expect_denied RT-30_FLYWAY_HISTORY_UPDATE "UPDATE public.flyway_schema_history SET success=true WHERE version='20261113.1'" 42501
expect_confirmed_value RT-31_SESSION_TOPOLOGY_OVERRIDE "SET sanad.deployment_topology='fixture'; SELECT current_setting('sanad.deployment_topology')" "fixture"
# The override is session-scoped only: a FRESH runtime session still sees the
# authoritative database-level topology the S10 gate reads.
expect_value RT-31B_CATALOG_TOPOLOGY_AUTHORITATIVE "SELECT current_setting('sanad.deployment_topology')" "separated"

# ============================================================================
# F. Definer function invocation (re-confirmed samples)
# ============================================================================
echo "-- F. Definer function invocation (re-confirmed samples) --" | tee -a "$LOG"
expect_denied RT-32_DEFINER_FOREIGN_TENANT_REPORT "SELECT public.hr_reconcile_tenant_report('$TB')" 42501 "$TA"
expect_denied RT-33_DEFINER_FOREIGN_TENANT_PRECHECK "SELECT public.hr_precheck_tenant('$TB')" 42501 "$TA"

# ============================================================================
# G. Separation invariants + service_role classification
# ============================================================================
echo "-- G. Separation invariants + service_role classification --" | tee -a "$LOG"
expect_value RT-34_NO_MIGRATOR_MEMBERSHIP "SELECT pg_has_role('sanad','sanad_migrator','MEMBER')" "f"
# RT-35: LOCAL isolated-cluster classification, asserted exactly.
admin_expect RT-35_SERVICE_ROLE_LOCAL_ABSENT postgres "SELECT count(*) FROM pg_roles WHERE rolname='service_role'" "0"

# ============================================================================
# H. ORDER 11 dedicated control-plane connection (V20261118_1/V20261118_2):
#    the dedicated connection identity performs the exact tenant and
#    capability lifecycles with read-backs on the RUNTIME connection (which
#    keeps SELECT — proving storage, not policy-invisibility); the runtime
#    SET ROLE escalation attempt is DENIED (no membership exists); the
#    effective ACL matrix proves the plain runtime role is SELECT-only on
#    both control-plane tables while the dedicated identity holds exactly
#    SELECT/INSERT/UPDATE/DELETE; and connection identities are isolated in
#    both directions (no pool privilege leakage).
# ============================================================================
echo "-- H. ORDER 11: dedicated control-plane connection lifecycle + SET ROLE denial + identity isolation --" | tee -a "$LOG"

# RT-36: tenant lifecycle on the DEDICATED control-plane connection.
expect_cp_value RT-36_TENANTS_CP_INSERT "WITH ins AS (INSERT INTO public.tenants (id,name,subdomain,status,created_at,updated_at) VALUES ('$TT','RT-CP','rt-cp','ACTIVE',now(),now()) RETURNING 1) SELECT count(*) FROM ins" "1"
expect_value RT-36B_TENANTS_RUNTIME_READBACK "SELECT name||'|'||status||'|'||subdomain FROM public.tenants WHERE id='$TT'" "RT-CP|ACTIVE|rt-cp"
expect_cp_value RT-36C_TENANTS_CP_UPDATE "WITH upd AS (UPDATE public.tenants SET status='SUSPENDED', suspension_reason='RT-CP' WHERE id='$TT' RETURNING status) SELECT count(*)||'|'||max(status) FROM upd" "1|SUSPENDED"
expect_cp_value RT-36D_TENANTS_CP_DELETE "WITH del AS (DELETE FROM public.tenants WHERE id='$TT' RETURNING 1) SELECT count(*) FROM del" "1"
admin_expect RT-36E_TENANTS_ADMINPROOF "$RT_DB" "SELECT count(*) FROM public.tenants WHERE id='$TT'" "0"

# RT-37: capability-registry lifecycle on the DEDICATED connection.
expect_cp_value RT-37_CAPS_CP_INSERT "WITH ins AS (INSERT INTO public.access_capabilities (id,code,name,created_at,updated_at) VALUES ('$CAP','RT.CP','RT cp probe',now(),now()) RETURNING code) SELECT count(*)||'|'||max(code) FROM ins" "1|RT.CP"
expect_value RT-37B_CAPS_RUNTIME_READBACK "SELECT count(*) FROM public.access_capabilities WHERE id='$CAP' AND code='RT.CP' AND status='ACTIVE'" "1"
expect_cp_value RT-37C_CAPS_CP_UPDATE "WITH upd AS (UPDATE public.access_capabilities SET status='INACTIVE' WHERE id='$CAP' RETURNING status) SELECT count(*)||'|'||max(status) FROM upd" "1|INACTIVE"
expect_cp_value RT-37D_CAPS_CP_DELETE "WITH del AS (DELETE FROM public.access_capabilities WHERE id='$CAP' RETURNING 1) SELECT count(*) FROM del" "1"
admin_expect RT-37E_CAPS_ADMINPROOF "$RT_DB" "SELECT count(*) FROM public.access_capabilities WHERE id='$CAP'" "0"

# RT-38: effective ACL matrix — runtime SELECT-only; dedicated identity
# holds exactly SELECT/INSERT/UPDATE/DELETE (SELECT is the WHERE-keyed DML
# requirement whose absence caused the ORDER 11 42501/25P02 chain).
expect_value RT-38_COMPENSATING_CONTROL_VERIFIED "SELECT has_table_privilege('sanad','public.tenants','SELECT')::text||'|'||has_table_privilege('sanad','public.tenants','INSERT')::text||'|'||has_table_privilege('sanad','public.tenants','UPDATE')::text||'|'||has_table_privilege('sanad','public.tenants','DELETE')::text||'|'||has_table_privilege('sanad_control_plane','public.tenants','SELECT')::text||'|'||has_table_privilege('sanad_control_plane','public.tenants','INSERT')::text||'|'||has_table_privilege('sanad_control_plane','public.tenants','UPDATE')::text||'|'||has_table_privilege('sanad_control_plane','public.tenants','DELETE')::text||'|'||has_table_privilege('sanad','public.access_capabilities','SELECT')::text||'|'||has_table_privilege('sanad','public.access_capabilities','INSERT')::text||'|'||has_table_privilege('sanad_control_plane','public.access_capabilities','SELECT')::text||'|'||has_table_privilege('sanad_control_plane','public.access_capabilities','INSERT')::text||'|'||has_table_privilege('sanad_control_plane','public.access_capabilities','UPDATE')::text||'|'||has_table_privilege('sanad_control_plane','public.access_capabilities','DELETE')::text" "true|false|false|false|true|true|true|true|true|false|true|true|true|true"

# RT-39: ORDER 11 — the runtime SET ROLE escalation attempt is DENIED
# (42501: no membership exists in any direction), and the membership
# matrix confirms the absence of every escalation path.
expect_denied RT-39_SETROLE_CP_DENIED "SET ROLE sanad_control_plane" 42501
expect_value RT-39B_MEMBERSHIP_ABSENT "SELECT pg_has_role('sanad','sanad_control_plane','MEMBER')::text||'|'||pg_has_role('sanad','sanad_control_plane','SET')::text||'|'||pg_has_role('sanad','sanad_control_plane','USAGE')::text" "false|false|false"
admin_expect RT-39C_REVERSE_MEMBERSHIP_ABSENT "$RT_DB" "SELECT count(*) FROM pg_auth_members m JOIN pg_roles g ON g.oid=m.roleid JOIN pg_roles r ON r.oid=m.member WHERE (g.rolname='sanad_control_plane' AND r.rolname IN ('sanad','sanad_migrator')) OR (g.rolname IN ('sanad','sanad_migrator') AND r.rolname='sanad_control_plane')" "0"

# RT-40: ORDER 11 connection-identity isolation (pool privilege-leakage
# denial, both directions, live): the dedicated connection authenticates as
# sanad_control_plane, the runtime connection as sanad, and neither can
# observe or acquire the other identity.
expect_cp_value RT-40_CP_CONNECTION_IDENTITY "SELECT current_user||'|'||session_user" "sanad_control_plane|sanad_control_plane"
expect_value RT-40B_RUNTIME_IDENTITY_ISOLATED "SELECT current_user||'|'||session_user||'|'||current_setting('role')" "sanad|sanad|none"

# service_role classification (recorded, NOT a pass):
#  - LOCAL: asserted absent above (the separated topology has no Supabase
#    service_role by design; local privilege claims about it are impossible).
#  - PRODUCTION: UNVERIFIED — no authorized production connection exists in
#    this engagement; nothing is probed, executed or claimed against any
#    production system. Historical read-only facts stand in doc 15 (§7,
#    2026-11-13: service_role EXECUTE on 11/12 definer functions); production
#    re-verification remains a G1 condition (doc 19 §4) and is BLOCKED.
{
  echo "service_role classification (battery v2, $(date -u +%Y-%m-%dT%H:%M:%SZ))"
  if grep -q $'^RT-35_SERVICE_ROLE_LOCAL_ABSENT\t0\t0\t' "$TSV"; then
    echo "LOCAL-ISOLATED-CLUSTER: VERIFIED-ABSENT (RT-35: pg_roles count=0, exact)"
  else
    echo "LOCAL-ISOLATED-CLUSTER: UNVERIFIED (RT-35 failed)"
  fi
  echo "PRODUCTION: UNVERIFIED — no authorized production connection in this engagement;"
  echo "  no production probe executed or claimed; production re-verification is a G1"
  echo "  production-readiness condition (doc 19 §4) and remains BLOCKED."
  echo "HISTORICAL-PRODUCTION-FACTS (doc 15, read-only, 2026-11-13): service_role EXECUTE 11/12."
  echo "This record is a classification, not a battery pass, and accepts no risk."
} > "$SRC"

# ============================================================================
# Verdict — fail closed on any failure AND on any skipped probe.
# ============================================================================
probes=$(wc -l < "$TSV")
{
  echo ""
  echo "REDTEAM_PROBES=$probes EXPECTED_PROBES=$EXPECTED_PROBES FAILURES=$FAILURES"
} | tee -a "$LOG"
if [ "$probes" -ne "$EXPECTED_PROBES" ]; then
  echo "REDTEAM_BATTERY=INCOMPLETE (probe count $probes != $EXPECTED_PROBES — a probe was skipped or duplicated)" | tee -a "$LOG"
  echo "::error::probe-count assertion failed: $probes != $EXPECTED_PROBES" >&2
  exit 1
fi
if [ "$FAILURES" -eq 0 ]; then
  echo "REDTEAM_BATTERY=COMPLETE (all $probes probes executed with exact expected outcomes)" | tee -a "$LOG"
else
  echo "REDTEAM_BATTERY=WITH_FAILURES ($FAILURES)" | tee -a "$LOG"
fi
# The forged audit row (RT-26) is intentionally left in place on the disposable
# database as tamper-attempt evidence; the cluster is destroyed with it.
exit "$FAILURES"
