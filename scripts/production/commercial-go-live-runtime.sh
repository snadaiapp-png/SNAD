#!/usr/bin/env bash
set -Eeuo pipefail

# ---- Capture previous live Render commit ----
curl --fail-with-body --silent --show-error --max-time 30 \
  -H "Authorization: Bearer $RENDER_API_KEY" \
  -H "Accept: application/json" \
  "https://api.render.com/v1/services/$RENDER_SERVICE_ID/deploys?limit=20" \
  > "$RUNNER_TEMP/render-deploys.json"

PREVIOUS_SHA="$(jq -r '
  [.[]? | (.deploy // .) | select((.status // "") == "live")][0].commit.id // empty
' "$RUNNER_TEMP/render-deploys.json")"
[[ "$PREVIOUS_SHA" =~ ^[0-9a-f]{40}$ ]] || {
  echo "::error::A valid previous live Render SHA is required for rollback."
  exit 1
}
PREVIOUS_RENDER_SHA="$PREVIOUS_SHA"
export PREVIOUS_RENDER_SHA
echo "PREVIOUS_RENDER_SHA=$PREVIOUS_RENDER_SHA" >> "$GITHUB_ENV"
rm -f "$RUNNER_TEMP/render-deploys.json"

# ---- Trigger exact Render release SHA ----
jq -n --arg commit "$RELEASE_SHA" \
  '{commitId:$commit,clearCache:"do_not_clear"}' \
  > "$RUNNER_TEMP/render-request.json"

curl --fail-with-body --silent --show-error --max-time 30 \
  -X POST \
  -H "Authorization: Bearer $RENDER_API_KEY" \
  -H "Accept: application/json" \
  -H "Content-Type: application/json" \
  --data @"$RUNNER_TEMP/render-request.json" \
  "https://api.render.com/v1/services/$RENDER_SERVICE_ID/deploys" \
  > "$RUNNER_TEMP/render-deployment-created.json"

DEPLOY_ID="$(jq -r '(.deploy // .).id // empty' "$RUNNER_TEMP/render-deployment-created.json")"
test -n "$DEPLOY_ID" || {
  echo "::error::Render did not return a deployment ID."
  exit 1
}
RENDER_DEPLOYMENT_ID="$DEPLOY_ID"
export RENDER_DEPLOYMENT_ID
echo "RENDER_DEPLOYMENT_ID=$RENDER_DEPLOYMENT_ID" >> "$GITHUB_ENV"
rm -f "$RUNNER_TEMP/render-request.json" "$RUNNER_TEMP/render-deployment-created.json"

# ---- Wait for exact Render deployment and verify SHA ----
test -n "$RENDER_DEPLOYMENT_ID"
for attempt in $(seq 1 180); do
  curl --fail-with-body --silent --show-error --max-time 30 \
    -H "Authorization: Bearer $RENDER_API_KEY" \
    -H "Accept: application/json" \
    "https://api.render.com/v1/services/$RENDER_SERVICE_ID/deploys/$RENDER_DEPLOYMENT_ID" \
    > "$RUNNER_TEMP/render-deployment-state.json"

  STATUS="$(jq -r '(.deploy // .).status // "pending"' "$RUNNER_TEMP/render-deployment-state.json")"
  case "$STATUS" in
    live) break ;;
    build_failed|update_failed|canceled|deactivated)
      echo "::error::Render deployment failed: $STATUS"
      exit 1
      ;;
  esac
  sleep 10
done

STATUS="$(jq -r '(.deploy // .).status // empty' "$RUNNER_TEMP/render-deployment-state.json")"
RENDER_SHA="$(jq -r '(.deploy // .).commit.id // empty' "$RUNNER_TEMP/render-deployment-state.json")"
[ "$STATUS" = "live" ] || exit 1
[ "$RENDER_SHA" = "$RELEASE_SHA" ] || exit 1
[[ "$PREVIOUS_RENDER_SHA" =~ ^[0-9a-f]{40}$ ]] || exit 1

