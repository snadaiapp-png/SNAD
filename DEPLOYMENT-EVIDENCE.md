# DEPLOYMENT-EVIDENCE.md

## Production Deployment Evidence

**Date:** 2026-08-03
**Mission:** SANAD Production Stabilization — Build & Auth Hardening

---

## Commit Details

| Field | Value |
|-------|-------|
| Branch | `main` |
| Commit Message | `fix(stabilize): build dependency hardening + auth refresh 504 resolution` |
| Files Changed | 8 |
| Insertions | ~120 |
| Deletions | ~10 |

## Files Changed

| File | Change Type | Description |
|------|------------|-------------|
| `apps/web/package.json` | Modified | Added tsx devDependency, removed npx from script |
| `apps/web/package-lock.json` | Modified | Updated lockfile with tsx |
| `apps/web/app/api/platform/[...path]/route.ts` | Modified | Increased BFF timeout (15s→25s default, 25s→45s max) |
| `apps/web/lib/api/auth.ts` | Modified | Increased browser auth timeout (30s→60s) |
| `apps/web/app/api/keepalive/route.ts` | Created | Backend keepalive endpoint for Vercel cron |
| `apps/web/vercel.json` | Modified | Added cron: `*/10 * * * *` → `/api/keepalive` |

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

## Deployment Steps

1. ✅ Committed all changes
2. ✅ Pushed to `main`
3. ✅ Vercel auto-deploy triggered
4. ⏳ Waiting for deployment READY status

## Post-Deploy Verification Checklist

- [ ] HTTP 200 on production root
- [ ] No npm warnings in build log
- [ ] No 504 on auth refresh
- [ ] Keepalive cron registered
- [ ] Login works
- [ ] Logout works
- [ ] Session renewal works
