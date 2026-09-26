# Tenant Login Link Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let authorized control-plane operators open or copy an active tenant's preselected sign-in URL while recording each action in the platform audit log.

**Architecture:** The executive tenant table derives a same-origin `/?tenantId=<UUID>` URL for the application's existing root sign-in screen and exposes open/copy controls only for `ACTIVE` tenants and `EXECUTIVE_MANAGE` operators. Before opening or copying, the UI posts an event to a protected backend endpoint; the backend validates the tenant exists and is active, accepts only `OPEN` or `COPY`, and writes a `TENANT_LOGIN_LINK_<ACTION>` platform audit row without creating a session or token.

**Tech Stack:** Next.js 16, React 19, TypeScript, Vitest/Testing Library, Spring Boot 3.5, Java 21, JUnit 5, Mockito.

**Spec:** Approved in the chat immediately before implementation: safe tenant-specific login link, open/copy actions, disabled for archived/terminated tenants, no impersonation/token, audit logging.

## Global Constraints

- Never embed credentials, session identifiers, or login tokens in the URL.
- The backend is authoritative for eligibility and rejects non-active tenants fail-closed.
- Only control-plane users with `EXECUTIVE_MANAGE` may record or use the controls.
- Both Arabic and English labels must be present.

---

### Task 1: Backend audit command

**Files:**
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/executive/api/PlatformOperationsCommandController.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/executive/service/ExecutivePlatformService.java`
- Test: `apps/sanad-platform/src/test/java/com/sanad/platform/executive/api/PlatformOperationsTenantManagementContractTest.java`

**Interfaces:**
- Consumes: tenant UUID, authenticated control-plane principal, action `OPEN|COPY`.
- Produces: `POST /api/v1/executive/tenants/{tenantId}/login-link-events` returning HTTP 200 with the authoritative platform-owned `{hostname}` plus a durable platform audit row. The backend reconciles the tenant's generated APPLICATION hostname and fails closed when no routable base domain is configured.

- [x] Add failing controller/service tests for authorization, active-status validation, enum validation, and audit action.
- [ ] Run the focused Maven test and confirm the expected RED failure. *(Blocked before compilation: Spring Boot parent POM is unavailable and Maven Central DNS is restricted.)*
- [x] Implement the smallest command DTO, controller route, and service method.
- [ ] Run the focused Maven test and confirm GREEN.

### Task 2: Tenant-aware authentication entry

**Files:**
- Modify: `apps/web/components/auth/auth-entry.tsx`
- Test: `apps/web/components/auth/auth-entry.test.tsx`

**Interfaces:**
- Consumes: optional `tenantId` query parameter.
- Produces: the existing login API request with that tenant ID; invalid/missing values remain unscoped.

- [x] Add a failing test proving the query tenant ID is submitted with credentials.
- [x] Run the focused Vitest test and confirm RED.
- [x] Implement query parsing and pass the optional tenant ID through the existing auth provider.
- [x] Run the focused Vitest test and confirm GREEN.

### Task 3: Executive open/copy controls

**Files:**
- Modify: `apps/web/lib/api/executive-api.ts`
- Modify: `apps/web/app/executive/tenants/page.tsx`
- Modify: `apps/web/lib/i18n/locales/ar.ts`
- Modify: `apps/web/lib/i18n/locales/en.ts`
- Test: `apps/web/app/executive/tenants/tenant-management.test.tsx`

**Interfaces:**
- Consumes: active tenant ID and `executiveApi.recordTenantLoginLinkEvent(tenantId, action)`.
- Produces: audited open-in-new-tab and clipboard copy behavior using only the backend-returned isolated tenant hostname, plus user-visible success/error feedback. Same-origin fallback is forbidden because it would share the control-plane refresh cookie.

- [x] Add failing UI tests for active visibility, inactive hiding, literal URL, open/copy behavior, and audit failure.
- [x] Run the focused Vitest test and confirm RED.
- [x] Implement the API client and accessible controls with bilingual copy.
- [x] Run focused tests, full web tests, typecheck, lint, and build.
- [x] Run the focused backend tests and report any environment blocker exactly.
