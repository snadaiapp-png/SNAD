# CRM-022 — Validation Report

| Field | Value |
|-------|-------|
| Date | 2026-07-30 |
| Branch | `feature/crm-022-ci-job` |

## 1. Validation matrix

| Validation | Tool | Result | Evidence |
|------------|------|--------|----------|
| YAML structure | `python3 -c yaml.safe_load` | ✅ PASS | `jobs: ['test', 'crm']`; `crm` has 7 steps |
| actionlint | actionlint | ⚠ N/A | not installed in environment; recommendation only |
| Test-selection pattern | python enumeration | ✅ PASS | 16/16 CRM `*IntegrationTest` classes matched by `com.sanad.platform.crm.**.*IntegrationTest` |
| JUnit output support | filesystem check | ✅ PASS | `target/surefire-reports/TEST-com.sanad.platform.crm.*.xml` present |
| Coverage support | `pom.xml` grep | ❌ N/A (correctly skipped) | No `jacoco` configured → "if supported" clause = skip |
| Diff scope | `git diff` | ✅ PASS | only `ci.yml` changed; `test` job untouched; `crm` purely additive |
| Live test run (Docker) | `docker version` | ⏸ NOT RUN | Docker daemon not running on workstation; runs in CI `ubuntu-latest` |

## 2. Commands executed (this environment)

```
git checkout -b feature/crm-022-ci-job
# (edit .github/workflows/ci.yml via editor)
python3 -c "import yaml; d=yaml.safe_load(open('.github/workflows/ci.yml')); ..."
git diff -- .github/workflows/ci.yml
python3 <<'PY'  # pattern resolution check → 16 classes
git status
```

## 3. CI-side validation (pending PR)

The authoritative execution result is produced by GitHub Actions when the PR
is opened: the `CRM Integration Tests` check must report green. This will be
confirmed after `gh pr create`.

## 4. Outstanding (non-blocking) governance step

Register `crm` / `CRM Integration Tests` as a **required status check** on
`main` via GitHub branch-protection settings (repo-admin action, not a
workflow change). Owner: Platform CI squad.
