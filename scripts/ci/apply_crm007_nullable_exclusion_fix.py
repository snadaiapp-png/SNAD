from pathlib import Path
import re


def replace_exact(path: str, old: str, new: str, expected: int = 1) -> None:
    file = Path(path)
    text = file.read_text()
    actual = text.count(old)
    if actual != expected:
        raise SystemExit(
            f"{path}: expected {expected} occurrences, found {actual}: {old[:120]!r}"
        )
    file.write_text(text.replace(old, new))


def replace_regex(path: str, pattern: str, replacement: str, expected: int = 1) -> None:
    file = Path(path)
    text = file.read_text()
    updated, actual = re.subn(pattern, replacement, text, flags=re.S)
    if actual != expected:
        raise SystemExit(f"{path}: expected {expected} regex replacements, found {actual}")
    file.write_text(updated)


repository = (
    "apps/sanad-platform/src/main/java/com/sanad/platform/crm/party/"
    "infrastructure/JdbcAddressCommunicationRepository.java"
)
replace_exact(
    repository,
    "(:exceptId IS NULL OR id<>:exceptId)",
    "(CAST(:exceptId AS UUID) IS NULL OR id<>:exceptId)",
    3,
)
replace_exact(
    repository,
    '.addValue("exceptId", exceptId)',
    '.addValue("exceptId", exceptId, Types.OTHER)',
    3,
)

postgres_test = (
    "apps/sanad-platform/src/test/java/com/sanad/platform/crm/party/"
    "infrastructure/JdbcAddressCommunicationNullableFilterPostgresTest.java"
)
test_method = '''    @Test
    void bindsNullableMutationExclusionsWithExplicitPostgresUuidType() {
        NamedParameterJdbcTemplate jdbc = jdbc();
        MapSqlParameterSource params = baseParams()
                .addValue("actorId", UUID.randomUUID())
                .addValue("now", java.sql.Timestamp.from(java.time.Instant.now()), Types.TIMESTAMP)
                .addValue("addressType", "WORK")
                .addValue("methodType", "EMAIL")
                .addValue("normalizedValue", "nullable-exclusion@example.invalid")
                .addValue("exceptId", null, Types.OTHER);

        assertThat(jdbc.update("""
                UPDATE crm_party_addresses
                   SET primary_address=FALSE,primary_slot=NULL,updated_by=:actorId,updated_at=:now,version=version+1
                 WHERE tenant_id=:tenantId AND owner_type=:ownerType AND owner_id=:ownerId
                   AND address_type=:addressType AND primary_address=TRUE
                   AND (CAST(:exceptId AS UUID) IS NULL OR id<>:exceptId)
                """, params)).isZero();

        assertThat(jdbc.update("""
                UPDATE crm_communication_methods
                   SET preferred=FALSE,preferred_slot=NULL,updated_by=:actorId,updated_at=:now,version=version+1
                 WHERE tenant_id=:tenantId AND owner_type=:ownerType AND owner_id=:ownerId
                   AND method_type=:methodType AND preferred=TRUE
                   AND (CAST(:exceptId AS UUID) IS NULL OR id<>:exceptId)
                """, params)).isZero();

        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM crm_communication_methods
                 WHERE tenant_id=:tenantId AND owner_type=:ownerType AND owner_id=:ownerId
                   AND method_type=:methodType AND normalized_value=:normalizedValue AND status<>'ARCHIVED'
                   AND (CAST(:exceptId AS UUID) IS NULL OR id<>:exceptId)
                """, params, Long.class)).isZero();
    }

'''
replace_exact(
    postgres_test,
    "    @Test\n    void productionRepositoryDeclaresAllNullableSqlTypes() throws Exception {",
    test_method
    + "    @Test\n    void productionRepositoryDeclaresAllNullableSqlTypes() throws Exception {",
)
replace_exact(
    postgres_test,
    '                .contains("CAST(:verificationStatus AS VARCHAR) IS NULL");',
    '                .contains("CAST(:verificationStatus AS VARCHAR) IS NULL")\n'
    '                .contains("CAST(:exceptId AS UUID) IS NULL")\n'
    '                .contains("addValue(\\\"exceptId\\\", exceptId, Types.OTHER)");',
)

