#!/usr/bin/env bash
# ============================================================================
# G1/C4 S10 — PROTECTED DEPLOYMENT ENFORCEMENT: 7-case self-test of the
# append-only post-migration security gate
# (scripts/production/verify-append-only-gate.sh) on an ISOLATED PostgreSQL
# cluster. No production access. Fail closed everywhere.
#
# Cases (per the S10 acceptance contract):
#   1 separated            full chain on a separated-topology DB  -> gate PASS
#   2 migration-fixture    disposable topology (validate-only)    -> gate FAIL
#   3 missing topology     no database-level GUC                  -> gate FAIL
#   4 empty topology       GUC set to '' (chain itself fails)     -> gate FAIL
#   5 unknown topology     GUC set to a bogus value (chain fails) -> gate FAIL
#   6 session-level override  PGOPTIONS topology injection        -> gate FAIL
#   7 inherited privilege bypass  hostile grant to a member role  -> gate FAIL
#                          (and the gate must PASS again after revocation)
#
# Preconditions: an isolated cluster (loopback only) that already provisioned
# the sanad/sanad_migrator/sanad_callback_replay_cleanup roles, e.g. by
# tests/security/migrator-separation/run-separation-cert.sh S0 on the same
# runner. This script generates its own ephemeral passwords for the isolated
# identities; they are masked and never written to the artifacts.
#
# Environment:
#   S10_PGHOST (127.0.0.1)  S10_PGPORT (55432)
#   S10_ADMIN_PSQL (psql -h $S10_PGHOST -p $S10_PGPORT -U postgres)
#   S10_FLYWAY (/tmp/flyway/flyway)  S10_REPO (repo root, auto-detected)
#   S10_ARTIFACTS (/tmp/s10-gate-artifacts)
# ============================================================================
set -uo pipefail
export LC_ALL=C.UTF-8

S10_PGHOST="${S10_PGHOST:-127.0.0.1}"
S10_PGPORT="${S10_PGPORT:-55432}"
S10_ADMIN_PSQL="${S10_ADMIN_PSQL:-psql -h ${S10_PGHOST} -p ${S10_PGPORT} -U postgres}"
S10_FLYWAY="${S10_FLYWAY:-/tmp/flyway/flyway}"
S10_ARTIFACTS="${S10_ARTIFACTS:-/tmp/s10-gate-artifacts}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
S10_REPO="${S10_REPO:-$(cd "$SCRIPT_DIR/../../.." && pwd)}"
MIG_LOC="$S10_REPO/apps/sanad-platform/src/main/resources/db/migration"
VEN_LOC="$S10_REPO/apps/sanad-platform/src/main/resources/db/vendor/postgresql"
LOCS="filesystem:$MIG_LOC,filesystem:$VEN_LOC"
GATE="$S10_REPO/scripts/production/verify-append-only-gate.sh"

# Isolated-cluster guard: refuse anything but loopback.
case "$S10_PGHOST" in
  127.0.0.1|localhost|::1) ;;
  *) echo "::error::S10: refusing non-loopback S10_PGHOST=$S10_PGHOST" >&2; exit 1 ;;
esac

umask 077
[ ! -L "$S10_ARTIFACTS" ] || { echo "::error::S10: artifacts path is a symlink" >&2; exit 1; }
if [ -e "$S10_ARTIFACTS" ]; then
  [ -d "$S10_ARTIFACTS" ] && [ -z "$(ls -A "$S10_ARTIFACTS")" ] || {
    echo "::error::S10: artifacts directory already contains data; choose a fresh S10_ARTIFACTS" >&2
    exit 1
  }
else
  mkdir -m 700 -p "$S10_ARTIFACTS" || exit 1
fi
TSV="$S10_ARTIFACTS/s10-matrix.tsv"; : > "$TSV"
SUMMARY="$S10_ARTIFACTS/summary.md"
CASES_TOTAL=0
CASES_PASS=0

record() { # record <case> <expected> <actual> <detail>
  printf '%s\t%s\t%s\t%s\n' "$1" "$2" "$3" "$(echo "$4" | tr '\n' ' ' | cut -c1-200)" >> "$TSV"
  echo "  [$1] expected=$2 got=$3 | $(echo "$4" | head -1 | cut -c1-140)"
}
case_result() { # case_result <case> <expected> <actual> <detail>
  CASES_TOTAL=$((CASES_TOTAL+1))
  record "$@"
  if [ "$2" = "$3" ]; then CASES_PASS=$((CASES_PASS+1)); fi
}

