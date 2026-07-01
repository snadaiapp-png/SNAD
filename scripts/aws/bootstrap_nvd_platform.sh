#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SOURCE_TEMPLATE="${ROOT_DIR}/infra/aws/nvd-platform-bootstrap.yml"
STACK_NAME="${STACK_NAME:-snad-nvd-platform}"
REPOSITORY_OWNER="${REPOSITORY_OWNER:-snadaiapp-png}"
REPOSITORY_NAME="${REPOSITORY_NAME:-SNAD}"
READER_ENVIRONMENT="${READER_ENVIRONMENT:-nvd-reader}"
PUBLISHER_ENVIRONMENT="${PUBLISHER_ENVIRONMENT:-nvd-publisher}"
SNAPSHOT_PREFIX="${NVD_SNAPSHOT_PREFIX:-snad-nvd}"
RETENTION_DAYS="${NONCURRENT_RETENTION_DAYS:-90}"
OUTPUT_DIR="${OUTPUT_DIR:-${ROOT_DIR}/artifacts/security/aws-bootstrap}"
TEMPLATE="${OUTPUT_DIR}/nvd-platform-bootstrap.rendered.yml"

command -v aws >/dev/null || { echo 'AWS CLI is required.' >&2; exit 1; }
command -v python3 >/dev/null || { echo 'Python 3 is required.' >&2; exit 1; }
[[ -f "${SOURCE_TEMPLATE}" ]] || { echo "Template not found: ${SOURCE_TEMPLATE}" >&2; exit 1; }

REGION="${AWS_REGION:-${AWS_DEFAULT_REGION:-$(aws configure get region 2>/dev/null || true)}}"
[[ -n "${REGION}" ]] || { echo 'Set AWS_REGION to the approved deployment region.' >&2; exit 1; }

mkdir -p "${OUTPUT_DIR}"
python3 "${ROOT_DIR}/scripts/aws/render_nvd_platform_template.py" \
  --source "${SOURCE_TEMPLATE}" \
  --output "${TEMPLATE}"

IDENTITY_FILE="${OUTPUT_DIR}/bootstrap-caller-identity.json"
OUTPUTS_FILE="${OUTPUT_DIR}/bootstrap-outputs.json"

aws sts get-caller-identity --output json > "${IDENTITY_FILE}"
ACCOUNT_ID="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["Account"])' "${IDENTITY_FILE}")"
CALLER_ARN="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["Arn"])' "${IDENTITY_FILE}")"
PARTITION="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["Arn"].split(":",2)[1])' "${IDENTITY_FILE}")"
OIDC_PROVIDER_ARN="arn:${PARTITION}:iam::${ACCOUNT_ID}:oidc-provider/token.actions.githubusercontent.com"
CREATE_OIDC='true'
EXISTING_OIDC=''
if aws iam get-open-id-connect-provider --open-id-connect-provider-arn "${OIDC_PROVIDER_ARN}" >/dev/null 2>&1; then
  CREATE_OIDC='false'
  EXISTING_OIDC="${OIDC_PROVIDER_ARN}"
fi

BUCKET_NAME="${NVD_SNAPSHOT_BUCKET:-snad-nvd-${ACCOUNT_ID}-${REGION}}"
BUCKET_NAME="$(printf '%s' "${BUCKET_NAME}" | tr '[:upper:]' '[:lower:]')"

printf 'Validating CloudFormation template...\n'
aws cloudformation validate-template \
  --region "${REGION}" \
  --template-body "file://${TEMPLATE}" >/dev/null

printf 'Deploying stack %s in account %s, region %s...\n' "${STACK_NAME}" "${ACCOUNT_ID}" "${REGION}"
aws cloudformation deploy \
  --region "${REGION}" \
  --stack-name "${STACK_NAME}" \
  --template-file "${TEMPLATE}" \
  --capabilities CAPABILITY_NAMED_IAM \
  --no-fail-on-empty-changeset \
  --parameter-overrides \
    CreateGitHubOidcProvider="${CREATE_OIDC}" \
    ExistingGitHubOidcProviderArn="${EXISTING_OIDC}" \
    RepositoryOwner="${REPOSITORY_OWNER}" \
    RepositoryName="${REPOSITORY_NAME}" \
    ReaderEnvironment="${READER_ENVIRONMENT}" \
    PublisherEnvironment="${PUBLISHER_ENVIRONMENT}" \
    SnapshotBucketName="${BUCKET_NAME}" \
    SnapshotPrefix="${SNAPSHOT_PREFIX}" \
    NoncurrentVersionRetentionDays="${RETENTION_DAYS}"

