#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${CRM_BASE_URL:-http://localhost:8080}"
TOKEN="${CRM_BEARER_TOKEN:-}"
SECOND_TENANT_TOKEN="${CRM_SECOND_TENANT_BEARER_TOKEN:-}"
RUN_ID="${CRM_SMOKE_RUN_ID:-$(date -u +%Y%m%d%H%M%S)-$RANDOM}"

if [[ -z "$TOKEN" ]]; then
  echo 'CRM_BEARER_TOKEN is required. This smoke test must run against the real authenticated CRM API.' >&2
  exit 2
fi

api_with_token() {
  local token="$1"
  local method="$2"
  local path="$3"
  local body="${4:-}"
  local args=(--fail --silent --show-error --max-time 30 -X "$method" "$BASE_URL$path"
    -H "Authorization: Bearer $token" -H 'Accept: application/json')
  if [[ -n "$body" ]]; then
    args+=(-H 'Content-Type: application/json' --data "$body")
  fi
  curl "${args[@]}"
}

api() {
  api_with_token "$TOKEN" "$@"
}

http_status_with_token() {
  local token="$1"
  local method="$2"
  local path="$3"
  local body="${4:-}"
  local response_file
  response_file=$(mktemp)
  local args=(--silent --show-error --max-time 30 -o "$response_file" -w '%{http_code}'
    -X "$method" "$BASE_URL$path" -H "Authorization: Bearer $token" -H 'Accept: application/json')
  if [[ -n "$body" ]]; then
    args+=(-H 'Content-Type: application/json' --data "$body")
  fi
  local status
  status=$(curl "${args[@]}")
  rm -f "$response_file"
  printf '%s' "$status"
}

field() {
  local key="$1"
  python3 -c 'import json,sys; value=json.load(sys.stdin); print(value[sys.argv[1]])' "$key"
}

assert_json() {
  local expression="$1"
  python3 -c "import json,sys; data=json.load(sys.stdin); assert $expression, data"
}

echo '0. Verify dashboard is authenticated and reachable'
api GET /api/v1/crm/dashboard | assert_json "set(['accounts','contacts','openLeads','openOpportunities']).issubset(data)"

echo '1. Create and read account'
ACCOUNT_NAME="Smoke Account $RUN_ID"
ACCOUNT_JSON=$(api POST /api/v1/crm/accounts "{\"displayName\":\"$ACCOUNT_NAME\",\"accountType\":\"PROSPECT\",\"primaryCurrencyCode\":\"SAR\",\"preferredLocale\":\"ar-SA\",\"timeZone\":\"Asia/Riyadh\",\"source\":\"REAL_SMOKE\"}")
ACCOUNT_ID=$(field id <<<"$ACCOUNT_JSON")
api GET "/api/v1/crm/accounts/$ACCOUNT_ID" | assert_json "data['id']=='$ACCOUNT_ID' and data['display_name']=='$ACCOUNT_NAME'"

echo '2. Create contact linked to the account'
CONTACT_JSON=$(api POST /api/v1/crm/contacts "{\"accountId\":\"$ACCOUNT_ID\",\"givenName\":\"Smoke\",\"familyName\":\"$RUN_ID\",\"preferredLocale\":\"ar-SA\",\"timeZone\":\"Asia/Riyadh\",\"consentSummary\":\"UNKNOWN\"}")
CONTACT_ID=$(field id <<<"$CONTACT_JSON")
assert_json "data['account_id']=='$ACCOUNT_ID'" <<<"$CONTACT_JSON"

echo '3. Create pipeline and read it with its stages'
PIPELINE_JSON=$(api POST /api/v1/crm/pipelines "{\"name\":\"Smoke Pipeline $RUN_ID\",\"currencyCode\":\"SAR\",\"stages\":[\"New\",\"Qualified\",\"Won\",\"Lost\"]}")
PIPELINE_ID=$(field id <<<"$PIPELINE_JSON")
api GET /api/v1/crm/pipelines | assert_json "any(item['id']=='$PIPELINE_ID' for item in data)"
STAGES_JSON=$(api GET "/api/v1/crm/pipelines/$PIPELINE_ID/stages")
FIRST_STAGE_ID=$(python3 -c 'import json,sys; print(json.load(sys.stdin)[0]["id"])' <<<"$STAGES_JSON")
WON_STAGE_ID=$(python3 -c 'import json,sys; print(next(item["id"] for item in json.load(sys.stdin) if item.get("terminal_state")=="WON"))' <<<"$STAGES_JSON")

