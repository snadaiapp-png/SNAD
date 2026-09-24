# WAVE 5 — Actual-Net-Collected Settlement + SANAD → Partner Billing (Implementation Plan)

> For agentic workers: REQUIRED SUB-SKILL: verification-before-completion — never report PASS without same-SHA machine evidence; every task below is RED → GREEN with exact commands and commit boundaries.

**Spec:** Revision B — §18 (settlement basis B: actual net collected revenue), §18.3 (version pinning), §18.5 (period state machine: full deterministic rebuild; same key = strict no-op; new key = transactional replace; FINALIZED immutable), §18.6 (late adjustments = next-period items), §18.7 (NO persisted period fee authority; derived `weighted_effective_fee_percent` display only), §19 (Finance authoritative), §6.1, §31.6 (concurrency + adjustment tests).
**Depends on:** W4. **Migrations:** `V20260928_1`..`V20260928_6` · **Flag:** `SANAD_SETTLEMENT_ENABLED` (default `false`).
**Rev C (R2) changes in this wave:** (1) Task 1 run model — `partner_settlement_runs.period_id` is NULLABLE for pre-period RUNNING/FAILED runs with CHECK `state <> 'COMPLETED' OR period_id IS NOT NULL`; adjustment items gain `status ('QUEUED','APPLIED')` with `target_period_id` NULL while QUEUED / NOT NULL once APPLIED, plus a UNIQUE idempotency key; (2) Task 4 calculator — negative eligible results are NEVER silently clamped to 0; the negative residual is preserved deterministically and carried forward (period CHECKs allow negative eligible net; no negative client invoice is ever issued); (3) NEW Task 8 `CollectedCashReadPort` — collected-cash truth is read through a Finance-owned read port with an architecture boundary test forbidding direct Finance SQL from `partner/billing/**` AND `partner/settlement/**`; (4) Task 10 — SANAD→Partner settlement invoices use an explicit Finance PRINCIPAL-invoice port (`ensurePrincipalInvoice`), seller = PLATFORM principal, buyer = PARTNER principal, control-plane tenant as RLS/storage carrier only — never a forged subscription/tenant UUID; (5) `SETTLEMENT.MANAGE` added to the capability chain (calculate/approve authority; `SETTLEMENT.VIEW` read-only; `SETTLEMENT.FINALIZE` platform-only), consistent across registry seeds, controllers, tests, and the spec §6.1 table.

## Goal

Compute SANAD's charge to partners from actual net collected revenue with a deterministic, replay-safe period state machine, per-item agreement-version authority, DB-complete FKs, and immutable finalized economics with next-period adjustment items.

## Architecture

One period row per (partner, window). Calculation is a FULL deterministic rebuild: the same idempotency key replays as a strict no-op; a NEW key creates a new run record and atomically replaces the non-finalized calculation. Finalization locks economics; late refunds/credits/collections become explicit adjustment items in the NEXT open period (finalized history never rewritten). Collected-cash truth = webhook-verified `finance_payments` with `status='COMPLETED'` (manual `markInvoicePaid` rows are structurally excluded). Allocation candidates join `finance_payments → finance_invoices (invoice_id) → external_reference='SCP_INVOICE:<billingInvoiceId>' → billing_invoices` — grounded in the `uk_finance_invoices_tenant_external_ref` partial unique index (`V20260820_6` lines 26–28); `finance_payments` has NO `external_reference`/`completed_at` column at baseline, so ordering uses `(payment_date, id)` and no new Finance columns are invented.

## Tech Stack

Java 21 · Spring Boot single-module Maven · Flyway · PostgreSQL 16 (row locks, FK RESTRICT, partial indexes) · Next.js `apps/web`.

## Spec

§18.5 Rev B is binding. `"Incremental delta only"` is NOT part of the model and appears nowhere in this plan. The per-item bound `agreement_version_id` is the ONLY fee authority; no `platform_fee_percent` column exists on `partner_settlement_periods` (option A of §18.7 — removal, with the derived display statistic computed at read time in W6).

## Implementation Baseline

