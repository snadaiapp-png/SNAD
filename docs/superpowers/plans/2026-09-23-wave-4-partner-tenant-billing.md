# WAVE 4 — Partner → Tenant Billing + Trial Continuation Gate (Implementation Plan)

> For agentic workers: REQUIRED SUB-SKILL: verification-before-completion — never report PASS without same-SHA machine evidence; every task below is RED → GREEN with exact commands and commit boundaries.

**Spec:** Revision B — §16 (commercial models), §17 (manual/automatic billing), §17.2 (auto-invoicing carrier = `tenant_subscriptions`, scheduler five-condition), §18.2–§18.3 (fee percent + agreement versioning + temporal exclusion), §15/§30 (immutable snapshots), §19 (Finance authoritative), §6.1 (Capability Contract Table), §31.6 (temporal + concurrency tests).
**Depends on:** W2 (partner/binding/delegation), W3 (business identity + snapshots). **Migrations:** `V20260927_1`..`V20260927_9` · **Flags:** `SANAD_TRIAL_CONTINUATION_ENABLED`, `SANAD_PARTNER_BILLING_ENABLED` (default `false`).

## Goal

Enable partner-issued tenant invoices bound to versioned commercial agreements with DB-enforced non-overlapping effective windows, move the automatic-invoicing preference OFF the invoice onto the subscription (`tenant_subscriptions.auto_invoicing_enabled`), complete the billing FK audit, and keep Finance authoritative through the EXISTING `SubscriptionFinancePort` contract.

## Architecture

Agreement tables get an `EXCLUDE USING gist` temporal constraint (`btree_gist` — same mechanism family as `hr_org_unit_versions`, `V20260905_3` lines 78–85). `billing_invoices` gains party/kind columns with FKs (NO `auto_invoicing_enabled` — an invoice does not exist when the automatic-invoicing decision is made). Issuance goes through `SubscriptionFinancePort.ensureInvoice(UUID tenantId, UUID billingInvoiceId)` EXACTLY as it exists at baseline (`subscription/billing/domain/SubscriptionFinancePort.java` lines 12–60; adapter derives `external_reference = "SCP_INVOICE:" + billingInvoiceId`, idempotent via `uk_finance_invoices_tenant_external_ref`, `V20260820_6` lines 26–28). Credit notes are represented in Finance as `finance_payments` rows with `status='REFUNDED'` + `reference_type='PARTNER_CREDIT_NOTE'` (finance_payments already has `reference_type/reference_id` and the `REFUNDED` status — `V20260815_16` lines 164–190); `finance_invoices` is NOT altered (no `invoice_kind` column exists there and none is invented).

## Tech Stack

Java 21 · Spring Boot single-module Maven · Flyway · PostgreSQL 16 (EXCLUDE USING gist, partial unique indexes, drop+recreate additive CHECK widening per `V20260830_1` idiom) · Next.js `apps/web`.

## Spec

§17.2 Rev B scheduler condition, verbatim: `subscription ACTIVE_BILLABLE AND tenant_subscriptions.auto_invoicing_enabled = true AND auditable continuation confirmation exists AND partner binding ACTIVE AND required delegation AND effective commercial agreement valid`, then create the invoice. §18.3 Rev B: overlap DB-blocked; same-start race admits exactly one row; open-ended version blocks later overlap until properly closed via append-only transition.

## Implementation Baseline

Repository evidence at `8d0d49c7`: `billing_invoices` DDL `V19__create_saas_administration.sql` lines 89–117 — status CHECK `('DRAFT','OPEN','PAID','VOID')`, no party/kind columns; `SaasAdministrationService.markInvoicePaid` (`admin/service/SaasAdministrationService.java` lines 764–788) writes `billing_invoices` directly (dual-truth risk A5); `tenant_subscriptions` status constraint `ck_tenant_subscriptions_status` (13 values after `V20260830_1` lines 20–28), NO auto-invoicing/continuation columns anywhere; `SubscriptionLifecycle.STATUSES` = 13 values, `TERMINAL_STATUSES = {CANCELLED,EXPIRED,TERMINATED}`, transitions only via `SubscriptionCommandService.applyCanonicalTransition` (single writer, guarded UPDATE + `subscription_commands` ledger); `TrialExpirationService` env-gated `SANAD_TRIAL_EXPIRY_ENABLED`; `BillingStateService` early-returns on `billing_state='TRIALING'` (lines 165–168); `SubscriptionFinancePort.ensureInvoice(UUID,UUID)` + `recordSettlement`/`recordRefund` (exact signatures in §Architecture); `finance_invoice_number_sequences(tenant_id, period, last_value)` with atomic `ON CONFLICT ... RETURNING last_value` idiom (`commerce/application/CommerceFinanceAdapter.java` lines 169–180); `subscription_billing_outbox` write-only (no dispatcher), `BillingOutbox.emit(...)` with `uq (tenant_id, idempotency_key)`; `BillingWebhookService.receive(byte[], String)` verifies signature first.

## Global Constraints

1. DOCS-ONLY branch: not executed until the owner authorizes WAVE 4.
2. NO `auto_invoicing_enabled` column on `billing_invoices` — the carrier is `tenant_subscriptions.auto_invoicing_enabled boolean NOT NULL DEFAULT false`.
3. Agreement effective windows: overlap-impossible at DB level (EXCLUDE constraint); service SELECT-before-INSERT is never relied upon.
4. FK audit: every new `*_id` below either has a real FK or a documented reason (register in Task 9).
5. Finance: no second ledger; partner flows use the existing port; `markInvoicePaid` hard-rejects partner-issued invoices.
6. Trial expiration alone must never produce a chargeable invoice.

## Review Focus

Temporal integrity suite; carrier location; FK register completeness; capability vocabulary (NO `PARTNER.BILLING.*`); port contract exactness.

---

