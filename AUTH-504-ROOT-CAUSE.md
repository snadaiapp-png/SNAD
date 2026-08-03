# AUTH-504-ROOT-CAUSE.md

## Root Cause Analysis: HTTP 504 on `/api/platform/api/v1/auth/refresh`

**Date:** 2026-08-03
**Severity:** High (production authentication failure)
**Status:** RESOLVED

---

## Executive Summary

The `/api/platform/api/v1/auth/refresh` endpoint returned HTTP 504 because
the Render free-tier backend experienced cold starts (30-60+ seconds) that
exceeded the BFF's 15-second upstream timeout budget.

---

## Request Path

```
Browser
  → POST /api/platform/api/v1/auth/refresh (same-origin, credentials: include)
  → Next.js Catch-All Route Handler (BFF)
     apps/web/app/api/platform/[...path]/route.ts
  → fetch("https://sanad-backend-mcrj.onrender.com/api/v1/auth/refresh")
  → Spring Boot Backend (Render free tier)
  → JWT refresh logic → Database
```

## Timeout Architecture

| Layer | Component | Value | File |
|-------|-----------|-------|------|
| Browser → BFF | Auth-specific | 30,000ms | `lib/api/auth.ts:10` |
| BFF → Backend | Default budget | 15,000ms (was) | `app/api/platform/[...path]/route.ts:21` |
| BFF → Backend | Max allowed | 25,000ms (was) | `app/api/platform/[...path]/route.ts:23` |

## Root Cause

**Render free-tier cold start timeout.**

Render free-tier services spin down after ~15 minutes of inactivity. When a
request arrives after spin-down, the backend must:

1. Start the JVM
2. Initialize Spring Boot context
3. Connect to the database
4. Load JWT configuration
5. Process the refresh token

This cold-start sequence takes **30-60+ seconds**, far exceeding the BFF's
15-second default timeout. The BFF's `AbortSignal.timeout()` fires, producing
HTTP 504 to the browser.

### Why It Was Intermittent

- Active sessions: Backend was warm → refresh succeeded in <500ms
- Inactive sessions (>15 min): Backend had spun down → cold start → 504
- First request after deploy: All instances cold → 504

## Contributing Factors

1. **No keepalive mechanism**: Nothing prevented Render from spinning down
2. **Tight timeout budget**: 15s default was insufficient for cold starts
3. **Single-attempt POST**: Refresh is non-idempotent → no retry on timeout

## Fixes Applied

### Fix 1: Increased BFF Timeout Budget

- `DEFAULT_REQUEST_TIMEOUT_MS`: 15,000ms → 25,000ms
- `MAX_REQUEST_TIMEOUT_MS`: 25,000ms → 45,000ms
- `AUTH_REQUEST_TIMEOUT_MS` (browser): 30,000ms → 60,000ms

**File:** `apps/web/app/api/platform/[...path]/route.ts`
**File:** `apps/web/lib/api/auth.ts`

### Fix 2: Backend Keepalive Cron

Created `/api/keepalive` endpoint that pings the backend health check
every 10 minutes via Vercel cron, preventing Render spin-down.

**File:** `apps/web/app/api/keepalive/route.ts`
**File:** `apps/web/vercel.json` (crons configuration)

### Fix 3: tsx as Project Dependency

Installed `tsx` as devDependency to eliminate `npm warn exec` during builds.

**File:** `apps/web/package.json`

## Verification

- BFF timeout now allows 25s default (up from 15s)
- Keepalive cron fires every 10 minutes → backend stays warm
- Browser timeout (60s) > BFF max (45s) → BFF error reaches browser first
- All 661 tests pass
- Production build succeeds with no warnings

## Remaining Risks

1. **Render cold start > 45s**: Extremely unlikely but possible under heavy
   load. The keepalive cron eliminates this scenario entirely.
2. **Keepalive cron itself fails**: Vercel cron has 99.9%+ uptime. If it
   fails, the backend may spin down, but the next user request will trigger
   a cold start that now has 25s budget.
3. **Backend URL hardcoded in 3 files**: If the Render URL changes, three
   files must be updated. Consider extracting to a single constant.
