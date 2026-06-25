# SANAD Remaining Issues
## EXEC-PROMPT-010 — 2026-06-25

---

## Overview

This document lists all remaining issues after the EXEC-PROMPT-010 audit cycle. Issues are categorized by severity and ownership.

---

## Owner Actions Required

### OA-001: Enable Branch Protection on main

| Field | Value |
|-------|-------|
| Severity | HIGH |
| Owner | snadaiapp-png |
| Blocker | YES — blocks merging the 5 open PRs safely |
| Description | The `main` branch has no branch protection rules. API returns 404 on the protection endpoint. |
| Required Action | Enable: require PR, require approvals (min 1), require status checks (CI, Web CI, Security Baseline, Compile Diagnostics), require branches to be up-to-date, require conversation resolution, block force-push, block deletion |
| URL | https://github.com/snadaiapp-png/SNAD/settings/branches |

### OA-002: Merge 5 Open PRs

| Field | Value |
|-------|-------|
| Severity | HIGH |
| Owner | snadaiapp-png |
| Blocker | YES — blocks stage closure |
| Description | 5 PRs with verified fixes and green CI are awaiting merge. |
| Required Action | Merge in order: #82 → #86 → #84 → #83 → #85 (review CSP on #85 before merge) |
| URLs | https://github.com/snadaiapp-png/SNAD/pulls |

### OA-003: Review CSP Before Merging PR #85

| Field | Value |
|-------|-------|
| Severity | MEDIUM |
| Owner | snadaiapp-png |
| Blocker | NO (but recommended before merge) |
| Description | PR #85 adds a strict Content-Security-Policy. Need to verify no frontend functionality breaks. |
| Required Action | Open Vercel preview for PR #85, open Developer Tools → Console, check for CSP violations |

### OA-004: Review 5 Branches with Unique Unmerged Work

| Field | Value |
|-------|-------|
| Severity | MEDIUM |
| Owner | snadaiapp-png |
| Blocker | NO |
| Description | 5 branches have unique unmerged work that may be valuable. |
| Branches | feat/EXEC-PROMPT-005-organization-service, feat/EXEC-PROMPT-018A-frontend-foundation, feat/EXEC-PROMPT-027-backend-hosting-readiness, feat/EXEC-PROMPT-028-backend-production-release, feat/EXEC-PROMPT-029-frontend-backend-integration-foundation |
| Required Action | Review each branch's diff against main, decide merge/archive/supersede |

---

## Open Technical Issues

### TI-001: DEFECT-015 — Non-Distributed Rate Limiting

| Field | Value |
|-------|-------|
| Severity | P2 (Medium) |
| Owner | Backend team |
| Blocker | NO (single-instance deployment currently) |
| Description | Login and password reset rate limits use in-memory Caffeine cache — ineffective in multi-instance deployment |
| Required Action | Implement Redis-backed rate limiting (Bucket4j or Resilience4j) |
| Dependencies | Redis instance (requires budget approval) |
| Target Stage | Stage 2 — Production Readiness |

### TI-002: DEFECT-016 — Frontend Lint Errors

| Field | Value |
|-------|-------|
| Severity | P2 (Medium) |
| Owner | Frontend team |
| Blocker | NO |
| Description | 6 ESLint errors in auth-boundary.tsx (3x `<a>` instead of `<Link>`, 3x setState in effect body) |
| Required Action | Replace `<a>` with `<Link>`, refactor setState-in-effect to useMemo |
| Target Stage | Stage 2 |

### TI-003: DEFECT-018 — No SHA Verification in backend-deploy.yml

| Field | Value |
|-------|-------|
| Severity | P2 (Medium) |
| Owner | DevOps |
| Blocker | NO |
| Description | backend-deploy.yml deploys via hook without verifying commit SHA |
| Required Action | Add SHA verification and pinning similar to production-release.yml |
| Target Stage | Stage 2 |

### TI-004: DEFECT-023 — Rollback Never Tested

| Field | Value |
|-------|-------|
| Severity | P3 (Low) |
| Owner | DevOps |
| Blocker | NO |
| Description | Production rollback procedure documented but never executed |
| Required Action | Conduct and document a rollback test in staging |
| Dependencies | Staging environment |
| Target Stage | Stage 2 |

### TI-005: DEFECT-025 — Free-Tier Infrastructure Not Production Grade

| Field | Value |
|-------|-------|
| Severity | P3 (Low for pilot, P1 for commercial) |
| Owner | Owner + DevOps |
| Blocker | NO for pilot, YES for commercial Go-Live |
| Description | Render free tier with cold starts and connection pool max=5 |
| Required Action | Upgrade to paid tier, increase pool to 15-20, add HA |
| Dependencies | Budget approval |
| Target Stage | Stage 2 |

### TI-006: DEFECT-026 — No Structured Audit Framework

| Field | Value |
|-------|-------|
| Severity | P4 (Improvement) |
| Owner | Backend team |
| Blocker | NO |
| Description | Auth events logged via SLF4J text — no structured/tamper-proof audit trail |
| Required Action | Implement structured JSON audit logging with correlation IDs |
| Target Stage | Stage 3 |

---

## Closed Issues (Confirmed During This Audit)

| Issue | Title | Closed At | Acceptance |
|-------|-------|-----------|------------|
| #59 | Authenticated session acceptance gate | 2026-06-24T21:04:13Z | Backend tests pass (425/425), 10 tenant isolation tests, session version mechanism verified |
| #53 | Backend Auth & Session Foundation | 2026-06-24T21:04:17Z | JWT, refresh rotation, password reset, rate limiting all implemented and tested |
| #29 | Production Readiness & Go-Live | 2026-06-24T21:07:03Z | Pilot live and healthy; commercial Go-Live deferred pending budget + external audit |

---

## Issues Not Yet Created (Recommended)

### NC-001: History Rewrite Decision for SEC-006

| Field | Value |
|-------|-------|
| Severity | LOW (password already rotated) |
| Description | The password `Snad2026ProdSec` exists in git history (commits d6c6e1f, f766e42). While the password has been rotated and is no longer valid, some compliance frameworks require history rewrite. |
| Recommendation | ACCEPTED RISK — history rewrite is destructive and the password is already invalid. Document as accepted risk. |

### NC-002: Workflow Consolidation

| Field | Value |
|-------|-------|
| Severity | LOW |
| Description | Several workflows have overlapping responsibilities (e.g., `production-smoke` vs `backend-production-smoke` vs `pilot-synthetic-monitoring`). |
| Recommendation | Consolidate in Stage 2 after monitoring data is collected. |