jq \
  --arg releaseSha "$RELEASE_SHA" \
  --arg previousLiveSha "$PREVIOUS_RENDER_SHA" \
  '(.deploy // .) |
  {
    releaseSha:$releaseSha,
    previousLiveSha:$previousLiveSha,
    deploymentId:.id,
    commitSha:.commit.id,
    status:.status,
    createdAt:.createdAt,
    startedAt:.startedAt,
    finishedAt:.finishedAt,
    rollbackCapability:(
      if ($previousLiveSha|test("^[0-9a-f]{40}$"))
      then "VERIFIED" else "NOT_VERIFIED" end
    ),
    result:(
      if (.status == "live" and .commit.id == $releaseSha)
      then "PASS" else "FAIL" end
    )
  }' "$RUNNER_TEMP/render-deployment-state.json" \
  > evidence/render-deployment.json

echo "sha=$RENDER_SHA" >> "$GITHUB_OUTPUT"

# ---- Verify backend health and readiness ----
HEALTH_CODE=000
READINESS_CODE=000
for attempt in $(seq 1 72); do
  HEALTH_CODE="$(curl --silent --show-error --output "$RUNNER_TEMP/health.json" \
    --write-out '%{http_code}' --max-time 20 \
    "$PRODUCTION_BASE_URL/actuator/health" || true)"
  READINESS_CODE="$(curl --silent --show-error --output "$RUNNER_TEMP/readiness.json" \
    --write-out '%{http_code}' --max-time 20 \
    "$PRODUCTION_BASE_URL/actuator/health/readiness" || true)"

  if [ "$HEALTH_CODE" = "200" ] && [ "$READINESS_CODE" = "200" ] && \
     jq -e '.status == "UP"' "$RUNNER_TEMP/health.json" >/dev/null 2>&1 && \
     jq -e '.status == "UP"' "$RUNNER_TEMP/readiness.json" >/dev/null 2>&1; then
    break
  fi
  sleep 5
done

[ "$HEALTH_CODE" = "200" ] && jq -e '.status == "UP"' "$RUNNER_TEMP/health.json" >/dev/null
[ "$READINESS_CODE" = "200" ] && jq -e '.status == "UP"' "$RUNNER_TEMP/readiness.json" >/dev/null

jq -n \
  --arg releaseSha "$RELEASE_SHA" \
  --arg healthHttp "$HEALTH_CODE" \
  --arg readinessHttp "$READINESS_CODE" \
  --slurpfile health "$RUNNER_TEMP/health.json" \
  --slurpfile readiness "$RUNNER_TEMP/readiness.json" \
  '{
    releaseSha:$releaseSha,
    health:{httpStatus:$healthHttp,status:$health[0].status},
    readiness:{httpStatus:$readinessHttp,status:$readiness[0].status},
    result:(
      if (
        $healthHttp == "200" and $readinessHttp == "200" and
        $health[0].status == "UP" and $readiness[0].status == "UP"
      ) then "PASS" else "FAIL" end
    )
  }' > evidence/health-readiness-evidence.json

# ---- Verify Control Plane page and unauthenticated BFF boundary ----
CONTROL_PLANE_CODE="$(curl --silent --show-error --location \
  --output "$RUNNER_TEMP/control-plane.html" \
  --write-out '%{http_code}' --max-time 60 \
  "$WEB_PRODUCTION_BASE_URL/control-plane")"
[ "$CONTROL_PLANE_CODE" = "200" ] || exit 1

BFF_CODE="$(curl --silent --show-error \
  --output "$RUNNER_TEMP/bff-boundary.json" \
  --write-out '%{http_code}' --max-time 90 \
  "$WEB_PRODUCTION_BASE_URL/api/platform/api/v1/control-plane/dashboard" || true)"
case "$BFF_CODE" in
  401|403) ;;
  *) echo "::error::Unauthenticated BFF boundary returned HTTP $BFF_CODE."; exit 1 ;;
esac

