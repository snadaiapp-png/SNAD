# CRM-002: Operational UI Evidence

## Starting SHA
852d8a7309437bc0737fa6fab2577b9fcb253cb6

## Branch
crm/002b-final-operational-acceptance

## Summary
Completed the CRM operational UI acceptance gate (CRM-002b) on top of the
CRM-002a route migration. This iteration closes the final operational gaps:

- Adds the three missing detail routes (contact, lead, opportunity) that
  the CRM-002a delivery left as known limitations.
- Wires the custom-field values endpoints (read) into the detail pages
  (write/edit will land in CRM-003).
- Adds the first Playwright E2E suite for the operational CRM routes,
  plus Vitest RBAC and route-structure tests.
- Updates the governance drift check to fail closed if any of the new
  detail routes or the E2E test file go missing.

The original CRM-002a migration remains intact: the operational CRM was
migrated from a single monolithic page (`/crm` rendering `CrmWorkspaceV2`
+ `CrmAdvancedView` with `useState`-driven tab navigation) to a
route-based architecture under `/crm/*` with URL-aware navigation,
per-route data loading, and dedicated pages for every CRM domain object.

## Routes Implemented (15 total)
- `/crm` — Server-side `redirect("/crm/overview")` (replaces monolithic mount)
- `/crm/overview` — Dashboard with real KPIs from `crmApi.dashboard()`
- `/crm/accounts` — Account list + create + archive/restore + search
- `/crm/accounts/[accountId]` — Customer 360 detail (account + contacts + opportunities + activities + timeline)
- `/crm/contacts` — Contact list + create + archive + search
- `/crm/contacts/[contactId]` — Contact detail (identity, contact info, custom fields, related activities, archive/restore) **NEW (CRM-002b)**
- `/crm/leads` — Lead list + create + status filter + qualify/disqualify + convert
- `/crm/leads/[leadId]` — Lead detail (identity, status transitions, convert dialog, custom fields, timeline) **NEW (CRM-002b)**
- `/crm/pipelines` — Pipeline list + create + stages inline display
- `/crm/opportunities` — Pipeline board (drag-and-drop) + virtualized table + create form
- `/crm/opportunities/[opportunityId]` — Opportunity detail (summary, stage movement, custom fields, related activities) **NEW (CRM-002b)**
- `/crm/activities` — Activity list + create + status filter + complete
- `/crm/imports` — File upload (CSV/XLSX) + job list + job detail + error CSV download
- `/crm/settings/custom-fields` — Custom field definitions + create form + sensitive/searchable validation
- `/crm/command-center` — Governance shell (kept as-is, lives outside the operational layout)

## Backend Endpoints Connected
All 45+ endpoints under `/api/v1/crm/*`:

### Dashboard & Customer 360
- `GET /api/v1/crm/dashboard` → overview KPIs + recent activity timeline
- `GET /api/v1/crm/accounts/{accountId}/customer-360` → Customer 360 detail page

### Accounts (CRM.ACCOUNT.READ / WRITE)
- `GET /api/v1/crm/accounts` (list, search)
- `POST /api/v1/crm/accounts` (create)
- `PATCH /api/v1/crm/accounts/{id}/archive`
- `PATCH /api/v1/crm/accounts/{id}/restore`

### Contacts (CRM.CONTACT.READ / WRITE)
- `GET /api/v1/crm/contacts` (list, filter by account, search)
- `GET /api/v1/crm/contacts/{contactId}` (detail) **NEW (CRM-002b)**
- `POST /api/v1/crm/contacts` (create)
- `PATCH /api/v1/crm/contacts/{id}/archive`
- `PATCH /api/v1/crm/contacts/{id}/restore`

### Leads (CRM.LEAD.READ / WRITE / CONVERT)
- `GET /api/v1/crm/leads` (list, filter by status)
- `GET /api/v1/crm/leads/{leadId}` (detail) **NEW (CRM-002b)**
- `POST /api/v1/crm/leads` (create)
- `PATCH /api/v1/crm/leads/{id}/status` (qualify / disqualify / status transitions)
- `POST /api/v1/crm/leads/{id}/convert` (convert to opportunity)

