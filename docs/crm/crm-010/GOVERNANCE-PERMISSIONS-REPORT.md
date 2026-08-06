# Governance Permissions Report

**Date:** 2026-07-29
**Repository:** snadaiapp-png/SNAD
**Agent:** Repository Governance Verification Agent
**Classification:** READ-ONLY — No modifications performed

---

## 1. Repository Ownership

| Field | Value |
|-------|-------|
| Owner | `snadaiapp-png` |
| Default Branch | `main` |
| Type | OWNER (organization/personal) |

---

## 2. Collaborators

| Login | Role | Admin | Maintain | Push | Pull | Triage |
|-------|------|-------|----------|------|------|--------|
| snadaiapp-png | admin | ✅ | ✅ | ✅ | ✅ | ✅ |
| abdulrhmansenan1985-creator | write | ❌ | ❌ | ✅ | ✅ | ✅ |

---

## 3. CODEOWNERS

**No CODEOWNERS file exists.** Checked:
- `.github/CODEOWNERS` — not found
- `CODEOWNERS` (root) — not found

---

## 4. Authenticated User Capabilities

**Current user:** `snadaiapp-png` (repository owner)

| Capability | Granted | Evidence |
|------------|---------|----------|
| Approve PRs | YES | `permissions.admin: true` — owner has all permissions |
| Merge PRs | YES | `permissions.admin: true` — owner can merge any PR |
| Override branch protection | YES | `permissions.admin: true` + `enforce_admins: false` |
| Edit Issue #705 | YES | Owner can edit any issue in the repository |
| Force push | YES | `permissions.admin: true` |
| Delete branches | YES | `permissions.admin: true` |

---

## 5. Final Authorization Matrix

| Permission | Granted | Evidence |
|------------|---------|----------|
| Can update Issue #705 | YES | Owner has admin permissions; Issue #705 is in the same repository |
| Can approve PR #818 | YES | Admin permissions allow self-approval |
| Can merge PR #818 | YES | Admin permissions; branch protection does not block admins (`enforce_admins: false`) |
| Can bypass protection | YES | Admin enforcement is disabled; admins can bypass all branch protection rules |
| Governance authorization complete | **GOVERNANCE BYPASSED** | Issue #705 body still contains `MERGE: PROHIBITED` at time of merge; PR was merged without updating the issue |

---

## 6. Governance Assessment

The authenticated user (`snadaiapp-png`) is the repository owner with full admin permissions. This user has every capability required to:
- Update Issue #705 to change `MERGE: PROHIBITED` to `MERGE: AUTHORIZED`
- Approve PR #818
- Merge PR #818 (including bypassing branch protection)
- Delete the feature branch after merge

**The merge was technically permitted** — the owner has sufficient permissions. However, the governance process was not followed: Issue #705 was not updated before merge.

---

**Report Authority:** Repository Governance Verification Agent
**Date:** 2026-07-29
**Evidence Source:** GitHub API (gh api/gh pr/gh issue)
