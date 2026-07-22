# CRM-009 — Workflow & AI Integration Implementation Backlog

> **Control issue:** #692  
> **State:** `PREPARED / EXECUTION_BLOCKED_BY_CRM_008`  
> **Estimate scale:** XS=1, S=2, M=3, L=5, XL=8  
> **Rule:** No item below may move to `IN_PROGRESS` before the activation record in `CRM-009-PREPARATION-GATE.md` is approved.

## 1. Delivery objectives

CRM-009 provides platform-governed Workflow and AI integration contracts without moving workflow orchestration or model-provider responsibility into the CRM bounded context.

## 2. Work packages

### WP-00 — Gate, baseline, and dependency reconciliation

**Owner:** CRM Governance + Architecture  
**Estimate:** M  
**Priority:** P0

- CRM-009-S00-01 — Record exact post-CRM-008 `main` SHA. Estimate: XS.
- CRM-009-S00-02 — Reconcile CRM-008 ownership/assignment/transfer events with CRM-009 workflow inputs. Estimate: S.
- CRM-009-S00-03 — Verify central Workflow Engine and AI Gateway contract versions. Estimate: S.
- CRM-009-S00-04 — Record implementation authorization and scope exclusions. Estimate: XS.

Acceptance:

- exact base SHA is immutable;
- #597 is closed and #691 is merged;
- no contract assumption relies on a stub without explicit fail-closed behavior;
- implementation remains blocked if any dependency is unavailable or incompatible.

### WP-01 — Shared integration envelope

**Owner:** Platform Integration + CRM Backend  
**Estimate:** L  
**Priority:** P0

- CRM-009-S01-01 — Define tenant, actor, correlation, causation, locale, and request-version envelope. Estimate: M.
- CRM-009-S01-02 — Define idempotency and replay semantics. Estimate: M.
- CRM-009-S01-03 — Define timeout, cancellation, retry, dead-letter, and fallback classifications. Estimate: M.
- CRM-009-S01-04 — Define capability checks and audit events for request and result handling. Estimate: M.
- CRM-009-S01-05 — Define compatibility and deprecation policy. Estimate: S.

Acceptance:

- every integration request carries tenant and actor context;
- duplicate delivery cannot duplicate CRM mutations;
- retries are bounded and observable;
- unsupported versions fail closed;
- audit records correlate CRM, Workflow, and AI events.

### WP-02 — Assignment workflow contract

**Owner:** CRM Ownership + Workflow Team  
**Estimate:** L  
**Priority:** P0

- CRM-009-S02-01 — Define workflow start request for assignment and reassignment. Estimate: M.
- CRM-009-S02-02 — Define approval, rejection, expiry, cancellation, and completion results. Estimate: M.
- CRM-009-S02-03 — Map CRM-008 assignment and transfer invariants to workflow commands. Estimate: M.
- CRM-009-S02-04 — Define stale-result and concurrent-change handling. Estimate: M.
- CRM-009-S02-05 — Define workflow reference/status projection for CRM reads. Estimate: S.

Acceptance:

- workflow result cannot bypass CRM-008 ownership invariants;
- stale or cross-tenant results are rejected and audited;
- only a completed, authorized result may invoke the normal CRM ownership command;
- failure leaves the existing owner unchanged.

### WP-03 — Opportunity approval contract

**Owner:** CRM Opportunity + Workflow Team  
**Estimate:** L  
**Priority:** P1

- CRM-009-S03-01 — Define approval policies and triggering business conditions. Estimate: M.
- CRM-009-S03-02 — Define immutable approval snapshot and version checks. Estimate: M.
- CRM-009-S03-03 — Define approve, reject, return, cancel, and expire results. Estimate: M.
- CRM-009-S03-04 — Define high-impact mutation confirmation and authorization. Estimate: M.
- CRM-009-S03-05 — Define CRM projection of approval status/history. Estimate: S.

Acceptance:

