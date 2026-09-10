# HRM-G1 Recruitment & Onboarding — Implementation Plan

```text
REPOSITORY                           = snadaiapp-png/SNAD
G1_BASE_SHA                          = 277dd5efb31e9a9e6a738543a6e6b92bba016486
G0_ENGINEERING_CLOSURE               = PASS
PMV_FINAL_GATE                       = PASS   (PMV #1014 at G1_BASE_SHA)
G1_DESIGN_AUTHORIZATION              = YES
G1_IMPLEMENTATION_PLAN_AUTHORIZATION = YES
G1_IMPLEMENTATION_AUTHORIZATION      = NO
PRODUCTION_AUTHORIZATION             = NO
LEGAL_REVIEW                         = PENDING_HUMAN
```

> **Status:** PLAN ONLY. No task below has started. Execution of T1–T12 requires
> a separate, explicit implementation authorization directive. Companion design:
> `docs/superpowers/specs/2026-09-07-hrm-g1-recruitment-onboarding-design.md`
> (§ references below point there).

## 0. Environment contract (binding for every task — unchanged from G0)

```text
POSTGRESQL_ONLY        = TRUE     POSTGRESQL_MODE = HOST_NATIVE_DIRECT
DOCKER_USED_ON_GOVERNED_PG_PATH  = NO
TESTCONTAINERS_USED_ON_GOVERNED_PG_PATH = NO
CONTAINERIZED_PG_ON_GOVERNED_PATH = FORBIDDEN
SERVICE_CONTAINERS_ON_GOVERNED_PATH = FORBIDDEN
SCOPE_NOTE             = the container rules above are path-scoped to the governed
                         PostgreSQL acceptance path used by G1 tasks/tests;
                         they are not a global prohibition outside that path
PRODUCTION_MUTATION    = FORBIDDEN   PRODUCTION_DATABASE = DO_NOT_TOUCH
FLYWAY                 = append-only migrations, NO repair, NO history edit
CI_ROLE_CONTRACT       = NOSUPERUSER NOBYPASSRLS (asserted, extended to G1 tables)
DIRECT_PUSH_TO_MAIN    = FORBIDDEN   BRANCH_PROTECTION_BYPASS = FORBIDDEN
AUDIT                  = fail-closed (write-without-audit ⇒ rollback)
IDEMPOTENCY            = all mutations via HrmIdempotentCommandExecutor
TEST_BASELINE_RULE     = FAILURES=0, ERRORS=0, UNEXPLAINED_SKIPS=0 on exact SHA
EVIDENCE               = G0 certificate discipline; stage gates below
```

Task ordering follows the directive: T1 → T2 → (T3 ∥ T4) → T5 → T6 → T7 → T8 →
T9 → T10 → T11 → T12, with the parallelization analysis in §2 (dependency
graph). Every task is TDD-first: the listed tests are written and RED before
the implementation that turns them GREEN.

## 1. Task cards T1–T12

### G1-T1 — Domain contracts

```text
OBJECTIVE          : Establish the G1 module skeleton + domain contracts (value
                     objects, state enums, transition guard specs, reason-code
                     registries, capability constants HRM.RECRUITMENT.*/HRM.ONBOARDING.*)
                     per design §5–§6, §9 — zero persistence.
DEPENDENCIES       : none (G1_BASE_SHA)
FILES_EXPECTED     : apps/sanad-platform/src/main/java/com/sanad/platform/hr/
                       recruitment/domain/** (aggregates, value objects, guards)
                       recruitment/RecruitmentCapabilities.java
                       onboarding/domain/**
                     NO SQL, NO controllers, NO web files
TESTS_FIRST        : unit tests for every transition guard matrix (§6.1–6.4
                     allowed/forbidden); reason-code validation; capability
                     constant naming contract test (regex HRM.<DOMAIN>.<ACTION>)
RED                : guards reject forbidden transitions; unknown reason codes fail
GREEN              : guards accept exactly the §6 allowed transitions; capability
                     constants compile against naming contract
REFACTOR           : extract shared state-machine test harness for reuse in T3–T9
ACCEPTANCE_CRITERIA: full §6 matrices enforced at domain level; naming contract
                     test green; zero production table writes
SECURITY_GATES     : capability naming contract; no auth bypass surface created
TENANT_GATES       : all value objects carry tenant_id in identity tuples
ROLLBACK_CONSIDERATIONS: pure addition; revert = delete module tree
DONE_DEFINITION    : unit tests green; boundary architecture test extended to
                     recruitment/onboarding packages; REVIEW: no files outside
                     declared tree
```

