# HRM G4 — Payroll Integration Design

**Status:** DESIGN / PRE-IMPLEMENTATION GATE  
**Date:** 2026-10-06  
**Repository:** `snadaiapp-png/SNAD`  
**Branch:** `feat/hrm-g4-payroll-integration`  
**Branch baseline:** `9665249708db48f4bb8c641553a37e61f8b10426`  
**Database execution model:** PostgreSQL Direct only

## 1. Purpose

G4 integrates authoritative HR employee/compensation data and approved G2 time/leave
outcomes into a governed payroll-calculation boundary. The repository roadmap defines
G4 as **Payroll Integration** and contains three visible roadmap items:

1. `G4-T01` — payroll data persistence.
2. `G4-T02` — payroll calculation engine.
3. `G4-T03` — payroll review UI.

The roadmap rows are business scope. The engineering implementation must additionally
supply tenancy, RLS, audit/outbox, idempotency, API, workflow/approval, accounting
integration ports, PostgreSQL Direct acceptance, and exact-SHA certification.

## 2. Entry gates

G4 starts only because the preceding engineering chain is closed:

```text
G0_ENGINEERING = CLOSED
G1_ENGINEERING = CLOSED
G2_ENGINEERING = CLOSED
G3_ENGINEERING = CLOSED
G3_PRODUCTION_OPERATIONAL = PASS
G4_ENGINEERING_ENTRY_GATE = PASS
LEGAL_REVIEW = PENDING_HUMAN
SA_COUNTRY_PACK = DRAFT
G4_SAUDI_STATUTORY_RULES_GATE = OPEN
```

G4 may implement country-neutral payroll mechanics. It MUST NOT claim Saudi statutory
correctness or enable Saudi-specific statutory calculation while the legal/Country Pack
gate is open.

## 3. Existing contracts G4 must preserve

### 3.1 Canonical HR identity

All subject resolution continues through the canonical graph:

`User -> Person -> Employment -> effective PRIMARY Assignment`

Legacy direct user/manager shortcuts are forbidden.

### 3.2 Compensation source

G0 contract/compensation is the authoritative source for effective-dated compensation.
G4 consumes it through HR-owned services/ports. Historical compensation remains
immutable and sensitive-read audit remains fail-closed.

### 3.3 Time/leave source

G2 remains authoritative for:
- approved/locked timesheets;
- derived work hours;
- approved leave outcomes;
- attendance corrections and provenance.

G4 must not recalculate attendance or mutate G2 ledgers.

### 3.4 Performance source

G3 is a dependency in the roadmap but performance scores MUST NOT alter payroll by
default. Any future performance-linked compensation requires a separately governed
policy and is out of G4 core scope.

## 4. Canonical G4 domain

### 4.1 Payroll period/run

A payroll run is tenant-bound and period-bound. At minimum:

- tenant_id;
- legal_entity_id / organization context where applicable;
- period start/end;
- currency;
- lifecycle status;
- source-cutoff timestamp;
- immutable calculation snapshot identity;
- created/approved actor and timestamps;
- optimistic version / idempotency identity.

Lifecycle:

```text
DRAFT -> CALCULATED -> REVIEWED -> APPROVED -> EXPORTED
  |          |            |
  +------> CANCELLED <----+
```

No state after APPROVED may silently recalculate historical results.

### 4.2 Payroll item

Each run contains one item per eligible employment for the governed period:

- employment/person identifiers;
- compensation package/version reference;
- approved time/leave snapshot references;
- base amount;
- allowance components;
- non-statutory configurable deductions;
- gross;
- deduction total;
- net;
- calculation status / exception code;
- calculation evidence metadata.

Amounts use fixed-precision decimal types; floating point is prohibited.

### 4.3 Calculation snapshot

Payroll must be reproducible. A calculated item records the identifiers/versions of
all authoritative inputs used. Re-running with unchanged inputs is deterministic.

## 5. Country-neutral calculation scope

G4 core MAY calculate:
- effective base salary from certified HR compensation;
- configured allowances;
- approved unpaid-time deductions where tenant policy explicitly enables them;
- tenant-configurable non-statutory deductions;
- gross, total deductions, net.

G4 core MUST NOT implement or infer:
- GOSI/social-insurance statutory formulas;
- Saudi labor-law entitlement calculations;
- WPS/Sarie/bank payment files;
- tax/Zakat withholding;
- end-of-service statutory formulas;
- jurisdiction-specific overtime/statutory premium formulas;
- government submission formats.

Those require an approved Country Pack/legal authority.

## 6. Architecture boundary

G4 remains inside the HR bounded context for payroll preparation/calculation.

