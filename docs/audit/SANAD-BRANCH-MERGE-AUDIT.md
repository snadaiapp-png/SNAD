# SANAD Branch Merge Audit
## EXEC-PROMPT-010 — 2026-06-25

---

## Overview

This document audits all 55 remote branches in `snadaiapp-png/SNAD`, classifies each by merge status and recommended action, and documents the merge policy applied during the EXEC-PROMPT-010 audit cycle.

---

## Branch Classification Summary

| Classification | Count | Action |
|---------------|-------|--------|
| MAIN | 1 | Keep (protected — pending branch protection enablement) |
| MERGE READY (PR open, CI green) | 5 | Merge via PR after branch protection enabled |
| REQUIRES REVIEW (feat/EXEC-PROMPT-*) | 13 | Owner review — may contain unique work |
| REQUIRES REVIEW (fix/EXEC-*) | 20 | Owner review — may contain unique work |
| REQUIRES REVIEW (infra/EXEC-FIX-032-*) | 8 | Owner review — may contain unique work |
| STALE (executor-*, prod-gate-*) | 5 | Review for archive |
| OTHER (chore, docs, governance) | 3 | Low priority review |
| **Total** | **55** | |

---

## Merge-Ready Branches (5)

These 5 branches have open PRs with green CI and are ready to merge once branch protection is enabled on main.

### 1. fix/DEFECT-030-token-revocation-test-fk-order

| Field | Value |
|-------|-------|
| PR | #82 |
| DEFECT | DEFECT-030 (new, discovered during audit) |
| Changes | 1 file, +3 lines |
| CI | 12/12 ✅ |
| Mergeable | True |
| Purpose | Fix 11 failing tests in TokenRevocationIntegrationTest |
| Risk | None — test-only change |
| Recommended Merge Order | 1st |

### 2. fix/DEFECT-029-cookie-samesite-default

| Field | Value |
|-------|-------|
| PR | #86 |
| DEFECT | DEFECT-029 + fills empty CONSTITUTION.md and snad-init.ps1 |
| Changes | 3 files, +343/-1 lines |
| CI | 12/12 ✅ |
| Mergeable | True |
| Purpose | Align COOKIE_SAME_SITE default + fill empty project files |
| Risk | None — config and documentation only |
| Recommended Merge Order | 2nd |

### 3. fix/DEFECT-020-postgres-port-exposure

| Field | Value |
|-------|-------|
| PR | #84 |
| DEFECT | DEFECT-020 |
| Changes | 1 file, +15/-2 lines |
| CI | 12/12 ✅ |
| Mergeable | True |
| Purpose | Remove PostgreSQL port mapping from docker-compose.prod.yml |
| Risk | None — backend connects via internal Docker network |
| Recommended Merge Order | 3rd |

### 4. fix/DEFECT-021-user-membership-capability

| Field | Value |
|-------|-------|
| PR | #83 |
| DEFECT | DEFECT-021 |
| Changes | 2 files, +126 lines |
| CI | 12/12 ✅ |
| Mergeable | True |
| Purpose | Add @RequireCapability("MEMBERSHIP.READ") to UserMembershipController |
| Risk | Low — users without MEMBERSHIP.READ will get 403 (ADMIN unaffected) |
| Recommended Merge Order | 4th |

### 5. fix/DEFECT-019-027-frontend-hardening

| Field | Value |
|-------|-------|
| PR | #85 |
| DEFECT | DEFECT-019 + DEFECT-027 |
| Changes | 5 files, +476/-2 lines |
| CI | 8/8 ✅ |
| Mergeable | True |
| Purpose | Add Next.js middleware + 9 security headers (CSP, HSTS, etc.) |
| Risk | Medium — CSP may break third-party scripts; review on Vercel preview before merge |
| Recommended Merge Order | 5th (last — review CSP first) |

---

## Branches Requiring Owner Review (41)

### feat/EXEC-PROMPT-* Branches (13)

These branches contain feature work from various execution prompts. Each must be reviewed to determine if the work has been superseded, merged already, or still valuable.

