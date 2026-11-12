#!/usr/bin/env bash
set -euo pipefail

# Regression tests for scripts/production/verify-control-plane-tenant.sh
# (production release incident 43189957654).
#
# DEFECT PINNED HERE:
#   The ADMIN gate ran `psql --command="SET app.tenant_id = '...';
#   SELECT COUNT(*) FROM roles ..."` in ONE session (correct: the Supabase
#   session pooler strips PGOPTIONS, so the RLS GUC must be bound in-SQL) but
#   then tested the raw stdout with `[ "$ADMIN_EXISTS" -ge 1 ]`. psql prints
#   the SET command tag before the SELECT row, so ADMIN_EXISTS was "SET\n1";
#   the [ -ge ] test aborted with "integer expression expected" and the gate
#   failed a healthy production database (run 43189957654, job "Verify
#   Control Plane tenant in production database") with the misleading message
#   "No ACTIVE ADMIN role".
#
# Required outcomes pinned here:
#   1. "SET"+count 1 → PASS (the regression).
#   2. "SET"+count 0 → FAIL "No ACTIVE ADMIN role" (count verdicts kept).
#   3. tenant count 0 → FAIL "not found or not ACTIVE" (tenant verdict kept).
#   4. empty / non-numeric / duplicated / out-of-order / trailing-tag output
#      → FAIL with the unparseable-output error, never a count verdict.
#   5. psql connection failure (exit 2) → FAIL, stderr surfaced, not swallowed.
#   6. psql SQL failure under ON_ERROR_STOP (exit 3) → FAIL, stderr surfaced.
#   7. dropping the in-SQL GUC bind (splitting SET from SELECT) → the RLS
#      default-deny yields count 0 → FAIL "No ACTIVE ADMIN role"; the
#      single-session binding is load-bearing and cannot be "simplified away".
#   8. static contract: ON_ERROR_STOP on every psql call, no `|| true`,
#      SET+SELECT in one --command, mask calls preserved.
#
# The harness runs the REAL verify-control-plane-tenant.sh against a stub
# `psql` that reproduces the exact production output semantics (SET command
# tag before the SELECT row; RLS default-deny "0" when the GUC bind is
# absent) and a stub `curl` that serves the Render env-var fixture. No
# production access; every credential is ephemeral (`openssl rand`) so
# nothing here can leak a real secret. The harness also asserts the stub
# database password never appears in any output.

SCRIPT_UNDER_TEST="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/verify-control-plane-tenant.sh"

if [ ! -f "$SCRIPT_UNDER_TEST" ]; then
  echo "FAIL: verify-control-plane-tenant.sh not found at $SCRIPT_UNDER_TEST"
  exit 1
fi

WORK="$(mktemp -d)"
cleanup() { rm -rf "$WORK"; }
trap cleanup EXIT

STUB_BIN="$WORK/bin"
mkdir -p "$STUB_BIN"
OUT_FILE="$WORK/last.out"

# ---------------------------------------------------------------------------
# Stub curl — the ONLY permitted network call is the read-only Render
# env-vars listing, exactly as the verifier issues it. It serves two
# synthetic values: the stub DATABASE_URL and an ephemeral password.
# ---------------------------------------------------------------------------
cat > "$STUB_BIN/curl" << 'EOF'
#!/usr/bin/env bash
set -euo pipefail
url=""
has_auth=0
for arg in "$@"; do
  case "$arg" in
    https://api.render.com/*) url="$arg" ;;
  esac
done
for arg in "$@"; do
  case "$arg" in
    "Authorization: Bearer "*) has_auth=1 ;;
  esac
done
if [ -z "$url" ] || [ "$has_auth" -ne 1 ]; then
  echo "STUB-ERROR: unexpected curl invocation (url=${url:-none} auth=$has_auth)" >&2
  exit 99
fi
case "$url" in
  */env-vars?limit=100) ;;
  *)
    echo "STUB-ERROR: only the read-only env-vars listing may be queried, got: $url" >&2
    exit 99
    ;;
esac
printf '[{"key":"DATABASE_URL","value":"%s"},{"key":"DATABASE_PASSWORD","value":"%s"}]\n' \
  "${STUB_DB_URL:-postgresql://stubhost.invalid:5432/stubdb}" \
  "${STUB_DB_PASSWORD:?STUB_DB_PASSWORD is required}"
EOF
chmod +x "$STUB_BIN/curl"

