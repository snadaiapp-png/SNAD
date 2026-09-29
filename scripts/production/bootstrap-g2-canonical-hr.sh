#!/usr/bin/env bash
set -euo pipefail

# G2 canonical HR bootstrap. API-only; PostgreSQL RLS remains authoritative.
for var in BASE_URL ACCESS_TOKEN G2_TENANT_ID EMP_USER_ID MGR_USER_ID HR_USER_ID; do
  test -n "${!var:-}" || { echo "::error::$var is required"; exit 1; }
done

API_V1="${BASE_URL%/}/api/v1"
HR_API="${BASE_URL%/}/api/v2/hr"
AUTH="Authorization: Bearer $ACCESS_TOKEN"
G2_EFFECTIVE_DATE="$(date -u +%F)"
echo "G2_EFFECTIVE_DATE=$G2_EFFECTIVE_DATE" >> "$GITHUB_ENV"

fail_http() {
  echo "::error::$1 failed (HTTP $2)"
  cat "$3" 2>/dev/null | jq . 2>/dev/null || cat "$3" 2>/dev/null || true
  exit 1
}
get_json() {
  local status
  status=$(curl --silent --show-error -o "$2" -w '%{http_code}' -H "$AUTH" "$1")
  [ "$status" = 200 ] || fail_http "$3" "$status" "$2"
}
post_json() {
  local status
  status=$(curl --silent --show-error -o "$4" -w '%{http_code}' -X POST "$1" \
    -H "$AUTH" -H 'Content-Type: application/json' -H "Idempotency-Key: $2" -d "$3")
  case "$status" in 200|201) ;; *) fail_http "$5" "$status" "$4" ;; esac
}

# Unique employer context only; zero or >1 is never guessed.
ORG_BODY=/tmp/g2-organizations.json
get_json "$API_V1/organizations?tenantId=$G2_TENANT_ID" "$ORG_BODY" 'Organization discovery'
ACTIVE_ORG_COUNT=$(jq '[.[] | select(.status == "ACTIVE")] | length' "$ORG_BODY")
if [ "$ACTIVE_ORG_COUNT" != "1" ]; then
  echo "::error::HR_CONTEXT_AMBIGUOUS: expected exactly one ACTIVE Organization, found $ACTIVE_ORG_COUNT"
  exit 1
fi
G2_ORGANIZATION_ID=$(jq -r '.[] | select(.status == "ACTIVE") | .id' "$ORG_BODY")

LE_BODY=/tmp/g2-eligible-legal-entities.json
get_json "$API_V1/organizations/$G2_ORGANIZATION_ID/legal-entities/eligible?tenantId=$G2_TENANT_ID&effectiveDate=$G2_EFFECTIVE_DATE" \
  "$LE_BODY" 'Legal Entity eligibility discovery'
ELIGIBLE_LE_COUNT=$(jq 'length' "$LE_BODY")
if [ "$ELIGIBLE_LE_COUNT" != "1" ]; then
  echo "::error::HR_CONTEXT_AMBIGUOUS: expected exactly one eligible Legal Entity, found $ELIGIBLE_LE_COUNT"
  exit 1
fi
G2_LEGAL_ENTITY_ID=$(jq -r '.[0].id' "$LE_BODY")
G2_LABOR_JURISDICTION=$(jq -r '.[0].statutoryCountryCode // .[0].registeredCountryCode // empty' "$LE_BODY")
[[ "$G2_LABOR_JURISDICTION" =~ ^[A-Z]{2}$ ]] || {
  echo '::error::HR_CONTEXT_AMBIGUOUS: Legal Entity has no valid ISO alpha-2 jurisdiction'; exit 1;
}
for pair in "G2_ORGANIZATION_ID=$G2_ORGANIZATION_ID" "G2_LEGAL_ENTITY_ID=$G2_LEGAL_ENTITY_ID" \
            "G2_LABOR_JURISDICTION=$G2_LABOR_JURISDICTION"; do echo "$pair" >> "$GITHUB_ENV"; done

