# CRM-002 — Restore & Unify the Operational CRM UX — Evidence

| Field              | Value                                                |
| ------------------ | ---------------------------------------------------- |
| Branch             | `crm/002-restore-operational-ui`                     |
| Starting SHA       | `0f4e17993b76a7d1297b4e1e003cd190e6506809`           |
| Work item          | CRM-002 — Restore and unify the operational CRM UI   |
| Date               | 2026-07-12                                           |
| Owner              | SNAD CRM front-office engineering                    |

---

## 1. Objective

Transform `/crm` from an empty-state Command Center shell into a **real,
API-connected operational CRM workspace**. The operational UI (`CrmWorkspaceV2`)
already existed on disk but was not wired into the `/crm` route. This change
connects it without deleting any prior governance or execution-board artifacts.

## 2. Routes implemented

| Route                       | File                                                    | Component rendered                                  | Purpose                                                     |
| --------------------------- | ------------------------------------------------------- | --------------------------------------------------- | ----------------------------------------------------------- |
| `/crm`                      | `apps/web/app/crm/page.tsx`                             | `<CrmWorkspaceV2 />` + `<CrmAdvancedView />`        | Operational CRM: dashboard, accounts, contacts, leads, opportunities, activities, customer 360, pipeline kanban board. |
| `/crm/command-center`       | `apps/web/app/crm/command-center/page.tsx`              | `<CrmCommandCenterPage />` (default export)         | Preserves the 16-tab governance Command Center + Execution Board at a dedicated URL. |
| `/workspace`                | `apps/web/app/workspace/page.tsx`                       | (existing) Workspace card                           | Adds primary "Open CRM" button → `/crm` and secondary "CRM Command Center" button → `/crm/command-center`. |

### 2.1 `/crm` page (after)

```tsx
import { CrmWorkspaceV2 } from "./crm-workspace-v2";
import { CrmAdvancedView } from "./crm-advanced-view";

export default function CrmPage() {
  return (
    <>
      <CrmWorkspaceV2 />
      <CrmAdvancedView />
    </>
  );
}
```

### 2.2 `/crm/command-center` page (new)

```tsx
import CrmCommandCenterPage from "../crm-command-center";

export default function CrmCommandCenterRoute() {
  return <CrmCommandCenterPage />;
}
```

### 2.3 Workspace buttons

Two new i18n keys were added (`workspace.openCrm`, `workspace.openCrmCommandCenter`)
to both `ar.ts` and `en.ts`. The primary button keeps the `workspacePrimaryButton`
style; the secondary uses `workspaceLogoutButton`.

## 3. Backend endpoints connected

All calls go through `apps/web/lib/api/crm.ts` (`crmApi.*`). Every endpoint is
under `/api/v1/crm/*` and is implemented by
`apps/sanad-platform/src/main/java/com/sanad/platform/crm/web/CrmController.java`
(44 endpoint mappings total). The operational UI exercises the following subset:

| Frontend action                       | `crmApi` method          | HTTP call                                            |
| ------------------------------------- | ------------------------ | ---------------------------------------------------- |
| Dashboard metrics                     | `dashboard()`            | `GET  /api/v1/crm/dashboard`                         |
| List / create / archive accounts      | `accounts()`, `createAccount()`, `archiveAccount()` | `GET /accounts`, `POST /accounts`, `PATCH /accounts/{id}/archive` |
| Restore accounts                      | `restoreAccount()`       | `PATCH /accounts/{id}/restore`                       |
| Customer 360 drawer                   | `customer360(id)`        | `GET  /accounts/{id}/customer-360`                   |
| List / create / archive contacts      | `contacts()`, `createContact()`, `archiveContact()` | `GET /contacts`, `POST /contacts`, `PATCH /contacts/{id}/archive` |
| List / create / status-change / convert leads | `leads()`, `createLead()`, `changeLeadStatus()`, `convertLead()` | `GET /leads`, `POST /leads`, `PATCH /leads/{id}/status`, `POST /leads/{id}/convert` |
| List / create pipelines + stages      | `pipelines()`, `createPipeline()`, `stages(id)` | `GET /pipelines`, `POST /pipelines`, `GET /pipelines/{id}/stages` |
| List / create opportunities           | `opportunities()`, `createOpportunity()` | `GET /opportunities`, `POST /opportunities`          |
| Move opportunity stage (drag-drop)    | `moveOpportunity(id, stageId)` | `PATCH /opportunities/{id}/stage`                    |
| List / create / complete activities   | `activities()`, `createActivity()`, `completeActivity()` | `GET /activities`, `POST /activities`, `PATCH /activities/{id}/complete` |
| Timeline (Customer 360)               | (via `customer360()`)    | `GET  /timeline/{subjectType}/{subjectId}`           |

## 4. Components reused (no new CRM components created)