admin() { $S10_ADMIN_PSQL -X -v ON_ERROR_STOP=1 -d "$2" -Atc "$3"; }

# --- isolated identities -----------------------------------------------------
for r in sanad sanad_migrator sanad_callback_replay_cleanup; do
  n=$(admin postgres postgres "SELECT count(*) FROM pg_roles WHERE rolname='$r'") || {
    echo "::error::S10: role inventory failed; run the separation certification (S0) first on this isolated cluster" >&2; exit 1; }
  [ "$n" = "1" ] || { echo "::error::S10: role $r missing; run run-separation-cert.sh S0 first (isolated cluster only)" >&2; exit 1; }
done

# Ephemeral passwords for the isolated identities (masked, never in artifacts).
MIG_PW="$(openssl rand -hex 24)"
AUD_PW="$(openssl rand -hex 24)"
echo "::add-mask::$MIG_PW" 2>/dev/null || true
echo "::add-mask::$AUD_PW" 2>/dev/null || true
admin postgres postgres "ALTER ROLE sanad_migrator PASSWORD '$MIG_PW';" >/dev/null || { echo "::error::S10: migrator password" >&2; exit 1; }
admin postgres postgres "ALTER ROLE sanad_release_auditor PASSWORD '$AUD_PW';" >/dev/null 2>&1 || \
  admin postgres postgres "CREATE ROLE sanad_release_auditor LOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE PASSWORD '$AUD_PW';" >/dev/null || { echo "::error::S10: auditor provisioning" >&2; exit 1; }
n=$(admin postgres postgres "SELECT count(*) FROM pg_roles WHERE rolname='sanad_release_auditor' AND NOT rolsuper AND NOT rolbypassrls AND NOT rolcreatedb AND NOT rolcreaterole") || exit 1
[ "$n" = "1" ] || { echo "::error::S10: sanad_release_auditor contract invalid" >&2; exit 1; }

flyway_migrate() { # flyway_migrate <db> <logfile>
  # Flyway environment credential channel only: the password never appears
  # in CLI arguments or process listings (same contract as S1).
  env FLYWAY_PASSWORD="$MIG_PW" "$S10_FLYWAY" -url="jdbc:postgresql://$S10_PGHOST:$S10_PGPORT/$1" \
    -user=sanad_migrator -connectRetries=10 \
    -locations="$LOCS" migrate > "$2" 2>&1
}


# Operator change-window (documented separated-topology flow): the migration
# chain pauses after V20261124.1 (tables + protocol functions exist, enforcement
# not yet armed). The operator provisions a per-run ACTIVE signing key and the
# enforcement flag, then the chain resumes. Flyway credentials stay in env;
# the key value never appears in CLI arguments or logs (stdin psql vars only).
provision_separated_change_window() { # provision_separated_change_window <db> <logfile>
  env FLYWAY_PASSWORD="$MIG_PW" "$S10_FLYWAY" -url="jdbc:postgresql://$S10_PGHOST:$S10_PGPORT/$1" \
    -user=sanad_migrator -connectRetries=10 \
    -locations="$LOCS" -target=20261124.1 migrate >> "$2" 2>&1 || return 1
  local op_key
  op_key="$(openssl rand -hex 32)"
  $S10_ADMIN_PSQL -X -v ON_ERROR_STOP=1 -d "$1" -v op_key="$op_key" <<'S10SQL'
  INSERT INTO public.platform_tenant_ctx_keys (key_id, secret, status)
  VALUES ('s10-operator', decode(:'op_key', 'hex'), 'ACTIVE')
  ON CONFLICT (key_id) DO UPDATE
    SET status = 'ACTIVE', retired_at = NULL, secret = EXCLUDED.secret;
  INSERT INTO public.platform_ctx_enforce (flag, enabled_at, enabled_by)
  VALUES ('ON', now(), 's10-operator-change-window')
  ON CONFLICT (flag) DO NOTHING;
S10SQL
}

