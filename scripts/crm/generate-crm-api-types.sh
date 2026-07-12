#!/usr/bin/env bash
# =============================================================================
# SNAD CRM — Generate Frontend TypeScript Types from OpenAPI
# -----------------------------------------------------------------------------
# Branch: crm/003-stable-api-contracts
# Gate:   CRM-G2 — API Contract and Concurrency Gate
#
# Regenerates apps/web/lib/api/generated/crm-api-types.ts from the committed
# OpenAPI artifact at docs/crm/contracts/openapi/crm-openapi.json.
#
# Usage (from apps/web):
#   npm run crm:generate-api-types
#
# Or directly:
#   bash scripts/crm/generate-crm-api-types.sh
#
# CI uses this script to detect drift: if regeneration produces a diff,
# CI fails with "Generated TypeScript types are out of date — run
# 'npm run crm:generate-api-types' and commit the result."
# =============================================================================
set -euo pipefail

SCRIPT_PATH="${BASH_SOURCE[0]}"
REPO_ROOT="$(cd "$(dirname "${SCRIPT_PATH}")/../.." && pwd)"
OPENAPI_SPEC="${REPO_ROOT}/docs/crm/contracts/openapi/crm-openapi.json"
OUTPUT_TS="${REPO_ROOT}/apps/web/lib/api/generated/crm-api-types.ts"
WEB_DIR="${REPO_ROOT}/apps/web"

if [[ ! -s "$OPENAPI_SPEC" ]]; then
  echo "ERROR: OpenAPI spec not found at $OPENAPI_SPEC" >&2
  exit 2
fi

mkdir -p "$(dirname "$OUTPUT_TS")"

cd "$WEB_DIR"

# Install openapi-typescript if not already present (idempotent).
if ! npm ls openapi-typescript >/dev/null 2>&1; then
  echo "Installing openapi-typescript (devDependency)..."
  npm install --save-dev openapi-typescript >/dev/null
fi

# Generate the types file.
npx openapi-typescript "$OPENAPI_SPEC" \
  --output "$OUTPUT_TS" \
  --immutable \
  --export-type

# Prepend the DO NOT EDIT header so the file is self-documenting.
HEADER=$(cat <<'EOF'
/**
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

EOF
)

# Strip any existing header lines starting with /** and ending with */
# (openapi-typescript writes its own header), then prepend ours.
python3 - "$OUTPUT_TS" <<'PY'
import sys, re, pathlib
path = pathlib.Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
# Remove the openapi-typescript default header block (if any).
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

# Compute and print the spec checksum so the evidence doc can record it.
CHECKSUM=$(sha256sum "$OPENAPI_SPEC" | cut -d' ' -f1)
echo "OpenAPI spec sha256: $CHECKSUM"
echo "Output: $OUTPUT_TS"