| Component                    | File                                            | Notes |
| ---------------------------- | ----------------------------------------------- | ----- |
| `CrmWorkspaceV2`             | `apps/web/app/crm/crm-workspace-v2.tsx`         | Main operational tabs (accounts / contacts / leads / sales / activities) + metrics + Customer 360 drawer. |
| `CrmAdvancedView`            | `apps/web/app/crm/crm-advanced-view.tsx`        | Drag-and-drop kanban board + virtualized opportunities table. |
| `CrmPipelineBoard`           | `apps/web/app/crm/crm-pipeline-board.tsx`       | Kanban DnD surface used by `CrmAdvancedView`. |
| `CrmVirtualTable`            | `apps/web/app/crm/crm-virtual-table.tsx`        | Windowed table used by `CrmAdvancedView`. |
| `crmApi`                     | `apps/web/lib/api/crm.ts`                       | API client (typed). |
| `toUserFacingError`          | `apps/web/lib/api/user-facing-errors.ts`        | Error normalization. |
| `useAuth`                    | `apps/web/lib/auth/auth-provider.tsx`           | Auth gating + user identity. |
| `AuthLoadingState`           | `apps/web/components/auth/auth-loading-state.tsx` | Loading fallback during session bootstrap. |
| `CrmCommandCenterPage`       | `apps/web/app/crm/crm-command-center.tsx`       | Preserved verbatim, now mounted at `/crm/command-center`. |

### Files NOT deleted (per constraints)

- `crm-i18n.tsx`, `crm-execution-data.ts`, `crm-execution-board.tsx`,
  `crm-command-center.tsx`, `crm-empty-state.tsx`, `crm-overview.tsx` — all
  retained because the Command Center route still depends on them.

## 5. CSS class — `workspacePrimaryButton`

Already present in `apps/web/components/auth/auth.module.css` (lines ~691–714).
No addition was required; verified by reading the file. The class styles the
primary "Open CRM" button on `/workspace` (brand-primary background, inverse
text, 48px min-height, focus-visible ring).

## 6. Governance drift check update

`scripts/crm/governance-drift-check.sh` now includes a new check (#12) that
fails closed if `apps/web/app/crm/page.tsx` does not import `CrmWorkspaceV2`
from `./crm-workspace-v2`. This prevents a future regression from silently
reverting `/crm` back to the empty-state Command Center.

The check is purely additive — all prior governance checks (baseline presence,
migration reconciliation, capability count, AI-CRM claims, production-GO
authorization, etc.) remain unchanged.

## 7. Verification performed

| Check                                    | Command                                         | Result |
| ---------------------------------------- | ----------------------------------------------- | ------ |
| TypeScript compile (`apps/web`)          | `bunx tsc --noEmit -p apps/web/tsconfig.json`   | PASS   |
| ESLint                                   | `bun run lint`                                  | PASS   |
| Governance drift check                   | `bash scripts/crm/governance-drift-check.sh`    | PASS   |
| CrmPipelineBoard / CrmVirtualTable tests | `bunx vitest run apps/web/app/crm/crm-interactions.test.tsx` | PASS (unchanged) |

## 8. Known limitations

1. **No new API endpoints or G1 tables** were created — this work item is
   strictly a front-end rewiring of `/crm`. Backend capabilities remain at the
   pre-existing CRM-G0 baseline.
2. **`CrmWorkspaceV2` and `CrmAdvancedView` each load their own copy of the
   same data** (accounts, pipelines, opportunities) on mount. They do not share
   a cache. This was already the case before this change and is acceptable for
   the operational MVP; a future task may introduce a shared TanStack Query
   cache to deduplicate the requests.
3. **The Command Center's empty-state tabs** (leads, customers, contacts,
   opportunities, pipeline, tasks, transfers, employees, reports, mobileSync,
   callerId, aiCrm, billing, settings) remain empty states at
   `/crm/command-center`. This is intentional — the operational equivalents now
   live at `/crm`. The governance drift check continues to forbid docs from
   presenting those tabs as delivered features.
4. **No mock data was added.** If the backend is unreachable, the workspace
   renders an error banner via `toUserFacingError`; it never shows fake rows.
5. **Auth gating** is delegated to `useAuth()` inside `CrmWorkspaceV2` — when
   `state` is `ANONYMOUS`/`EXPIRED`/`ERROR`/`CREDENTIAL_ROTATION_REQUIRED` the
   component redirects to `/`. The `/crm` page itself is a server component
   shell and does not perform its own redirect; this matches the existing
   pattern used by `/workspace`.
6. **The new `/crm/command-center` route** is a thin server-component wrapper
   around `CrmCommandCenterPage`. The Command Center component remains a
   client component (`"use client"`).

## 9. Files touched

```
apps/web/app/crm/page.tsx                            (rewritten)
apps/web/app/crm/command-center/page.tsx             (new)
apps/web/app/workspace/page.tsx                      (edited — buttons + labels)
apps/web/components/auth/auth.module.css             (no change — verified)
apps/web/lib/i18n/locales/ar.ts                      (added 2 keys)
apps/web/lib/i18n/locales/en.ts                      (added 2 keys)
scripts/crm/governance-drift-check.sh                (added check #12)
docs/crm/evidence/CRM-002-OPERATIONAL-UI-EVIDENCE.md (this file)
```