grant_auditor() { # per-DB read-only audit grants (isolated rehearsal only)
  admin postgres "$1" "GRANT USAGE ON SCHEMA public TO sanad_release_auditor;
GRANT SELECT ON TABLE public.flyway_schema_history TO sanad_release_auditor;" >/dev/null || {
    echo "::error::S10: auditor grants failed on $1" >&2; exit 1; }
}

run_gate() { # run_gate <db> <logfile> [extra env]
  local db="$1" log="$2"
  shift 2
  env "$@" PGSSLMODE=disable \
    AUDIT_DATABASE_URL="postgresql://$S10_PGHOST:$S10_PGPORT/$db" \
    AUDIT_DATABASE_USERNAME=sanad_release_auditor \
    AUDIT_DATABASE_PASSWORD="$AUD_PW" \
    bash "$GATE" > "$log" 2>&1
}

fresh_db() { # fresh_db <db> [topology]
  admin postgres postgres "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='$1' AND pid<>pg_backend_pid();" >/dev/null 2>&1 || true
  admin postgres postgres "DROP DATABASE IF EXISTS $1;" >/dev/null || { echo "::error::S10: drop $1 failed" >&2; exit 1; }
  admin postgres postgres "CREATE DATABASE $1 OWNER sanad_migrator;" >/dev/null || { echo "::error::S10: create $1 failed" >&2; exit 1; }
  if [ "${2:-}" != "" ]; then
    admin postgres postgres "ALTER DATABASE $1 SET sanad.deployment_topology = '$2';" >/dev/null || { echo "::error::S10: topology on $1 failed" >&2; exit 1; }
  fi
}

echo "GATE [S10] 7-case protected-deployment gate self-test (isolated cluster only)"
echo "GATE [S10] 7-case protected-deployment gate self-test (isolated cluster only)" >> "$SUMMARY"

# --- Case 1: separated (positive control) ------------------------------------
C1=s10_case_separated
fresh_db "$C1" separated
if provision_separated_change_window "$C1" "$S10_ARTIFACTS/case1-flyway.log" \
   && flyway_migrate "$C1" "$S10_ARTIFACTS/case1-flyway.log"; then
  grant_auditor "$C1"
  if run_gate "$C1" "$S10_ARTIFACTS/case1-gate.log"; then
    case_result CASE_1_SEPARATED "GATE_PASS" "GATE_PASS" "full chain + gate PASS on separated topology"
  else
    case_result CASE_1_SEPARATED "GATE_PASS" "GATE_FAIL" "gate unexpectedly failed on a correct separated deployment; see case1-gate.log"
  fi
else
  case_result CASE_1_SEPARATED "GATE_PASS" "FLYWAY_FAIL" "chain failed before the gate could be exercised; see case1-flyway.log"
fi

# --- Case 2: migration-fixture (validate-only, never certifiable) ------------
C2=s10_case_fixture
fresh_db "$C2" migration-fixture
if flyway_migrate "$C2" "$S10_ARTIFACTS/case2-flyway.log"; then
  grant_auditor "$C2"
  if run_gate "$C2" "$S10_ARTIFACTS/case2-gate.log"; then
    case_result CASE_2_MIGRATION_FIXTURE "GATE_FAIL" "GATE_PASS" "UNACCEPTED: gate passed a fixture-topology database"
  else
    case_result CASE_2_MIGRATION_FIXTURE "GATE_FAIL" "GATE_FAIL" "gate correctly refused fixture topology (not a separation certification path)"
  fi
else
  case_result CASE_2_MIGRATION_FIXTURE "GATE_FAIL" "FLYWAY_FAIL" "chain failed on fixture topology; see case2-flyway.log"
fi

# --- Case 3: missing topology (no database-level GUC) -------------------------
C3=s10_case_missing
fresh_db "$C3" ""
if flyway_migrate "$C3" "$S10_ARTIFACTS/case3-flyway.log"; then
  grant_auditor "$C3"
  if run_gate "$C3" "$S10_ARTIFACTS/case3-gate.log"; then
    case_result CASE_3_MISSING_TOPOLOGY "GATE_FAIL" "GATE_PASS" "UNACCEPTED: gate passed with no database-level topology"
  else
    case_result CASE_3_MISSING_TOPOLOGY "GATE_FAIL" "GATE_FAIL" "gate correctly refused a missing topology setting"
  fi
