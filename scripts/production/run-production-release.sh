#!/usr/bin/env bash
set -euo pipefail

: "${TARGET_SHA:?TARGET_SHA is required}"
: "${RENDER_API_KEY:?RENDER_API_KEY is required}"
: "${RENDER_SERVICE_ID:?RENDER_SERVICE_ID is required}"
: "${PRODUCTION_DATABASE_URL:?PRODUCTION_DATABASE_URL is required}"
: "${DATABASE_USERNAME:?DATABASE_USERNAME is required}"
: "${DATABASE_PASSWORD:?DATABASE_PASSWORD is required}"
: "${PRODUCTION_BASE_URL:?PRODUCTION_BASE_URL is required}"
: "${WEB_PRODUCTION_BASE_URL:?WEB_PRODUCTION_BASE_URL is required}"
: "${CONTROL_PLANE_ADMIN_EMAIL:?CONTROL_PLANE_ADMIN_EMAIL is required}"
: "${CONTROL_PLANE_ADMIN_PASSWORD:?CONTROL_PLANE_ADMIN_PASSWORD is required}"
: "${CONTROL_PLANE_NON_ADMIN_EMAIL:?CONTROL_PLANE_NON_ADMIN_EMAIL is required}"
: "${CONTROL_PLANE_NON_ADMIN_PASSWORD:?CONTROL_PLANE_NON_ADMIN_PASSWORD is required}"
: "${CONTROL_PLANE_NON_ADMIN_TENANT_ID:?CONTROL_PLANE_NON_ADMIN_TENANT_ID is required}"

ROLLBACK_ON_FAILURE="${ROLLBACK_ON_FAILURE:-true}"
PREVIOUS_COMMIT=""
DEPLOYMENT_STARTED=false
RELEASE_COMPLETE=false

cleanup() {
  rm -f /tmp/render-env.json /tmp/render-env-normalized.json /tmp/render-deploys.json \
    /tmp/readiness.json /tmp/control-plane.html /tmp/control-plane-api.json
}

rollback_if_needed() {
  local result=$?
  cleanup
  if [ "$result" -ne 0 ] && [ "$RELEASE_COMPLETE" != "true" ] && \
     [ "$DEPLOYMENT_STARTED" = "true" ] && [ "$ROLLBACK_ON_FAILURE" = "true" ] && \
     [ -n "$PREVIOUS_COMMIT" ]; then
    echo "Release verification failed; redeploying the previous live commit."
    render deploys create "$RENDER_SERVICE_ID" \
      --clear-cache \
      --commit "$PREVIOUS_COMMIT" \
      --wait \
      --output json \
      --confirm > render-rollback.json || true
  fi
  exit "$result"
}
trap rollback_if_needed EXIT