# ---------------------------------------------------------------------------
# Stub psql — reproduces production PostgreSQL/psql semantics for the two
# verifier queries:
#   * tenants SELECT          → STUB_TENANT_RAW (default "1\n")
#   * roles SET+SELECT in one → prints "SET" then the count (default "1\n")
#     --command                  — the exact release-43189957654 signature
#   * roles SELECT WITHOUT the in-Sql SET → "0\n": the V20261029_1 policy
#     tenant_id::text = NULLIF(current_setting('app.tenant_id', true), '')
#     default-denies when the GUC is unbound (missing context ⇒ zero rows)
#   * STUB_PSQL_MODE=conn_fail → psql exit 2, connection refused on stderr
#   * STUB_PSQL_MODE=sql_fail  → psql exit 3, permission denied on stderr
#     (ON_ERROR_STOP semantics); STUB_FAIL_QUERY selects which query fails
# The stub also refuses to run without --set=ON_ERROR_STOP=1.
# ---------------------------------------------------------------------------
cat > "$STUB_BIN/psql" << 'EOF'
#!/usr/bin/env bash
set -uo pipefail

cmd=""
on_error_stop=0
for arg in "$@"; do
  case "$arg" in
    --command=*) cmd="${arg#--command=}" ;;
    --set=ON_ERROR_STOP=1) on_error_stop=1 ;;
  esac
done

if [ -z "$cmd" ]; then
  echo "STUB-ERROR: psql invoked without --command" >&2
  exit 64
fi
if [ "$on_error_stop" -ne 1 ]; then
  echo "STUB-ERROR: psql invoked without --set=ON_ERROR_STOP=1" >&2
  exit 64
fi

query_kind=other
case "$cmd" in
  *"FROM tenants"*) query_kind=tenant ;;
  *"FROM roles"*) query_kind=admin ;;
esac

mode="${STUB_PSQL_MODE:-ok}"
fail_query="${STUB_FAIL_QUERY:-}"
should_fail=0
if [ "$mode" = conn_fail ] || [ "$mode" = sql_fail ]; then
  case "$fail_query" in
    "") should_fail=1 ;;
    "$query_kind") should_fail=1 ;;
  esac
fi
if [ "$should_fail" -eq 1 ]; then
  if [ "$mode" = conn_fail ]; then
    echo 'psql: error: connection to server at "stubhost.invalid" (192.0.2.1), port 5432 failed: Connection refused' >&2
    echo '  Is the server running on that host and accepting TCP/IP connections?' >&2
    exit 2
  fi
  echo 'psql: error: ERROR:  permission denied for table roles' >&2
  exit 3
fi

if [ "$query_kind" = tenant ]; then
  if [ -n "${STUB_TENANT_RAW+x}" ]; then
    printf '%s' "$STUB_TENANT_RAW"
  else
    printf '1\n'
  fi
  exit 0
fi

if [ "$query_kind" = admin ]; then
  case "$cmd" in
    *"SET app.tenant_id"*)
      if [ -n "${STUB_ADMIN_RAW+x}" ]; then
        printf '%s' "$STUB_ADMIN_RAW"
      else
        printf 'SET\n1\n'
      fi
      exit 0
      ;;
    *)
      printf '0\n'
      exit 0
      ;;
  esac
fi

echo "STUB-ERROR: unexpected query shape (neither the tenants nor the roles verifier query)" >&2
exit 64
EOF
chmod +x "$STUB_BIN/psql"

export PATH="$STUB_BIN:$PATH"

# Scenario knobs are reset between cases so no fixture leaks across tests.
reset_scenario() {
  unset STUB_TENANT_RAW STUB_ADMIN_RAW STUB_PSQL_MODE STUB_FAIL_QUERY
  unset STUB_SCRIPT_OVERRIDE STUB_DB_URL
}

STUB_DB_PASSWORD="$(openssl rand -hex 24)"
export STUB_DB_PASSWORD

run_verify() {
  RENDER_API_KEY="stub-render-key" \
  RENDER_SERVICE_ID="stub-srv-0001" \
  DATABASE_USERNAME="sanad_stub" \
  CONTROL_PLANE_TENANT_ID="11111111-1111-1111-1111-111111111111" \
  bash "${STUB_SCRIPT_OVERRIDE:-$SCRIPT_UNDER_TEST}"
}

fail_count=0

assert_pass() {
  local desc="$1"
  if run_verify > "$OUT_FILE" 2>&1; then
    # ::add-mask:: lines legitimately carry the value they register for
    # masking; the secret must appear nowhere else.
    if grep -q "Control Plane tenant verified: ACTIVE tenant with ADMIN role exists." "$OUT_FILE" \
       && ! grep -v '^::add-mask::' "$OUT_FILE" | grep -qF "$STUB_DB_PASSWORD"; then
      echo "PASS: $desc"
    else
      echo "FAIL: $desc — exit 0 but confirmation line missing or secret leaked"
      sed -n '1,40p' "$OUT_FILE"
      fail_count=$((fail_count + 1))
    fi
  else
    echo "FAIL: $desc — script exited non-zero"
    sed -n '1,40p' "$OUT_FILE"
    fail_count=$((fail_count + 1))
  fi
}