Java paths relative to `apps/sanad-platform/src/main/java/com/sanad/platform/` (new package `partner/billing/`; lifecycle changes in `subscription/lifecycle/`); tests to `apps/sanad-platform/src/test/java/com/sanad/platform/`.

### Task 1: Agreements + versions with temporal exclusion constraint

Files:
- Create: `db/migration/V20260927_1__partner_commercial_agreements.sql`
- Test: `partner/billing/AgreementTemporalIntegrityPostgresTest.java` (Create)

Interfaces:
- Consumes: `partners` (W2); `btree_gist` (precedent `V20260905_3` line 31).
- Produces: `partner_commercial_agreements`, `partner_commercial_agreement_versions` (append-only, non-overlapping windows).

- [ ] Step 1: exact failing test — `partner/billing/AgreementTemporalIntegrityPostgresTest.java` (plain-JDBC + `MigrationTestSchemaSupport`), spec §31.6 matrix: v1 `[T1,T2)` accepted; v2 `[T2,T3)` accepted (adjacent, `[)` semantics); overlapping `[T1.5,T3)` rejected SQLState 23P01 (exclusion_violation); same-start race (two threads inserting windows starting at the same instant) admits EXACTLY one row; open-ended active version (effective_to NULL) blocks any overlapping later version; closing the open version via an append-only UPDATE of `effective_to` ONLY (fee/model/effective_from still immutable) then inserting the successor succeeds.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.partner.billing.AgreementTemporalIntegrityPostgresTest test` → `PSQLException: relation "partner_commercial_agreements" does not exist`.
- [ ] Step 3: exact minimal implementation — `V20260927_1__partner_commercial_agreements.sql`:
  `CREATE EXTENSION IF NOT EXISTS btree_gist;`
  `partner_commercial_agreements(id uuid pk, partner_id uuid NOT NULL UNIQUE REFERENCES partners(id), status text NOT NULL CHECK (status IN ('DRAFT','EFFECTIVE')), created_by uuid NOT NULL, created_at/updated_at)` + partner composite FORCE RLS (partner READ own; writes platform-context only).
  `partner_commercial_agreement_versions(id uuid pk, agreement_id uuid NOT NULL REFERENCES partner_commercial_agreements(id), version int NOT NULL, platform_fee_percent numeric(5,4) NOT NULL CHECK (platform_fee_percent >= 0 AND platform_fee_percent <= 1), commercial_model text NOT NULL CHECK (commercial_model IN ('DIRECT','RESELLER','COMMISSION','HYBRID')), currency_code char(3) NOT NULL, effective_from timestamptz NOT NULL, effective_to timestamptz NULL, approved_by uuid NOT NULL, created_at timestamptz NOT NULL DEFAULT now(), CHECK (effective_to IS NULL OR effective_to > effective_from), UNIQUE (agreement_id, version))` + `ADD CONSTRAINT ex_pca_versions_no_overlap EXCLUDE USING gist (agreement_id WITH =, tstzrange(effective_from, COALESCE(effective_to, 'infinity'::timestamptz), '[)') WITH &&)` + immutability trigger `commercial_forbid_agreement_version_mutation` blocking UPDATE of fee/model/effective_from and ALL DELETEs (closing an open window = the ONLY permitted UPDATE, limited to `effective_to`); partner RLS READ-own + FORCE.
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green (all six temporal scenarios).
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.rls.PartnerPrincipalRlsPostgresTest test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave4(schema): agreements with exclusion-constraint temporal integrity (C1)"`.

### Task 2: billing_invoices party columns + FKs (NO auto-invoicing flag)

Files:
- Create: `db/migration/V20260927_2__billing_invoices_party_columns.sql`
- Test: `partner/billing/InvoicePartyColumnsPostgresTest.java` (Create)

Interfaces:
- Consumes: `billing_invoices` (`V19` lines 89–117), `business_principals` (W3), `partner_commercial_agreement_versions` (Task 1).
- Produces: `invoice_kind` (additive CHECK widen, drop+recreate per `V20260830_1` idiom), `seller_principal_id` FK, `buyer_principal_id` FK, `agreement_version_id` FK.

- [ ] Step 1: exact failing test — legacy rows unaffected (status values unchanged); `invoice_kind` defaults `STANDARD` and CHECK accepts only `('STANDARD','CREDIT')` (SETTLEMENT added in W5 `V20260928_2`); `buyer_principal_id` with a random UUID rejected 23503; `seller_principal_id` FK enforced; `agreement_version_id` FK enforced; NO column named `auto_invoicing_enabled` on `billing_invoices` (`information_schema.columns` negative assertion — spec §17.2 Rev B).
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.partner.billing.InvoicePartyColumnsPostgresTest test` → columns missing (red).
- [ ] Step 3: exact minimal implementation — `V20260927_2__billing_invoices_party_columns.sql`: `ALTER TABLE billing_invoices ADD COLUMN invoice_kind text NOT NULL DEFAULT 'STANDARD', ADD COLUMN seller_principal_id uuid REFERENCES business_principals(id), ADD COLUMN buyer_principal_id uuid REFERENCES business_principals(id), ADD COLUMN agreement_version_id uuid REFERENCES partner_commercial_agreement_versions(id);` then drop+recreate the status-adjacent CHECK: `ALTER TABLE billing_invoices DROP CONSTRAINT IF EXISTS ck_billing_invoices_invoice_kind; ALTER TABLE billing_invoices ADD CONSTRAINT ck_billing_invoices_invoice_kind CHECK (invoice_kind IN ('STANDARD','CREDIT'));` — legacy rows keep NULL principals (SANAD-direct), documented invariant. Header comment: `auto_invoicing_enabled` deliberately NOT here — carrier is `tenant_subscriptions` (Task 5).
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.admin.service.SaasAdministrationServiceTest test` → green (legacy invoice paths untouched).
- [ ] Step 6: exact commit — `git commit -m "wave4(schema): invoice party columns with FKs, carrier flag excluded (C2)"`.

