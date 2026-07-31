# CRM-027 ARCHITECTURE REVIEW

**Date:** 2026-07-31
**Ticket:** CRM-027 — Gate `crm-real-smoke.yml` on every production deploy
**Status:** ✅ ARCHITECTURE READY

---

## 1. Current Workflow Architecture

### 1.1 Production Release Workflow

| Component | Status | Location |
|-----------|--------|----------|
| `production-release.yml` | ✅ EXISTS | `.github/workflows/production-release.yml` |
| Trigger | ✅ `workflow_dispatch` | Manual trigger with inputs |
| Deploy | ✅ Render backend | Uses `RENDER_API_KEY` |
| Verify | ✅ Health checks | Backend health verification |

### 1.2 CRM Real Smoke Workflow

| Component | Status | Location |
|-----------|--------|----------|
| `crm-real-smoke.yml` | ✅ EXISTS | `.github/workflows/crm-real-smoke.yml` |
| Trigger | ⚠️ `workflow_dispatch` only | Needs auto-trigger |
| Smoke test | ✅ Two-tenant API smoke | `scripts/crm/real-crm-smoke.sh` |
| Evidence | ✅ JSON schema validation | `crm-smoke-evidence.json` |
| Artifact | ✅ Upload with retention | 30 days (needs 90 days) |

### 1.3 Related Workflows

| Workflow | Purpose | Status |
|----------|---------|--------|
| `smoke-test.yml` | General production smoke | ✅ Exists |
| `backend-production-smoke.yml` | Backend-specific smoke | ✅ Exists |
| `crm-deployment-readiness.yml` | CRM deployment readiness | ✅ Exists |

---

## 2. Integration Points Required

### 2.1 Auto-Trigger from Production Release

**Current:** `crm-real-smoke.yml` requires manual `workflow_dispatch`
**Required:** Auto-trigger after successful `production-release.yml` run

**Implementation Options:**

| Option | Approach | Pros | Cons |
|--------|----------|------|------|
| A | `workflow_run` trigger | Native GitHub Actions | Limited control |
| B | `repository_dispatch` event | Full control | Requires API call |
| C | Job dependency in release workflow | Simplest |耦合 workflows |

**Recommendation:** Option A (`workflow_run` trigger) — native, simple, maintainable

### 2.2 Failure Gating

**Current:** `crm-real-smoke.yml` runs independently
**Required:** Smoke workflow fails the release if any check returns `FAIL`

**Implementation:**
- Add `workflow_run` trigger to `crm-real-smoke.yml`
- Check `github.event.workflow_run.conclusion == 'success'`
- Fail workflow if smoke checks fail
- Use `continue-on-error: false` to block subsequent steps

### 2.3 Evidence Artifact Retention

**Current:** `retention-days: 30`
**Required:** `retention-days: 90`

**Implementation:**
- Update `actions/upload-artifact@v4` step
- Change `retention-days: 30` to `retention-days: 90`

---

## 3. Required Status Checks

| Check | Current | Required |
|-------|---------|----------|
| Production release succeeds | ✅ | ✅ |
| CRM smoke passes | ⚠️ Manual | ✅ Auto-gated |
| Evidence artifact uploaded | ✅ | ✅ |
| Evidence schema valid | ✅ | ✅ |

---

## 4. Architecture Decision

**Approach:** `workflow_run` trigger with failure gating

```yaml
on:
  workflow_run:
    workflows: ["SANAD Production Release"]
    types:
      - completed
```

**Rationale:**
- Native GitHub Actions feature
- No external API calls required
- Simple to implement and maintain
- Follows existing workflow patterns

---

## 5. Authorization

✅ **CRM-027 ARCHITECTURE REVIEW PASSED**

All integration points identified. Implementation may proceed.
