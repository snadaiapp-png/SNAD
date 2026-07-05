#!/usr/bin/env python3
"""
Apply executive order §8 security hardening to commercial-go-live.yml
extracted from PR #244 branch.

Changes:
  §8.2 — Isolate workflow_dispatch inputs from bash via env vars
  §8.4 — Verify least-privilege permissions (no PR/issues write)
  §8.1 — Confirm no credential inputs (already done in PR #244)
"""
import re
from pathlib import Path

SRC = Path("/tmp/commercial-go-live-pr244.yml")
DST = Path("/tmp/commercial-go-live-hardened.yml")

text = SRC.read_text(encoding="utf-8")

# ============================================================
# §8.2 — Add env vars for inputs isolation
# ============================================================
# Find the env: block of the commercial-release job and inject
# RELEASE_CONFIRMATION and REQUESTED_RELEASE_SHA at the top.
env_block_pattern = re.compile(
    r"(    env:\n)(      PRODUCTION_BASE_URL)",
    re.MULTILINE,
)

new_env_block = (
    "    env:\n"
    "      # §8.2 — Inputs are passed through env vars to isolate shell interpolation\n"
    "      RELEASE_CONFIRMATION: ${{ inputs.confirm }}\n"
    "      REQUESTED_RELEASE_SHA: ${{ inputs.release_sha }}\n"
    "      PRODUCTION_BASE_URL"
)

text = env_block_pattern.sub(new_env_block, text, count=1)

# ============================================================
# §8.2 — Replace direct input interpolation with env-var refs
# ============================================================
# Pattern: [ "${{ inputs.confirm }}" = "COMMERCIAL-GO-LIVE" ]
# → [ "$RELEASE_CONFIRMATION" = "COMMERCIAL-GO-LIVE" ]
text = text.replace(
    '[ "${{ inputs.confirm }}" = "COMMERCIAL-GO-LIVE" ]',
    '[ "$RELEASE_CONFIRMATION" = "COMMERCIAL-GO-LIVE" ]',
)

# Pattern: RELEASE_SHA="${{ inputs.release_sha }}"
# → RELEASE_SHA="$REQUESTED_RELEASE_SHA"
text = text.replace(
    'RELEASE_SHA="${{ inputs.release_sha }}"',
    'RELEASE_SHA="$REQUESTED_RELEASE_SHA"',
)

# ============================================================
# Verify §8.1 — No credential inputs remain
# ============================================================
forbidden_inputs = [
    "identity_b_email",
    "identity_b_password",
    "admin_password",
    "api_key",
    "token",
    "secret",
]
for inp in forbidden_inputs:
    pattern = re.compile(rf"^\s+{inp}\s*:", re.MULTILINE | re.IGNORECASE)
    if pattern.search(text):
        # Check if it's actually under workflow_dispatch.inputs (not env: which uses different syntax)
        # The inputs are indented with 6 spaces under workflow_dispatch
        match = pattern.search(text)
        line_start = text.rfind("\n", 0, match.start()) + 1
        line = text[line_start:text.find("\n", match.start())]
        if "inputs:" in text[:match.start()] and "env:" not in text[:match.start()].split("jobs:")[0]:
            raise SystemExit(f"❌ §8.1 violation: forbidden credential input '{inp}' found: {line.strip()}")

# ============================================================
# Verify §8.2 — No direct input interpolation in run: blocks
# ============================================================
# Look for ${{ inputs.* }} patterns in run: blocks
direct_interpolation_pattern = re.compile(r"\$\{\{\s*inputs\.\w+\s*\}\}")
matches = list(direct_interpolation_pattern.finditer(text))
if matches:
    print("⚠️  Direct input interpolations remaining (review):")
    for m in matches:
        line_start = text.rfind("\n", 0, m.start()) + 1
        line = text[line_start:text.find("\n", m.start())]
        print(f"   {line.strip()}")
    # We expect them only in env: declarations (which is correct)
    # Check that all remaining are in env: lines (variable assignment lines starting with whitespace + name: ${{ inputs.* }})
    bad = []
    for m in matches:
        line_start = text.rfind("\n", 0, m.start()) + 1
        line = text[line_start:text.find("\n", m.start())]
        # Env declaration lines look like: "      VAR_NAME: ${{ inputs.X }}"
        if not re.match(r"^\s+[A-Z_]+:\s+\$\{\{ inputs\.", line):
            bad.append(line.strip())
    if bad:
        raise SystemExit(f"❌ §8.2 violation: direct interpolation in shell run: {bad}")
    print("   ✅ All remaining interpolations are in env: declarations (correct pattern)")
else:
    print("✅ §8.2: No direct input interpolation anywhere")

# ============================================================
# §8.4 — Verify least-privilege permissions
# ============================================================
perms_match = re.search(r"^permissions:\s*\n((?:  [a-z-]+:\s*\w+\s*\n)+)", text, re.MULTILINE)
if not perms_match:
    raise SystemExit("❌ Could not find permissions: block")

perms_block = perms_match.group(1)
print(f"\n§8.4 — Current permissions block:\n{perms_block}")

# Verify no PR/issues write
if re.search(r"pull-requests:\s*write", perms_block):
    raise SystemExit("❌ §8.4 violation: pull-requests: write present")
if re.search(r"issues:\s*write", perms_block):
    raise SystemExit("❌ §8.4 violation: issues: write present")

# contents: write is required for release tag creation — keep but document
if re.search(r"contents:\s*write", perms_block):
    print("   ℹ️  contents: write is required for release tag creation (git tag) — documented")
else:
    print("   ✅ contents: read only")

# Write hardened file
DST.write_text(text, encoding="utf-8")
print(f"\n✅ Hardened file written to: {DST}")
print(f"   Lines: {len(text.splitlines())}")