### Task 3: Credit notes with partner FK

Files:
- Create: `db/migration/V20260927_3__billing_credit_notes.sql`
- Test: `partner/billing/CreditNoteSchemaPostgresTest.java` (Create)

Interfaces:
- Consumes: `billing_invoices` (Task 2), `partners` (W2).
- Produces: `billing_credit_notes` (tenant FORCE RLS + partner composite read policy).

- [ ] Step 1: exact failing test — table exists; `partner_id` FK enforced (random UUID ⇒ 23503); `amount_minor > 0` CHECK; status CHECK `('ISSUED','APPLIED','VOID')`; tenant RLS isolation + partner read policy.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.partner.billing.CreditNoteSchemaPostgresTest test` → relation missing (red).
- [ ] Step 3: exact minimal implementation — `V20260927_3__billing_credit_notes.sql`: `billing_credit_notes(id uuid pk, tenant_id uuid NOT NULL REFERENCES tenants(id), partner_id uuid REFERENCES partners(id), billing_invoice_id uuid NOT NULL REFERENCES billing_invoices(id), reason text NOT NULL, amount_minor bigint NOT NULL CHECK (amount_minor > 0), tax_minor bigint NOT NULL DEFAULT 0, currency_code char(3) NOT NULL, status text NOT NULL CHECK (status IN ('ISSUED','APPLIED','VOID')), issued_by uuid NOT NULL, created_at/updated_at, version bigint NOT NULL DEFAULT 0)` + FORCE tenant RLS + partner composite read policy.
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.rls.FlywayJavaMigrationsChainConsistencyTest test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave4(schema): credit notes with partner FK (C2)"`.

### Task 4: Snapshot ↔ invoice binding FK

Files:
- Create: `db/migration/V20260927_4__invoice_snapshot_binding.sql`
- Test: `commercial/domain/SnapshotInvoiceBindingPostgresTest.java` (Create)

Interfaces:
- Consumes: `invoice_party_snapshots.invoice_id` (NULL since W3 `V20260926_3` — documented forward reference), `billing_invoices` (Task 2).
- Produces: `fk_snapshot_invoice FOREIGN KEY (invoice_id) REFERENCES billing_invoices(id)` + index.

- [ ] Step 1: exact failing test — snapshot insert with `invoice_id` = random UUID rejected 23503; with real invoice id accepted; plain-id FK documented (cross-context platform table; composite tenant FK not applicable because seller-side rows carry the partner scope).
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.commercial.domain.SnapshotInvoiceBindingPostgresTest test` → insert succeeds (no constraint) ⇒ red.
- [ ] Step 3: exact minimal implementation — `V20260927_4__invoice_snapshot_binding.sql`: `ALTER TABLE invoice_party_snapshots ADD CONSTRAINT fk_snapshot_invoice FOREIGN KEY (invoice_id) REFERENCES billing_invoices(id);` + `CREATE INDEX idx_snapshot_invoice ON invoice_party_snapshots (invoice_id);`.
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.commercial.domain.SnapshotImmutabilityPostgresTest test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave4(schema): snapshot-invoice FK binding (C2)"`.

### Task 5: Lifecycle gate statuses + auto-invoicing carrier on the subscription

Files:
- Create: `db/migration/V20260927_5__lifecycle_gate_statuses_and_auto_invoicing.sql`
- Test: `subscription/lifecycle/LifecycleGateStatusesPostgresTest.java` (Create)

Interfaces:
- Consumes: `ck_tenant_subscriptions_status` (13 values after `V20260830_1`).
- Produces: +3 statuses (`TRIAL_ENDING`, `PENDING_CONTINUATION`, `ACTIVE_BILLABLE`); `tenant_subscriptions.auto_invoicing_enabled boolean NOT NULL DEFAULT false`; `continuation_confirmed_at/continuation_confirmed_by` columns.

- [ ] Step 1: exact failing test — new statuses accepted by CHECK; legacy 13 still accepted; `auto_invoicing_enabled` exists, defaults false, NOT NULL; `billing_invoices` has NO such column (cross-assertion with Task 2); continuation columns nullable.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.subscription.lifecycle.LifecycleGateStatusesPostgresTest test` → invalid status rejected by old CHECK (red).
- [ ] Step 3: exact minimal implementation — `V20260927_5__lifecycle_gate_statuses_and_auto_invoicing.sql`: drop+recreate `ck_tenant_subscriptions_status` with legacy 13 + `'TRIAL_ENDING','PENDING_CONTINUATION','ACTIVE_BILLABLE'` (additive, `V20260830_1` idiom verbatim); `ALTER TABLE tenant_subscriptions ADD COLUMN auto_invoicing_enabled boolean NOT NULL DEFAULT false, ADD COLUMN continuation_confirmed_at timestamptz NULL, ADD COLUMN continuation_confirmed_by uuid NULL;` — header comment: authoritative pre-invoice carrier per spec §17.2 Rev B.
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.subscription.lifecycle.SubscriptionLifecycleTest,com.sanad.platform.subscription.lifecycle.LifecycleSingleWriterPostgresTest test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave4(schema): lifecycle gate statuses + subscription auto-invoicing carrier (C3)"`.

### Task 6: Continuation confirmations (auditable source + timestamp)

Files:
- Create: `db/migration/V20260927_6__subscription_continuation_confirmations.sql`
- Test: `subscription/lifecycle/ContinuationConfirmationSchemaPostgresTest.java` (Create)

Interfaces:
- Consumes: `tenant_subscriptions` (Task 5).
- Produces: `subscription_continuation_confirmations` — the auditable confirmation artifact of §17.2.

