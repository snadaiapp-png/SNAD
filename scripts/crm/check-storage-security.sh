#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT_DIR}"

if [[ ! -f .env ]]; then
  echo "Missing .env. Run make bootstrap." >&2
  exit 1
fi

COMPOSE=(docker compose --env-file .env -f compose.yaml -f compose.crm.yaml)

if ! "${COMPOSE[@]}" ps --services --status running | grep -Fxq clamav; then
  echo "ClamAV profile is not running." >&2
  exit 1
fi

"${COMPOSE[@]}" exec -T clamav clamdscan --ping 1 >/dev/null

echo "SNAD CRM attachment scanning service is ready."