### G1-T2 — Database schema + RLS

```text
OBJECTIVE          : Flyway migration set creating all §5.2 tables with keys/
                     constraints + ENABLE+FORCE RLS + tenant-isolation policies
                     (§8.1) in the G0 policy shape; seed one generic onboarding
                     checklist template.
DEPENDENCIES       : G1-T1 (enums/codes referenced by check constraints)
FILES_EXPECTED     : apps/sanad-platform/src/main/resources/db/migration/
                       V2026090X_N__hr_g1_recruitment_onboarding_schema.sql
                       V2026090X_N+1__hr_g1_rls_policies.sql
                       V2026090X_N+2__hr_g1_onboarding_template_seed.sql
                     (append-only; no edits to prior migrations)
TESTS_FIRST        : CrmPostgresMigrationTest-pattern → HrG1MigrationTest (RED:
                     tables/policies absent); HrG1RlsFailClosedIntegrationTest
                     (RED: cross-tenant returns rows / no-context leaks)
GREEN              : migrations apply clean on host-native PG; §8.1 matrix
                     probes pass (cross-tenant read empty, write 42501-class,
                     no-context fail-closed, FORCE verified via pg_catalog)
REFACTOR           : shared RLS probe harness generalized from G0 pattern
ACCEPTANCE_CRITERIA: every §5.2 table + policy present; ledger UK(tenant_id,
                     offer_id) + partial unique active-application index exist;
                     role-contract CI assertion extended and green
SECURITY_GATES     : NOSUPERUSER NOBYPASSRLS role contract asserted against G1
                     tables; no BYPASSRLS/role impersonation anywhere
TENANT_GATES       : every table tenant_id NOT NULL + policy; index usage proven
ROLLBACK_CONSIDERATIONS: expand-safe additive schema; no down-script (G0 rule);
                     revert strategy = forward migration only
DONE_DEFINITION    : migration + RLS + seed tests green on host-native PG;
                     Flyway history append-only verified
```

### G1-T3 — Job openings

```text
OBJECTIVE          : HrJobOpening aggregate services + repository + compliance-
                     gated publication (CountryPolicyResolver integration) +
                     state transitions §6.1 + headcount derivation field.
DEPENDENCIES       : G1-T2 (tables), G1-T1 (guards); BLOCKED_BY nothing else
FILES_EXPECTED     : recruitment/application/HrJobOpeningService.java (+commands)
                       recruitment/domain/HrJobOpening.java
                       recruitment/infrastructure/JdbcHrJobOpeningRepository.java
TESTS_FIRST        : HrJobOpeningServiceIntegrationTest (RED); opening RLS probes
                     scoped to service path; compliance fail-closed test (absent
                     pack ⇒ decision row, no publish)
GREEN              : full §6.1 matrix via service; compliance decision persisted;
                     headcount filled updates ONLY via conversion linkage (stub)
REFACTOR           : transition audit+outbox emission unified (shared with T5–T9)
ACCEPTANCE_CRITERIA: §6.1 transitions + audit + outbox rows in-transaction;
                     OPENING_APPROVAL_REQUIRED honored (workflow link lands in T7
                     — service hook present, default policy OFF for T3 scope)
SECURITY_GATES     : capability checks on every command (matrix test rows added)
TENANT_GATES       : cross-tenant service-level denial + RLS probe green
ROLLBACK_CONSIDERATIONS: additive; feature unused if capability ungranted
DONE_DEFINITION    : integration tests green on host-native PG; zero workflow
                     hard dependency at this stage
```

### G1-T4 — Candidate identity

