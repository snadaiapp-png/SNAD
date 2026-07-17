# REM-P1-007 — Business Process E2E Execution Plan

**Status:** `REMEDIATION_IN_PROGRESS`  
**Finding:** `REM-P1-007`  
**Owner:** QA & Release with CRM, ERP, Accounting, HRM, Ecommerce and Workflow Product Owners  
**Status authority:** GitHub Issue #516 and `docs/governance/CURRENT-STATUS.json`

## Objective

Create reproducible, exact-SHA evidence that SANAD business processes work across module boundaries, not only as isolated endpoints, screens or component tests.

No closure is authorized from documentation, mocks, route existence, isolated module tests or a successful frontend deployment. Closure requires all four governed processes to be fully executable and verified with real application paths, tenant isolation, authorization, audit, rollback, reconciliation and accountable owner acceptance.

## Current executable baseline

The first executable foundation slice is implemented by:

```text
com.sanad.platform.e2e.SalesQualificationBusinessProcessE2ETest
```

It proves:

```text
Lead
→ Qualification
→ Account and Contact creation
→ Opportunity creation
→ Idempotent replay protection
→ Won stage
→ Completed commercial handoff activity
→ Customer 360 consistency
→ Dashboard consistency
→ Tenant isolation
→ RBAC denial
→ Audit evidence
→ Timeline evidence
→ Rejected-mutation rollback
```

This is a real HTTP-to-application-to-database test. It is deliberately classified as `PARTIAL_VERIFIED`, because the following Order-to-Cash stages remain unproved:

```text
Quotation
→ Sales Order
→ Inventory Reservation
→ Delivery
→ Invoice
→ Ledger Posting
→ Collection
→ Analytics reconciliation
```

## Governed process matrix

| Process | Current state | Executable evidence | Remaining boundary |
|---|---|---|---|
| Sales Order to Cash | `PARTIAL_VERIFIED` | Lead through Won Opportunity, handoff, tenant/RBAC/audit/timeline/rollback | Quotation through collection and reconciled analytics |
| Procure to Pay | `NOT_EXECUTABLE` | None accepted | Purchase Request, Approval, Purchase Order, Receipt, Supplier Invoice, Ledger Posting, Payment, Reconciliation |
| Hire to Pay | `NOT_EXECUTABLE` | None accepted | Employee, Contract, Attendance, Leave, Payroll, Ledger Posting, Payment, Analytics |
| Commerce Order to Refund | `NOT_EXECUTABLE` | None accepted | Customer Order, Payment, Inventory, Shipment, Invoice, Return, Refund, Ledger Reconciliation, Analytics |

The machine-readable authority for this matrix is:

```text
docs/quality/e2e/business-process-catalog.json
```

## Phase 1 — Evidence foundation

Deliverables:

1. Machine-readable process and step catalog.
2. Real executable Sales Qualification process slice.
3. Tenant-isolation assertion.
4. Capability-denial assertion.
5. Idempotent lead-conversion assertion.
6. Central audit and CRM timeline assertions.
7. Customer 360 and dashboard consistency assertions.
8. Rollback assertion proving a rejected cross-account opportunity creates no row.
9. Exact-SHA GitHub Actions workflow.
10. Sanitized JSON and Surefire evidence artifacts.
11. Fail-closed governance validator.

Acceptance for Phase 1 does not close REM-P1-007. It only changes the finding from "no unified evidence framework" to "framework active; one partial process slice verified."

## Phase 2 — Sales Order to Cash completion

Required implementation and evidence:

1. Convert Won Opportunity to governed Quotation.
2. Approve Quotation where policy requires approval.
3. Create Sales Order using idempotency and optimistic concurrency.
4. Reserve inventory without crossing tenant boundaries.
5. Deliver only reserved and authorized quantities.
6. Create invoice exactly once from an accepted source event.
7. Post balanced double-entry ledger entries.
8. Record collection and customer balance settlement.
9. Reconcile CRM, order, inventory, invoice, ledger and analytics identifiers.
10. Prove rollback and retry behavior at every failure boundary.

## Phase 3 — Procure to Pay

Required executable path:

```text
Purchase Request
→ Approval
→ Purchase Order
→ Goods Receipt
→ Supplier Invoice
→ Ledger Posting
→ Payment
→ Reconciliation
```

Mandatory assertions include approval segregation, three-way matching, duplicate-invoice prevention, inventory receipt accuracy, balanced accounting, payment authorization, tenant isolation and audit completeness.

## Phase 4 — Hire to Pay

Required executable path:

```text
Employee
→ Contract
→ Attendance
→ Leave
→ Payroll
→ Ledger Posting
→ Payment
→ Analytics
```

Mandatory assertions include effective-date handling, payroll calculation inputs, approval, duplicate-pay prevention, balanced accounting, employee privacy, tenant isolation and audit completeness.

## Phase 5 — Commerce Order to Refund

Required executable path:

```text
Customer Order
→ Payment Authorization
→ Inventory Reservation
→ Shipment
→ Invoice
→ Return
→ Refund
→ Ledger Reconciliation
→ Analytics
```

Mandatory assertions include idempotent checkout, payment/reference consistency, inventory conservation, partial shipment and return handling, refund limits, balanced accounting, tenant isolation and audit completeness.

## Evidence requirements

Every accepted run must include:

- Exact Git commit SHA.
- Workflow run and job identifiers.
- Test class and testcase names.
- Environment and database engine.
- Process and step identifiers.
- Created entity identifiers in sanitized form or hashed form.
- Expected and actual financial totals where applicable.
- Expected and actual inventory quantities where applicable.
- Tenant-isolation result.
- Authorization result.
- Audit/timeline result.
- Rollback/retry result.
- Analytics reconciliation result.
- Failures, errors and skipped critical tests.
- Accountable owner acceptance.

Secrets, access tokens, refresh tokens, passwords and customer personal data must never appear in artifacts.

## Closure gate

REM-P1-007 may close only when:

1. All four catalog processes are `FULLY_VERIFIED`.
2. Every required step is listed under `verified_steps`.
3. Every `blocked_steps` list is empty.
4. PostgreSQL-backed execution succeeds on an exact SHA.
5. Tenant isolation and RBAC pass for every process.
6. Audit and timeline evidence is complete.
7. Failure recovery and rollback are proven.
8. Financial and inventory reconciliation pass where applicable.
9. Analytics agree with operational and financial sources.
10. QA & Release and every accountable Business Product Owner approve the evidence.
11. Issue #516 and current status authorities are updated through a reviewed closure decision.

Until then:

```text
REM-P1-007: OPEN
BROAD_COMMERCIAL_GO_LIVE: NOT_APPROVED
```
