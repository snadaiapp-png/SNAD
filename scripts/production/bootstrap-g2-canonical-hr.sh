#!/usr/bin/env bash
set -euo pipefail

# Task 7 / G2 canonical HR identity bootstrap.
# Required env: BASE_URL, ACCESS_TOKEN, G2_TENANT_ID,
#               EMP_USER_ID, MGR_USER_ID, HR_USER_ID.
# Governance: API-only, PostgreSQL RLS remains authoritative, no direct DB access.

for var in BASE_URL ACCESS_TOKEN G2_TENANT_ID EMP_USER_ID MGR_USER_ID HR_USER_ID; do
  if [ -z "${!var:-}" ]; then
    echo "::error::$var is required"
    exit 1
  fi
done

API_V1="${BASE_URL%/}/api/v1"
HR_API="${BASE_URL%/}/api/v2/hr"
AUTH_HEADER="Authorization: Bearer $ACCESS_TOKEN"
G2_EFFECTIVE_DATE="$(date -u +%F)"
echo "G2_EFFECTIVE_DATE=$G2_EFFECTIVE_DATE" >> "$GITHUB_ENV"

fail_http() {
  local label="$1" status="$2" body="$3"
  echo "::error::$label failed (HTTP $status)"
  cat "$body" 2>/dev/null | jq . 2>/dev/null || cat "$body" 2>/dev/null || true
  exit 1
}

get_json() {
  local url="$1" output="$2" label="$3"
  local status
  status=$(curl --silent --show-error -o "$output" -w '%{http_code}' \
    -H "$AUTH_HEADER" "$url")
  if [ "$status" != "200" ]; then
    fail_http "$label" "$status" "$output"
  fi
}

post_json() {
  local url="$1" key="$2" payload="$3" output="$4" label="$5"
  local status
  status=$(curl --silent --show-error -o "$output" -w '%{http_code}' \
    -X POST "$url" \
    -H "$AUTH_HEADER" \
    -H "Content-Type: application/json" \
    -H "Idempotency-Key: $key" \
    -d "$payload")
  if [ "$status" != "200" ] && [ "$status" != "201" ]; then
    fail_http "$label" "$status" "$output"
  fi
}

# -----------------------------------------------------------------------------
# 1. Resolve exactly one active Organization. Ambiguity is a hard blocker.
# -----------------------------------------------------------------------------
ORG_BODY=/tmp/g2-organizations.json
get_json "$API_V1/organizations?tenantId=$G2_TENANT_ID" "$ORG_BODY" "Organization discovery"
ACTIVE_ORG_COUNT=$(jq '[.[] | select(.status == "ACTIVE")] | length' "$ORG_BODY")
if [ "$ACTIVE_ORG_COUNT" != "1" ]; then
  echo "::error::HR_CONTEXT_AMBIGUOUS: expected exactly one ACTIVE Organization, found $ACTIVE_ORG_COUNT"
  exit 1
fi
G2_ORGANIZATION_ID=$(jq -r '.[] | select(.status == "ACTIVE") | .id' "$ORG_BODY")
echo "G2_ORGANIZATION_ID=$G2_ORGANIZATION_ID" >> "$GITHUB_ENV"

# -----------------------------------------------------------------------------
# 2. Resolve exactly one active Legal Entity eligible for that Organization.
#    The endpoint is tenant-bound and read-only.
# -----------------------------------------------------------------------------
LE_BODY=/tmp/g2-eligible-legal-entities.json
get_json "$API_V1/organizations/$G2_ORGANIZATION_ID/legal-entities/eligible?tenantId=$G2_TENANT_ID&effectiveDate=$G2_EFFECTIVE_DATE" \
  "$LE_BODY" "Legal Entity eligibility discovery"
ELIGIBLE_LE_COUNT=$(jq 'length' "$LE_BODY")
if [ "$ELIGIBLE_LE_COUNT" != "1" ]; then
  echo "::error::HR_CONTEXT_AMBIGUOUS: expected exactly one eligible Legal Entity, found $ELIGIBLE_LE_COUNT"
  exit 1