else
  case_result CASE_3_MISSING_TOPOLOGY "GATE_FAIL" "FLYWAY_FAIL" "chain failed with missing topology; see case3-flyway.log"
fi

# --- Case 4: empty topology ('' — chain must fail closed) ---------------------
C4=s10_case_empty
fresh_db "$C4" ""
admin postgres postgres "ALTER DATABASE $C4 SET sanad.deployment_topology = '';" >/dev/null || { echo "::error::S10: empty topology set failed" >&2; exit 1; }
if flyway_migrate "$C4" "$S10_ARTIFACTS/case4-flyway.log"; then
  flyway_outcome=FLYWAY_OK
else
  flyway_outcome=FLYWAY_FAIL
fi
if run_gate "$C4" "$S10_ARTIFACTS/case4-gate.log"; then
  case_result CASE_4_EMPTY_TOPOLOGY "GATE_FAIL" "GATE_PASS" "UNACCEPTED: gate passed an empty topology (flyway=$flyway_outcome)"
else
  case_result CASE_4_EMPTY_TOPOLOGY "GATE_FAIL" "GATE_FAIL" "gate correctly refused empty topology (chain fail-closed=$flyway_outcome)"
fi

# --- Case 5: unknown topology (bogus value — chain must fail closed) ----------
C5=s10_case_unknown
fresh_db "$C5" "totally-unknown-topology"
if flyway_migrate "$C5" "$S10_ARTIFACTS/case5-flyway.log"; then
  flyway_outcome=FLYWAY_OK
else
  flyway_outcome=FLYWAY_FAIL
fi
if run_gate "$C5" "$S10_ARTIFACTS/case5-gate.log"; then
  case_result CASE_5_UNKNOWN_TOPOLOGY "GATE_FAIL" "GATE_PASS" "UNACCEPTED: gate passed an unknown topology (flyway=$flyway_outcome)"
else
  case_result CASE_5_UNKNOWN_TOPOLOGY "GATE_FAIL" "GATE_FAIL" "gate correctly refused unknown topology (chain fail-closed=$flyway_outcome)"
fi

# --- Case 6: session-level override (PGOPTIONS injection) ---------------------
# A fixture-topology database dressed up as 'separated' via ambient
# PGOPTIONS must NOT satisfy the gate: the authoritative source is the
# database-level catalog setting, and session/catalog divergence is rejected.
C6=s10_case_session_override
fresh_db "$C6" migration-fixture
if flyway_migrate "$C6" "$S10_ARTIFACTS/case6-flyway.log"; then
  grant_auditor "$C6"
  if run_gate "$C6" "$S10_ARTIFACTS/case6-gate.log" PGOPTIONS="-c sanad.deployment_topology=separated"; then
    case_result CASE_6_SESSION_OVERRIDE "GATE_FAIL" "GATE_PASS" "UNACCEPTED: a session-level topology override satisfied the gate"
  else
    case_result CASE_6_SESSION_OVERRIDE "GATE_FAIL" "GATE_FAIL" "gate correctly rejected the session-level override (catalog is authoritative)"
  fi
else
  case_result CASE_6_SESSION_OVERRIDE "GATE_FAIL" "FLYWAY_FAIL" "chain failed before the override case; see case6-flyway.log"
fi

