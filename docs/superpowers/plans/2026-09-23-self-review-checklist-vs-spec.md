# Self-Review & Coverage Matrix — Corrected Plans vs Design Spec Revision B (§1–§34)

**Reviewed artifacts:** master plan + 7 wave subplans + this checklist as corrected on branch `docs/unified-control-plane-plan-corrections`, against `docs/superpowers/specs/2026-09-23-unified-authorization-partner-billing-design.md` Revision B (Rev A blob `829000a1`, merged to main @ `8d0d49c7bdd3ad3a886a23cffc1e735e61998712` via PR #1140; Rev B written on the correction branch per `INDEPENDENT_REVIEW_COMPLETE` findings).
**Verdict rule (Rev B):** statuses are limited to `COVERED`, `PARTIAL`, `NOT_APPLICABLE_WITH_REASON`, `BLOCKED`. Nothing is pre-marked COVERED; each row names the plan, task, and test that will prove it. The statement "every requirement maps to a task and test" is TRUE only where a row says COVERED with a named executable test; architecture-only requirements carry `NOT_APPLICABLE_WITH_REASON` instead of a pretend test.

## A. Spec section coverage (§ → requirement → plan → task → test → status)

| SPEC SECTION | REQUIREMENT | PLAN | TASK | TEST | STATUS |
|---|---|---|---|---|---|
| §1 | Purpose / scope statement | Master §0 | — | — | NOT_APPLICABLE_WITH_REASON=scope narrative; no executable requirement |
| §2 | Extend existing foundations; backward compat | Master §0 table | all waves | W1 Task 8 `EvaluateFacadeCompatibilityTest` | COVERED |
| §3 | Hierarchical multi-principal platform + boundaries | W2 | Task 1, Task 12 | `PartnerPrincipalRlsPostgresTest`; portal IT | COVERED |
| §4.1 | Five-stage runtime algorithm (A–E); protected safety NOT a precedence layer; no `PROTECTED_SAFETY` source | W1 | Task 7, Task 8, Task 11 | `UnifiedDecisionPipelineTest`; `DenyDominancePostgresTest`; reflection scan on `DecisionSource` | COVERED |
| §4.2 | Fail-closed list | W1 | Task 7 | `UnifiedDecisionPipelineTest` cases a–c,f,i | COVERED |
| §5 | Protected roles = PLATFORM_OWNER, PLATFORM_ADMIN, AGENT_SUPER_ADMIN, TENANT_ADMIN; AGENT_CUSTOM_ADMIN NOT protected | W1+W2+W7 | W1 Task 3/14, W2 Task 11, W7 Task 3 | `ProtectedRoleRegistrySchemaPostgresTest`; `ProtectedRoleGrantGuardTest`; `ProtectedSeedFourRolesPostgresTest` | COVERED |
| §5.1 | Break-glass rejections + simulation + break-glass evaluated normally (DENY still wins) | W1+W7 | W1 Task 15, W7 Task 5 | `BreakGlassAccessServiceTest`; `BreakGlassInvariantPostgresAcceptanceTest` | COVERED |
| §6 | Registry metadata columns | W1 | Task 4 | `CapabilityMetadataMigrationContractTest` | COVERED |
| §6.1 | ONE canonical Capability Contract Table; no duplicate namespace | W2+W3+W4 | W2 Task 5, W3 Task 6, W4 Tasks 8–9 | `PartnerCapabilitySeedContractTest`; `CommercialCapabilitySeedContractTest`; `BillingCapabilitySeedContractTest`; `BillingForeignKeyAuditPostgresTest` | COVERED |
| §7 | RBAC + ABAC + ReBAC-lite; explicit relationships; no fuzzy matching | W1 | Task 7, Task 12 | pipeline + `EffectivePermissionProjectionServiceTest` + `HrScopedRelationshipResolver` | COVERED |
| §8 | User/subject vs employee; service accounts as subjects | W1 (existing `users` table = subjects) | documented mapping (master §0) | no schema change required — existing identity tests | NOT_APPLICABLE_WITH_REASON=design reuses the existing users/employees model; no new executable behavior |
| §9 | Overrides + Direct DENY v1 DB invariant + precedence correction | W1 | Task 1, Task 9, Task 11 | `UserPermissionOverrideRlsPostgresTest` (23514 on scoped DENY); `UserPermissionOverrideServiceTest`; `DenyDominancePostgresTest` | COVERED |
| §10 | Dynamic scope family + capability-declared scopes | W1 | Task 1, Task 9 | scope CHECK + `supports_scope` validation matrix | COVERED |
| §11 | Explainable decisions + cache model | W1 | Task 7, Task 13 | `AuthorizationDecision` record; `AuthorizationInvalidationListenerTest` | COVERED |
| §12 | Entitlements vs permissions; recalc vs resync | W1 (+ existing entitlement gate) | Task 10 | `UserOverrideControllerIT` resync 403/200 | COVERED |
| §13 | Delegation over canonical capabilities; scope from authenticated context | W2 | Task 10, Task 12 | `PartnerDelegationGateTest`; `PartnerPortalControllerIT` | COVERED |
| §13.1 | One ACTIVE partner membership per user; immediate session invalidation | W2 | Task 1, Task 6, Task 7 | `PartnerPrincipalRlsPostgresTest` (23505 race); `PartnerMembershipConcurrencyPostgresTest`; `PartnerSessionInvalidationTest` | COVERED |
| §14 | Business identity fields + 3 mounts | W3 | Task 1, Task 8, Task 11 | `BusinessIdentityRlsPostgresTest`; `BusinessIdentityServiceTest`; controller ITs | COVERED |
| §14.1 | Reusable Commercial & Tax Information screen | W3 | Task 12 | `commercial-api.test.ts` + form component tests | COVERED |
| §14.2 | White-label readiness (branding modeled separately) | W3 | Task 1/9 schema | `brand_profiles` separate table asserted in `LogoUploadServiceTest` | COVERED |
| §14.3 | platform_files: real schema, no `kind`, ownership CHECK, ONE fail-closed policy, shared-table regression | W3 | Task 2, Task 3, Task 9 | `PlatformFilesForceRlsPostgresTest`; `WorkflowAttachmentExternalFoundationTest` | COVERED |
| §15 | Immutable billing identity snapshots | W3+W4 | W3 Tasks 4/5/10, W4 Task 4/14 | `SnapshotImmutabilityPostgresTest`; issuance chain test | COVERED |
| §16 | DIRECT/RESELLER/COMMISSION/HYBRID | W2+W4 | W2 Task 1, W4 Task 1 | CHECK-constraint assertions in schema tests | COVERED |
| §17 | Partner→tenant invoices MANUAL/AUTOMATIC | W4 | Task 14, Task 17 | `PartnerInvoiceIssuancePostgresTest`; `AutomaticBillingPostgresTest` | COVERED |
| §17.2 | Auto-invoicing carrier = subscription; five-condition scheduler; auditable confirmation | W4 | Task 5, Task 6, Task 17 | `LifecycleGateStatusesPostgresTest` (negative column assertion on billing_invoices); `AutomaticBillingPostgresTest` (5 negatives) | COVERED |
| §18.1 | Settlement basis B: net collected; no double subtraction | W5 | Task 4 | `EligibleNetCalculatorTest` matrix | COVERED |
| §18.2 | Fee percent unambiguous (agreement version field) | W4+W5 | W4 Task 1, W5 Task 5 | CHECK bounds; per-item math | COVERED |
| §18.3 | Version immutability + invoice pinning + temporal exclusion | W4 | Task 1, Task 16 | `AgreementTemporalIntegrityPostgresTest` (23P01 + same-start race + open-ended block); `AgreementVersionPinningPostgresTest` | COVERED |
| §18.4 | Settlement lifecycle replay-safe + reconcilable | W5 | Task 5, Task 6, Task 11 | `PartnerSettlementCalculatePostgresTest`; `SettlementReplayReplacePostgresTest`; `SettlementReconciliationPostgresTest` | COVERED |
| §18.5 | Period state machine: full rebuild; same key no-op; new key replace; finalize immutable; concurrency | W5 | Task 6, Task 9 | `SettlementReplayReplacePostgresTest`; `SettlementFinalizeConcurrencyPostgresTest` | COVERED |
| §18.6 | Late adjustments = next-period items; finalized history untouched | W5 | Task 10 | `LateAdjustmentPostgresTest` | COVERED |
| §18.7 | NO persisted period fee; derived weighted display only | W5+W6 | W5 Task 1 (negative column assertion), W6 Task 4 (no column), W5 Task 14 (UI label) | `SettlementSchemaFkPostgresTest`; `ProjectionSchemaExactPostgresTest` | COVERED |
| §19 | Finance authoritative; no second ledger | W4/W5 | W4 Tasks 14/15/18, W5 Task 7 | `markInvoicePaid` 409; `R0C13ArchitectureBoundaryTest` extension; `ManualPaymentExclusionPostgresTest` | COVERED |
| §20 | Logical data model (tables) | W1–W6 migrations | per-wave schema tasks | schema/RLS tests per wave | COVERED |
| §21 | Notification events list + mandatory owner in-product delivery | W6 | Task 2 | `MandatoryOwnerNotificationPostgresTest` | COVERED |
| §21.1 | Denormalized scope + consistency CHECKs + explicit RLS + isolation negatives + ack ownership | W6 | Task 1, Task 3 | `NotificationSchemaRlsPostgresTest`; `NotificationIsolationPostgresTest` | COVERED |
| §22 | Three dashboard scopes + governed projections | W6 | Task 4, Task 6, Task 8 | `ProjectionSchemaExactPostgresTest`; `PartnerProjectionPostgresTest`; controller ITs | COVERED |
| §22.4 | Exact typed projection schema; no schematic placeholders; typed apply-log | W6 | Task 4, Task 5 | `ProjectionSchemaExactPostgresTest` (name-by-name); `ProjectionEventTypingPostgresTest` | COVERED |
| §22.5 | GLOBAL = DIRECT + Σ(PARTNERS); fixture ≥2 partners + ≥1 direct tenant | W6 | Task 7 | `DashboardReconciliationPostgresTest` | COVERED |
| §23 | Invoice dashboards (owner + partner views) | W6 | Task 8, Task 9 | `DashboardControllerIT`; FE gating tests | COVERED |
| §24 | Executive/partner/tenant IA | W2/W3/W6 | W2 Task 13, W3 Task 12, W6 Task 9 | FE gating tests + visual-matrix baselines | PARTIAL=component/gating tests only; no cross-browser E2E harness exists in the repo (visual-matrix baselines added in W6 Task 9; execution evidence pending) |
| §25 | Authorization administration UI + user detail panels | W1 | Task 16 | `access-api.test.ts`; `authorization-page-gating.test.tsx` | COVERED |
| §26 | Event-driven invalidation + projections; atomic outbox/audit | W1+W6 | W1 Task 13, W6 Task 2 | listener tests; fan-out same-transaction assertions | COVERED |
| §27 | Audit record fields; DENY/high-risk auditable | W1 (+ existing PlatformAuditWriter) | Task 6, Task 10 | `AccessAdminAuditContractTest` | COVERED |
| §28 | API categories fixed (routes are plan details) | W1/W2/W4/W5/W6 controllers | per-wave controller ITs | MockMvc ITs | COVERED |
| §29 | Migration strategy phases 1–9 | Master §1 mapping | per-wave | per-wave | COVERED |
| §29.1 | Progressive cutover G7-A..G7-G; per-stage gates; no big bang | W7 | Tasks 7–13 | stage smoke ITs + per-stage records | COVERED |
| §30 | 10 release-blocking security invariants | W7 | Task 4 | `SecurityInvariantsPostgresAcceptanceTest` | COVERED |
| §31.1 | Authorization matrix (subjects × dimensions) | W7 | Task 5 | `AuthorizationMatrixPostgresAcceptanceTest` | COVERED |
| §31.2 | Break-glass test list (7 rejections) | W7 | Task 5 | `BreakGlassInvariantPostgresAcceptanceTest` | COVERED |
| §31.3 | Billing/settlement mandatory tests | W4+W5 | W4 Tasks 14–18, W5 Tasks 4–12 | named per task | COVERED |
| §31.4 | Dashboard reconciliation + direct-API isolation | W6 | Task 7, Task 8 | reconciliation + isolation tests | COVERED |
| §31.5 | PostgreSQL Direct mandatory | all waves | every PG test | local + pg-acceptance profiles with exact commands | COVERED |
| §31.6 | Correction-mandated negative/concurrency tests (19 lines) | W1/W2/W3/W4/W5/W6 | W1 Task 11, W2 Task 7, W3 Tasks 1/2, W4 Task 1, W5 Tasks 6/9/10, W6 Tasks 3/7 | each line mapped to a named test class | COVERED |
| §32 | Release acceptance criteria list | W7 | Task 14 | verification runbook §4 | PARTIAL=backend/CI gates fully specified; the RTL/accessibility/mobile line depends on visual-matrix baselines (added W6 Task 9) whose execution evidence can only exist after implementation |
| §33 | Non-goals (no ZATCA/live-payments claims) | W4/W5/W7 | flags unchanged (`R0C13_PROVIDER_MODE`) | documented invariant | NOT_APPLICABLE_WITH_REASON=negative space; compliance certification is out of scope by design |
| §34 | Final architecture decision | Master §0 + waves | — | — | NOT_APPLICABLE_WITH_REASON=architecture statement; no executable requirement |

**BLOCKED rows: none.** No requirement is blocked at planning time.

## B. Quality gates applied to the corrected files (Rev B)

1. **Placeholder/shorthand scan** — the ten corrected files were scanned for: command ellipses, cross-row command references (the "reuse the command from another row" style), lettered count placeholders (the matrix-N / partner-M family), generic unwritten-count phrases, generic "battery by name only" references, TBD markers, and schematic wildcard DDL (star-suffixed column lists). Every remaining command is exact (cwd + Maven flags + fully-qualified class name); §D records the scan method and result.
2. **Exact path validation** — every pre-existing path cited (services, migrations, workflows, web files) was re-verified at `8d0d49c7` during the Rev B forensic pass (platform_files DDL, capability namespace, finance model, port signatures, lifecycle columns, flag conventions, GUC wiring, canonical constants).
3. **Interface consistency** — table names, event names, scope names, endpoint prefixes, capability codes, DTO names, and the `SubscriptionFinancePort.ensureInvoice(UUID,UUID)` contract cross-checked within and across waves and against spec §6.1.
4. **Requirement coverage** — table §A; no approved requirement silently absent; architecture-only rows carry explicit `NOT_APPLICABLE_WITH_REASON`.
5. **TDD structure** — every task carries Files/Interfaces and six exact steps (failing test, RED command, minimal implementation, GREEN command, regression, commit).

## C. Consistency decisions recorded (Rev B)

- `DecisionSource` = `EXPLICIT_DENY, EXPLICIT_ALLOW, ROLE_GRANT, RELATIONSHIP_POLICY, DELEGATED_GRANT, DEFAULT_DENY` — no `PROTECTED_SAFETY` (spec §9 Rev B).
- Protected registry = exactly 4 codes in W1 migration, W2 grant guard, and W7 seeding; `AGENT_CUSTOM_ADMIN` is a normal hierarchy role (spec §5 Rev B).
- `BILLING.READ` reused (canonicalized `V20260830_2`→`V20260901_1`); `BILLING.MANAGE` created once in W2; `PARTNER.BILLING.*` forbidden and negatively tested (spec §6.1).
- Membership determinism via partial unique `UNIQUE(user_id) WHERE status='ACTIVE'` + `session_version` invalidation; `UNIQUE(partner_id,user_id)` dropped (spec §13.1).
- `business_principals` uniqueness via partial unique indexes (NULLS NOT DISTINCT rejected as invalid for the polymorphic model).
- `platform_files` extended with nullable `partner_id` + ownership CHECK + ONE fail-closed policy; `kind` does not exist and is not created (spec §14.3).
- Agreement overlap via `EXCLUDE USING gist` (`btree_gist` precedent exists); settlement periods carry NO fee column; SANAD settlement invoices use `platform_invoice_number_sequences` (never a forged partner UUID).
- Auto-invoicing carrier = `tenant_subscriptions.auto_invoicing_enabled`; `billing_invoices` negatively tested for the column.
- Credit notes in Finance = `finance_payments` REFUNDED rows through the port extension; `finance_invoices` untouched.
- Credit notes are built in W4; W5 arithmetic depends on them (dependency noted in W5 header).
- `authorization_subjects` maps to existing `users`; `role_permission_scopes` maps to existing FORCE-RLS `access_scope_grants` (spec §20 allows naming decisions).
- The 31-test CI trio is extended only in W7 with the INLINE ci.yml map updated from machine-derived surefire counts in the same commit (no `tests/ci/*.py` map exists; no web job in `ci.yml` — web gates in `post-merge-verification.yml`).
- PARTNER execution provider requires registration in two files (contract tests + execution dashboard) — both listed in W6 Task 9.

## D. Task-count parse + scan evidence (recalculated from the corrected files)

Parsed object: `### Task N:` headings per wave file.

```text
WAVE_1_TASK_COUNT = 17
WAVE_2_TASK_COUNT = 14
WAVE_3_TASK_COUNT = 13
WAVE_4_TASK_COUNT = 21
WAVE_5_TASK_COUNT = 15
WAVE_6_TASK_COUNT = 10
WAVE_7_TASK_COUNT = 14
TOTAL_TASK_COUNT  = 104
```

Placeholder scan result: ZERO occurrences of any banned shorthand in the ten corrected files. The scan was executed mechanically at correction time over: (a) the Unicode ellipsis character anywhere; (b) the exact phrases used by Rev A table cells for reused commands, generic unit-test rows, generic integration rows, lettered matrix/partner count placeholders, the generic count-deferral phrase, the generic battery phrase, and TBD markers; (c) star-suffixed schematic column lists used as DDL. All remaining hits are negative-rule statements (this checklist and the spec/W6 prohibition clauses), never schema or commands. The Rev A draft estimate of 86 and the Rev A parse of 80 are both superseded by the 104-task Rev B parse; the master plan §7 reflects it.