echo '4. Create and move opportunity to Won'
OPP_JSON=$(api POST /api/v1/crm/opportunities "{\"accountId\":\"$ACCOUNT_ID\",\"contactId\":\"$CONTACT_ID\",\"pipelineId\":\"$PIPELINE_ID\",\"stageId\":\"$FIRST_STAGE_ID\",\"name\":\"Smoke Opportunity $RUN_ID\",\"amount\":1000,\"currencyCode\":\"SAR\"}")
OPP_ID=$(field id <<<"$OPP_JSON")
api PATCH "/api/v1/crm/opportunities/$OPP_ID/stage" "{\"stageId\":\"$WON_STAGE_ID\",\"reason\":\"Smoke acceptance\"}" | assert_json "data['id']=='$OPP_ID' and data['status']=='WON'"

echo '5. Create and complete activity'
ACTIVITY_JSON=$(api POST /api/v1/crm/activities "{\"activityType\":\"TASK\",\"subject\":\"Smoke follow-up $RUN_ID\",\"relatedType\":\"ACCOUNT\",\"relatedId\":\"$ACCOUNT_ID\",\"priority\":80}")
ACTIVITY_ID=$(field id <<<"$ACTIVITY_JSON")
api PATCH "/api/v1/crm/activities/$ACTIVITY_ID/complete" '{"result":"Smoke completed"}' | assert_json "data['id']=='$ACTIVITY_ID' and data['status']=='COMPLETED'"

echo '6. Create, qualify, and convert lead without an opportunity'
LEAD_JSON=$(api POST /api/v1/crm/leads "{\"displayName\":\"Smoke Lead $RUN_ID\",\"companyName\":\"Smoke Labs\",\"source\":\"REAL_SMOKE\"}")
LEAD_ID=$(field id <<<"$LEAD_JSON")
api PATCH "/api/v1/crm/leads/$LEAD_ID/status" '{"status":"QUALIFIED"}' | assert_json "data['id']=='$LEAD_ID' and data['status']=='QUALIFIED'"
api POST "/api/v1/crm/leads/$LEAD_ID/convert" '{"createOpportunity":false,"currencyCode":"SAR"}' | assert_json "data['idempotent'] is False and data['opportunity'] is None"
api GET "/api/v1/crm/leads/$LEAD_ID" | assert_json "data['id']=='$LEAD_ID' and data['status']=='CONVERTED'"

echo '7. Verify Customer 360 and timeline use application records'
api GET "/api/v1/crm/accounts/$ACCOUNT_ID/customer-360" | assert_json "len(data['contacts'])==1 and len(data['opportunities'])==1 and len(data['activities'])==1 and len(data['timeline'])>=3"
api GET "/api/v1/crm/timeline/ACCOUNT/$ACCOUNT_ID" | assert_json "len(data)>=3"

echo '8. Verify dashboard reflects the created records'
api GET /api/v1/crm/dashboard | assert_json "data['accounts']>=2 and data['contacts']>=2 and data['openLeads']>=0 and data['openOpportunities']>=0"

echo '9. Verify tenant isolation when a second-tenant token is available'
if [[ -n "$SECOND_TENANT_TOKEN" ]]; then
  ISOLATION_STATUS=$(http_status_with_token "$SECOND_TENANT_TOKEN" GET "/api/v1/crm/accounts/$ACCOUNT_ID")
  if [[ "$ISOLATION_STATUS" != "404" ]]; then
    echo "Tenant isolation failed: second tenant read returned HTTP $ISOLATION_STATUS, expected 404" >&2
    exit 1
  fi
  api_with_token "$SECOND_TENANT_TOKEN" GET /api/v1/crm/accounts | assert_json "all(item['id']!='$ACCOUNT_ID' for item in data)"
  echo 'CRM_TENANT_ISOLATION: PASS'
else
  echo 'CRM_TENANT_ISOLATION: SKIPPED (CRM_SECOND_TENANT_BEARER_TOKEN not set)'
fi

echo "CRM_REAL_SMOKE: PASS run=$RUN_ID account=$ACCOUNT_ID contact=$CONTACT_ID opportunity=$OPP_ID lead=$LEAD_ID"