```text
OBJECTIVE          : HrCandidate aggregate + minimized-contact storage (hash +
                     ciphertext per G0 pattern), duplicate detection (warning +
                     audit, never merge), archive state.
DEPENDENCIES       : G1-T2; parallel-safe with G1-T3
FILES_EXPECTED     : recruitment/domain/HrCandidate.java (+contact value object)
                       recruitment/application/HrCandidateService.java
                       recruitment/infrastructure/JdbcHrCandidateRepository.java
TESTS_FIRST        : HrCandidateServiceIntegrationTest (RED: contact hash/cipher
                     round-trip, duplicate warning emitted, no raw PII in logs)
GREEN              : create/update/archive per §5.1; duplicate-detection warning
                     + audit row; log-assertion test (no PII) green
REFACTOR           : blind-index helper reuse from G0 private-identity utilities
                     (read-only reuse — no Person writes at this task)
ACCEPTANCE_CRITERIA: §10 PII matrix columns for candidate classes enforced by
                     tests; candidate_number tenant-unique
SECURITY_GATES     : PII minimization assertions; masking contract for read DTOs
TENANT_GATES       : contact uniqueness tenant-scoped; RLS probes green
ROLLBACK_CONSIDERATIONS: additive; no canonical identity writes
DONE_DEFINITION    : integration + PII assertion tests green
```

### G1-T5 — Applications + recruitment pipeline

```text
OBJECTIVE          : HrApplication aggregate + stage-period history + pipeline
                     derivation + screening record + §6.2 transitions.
DEPENDENCIES       : G1-T3, G1-T4 (aggregates + tables); SEQUENTIAL after both
FILES_EXPECTED     : recruitment/domain/HrApplication.java (+stage period)
                       recruitment/application/HrApplicationService.java
                       recruitment/infrastructure/JdbcHrApplicationRepository.java
TESTS_FIRST        : HrApplicationServiceIntegrationTest (RED: single-active-
                     application invariant, forbidden backward transitions,
                     stage history append-only)
GREEN              : §6.2 matrix via service; reason codes mandatory on
                     reject/withdraw; pipeline history query deterministic
REFACTOR           : shared transition/audit/outbox harness hardened
ACCEPTANCE_CRITERIA: stage board projection derives from state only; HIRED
                     transition IMPOSSIBLE here (conversion-only, compile-level
                     guard + test proving no service path skips T8)
SECURITY_GATES     : APPLICATION.MANAGE/ADVANCE/REJECT matrix rows
TENANT_GATES       : (candidate, opening) uniqueness tenant-scoped; RLS probes
ROLLBACK_CONSIDERATIONS: additive; history rows immutable by constraint
DONE_DEFINITION    : integration tests green; concurrency tests (advance vs
                     reject race) green
```

### G1-T6 — Interviews

```text
OBJECTIVE          : HrInterview + participants + versioned feedback scorecards
                     (schema-validated JSONB) + §6.2 interview operations.
DEPENDENCIES       : G1-T5 (application aggregate)
FILES_EXPECTED     : recruitment/domain/HrInterview.java (+participant, feedback)
                       recruitment/application/HrInterviewService.java
                       recruitment/infrastructure/JdbcHrInterviewRepository.java
TESTS_FIRST        : HrInterviewServiceIntegrationTest (RED: outcome matrix,
                     one-feedback-per-participant versioning, panel tenant users)
GREEN              : §6.2 interview ops + feedback PUT semantics; scorecard JSONB
                     schema validated at write
REFACTOR           : JSONB schema validator extracted for reuse
ACCEPTANCE_CRITERIA: interview rows carry no notes in payloads (§12 REFS_ONLY
                     asserted); participant capability checks
SECURITY_GATES     : INTERVIEW.MANAGE/SCHEDULE/RECORD_OUTCOME matrix rows
TENANT_GATES       : participant user ids resolve within tenant; RLS probes
ROLLBACK_CONSIDERATIONS: additive; feedback versions immutable
DONE_DEFINITION    : integration tests green on host-native PG
```

### G1-T7 — Offers + approvals