fi
G2_LEGAL_ENTITY_ID=$(jq -r '.[0].id' "$LE_BODY")
G2_LABOR_JURISDICTION=$(jq -r '.[0].statutoryCountryCode // .[0].registeredCountryCode // empty' "$LE_BODY")
if ! [[ "$G2_LABOR_JURISDICTION" =~ ^[A-Z]{2}$ ]]; then
  echo "::error::HR_CONTEXT_AMBIGUOUS: Legal Entity does not expose a valid ISO-3166 alpha-2 jurisdiction"
  exit 1
fi
echo "G2_LEGAL_ENTITY_ID=$G2_LEGAL_ENTITY_ID" >> "$GITHUB_ENV"
echo "G2_LABOR_JURISDICTION=$G2_LABOR_JURISDICTION" >> "$GITHUB_ENV"

# -----------------------------------------------------------------------------
# 3. Canonical Person identity: User -> Person through governed v2 HR APIs.
#    Re-running the same Idempotency-Key reuses the same command result.
# -----------------------------------------------------------------------------
ensure_person() {
  local label="$1" user_id="$2" first="$3" last="$4"
  local list_body="/tmp/g2-${label,,}-people.json"
  get_json "$HR_API/people" "$list_body" "$label Person lookup"

  local count person_id
  count=$(jq --arg u "$user_id" '[.[] | select(.userId == $u)] | length' "$list_body")
  if [ "$count" -gt 1 ]; then
    echo "::error::HR_CONTEXT_AMBIGUOUS: $label has multiple Person links"
    exit 1
  fi
  if [ "$count" -eq 1 ]; then
    jq -r --arg u "$user_id" '.[] | select(.userId == $u) | .personId' "$list_body"
    return 0
  fi

  local create_body="/tmp/g2-${label,,}-person-create.json"
  post_json "$HR_API/people" "g2-${label,,}-person-v1" \
    "$(jq -n --arg first "$first" --arg last "$last" '{firstName:$first,lastName:$last}')" \
    "$create_body" "$label Person create"
  person_id=$(jq -r '.personId // empty' "$create_body")
  if [ -z "$person_id" ]; then
    echo "::error::$label Person create returned no personId"
    exit 1
  fi

  local link_body="/tmp/g2-${label,,}-person-link.json"
  post_json "$HR_API/people/$person_id/user-link" "g2-${label,,}-user-link-v1" \
    "$(jq -n --arg u "$user_id" '{userId:$u}')" \
    "$link_body" "$label Person user-link"
  echo "$person_id"
}

EMP_PERSON_ID=$(ensure_person EMPLOYEE "$EMP_USER_ID" "G2" "Employee QA")
MGR_PERSON_ID=$(ensure_person MANAGER "$MGR_USER_ID" "G2" "Manager QA")
HR_PERSON_ID=$(ensure_person HR "$HR_USER_ID" "G2" "HR QA")
for pair in "EMP_PERSON_ID=$EMP_PERSON_ID" "MGR_PERSON_ID=$MGR_PERSON_ID" "HR_PERSON_ID=$HR_PERSON_ID"; do
  echo "$pair" >> "$GITHUB_ENV"
done

