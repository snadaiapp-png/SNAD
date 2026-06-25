# SANAD Security Exposure Register
## EXEC-PROMPT-010 — Updated 2026-06-25

---

## Finding SEC-001: EXPOSED TEMPORARY CREDENTIAL (First Password)

| Field | Value |
|-------|-------|
| Severity | CRITICAL |
| Status | VERIFIED — ROTATED |
| Category | Credential Exposure |
| Description | Temporary admin password `Sanad@2026!Temp` was set via direct database mutation during `verify-admin-login.yml` workflow execution. The password appeared in workflow logs. |
| Affected Files | (deleted) `.github/workflows/verify-admin-login.yml`, `.github/workflows/admin-password-direct-reset.yml` |
| Affected Workflow Runs | 28125798745, 28126201666 |
| Affected Commits | 5b1ebe7 (added), 61559ce (removed) |
| Credential Type | Admin login password |
| Rotation Status | **ROTATED** |
| Required Action | Password rotated via API, SANAD_ADMIN_PASSWORD GitHub secret updated, SANAD_ADMIN_RECOVERY_PASSWORD deleted |
| Residual Risk | Password known to anyone with access to GitHub Actions logs in this repository (historical runs are read-only) |
| History Rewrite Required | No — password rotated, historical runs are immutable artifacts |

---

## Finding SEC-006: EXPOSED TEMPORARY CREDENTIAL (Second Password)

| Field | Value |
|-------|-------|
| Severity | CRITICAL |
| Status | VERIFIED — ROTATED |
| Category | Credential Exposure |
| Description | Second temporary admin password `Snad2026ProdSec` was set via direct database mutation during `set-admin-password.yml` workflow execution (post-audit recovery attempt). The password appeared in workflow source code and was committed to git history. |
| Affected Files | (deleted) `.github/workflows/set-admin-password.yml` |
| Affected Workflow Runs | Runs triggered from commit f766e42 |
| Affected Commits | d6c6e1f (first version added), f766e42 (second version added), 6dfd05e (final deletion) |
| Credential Type | Admin login password |
| Rotation Status | **ROTATED** |
| Required Action | Password rotated, workflow deleted from current tree, file not in working directory |
| Residual Risk | Password exists in git history (commits d6c6e1f and f766e42). History rewrite NOT required because password has been rotated and is no longer valid. |
| History Rewrite Required | No — credential rotated, history is immutable evidence |

---

## Finding SEC-002–005: Missing Least-Privilege Permissions

| Field | Value |
|-------|-------|
| Severity | HIGH |
| Status | FIXED |
| Files | ci.yml, render-env-recovery.yml, smoke-test.yml, uptime-monitor.yml |
| Fix | Added `permissions: contents: read` via PR #81 |
| Validation | All 24 active workflows verified to have `permissions: contents: read` (2026-06-25) |

---

## Finding SEC-007: Build Artifacts in Git Tree

| Field | Value |
|-------|-------|
| Severity | MEDIUM |
| Status | FIXED |
| Description | `apps/sanad-platform/target/` and `apps/web/.next/` contained test tokens and build keys flagged by gitleaks |
| Fix | Added to `.gitignore` via PR #81 |
| History Findings | 56 gitleaks findings in target/.next (build artifacts only); 6 history findings (all false positives: test JWTs, commit SHAs in docs) |
| History Rewrite | NOT REQUIRED |

---

## Finding SEC-008: Direct Database Mutations from GitHub Actions

| Field | Value |
|-------|-------|
| Severity | CRITICAL |
| Status | FIXED |
| Description | Recovery workflows performed direct `password_hash` updates, `session_version` manipulation, and `refresh_tokens` deletion from GitHub Actions via psycopg2 |
| Affected Workflows | verify-admin-login.yml, admin-password-direct-reset.yml, admin-credential-recovery.yml, set-admin-password.yml (all deleted) |
| Fix | All 4 workflows deleted from current tree (commits 61559ce, 6dfd05e) |
| Residual Risk | Historical workflow runs still exist (read-only); no active code paths perform these operations |

---

## Finding SEC-009: Bootstrap Safety

| Field | Value |
|-------|-------|
| Severity | HIGH |
| Status | VERIFIED |
| Description | BOOTSTRAP_ENABLED must be false in production |
| Verification | Render env vars cleaned (HTTP 200), backend health UP, BOOTSTRAP_ENABLED=false, BOOTSTRAP_FORCE_RESET=false, BOOTSTRAP_ADMIN_PASSWORD=empty |

---

## Finding SEC-010: Missing Branch Protection on main

| Field | Value |
|-------|-------|
| Severity | HIGH |
| Status | OWNER ACTION REQUIRED |
| Description | The `main` branch has no branch protection rules. API returns 404 on the protection endpoint. |
| Risk | Direct pushes to main are possible. Unreviewed code could reach production. Force-push is not blocked. |
| Required Action | Enable branch protection: require PR, require approvals (min 1), require status checks (CI, Web CI, Security Baseline), block force-push, block deletion |
| Owner | snadaiapp-png |

---

## Gitleaks Scan Summary (2026-06-25)

### Current Tree Scan

| # | Rule | File | Line | Secret (truncated) | Verdict |
|---|------|------|------|-------------------|---------|
| 1 | generic-api-key | .github/workflows/set-admin-password.yml | 51 | Snad2026ProdSec | REAL (file deleted, password rotated — SEC-006) |
| 2 | generic-api-key | apps/web/lib/api/auth-flow.test.ts | 72 | eyJhbGciOiJIUzI1NiJ9... | FALSE POSITIVE — test JWT token with explicit "not-a-jwt" comment |
| 3 | generic-api-key | apps/web/lib/api/auth-flow.test.ts | 72 | eyJhbGciOiJIUzI1NiJ9... | FALSE POSITIVE — duplicate of #2 from different commit |
| 4 | generic-api-key | docs/runbooks/backend-auth-rollback.md | 9 | eabaf127deff75a2ba59... | FALSE POSITIVE — commit SHA fragment in documentation |
| 5 | generic-api-key | docs/runbooks/backend-auth-rollback.md | 10 | 083511210c21c7a93e78... | FALSE POSITIVE — commit SHA fragment in documentation |
| 6 | generic-api-key | docs/execution/EXEC-PROMPT-029...md | 54 | EXEC-PROMPT-032 | FALSE POSITIVE — execution identifier string |
| 7 | generic-api-key | docs/execution/EXEC-PROMPT-029...md | 54 | EXEC-PROMPT-032 | FALSE POSITIVE — duplicate of #6 from different commit |

### History Scan

Same 7 findings, all from historical commits. No new findings in history beyond what's already documented.

### Conclusion

**0 real secrets in current tree.** The only real finding (SEC-006) is in a deleted file that exists only in git history. The password has been rotated and is no longer valid.
