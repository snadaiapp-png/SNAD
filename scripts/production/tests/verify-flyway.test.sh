#!/usr/bin/env bash
set -euo pipefail

# verify-flyway.sh ledger-head regression tests (2026-09-08 release incident).
#
# DEFECT PINNED HERE (DB_MAX_VERSION_TEXT_MAX=FIXED):
#   verify-flyway.sh computed the production Flyway head with
#     SELECT max(version) FROM flyway_schema_history ...
#   `version` is VARCHAR, so PostgreSQL computes a LEXICOGRAPHIC maximum.
#   The canonical production ledger contains legacy numeric versions ("9",
#   "14", "15") next to date-based versions ("20260904.1"). Lexicographically
#   "9" sorts above every "2026..." version, so the gate reported
#     production schema version 9 is older than repository head 20260908.1
#   and failed an otherwise fully successful release (image live, readiness
#   UP, migrations applied, run 34291484570) into an automatic rollback.
#
#   The ledger head must therefore be derived by application order
#   (installed_rank DESC), which is how Flyway itself defines "head".
#
# Required outcomes pinned here:
#   1. ledger with legacy "9"/"14"/"15" rows + rank head == repo head → PASS
#   2. rank head strictly older than repo head  → MUST FAIL (pending guard kept)
#   3. rank head strictly newer than repo head  → MUST FAIL (drift guard kept)
#
# The harness runs the REAL verify-flyway.sh against stub `curl`/`psql`
# executables that emulate the exact PostgreSQL semantics of each query shape
# (text max for max(version), application-order for installed_rank DESC).
# No production access.

SCRIPT_UNDER_TEST="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/verify-flyway.sh"

if [ ! -f "$SCRIPT_UNDER_TEST" ]; then
  echo "FAIL: verify-flyway.sh not found at $SCRIPT_UNDER_TEST"
  exit 1
fi

WORK="$(mktemp -d)"
cleanup() { rm -rf "$WORK"; }
trap cleanup EXIT

STUB_BIN="$WORK/bin"
mkdir -p "$STUB_BIN"

# ---------------------------------------------------------------------------
# Stub curl — serves a fixed Render env-vars payload.
# ---------------------------------------------------------------------------
cat > "$STUB_BIN/curl" << 'EOF'
#!/usr/bin/env bash
cat "${CURL_FIXTURE:?}"
EOF
chmod +x "$STUB_BIN/curl"

# ---------------------------------------------------------------------------
# Stub psql — emulates PostgreSQL semantics per query shape.
#
#   * "max(version)"          → VARCHAR max: lexicographic over the fixture
#                               version list (this is the production bug).
#   * "installed_rank DESC"   → application-order head (what Flyway means).
#   * everything else         → canned TSV / counts per fixture.
# ---------------------------------------------------------------------------
cat > "$STUB_BIN/psql" << 'EOF'
#!/usr/bin/env bash
SQL=""
for arg in "$@"; do
  case "$arg" in
    --command=*) SQL="${arg#--command=}" ;;
  esac
done
case "$SQL" in
  *"SELECT current_user, rolsuper"*)
      printf "%s\tf\tf\tf\tf\n" "${STUB_ACTUAL_ROLE:-sanad_release_auditor}"
      ;;
  *"SELECT has_table_privilege(current_user"*)
      printf "%s\n" "${STUB_ACTUAL_ACL:-$(printf "t\tf\tf\tf\tt\tf\tf\tf")}"
      ;;
  *"flyway_schema_history WHERE version IN"*)
      printf '15\tJDBC\tseed rbac roles and capabilities\tt\n'
      printf '20260702.1\tSQL\tcreate unified crm core\tt\n'
      printf '20260702.2\tSQL\treconcile admin role and capabilities\tt\n'
      printf '20260702.3\tSQL\tcomplete crm imports custom fields\tt\n'
      ;;
  *"success = FALSE"*)
      echo "${STUB_FAILED_MIGRATIONS:-0}"
      ;;
  *"HAVING COUNT(*) > 1"*)
      echo "0"
      ;;
  *"checksum IS NULL"*)
      echo "${STUB_MISSING_SQL_CHECKSUMS:-0}"
      ;;
  *"max(version)"*)
      # PostgreSQL VARCHAR max = lexicographic maximum of the fixture ledger.
      printf '%s\n' ${STUB_LEDGER_VERSIONS:?} | sort | tail -1
      ;;
  *"installed_rank DESC"*)
      echo "${STUB_DB_RANK_HEAD:?}"
      ;;
  *"pg_tables"*)
      echo "1"
      ;;
  *"definition_family_id"*)
      echo "1"
      ;;
  *"access_capabilities"*)
      echo "${STUB_CAPABILITY_COUNT:-1}"
      ;;
  *)
      echo "0"
      ;;
esac
EOF
chmod +x "$STUB_BIN/psql"

# Protected audit connection fixture: the verifier MUST NOT call the Render API.
# Stub curl exits nonzero if unexpectedly called in the new implementation.
cat > "$STUB_BIN/curl" << 'EOF'
#!/usr/bin/env bash
echo "ERROR: unexpected Render API access" >&2
exit 99
EOF
chmod +x "$STUB_BIN/curl"
export STUB_LEDGER_VERSIONS="9 14 15 20260702.1 20260702.2 20260702.3 20260904.1"