- approval references the exact opportunity version evaluated;
- changed monetary/stage data invalidates stale approval;
- workflow approval never posts accounting entries or bypasses CRM stage rules;
- all decisions are attributable and auditable.

### WP-04 — Reminder and escalation contract

**Owner:** CRM Activity + Workflow/Notification Teams  
**Estimate:** M  
**Priority:** P1

- CRM-009-S04-01 — Define reminder schedule request and cancellation semantics. Estimate: S.
- CRM-009-S04-02 — Define escalation trigger, recipient resolution, and suppression rules. Estimate: M.
- CRM-009-S04-03 — Define completion/archival cancellation behavior. Estimate: S.
- CRM-009-S04-04 — Define notification handoff without CRM owning delivery. Estimate: S.
- CRM-009-S04-05 — Define duplicate and late-event handling. Estimate: S.

Acceptance:

- completed or archived work cannot generate new unauthorized reminders;
- late events are ignored safely and audited;
- CRM does not become the scheduler or notification transport;
- tenant and visibility rules are preserved.

### WP-05 — AI customer-summary contract

**Owner:** CRM + AI Gateway + Security  
**Estimate:** L  
**Priority:** P1

- CRM-009-S05-01 — Define authorized Customer 360 input projection. Estimate: M.
- CRM-009-S05-02 — Define field minimization, redaction, consent, and retention policy. Estimate: M.
- CRM-009-S05-03 — Define summary response, citations/source references, confidence, and freshness. Estimate: M.
- CRM-009-S05-04 — Define timeout, unavailable, unsafe-output, and partial-data fallback. Estimate: M.
- CRM-009-S05-05 — Define user-visible disclosure and feedback capture. Estimate: S.

Acceptance:

- model provider is never called directly by CRM;
- unauthorized/private fields never enter the AI request;
- summary is labelled as generated and linked to permitted source records;
- AI unavailability returns a safe CRM experience without transaction impact.

### WP-06 — Next-best-action contract

**Owner:** CRM Product + AI Gateway + Policy  
**Estimate:** L  
**Priority:** P1

- CRM-009-S06-01 — Define recommendation inputs and eligible action catalog. Estimate: M.
- CRM-009-S06-02 — Define explanation, confidence, expiry, and policy-decision fields. Estimate: M.
- CRM-009-S06-03 — Separate advisory recommendation from executable CRM command. Estimate: M.
- CRM-009-S06-04 — Define human confirmation and capability recheck at execution time. Estimate: M.
- CRM-009-S06-05 — Define feedback and outcome event without training-data assumptions. Estimate: S.

Acceptance:

- a recommendation cannot mutate CRM by itself;
- execution revalidates current tenant, actor, capability, entity version, and business rules;
- expired or policy-denied recommendations cannot execute;
- rejected recommendations do not degrade normal CRM operations.

### WP-07 — Scoring and explanation contract

**Owner:** CRM Analytics + AI Gateway + Risk  
**Estimate:** L  
**Priority:** P1

- CRM-009-S07-01 — Define score type, scale, version, timestamp, and applicability. Estimate: S.
- CRM-009-S07-02 — Define explanation factors and prohibited sensitive attributes. Estimate: M.
- CRM-009-S07-03 — Define drift, unavailable-score, and stale-score semantics. Estimate: M.
- CRM-009-S07-04 — Define human override and reason capture. Estimate: M.
- CRM-009-S07-05 — Define audit and monitoring events. Estimate: S.

Acceptance:

- score version and freshness are visible;
- prohibited attributes are excluded;
- score cannot be the sole unreviewed basis for a high-impact mutation;
- override is authorized, reasoned, and auditable.

### WP-08 — Security, privacy, resilience, and observability

**Owner:** Security + SRE + Platform  
**Estimate:** XL  
**Priority:** P0

