#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "::error::$*" >&2
  exit 1
}

for required in BASE_URL G2_TENANT_ID ACCESS_TOKEN EMP_USER_ID MGR_USER_ID HR_USER_ID GITHUB_ENV; do
  [ -n "${!required:-}" ] || fail "$required is required"
done

API_V1="${BASE_URL%/}/api/v1"
HR_API="${BASE_URL%/}/api/v2/hr"
G2_EMPLOYMENT_START_DATE="2026-01-01"
G2_ACTIVE_DATE="2026-01-02"
G2_MANAGER_LINK_DATE="2026-01-03"

get_json() {
  local url="$1" label="$2" response
  if ! response=$(curl --silent --show-error --fail-with-body \
      -H "Authorization: Bearer $ACCESS_TOKEN" "$url"); then
    echo "::error::$label failed" >&2
    [ -n "${response:-}" ] && echo "$response" | jq . 2>/dev/null >&2 || true
    exit 1
  fi
  printf '%s' "$response"
}

post_json() {
  local url="$1" payload="$2" idempotency_key="$3" label="$4" response
  local -a headers=(-H "Authorization: Bearer $ACCESS_TOKEN" -H "Content-Type: application/json")
  if [ -n "$idempotency_key" ]; then
    headers+=(-H "Idempotency-Key: $idempotency_key")
  fi
  if ! response=$(curl --silent --show-error --fail-with-body \
      -X POST "$url" "${headers[@]}" -d "$payload"); then
    echo "::error::$label failed" >&2
    [ -n "${response:-}" ] && echo "$response" | jq . 2>/dev/null >&2 || true
    exit 1
  fi
  printf '%s' "$response"
}

set_output_var() {
  local name="$1" value="$2"
  printf -v "$name" '%s' "$value"
  echo "$name=$value" >> "$GITHUB_ENV"
}

ME=$(get_json "$API_V1/auth/me" "Read Tenant B admin capability set")
for capability in \
  USER.CREATE USER.WRITE ORGANIZATION.READ \
  HRM.EMPLOYEE.VIEW HRM.EMPLOYEE.CREATE HRM.EMPLOYEE.UPDATE \
  HRM.USER_LINK.MANAGE HRM.ASSIGNMENT.VIEW HRM.ASSIGNMENT.MANAGE; do
  if ! echo "$ME" | jq -e --arg cap "$capability" '.capabilities // [] | index($cap) != null' >/dev/null; then
    fail "Tenant B admin missing required capability: $capability"
  fi
done
echo "G2_CANONICAL_HR_ADMIN_CAPABILITIES=PASS"

ORGANIZATIONS=$(get_json "$API_V1/organizations?tenantId=$G2_TENANT_ID" "List G2 tenant organizations")
echo "$ORGANIZATIONS" | jq -e 'type == "array"' >/dev/null || fail "Organization API did not return an array"
ACTIVE_ORG_COUNT=$(echo "$ORGANIZATIONS" | jq '[.[] | select(.status == "ACTIVE")] | length')
[ "$ACTIVE_ORG_COUNT" = "1" ] || fail "G2 tenant must have exactly one ACTIVE organization; found $ACTIVE_ORG_COUNT"
G2_ORGANIZATION_ID=$(echo "$ORGANIZATIONS" | jq -r '.[] | select(.status == "ACTIVE") | .id')
[ -n "$G2_ORGANIZATION_ID" ] && [ "$G2_ORGANIZATION_ID" != "null" ] || fail "Active organization has no id"
set_output_var G2_ORGANIZATION_ID "$G2_ORGANIZATION_ID"

G2_LEGAL_ENTITY_ID=$(TID="$G2_TENANT_ID" python3 -c 'import os,uuid; print(uuid.uuid5(uuid.UUID(os.environ["TID"]), "auth-smoke-legal-entity"))')
set_output_var G2_LEGAL_ENTITY_ID "$G2_LEGAL_ENTITY_ID"
echo "G2_EMPLOYER_CONTEXT=RESOLVED_DETERMINISTICALLY"

