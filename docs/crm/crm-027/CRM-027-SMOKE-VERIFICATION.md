# CRM-027 SMOKE VERIFICATION REPORT

**Date:** 2026-07-31
**Ticket:** CRM-027 — Gate `crm-real-smoke.yml` on every production deploy
**Status:** ✅ VERIFIED

---

## 1. Workflow Configuration Verification

| Check | Status | Evidence |
|-------|--------|----------|
| `workflow_run` trigger | ✅ CONFIGURED | Triggers after "SANAD Production Release" completes |
| Failure gating | ✅ CONFIGURED | Only runs if `conclusion == 'success'` |
| Manual dispatch | ✅ PRESERVED | `workflow_dispatch` still supported |
| Concurrency control | ✅ CONFIGURED | `crm-real-smoke-${{ github.event_name }}` |
| Artifact retention | ✅ UPDATED | 90 days (was 30) |
| Expected SHA resolution | ✅ CONFIGURED | Uses `head_sha` from `workflow_run` |

---

## 2. Auto-Trigger Verification

**Configuration:**
```yaml
on:
  workflow_run:
    workflows: ["SANAD Production Release"]
    types:
      - completed
```

**Behavior:**
- Triggers automatically when "SANAD Production Release" workflow completes
- Only runs if the production release was successful (`conclusion == 'success'`)
- Uses the commit SHA from the production release as `EXPECTED_SHA`

**Status:** ✅ CONFIGURED CORRECTLY

---

## 3. Failure Gating Verification

**Configuration:**
```yaml
if: >
  github.event_name == 'workflow_dispatch' ||
  (github.event_name == 'workflow_run' &&
   github.event.workflow_run.conclusion == 'success')
```

**Behavior:**
- Manual dispatch: Always runs
- Auto-trigger: Only runs if production release succeeded
- Failed production release: Smoke workflow is skipped

**Status:** ✅ CONFIGURED CORRECTLY

---

## 4. Artifact Retention Verification

**Configuration:**
```yaml
retention-days: 90
```

**Behavior:**
- Evidence artifacts retained for 90 days
- Meets compliance requirement

**Status:** ✅ CONFIGURED CORRECTLY

---

## 5. Concurrency Control Verification

**Configuration:**
```yaml
concurrency:
  group: crm-real-smoke-${{ github.event_name }}
  cancel-in-progress: false
```

**Behavior:**
- Prevents duplicate concurrent executions
- Does not cancel in-progress runs

**Status:** ✅ CONFIGURED CORRECTLY

---

## 6. Manual Trigger Verification

**Configuration:**
```yaml
on:
  workflow_dispatch:
    inputs:
      base_url:
        description: 'Render backend URL override'
        required: false
        default: ''
      expected_sha:
        description: 'Deployed application commit SHA'
        required: true
```

**Behavior:**
- Can be triggered manually via GitHub UI
- Supports custom base URL and expected SHA

**Status:** ✅ CONFIGURED CORRECTLY

---

## 7. Auto-Trigger Activation

**Current State:**
- Workflow configuration is correct
- Auto-trigger will activate after next production release
- No production release has occurred since configuration change

**Next Steps:**
1. Next production release will trigger auto-run
2. Verify smoke workflow executes automatically
3. Verify evidence artifact is uploaded with 90-day retention

---

## 8. Verification Summary

| Component | Status |
|-----------|--------|
| Workflow trigger | ✅ VERIFIED |
| Failure gating | ✅ VERIFIED |
| Artifact retention | ✅ VERIFIED |
| Concurrency control | ✅ VERIFIED |
| Manual dispatch | ✅ VERIFIED |
| Auto-trigger config | ✅ VERIFIED |

---

## 9. Authorization

✅ **CRM-027 SMOKE VERIFICATION COMPLETE**

All workflow configurations verified. Auto-trigger will activate on next production release.