reconciliation = ".github/workflows/crm-idempotency-production-reconciliation.yml"
replace_exact(
    reconciliation,
    "      - 'apps/sanad-platform/src/test/java/com/sanad/platform/crm/web/CrmIdempotencyBaselineGapReconciliationPostgresTest.java'\n",
    "      - 'apps/sanad-platform/src/test/java/com/sanad/platform/crm/web/CrmIdempotencyBaselineGapReconciliationPostgresTest.java'\n"
    "      - 'apps/sanad-platform/src/main/java/com/sanad/platform/crm/party/infrastructure/JdbcAddressCommunicationRepository.java'\n"
    "      - 'apps/sanad-platform/src/test/java/com/sanad/platform/crm/party/infrastructure/JdbcAddressCommunicationNullableFilterPostgresTest.java'\n"
    "      - '.github/workflows/crm-007-final-production-closure.yml'\n",
)
replace_exact(
    reconciliation,
    "      - name: Install PostgreSQL client\n",
    '''      - name: Initialize immutable reconciliation evidence
        shell: bash
        run: |
          set -euo pipefail
          EVIDENCE_DIR="$RUNNER_TEMP/crm-idempotency-reconciliation"
          rm -rf "$EVIDENCE_DIR"
          mkdir -p "$EVIDENCE_DIR"
          jq -n --arg source_sha "$GITHUB_SHA" --arg image_ref "$IMAGE_REF" --arg run "${GITHUB_SERVER_URL}/${GITHUB_REPOSITORY}/actions/runs/${GITHUB_RUN_ID}" \\
            '{result:"IN_PROGRESS",source_sha:$source_sha,image_ref:$image_ref,workflow_run:$run,database_mode:"READ_ONLY",flyway_repair:"NONE",history_edit:"NONE",manual_sql:"NONE"}' \\
            > "$EVIDENCE_DIR/run-context.json"

      - name: Install PostgreSQL client
''',
)
redeploy_block = '''      - name: Redeploy exact image
        id: deploy
        shell: bash
        run: |
          set -euo pipefail
          jq -n --arg image "$IMAGE_REF" '{imageUrl:$image}' > "$RUNNER_TEMP/render-deploy-request.json"
          curl --fail-with-body --silent --show-error --max-time 60 \\
            -X POST \\
            -H "Authorization: Bearer ${RENDER_API_KEY}" \\
            -H 'Accept: application/json' \\
            -H 'Content-Type: application/json' \\
            --data @"$RUNNER_TEMP/render-deploy-request.json" \\
            "https://api.render.com/v1/services/${RENDER_SERVICE_ID}/deploys" \\
            > "$RUNNER_TEMP/render-deploy-created.json"
          DEPLOY_ID="$(jq -r '(.deploy // .).id // empty' "$RUNNER_TEMP/render-deploy-created.json")"
          test -n "$DEPLOY_ID"
          echo "deployment_id=$DEPLOY_ID" >> "$GITHUB_OUTPUT"

          EXACT_LIVE=false
          STATUS=""
          for attempt in $(seq 1 180); do
            curl --fail-with-body --silent --show-error --max-time 30 \\
              -H "Authorization: Bearer ${RENDER_API_KEY}" \\
              -H 'Accept: application/json' \\
              "https://api.render.com/v1/services/${RENDER_SERVICE_ID}/deploys/${DEPLOY_ID}" \\
              > "$RUNNER_TEMP/render-deploy-state.json"
            STATUS="$(jq -r '(.deploy // .).status // empty' "$RUNNER_TEMP/render-deploy-state.json")"
            case "$STATUS" in
              build_failed|update_failed|canceled|deactivated)
                echo "::error::Render deployment failed: ${STATUS}"
                exit 1
                ;;
            esac

            curl --fail-with-body --silent --show-error --max-time 30 \\
              -H "Authorization: Bearer ${RENDER_API_KEY}" \\
              -H 'Accept: application/json' \\
              "https://api.render.com/v1/services/${RENDER_SERVICE_ID}/deploys?limit=50" \\
              > "$RUNNER_TEMP/render-deploys.json"
            jq --arg id "$DEPLOY_ID" --arg expected "$IMAGE_REF" \\
              '[if type == "array" then .[] else (.deploys // .items // [])[] end | (.deploy // .) | select(.id == $id and .status == "live" and (.image.ref // .imageRef) == $expected)] | first // empty' \\
              "$RUNNER_TEMP/render-deploys.json" > "$RUNNER_TEMP/render-live-exact.json"
            if [ "$(jq -r '.id // empty' "$RUNNER_TEMP/render-live-exact.json")" = "$DEPLOY_ID" ]; then
              EXACT_LIVE=true
              break
            fi
            sleep 10
          done
          test "$EXACT_LIVE" = "true" || { echo "::error::Render did not expose the created deployment as live on the exact immutable image"; exit 1; }
          cp "$RUNNER_TEMP/render-live-exact.json" "$RUNNER_TEMP/crm-idempotency-reconciliation/render-deployment.json"

'''
replace_regex(
    reconciliation,
    r"      - name: Redeploy exact image\n.*?(?=      - name: Verify migration and idempotency schema read-only)",
    redeploy_block,
)
replace_exact(
    reconciliation,
    "          jq -n --arg ref main '{ref:$ref}' > \"$RUNNER_TEMP/crm007-dispatch.json\"",
    "          jq -n --arg ref main --arg sha \"$GITHUB_SHA\" --arg run \"${{ steps.g1.outputs.run_id }}\" '{ref:$ref,inputs:{upstream_g1_sha:$sha,upstream_g1_run_id:$run}}' > \"$RUNNER_TEMP/crm007-dispatch.json\"",
)

