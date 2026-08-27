# ERP Operational UI Design

## Purpose
Turn the existing ERP Core from a dashboard/list viewer into a human-operable ERP workspace without replacing its domain model, persistence, security model, or business services.

## Current verified baseline
- The backend already owns items, suppliers, warehouses, inventory balances, reservations, transfers, adjustments, purchase requisitions, purchase orders, goods receipts, dashboard metrics, tenant isolation, capability enforcement, audit calls, migrations, and integration tests.
- The current web route `/erp` only renders dashboard counters and an item list.
- The current web API wrapper only exposes dashboard/list reads for items, suppliers, and warehouses.

## Success criteria
A signed-in authorized user can operate every currently implemented ERP business flow from the web UI:
1. Create and manage items, including lifecycle actions.
2. Create and manage suppliers, including activation/blocking.
3. Create and manage warehouses, including activation/archiving.
4. Inspect inventory balances and low-stock state.
5. Create, inspect, release, and confirm stock reservations.
6. Create, submit, and receive warehouse transfers.
7. Create and approve inventory adjustments.
8. Create, inspect, submit, approve, and reject purchase requisitions.
9. Create, inspect, submit, approve, and cancel purchase orders.
10. Create, inspect, and post goods receipts.
11. See all of the above through a coherent ERP navigation shell.
12. Preserve existing backend capability checks; the frontend does not bypass authorization.
13. Preserve tenant isolation and existing audit behavior.
14. Build and tests pass before deployment.
15. Human visual acceptance is performed on a Vercel preview before production merge.

## Architecture

### Backend
Keep existing services, tables, lifecycle rules, and write endpoints unchanged. Add only two missing read surfaces required for human operation:
- `GET /api/v1/erp/inventory/reservations` -> delegates to the already-existing `ErpInventoryReservationService.list(tenantId)`.
- `GET /api/v1/erp/inventory/movements` -> a new read-only tenant-scoped query in `ErpInventoryService` over the append-only movement ledger.

Both endpoints require `ERP.VIEW`. No migration is required.

### Frontend API contract
Expand `apps/web/lib/api/erp-api.ts` to mirror the backend contract using explicit request/response TypeScript types. All mutations use the existing authenticated `apiClient`. No new state-management or UI dependency is introduced.

### ERP workspace structure
Use a shared client-side ERP shell that preserves the existing `ExecutiveShell` and adds an ERP-local navigation bar. Routes:
- `/erp` — dashboard and quick links.
- `/erp/items` — item master, create form, lifecycle actions.
- `/erp/suppliers` — supplier master, create form, activation/blocking.
- `/erp/warehouses` — warehouse master, create form, activation/archive.
- `/erp/inventory` — balances, reservations, transfers, adjustments, and movement ledger.
- `/erp/requisitions` — purchase requisitions and workflow actions.
- `/erp/purchase-orders` — purchase orders and workflow actions.
- `/erp/goods-receipts` — receipts and posting.

### Interaction design
Follow the established operational CRM pattern:
- explicit loading/error/success states;
- create forms adjacent to operational tables;
- mutation buttons are disabled while requests are in flight;
- empty states explain the next actionable step;
- Arabic-first labels while preserving existing global language/dark-mode controls;
- no hidden mock data and no local optimistic business-state fabrication: reload server state after mutations.

For multi-line documents (requisitions, purchase orders, receipts, transfers), the initial operational UI supports one or more lines using an editable line collection in the form. It submits the exact backend DTO shape.

### Error handling
Use the existing API client/error conventions. Surface server validation/conflict/authorization failures as user-visible error banners. Do not convert failed writes into local success.

### Security
The UI is a convenience layer only. Backend capability checks remain authoritative:
- `ERP.VIEW`
- `ERP.WRITE`
- `ERP.ADMIN`
- `ERP.APPROVE`
- `ERP.INVENTORY`
- `ERP.PROCUREMENT`

No capability is added or weakened.

## Testing

### Backend
Add focused integration tests proving the two new read endpoints/service reads are tenant-scoped and return only the caller tenant's records.

### Frontend
Add Vitest tests for:
- ERP API mutation URLs and payloads.
- ERP workspace navigation contains every operational route.
- representative create flows (item, warehouse, purchase order) call the expected API and refresh data.
- inventory action coverage for reservation/transfer/adjustment.

### Build and preview
Run frontend tests, lint/build, relevant backend ERP tests, then open a PR to obtain a Vercel preview. Human visual acceptance is a release gate before production merge.

## Non-goals
- No redesign of ERP database tables.
- No replacement of ERP business services.
- No POS implementation.
- No HRM work.
- No accounting expansion beyond the existing purchase-order totals.
- No unrelated refactor of global shell or CRM.
