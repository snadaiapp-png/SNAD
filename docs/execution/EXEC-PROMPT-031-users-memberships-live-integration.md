# EXEC-PROMPT-031 — Users & Organization Memberships Live Integration

## Objective

Connect the SANAD frontend to the live Backend APIs for Users and Organization Memberships using the typed API client foundation from EXEC-PROMPT-029. Implement typed API modules, input validation, Arabic error mapper, and Arabic RTL UI panels — wired into the actual page route — with full lifecycle support.

## Status

```
IMPLEMENTED — PENDING CI AND PM REVIEW
```

## Base

- **Base SHA:** `bc42935b4808afd0a2700579bfd95ce4f971dec9` (EXEC-PROMPT-030 merge via PR #49)
- **Branch:** `feat/EXEC-PROMPT-031-users-memberships-live-integration`
- **Fresh clone:** Yes — created from `origin/main` at `bc42935`

## Scope

### In Scope

- `apps/web/lib/api/validation.ts` — UUID, email, displayName validators + normalizers
- `apps/web/lib/api/users.ts` — Typed Users API client (list, get, create, update, transition)
- `apps/web/lib/api/memberships.ts` — Typed Memberships API client (list, get, invite, transition)
- `apps/web/lib/api/user-facing-errors.ts` — Arabic error mapper (handles ApiRequestSerializationError from main)
- `apps/web/components/users-live-panel.tsx` — Arabic RTL users UI (matches OrganizationLivePanel design)
- `apps/web/components/memberships-live-panel.tsx` — Arabic RTL memberships UI
- `apps/web/app/page.tsx` — Wired `<UsersLivePanel />` and `<MembershipsLivePanel />` into the actual route
- Tests for all API modules
- Execution documentation + progress report update

### Out of Scope (deferred)

- Login / Logout / JWT / Refresh Tokens → EXEC-PROMPT-032+
- Session Management, Passwords, OAuth, SSO → EXEC-PROMPT-032+
- Authorization / RBAC enforcement → EXEC-PROMPT-032+
- Roles / Permissions UI → EXEC-PROMPT-032+
- Automatic Tenant Resolution → EXEC-PROMPT-035
- Global Tenant Context → EXEC-PROMPT-035
- Invite Email Delivery, Invite Acceptance Flow, Password Reset → later stages
- Backend Schema Migrations, Backend contract changes → not applicable
- Commercial production approval → separate gate
- EXEC-PROMPT-032 or any later stage → prohibited

## Backend Contracts Implemented

### Users (`/api/v1/users`)

| Method | Path | Query | Body | Response | Status |
|---|---|---|---|---|---|
| POST | `/api/v1/users` | `tenantId` | `{email, displayName, status?}` | `UserResponse` | 201 |
| GET | `/api/v1/users` | `tenantId` | — | `List<UserResponse>` | 200 |
| GET | `/api/v1/users/{userId}` | `tenantId` | — | `UserResponse` | 200 |
| PUT | `/api/v1/users/{userId}` | `tenantId` | `{email, displayName}` | `UserResponse` | 200 |
| PATCH | `/api/v1/users/{userId}/activate` | `tenantId` | — | `UserResponse` | 200 |
| PATCH | `/api/v1/users/{userId}/deactivate` | `tenantId` | — | `UserResponse` | 200 |
| PATCH | `/api/v1/users/{userId}/suspend` | `tenantId` | — | `UserResponse` | 200 |
| PATCH | `/api/v1/users/{userId}/archive` | `tenantId` | — | `UserResponse` | 200 |

**UserStatus enum:** `ACTIVE`, `INACTIVE`, `INVITED`, `SUSPENDED`, `ARCHIVED`
**Default status on create:** `INVITED` (when `status` omitted)
**Update does NOT send status** — lifecycle changes use PATCH endpoints.

### Organization Memberships (`/api/v1/organizations/{organizationId}/memberships`)

| Method | Path | Query | Body | Response | Status |
|---|---|---|---|---|---|
| POST | `.../memberships` | `tenantId` | `{tenantId, organizationId, email, displayName}` | `OrganizationMembershipResponse` | 201 |
| GET | `.../memberships` | `tenantId` | — | `List<OrganizationMembershipResponse>` | 200 |
| GET | `.../memberships/{membershipId}` | `tenantId` | — | `OrganizationMembershipResponse` | 200 |
| PATCH | `.../memberships/{membershipId}/activate` | `tenantId` | — | `OrganizationMembershipResponse` | 200 |
| PATCH | `.../memberships/{membershipId}/deactivate` | `tenantId` | — | `OrganizationMembershipResponse` | 200 |
| PATCH | `.../memberships/{membershipId}/remove` | `tenantId` | — | `OrganizationMembershipResponse` | 200 |

**MembershipStatus enum:** `ACTIVE`, `INACTIVE`, `INVITED`, `REMOVED`
**`remove` is a SOFT DELETE** (status → REMOVED), NOT HTTP DELETE.
**Body IDs MUST match URL IDs** — enforced client-side.

## Files Created

| File | Purpose |
|---|---|
| `apps/web/lib/api/validation.ts` | UUID/email/displayName validators + normalizers |
| `apps/web/lib/api/users.ts` | Typed Users API client (factory pattern matching organizations.ts) |
| `apps/web/lib/api/memberships.ts` | Typed Memberships API client (factory pattern) |
| `apps/web/lib/api/user-facing-errors.ts` | Arabic error mapper (handles ApiRequestSerializationError) |
| `apps/web/lib/api/validation.test.ts` | Validation tests |
| `apps/web/lib/api/users.test.ts` | Users API tests |
| `apps/web/lib/api/memberships.test.ts` | Memberships API tests |
| `apps/web/components/users-live-panel.tsx` | Arabic RTL users UI |
| `apps/web/components/memberships-live-panel.tsx` | Arabic RTL memberships UI |
| `docs/execution/EXEC-PROMPT-031-users-memberships-live-integration.md` | This document |

## Files Modified

| File | Change |
|---|---|
| `apps/web/lib/api/index.ts` | Added exports for validation, users, memberships, user-facing-errors |
| `apps/web/app/page.tsx` | Wired `<UsersLivePanel />` and `<MembershipsLivePanel />` into the route; preserved `<OrganizationLivePanel />` and `<SanadDashboard />` |
| `docs/execution/progress-report.md` | Added EXEC-PROMPT-031 entry |

## Conflict Resolution Summary

### Conflicts encountered and resolved:

1. **`lib/api/index.ts`** — main's version (from merged 029) exports `ApiRequestSerializationError` and `isApiRequestSerializationError` which my local 029 did not have. **Resolution:** preserved all of main's existing exports, appended new 031 exports at the end. No existing export removed.

2. **`lib/api/errors.ts`** — main's version has an additional `ApiRequestSerializationError` class. **Resolution:** my `user-facing-errors.ts` imports and handles this class (maps to "serialization" kind with Arabic message). No modification to `errors.ts` itself.

3. **`app/page.tsx`** — main already renders `<OrganizationLivePanel />` + `<SanadDashboard />`. **Resolution:** inserted `<UsersLivePanel />` and `<MembershipsLivePanel />` between them. Order: OrganizationLivePanel → UsersLivePanel → MembershipsLivePanel → SanadDashboard. Preserved DEMO mode (SanadDashboard) and existing LIVE mode (OrganizationLivePanel). Bumped version badge to v0.3.

4. **Component design language** — main's `organization-live-panel.tsx` uses a specific design (rounded-3xl, teal-800, amber "Pilot only" badge, etc.). **Resolution:** matched this exact design language in `users-live-panel.tsx` and `memberships-live-panel.tsx` for visual consistency.

5. **API module pattern** — main's `organizations.ts` uses a factory pattern (`createOrganizationsApi(client)`) returning an object, plus a singleton `organizationsApi`. **Resolution:** matched this exact pattern in `users.ts` (`createUsersApi`) and `memberships.ts` (`createMembershipsApi`).

## Security Controls

1. No secrets in code — no tokens, passwords, or credentials anywhere
2. No auth headers injected — Authorization is in protected-headers list
3. No hardcoded tenant UUID — tenantId passed explicitly per request
4. No Local Storage / Cookie usage — tenantId is component state only
5. No fake authentication — no placeholder tokens, no mock auth
6. Email normalized before transport — trimmed + lowercased
7. DisplayName normalized before transport — trimmed, empty → null
8. UUID validation before transport — invalid UUIDs throw ApiConfigurationError
9. Body/path/query ID consistency enforced — memberships invite validates body.tenantId === query.tenantId and body.organizationId === path.organizationId
10. No sensitive data in errors — user-facing error mapper strips stack traces, URLs, headers, response bodies
11. No console.log of email or IDs — components use state, not logging

## Test Results

```
npm ci       → exit 0
npm run lint → exit 0 (0 errors)
npm test     → exit 0 (all tests passed)
npm run build → exit 0 (Next.js 16.2.9 Turbopack, TypeScript passed)
git diff --check → clean
```

(Exact test counts recorded in the delivery report after execution.)

## Known Gaps / Deferred

- No retry logic (deferred per design)
- No auth token injection (deferred to EXEC-PROMPT-032+)
- No automatic tenant resolution (deferred to EXEC-PROMPT-035)
- Invite email delivery, acceptance flow, password reset → later stages

## Rollback Plan

To roll back:
1. Revert the merge commit on main (if already merged), OR
2. Close the Pull Request without merging, OR
3. Delete the branch

Changes are additive — no existing functionality removed. `OrganizationLivePanel`, `SanadDashboard`, and DEMO mode are fully preserved. The new panels are separate components rendered on the same page.

No data loss risk. No production impact. No backend changes.

## Stage Status

```
IMPLEMENTED — PENDING CI AND PM REVIEW
```

Does NOT transition to COMPLETE until:
- Remote branch exists ✓ (will be pushed)
- Pull Request exists ✓ (will be opened)
- All required CI checks pass ⏳ (pending CI run)
- PM review completed ⏳
- PR merged into main ⏳
- Post-merge verification completed ⏳

---

Executed For: Abdulrahman Sinan, SANAD Business Operating System
Date: 21 June 2026
