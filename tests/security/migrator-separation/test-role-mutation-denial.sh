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
set +e
SEP_ROLE_MUTATION_APPROVED=isolated-certification-only SEP_KEEP_DB=0 SEP_ARTIFACTS="$TMP/evidence-no-cleanup" \
  bash "$ROOT/tests/security/migrator-separation/run-separation-cert.sh" \
  >"$TMP/no-cleanup-output.txt" 2>&1
CLEANUP_RC=$?
set -e
if [ "$CLEANUP_RC" -ne 1 ] || ! grep -Fq "BLOCKED: automatic database deletion is disabled" "$TMP/no-cleanup-output.txt"; then
  echo "AUTO_DELETION_DENIAL=FAIL (exit=$CLEANUP_RC)"
  exit 1
fi
echo "AUTO_DELETION_DENIAL=PASS (blocked before any database changes)"