jq -n \
  --arg releaseSha "$RELEASE_SHA" \
  --arg controlPlane "$CONTROL_PLANE_CODE" \
  --arg bff "$BFF_CODE" \
  '{
    releaseSha:$releaseSha,
    controlPlane:{
      httpStatus:$controlPlane,
      result:(if $controlPlane == "200" then "PASS" else "FAIL" end)
    },
    bffSecurityBoundary:{
      httpStatus:$bff,
      result:(if ($bff == "401" or $bff == "403") then "PASS" else "FAIL" end)
    },
    result:(
      if ($controlPlane == "200" and ($bff == "401" or $bff == "403"))
      then "PASS" else "FAIL" end
    )
  }' > evidence/control-plane-boundary-evidence.json

# ---- Run authenticated two-identity production smoke ----
DEPLOYED_COMMIT_SHA="$RELEASE_SHA"
CONTROL_PLANE_SMOKE_EVIDENCE_FILE="evidence/authenticated-smoke-evidence.json"
export DEPLOYED_COMMIT_SHA CONTROL_PLANE_SMOKE_EVIDENCE_FILE
bash scripts/production/verify-control-plane-authenticated-smoke.sh
jq -e --arg sha "$RELEASE_SHA" \
  '.result == "PASS" and .releaseSha == $sha' \
  evidence/authenticated-smoke-evidence.json >/dev/null

jq --arg releaseSha "$RELEASE_SHA" '{
  releaseSha:$releaseSha,
  schema:"sanad.tenant-isolation.production-smoke.v1",
  checks:{
    identityB:.checks.identityB,
    rbacDenial:.checks.rbacDenial,
    tenantIsolation:.checks.tenantIsolation
  },
  result:(
    if (
      .checks.identityB.result == "PASS" and
      .checks.rbacDenial.result == "PASS" and
      .checks.tenantIsolation.result == "PASS"
    ) then "PASS" else "FAIL" end
  )
}' evidence/authenticated-smoke-evidence.json \
  > evidence/tenant-isolation-evidence.json

# ---- Verify recovery email with a current delivered Resend message ----
REQUESTED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

FORGOT_PAYLOAD="$(jq -n --arg value "$CONTROL_PLANE_ADMIN_EMAIL" '{email:$value}')"
FORGOT_CODE="$(curl --silent --show-error \
  --output "$RUNNER_TEMP/forgot-password.json" \
  --write-out '%{http_code}' --max-time 30 \
  -X POST \
  -H 'Content-Type: application/json' \
  --data "$FORGOT_PAYLOAD" \
  "$PRODUCTION_BASE_URL/api/v1/auth/forgot-password")"
[ "$FORGOT_CODE" = "200" ] || exit 1

