#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${CRM_BASE_URL:-http://localhost:8080}"
TOKEN="${CRM_BEARER_TOKEN:-}"

if [[ -z "$TOKEN" ]]; then
  echo 'CRM_BEARER_TOKEN is required. This smoke test must run against the real authenticated CRM API.' >&2
  exit 2
fi

api() {
  local method="$1"
  local path="$2"
  local body="${3:-}"
  if [[ -n "$body" ]]; then
    curl --fail --silent --show-error \
      -X "$method" "$BASE_URL$path" \
      -H "Authorization: Bearer $TOKEN" \
      -H 'Content-Type: application/json' \
      -H 'Accept: application/json' \
      --data "$body"
  else
    curl --fail --silent --show-error \
      -X "$method" "$BASE_URL$path" \
      -H "Authorization: Bearer $TOKEN" \
      -H 'Accept: application/json'
  fi
}

echo '1. Create account'
ACCOUNT_JSON=$(api POST /api/v1/crm/accounts '{"displayName":"Smoke Account","accountType":"PROSPECT","primaryCurrencyCode":"SAR","preferredLocale":"ar-SA","timeZone":"Asia/Riyadh","source":"REAL_SMOKE"}')
ACCOUNT_ID=$(python3 - <<'PY' <<<"$ACCOUNT_JSON"
import json,sys
print(json.load(sys.stdin)['id'])
PY
)

echo '2. Create contact'
CONTACT_JSON=$(api POST /api/v1/crm/contacts "{\"accountId\":\"$ACCOUNT_ID\",\"givenName\":\"Smoke\",\"familyName\":\"Contact\",\"primaryEmail\":\"smoke@example.test\",\"preferredLocale\":\"ar-SA\",\"timeZone\":\"Asia/Riyadh\"}")
CONTACT_ID=$(python3 - <<'PY' <<<"$CONTACT_JSON"
import json,sys
print(json.load(sys.stdin)['id'])
PY
)

echo '3. Create pipeline'
PIPELINE_JSON=$(api POST /api/v1/crm/pipelines '{"name":"Smoke Pipeline","currencyCode":"SAR","stages":["New","Qualified","Won","Lost"]}')
PIPELINE_ID=$(python3 - <<'PY' <<<"$PIPELINE_JSON"
import json,sys
print(json.load(sys.stdin)['id'])
PY
)
STAGE_ID=$(python3 - <<'PY' <<<"$PIPELINE_JSON"
import json,sys
print(json.load(sys.stdin)['stageIds'][0])
PY
)

echo '4. Create opportunity'
OPP_JSON=$(api POST /api/v1/crm/opportunities "{\"accountId\":\"$ACCOUNT_ID\",\"contactId\":\"$CONTACT_ID\",\"pipelineId\":\"$PIPELINE_ID\",\"stageId\":\"$STAGE_ID\",\"name\":\"Smoke Opportunity\",\"amount\":1000,\"currencyCode\":\"SAR\"}")
OPP_ID=$(python3 - <<'PY' <<<"$OPP_JSON"
import json,sys
print(json.load(sys.stdin)['id'])
PY
)

echo '5. Create activity and read timeline'
api POST /api/v1/crm/activities "{\"activityType\":\"TASK\",\"subject\":\"Smoke follow-up\",\"relatedType\":\"ACCOUNT\",\"relatedId\":\"$ACCOUNT_ID\"}" >/dev/null
api GET "/api/v1/crm/timeline/ACCOUNT/$ACCOUNT_ID" | python3 -m json.tool >/dev/null

echo "CRM_REAL_SMOKE: PASS account=$ACCOUNT_ID contact=$CONTACT_ID opportunity=$OPP_ID"
