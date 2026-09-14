#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[3]
path = root / '.github/workflows/workflow-y2-production-write-canary.yml'
text = path.read_text()

required = [
    'workflow_dispatch:',
    'release_sha:',
    'confirm:',
    'environment: production',
    'secrets.SANAD_ADMIN_EMAIL',
    'secrets.SANAD_ADMIN_PASSWORD',
    'secrets.RENDER_API_KEY',
    'secrets.RENDER_SERVICE_ID',
    'ghcr.io/snadaiapp-png/snad-backend:${RELEASE_SHA}',
    'git diff --quiet "$RELEASE_SHA" HEAD -- apps/sanad-platform',
    'scripts/production/verify-workflow-y2-production-write-canary.sh',
    'workflow-y2-production-write-canary-${{ github.run_id }}',
]
for needle in required:
    assert needle in text, f'missing workflow contract: {needle}'

for forbidden in ['DATABASE_URL', 'psql ', 'flyway repair', 'docker ', 'testcontainers']:
    assert forbidden.lower() not in text.lower(), f'forbidden production-canary behavior: {forbidden}'
assert '-X POST' not in text and '--request POST' not in text, 'workflow must not mutate Render via API'

assert "[ \"$CONFIRM\" = \"run-y2-canary\" ]" in text
print('workflow-y2-production-write-canary workflow contract: PASS')