EMAIL_ID=""
EMAIL_CREATED_AT=""
EMAIL_EVENT=""
for attempt in $(seq 1 30); do
  curl --fail-with-body --silent --show-error --max-time 20 \
    -H "Authorization: Bearer $RESEND_API_KEY" \
    "https://api.resend.com/emails?limit=100" \
    > "$RUNNER_TEMP/resend-emails.json"

  EMAIL_ID="$(jq -r \
    --arg recipient "$CONTROL_PLANE_ADMIN_EMAIL" \
    --arg requestedAt "$REQUESTED_AT" '
    [
      .data[]?
      | select(
          (.created_at // "") >= $requestedAt and
          (
            (.to // []) as $recipients |
            (
              (($recipients | type) == "array" and ($recipients | index($recipient) != null)) or
              (($recipients | type) == "string" and $recipients == $recipient)
            )
          ) and
          ((.subject // "") | test("reset|password|إعادة|كلمة المرور"; "i"))
        )
    ]
    | sort_by(.created_at)
    | last
    | .id // empty
  ' "$RUNNER_TEMP/resend-emails.json")"

  if [ -n "$EMAIL_ID" ]; then
    curl --fail-with-body --silent --show-error --max-time 20 \
      -H "Authorization: Bearer $RESEND_API_KEY" \
      "https://api.resend.com/emails/$EMAIL_ID" \
      > "$RUNNER_TEMP/resend-email.json"
    EMAIL_CREATED_AT="$(jq -r '.created_at // empty' "$RUNNER_TEMP/resend-email.json")"
    EMAIL_EVENT="$(jq -r '.last_event // .status // empty' "$RUNNER_TEMP/resend-email.json")"
    if [ "$EMAIL_CREATED_AT" \> "$REQUESTED_AT" ] || [ "$EMAIL_CREATED_AT" = "$REQUESTED_AT" ]; then
      [ "$EMAIL_EVENT" = "delivered" ] && break
    fi
  fi
  sleep 5
done

test -n "$EMAIL_ID"
[ "$EMAIL_CREATED_AT" \> "$REQUESTED_AT" ] || [ "$EMAIL_CREATED_AT" = "$REQUESTED_AT" ]
[ "$EMAIL_EVENT" = "delivered" ]

jq -n \
  --arg releaseSha "$RELEASE_SHA" \
  --arg messageId "$EMAIL_ID" \
  --arg requestedAt "$REQUESTED_AT" \
  --arg createdAt "$EMAIL_CREATED_AT" \
  --arg deliveryStatus "$EMAIL_EVENT" \
  '{
    releaseSha:$releaseSha,
    messageId:$messageId,
    requestedAt:$requestedAt,
    createdAt:$createdAt,
    deliveryStatus:$deliveryStatus,
    result:(
      if (
        ($messageId|length) > 0 and
        $createdAt >= $requestedAt and
        $deliveryStatus == "delivered"
      ) then "PASS" else "FAIL" end
    )
  }' > evidence/email-delivery-evidence.json

echo "message_id=$EMAIL_ID" >> "$GITHUB_OUTPUT"

# ---- Ensure exact Vercel production deployment exists ----
TEAM_QUERY=""
[ -z "$VERCEL_TEAM_ID" ] || TEAM_QUERY="&teamId=$VERCEL_TEAM_ID"

curl --fail-with-body --silent --show-error --max-time 30 \
  -H "Authorization: Bearer $VERCEL_TOKEN" \
  "https://api.vercel.com/v6/deployments?projectId=$VERCEL_PROJECT_ID&target=production&limit=100${TEAM_QUERY}" \
  > "$RUNNER_TEMP/vercel-deployments.json"

MATCH_ID="$(jq -r --arg sha "$RELEASE_SHA" '
  [
    .deployments[]?
    | select(
        (.meta.githubCommitSha // "") == $sha and
        (.target // "") == "production" and
        ((.readyState // .state // "") == "READY")
      )
  ]
  | sort_by(.createdAt)
  | last
  | .uid // empty
' "$RUNNER_TEMP/vercel-deployments.json")"

if [ -z "$MATCH_ID" ]; then
  PROJECT_QUERY=""
  [ -z "$VERCEL_TEAM_ID" ] || PROJECT_QUERY="?teamId=$VERCEL_TEAM_ID"
  curl --fail-with-body --silent --show-error --max-time 30 \
    -H "Authorization: Bearer $VERCEL_TOKEN" \
    "https://api.vercel.com/v9/projects/$VERCEL_PROJECT_ID${PROJECT_QUERY}" \
    > "$RUNNER_TEMP/vercel-project.json"

  PROJECT_NAME="$(jq -r '.name // empty' "$RUNNER_TEMP/vercel-project.json")"
  test -n "$PROJECT_NAME"

  jq -n \
    --arg name "$PROJECT_NAME" \
    --arg project "$VERCEL_PROJECT_ID" \
    --arg sha "$RELEASE_SHA" \
    '{
      name:$name,
      project:$project,
      target:"production",
      gitSource:{
        type:"github",
        org:"snadaiapp-png",
        repo:"SNAD",
        ref:"main",
        sha:$sha
      },
      gitMetadata:{
        remoteUrl:"https://github.com/snadaiapp-png/SNAD",
        commitRef:"main",
        commitSha:$sha,
        dirty:"false",
        ci:"true",
        ciType:"github-actions"
      },
      meta:{githubCommitSha:$sha}
    }' > "$RUNNER_TEMP/vercel-create.json"

  CREATE_QUERY=""
  [ -z "$VERCEL_TEAM_ID" ] || CREATE_QUERY="?teamId=$VERCEL_TEAM_ID"
  curl --fail-with-body --silent --show-error --max-time 60 \
    -X POST \
    -H "Authorization: Bearer $VERCEL_TOKEN" \
    -H "Content-Type: application/json" \
    --data @"$RUNNER_TEMP/vercel-create.json" \
    "https://api.vercel.com/v13/deployments${CREATE_QUERY}" \
    > "$RUNNER_TEMP/vercel-created.json"

  MATCH_ID="$(jq -r '.id // .uid // empty' "$RUNNER_TEMP/vercel-created.json")"
  test -n "$MATCH_ID" || {
    echo "::error::Vercel did not return a deployment ID."
    exit 1
  }
fi

EXPECTED_VERCEL_DEPLOYMENT_ID="$MATCH_ID"
export EXPECTED_VERCEL_DEPLOYMENT_ID
echo "EXPECTED_VERCEL_DEPLOYMENT_ID=$EXPECTED_VERCEL_DEPLOYMENT_ID" >> "$GITHUB_ENV"

# ---- Verify actual Vercel production deployment metadata ----
TEAM_QUERY=""
[ -z "$VERCEL_TEAM_ID" ] || TEAM_QUERY="&teamId=$VERCEL_TEAM_ID"

MATCH=""
for attempt in $(seq 1 180); do
  curl --fail-with-body --silent --show-error --max-time 30 \
    -H "Authorization: Bearer $VERCEL_TOKEN" \
    "https://api.vercel.com/v6/deployments?projectId=$VERCEL_PROJECT_ID&target=production&limit=100${TEAM_QUERY}" \
    > "$RUNNER_TEMP/vercel-deployments.json"

  MATCH="$(jq -c \
    --arg id "$EXPECTED_VERCEL_DEPLOYMENT_ID" \
    --arg sha "$RELEASE_SHA" '
    [
      .deployments[]?
      | select(
          (.uid // "") == $id and
          (.meta.githubCommitSha // "") == $sha and
          (.target // "") == "production"
        )
    ]
    | last // empty
  ' "$RUNNER_TEMP/vercel-deployments.json")"

  if [ -n "$MATCH" ] && [ "$MATCH" != "null" ]; then
    STATE="$(jq -r '.readyState // .state // empty' <<< "$MATCH")"
    case "$STATE" in
      READY) break ;;
      ERROR|CANCELED) echo "::error::Vercel deployment failed: $STATE"; exit 1 ;;
    esac
  fi
  sleep 10
done

[ -n "$MATCH" ] && [ "$MATCH" != "null" ] || exit 1
VERCEL_ID="$(jq -r '.uid // empty' <<< "$MATCH")"
VERCEL_SHA="$(jq -r '.meta.githubCommitSha // empty' <<< "$MATCH")"
VERCEL_STATE="$(jq -r '.readyState // .state // empty' <<< "$MATCH")"
[ "$VERCEL_SHA" = "$RELEASE_SHA" ]
[ "$VERCEL_STATE" = "READY" ]

jq -n \
  --arg releaseSha "$RELEASE_SHA" \
  --argjson deployment "$MATCH" \
  '{
    releaseSha:$releaseSha,
    deploymentId:$deployment.uid,
    githubCommitSha:$deployment.meta.githubCommitSha,
    target:$deployment.target,
    readyState:($deployment.readyState // $deployment.state),
    createdAt:$deployment.createdAt,
    deploymentUrl:$deployment.url,
    result:(
      if (
        $deployment.meta.githubCommitSha == $releaseSha and
        $deployment.target == "production" and
        (($deployment.readyState // $deployment.state) == "READY")
      ) then "PASS" else "FAIL" end
    )
  }' > evidence/vercel-deployment.json

echo "id=$VERCEL_ID" >> "$GITHUB_OUTPUT"
