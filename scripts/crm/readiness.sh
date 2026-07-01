#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT_DIR}"

if [[ ! -f .env ]]; then
  echo "Missing .env. Run: make bootstrap" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1091
source .env
set +a

COMPOSE=(docker compose --env-file .env -f compose.yaml -f compose.crm.yaml)
FAILURES=0

pass() { printf 'PASS  %s\n' "$1"; }
warn() { printf 'WARN  %s\n' "$1"; }
fail() { printf 'FAIL  %s\n' "$1" >&2; FAILURES=$((FAILURES + 1)); }

service_running() {
  "${COMPOSE[@]}" ps --services --status running | grep -Fxq "$1"
}

check_url() {
  local label="$1"
  local url="$2"
  local attempts="${3:-20}"
  for ((attempt = 1; attempt <= attempts; attempt++)); do
    if curl --fail --silent --show-error "${url}" >/dev/null 2>&1; then
      pass "${label}: ${url}"
      return 0
    fi
    sleep 2
  done
  fail "${label}: ${url}"
  return 1
}

if "${COMPOSE[@]}" config --quiet; then
  pass "Merged CRM Compose configuration"
else
  fail "Merged CRM Compose configuration"
fi

if service_running postgres; then
  pass "PostgreSQL service is running"
  if "${COMPOSE[@]}" exec -T postgres pg_isready -U "${POSTGRES_USER:-sanad}" -d "${POSTGRES_DB:-sanad}" >/dev/null; then
    pass "PostgreSQL accepts connections"
  else
    fail "PostgreSQL readiness"
  fi

  REQUIRED_EXTENSIONS="vector pg_stat_statements pg_trgm unaccent btree_gin btree_gist citext pgcrypto"
  for extension in ${REQUIRED_EXTENSIONS}; do
    count="$("${COMPOSE[@]}" exec -T postgres psql -U "${POSTGRES_USER:-sanad}" -d "${POSTGRES_DB:-sanad}" -Atqc "SELECT count(*) FROM pg_extension WHERE extname='${extension}'")"
    if [[ "${count}" == "1" ]]; then
      pass "PostgreSQL extension ${extension}"
    else
      fail "PostgreSQL extension ${extension}"
    fi
  done

  schema_count="$("${COMPOSE[@]}" exec -T postgres psql -U "${POSTGRES_USER:-sanad}" -d "${POSTGRES_DB:-sanad}" -Atqc "SELECT count(*) FROM information_schema.schemata WHERE schema_name='crm_runtime'")"
  [[ "${schema_count}" == "1" ]] && pass "CRM runtime schema" || fail "CRM runtime schema"

  max_connections="$("${COMPOSE[@]}" exec -T postgres psql -U "${POSTGRES_USER:-sanad}" -d "${POSTGRES_DB:-sanad}" -Atqc "SHOW max_connections")"
  if [[ "${max_connections}" -ge 300 ]]; then
    pass "PostgreSQL max_connections=${max_connections}"
  else
    fail "PostgreSQL max_connections=${max_connections}; expected >=300"
  fi
else
  fail "PostgreSQL service is not running"
fi

if service_running backend; then
  check_url "Backend readiness" "http://localhost:${BACKEND_PORT:-8080}/actuator/health/readiness" 30 || true
  check_url "Backend liveness" "http://localhost:${BACKEND_PORT:-8080}/actuator/health/liveness" 10 || true
  check_url "OpenAPI contract" "http://localhost:${BACKEND_PORT:-8080}/v3/api-docs" 10 || true
  if "${COMPOSE[@]}" exec -T backend sh -c 'test -d /var/lib/snad/crm-attachments && test -w /var/lib/snad/crm-attachments'; then
    pass "CRM attachment filesystem is writable"
  else
    fail "CRM attachment filesystem is not writable"
  fi
else
  warn "Backend is not running; API checks skipped"
fi

if service_running web; then
  check_url "CRM web application" "http://localhost:${WEB_PORT:-3000}" 20 || true
else
  warn "Web application is not running"
fi

if service_running valkey; then
  if "${COMPOSE[@]}" exec -T valkey valkey-cli ping | grep -q PONG; then
    pass "Valkey cache and coordination service"
  else
    fail "Valkey ping"
  fi
else
  warn "Valkey profile is not active"
fi

if service_running opensearch; then
  check_url "OpenSearch cluster" "http://localhost:9200/_cluster/health" 20 || true
  if curl --fail --silent "http://localhost:9200/_index_template/snad-crm-v1" >/dev/null; then
    pass "OpenSearch CRM index template"
  else
    fail "OpenSearch CRM index template"
  fi
else
  warn "OpenSearch profile is not active"
fi

if service_running prometheus; then
  check_url "Prometheus" "http://localhost:${PROMETHEUS_PORT:-9090}/-/ready" 10 || true
else
  warn "Prometheus profile is not active"
fi

if service_running grafana; then
  check_url "Grafana" "http://localhost:${GRAFANA_PORT:-3001}/api/health" 10 || true
else
  warn "Grafana profile is not active"
fi

printf '\nCRM readiness result: %d failure(s)\n' "${FAILURES}"
[[ "${FAILURES}" -eq 0 ]]
