#!/usr/bin/env bash
set -euo pipefail

phase="${1:?phase is required}"

mask_required() {
  local value
  for value in "$@"; do
    test -n "$value" || { echo "::error::A required Production secret is missing"; exit 1; }
    echo "::add-mask::$value"
  done
}

resolve_tenant() {
  local email="$1"
  local fingerprint="$2"
  local candidates=""
  local candidate=""
  local matches=0
  local resolved=""

  if ! candidates="$(
    psql "$PSQL_URL" \
      -U "$PROD_DB_USER" \
      -X \
      -v ON_ERROR_STOP=1 \
      -At \
      -v email="$email" \
      -f - <<'SQL'
SELECT DISTINCT tenant_id
FROM users
WHERE lower(email) = lower(:'email')
  AND status = 'ACTIVE'
ORDER BY tenant_id;
SQL
  )"; then
    echo "::error::Tenant resolution query failed" >&2
    return 1
  fi

  while IFS= read -r candidate; do
    test -n "$candidate" || continue

    echo "::add-mask::$candidate" >&2

    if test "$(printf '%s' "$candidate" | sha256sum | awk '{print $1}')" = "$fingerprint"; then
      resolved="$candidate"
      matches=$((matches + 1))
    fi
  done <<< "$candidates"

  if test "$matches" != "1"; then
    echo "::error::Expected exactly one fingerprint-matching active tenant" >&2
    return 1
  fi

  printf '%s' "$resolved"
}

