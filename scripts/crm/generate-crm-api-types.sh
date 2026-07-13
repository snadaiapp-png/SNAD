#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
OPENAPI_SPEC="${REPO_ROOT}/docs/crm/contracts/openapi/crm-openapi.json"
OUTPUT_TS="${REPO_ROOT}/apps/web/lib/api/generated/crm-api-types.ts"
WEB_DIR="${REPO_ROOT}/apps/web"

if [[ ! -s "$OPENAPI_SPEC" ]]; then
  echo "ERROR: OpenAPI spec not found at $OPENAPI_SPEC" >&2
  exit 2
fi

mkdir -p "$(dirname "$OUTPUT_TS")"

cd "$WEB_DIR"

if ! npm ls openapi-typescript >/dev/null 2>&1; then
  echo "Installing openapi-typescript (devDependency)..."
  npm install --save-dev openapi-typescript >/dev/null
fi

npx openapi-typescript "$OPENAPI_SPEC" --output "$OUTPUT_TS" --immutable --export-type

python3 - "$OUTPUT_TS" <<'PY'
import sys, re, pathlib
path = pathlib.Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
text = re.sub(r'^/\*\*.*?\*/\s*', '', text, count=1, flags=re.DOTALL)
header = """/**
 * SNAD CRM API — Generated TypeScript types.
 * ----------------------------------------------------------------------------
 * DO NOT EDIT BY HAND. This file is generated from
 *   docs/crm/contracts/openapi/crm-openapi.json
 * by the `npm run crm:generate-api-types` script (see
 *   scripts/crm/generate-crm-api-types.sh).
 *
 * Regeneration command (from apps/web):
 *   npm run crm:generate-api-types
 *
 * Branch: crm/003-stable-api-contracts
 * Gate:   CRM-G2 — API Contract and Concurrency Gate
 */

"""
path.write_text(header + text.lstrip(), encoding="utf-8")
print(f"Generated {path}")
PY

# Replace empty interface extends with type aliases
python3 - "$OUTPUT_TS" <<'PY'
import sys, re, pathlib
path = pathlib.Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
text = re.sub(
    r'export interface (\w+) extends (\w+)<(\w+)> \{\}',
    r'export type \1 = \2<\3>;',
    text
)
path.write_text(text, encoding="utf-8")
print("Replaced empty interfaces with type aliases")
PY

CHECKSUM=$(sha256sum "$OPENAPI_SPEC" | cut -d' ' -f1)
echo "OpenAPI spec sha256: $CHECKSUM"
echo "Output: $OUTPUT_TS"