- [ ] Step 1: exact failing test — insert with `channel` outside `('PRODUCT_WORKFLOW','API_EXPLICIT')` rejected; FORCE tenant RLS; index `(subscription_id, confirmed_at DESC)`.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.subscription.lifecycle.ContinuationConfirmationSchemaPostgresTest test` → relation missing (red).
- [ ] Step 3: exact minimal implementation — `V20260927_6__subscription_continuation_confirmations.sql`: `subscription_continuation_confirmations(id uuid pk, tenant_id uuid NOT NULL REFERENCES tenants(id), subscription_id uuid NOT NULL REFERENCES tenant_subscriptions(id), confirmed_by uuid NOT NULL, confirmed_at timestamptz NOT NULL DEFAULT now(), channel text NOT NULL CHECK (channel IN ('PRODUCT_WORKFLOW','API_EXPLICIT')), evidence jsonb NOT NULL DEFAULT '{}')` + FORCE tenant RLS + index.
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.rls.FlywayJavaMigrationsChainConsistencyTest test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave4(schema): auditable continuation confirmations (C3)"`.

### Task 7: Invoice number sequences (partner-FK'd + platform) + readiness indexes

Files:
- Create: `db/migration/V20260927_7__partner_and_platform_invoice_sequences.sql`, `db/migration/V20260927_8__settlement_readiness_indexes.sql`
- Test: `partner/billing/InvoiceSequencesPostgresTest.java` (Create)

Interfaces:
- Consumes: `partners`; atomic sequence idiom `CommerceFinanceAdapter` lines 169–180.
- Produces: `partner_invoice_number_sequences(partner_id REFERENCES partners(id), period, last_value, PK(partner_id, period))`; `platform_invoice_number_sequences(period, last_value, PK(period))` — used by W5 SANAD→partner settlement invoices so NO owner-user UUID is ever used as a fake partner sentinel; readiness indexes.

- [ ] Step 1: exact failing test — sequence upsert returns monotonic values per (partner, period); FK rejects unknown partner_id (23503); platform sequence monotonic per period; indexes `billing_invoices (seller_principal_id, status)`, `billing_invoices (buyer_principal_id, status)`, `billing_credit_notes (billing_invoice_id)`, `finance_payments (status, created_at) WHERE status='COMPLETED'`, `tenant_subscriptions (status) WHERE status IN ('TRIAL','TRIALING','TRIAL_ENDING','PENDING_CONTINUATION','ACTIVE_BILLABLE')` exist.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.partner.billing.InvoiceSequencesPostgresTest test` → relations missing (red).
- [ ] Step 3: exact minimal implementation — the two migrations exactly as listed (FK on partner sequences is deliberate: W5's SANAD settlement invoices use `platform_invoice_number_sequences`, never a forged partner row).
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.commerce.CommerceFinanceAdapterTest test` → green (existing sequence idiom untouched).
- [ ] Step 6: exact commit — `git commit -m "wave4(schema): partner+platform invoice sequences, settlement readiness indexes (C3)"`.

### Task 8: Billing capability seeds (contract table — NO duplicates)

Files:
- Create: `db/migration/V20260927_9__partner_billing_capability_seeds.sql`
- Test: `partner/billing/BillingCapabilitySeedContractTest.java` (Create)

Interfaces:
- Consumes: `access_capabilities`; W2 seeds (`BILLING.MANAGE`, `TENANT.*`, `SUBSCRIPTION.*`, `PARTNER.PLATFORM.MANAGE`).
- Produces: `PARTNER.INVOICE.ISSUE`, `PARTNER.CREDITNOTE.ISSUE`, `SETTLEMENT.VIEW`, `SETTLEMENT.FINALIZE` (`system_protected=true` for FINALIZE).

- [ ] Step 1: exact failing test — the 4 codes exist ACTIVE with correct flags; NEGATIVE assertions: `PARTNER.BILLING.MANAGE` and `PARTNER.BILLING.READ` do NOT exist (semantic-duplicate ban); `BILLING.READ`/`BILLING.MANAGE` already exist from W2/baseline.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.partner.billing.BillingCapabilitySeedContractTest test` → `capability not found: PARTNER.INVOICE.ISSUE` (red).
- [ ] Step 3: exact minimal implementation — `V20260927_9__partner_billing_capability_seeds.sql` (7-column INSERT idiom); header comment: `PARTNER.INVOICE.ISSUE`/`PARTNER.CREDITNOTE.ISSUE` are action-level document-issuance authorities deliberately distinct from `BILLING.MANAGE` (spec §6.1) and are used consistently in registry, delegation allowlist, controllers, tests, UI gates.
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.partner.application.PartnerDelegationGateTest test` → green (allowlist unchanged).
- [ ] Step 6: exact commit — `git commit -m "wave4(schema): issuance capability seeds per capability contract (C3)"`.

### Task 9: Billing FK audit register (machine-checked)

Files:
- Create: `partner/billing/BillingForeignKeyAuditPostgresTest.java`
- Modify: none
- Test: this class

Interfaces:
- Consumes: `information_schema` + `pg_constraint` for W4 tables.
- Produces: the FK register below, enforced by test so future drift fails CI.