# --- Case 7: inherited privilege bypass (hostile grant to a member role) ------
C7=s10_case_inherited_bypass
fresh_db "$C7" separated
if provision_separated_change_window "$C7" "$S10_ARTIFACTS/case7-flyway.log" \
   && flyway_migrate "$C7" "$S10_ARTIFACTS/case7-flyway.log"; then
  grant_auditor "$C7"
  if run_gate "$C7" "$S10_ARTIFACTS/case7-gate-baseline.log"; then
    baseline=PASS
  else
    baseline=FAIL
  fi
  # Inject the hostile grant: a member role of sanad gains ledger UPDATE.
  admin postgres "$C7" "GRANT UPDATE ON TABLE public.platform_audit_logs TO sanad_callback_replay_cleanup;" >/dev/null || {
    echo "::error::S10: hostile grant injection failed" >&2; exit 1; }
  if run_gate "$C7" "$S10_ARTIFACTS/case7-gate-bypass.log"; then
    bypass_result=GATE_PASS
  else
    bypass_result=GATE_FAIL
  fi
  # Restore and prove the gate turns green again (no residue).
  admin postgres "$C7" "REVOKE UPDATE ON TABLE public.platform_audit_logs FROM sanad_callback_replay_cleanup;" >/dev/null || {
    echo "::error::S10: hostile grant revocation failed" >&2; exit 1; }
  if run_gate "$C7" "$S10_ARTIFACTS/case7-gate-restored.log"; then
    restored=PASS
  else
    restored=FAIL
  fi
  if [ "$baseline" = "PASS" ] && [ "$bypass_result" = "GATE_FAIL" ] && [ "$restored" = "PASS" ]; then
    case_result CASE_7_INHERITED_BYPASS "GATE_FAIL+RESTORE_PASS" "GATE_FAIL+RESTORE_PASS" "hostile member-role grant detected and blocked; gate PASS again after revocation"
  else
    case_result CASE_7_INHERITED_BYPASS "GATE_FAIL+RESTORE_PASS" "baseline=$baseline bypass=$bypass_result restored=$restored" "unexpected case-7 behaviour; inspect case7-*.log"
  fi
else
  case_result CASE_7_INHERITED_BYPASS "GATE_FAIL+RESTORE_PASS" "FLYWAY_FAIL" "chain failed; see case7-flyway.log"
fi

# --- verdict + JUnit XML ------------------------------------------------------
{
  echo ""
  echo "## S10 protected-deployment gate self-test — $(date -u +'%Y-%m-%dT%H:%M:%SZ')"
  echo ""
  echo "| case | expected | actual | detail |"
  echo "|---|---|---|---|"
  awk -F'\t' '{printf "| %s | %s | %s | %s |\n", $1,$2,$3,$4}' "$TSV"
  echo ""
  echo "CASES=$CASES_TOTAL PASSED=$CASES_PASS FAILED=$((CASES_TOTAL-CASES_PASS))"
} >> "$SUMMARY"

XML="$S10_ARTIFACTS/s10-gate-matrix.xml"
xml_escape() { local s="$1"; s="${s//&/&amp;}"; s="${s//</&lt;}"; s="${s//>/&gt;}"; s="${s//\"/&quot;}"; printf '%s' "$s"; }
{
  echo '<?xml version="1.0" encoding="UTF-8"?>'
  echo '<testsuite name="S10-protected-deployment-gate" tests="'"$CASES_TOTAL"'" failures="'"$((CASES_TOTAL-CASES_PASS))"'" errors="0" timestamp="'"$(date -u +'%Y-%m-%dT%H:%M:%SZ')"'">'
  while IFS=$'\t' read -r c e a d; do
    if [ "$e" = "$a" ]; then
      echo "  <testcase name=\"$(xml_escape "$c")\" classname=\"S10\">"
      echo "    <system-out>expected=$(xml_escape "$e") actual=$(xml_escape "$a") $(xml_escape "$d")</system-out>"
      echo "  </testcase>"
    else
      echo "  <testcase name=\"$(xml_escape "$c")\" classname=\"S10\">"
      echo "    <failure message=\"expected $(xml_escape "$e"), got $(xml_escape "$a")\" type=\"GateContractViolation\">$(xml_escape "$d")</failure>"
      echo "  </testcase>"
    fi
  done < "$TSV"
  echo '</testsuite>'
} > "$XML"

echo "=================================================================="
if [ "$CASES_PASS" -eq "$CASES_TOTAL" ]; then
  echo "S10 GATE SELF-TEST: PASSED ($CASES_PASS/$CASES_TOTAL cases)"
  echo "Artifacts: $S10_ARTIFACTS (s10-matrix.tsv, summary.md, s10-gate-matrix.xml, per-case logs)"
  echo "=================================================================="
  exit 0
else
  echo "::error::S10 GATE SELF-TEST: FAILED ($((CASES_TOTAL-CASES_PASS))/$CASES_TOTAL cases) — see $TSV"
  echo "=================================================================="
  exit 1
fi
