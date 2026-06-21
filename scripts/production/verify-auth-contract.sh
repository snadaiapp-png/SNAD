#!/usr/bin/env bash
set -euo pipefail

: "${PRODUCTION_BASE_URL:?PRODUCTION_BASE_URL is required}"
base="${PRODUCTION_BASE_URL%/}"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

assert_status() {
  local expected="$1" path="$2" method="${3:-GET}"
  local actual
  actual=$(curl --silent --show-error \
    --request "$method" \
    --output "$work/response.body" \
    --write-out '%{http_code}' \
    "$base$path")
  [ "$actual" = "$expected" ] || {
    echo "::error::$method $path expected HTTP $expected but received $actual."
    exit 1
  }
}

health=$(curl --fail-with-body --silent --show-error "$base/actuator/health")
jq -e '.status == "UP"' <<< "$health" >/dev/null

readiness=$(curl --fail-with-body --silent --show-error "$base/actuator/health/readiness")
jq -e '.status == "UP"' <<< "$readiness" >/dev/null

assert_status 401 /api/v1/auth/me
assert_status 401 /api/v1/auth/refresh POST
assert_status 401 /api/v1/organizations
assert_status 404 /actuator/env
assert_status 404 /swagger-ui.html

echo "Production health and unauthenticated security boundary verified."