- [ ] Step 1: exact failing test — the test asserts every row of the register below (FK presence where FK=YES; absence where FK=NO with reason). RED on first run for any constraint not yet created.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.partner.billing.BillingForeignKeyAuditPostgresTest test` → register violations listed.
- [ ] Step 3: exact minimal implementation — none (constraints already shipped by Tasks 1–7; fix any gap in its source migration).
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green with this register verified:

| COLUMN | REFERENCES | FK | REASON | DELETE/UPDATE BEHAVIOR |
|---|---|---|---|---|
| `billing_invoices.tenant_id` | `tenants(id)` | YES (V19 `fk_billing_invoices_tenant`) | pre-existing | RESTRICT / — |
| `billing_invoices.subscription_id` | `tenant_subscriptions(id)` | YES (V19) | pre-existing | RESTRICT / — |
| `billing_invoices.seller_principal_id` | `business_principals(id)` | YES (Task 2) | seller identity authority | RESTRICT / — |
| `billing_invoices.buyer_principal_id` | `business_principals(id)` | YES (Task 2) | buyer identity authority | RESTRICT / — |
| `billing_invoices.agreement_version_id` | `partner_commercial_agreement_versions(id)` | YES (Task 2) | version pinning authority | RESTRICT / — |
| `billing_credit_notes.tenant_id` | `tenants(id)` | YES (Task 3) | tenant scope | RESTRICT / — |
| `billing_credit_notes.partner_id` | `partners(id)` | YES (Task 3) | issuing partner identity (mission §12) | RESTRICT / — |
| `billing_credit_notes.billing_invoice_id` | `billing_invoices(id)` | YES (Task 3) | corrective target | RESTRICT / — |
| `invoice_party_snapshots.principal_id` | `business_principals(id)` | YES (W3) | snapshot source | RESTRICT / — |
| `invoice_party_snapshots.invoice_id` | `billing_invoices(id)` | YES (Task 4) | issuance binding | RESTRICT / — |
| `subscription_continuation_confirmations.tenant_id` | `tenants(id)` | YES (Task 6) | tenant scope | RESTRICT / — |
| `subscription_continuation_confirmations.subscription_id` | `tenant_subscriptions(id)` | YES (Task 6) | confirmed subject | RESTRICT / — |
| `partner_invoice_number_sequences.partner_id` | `partners(id)` | YES (Task 7) | sequence owner | RESTRICT / — |
| `finance_payments.reference_id` (rows with `reference_type='PARTNER_CREDIT_NOTE'`) | `billing_credit_notes(id)` | NO | cross-module pointer INTO Finance kept loosely coupled by design — Finance is authoritative and must not depend on billing tables (spec §19); linkage is by deterministic reference string + reconciliation | n/a |

- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.rls.FlywayJavaMigrationsChainConsistencyTest test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave4(audit): billing FK register machine-checked (C4)"`.

### Task 10: Lifecycle code extension (single-writer preserved)

Files:
- Modify: `subscription/lifecycle/SubscriptionLifecycle.java` (add `TRIAL_ENDING`, `PENDING_CONTINUATION`, `ACTIVE_BILLABLE` to `STATUSES`; commands `MARK_TRIAL_ENDING` (TRIAL→TRIAL_ENDING), `AWAIT_CONTINUATION` (TRIAL_ENDING→PENDING_CONTINUATION), `CONFIRM_CONTINUATION` (TRIAL_ENDING|PENDING_CONTINUATION→ACTIVE_BILLABLE); `EXPIRE` extended to accept `PENDING_CONTINUATION`; `TERMINAL_STATUSES` unchanged), `subscription/lifecycle/SubscriptionResolutionService.java` (`EFFECTIVE_PREDICATE` treats the 3 new states as effective)
- Test: `subscription/lifecycle/TrialContinuationGateRedTest.java` (Create)

Interfaces:
- Consumes: `SubscriptionCommandService.applyCanonicalTransition` — every transition goes through the single writer (R0C-7); CHECK widened in Task 5.
- Produces: legal transition table entries; no fork of the command service.

- [ ] Step 1: exact failing test — `TrialContinuationGateRedTest`: each new command legal from its source status, illegal elsewhere; `EXPIRE` from `PENDING_CONTINUATION` legal; terminal statuses unchanged; DB CHECK accepts the three new values (Task 5 backstop).
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.subscription.lifecycle.TrialContinuationGateRedTest test` → unknown command/transition failures (red).
- [ ] Step 3: exact minimal implementation — the two modifications above.
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.subscription.lifecycle.LifecycleSingleWriterPostgresTest,com.sanad.platform.subscription.lifecycle.SubscriptionMultiplicityStoragePostgresTest test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave4(lifecycle): trial continuation transitions through single writer (C5)"`.

### Task 11: Continuation confirmation service

Files:
- Create: `subscription/lifecycle/ContinuationConfirmationService.java`
- Modify: none
- Test: `subscription/lifecycle/ContinuationConfirmationServiceTest.java` (Create)

Interfaces:
- Consumes: `subscription_continuation_confirmations` (Task 6); authorization — tenant admin (TENANT plane) or bound partner via `PartnerDelegationGate` (`SUBSCRIPTION.CREATE` + ACTIVE binding); `SubscriptionCommandService` (`CONFIRM_CONTINUATION`); `PlatformAuditWriter`.
- Produces: `confirm(subscriptionId, actor, channel, evidence)` — inserts confirmation row + canonical transition + `tenant_subscriptions.continuation_confirmed_at/by` + audit; double-confirm ⇒ 409.

- [ ] Step 1: exact failing test — confirmation row + transition + audit in one transaction; second confirm ⇒ 409; unauthorized actor ⇒ 403; partner path without delegation ⇒ 403.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5433/sanad SPRING_DATASOURCE_USERNAME=sanad SPRING_DATASOURCE_PASSWORD=sanad_pass mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.subscription.lifecycle.ContinuationConfirmationServiceTest test` → class missing (red).
- [ ] Step 3: exact minimal implementation — the service.
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.subscription.lifecycle.ExpiredSuccessorRuntimePostgresTest test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave4(lifecycle): auditable continuation confirmation service (C5)"`.

### Task 12: Trial continuation scheduler

Files:
- Create: `subscription/lifecycle/TrialContinuationScheduler.java`
- Modify: none
- Test: `subscription/lifecycle/TrialContinuationSchedulerPostgresTest.java` (Create)