ensure_person() {
  local label="$1" user_id="$2" last_name="$3" out_var="$4"
  local people by_user_count person_id candidate_count candidate user_link

  people=$(get_json "$HR_API/people" "List People for $label")
  by_user_count=$(echo "$people" | jq --arg uid "$user_id" '[.[] | select(.userId == $uid)] | length')
  if [ "$by_user_count" -gt 1 ]; then
    fail "$label has multiple canonical People linked to the same user"
  fi

  if [ "$by_user_count" = "1" ]; then
    person_id=$(echo "$people" | jq -r --arg uid "$user_id" '.[] | select(.userId == $uid) | .personId')
    echo "$label Person exists: $person_id (REUSED)"
  else
    candidate_count=$(echo "$people" | jq --arg last "$last_name" \
      '[.[] | select(.firstName == "G2" and .lastName == $last)] | length')
    [ "$candidate_count" -le 1 ] || fail "$label has ambiguous unlinked G2 QA Person candidates"

    if [ "$candidate_count" = "1" ]; then
      candidate=$(echo "$people" | jq --arg last "$last_name" '.[] | select(.firstName == "G2" and .lastName == $last)')
      person_id=$(echo "$candidate" | jq -r '.personId')
      user_link=$(echo "$candidate" | jq -r '.userId // empty')
      if [ -n "$user_link" ] && [ "$user_link" != "$user_id" ]; then
        fail "$label deterministic QA Person is linked to a different user"
      fi
      echo "$label Person recovered from deterministic QA name: $person_id"
    else
      local payload created
      payload=$(jq -n --arg last "$last_name" '{firstName:"G2",middleName:null,lastName:$last}')
      created=$(post_json "$HR_API/people" "$payload" "g2-${label,,}-person-v1" "Create $label Person")
      person_id=$(echo "$created" | jq -r '.personId // empty')
      [ -n "$person_id" ] || fail "$label Person create response missing personId"
      user_link=""
      echo "$label Person created: $person_id"
    fi

    if [ -z "${user_link:-}" ]; then
      post_json "$HR_API/people/$person_id/user-link" \
        "$(jq -n --arg uid "$user_id" '{userId:$uid}')" \
        "g2-${label,,}-user-link-v1" "Link $label Person to governed user" >/dev/null
    fi

    people=$(get_json "$HR_API/people" "Verify $label Person user link")
    if ! echo "$people" | jq -e --arg pid "$person_id" --arg uid "$user_id" \
      '.[] | select(.personId == $pid and .userId == $uid)' >/dev/null; then
      fail "$label canonical Person user-link verification failed"
    fi
  fi

  set_output_var "$out_var" "$person_id"
}