case "$phase" in
  preflight)
    mask_required \
      "$PROD_JDBC_URL" "$PROD_DB_USER" "$PROD_DB_PASSWORD" \
      "$VERCEL_TOKEN" "$VERCEL_TEAM_ID" "$RENDER_API_KEY" "$RENDER_SERVICE_ID" \
      "$CRM_TENANT_A_EMAIL" "$CRM_TENANT_A_PASSWORD" \
      "$CRM_TENANT_B_EMAIL" "$CRM_TENANT_B_PASSWORD"
    [[ "$PROD_JDBC_URL" == jdbc:postgresql://* ]]
    [[ "$RENDER_SERVICE_ID" == srv-* ]]
    test "${CRM_TENANT_A_EMAIL,,}" != "${CRM_TENANT_B_EMAIL,,}"

    RELEASE_SHA="$(git rev-parse HEAD)"
    REMOTE_MAIN_SHA="$(git ls-remote "https://github.com/${GITHUB_REPOSITORY}.git" refs/heads/main | awk '{print $1}')"
    test "$RELEASE_SHA" = "$REMOTE_MAIN_SHA" || { echo "::error::Checkout is not current main"; exit 1; }
    STARTED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    RUN_URL="${GITHUB_SERVER_URL}/${GITHUB_REPOSITORY}/actions/runs/${GITHUB_RUN_ID}"

    curl --fail-with-body --silent --show-error --max-time 45 \
      -H "Authorization: Bearer ${GH_TOKEN}" -H 'Accept: application/vnd.github+json' \
      -H 'X-GitHub-Api-Version: 2022-11-28' \
      "https://api.github.com/repos/${GITHUB_REPOSITORY}/actions/workflows/crm-g1-production-closure.yml/runs?event=workflow_dispatch&branch=main&per_page=20" \
      > "$RUNNER_TEMP/g1-runs.json"
    jq --arg sha "$RELEASE_SHA" \
      '[.workflow_runs[] | select(.head_sha == $sha and .status == "completed" and .conclusion == "success")] | sort_by(.created_at) | reverse | first // empty' \
      "$RUNNER_TEMP/g1-runs.json" > "$EVIDENCE_DIR/upstream-g1-run.json"
    G1_RUN_ID="$(jq -r '.id // empty' "$EVIDENCE_DIR/upstream-g1-run.json")"
    test -n "$G1_RUN_ID" || { echo "::error::No exact-SHA completed/success CRM-G1 run exists"; exit 1; }

    HOST="${WEB_BASE_URL#https://}"
    VERCEL_READY=false
    for attempt in $(seq 1 120); do
      curl --fail-with-body --silent --show-error --max-time 45 \
        -H "Authorization: Bearer ${VERCEL_TOKEN}" \
        "https://api.vercel.com/v13/deployments/${HOST}?teamId=${VERCEL_TEAM_ID}" > "$RUNNER_TEMP/vercel.json"
      if [ "$(jq -r '.meta.githubCommitSha // empty' "$RUNNER_TEMP/vercel.json")" = "$RELEASE_SHA" ] \
        && [ "$(jq -r '.readyState // .state // empty' "$RUNNER_TEMP/vercel.json")" = "READY" ] \
        && [ "$(jq -r '.target // empty' "$RUNNER_TEMP/vercel.json")" = "production" ]; then
        VERCEL_READY=true; break
      fi
      sleep 10
    done
    test "$VERCEL_READY" = "true" || { echo "::error::Vercel did not reach exact current main"; exit 1; }
    jq '{deployment_id:(.id // .uid),url:.url,state:(.readyState // .state),target:.target,git_sha:.meta.githubCommitSha}' \
      "$RUNNER_TEMP/vercel.json" > "$EVIDENCE_DIR/vercel-deployment.json"
    VERCEL_DEPLOYMENT_ID="$(jq -r '.deployment_id' "$EVIDENCE_DIR/vercel-deployment.json")"

    curl --fail-with-body --silent --show-error --max-time 45 \
      -H "Authorization: Bearer ${RENDER_API_KEY}" -H 'Accept: application/json' \
      "https://api.render.com/v1/services/${RENDER_SERVICE_ID}" > "$RUNNER_TEMP/render-service.json"
    RENDER_OWNER_ID="$(jq -r '(.service // .).ownerId // (.service // .).owner.id // empty' "$RUNNER_TEMP/render-service.json")"
    test -n "$RENDER_OWNER_ID"
    curl --fail-with-body --silent --show-error --max-time 45 \
      -H "Authorization: Bearer ${RENDER_API_KEY}" -H 'Accept: application/json' \
      "https://api.render.com/v1/services/${RENDER_SERVICE_ID}/deploys?limit=50" > "$RUNNER_TEMP/render-deploys.json"
    jq --arg expected "snadaiapp-png/snad-backend:${RELEASE_SHA}" \
      '[if type=="array" then .[] else (.deploys // .items // [])[] end | (.deploy // .) as $d |
        (($d.image.ref // $d.imageRef // $d.image.url // $d.imageUrl // "") |
         sub("^docker://";"") | sub("^https?://";"") | sub("^ghcr.io/";"")) as $image |
        select($d.status=="live" and $image==$expected) | $d] |
        sort_by(.finishedAt // .updatedAt // .createdAt) | reverse | first // empty' \
      "$RUNNER_TEMP/render-deploys.json" > "$EVIDENCE_DIR/render-deployment.json"
    RENDER_DEPLOYMENT_ID="$(jq -r '.id // empty' "$EVIDENCE_DIR/render-deployment.json")"
    test -n "$RENDER_DEPLOYMENT_ID" || { echo "::error::Render is not serving exact current main image"; exit 1; }
    RENDER_IMAGE_DIGEST="$(jq -r '.image.digest // empty' "$EVIDENCE_DIR/render-deployment.json")"

    for path in /actuator/health /actuator/health/liveness /actuator/health/readiness; do
      CODE="$(curl --silent --show-error --max-time 45 -o "$RUNNER_TEMP/health.json" -w '%{http_code}' "${RENDER_BASE_URL}${path}")"
      test "$CODE" = "200" || { echo "::error::Render health returned ${CODE}: ${path}"; exit 1; }
      jq -e '.status=="UP"' "$RUNNER_TEMP/health.json" >/dev/null
    done

    PSQL_URL="${PROD_JDBC_URL#jdbc:}"
    export PGPASSWORD="$PROD_DB_PASSWORD"
    export PGOPTIONS='-c default_transaction_read_only=on'
    psql "$PSQL_URL" -U "$PROD_DB_USER" -X -v ON_ERROR_STOP=1 -Atc \
      "SELECT version || '|' || type || '|' || success || '|' || installed_on
         FROM flyway_schema_history
        WHERE version IN ('20260717.100','20260717.101','20260718.1','20260721.1','20260721.2')
        ORDER BY installed_rank" > "$EVIDENCE_DIR/flyway-crm007.txt"
    test "$(wc -l < "$EVIDENCE_DIR/flyway-crm007.txt" | tr -d ' ')" = "5"
    test "$(grep -Ec '^(20260717\.100|20260717\.101|20260718\.1|20260721\.1|20260721\.2)\|SQL\|t(rue)?\|' "$EVIDENCE_DIR/flyway-crm007.txt")" = "5"
    test "$(psql "$PSQL_URL" -U "$PROD_DB_USER" -X -v ON_ERROR_STOP=1 -Atc "SELECT count(*) FROM flyway_schema_history WHERE success=false")" = "0"
    psql "$PSQL_URL" -U "$PROD_DB_USER" -X -v ON_ERROR_STOP=1 -P pager=off \
      -f scripts/crm/verify-g1-tenant-isolation.sql > "$EVIDENCE_DIR/crm-g1-isolation.txt"

    COUNTS="$(psql "$PSQL_URL" -U "$PROD_DB_USER" -X -v ON_ERROR_STOP=1 -Atc \
      "SELECT
         (SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name IN ('crm_party_addresses','crm_party_address_history','crm_communication_policies','crm_communication_methods','crm_communication_method_history')) || '|' ||
         (SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name IN ('crm_tasks','crm_notes','crm_assignments','crm_transfers','crm_audit_logs','crm_reports','crm_phone_numbers','crm_contact_lookup_index')) || '|' ||
         (SELECT count(*) FROM pg_indexes WHERE schemaname='public' AND tablename IN ('crm_tasks','crm_notes','crm_assignments','crm_transfers','crm_audit_logs','crm_reports','crm_phone_numbers','crm_contact_lookup_index') AND indexname LIKE 'idx_crm_%') || '|' ||
         (SELECT count(*) FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid WHERE c.contype='f' AND c.confrelid='tenants'::regclass AND t.relname IN ('crm_tasks','crm_notes','crm_assignments','crm_transfers','crm_audit_logs','crm_reports','crm_phone_numbers','crm_contact_lookup_index')) || '|' ||
         (SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='crm_contacts' AND column_name IN ('legal_name','preferred_name','middle_name','pronouns','source')) || '|' ||
         (SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name IN ('crm_contact_relationship_roles','crm_contact_account_relationships','crm_contact_relationship_history','crm_contact_ownership_history'))")"
    test "$COUNTS" = "5|8|26|8|5|4"
    jq -n --arg counts "$COUNTS" '{result:"PASS",counts:$counts,expected:"5|8|26|8|5|4"}' > "$EVIDENCE_DIR/schema-postconditions.json"

    TENANT_A_ID="$(resolve_tenant "$CRM_TENANT_A_EMAIL" "$CRM_TENANT_A_FINGERPRINT")"
    TENANT_B_ID="$(resolve_tenant "$CRM_TENANT_B_EMAIL" "$CRM_TENANT_B_FINGERPRINT")"
    test "$TENANT_A_ID" != "$TENANT_B_ID"
    echo "::add-mask::$TENANT_A_ID"; echo "::add-mask::$TENANT_B_ID"
    {
      echo "CRM_TENANT_A_ID=$TENANT_A_ID"; echo "CRM_TENANT_B_ID=$TENANT_B_ID"
      echo "RENDER_OWNER_ID=$RENDER_OWNER_ID"; echo "VERCEL_DEPLOYMENT_ID=$VERCEL_DEPLOYMENT_ID"
      echo "RENDER_DEPLOYMENT_ID=$RENDER_DEPLOYMENT_ID"; echo "RENDER_IMAGE_DIGEST=$RENDER_IMAGE_DIGEST"
      echo "CRM007_STARTED_AT=$STARTED_AT"; echo "CRM007_RUN_URL=$RUN_URL"
      echo "CRM_TESTED_SHA=$RELEASE_SHA"; echo "UPSTREAM_G1_RUN_ID=$G1_RUN_ID"
      echo "PLAYWRIGHT_BASE_URL=$WEB_BASE_URL"; echo "CRM007_EVIDENCE_FILE=$EVIDENCE_DIR/crm007-production-smoke.json"
    } >> "$GITHUB_ENV"
    {
      echo "release_sha=$RELEASE_SHA"; echo "vercel_deployment_id=$VERCEL_DEPLOYMENT_ID"
      echo "render_deployment_id=$RENDER_DEPLOYMENT_ID"; echo "render_image_digest=$RENDER_IMAGE_DIGEST"; echo "run_url=$RUN_URL"
    } >> "$GITHUB_OUTPUT"
    jq -n --arg release "$RELEASE_SHA" --arg started "$STARTED_AT" --arg run "$RUN_URL" --arg upstream "$G1_RUN_ID" \
      '{schema:"snad.crm007.production-closure.v3",result:"IN_PROGRESS",release_sha:$release,started_at_utc:$started,workflow_run:$run,upstream_crm_g1_run:$upstream,database_mode:"READ_ONLY",migration_action:"NONE"}' \
      > "$EVIDENCE_DIR/run-context.json"
    ;;

  collect)
    acceptance="${2:-failure}"
    LIFECYCLE="FAIL"
    if [ "$acceptance" = "success" ] && [ -f "$CRM007_EVIDENCE_FILE" ]; then LIFECYCLE="PASS"; fi
    if [ -d apps/web/crm007-production-report ]; then cp -R apps/web/crm007-production-report "$EVIDENCE_DIR/"; fi
    if [ -d apps/web/test-results ]; then cp -R apps/web/test-results "$EVIDENCE_DIR/playwright-test-results"; fi
    END_TIME="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    curl --get --fail-with-body --silent --show-error --max-time 45 \
      -H "Authorization: Bearer ${RENDER_API_KEY}" -H 'Accept: application/json' \
      --data-urlencode "ownerId=${RENDER_OWNER_ID}" --data-urlencode "resource=${RENDER_SERVICE_ID}" \
      --data-urlencode "startTime=${CRM007_STARTED_AT}" --data-urlencode "endTime=${END_TIME}" \
      --data-urlencode "type=request" --data-urlencode "statusCode=500" --data-urlencode "limit=100" \
      https://api.render.com/v1/logs > "$EVIDENCE_DIR/render-500-logs.json"
    CRM_500_COUNT="$(jq '[if type=="array" then .[] else (.logs // .items // [])[] end | tostring | select(test("/api/v(1|2)/crm"))] | length' "$EVIDENCE_DIR/render-500-logs.json")"
    # Capture application logs (type=app) for exception class, SQLSTATE, and redacted stack traces
    curl --get --fail-with-body --silent --show-error --max-time 45 \
      -H "Authorization: Bearer ${RENDER_API_KEY}" -H 'Accept: application/json' \
      --data-urlencode "ownerId=${RENDER_OWNER_ID}" --data-urlencode "resource=${RENDER_SERVICE_ID}" \
      --data-urlencode "startTime=${CRM007_STARTED_AT}" --data-urlencode "endTime=${END_TIME}" \
      --data-urlencode "type=app" --data-urlencode "level=error" --data-urlencode "limit=200" \
      https://api.render.com/v1/logs > "$EVIDENCE_DIR/render-app-error-logs.json"
    # Extract exception details from application error logs
    python3 -c "
import json, sys, re
from pathlib import Path
evidence = Path(sys.argv[1])
raw = (evidence / 'render-app-error-logs.json').read_text(encoding='utf-8', errors='replace')
data = json.loads(raw)
entries = data if isinstance(data, list) else data.get('logs') or data.get('items') or []
crm_entries = [e for e in entries if 'crm' in json.dumps(e, ensure_ascii=False).lower()]
exceptions = []
for e in crm_entries:
    msg = e.get('message', '')
    exc_match = re.search(r'([\w.]+Exception)', msg)
    sqlstate_match = re.search(r'SQLSTATE[:\s]+([\w]+)', msg) or re.search(r'PSQLException.*?ERROR:\s+([^\n]+)', msg)
    frames = re.findall(r'at\s+(com\.sanad\.platform\.[^\s(]+)', msg)
    exceptions.append({
        'exception_class': exc_match.group(1) if exc_match else None,
        'cause': sqlstate_match.group(1) if sqlstate_match else None,
        'application_frames': frames[:5],
        'timestamp': e.get('timestamp'),
        'log_id': e.get('id')
    })
(evidence / 'crm-500-exception-evidence.json').write_text(
    json.dumps({'count': len(exceptions), 'exceptions': exceptions}, ensure_ascii=False, indent=2),
    encoding='utf-8')
print(f'Application error entries: {len(crm_entries)}, Exception details: {len(exceptions)}')
" "$EVIDENCE_DIR"
    # Fail closed: if CRM 500s exist but no application log evidence was captured, fail
    if [ "$CRM_500_COUNT" -gt 0 ]; then
      APP_LOG_SIZE="$(wc -c < "$EVIDENCE_DIR/render-app-error-logs.json" | tr -d ' ')"
      test "$APP_LOG_SIZE" -gt 10 || { echo "::error::CRM HTTP 500 detected but no application-log evidence captured"; exit 1; }
    fi
    FRONT_CODE="$(curl --silent --show-error --max-time 45 -o /dev/null -w '%{http_code}' "$WEB_BASE_URL")"
    STATUS_CODE="$(curl --silent --show-error --max-time 45 -o "$EVIDENCE_DIR/backend-status.json" -w '%{http_code}' "${WEB_BASE_URL}/api/system/backend-status")"
    AUTH_CODE="$(curl --silent --show-error --max-time 45 -o /dev/null -w '%{http_code}' "${WEB_BASE_URL}/api/platform/api/v1/auth/me")"
    V2_CODE="$(curl --silent --show-error --max-time 45 -o /dev/null -w '%{http_code}' "${WEB_BASE_URL}/api/platform/api/v2/crm/addresses/search")"
    test "$FRONT_CODE" = "200" && test "$STATUS_CODE" = "200"
    jq -e '.configured==true and .reachable==true and .statusCode==200' "$EVIDENCE_DIR/backend-status.json" >/dev/null
    test "$AUTH_CODE" = "401" && test "$V2_CODE" = "401"
    jq -n --arg release "$CRM_TESTED_SHA" --arg started "$CRM007_STARTED_AT" --arg completed "$END_TIME" \
      --arg run "$CRM007_RUN_URL" --arg upstream "$UPSTREAM_G1_RUN_ID" --arg vercel "$VERCEL_DEPLOYMENT_ID" \
      --arg render "$RENDER_DEPLOYMENT_ID" --arg digest "$RENDER_IMAGE_DIGEST" --arg lifecycle "$LIFECYCLE" --argjson count "$CRM_500_COUNT" \
      '{schema:"snad.crm007.production-closure.v3",result:(if $lifecycle=="PASS" and $count==0 then "PASS" else "FAIL" end),release_sha:$release,started_at_utc:$started,completed_at_utc:$completed,workflow_run:$run,upstream_crm_g1_run:$upstream,vercel_deployment_id:$vercel,render_deployment_id:$render,render_image_digest:($digest|if length>0 then . else null end),flyway:["20260717.100 SQL true","20260717.101 SQL true","20260718.1 SQL true","20260721.1 SQL true","20260721.2 SQL true"],authenticated_lifecycle:$lifecycle,two_tenant_isolation:$lifecycle,crm_500_count:$count,database_mode:"READ_ONLY",migration_action:"NONE"}' \
      > "$EVIDENCE_DIR/execution-summary.json"
    test "$LIFECYCLE" = "PASS" || { echo "::error::CRM-007 Playwright acceptance failed"; exit 1; }
    test "$CRM_500_COUNT" = "0" || { echo "::error::CRM HTTP 500 logs found during final closure"; exit 1; }
    ;;

  *) echo "Unknown phase: $phase"; exit 2 ;;
esac