assert_fail_with() {
  local desc="$1"; shift
  local pattern
  if run_verify > "$OUT_FILE" 2>&1; then
    echo "FAIL: $desc — script passed but must fail"
    sed -n '1,40p' "$OUT_FILE"
    fail_count=$((fail_count + 1))
    return
  fi
  for pattern in "$@"; do
    if ! grep -qF "$pattern" "$OUT_FILE"; then
      echo "FAIL: $desc — failed without expected output: $pattern"
      sed -n '1,40p' "$OUT_FILE"
      fail_count=$((fail_count + 1))
      return
    fi
  done
  if grep -v '^::add-mask::' "$OUT_FILE" | grep -qF "$STUB_DB_PASSWORD"; then
    echo "FAIL: $desc — the stub database password leaked into the output"
    fail_count=$((fail_count + 1))
    return
  fi
  echo "PASS: $desc"
}

# ===========================================================================
# 0. STATIC CONTRACT
# ===========================================================================
bash -n "$SCRIPT_UNDER_TEST"

psql_calls=$(grep -c 'PGPASSWORD="\$DATABASE_PASSWORD" psql' "$SCRIPT_UNDER_TEST")
on_error_stop_calls=$(grep -c -- '--set=ON_ERROR_STOP=1' "$SCRIPT_UNDER_TEST")
if [ "$psql_calls" -eq 2 ] && [ "$on_error_stop_calls" -eq 2 ]; then
  echo "PASS: static contract — both psql calls carry ON_ERROR_STOP"
else
  echo "FAIL: static contract — expected 2 psql calls each with ON_ERROR_STOP, got $psql_calls/$on_error_stop_calls"
  fail_count=$((fail_count + 1))
fi

# `|| true` as a COMMAND is forbidden; comment prose mentioning it is not.
if sed '/^[[:space:]]*#/d' "$SCRIPT_UNDER_TEST" | grep -q '|| true'; then
  echo "FAIL: static contract — '|| true' must never appear in the verifier"
  fail_count=$((fail_count + 1))
else
  echo "PASS: static contract — no '|| true' error suppression"
fi

if grep -qF "SET app.tenant_id = '\${CONTROL_PLANE_TENANT_ID}'; SELECT COUNT(*) FROM roles" "$SCRIPT_UNDER_TEST"; then
  echo "PASS: static contract — GUC bind and roles SELECT share one --command (one session)"
else
  echo "FAIL: static contract — the in-SQL SET and the roles SELECT are no longer in one --command"
  fail_count=$((fail_count + 1))
fi

if [ "$(grep -c '::add-mask::' "$SCRIPT_UNDER_TEST")" -ge 6 ] \
   && grep -qF "FROM tenants WHERE id = '\${CONTROL_PLANE_TENANT_ID}' AND status = 'ACTIVE'" "$SCRIPT_UNDER_TEST" \
   && grep -qF "code = 'ADMIN' AND status = 'ACTIVE'" "$SCRIPT_UNDER_TEST"; then
  echo "PASS: static contract — tenant and ADMIN checks present, secrets masked"
else
  echo "FAIL: static contract — a tenant/ADMIN check or mask call was lost"
  fail_count=$((fail_count + 1))
fi

# ===========================================================================
# 1. BEHAVIORAL — the regression and the count verdicts
# ===========================================================================
reset_scenario
assert_pass "SET + count 1 (release 43189957654 shape) passes"

reset_scenario
export STUB_ADMIN_RAW='SET
0'
assert_fail_with "SET + count 0 fails with the ADMIN verdict" \
  "No ACTIVE ADMIN role for Control Plane tenant"

reset_scenario
export STUB_TENANT_RAW='0'
assert_fail_with "tenant count 0 fails with the tenant verdict" \
  "Control Plane tenant not found or not ACTIVE"

# ===========================================================================
# 2. BEHAVIORAL — unparseable output fails closed, never as a count verdict
# ===========================================================================
reset_scenario
export STUB_ADMIN_RAW=''
assert_fail_with "empty admin output fails closed as unparseable" \
  "Unparseable psql output for the ACTIVE ADMIN role existence check"

reset_scenario
export STUB_ADMIN_RAW='SET'
assert_fail_with "SET tag without a count fails closed as unparseable" \
  "Unparseable psql output for the ACTIVE ADMIN role existence check"

reset_scenario
export STUB_ADMIN_RAW='abc'
assert_fail_with "non-numeric admin output fails closed as unparseable" \
  "Unparseable psql output for the ACTIVE ADMIN role existence check"

reset_scenario
export STUB_ADMIN_RAW='SET
abc'
assert_fail_with "SET + non-numeric count fails closed as unparseable" \
  "Unparseable psql output for the ACTIVE ADMIN role existence check"