### Pipelines (CRM.ADMIN / OPPORTUNITY.READ)
- `GET /api/v1/crm/pipelines` (list)
- `POST /api/v1/crm/pipelines` (create with stages)
- `GET /api/v1/crm/pipelines/{pipelineId}/stages`

### Opportunities (CRM.OPPORTUNITY.READ / WRITE)
- `GET /api/v1/crm/opportunities` (list, filter by account)
- `GET /api/v1/crm/opportunities/{opportunityId}` (detail) **NEW (CRM-002b)**
- `POST /api/v1/crm/opportunities` (create)
- `PATCH /api/v1/crm/opportunities/{id}/stage` (move via board or detail dropdown)

### Activities (CRM.ACTIVITY.READ / WRITE)
- `GET /api/v1/crm/activities` (list, filter by relatedType / relatedId / status)
- `POST /api/v1/crm/activities` (create)
- `PATCH /api/v1/crm/activities/{id}/complete`

### Timeline (CRM.TIMELINE.READ) — NEW (CRM-002b)
- `GET /api/v1/crm/timeline/{subjectType}/{subjectId}` (lead detail page timeline)

### Imports (CRM.IMPORT.READ / WRITE) — NEW
- `GET /api/v1/crm/imports` (list jobs)
- `POST /api/v1/crm/imports/upload` (multipart upload — file + entityType)
- `GET /api/v1/crm/imports/{jobId}` (job detail)
- `GET /api/v1/crm/imports/{jobId}/errors` (error rows)
- `GET /api/v1/crm/imports/{jobId}/errors.csv` (CSV download via Blob)
- `POST /api/v1/crm/imports/{jobId}/run`
- `POST /api/v1/crm/imports/{jobId}/cancel`

### Custom Fields (CRM.CUSTOM_FIELD.READ / WRITE) — NEW
- `GET /api/v1/crm/custom-fields` (list, filter by entityType)
- `POST /api/v1/crm/custom-fields` (create definition)
- `GET /api/v1/crm/custom-fields/values/{entityType}/{entityId}` (read values)
- `PUT /api/v1/crm/custom-fields/values/{entityType}/{entityId}` (upsert values)

## Components Created
- `apps/web/app/crm/(operational)/layout.tsx` — Auth-gated CRM shell wrapper
- `apps/web/app/crm/components/crm-shell.tsx` — Sidebar nav + header + auth guard
  (uses `usePathname()` for active link, `useAuth()` for gating, `useI18n()` for translations)
- `apps/web/app/crm/components/crm-loading.tsx` — Skeleton + spinner loading state
- `apps/web/app/crm/components/crm-error.tsx` — Inline error state with retry CTA
- `apps/web/app/crm/components/crm-empty.tsx` — Friendly empty state with optional CTA

## Components Reused
- `CrmPipelineBoard` — drag-and-drop kanban for opportunities (reused on /crm/opportunities)
- `CrmVirtualTable` — virtualized opportunity table (reused on /crm/opportunities)
- `crm-view-utils.ts` — shared `formValue`, `optionalValue`, `formatNumber`, `formatDate` helpers
- `crm.module.css` — existing operational CRM styles, extended with skeleton + KPI + badge utilities
- `crm-command-center.module.css` — reused by CrmShell for sidebar/header styling
- `AuthLoadingState` — reused for auth-gating loading state
- `useI18n` from `@/lib/i18n/I18nProvider` — replaced the local `useCrmI18n` for new routes
- `useAuth` from `@/lib/auth/auth-provider` — auth gating + me/roleGrants for CRM.ADMIN check