# -----------------------------------------------------------------------------
# 4. Canonical Employment identity. DRAFT -> PENDING_ONBOARDING -> ACTIVE.
# -----------------------------------------------------------------------------
ensure_employment() {
  local label="$1" person_id="$2" user_id="$3"
  local list_body="/tmp/g2-${label,,}-employments.json"
  get_json "$HR_API/employments" "$list_body" "$label Employment lookup"

  local count employment_id state version
  count=$(jq --arg p "$person_id" --arg le "$G2_LEGAL_ENTITY_ID" \
    '[.[] | select(.personId == $p and .legalEntityId == $le and (.currentStatus != "TERMINATED" and .currentStatus != "VOIDED"))] | length' \
    "$list_body")
  if [ "$count" -gt 1 ]; then
    echo "::error::HR_CONTEXT_AMBIGUOUS: $label has multiple non-terminal Employments in the Legal Entity"
    exit 1
  fi

  if [ "$count" -eq 1 ]; then
    employment_id=$(jq -r --arg p "$person_id" --arg le "$G2_LEGAL_ENTITY_ID" \
      '.[] | select(.personId == $p and .legalEntityId == $le and (.currentStatus != "TERMINATED" and .currentStatus != "VOIDED")) | .employmentId' \
      "$list_body")
  else
    local create_body="/tmp/g2-${label,,}-employment-create.json"
    local employee_number="G2-${label:0:3}-${user_id:0:8}"
    post_json "$HR_API/employments" "g2-${label,,}-employment-v1" \
      "$(jq -n \
        --arg p "$person_id" \
        --arg le "$G2_LEGAL_ENTITY_ID" \
        --arg n "$employee_number" \
        --arg d "$G2_EFFECTIVE_DATE" \
        --arg j "$G2_LABOR_JURISDICTION" \
        '{personId:$p,legalEntityId:$le,employeeNumber:$n,employmentStartDate:$d,laborJurisdictionCode:$j,workerClassificationCode:"FULL_TIME"}')" \
      "$create_body" "$label Employment create"
    employment_id=$(jq -r '.employmentId // empty' "$create_body")
    if [ -z "$employment_id" ]; then
      echo "::error::$label Employment create returned no employmentId"
      exit 1
    fi
  fi

  local current_body="/tmp/g2-${label,,}-employment-current.json"
  get_json "$HR_API/employments/$employment_id" "$current_body" "$label Employment read"
  state=$(jq -r '.currentStatus' "$current_body")
  version=$(jq -r '.version' "$current_body")

  if [ "$state" = "DRAFT" ]; then
    local transition_body="/tmp/g2-${label,,}-employment-submit.json"
    post_json "$HR_API/employments/$employment_id/submit-onboarding" "g2-${label,,}-submit-onboarding-v1" \
      "$(jq -n --argjson v "$version" --arg d "$G2_EFFECTIVE_DATE" '{expectedVersion:$v,effectiveDate:$d,reasonCode:"G2_QA_PROVISIONING"}')" \
      "$transition_body" "$label submit onboarding"
    get_json "$HR_API/employments/$employment_id" "$current_body" "$label Employment reread"
    state=$(jq -r '.currentStatus' "$current_body")
    version=$(jq -r '.version' "$current_body")
  fi

  if [ "$state" = "PENDING_ONBOARDING" ]; then
    local activate_body="/tmp/g2-${label,,}-employment-activate.json"
    post_json "$HR_API/employments/$employment_id/activate" "g2-${label,,}-activate-v1" \
      "$(jq -n --argjson v "$version" --arg d "$G2_EFFECTIVE_DATE" '{expectedVersion:$v,effectiveDate:$d,reasonCode:"G2_QA_PROVISIONING"}')" \
      "$activate_body" "$label Employment activate"
    get_json "$HR_API/employments/$employment_id" "$current_body" "$label Employment final read"
    state=$(jq -r '.currentStatus' "$current_body")
  fi

  if [ "$state" != "ACTIVE" ]; then
    echo "::error::HR_CONTEXT_AMBIGUOUS: $label Employment must be ACTIVE; current state=$state"
    exit 1
  fi
  echo "$employment_id"
}

EMP_EMPLOYMENT_ID=$(ensure_employment EMPLOYEE "$EMP_PERSON_ID" "$EMP_USER_ID")
MGR_EMPLOYMENT_ID=$(ensure_employment MANAGER "$MGR_PERSON_ID" "$MGR_USER_ID")
HR_EMPLOYMENT_ID=$(ensure_employment HR "$HR_PERSON_ID" "$HR_USER_ID")
for pair in "EMP_EMPLOYMENT_ID=$EMP_EMPLOYMENT_ID" "MGR_EMPLOYMENT_ID=$MGR_EMPLOYMENT_ID" "HR_EMPLOYMENT_ID=$HR_EMPLOYMENT_ID"; do
  echo "$pair" >> "$GITHUB_ENV"
done

