#!/usr/bin/env bash
# Static safeguard only: never claim runtime F05/F06 success from this test.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
F05="$ROOT/tests/security/g1-f05-backfill-transactional-trial.sql"
F06="$ROOT/tests/security/g1-callback-replay-minimal-acl-rollback.sql"
FLOW="$ROOT/.github/workflows/migrator-runtime-separation.yml"
RUNNER="$ROOT/tests/security/migrator-separation/run-separation-cert.sh"
DOC="$ROOT/docs/security/g1-remediation-reference-20261104.md"
require() {
  local file="$1" pattern="$2" label="$3"
  if ! grep -Fq -- "$pattern" "$file"; then
    echo "G1_STATIC_GUARD=FAIL missing=$label" >&2
    exit 1
  fi
}
for file in "$F05" "$FLOW" "$RUNNER" "$DOC"; do
  test -s "$file" || { echo "G1_STATIC_GUARD=FAIL missing_file" >&2; exit 1; }
done
require "$F05" 'BEGIN;' f05_transaction_start
require "$F05" 'ROLLBACK;' f05_transaction_rollback
require "$F05" "'separation_cert'" f05_database_guard
require "$F05" '7704438226812603328' f05_cluster_guard
require "$F05" 'SET LOCAL sanad.g1_backfill_approved' f05_test_only_approval_flag
require "$F05" 'g1-login-index-admin-backfill.sql' f05_existing_admin_script
# F06 trial is intentionally untracked and NOT RUN after an execution-safety block.
# Verify it if present; its absence must not be interpreted as closure.
if [ -f "$F06" ]; then
  require "$F06" 'BEGIN;' f06_transaction_start
  require "$F06" 'ROLLBACK;' f06_transaction_rollback
  require "$F06" 'REVOKE UPDATE ON public.service_callback_replay FROM sanad;' f06_scoped_revoke
  require "$F06" "'separation_cert'" f06_database_guard
fi
require "$FLOW" '17.6' pinned_pg_release
require "$RUNNER" 'S7-g1-release-catalog.log' strict_catalog_gate
require "$DOC" 'G1 remains HOLD' release_blocked
require "$DOC" 'NOT RUN' not_run_status_preserved
echo 'G1_STATIC_EVIDENCE_GUARD=PASS (static only; F05/F06 NOT RUN)'
