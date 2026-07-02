#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
EVIDENCE_FILE="${1:-${ROOT_DIR}/artifacts/security/aws-bootstrap/bootstrap-controls.json}"
REPOSITORY="${GITHUB_REPOSITORY:-snadaiapp-png/SNAD}"
READER_ENVIRONMENT="${READER_ENVIRONMENT:-nvd-reader}"
PUBLISHER_ENVIRONMENT="${PUBLISHER_ENVIRONMENT:-nvd-publisher}"

command -v gh >/dev/null || { echo 'GitHub CLI is required.' >&2; exit 1; }
command -v python3 >/dev/null || { echo 'Python 3 is required.' >&2; exit 1; }
[[ -f "${EVIDENCE_FILE}" ]] || { echo "Bootstrap evidence not found: ${EVIDENCE_FILE}" >&2; exit 1; }
gh auth status >/dev/null

read_value() {
  local key="$1"
  python3 - "${EVIDENCE_FILE}" "$key" <<'PY'
import json, sys
path, key = sys.argv[1:]
data = json.load(open(path, encoding='utf-8'))
value = data.get(key, '')
if not value:
    raise SystemExit(f'missing evidence value: {key}')
print(value)
PY
}

RESULT="$(read_value result)"
[[ "${RESULT}" == 'pass' ]] || { echo 'Bootstrap controls are not PASS.' >&2; exit 1; }

ACCOUNT_ID="$(read_value account_id)"
REGION="$(read_value region)"
BUCKET="$(read_value bucket)"
PREFIX="$(read_value prefix)"
READER_ROLE="$(read_value reader_role_arn)"
PUBLISHER_ROLE="$(read_value publisher_role_arn)"
KMS_KEY="$(read_value kms_key_arn)"

printf 'Creating or updating GitHub environments...\n'
gh api --method PUT "repos/${REPOSITORY}/environments/${READER_ENVIRONMENT}" --silent
gh api --method PUT "repos/${REPOSITORY}/environments/${PUBLISHER_ENVIRONMENT}" --silent

set_repo_variable() {
  gh variable set "$1" --body "$2" --repo "${REPOSITORY}"
}

set_environment_variable() {
  gh variable set "$1" --body "$2" --repo "${REPOSITORY}" --env "$3"
}

printf 'Configuring repository variables...\n'
set_repo_variable NVD_SNAPSHOT_BACKEND s3
set_repo_variable NVD_SNAPSHOT_AWS_ACCOUNT_ID "${ACCOUNT_ID}"
set_repo_variable NVD_SNAPSHOT_REGION "${REGION}"
set_repo_variable NVD_SNAPSHOT_BUCKET "${BUCKET}"
set_repo_variable NVD_SNAPSHOT_PREFIX "${PREFIX}"
set_repo_variable NVD_SNAPSHOT_READER_ROLE "${READER_ROLE}"
set_repo_variable NVD_SNAPSHOT_PUBLISHER_ROLE "${PUBLISHER_ROLE}"
set_repo_variable NVD_SNAPSHOT_KMS_KEY_ARN "${KMS_KEY}"

printf 'Configuring reader environment variables...\n'
set_environment_variable NVD_SNAPSHOT_BACKEND s3 "${READER_ENVIRONMENT}"
set_environment_variable NVD_SNAPSHOT_AWS_ACCOUNT_ID "${ACCOUNT_ID}" "${READER_ENVIRONMENT}"
set_environment_variable NVD_SNAPSHOT_REGION "${REGION}" "${READER_ENVIRONMENT}"
set_environment_variable NVD_SNAPSHOT_BUCKET "${BUCKET}" "${READER_ENVIRONMENT}"
set_environment_variable NVD_SNAPSHOT_PREFIX "${PREFIX}" "${READER_ENVIRONMENT}"
set_environment_variable NVD_SNAPSHOT_READER_ROLE "${READER_ROLE}" "${READER_ENVIRONMENT}"
set_environment_variable NVD_SNAPSHOT_KMS_KEY_ARN "${KMS_KEY}" "${READER_ENVIRONMENT}"

printf 'Configuring publisher environment variables...\n'
set_environment_variable NVD_SNAPSHOT_BACKEND s3 "${PUBLISHER_ENVIRONMENT}"
set_environment_variable NVD_SNAPSHOT_AWS_ACCOUNT_ID "${ACCOUNT_ID}" "${PUBLISHER_ENVIRONMENT}"
set_environment_variable NVD_SNAPSHOT_REGION "${REGION}" "${PUBLISHER_ENVIRONMENT}"
set_environment_variable NVD_SNAPSHOT_BUCKET "${BUCKET}" "${PUBLISHER_ENVIRONMENT}"
set_environment_variable NVD_SNAPSHOT_PREFIX "${PREFIX}" "${PUBLISHER_ENVIRONMENT}"
set_environment_variable NVD_SNAPSHOT_PUBLISHER_ROLE "${PUBLISHER_ROLE}" "${PUBLISHER_ENVIRONMENT}"
set_environment_variable NVD_SNAPSHOT_KMS_KEY_ARN "${KMS_KEY}" "${PUBLISHER_ENVIRONMENT}"

printf 'Verifying non-secret GitHub variables...\n'
gh variable list --repo "${REPOSITORY}" | grep -E '^NVD_SNAPSHOT_(BACKEND|AWS_ACCOUNT_ID|REGION|BUCKET|PREFIX|READER_ROLE|PUBLISHER_ROLE|KMS_KEY_ARN)[[:space:]]' >/dev/null

cat <<EOF
GitHub configuration completed for ${REPOSITORY}.

Reader environment: ${READER_ENVIRONMENT}
Publisher environment: ${PUBLISHER_ENVIRONMENT}
Authentication model: short-lived GitHub OIDC sessions
Long-lived AWS access keys stored in GitHub: no

Next workflow sequence:
1. AWS NVD Snapshot Mirror
2. AWS NVD Reader Validation
3. Security Scan (OWASP) acceptance run
EOF
