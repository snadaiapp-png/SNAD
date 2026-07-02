#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT_DIR}"

[[ -f .env ]] || { echo 'Missing .env. Run make bootstrap.' >&2; exit 1; }
set -a
# shellcheck disable=SC1091
source .env
set +a

COMPOSE=(
  docker compose --env-file .env
  -f compose.yaml
  -f compose.crm.yaml
  -f compose.crm.messaging-storage.yaml
  -f compose.crm.tracing.yaml
  -f compose.crm.logging.yaml
  -f compose.crm.gateway.yaml
)
FAILURES=0

pass() { printf 'PASS  %s\n' "$1"; }
warn() { printf 'WARN  %s\n' "$1"; }
fail() { printf 'FAIL  %s\n' "$1" >&2; FAILURES=$((FAILURES + 1)); }

running() {
  "${COMPOSE[@]}" ps --services --status running | grep -Fxq "$1"
}

url_check() {
  local label="$1" url="$2" attempts="${3:-20}"
  for ((i=1; i<=attempts; i++)); do
    if curl --fail --silent --show-error "$url" >/dev/null 2>&1; then
      pass "$label"
      return 0
    fi
    sleep 2
  done
  fail "$label"
  return 1
}

if "${COMPOSE[@]}" config --quiet; then
  pass 'Merged CRM platform Compose contract'
else
  fail 'Merged CRM platform Compose contract'
fi

required_services=(postgres backend web valkey clamav opensearch rabbitmq minio otel-collector tempo loki api-gateway)
for service in "${required_services[@]}"; do
  running "$service" && pass "Service running: $service" || fail "Service missing: $service"
done

if running postgres; then
  required_tables=(
    event_outbox event_dead_letter workflow_definition workflow_instance workflow_task workflow_timer
    notification_template notification_message import_job export_job webhook_subscription webhook_delivery
    consent_record retention_policy privacy_request pipeline pipeline_stage lead opportunity activity
    custom_field_definition integration_endpoint attachment
  )
  for table in "${required_tables[@]}"; do
    count="$("${COMPOSE[@]}" exec -T postgres psql \
      -U "${POSTGRES_USER:-sanad}" -d "${POSTGRES_DB:-sanad}" -Atqc \
      "SELECT count(*) FROM information_schema.tables WHERE table_schema='crm_platform' AND table_name='${table}'")"
    [[ "$count" == 1 ]] && pass "Database contract crm_platform.${table}" || fail "Database contract crm_platform.${table}"
  done
  outbox_indexes="$("${COMPOSE[@]}" exec -T postgres psql \
    -U "${POSTGRES_USER:-sanad}" -d "${POSTGRES_DB:-sanad}" -Atqc \
    "SELECT count(*) FROM pg_indexes WHERE schemaname='crm_platform' AND indexname='idx_crm_event_outbox_dispatch'")"
  [[ "$outbox_indexes" == 1 ]] && pass 'Outbox dispatch index' || fail 'Outbox dispatch index'
fi

if running rabbitmq; then
  "${COMPOSE[@]}" exec -T rabbitmq rabbitmq-diagnostics -q ping >/dev/null \
    && pass 'RabbitMQ broker health' || fail 'RabbitMQ broker health'
  vhost="$("${COMPOSE[@]}" exec -T rabbitmq rabbitmqctl list_vhosts -q | grep -Fx '/snad' || true)"
  [[ "$vhost" == '/snad' ]] && pass 'RabbitMQ /snad vhost' || fail 'RabbitMQ /snad vhost'
fi

if running valkey; then
  if [[ -n "${CRM_CACHE_PASSWORD:-}" ]]; then
    "${COMPOSE[@]}" exec -T valkey valkey-cli -a "${CRM_CACHE_PASSWORD}" ping 2>/dev/null | grep -q PONG \
      && pass 'Valkey authenticated ping' || fail 'Valkey authenticated ping'
  else
    "${COMPOSE[@]}" exec -T valkey valkey-cli ping | grep -q PONG \
      && pass 'Valkey ping' || fail 'Valkey ping'
  fi
fi

if running minio; then
  url_check 'MinIO health' "http://localhost:${CRM_STORAGE_API_PORT:-9000}/minio/health/live" 20 || true
  if "${COMPOSE[@]}" run --rm minio-init >/dev/null; then
    pass 'S3 bucket exists, is private, and versioning is enabled'
  else
    fail 'S3 bucket initialization and versioning'
  fi
fi

if running clamav; then
  "${COMPOSE[@]}" exec -T clamav clamdscan --ping 1 >/dev/null \
    && pass 'ClamAV scanning engine' || fail 'ClamAV scanning engine'
fi

if running opensearch; then
  url_check 'OpenSearch cluster health' 'http://localhost:9200/_cluster/health' 20 || true
  curl --fail --silent 'http://localhost:9200/_index_template/snad-crm-v1' >/dev/null \
    && pass 'OpenSearch CRM template' || fail 'OpenSearch CRM template'
fi

if running backend; then
  url_check 'Backend readiness' "http://localhost:${BACKEND_PORT:-8080}/actuator/health/readiness" 30 || true
  url_check 'Backend Prometheus metrics' "http://localhost:${BACKEND_PORT:-8080}/actuator/prometheus" 10 || true
fi

if running web; then
  url_check 'CRM web application' "http://localhost:${WEB_PORT:-3000}" 20 || true
fi

if running api-gateway; then
  url_check 'CRM API gateway' 'http://localhost:8088/gateway-health' 20 || true
fi

if running tempo; then
  "${COMPOSE[@]}" exec -T tempo wget -q -O- http://localhost:3200/ready >/dev/null 2>&1 \
    && pass 'Tempo trace store' || warn 'Tempo image does not expose wget readiness; inspect container health'
fi

if running loki; then
  "${COMPOSE[@]}" exec -T loki wget -q -O- http://localhost:3100/ready >/dev/null 2>&1 \
    && pass 'Loki log store' || warn 'Loki image does not expose wget readiness; inspect container health'
fi

printf '\nCRM platform readiness: %d failure(s)\n' "$FAILURES"
[[ "$FAILURES" -eq 0 ]]
