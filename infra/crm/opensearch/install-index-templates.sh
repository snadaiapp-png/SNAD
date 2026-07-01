#!/bin/sh
set -eu

OPENSEARCH_URL="${OPENSEARCH_URL:-http://opensearch:9200}"
TEMPLATE_FILE="/config/crm-index-template.json"
TEMPLATE_NAME="snad-crm-v1"

until curl --fail --silent "${OPENSEARCH_URL}/_cluster/health" >/dev/null; do
  sleep 2
done

curl --fail --silent --show-error \
  -X PUT \
  -H 'Content-Type: application/json' \
  --data-binary "@${TEMPLATE_FILE}" \
  "${OPENSEARCH_URL}/_index_template/${TEMPLATE_NAME}" >/dev/null

curl --fail --silent --show-error \
  -X PUT \
  -H 'Content-Type: application/json' \
  --data '{"aliases":{"snad-crm-read":{},"snad-crm-write":{"is_write_index":true}}}' \
  "${OPENSEARCH_URL}/snad-crm-000001" >/dev/null

printf 'Installed OpenSearch template %s and initial CRM index.\n' "${TEMPLATE_NAME}"
