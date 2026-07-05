#!/usr/bin/env bash
set -Eeuo pipefail

# ---- Initialize evidence workspace ----
ACTUAL_SHA="$(git rev-parse HEAD)"
[ "$ACTUAL_SHA" = "$RELEASE_SHA" ] || {
  echo "::error::Checked-out SHA does not match the authorized release SHA."
  exit 1
}
rm -rf evidence
mkdir -p evidence
jq -n \
  --arg releaseSha "$RELEASE_SHA" \
  --arg actualSha "$ACTUAL_SHA" \
  --arg runId "$GITHUB_RUN_ID" \
  '{
    releaseSha:$releaseSha,
    actualSha:$actualSha,
    runId:$runId,
    verifiedAt:(now|todateiso8601),
    result:(if $actualSha == $releaseSha then "PASS" else "FAIL" end)
  }' > evidence/release-sha.json

# ---- Verify workflow-dispatch input isolation ----
WORKFLOW_FILE=".github/workflows/commercial-go-live.yml"
INPUT_PREFIX='$'"{{ inputs."
CONFIRM_BINDING='RELEASE_CONFIRMATION: $'"{{ inputs.confirm }}"
SHA_BINDING='REQUESTED_RELEASE_SHA: $'"{{ inputs.release_sha }}"

DIRECT_INPUT_COUNT="$(grep -F "$INPUT_PREFIX" "$WORKFLOW_FILE" | wc -l | tr -d '[:space:]')"
CONFIRM_BINDING_COUNT="$(grep -F "$CONFIRM_BINDING" "$WORKFLOW_FILE" | wc -l | tr -d '[:space:]')"
SHA_BINDING_COUNT="$(grep -F "$SHA_BINDING" "$WORKFLOW_FILE" | wc -l | tr -d '[:space:]')"
LEGACY_PREFIX='github.event.'
LEGACY_INPUT_COUNT="$(grep -F "${LEGACY_PREFIX}inputs" "$WORKFLOW_FILE" | wc -l | tr -d '[:space:]')"
SHELL_INPUT_COUNT=$((DIRECT_INPUT_COUNT - CONFIRM_BINDING_COUNT - SHA_BINDING_COUNT))

[ "$DIRECT_INPUT_COUNT" -eq 2 ] || {
  echo "::error::Unexpected workflow input expression count."
  exit 1
}
[ "$CONFIRM_BINDING_COUNT" -eq 1 ] || {
  echo "::error::Release confirmation is not bound exactly once through job env."
  exit 1
}
[ "$SHA_BINDING_COUNT" -eq 1 ] || {
  echo "::error::Release SHA is not bound exactly once through job env."
  exit 1
}
[ "$LEGACY_INPUT_COUNT" -eq 0 ] || {
  echo "::error::Legacy workflow inputs are referenced."
  exit 1
}
[ "$SHELL_INPUT_COUNT" -eq 0 ] || {
  echo "::error::Workflow inputs are interpolated directly inside shell."
  exit 1
}

jq -n \
  --arg releaseSha "$RELEASE_SHA" \
  --argjson directInputExpressions "$DIRECT_INPUT_COUNT" \
  --argjson environmentBindings "$((CONFIRM_BINDING_COUNT + SHA_BINDING_COUNT))" \
  --argjson legacyInputExpressions "$LEGACY_INPUT_COUNT" \
  --argjson shellInputExpressions "$SHELL_INPUT_COUNT" \
  '{
    releaseSha:$releaseSha,
    directInputExpressions:$directInputExpressions,
    environmentBindings:$environmentBindings,
    legacyInputExpressions:$legacyInputExpressions,
    shellInputExpressions:$shellInputExpressions,
    result:(
      if (
        $directInputExpressions == 2 and
        $environmentBindings == 2 and
        $legacyInputExpressions == 0 and
        $shellInputExpressions == 0
      ) then "PASS" else "FAIL" end
    )
  }' > evidence/workflow-input-security-evidence.json

# ---- Repository current-tree secret scan ----
REPORT_DIR="$RUNNER_TEMP/gitleaks-commercial"
rm -rf "$REPORT_DIR"
mkdir -p "$REPORT_DIR"

set +e
docker run --rm \
  -v "$PWD:/repo" \
  -v "$REPORT_DIR:/report" \
  zricethezav/gitleaks:v8.24.3 detect \
  --source=/repo \
  --config=/repo/.gitleaks.toml \
  --no-git \
  --redact \
  --report-format=json \
  --report-path=/report/findings.json \
  --exit-code=1
