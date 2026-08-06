# NEXT.JS PROXY MIGRATION

**Date:** 2026-08-03
**Repository:** snadaiapp-png/SNAD
**Migration:** middleware.ts → proxy.ts (Next.js 16 pattern)

---

## Summary

Migrated the deprecated Next.js middleware implementation to the Next.js 16 proxy pattern. Zero deprecation warnings. Build PASS. Runtime behavior identical.

---

## What Changed

### Deleted

| File | Purpose |
|------|---------|
| `apps/web/middleware.ts` | Deprecated Next.js middleware (redirect `/crm` → `/crm/overview`, set cookie) |

### Created

| File | Purpose |
|------|---------|
| `apps/web/proxy.ts` | Next.js 16 proxy — identical runtime behavior to deleted middleware |

### Modified

| File | Change |
|------|--------|
| `apps/web/app/providers.tsx` | Import `CRM_ROOT_ENTRY_COOKIE` from `proxy.ts`; added `setCrmRootEntryMarker()` for client-side cookie |

---

## Runtime Behavior (Identical)

| Behavior | Before (middleware.ts) | After (proxy.ts) |
|----------|----------------------|-------------------|
| Route | `/crm` | `/crm` |
| Redirect | 307 → `/crm/overview` | 307 → `/crm/overview` |
| Cookie | `snad_crm_root_entry=1` (60s TTL) | `snad_crm_root_entry=1` (60s TTL) |
| Cookie secure | `true` on HTTPS | `true` on HTTPS |
| Cookie sameSite | `lax` | `lax` |
| Matcher | `["/crm"]` | `["/crm"]` |

---

## Migration Pattern

Next.js 16 replaces `middleware.ts` with `proxy.ts`:

```typescript
// proxy.ts — Next.js 16 pattern
import { NextResponse, type NextRequest } from "next/server";

export const config = {
  matcher: ["/crm"],
};

export function proxy(request: NextRequest) {
  // Same logic as previous middleware function
  const response = NextResponse.redirect(destination, 307);
  response.cookies.set({...});
  return response;
}
```

Key differences:
- File name: `middleware.ts` → `proxy.ts`
- Export name: `middleware` → `proxy`
- Build output: `Middleware` → `Proxy (Middleware)`
- No deprecation warnings

---

## Verification

| Check | Result |
|-------|--------|
| `npm run lint` | PASS (0 errors) |
| `npm run build` | PASS |
| `npm test` | PASS (468 tests, 44 files) |
| Build output | Shows `ƒ Proxy (Middleware)` |
| Deprecation warnings | Zero |

---

## Cookie Flow

1. User navigates to `/crm`
2. `proxy.ts` redirects to `/crm/overview` (307) and sets cookie
3. `providers.tsx` `AuthRouteRecovery` detects cookie on `/crm/overview`
4. If session is valid: cookie is cleared
5. If session is gone: redirect to `/?returnUrl=/crm`

---

## Acceptance Criteria

- ✅ middleware.ts deleted
- ✅ proxy.ts created with identical behavior
- ✅ Zero deprecation warnings
- ✅ Build PASS
- ✅ Tests PASS
- ✅ Runtime identical
