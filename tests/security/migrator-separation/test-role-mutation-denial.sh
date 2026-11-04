#!/usr/bin/env bash
# Regression: cert runner must refuse cluster-wide role writes without explicit approval.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
set +e
env -u SEP_ROLE_MUTATION_APPROVED SEP_ARTIFACTS="$TMP/evidence" \
  bash "$ROOT/tests/security/migrator-separation/run-separation-cert.sh" \
  >"$TMP/denial-output.txt" 2>&1
RC=$?
set -e
if [ "$RC" -ne 1 ]; then
  echo "ROLE_MUTATION_DENIAL=FAIL unexpected_exit=$RC"
  exit 1
fi
if ! grep -Fq "BLOCKED: cluster-wide role changes require explicit isolated certification approval" "$TMP/denial-output.txt"; then
  echo "ROLE_MUTATION_DENIAL=FAIL expected_denial_marker_missing"
  exit 1
fi
echo "ROLE_MUTATION_DENIAL=PASS (expected runner exit=1 before database or role mutations)"
