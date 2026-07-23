# CRM-010 Quality, Security & Operations — Preparation Package

Control issue: #705

## 1. Control state

```text
BASELINE_MAIN_SHA: 74c6618a60ecd983086553cf75f71b5a6c8d2c9a
MODE: PREPARATION_ONLY
IMPLEMENTATION_COMPLETE: NO
MERGE_AUTHORIZED: NO
ISSUE_CLOSURE_AUTHORIZED: NO
DEPLOYMENT_AUTHORIZED: NO
PRODUCTION_MUTATION_AUTHORIZED: NO
```

This package converts CRM-010 into an executable work programme without asserting that implementation, verification, release or production readiness is complete.

## 2. Scope decomposition

### WP-01 — CRM surface inventory

Deliverables:
- machine-readable inventory of every `/api/v2/crm` operation;
- operation → capability → field policy → tenant boundary mapping;
- write-operation idempotency and concurrency requirements;
- event publisher/subscriber inventory;
- frontend route and user-flow inventory.

Exit criteria:
- no unclassified CRM endpoint;
- no endpoint without an explicit tenant and authorization test requirement;
- internal-only operations are distinguished from public API operations.

### WP-02 — Tenant-isolation acceptance

Required matrix per endpoint:
1. same-tenant authorized success;
2. same-tenant unauthorized denial;
3. cross-tenant resource identifier denial without existence leakage;
4. forged tenant header/body/query rejection;
5. trusted tenant context mismatch rejection;
6. pagination/search isolation;
7. batch and asynchronous job isolation;
8. audit correlation for denied and successful writes.

Evidence:
- unit/service tests;
- PostgreSQL integration tests;
- authenticated API acceptance;
- two-tenant end-to-end flows.

### WP-03 — Capability and field-level authorization

Coverage dimensions:
- role/capability positive and negative cases;
- deny-by-default behavior;
- row ownership restrictions;
- field visibility and mutation restrictions;
- export/import permissions;
- bulk-operation permission equivalence;
- separation-of-duties controls;
- internal-only capabilities.

No test may treat UI hiding as authorization evidence.

### WP-04 — Migration, rollback and recovery acceptance

Preparation must define:
- clean-install migration test;
- upgrade-from-supported-baseline test;
- Flyway validation and checksum integrity;
- forward-only recovery strategy;
- backup/restore preconditions;
- partial migration failure handling;
- schema/data postconditions;
- rollback decision authority;
- immutable evidence fields: exact SHA, database engine/version, migration head, timestamps, artifact digest.

Production restore execution is out of scope for this preparation branch.

### WP-05 — API and event compatibility

Gates:
- OpenAPI generation and committed-contract drift;
- RFC 7807 error compatibility;
- no undocumented breaking operation/schema changes;
- event envelope versioning;
- consumer compatibility tests;
- additive-change policy;
- deprecation window and migration notes;
- idempotency/replay semantics.

### WP-06 — Arabic/English UX acceptance

Matrix:
- Arabic RTL and English LTR routes;
- locale-aware date/time, currency, names, phones and addresses;
- translated validation and authorization errors;
- keyboard-only navigation;
- screen-reader names and focus order;
- responsive desktop/tablet/mobile layouts;
- no unauthorized action rendered;
- empty/loading/error states in both locales.

### WP-07 — Observability contract

Required telemetry:
- structured logs with tenant-safe identifiers, request ID and correlation ID;
- metrics for request rate, errors, latency, saturation, retries, idempotency conflicts, authorization denials, import jobs and search;
- traces across BFF, CRM service, database and central integrations;
- redaction policy prohibiting secrets, raw tokens and restricted customer payloads;
- dashboards for availability, latency, errors, saturation, integrations and background jobs.

Cardinality budgets and tenant data exposure rules are release-blocking design inputs.

### WP-08 — SLI/SLO and alert candidates

Initial candidates, subject to measured baseline approval:
- API availability SLI: successful eligible requests / eligible requests;
- latency SLIs: p50/p95/p99 by operation class;
- correctness SLI: accepted business operations without invariant violation;
- tenant-isolation SLI: zero confirmed cross-tenant disclosures;
- background-job success and freshness;
- workflow/AI optional dependency degradation rate;
- search and import completion latency.

Alert policy must use multi-window burn-rate alerts where an error budget applies. Security isolation failures page immediately and are not governed by an availability error budget.

### WP-09 — Performance baselines

Workloads:
- account/contact/lead/opportunity search;
- Customer 360 reads;
- timeline pagination;
- bulk import validation and commit;
- assignment and ownership queries;
- concurrent optimistic updates.

Each benchmark records dataset size, tenant distribution, hardware/runtime, warm-up, concurrency, duration, exact SHA, p50/p95/p99, throughput, error rate, database query plan evidence and acceptance threshold. Thresholds must be approved from measurements, not invented.

### WP-10 — Runbook and recovery guide

Required sections:
- service ownership and escalation;
- dependency map;
- health/readiness interpretation;
- common failure modes;
- authorization/tenant incident response;
- import/search degradation;
- workflow/AI dependency degradation;
- migration failure response;
- backup/restore handoff;
- evidence collection;
- rollback/forward-fix decision tree;
- post-incident review requirements.

## 3. Planned repository artifacts

```text
docs/crm/crm-010/
  CRM-010-PREPARATION-PACKAGE.md
  CRM-010-TRACEABILITY.md
  CRM-010-OBSERVABILITY-SLO.md
  CRM-010-RUNBOOK.md
```

Executable implementation may later add tests, workflows, dashboards and code only after explicit authorization.

## 4. CI gate design

Proposed non-production gates:
1. CRM API inventory completeness.
2. Tenant isolation and capability matrix.
3. PostgreSQL migration acceptance.
4. OpenAPI/event compatibility.
5. Arabic/English UI acceptance.
6. Security baseline and dependency audit.
7. Performance baseline with regression thresholds.
8. Observability contract validation.
9. Runbook/traceability completeness.

Every gate must bind evidence to the exact PR head SHA. Skips, weakened assertions, accepted HTTP 5xx, broad `continue-on-error`, hidden exclusions and evidence from another SHA are prohibited.

## 5. Risks and blockers

- CRM-009 is still open; CRM-010 must distinguish optional integration degradation from CRM transaction correctness.
- Current production-readiness remediation remains open; this package cannot authorize deployment.
- Endpoint inventories may change as CRM-009 evolves; inventory generation must be automated.
- Performance thresholds require representative datasets and measured infrastructure.
- Restore/RPO/RTO evidence requires a separately authorized operational exercise.

## 6. Preparation definition of done

```text
CONTROL_ISSUE: OPEN
DRAFT_PR: OPEN
SCOPE_DECOMPOSED: YES
TRACEABILITY_DEFINED: YES
TEST_STRATEGY_DEFINED: YES
OBSERVABILITY_AND_SLO_CANDIDATES_DEFINED: YES
RUNBOOK_STRUCTURE_DEFINED: YES
RUNTIME_IMPLEMENTATION: NOT_CLAIMED
MERGE: PROHIBITED
CLOSURE: PROHIBITED
DEPLOYMENT: PROHIBITED
```
