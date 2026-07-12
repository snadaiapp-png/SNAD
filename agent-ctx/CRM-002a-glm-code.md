# Task CRM-002a — Complete Operational CRM UI with URL-based routing

## Agent
GLM Code (single agent)

## Branch
crm/002a-complete-operational-ui

## Starting SHA
18aa875819f41de34d972c56a9d2e15695c50eb8

## Work Summary
Migrated the operational CRM from a monolithic `useState`-driven single page
(`/crm` rendering `CrmWorkspaceV2` + `CrmAdvancedView`) to a route-based
architecture with URL-aware navigation and per-route data loading.

## Routes Created
- `/crm` → server-side `redirect("/crm/overview")`
- `/crm/overview` — KPI dashboard via `crmApi.dashboard()`
- `/crm/accounts` — list + create + archive/restore + search
- `/crm/accounts/[accountId]` — Customer 360 detail
- `/crm/contacts` — list + create + archive + search
- `/crm/leads` — list + create + status filter + qualify/disqualify + convert
- `/crm/pipelines` — list + create + inline stages
- `/crm/opportunities` — pipeline board + virtualized table + create
- `/crm/activities` — list + create + status filter + complete
- `/crm/imports` — upload + list + job detail + error CSV download (NEW)
- `/crm/settings/custom-fields` — admin UI for custom field definitions (NEW)
- `/crm/command-center` — kept as-is, lives outside the operational layout

## Architecture Decisions
1. **Route group `(operational)`**: All new operational pages live under
   `/crm/(operational)/*` so the `CrmShell` layout applies ONLY to them,
   not to `/crm/command-center` (which retains its own independent shell).
2. **Server-side redirect at `/crm`**: `apps/web/app/crm/page.tsx` calls
   `redirect("/crm/overview")` for a clean entry point.
3. **Per-route data loading**: Each page calls only the API methods it needs.
4. **Auth gating in layout**: `CrmShell` (rendered by `(operational)/layout.tsx`)
   handles `useAuth()` and `AuthLoadingState` so child pages can assume
   `state === "AUTHENTICATED"`.
5. **URL-aware active link**: `CrmShell` uses `usePathname()` for sidebar
   active state — deep links and browser back/forward keep nav in sync.

## Files Modified
- `apps/web/lib/api/crm.ts` — added 11 new methods (imports, customFields,
  downloadImportErrorsCsv); added CrmImportJob, CrmImportErrorRow,
  CrmCustomField, CrmCustomFieldValues, CrmCustomFieldValueEntry interfaces
- `apps/web/lib/api/client.ts` — added `getBlob()` for non-JSON authenticated
  GETs; updated `mergeHeaders` + `request()` to detect FormData/Blob bodies
  and skip JSON serialization so the browser manages the multipart boundary
- `apps/web/lib/i18n/locales/ar.ts` — added ~200 new keys
- `apps/web/lib/i18n/locales/en.ts` — added ~200 new keys (mirrored)
- `apps/web/app/crm/crm.module.css` — added skeleton, KPI, badge, overview
  section, page-title utility classes
- `apps/web/app/crm/page.tsx` — replaced monolithic mount with server-side
  redirect to `/crm/overview`
- `apps/web/app/crm/crm-workspace-v2.tsx` — added `@deprecated` JSDoc notice
- `apps/web/app/crm/crm-advanced-view.tsx` — added `@deprecated` JSDoc notice
- `docs/crm/evidence/CRM-002-OPERATIONAL-UI-EVIDENCE.md` — rewrote with the
  new route inventory, endpoint coverage, components, and known limitations
- `scripts/crm/governance-drift-check.sh` — replaced the CrmWorkspaceV2
  assertion with a redirect-to-overview assertion plus existence checks for
  /crm/(operational)/overview, /accounts, /imports, /settings/custom-fields

## Files Created
- `apps/web/app/crm/(operational)/layout.tsx`
- `apps/web/app/crm/(operational)/overview/page.tsx`
- `apps/web/app/crm/(operational)/accounts/page.tsx`
- `apps/web/app/crm/(operational)/accounts/[accountId]/page.tsx`
- `apps/web/app/crm/(operational)/contacts/page.tsx`
- `apps/web/app/crm/(operational)/leads/page.tsx`
- `apps/web/app/crm/(operational)/pipelines/page.tsx`
- `apps/web/app/crm/(operational)/opportunities/page.tsx`
- `apps/web/app/crm/(operational)/activities/page.tsx`
- `apps/web/app/crm/(operational)/imports/page.tsx`
- `apps/web/app/crm/(operational)/settings/custom-fields/page.tsx`
- `apps/web/app/crm/components/crm-shell.tsx`
- `apps/web/app/crm/components/crm-loading.tsx`
- `apps/web/app/crm/components/crm-error.tsx`
- `apps/web/app/crm/components/crm-empty.tsx`

## Verification
- `bun run lint` — clean (0 errors, 0 warnings)
- `bun run test` — 376/376 passing (33 test files)
- `bash scripts/crm/governance-drift-check.sh` — PASS

## Constraints Honored
- Did NOT delete crm-workspace-v2.tsx, crm-advanced-view.tsx,
  crm-pipeline-board.tsx, crm-virtual-table.tsx, crm-command-center.tsx,
  crm-i18n.tsx, crm-execution-data.ts, crm-execution-board.tsx,
  crm-empty-state.tsx, crm-overview.tsx
- Did NOT create mock data — every page fetches real data from crmApi
- Did NOT create G1 tables
- Did NOT change backend API contracts
- Used the existing API client (crmApi) for all data fetching
- Used useAuth() for auth checks
- Used useI18n() for translations
- All pages handle loading, error, empty, and success states

## Known Limitations (carried forward to CRM-003)
- Deprecated components (CrmWorkspaceV2, CrmAdvancedView) are kept for
  reference; scheduled for removal in EXEC-PROMPT-CRM-003
- Custom-field values (per-entity read/upsert) endpoints are wired in the
  API client but not yet surfaced in a UI page
- Import "mapping" UI not yet exposed (API client supports it)
- No Playwright E2E tests added for the new routes (existing visual
  regression tests cover the /crm redirect only)

## Next Prompt
EXEC-PROMPT-CRM-003
