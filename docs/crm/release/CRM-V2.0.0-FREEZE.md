# CRM v2.0.0 — Version Freeze Certificate

| Field | Value |
|-------|-------|
| Freeze Date | 2026-07-30 |
| Release Version | **crm-v2.0.0** |
| Release SHA | `8c2950bcbca922c8fa34b314696234fdf7bf79cb` |
| Release Tag | `crm-v2.0.0` |
| Freeze Authority | Release Baseline Authority |

---

## 1. Freeze Declaration

Effective **2026-07-30**, CRM version **v2.0.0** is hereby frozen as the
official production baseline of the SNAD CRM platform.

**This release is the designated rollback point** for all future G5+ work.

---

## 2. What This Freeze Means

### 2.1 Immutable History

The `crm-v2.0.0` tag and its associated commit `8c2950bc` are frozen:

- ✅ No rewrite of release history
- ✅ No rebasing past this commit
- ✅ No amending of release commits
- ✅ No force-push to `crm-v2.0.0` tag
- ✅ All G5+ work builds on top of this baseline

### 2.2 Rollback Guarantee

This baseline serves as the definitive rollback point:

```bash
# Frontend rollback (Vercel)
vercel rollback --prod

# Git rollback
git checkout crm-v2.0.0

# Database rollback (RLS)
# Apply V20260730_2 or set SNAD_RLS_ENABLED=false
```

### 2.3 Branching Policy

From this point forward:

- **`main`** continues to receive G5+ feature work
- **`crm-v2.0.0` tag** remains pinned to the freeze commit
- If hotfixes are required against v2.0.0, they must be branched from the tag
  and released as `crm-v2.0.1` or `crm-v2.0.0-hotfix-N`

---

## 3. Frozen Assets

### 3.1 Source Code

| Asset | Frozen At |
|-------|-----------|
| Repository tag | `crm-v2.0.0` |
| Branch | `main` at `8c2950bc` |
| GitHub Release | https://github.com/snadaiapp-png/SNAD/releases/tag/crm-v2.0.0 |

### 3.2 Deployed Artifacts

| Environment | URL | Frozen |
|-------------|-----|--------|
| Vercel Production | https://snad-app.vercel.app | ✅ |
| Vercel Latest Deploy | Auto-deployed from `main` | ✅ |

### 3.3 Database

| Asset | Version |
|-------|---------|
| Latest migration | V20260730_1 (RLS enable) |
| Rollback migration | V20260730_2 (RLS disable) |
| CRM tables | 62 with RLS policies |

---

## 4. Release Boundary

### 4.1 Included

| Category | Count | Details |
|----------|-------|---------|
| Prompts completed | 18 | CRM-001–020 (excluding CRM-002, CRM-008) |
| Milestones closed | 4 | G0, G2, G3, G4 |
| Work items released | 11 | CRM-010 through CRM-020 |
| API endpoints | 357 | Across all API groups |

### 4.2 Excluded (Deferred to G5+)

| Prompt | Title | Reason |
|--------|-------|--------|
| CRM-002 | Refresh stale MVP backlog | IN_PROGRESS |
| CRM-008 | Land G1 extension tables | Code on main, not closed |
| CRM-021 | Wire tasks tab | G5 — next milestone |
| CRM-022 | Add CRM CI job | G5 — ready |
| CRM-023 | Wire employees/transfers | G5 |
| CRM-024 | Reports dashboard | G6 |
| CRM-025–034 | Remaining prompts | G5–G8 |

---

## 5. Freeze Enforcement

Any attempt to alter the v2.0.0 baseline will be detected by:

1. **Tag immutability** — `git tag -l crm-v2.0.0` shows the freeze commit
2. **Drift detection** — `scripts/crm/governance-drift-check.sh` validates roadmap
3. **GitHub branch protection** — `main` requires PRs and status checks

---

## 6. Freeze Sign-off

| Role | Status | Date |
|------|--------|------|
| Release Baseline Authority | ✅ APPROVED | 2026-07-30 |

---

*Version freeze executed 2026-07-30 by Release Baseline Authority*