ensure_active_employment() {
  local label="$1" person_id="$2" employee_number="$3" out_var="$4"
  local employments matches count employment_id employment status version payload result

  employments=$(get_json "$HR_API/employments" "List Employments for $label")
  matches=$(echo "$employments" | jq --arg pid "$person_id" --arg le "$G2_LEGAL_ENTITY_ID" \
    '[.[] | select(.personId == $pid and .legalEntityId == $le and (.currentStatus != "TERMINATED" and .currentStatus != "VOIDED"))]')
  count=$(echo "$matches" | jq 'length')
  [ "$count" -le 1 ] || fail "$label has multiple non-terminal Employments in the deterministic Legal Entity"

  if [ "$count" = "0" ]; then
    payload=$(jq -n \
      --arg pid "$person_id" --arg le "$G2_LEGAL_ENTITY_ID" --arg num "$employee_number" \
      --arg start "$G2_EMPLOYMENT_START_DATE" \
      '{personId:$pid,legalEntityId:$le,employeeNumber:$num,employmentStartDate:$start,laborJurisdictionCode:"SA",workerClassificationCode:"FULL_TIME"}')
    result=$(post_json "$HR_API/employments" "$payload" "g2-${label,,}-employment-v1" "Create $label Employment")
    employment_id=$(echo "$result" | jq -r '.employmentId // empty')
    [ -n "$employment_id" ] || fail "$label Employment create response missing employmentId"
    echo "$label Employment created: $employment_id"
  else
    employment_id=$(echo "$matches" | jq -r '.[0].employmentId')
    echo "$label Employment exists: $employment_id (REUSED)"
  fi

  employment=$(get_json "$HR_API/employments/$employment_id" "Read $label Employment")
  status=$(echo "$employment" | jq -r '.currentStatus')
  version=$(echo "$employment" | jq -r '.version')

  if [ "$status" = "DRAFT" ]; then
    payload=$(jq -n --arg d "$G2_EMPLOYMENT_START_DATE" --argjson v "$version" \
      '{effectiveDate:$d,expectedVersion:$v,reasonCode:"G2_QA_PROVISIONING"}')
    post_json "$HR_API/employments/$employment_id/submit-onboarding" "$payload" \
      "g2-${label,,}-submit-onboarding-v1" "Submit $label onboarding" >/dev/null
    employment=$(get_json "$HR_API/employments/$employment_id" "Read $label Employment after onboarding submit")
    status=$(echo "$employment" | jq -r '.currentStatus')
    version=$(echo "$employment" | jq -r '.version')
  fi

  if [ "$status" = "PENDING_ONBOARDING" ]; then
    payload=$(jq -n --arg d "$G2_ACTIVE_DATE" --argjson v "$version" \
      '{effectiveDate:$d,expectedVersion:$v,reasonCode:"G2_QA_PROVISIONING"}')
    post_json "$HR_API/employments/$employment_id/activate" "$payload" \
      "g2-${label,,}-activate-v1" "Activate $label Employment" >/dev/null
    employment=$(get_json "$HR_API/employments/$employment_id" "Read $label Employment after activation")
    status=$(echo "$employment" | jq -r '.currentStatus')
  fi

  [ "$status" = "ACTIVE" ] || fail "$label Employment is not ACTIVE after provisioning (status=$status)"
  set_output_var "$out_var" "$employment_id"
}