ensure_person() {
  local label="$1" user_id="$2" first="$3" last="$4" body count person_id
  body="/tmp/g2-${label,,}-people.json"
  get_json "$HR_API/people" "$body" "$label Person lookup"
  count=$(jq --arg u "$user_id" '[.[] | select(.userId == $u)] | length' "$body")
  [ "$count" -le 1 ] || { echo "::error::HR_CONTEXT_AMBIGUOUS: $label has multiple Person links"; exit 1; }
  if [ "$count" -eq 1 ]; then
    jq -r --arg u "$user_id" '.[] | select(.userId == $u) | .personId' "$body"; return
  fi
  body="/tmp/g2-${label,,}-person-create.json"
  post_json "$HR_API/people" "g2-${label,,}-person-v1" \
    "$(jq -n --arg f "$first" --arg l "$last" '{firstName:$f,lastName:$l}')" "$body" "$label Person create"
  person_id=$(jq -r '.personId // empty' "$body")
  test -n "$person_id" || { echo "::error::$label Person create returned no personId"; exit 1; }
  body="/tmp/g2-${label,,}-person-link.json"
  post_json "$HR_API/people/$person_id/user-link" "g2-${label,,}-user-link-v1" \
    "$(jq -n --arg u "$user_id" '{userId:$u}')" "$body" "$label Person user-link"
  echo "$person_id"
}
EMP_PERSON_ID=$(ensure_person EMPLOYEE "$EMP_USER_ID" G2 'Employee QA')
MGR_PERSON_ID=$(ensure_person MANAGER "$MGR_USER_ID" G2 'Manager QA')
HR_PERSON_ID=$(ensure_person HR "$HR_USER_ID" G2 'HR QA')

ensure_employment() {
  local label="$1" person_id="$2" user_id="$3" body count employment_id state version number
  body="/tmp/g2-${label,,}-employments.json"
  get_json "$HR_API/employments" "$body" "$label Employment lookup"
  count=$(jq --arg p "$person_id" --arg le "$G2_LEGAL_ENTITY_ID" \
    '[.[] | select(.personId == $p and .legalEntityId == $le and (.currentStatus != "TERMINATED" and .currentStatus != "VOIDED"))] | length' "$body")
  [ "$count" -le 1 ] || { echo "::error::HR_CONTEXT_AMBIGUOUS: $label has multiple non-terminal Employments"; exit 1; }
  if [ "$count" -eq 1 ]; then
    employment_id=$(jq -r --arg p "$person_id" --arg le "$G2_LEGAL_ENTITY_ID" \
      '.[] | select(.personId == $p and .legalEntityId == $le and (.currentStatus != "TERMINATED" and .currentStatus != "VOIDED")) | .employmentId' "$body")
  else
    number="G2-${label:0:3}-${user_id:0:8}"
    body="/tmp/g2-${label,,}-employment-create.json"
    post_json "$HR_API/employments" "g2-${label,,}-employment-v1" \
      "$(jq -n --arg p "$person_id" --arg le "$G2_LEGAL_ENTITY_ID" --arg n "$number" \
        --arg d "$G2_EFFECTIVE_DATE" --arg j "$G2_LABOR_JURISDICTION" \
        '{personId:$p,legalEntityId:$le,employeeNumber:$n,employmentStartDate:$d,laborJurisdictionCode:$j,workerClassificationCode:"FULL_TIME"}')" \
      "$body" "$label Employment create"
    employment_id=$(jq -r '.employmentId // empty' "$body")
  fi
  test -n "$employment_id" || { echo "::error::$label has no employmentId"; exit 1; }

  body="/tmp/g2-${label,,}-employment-current.json"
  get_json "$HR_API/employments/$employment_id" "$body" "$label Employment read"
  state=$(jq -r '.currentStatus' "$body"); version=$(jq -r '.version' "$body")
  if [ "$state" = DRAFT ]; then
    post_json "$HR_API/employments/$employment_id/submit-onboarding" "g2-${label,,}-submit-onboarding-v1" \
      "$(jq -n --argjson v "$version" --arg d "$G2_EFFECTIVE_DATE" '{expectedVersion:$v,effectiveDate:$d,reasonCode:"G2_QA_PROVISIONING"}')" \
      "/tmp/g2-${label,,}-submit.json" "$label submit onboarding"
    get_json "$HR_API/employments/$employment_id" "$body" "$label Employment reread"
    state=$(jq -r '.currentStatus' "$body"); version=$(jq -r '.version' "$body")
  fi
  if [ "$state" = PENDING_ONBOARDING ]; then
    post_json "$HR_API/employments/$employment_id/activate" "g2-${label,,}-activate-v1" \
      "$(jq -n --argjson v "$version" --arg d "$G2_EFFECTIVE_DATE" '{expectedVersion:$v,effectiveDate:$d,reasonCode:"G2_QA_PROVISIONING"}')" \
      "/tmp/g2-${label,,}-activate.json" "$label Employment activate"
    get_json "$HR_API/employments/$employment_id" "$body" "$label Employment final read"
    state=$(jq -r '.currentStatus' "$body")
  fi
  [ "$state" = ACTIVE ] || { echo "::error::HR_CONTEXT_AMBIGUOUS: $label Employment state=$state"; exit 1; }
  echo "$employment_id"
}
EMP_EMPLOYMENT_ID=$(ensure_employment EMPLOYEE "$EMP_PERSON_ID" "$EMP_USER_ID")
MGR_EMPLOYMENT_ID=$(ensure_employment MANAGER "$MGR_PERSON_ID" "$MGR_USER_ID")
HR_EMPLOYMENT_ID=$(ensure_employment HR "$HR_PERSON_ID" "$HR_USER_ID")

