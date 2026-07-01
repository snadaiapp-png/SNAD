#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

if [[ -f "${ROOT_DIR}/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "${ROOT_DIR}/.env"
  set +a
fi

BACKEND_URL="http://localhost:${BACKEND_PORT:-8080}"
WEB_URL="http://localhost:${WEB_PORT:-3000}"

check_url() {
  local name="$1"
  local url="$2"
  local attempts="${3:-30}"

  for ((i = 1; i <= attempts; i++)); do
    if curl --fail --silent --show-error "${url}" >/dev/null; then
      printf 'PASS  %s: %s\n' "${name}" "${url}"
      return 0
    fi
    sleep 2
  done

  printf 'FAIL  %s: %s\n' "${name}" "${url}" >&2
  return 1
}

check_url "Backend readiness" "${BACKEND_URL}/actuator/health/readiness" 45
check_url "Backend liveness" "${BACKEND_URL}/actuator/health/liveness" 10
check_url "OpenAPI contract" "${BACKEND_URL}/v3/api-docs" 10
check_url "Web application" "${WEB_URL}" 30

printf '\nSNAD core runtime smoke checks passed.\n'
