# CRM-030 ARCHITECTURE REVIEW

## Date: 2026-07-31
## Ticket: CRM-030 — Verify CRM workflows as required status checks

---

## Scope

CRM-030 is a CI/CD governance task that verifies CRM workflows are
configured as required status checks on `main` and documents the
branch protection configuration.

---

## Current State

### Required Status Checks on `main`

| # | Check Name | Source Workflow | CRM-030 Required |
|---|------------|-----------------|------------------|
| 1 | Build Next.js Web | web-ci.yml | ❌ No |
| 2 | provenance | provenance.yml | ❌ No |
| 3 | CRM Integration Tests | ci.yml | ❌ No |
| 4 | Maven Test Suite | ci.yml | ❌ No |
| 5 | CRM Deployment Readiness | crm-deployment-readiness.yml | ✅ Yes |
| 6 | Post-Merge Verification | post-merge.yml | ❌ No |
| 7 | Verify 8 tables, 26 indexes, and tenant isolation | crm-g1-schema-isolation.yml | ❌ No |
| 8 | CRM Real API Smoke | crm-real-smoke.yml | ✅ YES — MISSING |
| 9 | CRM Web Lint Diagnostics | crm-web-lint-diagnostics.yml | ✅ YES — MISSING |
| 10 | CI / crm | ci.yml (crm job) | ✅ YES — MISSING |

### Branch Protection Configuration

- **Strict mode:** Enabled (required checks must pass before merge)
- **Admin enforcement:** Enabled (admins cannot bypass checks)
- **Evidence file:** MISSING (`evidence/branch-protection-crm.json`)

---

## Architecture Impact

- **No code changes:** Only GitHub API calls and file creation
- **No database changes:** No migrations involved
- **No API changes:** No endpoint modifications
- **CI/CD impact:** Additional required checks may block PRs until workflows pass

---

## Review Conclusion

✅ **Architecture review passed** — CRM-030 is a low-risk CI/CD governance task
with clear acceptance criteria and no code dependencies.