Interfaces:
- Consumes: `@Scheduled` + double gate `scheduling.enabled` AND `SANAD_TRIAL_CONTINUATION_ENABLED` (env-gating precedent `TrialExpirationService` lines 123–129); `SELECT ... FOR UPDATE` re-check; emits `TRIAL_ENDING_SOON` notification event (consumed by W6).
- Produces: TRIAL ending ≤72h ⇒ `MARK_TRIAL_ENDING`; `TRIAL_ENDING` past `trial_ends_at` ⇒ `AWAIT_CONTINUATION`; `PENDING_CONTINUATION` past 7-day grace ⇒ canonical `EXPIRE`.

- [ ] Step 1: exact failing test — T-72h advance writes TRIAL_ENDING; past-due ⇒ PENDING_CONTINUATION; grace expiry ⇒ EXPIRE via the single writer; flag off ⇒ no-op; concurrent scheduler runs never double-transition (FOR UPDATE).
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5433/sanad SPRING_DATASOURCE_USERNAME=sanad SPRING_DATASOURCE_PASSWORD=sanad_pass mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.subscription.lifecycle.TrialContinuationSchedulerPostgresTest test` → class missing (red).
- [ ] Step 3: exact minimal implementation — the scheduler.
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.subscription.lifecycle.TrialExpirationRuntimePostgresTest test` → green (legacy expiry path intact).
- [ ] Step 6: exact commit — `git commit -m "wave4(lifecycle): trial continuation scheduler (C5)"`.

### Task 13: Agreement service

Files:
- Create: `partner/billing/AgreementService.java`
- Modify: none
- Test: `partner/billing/AgreementServiceTest.java` (Create)

Interfaces:
- Consumes: Task 1 tables; authority — platform `AUTHORIZATION.PLATFORM.MANAGE`; partner read-only own view.
- Produces: `createAgreement/createVersion` (platform only; overlap impossible by Task 1 constraint), `resolveEffectiveAt(UUID partnerId, Instant at)` — version whose window contains `at`; none ⇒ fail-closed `NO_EFFECTIVE_AGREEMENT`.

- [ ] Step 1: exact failing test — create v1 20%; attempt overlapping v2 ⇒ constraint violation surfaced as 409 `AGREEMENT_OVERLAP`; close v1 (append-only) then v2 succeeds; `resolveEffectiveAt` before any window ⇒ `NO_EFFECTIVE_AGREEMENT`; partner write attempt ⇒ 403.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.partner.billing.AgreementServiceTest test` → class missing (red).
- [ ] Step 3: exact minimal implementation — the service.
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.partner.billing.AgreementTemporalIntegrityPostgresTest test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave4(agreement): versioned agreement service with fail-closed resolution (C6)"`.

### Task 14: Partner invoice issuance (exact port contract)

Files:
- Create: `partner/billing/PartnerInvoiceService.java`
- Modify: none
- Test: `partner/billing/PartnerInvoiceIssuancePostgresTest.java` (Create)

Interfaces:
- Consumes: ACTIVE binding + delegation `BILLING.MANAGE` (administration) + `PARTNER.INVOICE.ISSUE` (document issuance) via `PartnerDelegationGate`; `PartySnapshotService.capture` (W3) with invoice binding; `partner_invoice_number_sequences` (`PINV-yyyyMM-<seq>`, idiom `CommerceFinanceAdapter` lines 169–180); `SubscriptionFinancePort.ensureInvoice(tenantId, billingInvoiceId)` — the EXISTING signature (adapter computes `SCP_INVOICE:<id>`, idempotent via `uk_finance_invoices_tenant_external_ref`); `BillingOutbox.emit("BILLING.PARTNER_INVOICE_ISSUED.v1")`.
- Produces: issuance transaction: resolve agreement version ⇒ `agreement_version_id`; capture SELLER (partner principal) + BUYER (tenant principal) snapshots bound to `invoice_id` (immutable thereafter); number; status `OPEN`; Finance mirror via port; outbox fact; owner notification event row.

- [ ] Step 1: exact failing test — MANUAL happy path: one issuance produces exactly one invoice + 2 snapshots (SELLER=partner, BUYER=tenant) + version bound + one `finance_invoices` mirror row (via port, `external_reference='SCP_INVOICE:<id>'`) + one outbox row + one owner notification event; replay of the same issuance id (port idempotency) creates NO second Finance row.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5433/sanad SPRING_DATASOURCE_USERNAME=sanad SPRING_DATASOURCE_PASSWORD=sanad_pass mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.partner.billing.PartnerInvoiceIssuancePostgresTest test` → class missing (red).
- [ ] Step 3: exact minimal implementation — `PartnerInvoiceService.createDraft/issue` exactly as specified (port called with `(tenantId, billingInvoiceId)` — never with a fabricated reference string; the adapter owns reference formats).
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.subscription.billing.R0C13G03FinanceIntegrationPostgresTest test` → green (port behavior unchanged for SCP flows).
- [ ] Step 6: exact commit — `git commit -m "wave4(billing): partner invoice issuance through existing finance port (C6)"`.

### Task 15: Issuance guards + markInvoicePaid rejection + billing-state mapping

Files:
- Create: `partner/billing/PartnerInvoiceGuardPostgresTest.java` (test only)
- Modify: `admin/service/SaasAdministrationService.java` (guard: `invoice.seller_principal_id != null` ⇒ throw `PartnerInvoiceFinanceAuthorityException` (409) inside `markInvoicePaid` before any write), `admin/service/BillingStateService.java` (map `TRIAL_ENDING`/`PENDING_CONTINUATION` ⇒ keep `billing_state='TRIALING'`; `ACTIVE_BILLABLE` on first issued invoice ⇒ `CURRENT` — removes the TRIALING early-return blind spot A7)
- Test: the new test class

Interfaces:
- Consumes: `SaasAdministrationService.markInvoicePaid` (lines 764–788 — writes `billing_invoices` directly, bypassing Finance).
- Produces: §30 invariant "partner cannot issue invoice for foreign tenant" + "Finance is sole collected-cash authority for partner invoices".

