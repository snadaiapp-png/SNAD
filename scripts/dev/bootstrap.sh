#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${ROOT_DIR}/.env"
ENV_EXAMPLE="${ROOT_DIR}/.env.example"

if [[ ! -f "${ENV_EXAMPLE}" ]]; then
  echo "Missing ${ENV_EXAMPLE}" >&2
  exit 1
fi

if [[ ! -f "${ENV_FILE}" ]]; then
  cp "${ENV_EXAMPLE}" "${ENV_FILE}"
  chmod 600 "${ENV_FILE}" 2>/dev/null || true
  echo "Created .env from .env.example"
fi

random_value() {
  local bytes="${1:-32}"
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -base64 "${bytes}" | tr -d '\n' | tr '/+' '_-'
    return
  fi

  if command -v python3 >/dev/null 2>&1; then
    python3 - "${bytes}" <<'PY'
import secrets
import sys
print(secrets.token_urlsafe(int(sys.argv[1])), end="")
PY
    return
  fi

  echo "openssl or python3 is required to generate local secrets" >&2
  exit 1
}

set_if_empty() {
  local key="$1"
  local value="$2"

  python3 - "${ENV_FILE}" "${key}" "${value}" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
key = sys.argv[2]
value = sys.argv[3]
lines = path.read_text(encoding="utf-8").splitlines()
updated = False
output = []

for line in lines:
    if line.startswith(f"{key}="):
        current = line.split("=", 1)[1]
        output.append(line if current else f"{key}={value}")
        updated = True
    else:
        output.append(line)

if not updated:
    output.append(f"{key}={value}")

path.write_text("\n".join(output) + "\n", encoding="utf-8")
PY
}

if ! command -v python3 >/dev/null 2>&1; then
  echo "python3 is required for deterministic .env updates" >&2
  exit 1
fi

set_if_empty POSTGRES_PASSWORD "$(random_value 32)"
set_if_empty SPRING_DATASOURCE_PASSWORD "$(grep '^POSTGRES_PASSWORD=' "${ENV_FILE}" | cut -d= -f2-)"
set_if_empty JWT_SECRET "$(random_value 64)"
set_if_empty REDIS_PASSWORD "$(random_value 32)"
set_if_empty GRAFANA_ADMIN_PASSWORD "$(random_value 32)"

chmod 600 "${ENV_FILE}" 2>/dev/null || true

cat <<'EOF'
SNAD local environment is initialized.

Next commands:
  make doctor
  make config
  make up

Optional profiles:
  make up-devtools
  make up-platform
  make up-observability
  make up-full

Secrets were generated into .env and were not printed.
EOF