reset_scenario
export STUB_ADMIN_RAW='SET
1
2'
assert_fail_with "duplicated count lines fail closed as unparseable" \
  "Unparseable psql output for the ACTIVE ADMIN role existence check"

reset_scenario
export STUB_ADMIN_RAW='1
SET'
assert_fail_with "trailing SET tag (out of order) fails closed as unparseable" \
  "Unparseable psql output for the ACTIVE ADMIN role existence check"

reset_scenario
export STUB_ADMIN_RAW='SET
SET
1'
assert_fail_with "two SET tags fail closed as unparseable" \
  "Unparseable psql output for the ACTIVE ADMIN role existence check"

reset_scenario
export STUB_ADMIN_RAW='SET
-1'
assert_fail_with "negative count fails closed as unparseable" \
  "Unparseable psql output for the ACTIVE ADMIN role existence check"

reset_scenario
export STUB_ADMIN_RAW='SET
1.5'
assert_fail_with "fractional count fails closed as unparseable" \
  "Unparseable psql output for the ACTIVE ADMIN role existence check"

reset_scenario
export STUB_TENANT_RAW=''
assert_fail_with "empty tenant output fails closed as unparseable" \
  "Unparseable psql output for the Control Plane tenant existence check"

reset_scenario
export STUB_TENANT_RAW='abc'
assert_fail_with "non-numeric tenant output fails closed as unparseable" \
  "Unparseable psql output for the Control Plane tenant existence check"

reset_scenario
export STUB_TENANT_RAW='1
2'
assert_fail_with "duplicated tenant counts fail closed as unparseable" \
  "Unparseable psql output for the Control Plane tenant existence check"

# ===========================================================================
# 3. BEHAVIORAL — tolerated transport noise (CRLF, padding) still passes
# ===========================================================================
reset_scenario
export STUB_ADMIN_RAW="$(printf 'SET\r\n1\r\n')"
assert_pass "CRLF line endings are tolerated"

reset_scenario
export STUB_ADMIN_RAW='  SET
  1  '
assert_pass "surrounding whitespace is tolerated"

# ===========================================================================
# 4. BEHAVIORAL — psql failures are surfaced, never swallowed
# ===========================================================================
reset_scenario
export STUB_PSQL_MODE=conn_fail
assert_fail_with "PostgreSQL connection failure fails closed with psql stderr" \
  "psql failed while verifying the Control Plane tenant exists" \
  "connection to server"

reset_scenario
export STUB_PSQL_MODE=conn_fail STUB_FAIL_QUERY=admin
assert_fail_with "connection failure on the ADMIN query fails closed" \
  "psql failed while verifying the ACTIVE ADMIN role exists" \
  "connection to server"

reset_scenario
export STUB_PSQL_MODE=sql_fail STUB_FAIL_QUERY=admin
assert_fail_with "SQL failure under ON_ERROR_STOP fails closed with stderr" \
  "psql failed while verifying the ACTIVE ADMIN role exists" \
  "permission denied for table roles"

reset_scenario
export STUB_PSQL_MODE=sql_fail STUB_FAIL_QUERY=tenant
assert_fail_with "SQL failure on the tenant query fails closed" \
  "psql failed while verifying the Control Plane tenant exists" \
  "permission denied for table roles"

# ===========================================================================
# 5. BEHAVIORAL — the single-session GUC bind is load-bearing
# ===========================================================================
# A copy of the verifier with the in-SQL SET stripped from the roles command
# emulates the "simplification" regression: without the GUC bound in the same
# session, the V20261029_1 policy default-denies and the count is 0 — the
# gate MUST fail. This pins that the fix cannot be undone by splitting the
# SET from the SELECT.
GUC_DROPPED="$WORK/verify-control-plane-tenant.noguc.sh"
sed "s/SET app\.tenant_id = '[^']*'; SELECT COUNT(\*) FROM roles/SELECT COUNT(*) FROM roles/" \
  "$SCRIPT_UNDER_TEST" > "$GUC_DROPPED"
chmod +x "$GUC_DROPPED"
if sed '/^[[:space:]]*#/d' "$GUC_DROPPED" | grep -qF "SET app.tenant_id"; then
  echo "FAIL: harness setup — GUC-strip sed did not modify the command"
  fail_count=$((fail_count + 1))
else
  reset_scenario
  export STUB_SCRIPT_OVERRIDE="$GUC_DROPPED"
  assert_fail_with "dropping the in-SQL GUC bind default-denies RLS and fails closed" \
    "No ACTIVE ADMIN role for Control Plane tenant"
fi
reset_scenario

# ===========================================================================
echo
if [ "$fail_count" -eq 0 ]; then
  echo "ALL CONTROL-PLANE TENANT GATE REGRESSION TESTS PASSED"
  exit 0
fi
echo "$fail_count CONTROL-PLANE TENANT GATE REGRESSION TEST(S) FAILED"
exit 1
