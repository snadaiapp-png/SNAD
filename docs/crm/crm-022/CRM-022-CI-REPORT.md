# CRM-022 — CI Report

| Field | Value |
|-------|-------|
| Workflow | `.github/workflows/ci.yml` (`name: CI`) |
| New job | `crm` (display name: `CRM Integration Tests`) |
| Triggers (inherited) | `push` to `main` (paths: `apps/sanad-platform/**`, `ci.yml`); `pull_request` to `main`; `workflow_dispatch` |
| Permissions (workflow-level, unchanged) | `contents: read` |
| Runner | `ubuntu-latest`, `timeout-minutes: 20` |

## 1. Job `crm` — steps

1. `actions/checkout@v4`
2. Verify Docker availability (for Testcontainers) — mirrors `test` job
3. `actions/setup-java@v4` — JDK 21 temurin, `cache: maven`
4. Run CRM integration tests:
   ```
   mvn test -B -ntp -Dsurefire.useFile=true -DfailIfNoTests=true \
     -Dtest='com.sanad.platform.crm.**.*IntegrationTest'
   ```
5. Upload `crm-surefire-reports` (always)
6. Upload `crm-testcontainers-logs` (on failure)
7. CRM test summary → `$GITHUB_STEP_SUMMARY`

## 2. Status-check mapping

The directive requires the `crm` job to be a **required status check** on `main`.
- ✅ Job declared and named (`crm` / `CRM Integration Tests`).
- ⏸ **Registering it as required in branch protection** is a GitHub repository
  setting that cannot be committed from a workflow change; it is a post-merge
  governance action by the Platform CI squad / repo admin. Tracked as a
  follow-up in `CRM-G5-PROGRESS-UPDATE.md` and in CRM-022-VALIDATION-REPORT.

## 3. Convention adherence

| Convention | Source | CRM-022 compliance |
|------------|--------|--------------------|
| `permissions: contents: read` | `ci.yml`, `web-ci.yml`, Constitution | ✅ inherited, unchanged |
| `actions/checkout@v4`, `setup-java@v4 temurin 21`, `cache: maven` | `ci.yml` `test` job | ✅ identical |
| `set -euo pipefail` shell blocks | `ci.yml` `test` job | ✅ identical |
| Surefire reports artifact + `$GITHUB_STEP_SUMMARY` | `ci.yml` `test` job | ✅ mirrored |
| `timeout-minutes` | `web-ci.yml` (15) | ✅ 20 (CRM IT + Testcontainers) |

## 4. Failure semantics

- Any CRM test failure → step 4 exits non-zero → job fails → PR check fails. ✅ satisfies spec: *"The job fails the workflow if any CRM test fails."*
- Zero matching tests → `-DfailIfNoTests=true` fails the build (drift guard).
