# CRM Readiness Gate

## Purpose

Define the mandatory conditions for starting SNAD CRM implementation.

## Current decision

```text
CRM_ARCHITECTURE_PREPARATION: AUTHORIZED
CRM_BACKLOG_PREPARATION: AUTHORIZED
CRM_CONTRACT_PREPARATION: AUTHORIZED
CRM_SOURCE_CODE_IMPLEMENTATION: BLOCKED PENDING THIS GATE
PRODUCTION_DEPLOYMENT: NOT AUTHORIZED
```

## Gate matrix

### G1 — Platform Core

Required:

- accepted Tenant Context behavior;
- accepted authentication and session architecture;
- accepted user, organization, membership, role, and capability foundations;
- exact-SHA backend and frontend build evidence;
- PostgreSQL and tenant-isolation acceptance;
- correlation ID and structured audit integration points;
- approved repository and package ownership rules.

Status: PENDING under Issues #101, #184, and #185.

### G2 — Security

Required:

- current-tree secret scan passed;
- historical findings classified and owner-controlled rotations completed where required;
- Issue #173 exit criteria completed for affected credentials;
- workflow security policy passed;
- dependency and container security evidence recorded;
- terminal OWASP result recorded for the accepted SHA or formally accepted risk with scope and expiry;
- no unresolved Critical or High CRM-entry blocker.

Status: BLOCKED by Issue #173 and OWASP evidence.

### G3 — Architecture

Required:

- global CRM reference reviewed;
- bounded contexts reviewed;
- source-of-truth ownership agreed with ERP, Accounting, HRM, Ecommerce, POS, Workflow, AI, and Data;
- modular delivery decision accepted;
- extraction criteria accepted;
- package dependency rules planned;
- unresolved ADRs identified and assigned.

Status: READY FOR REVIEW.

### G4 — Data and Privacy

Required:

- canonical CRM entities accepted;
- custom-field model accepted;
- data classification and field-level protection model accepted;
- consent, retention, deletion, export, and legal-hold integration points defined;
- Arabic and Latin normalization approach accepted;
- residency and regional deployment constraints documented;
- migration naming and rollback/forward-recovery strategy accepted.

Status: READY FOR REVIEW.

### G5 — API and Events

Required:

- `/api/v1/crm` resource conventions accepted;
- error and pagination contracts accepted;
- idempotency and optimistic-concurrency rules accepted;
- event envelope and initial catalog accepted;
- outbox or reliable publication approach accepted;
- API and event compatibility testing planned;
- no direct cross-domain database integration.

Status: READY FOR REVIEW.

### G6 — Workflow and AI

Required:

- central Workflow Engine integration contracts defined;
- central AI Gateway integration contracts defined;
- no provider-direct CRM AI integration;
- human-confirmation and policy rules defined for high-impact actions;
- non-AI fallback behavior defined;
- workflow and AI failure isolation defined.

Status: PARTIAL — contracts prepared; platform availability must be confirmed.

### G7 — Product and Backlog

Required:

- CRM MVP scope accepted;
- exclusions accepted;
- epics, stories, estimates, priorities, dependencies, acceptance criteria, and DoD reviewed;
- Product Owner, Engineering Owner, Security Owner, and Data Owner assigned;
- Sprint CRM-0 capacity assigned;
- release and change-control rules recorded.

Status: READY FOR REVIEW.

### G8 — Quality and Operations

Required:

- tenant-isolation test matrix;
- authorization and field-level access tests;
- migration and recovery tests;
- Arabic/English UI acceptance plan;
- accessibility plan;
- performance budgets;
- logs, metrics, traces, dashboards, alerts, and runbook plan;
- environment and test-data strategy.

Status: PENDING TEST PLAN APPROVAL.

## Mandatory blockers

CRM implementation must not start while any of these is true:

- Issue #101 has no accepted controlled development baseline;
- Issue #173 remains unresolved for credentials required by the selected runtime path;
- exact-SHA build and tenant-isolation evidence is missing;
- CRM source-of-truth boundaries are disputed;
- tenant context or authorization can be supplied or bypassed by untrusted input;
- no migration recovery approach exists;
- no Product and Engineering owner is assigned;
- production and development environments are not clearly separated.

## Permitted work before GO

- architecture and ADR preparation;
- API/event schema preparation;
- backlog refinement;
- test-design preparation;
- non-production Platform Core remediation;
- documentation reconciliation;
- prototypes that do not create production data or claim release readiness.

## GO decision format

The final owner decision must use one of:

```text
CRM BUILD GATE: GO
CRM BUILD GATE: GO WITH CONDITIONS
CRM BUILD GATE: NO-GO
```

A GO WITH CONDITIONS decision must list:

- allowed scope;
- prohibited scope;
- condition owner;
- evidence required;
- expiry or review point;
- rollback or stop condition.

## Sprint CRM-0 entry checklist

- [ ] Accepted exact main SHA recorded.
- [ ] Platform Core evidence linked.
- [ ] Security evidence linked.
- [ ] CRM architecture approved.
- [ ] Data/privacy review approved.
- [ ] API/event review approved.
- [ ] Product backlog approved.
- [ ] Owners and capacity assigned.
- [ ] Test plan approved.
- [ ] Development environment validated.
- [ ] Explicit owner GO decision recorded.

## Current recommendation

```text
CRM BUILD GATE: NO-GO FOR SOURCE-CODE IMPLEMENTATION
CRM PREPARATION: GO
NEXT REVIEW: after Platform Core and Security evidence closure
```
