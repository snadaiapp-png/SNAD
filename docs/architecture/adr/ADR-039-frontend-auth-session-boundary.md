# ADR-039: Frontend Authentication Session Boundary

**Status:** PROPOSED — Awaiting owner approval
**Date:** 2026-06-25
**Supersedes:** None (corrects the assumption in closed PR #85)
**Related:** EXEC-PROMPT-010R Section 9, DEFECT-019, DEFECT-027

---

## Context

The SANAD platform has two production origins:

| Component | Origin | Hosting |
|-----------|--------|---------|
| Frontend | `https://snad-app.vercel.app` | Vercel |
| Backend | `https://sanad-backend-mcrj.onrender.com` | Render |

The backend issues a refresh token as an HttpOnly cookie on login. The
frontend (Next.js 16) needs to know whether a user is authenticated to
protect routes server-side (prevent flash of unprotected content, prevent
SEO indexing of protected pages).

**Closed PR #85** assumed that Next.js Middleware on `*.vercel.app` could
read a refresh cookie issued by `*.onrender.com`. **This assumption is
invalid**: `vercel.app` and `onrender.com` are separate registrable
domains (different eTLD+1), so a cookie set by the backend is NOT sent
to the frontend origin by default.

### Cookie Behavior Background

- A cookie's `Domain` attribute controls which hosts receive it.
- If `Domain` is omitted, the cookie is "host-only" — sent only to the
  exact origin that set it.
- If `Domain=onrender.com`, the cookie is sent to `*.onrender.com` but
  NOT to `*.vercel.app`.
- `SameSite=Lax` (the current backend default) prevents cross-site
  cookie transmission except on top-level GET navigations.
- `SameSite=None; Secure` allows cross-site transmission but requires
  HTTPS and explicit CORS `Access-Control-Allow-Credentials: true`.

### Current State (Verified 2026-06-25)

- Backend sets refresh cookie with:
  - `HttpOnly: true`
  - `Secure: true`
  - `SameSite: Lax` (default in `application-prod.yml`)
  - `Domain:` (empty — host-only, scoped to `sanad-backend-mcrj.onrender.com`)
- Frontend is on `snad-app.vercel.app` (different registrable domain)
- Frontend `middleware.ts` (from closed PR #85) checks for
  `sanad_refresh` cookie — **which is NOT readable on the frontend
  origin**

---

## Decision Drivers

1. **Security**: Access tokens must never be in localStorage (XSS risk).
2. **User Experience**: Session should persist across page reloads.
3. **Operational Simplicity**: Avoid custom domain management if possible.
4. **Cost**: Avoid additional infrastructure (reverse proxy, BFF).
5. **Browser Compatibility**: Solution must work across modern browsers.
6. **GDPR/Compliance**: Cookie scoping must respect user privacy expectations.

---

## Considered Options

### Model A — Same Parent Production Domain

**Architecture:**
```
app.sanad-project.com  (Vercel — frontend)
api.sanad-project.com  (Render — backend)
```

**Cookie Configuration:**
- `Domain=.sanad-project.com` (apex domain — cookie shared across subdomains)
- `SameSite=Lax` (sufficient — same-site at the registrable domain level)
- `Secure=true`
- `HttpOnly=true`

**Pros:**
- ✅ Cookies naturally readable by both origins (same registrable domain)
- ✅ `SameSite=Lax` works correctly (same-site)
- ✅ Next.js Middleware can read the refresh cookie
- ✅ Industry-standard pattern (e.g., `app.github.com` + `api.github.com`)
- ✅ No additional infrastructure

**Cons:**
- ❌ Requires owning a custom domain (`sanad-project.com` or similar)
- ❌ Requires DNS configuration (CNAME records for both subdomains)
- ❌ Requires TLS certificates (Vercel and Render both provide automatic TLS)
- ❌ Requires updating CORS allowed origins on the backend
- ❌ One-time setup cost (~$10-15/year for domain registration)

**Migration Steps:**
1. Register custom domain
2. Configure DNS: `app.<domain>` → Vercel, `api.<domain>` → Render
3. Update Vercel project settings: add custom domain
4. Update Render service settings: add custom domain
5. Update backend `SANAD_CORS_ALLOWED_ORIGINS` to `https://app.<domain>`
6. Update backend cookie `Domain` to `.<domain>`
7. Update frontend `NEXT_PUBLIC_API_BASE_URL` to `https://api.<domain>`
8. Deploy and verify

### Model B — Frontend Reverse Proxy / BFF (Backend-For-Frontend)

**Architecture:**
```
snad-app.vercel.app  (Vercel — frontend + reverse proxy)
  ↓ (server-side fetch with credentials)
sanad-backend-mcrj.onrender.com  (Render — backend, not directly browser-accessible)
```

**Cookie Configuration:**
- Backend sets refresh cookie with `Domain=snad-app.vercel.app` (via
  reverse proxy rewriting)
- OR: Frontend Next.js API route re-issues the cookie on its own origin

**Pros:**
- ✅ No custom domain required
- ✅ Single origin from browser perspective
- ✅ CORS not needed (same-origin)
- ✅ Middleware can read the cookie

**Cons:**
- ❌ Requires Next.js API routes or middleware to proxy auth requests
- ❌ Adds latency (extra hop through Vercel serverless functions)
- ❌ Vercel serverless function timeout (10s on hobby plan) may be tight
- ❌ Complexity: must handle token rotation, replay detection in the proxy
- ❌ Backend becomes hidden — direct API access for mobile/CLI clients lost

**Migration Steps:**
1. Create Next.js API routes: `/api/auth/login`, `/api/auth/refresh`, `/api/auth/logout`
2. Each route proxies to the backend with `credentials: 'include'`
3. Backend CORS must allow the Vercel origin with credentials
4. Backend sets cookie with `Domain=snad-app.vercel.app` (or frontend re-issues)
5. Update frontend to call `/api/auth/*` instead of backend directly

### Model C — Client-Side Bootstrap Only

**Architecture:**
```
snad-app.vercel.app  (Vercel — frontend)
  ↓ (client-side fetch with credentials: 'include')
sanad-backend-mcrj.onrender.com  (Render — backend)
```

**Cookie Configuration:**
- Backend sets refresh cookie with `SameSite=None; Secure` (cross-site)
- Cookie is sent on cross-site XHR/fetch with `credentials: 'include'`
- Frontend CANNOT read the cookie (HttpOnly) — only the backend sees it

**Authentication Flow:**
1. User visits `/dashboard` (protected)
2. Next.js Middleware CANNOT check auth (no readable cookie)
3. Frontend renders a loading state
4. Client-side JS calls `/api/v1/auth/refresh` with `credentials: 'include'`
5. If refresh succeeds → user is authenticated, render dashboard
6. If refresh fails (401) → redirect to login

**Pros:**
- ✅ No custom domain required
- ✅ No reverse proxy complexity
- ✅ Cookie security maintained (HttpOnly, Secure, SameSite=None)
- ✅ Backend remains directly accessible for mobile/CLI clients

**Cons:**
- ❌ No server-side route protection (flash of unprotected content)
- ❌ SEO indexing of protected pages (mitigated via `noindex` meta tag)
- ❌ Extra round-trip on every page load (silent refresh)
- ❌ Requires `SameSite=None` (broader CSRF surface — needs CSRF tokens)
- ❌ Vercel Middleware can only do route metadata (no auth check)

**Migration Steps:**
1. Update backend cookie `SameSite=None; Secure`
2. Add CSRF token mechanism (double-submit cookie or synchronizer token)
3. Frontend: add `noindex` meta to protected pages
4. Frontend: client-side bootstrap with silent refresh on every load
5. Frontend: loading state while bootstrap runs

---

## Recommendation

**Model A (Same Parent Production Domain)** is recommended for the
production commercial launch because:

1. It is the only model that enables true server-side route protection
   (DEFECT-019 requirement).
2. It has the simplest cookie model (`SameSite=Lax` works correctly).
3. It is the industry-standard pattern for multi-origin SaaS.
4. The one-time domain cost (~$10-15/year) is trivial compared to the
   ongoing complexity of Models B and C.

**Model C (Client-Side Bootstrap)** is recommended as an interim
solution for the pilot phase (before custom domain is purchased)
because:

1. It requires no infrastructure changes.
2. The flash-of-unprotected-content is acceptable for a pilot.
3. SEO indexing is not a concern for an internal pilot tool.
4. It can be replaced by Model A when the custom domain is ready.

**Model B (Reverse Proxy)** is NOT recommended because:

1. It adds complexity without clear benefit over Model A.
2. Vercel serverless function timeouts may be problematic.
3. It hides the backend from non-browser clients.

---

## Decision Required

**Owner must choose one model and approve before PR #85 (replacement)
and PR #86B (cookie configuration) can proceed.**

### If Model A is chosen:
- Purchase custom domain
- Configure DNS and TLS
- Update backend CORS and cookie Domain
- Update frontend API base URL
- Create replacement PR for #85 with middleware reading the cookie
- Create PR #86B with `SameSite=Lax` (works because same-site)

### If Model C is chosen (interim):
- Update backend cookie `SameSite=None; Secure`
- Implement CSRF token mechanism
- Frontend: client-side bootstrap only
- Replace PR #85's middleware with `noindex` metadata only
- Create PR #86B with `SameSite=None` (cross-site)

### If Model B is chosen:
- Implement Next.js API routes for auth proxy
- Update backend CORS to allow Vercel origin with credentials
- Create replacement PR for #85 with middleware reading the proxied cookie
- Create PR #86B with cookie Domain scoped to Vercel origin

---

## Required Browser Integration Tests (All Models)

Regardless of chosen model, the following tests must pass before
DEFECT-019 and DEFECT-027 can be considered resolved:

```
Anonymous protected route → redirect to login
Successful login → cookie set, dashboard accessible
Protected route after login → 200 OK
Browser refresh after login → session persists (silent refresh)
Silent refresh → new access token issued
Expired refresh cookie → 401, redirect to login
Revoked refresh cookie → 401, redirect to login
Logout → cookie cleared, dashboard inaccessible
Cross-origin credentials → cookie sent/received correctly
Cookie visible only to intended origin → verified
No access token in localStorage → verified
No access token in sessionStorage → verified
```

**Test environment:** Vercel Preview + Render backend (or staging backend)
**Test type:** Browser-level (Playwright or Cypress) — unit tests insufficient

---

## Status Tracking

| Item | Status |
|------|--------|
| ADR created | ✅ This document |
| Owner review | ⏳ PENDING |
| Model selected | ⏳ PENDING |
| Browser integration tests | ⏳ PENDING (after model selection) |
| PR #85 replacement | ⏳ BLOCKED (after model selection) |
| PR #86B (cookie config) | ⏳ BLOCKED (after model selection) |
| DEFECT-019 resolved | ⏳ BLOCKED (after replacement PR merged) |
| DEFECT-027 resolved | ⏳ BLOCKED (after replacement PR merged) |

---

## References

- [RFC 6265bis — HTTP State Management Mechanism](https://datatracker.ietf.org/doc/html/draft-ietf-httpbis-rfc6265bis)
- [MDN — SameSite cookies](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Set-Cookie/SameSite)
- [Vercel — Custom Domains](https://vercel.com/docs/concepts/projects/custom-domains)
- [Render — Custom Domains](https://render.com/docs/custom-domains)
- Closed PR #85 (assumed cross-domain cookie readability — invalid)
- Closed PR #86 (mixed concerns — split into #86A and #86B)
