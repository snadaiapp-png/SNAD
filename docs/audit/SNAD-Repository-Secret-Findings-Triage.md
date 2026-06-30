# SNAD Repository Secret Findings Triage

## Purpose

Classifies repository-wide Gitleaks findings produced by local scans using Gitleaks v8.24.3 (the same version used by the repository's GitHub Actions `Current Tree Secret Scan` check). No secret values are recorded in this report. All secret fields were inspected only in redacted form.

## Scope

- PR #176 introduced no detected secrets in its two changed files. The repository-approved current-tree security check passed before merge.
- Repository-wide and Git-history findings require classification before the "No secrets found in source" exit criterion of Issue #173 can be treated as complete.
- No credential rotation was performed without owner access. No history rewrite was performed.

## Scan Configuration

| Parameter | Value |
|-----------|-------|
| Gitleaks version | v8.24.3 |
| Default config | Gitleaks built-in default rules |
| Repository config | `.gitleaks.toml` (extends default, adds path allowlist for gitignored build artifacts) |
| Current-tree mode | `--no-git` (scans working directory) |
| History mode | default (scans all 721 commits) |
| Redaction | `--redact` on every scan |

## Finding Counts

| Scan | Config | Findings |
|------|--------|---------:|
| Current tree | default | 62 |
| Current tree | repo `.gitleaks.toml` (before this triage) | 62 |
| Current tree | repo `.gitleaks.toml` (after this triage) | **0** |
| Git history | default | 8 |
| Git history | repo `.gitleaks.toml` | 8 |

The 62 current-tree findings were all in gitignored build artifacts (`apps/sanad-platform/target/` and `apps/web/.next/`). After adding a narrow path allowlist for those two build-output directories, the current-tree scan reports 0 findings.

The history scan produced 8 raw detections across 5 repository paths. Repeated detections across multiple commits were consolidated into 6 unique findings, classified below.

## Classification Table

| Finding ID | File | Line(s) | Rule | Scope | Classification | Action | Evidence |
| ---------- | ---- | ------: | ---- | ----- | -------------- | ------ | -------- |
| HF-01 | `.github/workflows/set-admin-password.yml` | 51 | generic-api-key | GIT HISTORY ONLY — file deleted from current tree | CONFIRMED_SECRET | BLOCKED — OWNER ACCESS REQUIRED: rotation status is NOT VERIFIED and old-value rejection is NOT VERIFIED. The owner must rotate the corresponding administrative credential if necessary and verify that the historical value is rejected. | The credential is absent from the current tracked tree, but no evidence currently proves rotation or rejection. Verification status: NEEDS_OWNER_VERIFICATION. |
| HF-02 | `apps/web/lib/api/auth-flow.test.ts` | 72 | generic-api-key | History (commits `28264cb9`, `d100caac`) and current tree | TEST_FIXTURE | No action required. | Value is the literal string `test-access-token-not-a-jwt`, explicitly labeled as a non-secret test token in a comment. Not a real credential format, not used to authenticate, only used to verify `Bearer` header formatting. |
| HF-03 | `docs/runbooks/backend-auth-rollback.md` | 9 | generic-api-key | History (commit `a4bedb10`) and current tree | FALSE_POSITIVE | No action required. | Gitleaks matched a backtick-quoted prose identifier in a markdown runbook. The matched text is a short reference tag, not a credential. Manual review confirmed no secret value. |
| HF-04 | `docs/runbooks/backend-auth-rollback.md` | 10 | generic-api-key | History (commit `a4bedb10`) and current tree | FALSE_POSITIVE | No action required. | Same as HF-03 — backtick-quoted prose identifier in markdown runbook. Manual review confirmed no secret value. |
| HF-05 | `docs/execution/EXEC-PROMPT-029-frontend-backend-integration-foundation.md` | 54 | generic-api-key | History (commits `8b45421c`, `15043d07`) and current tree | FALSE_POSITIVE | No action required. | Gitleaks matched the prose phrase `session-token lifecycle:` in a markdown execution log. The matched text is descriptive prose, not a credential. Manual review confirmed no secret value. |
| HF-06 | `apps/web/app/api/email-proxy/route.ts` | 18 | generic-api-key | History (commit `a6b11112`) — removed from current tree by PR #172 | FALSE_POSITIVE | No further source action required; the insecure fallback pattern was already removed by PR #172. | The matched historical value was a short non-provider-format placeholder used as an environment-variable fallback. It did not match the Resend credential format and is absent from the current tree. No secret value is recorded in this report. |

## Summary by Classification

| Classification | Count | Notes |
|----------------|------:|-------|
| CONFIRMED_SECRET | 1 | HF-01 — confirmed credential in Git history only; absent from current tracked source. |
| REVOKED_HISTORICAL_SECRET | 0 | No revocation claim is accepted until rotation and rejection evidence exists. |
| TEST_FIXTURE | 1 | HF-02 — explicitly labeled non-secret test token. |
| DOCUMENTATION_PLACEHOLDER | 0 | — |
| PUBLIC_IDENTIFIER | 0 | — |
| FALSE_POSITIVE | 4 | HF-03, HF-04, HF-05 are prose matches; HF-06 is a historical non-provider-format code placeholder already removed from the current tree. |
| NEEDS_OWNER_VERIFICATION | 1 | Verification status attached to HF-01; this is not an additional finding. |

## Build-Artifact Detections (62 untracked generated-file detections)

All 62 detections were located in untracked, gitignored generated build outputs. They are outside the tracked-source scanning scope, but values inside generated artifacts may still be sensitive in a live local or build environment and must not be committed, published, or logged:

- **`apps/sanad-platform/target/surefire-reports/*.xml`** (54 findings) — Spring Boot auto-generated development security password (`Using generated security password: <uuid>`). Regenerated on every application startup. Transient generated value in an untracked build artifact. The files are gitignored and absent from fresh CI checkouts.
- **`apps/web/.next/**/*.json`** (8 findings) — Next.js build-time generated encryption keys (`NEXT_SERVER_ACTIONS_ENCRYPTION_KEY`, `__NEXT_PREVIEW_MODE_ENCRYPTION_KEY`, etc.). Regenerated on every `next build`. Transient generated values in untracked build artifacts. The files are gitignored and absent from fresh CI checkouts.

For tracked-source policy, these detections are classified as OUT-OF-SCOPE GENERATED BUILD ARTIFACTS. The `.gitleaks.toml` allowlist excludes only these two gitignored output directories. This exclusion does not mean their generated values are safe to publish or commit.

## `.gitleaks.toml` Changes

A single `[allowlist]` block was added with two narrow path patterns:

```toml
[allowlist]
description = "Gitignored generated build outputs excluded from tracked-source scanning; values in these directories must never be committed or published"
paths = [
  '''^apps/sanad-platform/target/''',
  '''^apps/web/\.next/''',
]
```

Justification:
- Both paths are already in `.gitignore` and are not tracked by git.
- The values in these paths are generated transient values inside gitignored output directories and are excluded from tracked-source scanning.
- No source files, test files, or documentation are covered by this allowlist.
- No regex-based or rule-based allowlists were added.
- The default detection rules remain fully active for all source files.

## Security Workflow Review

The existing `Current Tree Secret Scan` job in `.github/workflows/security-baseline.yml` was reviewed. It already:
- Uses `zricethezav/gitleaks:v8.24.3` in Docker.
- Uses `--no-git --config=/repo/.gitleaks.toml --redact --exit-code=1`.
- Includes a synthetic negative control (canary AWS AKIA key) to verify the scanner still detects real secrets.
- Runs on every pull request (since PR #178 removed path filters).

No changes to the security workflow were required. The `.gitleaks.toml` allowlist will automatically apply to the CI scan.

## Owner Action Required

The only finding that requires owner action is HF-01:

- **What**: A one-time admin password was set via a deleted GitHub Actions workflow (`set-admin-password.yml`). The workflow set a production admin user's password to a hardcoded value, then the workflow file was deleted in a later commit.
- **Risk**: The hardcoded password value remains in git history. If the admin user's password was not subsequently rotated through a different mechanism, the historical value may still grant administrative access.
- **Owner action**:
  1. Verify whether the admin user's password was rotated after the one-time set workflow ran.
  2. If not rotated, rotate the admin user's password immediately through the application's normal password-reset flow.
  3. Verify the historical value is rejected by attempting to authenticate with it (it should fail).
  4. Record safe evidence (timestamp, verifier, rejection confirmed — NO secret values).
- **Status**: BLOCKED — OWNER ACCESS REQUIRED. The executor does not have owner access to perform this verification or rotation.

## Final Scan Results

| Scan | Findings | Status |
|------|---------:|--------|
| Current tree (repo config, after triage) | 0 | PASS |
| Git history (repo config) | 8 raw detections | Consolidated into 6 unique findings: 1 CONFIRMED_SECRET, 1 TEST_FIXTURE, and 4 FALSE_POSITIVE findings. HF-01 requires owner verification. |

## Issue #173 Impact

- The "No secrets found in source" exit criterion is downgraded from COMPLETE to PENDING TRIAGE — and after this triage, it can be re-evaluated as:
  - Current-tree scan: 0 findings. PASS.
  - All findings classified.
  - No confirmed active secret remains in current source.
  - One confirmed historical secret (HF-01) has rotation status NOT VERIFIED and old-value rejection NOT VERIFIED.
- The criterion can be marked COMPLETE only after the owner records safe evidence that the corresponding credential was rotated and that the historical value is rejected. No secret values may be recorded.
- Issue #173 remains OPEN. Confirmed exit criteria: 0/17.
