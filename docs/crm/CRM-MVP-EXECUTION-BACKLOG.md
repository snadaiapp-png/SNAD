# CRM MVP Execution Backlog

## Status

```text
Backlog: CRM MVP v1
Implementation: NOT STARTED
Entry gate: CRM-READINESS-GATE
Delivery model: controlled non-production first
```

## Estimation model

- XS = 1 point
- S = 2 points
- M = 3 points
- L = 5 points
- XL = 8 points
- XXL = 13 points

A sprint is two weeks. Capacity is assigned by the project operating model and is not assumed by this document.

## Epic CRM-000 — Platform dependency closure

**Priority:** P0  
**Owner:** Platform Core + Security  
**Dependency:** Issues #101, #173, #184, #185

### Feature CRM-000-F1 — Exact-SHA engineering baseline

- CRM-000-S1: Record the accepted main SHA and all build evidence. Estimate: S.
- CRM-000-S2: Pass backend tests and packaging on the accepted SHA. Estimate: M.
- CRM-000-S3: Pass frontend lint, tests, and production build. Estimate: M.
- CRM-000-S4: Pass tenant-isolation, authorization, and PostgreSQL acceptance. Estimate: L.
- CRM-000-S5: Reconcile implementation and governance documents. Estimate: M.

Acceptance:

- one exact SHA is used for the acceptance claim;
- no unresolved Critical or High repository-source security finding;
- production restrictions remain explicit.

## Epic CRM-001 — CRM architecture skeleton

**Priority:** P0  
**Owner:** CRM/Experience Squad  
**Dependencies:** CRM-000

### Feature CRM-001-F1 — Module and package boundaries

- CRM-001-S1: Create CRM root module and package dependency rules. Estimate: M.
- CRM-001-S2: Define application, domain, API, and infrastructure layers. Estimate: M.
- CRM-001-S3: Add architecture tests preventing controller-to-repository and cross-module repository access. Estimate: L.
- CRM-001-S4: Define CRM capability names and ownership matrix. Estimate: M.

Acceptance:

- modules match `CRM-DOMAIN-AND-SERVICE-BOUNDARIES.md`;
- no premature microservice deployment;
- architecture tests run in CI.

## Epic CRM-002 — Customer and account foundation

**Priority:** P0  
**Owner:** CRM/Experience Squad  
**Dependencies:** CRM-001, Tenant Context, IAM, Audit

### Feature CRM-002-F1 — Accounts

- CRM-002-S1: Create tenant-scoped account aggregate and migration. Estimate: L.
- CRM-002-S2: Implement account create/read/update/archive APIs. Estimate: XL.
- CRM-002-S3: Implement parent-account cycle prevention. Estimate: M.
- CRM-002-S4: Add account ownership and lifecycle transitions. Estimate: L.
- CRM-002-S5: Add account tenant-isolation and capability tests. Estimate: L.

### Feature CRM-002-F2 — Contacts

- CRM-002-S6: Create tenant-scoped contact aggregate and migration. Estimate: L.
- CRM-002-S7: Implement contact APIs and account relationships. Estimate: XL.
- CRM-002-S8: Add communication methods and preference metadata. Estimate: L.
- CRM-002-S9: Add consent-summary integration point. Estimate: M.
- CRM-002-S10: Add contact isolation, authorization, and audit tests. Estimate: L.

### Feature CRM-002-F3 — Customer 360 read model

- CRM-002-S11: Define Customer 360 projection contract. Estimate: M.
- CRM-002-S12: Project account, contact, activity, and relationship summaries. Estimate: XL.
- CRM-002-S13: Add Arabic/Latin normalized search baseline. Estimate: L.

Acceptance:

- all records are tenant-scoped;
- every endpoint has capability checks;
- Customer 360 is a projection, not a second source of truth;
- Arabic and English labels and errors are supported.

## Epic CRM-003 — Lead management

**Priority:** P0  
**Owner:** CRM/Experience Squad  
**Dependencies:** CRM-002, Workflow integration point

### Feature CRM-003-F1 — Lead lifecycle

- CRM-003-S1: Create lead aggregate and migration. Estimate: L.
- CRM-003-S2: Implement lead create/read/update APIs. Estimate: L.
- CRM-003-S3: Implement qualification and disqualification transitions. Estimate: M.
- CRM-003-S4: Implement owner or queue assignment. Estimate: L.
- CRM-003-S5: Emit lead lifecycle events. Estimate: M.

### Feature CRM-003-F2 — Lead conversion

- CRM-003-S6: Define idempotent conversion command. Estimate: M.
- CRM-003-S7: Convert or link account and contact. Estimate: XL.
- CRM-003-S8: Optionally create an opportunity in the same controlled flow. Estimate: L.
- CRM-003-S9: Add replay, rollback, and partial-failure tests. Estimate: XL.

