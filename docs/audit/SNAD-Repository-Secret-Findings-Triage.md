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
| Git history | default | 7 |
| Git history | repo `.gitleaks.toml` | 7 |

The 62 current-tree findings were all in gitignored build artifacts (`apps/sanad-platform/target/` and `apps/web/.next/`). After adding a narrow path allowlist for those two build-output directories, the current-tree scan reports 0 findings.

The 7 history findings are in 4 distinct source files across 7 commits. They are classified below.

## Classification Table

| Finding ID | File | Line(s) | Rule | Scope | Classification | Action | Evidence |
| ---------- | ---- | ------: | ---- | ----- | -------------- | ------ | -------- |
| HF-01 | `.github/workflows/set-admin-password.yml` | 51 | generic-api-key | History (commit `f766e429`, deleted in `6dfd05e`) | REVOKED_HISTORICAL_SECRET | BLOCKED — OWNER ACCESS REQUIRED: verify the production admin password set by this deleted one-time workflow has been rotated and the historical value is rejected by the live authentication system. | File deleted from current tree; value not present in any git-tracked file; workflow was a one-time password-set operation that was deleted after use. |
| HF-02 | `apps/web/lib/api/auth-flow.test.ts` | 72 | generic-api-key | History (commits `28264cb9`, `d100caac`) and current tree | TEST_FIXTURE | No action required. | Value is the literal string `test-access-token-not-a-jwt`, explicitly labeled as a non-secret test token in a comment. Not a real credential format, not used to authenticate, only used to verify `Bearer` header formatting. |
| HF-03 | `docs/runbooks/backend-auth-rollback.md` | 9 | generic-api-key | History (commit `a4bedb10`) and current tree | FALSE_POSITIVE | No action required. | Gitleaks matched a backtick-quoted prose identifier in a markdown runbook. The matched text is a short reference tag, not a credential. Manual review confirmed no secret value. |
| HF-04 | `docs/runbooks/backend-auth-rollback.md` | 10 | generic-api-key | History (commit `a4bedb10`) and current tree | FALSE_POSITIVE | No action required. | Same as HF-03 — backtick-quoted prose identifier in markdown runbook. Manual review confirmed no secret value. |
| HF-05 | `docs/execution/EXEC-PROMPT-029-frontend-backend-integration-foundation.md` | 54 | generic-api-key | History (commits `8b45421c`, `15043d07`) and current tree | FALSE_POSITIVE | No action required. | Gitleaks matched the prose phrase `session-token lifecycle:` in a markdown execution log. The matched text is descriptive prose, not a credential. Manual review confirmed no secret value. |

## Summary by Classification

| Classification | Count | Notes |
|----------------|------:|-------|
| CONFIRMED_SECRET | 0 | No active confirmed secrets found in current source. |
| REVOKED_HISTORICAL_SECRET | 1 | HF-01 — one-time admin password in deleted workflow. Owner must verify rejection. |
| TEST_FIXTURE | 1 | HF-02 — explicitly labeled non-secret test token. |
| DOCUMENTATION_PLACEHOLDER | 0 | — |
| PUBLIC_IDENTIFIER | 0 | — |
| FALSE_POSITIVE | 3 | HF-03, HF-04, HF-05 — prose text in markdown files matched by `generic-api-key` rule. |
| NEEDS_OWNER_VERIFICATION | 1 | HF-01 (also classified as REVOKED_HISTORICAL_SECRET) — owner must verify the historical admin password has been rotated and rejected. |

## Build-Artifact Findings (62 current-tree, all FALSE_POSITIVE)

All 62 current-tree findings were in gitignored build outputs:

- **`apps/sanad-platform/target/surefire-reports/*.xml`** (54 findings) — Spring Boot auto-generated development security password (`Using generated security password: <uuid>`). Regenerated on every application startup. Dev-only, not a real credential. Files are gitignored and not present in CI fresh checkouts.
- **`apps/web/.next/**/*.json`** (8 findings) — Next.js build-time generated encryption keys (`NEXT_SERVER_ACTIONS_ENCRYPTION_KEY`, `__NEXT_PREVIEW_MODE_ENCRYPTION_KEY`, etc.). Regenerated on every `next build`. Build artifacts, not real credentials. Files are gitignored and not present in CI fresh checkouts.

These findings are all classified as FALSE_POSITIVE. The `.gitleaks.toml` allowlist (added in this triage) excludes these two paths so future local scans and any CI step that builds before scanning will not produce these false positives.

## `.gitleaks.toml` Changes

A single `[allowlist]` block was added with two narrow path patterns:

```toml
[allowlist]
description = "Gitignored build artifacts only (Maven target/ and Next.js .next/) ..."
paths = [
  '''^apps/sanad-platform/target/''',
  '''^apps/web/\.next/''',
]
```

Justification:
- Both paths are already in `.gitignore` and are not tracked by git.
- The credentials in these paths are auto-generated development-only values that are regenerated on every build.
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
| Git history (repo config) | 7 | All classified (1 REVOKED_HISTORICAL_SECRET, 1 TEST_FIXTURE, 3 FALSE_POSITIVE — noting HF-01 is counted once in the table above) |

## Issue #173 Impact

- The "No secrets found in source" exit criterion is downgraded from COMPLETE to PENDING TRIAGE — and after this triage, it can be re-evaluated as:
  - Current-tree scan: 0 findings. PASS.
  - All findings classified.
  - No confirmed active secret remains in current source.
  - One revoked historical secret (HF-01) requires owner verification of rejection.
- The criterion can be marked COMPLETE only after the owner verifies the historical admin password has been rotated and rejected.
- Issue #173 remains OPEN. Confirmed exit criteria: 0/17.