```text
OBJECTIVE          : HrOffer + versions + §6.3 lifecycle + Workflow-gated
                     approval (Job-Opening approval link completion from T3's
                     hook + Offer Approval per §11.2) + expiry resolution.
DEPENDENCIES       : G1-T5 (application), Workflow Y2 services (existing)
FILES_EXPECTED     : recruitment/domain/HrOffer.java (+HrOfferVersion)
                       recruitment/application/HrOfferService.java
                       recruitment/application/HrOpeningApprovalLinkService.java
                       recruitment/infrastructure/JdbcHrOfferRepository.java
TESTS_FIRST        : HrOfferServiceIntegrationTest (RED: one-acceptance, expiry
                     fail-closed, version-on-edit-after-extend);
                     HrOfferApprovalWorkflowIntegrationTest (RED: EXTENDED only
                     after Y2 APPROVED; extender≠approver enforced)
GREEN              : §6.3 matrix; approval flows §11.1/§11.2 (approve/reject/
                     cancel/timeout-escalate); orphan work-item cancellation on
                     offer/opening cancel
REFACTOR           : shared workflow-link harness for T8 hire approval
ACCEPTANCE_CRITERIA: approval bypass impossible (in-transaction instance-state
                     check test); sensitive-read audit on approval compensation views
SECURITY_GATES     : OFFER.MANAGE/EXTEND/APPROVE separation of duties (matrix)
TENANT_GATES       : work items tenant-stamped; RLS probes
ROLLBACK_CONSIDERATIONS: approval policy is tenant config — default ON documented;
                     disabling post-hoc never retro-extends offers
DONE_DEFINITION    : workflow + integration tests green; no PII in work items
```

### G1-T8 — Candidate → Hire conversion

```text
OBJECTIVE          : §7 atomic/idempotent conversion command + ledger + Person/
                     Employment/Assignment writes + Contract/Compensation
                     writes ONLY WHEN REQUIRED by the G0 authority and tenant
                     policy, all THROUGH G0 services + onboarding plan
                     creation + Hire Approval link
                     (§11.3, policy default OFF) + HIRE_COMPLETED event.
DEPENDENCIES       : G1-T4 (candidate identity), G1-T7 (offer ACCEPTED), G0
                     services (existing); SEQUENTIAL (the critical boundary)
FILES_EXPECTED     : recruitment/application/HrHireConversionService.java
                       recruitment/application/HrHireApprovalLinkService.java
                       recruitment/domain/HrHireConversion.java
                       recruitment/infrastructure/JdbcHrHireConversionRepository.java
TESTS_FIRST        : HrHireConversionIntegrationTest — MANDATORY suite RED first:
                     §7.3 cases 1–9 (reuse link, ambiguity fail-closed, same-key
                     replay, different-key replay, mid-transaction rollback,
                     onboarding-template failure, IAM-unavailable success,
                     concurrent double-conversion race, tenant mismatch);
                     audit/outbox exactness; zero-duplicate employment
GREEN              : all nine scenarios green; ledger UK serializes race; single
                     transaction proven by failure injection at §7.1.4–9
REFACTOR           : conversion composition reviewed for step isolation w/o
                     breaking atomicity
ACCEPTANCE_CRITERIA: full §7 contract; occupancy re-check over-occupancy abort;
                     position occupancy derivation unchanged (G0-owned);
                     G0 module boundary test extended proves no direct canonical
                     SQL outside G0 services
SECURITY_GATES     : HIRE.CONVERT capability + optional Hire Approval;
                     separation of duties; NOSUPERUSER NOBYPASSRLS under load test
TENANT_GATES       : tenant-mismatch pre-condition; every key tenant-qualified
ROLLBACK_CONSIDERATIONS: single-transaction rollback (§7.3-5); ledger FAILED
                     state recovery = fresh conversion after remediation; no
                     compensation actions outside the transaction
DONE_DEFINITION    : the mandatory suite green on host-native PG = the T8 gate;
                     evidence file per G0 discipline
```

### G1-T9 — Onboarding domain

```text
OBJECTIVE          : HrOnboardingPlan/Checklist/Template/Task services + §6.4
                     transitions + derived completion + optional Y2 task link
                     (§11.4) + seeded template materialization.
DEPENDENCIES       : G1-T8 (conversion creates plans; standalone needs FK target)
FILES_EXPECTED     : onboarding/domain/** (plan, checklist, template, task)
                       onboarding/application/HrOnboardingService.java
                       onboarding/application/HrOnboardingWorkflowLinkService.java
                       onboarding/infrastructure/JdbcHrOnboardingRepository.java
TESTS_FIRST        : HrOnboardingServiceIntegrationTest (RED: completion derived
                     only, waive requires capability+reason, template snapshot
                     semantics, cancel cascade)
GREEN              : §6.4 matrix; Y2-linked apply-events idempotent by
                     (task_id, transition_seq); PLAN_CREATED on conversion path
                     already covered by T8 suite (regression kept)
REFACTOR           : shared transition harness final form
ACCEPTANCE_CRITERIA: no manual completion/percent fields; overdue never
                     auto-completes; cascade cancel cascades Y2 items
SECURITY_GATES     : TASK.COMPLETE ≤ TASK.WAIVE privilege ordering test
TENANT_GATES       : templates tenant-scoped; RLS probes; assignee tenant users
ROLLBACK_CONSIDERATIONS: plan cancel is terminal-state forward fix; instances
                     never mutate on template edits
DONE_DEFINITION    : integration tests green; workflow-link suite green
```