ensure_assignment() {
  local label="$1" employment_id="$2" reports_to="$3" body count assignment_id existing_manager payload
  body="/tmp/g2-${label,,}-assignments.json"
  get_json "$HR_API/assignments" "$body" "$label Assignment lookup"
  count=$(jq --arg e "$employment_id" --arg o "$G2_ORGANIZATION_ID" \
    '[.[] | select(.employmentId == $e and .organizationId == $o and .assignmentType == "PRIMARY" and .status == "ACTIVE")] | length' "$body")
  [ "$count" -le 1 ] || { echo "::error::HR_CONTEXT_AMBIGUOUS: $label has multiple ACTIVE PRIMARY Assignments"; exit 1; }
  if [ "$count" -eq 1 ]; then
    assignment_id=$(jq -r --arg e "$employment_id" --arg o "$G2_ORGANIZATION_ID" \
      '.[] | select(.employmentId == $e and .organizationId == $o and .assignmentType == "PRIMARY" and .status == "ACTIVE") | .id' "$body")
    if [ -n "$reports_to" ]; then
      existing_manager=$(jq -r --arg e "$employment_id" --arg o "$G2_ORGANIZATION_ID" \
        '.[] | select(.employmentId == $e and .organizationId == $o and .assignmentType == "PRIMARY" and .status == "ACTIVE") | .reportsToAssignmentId // empty' "$body")
      [ "$existing_manager" = "$reports_to" ] || { echo "::error::HR_CONTEXT_AMBIGUOUS: $label points to another manager"; exit 1; }
    fi
    echo "$assignment_id"; return
  fi
  if [ -n "$reports_to" ]; then
    payload=$(jq -n --arg e "$employment_id" --arg o "$G2_ORGANIZATION_ID" --arg m "$reports_to" --arg d "$G2_EFFECTIVE_DATE" \
      '{employmentId:$e,organizationId:$o,reportsToAssignmentId:$m,assignmentType:"PRIMARY",occupancyMode:"NON_OCCUPYING",allocationPercent:100,effectiveFrom:$d}')
  else
    payload=$(jq -n --arg e "$employment_id" --arg o "$G2_ORGANIZATION_ID" --arg d "$G2_EFFECTIVE_DATE" \
      '{employmentId:$e,organizationId:$o,assignmentType:"PRIMARY",occupancyMode:"NON_OCCUPYING",allocationPercent:100,effectiveFrom:$d}')
  fi
  body="/tmp/g2-${label,,}-assignment-create.json"
  post_json "$HR_API/assignments" "g2-${label,,}-assignment-v1" "$payload" "$body" "$label Assignment create"
  assignment_id=$(jq -r '.id // empty' "$body")
  test -n "$assignment_id" || { echo "::error::$label Assignment create returned no id"; exit 1; }
  echo "$assignment_id"
}
MGR_ASSIGNMENT_ID=$(ensure_assignment MANAGER "$MGR_EMPLOYMENT_ID" '')
EMP_ASSIGNMENT_ID=$(ensure_assignment EMPLOYEE "$EMP_EMPLOYMENT_ID" "$MGR_ASSIGNMENT_ID")
HR_ASSIGNMENT_ID=$(ensure_assignment HR "$HR_EMPLOYMENT_ID" '')

for pair in "EMP_PERSON_ID=$EMP_PERSON_ID" "MGR_PERSON_ID=$MGR_PERSON_ID" "HR_PERSON_ID=$HR_PERSON_ID" \
            "EMP_EMPLOYMENT_ID=$EMP_EMPLOYMENT_ID" "MGR_EMPLOYMENT_ID=$MGR_EMPLOYMENT_ID" "HR_EMPLOYMENT_ID=$HR_EMPLOYMENT_ID" \
            "MGR_ASSIGNMENT_ID=$MGR_ASSIGNMENT_ID" "EMP_ASSIGNMENT_ID=$EMP_ASSIGNMENT_ID" "HR_ASSIGNMENT_ID=$HR_ASSIGNMENT_ID"; do
  echo "$pair" >> "$GITHUB_ENV"
done

echo 'G2_CANONICAL_HR_BOOTSTRAP=PASS'
echo 'G2_EMPLOYEE_MANAGER_RELATIONSHIP=CANONICAL_ASSIGNMENT_GRAPH'
echo 'DIRECT_PRODUCTION_DB_MUTATION=NO'
