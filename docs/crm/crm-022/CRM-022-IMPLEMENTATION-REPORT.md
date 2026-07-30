# CRM-022 — Implementation Report

| Field | Value |
|-------|-------|
| Work item | EXEC-PROMPT-CRM-022 — Add a CRM-specific job to `ci.yml` |
| Spec | `docs/crm/CRM-ENTERPRISE-EXECUTION-ROADMAP.md` (EXEC-PROMPT-CRM-022) |
| Owner | Platform CI squad |
| Branch | `feature/crm-022-ci-job` |
| Base | `main` @ `5bb303a826d33e52a463db8d1abd930d25546795` |
| Date | 2026-07-30 |

## 1. Acceptance criteria vs. implementation

| Acceptance criterion (spec) | Status | How satisfied |
|-----------------------------|--------|---------------|
| `ci.yml` contains a named `crm` job | ✅ | Job `crm` (`name: CRM Integration Tests`) added. |
| … that runs the CRM integration test classes | ✅ | `-Dtest='com.sanad.platform.crm.**.*IntegrationTest'` → 16 classes. |
| … surfaces as a required status check on `main` | ⏸ partial | Job declared; required-check registration is a post-merge repo setting (governance follow-up). |
| The job fails the workflow if any CRM test fails | ✅ | Non-zero Maven exit ⇒ job fails. |
| The job is listed as a required check in branch protection | ⏸ | Same governance follow-up as above. |

## 2. Files modified

- `.github/workflows/ci.yml` — added `crm` job (+86 lines, additive; `test` job unchanged).

## 3. Files added (documentation)

- `docs/crm/crm-022/CRM-022-IMPLEMENTATION-REPORT.md` (this file)
- `docs/crm/crm-022/CRM-022-ARCHITECTURE-REVIEW.md`
- `docs/crm/crm-022/CRM-022-CODE-QUALITY-REPORT.md`
- `docs/crm/crm-022/CRM-022-TEST-REPORT.md`
- `docs/crm/crm-022/CRM-022-CI-REPORT.md`
- `docs/crm/crm-022/CRM-022-SECURITY-REPORT.md`
- `docs/crm/crm-022/CRM-022-VALIDATION-REPORT.md`
- `docs/crm/crm-022/CRM-G5-PROGRESS-UPDATE.md`

## 4. Design summary

The `crm` job mirrors the existing `test` job (same runner, Docker gate, JDK 21
+ Maven cache, Surefire, artifact upload, step summary) but is scoped to the
CRM integration-test package. TypeScript/ESLint/coverage were **intentionally
not duplicated** — they already run in `web-ci.yml` / `crm-web-lint-diagnostics.yml`,
and no Jacoco is configured. See CRM-022-ARCHITECTURE-REVIEW.

## 5. Validation summary

YAML valid; 16/16 CRM classes matched; diff is additive only; no security or
permission regression. Live Testcontainers run happens in CI (Docker not
available on the workstation). See CRM-022-VALIDATION-REPORT.

## 6. Risks & observations

- **Stale "four" count:** spec says four classes; repo has sixteen. Resolved by package-scoped selection.
- **Required-check registration:** post-merge governance step (not a code change).
- **actionlint:** not installed locally; recommend adding to a governance workflow later.
- **Local Docker/JDK21:** unavailable on workstation; CI provides both.