### G1-T10 — API + OpenAPI

```text
OBJECTIVE          : All §13 endpoints as v2 controllers with G0 error contract,
                     cursor pagination, If-Match, OpenAPI annotations; error-code
                     mapping tests; contract tests fail on undocumented routes.
DEPENDENCIES       : G1-T3..T9 (services behind endpoints)
FILES_EXPECTED     : hr/api/v2/recruitment/** (controllers, request/response
                     records, error mapping) + hr/api/v2/onboarding/**
                     + OpenAPI annotation updates
TESTS_FIRST        : HrRecruitmentOpenApiContractTest (RED: routes undocumented);
                     HrRecruitmentApiErrorMappingTest (RED: §15 codes unmapped);
                     idempotency-replay API tests (RED)
GREEN              : every §13 row implemented; replay semantics per §7 (200 +
                     original result); pagination determinism; 409 family stable
REFACTOR           : shared request-id handling via HrmIdempotentCommandExecutor
                     (no bespoke paths)
ACCEPTANCE_CRITERIA: contract test green incl. hire-conversion explicit idempotent
                     documentation; error list §15 exhaustive
SECURITY_GATES     : capability annotations on every route; matrix test covers
                     every endpoint × capability incl. candidate self-service scope
TENANT_GATES       : tenant context middleware assertions; cross-tenant 404/empty
ROLLBACK_CONCEPTIONS: none needed — additive API surface
DONE_DEFINITION    : OpenAPI + error-mapping + idempotency API suites green
```

### G1-T11 — Web UI

```text
OBJECTIVE          : 15 §14 screens under apps/web/app/hr/... with i18n/RTL,
                     accessibility, permission-aware rendering, loading/empty/
                     error states; HRM execution dashboard G1 rows updated ONLY
                     via certificate-bound reconciliation pattern (never hand-edited).
DEPENDENCIES       : G1-T10 (APIs); SEQUENTIAL after T10
FILES_EXPECTED     : apps/web/app/hr/recruitment/** (openings, candidates,
                     pipeline, interviews, offers, conversion)
                       apps/web/app/hr/onboarding/**
                       apps/web/app/hr/hr-labels.ts additions (keys, no literals)
TESTS_FIRST        : page tests per screen (RED); check_i18n_keys.py (RED: new
                     keys missing); RTL logical-property lint (RED)
GREEN              : all §14 screens per functional spec; permission-scoped
                     rendering tests; keyboard-path tests; error mapping tests
REFACTOR           : shared HR data-table + state-badge components
ACCEPTANCE_CRITERIA: §14 table coverage complete; no hard-coded strings; RTL
                     verified; a11y checks green; no PII in client cache beyond
                     permissioned DTOs
SECURITY_GATES     : UI hides unauthorized controls (capability-driven); masked
                     contact rendering per §10
TENANT_GATES       : all fetches tenant-scoped by session context
ROLLBACK_CONSIDERATIONS: additive pages; dashboard rows revert by reconciliation
                     evidence only
DONE_DEFINITION    : web test suite green; i18n governance script green;
                     build + performance budget green (checker now path-safe)
```

### G1-T12 — Security / integration / closure

