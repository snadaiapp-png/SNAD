# CRM-027 EXECUTION PLAN

**Date:** 2026-07-31
**Ticket:** CRM-027 — Gate `crm-real-smoke.yml` on every production deploy
**Status:** READY TO IMPLEMENT

---

## 1. Implementation Tasks

### Task 1: Add `workflow_run` trigger to `crm-real-smoke.yml`

**File:** `.github/workflows/crm-real-smoke.yml`

**Changes:**
```yaml
on:
  workflow_dispatch:
    inputs:
      base_url:
        description: 'Render backend URL override (optional; production Render URL is the default)'
        required: false
        default: ''
      expected_sha:
        description: 'Deployed application commit SHA'
        required: true
  workflow_run:
    workflows: ["SANAD Production Release"]
    types:
      - completed
```

**Rationale:** Auto-trigger after successful production release

### Task 2: Add failure gating logic

**File:** `.github/workflows/crm-real-smoke.yml`

**Changes:**
- Check `github.event.workflow_run.conclusion == 'success'`
- Skip smoke if production release failed
- Fail workflow if smoke checks fail

**Implementation:**
```yaml
jobs:
  real-smoke:
    if: >
      github.event_name == 'workflow_dispatch' ||
      (github.event_name == 'workflow_run' &&
       github.event.workflow_run.conclusion == 'success')
    steps:
      # ... existing steps ...
```

### Task 3: Update artifact retention

**File:** `.github/workflows/crm-real-smoke.yml`

**Changes:**
```yaml
- name: Upload CRM smoke evidence
  if: always()
  uses: actions/upload-artifact@v4
  with:
    name: crm-real-smoke-evidence-${{ github.run_id }}-${{ github.run_attempt }}
    path: |
      crm-smoke-evidence.json
      crm-smoke.log
    if-no-files-found: error
    retention-days: 90  # Changed from 30 to 90
```

### Task 4: Add production verification step

**File:** `.github/workflows/crm-real-smoke.yml`

**Changes:**
- Add health check before smoke run
- Verify deployment is healthy

**Implementation:**
```yaml
- name: Verify production deployment health
  if: github.event_name == 'workflow_run'
  shell: bash
  run: |
    set -euo pipefail
    curl --fail --silent --show-error --location --max-time 30 \
      "${CRM_BASE_URL%/}/actuator/health" >/dev/null
```

---

## 2. Task Order

| # | Task | Depends On | Estimated Time |
|---|------|-----------|----------------|
| 1 | Add `workflow_run` trigger | None | 10 min |
| 2 | Add failure gating logic | Task 1 | 15 min |
| 3 | Update artifact retention | None | 5 min |
| 4 | Add production verification | Task 1 | 10 min |
| 5 | Test locally | Tasks 1-4 | 15 min |
| 6 | Push to feature branch | Task 5 | 5 min |
| 7 | Verify CI passes | Task 6 | 10 min |
| 8 | Merge to main | Task 7 | 5 min |

**Total Estimated Time:** 75 min

---

## 3. Repository Changes

| File | Change Type | Description |
|------|-------------|-------------|
| `.github/workflows/crm-real-smoke.yml` | MODIFY | Add trigger, gating, retention, verification |
| `docs/crm/crm-027/CRM-027-FINAL-CERTIFICATION.md` | CREATE | Final certification |

---

## 4. Validation Strategy

### 4.1 Local Validation

1. YAML syntax validation
2. Workflow logic review
3. Manual trigger test

### 4.2 CI Validation

1. Push to feature branch
2. Verify workflow syntax is valid
3. Verify no lint errors

### 4.3 Production Validation

1. Merge to main
2. Trigger production release
3. Verify auto-trigger works
4. Verify failure gating works

---

## 5. CI Strategy

| Check | Required | Status |
|-------|----------|--------|
| YAML syntax | ✅ | Validate with `yq` |
| Workflow logic | ✅ | Review manually |
| Auto-trigger | ✅ | Test with production release |
| Failure gating | ✅ | Test with failed release |

---

## 6. Deployment Strategy

| Step | Action | Verification |
|------|--------|--------------|
| 1 | Merge to main | CI passes |
| 2 | Trigger production release | Workflow runs |
| 3 | Verify auto-trigger | `crm-real-smoke.yml` runs |
| 4 | Verify evidence | Artifact uploaded with 90-day retention |

---

## 7. Authorization

✅ **CRM-027 EXECUTION PLAN APPROVED**

All tasks defined. Ready to proceed with implementation.