SCAN_STATUS=$?
set -e

[ "$SCAN_STATUS" -eq 0 ] || {
  FINDING_COUNT=0
  [ ! -f "$REPORT_DIR/findings.json" ] || FINDING_COUNT="$(jq 'length' "$REPORT_DIR/findings.json")"
  echo "::error::Current-tree secret scan failed with $FINDING_COUNT finding(s)."
  exit 1
}

if [ -f "$REPORT_DIR/findings.json" ]; then
  jq -e 'type == "array"' "$REPORT_DIR/findings.json" >/dev/null
  FINDING_COUNT="$(jq 'length' "$REPORT_DIR/findings.json")"
else
  FINDING_COUNT=0
fi

[ "$FINDING_COUNT" -eq 0 ] || exit 1

jq -n \
  --arg releaseSha "$RELEASE_SHA" \
  --argjson findings "$FINDING_COUNT" \
  '{
    releaseSha:$releaseSha,
    tool:"gitleaks",
    mode:"current-tree",
    findings:$findings,
    result:(if $findings == 0 then "PASS" else "FAIL" end)
  }' > evidence/secret-scan-evidence.json

# ---- Read Render production configuration and verify tenant binding ----

curl --fail-with-body --silent --show-error --max-time 30 \
  -H "Authorization: Bearer $RENDER_API_KEY" \
  -H "Accept: application/json" \
  "https://api.render.com/v1/services/$RENDER_SERVICE_ID/env-vars?limit=100" \
  > "$RUNNER_TEMP/render-env.json"

test "$(jq 'length' "$RUNNER_TEMP/render-env.json")" -lt 100 || {
  echo "::error::Render environment pagination is incomplete."
  exit 1
}

jq '[.[]? | (.envVar // .)]' "$RUNNER_TEMP/render-env.json" \
  > "$RUNNER_TEMP/render-env-normalized.json"

for key in \
  DATABASE_URL DATABASE_USERNAME DATABASE_PASSWORD \
  SECURITY_NOTIFICATION_RESEND_API_KEY SANAD_CONTROL_PLANE_TENANT_ID
do
  jq -e --arg key "$key" \
    'any(.[]; .key == $key and ((.value // "") | length > 0))' \
    "$RUNNER_TEMP/render-env-normalized.json" >/dev/null || {
      echo "::error::Required Render environment value is missing: $key"
      exit 1
    }
done

DATABASE_URL="$(jq -r '.[] | select(.key == "DATABASE_URL") | .value' "$RUNNER_TEMP/render-env-normalized.json")"
DATABASE_USERNAME="$(jq -r '.[] | select(.key == "DATABASE_USERNAME") | .value' "$RUNNER_TEMP/render-env-normalized.json")"
DATABASE_PASSWORD="$(jq -r '.[] | select(.key == "DATABASE_PASSWORD") | .value' "$RUNNER_TEMP/render-env-normalized.json")"
RESEND_API_KEY="$(jq -r '.[] | select(.key == "SECURITY_NOTIFICATION_RESEND_API_KEY") | .value' "$RUNNER_TEMP/render-env-normalized.json")"
RENDER_CONTROL_PLANE_TENANT="$(jq -r '.[] | select(.key == "SANAD_CONTROL_PLANE_TENANT_ID") | .value' "$RUNNER_TEMP/render-env-normalized.json")"

for value in \
  "$DATABASE_URL" "$DATABASE_USERNAME" "$DATABASE_PASSWORD" \
  "$RESEND_API_KEY" "$RENDER_CONTROL_PLANE_TENANT"
do
  echo "::add-mask::$value"
done

CONTROL_PLANE_TENANT_MATCH=false
if [ "$RENDER_CONTROL_PLANE_TENANT" = "$CONTROL_PLANE_TENANT_ID" ]; then
  CONTROL_PLANE_TENANT_MATCH=true
else
  echo "::error::GitHub and Render Control Plane tenant configuration do not match."
  exit 1
fi

