#!/usr/bin/env bash
set -euo pipefail

required=(
  SPRING_DATASOURCE_URL
  SPRING_DATASOURCE_USERNAME
  SPRING_DATASOURCE_PASSWORD
  JWT_SECRET
  CRM_CUSTOM_FIELD_ENCRYPTION_KEY
)

missing=()
for name in "${required[@]}"; do
  if [[ -z "${!name:-}" ]]; then
    missing+=("$name")
  fi
done

if (( ${#missing[@]} > 0 )); then
  printf 'Missing required CRM deployment variables: %s\n' "${missing[*]}" >&2
  exit 2
fi

python3 - <<'PY'
import base64, os, sys
value = os.environ['CRM_CUSTOM_FIELD_ENCRYPTION_KEY']
try:
    key = base64.b64decode(value, validate=True)
except Exception as exc:
    raise SystemExit(f'CRM_CUSTOM_FIELD_ENCRYPTION_KEY is not valid base64: {exc}')
if len(key) != 32:
    raise SystemExit('CRM_CUSTOM_FIELD_ENCRYPTION_KEY must decode to exactly 32 bytes (AES-256)')
if not os.environ['SPRING_DATASOURCE_URL'].startswith('jdbc:postgresql://'):
    raise SystemExit('SPRING_DATASOURCE_URL must target PostgreSQL')
if os.environ.get('SPRING_PROFILES_ACTIVE', 'local') == 'local':
    raise SystemExit('SPRING_PROFILES_ACTIVE must not be local for deployment')
print('CRM_SECRET_AND_DATABASE_PREFLIGHT: PASS')
PY

if [[ "${FLYWAY_ENABLED:-true}" != "true" ]]; then
  echo 'FLYWAY_ENABLED must be true for the CRM deployment migration.' >&2
  exit 2
fi

if [[ "${JPA_DDL_AUTO:-none}" != "none" ]]; then
  echo 'JPA_DDL_AUTO must be none; Flyway owns the production schema.' >&2
  exit 2
fi

echo 'CRM_DEPLOYMENT_PREFLIGHT: PASS'
