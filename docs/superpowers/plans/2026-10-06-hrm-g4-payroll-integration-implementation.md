# HRM G4 — Payroll Integration Implementation Plan

**Status:** PROPOSED / PRE-IMPLEMENTATION  
**Branch:** `feat/hrm-g4-payroll-integration`  
**Baseline:** `9665249708db48f4bb8c641553a37e61f8b10426`

## Canonical execution sequence

### G4-T1 — Baseline and RED evidence
- Prove branch ancestry/current-main baseline.
- Capture existing G0 compensation + G2 time/leave contracts.
- Add first PostgreSQL Direct RED test for tenant-bound payroll snapshot creation.
- Assert foreign-tenant access fails closed.
- No product implementation before RED is observed.

**Exit:** reproducible failing contract and evidence record.

### G4-T2 — Payroll schema and RLS
Maps roadmap `G4-T01`.

Add narrowly scoped, forward-only Flyway objects for:
- payroll runs;
- payroll items/snapshots;
- optional component breakdown table if normalization is required.

Requirements:
- tenant_id on every tenant-owned row;
- ENABLE + FORCE RLS;
- period/legal-entity uniqueness/idempotency constraints;
- fixed-precision monetary columns;
- immutable approved snapshot protections;
- indexes for tenant + period + employment/status.

**Exit:** migration + real PostgreSQL isolation tests green.

### G4-T3 — Authoritative input adapters
Implement HR-owned application ports/adapters for:
- effective employment/contract/compensation;
- G2 approved/locked timesheet hours;
- G2 approved leave outcomes.

Rules:
- no direct cross-module SQL;
- no attendance recalculation;
- effective-dated/version-pinned inputs;
- missing/ambiguous canonical employment fails closed.

**Exit:** deterministic input snapshot tests green.

### G4-T4 — Payroll calculation engine
Maps roadmap `G4-T02`.

Country-neutral engine:
- base salary;
- configurable allowances;
- explicitly configured non-statutory deductions;
- approved unpaid-time deductions when policy enables;
- gross/deduction/net totals;
- fixed precision/rounding policy;
- deterministic re-run from same snapshot.

Saudi statutory formulas remain disabled/unimplemented.

**Exit:** calculation matrix + edge/rounding tests green.

### G4-T5 — Lifecycle, audit, outbox and idempotency
Implement:
- DRAFT -> CALCULATED -> REVIEWED -> APPROVED -> EXPORTED;
- guarded cancellation/recalculation;
- durable HR idempotency;
- same-transaction audit/outbox;
- optimistic/concurrent transition protection;
- amount-minimized event payloads.

**Exit:** duplicate/concurrency/rollback tests green.

### G4-T6 — Authorization and sensitive payroll reads
Introduce RED-first capability contracts for payroll read/calculate/review/approve/export.

Requirements:
- explicit capabilities, no role shortcut;
- tenant scope mandatory;
- restricted amount reads audited before response;
- audit failure blocks the read;
- self-service read, if delivered, limited to the authenticated canonical employment.

**Exit:** SELF/HR/reviewer/approver/foreign-tenant matrix green.

### G4-T7 — Accounting integration boundary
Create application port/event contract for approved payroll export.

Requirements:
- no Accounting/Finance infrastructure dependency;
- no direct accounting/finance table SQL;
- idempotent export request;
- Accounting owns journal/GL posting;
- HR stores only export correlation/status evidence.

**Exit:** architecture/boundary tests green.

### G4-T8 — Payroll API
Expose governed `/api/v2/hr/payroll` operations for run lifecycle, item review and export.

Requirements:
- tenant context from authenticated principal;
- explicit capability guards;
- idempotency on mutations;
- conflict semantics for stale version/state;
- no statutory endpoint claims.

**Exit:** controller/service/OpenAPI tests green.

### G4-T9 — Payroll review UI
Maps roadmap `G4-T03`.

Deliver:
- run list;
- run detail and status;
- earnings/deductions/net breakdown;
- exception queue;
- review/approve/export actions;
- AR/EN;
- desktop/mobile;
- loading/empty/error/forbidden/conflict states.

**Exit:** focused Vitest + visual/runtime evidence green.

### G4-T10 — Authenticated acceptance
Real runtime path:
- PostgreSQL Direct;
- Spring Boot;
- Next.js;
- authenticated HR payroll operator;
- create/calculate/review/approve flow;
- cross-tenant denial;
- accounting-export request boundary.

**Exit:** no hidden 403/500; exact-head acceptance green.

### G4-T11 — Full certification and merge
- focused G4 backend;
- Flyway validation;
- HRM security/RLS;
- Web CI;
- full Maven/CI;
- security/governance scans;
- human preview if applicable;
- independent review;
- protected merge.

**Exit:** all required checks terminal-green on exact PR head.

### G4-T12 — Post-merge closure
On merge SHA:
- Post-Merge Main Verification A-F;
- PostgreSQL Direct integration;
- HRM focused security/RLS;
- final evidence aggregation;
- production/reconcile only when G4 release authority requires it;
- publish G4 engineering closure certificate.

**Exit:** `HRM_G4_ENGINEERING = CLOSED`.

## Dependency graph

```text
G2 + G3 closed
   |
G4-T1 RED
   |
G4-T2 schema/RLS
   |
G4-T3 authoritative inputs
   |
G4-T4 calculation
   |
G4-T5 lifecycle/evidence
   +------ G4-T6 authorization
   +------ G4-T7 accounting port
             |
           G4-T8 API
             |
           G4-T9 UI
             |
          G4-T10 acceptance
             |
          G4-T11 merge
             |
          G4-T12 PMV/closure
```

## Independent statutory gate

```text
LEGAL_REVIEW = PENDING_HUMAN
SA_COUNTRY_PACK = DRAFT
SAUDI_STATUTORY_PAYROLL = DISABLED
```

Engineering work must fail closed rather than invent statutory values.