crm007 = ".github/workflows/crm-007-final-production-closure.yml"
replace_exact(
    crm007,
    '''on:
  workflow_run:
    workflows: ["CRM G1 Production Closure"]
    types: [completed]
  workflow_dispatch:
''',
    '''on:
  workflow_dispatch:
    inputs:
      upstream_g1_sha:
        description: Exact successful CRM-G1 release SHA
        required: true
        type: string
      upstream_g1_run_id:
        description: Completed successful CRM-G1 workflow run ID
        required: true
        type: string
''',
)
replace_exact(
    crm007,
    "    if: github.event_name == 'workflow_dispatch' || github.event.workflow_run.conclusion == 'success'\n",
    "",
)
replace_exact(
    crm007,
    "      UPSTREAM_G1_SHA: ${{ github.event.workflow_run.head_sha }}\n      UPSTREAM_G1_RUN_ID: ${{ github.event.workflow_run.id }}\n",
    "      GH_TOKEN: ${{ github.token }}\n      UPSTREAM_G1_SHA: ${{ inputs.upstream_g1_sha }}\n      UPSTREAM_G1_RUN_ID: ${{ inputs.upstream_g1_run_id }}\n",
)
replace_exact(
    crm007,
    '''          if [ "$GITHUB_EVENT_NAME" = "workflow_run" ]; then
            test "$UPSTREAM_G1_SHA" = "$RELEASE_SHA" || { echo "::error::CRM-G1 success is not for current main"; exit 1; }
            test -n "$UPSTREAM_G1_RUN_ID"
          fi
''',
    '''          test "$UPSTREAM_G1_SHA" = "$RELEASE_SHA" || { echo "::error::Explicit CRM-G1 success is not for current main"; exit 1; }
          test -n "$UPSTREAM_G1_RUN_ID"
          curl --fail-with-body --silent --show-error --max-time 45 \\
            -H "Authorization: Bearer ${GH_TOKEN}" -H 'Accept: application/vnd.github+json' \\
            -H 'X-GitHub-Api-Version: 2022-11-28' \\
            "https://api.github.com/repos/${GITHUB_REPOSITORY}/actions/runs/${UPSTREAM_G1_RUN_ID}" \\
            > "$RUNNER_TEMP/upstream-g1-run.json"
          jq -e --arg sha "$RELEASE_SHA" '.head_sha == $sha and .status == "completed" and .conclusion == "success" and .name == "CRM G1 Production Closure"' \\
            "$RUNNER_TEMP/upstream-g1-run.json" >/dev/null || { echo "::error::Supplied CRM-G1 run is not completed/success for exact current main"; exit 1; }
''',
)
replace_exact(
    crm007,
    "              WHERE version IN ('20260717.100','20260717.101','20260718.1','20260721.1')",
    "              WHERE version IN ('20260717.100','20260717.101','20260718.1','20260721.1','20260721.2')",
)
replace_exact(
    crm007,
    '''          test "$(wc -l < "$EVIDENCE_DIR/flyway-crm007.txt" | tr -d ' ')" = "4"
          test "$(grep -Ec '^(20260717\.100|20260717\.101|20260718\.1|20260721\.1)\|SQL\|t(rue)?\|' "$EVIDENCE_DIR/flyway-crm007.txt")" = "4"''',
    '''          test "$(wc -l < "$EVIDENCE_DIR/flyway-crm007.txt" | tr -d ' ')" = "5"
          test "$(grep -Ec '^(20260717\.100|20260717\.101|20260718\.1|20260721\.1|20260721\.2)\|SQL\|t(rue)?\|' "$EVIDENCE_DIR/flyway-crm007.txt")" = "5"''',
)
collect_block = '''      - name: Collect CRM-007 report and inspect Render errors
        id: collect
        if: always() && steps.preflight.outcome == 'success'
        shell: bash
        run: |
          set -euo pipefail
          PLAYWRIGHT_RESULT="FAIL"
          if [ -f "$CRM007_EVIDENCE_FILE" ]; then PLAYWRIGHT_RESULT="PASS"; fi
          if [ -d apps/web/crm007-production-report ]; then cp -R apps/web/crm007-production-report "$EVIDENCE_DIR/"; fi
          if [ -d apps/web/test-results ]; then cp -R apps/web/test-results "$EVIDENCE_DIR/playwright-test-results"; fi

          END_TIME="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
          curl --get --fail-with-body --silent --show-error --max-time 45 \\
            -H "Authorization: Bearer ${RENDER_API_KEY}" -H 'Accept: application/json' \\
            --data-urlencode "ownerId=${RENDER_OWNER_ID}" --data-urlencode "resource=${RENDER_SERVICE_ID}" \\
            --data-urlencode "startTime=${CRM007_STARTED_AT}" --data-urlencode "endTime=${END_TIME}" \\
            --data-urlencode "type=request" --data-urlencode "statusCode=500" --data-urlencode "limit=100" \\
            https://api.render.com/v1/logs > "$EVIDENCE_DIR/render-500-logs.json"
          CRM_500_COUNT="$(jq '[if type=="array" then .[] else (.logs // .items // [])[] end | tostring | select(test("/api/v(1|2)/crm"))] | length' "$EVIDENCE_DIR/render-500-logs.json")"

          FRONT_CODE="$(curl --silent --show-error --max-time 45 -o /dev/null -w '%{http_code}' "$WEB_BASE_URL")"
          STATUS_CODE="$(curl --silent --show-error --max-time 45 -o "$EVIDENCE_DIR/backend-status.json" -w '%{http_code}' "${WEB_BASE_URL}/api/system/backend-status")"
          AUTH_CODE="$(curl --silent --show-error --max-time 45 -o /dev/null -w '%{http_code}' "${WEB_BASE_URL}/api/platform/api/v1/auth/me")"
          V2_CODE="$(curl --silent --show-error --max-time 45 -o /dev/null -w '%{http_code}' "${WEB_BASE_URL}/api/platform/api/v2/crm/addresses/search")"
          test "$FRONT_CODE" = "200"
          test "$STATUS_CODE" = "200"
          jq -e '.configured == true and .reachable == true and .statusCode == 200' "$EVIDENCE_DIR/backend-status.json" >/dev/null
          test "$AUTH_CODE" = "401" && test "$V2_CODE" = "401"

          jq -n --arg release "$CRM_TESTED_SHA" --arg started "$CRM007_STARTED_AT" --arg completed "$END_TIME" --arg run "$CRM007_RUN_URL" \\
            --arg upstream "$UPSTREAM_G1_RUN_ID" --arg vercel "$VERCEL_DEPLOYMENT_ID" --arg render "$RENDER_DEPLOYMENT_ID" --arg digest "$RENDER_IMAGE_DIGEST" \\
            --arg playwright "$PLAYWRIGHT_RESULT" --argjson count "$CRM_500_COUNT" \\
            '{schema:"snad.crm007.production-closure.v2",result:(if $playwright=="PASS" and $count==0 then "PASS" else "FAIL" end),release_sha:$release,started_at_utc:$started,completed_at_utc:$completed,workflow_run:$run,upstream_crm_g1_run:$upstream,vercel_deployment_id:$vercel,render_deployment_id:$render,render_image_digest:($digest|if length>0 then . else null end),flyway:["20260717.100 SQL true","20260717.101 SQL true","20260718.1 SQL true","20260721.1 SQL true","20260721.2 SQL true"],authenticated_lifecycle:$playwright,two_tenant_isolation:$playwright,crm_500_count:$count,database_mode:"READ_ONLY",migration_action:"NONE"}' \\
            > "$EVIDENCE_DIR/execution-summary.json"
          find "$EVIDENCE_DIR" -type f ! -name SHA256SUMS.txt -print0 | sort -z | xargs -0 sha256sum > "$EVIDENCE_DIR/SHA256SUMS.txt"

          test "$PLAYWRIGHT_RESULT" = "PASS" || { echo "::error::CRM-007 Playwright acceptance did not complete successfully"; exit 1; }
          test "$CRM_500_COUNT" = "0" || { echo "::error::CRM HTTP 500 logs found during final closure"; exit 1; }

'''
replace_regex(
    crm007,
    r"      - name: Collect CRM-007 report and inspect Render errors\n.*?(?=      - name: Upload immutable CRM-007 evidence)",
    collect_block,
)
replace_exact(
    crm007,
    "          | Flyway | 20260717.100, 20260717.101, 20260718.1, 20260721.1 = SQL / true |",
    "          | Flyway | 20260717.100, 20260717.101, 20260718.1, 20260721.1, 20260721.2 = SQL / true |",
)

Path("scripts/ci/apply_crm007_nullable_exclusion_fix.py").unlink()
