#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
SCRIPT="$ROOT/scripts/production/verify-workflow-y2-production-write-canary.sh"
MOCK="$ROOT/scripts/production/tests/mock_workflow_y2_write_canary_backend.py"
TMP="$(mktemp -d)"
PIDS=()
trap 'for p in "${PIDS[@]:-}"; do kill "$p" 2>/dev/null || true; done; rm -rf "$TMP"' EXIT

free_port() { python3 - <<'PY'
import socket
s=socket.socket(); s.bind(('127.0.0.1',0)); print(s.getsockname()[1]); s.close()
PY
}

run_case() {
  local immutability_status="$1" expected_rc="$2" evidence="$3" existing="${4:-false}" full="${5:-false}"
  local port; port="$(free_port)"
  local -a mock_args=(--port "$port" --immutability-status "$immutability_status")
  [ "$existing" = "true" ] && mock_args+=(--existing-canary)
  [ "$full" = "true" ] && mock_args+=(--full-list)
  python3 "$MOCK" "${mock_args[@]}" &
  local pid=$!; PIDS+=("$pid")
  for _ in $(seq 1 30); do curl -fsS "http://127.0.0.1:$port/actuator/health" >/dev/null 2>&1 && break; sleep 0.1; done
  set +e
  Y2_CANARY_BASE_URL="http://127.0.0.1:$port" \
  Y2_CANARY_ADMIN_EMAIL='admin@example.test' \
  Y2_CANARY_ADMIN_PASSWORD='test-password' \
  Y2_CANARY_ALLOW_HTTP='true' \
  DEPLOYED_COMMIT_SHA='aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa' \
  Y2_CANARY_EVIDENCE_FILE="$evidence" \
  bash "$SCRIPT"
  local rc=$?
  set -e
  kill "$pid" 2>/dev/null || true
  [ "$rc" -eq "$expected_rc" ] || { echo "expected rc=$expected_rc got rc=$rc"; exit 1; }
}

PASS_EVIDENCE="$TMP/pass.json"
run_case 409 0 "$PASS_EVIDENCE"
jq -e '
  .schema == "snad.workflow-y2.production-write-canary.v1" and
  .result == "PASS" and
  .releaseSha == "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" and
  .definition.publicationState == "PUBLISHED" and
  .definition.engineGeneration == "Y2" and
  .instance.status == "COMPLETED" and
  .instance.workflowVersion == 1 and
  .instance.definitionVersionId == .definition.id and
  .immutability.addStepHttpStatus == 409 and
  .immutability.addTransitionHttpStatus == 409 and
  (.checks | length >= 10)
' "$PASS_EVIDENCE" >/dev/null
! grep -q 'test-password\|test-token\|admin@example' "$PASS_EVIDENCE"

FAIL_EVIDENCE="$TMP/fail.json"
run_case 200 1 "$FAIL_EVIDENCE"
jq -e '.result == "FAIL" and .failureStage == "published-immutability"' "$FAIL_EVIDENCE" >/dev/null

EXISTING_EVIDENCE="$TMP/existing.json"
run_case 409 1 "$EXISTING_EVIDENCE" true
jq -e '.result == "FAIL" and .failureStage == "preexisting-canary"' "$EXISTING_EVIDENCE" >/dev/null

FULL_EVIDENCE="$TMP/full.json"
run_case 409 1 "$FULL_EVIDENCE" false true
jq -e '.result == "FAIL" and .failureStage == "preexisting-canary-scan-incomplete"' "$FULL_EVIDENCE" >/dev/null

echo 'verify-workflow-y2-production-write-canary tests: PASS'
