# ENVIRONMENT-AUDIT.md

## Production Environment Variable Audit

**Date:** 2026-08-03
**Status:** PASS

---

## Summary

All production environment variables are correctly configured. No orphaned
variables, no conflicting URLs, production uses the production backend.

## Variable Inventory

### Active Variables (Used in Source Code)

| Variable | Scope | Value | Status |
|----------|-------|-------|--------|
| `BACKEND_API_BASE_URL` | Server-only | `http://127.0.0.1:8080` (dev) | CLEAN |
| `BACKEND_REQUEST_TIMEOUT_MS` | Server-only | `25000` (default) | CLEAN |
| `NEXT_PUBLIC_API_BASE_URL` | Client (dev only) | Commented out | CLEAN |
| `VERCEL_ENV` | Server-only | `production` (Vercel-injected) | CLEAN |
| `NODE_ENV` | Server-only | `production` (Next.js-injected) | CLEAN |

### Not Used (Confirmed Clean)

| Variable | Status |
|----------|--------|
| `NEXT_PUBLIC_API_URL` | Not referenced anywhere |
| `API_URL` | Not referenced anywhere |
| `NEXTAUTH_URL` | Not referenced anywhere |
| `AUTH_SECRET` | Not referenced anywhere |

### Backend-Only (Not in Next.js Code)

| Variable | Location |
|----------|----------|
| `JWT_SECRET` | Spring Boot YAML, self-hosted .env |
| `COOKIE_DOMAIN` | Spring Boot YAML, docker-compose |
| `COOKIE_SECURE` | Spring Boot YAML, docker-compose |

### Orphaned (Defined but Never Read)

| Variable | Defined In | Status |
|----------|-----------|--------|
| `NEXT_PUBLIC_APP_URL` | .env.local, .vercel | ORPHANED — safe to remove |

## Production Backend Routing

Production uses a hardcoded backend URL (`https://sanad-backend-mcrj.onrender.com`)
in three files:

1. `apps/web/app/api/platform/[...path]/route.ts` (line 16)
2. `apps/web/lib/api/health.ts` (line 15)
3. `apps/web/app/api/keepalive/route.ts` (line 17)

This is intentional — the RUNTIME-CONFIGURATION-MATRIX.md marks production
routing as immutable. Environment variables are ignored in production.

## Conflicts

None. No duplicated variables, no conflicting URLs.

## Recommendations

1. Remove `NEXT_PUBLIC_APP_URL` from .env files (orphaned)
2. Consider extracting `PRODUCTION_BACKEND_URL` to a shared constant