- [ ] Step 1: exact failing test — issue during TRIAL/TRIAL_ENDING/PENDING_CONTINUATION without confirmation ⇒ `TRIAL_NOT_BILLABLE`; foreign tenant ⇒ 403; no delegation ⇒ 403; `markInvoicePaid` on partner-issued invoice (`seller_principal_id != null`) ⇒ 409 `PARTNER_INVOICE_FINANCE_AUTHORITY` and NO row change; SANAD-direct invoice (`seller_principal_id IS NULL`) still payable via legacy path; ACTIVE_BILLABLE first invoice ⇒ `billing_state='CURRENT'`; TRIAL_ENDING keeps `TRIALING`.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5433/sanad SPRING_DATASOURCE_USERNAME=sanad SPRING_DATASOURCE_PASSWORD=sanad_pass mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.partner.billing.PartnerInvoiceGuardPostgresTest test` → guards absent (red).
- [ ] Step 3: exact minimal implementation — the two modifications.
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.admin.service.SaasAdministrationServiceTest,com.sanad.platform.subscription.billing.R0C13G06SettlementReconciliationPostgresTest test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave4(billing): issuance guards, finance-authority rejection, billing-state mapping (C6)"`.

### Task 16: Agreement version pinning

Files:
- Create: `partner/billing/AgreementVersionPinningPostgresTest.java`
- Modify: none
- Test: this class

Interfaces:
- Consumes: `AgreementService.resolveEffectiveAt` (Task 13) called inside the issuance transaction (Task 14).
- Produces: §18.3 proof — later versions never rewrite earlier economics.

- [ ] Step 1: exact failing test — issue invoice under v1 (20%); approve v2 (15%) with a later window; the original invoice still carries `agreement_version_id` = v1 and a W5 settlement preview for that invoice uses v1 math; closing/reopening windows never changes bound rows.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.partner.billing.AgreementVersionPinningPostgresTest test` → pinning absent (red).
- [ ] Step 3: exact minimal implementation — none (binding already in Task 14; fix there if red).
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.partner.billing.PartnerInvoiceIssuancePostgresTest test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave4(billing): agreement version pinning proven (C7)"`.

### Task 17: Automatic billing scheduler (five-condition gate)

Files:
- Create: `partner/billing/AutomaticBillingScheduler.java`
- Modify: none
- Test: `partner/billing/AutomaticBillingPostgresTest.java` (Create)

Interfaces:
- Consumes: the §17.2 Rev B condition set, evaluated in order: `ACTIVE_BILLABLE` AND `tenant_subscriptions.auto_invoicing_enabled` AND confirmation row exists AND binding ACTIVE AND delegation + effective agreement valid; idempotency key `AUTO:<subscriptionId>:<period>` (outbox `uq (tenant_id, idempotency_key)` backstop); gate `SANAD_PARTNER_BILLING_ENABLED` + `scheduling.enabled`; `SELECT ... FOR UPDATE`.
- Produces: draft+issue at period boundary; no invoice when ANY condition fails.

- [ ] Step 1: exact failing test — all conditions true ⇒ exactly one invoice per period; replay same key ⇒ exactly one invoice (idempotent); each condition individually false ⇒ zero invoices (5 negative cases); concurrent scheduler runs ⇒ no duplicates.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5433/sanad SPRING_DATASOURCE_USERNAME=sanad SPRING_DATASOURCE_PASSWORD=sanad_pass mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.partner.billing.AutomaticBillingPostgresTest test` → class missing (red).
- [ ] Step 3: exact minimal implementation — the scheduler (reads the flag from the subscription row — NOT from any invoice column).
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.subscription.lifecycle.TrialContinuationSchedulerPostgresTest test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave4(billing): five-condition automatic invoicing off the subscription carrier (C7)"`.

### Task 18: Credit notes — service + Finance representation

Files:
- Create: `partner/billing/CreditNoteService.java`
- Modify: `subscription/billing/domain/SubscriptionFinancePort.java` (add `FinanceInvoiceLink ensureCreditNote(UUID tenantId, UUID billingCreditNoteId, UUID billingInvoiceId, long amountMinor, String currencyCode)`), `finance/integration/SubscriptionFinanceAdapter.java` (implement: deterministic payment number from `billingCreditNoteId`; insert `finance_payments` row `status='REFUNDED'`, `payment_method='OTHER'`, `reference_type='PARTNER_CREDIT_NOTE'`, `reference_id=<creditNoteId>`; idempotent by deterministic number replay — mirroring `recordSettlement` replay semantics)
- Test: `partner/billing/CreditNotePostgresTest.java` (Create)

Interfaces:
- Consumes: `billing_credit_notes` (Task 3); delegation `BILLING.MANAGE` + `PARTNER.CREDITNOTE.ISSUE`; open-balance validation.
- Produces: credit against OPEN/PAID invoice (amount ≤ open balance); Finance REFUNDED payment row (NO `finance_invoices` schema change — no `invoice_kind` column is invented there); void path; emits `BILLING.CREDIT_NOTE_ISSUED.v1` + owner notification event.

- [ ] Step 1: exact failing test — credit reduces open balance; Finance mirror row created once (replay idempotent); void path records no further money movement; amount > open balance rejected; foreign-tenant/partner guard 403.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5433/sanad SPRING_DATASOURCE_USERNAME=sanad SPRING_DATASOURCE_PASSWORD=sanad_pass mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.partner.billing.CreditNotePostgresTest test` → class/method missing (red).
- [ ] Step 3: exact minimal implementation — service + port method + adapter implementation.
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.subscription.billing.R0C13G07CorrectivePostgresTest,com.sanad.platform.subscription.billing.R0C13ArchitectureBoundaryTest test` → green (partner code still never touches `finance_` tables outside the port).
- [ ] Step 6: exact commit — `git commit -m "wave4(billing): credit notes via finance payment ledger representation (C7)"`.