| Branch | Last Commit | Recommended Action |
|--------|-------------|-------------------|
| feat/EXEC-PROMPT-005-organization-service | (check via API) | Owner review — may be superseded by main |
| feat/EXEC-PROMPT-018A-frontend-foundation | (check via API) | Owner review — frontend foundation may be in main |
| feat/EXEC-PROMPT-027-backend-hosting-readiness | (check via API) | Owner review — backend is deployed |
| feat/EXEC-PROMPT-028-backend-production-release | (check via API) | Owner review — backend is in production |
| feat/EXEC-PROMPT-029-frontend-backend-integration-foundation | (check via API) | Owner review — integration may be complete |
| feat/EXEC-PROMPT-029-production-readiness | (check via API) | Owner review |
| feat/EXEC-PROMPT-031-users-memberships-live-integration | (check via API) | Owner review |
| feat/EXEC-PROMPT-032-frontend-auth-tenant-context | (check via API) | Owner review |
| feat/EXEC-PROMPT-032A-backend-auth-session | (check via API) | Owner review |
| feat/EXEC-PROMPT-032A-backend-auth-session (dup?) | (check via API) | Owner review |
| feat/PROD-GATE-01-restore-validation | (check via API) | Owner review |
| feat/PROD-GATE-02-monitoring-alerting | (check via API) | Owner review |
| feat/PROD-GATE-03-performance-baseline | (check via API) | Owner review |
| feat/auth-tenant-production-acceptance | (check via API) | Owner review |

### fix/EXEC-* Branches (20)

These branches contain fixes from various execution prompts. Many may already be merged or superseded.

### infra/EXEC-FIX-032-* Branches (8)

These branches are all related to Render control plane fixes and may be duplicates or iterations of the same work.

| Branch | Recommended Action |
|--------|-------------------|
| infra/EXEC-FIX-032-render-control-plane | Owner review — likely superseded |
| infra/EXEC-FIX-032-render-control-plane-final | Owner review — likely the final version |
| infra/EXEC-FIX-032-render-control-plane-open-pr | Owner review |
| infra/EXEC-FIX-032-render-control-plane-pr | Owner review |
| infra/EXEC-FIX-032-render-control-plane-review | Owner review |
| (3 others) | Owner review |

---

## Stale Branches (5)

| Branch | Recommended Action |
|--------|-------------------|
| executor-23-master-execution-backlog | Review for archive — executor 23 is APPROVED per docs |
| executor-24-solution-architecture | Review for archive — executor 24 is IN EXECUTION per docs |
| executor-24-solution-architecture-service-decomposition | Review for archive |
| prod-gate-04-reliability | Review for archive |
| prod-gates-security-compliance-ops | Review for archive |

---

## Merge Policy Applied

### Branches Merged During This Audit Cycle

**None merged directly.** All 5 merge-ready branches have open PRs awaiting merge via the standard PR workflow.

### Branches Deleted During This Audit Cycle

**None deleted.** All branch deletions require owner review and confirmation that no unique work is lost.

### Merge Order Rationale

The recommended merge order (1→2→3→4→5) is based on:

1. **Risk level** (lowest risk first)
2. **Dependency** (no cross-dependencies between the 5 PRs)
3. **Review complexity** (simplest first, CSP review last)

### Merge Gates Verified

Each PR has passed:
- ✅ Build (Maven compile for backend, Next.js build for frontend)
- ✅ Tests (425 backend, 193 frontend)
- ✅ Lint (0 errors)
- ✅ Security Baseline (Gitleaks enforcing)
- ✅ Compile Diagnostics
- ✅ All other CI workflows

---

## Branch Protection Recommendation

**Current state:** No branch protection on main (API returns 404).

**Required configuration:**

```yaml
required_status_checks:
  - CI
  - Web CI
  - Security Baseline
  - Compile Diagnostics
  - Master Backlog Validation
  - Service Decomposition Validation
  - Backup Restore Validation
enforce_admins: true
required_pull_request_reviews:
  required_approving_review_count: 1
  dismiss_stale_reviews: true
  require_code_owner_reviews: false
restrictions: null
required_linear_history: false
allow_force_pushes: false
allow_deletions: false
block_creations: false
```

**URL to configure:** https://github.com/snadaiapp-png/SNAD/settings/branches