aws cloudformation describe-stacks \
  --region "${REGION}" \
  --stack-name "${STACK_NAME}" \
  --query 'Stacks[0].Outputs' \
  --output json > "${OUTPUTS_FILE}"

output_value() {
  local key="$1"
  python3 - "$OUTPUTS_FILE" "$key" <<'PY'
import json, sys
path, key = sys.argv[1:]
outputs = {item['OutputKey']: item['OutputValue'] for item in json.load(open(path, encoding='utf-8'))}
value = outputs.get(key, '')
if not value:
    raise SystemExit(f'missing CloudFormation output: {key}')
print(value)
PY
}

READER_ROLE_ARN="$(output_value ReaderRoleArn)"
PUBLISHER_ROLE_ARN="$(output_value PublisherRoleArn)"
KMS_KEY_ARN="$(output_value SnapshotKmsKeyArn)"
DEPLOYED_BUCKET="$(output_value SnapshotBucketName)"
DEPLOYED_PREFIX="$(output_value SnapshotPrefix)"

aws s3api get-bucket-versioning --region "${REGION}" --bucket "${DEPLOYED_BUCKET}" --output json > "${OUTPUT_DIR}/bucket-versioning.json"
aws s3api get-bucket-encryption --region "${REGION}" --bucket "${DEPLOYED_BUCKET}" --output json > "${OUTPUT_DIR}/bucket-encryption.json"
aws s3api get-public-access-block --region "${REGION}" --bucket "${DEPLOYED_BUCKET}" --output json > "${OUTPUT_DIR}/bucket-public-access-block.json"

python3 - "${OUTPUT_DIR}" "${ACCOUNT_ID}" "${REGION}" "${CALLER_ARN}" "${DEPLOYED_BUCKET}" "${DEPLOYED_PREFIX}" "${READER_ROLE_ARN}" "${PUBLISHER_ROLE_ARN}" "${KMS_KEY_ARN}" <<'PY'
import json, pathlib, sys
out, account, region, caller, bucket, prefix, reader, publisher, kms = sys.argv[1:]
base = pathlib.Path(out)
versioning = json.loads((base / 'bucket-versioning.json').read_text(encoding='utf-8'))
encryption = json.loads((base / 'bucket-encryption.json').read_text(encoding='utf-8'))
public_access = json.loads((base / 'bucket-public-access-block.json').read_text(encoding='utf-8'))
configuration = public_access.get('PublicAccessBlockConfiguration', {})
checks = {
    'versioning_enabled': versioning.get('Status') == 'Enabled',
    'encryption_configured': bool(encryption.get('ServerSideEncryptionConfiguration', {}).get('Rules')),
    'public_access_blocked': all(configuration.get(key) is True for key in (
        'BlockPublicAcls', 'IgnorePublicAcls', 'BlockPublicPolicy', 'RestrictPublicBuckets'
    )),
}
result = {
    'account_id': account,
    'region': region,
    'bootstrap_caller_arn': caller,
    'bucket': bucket,
    'prefix': prefix,
    'reader_role_arn': reader,
    'publisher_role_arn': publisher,
    'kms_key_arn': kms,
    'checks': checks,
    'result': 'pass' if all(checks.values()) else 'fail',
}
(base / 'bootstrap-controls.json').write_text(json.dumps(result, indent=2, sort_keys=True), encoding='utf-8')
if result['result'] != 'pass':
    raise SystemExit('AWS bootstrap controls failed')
print(json.dumps(result, indent=2, sort_keys=True))
PY

cat <<EOF

AWS bootstrap completed and verified.
NVD_SNAPSHOT_BACKEND=s3
NVD_SNAPSHOT_AWS_ACCOUNT_ID=${ACCOUNT_ID}
NVD_SNAPSHOT_REGION=${REGION}
NVD_SNAPSHOT_BUCKET=${DEPLOYED_BUCKET}
NVD_SNAPSHOT_PREFIX=${DEPLOYED_PREFIX}
NVD_SNAPSHOT_READER_ROLE=${READER_ROLE_ARN}
NVD_SNAPSHOT_PUBLISHER_ROLE=${PUBLISHER_ROLE_ARN}
NVD_SNAPSHOT_KMS_KEY_ARN=${KMS_KEY_ARN}
Evidence directory: ${OUTPUT_DIR}
EOF
