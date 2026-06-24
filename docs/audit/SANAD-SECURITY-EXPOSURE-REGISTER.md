# SANAD Security Exposure Register
## EXEC-PROMPT-010

---

## Finding SEC-001: EXPOSED TEMPORARY CREDENTIAL

| Field | Value |
|-------|-------|
| Severity | CRITICAL |
| Status | VERIFIED |
| Category | Credential Exposure |
| Description | Temporary admin password was set via direct database mutation during recovery workflow execution. The password appeared in workflow logs (masked but identifiable via context). |
| Affected Files | (deleted) admin-password-direct-reset.yml, verify-admin-login.yml |
| Affected Workflow Runs | 28125798745, 28126201666 |
| Credential Type | Admin login password |
| Rotation Status | **ROTATED** |
| Required Action | Password rotated via API, SANAD_ADMIN_PASSWORD secret updated, SANAD_ADMIN_RECOVERY_PASSWORD deleted |
| Residual Risk | Password known to anyone with access to GitHub Actions logs in this repository |

---

## Finding SEC-002–005: Missing Least-Privilege Permissions

| Field | Value |
|-------|-------|
| Severity | HIGH |
| Status | FIXED |
| Files | ci.yml, render-env-recovery.yml, smoke-test.yml, uptime-monitor.yml |
| Fix | Added `permissions: contents: read` via PR #81 |
| Validation | Workflows now run with minimal permissions |

---

## Finding SEC-006: Build Artifacts in Git Tree

| Field | Value |
|-------|-------|
| Severity | MEDIUM |
| Status | FIXED |
| Description | apps/sanad-platform/target/ and apps/web/.next/ contained test tokens and build keys flagged by gitleaks |
| Fix | Added to .gitignore via PR #81 |
| History Findings | 56 gitleaks findings in target/.next (build artifacts only); 6 history findings (all false positives: test JWTs, commit SHAs in docs) |
| History Rewrite | NOT REQUIRED |

---

## Finding SEC-007: Direct Database Mutations from GitHub Actions

| Field | Value |
|-------|-------|
| Severity | CRITICAL |
| Status | FIXED |
| Description | Recovery workflows performed direct password_hash updates, session_version manipulation, and refresh_token deletion from GitHub Actions |
| Fix | All three recovery workflows deleted from tree (commit 61559ce) |
| Residual Risk | Historical workflow runs still exist (read-only); no active code paths perform these operations |

---

## Finding SEC-008: Bootstrap Safety

| Field | Value |
|-------|-------|
| Severity | HIGH |
| Status | VERIFIED |
| Description | BOOTSTRAP_ENABLED must be false in production |
| Verification | Render env vars cleaned (HTTP 200), backend health UP, BOOTSTRAP_ENABLED=false, BOOTSTRAP_FORCE_RESET=false, BOOTSTRAP_ADMIN_PASSWORD=empty |
