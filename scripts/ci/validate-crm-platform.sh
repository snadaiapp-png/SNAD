#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"
FAILURES=0

require_file() {
  local path="$1"
  if [[ -s "$path" ]]; then
    printf 'PASS file %s\n' "$path"
  else
    printf 'FAIL file %s\n' "$path" >&2
    FAILURES=$((FAILURES + 1))
  fi
}

require_text() {
  local path="$1" text="$2"
  if grep -Fq "$text" "$path"; then
    printf 'PASS contract %s :: %s\n' "$path" "$text"
  else
    printf 'FAIL contract %s :: %s\n' "$path" "$text" >&2
    FAILURES=$((FAILURES + 1))
  fi
}

FILES=(
  apps/sanad-platform/src/main/resources/application-crm-platform.yml
  apps/sanad-platform/src/main/resources/db/migration/V50__create_crm_platform_runtime.sql
  apps/sanad-platform/src/main/resources/db/migration/V51__create_crm_attachment_registry.sql
  apps/sanad-platform/src/main/java/com/sanad/platform/crm/platform/CrmPlatformProperties.java
  apps/sanad-platform/src/main/java/com/sanad/platform/crm/platform/CrmPlatformConfiguration.java
  apps/sanad-platform/src/main/java/com/sanad/platform/crm/platform/CrmStorageConfiguration.java
  apps/sanad-platform/src/main/java/com/sanad/platform/crm/platform/CrmOutboxDispatcher.java
  apps/sanad-platform/src/main/java/com/sanad/platform/crm/platform/CrmTimerDispatcher.java
  apps/sanad-platform/src/main/java/com/sanad/platform/crm/platform/ClamAvClient.java
  compose.crm.messaging-storage.yaml
  compose.crm.tracing.yaml
  compose.crm.logging.yaml
  compose.crm.gateway.yaml
  infra/gateway/nginx.conf
  infra/observability/otel-collector.yml
  infra/observability/tempo.yml
  infra/observability/loki.yml
  infra/observability/promtail.yml
  infra/observability/prometheus/crm-alerts.yml
  scripts/crm/platform-readiness.sh
  scripts/crm/backup.sh
  scripts/crm/restore-verify.sh
  tests/performance/k6/crm-readiness.js
  deploy/kubernetes/crm/namespace.yaml
  deploy/kubernetes/crm/serviceaccount.yaml
  deploy/kubernetes/crm/configmap.yaml
)

for path in "${FILES[@]}"; do require_file "$path"; done

for dependency in spring-boot-starter-amqp spring-boot-starter-data-redis spring-boot-starter-quartz micrometer-tracing-bridge-otel opentelemetry-exporter-otlp '<artifactId>s3</artifactId>'; do
  require_text apps/sanad-platform/pom.xml "$dependency"
done

for table in event_outbox event_dead_letter workflow_definition workflow_instance workflow_task workflow_timer notification_message import_job export_job webhook_subscription webhook_delivery consent_record retention_policy privacy_request pipeline pipeline_stage lead opportunity activity custom_field_definition integration_endpoint; do
  require_text apps/sanad-platform/src/main/resources/db/migration/V50__create_crm_platform_runtime.sql "crm_platform.$table"
done

for service in rabbitmq minio minio-init; do
  require_text compose.crm.messaging-storage.yaml "$service:"
done
for service in otel-collector tempo; do
  require_text compose.crm.tracing.yaml "$service:"
done
for service in loki promtail; do
  require_text compose.crm.logging.yaml "$service:"
done
require_text compose.crm.gateway.yaml 'api-gateway:'

printf 'CRM platform completeness failures: %d\n' "$FAILURES"
[[ "$FAILURES" -eq 0 ]]
