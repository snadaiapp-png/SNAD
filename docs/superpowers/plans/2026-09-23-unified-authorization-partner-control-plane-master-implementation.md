# SANAD Unified Authorization & Partner Control Plane — Master Implementation Plan

**Date:** 2026-09-23 · **Revision B — 2026-09-24 correction record**
**Status:** PLAN_CORRECTION_REQUIRED resolved — Rev B plans ready for `INDEPENDENT_RE_REVIEW`; implementation NOT authorized.
**Spec (authoritative):** `docs/superpowers/specs/2026-09-23-unified-authorization-partner-billing-design.md` Revision B (Rev A merged to main via PR #1140, commit `8d0d49c7`; Rev B on correction branch).
**IMPLEMENTATION_BASE_SHA:** `8d0d49c7bdd3ad3a886a23cffc1e735e61998712` (= origin/main at correction time, re-verified after `git fetch --all --prune`)
**Correction branch:** `docs/unified-control-plane-plan-corrections` — docs-only.
**Forensics basis:** worklog tasks F-A/F-B/F-C/F-D at the baseline SHA, re-verified for the Rev B correction set (platform_files DDL, capability namespace, finance payment model, SubscriptionFinancePort signatures, tenant_subscriptions lifecycle, flag conventions, RLS GUC wiring, canonical constants — evidence cited per wave in "Implementation Baseline").

---

## 0. Prime directive (from spec §2 + mission rules)

EXTEND EXISTING AUTHORITATIVE SYSTEMS. DO NOT BUILD PARALLEL SECURITY OR FINANCE SYSTEMS.

| Design concept | Existing system extended (never replaced) | Physical carrier |
|---|---|---|
| Capability registry | `access_capabilities` (V7) + `AccessCapabilityService` | additive metadata columns + Rev B capability seeds per spec §6.1 |
| RBAC | `roles`/`role_capabilities`/`user_role_assignments` (V6/V8/V9) + `CapabilityEvaluationService.evaluate()` | five-stage pipeline inside evaluator; old signature preserved |
| ABAC/ReBAC-lite scopes | `access_scope_grants` (V20260905_12, FORCE RLS) + `security/scope` + `HrResourceContextResolver` | port extraction, HR adapters untouched |
| Explicit ALLOW/DENY | precedent `access_scope_grants.is_direct_exception` + `WorkflowBreakGlassService` pattern | `user_permission_overrides` with `effect='DENY' ⇒ scope NULL` DB CHECK (spec §9 Rev B) |
| Protected roles | `role_origin='SNAD_TEMPLATE'` + `role_template_bindings` (V20260820_5/_6) + canonical-owner pins (V20260921_1) | `protected_system_roles` registry with EXACTLY 4 codes — mutation invariants only, NOT a runtime source |
| Explainable decisions | `AccessDecisionResponse` / `ScopedAuthorizationDecision` | `AuthorizationDecision` (superset; `DecisionSource` has NO `PROTECTED_SAFETY`) |
| Finance authority | `Finance*Service` + `SubscriptionFinancePort.ensureInvoice(UUID,UUID)`/`recordSettlement`/`recordRefund` (R0C13) + `subscription_billing_outbox` | partner flows through the SAME port; credit notes represented as `finance_payments` REFUNDED rows |
| Invoice truth | `billing_invoices` (V19) + `finance_invoices` mirror | additive party/kind columns with FKs; NO `auto_invoicing_enabled` on invoices |
| Trial lifecycle | `SubscriptionLifecycle` + `SubscriptionCommandService` (single-writer R0C-7) + `TrialExpirationService` (R0C-8) | 3 new statuses + confirmation artifact + `tenant_subscriptions.auto_invoicing_enabled` carrier |
| Notifications | audit outbox idioms (HR V20260905_14) + `BillingOutbox` | `notification_events`/`_deliveries` with denormalized scope + explicit RLS |
| Dashboards | `ExecutiveOverviewService` read-models + billing read services | column-exact governed projections + typed per-scope event log |
| Logo storage | `platform_files` (V20260911_2 — NO `kind`, NO `partner_id`) | `source_module='COMMERCIAL'`, `source_entity_type='BRAND_LOGO'`, additive nullable `partner_id`, ONE fail-closed principal policy (spec §14.3) |

## 1. Waves, subplans, dependency graph

| Wave | Subplan (same directory) | Depends on | Flags (default OFF until their OWN G7 stage passes — spec §29.1) |
|---|---|---|---|
| 1 Unified Authorization Core | `2026-09-23-wave-1-unified-authorization-core.md` | — | `SANAD_UAC_PIPELINE_ENABLED`, mode `SANAD_UAC_MODE` (G7-A/B) |
| 2 Partner Principal + Delegated Administration | `2026-09-23-wave-2-partner-delegated-administration.md` | W1 | `SANAD_PARTNER_ENABLED` (G7-C) |
| 3 Commercial/Tax Identity + Branding | `2026-09-23-wave-3-commercial-tax-identity-branding.md` | W2 | `SANAD_COMMERCIAL_IDENTITY_ENABLED` (G7-D) |
| 4 Partner → Tenant Billing | `2026-09-23-wave-4-partner-tenant-billing.md` | W2, W3 | `SANAD_TRIAL_CONTINUATION_ENABLED`, `SANAD_PARTNER_BILLING_ENABLED` (G7-E) |
| 5 Net-Collected Settlement + SANAD→Partner Billing | `2026-09-23-wave-5-partner-settlement-sanad-billing.md` | W4 | `SANAD_SETTLEMENT_ENABLED` (G7-F) |
| 6 Notifications + 4 Dashboards + Invoice Dashboards | `2026-09-23-wave-6-notifications-executive-dashboards.md` | W2 (events), W3 (portal screen), W4/W5 (metrics) | `SANAD_NOTIFICATIONS_ENABLED`, `SANAD_DASHBOARDS_ENABLED` (G7-G) |
| 7 Progressive Cutover + Hardening + Release Verification | `2026-09-23-wave-7-cutover-security-release.md` | W1–W6 | stages G7-A..G7-G; never all flags in one commit/deployment |

```text
W1 ──> W2 ──> W3 ──> W4 ──> W5 ──> W6 ──> W7
 │      └──────────────┴─────────────────┘ (W3 starts once W2 merges; W4 needs W3 snapshots)
 └─────────────── frontend scaffolding of W6 partner portal shell may start after W2 merges
```

## 2. Migration stamp ledger (zero-collision allocation, convention `V{YYYYMMDD}_{seq}` per F-C)

| Wave | Allocated stamps | Count |
|---|---|---|
| W1 | `V20260924_1` – `V20260924_6` | 6 |
| W2 | `V20260925_1` – `V20260925_6` | 6 |
| W3 | `V20260926_1` – `V20260926_7` | 7 |
| W4 | `V20260927_1` – `V20260927_9` | 9 |
| W5 | `V20260928_1` – `V20260928_6` | 6 |
| W6 | `V20260929_1` – `V20260929_5` | 5 |
| W7 | `V20260930_1` – `V20260930_5` | 5 |

Rules (F-C): only ADD migrations (never edit shipped ones — prod `validate-on-migrate: true`); every new table gets ENABLE+FORCE RLS + `DROP POLICY IF EXISTS` before the fail-closed policy in the SAME migration; RLS SQL lives in `db/migration` (vendor dir is legacy-only); per-date seq restarts at `_1`; stamps are pre-allocated so parallel wave branches cannot collide. Rev B change vs Rev A: W2 consumes 6 stamps (the 7th was absorbed — `V20260925_5` now carries the delegation capability seeds demanded by spec §6.1).

## 3. TDD + evidence protocol (every wave, non-negotiable)

1. Failing test first (exact command + expected failure listed per task in each wave plan).
2. Implement; exact verification command; regression set re-run.
3. Commit boundary = green tests at that exact commit; commit message format `wave<N>(<scope>): <concrete description>` with the scope and description written out per task.
4. Wave exit gate (all green, logged with HEAD SHA):
   - Frontend: `cd apps/web && npm ci && npm run lint && npx tsc --noEmit && npm test -- --reporter=verbose && npm run build` (mirrors `.github/workflows/post-merge-verification.yml` — `ci.yml` has NO web job; verified).
   - Backend full: `cd apps/sanad-platform && mvn test -B -ntp -Dsurefire.useFile=false` (CI uses bare `mvn` with `working-directory: apps/sanad-platform`; single-module; no root `mvnw`/`pom.xml` — verified).
   - PostgreSQL Direct: pg-acceptance profile command with `SPRING_PROFILES_ACTIVE=pg-acceptance PG_ACCEPTANCE_JDBC_URL='jdbc:postgresql://127.0.0.1:5432/pg_acceptance?prepareThreshold=0' PG_ACCEPTANCE_USERNAME=sanad PG_ACCEPTANCE_PASSWORD=sanad_pass` — baseline trio `CommerceOrderPostgresConcurrencyTest:6, RbacAccessCheckPostgresAcceptanceTest:15, ModuleRegistryUatPostgresAcceptanceTest:10` (31 tests; the `expected` map lives INLINE in `.github/workflows/ci.yml` lines 535–539 — NOT in `tests/ci/*.py`; verified), extended by 4 acceptance classes in W7 Task 6 with machine-derived counts in the SAME commit.
5. Evidence-gated reporting: no real test execution ⇒ no pass; no same-SHA evidence ⇒ no pass; final report only after exact-HEAD CI green. Count rule (mission §19): when a test count cannot exist until the test file is written, the count is derived mechanically from surefire XML and the CI map updated in the SAME commit — never invented.

## 4. Baseline test evidence (current; refreshed per wave gate)

Local battery mirroring `ci.yml` contracts against CI-contract PostgreSQL 16 (local `127.0.0.1:5433` DBs `sanad`/`test_migration`/`pg_acceptance`, roles `sanad` NOBYPASSRLS + `crm_contact_rls_test_user`; CI pg-acceptance host-native on `127.0.0.1:5432/pg_acceptance`); frontend `typecheck`/`lint`/`vitest`/`build`; backend full suite; RLS isolation classes (named explicitly per wave regression lists — there is no CI artifact called "8-class isolation batch"; that Rev A phrase referred to the local battery, now spelled out per wave); CI 31-test trio (extended to 7 classes in W7). Results recorded per HEAD SHA in `snad-evidence/evidence-<sha>.log`.

## 5. Risk registers (from forensics; owners = wave plans)

**Architectural conflicts**
- A1 (W1): `CapabilityEvaluationService.evaluate()` is load-bearing at 874 `@RequireCapability` sites/104 files — the five-stage pipeline is a strict superset; old method delegates, no signature change (W1 Task 8).
- A2 (W1): access admin mutations are unaudited today — every new/extended admin API writes `platform_audit_logs` (PlatformAuditWriter) or the wave fails `AccessAdminAuditContractTest` (W1 Tasks 6/10).
- A3 (W1): `Role` JPA entity does not map `is_system_managed/role_origin/template_key/template_version` (V20260820_5) — reconcile before protected-role guards rely on it.
- A4 (W2): RLS GUC today is `app.tenant_id` ONLY (verified — no second GUC exists anywhere) — `app.partner_id` is net-new, set by `TenantRlsConnectionHandler` from the JWT claim; the control-plane UUID appears in NEW partner policies as the documented SQL constant (mirrors `V20260921_1`), a deliberate Rev B decision since no policy embeds it today.
- A5 (W4): `SaasAdministrationService.markInvoicePaid` bypasses Finance — hard-rejects partner-issued invoices (`seller_principal_id != null` ⇒ 409); SANAD-direct legacy path flagged for W7 reconciliation.
- A6 (W4/W6): `subscription_billing_outbox` has no dispatcher (rows stay READY — verified) — outbox is a durable fact log; W6 consumes events inline-transactionally.
- A7 (W4): `BillingStateService` freezes `billing_state='TRIALING'` (early return, lines 165–168) — new lifecycle states mapped explicitly (W4 Task 15).
- A8 (W5, Rev B): `finance_payments` has NO `completed_at`/`external_reference` — allocation candidates join through `finance_invoices.external_reference` (`uk_finance_invoices_tenant_external_ref`), ordering by `(payment_date, id)`; no Finance schema is invented.

**Security risks**
- S1: fail-open permissive-when-unset RLS on ~70 `crm_%` tables (vendor DO-loop `V20260730_1`) — W7 closes with enumerated DROP+fail-closed policies gated by the full CRM integration job (16 classes) + new `CrmRlsFailClosedPostgresTest`.
- S2: finance tables ENABLE-not-FORCE — W7 forces them (Task 1); background/owner contexts already set the GUC.
- S3: webhook endpoint is permitAll — safety rides on `DisabledBillingPaymentProvider` throwing; partner billing reuses signature-first `BillingWebhookService` ingress only.
- S4: partner scope never comes from caller-supplied IDs — resolved from the signed `partner_id` claim (deterministic because of the one-ACTIVE-membership partial unique index, W2 Tasks 1/6/7); mismatch ⇒ 403.
- S5 (Rev B): protected roles + break-glass are MUTATION INVARIANTS (`ProtectedRoleGuard`, `LastAdminGuard`), never a runtime precedence layer; break-glass creates a time-boxed ≤4h audited ALLOW override evaluated normally; an active direct DENY always beats it (W1 Tasks 11/15). `DecisionSource.PROTECTED_SAFETY` does not exist.
- S6 (W3, Rev B): `platform_files` is SHARED infrastructure (only FK consumer `workflow_attachments`) — its new RLS is ONE fail-closed principal-aware policy and the full workflow attachment + CRM integration suites are regression gates (W3 Task 3).

**Billing risks**
- B1: VAT engine does not exist (`tax_minor` hardcoded 0) — W4 introduces per-line `tax_rate` from commercial profile config; NO ZATCA/legal claims (spec §33).
- B2 (Rev B): refunds are full-only in Finance; credit notes are represented as `finance_payments` REFUNDED rows (`reference_type='PARTNER_CREDIT_NOTE'`) through a port extension — `finance_invoices` schema untouched (no `invoice_kind` column exists there; none invented).
- B3: no FX — settlement enforces single currency per period (`PERIOD_CURRENCY_MIX` fail-closed).
- B4: collected-cash truth = webhook-verified `finance_payments COMPLETED` only; manual `markInvoicePaid` structurally excluded from the candidate view (W5 Task 7).
- B5: sequence numbering reuses the atomic `ON CONFLICT ... RETURNING` idiom of `CommerceFinanceAdapter`; partner sequences are FK'd to `partners`, and SANAD settlement invoices use the dedicated `platform_invoice_number_sequences` — never a forged partner sentinel.
- B6 (Rev B): agreement temporal overlap is constraint-enforced (`EXCLUDE USING gist` + `btree_gist`, precedent `hr_org_unit_versions`), not SELECT-before-INSERT.

**Migration risks**
- M1: version collisions across parallel wave branches — eliminated by §2 pre-allocation.
- M2: FORCE-RLS ordering + policy OR-combining — `DROP POLICY IF EXISTS` template replicated verbatim per table.
- M3: exact-count CI gate — any trio/acceptance change updates the INLINE ci.yml map with machine-derived counts in the same commit.
- M4: pg_acceptance DB is dropped/recreated per CI run — acceptance tests fully self-migrate; ~42 Rev B migrations measured in W4 and W7.

## 6. Capability contract + coverage

The ONE canonical Capability Contract Table (CAPABILITY / PRINCIPAL TYPES / SCOPE / DELEGATABLE / SYSTEM_PROTECTED / USED BY API / SEEDED IN MIGRATION) lives in spec §6.1 Rev B and is enforced by seed-contract tests (W2 Task 5, W3 Task 6, W4 Tasks 8/9). Canonical billing vocabulary: `BILLING.READ` (pre-existing via canonicalization) and `BILLING.MANAGE` (created once in W2), wrapped by `PartnerDelegationGate` + partner scope; NO `PARTNER.BILLING.*` duplicates; `PARTNER.INVOICE.ISSUE`/`PARTNER.CREDITNOTE.ISSUE` are deliberate action-level issuance authorities. The truthful per-section coverage matrix (statuses `COVERED / PARTIAL / NOT_APPLICABLE_WITH_REASON / BLOCKED`) is regenerated in `2026-09-23-self-review-checklist-vs-spec.md` §A.

## 7. Task counts (parsed from the Rev B plan files)

```text
WAVE_1_TASK_COUNT = 17  (Task 1..Task 17)
WAVE_2_TASK_COUNT = 14  (Task 1..Task 14)
WAVE_3_TASK_COUNT = 13  (Task 1..Task 13)
WAVE_4_TASK_COUNT = 21  (Task 1..Task 21)
WAVE_5_TASK_COUNT = 15  (Task 1..Task 15)
WAVE_6_TASK_COUNT = 10  (Task 1..Task 10)
WAVE_7_TASK_COUNT = 14  (Task 1..Task 14)
TOTAL_TASK_COUNT  = 104
```

Parse rule: `### Task N:` headings per wave file (the Rev A table-row parse of 80 is superseded). CI per-class counts are NEVER derived from these plan counts — they are derived mechanically from surefire XML at implementation (W7 Task 6).

## 8. Progressive cutover

Seven ordered stages G7-A..G7-G (spec §29.1 Rev B; W7 Tasks 7–13), each with exact precondition, same-SHA tests, negative security gates, observability, rollback flag, rollback trigger, post-enable smoke, and evidence path. All flags default OFF in code/config until their own gate passes. No stage turns on another subsystem's flags and no stage combines flips into one commit/deployment.

## 9. Stop condition

Per mission rules: NO implementation starts before the project owner authorizes it; the next allowed action is `INDEPENDENT_RE_REVIEW` of the corrected spec + nine plans (`EVIDENCE BEFORE ASSERTION`). Upon authorization: TDD Wave-by-Wave, evidence per §3, final report per rules 14–16 (`EXECUTION_BLOCKER_REPORT` if any gate is NOT_RUN/BLOCKED/FAIL/IN_PROGRESS; `VERIFIED_COMPLETE` only with same-SHA machine evidence).