Acceptance:

- a lead cannot be converted twice;
- conversion is tenant-safe and auditable;
- cross-domain orchestration uses application services and events.

## Epic CRM-004 — Opportunities and pipelines

**Priority:** P0  
**Owner:** CRM/Experience Squad  
**Dependencies:** CRM-002, CRM-001

### Feature CRM-004-F1 — Pipeline configuration

- CRM-004-S1: Create tenant-scoped pipeline and stage definitions. Estimate: L.
- CRM-004-S2: Validate stage ordering and active state. Estimate: M.
- CRM-004-S3: Add localized labels and configurable outcome reasons. Estimate: M.

### Feature CRM-004-F2 — Opportunity lifecycle

- CRM-004-S4: Create opportunity aggregate and migration. Estimate: L.
- CRM-004-S5: Implement opportunity APIs. Estimate: XL.
- CRM-004-S6: Implement stage transitions and immutable stage history. Estimate: L.
- CRM-004-S7: Implement value, currency, probability, and forecast category. Estimate: L.
- CRM-004-S8: Implement closed-won and closed-lost validation. Estimate: M.
- CRM-004-S9: Emit opportunity lifecycle events. Estimate: M.

Acceptance:

- stage belongs to the selected pipeline;
- monetary values include ISO currency;
- official accounting conversion and posting are excluded;
- every outcome and ownership change is audited.

## Epic CRM-005 — Activities and timeline

**Priority:** P1  
**Owner:** CRM/Experience Squad  
**Dependencies:** CRM-002, Notification and Workflow integration points

### Feature CRM-005-F1 — Activities

- CRM-005-S1: Create activity aggregate and migration. Estimate: L.
- CRM-005-S2: Implement task, meeting, call, email-reference, and note types. Estimate: XL.
- CRM-005-S3: Implement assignment, due dates, priorities, and completion. Estimate: L.
- CRM-005-S4: Integrate reminder and escalation workflow requests. Estimate: L.

### Feature CRM-005-F2 — Timeline

- CRM-005-S5: Create event-driven timeline projection. Estimate: XL.
- CRM-005-S6: Enforce field-level visibility in timeline entries. Estimate: L.
- CRM-005-S7: Add pagination and stable ordering. Estimate: M.

Acceptance:

- timeline entries preserve source references;
- CRM does not duplicate authoritative communication payloads;
- private fields are not exposed through projections.

## Epic CRM-006 — Data quality and import

**Priority:** P1  
**Owner:** CRM + Data  
**Dependencies:** Object storage, background jobs, Audit

### Feature CRM-006-F1 — Duplicate detection

- CRM-006-S1: Define normalized matching keys for Arabic and Latin data. Estimate: L.
- CRM-006-S2: Implement duplicate candidate generation. Estimate: XL.
- CRM-006-S3: Implement reviewed account/contact merge. Estimate: XL.
- CRM-006-S4: Preserve aliases, external IDs, and audit history. Estimate: L.

### Feature CRM-006-F2 — CSV import

- CRM-006-S5: Create asynchronous import job model. Estimate: L.
- CRM-006-S6: Implement mapping, dry run, validation, and bounded batches. Estimate: XL.
- CRM-006-S7: Produce authorized row-level error report. Estimate: L.
- CRM-006-S8: Add restart-safety and idempotency tests. Estimate: L.

Acceptance:

- imports never bypass authorization or tenant context;
- failures do not leave untraceable partial state;
- merge decisions are auditable and reversible through documented recovery.

## Epic CRM-007 — Configuration and extensibility

**Priority:** P1  
**Owner:** Platform + CRM  
**Dependencies:** CRM-001

### Feature CRM-007-F1 — Custom fields

- CRM-007-S1: Create custom-field definition model. Estimate: XL.
- CRM-007-S2: Validate typed values and sensitivity settings. Estimate: XL.
- CRM-007-S3: Expose metadata contracts for UI rendering. Estimate: L.
- CRM-007-S4: Add safe deprecation rules. Estimate: M.

### Feature CRM-007-F2 — Queues and territories

- CRM-007-S5: Define CRM queues and membership references. Estimate: L.
- CRM-007-S6: Define territories and assignment metadata. Estimate: L.
- CRM-007-S7: Integrate assignment requests with Workflow Engine. Estimate: M.

Acceptance:

- tenant configuration cannot alter another tenant;
- custom fields remain query-safe and schema-governed;
- configuration changes are fully audited.

## Epic CRM-008 — Global experience

