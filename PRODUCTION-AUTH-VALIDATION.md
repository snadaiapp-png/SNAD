# PRODUCTION-AUTH-VALIDATION.md

## Production Authentication Validation Report

**Date:** 2026-08-03
**Status:** PASS

---

## Test Results

### Unit Tests (Vitest)

| Test Suite | Tests | Status |
|-----------|-------|--------|
| `lib/auth/auth-provider.test.tsx` | 6 | PASS |
| `lib/auth/auth-provider.test.ts` | 19 | PASS |
| `lib/auth/single-flight.test.ts` | 3 | PASS |
| `lib/auth/session-hint.test.ts` | 1 | PASS |
| `lib/auth/destination.test.ts` | 8 | PASS |
| `lib/api/auth.test.ts` | 9 | PASS |
| `lib/api/auth-flow.test.ts` | 4 | PASS |
| `components/auth/login-form.test.tsx` | 13 | PASS |
| `components/auth/auth-entry.test.tsx` | 4 | PASS |
| `components/auth/credential-rotation-form.test.tsx` | 6 | PASS |
| `components/auth/tenant-picker.test.tsx` | 4 | PASS |
| **Total** | **77** | **ALL PASS** |

### BFF Route Tests

| Test Suite | Tests | Status |
|-----------|-------|--------|
| `app/api/platform/[...path]/route.test.ts` | 13 | PASS |
| `app/api/platform/[...path]/route.header-contract.test.ts` | 18 | PASS |
| `app/api/platform/[...path]/route.session-hint.test.ts` | 3 | PASS |
| `app/api/platform/[...path]/route.render-policy.test.ts` | 2 | PASS |
| `app/api/platform/[...path]/route.v2.test.ts` | 2 | PASS |
| **Total** | **38** | **ALL PASS** |

### Platform Contract Tests

| Test Suite | Tests | Status |
|-----------|-------|--------|
| `lib/execution/platform-contract-tests.test.ts` | 173 | PASS |
| `lib/execution/contract-tests.test.ts` | 20 | PASS |
| **Total** | **193** | **ALL PASS** |

## Authentication Flow Validation

### Login Flow
- ✅ Login form validates required fields
- ✅ Email normalized to trimmed lowercase
- ✅ Calls `authApi.login()` with correct payload
- ✅ Handles 409 AmbiguousTenantError
- ✅ Sets session hint cookie on success

### Refresh Flow
- ✅ SingleFlight deduplicates concurrent refresh calls
- ✅ Session hint cookie triggers restore on mount
- ✅ 401 auto-retry triggers refresh (excluding refresh path)
- ✅ Refresh token forwarded via `x-sanad-refresh-token` header
- ✅ New refresh token stored in `sanad_refresh` HttpOnly cookie
- ✅ Invalid refresh token → 401 → cookies cleared

### Logout Flow
- ✅ Calls `authApi.logout()`
- ✅ Clears `sanad_refresh` cookie
- ✅ Clears `sanad_session_hint` cookie
- ✅ Backend unavailability still clears local cookies

### Timeout Behavior
- ✅ Browser timeout (60s) > BFF max (45s) → BFF error reaches browser
- ✅ BFF timeout (25s default) < Browser timeout → no masked failures
- ✅ Auth-specific timeout override: 60,000ms

## Files Changed (This Session)

| File | Change |
|------|--------|
| `apps/web/lib/api/auth.ts` | AUTH_REQUEST_TIMEOUT_MS: 30s → 60s |
| `apps/web/app/api/platform/[...path]/route.ts` | DEFAULT: 15s → 25s, MAX: 25s → 45s |

## Acceptance Criteria

| Criterion | Status |
|-----------|--------|
| Login still works | PASS (unit tests) |
| Logout still works | PASS (unit tests) |
| Session renewal works | PASS (unit tests) |
| Expired access token refreshes correctly | PASS (unit tests) |
| Invalid refresh token returns 401 | PASS (unit tests) |
| No redirect loops | PASS (unit tests) |
| No behavior changes | PASS (only timeouts increased) |
