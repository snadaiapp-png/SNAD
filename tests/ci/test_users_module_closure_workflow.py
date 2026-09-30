#!/usr/bin/env python3
from pathlib import Path
import re

WORKFLOW = Path('.github/workflows/users-module-closure.yml')
CONTROL_TENANT_ID = '00000000-0000-0000-0000-000000000001'


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def main() -> None:
    require(WORKFLOW.exists(), f'missing {WORKFLOW}')
    text = WORKFLOW.read_text(encoding='utf-8')
    lower = text.lower()

    require('actions/checkout@v4' in text, 'workflow must checkout repository')
    require('ref: ${{ github.event.pull_request.head.sha || github.sha }}' in text,
            'workflow must checkout exact candidate SHA')
    require('test "$(git rev-parse HEAD)" = "$CANDIDATE_SHA"' in text,
            'workflow must verify checked-out SHA')
    require("- 'apps/web/package-lock.json'" in text,
            'workflow must rerun when the web dependency lockfile changes')

    require('services:' not in lower, 'service containers are forbidden')
    require('testcontainers' not in lower, 'Testcontainers path is forbidden')
    require('docker ' not in lower and 'docker:' not in lower, 'Docker path is forbidden')
    require('systemctl start postgresql' in text, 'host-native PostgreSQL must be started directly')

    require(f'SANAD_CONTROL_PLANE_TENANT_ID: {CONTROL_TENANT_ID}' in text,
            'workflow must configure the backend control-plane tenant guard')
    require(f'USERS_CONTROL_TENANT_ID: {CONTROL_TENANT_ID}' in text,
            'browser fixture and backend guard must target the same control-plane tenant')

    require('playwright.users-module.config.ts' in text,
            'dedicated users-module Playwright config must be authoritative')
    require('users-module-authenticated.spec.ts' in text,
            'behavioral authenticated spec must be enumerated')
    require('users-module-visual.spec.ts' in text,
            'visual authenticated spec must be enumerated')

    require('openssl rand' in text, 'credentials must be generated at runtime')
    require('::add-mask::' in text, 'runtime credentials must be masked')
    require(not re.search(r'(?im)^\s*(?:DATABASE_PASSWORD|USERS_TENANT_PASSWORD|USERS_CONTROL_PASSWORD):\s*[^$\s][^\n]*$', text),
            'workflow must not commit password literals')

    require('actions/upload-artifact@v4' in text, 'evidence must use upload-artifact@v4')
    require('test-results/users-module-visual-evidence' in text,
            'workflow must upload the visual evidence directory')
    require('manifest.ndjson' in text, 'workflow must require evidence manifest')

    for device in ('desktop', 'mobile'):
        for name in (
            'management-users',
            'management-user-detail',
            'management-access',
            'executive-users',
            'executive-user-detail',
            'executive-access',
        ):
            require(f'{device}/{name}.png' in text,
                    f'missing required evidence check: {device}/{name}.png')

    require('if-no-files-found: error' in text,
            'artifact upload must fail closed when evidence is absent')
    print('USERS_MODULE_CLOSURE_WORKFLOW_CONTRACT=PASS')


if __name__ == '__main__':
    main()
