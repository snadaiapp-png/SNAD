# ERP Operational UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expose the existing ERP Core as a complete human-operable web workspace and add only the missing tenant-scoped read endpoints required to inspect reservations and inventory movements.

**Architecture:** Preserve the existing Spring ERP domain/services/database and capability model. Expand the typed Next.js ERP API client, add a shared ERP workspace/navigation layer, and implement focused operational pages that reload authoritative server state after mutations. No new dependency or migration is introduced.

**Tech Stack:** Java 17+/Spring Boot/JdbcTemplate, Next.js 16, React 19, TypeScript 5.9, Vitest 4, existing SNAD `apiClient` and `ExecutiveShell`.

**Spec:** `docs/superpowers/specs/2026-08-27-erp-operational-ui-design.md`

## Global Constraints
- Do not modify ERP table schemas or migrations.
- Do not weaken `ERP.VIEW`, `ERP.WRITE`, `ERP.ADMIN`, `ERP.APPROVE`, `ERP.INVENTORY`, or `ERP.PROCUREMENT` enforcement.
- All reads and writes remain tenant-scoped.
- No mock ERP data in production UI.
- No POS, HRM, accounting, CRM, Render, or unrelated refactor work.
- Human visual acceptance on Vercel preview is required before production merge.

---

### Task 1: Close ERP read-surface gaps

**Files:**
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/erp/application/ErpInventoryService.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/erp/api/ErpController.java`
- Test: `apps/sanad-platform/src/test/java/com/sanad/platform/erp/ErpModuleIntegrationTest.java`

**Interfaces:**
- Produces: `ErpInventoryService.listMovements(UUID tenantId)`
- Produces: `GET /api/v1/erp/inventory/reservations`
- Produces: `GET /api/v1/erp/inventory/movements`

- [ ] Add failing integration coverage proving reservation and movement reads return only the requested tenant.
- [ ] Run the focused ERP integration test and confirm failure because the movement read/API surface is absent.
- [ ] Implement `listMovements(UUID tenantId)` as a read-only query ordered by `created_at DESC`.
- [ ] Add the two `ERP.VIEW` controller GET endpoints delegating to existing tenant-scoped services.
- [ ] Re-run focused ERP tests and confirm pass.

### Task 2: Expand the typed ERP web API client

**Files:**
- Modify: `apps/web/lib/api/erp-api.ts`
- Create: `apps/web/lib/api/erp-api.test.ts`

**Interfaces:**
- Produces typed CRUD/lifecycle methods for items, suppliers, warehouses, balances, reservations, movements, transfers, adjustments, requisitions, purchase orders, and goods receipts.

- [ ] Write failing Vitest assertions for representative URLs/payloads and lifecycle methods.
- [ ] Expand request/response types to match `ErpDtos.java` exactly.
- [ ] Implement all GET/POST/PUT methods using the existing `apiClient`.
- [ ] Run the ERP API client tests and confirm pass.

### Task 3: Build shared ERP workspace primitives

**Files:**
- Create: `apps/web/app/erp/components/erp-workspace.tsx`
- Create: `apps/web/app/erp/components/erp-feedback.tsx`
- Create: `apps/web/app/erp/erp.module.css`
- Create: `apps/web/app/erp/components/erp-workspace.test.tsx`

**Interfaces:**
- Produces: `ErpWorkspace({children,title,description})`
- Produces: ERP local navigation links for dashboard, items, suppliers, warehouses, inventory, requisitions, purchase orders, and goods receipts.

- [ ] Write a failing navigation test requiring every operational route.
- [ ] Implement authenticated shell, Arabic-first navigation, responsive workspace styles, error/success/empty helpers.
- [ ] Run focused component tests.

### Task 4: Operational master-data pages

**Files:**
- Replace: `apps/web/app/erp/page.tsx`
- Create: `apps/web/app/erp/items/page.tsx`
- Create: `apps/web/app/erp/suppliers/page.tsx`
- Create: `apps/web/app/erp/warehouses/page.tsx`
- Create: `apps/web/app/erp/items/page.test.tsx`
- Create: `apps/web/app/erp/warehouses/page.test.tsx`

**Interfaces:**
- Consumes Task 2 API and Task 3 workspace.

- [ ] Add failing tests for dashboard quick links and item/warehouse creation.
- [ ] Implement dashboard with metrics + quick actions + low-stock section.
- [ ] Implement item create/list lifecycle actions: activate, inactivate, archive.
- [ ] Implement supplier create/list lifecycle actions: activate, block.
- [ ] Implement warehouse create/list lifecycle actions: activate, archive.
- [ ] Reload authoritative server data after every mutation.
- [ ] Run focused UI tests.

### Task 5: Operational inventory page

**Files:**
- Create: `apps/web/app/erp/inventory/page.tsx`
- Create: `apps/web/app/erp/inventory/page.test.tsx`

**Interfaces:**
- Consumes balances, inventory summary, reservations, movements, transfers, adjustments, items, warehouses.

- [ ] Write failing tests for creating reservation, transfer, and adjustment.
- [ ] Implement balance table with warehouse filter and low-stock context.
- [ ] Implement reservation create/list/release/confirm.
- [ ] Implement transfer create/list/submit/receive.
- [ ] Implement adjustment create/list/approve.
- [ ] Implement append-only movement ledger table.
- [ ] Run focused UI tests.

### Task 6: Operational requisitions and purchase orders

**Files:**
- Create: `apps/web/app/erp/requisitions/page.tsx`
- Create: `apps/web/app/erp/purchase-orders/page.tsx`
- Create: `apps/web/app/erp/purchase-orders/page.test.tsx`

**Interfaces:**
- Requisition form submits `CreateRequisitionRequest` with one or more item lines.
- Purchase-order form submits `CreatePurchaseOrderRequest` with supplier and one or more item lines.

- [ ] Write failing purchase-order create/action test.
- [ ] Implement requisition create/list/detail-inline workflow actions: submit, approve, reject.
- [ ] Implement purchase-order create/list/detail-inline workflow actions: submit, approve, cancel.
- [ ] Show totals, supplier, expected date, line quantities/costs/received quantities.
- [ ] Run focused UI tests.

### Task 7: Operational goods receipts

**Files:**
- Create: `apps/web/app/erp/goods-receipts/page.tsx`
- Create: `apps/web/app/erp/goods-receipts/page.test.tsx`

**Interfaces:**
- Receipt creation references purchase order, warehouse, and one or more receipt lines.

- [ ] Write failing receipt create/post test.
- [ ] Implement receipt creation from existing PO/items and selected warehouse.
- [ ] Implement receipt list and POST lifecycle action.
- [ ] Display posting state and quantities.
- [ ] Run focused UI tests.

### Task 8: Verification, PR, and preview acceptance

**Files:**
- No production logic added in this task.

- [ ] Run `npm test -- --run` or repository-equivalent Vitest command for ERP web tests.
- [ ] Run `npm run lint` in `apps/web`.
- [ ] Run `npm run build` in `apps/web`.
- [ ] Run focused Spring ERP integration tests using repository CI/PostgreSQL policy.
- [ ] Inspect diff for mock data, unrelated changes, secrets, and capability bypasses.
- [ ] Open PR from `feat/erp-operational-ui` to `main`.
- [ ] Verify required checks and Vercel preview deployment are successful.
- [ ] Provide the preview `/erp` URL for human visual acceptance.
- [ ] Do not merge to production until the human visual acceptance gate is explicitly approved.