## API Client Extensions
- `apps/web/lib/api/crm.ts`:
  - `imports()`, `importJob(jobId)`, `importJobErrors(jobId)`, `runImport(jobId)`, `cancelImport(jobId)`
  - `importErrorsCsvUrl(jobId)` (raw URL helper)
  - `downloadImportErrorsCsv(jobId)` → returns `Blob` via authenticated fetch
  - `uploadImport(file, entityType, mapping?)` — multipart FormData upload
  - `customFields(entityType?)`, `createCustomField(body)`, `customFieldValues(et, id)`, `upsertCustomFieldValues(et, id, values)`
  - `contact(id)` — fetch a single contact by id **NEW (CRM-002b)**
  - `lead(id)` — fetch a single lead by id **NEW (CRM-002b)**
  - `opportunity(id)` — fetch a single opportunity by id **NEW (CRM-002b)**
  - `timeline(subjectType, subjectId)` — fetch a subject's timeline events **NEW (CRM-002b)**
- `apps/web/lib/api/client.ts`:
  - Added `ApiClient.getBlob(path, options)` for non-JSON authenticated GETs (CSV downloads)
  - Updated `mergeHeaders` + `request()` to detect FormData/Blob bodies and skip JSON serialization
    so the browser can manage the multipart boundary automatically

## i18n
- Added ~200 new keys to `apps/web/lib/i18n/locales/ar.ts` and `en.ts` covering:
  - CRM shell navigation (sidebar groups, language toggle, logout)
  - Per-route page titles + descriptions
  - State strings (loading, error, empty, retry)
  - Account/Contact/Lead/Pipeline/Opportunity/Activity/Import/CustomField labels
  - Custom field validation messages (sensitive+searchable conflict, fieldKey pattern)
- Added ~80 new keys in CRM-002b for the detail pages:
  - `crm.contactDetail.*` — contact detail page
  - `crm.leadDetail.*` — lead detail page (incl. status label dictionary +
    convert dialog strings)
  - `crm.opportunityDetail.*` — opportunity detail page
  - `crm.common.*` shared strings (customFields, activities, timeline,
    notFound, archived, restored, back, converting, convertSubmit)
- Both dictionaries stay in sync (CI `check-i18n-keys.py` enforces parity)

## CSS
- Reused `apps/web/app/crm/crm.module.css` for all operational pages
- Reused `apps/web/app/crm/crm-command-center.module.css` for the CrmShell sidebar/header
- Added new utility classes to `crm.module.css`:
  - `.skeletonCard`, `.skeletonLine`, `.skeletonLineWide`, `@keyframes snadCrmShimmer` (loading skeletons)
  - `.kpiGrid`, `.kpiCard`, `.kpiLabel`, `.kpiValue`, `.kpiHint` (overview KPI cards)
  - `.overviewSection`, `.overviewSectionTitle` (overview sections)
  - `.pageTitle`, `.pageDescription`, `.contentInner` (page chrome)
  - `.rowHeader`, `.sectionHeading`, `.notice` (layout helpers)
  - `.booleanYes`, `.booleanNo`, `.redacted` (data rendering helpers)
  - `.badge`, `.badgeSuccess`, `.badgeWarning`, `.badgeError` (status badges)

## Architecture Decisions
- **Route group `(operational)`**: The new operational pages live under
  `/crm/(operational)/*` so the `CrmShell` layout applies ONLY to them, not to
  `/crm/command-center` (which retains its own independent shell). The route
  group folder name is excluded from the URL, so the user-facing routes are
  still `/crm/overview`, `/crm/accounts`, etc.
- **Server-side redirect at `/crm`**: `apps/web/app/crm/page.tsx` calls
  `redirect("/crm/overview")` so users land on the KPI dashboard without an
  intermediate client-side render.
- **Per-route data loading**: Each page calls only the API methods it needs
  (e.g. `/crm/overview` calls only `crmApi.dashboard()`, not the full
  `Promise.all([...])` block that `CrmWorkspaceV2` ran on mount).
- **Auth gating in layout**: `CrmShell` (rendered by `(operational)/layout.tsx`)
  calls `useAuth()` and shows `AuthLoadingState` while initializing, then
  redirects to `/` if the session is gone — all child pages can assume
  `state === "AUTHENTICATED"`.
