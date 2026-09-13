#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
SCRIPT="$ROOT/scripts/production/verify-workflow-vercel-production-runtime.sh"
MOCK="$ROOT/scripts/production/tests/mock_workflow_vercel_runtime.py"
TMP="$(mktemp -d)"
PIDS=()
trap 'for p in "${PIDS[@]:-}"; do kill "$p" 2>/dev/null || true; done; rm -rf "$TMP"' EXIT

free_port() {
  python3 - <<'PY'
import socket
s=socket.socket(); s.bind(('127.0.0.1',0)); print(s.getsockname()[1]); s.close()
PY
}

run_mock() {
  local sha="$1"
  local port
  port="$(free_port)"
  python3 "$MOCK" --port "$port" --release-sha "$sha" >/dev/null 2>&1 &
  local pid=$!
  PIDS+=("$pid")
  for _ in $(seq 1 30); do
    curl -fsS "http://127.0.0.1:$port/api/system/release" >/dev/null 2>&1 && break
    sleep 0.1
  done
  printf '%s' "$port"
}

TEST_CREDENTIAL='workflow-runtime-fixture-credential'
PASS_PORT="$(run_mock aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa)"
PASS_EVIDENCE="$TMP/pass.json"
WORKFLOW_VERCEL_ALLOW_HTTP='true' \
WORKFLOW_RUNTIME_ADMIN_PASSWORD="$TEST_CREDENTIAL" \
WORKFLOW_RUNTIME_ADMIN_EMAIL='admin@example.test' \
WORKFLOW_RUNTIME_TENANT_ID='77777777-7777-7777-7777-777777777777' \
VERCEL_BASE_URL="http://127.0.0.1:$PASS_PORT" \
VERCEL_EXPECTED_SHA='aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa' \
WORKFLOW_VERCEL_EVIDENCE_FILE="$PASS_EVIDENCE" \
bash "$SCRIPT"

jq -e '
  .schema == "snad.workflow.vercel-production-runtime.v1"
  and .result == "PASS"
  and .releaseSha == "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
  and .transport == "vercel-bff"
  and (.checks | length >= 9)
' "$PASS_EVIDENCE" >/dev/null
! grep -q "$TEST_CREDENTIAL\|test-token\|admin@example.test" "$PASS_EVIDENCE"

FAIL_PORT="$(run_mock bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb)"
FAIL_EVIDENCE="$TMP/fail.json"
set +e
WORKFLOW_VERCEL_ALLOW_HTTP='true' \
WORKFLOW_RUNTIME_ADMIN_PASSWORD="$TEST_CREDENTIAL" \
WORKFLOW_RUNTIME_ADMIN_EMAIL='admin@example.test' \
WORKFLOW_RUNTIME_TENANT_ID='77777777-7777-7777-7777-777777777777' \
VERCEL_BASE_URL="http://127.0.0.1:$FAIL_PORT" \
VERCEL_EXPECTED_SHA='aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa' \
WORKFLOW_VERCEL_EVIDENCE_FILE="$FAIL_EVIDENCE" \
bash "$SCRIPT"
rc=$?
set -e
[ "$rc" -ne 0 ]
jq -e '.result == "FAIL" and .failureStage == "vercel-release-identity"' "$FAIL_EVIDENCE" >/dev/null

echo "verify-workflow-vercel-production-runtime tests: PASS"
