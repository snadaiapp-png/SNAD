#!/usr/bin/env python3
from pathlib import Path
import re

WORKFLOW = Path('.github/workflows/users-module-closure.yml')
PRODUCTION_WORKFLOW = Path('.github/workflows/users-production-release.yml')
CONTROL_TENANT_ID = '00000000-0000-0000-0000-000000000001'


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def verify_users_module_closure() -> None:
    require(WORKFLOW.exists(), f'missing {WORKFLOW}')
    text = WORKFLOW.read_text(encoding='utf-8')
    lower = text.lower()

    require('actions/checkout@v4' in text, 'workflow must checkout repository')
    require('ref: ${{ github.event.pull_request.head.sha || github.sha }}' in text,
            'workflow must checkout exact candidate SHA')
    require('test "$(git rev-parse HEAD)" = "$CANDIDATE_SHA"' in text,
            'workflow must verify checked-out SHA')

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


def verify_users_production_release() -> None:
    require(PRODUCTION_WORKFLOW.exists(), f'missing {PRODUCTION_WORKFLOW}')
    text = PRODUCTION_WORKFLOW.read_text(encoding='utf-8')
    lower = text.lower()

    require('name: Users Production Release' in text,
            'users production workflow must have the canonical name')
    require('workflow_dispatch:' in text,
            'users production workflow must be manually dispatched')
    for input_name in ('governance_sha:', 'deployment_image_sha:', 'rollback_on_failure:'):
        require(input_name in text, f'missing protected input {input_name}')

    require('git ls-remote' in text and 'refs/heads/main' in text,
            'governance SHA must be checked against current main')
    require('test "$remote_main" = "$GOVERNANCE_SHA"' in text,
            'workflow must reject stale governance SHA')
    require('git merge-base --is-ancestor "$DEPLOYMENT_IMAGE_SHA" "$GOVERNANCE_SHA"' in text,
            'deployment image SHA must be an ancestor of governance SHA')
    require('git diff --name-only "$DEPLOYMENT_IMAGE_SHA..$GOVERNANCE_SHA"' in text,
            'workflow must prove the image/governance diff is release-governance only')
    for allowed_path in (
        '.github/workflows/users-production-release.yml',
        '.github/workflows/users-module-closure.yml',
        'tests/ci/test_users_module_closure_workflow.py',
    ):
        require(allowed_path in text, f'lineage allowlist must contain {allowed_path}')

    require('ghcr.io/token' in text and '/manifests/$DEPLOYMENT_IMAGE_SHA' in text,
            'workflow must verify the immutable GHCR manifest without Docker')
    require('docker ' not in lower and 'docker:' not in lower,
            'Docker is forbidden in the Users production release path')
    require('testcontainers' not in lower and 'services:' not in lower,
            'containerized database/test paths are forbidden')

    for required_gate in (
        'scripts/production/verify-control-plane-tenant.sh',
        'scripts/production/verify-flyway.sh',
        'scripts/production/verify-auth-contract.sh',
        '/actuator/health/readiness',
    ):
        require(required_gate in text, f'missing fail-closed production gate {required_gate}')

    for required_secret in (
        'CONTROL_PLANE_ADMIN_EMAIL',
        'CONTROL_PLANE_ADMIN_PASSWORD',
        'SANAD_CONTROL_PLANE_TENANT_ID',
    ):
        require(required_secret in text, f'missing Users production credential/config {required_secret}')

    for endpoint in (
        '/api/v1/auth/login',
        '/api/v1/executive/users',
        '/api/v1/executive/roles',
        '/api/v1/executive/capabilities',
    ):
        require(endpoint in text, f'missing read-only Users production probe {endpoint}')

    require('CANONICAL_OWNER_EMAIL: snad.ai.app@gmail.com' in text,
            'canonical owner identity must be asserted in production')
    require('PLATFORM_OWNER' in text,
            'PLATFORM_OWNER role must be asserted in production')
    require('PLATFORM.USER.READ' in text and 'PLATFORM.ROLE.READ' in text,
            'mandatory Users read capabilities must be asserted in production')

    for forbidden in ('playwright-g2', 'g2-visual', 'E2E_HR_', '/hr/'):
        require(forbidden.lower() not in lower,
                f'Users production release must not depend on HR/G2 gate: {forbidden}')

    require('Roll back failed Users release' in text,
            'Users production release must retain rollback on verification failure')
    require("if: failure() && inputs.rollback_on_failure" in text,
            'rollback must be conditional on release failure and explicit policy')


def main() -> None:
    verify_users_module_closure()
    verify_users_production_release()
    print('USERS_MODULE_CLOSURE_WORKFLOW_CONTRACT=PASS')


if __name__ == '__main__':
    main()
