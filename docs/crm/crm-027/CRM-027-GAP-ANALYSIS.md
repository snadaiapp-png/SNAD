# CRM-027 GAP ANALYSIS

**Date:** 2026-07-31
**Ticket:** CRM-027 — Gate `crm-real-smoke.yml` on every production deploy

---

## 1. Gap Summary

| Gap | Type | Impact | Priority |
|-----|------|--------|----------|
| Missing workflow trigger | Workflow | HIGH | P0 |
| Missing failure gating | Workflow | HIGH | P0 |
| Missing artifact retention | Workflow | MEDIUM | P1 |
| Missing production verification | Verification | MEDIUM | P1 |

---

## 2. Detailed Gap Analysis

### 2.1 Missing Workflow Trigger

**Current State:**
- `crm-real-smoke.yml` only triggers via `workflow_dispatch`
- Requires manual intervention after every production deploy

**Required State:**
- Auto-trigger after successful `production-release.yml` run
- No manual intervention required

**Gap:**
- `crm-real-smoke.yml` lacks `workflow_run` trigger
- No automation to chain workflows

**Impact:** HIGH — Manual process creates risk of missed smoke runs

### 2.2 Missing Failure Gating

**Current State:**
- `crm-real-smoke.yml` runs independently
- Smoke failures don't block anything

**Required State:**
- Smoke workflow fails the release if any check returns `FAIL`
- Failed smoke should trigger rollback

**Gap:**
- No integration between smoke results and release status
- No rollback trigger on smoke failure

**Impact:** HIGH — Failed smokes can go undetected

### 2.3 Missing Artifact Retention

**Current State:**
- `retention-days: 30`

**Required State:**
- `retention-days: 90`

**Gap:**
- Evidence artifacts deleted after 30 days
- Compliance requirement is 90 days

**Impact:** MEDIUM — Non-compliance with retention policy

### 2.4 Missing Production Verification

**Current State:**
- `crm-real-smoke.yml` validates evidence schema
- No verification that production deployment is healthy

**Required State:**
- Verify production deployment is healthy before smoke
- Verify smoke results match deployment state

**Gap:**
- No health check before smoke run
- No correlation between deployment and smoke results

**Impact:** MEDIUM — Smoke may run against unhealthy deployment

---

## 3. Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Missed smoke runs | High | High | Implement auto-trigger |
| Failed smokes undetected | High | High | Implement failure gating |
| Non-compliance with retention | Medium | Medium | Update retention days |
| Smoke against unhealthy deploy | Low | Medium | Add health check |

---

## 4. Authorization

✅ **CRM-027 GAP ANALYSIS COMPLETE**

All gaps identified. Ready for execution plan.