**Priority:** P0  
**Owner:** Experience Squad  
**Dependencies:** CRM APIs and platform localization

### Feature CRM-008-F1 — Application shell

- CRM-008-S1: Add CRM navigation and capability-aware routes. Estimate: L.
- CRM-008-S2: Implement account and contact list/detail screens. Estimate: XL.
- CRM-008-S3: Implement lead workspace. Estimate: XL.
- CRM-008-S4: Implement pipeline board and opportunity detail. Estimate: XXL.
- CRM-008-S5: Implement activity timeline. Estimate: XL.

### Feature CRM-008-F2 — Global UX

- CRM-008-S6: Validate full Arabic RTL behavior. Estimate: L.
- CRM-008-S7: Validate English LTR behavior. Estimate: M.
- CRM-008-S8: Add locale-aware dates, currency, names, phones, and addresses. Estimate: L.
- CRM-008-S9: Add keyboard and screen-reader acceptance tests. Estimate: L.
- CRM-008-S10: Add responsive desktop/tablet/mobile acceptance. Estimate: L.

Acceptance:

- routes are protected by approved session architecture;
- UI displays only authorized actions;
- Arabic and English are release-blocking test paths.

## Epic CRM-009 — Workflow and AI integration points

**Priority:** P1  
**Owner:** CRM + Workflow + AI  
**Dependencies:** central Workflow Engine and AI Gateway contracts

### Feature CRM-009-F1 — Workflow

- CRM-009-S1: Define assignment workflow contract. Estimate: M.
- CRM-009-S2: Define opportunity approval contract. Estimate: M.
- CRM-009-S3: Define reminder and escalation contract. Estimate: M.
- CRM-009-S4: Add workflow reference and status projections. Estimate: L.

### Feature CRM-009-F2 — AI

- CRM-009-S5: Define customer-summary request/response contract. Estimate: M.
- CRM-009-S6: Define next-best-action contract. Estimate: M.
- CRM-009-S7: Define scoring and explanation contract. Estimate: L.
- CRM-009-S8: Add policy, redaction, fallback, and human-confirmation tests. Estimate: L.

Acceptance:

- CRM does not implement a separate workflow runtime;
- CRM does not call model providers directly;
- AI failure cannot corrupt CRM transactions;
- high-impact mutation remains policy controlled.

## Epic CRM-010 — Quality, security, and operations

**Priority:** P0  
**Owner:** QA + Security + SRE + CRM  
**Dependencies:** all CRM epics

### Feature CRM-010-F1 — Automated acceptance

- CRM-010-S1: Tenant-isolation test matrix for every endpoint. Estimate: XL.
- CRM-010-S2: Capability and field-level authorization tests. Estimate: XL.
- CRM-010-S3: Migration and recovery tests. Estimate: L.
- CRM-010-S4: API and event compatibility tests. Estimate: L.
- CRM-010-S5: Arabic/English UI test suite. Estimate: L.

### Feature CRM-010-F2 — Operations

- CRM-010-S6: Add CRM logs, metrics, traces, and dashboards. Estimate: L.
- CRM-010-S7: Define CRM SLO candidates and alert conditions. Estimate: M.
- CRM-010-S8: Add import/search performance baselines. Estimate: L.
- CRM-010-S9: Create CRM runbook and recovery guide. Estimate: M.

Acceptance:

- no Critical or High unresolved security finding;
- no cross-tenant test failure;
- API, event, migration, privacy, and localization gates pass;
- production remains separately gated.

## Proposed sprint sequence

### Sprint CRM-0 — Entry and architecture

CRM-000 and CRM-001.

### Sprint CRM-1 — Accounts

Account aggregate, APIs, authorization, audit, and tests.

### Sprint CRM-2 — Contacts and relationships

Contact aggregate, account relationships, preferences, and Customer 360 foundation.

### Sprint CRM-3 — Leads

Lead lifecycle, assignment integration, and qualification.

### Sprint CRM-4 — Lead conversion and pipelines

Idempotent conversion and pipeline configuration.

### Sprint CRM-5 — Opportunities

Opportunity lifecycle, stage history, values, outcomes, and events.

### Sprint CRM-6 — Activities and timeline

Activities, reminders, escalation integration, and timeline projection.

### Sprint CRM-7 — Data quality and import

Duplicate detection, merge, import, and error reporting.

### Sprint CRM-8 — Global CRM UX

Arabic/English application views and accessibility.

### Sprint CRM-9 — Workflow, AI, hardening

Central integrations, performance, security, observability, and release evidence.

## Release decision

This backlog does not authorize CRM implementation by itself. Sprint CRM-0 starts only after `CRM-READINESS-GATE.md` records GO or GO WITH CONDITIONS.