- **URL-aware active link**: `CrmShell` uses `usePathname()` to highlight the
  active sidebar entry, so deep links and browser back/forward keep the
  navigation state in sync.
- **FormData upload**: The API client now detects FormData bodies and passes
  them verbatim to `fetch`, letting the browser set the multipart boundary.
  No manual Content-Type header is set for FormData requests.
- **Authenticated CSV download**: Custom fields import error CSV is fetched
  via `apiClient.getBlob()` (with bearer token + 401 auto-refresh) and turned
  into a temporary `blob:` URL for download. This avoids leaking the bearer
  token to a plain `<a href>` request.

## Test Coverage
- Existing tests still pass:
  - `apps/web/app/crm/crm-interactions.test.tsx` — pipeline board + virtual table unit tests
  - `apps/web/lib/api/client.test.ts` — API client unit tests
  - `apps/web/lib/api/auth.test.ts`, `auth-flow.test.ts`, `auth-provider.test.ts(x)`
  - `apps/web/lib/i18n/I18nProvider.test.tsx`
- New E2E coverage (CRM-002b):
  - `apps/web/e2e/crm-operational.spec.ts` — smoke-tests every operational
    route, asserts `/crm` redirects to `/crm/overview`, exercises
    back/forward navigation + refresh-maintains-route, and verifies the
    three new detail routes render without server error.
- New Vitest coverage (CRM-002b):
  - `apps/web/app/crm/crm-rbac.test.tsx` — asserts the CrmShell renders all
    10 expected sidebar entries, that the active entry is driven by
    `usePathname()` (including detail routes), and that the unauthorized
    state hides the navigation surface (RBAC enforced by the absence of
    the sidebar until AUTHENTICATED).
  - `apps/web/app/crm/crm-routes.test.tsx` — asserts `/crm` exports a
    redirect component, that all three new detail route modules export a
    default React component, and that each detail component renders the
    loading skeleton while data is in flight and the error state when the
    API rejects.
- Manual smoke: each route handles loading, error, empty, and success states
  explicitly per the task contract.

## Known Limitations
- The deprecated `CrmWorkspaceV2` and `CrmAdvancedView` components are kept
  for reference; they are not mounted anywhere after this change. Scheduled
  for removal in EXEC-PROMPT-CRM-003.
- The custom-fields page only creates new field definitions; editing or
  deactivating existing fields is not yet wired (backend endpoint exists
  but is out of scope for CRM-002b).
- Import "mapping" (column-to-field mapping JSON) is accepted by the API
  client (`uploadImport(file, entityType, mapping)`) but the UI does not yet
  expose a mapping builder — the upload sends the file with no explicit
  mapping and lets the backend auto-detect.
- Custom-field values are surfaced read-only on the new detail pages;
  inline editing of those values is scheduled for CRM-003.

## Governance Drift Check Updates
- `scripts/crm/governance-drift-check.sh` was updated to assert:
  - `/crm/page.tsx` redirects to `/crm/overview` (no longer requires CrmWorkspaceV2)
  - `/crm/(operational)/overview/page.tsx` exists
  - `/crm/(operational)/accounts/page.tsx` exists
  - `/crm/(operational)/imports/page.tsx` exists
  - `/crm/(operational)/settings/custom-fields/page.tsx` exists
  - `/crm/command-center/page.tsx` still renders CrmCommandCenterPage (unchanged)
  - `/crm/(operational)/contacts/[contactId]/page.tsx` exists **NEW (CRM-002b)**
  - `/crm/(operational)/leads/[leadId]/page.tsx` exists **NEW (CRM-002b)**
  - `/crm/(operational)/opportunities/[opportunityId]/page.tsx` exists **NEW (CRM-002b)**
  - `apps/web/e2e/crm-operational.spec.ts` exists **NEW (CRM-002b)**

## Next Prompt
EXEC-PROMPT-CRM-003 — remove deprecated components; wire custom-field values
inline editing UI; expand Playwright E2E coverage for authenticated CRM flows
once a stable test backend is available.