run_verify() {
  local scenario_head="$1"
  export STUB_DB_RANK_HEAD="$scenario_head"
  export FLYWAY_AUDIT_JDBC_URL="${STUB_OVERRIDE_URL:-jdbc:postgresql://stubhost:5432/stubdb?sslmode=require}"
  export FLYWAY_AUDIT_USERNAME="${STUB_OVERRIDE_USERNAME:-sanad_release_auditor}"
  export FLYWAY_AUDIT_PASSWORD="stub-password-only-for-tests"
  export PATH="$STUB_BIN:$PATH"
  if [[ "${STUB_UNSET_PASSWORD:-0}" == "1" ]]; then unset FLYWAY_AUDIT_PASSWORD; fi
  bash "$SCRIPT_UNDER_TEST"
}

fail_count=0
assert_pass() {
  local desc="$1"
  if run_verify "$2" > "$WORK/out.txt" 2>&1; then
    if grep -q "PENDING MIGRATIONS: 0 (production schema version $2" "$WORK/out.txt"; then
      echo "PASS: $desc"
    else
      echo "FAIL: $desc — passed but without rank-head confirmation"
      sed -n '1,40p' "$WORK/out.txt"
      fail_count=$((fail_count + 1))
    fi
  else
    echo "FAIL: $desc — script exited non-zero"
    sed -n '1,40p' "$WORK/out.txt"
    fail_count=$((fail_count + 1))
  fi
}

assert_fail_with() {
  local desc="$1" scenario_head="$2" expected="$3"
  local rc=0
  run_verify "$scenario_head" > "$WORK/out.txt" 2>&1 || rc=$?
  if [ "$rc" -eq 0 ]; then
    echo "FAIL: $desc — script passed but must fail"
    sed -n '1,40p' "$WORK/out.txt"
    fail_count=$((fail_count + 1))
  elif ! grep -q "$expected" "$WORK/out.txt"; then
    echo "FAIL: $desc — failed without expected error '$expected'"
    sed -n '1,40p' "$WORK/out.txt"
    fail_count=$((fail_count + 1))
  else
    echo "PASS: $desc"
  fi
}

# 1. REGRESSION: legacy numeric rows must not fake an older ledger head.
#    (RED against the max(version) implementation, GREEN after the fix.)
assert_pass "rank head == repo head with legacy numeric rows present" "20261107.1"

# 2. The pending guard must survive the fix.
assert_fail_with "rank head older than repo head still fails (pending guard)" \
  "20261021.1" "Pending migrations detected"

# 3. The drift guard must survive the fix.
assert_fail_with "rank head newer than repo head still fails (drift guard)" \
  "20261108.1" "Schema drift"

# 4. Role alias, runtime and migration identities must be rejected.
for forbidden in sanad sanad_migrator postgres; do
  export STUB_OVERRIDE_USERNAME="$forbidden"
  assert_fail_with "forbidden audit identity $forbidden" "20261107.1" "must equal sanad_release_auditor"
done
unset STUB_OVERRIDE_USERNAME

# 5. Authenticated identity must match the permitted role (even when the
# client claims to connect as the auditor).
export STUB_ACTUAL_ROLE="sanad"
assert_fail_with "actual DB role mismatch fails" "20261107.1" "role or privilege contract invalid"
unset STUB_ACTUAL_ROLE

# 6. The auditor cannot be allowed to write Flyway history or inherit the
# runtime/migrator role. This fixture mimics an unexpected UPDATE privilege.
export STUB_ACTUAL_ACL="$(printf "t\\tt\\tf\\tf\\tt\\tf\\tf\\tf")"
assert_fail_with "auditor with Flyway UPDATE fails" "20261107.1" "minimum read-only contract"
unset STUB_ACTUAL_ACL

# 7. No silent fallback to the Render runtime password.
export STUB_UNSET_PASSWORD=1
assert_fail_with "missing independent audit password fails" "20261107.1" "FLYWAY_AUDIT_PASSWORD is required"
unset STUB_UNSET_PASSWORD

# 8. Connection string may not embed or override database credentials.
export STUB_OVERRIDE_URL="jdbc:postgresql://sanad:secret@stubhost:5432/stubdb"
assert_fail_with "embedded runtime connection credentials rejected" "20261107.1" "Invalid audit JDBC URL"
unset STUB_OVERRIDE_URL

# 9. Never approve a failed Flyway migration or missing SQL checksum.
export STUB_FAILED_MIGRATIONS=1
assert_fail_with "failed migration detected" "20261107.1" "Failed migrations"
unset STUB_FAILED_MIGRATIONS
export STUB_MISSING_SQL_CHECKSUMS=1
assert_fail_with "missing SQL checksum detected" "20261107.1" "missing checksums"
unset STUB_MISSING_SQL_CHECKSUMS

# 10. The auditor must be able to verify the Y2 capability catalog.
export STUB_CAPABILITY_COUNT=0
assert_fail_with "required active capability absent" "20261107.1" "Y2 sentinel capability"
unset STUB_CAPABILITY_COUNT

if [ "$fail_count" -gt 0 ]; then
  echo "RESULT: RED — $fail_count failing scenario(s)"
  exit 1
fi
echo "RESULT: GREEN — verify-flyway.sh ledger-head semantics correct"
