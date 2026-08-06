# CRM Deployment Report — v2.0.0

| Field | Value |
|-------|-------|
| Deployment Date | 2026-07-30 |
| Release Version | crm-v2.0.0 |
| Branch | main |
| Commit SHA | `4480e107` |
| Deployed By | Release & Deployment Authority |
| Platform | Vercel (Frontend) |

---

## 1. Deployment Summary

### 1.1 Vercel Frontend

| Property | Value |
|----------|-------|
| Vercel Project | snad-team/snad-app |
| Root Directory | apps/web |
| Framework | Next.js 16.2.11 |
| Node Version | 24.x |
| Trigger | Git push to `main` |
| Deployments Triggered | 1 (automatic via Git integration) |
| Latest Deployment | ✅ Ready (Production) |
| Build Duration | ~40s |

### 1.2 Deployment Targets

| Environment | URL | Status |
|-------------|-----|--------|
| Production | https://snad-app.vercel.app | ✅ Active |
| Latest Deployment | https://snad-heyiohc0c-snad-team.vercel.app | ✅ Ready |

### 1.3 Backend

Backend deployment is managed separately (Render/Fly.io). The frontend
deployments communicate with the backend API via configured environment
variables. API connectivity is verified in post-deployment validation.

---

## 2. Build Verification

### 2.1 Backend (Maven)

| Step | Result |
|------|--------|
| `mvn clean` | ✅ Success |
| `mvn verify` | ✅ 920/920 non-Docker tests pass |
| Compilation errors | ✅ Zero |
| Test failures | ✅ Zero |

### 2.2 Frontend (Next.js)

| Step | Result |
|------|--------|
| `npm install` | ✅ Up to date |
| `npx tsc --noEmit` | ✅ Zero TypeScript errors |
| `npm run lint` | ⚠️ 6 known pre-existing errors (set-state-in-effect pattern) |
| `npm run build` | ✅ Success — all 27 pages generated |

### 2.3 Deployment Build

| Step | Result |
|------|--------|
| Vercel Build | ✅ Success (~40s) |
| Static Pages Generated | ✅ 27/27 |
| Edge Functions | ✅ Middleware deployed |

---

## 3. Routes Deployed

| Route | Type | Status |
|-------|------|--------|
| `/` | Static | ✅ |
| `/crm/command-center` | Static | ✅ |
| `/crm/leads` | Static | ✅ |
| `/crm/accounts` | Static | ✅ |
| `/crm/contacts` | Static | ✅ |
| `/crm/opportunities` | Static | ✅ |
| `/crm/pipelines` | Static | ✅ |
| `/crm/overview` | Static | ✅ |
| `/crm/activities` | Static | ✅ |
| `/crm/tasks` | Static | ✅ |
| `/crm/notes` | Static | ✅ |
| `/crm/tags` | Static | ✅ |
| `/crm/reports` | Static | ✅ |
| `/crm/search` | Static | ✅ |
| `/crm/settings/custom-fields` | Static | ✅ |
| `/crm/imports` | Static | ✅ |
| `/crm/integrations` | Static | ✅ |
| `/auth/forgot-password` | Static | ✅ |
| `/control-plane` | Static | ✅ |
| API routes | Dynamic (λ) | ✅ |

---

## 4. Post-Deployment Checks

| Check | Result | Evidence |
|-------|--------|----------|
| Production URL reachable | ✅ HTTP 200 | `https://snad-app.vercel.app` |
| CRM routes accessible | ✅ 9/9 HTTP 200 | All CRM tabs respond |
| API connectivity | ✅ Reachable, status 200 | `/api/system/backend-status` |
| Page content valid | ✅ CRM-specific keywords present | Content validation passed |
| No server errors | ✅ No 500/404 in production responses | Header inspection passed |

---

## 5. Environment Configuration

| Variable | Status |
|----------|--------|
| `VERCEL_OIDC_TOKEN` | ✅ Configured |
| `NEXT_PUBLIC_APP_URL` | ✅ Configured |
| Framework | ✅ Next.js (detected) |
| Node.js Version | ✅ 24.x |

---

*Report generated 2026-07-30 by Release & Deployment Authority*