### Task 19: Billing RLS verification

Files:
- Create: `partner/billing/PartnerBillingRlsPostgresTest.java`
- Modify: none
- Test: this class

Interfaces:
- Consumes: RLS shipped with Tasks 2/3 migrations.
- Produces: partner A sees only own invoices/credit notes; tenant sees own; platform oversight all.

- [ ] Step 1: exact failing test — partner GUC context: partner-A invoice rows visible, partner-B invisible; tenant GUC: own buyer rows visible; control-plane context: all visible; inserts under wrong context blocked.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.partner.billing.PartnerBillingRlsPostgresTest test` → cross rows visible (red).
- [ ] Step 3: exact minimal implementation — none (fix source migrations if red).
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — full W1–W3 RLS classes: `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.rls.PartnerPrincipalRlsPostgresTest,com.sanad.platform.security.rls.BusinessIdentityRlsPostgresTest,com.sanad.platform.security.rls.PlatformFilesForceRlsPostgresTest test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave4(rls): partner billing isolation proven (C7)"`.

### Task 20: Frontend — partner billing surfaces

Files:
- Create: `apps/web/lib/api/partner-billing-api.ts`, `apps/web/lib/api/partner-billing-api.test.ts`, `apps/web/app/executive/billing/partner-invoices/page.tsx`, `apps/web/app/executive/partners/[id]/agreement/page.tsx`, `apps/web/app/subscriptions/[id]/continuation-panel.tsx`
- Modify: i18n `ar.ts`/`en.ts` (`partner.billing.*`, `continuation.*`, same commit)
- Test: the API test + continuation panel gating test

Interfaces:
- Consumes: `/api/v1/partner/invoices`, `/api/v1/partner/credit-notes`, `/api/v1/executive/billing/partner-invoices`, `/api/v1/executive/billing/partner-invoices/agreements`, `POST /api/v1/subscriptions/{id}/continuation/confirm` + partner variant `/api/v1/partner/tenants/{tenantId}/subscriptions/{id}/continuation/confirm`.
- Produces: §23.1 oversight grid preview, agreement versions viewer, TRIAL_ENDING/PENDING_CONTINUATION banner + Confirm Continuation (capability `SUBSCRIPTION.MANAGE`).

- [ ] Step 1: exact failing test — `partner-billing-api.test.ts`: exact URLs above + typed exports `createDraft/issueInvoice/listPartnerInvoices/listCreditNotes/issueCreditNote/listAgreements/createAgreementVersion/confirmContinuation`.
- [ ] Step 2: exact command proving RED — `cd apps/web && npm test -- partner-billing` → red.
- [ ] Step 3: exact minimal implementation — client + three surfaces + i18n.
- [ ] Step 4: exact command proving GREEN — `cd apps/web && npm test -- partner-billing` → green.
- [ ] Step 5: exact affected regression — `cd apps/web && npm run typecheck && npm run lint && npm test && python3 scripts/ci/check_i18n_keys.py` → green.
- [ ] Step 6: exact commit — `git commit -m "wave4(web): partner billing surfaces + continuation panel (C8)"`.

### Task 21: Wave exit evidence battery

Files:
- Modify: none (evidence only)
- Test: full local battery

Interfaces:
- Consumes: PostgreSQL 16 local battery.
- Produces: `snad-evidence/evidence-<HEAD-SHA>.log`.

- [ ] Step 1: exact failing test — none.
- [ ] Step 2: exact command proving RED — none.
- [ ] Step 3: exact minimal implementation — none.
- [ ] Step 4: exact command proving GREEN —
  `cd apps/web && npm ci && npm run lint && npx tsc --noEmit && npm test -- --reporter=verbose && npm run build`
  `cd apps/sanad-platform && mvn test -B -ntp -Dsurefire.useFile=false`
  `cd apps/sanad-platform && SPRING_PROFILES_ACTIVE=pg-acceptance PG_ACCEPTANCE_JDBC_URL='jdbc:postgresql://127.0.0.1:5432/pg_acceptance?prepareThreshold=0' PG_ACCEPTANCE_USERNAME=sanad PG_ACCEPTANCE_PASSWORD=sanad_pass mvn test -B -ntp -Dsurefire.useFile=true -DfailIfNoTests=true -Dtest='CommerceOrderPostgresConcurrencyTest,RbacAccessCheckPostgresAcceptanceTest,ModuleRegistryUatPostgresAcceptanceTest'` → 31/31 (6/15/10 unchanged; new acceptance classes join the CI list only in W7 with lockstep map update).
- [ ] Step 5: exact affected regression — battery IS the regression; log at `snad-evidence/evidence-<HEAD-SHA>.log`.
- [ ] Step 6: exact commit — `git commit -m "wave4(evidence): gate run @ <HEAD-SHA> (C9)"`.

## Dependencies, security, rollback

**Dependencies:** W2, W3. Blocks W5/W6. **Security:** issuance requires binding + delegation + claim congruence (§30 negative-tested Task 15); Finance remains sole collected-cash authority (`markInvoicePaid` hard-rejected for partner invoices; credit notes represented in the Finance payment ledger through the port); snapshots immutable at issuance; agreement windows overlap-impossible (constraint, not service logic); schedulers double-gated + `FOR UPDATE`; webhook surface unchanged (signature-first ingress only, S3). **Rollback:** both flags off ⇒ schedulers no-op, partner controllers 404-guarded; lifecycle CHECK widened additively (legacy rows forward-compatible — proven by Task 10 regression set); credit-note Finance mirrors remain consistent (Finance is source of truth); the `tenant_subscriptions.auto_invoicing_enabled` carrier is inert without the flag; revert commits safe.
