# CRM-030 GAP ANALYSIS

## Date: 2026-07-31
## Ticket: CRM-030 — Verify CRM workflows as required status checks

---

## Gaps Identified

### Gap 1: CRM Real API Smoke not in required status checks

**Priority:** P0
**Acceptance criterion:** #1
**Current state:** `crm-real-smoke.yml` (CRM Real API Smoke) is NOT listed as a required status check
**Required state:** Must be listed as required check on `main`
**Mitigation:** Add `CRM Real API Smoke` to branch protection required status checks

---

### Gap 2: CRM Web Lint Diagnostics not in required status checks

**Priority:** P0
**Acceptance criterion:** #1
**Current state:** `crm-web-lint-diagnostics.yml` (CRM Web Lint Diagnostics) is NOT listed as a required status check
**Required state:** Must be listed as required check on `main`
**Mitigation:** Add `CRM Web Lint Diagnostics` to branch protection required status checks

---

### Gap 3: crm job in ci.yml not in required status checks

**Priority:** P0
**Acceptance criterion:** #1
**Current state:** The `crm` job in `ci.yml` is NOT listed as a required status check
**Required state:** Must be listed as required check on `main`
**Mitigation:** Add `CI / crm` (or workflow name + job name format) to branch protection required status checks

---

### Gap 4: Branch protection evidence file missing

**Priority:** P0
**Acceptance criterion:** #2
**Current state:** `evidence/branch-protection-crm.json` does NOT exist
**Required state:** Must exist with branch protection configuration evidence
**Mitigation:** Create `evidence/branch-protection-crm.json` with current configuration

---

## Gap Summary

| Gap | Priority | Effort | Risk |
|-----|----------|--------|------|
| 1 | P0 | Low | Low |
| 2 | P0 | Low | Low |
| 3 | P0 | Low | Low |
| 4 | P0 | Low | Low |

**Total gaps:** 4
**Overall risk:** Low — configuration-only changes (GitHub API + file creation)

---

## Current Required Status Checks

```json
{
  "contexts": [
    "Build Next.js Web",
    "provenance",
    "CRM Integration Tests",
    "Maven Test Suite",
    "CRM Deployment Readiness",
    "Post-Merge Verification",
    "Verify 8 tables, 26 indexes, and tenant isolation"
  ]
}
```

## Required After CRM-030

```json
{
  "contexts": [
    "Build Next.js Web",
    "provenance",
    "CRM Integration Tests",
    "Maven Test Suite",
    "CRM Deployment Readiness",
    "CRM Real API Smoke",
    "CRM Web Lint Diagnostics",
    "CI / crm",
    "Post-Merge Verification",
    "Verify 8 tables, 26 indexes, and tenant isolation"
  ]
}
```