ensure_primary_assignment() {
  local label="$1" employment_id="$2" reports_to_id="$3" out_var="$4"
  local assignments matches count assignment assignment_id current_report version effective_from payload result

  assignments=$(get_json "$HR_API/assignments" "List Assignments for $label")
  matches=$(echo "$assignments" | jq --arg eid "$employment_id" \
    '[.[] | select(.employmentId == $eid and .assignmentType == "PRIMARY" and .status == "ACTIVE" and .effectiveTo == null)]')
  count=$(echo "$matches" | jq 'length')
  [ "$count" -le 1 ] || fail "$label has multiple open ACTIVE PRIMARY assignments"

  if [ "$count" = "0" ]; then
    if [ -n "$reports_to_id" ]; then
      payload=$(jq -n --arg eid "$employment_id" --arg org "$G2_ORGANIZATION_ID" --arg mgr "$reports_to_id" --arg d "$G2_ACTIVE_DATE" \
        '{employmentId:$eid,organizationId:$org,orgUnitId:null,positionId:null,reportsToAssignmentId:$mgr,assignmentType:"PRIMARY",occupancyMode:"NON_OCCUPYING",allocationPercent:100,effectiveFrom:$d,effectiveTo:null}')
    else
      payload=$(jq -n --arg eid "$employment_id" --arg org "$G2_ORGANIZATION_ID" --arg d "$G2_ACTIVE_DATE" \
        '{employmentId:$eid,organizationId:$org,orgUnitId:null,positionId:null,reportsToAssignmentId:null,assignmentType:"PRIMARY",occupancyMode:"NON_OCCUPYING",allocationPercent:100,effectiveFrom:$d,effectiveTo:null}')
    fi
    result=$(post_json "$HR_API/assignments" "$payload" "g2-${label,,}-primary-assignment-v1" "Create $label PRIMARY Assignment")
    assignment_id=$(echo "$result" | jq -r '.assignmentId // empty')
    [ -n "$assignment_id" ] || fail "$label Assignment create response missing assignmentId"
    assignment="$result"
    echo "$label PRIMARY Assignment created: $assignment_id"
  else
    assignment=$(echo "$matches" | jq '.[0]')
    assignment_id=$(echo "$assignment" | jq -r '.assignmentId')
    echo "$label PRIMARY Assignment exists: $assignment_id (REUSED)"
  fi

  current_report=$(echo "$assignment" | jq -r '.reportsToAssignmentId // empty')
  if [ -n "$reports_to_id" ] && [ "$current_report" != "$reports_to_id" ]; then
    version=$(echo "$assignment" | jq -r '.version')
    effective_from=$(echo "$assignment" | jq -r '.effectiveFrom')
    if [[ "$effective_from" > "$G2_MANAGER_LINK_DATE" ]] || [ "$effective_from" = "$G2_MANAGER_LINK_DATE" ]; then
      fail "$label existing PRIMARY Assignment starts on/after manager-link date; refusing to invent a later effective date"
    fi
    payload=$(jq -n --arg mgr "$reports_to_id" --arg d "$G2_MANAGER_LINK_DATE" --argjson v "$version" \
      '{reportsToAssignmentId:$mgr,effectiveDate:$d,expectedVersion:$v}')
    result=$(post_json "$HR_API/assignments/$assignment_id/change-manager" "$payload" \
      "g2-${label,,}-manager-link-v1" "Link $label Assignment to Manager")
    assignment_id=$(echo "$result" | jq -r '.assignmentId // empty')
    [ -n "$assignment_id" ] || fail "$label manager-link response missing replacement assignmentId"
  fi

  assignment=$(get_json "$HR_API/assignments/$assignment_id" "Verify $label PRIMARY Assignment")
  if ! echo "$assignment" | jq -e --arg eid "$employment_id" --arg org "$G2_ORGANIZATION_ID" \
    '.employmentId == $eid and .organizationId == $org and .assignmentType == "PRIMARY" and .status == "ACTIVE" and .effectiveTo == null' >/dev/null; then
    fail "$label PRIMARY Assignment verification failed"
  fi
  if [ -n "$reports_to_id" ] && ! echo "$assignment" | jq -e --arg mgr "$reports_to_id" '.reportsToAssignmentId == $mgr' >/dev/null; then
    fail "$label PRIMARY Assignment reporting link verification failed"
  fi

  set_output_var "$out_var" "$assignment_id"
}

ensure_person EMPLOYEE "$EMP_USER_ID" "EmployeeQA" EMP_PERSON_ID
ensure_person MANAGER "$MGR_USER_ID" "ManagerQA" MGR_PERSON_ID
ensure_person HR "$HR_USER_ID" "HRQA" HR_PERSON_ID

ensure_active_employment EMPLOYEE "$EMP_PERSON_ID" "G2-EMPLOYEE-QA" EMP_EMPLOYMENT_ID
ensure_active_employment MANAGER "$MGR_PERSON_ID" "G2-MANAGER-QA" MGR_EMPLOYMENT_ID
ensure_active_employment HR "$HR_PERSON_ID" "G2-HR-QA" HR_EMPLOYMENT_ID

ensure_primary_assignment MANAGER "$MGR_EMPLOYMENT_ID" "" MGR_ASSIGNMENT_ID
ensure_primary_assignment HR "$HR_EMPLOYMENT_ID" "" HR_ASSIGNMENT_ID
ensure_primary_assignment EMPLOYEE "$EMP_EMPLOYMENT_ID" "$MGR_ASSIGNMENT_ID" EMP_ASSIGNMENT_ID

echo "G2_CANONICAL_PERSON_LINKS=PASS"
echo "G2_CANONICAL_EMPLOYMENTS=PASS"
echo "G2_CANONICAL_ASSIGNMENTS=PASS"
echo "G2_CANONICAL_REPORTING=PASS"
echo "CONTROL_PLANE_TENANT_USED=NO"