Hard rules:
1. HR production SQL never reads/writes `accounting_*`, `finance_*`, `erp_*`, or external payroll tables directly.
2. No HR class depends on Accounting/Finance infrastructure packages.
3. Posting/export uses an explicit application port/event contract.
4. Accounting remains the financial system of record for GL/journal posting.
5. Bank/payment execution is out of scope.
6. Existing `HrModuleBoundaryArchitectureTest` invariants remain green.

## 7. Security, tenancy, privacy

- Every G4 persistent table: tenant_id + ENABLE/FORCE RLS.
- Cross-tenant read/write must fail closed on real PostgreSQL.
- Runtime DB role: NOSUPERUSER / NOBYPASSRLS.
- Payroll amounts are restricted HR data and require explicit capabilities.
- Sensitive reads must use the existing fail-closed audit mechanism.
- Audit/outbox payloads must not copy raw bank identifiers, credentials, or unnecessary compensation amounts.
- Mutations use the existing HR idempotency contract.
- Approval authority must be explicit; no broad role-name shortcut.

Initial capability family proposed for implementation:
- `HR.PAYROLL.READ`
- `HR.PAYROLL.CALCULATE`
- `HR.PAYROLL.REVIEW`
- `HR.PAYROLL.APPROVE`
- `HR.PAYROLL.EXPORT`

Final capability binding requires RED-first authorization tests before seeding.

## 8. Integration contracts

### Inputs
- G0 employment + effective contract/compensation.
- G2 approved/locked time and approved leave.
- tenant/legal-entity/currency context from shared platform contracts.

### Outputs
- amount-free lifecycle/audit events where possible;
- governed payroll-approved event for downstream consumers;
- accounting export/posting request through a port, never direct DB access.

Suggested event types:
- `HRM.PAYROLL.CALCULATED.v1`
- `HRM.PAYROLL.REVIEWED.v1`
- `HRM.PAYROLL.APPROVED.v1`
- `HRM.PAYROLL.EXPORT_REQUESTED.v1`

Events should expose identifiers, period, status and correlation metadata; raw detailed
payroll amounts stay inside governed payroll read models unless an explicit downstream
contract requires them.

## 9. API contract

Governed API surface under `/api/v2/hr/payroll` should cover:

- create/list/get payroll runs;
- calculate/recalculate a DRAFT run;
- list/get payroll items;
- review exceptions;
- approve a reviewed run;
- request governed accounting export;
- authenticated employee payslip/read view only if explicitly included in implementation.

All operations validate tenant context, lifecycle transitions, source cutoff, capability,
and idempotency.

## 10. Web scope

Roadmap-required surface: payroll review UI.

Minimum views:
- payroll run list/status;
- payroll run detail;
- item breakdown (earnings/deductions/net);
- exception queue;
- review/approve actions gated by capability;
- deterministic loading, empty, validation, forbidden and conflict states;
- Arabic/English;
- desktop/mobile behavior consistent with the existing HR shell.

No banking/payment execution UI is authorized.

## 11. RED-first contract

No production G4 implementation begins until the first PostgreSQL Direct RED test is
committed and observed failing.

Preferred first invariant:

`tenant + canonical employment + effective compensation + locked timesheet -> payroll item snapshot; foreign tenant denied`

Mock-only evidence is insufficient for the database/security gate.

## 12. Verification gates

G4 cannot close until one exact head SHA satisfies:

1. baseline ancestry from authorized main;
2. RED evidence;
3. additive Flyway schema;
4. FORCE-RLS tenant-isolation PostgreSQL tests;
5. canonical identity/input-source tests;
6. compensation sensitive-read/audit contract;
7. deterministic calculation tests;
8. lifecycle/idempotency/concurrency tests;
9. capability authorization tests;
10. accounting boundary/port tests;
11. focused backend tests;
12. frontend Arabic/English + desktop/mobile tests;
13. authenticated PostgreSQL Direct runtime acceptance;
14. full required CI terminal-green;
15. independent review/protected merge;
16. post-merge main verification A-F terminal-green.

## 13. Explicit non-goals

- Docker/Testcontainers.
- Saudi statutory compliance claim.
- GOSI/WPS/tax/government-file implementations.
- bank payment execution.
- direct Accounting/Finance database access.
- general-ledger mutation inside HR.
- performance-based pay rules.
- reopening G0-G3.
- broad RBAC/RLS rewrites without a failing contract proving necessity.

## 14. Design completion condition

This document authorizes planning only. G4 remains NOT_STARTED until the implementation
plan is approved and the first RED contract is committed.