- CRM-009-S08-01 — Complete threat model and abuse-case review. Estimate: M.
- CRM-009-S08-02 — Validate tenant isolation for requests, callbacks, projections, logs, and caches. Estimate: L.
- CRM-009-S08-03 — Validate prompt-injection/content-injection defenses at the AI Gateway boundary. Estimate: M.
- CRM-009-S08-04 — Define metrics, traces, logs, SLO candidates, and alerts. Estimate: M.
- CRM-009-S08-05 — Define circuit breaker and degraded-mode behavior. Estimate: M.
- CRM-009-S08-06 — Define data retention and deletion propagation. Estimate: M.

Acceptance:

- no secret or cross-tenant data is logged;
- every callback is authenticated, authorized, correlated, and replay-safe;
- outages degrade safely without corrupting CRM state;
- security, privacy, latency, error-rate, and fallback metrics are observable.

### WP-09 — Frontend experience and human controls

**Owner:** CRM Frontend + Accessibility + Product  
**Estimate:** L  
**Priority:** P1

- CRM-009-S09-01 — Define workflow status and history surfaces. Estimate: M.
- CRM-009-S09-02 — Define generated-summary and recommendation disclosure. Estimate: S.
- CRM-009-S09-03 — Define confirmation, rejection, feedback, and fallback states. Estimate: M.
- CRM-009-S09-04 — Define Arabic/English, RTL/LTR, keyboard, and screen-reader acceptance. Estimate: M.
- CRM-009-S09-05 — Define loading, timeout, stale, denied, and unavailable states. Estimate: M.

Acceptance:

- AI and workflow states are never represented as completed before authoritative confirmation;
- generated content and confidence/freshness are visible;
- high-impact actions require an explicit confirmation surface;
- all states meet localization and accessibility gates.

### WP-10 — Verification and immutable closure evidence

**Owner:** QA + SRE + CRM Governance  
**Estimate:** XL  
**Priority:** P0

- CRM-009-S10-01 — Run unit, integration, contract, security, tenant-isolation, concurrency, and failure-injection suites. Estimate: L.
- CRM-009-S10-02 — Run browser, localization, accessibility, and visual acceptance. Estimate: M.
- CRM-009-S10-03 — Run performance and resilience acceptance. Estimate: M.
- CRM-009-S10-04 — Run exact-SHA production proof only after separate authorization. Estimate: M.
- CRM-009-S10-05 — Publish evidence index and stage report linked to one unchanged candidate SHA. Estimate: M.

Acceptance:

- all required checks pass on one unchanged SHA;
- zero cross-tenant exposure and zero unexplained CRM HTTP 5xx;
- no Critical or High unresolved security finding;
- evidence artifacts, run IDs, digests, deployments, and exact SHA are linked;
- formal closure does not imply broad commercial go-live.

## 3. Cross-cutting Definition of Done

An implementation item is `DONE` only when:

1. CRM-008 is formally closed and CRM-009 execution is explicitly authorized.
2. Contract, implementation, tests, documentation, and observability are delivered together.
3. Tenant isolation and capability authorization are tested on every applicable path.
4. Idempotency, concurrency, timeout, retry, stale-result, and replay behavior are proven.
5. Audit correlation spans CRM and the platform service involved.
6. Arabic/English, RTL/LTR, accessibility, and safe error states pass where user-facing.
7. No direct workflow runtime or model-provider dependency enters CRM.
8. Required CI and exact-SHA evidence pass with no unexplained 5xx.
9. The control issue remains open until formal stage closure evidence is approved.

## 4. Preparation counters

```text
DOCUMENTATION_FILES_PLANNED: 4
EXECUTABLE_SQL: 0
BACKEND_CHANGES: 0
FRONTEND_CHANGES: 0
WORKFLOW_DEFINITIONS: 0
AI_PROMPTS_OR_PROVIDER_CONFIG: 0
RUNTIME_CONFIG_CHANGES: 0
PRODUCTION_CHANGES: 0
```