{
  echo "DATABASE_URL=$DATABASE_URL"
  echo "DATABASE_USERNAME=$DATABASE_USERNAME"
  echo "DATABASE_PASSWORD=$DATABASE_PASSWORD"
  echo "RESEND_API_KEY=$RESEND_API_KEY"
  echo "CONTROL_PLANE_TENANT_MATCH=$CONTROL_PLANE_TENANT_MATCH"
} >> "$GITHUB_ENV"

rm -f "$RUNNER_TEMP/render-env.json" "$RUNNER_TEMP/render-env-normalized.json"

# ---- Validate database and Resend credentials ----
sudo apt-get update -qq
sudo apt-get install -y -qq postgresql-client >/dev/null

RAW_URL="${DATABASE_URL#jdbc:}"
RAW_URL="${RAW_URL#postgresql://}"
RAW_URL="${RAW_URL#https://}"
HOST_PORT="${RAW_URL%%/*}"
DB_PART="${RAW_URL#*/}"
DB_NAME="${DB_PART%%\?*}"
PGHOST="${HOST_PORT%%:*}"
PGPORT="${HOST_PORT#*:}"
[ "$PGPORT" != "$PGHOST" ] || PGPORT=5432

DB_RESULT="$(PGPASSWORD="$DATABASE_PASSWORD" psql \
  -h "$PGHOST" -p "$PGPORT" -U "$DATABASE_USERNAME" -d "$DB_NAME" \
  --no-psqlrc --set=ON_ERROR_STOP=1 --tuples-only --no-align \
  --command='SELECT 1;')"
[ "$(tr -d '[:space:]' <<< "$DB_RESULT")" = "1" ] || {
  echo "::error::Database credential validation failed."
  exit 1
}

RESEND_STATUS="$(curl --silent --show-error --output /dev/null --write-out '%{http_code}' \
  --max-time 20 \
  --header "Authorization: Bearer $RESEND_API_KEY" \
  "https://api.resend.com/domains")"
[ "$RESEND_STATUS" = "200" ] || {
  echo "::error::Resend credential validation failed."
  exit 1
}

curl --fail-with-body --silent --show-error --max-time 30 \
  -H "Authorization: Bearer $BRANCH_PROTECTION_TOKEN" \
  -H "Accept: application/vnd.github+json" \
  -H "X-GitHub-Api-Version: 2022-11-28" \
  "https://api.github.com/repos/$GITHUB_REPOSITORY/environments/production/secrets?per_page=100" \
  > "$RUNNER_TEMP/production-secrets-metadata.json"

REQUIRED_PRODUCTION_SECRET_NAMES=(
  SANAD_ADMIN_EMAIL
  SANAD_ADMIN_PASSWORD
  CONTROL_PLANE_NON_ADMIN_EMAIL
  CONTROL_PLANE_NON_ADMIN_PASSWORD
  CONTROL_PLANE_NON_ADMIN_TENANT_ID
  CONTROL_PLANE_TENANT_ID
  RENDER_API_KEY
  RENDER_SERVICE_ID
  VERCEL_TOKEN
  VERCEL_PROJECT_ID
  VERCEL_TEAM_ID
  BRANCH_PROTECTION_TOKEN
)

VERIFIED_PRODUCTION_SECRET_COUNT=0
for required_name in "${REQUIRED_PRODUCTION_SECRET_NAMES[@]}"; do
  jq -e --arg name "$required_name" \
    'any(.secrets[]?; .name == $name)' \
    "$RUNNER_TEMP/production-secrets-metadata.json" >/dev/null || {
      echo "::error::Required production environment secret is missing: $required_name"
      exit 1
    }
  VERIFIED_PRODUCTION_SECRET_COUNT=$((VERIFIED_PRODUCTION_SECRET_COUNT + 1))
done

ADMIN_PASSWORD_UPDATED_AT="$(jq -r '
  [.secrets[]? | select(.name == "SANAD_ADMIN_PASSWORD")][0].updated_at // empty
' "$RUNNER_TEMP/production-secrets-metadata.json")"
test -n "$ADMIN_PASSWORD_UPDATED_AT" || {
  echo "::error::SANAD_ADMIN_PASSWORD metadata is unavailable."
  exit 1
}
[ "$ADMIN_PASSWORD_UPDATED_AT" \> "$CREDENTIAL_ROTATION_NOT_BEFORE" ] || \
  [ "$ADMIN_PASSWORD_UPDATED_AT" = "$CREDENTIAL_ROTATION_NOT_BEFORE" ] || {
    echo "::error::Administrative credential has not been rotated after the mandated cutoff."
    exit 1
  }