```text
OBJECTIVE          : Stage closure — full security integration sweep, evidence
                     assembly, execution-dashboard certificate reconciliation,
                     closure certificate per G0 discipline.
DEPENDENCIES       : G1-T1..T11 all DONE
FILES_EXPECTED     : extended security suites (RLS/authorization matrices final),
                     docs/hrm/g1/evidence/HRM-G1-ENGINEERING-CLOSURE.md,
                     hr-execution-data.ts G1 rows (certificate-bound),
                     hr-g1-closure-state regression test
TESTS_FIRST        : consolidated gate suite run (RED if any gap): HrG1 RLS full
                     matrix, authorization matrix full, HrHireConversion full,
                     outbox/audit sweep, module boundary sweep
GREEN              : FAILURES=0, ERRORS=0, UNEXPLAINED_SKIPS=0 on exact closure SHA
REFACTOR           : none post-evidence (evidence discipline: no post-green edits)
ACCEPTANCE_CRITERIA: certificate complete per G0 template; SOURCE_DEFECTS_OPEN=0
                     or remediated+re-evidenced; claim discipline (no production/
                     legal claims); LEGAL_REVIEW remains PENDING_HUMAN
SECURITY_GATES     : CI role contract + PMV green on closure merge SHA
TENANT_GATES       : final cross-tenant sweep green
ROLLBACK_CONSIDERATIONS: closure is declarative; revert = new forward evidence
DONE_DEFINITION    : G1 engineering closure certificate + evidence bundle;
                     G1 stage exit; G2 remains NOT_AUTHORIZED for implementation
```

## 2. Dependency graph and parallelization

```text
T1 ──▶ T2 ──▶ T3 ──┐
              T4 ──┤ (T3 ∥ T4 after T2)
                   ▼
                   T5 ──▶ T6 ──▶ T7 ──▶ T8 ──▶ T9 ──▶ T10 ──▶ T11 ──▶ T12

PARALLEL_SAFE      : T3 ∥ T4 (disjoint aggregates; shared T2 schema read-only
                     for both); T9-standalone-plan pieces may start after T2 but
                     their conversion-link suite waits for T8; UI skeleton
                     components (T11) may precede T10 against mocked contracts —
                     but screen tests gate on T10.
SEQUENTIAL_REQUIRED: T1→T2 (contracts before schema); T5 after T3+T4 (edge
                     aggregate); T6 after T5; T7 after T6 (offer needs interview
                     stage semantics); T8 after T7 (ACCEPTED offers); T9 after
                     T8 (conversion creates plans); T10 after T9; T11 after T10;
                     T12 last.
BLOCKED_BY         : T8 BLOCKED_BY T4+T7 (+ G0 services); T12 BLOCKED_BY all.
CRITICAL_PATH      : T1 → T2 → T3 → T5 → T6 → T7 → T8 → T9 → T10 → T11 → T12
                     (T4 parallel on the T3 branch; T8 is the single highest-
                     risk stage — mandatory suite is its own gate).
```

Stage gates (evidence discipline, G0 pattern): Gate-A after T2 (schema+RLS
evidence), Gate-B after T8 (conversion suite evidence — the stage's decisive
gate), Gate-C after T11 (UI/i18n/a11y evidence), Gate-D at T12 (closure
certificate). Each gate: exact-SHA evidence file, FAILURES=0/ERRORS=0/
UNEXPLAINED_SKIPS=0, no claim inflation.

## 3. Risk register (top items)

| Risk | Impact | Likelihood | Mitigation |
|---|---|---|---|
| Conversion atomicity violated by hidden async step | duplicate/partial hires | low | §7 single-transaction rule; failure-injection suite at every output step; review checklist bans async inside boundary |
| Identity ambiguity mishandled (auto-merge) | wrong Person linkage | medium | §7.3-1/2 fail-closed ambiguity; human-review state; blind-index thresholds conservative |
| RLS regression on new tables | cross-tenant leak | medium | FORCE RLS + policy probes per table in CI; role-contract assertion extended |
| Workflow link desync (Y2 ↔ G1 state) | stuck approvals | medium | in-transaction instance-state checks; orphan-cancel compensations; idempotent apply-events |
| Zero-byte JS budget masking UI regressions | budget blind spot | known debt | follow-up PERF-CHECKER-MEASUREMENT-COVERAGE (pre-existing, G0 register); T11 runs checker but does not weaken it |
| Schema drift vs G0 migration terminal state | Flyway conflicts | low | append-only naming V2026090X; history assertion tests |
| PII leakage into logs/payloads | privacy violation | medium | §10.2 bans + log-assertion + payload REFS_ONLY tests |
| Scope creep into G2 (payroll/attendance) | directive violation | medium | §4 OUT_OF_SCOPE; boundary architecture test; PR review contract |

## 4. Non-start declaration

```text
G1_IMPLEMENTATION_STARTED = NO
This plan authorizes execution of NOTHING. Each task above starts only after a
separate explicit directive authorizes HRM G1 IMPLEMENTATION. Until then the
only permitted artifacts are the two documents committed with this change.
```
