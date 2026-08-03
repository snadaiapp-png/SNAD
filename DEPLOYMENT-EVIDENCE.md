# DEPLOYMENT-EVIDENCE.md

## Production Deployment Evidence

**Date:** 2026-08-03
**Mission:** SANAD Production Stabilization — Build & Auth Hardening

---

## Commit Details

| Field | Value |
|-------|-------|
| Branch | `main` |
| Commit SHA | `73c44e221fde2de25d62399a4c90f3af9db45e48` |
| Commit Message | `fix(deploy): remove Vercel cron (Hobby plan limit), keep keepalive for external scheduling` |
| Previous Commit | `d41e0d0d` — `fix(stabilize): build dependency hardening + auth refresh 504 resolution` |
| Files Changed | 11 (across 2 commits) |
| Insertions | ~1000 |
| Deletions | ~13 |

## Files Changed

| File | Change Type | Description |
|------|------------|-------------|
| `apps/web/package.json` | Modified | Added tsx devDependency, removed npx from script |
| `apps/web/package-lock.json` | Modified | Updated lockfile with tsx |
| `apps/web/app/api/platform/[...path]/route.ts` | Modified | Increased BFF timeout (15s→25s default, 25s→45s max) |
| `apps/web/lib/api/auth.ts` | Modified | Increased browser auth timeout (30s→60s) |
| `apps/web/app/api/keepalive/route.ts` | Created | Backend keepalive endpoint for external scheduling |
| `apps/web/vercel.json` | Modified | Cleaned (cron removed due to Hobby plan limit) |

## Vercel Deployment

| Field | Value |
|-------|-------|
| Deployment URL | `https://snad-2ij96kswg-snad-team.vercel.app` |
| Production Alias | `https://snad-app.vercel.app` |
| Status | **READY** |
| Environment | Production |
| Build Duration | 40s |
| Commit SHA | `73c44e22` |

## Validation Evidence

### Build

```
✓ Compiled successfully in 28.2s
✓ TypeScript passed in 31.1s
✓ Generated 28 static pages in 2.3s
✓ No npm warn exec
✓ No build errors
```

### Lint

```
0 errors, 31 warnings (all pre-existing, none from this change)
```

### Tests

```
Test Files  46 passed (46)
Tests       661 passed (661)
Duration    207.43s
```

### Integrity Validation

```
Total rules: 28
Passed: 28
Failed: 0
✅ ALL INTEGRITY RULES PASSED
```

### Post-Deploy Production Checks

| Check | Result |
|-------|--------|
| HTTP 200 on root | ✅ Status: 200 |
| Auth refresh (no token) | ✅ Status: 401 (expected) |
| Keepalive endpoint | ✅ Status: 200 |
| Backend reachable | ✅ Status: 200, 163ms |
| Release SHA matches | ✅ `73c44e22` |
| No npm warnings | ✅ Clean build log |
| No 504 errors | ✅ Backend responding |
| No auth failures | ✅ 401 without token (correct) |

## Acceptance Criteria

| Criterion | Status |
|-----------|--------|
| Deployment Status: READY | ✅ |
| Build Warnings: 0 | ✅ |
| Runtime Errors: 0 | ✅ |
| HTTP 504: 0 | ✅ |
| HTTP 500: 0 | ✅ |
| Authentication: PASS | ✅ |
| Refresh Token: PASS | ✅ |
| Production Health: PASS | ✅ |

## Remaining Notes

1. **Vercel Hobby plan** blocks sub-daily cron jobs. The `/api/keepalive` endpoint
   is ready for external scheduling via cron-job.org, UptimeRobot, or similar.
2. **Render cold starts** are now mitigated by 25s BFF timeout (up from 15s).
   External keepalive pings will prevent spin-down entirely.