[[ "$TARGET_SHA" =~ ^[0-9a-f]{40}$ ]]
[[ "$PRODUCTION_BASE_URL" == https://* ]]
[[ "$WEB_PRODUCTION_BASE_URL" == https://* ]]

remote_main=$(git ls-remote "https://github.com/$GITHUB_REPOSITORY.git" refs/heads/main | awk '{print $1}')
[ "$remote_main" = "$TARGET_SHA" ] || {
  echo "::error::TARGET_SHA is not the current main head."
  exit 1
}
[ "$(git rev-parse HEAD)" = "$TARGET_SHA" ] || {
  echo "::error::The checked-out commit does not match TARGET_SHA."
  exit 1
}

curl --fail-with-body --silent --show-error \
  --header "Authorization: Bearer $RENDER_API_KEY" \
  --header "Accept: application/json" \
  "https://api.render.com/v1/services/$RENDER_SERVICE_ID/env-vars?limit=100" \
  > /tmp/render-env.json
[ "$(jq 'length' /tmp/render-env.json)" -lt 100 ] || {
  echo "::error::Cannot prove complete Render environment pagination."
  exit 1
}
jq '[.[]? | (.envVar // .)]' /tmp/render-env.json > /tmp/render-env-normalized.json

required=(SPRING_PROFILES_ACTIVE DATABASE_URL DATABASE_USERNAME DATABASE_PASSWORD JWT_SECRET BOOTSTRAP_ENABLED SANAD_CONTROL_PLANE_TENANT_ID)
for key in "${required[@]}"; do
  jq -e --arg key "$key" 'any(.[]; .key == $key and ((.value // "") | length > 0))' \
    /tmp/render-env-normalized.json >/dev/null || {
    echo "::error::Required Render environment value is missing: $key"
    exit 1
  }
done

[ "$(jq -r '.[] | select(.key == "BOOTSTRAP_ENABLED") | .value // empty' /tmp/render-env-normalized.json)" = "false" ] || {
  echo "::error::BOOTSTRAP_ENABLED must equal false in production."
  exit 1
}

CONTROL_PLANE_TENANT_ID=$(jq -r '.[] | select(.key == "SANAD_CONTROL_PLANE_TENANT_ID") | .value // empty' /tmp/render-env-normalized.json)
export CONTROL_PLANE_TENANT_ID
echo "::add-mask::$CONTROL_PLANE_TENANT_ID"
bash scripts/production/verify-control-plane-tenant.sh

curl --fail-with-body --silent --show-error \
  --header "Authorization: Bearer $RENDER_API_KEY" \
  --header "Accept: application/json" \
  "https://api.render.com/v1/services/$RENDER_SERVICE_ID/deploys?limit=20" \
  > /tmp/render-deploys.json
PREVIOUS_COMMIT=$(jq -r '[.[]? | (.deploy // .)] | map(select((.status // "") == "live")) | .[0].commit.id // .[0].commitId // empty' /tmp/render-deploys.json)

DEPLOYMENT_STARTED=true
render deploys create "$RENDER_SERVICE_ID" \
  --clear-cache \
  --commit "$TARGET_SHA" \
  --wait \
  --output json \
  --confirm > render-deployment.json
jq -e '(.status // "") == "live"' render-deployment.json >/dev/null
DEPLOYMENT_ID=$(jq -r '(.id // .deploy.id // empty)' render-deployment.json)
test -n "$DEPLOYMENT_ID"

ready=false
for attempt in $(seq 1 72); do
  status=$(curl --silent --show-error --output /tmp/readiness.json --write-out '%{http_code}' \
    --max-time 20 "${PRODUCTION_BASE_URL%/}/actuator/health/readiness" || true)
  if [ "$status" = "200" ] && jq -e '.status == "UP"' /tmp/readiness.json >/dev/null 2>&1; then
    ready=true
    break
  fi
  sleep 5
done
[ "$ready" = "true" ] || { echo "::error::Production readiness did not become UP."; exit 1; }

bash scripts/production/verify-flyway.sh
bash scripts/production/verify-auth-contract.sh

control_status=$(curl --silent --show-error --location \
  --output /tmp/control-plane.html --write-out '%{http_code}' --max-time 60 \
  "${WEB_PRODUCTION_BASE_URL%/}/control-plane")
[ "$control_status" = "200" ] || {
  echo "::error::Vercel Control Plane returned HTTP $control_status."
  exit 1
}

bff_status=$(curl --silent --show-error \
  --output /tmp/control-plane-api.json --write-out '%{http_code}' --max-time 90 \
  "${WEB_PRODUCTION_BASE_URL%/}/api/platform/api/v1/control-plane/dashboard" || true)
case "$bff_status" in
  401|403) ;;
  *) echo "::error::Vercel BFF returned HTTP $bff_status; expected 401 or 403."; exit 1 ;;
esac

export DEPLOYED_COMMIT_SHA="$TARGET_SHA"
export CONTROL_PLANE_SMOKE_EVIDENCE_FILE=control-plane-authenticated-smoke.json
bash scripts/production/verify-control-plane-authenticated-smoke.sh

jq -n \
  --arg commit "$TARGET_SHA" \
  --arg deployment "$DEPLOYMENT_ID" \
  --arg previous "$PREVIOUS_COMMIT" \
  --arg backendUrl "$PRODUCTION_BASE_URL" \
  --arg webUrl "$WEB_PRODUCTION_BASE_URL" \
  --slurpfile smoke control-plane-authenticated-smoke.json \
  '{result:"DEPLOYMENT_VERIFIED",commit:$commit,deploymentId:$deployment,previousCommit:$previous,backendUrl:$backendUrl,webUrl:$webUrl,flyway:["V15:JDBC","V20260702.1:SQL","V20260702.2:SQL","V20260702.3:SQL"],health:"UP",vercelControlPlane:"UP",vercelBff:"CONNECTED",authenticatedProductionSmoke:$smoke[0]}' \
  > production-release-evidence.json

RELEASE_COMPLETE=true
echo "SANAD production release verification: PASSED"
