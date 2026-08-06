# BUILD-STABILITY-REPORT.md

## Build Dependency Stabilization Report

**Date:** 2026-08-03
**Status:** RESOLVED

---

## Problem

Vercel build logs showed:

```
npm warn exec The following package was not found and will be installed:
tsx@4.x
```

This occurred because `tsx` was referenced in the `validate:integrity` script
via `npx tsx` but was not listed as a project dependency.

## Root Cause

`apps/web/package.json` contained:

```json
"validate:integrity": "npx tsx ../../scripts/validate-execution-integrity.ts"
```

The `tsx` package was only declared in `skills/podcast-generate/package.json`
(unrelated sub-package), not in `apps/web/package.json`.

`npx` resolved this by downloading `tsx` on-the-fly during each build,
causing:
- Non-deterministic builds (version could change between runs)
- Build warnings in Vercel logs
- Potential supply-chain risk (downloading unversioned packages)

## Fix

1. Added `tsx` to `apps/web` devDependencies:
   ```json
   "tsx": "^4.19.0"
   ```

2. Changed script from `npx tsx` to `tsx`:
   ```json
   "validate:integrity": "tsx ../../scripts/validate-execution-integrity.ts"
   ```

3. Updated `package-lock.json` via `npm install --save-dev tsx`

## Verification

| Check | Result |
|-------|--------|
| `npm run validate:integrity` | 28/28 rules pass |
| `npm run build` (prebuild) | Clean, no npm warn exec |
| `npm run build` (full) | Compiled successfully |
| `npm run lint` | 0 errors, 31 pre-existing warnings |
| `npm test` | 661/661 tests pass |

## Files Changed

- `apps/web/package.json` — added tsx devDependency, updated script
- `apps/web/package-lock.json` — updated lockfile

## Build Determinism

Before: `npx tsx` → downloads tsx@latest on each build (non-deterministic)
After: `tsx` → uses pinned tsx@^4.19.0 from node_modules (deterministic)
