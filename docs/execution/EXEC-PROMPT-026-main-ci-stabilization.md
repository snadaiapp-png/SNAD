# EXEC-PROMPT-026 — Main Branch CI Stabilization

## Objective

Stabilize the GitHub Actions CI workflows for the `main` branch. Remove obsolete feature-branch-only triggers, add `workflow_dispatch`, add least-privilege permissions, and ensure both backend and frontend CI pipelines run on push to main and pull requests targeting main.

## Initial CI State

Before this change:

- `ci.yml` (backend) triggered only on `feat/EXEC-PROMPT-001-sanad-platform-skeleton` — the obsolete feature branch. Pushes to `main` did not run backend CI.
- `web-ci.yml` (frontend) triggered on both `main` and the obsolete feature branch.
- `production-smoke.yml` already triggered on `main` push and `workflow_dispatch`.
- None of the workflows had explicit `permissions` blocks.
- `ci.yml` and `web-ci.yml` lacked `workflow_dispatch`.
- `web-ci.yml` was missing a lint step.

## Root Cause

The backend CI was locked to a feature branch (`feat/EXEC-PROMPT-001-sanad-platform-skeleton`) that has since been merged into `main`. After the merge, backend CI never ran on `main`, leaving production without automated backend validation.

## Files Inspected

| File | Purpose |
|---|---|
| `.github/workflows/ci.yml` | Backend CI (Maven) |
| `.github/workflows/web-ci.yml` | Frontend CI (Next.js) |
| `.github/workflows/production-smoke.yml` | Production smoke test |
| `apps/sanad-platform/pom.xml` | Maven project descriptor (confirms Maven build tool) |
| `apps/web/package.json` | Frontend package manifest (confirms npm + Next.js 16) |
| `apps/web/package-lock.json` | npm lockfile (confirms npm as package manager) |

## Files Modified

| File | Changes |
|---|---|
| `.github/workflows/ci.yml` | Triggers changed from feature branch to `main`; added `workflow_dispatch`; added `permissions: contents: read`; updated comments |
| `.github/workflows/web-ci.yml` | Removed obsolete feature branch trigger; added `workflow_dispatch`; added `permissions: contents: read`; added lint step (`npm run lint`) |
| `.github/workflows/production-smoke.yml` | Added `permissions: contents: read` |

## Workflow Trigger Changes

### ci.yml (Backend)

| Before | After |
|---|---|
| `push: branches: [feat/EXEC-PROMPT-001-sanad-platform-skeleton]` | `push: branches: [main]` |
| `pull_request: branches: [feat/EXEC-PROMPT-001-sanad-platform-skeleton]` | `pull_request: branches: [main]` |
| (none) | `workflow_dispatch` |
| (none) | `permissions: contents: read` |

### web-ci.yml (Frontend)

| Before | After |
|---|---|
| `push: branches: [main, feat/EXEC-PROMPT-001-sanad-platform-skeleton]` | `push: branches: [main]` |
| `pull_request: branches: [main, feat/EXEC-PROMPT-001-sanad-platform-skeleton]` | `pull_request: branches: [main]` |
| (none) | `workflow_dispatch` |
| (none) | `permissions: contents: read` |
| (no lint step) | Added `npm run lint` step |

### production-smoke.yml

| Before | After |
|---|---|
| (no permissions block) | `permissions: contents: read` |

## Backend Path Detected

```
apps/sanad-platform/
  Build tool: Maven (pom.xml confirmed)
  Java version: 21 (Temurin, set in pom.xml via spring-boot-starter-parent)
  Working directory in CI: apps/sanad-platform (unchanged, already correct)
```

## Commands Executed (Local Verification)

### Backend

```bash
mvn -B -ntp clean compile    # BUILD SUCCESS (76 source files compiled)
mvn -B -ntp clean test       # BUILD SUCCESS (250 tests, 0 failures, 0 errors, 0 skipped)
mvn -B -ntp clean package    # BUILD SUCCESS (57 MB fat jar)
```

### Frontend

```bash
npm ci                        # Success (dependencies installed)
npm run lint                  # Success (exit 0, no errors)
npm run build                 # Success (Next.js build completed, 2 static pages)
```

### Workflow Validation

```bash
# YAML syntax validated for all 3 workflow files (python3 yaml.safe_load)
# Zero references to obsolete feature branch in any workflow file
# workflow_dispatch present in ci.yml and web-ci.yml
# permissions: contents: read present in all 3 files
# git diff --check: clean (no whitespace errors)
```

## Test Results

| Suite | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|
| Backend (Maven) | 250 | 0 | 0 | 0 |
| Frontend (lint) | — | 0 | 0 | — |
| Frontend (build) | — | 0 | 0 | — |
| **Total** | **250** | **0** | **0** | **0** |

## Remaining Risks

1. **Frontend has no test runner** — `package.json` does not define a `test` script. No frontend unit tests exist yet. This is not a regression; it's the current state. Adding a test framework (e.g., Vitest or Jest) is a future task.

2. **No environment variables configured in Vercel** — The Vercel project has `env: []`. This does not affect CI (which runs locally against H2), but may affect runtime when the backend is deployed.

3. **Production smoke test depends on Vercel deployment** — The `production-smoke.yml` workflow verifies `https://snad-app.vercel.app` after a push to `main`. If Vercel's `rootDirectory` setting is incorrect (as diagnosed in the previous deployment review), the smoke test will fail until that is fixed. This is a separate issue outside this CI stabilization task.

## Rollback Instructions

To revert this change:

```bash
git checkout main
git revert <commit-sha>
git push origin main
```

This will restore the previous workflow triggers (feature-branch-only) and remove the `permissions` blocks and `workflow_dispatch` triggers.

## Commit SHA

```
6e5a89ec00931a82f72b522488da45918eb1a518
```

## Pull Request URL

https://github.com/snadaiapp-png/SNAD/pull/19