# -----------------------------------------------------------------------------
# 5. Canonical PRIMARY Assignments. Manager first; Employee reports to Manager.
# -----------------------------------------------------------------------------
ensure_assignment() {
  local label="$1" employment_id="$2" reports_to="$3"
  local list_body="/tmp/g2-${label,,}-assignments.json"
  get_json "$HR_API/assignments" "$list_body" "$label Assignment lookup"

  local count assignment_id
  count=$(jq --arg e "$employment_id" --arg o "$G2_ORGANIZATION_ID" \
    '[.[] | select(.employmentId == $e and .organizationId == $o and .assignmentType == "PRIMARY" and .status == "ACTIVE")] | length' \
    "$list_body")
  if [ "$count" -gt 1 ]; then
    echo "::error::HR_CONTEXT_AMBIGUOUS: $label has multiple ACTIVE PRIMARY Assignments"
    exit 1
  fi
  if [ "$count" -eq 1 ]; then
    assignment_id=$(jq -r --arg e "$employment_id" --arg o "$G2_ORGANIZATION_ID" \
      '.[] | select(.employmentId == $e and .organizationId == $o and .assignmentType == "PRIMARY" and .status == "ACTIVE") | .assignmentId' \
      "$list_body")
    if [ -n "$reports_to" ]; then
      local existing_manager
      existing_manager=$(jq -r --arg e "$employment_id" --arg o "$G2_ORGANIZATION_ID" \
        '.[] | select(.employmentId == $e and .organizationId == $o and .assignmentType == "PRIMARY" and .status == "ACTIVE") | .reportsToAssignmentId // empty' \
        "$list_body")
      if [ "$existing_manager" != "$reports_to" ]; then
        echo "::error::HR_CONTEXT_AMBIGUOUS: $label PRIMARY Assignment points to a different manager"
        exit 1
      fi
    fi
    echo "$assignment_id"
    return 0
  fi

  local create_body="/tmp/g2-${label,,}-assignment-create.json"
  local payload
  if [ -n "$reports_to" ]; then
    payload=$(jq -n \
      --arg e "$employment_id" --arg o "$G2_ORGANIZATION_ID" --arg m "$reports_to" --arg d "$G2_EFFECTIVE_DATE" \
      '{employmentId:$e,organizationId:$o,reportsToAssignmentId:$m,assignmentType:"PRIMARY",occupancyMode:"NON_OCCUPYING",allocationPercent:100,effectiveFrom:$d}')
  else
    payload=$(jq -n \
      --arg e "$employment_id" --arg o "$G2_ORGANIZATION_ID" --arg d "$G2_EFFECTIVE_DATE" \
      '{employmentId:$e,organizationId:$o,assignmentType:"PRIMARY",occupancyMode:"NON_OCCUPYING",allocationPercent:100,effectiveFrom:$d}')
  fi
  post_json "$HR_API/assignments" "g2-${label,,}-assignment-v1" "$payload" "$create_body" "$label Assignment create"
  assignment_id=$(jq -r '.assignmentId // .id // empty' "$create_body")
  if [ -z "$assignment_id" ]; then
    echo "::error::$label Assignment create returned no assignmentId"
    exit 1
  fi
  echo "$assignment_id"
}

MGR_ASSIGNMENT_ID=$(ensure_assignment MANAGER "$MGR_EMPLOYMENT_ID" "")
EMP_ASSIGNMENT_ID=$(ensure_assignment EMPLOYEE "$EMP_EMPLOYMENT_ID" "$MGR_ASSIGNMENT_ID")
HR_ASSIGNMENT_ID=$(ensure_assignment HR "$HR_EMPLOYMENT_ID" "")
for pair in "MGR_ASSIGNMENT_ID=$MGR_ASSIGNMENT_ID" "EMP_ASSIGNMENT_ID=$EMP_ASSIGNMENT_ID" "HR_ASSIGNMENT_ID=$HR_ASSIGNMENT_ID"; do
  echo "$pair" >> "$GITHUB_ENV"
done

echo "G2_CANONICAL_HR_BOOTSTRAP=PASS"
echo "G2_EMPLOYEE_MANAGER_RELATIONSHIP=CANONICAL_ASSIGNMENT_GRAPH"
echo "DIRECT_PRODUCTION_DB_MUTATION=NO"
