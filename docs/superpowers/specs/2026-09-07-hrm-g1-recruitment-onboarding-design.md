# HRM-G1 Recruitment & Onboarding — Design Specification

```text
REPOSITORY                          = snadaiapp-png/SNAD
G1_BASE_SHA                         = 277dd5efb31e9a9e6a738543a6e6b92bba016486
G0_ENGINEERING_CLOSURE              = PASS
PMV_FINAL_GATE                      = PASS   (PMV #1014 at G1_BASE_SHA)
G1_DESIGN_AUTHORIZATION             = YES
G1_IMPLEMENTATION_PLAN_AUTHORIZATION = YES
G1_IMPLEMENTATION_AUTHORIZATION     = NO
PRODUCTION_AUTHORIZATION            = NO
LEGAL_REVIEW                        = PENDING_HUMAN
```

> **Status:** DESIGN + IMPLEMENTATION PLAN ONLY. This document authorizes no code,
> no migrations, no workflow changes, and no production mutation. G1 implementation
> requires a separate explicit authorization directive.
> **Baseline proof:** `origin/main` verified equal to `G1_BASE_SHA` at Phase 0
> (`git rev-parse origin/main`). Baseline delta `ddf7a775..277dd5ef` (13 files)
> classified **Category A**: CI/workflow/test-lifecycle only
> (#991 test lifecycle, #992 CRM workflow isolation, #988 Task-15 workflow
> notification race-safety, #994 performance-checker path fix, #995 PMV final-gate
> exact checkout). No HR canonical model, no database/security/API contract, and
> no recruitment-area file was touched (the single HR-path file in the delta is a
> test-lifecycle refactor of `HrRlsFailClosedIntegrationTest.java`).
> **G0 closure chain:** G0 merge `748e2c60` → reconciliation `ddf7a775` (#990) →
> test-lifecycle `af52d539` (#991) → CRM CI `7f390c2f` (#992) → performance-path
> fix `d35800bf` (#994) → PMV final-gate checkout fix `277dd5ef` (#995) =
> **G1_BASE_SHA**.

---

## 1. Purpose, authorization state, and reading rules

This specification opens **HRM-G1 — Recruitment & Onboarding** as a separate,
independently governed stage that begins **only after** the G0 engineering
closure was verified on `main` at exact SHA `277dd5efb31e9a9e6a738543a6e6b92bba016486`
(G0 PMV final gate PASS; `G0_CI_BLOCKERS = 0`). It defines architecture, domain
design, technical design, security design, database design, API design, workflow
design, UI/UX functional design, test strategy, task decomposition T1–T12,
acceptance criteria, and dependency/risk analysis — and **nothing else**.

Three reading rules govern every section below. First, G0 is the **only**
authority for identity, employment, organization, compliance, IAM, workflow,
audit, outbox, and idempotency; G1 extends the platform, it never re-models it.
Second, every design element that writes data must name its tenant scope, its
capability, its audit trail, its idempotency key, and its failure behavior
**before** implementation is authorized. Third, anything this document does not
explicitly place in scope is out of scope (§4).

All repository facts cited here were re-verified at the exact baseline SHA:
`HrmIdempotentCommandExecutor` (`hr/api/v2/`), HR outbox infrastructure
(`HrOutboxEvent`, `HrOutboxWorker`, `JdbcHrOutboxRepository`,
`HrAuditOutboxAtomicityIntegrationTest`), `hr/api/v2/HrApiErrorCode` +
`HrApiErrorResponse`, `HrScopedAuthorizationScopeMatrixIntegrationTest`,
36 × `FORCE ROW LEVEL SECURITY` policies keyed on
`current_setting('app.tenant_id', true)::UUID`, Workflow Y2
(`WorkflowDefinitionService`, `WorkflowDefinitionValidator`,
`WorkflowWorkItemService`), HR identity domain (`HrPerson`, `HrPersonPrivate`,
`HrPersonService`), employment domain (`HrEmployee`, `HrEmployeeRepository`),
i18n governance (`scripts/ci/check_i18n_keys.py`, `apps/web/app/hr/hr-labels.ts`),
and Flyway terminal state `20260906.1` at this SHA.

## 2. G0_CANONICAL_AUTHORITY_PRESERVATION_MATRIX

G1 MUST NOT create a second authority for any domain below. The matrix is the
contract every G1 PR is reviewed against. "Allowed writes" is exhaustive — any
write outside it is a design violation and blocks merge.

| Domain | Current authority (G0) | G1 usage | Allowed writes from G1 | Forbidden duplicate model | Integration boundary | Tenant boundary | Audit requirement |
|---|---|---|---|---|---|---|---|
| **Person** | `hr_people`, `hr_person_private`, `hr_person_identifiers`; services in `hr/identity/` (`HrPersonService`, private-identity encryption + blind index) | Candidate is a **recruitment-scoped entity, not a Person row**. Hire conversion creates/links Person **only** via G0 Person services | Hire conversion may insert `hr_people` / `hr_person_private` / `hr_person_identifiers` **through G0 services only** | Candidate-as-Person table, parallel identity store, G1-private person fields | `HrPersonService` API only; no direct SQL to Person tables from G1 modules | All rows tenant-scoped; private identity tenant+person scoped | Conversion emits `HRM.RECRUITMENT.HIRE_COMPLETED` + full audit entry on each G0 write |
| **Employment** | `hr_employees` (+ status/jurisdiction periods, legacy mappings); `HrEmployee`, `HrEmployeeRepository` | Hire conversion creates **exactly one** canonical employment through the G0 employment write path (the path remediated by T-G0-DEF-1) | Employment row + initial status period, conversion-time only | "Future employee" / "candidate employee" tables; G1 employment status machine | G0 employment service transaction; G1 never updates employment state directly afterwards | Tenant-scoped, RLS FORCE | Audit on creation; employment lifecycle events stay G0-owned |
| **Org Unit** | `hr_org_units` (+ versions) | Openings reference an owning org unit (display + ownership semantics only) | **None** (read-only reference) | Parallel org tree | Read via G0 org projection | Inherited via referenced rows | n/a (no G1 mutation) |
| **Job** | `hr_jobs`, `hr_job_versions` | Job Opening references `hr_jobs`/`hr_job_versions` for the requisitioned role; opening never forks the job model | **None** (read-only reference) | Recruitment-side job catalog | Effective-dated read at publication | Inherited | n/a |
| **Position** | `hr_positions`, `hr_position_versions`; occupancy derived from assignments | Offer may target a position; occupancy is **derived**, never directly set by G1 | **None** (occupancy re-check is read-only inside conversion) | Recruitment-side occupancy counter as truth | Read + occupancy re-check inside conversion transaction | Inherited | Occupancy re-check result recorded in conversion audit |
| **Assignment** | `hr_employee_assignments` | Created **only** by hire conversion via G0 assignment services | One assignment row per conversion, G0 service-mediated | Parallel assignment ledger | G0 assignment service | Tenant-scoped | Audit + G0 `HRM.ASSIGNMENT.CREATED` audit code |
| **Contract** | `hr_employment_contracts`, `hr_employment_contract_versions` | Offer terms **project** into a versioned contract at conversion; G1 never writes contract tables directly | Conversion-time contract + version, G0 contract service-mediated | Offer-as-contract tables | G0 contract service | Tenant-scoped | Audit; `HRM.CONTRACT.CREATE/ACTIVATE` codes unchanged |
| **Compensation** | `hr_compensation_packages`, `hr_compensation_components` | Offer compensation draft mirrors into the G0 aggregate at conversion; reads of offer compensation are sensitive-read audited | Conversion-time package/components via G0 compensation services | Recruitment pay table | G0 compensation service | Tenant-scoped | Sensitive-read audit (`HRM.COMPENSATION.VIEW` semantics) on offer reads |
| **Compliance** | `hr_compliance_decisions`, `hr_compliance_rules`, `hr_country_packs`, `CountryPolicyResolver`, `ComplianceEngine` | Publishing an opening (and any statutory recruitment action) resolves through the same resolver/engine; unknown/absent pack ⇒ fail closed with a decision row | New compliance decision rows **written by the resolver** (G1 passes parameters, not verdicts) | G1-side statutory rule engine | `CountryPolicyResolver`/`ComplianceEngine` only | Decisions tenant-scoped | Every decision persisted, fail-closed, no silent pass |
| **IAM** | `hr_iam_access_bindings`, `HrmIamAccessPolicy`, `IamEmploymentAccessPort`, `HrmIamEventConsumer` | All G1 capability checks flow through the G0 access policy; IAM lifecycle boundary consumed, never bypassed | **None** (no IAM writes from G1) | G1 role store, implicit account activation, recruitment-side permission cache | Capability checks via G0 policy; account provisioning stays an explicit IAM-side contract | Bindings tenant-scoped | Access-denied events auditable at G0 layer |
| **Workflow** | Workflow Orchestration Platform (Y2): `WorkflowDefinitionService`, `WorkflowDefinitionValidator`, `WorkflowWorkItemService` | Opening approval, offer approval, hire approval (optional per tenant policy), onboarding tasks as work items | Work-item create/complete/cancel through Y2 services; workflow definitions seeded as versioned seed data | Recruitment-side approval state machine that bypasses Y2 | Event contracts (§11) + work-item APIs; no cross-module DB writes | Work items carry tenant context | Y2 audit + G1 audit on the business transition |
| **Audit** | `hr_audit_ledger`, `hr_audit_delivery` (transactional, fail-closed) | Every G1 state transition emits an audit entry in the same transaction | Audit rows via existing audit writer | Parallel audit table, fire-and-forget audit | G0 audit service; write without audit ⇒ rollback | Rows tenant-scoped | `AUDIT_FAIL_OPEN` is a stop condition |
| **Outbox** | `hr_domain_event_outbox` (`event_type VARCHAR(150)`), `HrOutboxWorker`, `HrOutboxEventConsumer` | G1 publishes §11 events via the same transactional outbox | Outbox rows in the same transaction as the state change | Parallel outbox/publisher, direct cross-module reads | `HrOutboxEvent` contract; consumers by contract only | Events tenant-stamped | Outbox row + audit row atomic together |
| **Idempotency** | `hr_idempotency_records`, `HrmIdempotentCommandExecutor` | All mutating G1 API commands execute through the executor; hire conversion adds a dedicated terminal-state ledger (`hr_hire_conversions`) | Idempotency records via the executor; conversion ledger rows | Ad-hoc dedup tables per endpoint | Executor wraps every mutating command | Keys tenant-scoped | Replay detection audited |

**Non-negotiable rules derived from the matrix:** (1) Candidate is not a Person
authority substitute; (2) hire conversion creates no parallel employee identity
model; (3) Employment remains the canonical employment authority; (4) IAM
account creation/activation never happens implicitly from recruitment; (5) the
Workflow Engine remains the sole approvals/process authority; (6) Audit/Outbox/
Idempotency reuse the existing infrastructure; (7) no parallel tenant model;
(8) no parallel RBAC model.

## 3. Authoritative G1 functional scope

The authorized functional scope is exactly this list (31 items). Each item maps
to the design sections that specify it and to the T1–T12 tasks that will
implement it after separate authorization.

1. **Job Openings** — requisition lifecycle with compliance-gated publication (§5, §6.1, §13.1)
2. **Candidates** — recruitment-scoped candidate identity with minimized PII (§5, §10)
3. **Applications** — candidate↔opening edge with full stage history (§5, §6.2)
4. **Recruitment Pipeline** — stage board per opening, derived from application state (§14.3)
5. **Screening** — screening outcomes recorded on the application (§5, §6.2)
6. **Interviews** — scheduling, panel, modes (§5, §13.4)
7. **Interview feedback** — per-participant structured scorecards (§5, §13.4)
8. **Offers** — versioned offer with compensation draft (§5, §6.3)
9. **Offer approval** — optional Workflow-gated approval before extension (§11)
10. **Offer lifecycle** — DRAFT→EXTENDED→ACCEPTED/DECLINED/EXPIRED/WITHDRAWN (§6.3)
11. **Candidate → Hire conversion** — the atomic/idempotent boundary (§7)
12. **Onboarding Plans** — conversion-created or standalone (§5, §7)
13. **Onboarding Checklists** — versioned templates (§5)
14. **Onboarding Tasks** — assignee, due dates, completion/waiver (§5, §6.4)
15. **Recruitment APIs** — canonical HR v2 API family (§13)
16. **Onboarding APIs** — same family (§13)
17. **OpenAPI contracts** — contract-complete, test-enforced (§13, §16)
18. **Operational Web UI** — 15 functional screens (§14)
19. **RBAC** — capability families under existing convention (§9)
20. **Tenant isolation / RLS** — FORCE RLS on every G1 table (§8)
21. **Audit** — every transition, fail-closed (§2 Audit row, §12)
22. **Idempotency** — every mutating command (§2 Idempotency row)
23. **Workflow integration** — Y2 as process authority (§11)
24. **Event contracts / Outbox** — typed, versioned, tenant-stamped (§12)
25. **Arabic / RTL** — logical CSS, i18n keys, governance script (§14)
26. **Accessibility** — G0 standard (semantic structure, focus, contrast, keyboard) (§14)
27. **PostgreSQL Direct test strategy** — host-native only (§16)
28. **PII handling** — classification matrix + minimization (§10)
29. **Search/filter/sort/pagination** — deterministic, cursor-based (§13)
30. **Error model** — G0 `HrApiErrorCode` contract (§13, §15)
31. **Concurrency strategy** — optimistic locking + boundary re-checks (§15)

## 4. G1_OUT_OF_SCOPE

The following are **excluded from G1 entirely**. They may be referenced as
future dependencies in prose, but no G1 artifact (design, code, migration, UI
string, or test) may implement, claim, or partially stage them:

```text
Payroll execution               Attendance                  Timesheets
Leave                           Leave accrual               Scheduling
Benefits execution              Performance management      Training / LMS
Government integrations (any, beyond what G0 already resolves)
Production deployment           Automatic IAM activation    G2 implementation
```

Additionally out of scope for G1: calendar-service integrations for interviews
(manual date/time only), external job-board publishing, background-check vendor
integrations, offer e-signature services, and any cross-tenant recruitment
access. Where a future dependency is unavoidable in a payload (e.g., compensation
fields that payroll will eventually consume), G1 stores business data only and
marks the downstream consumer as a documented future contract — never an active
integration.

## 5. Domain model design (design-level only — no migrations)

Entity naming below was reviewed against repository conventions: aggregates live
in `com.sanad.platform.hr.<domain>.domain` (e.g., `HrPerson`, `HrEmployee`),
tables are `hr_*` snake_case plural, versioned tables carry `_versions` suffixes,
and state-period tables follow `hr_employment_status_periods` shape. Names below
are therefore prefixed `Hr`/`hr_` and follow those patterns. All tables carry
`id UUID PK (v7-style time-ordered)`, `tenant_id UUID NOT NULL`, created/updated
attribution columns, and the standard G0 audit linkage; per-entity details below
list only what is specific.

### 5.1 Aggregates

```text
HrJobOpening (aggregate root)
├── identity: opening_number (human-readable, tenant-unique, HRU-<year>-<seq>)
├── references: job_id + job_version_id (effective-dated snapshot at publication),
│               org_unit_id, position_id? (target, optional)
├── lifecycle: DRAFT → PENDING_APPROVAL → OPEN → PAUSED → CLOSED | CANCELLED
│               (state machine §6.1; every transition dated, attributed, audited)
├── headcount: requested int ≥1; filled int derived ONLY from converted hires
├── compliance: CountryPolicyResolver decision bound at publish; republish re-resolves
├── publish window: opens_at?/closes_at? effective-dated
└── audit + events: OPENING_CREATED/PUBLISHED/PAUSED/RESUMED/CLOSED/CANCELLED

HrCandidate (aggregate root — recruitment-scoped identity, NOT hr_people)
├── identity: display_name, candidate_number (tenant-unique)
├── contact: minimized channels (email/phone normalized for dedup, stored per §10)
├── PII class: CONTACT_MINIMIZED; NO national-ID/private identity rows ever
│   (private identity is a Person concept, G0-owned, §10)
├── pool state: ACTIVE | HIRED | WITHDRAWN | ARCHIVED (history retained)
├── duplicate-detection: tenant-scoped normalized contact match → warning + audit,
│   never a silent merge
└── invariants: email/phone uniqueness enforced at (tenant_id, normalized_hash)
    level only as a soft advisory (candidates may legitimately re-apply)

HrApplication (aggregate root; the Candidate↔JobOpening edge)
├── state: APPLIED → SCREENING → INTERVIEW → OFFER → HIRED | REJECTED | WITHDRAWN
│          (state machine §6.2)
├── stage history: versioned rows (pipeline_stage_periods), like G0 status periods
├── uniqueness: ONE active application per (candidate, opening); re-application
│   after terminal state = NEW row linked by candidate (history preserved)
├── withdrawal: candidate-attributed or recruiter-attributed, reason required
└── hiring constraint: transition to HIRED only through §7 conversion success

HrInterview (child of HrApplication)
├── schedule: planned_at, duration, mode (ONSITE | REMOTE | PHONE)
├── panel: interview participants (user ids via IAM; tenant users only)
├── outcome: SCHEDULED → DONE (PASSED | FAILED) | CANCELLED | NO_SHOW
└── feedback: one structured scorecard per participant (§13.4), versioned on edit

HrOffer (child of HrApplication)
├── payload: position intent, contract terms draft, compensation draft
│   (structures mirror G0 contract/compensation fields — projection source)
├── versioning: OfferVersion rows; edits after EXTENDED create a new version
│   and require re-extension (never in-place mutation of an extended offer)
├── state: DRAFT → PENDING_APPROVAL? → EXTENDED → ACCEPTED | DECLINED
│          | EXPIRED | WITHDRAWN (state machine §6.3)
├── expiry: expires_at effective-dated; acceptance after expiry fails closed
└── approval: optional Workflow approval before EXTENDED (§11.2)

HrHireConversion (explicit boundary object, terminal-state ledger)
├── input: accepted Offer (application state = OFFER, offer state = ACCEPTED)
├── key: (tenant_id, offer_id) unique; conversion_state terminal once COMPLETE
├── output refs: person_id, employment_id, assignment_id, contract_id,
│                compensation_package_id, onboarding_plan_id (all nullable until done)
└── atomic + idempotent + fail-closed (§7); the ledger row is written in the
    SAME transaction as every output

HrOnboardingPlan (aggregate root, created by conversion or standalone)
├── identity: plan_number, subject employment_id (must exist), template ref?
├── lifecycle: ACTIVE → COMPLETED | CANCELLED (completion derived, §6.4)
└── checklists: versioned template instances → tasks

HrOnboardingChecklistTemplate / HrOnboardingChecklist
├── template: tenant-scoped, versioned; one seeded generic template (§19 Q3)
└── instance: materialized from template at plan creation; template edits never
    mutate existing instances (snapshot semantics)

HrOnboardingTask (child of plan/checklist)
├── state: OPEN → DONE | WAIVED (waiver requires capability + reason, §6.4)
├── assignee: tenant user via IAM; due date optional
└── completion derivation: plan completes when no OPEN tasks remain (no manual %)
```

### 5.2 Tables, keys, and constraints (declarative — implemented only after authorization)

```text
hr_job_openings          UK(tenant_id, opening_number); FK job_id→hr_jobs,
                         job_version_id→hr_job_versions, org_unit_id, position_id?
hr_job_opening_periods   opening lifecycle history (state, from, to, actor, reason)
hr_candidates            UK(tenant_id, candidate_number); idx (tenant_id, contact_email_hash)
                         idx (tenant_id, contact_phone_hash)
hr_applications          UK(tenant_id, candidate_id, job_opening_id) WHERE active;
                         FK candidate_id, job_opening_id
hr_application_stage_periods  application lifecycle history (like G0 status periods)
hr_interviews            FK application_id; idx (tenant_id, application_id, planned_at)
hr_interview_participants FK interview_id, user_id (tenant users via IAM)
hr_interview_feedback    UK(interview_id, participant_id); scorecard JSONB (schema-validated)
hr_offers                FK application_id; UK(tenant_id, offer_number)
hr_offer_versions        FK offer_id; seq-int version; immutable rows
hr_hire_conversions      UK(tenant_id, offer_id); terminal-state ledger; output FKs
hr_onboarding_plans      UK(tenant_id, plan_number); FK employment_id→hr_employees
hr_onboarding_checklist_templates    UK(tenant_id, code); versioned
hr_onboarding_checklists FK plan_id, template_version snapshot
hr_onboarding_tasks      FK checklist_id, plan_id; idx (tenant_id, plan_id, state)

Retention/archive: application-level archive flags on candidates/applications
(ARCHIVED pool state / CLOSED+archived openings); no physical DELETE on any
canonical path — state transitions only (G0 convention). Recruitment rows are
tenant business records: archive-by-state, purge only via a future explicit
retention policy contract (§10).
```

### 5.3 Per-entity design template (applied to every aggregate)

For every aggregate in §5.1, the following fields are fixed by this design and
bind the implementing task: **responsibility** (single business object it
owns); **tenant_id** (mandatory, UUID, part of every unique key and every RLS
policy); **primary key strategy** (UUID, time-ordered; no client-supplied IDs);
**lifecycle/state machine** (§6); **invariants** (listed per aggregate above;
violations fail closed); **unique constraints** (§5.2); **relations** (FKs
listed; no cross-module FK to non-HR tables); **audit requirements** (every
state transition + every sensitive read per §2/§10); **PII classification**
(§10); **idempotency requirements** (all mutations via
`HrmIdempotentCommandExecutor`; conversion additionally via ledger §7);
**soft-delete/archive policy** (state-based archive only, §5.2); **retention
considerations** (per §10; no autonomous purge in G1).

---

## 6. State machines (explicit, review-gated)

State names were reviewed against G0 conventions: G0 uses uppercase domain
states persisted with effective-dated period rows and attributed transitions.
The directive's indicative JobOpening states (PENDING_APPROVAL, OPEN, PAUSED)
are adopted; audit/outbox event codes follow the existing
`HRM.<DOMAIN>.<STATE_CHANGE>` pattern.

### 6.1 HrJobOpening

```text
DRAFT ──submit──▶ PENDING_APPROVAL ──approve──▶ OPEN ──pause──▶ PAUSED
  │                     │                        │ ▲                │
  │                     │                        ▼ │──resume─────────┘
  │                     │                       (OPEN)
  │                     ▼
  │                reject──▶ DRAFT (with reason)
  ├──cancel──▶ CANCELLED        PENDING_APPROVAL ──cancel──▶ CANCELLED
  └──(any pre-OPEN state)──▶ CANCELLED
OPEN/PAUSED ──close──▶ CLOSED (closes_at or manual; reason recorded)
```

| Rule | Specification |
|---|---|
| Allowed transitions | DRAFT→PENDING_APPROVAL; PENDING_APPROVAL→OPEN (approve) or →DRAFT (reject, reason mandatory) or →CANCELLED; OPEN→PAUSED→OPEN; OPEN/PAUSED→CLOSED; DRAFT/PENDING_APPROVAL/OPEN/PAUSED→CANCELLED |
| Forbidden transitions | Any →DRAFT from OPEN/PAUSED/CLOSED (edit-forward only via new state events); CLOSED→anything; CANCELLED→anything; DRAFT→OPEN directly (approval gate cannot be skipped when tenant policy requires approval — policy default: approval required); PAUSED→CLOSED allowed but must pass through compliance re-check if statutory window changed |
| Who may transition | create/submit/edit: `HRM.RECRUITMENT.OPENING.MANAGE`; approve: `HRM.RECRUITMENT.OPENING.PUBLISH` (separation of duties: submitter ≠ approver when approval is enabled); pause/resume/close/cancel: `HRM.RECRUITMENT.OPENING.MANAGE` |
| Workflow approval requirement | If tenant policy `opening_approval_required` = true (default true), PENDING_APPROVAL→OPEN requires the Y2 "Job Opening Approval" workflow to complete APPROVED (§11.1). Workflow REJECTED ⇒ back to DRAFT. Workflow CANCELLED (e.g., opening cancelled) ⇒ opening CANCELLED |
| Retry behavior | Approve/publish retries are idempotent via `HrmIdempotentCommandExecutor` (same request-id returns same result); compliance re-resolution on republish is deterministic (same pack+rules ⇒ same decision) |
| Concurrency behavior | Optimistic lock (version column; `If-Match` at API layer). Two concurrent approvals: one wins, the other gets 409 state-conflict. Concurrent pause+close: serialized by row lock; loser 409 |
| Audit event | Every transition: audit row with from/to state, actor, capability, reason?, timestamp — same transaction |
| Outbox event | `HRM.RECRUITMENT.OPENING_PUBLISHED / OPENING_PAUSED / OPENING_RESUMED / OPENING_CLOSED / OPENING_CANCELLED` (§12) |

### 6.2 HrApplication

```text
APPLIED ──▶ SCREENING ──▶ INTERVIEW ──▶ OFFER ──▶ HIRED
   │            │             │            │
   └──▶ REJECTED ◀────────────┴────────────┘  (any pre-HIRED state, reason mandatory)
   └──▶ WITHDRAWN (candidate-initiated any time pre-HIRED; recruiter-initiated only per policy)
```

| Rule | Specification |
|---|---|
| Allowed transitions | APPLIED→SCREENING/REJECTED/WITHDRAWN; SCREENING→INTERVIEW/REJECTED/WITHDRAWN; INTERVIEW→OFFER/REJECTED/WITHDRAWN; OFFER→HIRED (only via §7 conversion success) /REJECTED/WITHDRAWN |
| Forbidden transitions | Backward transitions (SCREENING→APPLIED, INTERVIEW→SCREENING, OFFER→INTERVIEW, HIRED→anything); REJECTED/WITHDRAWN→any active state (re-application = new row); APPLIED→OFFER (stages cannot be skipped); APPLIED/SCREENING/INTERVIEW→HIRED (only OFFER→HIRED, only via conversion) |
| Who may transition | stage advance/reject: `HRM.RECRUITMENT.APPLICATION.ADVANCE` / `.REJECT`; withdraw-self: candidate-attributed via authenticated candidate surface (or recruiter on behalf per tenant policy with `HRM.RECRUITMENT.APPLICATION.MANAGE`); hire: `HRM.RECRUITMENT.HIRE.CONVERT` |
| Workflow approval requirement | None for standard stage movement (screening approval workflow is a §19 open question — default: none). HIRED transition is gated by the Hire Approval workflow when tenant policy enables it (§11.3) |
| Retry behavior | Stage transitions idempotent per request-id; rejecting an already-rejected application returns the original result (no duplicate rejection rows) |
| Concurrency behavior | Optimistic lock; concurrent advance+reject: first commit wins, loser 409. Concurrent advance to two different stages impossible: transition validates current state inside the same transaction (row-locked read) |
| Audit event | Every transition with reason (rejection/withdrawal reason mandatory), actor, capability |
| Outbox event | `HRM.RECRUITMENT.APPLICATION_ADVANCED / APPLICATION_REJECTED / APPLICATION_WITHDRAWN` (§12) |

### 6.3 HrOffer

```text
DRAFT ──(approval? yes)──▶ PENDING_APPROVAL ──approve──▶ EXTENDED
  │                            │
  │                            └──reject──▶ DRAFT (with reason)
  └──(approval? no)──▶ EXTENDED
EXTENDED ──accept──▶ ACCEPTED ──(§7)──▶ HIRED (application+offer terminal pair)
   ├──decline──▶ DECLINED
   ├──withdraw──▶ WITHDRAWN (before acceptance only)
   └──expire──▶ EXPIRED (scheduled expiry resolution; acceptance-after-expiry fails closed)
```

| Rule | Specification |
|---|---|
| Allowed transitions | DRAFT→PENDING_APPROVAL (if policy) or DRAFT→EXTENDED; PENDING_APPROVAL→EXTENDED (approve) or →DRAFT (reject); EXTENDED→ACCEPTED/DECLINED/WITHDRAWN/EXPIRED; ACCEPTED→(conversion)→terminal |
| Forbidden transitions | EXTENDED→DRAFT (edits create a new OfferVersion and require re-extension through approval); WITHDRAWN/DECLINED/EXPIRED/ACCEPTED→anything; two ACCEPTED offers for the same application (single-acceptance invariant); EXTENDED without approval when policy requires it |
| Who may transition | create/edit: `HRM.RECRUITMENT.OFFER.MANAGE`; extend: `HRM.RECRUITMENT.OFFER.EXTEND`; approve: `HRM.RECRUITMENT.OFFER.APPROVE` (separation of duties: extender ≠ approver); accept: candidate (authenticated acceptance surface, tenant-scoped); withdraw: `HRM.RECRUITMENT.OFFER.MANAGE` with reason |
| Workflow approval requirement | When tenant policy `offer_approval_required` = true (default true), EXTENDED requires the Y2 "Offer Approval" workflow APPROVED (§11.2). Approval cancellation (offer withdrawn) ⇒ Y2 work item cancelled |
| Retry behavior | Accept retry with same request-id returns same result; extend retry idempotent via executor; expiry resolution is a deterministic scheduled transition (idempotent by state check) |
| Concurrency behavior | Concurrent accept+decline: single winner (row-locked state check), loser 409. Concurrent accept+withdraw: accept wins if it committed first; withdraw after accept is forbidden (409 state-conflict). Concurrent acceptance of two offers in different applications targeting one single-occupancy position: NOT serialized here — serialized inside conversion occupancy re-check (§7) |
| Audit event | Every transition incl. compensation-draft sensitive-read flag on approval views |
| Outbox event | `HRM.RECRUITMENT.OFFER_EXTENDED / OFFER_ACCEPTED / OFFER_DECLINED / OFFER_WITHDRAWN / OFFER_EXPIRED` (§12) |

### 6.4 HrOnboardingPlan / HrOnboardingTask

```text
Plan:  ACTIVE ──(all tasks terminal)──▶ COMPLETED ; ACTIVE ──cancel──▶ CANCELLED
Task:  OPEN ──▶ DONE (assignee or `HRM.ONBOARDING.TASK.COMPLETE`)
       OPEN ──▶ WAIVED (capability `HRM.ONBOARDING.TASK.WAIVE` + reason mandatory)
```

| Rule | Specification |
|---|---|
| Allowed transitions | Plan ACTIVE→COMPLETED (derived, not manual) / ACTIVE→CANCELLED (with reason; cascades cancel OPEN tasks); Task OPEN→DONE/WAIVED |
| Forbidden transitions | Plan COMPLETED/CANCELLED→anything; manual COMPLETED (percent fields do not exist); DONE/WAIVED→OPEN (corrections = new audit-annotated note task; state stays terminal); DONE→WAIVED or WAIVED→DONE |
| Who may transition | Task DONE: assignee or `HRM.ONBOARDING.TASK.COMPLETE`; WAIVED: `HRM.ONBOARDING.TASK.WAIVE` (distinct, more privileged capability); plan cancel: `HRM.ONBOARDING.PLAN.MANAGE` |
| Workflow approval requirement | None by default; onboarding tasks MAY be Y2 work items when tenant policy links the plan to a workflow template (§11.4) — task completion flows back by event, never by direct write |
| Retry behavior | Task completion retry idempotent (same request-id ⇒ same result); double-completion returns existing completion |
| Concurrency behavior | Concurrent complete+waive: first wins, loser 409. Plan completion check is transactional on the last task transition |
| Audit event | Task transitions with actor/reason; plan completion derived event |
| Outbox event | `HRM.ONBOARDING.PLAN_CREATED / TASK_COMPLETED / TASK_WAIVED / PLAN_COMPLETED / PLAN_CANCELLED` (§12) |

## 7. Candidate → Hire conversion — the atomic/idempotent boundary

The single most safety-critical boundary in G1. Design properties: **ATOMIC**
(one database transaction), **IDEMPOTENT** (ledger + executor keys),
**AUDITABLE** (audit rows in-transaction), **TENANT_SCOPED** (every key
tenant-qualified), **RETRY_SAFE** (replays return the original result).

### 7.1 Transaction boundary and contract sequence

Single database transaction (isolation READ COMMITTED with explicit row locks
on the invariants it checks; no nested transactions, no async steps inside):

```text
BEGIN
 1. Lock + load Offer (tenant-scoped, FOR UPDATE): require state=ACCEPTED,
    application state=OFFER, application not yet HIRED.
 2. Ledger check: SELECT hr_hire_conversions WHERE tenant_id AND offer_id.
    If COMPLETE → return original result (idempotent replay; HTTP 200 with
    original refs). If FAILED/terminal → per §7.3 recovery rules.
 3. Idempotency executor admission: request-id recorded in hr_idempotency_records.
 4. Person resolution: blind-index match on candidate contact + supplied
    identity claims →
      a) existing Person match (unambiguous) → link (no new Person row);
      b) no match → create Person (+private identity + identifiers) THROUGH
         G0 person services only (HrPersonService path);
      c) ambiguous/conflicting claims → FAIL CLOSED for human review (no guess).
 5. Employment creation: exactly one canonical employment via the G0 employment
    write path (real Person identity projected; real workerClassificationCode
    persisted; initial status period opened).
 6. Assignment: create via G0 assignment services (effective-dated, position
    intent from offer). If offer targeted a position: occupancy re-check inside
    the transaction (re-read assignments for the position); over-occupancy ⇒
    abort (fail closed).
 7. Contract: create versioned contract (+version) via G0 contract services
    from the offer version terms; activate per tenant onboarding policy.
 8. Compensation: mirror offer compensation draft into hr_compensation_packages/
    components via G0 compensation services.
 9. Onboarding: create HrOnboardingPlan (+checklist instance + tasks) for the
    new employment. Onboarding creation failure ⇒ abort (§7.3 case 6).
10. Ledger write: hr_hire_conversions row → COMPLETE with all output refs.
11. Application → HIRED (state machine §6.2), opening headcount filled += 1
    (derived update through opening aggregate).
12. Audit rows (every write above) + outbox rows (HRM.RECRUITMENT.HIRE_COMPLETED
    carrying employment_id + person_id; HRM.ONBOARDING.PLAN_CREATED) — same
    transaction.
COMMIT   (no IAM call inside the transaction — §7.2; no post-commit async step
          is part of the atomic boundary)
```

Steps 4–9 all run through **existing G0 services**; G1 writes no canonical
tables directly. The transaction is the only place recruitment may touch
`hr_people`/`hr_employees` paths.

### 7.2 Explicit non-goals inside the boundary

- **No IAM activation.** No IAM account is created, activated, or granted here.
  IAM provisioning remains an explicit, separate, contract-driven process
  consuming `HIRE_COMPLETED` per IAM's own authorization rules. If IAM is
  unavailable, conversion still succeeds (§7.3 case 7) — recruitment and access
  provisioning are deliberately decoupled.
- **No payroll/attendance/leave artifacts** (G1_OUT_OF_SCOPE).
- **No direct workflow mutation** beyond cancelling/completing linked work
  items after commit via Y2 APIs (compensating action, not part of atomicity).

### 7.3 Failure scenario matrix (normative)

| # | Scenario | Required behavior |
|---|---|---|
| 1 | Person already exists (returning employee / prior contact converted to Person) | Blind-index match → **link** to existing Person; conversion proceeds with `person_reused=true` recorded in ledger + audit. No duplicate `hr_people` row may ever be created for the same verified identity claims |
| 2 | Candidate email duplicates an existing Person's email (no other match) | NOT an automatic link. Ambiguity rule §7.1.4c: conversion fails closed (`CONVERSION_IDENTITY_AMBIGUOUS`), human review required. Never silently merge identities on email alone |
| 3 | Conversion replayed with the SAME idempotency key | Ledger hit (§7.1.2) → return the ORIGINAL result (same employment/person refs), HTTP 200, `replayed=true`. No second employment, no second assignment, no second onboarding plan. Audit: replay access recorded |
| 4 | Conversion retried with a DIFFERENT idempotency key (same offer) | Ledger check is keyed on `(tenant_id, offer_id)`, not on request-id → ledger hit returns original result with `replayed=true`; executor records the new request-id as a duplicate-of-conversion. Uniqueness of `(tenant_id, offer_id)` makes double-hire structurally impossible |
| 5 | Transaction fails mid-way (crash/constraint/timeout) | Full ROLLBACK (single transaction; no partial hire states can exist). Ledger row rolled back with everything else. Retry (either key) starts clean; advisory: failure reason logged in application logs, not in canonical tables |
| 6 | Onboarding creation fails (template missing/invalid) | Abort + rollback (§7.1.9). `CONVERSION_ONBOARDING_TEMPLATE_INVALID` error. Tenant must fix template before re-conversion. No hire without a plan (onboarding is part of the atomic output set) |
| 7 | IAM unavailable | Conversion proceeds and commits (IAM is explicitly out of the boundary, §7.2). Access provisioning happens later through IAM's own contract consuming `HIRE_COMPLETED`; a documented reconciliation query lists hires lacking IAM binding for IAM-side ops — no automatic activation ever |
| 8 | Concurrent conversion of the same offer (double-submit race) | Both transactions attempt to insert `hr_hire_conversions (tenant_id, offer_id)`; unique constraint serializes: winner commits COMPLETE, loser receives constraint violation → converts to idempotent-read path (return winner's result, `replayed=true`). Zero duplicate hires |
| 9 | Tenant mismatch (offer tenant ≠ actor tenant ≠ target employment tenant) | Every step is tenant-qualified; RLS makes cross-tenant reads return empty and writes fail; conversion pre-condition asserts single tenant context (`app.tenant_id`) — mismatch ⇒ `TENANT_CONTEXT_MISMATCH` fail-closed before any write. No cross-tenant conversion is possible |

## 8. Tenant / RLS security design

Every G1 table gets the G0 policy shape, verified at baseline:
`ENABLE ROW LEVEL SECURITY` + `FORCE ROW LEVEL SECURITY` + policy
`USING (tenant_id = current_setting('app.tenant_id', true)::UUID)` (and matching
`WITH CHECK`). Missing tenant context (`current_setting(..., true)` → NULL)
yields an empty result set / denied write — **fail closed by construction**.

### 8.1 RLS matrix (applies to every table in §5.2)

| Table | OWN_TENANT_READ | OWN_TENANT_WRITE | CROSS_TENANT_READ | CROSS_TENANT_WRITE | NO_CONTEXT | FORCE_RLS |
|---|---|---|---|---|---|---|
| hr_job_openings (+periods) | allowed (capability-gated at app layer) | allowed | DENIED (empty set) | DENIED (42501-class policy violation) | DENIED (empty) | YES |
| hr_candidates | allowed | allowed | DENIED | DENIED | DENIED | YES |
| hr_applications (+stage periods) | allowed | allowed | DENIED | DENIED | DENIED | YES |
| hr_interviews (+participants, feedback) | allowed | allowed | DENIED | DENIED | DENIED | YES |
| hr_offers (+versions) | allowed (sensitive-read audit on compensation fields) | allowed | DENIED | DENIED | DENIED | YES |
| hr_hire_conversions | allowed (restricted: completed refs) | insert-only app path | DENIED | DENIED | DENIED | YES |
| hr_onboarding_plans / checklists / templates / tasks | allowed | allowed | DENIED | DENIED | DENIED | YES |

### 8.2 Role and environment contract (unchanged, restated)

- Application role contract stays `NOSUPERUSER NOBYPASSRLS` (CI asserts the
  role contract query; G1 adds its tables to that proof — G1-T2).
- Test/acceptance path: **HOST_NATIVE_POSTGRESQL_DIRECT_ONLY** — host-native
  PostgreSQL started directly on the runner (G0 JOB C pattern: `psql --version`
  + `pg_isready` proofs, least-privilege provisioning, role-contract assertion).
  **Docker, Testcontainers, and service containers are forbidden.**
- No superuser data paths, no `BYPASSRLS` escape hatches, no role impersonation
  in G1 code or tests.
- Admin/HR-operational roles are application capabilities (§9), NOT database
  superusers; an admin without the tenant context sees nothing (fail closed).

## 9. RBAC design (aligned to existing convention)

Conceptual names in the directive map onto the repository's canonical
`HRM.<DOMAIN>.<ACTION>` capability convention (verified at baseline:
`HRM.EMPLOYEE.*`, `HRM.ASSIGNMENT.*`, `HRM.CONTRACT.*`, `HRM.COMPENSATION.*`,
`HRM.COMPLIANCE_OVERRIDE.*`, `HRM.AUDIT.VIEW`). The G1 capability families are:

| Conceptual directive name | Adopted capability (convention-aligned) | Grant notes |
|---|---|---|
| recruitment.job-opening.read | `HRM.RECRUITMENT.OPENING.VIEW` | broad for HR operators |
| recruitment.job-opening.write | `HRM.RECRUITMENT.OPENING.MANAGE` | create/submit/pause/resume/close/cancel |
| (approval separation) | `HRM.RECRUITMENT.OPENING.PUBLISH` | approve PENDING_APPROVAL→OPEN |
| recruitment.candidate.read | `HRM.RECRUITMENT.CANDIDATE.VIEW` | minimized fields per §10 |
| recruitment.candidate.write | `HRM.RECRUITMENT.CANDIDATE.MANAGE` | create/update/archive |
| recruitment.application.manage | `HRM.RECRUITMENT.APPLICATION.MANAGE` | + `.ADVANCE`, `.REJECT` sub-actions |
| recruitment.interview.manage | `HRM.RECRUITMENT.INTERVIEW.MANAGE` | + `.SCHEDULE`, `.RECORD_OUTCOME` |
| recruitment.offer.create | `HRM.RECRUITMENT.OFFER.MANAGE` / `.EXTEND` | separation of duties enforced |
| recruitment.offer.approve | `HRM.RECRUITMENT.OFFER.APPROVE` | distinct holder from extender |
| recruitment.hire.convert | `HRM.RECRUITMENT.HIRE.CONVERT` | the conversion command capability |
| onboarding.plan.manage | `HRM.ONBOARDING.PLAN.MANAGE` | create/cancel plans |
| onboarding.task.manage | `HRM.ONBOARDING.TASK.COMPLETE` / `.WAIVE` | COMPLETE ≤ WAIVE privilege |

Rules: capabilities are consumed **only** through the existing scoped
authorization machinery (`HrScopedAuthorizationScopeMatrix` test pattern); no
new role engine, no role hierarchy, no permission caching outside the G0
policy; grants/matrix seeds are tenant-configuration concerns using the G0
binding tables. Candidate self-service surfaces (application submission,
withdrawal, offer acceptance) authenticate the candidate through the existing
IAM identity model and are capability-gated on their own scoped actions —
they never reuse operator capabilities. Separation of duties is enforced at
the service layer (submitter≠approver; extender≠approver) and covered by
dedicated tests (G1-T7/T8).

## 10. PII / privacy design

**No legal compliance claims are made anywhere in G1.** Country-pack statutory
behavior remains governed by the G0 resolver with the DRAFT/BLOCKED states;
`LEGAL_REVIEW = PENDING_HUMAN` is untouched by this design.

### 10.1 PII_DATA_CLASSIFICATION_MATRIX

| Data class | Storage | Access | Masking | Logging prohibition | Audit | Retention | Deletion/anonymization | Encryption |
|---|---|---|---|---|---|---|---|---|
| Candidate full name | `hr_candidates.display_name` (plaintext, minimized) | `HRM.RECRUITMENT.CANDIDATE.VIEW` | masked in list views for non-owners | never in logs | read-audited at candidate-detail level | business archive by state | archive-only (no purge in G1) | transport+at-rest per platform defaults |
| Candidate email / phone | normalized hash for dedup + ciphertext column, same pattern as G0 contact fields | `HRM.RECRUITMENT.CANDIDATE.MANAGE` (view: masked; manage: full) | masked by default | hash only in logs, never raw | sensitive-read audit | archive-only | archive-only | platform column encryption where G0 uses it for contact PII |
| Candidate address | **collected only at conversion** via G0 private-identity path (if required by contract data) | G0 private-identity access rules | n/a pre-conversion (not stored) | never in logs | G0 private-identity audit | G0 rules | G0 rules | G0 `hr_person_private` encryption + blind index |
| Resume / CV + attachments | attachment store reference only (tenant-scoped object storage path), metadata in `hr_candidates` | `HRM.RECRUITMENT.CANDIDATE.MANAGE` + explicit download authorization | filename-only in UI lists | URLs/tokens never in logs | download events audited | archive-only | archive-only | storage-side per platform defaults |
| Identification fields (national ID etc.) | **NOT collected pre-conversion.** At conversion only, via `hr_person_private` (encryption + blind index keys as in G0 `V20260905_14` pattern) | G0 private-identity rules only | n/a | never in logs | G0 audit | G0 rules | G0 rules | G0 key management |
| Interview notes / scorecards | `hr_interview_feedback.scorecard` JSONB (schema-validated) | `HRM.RECRUITMENT.INTERVIEW.MANAGE` + participant | visible to panel + `HRM.RECRUITMENT.APPLICATION.MANAGE` | never in logs | write-audited, edit-versioned | archive-only | archive-only | platform defaults |
| Compensation expectations (candidate) | `hr_candidates` optional field, ciphertext if G0 pattern provides for sensitive contact-adjacent data | `HRM.RECRUITMENT.CANDIDATE.MANAGE` | masked in lists | never in logs | sensitive-read audit | archive-only | archive-only | per platform pattern |
| Offer details / compensation draft | `hr_offer_versions` | `HRM.RECRUITMENT.OFFER.MANAGE/.APPROVE` + candidate self-view of own offer | masked for non-owners | never in logs | sensitive-read audit (HRM.COMPENSATION.VIEW semantics) | archive-only | archive-only | platform defaults |
| Audit/event payloads | `hr_audit_ledger` / `hr_domain_event_outbox` | G0 rules (`HRM.AUDIT.VIEW`) | payloads carry **no raw PII** (§12 envelope: references + hashes only) | — | is the audit | G0 rules | G0 rules | G0 rules |

### 10.2 Hard PII rules

1. Candidate contact PII is minimized before hire; national-ID-grade private
   identity is collected **only** inside hire conversion through the G0
   private-identity path. Any earlier collection is a design violation.
2. Raw PII never enters logs, audit payloads, outbox payloads, or error
   responses. Identifiers in payloads are references or blind-index hashes.
3. Error messages never echo PII field values (§15 error model).
4. Cross-tenant access to candidate PII is impossible (RLS §8) and additionally
   denied at the service layer.
5. Deletion/anonymization in G1 = state-based archive; any true purge/anonymity
   regime is a future, separately-authorized retention contract (§19 Q5).

---

## 11. Workflow integration design (Workflow Engine remains the authority)

G1 consumes the central Workflow Orchestration Platform (Y2:
`WorkflowDefinitionService`, `WorkflowDefinitionValidator`,
`WorkflowWorkItemService`) as the **only** approvals/process authority. G1 owns
its business aggregates; Y2 owns process state. All crossings use §12 events +
the transactional outbox — no direct cross-module DB writes (module boundary
test pattern extended to G1). No new workflow engine, no parallel approval
state.

### 11.1 Job Opening Approval (optional per tenant policy; default ON)

```text
trigger            : opening submitted DRAFT→PENDING_APPROVAL
inputs             : opening ref, job snapshot title, org unit, headcount,
                     submitter, tenant (all references; no PII)
actor              : submitter = HRM.RECRUITMENT.OPENING.MANAGE holder
approvers          : per tenant workflow template (publish capability holders)
state transition   : Y2 work item APPROVED  ⇒ opening OPEN (capability
                     HRM.RECRUITMENT.OPENING.PUBLISH checked at apply-time)
                     REJECTED ⇒ opening DRAFT (reason) ; CANCELLED ⇒ opening CANCELLED
timeout            : template-defined (seed default 5 business days) ⇒ ESCALATE
                     work item per Y2 timeout semantics (no auto-approval)
retry              : Y2-native task retries; G1 re-apply is idempotent by state check
rejection/cancel   : reason mandatory; rejection does not delete history
audit              : Y2 audit + opening transition audit (same event timestamp)
outbox events      : OPENING_PUBLISHED on success; OPENING_TRANSITION events per §12
idempotency        : one work item per (opening, submitted state period);
                     duplicate submissions return the existing work item ref
```

### 11.2 Offer Approval (optional per tenant policy; default ON)

```text
trigger            : offer extend command issued (before EXTENDED committed)
inputs             : offer ref + version, application ref, candidate display
                     name, compensation draft **as masked summary** (amount
                     bands only in the work item; full values behind capability)
actor              : HRM.RECRUITMENT.OFFER.EXTEND holder
approvers          : per tenant template; separation of duties extender≠approver
state transition   : APPROVED ⇒ offer EXTENDED (expires_at set);
                     REJECTED ⇒ offer DRAFT (reason); CANCELLED (withdraw) ⇒ work item cancelled
timeout            : template-defined; expiry of the OFFER is independent (§6.3)
retry              : extend retry with same request-id returns same work item/outcome
rejection/cancel   : rejection creates a new offer version on next edit; history immutable
audit              : Y2 audit + offer audit; approval views of compensation are
                     sensitive-read audited
outbox events      : OFFER_EXTENDED / OFFER_WITHDRAWN
idempotency        : one open work item per (offer_id, version); unique index at G1 side
```

### 11.3 Hire Approval (optional per tenant policy; default OFF)

```text
trigger            : conversion command issued while policy enabled
inputs             : offer ref, candidate ref, position intent, employment
                     terms summary (references; PII per §10.2 rule 2)
actor              : HRM.RECRUITMENT.HIRE.CONVERT holder
approvers          : per tenant template (HR operator + approver separation)
state transition   : APPROVED ⇒ conversion command proceeds (§7 transaction);
                     REJECTED ⇒ conversion refused fail-closed (offer stays ACCEPTED;
                     human decides next step); CANCELLED ⇒ conversion refused
timeout            : template-defined; no auto-run on timeout
retry              : approval grant cached by (offer, approval-instance); the
                     conversion itself stays ledger-idempotent (§7.3 case 3/4)
rejection/cancel   : refusal audited; no partial state (conversion never started)
audit + outbox     : Y2 audit; HIRE_COMPLETED only on real conversion success
idempotency        : one approval instance per (offer_id, conversion-attempt window)
```

### 11.4 Onboarding Task Workflow (opt-in per plan template)

```text
trigger            : onboarding plan created with `workflow_linked=true` template
inputs             : plan ref, task refs, assignee, due dates (no PII beyond role)
actor/approvers    : assignee completes; reviewers per template when required
state transition   : Y2 task DONE ⇒ G1 task DONE (event-sourced apply, capability
                     re-checked at apply time); CANCELLED ⇒ task waived-with-reason
                     recorded; G1-side completion also possible without Y2 when
                     template not linked (single source of truth per task =
                     the state machine §6.4, Y2 is only a mirror when linked)
timeout            : due-date reminders via Y2; overdue never auto-completes
retry              : apply-events idempotent by (task_id, transition_seq)
rejection/cancel   : plan CANCELLED cascades Y2 item cancellation (compensating)
audit/outbox       : TASK_COMPLETED events both sides reconciled by idempotency
idempotency        : (task_id, transition_seq) uniqueness; executor for API commands
```

## 12. Event / Outbox contracts (design only — no publishers/consumers implemented)

All events follow the G0 outbox envelope (`hr_domain_event_outbox`,
`event_type VARCHAR(150)`) and the existing typed/versioned/tenant-stamped
payload conventions. Envelope fields fixed for every G1 event:

```text
aggregate       : <table singular> (e.g., HrOffer)
event_version   : v1 (initial; additive evolution only, versioned in payload)
tenant          : tenant_id (UUID, NOT NULL) — also implicit in RLS-scoped row
entity_id       : aggregate root UUID
occurred_at     : transaction timestamp (UTC), same for audit row
correlation_id  : inbound request-id / command id (HrmIdempotentCommandExecutor)
causation_id    : triggering event id or workflow work-item id (nullable)
idempotency     : consumer contract — (event_type, entity_id, occurred_at,
                  aggregate_version) tuple dedup; producers guarantee exactly-once
                  *emission* via outbox + at-least-once delivery
payload class   : REFS_ONLY — raw PII forbidden (§10.2 rule 2); payloads carry
                  UUIDs, codes, masked summaries, blind-index hashes only
PII restriction : any payload field class from §10.1 marked "never in logs"
                  is banned from payloads entirely
```

| Event (event_type) | Aggregate | Emitted when | Payload (refs only) | Notes |
|---|---|---|---|---|
| HRM.RECRUITMENT.OPENING_PUBLISHED | HrJobOpening | OPEN committed | opening_id, job_id, org_unit_id, headcount | compliance decision ref included |
| HRM.RECRUITMENT.OPENING_CLOSED / OPENING_CANCELLED | HrJobOpening | CLOSED/CANCELLED | opening_id, reason_code | |
| HRM.RECRUITMENT.CANDIDATE_CREATED | HrCandidate | candidate created | candidate_id | no contact PII |
| HRM.RECRUITMENT.APPLICATION_SUBMITTED | HrApplication | APPLIED committed | application_id, opening_id, candidate_id | |
| HRM.RECRUITMENT.APPLICATION_ADVANCED / _REJECTED / _WITHDRAWN | HrApplication | §6.2 transitions | application_id, from_stage, to_stage, reason_code | reason CODE, not free text |
| HRM.RECRUITMENT.INTERVIEW_SCHEDULED / _COMPLETED | HrInterview | §6.2 interview ops | interview_id, application_id, outcome_code | no notes payload |
| HRM.RECRUITMENT.OFFER_EXTENDED / _ACCEPTED / _DECLINED / _WITHDRAWN / _EXPIRED | HrOffer | §6.3 transitions | offer_id, offer_version, application_id | no compensation values |
| HRM.RECRUITMENT.HIRE_COMPLETED | HrHireConversion | §7 COMMIT | conversion_id, offer_id, application_id, person_id, employment_id, assignment_id, contract_id, compensation_package_id, onboarding_plan_id | THE contract event other modules consume; direct recruitment-table reads by other modules remain forbidden |
| HRM.ONBOARDING.PLAN_CREATED / _COMPLETED / _CANCELLED | HrOnboardingPlan | §6.4 | plan_id, employment_id | |
| HRM.ONBOARDING.TASK_COMPLETED / _WAIVED | HrOnboardingTask | §6.4 | task_id, plan_id, actor_ref | waiver_reason_code only |

Delivery semantics: outbox worker + consumers per G0 infrastructure
(`HrOutboxWorker`, `HrOutboxEventConsumer`); consumers must be idempotent on
the envelope key; ordering per aggregate by occurred_at/sequence; cross-module
consumers (IAM provisioning, notification) subscribe by contract, never by
table access.

## 13. API design (v2 contracts — no controller code)

All G1 endpoints live in the canonical HR v2 API family (`hr/api/v2/`) with the
G0 error contract (`HrApiErrorCode`/`HrApiErrorResponse`/`HrApiErrorHandler`),
`If-Match`/`If-None-Match` optimistic concurrency where G0 uses it, OpenAPI
annotations enforced by the contract test pattern (`HrOpenApiContractTest`
extended to fail on undocumented routes/fields). Pagination: cursor-based
(`limit` + `next_cursor`), deterministic ordering; filters/sorting per domain
below. Every mutating endpoint: capability + tenant scope + idempotency
(request-id) + audit + emitted events per §12. Errors are deterministic
(stable codes, §15).

| # | Method + path | Purpose / request → response | Pagination / filters / sort | RBAC | Idempotency | Emitted events |
|---|---|---|---|---|---|---|
| 1 | POST `/hr/api/v2/recruitment/openings` | create opening (job ref, org unit, position?, headcount, window) → Opening | — | OPENING.MANAGE | request-id | OPENING_CREATED (audit) |
| 2 | GET `/hr/api/v2/recruitment/openings` | list openings → page | cursor; filters: state, org_unit, job, window; sort: opening_number, created_at | OPENING.VIEW | n/a | — |
| 3 | GET `/hr/api/v2/recruitment/openings/{id}` | detail + state periods → Opening | — | OPENING.VIEW | n/a | — |
| 4 | POST `.../openings/{id}/submit` | DRAFT→PENDING_APPROVAL → Opening | — | OPENING.MANAGE | request-id | (workflow §11.1) |
| 5 | POST `.../openings/{id}/approve` · `/reject` · `/pause` · `/resume` · `/close` · `/cancel` | state ops (§6.1) → Opening | — | PUBLISH/MANAGE | request-id | OPENING_* |
| 6 | POST `/hr/api/v2/recruitment/candidates` | create candidate (minimized contact) → Candidate | — | CANDIDATE.MANAGE | request-id | CANDIDATE_CREATED |
| 7 | GET `/hr/api/v2/recruitment/candidates` · `/{id}` | directory/detail → page | cursor; filters: pool_state, contact hash; sort: candidate_number | CANDIDATE.VIEW (masked) / MANAGE (full) | n/a | — |
| 8 | POST `.../candidates/{id}/archive` | pool ARCHIVED → Candidate | — | CANDIDATE.MANAGE | request-id | (audit) |
| 9 | POST `/hr/api/v2/recruitment/applications` | apply (candidate, opening) → Application | — | CANDIDATE.MANAGE (self) / APPLICATION.MANAGE (operator) | request-id + (candidate,opening) active-unique | APPLICATION_SUBMITTED |
| 10 | GET `.../applications` · `/{id}` | list/detail + stage history → page | cursor; filters: opening, stage, candidate; sort: applied_at | APPLICATION.MANAGE (candidate sees own) | n/a | — |
| 11 | POST `.../applications/{id}/advance` · `/reject` · `/withdraw` | §6.2 ops (reason code mandatory for reject/withdraw) → Application | — | ADVANCE / REJECT / MANAGE | request-id | APPLICATION_* |
| 12 | GET `.../applications/{id}/pipeline` | stage periods → history | — | APPLICATION.MANAGE | n/a | — |
| 13 | POST `.../applications/{id}/interviews` | schedule (planned_at, mode, panel) → Interview | — | INTERVIEW.SCHEDULE | request-id | INTERVIEW_SCHEDULED |
| 14 | GET `.../interviews/{id}` | detail + participants → Interview | — | INTERVIEW.MANAGE | n/a | — |
| 15 | POST `.../interviews/{id}/outcome` | DONE(PASSED\|FAILED)/CANCELLED/NO_SHOW → Interview | — | INTERVIEW.RECORD_OUTCOME | request-id | INTERVIEW_COMPLETED |
| 16 | PUT/GET `.../interviews/{id}/feedback` | participant scorecard (JSONB schema-validated) → Feedback | — | participant or INTERVIEW.MANAGE | request-id (versioned) | (audit) |
| 17 | POST `.../applications/{id}/offers` | create offer (version payload) → Offer | — | OFFER.MANAGE | request-id | (audit) |
| 18 | POST `.../offers/{id}/extend` | approval-gated extension (§11.2) → Offer | — | OFFER.EXTEND | request-id | OFFER_EXTENDED |
| 19 | POST `.../offers/{id}/accept` · `/decline` | candidate self-service (own application only) → Offer | — | candidate scope | request-id | OFFER_ACCEPTED/DECLINED |
| 20 | POST `.../offers/{id}/withdraw` | operator withdrawal pre-acceptance → Offer | — | OFFER.MANAGE + reason | request-id | OFFER_WITHDRAWN |
| 21 | **POST `/hr/api/v2/recruitment/offers/{id}/hire-conversion`** | **explicit idempotent command** (§7) body: identity claims refs, contract effective date → ConversionResult{person_id?, employment_id, assignment_id, contract_id, compensation_package_id, onboarding_plan_id, replayed:boolean} | — | HIRE.CONVERT (+ Hire Approval §11.3 when policy on) | **ledger (tenant_id, offer_id) + request-id** | HIRE_COMPLETED |
| 22 | GET `/hr/api/v2/onboarding/plans` · `/{id}` | list/detail + tasks → page | cursor; filters: employment, state, due; sort: plan_number | PLAN.MANAGE | n/a | — |
| 23 | POST `/hr/api/v2/onboarding/plans` | standalone plan from template → Plan | — | PLAN.MANAGE | request-id | PLAN_CREATED |
| 24 | POST `.../plans/{id}/tasks/{taskId}/complete` · `/waive` | §6.4 ops (waive reason mandatory) → Task | — | TASK.COMPLETE / TASK.WAIVE | request-id | TASK_* |
| 25 | POST `.../plans/{id}/cancel` | plan cancel + cascade → Plan | — | PLAN.MANAGE + reason | request-id | PLAN_CANCELLED |

OpenAPI: every route/field above must appear in the contract test; hire
conversion documents its idempotent semantics explicitly (replay → 200 with
original result). Search: candidate/opening text search uses tenant-scoped
indexed fields only (no cross-tenant index).

## 14. UI / UX functional design (functional screens only — no React code)

All screens live under `apps/web/app/hr/...` following the existing HR
workspace patterns (`hr-workspace.tsx`, `hr-command-dialog.tsx`,
`hr-feedback.tsx`, `hr-labels.ts` + I18nProvider; governance via
`scripts/ci/check_i18n_keys.py`). Every screen: **Arabic + English** via i18n
keys (no hard-coded strings), **RTL** via logical CSS properties (no
left/right physical properties), **accessibility** per G0 standard (semantic
landmarks, focus management in dialogs, contrast, full keyboard paths,
visible focus), **loading** (skeletons), **empty states** (actionable CTA),
**error states** (deterministic API error mapping, no raw messages),
**permissions** (no capability → no control rendered, not merely disabled),
**responsive** (mobile ≥360px, tablet, desktop grids).

| # | Screen | Functional content | Key states/behaviors |
|---|---|---|---|
| 1 | Recruitment Dashboard | KPI tiles (openings by state, pipeline funnel, tasks due), recent activity | permission-scoped tiles; RTL-aware card grid |
| 2 | Job Openings List | table w/ state badges, filters, cursor pagination | empty CTA "create opening"; keyboard row actions |
| 3 | Job Opening Detail | state timeline, headcount, compliance decision badge, actions per §6.1 | approval-in-progress banner; disabled-by-permission vs absent |
| 4 | Candidate Directory | masked-contact table, pool-state filters, duplicate warnings | masking per §10; a11y for masked values |
| 5 | Candidate Profile | contact (permissioned), applications history, archive action | duplicate-warning panel; no PII in page title/meta |
| 6 | Applications Board (Pipeline) | per-opening kanban by stage, drag-or-command moves | command-first (keyboard + menu) with drag enhancement; concurrency 409 toast |
| 7 | Application Detail | stage history timeline, interviews, offers, actions per §6.2 | reject/withdraw reason dialogs (mandatory reason) |
| 8 | Interview Scheduling | planner (participants, mode, planned_at), conflict-free pick | timezone explicit; RTL calendar |
| 9 | Interview Feedback | per-participant scorecard form (schema-validated JSONB) | autosave draft vs committed version; participant-only visibility |
| 10 | Offer Editor | versioned payload editor, compensation draft, expiry | separation-of-duties UI lock for extender; version diff view |
| 11 | Offer Approval | Y2 work-item view, masked compensation summary, approve/reject | reason mandatory on reject; sensitive-read noted |
| 12 | Hire Conversion | pre-flight checklist (identity resolution preview, position occupancy, approval state) + explicit confirm command | idempotent replay banner when already converted; failure-matrix errors per §7.3 |
| 13 | Onboarding Dashboard | plans by state, tasks due/overdue, assignee filter | RTL task lists; overdue emphasis non-color-coded |
| 14 | Onboarding Plan Detail | checklists/tasks per §6.4, waive-with-reason | completion derived progress (no fake %), cancel cascade confirm |
| 15 | Onboarding Tasks (my tasks) | assignee worklist, complete action | keyboard-first completion; audit-visible history |

## 15. Error model and concurrency strategy

**Error model:** all G1 endpoints reuse `HrApiErrorCode` + `HrApiErrorResponse`
+ the G0 handler — deterministic, stable, i18n-mappable codes; no stack traces,
no PII echo (§10.2). New codes introduced (exhaustive):
`OPENING_APPROVAL_REQUIRED`, `OPENING_STATE_CONFLICT`,
`APPLICATION_STAGE_CONFLICT`, `APPLICATION_DUP_ACTIVE`, `OFFER_VERSION_CONFLICT`,
`OFFER_STATE_CONFLICT`, `OFFER_EXPIRED`, `OFFER_APPROVAL_REQUIRED`,
`CONVERSION_IDENTITY_AMBIGUOUS`, `CONVERSION_ONBOARDING_TEMPLATE_INVALID`,
`CONVERSION_POSITION_OVER_OCCUPANCY`, `TENANT_CONTEXT_MISMATCH`,
`IDEMPOTENCY_REPLAY` (documented 200-replay instead where §7 mandates),
`WAIVER_REASON_REQUIRED`. Mapping tests cover every code (G1-T10).

**Concurrency strategy:** optimistic locking on all aggregates (version
columns; `If-Match` at API; 409 state-conflict family on loss); row-locked
state validation inside every state transition (read current state FOR UPDATE
in-transaction); the single true serialization point is the conversion ledger
unique key + in-transaction occupancy re-check (§7); stage-period and
opening-period history rows are append-only under the same transaction (no
lost-history window); scheduled expiry is idempotent and state-checked;
Y2-linked transitions apply events idempotently by `(entity, transition_seq)`.

## 16. Test strategy (design-level matrix — PostgreSQL Direct only)

Environment: **host-native PostgreSQL Direct only** (G0 JOB C pattern; Docker /
Testcontainers / service containers forbidden). Definition of done for the G1
stage: `FAILURES = 0, ERRORS = 0, UNEXPLAINED_SKIPS = 0` on the exact verified
SHA, evidence file + certificate per G0 discipline.

| Category | Coverage (G1) |
|---|---|
| UNIT | value objects, state transition guards, reason-code validation |
| DOMAIN | aggregate invariants: single active application, one-acceptance, expiry math, completion derivation |
| INTEGRATION | repositories + services against host-native PG |
| POSTGRESQL DIRECT | every repository/service integration test; Flyway applies clean; no container |
| RLS | per-table §8.1 matrix probes (42501 cross-tenant denial, no-context empty set, FORCE verification) |
| RBAC | `HrScopedAuthorizationScopeMatrix` pattern extended: full capability × endpoint matrix incl. separation of duties |
| TENANT ISOLATION | cross-tenant read/write denial on every G1 path; conversion tenant-mismatch pre-condition |
| API | request/response contracts, status codes, pagination determinism |
| OPENAPI | undocumented route/field ⇒ fail (G0 contract test pattern) |
| IDEMPOTENCY | replay on every mutating endpoint; executor semantics; conversion cases §7.3-3/4 |
| AUDIT | every transition writes audit in-transaction; audit-failure rolls back (fail-closed) |
| OUTBOX | outbox row + state change atomic; envelope schema; no-PII payload assertion |
| WORKFLOW | Y2 link: approve/reject/cancel/timeout paths per §11; orphan work-item cancellation on entity cancel |
| CONCURRENCY | optimistic-lock loss; concurrent advance/reject; concurrent accept/decline; §7.3-8 double-conversion race |
| RETRY | client retry storms with same/different request-ids; scheduled expiry re-runs |
| FAILURE INJECTION | §7.3 cases 5/6 (mid-transaction crash, onboarding template invalid); audit/outbox write failure rollback |
| ARABIC/RTL | label keys exist (check_i18n_keys.py), RTL layout logical properties in UI tests |
| ACCESSIBILITY | keyboard paths, focus traps, contrast — G0 UI test standard |

**Candidate→Hire mandatory suite (`HrHireConversionIntegrationTest`):**
duplicate retry (same key) → original result; concurrent retry (different keys,
§7.3-8) → zero duplicates; transaction rollback (failure injection at each
output step §7.1.4–9 → no partial state); tenant mismatch → fail-closed
pre-condition; existing-Person reuse (§7.3-1 link, no second Person); duplicate
employment prevention (second conversion for the same offer structurally
impossible); audit exactness (row-per-write, exact actor/action); outbox
exactness (HIRE_COMPLETED exactly once, refs present, no PII).

## 17. Rollout and cutover

G1 is additive: no existing table requires data migration for existing tenants;
new state starts empty. A tenant enables recruitment by creating/publishing its
first opening (capability-gated). Rollback compatibility: previous application
binary remains functional (G1 tables ignored by G0 code paths — expand-safe by
construction). No Flyway down-scripts; no schema-history edits (history-append
only, terminal state `20260906.1` at baseline continues forward). Seed data:
one generic onboarding checklist template, optional workflow definition seeds
(§11) — versioned, tenant-scoped, feature-flagged where G0 does.

## 18. Adversarial design review (Phase-19 gate — 20 questions)

| # | Question | Resolution |
|---|---|---|
| 1 | Did we create a second Person authority? | No — §2 Person row: candidates are recruitment-scoped; Person writes only via G0 services at conversion; enforced by module boundary test (G1-T2/T8) |
| 2 | Does Candidate secretly become an employee model? | No — no candidate-employee/future-employee tables; the ONLY path from recruitment to `hr_people`/`hr_employees` is §7 |
| 3 | Is hire conversion actually atomic? | Yes — single DB transaction, all outputs + ledger + audit + outbox in it; no async/step-outside; failure injection tests per output step |
| 4 | Can retry create a duplicate? | No — ledger UK(tenant_id, offer_id) + executor request-id; §7.3-3/4 normative; race case §7.3-8 serialized by unique constraint |
| 5 | Is cross-tenant access possible? | No — RLS FORCE + fail-closed on every table (§8.1); service-layer tenant assertions; tenant-mismatch pre-condition in conversion; RLS probe tests |
| 6 | Can Offer approval be bypassed? | No — EXTENDED transition checks approval-instance state in-transaction when policy on; approval cannot be skipped (§6.3); separation of duties tested |
| 7 | Does IAM activation happen implicitly? | No — §7.2: no IAM calls in the boundary; IAM consumes HIRE_COMPLETED by its own contract; reconciliation query is ops-side only |
| 8 | Can PII appear in logs? | Prohibited by §10.2; payloads REFS_ONLY (§12); error model never echoes PII (§15); payload assertion tests |
| 9 | Can the Workflow be bypassed from the API? | No — state transitions validate Y2 approval instance in-transaction (§6.1/6.3); no API path writes OPEN/EXTENDED without it when policy on |
| 10 | Are all application state transitions governed? | Yes — §6.2 allowed/forbidden matrix, capability + actor + reason per transition, guarded in-transaction |
| 11 | Can events duplicate? | At-least-once delivery with idempotent consumers on envelope key (§12); producers exactly-once *emit* via outbox; consumers dedup — documented contract |
| 12 | Is idempotency defined for every sensitive command? | Yes — all mutations via `HrmIdempotentCommandExecutor`; conversion double-keyed (ledger + executor) |
| 13 | Is RLS fail-closed? | Yes — `current_setting('app.tenant_id', true)` NULL ⇒ empty/denied; FORCE RLS everywhere (§8); role contract asserted in CI |
| 14 | Does any test need Docker? | No — host-native PostgreSQL Direct only (§16); directive non-negotiable restated in plan tasks |
| 15 | Does schema design assume real PostgreSQL? | Yes — JSONB scorecards, partial unique indexes, RLS, ledger constraints; PostgreSQL-Direct tests prove it |
| 16 | Does onboarding create a new Employment? | No — onboarding references the employment created by conversion; standalone plans require an existing employment FK (§5.2) |
| 17 | Are Person reuse rules clear? | Yes — blind-index unambiguous match links; email-only ambiguity fails closed for human review (§7.3-1/2) |
| 18 | What happens on concurrent hire conversion? | Unique-key serialization; loser returns winner's result (§7.3-8); zero-duplicate test mandatory |
| 19 | Are API errors deterministic? | Yes — stable code list (§15) mapped through the G0 error contract; mapping tests in G1-T10 |
| 20 | Are rollback semantics clear? | Yes — single-transaction rollback in conversion (§7.3-5); stage-level rollback = forward-only state fixes (§6); schema rollback = expand-safe, no down-scripts (§17) |

**Adversarial review verdict: no unresolved critical issue.** Open (non-blocking)
questions are tracked in §19.

## 19. Open questions for G1 design review (non-blocking)

1. Offer approval default policy per tenant — proposal: ON by default with a
   seeded workflow template (changed from the earlier draft's "off by default"
   after separation-of-duties review).
2. Candidate duplicate-detection UX — warn + audit (never silent merge);
   blocking remains a future policy decision.
3. Onboarding checklist template library at G1 — seed exactly one generic
   tenant-editable template.
4. Interview calendar integration — out of scope; manual date/time only.
5. Candidate data retention/anonymization regime — future separate retention
   contract; G1 ships archive-by-state only.
6. Screening approval workflow (Screening stage governance) — default: none at
   G1; capability-gated stage movement is deemed sufficient.
