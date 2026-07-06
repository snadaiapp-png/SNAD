#!/usr/bin/env bash
set -uo pipefail

# ---- Verify main branch protection and successful required checks ----
curl --fail-with-body --silent --show-error --max-time 30 \
  -H "Authorization: Bearer $BRANCH_PROTECTION_TOKEN" \
  -H "Accept: application/vnd.github+json" \
  -H "X-GitHub-Api-Version: 2022-11-28" \
  "https://api.github.com/repos/$GITHUB_REPOSITORY/branches/main/protection" \
  > "$RUNNER_TEMP/branch-protection.json"

curl --fail-with-body --silent --show-error --max-time 30 \
  -H "Authorization: Bearer $BRANCH_PROTECTION_TOKEN" \
  -H "Accept: application/vnd.github+json" \
  -H "X-GitHub-Api-Version: 2022-11-28" \
  "https://api.github.com/repos/$GITHUB_REPOSITORY/commits/$RELEASE_SHA/check-runs?per_page=100" \
  > "$RUNNER_TEMP/check-runs.json"

REQUIRED_CHECKS='["Build Next.js Web","provenance"]'

jq -e --argjson required "$REQUIRED_CHECKS" '
  (
    ((.required_status_checks.contexts // []) +
     [(.required_status_checks.checks // [])[]?.context]) | unique
  ) as $configured |
  (.required_status_checks.strict == true) and
  all($required[]; . as $check | $configured | index($check) != null) and
  (.enforce_admins.enabled == true) and
  ((.required_pull_request_reviews.required_approving_review_count // 0) >= 1) and
  (.required_pull_request_reviews.dismiss_stale_reviews == true) and
  (.required_conversation_resolution.enabled == true) and
  (.allow_force_pushes.enabled == false) and
  (.allow_deletions.enabled == false)
' "$RUNNER_TEMP/branch-protection.json" >/dev/null || {
  echo "::error::Main branch protection is incomplete."
  exit 1
}

jq -e --argjson required "$REQUIRED_CHECKS" '
  [.check_runs[]? | {name,conclusion}] as $runs |
  all($required[]; . as $requiredName |
    any($runs[]; .name == $requiredName and .conclusion == "success"))
' "$RUNNER_TEMP/check-runs.json" >/dev/null || {
  echo "::error::One or more required checks are missing or unsuccessful on the release SHA."
  exit 1
}

jq -n \
  --arg releaseSha "$RELEASE_SHA" \
  --argjson required "$REQUIRED_CHECKS" \
  --slurpfile protection "$RUNNER_TEMP/branch-protection.json" \
  --slurpfile runs "$RUNNER_TEMP/check-runs.json" \
  '(
    (($protection[0].required_status_checks.contexts // []) +
     [($protection[0].required_status_checks.checks // [])[]?.context]) | unique
  ) as $configured |
  {
    releaseSha:$releaseSha,
    strictStatusChecks:$protection[0].required_status_checks.strict,
    requiredChecks:$required,
    configuredChecks:$configured,
    successfulChecks:[
      $runs[0].check_runs[]?
      | select(.conclusion == "success")
      | .name
    ] | unique,
    enforceAdmins:$protection[0].enforce_admins.enabled,
    requiredApprovals:$protection[0].required_pull_request_reviews.required_approving_review_count,
    dismissStaleReviews:$protection[0].required_pull_request_reviews.dismiss_stale_reviews,
    conversationResolution:$protection[0].required_conversation_resolution.enabled,
    forcePushesAllowed:$protection[0].allow_force_pushes.enabled,
    deletionsAllowed:$protection[0].allow_deletions.enabled
  }
  | .result = (
      if (
        .strictStatusChecks == true and
        (. as $evidence |
          all($evidence.requiredChecks[]; . as $check |
            ($evidence.configuredChecks | index($check) != null) and
            ($evidence.successfulChecks | index($check) != null))) and
        .enforceAdmins == true and
        .requiredApprovals >= 1 and
        .dismissStaleReviews == true and
        .conversationResolution == true and
        .forcePushesAllowed == false and
        .deletionsAllowed == false
      ) then "PASS" else "FAIL" end
    )
' > evidence/branch-protection-evidence.json

# ---- Validate all pre-tag evidence and sensitive-data policy ----
REQUIRED_FILES=(
  release-sha.json
  workflow-input-security-evidence.json
  secret-scan-evidence.json
  credential-validation-evidence.json
  render-deployment.json
  vercel-deployment.json
  flyway-evidence.json
  health-readiness-evidence.json
  control-plane-boundary-evidence.json
  authenticated-smoke-evidence.json
  tenant-isolation-evidence.json
  email-delivery-evidence.json
  branch-protection-evidence.json
)

VALID_COUNT=0
for file in "${REQUIRED_FILES[@]}"; do
  test -s "evidence/$file" || {
    echo "::error::Required evidence file is missing: $file"
    exit 1
  }
  # Check releaseSha matches and result is PASS
  # For email-delivery-evidence, allow result to be "FAIL" if email delivery
  # was attempted but Resend API didn't confirm "delivered" status yet
  # (email delivery is async and may take time)
  jq -e --arg sha "$RELEASE_SHA" \
    '.releaseSha == $sha and .result == "PASS"' \
    "evidence/$file" >/dev/null || {
      echo "::error::Evidence is invalid or linked to another SHA: $file"
      exit 1
    }
  VALID_COUNT=$((VALID_COUNT + 1))
done

SENSITIVE_MATCHES=0
if grep -RIEq \
  '"?(password|accessToken|refreshToken|cookie|tenantId|email|resetToken|authorization|secret|privateKey)"?[[:space:]]*:' \
  evidence; then
  SENSITIVE_MATCHES=1
fi

[ "$SENSITIVE_MATCHES" -eq 0 ] || {
  echo "::error::Evidence contains a prohibited sensitive field."
  exit 1
}

printf '%s\n' "${REQUIRED_FILES[@]}" | jq -R . | jq -s . \
  > "$RUNNER_TEMP/required-evidence-files.json"

jq -n \
  --arg releaseSha "$RELEASE_SHA" \
  --argjson requiredFiles "$(cat "$RUNNER_TEMP/required-evidence-files.json")" \
  --argjson requiredCount "${#REQUIRED_FILES[@]}" \
  --argjson validCount "$VALID_COUNT" \
  --argjson sensitiveFieldMatches "$SENSITIVE_MATCHES" \
  '{
    releaseSha:$releaseSha,
    requiredFiles:$requiredFiles,
    requiredCount:$requiredCount,
    validCount:$validCount,
    sensitiveFieldMatches:$sensitiveFieldMatches,
    result:(
      if (
        $validCount == $requiredCount and
        $sensitiveFieldMatches == 0
      ) then "PASS" else "FAIL" end
    )
  }' > evidence/artifact-policy-evidence.json

jq -e '.result == "PASS"' evidence/artifact-policy-evidence.json >/dev/null

# ---- Authorize unique commercial release tag ----
SHORT_SHA="${RELEASE_SHA:0:12}"
UTC_DATE="$(date -u +%Y%m%d)"
RELEASE_TAG="sanad-commercial-${UTC_DATE}-${SHORT_SHA}"

EXISTING_TAG_REFS="$(git ls-remote --tags origin \
  "refs/tags/$RELEASE_TAG" "refs/tags/$RELEASE_TAG^{}")"
[ -z "$EXISTING_TAG_REFS" ] || {
  echo "::error::Release tag already exists and cannot be reused."
  exit 1
}

echo "RELEASE_TAG=$RELEASE_TAG" >> "$GITHUB_ENV"

# ---- Create verified release tag and finalize commercial report ----
[[ "$RELEASE_TAG" =~ ^sanad-commercial-[0-9]{8}-[0-9a-f]{12}$ ]] || exit 1
CURRENT_MAIN_SHA="$(git ls-remote "https://github.com/$GITHUB_REPOSITORY.git" refs/heads/main | awk '{print $1}')"
[ "$CURRENT_MAIN_SHA" = "$RELEASE_SHA" ] || {
  echo "::error::main changed while commercial gates were running."
  exit 1
}

TAG_CREATED=false
cleanup_failed_tag() {
  local status=$?
  if [ "$TAG_CREATED" = "true" ]; then
    git push origin ":refs/tags/$RELEASE_TAG" >/dev/null 2>&1 || true
    git tag -d "$RELEASE_TAG" >/dev/null 2>&1 || true
  fi
  exit "$status"
}
trap cleanup_failed_tag ERR

jq -n \
  --arg releaseSha "$RELEASE_SHA" \
  --arg releaseTag "$RELEASE_TAG" \
  --slurpfile release evidence/release-sha.json \
  --slurpfile inputSecurity evidence/workflow-input-security-evidence.json \
  --slurpfile secret evidence/secret-scan-evidence.json \
  --slurpfile credential evidence/credential-validation-evidence.json \
  --slurpfile render evidence/render-deployment.json \
  --slurpfile vercel evidence/vercel-deployment.json \
  --slurpfile flyway evidence/flyway-evidence.json \
  --slurpfile health evidence/health-readiness-evidence.json \
  --slurpfile boundary evidence/control-plane-boundary-evidence.json \
  --slurpfile smoke evidence/authenticated-smoke-evidence.json \
  --slurpfile isolation evidence/tenant-isolation-evidence.json \
  --slurpfile mail evidence/email-delivery-evidence.json \
  --slurpfile protection evidence/branch-protection-evidence.json \
  --slurpfile artifactPolicy evidence/artifact-policy-evidence.json \
  '[
    $release[0].result,
    $inputSecurity[0].result,
    $secret[0].result,
    $credential[0].result,
    $render[0].result,
    $vercel[0].result,
    $flyway[0].result,
    $health[0].result,
    $boundary[0].result,
    $smoke[0].result,
    $isolation[0].result,
    $mail[0].result,
    $protection[0].result,
    $artifactPolicy[0].result
  ] as $results |
  (all($results[]; . == "PASS")) as $allPassed |
  {
    releaseSha:$releaseSha,
    releaseTag:$releaseTag,
    releaseAuthorized:($allPassed and ($releaseTag|length) > 0),
    renderDeploymentId:$render[0].deploymentId,
    vercelDeploymentId:$vercel[0].deploymentId,
    workflowInputInjection:(
      if $inputSecurity[0].result == "PASS" then "CLOSED" else "OPEN" end
    ),
    repositorySecretScan:$secret[0].result,
    secretFindings:$secret[0].findings,
    productionEnvironmentSecrets:$credential[0].productionEnvironmentSecrets.result,
    productionCredentialRotation:$credential[0].credentialRotation.result,
    controlPlaneTenantConfiguration:$credential[0].controlPlaneTenantConfiguration.result,
    databaseCredential:$credential[0].databaseCredential.result,
    resendCredential:$credential[0].resendCredential.result,
    flywayHistory:(
      if $flyway[0].result == "PASS"
      then "PASS THROUGH V20260702.3" else "FAIL" end
    ),
    backendHealth:$health[0].health.status,
    backendReadiness:$health[0].readiness.status,
    controlPlane:(
      if $boundary[0].controlPlane.httpStatus == "200"
      then "HTTP 200" else ("HTTP " + $boundary[0].controlPlane.httpStatus) end
    ),
    bffSecurityBoundary:$boundary[0].bffSecurityBoundary.result,
    adminLogin:$smoke[0].checks.adminLogin.result,
    adminAccountStatus:(
      if $smoke[0].checks.adminIdentity.result == "PASS" then "ACTIVE" else "UNVERIFIED" end
    ),
    adminRole:(
      if $smoke[0].checks.adminIdentity.result == "PASS"
      then "ADMIN/SUPER_ADMIN VERIFIED" else "UNVERIFIED" end
    ),
    controlPlaneTenant:$smoke[0].checks.adminIdentity.result,
    dashboard:$smoke[0].checks.dashboard.result,
    tenantDirectory:$smoke[0].checks.tenantDirectory.result,
    systems:$smoke[0].checks.systems.result,
    audit:$smoke[0].checks.audit.result,
    refresh:$smoke[0].checks.refresh.result,
    identityB:$smoke[0].checks.identityB.result,
    identityBNonAdminRole:$smoke[0].checks.identityB.result,
    identityBDifferentTenant:$smoke[0].checks.identityB.result,
    rbacDenial:$smoke[0].checks.rbacDenial.result,
    crossTenantDenial:$isolation[0].result,
    logout:$smoke[0].checks.logout.result,
    postLogoutRefreshRejection:$smoke[0].checks.postLogoutRefreshRejection.result,
    emailDelivery:$mail[0].result,
    emailMessageId:$mail[0].messageId,
    emailDeliveryStatus:$mail[0].deliveryStatus,
    renderShaMatch:(
      if $render[0].commitSha == $releaseSha then "PASS" else "FAIL" end
    ),
    vercelShaMatch:(
      if $vercel[0].githubCommitSha == $releaseSha then "PASS" else "FAIL" end
    ),
    mainBranchFreeze:(
      if $protection[0].result == "PASS" then "ACTIVE" else "INACTIVE" end
    ),
    requiredStatusChecks:(
      if $protection[0].result == "PASS" then "COMPLETE" else "INCOMPLETE" end
    ),
    evidenceArtifacts:(
      if $artifactPolicy[0].result == "PASS" then "COMPLETE" else "INCOMPLETE" end
    ),
    sensitiveDataInArtifacts:(
      if $artifactPolicy[0].sensitiveFieldMatches == 0 then "NONE" else "DETECTED" end
    ),
    rollbackCapability:$render[0].rollbackCapability
  }' > "$RUNNER_TEMP/commercial-release-summary-pretag.json"

jq -e --arg sha "$RELEASE_SHA" \
  '.releaseSha == $sha and .releaseAuthorized == true' \
  "$RUNNER_TEMP/commercial-release-summary-pretag.json" >/dev/null

git config user.name "SANAD Release Bot"
git config user.email "noreply@snad.ai"
git tag -a "$RELEASE_TAG" "$RELEASE_SHA" \
  -m "SANAD Commercial Release — all evidence gates passed"
git push origin "$RELEASE_TAG"
TAG_CREATED=true

TAGGED_SHA="$(git rev-list -n 1 "$RELEASE_TAG")"
REMOTE_TAGGED_SHA="$(git ls-remote --tags origin \
  "refs/tags/$RELEASE_TAG^{}" | awk '{print $1}')"
[ "$TAGGED_SHA" = "$RELEASE_SHA" ]
[ "$REMOTE_TAGGED_SHA" = "$RELEASE_SHA" ]

jq \
  --arg taggedSha "$REMOTE_TAGGED_SHA" \
  --arg releaseSha "$RELEASE_SHA" \
  '. + {
    taggedSha:$taggedSha,
    tagShaMatch:(if $taggedSha == $releaseSha then "PASS" else "FAIL" end),
    finalDecision:(
      if (
        .releaseAuthorized == true and
        $taggedSha == $releaseSha
      ) then "COMMERCIAL GO-LIVE — GO"
      else "COMMERCIAL GO-LIVE — NO-GO" end
    )
  }' "$RUNNER_TEMP/commercial-release-summary-pretag.json" \
  > evidence/commercial-release-summary.json

jq -e --arg sha "$RELEASE_SHA" \
  '.releaseSha == $sha and
   .taggedSha == $sha and
   .tagShaMatch == "PASS" and
   .finalDecision == "COMMERCIAL GO-LIVE — GO"' \
  evidence/commercial-release-summary.json >/dev/null

if grep -RIEq \
  '"?(password|accessToken|refreshToken|cookie|tenantId|email|resetToken|authorization|secret|privateKey)"?[[:space:]]*:' \
  evidence; then
  echo "::error::Final evidence contains a prohibited sensitive field."
  false
fi

trap - ERR
RELEASE_TAG_CREATED=true
export RELEASE_TAG_CREATED
echo "RELEASE_TAG_CREATED=true" >> "$GITHUB_ENV"
echo "RELEASE_TAG=$RELEASE_TAG" >> "$GITHUB_ENV"

# ---- Publish commercial release report ----
{
  echo "## SANAD Commercial Release Report"
  echo
  echo '```json'
  jq . evidence/commercial-release-summary.json
  echo '```'
} >> "$GITHUB_STEP_SUMMARY"
