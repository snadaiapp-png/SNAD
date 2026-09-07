#!/usr/bin/env bash
set -euo pipefail

# R0C-12 corrective recertification — smoke-script contract tests.
#
# Pins the PRODUCTION SMOKE blind spot found during the real-JWT RBAC
# correction: the former verify-scp-contract-smoke.sh accepted
# access-check/v2 responses with authenticated=false as long as the JSON
# field TYPES were right (boolean + object). A deployed backend that
# reported every genuine JWT user as unauthenticated therefore PASSED the
# production smoke — exactly the observed release blocker.
#
# Required outcomes pinned here (ACCESS_CHECK_SMOKE_FAIL_OPEN_GAP=FIXED):
#   1. unauthorized (authenticated=false)        → smoke MUST FAIL
#   2. missing required read capability          → smoke MUST FAIL
#   3. authenticated=true + 10 read capabilities → smoke MUST PASS
#
# The harness runs the REAL smoke script against a local mock backend
# (scripts/production/tests/mock_scp_backend.py). No production access.

SCRIPT_UNDER_TEST="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/verify-scp-contract-smoke.sh"
MOCK="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/mock_scp_backend.py"

if [ ! -f "$SCRIPT_UNDER_TEST" ]; then
  echo "FAIL: smoke script not found at $SCRIPT_UNDER_TEST"
  exit 1
fi

WORK="$(mktemp -d)"
PORT=$(python3 -c 'import socket; s=socket.socket(); s.bind(("127.0.0.1",0)); print(s.getsockname()[1]); s.close()')
EVIDENCE="$WORK/evidence.json"

cleanup() {
  if [ -n "${MOCK_PID:-}" ] && kill -0 "$MOCK_PID" 2>/dev/null; then
    kill "$MOCK_PID" 2>/dev/null || true
  fi
  rm -rf "$WORK"
}
trap cleanup EXIT

run_smoke() {
  ACCESS_MODE="$1" python3 "$MOCK" "$PORT" &
  MOCK_PID=$!

  # Wait for the mock to accept connections.
  for _ in $(seq 1 50); do
    if curl --silent --output /dev/null --connect-timeout 1 \
      "http://127.0.0.1:$PORT/api/v1/executive/overview" 2>/dev/null; then
      break
    fi
    sleep 0.1
  done

  local exit_code=0
  # Mock credentials are generated per run (never hardcoded — the scanner is
  # authoritative even for test scaffolding). The mock backend does not
  # validate them; the smoke script only needs a 200 login response.
  local run_pw run_email
  run_pw="mock-$(head -c 16 /dev/urandom | od -An -tx1 | tr -d ' \n')"
  run_email="smoke-$(date +%s%N)@example.test"
  (
    export SCP_SMOKE_BASE_URL="http://127.0.0.1:$PORT"
    export CONTROL_PLANE_ADMIN_EMAIL="$run_email"
    export CONTROL_PLANE_ADMIN_PASSWORD="$run_pw"
    export CONTROL_PLANE_TENANT_ID="00000000-0000-0000-0000-000000000001"
    export DEPLOYED_COMMIT_SHA="83228cec83a2bc7a7a99cc1a72726f27f82fe378"
    export SCP_SMOKE_EVIDENCE_FILE="$EVIDENCE"
    bash "$SCRIPT_UNDER_TEST"
  ) >"$WORK/stdout.log" 2>&1 || exit_code=$?

  kill "$MOCK_PID" 2>/dev/null || true
  wait "$MOCK_PID" 2>/dev/null || true
  MOCK_PID=""
  echo "$exit_code"
}

failures=0

# --- 1. authenticated=false MUST be rejected (RED against the blind spot) ---
echo "== CASE 1: access-check returns authenticated=false =="
code="$(run_smoke unauthorized)"
if [ "$code" = "0" ]; then
  echo "FAIL: smoke script PASSED while access-check reported authenticated=false (fail-open blind spot)"
  failures=$((failures + 1))
else
  echo "PASS: smoke script rejected authenticated=false (exit=$code)"
fi

# --- 2. a missing mandatory read capability MUST be rejected ---
echo "== CASE 2: access-check authenticated but audit.read=false =="
code="$(run_smoke missing_cap)"
if [ "$code" = "0" ]; then
  echo "FAIL: smoke script PASSED while a mandatory read capability was denied"
  failures=$((failures + 1))
else
  echo "PASS: smoke script rejected missing mandatory read capability (exit=$code)"
fi

# --- 3. authorized control-plane identity MUST pass ---
echo "== CASE 3: access-check authenticated=true with all mandatory read capabilities =="
code="$(run_smoke authorized)"
if [ "$code" = "0" ]; then
  echo "PASS: smoke script accepted the authorized control-plane identity (exit=$code)"
else
  echo "FAIL: smoke script rejected a fully authorized identity (exit=$code)"
  cat "$WORK/stdout.log" || true
  failures=$((failures + 1))
fi

if [ "$failures" -ne 0 ]; then
  echo "RESULT: $failures contract case(s) FAILED"
  exit 1
fi
echo "RESULT: ALL SMOKE CONTRACT CASES PASS"