jq -n \
  --arg releaseSha "$RELEASE_SHA" \
  --arg databaseProbe "$(tr -d '[:space:]' <<< "$DB_RESULT")" \
  --arg resendHttp "$RESEND_STATUS" \
  --arg adminPasswordUpdatedAt "$ADMIN_PASSWORD_UPDATED_AT" \
  --arg rotationNotBefore "$CREDENTIAL_ROTATION_NOT_BEFORE" \
  --argjson requiredProductionSecretCount "${#REQUIRED_PRODUCTION_SECRET_NAMES[@]}" \
  --argjson verifiedProductionSecretCount "$VERIFIED_PRODUCTION_SECRET_COUNT" \
  --argjson tenantMatch "$CONTROL_PLANE_TENANT_MATCH" \
  '{
    releaseSha:$releaseSha,
    databaseCredential:{
      probe:$databaseProbe,
      result:(if $databaseProbe == "1" then "VALID" else "INVALID" end)
    },
    resendCredential:{
      httpStatus:$resendHttp,
      result:(if $resendHttp == "200" then "VALID" else "INVALID" end)
    },
    productionEnvironmentSecrets:{
      requiredCount:$requiredProductionSecretCount,
      verifiedCount:$verifiedProductionSecretCount,
      result:(
        if $verifiedProductionSecretCount == $requiredProductionSecretCount
        then "VERIFIED" else "INCOMPLETE" end
      )
    },
    credentialRotation:{
      administrativeSecretUpdatedAt:$adminPasswordUpdatedAt,
      notBefore:$rotationNotBefore,
      result:(
        if $adminPasswordUpdatedAt >= $rotationNotBefore
        then "VERIFIED" else "NOT_VERIFIED" end
      )
    },
    controlPlaneTenantConfiguration:{
      matched:$tenantMatch,
      result:(if $tenantMatch then "VERIFIED" else "NOT_VERIFIED" end)
    },
    result:(
      if (
        $databaseProbe == "1" and
        $resendHttp == "200" and
        $verifiedProductionSecretCount == $requiredProductionSecretCount and
        $adminPasswordUpdatedAt >= $rotationNotBefore and
        $tenantMatch
      ) then "PASS" else "FAIL" end
    )
  }' > evidence/credential-validation-evidence.json

# ---- Verify Flyway production history ----
bash scripts/production/verify-flyway.sh

RAW_URL="${DATABASE_URL#jdbc:}"
RAW_URL="${RAW_URL#postgresql://}"
RAW_URL="${RAW_URL#https://}"
HOST_PORT="${RAW_URL%%/*}"
DB_PART="${RAW_URL#*/}"
DB_NAME="${DB_PART%%\?*}"
PGHOST="${HOST_PORT%%:*}"
PGPORT="${HOST_PORT#*:}"
[ "$PGPORT" != "$PGHOST" ] || PGPORT=5432

HISTORY_JSON="$(PGPASSWORD="$DATABASE_PASSWORD" psql \
  -h "$PGHOST" -p "$PGPORT" -U "$DATABASE_USERNAME" -d "$DB_NAME" \
  --no-psqlrc --set=ON_ERROR_STOP=1 --tuples-only --no-align \
  --command="
    SELECT COALESCE(
      json_agg(
        json_build_object(
          'version', version,
          'type', type,
          'description', description,
          'success', success
        )
        ORDER BY installed_rank
      ),
      '[]'::json
    )
    FROM flyway_schema_history
    WHERE version IN ('15','20260702.1','20260702.2','20260702.3');
  ")"

echo "$HISTORY_JSON" | jq -e '
  length == 4 and
  all(.[]; .success == true) and
  ([.[].version] | sort) ==
    (["15","20260702.1","20260702.2","20260702.3"] | sort)
' >/dev/null || {
  echo "::error::Flyway production history is incomplete or failed."
  exit 1
}

jq -n \
  --arg releaseSha "$RELEASE_SHA" \
  --argjson history "$HISTORY_JSON" \
  '{
    releaseSha:$releaseSha,
    requiredVersions:["15","20260702.1","20260702.2","20260702.3"],
    history:$history,
    result:(
      if (($history|length) == 4 and all($history[]; .success == true))
      then "PASS" else "FAIL" end
    )
  }' > evidence/flyway-evidence.json