Repository evidence at `8d0d49c7`: `finance_payments` DDL `V20260815_16` lines 164–190 — status CHECK `('PENDING','COMPLETED','FAILED','REFUNDED','CANCELLED')`, `payment_date DATE NOT NULL`, `reference_type/reference_id`, `uk (tenant_id, payment_number)`, `fk (tenant_id, invoice_id) → finance_invoices(tenant_id, id)`; NO `completed_at`, NO `external_reference`. `finance_invoices.external_reference` + `uk_finance_invoices_tenant_external_ref` (`V20260820_6`). `billing_invoices` gains `invoice_kind/seller_principal_id/buyer_principal_id/agreement_version_id` in W4. `partner_invoice_number_sequences` (W4, partner-FK'd) + `platform_invoice_number_sequences(period)` (W4) — settlement invoices use the PLATFORM sequence, never a forged partner sentinel. `BillingOutbox.emit` with `uq (tenant_id, idempotency_key)`; `R0C13ArchitectureBoundaryTest` forbids partner code touching `finance_` tables outside the port.

## Global Constraints

1. DOCS-ONLY branch: not executed until the owner authorizes WAVE 5.
2. No `platform_fee_percent` column on `partner_settlement_periods` — authority is per item.
3. Every `*_id` below has a real FK or a documented reason (register in Task 1 test).
4. `UNIQUE (partner_id, period_start, period_end)` on periods — one row per window (compatible with the rebuild model: rows are updated, not duplicated).
5. SANAD→partner invoices ride the same `billing_invoices` + `SubscriptionFinancePort` machinery (no second ledger).
6. PLATFORM-only finalize (`SETTLEMENT.FINALIZE`); partner finalize attempt ⇒ 403.

## Review Focus

State-machine semantics (replay/replace/finalize/adjust); FK register; arithmetic invariants (no double-subtraction); currency guard; concurrency.

---

Java paths relative to `apps/sanad-platform/src/main/java/com/sanad/platform/` (package `partner/settlement/`); tests to `apps/sanad-platform/src/test/java/com/sanad/platform/`.

### Task 1: Settlement schema with complete FKs + adjustment items

Files:
- Create: `db/migration/V20260928_1__partner_settlement_schema.sql`
- Test: `partner/settlement/SettlementSchemaFkPostgresTest.java` (Create)

Interfaces:
- Consumes: `partners`, `billing_invoices`, `finance_payments`, `partner_commercial_agreement_versions` (W2–W4).
- Produces: `partner_settlement_periods`, `partner_settlement_items`, `partner_settlement_runs`, `partner_settlement_adjustment_items`.

- [ ] Step 1: exact failing test — schema + FK register asserted via `pg_constraint`: (a) `partner_settlement_items.finance_payment_id → finance_payments(id)` FK YES; (b) `partner_settlement_items.tenant_invoice_id → billing_invoices(id)` FK YES; (c) `partner_settlement_items.agreement_version_id → partner_commercial_agreement_versions(id)` FK YES; (d) `partner_settlement_runs.partner_id → partners(id)` and `.period_id → partner_settlement_periods(id)` FK YES; (e) `partner_settlement_adjustment_items` FKs to `billing_invoices(id)`, `finance_payments(id)`, `partner_settlement_periods(id)` (source period) all YES; (f) NO column `platform_fee_percent` on periods; (g) `UNIQUE (partner_id, period_start, period_end)` enforced; (h) random-UUID insert into any FK column rejected 23503. Composite FORCE RLS on all four tables (partner READ own; writes platform context only).
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.partner.settlement.SettlementSchemaFkPostgresTest test` → relations missing (red).
- [ ] Step 3: exact minimal implementation — `V20260928_1__partner_settlement_schema.sql`:
  `partner_settlement_periods(id uuid pk, partner_id uuid NOT NULL REFERENCES partners(id), period_start date NOT NULL, period_end date NOT NULL, currency_code char(3) NOT NULL, status text NOT NULL DEFAULT 'CALCULATED' CHECK (status IN ('CALCULATED','PENDING_APPROVAL','FINALIZED','INVOICED','CANCELLED')), eligible_net_collected_minor bigint NOT NULL DEFAULT 0, sanad_charge_minor bigint NOT NULL DEFAULT 0 CHECK (sanad_charge_minor >= 0), calculated_at timestamptz, calculated_by uuid, finalized_by uuid NULL, finalized_at timestamptz NULL, created_at/updated_at, version bigint NOT NULL DEFAULT 0, CHECK (period_end >= period_start), UNIQUE (partner_id, period_start, period_end))` — header comment: per spec §18.7 Rev C option A, NO period-level fee column; dashboards derive `weighted_effective_fee_percent` at read time; REV C: `eligible_net_collected_minor` deliberately has NO `>= 0` CHECK — a negative net (over-refund/correction beyond collections) is a REAL economic state that is preserved and carried forward (never silently clamped to 0); `sanad_charge_minor` stays `>= 0` (a negative net yields charge 0 and a carried residual, never a negative customer invoice).
  `partner_settlement_items(id uuid pk, period_id uuid NOT NULL REFERENCES partner_settlement_periods(id), partner_id uuid NOT NULL REFERENCES partners(id), tenant_invoice_id uuid NOT NULL REFERENCES billing_invoices(id), finance_payment_id uuid NOT NULL REFERENCES finance_payments(id), agreement_version_id uuid NOT NULL REFERENCES partner_commercial_agreement_versions(id), gross_collected_minor bigint NOT NULL, tax_minor bigint NOT NULL DEFAULT 0, refund_minor bigint NOT NULL DEFAULT 0, credit_note_minor bigint NOT NULL DEFAULT 0, eligible_net_minor bigint NOT NULL, platform_charge_minor bigint NOT NULL, created_at/updated_at, UNIQUE (tenant_invoice_id, finance_payment_id))` + indexes `(period_id)`, `(partner_id, agreement_version_id)`.
  `partner_settlement_runs(id uuid pk, partner_id uuid NOT NULL REFERENCES partners(id), period_id uuid NULL REFERENCES partner_settlement_periods(id), idempotency_key text NOT NULL, state text NOT NULL CHECK (state IN ('RUNNING','COMPLETED','FAILED')), stats jsonb NOT NULL DEFAULT '{}', started_at timestamptz NOT NULL DEFAULT now(), finished_at timestamptz, UNIQUE (partner_id, idempotency_key), CHECK (state <> 'COMPLETED' OR period_id IS NOT NULL))` — REV C run model: a run may START before its period row exists (pre-period RUNNING/FAILED probes, currency pre-checks, dry-run validation) so `period_id` is NULLABLE; the CHECK guarantees a COMPLETED run is always bound to its period; the calculate service binds `period_id` in the SAME transaction that creates/locks the period row and marks the run COMPLETED (atomic claim — no window where a COMPLETED run lacks its period).
  `partner_settlement_adjustment_items(id uuid pk, partner_id uuid NOT NULL REFERENCES partners(id), status text NOT NULL DEFAULT 'QUEUED' CHECK (status IN ('QUEUED','APPLIED')), target_period_id uuid NULL REFERENCES partner_settlement_periods(id), source_period_id uuid NULL REFERENCES partner_settlement_periods(id), tenant_invoice_id uuid NULL REFERENCES billing_invoices(id), finance_payment_id uuid NULL REFERENCES finance_payments(id), adjustment_type text NOT NULL CHECK (adjustment_type IN ('LATE_REFUND','LATE_CREDIT_NOTE','LATE_COLLECTION','CORRECTION')), amount_minor bigint NOT NULL, idempotency_key text NOT NULL, reason text NOT NULL, claimed_by_run_id uuid NULL REFERENCES partner_settlement_runs(id), claimed_at timestamptz NULL, created_by uuid NOT NULL, created_at, CHECK (amount_minor <> 0), UNIQUE (idempotency_key), CHECK ((status = 'APPLIED' AND target_period_id IS NOT NULL AND claimed_by_run_id IS NOT NULL) OR (status = 'QUEUED' AND target_period_id IS NULL AND claimed_by_run_id IS NULL)))` — REV C adjustment model: QUEUED items carry NULL target and are claimed ATOMICALLY by the next period calculation (`UPDATE ... SET status='APPLIED', target_period_id=<next period>, claimed_by_run_id=<run>, claimed_at=now() WHERE id IN (...) AND status='QUEUED'` inside the calculate transaction — the row lock guarantees exactly one claiming run); APPLIED items are permanently bound; the UNIQUE idempotency key makes re-emission of the same adjustment a no-op; signed amounts participate in the target period's eligible-net (spec §18.6). SAME migration ships the FINALIZED-ECONOMICS IMMUTABILITY triggers: `BEFORE UPDATE OR DELETE ON partner_settlement_periods WHEN OLD.status IN ('FINALIZED','INVOICED')` and `BEFORE UPDATE OR DELETE ON partner_settlement_items` (joining periods' status) raise exception `finalized settlement economics are immutable (spec §18.5) — use next-period adjustment items` — covering totals, items, `agreement_version_id`, `finance_payment_id`, `tenant_invoice_id`, charges, and eligible-net; only adjustment rows may change afterwards (status QUEUED→APPLIED claim), never finalized history.
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green (register complete).
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.rls.FlywayJavaMigrationsChainConsistencyTest test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave5(schema): settlement schema, complete FK register, adjustment items (C1)"`.

### Task 2: SETTLEMENT invoice kind + linkage + reconciliation support

Files:
- Create: `db/migration/V20260928_2__billing_invoice_kind_settlement.sql`, `db/migration/V20260928_3__settlement_invoice_linkage.sql`, `db/migration/V20260928_4__settlement_reconciliation_support.sql`
- Test: `partner/settlement/SettlementSupportSchemaPostgresTest.java` (Create)

Interfaces:
- Consumes: `billing_invoices.invoice_kind` (W4 `STANDARD,CREDIT`); `platform_invoice_number_sequences` (W4).
- Produces: kind `SETTLEMENT`; `partner_settlement_periods.settlement_invoice_id` (set once at INVOICED, immutable after first set); allocation-candidate view + indexes.

- [ ] Step 1: exact failing test — `invoice_kind='SETTLEMENT'` accepted, `'FOO'` rejected; `settlement_invoice_id` FK to `billing_invoices(id)`; immutability trigger blocks CHANGING a non-null `settlement_invoice_id`; the view `v_partner_settlement_allocation_candidates` exists and returns exactly the join: `finance_payments fp (fp.status='COMPLETED') → finance_invoices fi ON (fi.tenant_id=fp.tenant_id AND fi.id=fp.invoice_id) → billing_invoices bi ON bi.id = NULLIF(fi.external_reference, substring path) ... WHERE fi.external_reference = 'SCP_INVOICE:' || bi.id AND bi.invoice_kind='STANDARD' AND bi.seller_principal_id IS NOT NULL AND bi.status IN ('OPEN','PAID') AND NOT EXISTS (allocation)`; indexes `finance_payments (status, payment_date)` partial WHERE COMPLETED, `partner_settlement_items (finance_payment_id)` implicit via unique.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.partner.settlement.SettlementSupportSchemaPostgresTest test` → kind rejected / view missing (red).
- [ ] Step 3: exact minimal implementation — the three migrations exactly as specified (candidate view is READ-only; `SETTLEMENT` widen is drop+recreate additive per `V20260830_1` idiom).
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.partner.billing.InvoicePartyColumnsPostgresTest test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave5(schema): settlement invoice kind, linkage, allocation candidate view (C1)"`.

### Task 3: Arithmetic documentation migration

Files:
- Create: `db/migration/V20260928_5__settlement_arithmetic_invariants_docs.sql`, `db/migration/V20260928_6__settlement_statistics_support.sql`
- Test: covered by Task 4's calculator suite (no standalone class)

Interfaces:
- Consumes: none.
- Produces: executable documentation of §18.1 invariants + statistics indexes.

- [ ] Step 1: exact failing test — none (documentation migration; correctness owned by Task 4 tests).
- [ ] Step 2: exact command proving RED — none.
- [ ] Step 3: exact minimal implementation — `V20260928_5`: comment block stating verbatim: `eligibleNet = gross_collected − tax_portion − refunds_allocated − credit_notes_allocated; discounts already reflected in collected amount are NOT subtracted again (§18.1); only webhook/Finance-verified finance_payments COMPLETED count`; `V20260928_6`: index `partner_settlement_items (period_id, eligible_net_minor)` for reconciliation scans.
- [ ] Step 4: exact command proving GREEN — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.rls.FlywayJavaMigrationsChainConsistencyTest test` → green.
- [ ] Step 5: exact affected regression — same as Step 4.
- [ ] Step 6: exact commit — `git commit -m "wave5(docs): arithmetic invariants as migration documentation (C1)"`.

### Task 4: Eligible-net calculator (pure, §31.3 matrix)

Files:
- Create: `partner/settlement/EligibleNetCalculator.java`
- Modify: none
- Test: `partner/settlement/EligibleNetCalculatorTest.java` (Create)

Interfaces:
- Consumes: per invoice+payment collection facts.
- Produces: `{gross, tax, refund, creditNote, eligibleNet}` — `eligibleNet = gross − tax_portion − refunds − credit_notes`; NO second discount subtraction.

- [ ] Step 1: exact failing test — §31.3 matrix: VAT excluded (collected 1000 incl. 50 VAT ⇒ 950); refund reduces (950 − 100 ⇒ 850); credit note reduces; discount NOT double-subtracted (invoice 1000 with 50 discount, collected 950, VAT 0 ⇒ 950); zero-eligibility edge ⇒ 0 (item retained with 0 charge); REV C NEGATIVE-CARRYOVER matrix: corrections exceeding collected amounts produce a NEGATIVE eligible net that is preserved verbatim (collected 500, corrections −700 ⇒ eligible_net = −200, output carries the negative value and an `AUDIT_NEGATIVE_RESIDUAL` flag — NO clamping to 0 anywhere in the calculator or persistence); the negative residual deterministically reduces the NEXT period's eligible net (carry-forward semantics proven with a two-period fixture); (Rev C matrix edges) net < 0, net == 0, net > 0 after a late correction each assert the exact preserved value.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.partner.settlement.EligibleNetCalculatorTest test` → class missing (red).
- [ ] Step 3: exact minimal implementation — the pure calculator (no Spring dependencies; unit-testable).
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.partner.billing.AgreementVersionPinningPostgresTest test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave5(calc): eligible-net calculator matrix (C2)"`.

### Task 5: Settlement calculation — FULL deterministic rebuild

Files:
- Create: `partner/settlement/PartnerSettlementService.java` (calculate + rebuild core)
- Modify: none
- Test: `partner/settlement/PartnerSettlementCalculatePostgresTest.java` (Create)

Interfaces:
- Consumes: `v_partner_settlement_allocation_candidates`; `EligibleNetCalculator`; per-item `agreement_version_id` from the invoice; `platform_charge_minor = round(eligible_net_minor × version.platform_fee_percent)` per item; single-currency enforcement.
- Produces: period row (CALCULATED) + items + run record in ONE transaction.

- [ ] Step 1: exact failing test — end-to-end: seed partner-issued invoices + COMPLETED finance payments ⇒ period CALCULATED with exact per-item version math (invoice under v1 20% + v2 15% effective later ⇒ items keep their pinned %); `sanad_charge_minor` = Σ(item charges); per-item pinning preserved; period totals == Σ items.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5433/sanad SPRING_DATASOURCE_USERNAME=sanad SPRING_DATASOURCE_PASSWORD=sanad_pass mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.partner.settlement.PartnerSettlementCalculatePostgresTest test` → class missing (red).
- [ ] Step 3: exact minimal implementation — `calculate(partnerId, periodStart, periodEnd, idempotencyKey)`: lock/create period row; build candidates; compute; REPLACE all non-finalized items of the period with the fresh set (full rebuild, §18.5); write run record; mixed currency ⇒ `PERIOD_CURRENCY_MIX` and NO period row written (fail-closed).
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.subscription.billing.R0C13G06SettlementReconciliationPostgresTest test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave5(calc): full deterministic period rebuild with per-item pinning (C2)"`.

### Task 6: Replay + replace semantics (state machine core)

Files:
- Modify: `partner/settlement/PartnerSettlementService.java`
- Test: `partner/settlement/SettlementReplayReplacePostgresTest.java` (Create)

Interfaces:
- Consumes: `partner_settlement_runs` UNIQUE `(partner_id, idempotency_key)`; period row lock.
- Produces: §18.5 semantics — same key = strict no-op replay with drift detection; new key = new run + transactional replacement of the non-finalized calculation.

- [ ] Step 1: exact failing test — (a) same idempotency key twice ⇒ identical row counts + identical totals; second invocation returns the first run's result without writing (row-count invariant, replay-safe §18.4); (b) same key with tampered expectations ⇒ `SETTLEMENT_DRIFT` fail-closed; (c) NEW key after new collections ⇒ new run record AND the period/items fully replaced deterministically (old item set gone, new complete set present, totals consistent); (d) replace attempt on a FINALIZED period ⇒ 409 `PERIOD_FINALIZED`.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5433/sanad SPRING_DATASOURCE_USERNAME=sanad SPRING_DATASOURCE_PASSWORD=sanad_pass mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.partner.settlement.SettlementReplayReplacePostgresTest test` → replay/replace behaviors absent (red).
- [ ] Step 3: exact minimal implementation — replay check (run row exists + COMPLETED ⇒ recompute-and-compare or short-circuit per drift policy) + replacement path (delete non-finalized items + reinsert within the caller's transaction).
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.partner.settlement.PartnerSettlementCalculatePostgresTest test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave5(calc): replay no-op + new-key full replace + finalized guard (C2)"`.

### Task 7: Manual payment exclusion

Files:
- Create: `partner/settlement/ManualPaymentExclusionPostgresTest.java`
- Modify: none
- Test: this class

Interfaces:
- Consumes: candidate view (Task 2) — structurally excludes invoices paid ONLY via `SaasAdministrationService.markInvoicePaid` (no finance payment row).
- Produces: A5/B4 proof.

- [ ] Step 1: exact failing test — invoice with a `markInvoicePaid` write (no finance payment) is absent from candidates ⇒ base unaffected; the same invoice AFTER a webhook-verified COMPLETED payment appears exactly once.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5433/sanad SPRING_DATASOURCE_USERNAME=sanad SPRING_DATASOURCE_PASSWORD=sanad_pass mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.partner.settlement.ManualPaymentExclusionPostgresTest test` → manual row leaks into candidates (red).
- [ ] Step 3: exact minimal implementation — none (fix the view in `V20260928_4` if red).
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.partner.billing.PartnerInvoiceGuardPostgresTest test` → green (markInvoicePaid guard intact).
- [ ] Step 6: exact commit — `git commit -m "wave5(calc): manual payments structurally excluded from settlement base (C3)"`.

### Task 8: Finance collected-cash read port — `CollectedCashReadPort` (Rev C, new)

Files:
- Create: `finance/readmodel/CollectedCashReadPort.java` (Finance-owned read interface), `finance/readmodel/CollectedCashReadAdapter.java` (Finance-owned implementation)
- Modify: `partner/settlement/PartnerSettlementService.java` (candidate/eligibility reads consume the port — no direct `finance_%` SQL in `partner/**`)
- Test: `finance/readmodel/CollectedCashReadPortPostgresTest.java`, `finance/readmodel/FinanceReadBoundaryTest.java` (Create both)

Interfaces:
- Consumes: `finance_payments fp (status='COMPLETED')`, `finance_invoices fi` join, `billing_invoices bi` linkage — all inside the Finance module.
- Produces: `CollectedCashReadPort.listCollectedForPartnerScopedInvoices(partnerScopeFilter, periodWindow)` returning records with EXACTLY: `financePaymentId`, `financeInvoiceId`, `billingInvoiceId` (or the external-reference link), `tenantId`, `amountMinor`, `currencyCode`, `paymentStatus`, `paymentDate`, plus refund/correction facts (`refundMinor`, `correctionMinor` — sourced from Finance payment REFUNDED transitions and the `finance_credit_corrections` ledger); READ-ONLY (no write methods on the interface).

- [ ] Step 1: exact failing test — port returns the exact field set for a seeded collected/refunded/corrected fixture; tenant GUC honored (partner-scoped filter cannot leak foreign tenants); `FinanceReadBoundaryTest` file-content scan FAILS the build if any class under `partner/billing/**` or `partner/settlement/**` contains a direct `finance_invoices|finance_payments|finance_journal|finance_credit_corrections` table reference outside the ports (Rev C: BOTH packages guarded — the Rev B guard covered settlement only).
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.finance.readmodel.CollectedCashReadPortPostgresTest,com.sanad.platform.finance.readmodel.FinanceReadBoundaryTest test` → port missing / boundary scan fails on Task 5's candidate join ⇒ red.
- [ ] Step 3: exact minimal implementation — port + adapter (Finance module; `TenantRlsTransactionContext` GUC discipline identical to `SubscriptionFinanceAdapter`); rewire Task 5/10 candidate building through the port.
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.subscription.billing.R0C13ArchitectureBoundaryTest test` → green (extended guard intact).
- [ ] Step 6: exact commit — `git commit -m "wave5(finance): collected-cash read port + finance read boundary for partner packages (C3)"`.

### Task 9: Currency guard

Files:
- Create: `partner/settlement/CurrencyMixPostgresTest.java`
- Modify: none
- Test: this class

Interfaces:
- Consumes: candidate set with mixed `currency_code`.
- Produces: `PERIOD_CURRENCY_MIX` fail-closed (B3 — no FX).

- [ ] Step 1: exact failing test — mixed-currency candidates ⇒ service throws `PERIOD_CURRENCY_MIX`, NO period row, NO items, run row FAILED.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5433/sanad SPRING_DATASOURCE_USERNAME=sanad SPRING_DATASOURCE_PASSWORD=sanad_pass mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.partner.settlement.CurrencyMixPostgresTest test` → guard absent (red).
- [ ] Step 3: exact minimal implementation — currency check in `PartnerSettlementService.calculate` (belongs to Task 5 class; fix there if red).
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.partner.settlement.EligibleNetCalculatorTest test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave5(calc): single-currency fail-closed guard (C3)"`.

### Task 10: Concurrent calculate/finalize + approval + SANAD invoice issuance

Files:
- Modify: `partner/settlement/PartnerSettlementService.java` (submitForApproval, finalize, issueSanadPartnerInvoice)
- Create: `partner/settlement/SettlementFinalizeConcurrencyPostgresTest.java`
- Test: this class + `partner/settlement/SettlementApprovalPostgresTest.java` (Create both)

Interfaces:
- Consumes: `SELECT ... FOR UPDATE` on the period row; `SETTLEMENT.FINALIZE` (`PLATFORM-only`); PLATFORM principal snapshots (W3); `platform_invoice_number_sequences` (`SSET-yyyyMM-<seq>`); REV C — an explicit Finance PRINCIPAL-invoice port: `FinancePrincipalInvoicePort.ensurePrincipalInvoice(UUID settlementInvoiceId, UUID sellerPrincipalId, UUID buyerPrincipalId, long amountMinor, String currencyCode)` (Create `finance/principalinvoice/FinancePrincipalInvoicePort.java` + Finance-owned adapter implementation) — NEVER `SubscriptionFinancePort.ensureInvoice(tenantId, …)`, which would forge the SANAD→Partner settlement invoice as a tenant invoice with a fabricated subscription; `billing_invoices.invoice_kind='SETTLEMENT'` with `seller_principal_id` = SANAD PLATFORM principal, `buyer_principal_id` = partner principal, `tenant_id` = the canonical control-plane carrier (RLS/storage carrier ONLY — never a fabricated tenant or subscription UUID); conditional billing constraint (Task 2 of W4 `ck_billing_invoices_invoice_kind` widened in `V20260928_2` to also carry the kind-scoped party shape: STANDARD ⇒ real tenant context, SETTLEMENT ⇒ carrier tenant + both principals set).
- Produces: CALCULATED→PENDING_APPROVAL→FINALIZED→INVOICED; exactly one finalizer under concurrency; partner finalize ⇒ 403; issued invoice linked immutably (`settlement_invoice_id`); Finance mirror via the principal-invoice port; cross-entity congruence chain proven (see Step 1).

- [ ] Step 1: exact failing test — concurrency test (TransactionTemplate, two threads finalizing the same period): exactly one wins, loser 409; calculate during finalize blocks/loses cleanly (no torn state). Approval test: partner finalize ⇒ 403; platform finalize OK + audited; FINALIZED→INVOICED creates `billing_invoices` row `invoice_kind='SETTLEMENT'` with SELLER snapshot = SANAD PLATFORM principal, BUYER snapshot = partner principal, totals = `period.sanad_charge_minor`, Finance mirror via `FinancePrincipalInvoicePort.ensurePrincipalInvoice(...)` (principal-invoice linkage — no tenant/subscription fabrication), outbox `BILLING.SANAD_PARTNER_INVOICE_ISSUED.v1`, owner notification event, `settlement_invoice_id` set once and immutable thereafter; REV C cross-entity financial congruence chain asserted end-to-end on one fixture: `billing_invoices SETTLEMENT.total_minor == period.sanad_charge_minor == Σ(items.platform_charge_minor)` AND the Finance mirror's amount equals the same value AND Σ(payments collected against partner-issued tenant invoices) − tax − refunds − corrections == Σ(items.eligible_net_minor) — every hop of the chain is a stored value, and the chain test fails listing the first mismatching hop if any identity breaks. ONE Finance payment CANNOT be economically allocated into TWO FINALIZED periods: the allocation-candidate view is consumed transactionally with the item INSERT (payment rows claimed exactly once — `UNIQUE (tenant_invoice_id, finance_payment_id)` at item level + the candidate view excluding already-allocated payments), and the concurrency test issues two overlapping calculate runs on adjacent periods sharing one candidate payment ⇒ exactly one period claims it; the other run either waits (row lock) or re-reads the view after the winner commits, never double-allocating.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5433/sanad SPRING_DATASOURCE_USERNAME=sanad SPRING_DATASOURCE_PASSWORD=sanad_pass mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.partner.settlement.SettlementFinalizeConcurrencyPostgresTest,com.sanad.platform.partner.settlement.SettlementApprovalPostgresTest test` → transitions absent (red).
- [ ] Step 3: exact minimal implementation — the three service methods with row-level locking.
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.subscription.billing.R0C13ArchitectureBoundaryTest test` → green (extend its file-content guardrail to forbid `partner/settlement/**` from touching `finance_` tables outside the port — the extension ships in this task).
- [ ] Step 6: exact commit — `git commit -m "wave5(lifecycle): serialized finalize + SANAD settlement invoice issuance (C4)"`.

### Task 11: Late adjustments after FINALIZED (next-period items)

Files:
- Create: `partner/settlement/SettlementAdjustmentService.java`
- Modify: `partner/settlement/PartnerSettlementService.java` (next CALCULATED period includes signed adjustment items in its eligible-net)
- Test: `partner/settlement/LateAdjustmentPostgresTest.java` (Create)

Interfaces:
- Consumes: `partner_settlement_adjustment_items` (Task 1); finalized-period immutability.
- Produces: `recordAdjustment(partnerId, type, refs, amountMinor, reason)` — writes an adjustment item targeting the next open (non-finalized) period; a late refund after FINALIZED never rewrites the finalized period.

- [ ] Step 1: exact failing test — finalize period P1 (charge X); record LATE_REFUND −100 afterwards ⇒ P1 rows byte-identical (nothing rewritten — finalized economic values, including totals/items/`agreement_version_id`/`finance_payment_id`/`tenant_invoice_id`/charges/eligible-net, are DB-level immutable per the finalized-immutability triggers shipped in W5 Task 1 (`V20260928_1`)); a QUEUED adjustment item (target NULL) is claimed ATOMICALLY by the next calculation run — the claiming UPDATE runs inside the calculate transaction with the run id stamped (`claimed_by_run_id`, `claimed_at`) and `target_period_id` bound to the new period; two concurrent calculate runs cannot both claim the same QUEUED item (row lock ⇒ exactly one claimant); a duplicate adjustment emission with the same `idempotency_key` is a strict no-op; P2 totals reflect the claimed −100; adjustment on a period with no successor stays QUEUED with NULL target until the next window is calculated (single authoritative rule: adjustments are APPLIED only against a NON-finalized target).
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5433/sanad SPRING_DATASOURCE_USERNAME=sanad SPRING_DATASOURCE_PASSWORD=sanad_pass mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.partner.settlement.LateAdjustmentPostgresTest test` → class missing (red).
- [ ] Step 3: exact minimal implementation — the service + inclusion of signed adjustment sums in `calculate`.
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.partner.settlement.SettlementReplayReplacePostgresTest test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave5(adjustments): finalized history immutable, next-period signed items (C4)"`.

### Task 12: Reconciliation service

Files:
- Create: `partner/settlement/SettlementReconciliationService.java` (READ_ONLY)
- Modify: none
- Test: `partner/settlement/SettlementReconciliationPostgresTest.java` (Create)

Interfaces:
- Consumes: pattern of `subscription/billing/application/BillingReconciliationService.java`.
- Produces: Σ(items.eligible_net) == period.eligible_net_collected_minor; Σ(items.platform_charge) == period.sanad_charge_minor == Σ(invoice totals); every allocated payment appears exactly once; orphan report (collected-but-unallocated, allocated-but-missing payment).

- [ ] Step 1: exact failing test — reconcile green on consistent fixture; injected drift (manual UPDATE via platform context on a COPY fixture, never on FINALIZED history) ⇒ report finds the mismatch with exact column named.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.partner.settlement.SettlementReconciliationPostgresTest test` → class missing (red).
- [ ] Step 3: exact minimal implementation — the service.
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.subscription.billing.R0C13G06SettlementReconciliationPostgresTest test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave5(recon): settlement reconciliation service with orphan report (C5)"`.

### Task 13: Settlement RLS

Files:
- Create: `partner/settlement/SettlementRlsPostgresTest.java`
- Modify: none
- Test: this class

Interfaces:
- Consumes: RLS shipped with Task 1 migrations.
- Produces: partner A reads only own periods/items/runs/adjustments; SANAD settlement invoice visible to the partner as buyer via billing tables' own policies.

- [ ] Step 1: exact failing test — partner GUC isolation across all four tables; platform context sees all; wrong-context inserts blocked.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.partner.settlement.SettlementRlsPostgresTest test` → cross rows visible (red).
- [ ] Step 3: exact minimal implementation — none (fix `V20260928_1` if red).
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.partner.billing.PartnerBillingRlsPostgresTest test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave5(rls): settlement isolation proven (C5)"`.

### Task 14: Settlement controllers + events

Files:
- Create: `partner/settlement/api/ExecutiveSettlementController.java` (`/api/v1/executive/settlements` list/calculate/approve/finalize/issue-invoice/reconcile — `SETTLEMENT.VIEW` read-only list/reconcile, `SETTLEMENT.MANAGE` calculate/approve, `SETTLEMENT.FINALIZE` finalize/issue-invoice; Rev C full-chain capability consistency with the W4 seed migration, registry, tests, and spec §6.1), `partner/settlement/api/PartnerSettlementController.java` (`/api/v1/partner/settlements` read own periods/items/invoices + `current` live estimate endpoint, no writes — claim-scoped, `SETTLEMENT.VIEW` via partner self-read)
- Modify: none
- Test: `partner/settlement/SettlementControllersIT.java` (Create)

Interfaces:
- Consumes: `PartnerSettlementService`, `SettlementReconciliationService`, `PartnerClaimResolver`, `notification_events` emissions (`PARTNER_SETTLEMENT_CALCULATED`, `PARTNER_INVOICE_CREATED/ISSUED` — consumed by W6).
- Produces: the §28 settlement API categories.

- [ ] Step 1: exact failing test — executive paths authorized by `SETTLEMENT.VIEW` (list/reconcile), `SETTLEMENT.MANAGE` (calculate/approve — 403 without it), `SETTLEMENT.FINALIZE` (finalize/issue — 403 without it); partner paths claim-scoped (`?partnerId=` mismatch ⇒ 403); reconcile endpoint runs READ_ONLY.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5433/sanad SPRING_DATASOURCE_USERNAME=sanad SPRING_DATASOURCE_PASSWORD=sanad_pass mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.partner.settlement.SettlementControllersIT test` → 404 routes (red).
- [ ] Step 3: exact minimal implementation — the two controllers.
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.TenantBindingSecurityIntegrationTest test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave5(api): settlement executive + partner surfaces (C5)"`.

### Task 15: Frontend — settlement surfaces

Files:
- Create: `apps/web/lib/api/settlement-api.ts`, `apps/web/lib/api/settlement-api.test.ts`, `apps/web/app/executive/settlements/page.tsx`, `apps/web/app/executive/settlements/[id]/page.tsx`
- Modify: i18n `ar.ts`/`en.ts` (`settlement.*`, same commit)
- Test: the API test + page tests

Interfaces:
- Consumes: `/api/v1/executive/settlements/**`; the partner settlement PANEL ships as the W6 portal page (W5 delivers API + read models — sequencing recorded here deliberately).
- Produces: periods grid + calculate/finalize actions (`has("SETTLEMENT.FINALIZE")`) + per-item drill-down with agreement version and arithmetic columns + reconciliation panel; fee column renders the derived `weighted_effective_fee_percent` (read-time statistic, labeled display-only per §18.7).

- [ ] Step 1: exact failing test — `settlement-api.test.ts`: exact URLs `/api/v1/executive/settlements`, `/api/v1/executive/settlements/{id}`, `/api/v1/executive/settlements/{id}/reconcile` + typed exports.
- [ ] Step 2: exact command proving RED — `cd apps/web && npm test -- settlement` → red.
- [ ] Step 3: exact minimal implementation — client + two pages + i18n.
- [ ] Step 4: exact command proving GREEN — `cd apps/web && npm test -- settlement` → green.
- [ ] Step 5: exact affected regression — `cd apps/web && npm run typecheck && npm run lint && npm test && python3 scripts/ci/check_i18n_keys.py` → green.
- [ ] Step 6: exact commit — `git commit -m "wave5(web): settlement executive surfaces (C6)"`.

### Task 16: Wave exit evidence battery

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
  `cd apps/sanad-platform && SPRING_PROFILES_ACTIVE=pg-acceptance PG_ACCEPTANCE_JDBC_URL='jdbc:postgresql://127.0.0.1:5432/pg_acceptance?prepareThreshold=0' PG_ACCEPTANCE_USERNAME=sanad PG_ACCEPTANCE_PASSWORD=sanad_pass mvn test -B -ntp -Dsurefire.useFile=true -DfailIfNoTests=true -Dtest='CommerceOrderPostgresConcurrencyTest,RbacAccessCheckPostgresAcceptanceTest,ModuleRegistryUatPostgresAcceptanceTest'` → 31/31 (6/15/10 unchanged).
- [ ] Step 5: exact affected regression — battery IS the regression; log at `snad-evidence/evidence-<HEAD-SHA>.log`.
- [ ] Step 6: exact commit — `git commit -m "wave5(evidence): gate run @ <HEAD-SHA> (C7)"`.

## Dependencies, security, rollback

**Dependencies:** W4 (invoices, agreement versions, credit notes, sequences, finance ports). Blocks W6 (metrics). **Security/Billing:** PLATFORM-only finalize (§30; negative-tested); partner reads scoped by FORCE RLS; collected-cash truth solely webhook-verified Finance payments with manual-bypass exclusion proven by test; version pinning prevents retroactive economics; finalized history immutable with next-period adjustments; replay-safe with drift detection; no second ledger (settlement invoices use the same billing/Finance machinery). **Rollback:** flag `SANAD_SETTLEMENT_ENABLED=false` ⇒ controllers 404-guarded; periods are computed artifacts (recomputable); DDL additive; `SETTLEMENT` invoice-kind value unused when the flag is off; revert commits safe.
