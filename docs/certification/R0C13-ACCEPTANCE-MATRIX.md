# R0C13 — Revenue/Billing Integration Closure — Acceptance Matrix

- **Date:** 2026-09-12
- **Starting production baseline:** `16509abed344ce5d6635eb3660512e3e9011584b`
- **Implementation repository baseline (R13-S0 re-anchor):** `15d50fa03b748a9190b9ee7380d746f91d55b34e`
- **Pre-R0C13 repository Flyway head:** `20260910.1`
- **Issue:** #1017
- **Design:** `docs/superpowers/specs/2026-09-11-r0c13-revenue-billing-integration-closure-design.md`
- **Plan:** `docs/superpowers/plans/2026-09-11-r0c13-revenue-billing-integration-closure-implementation.md`
- **Current stage:** R13-G06 FINAL CERTIFICATION — DIRECT EVIDENCE BACKFILLED
- **Implementation:** G01-G06 IMPLEMENTED; G02-G06 DIRECT EVIDENCE RECORDED; G07 NOT STARTED

## Acceptance vocabulary

- **PASS** — directly proven by authoritative evidence.
- **FAIL** — acceptance predicate disproven.
- **BLOCKED** — required evidence cannot currently be obtained.
- **NOT_STARTED** — implementation/evidence not yet attempted.
- **N/A_WITH_PROOF** — explicitly non-applicable with recorded evidence.

No inferred PASS is permitted.

## A. Scope and architecture acceptance

| ID | Acceptance predicate | Required evidence | Gate | Initial |
|---|---|---|---|---|
| R13-AC-001 | Starting production baseline is `16509abed...`; implementation repository baseline is re-anchored to `15d50fa0...`; no R0C13 implementation precedes protected scope approval | branch/compare/commit evidence | S0/G01 | PASS |
| R13-AC-002 | Finance remains source of truth for accounting invoices/payments/ledger | `R0C13ArchitectureBoundaryTest.r0c13BillingMustNotWriteFinanceTablesDirectly` + Finance-owned `SubscriptionFinanceAdapter`; G03 suite 6/0/0/0 and Finance regression 11/0/0/0; exact head `9388c140f42844ae4949565d4dbb6286a53c7b46`; CI `34623561685`; Maven `103343100356`; PostgreSQL Direct `103343100450`; CRM `103343100072`; Surefire `10273874809` | G03 | PASS |
| R13-AC-003 | No parallel GL/accounting model introduced | G02 schema foundation + `R0C13ArchitectureBoundaryTest.r0c13FinanceAdapterMustNotWriteJournalOrLedgerTables`; exact head `9388c140f42844ae4949565d4dbb6286a53c7b46`; CI `34623561685`; Maven `103343100356`; PostgreSQL Direct `103343100450`; CRM `103343100072`; Surefire `10273874809` | G03 | PASS |
| R13-AC-004 | `billing_invoices` remains compatible with existing dunning | `R0C13G06SettlementReconciliationPostgresTest.providerFailureLeavesInvoiceOpenAndDunningOwnedStateUnchanged` + `R0C13ArchitectureBoundaryTest.g06DunningCadenceAndGraceSemanticsMustRemainUnchanged`; exact tested head `a7b637318425f771bae71fdddeb64accd5678313`; CI `34662026186`; Maven `103466325831`; PostgreSQL Direct `103466325979`; CRM `103466325898`; Surefire `10288467878`; aggregate 3403 / 0F / 0E / 31S | G03/G06 | PASS |
| R13-AC-005 | `BillingStateService` remains sole billing-state authority | `R0C13ArchitectureBoundaryTest.g06BillingAndLifecycleWritersMustRemainUnique` + `BillingStateServiceIntegrationTest` 8/0/0/0; exact tested head `a7b637318425f771bae71fdddeb64accd5678313`; CI `34662026186`; Maven `103466325831`; PostgreSQL Direct `103466325979`; CRM `103466325898`; Surefire `10288467878`; aggregate 3403 / 0F / 0E / 31S | G06 | PASS |
| R13-AC-006 | `SubscriptionCommandService` remains lifecycle single writer | `R0C13ArchitectureBoundaryTest.g06BillingAndLifecycleWritersMustRemainUnique` + `LifecycleSingleWriterPostgresTest` 19/0/0/0; exact tested head `a7b637318425f771bae71fdddeb64accd5678313`; CI `34662026186`; Maven `103466325831`; PostgreSQL Direct `103466325979`; CRM `103466325898`; Surefire `10288467878`; aggregate 3403 / 0F / 0E / 31S | G06 | PASS |
| R13-AC-007 | Commerce `PaymentGatewayPort` contract remains unchanged | API diff + commerce tests | G04/G08 | NOT_STARTED |
| R13-AC-008 | SaaS billing provider contract uses integer minor units | `R0C13G04BillingProviderContractTest.testAdapterUsesMinorUnitsAndIdempotentReferences` + `R0C13ArchitectureBoundaryTest.providerDomainMustNotUseBigDecimalForChargeOrRefundMoney`; exact head `9388c140f42844ae4949565d4dbb6286a53c7b46`; CI `34623561685`; Maven `103343100356`; PostgreSQL Direct `103343100450`; CRM `103343100072`; Surefire `10273874809` | G04 | PASS |
| R13-AC-009 | R0C13 implementation is additive/backward-compatible | API/schema diff | G08 | NOT_STARTED |
| R13-AC-010 | LIVE payment collection is outside automatic engineering closure | config/governance evidence | G04/G11 | NOT_STARTED |

## B. Database / tenancy / migration acceptance

| ID | Acceptance predicate | Required evidence | Gate | Initial |
|---|---|---|---|---|
| R13-DB-001 | All new tenant-scoped tables have `tenant_id NOT NULL` | `R0C13G02SchemaPostgresTest.allG02TablesAreTenantScopedAndForceRls`; exact head `9388c140f42844ae4949565d4dbb6286a53c7b46`; CI `34623561685`; Maven `103343100356`; PostgreSQL Direct `103343100450`; CRM `103343100072`; Surefire `10273874809` | G02 | PASS |
| R13-DB-002 | New tenant tables ENABLE and FORCE RLS | `R0C13G02SchemaPostgresTest.allG02TablesAreTenantScopedAndForceRls`; exact head `9388c140f42844ae4949565d4dbb6286a53c7b46`; CI `34623561685`; Maven `103343100356`; PostgreSQL Direct `103343100450`; CRM `103343100072`; Surefire `10273874809` | G02 | PASS |
| R13-DB-003 | own-tenant access succeeds | `R0C13G02SchemaPostgresTest.ownTenantCanInsertProviderBinding`; exact head `9388c140f42844ae4949565d4dbb6286a53c7b46`; CI `34623561685`; Maven `103343100356`; PostgreSQL Direct `103343100450`; CRM `103343100072`; Surefire `10273874809` | G02 | PASS |
| R13-DB-004 | cross-tenant read denied | `R0C13G02SchemaPostgresTest.wrongTenantCannotReadOrWriteAnotherTenantsRow`; exact head `9388c140f42844ae4949565d4dbb6286a53c7b46`; CI `34623561685`; Maven `103343100356`; PostgreSQL Direct `103343100450`; CRM `103343100072`; Surefire `10273874809` | G02 | PASS |
| R13-DB-005 | cross-tenant write denied | `R0C13G02SchemaPostgresTest.wrongTenantCannotReadOrWriteAnotherTenantsRow`; SQLSTATE 42501; exact head `9388c140f42844ae4949565d4dbb6286a53c7b46`; CI `34623561685`; Maven `103343100356`; PostgreSQL Direct `103343100450`; CRM `103343100072`; Surefire `10273874809` | G02 | PASS |
| R13-DB-006 | no-tenant context fails closed | `R0C13G02SchemaPostgresTest.noTenantContextFailsClosedForReadsAndWrites`; zero visible rows + write SQLSTATE 42501; exact head `9388c140f42844ae4949565d4dbb6286a53c7b46`; CI `34623561685`; Maven `103343100356`; PostgreSQL Direct `103343100450`; CRM `103343100072`; Surefire `10273874809` | G02 | PASS |
| R13-DB-007 | provider event id uniqueness is DB-enforced | `R0C13G02SchemaPostgresTest.providerEventAndOutboxIdempotencyAreDatabaseEnforced`; CI `34613965153`, Maven job `103311137936`, artifact `10270828975` | G02/G05 | PASS |
| R13-DB-008 | idempotency key uniqueness is DB-enforced | `R0C13G02SchemaPostgresTest.providerEventAndOutboxIdempotencyAreDatabaseEnforced`; CI `34613965153`, Maven job `103311137936`, artifact `10270828975` | G02/G05 | PASS |
| R13-DB-009 | same-tenant FK prevents mismatched linkage | `R0C13G03FinanceIntegrationPostgresTest.sameTenantForeignKeyRejectsCrossTenantFinanceInvoice` + `crossTenantBillingInvoiceCannotBeLinked`; exact head `9388c140f42844ae4949565d4dbb6286a53c7b46`; CI `34623561685`; Maven `103343100356`; PostgreSQL Direct `103343100450`; CRM `103343100072`; Surefire `10273874809` | G02/G03 | PASS |
| R13-DB-010 | Flyway fresh chain succeeds without repair/out-of-order | canonical PG Direct evidence | G02/G08 | NOT_STARTED |
| R13-DB-011 | destructive DDL against R0C12 billing/finance tables = 0 | `R0C13ArchitectureBoundaryTest.r0c13FoundationMigrationMustNotDestructivelyAlterLegacyBillingOrFinance`; exact head `9388c140f42844ae4949565d4dbb6286a53c7b46`; CI `34623561685`; Maven `103343100356`; PostgreSQL Direct `103343100450`; CRM `103343100072`; Surefire `10273874809` | G02 | PASS |
| R13-DB-012 | cardholder/secret columns = 0 | `R0C13G02SchemaPostgresTest.g02SchemaContainsNoCardholderOrSecretColumns`; exact head `9388c140f42844ae4949565d4dbb6286a53c7b46`; CI `34623561685`; Maven `103343100356`; PostgreSQL Direct `103343100450`; CRM `103343100072`; Surefire `10273874809` | G02/G09 | PASS |

## C. Finance integration acceptance

| ID | Acceptance predicate | Required evidence | Gate | Initial |
|---|---|---|---|---|
| R13-FIN-001 | one billing invoice maps to at most one Finance invoice | DB unique linkage + `R0C13G03FinanceIntegrationPostgresTest.replayCreatesExactlyOneFinanceInvoiceAndOneLine`; exact head `9388c140f42844ae4949565d4dbb6286a53c7b46`; CI `34623561685`; Maven `103343100356`; PostgreSQL Direct `103343100450`; CRM `103343100072`; Surefire `10273874809` | G03 | PASS |
| R13-FIN-002 | integration replay creates no duplicate Finance invoice | `R0C13G03FinanceIntegrationPostgresTest.replayCreatesExactlyOneFinanceInvoiceAndOneLine`; exact head `9388c140f42844ae4949565d4dbb6286a53c7b46`; CI `34623561685`; Maven `103343100356`; PostgreSQL Direct `103343100450`; CRM `103343100072`; Surefire `10273874809` | G03 | PASS |
| R13-FIN-003 | settlement replay creates no duplicate Finance payment | `R0C13G03FinanceIntegrationPostgresTest.settlementReplayCreatesExactlyOneCompletedFinancePayment`; exact head `9388c140f42844ae4949565d4dbb6286a53c7b46`; CI `34623561685`; Maven `103343100356`; PostgreSQL Direct `103343100450`; CRM `103343100072`; Surefire `10273874809` | G03 | PASS |
| R13-FIN-004 | amount mismatch fails closed | `R0C13G06SettlementReconciliationPostgresTest.amountAndCurrencyMismatchFailClosedBeforeFinanceOrLifecycleMutation`; exact tested head `a7b637318425f771bae71fdddeb64accd5678313`; CI `34662026186`; Maven `103466325831`; PostgreSQL Direct `103466325979`; CRM `103466325898`; Surefire `10288467878`; aggregate 3403 / 0F / 0E / 31S | G03/G06 | PASS |
| R13-FIN-005 | currency mismatch fails closed | `R0C13G06SettlementReconciliationPostgresTest.amountAndCurrencyMismatchFailClosedBeforeFinanceOrLifecycleMutation`; exact tested head `a7b637318425f771bae71fdddeb64accd5678313`; CI `34662026186`; Maven `103466325831`; PostgreSQL Direct `103466325979`; CRM `103466325898`; Surefire `10288467878`; aggregate 3403 / 0F / 0E / 31S | G03/G06 | PASS |
| R13-FIN-006 | cross-tenant invoice/payment link fails | `R0C13G03FinanceIntegrationPostgresTest.crossTenantBillingInvoiceCannotBeLinked` + `sameTenantForeignKeyRejectsCrossTenantFinanceInvoice`; exact head `9388c140f42844ae4949565d4dbb6286a53c7b46`; CI `34623561685`; Maven `103343100356`; PostgreSQL Direct `103343100450`; CRM `103343100072`; Surefire `10273874809` | G03 | PASS |
| R13-FIN-007 | subscription package does not write Finance journal tables directly | `R0C13ArchitectureBoundaryTest.r0c13BillingMustNotWriteFinanceTablesDirectly` + `r0c13FinanceAdapterMustNotWriteJournalOrLedgerTables`; exact head `9388c140f42844ae4949565d4dbb6286a53c7b46`; CI `34623561685`; Maven `103343100356`; PostgreSQL Direct `103343100450`; CRM `103343100072`; Surefire `10273874809` | G03 | PASS |
| R13-FIN-008 | Finance regression suite remains green | FinanceModuleIntegrationTest + full suite | G03/G08 | NOT_STARTED |
| R13-FIN-009 | provider settlement is reflected in authoritative Finance payment before lifecycle recovery | `R0C13G06SettlementReconciliationPostgresTest.providerSuccessSettlesFinanceThenProjectionThenCanonicalLifecycle` + `R0C13G06RequiredContractsRedTest.settlementMustConvergeFinanceBeforeCanonicalBillingRecovery`; exact tested head `a7b637318425f771bae71fdddeb64accd5678313`; CI `34662026186`; Maven `103466325831`; PostgreSQL Direct `103466325979`; CRM `103466325898`; Surefire `10288467878`; aggregate 3403 / 0F / 0E / 31S | G06 | PASS |

## D. Provider integration acceptance

| ID | Acceptance predicate | Required evidence | Gate | Initial |
|---|---|---|---|---|
| R13-PAY-001 | provider contract is domain-neutral | `BillingPaymentProvider` + `R0C13ArchitectureBoundaryTest.r0c13BillingMustNotReuseCommercePaymentGatewayPort`; exact head `9388c140f42844ae4949565d4dbb6286a53c7b46`; CI `34623561685`; Maven `103343100356`; PostgreSQL Direct `103343100450`; CRM `103343100072`; Surefire `10273874809` | G04 | PASS |
| R13-PAY-002 | DISABLED is production-safe default | `R0C13G04BillingProviderContractTest.providerModeGuardDefaultsDisabledAndRejectsTestInProdAndAllLive` + architecture config guard; exact head `9388c140f42844ae4949565d4dbb6286a53c7b46`; CI `34623561685`; Maven `103343100356`; PostgreSQL Direct `103343100450`; CRM `103343100072`; Surefire `10273874809` | G04 | PASS |
| R13-PAY-003 | DISABLED refuses create/verify/refund | `R0C13G04BillingProviderContractTest.disabledAdapterRefusesEveryProviderOperation`; exact head `9388c140f42844ae4949565d4dbb6286a53c7b46`; CI `34623561685`; Maven `103343100356`; PostgreSQL Direct `103343100450`; CRM `103343100072`; Surefire `10273874809` | G04 | PASS |
| R13-PAY-004 | TEST requires explicit configuration | `R0C13G04BillingProviderContractTest.disabledAndTestBeansRequireExplicitModeProperty` + mode-guard test; exact head `9388c140f42844ae4949565d4dbb6286a53c7b46`; CI `34623561685`; Maven `103343100356`; PostgreSQL Direct `103343100450`; CRM `103343100072`; Surefire `10273874809` | G04 | PASS |
| R13-PAY-005 | LIVE cannot start without separate authority/config gate | `R0C13G04BillingProviderContractTest.providerModeGuardDefaultsDisabledAndRejectsTestInProdAndAllLive` + `R0C13ArchitectureBoundaryTest.r0c13LiveProviderModeMustFailClosedAtStartup`; exact head `9388c140f42844ae4949565d4dbb6286a53c7b46`; CI `34623561685`; Maven `103343100356`; PostgreSQL Direct `103343100450`; CRM `103343100072`; Surefire `10273874809` | G04/G11 | PASS |
| R13-PAY-006 | no simulated provider activates by default | Spring context + production config scan | G04/G08 | NOT_STARTED |
| R13-PAY-007 | provider secret is never logged or returned | log/API security tests | G09 | NOT_STARTED |
| R13-PAY-008 | raw PAN/CVC/card data storage = 0 | schema/source scan | G09 | NOT_STARTED |

## E. Webhook / event acceptance

| ID | Acceptance predicate | Required evidence | Gate | Initial |
|---|---|---|---|---|
| R13-WH-001 | invalid signature causes zero domain mutation | `R0C13G05WebhookPostgresTest.invalidSignatureIsHttpRejectedWithZeroSideEffects`; 9-test G05 suite PASS on `b61e7907...` | G05 | PASS |
| R13-WH-002 | valid signed event is accepted exactly once | `verifiedWebhookResolvesTenantOnlyFromStoredProviderBinding` + `concurrentDuplicateWebhooksCommitExactlyOnce`; CI `34613965153` | G05 | PASS |
| R13-WH-003 | duplicate event is side-effect-free | `duplicateWebhookIsSideEffectFreeAndReplayMismatchFailsClosed` + six-way `concurrentDuplicateWebhooksCommitExactlyOnce`; artifact `10270828975` | G05 | PASS |
| R13-WH-004 | tenant identity resolves from trusted binding, not body input | `verifiedWebhookResolvesTenantOnlyFromStoredProviderBinding` + `verifiedProviderResolutionPolicyIsSelectOnlyAndFailClosedWithoutWebhookContext` | G05 | PASS |
| R13-WH-005 | unknown provider customer/reference is rejected | `signedUnknownProviderReferenceIsRejectedWithZeroSideEffects` | G05 | PASS |
| R13-WH-006 | processing failure remains retryable | `outboxFailureRollsBackInboxAndAuditThenRetrySucceeds` | G05 | PASS |
| R13-WH-007 | required mutation + audit + outbox is atomic | `outboxFailureRollsBackInboxAndAuditThenRetrySucceeds`; rollback proves inbox/audit/outbox transactionality | G05 | PASS |
| R13-WH-008 | billing outbox has deterministic typed/versioned events | `billingOutboxContractIsDeterministicTypedAndVersioned` | G05 | PASS |
| R13-WH-009 | no secret/raw sensitive provider payload in evidence | `rawPayloadCardAndSecretFieldsAreNeverPersisted` + architecture guard + Surefire artifact `10270828975` sentinel scan: 0 hits for PAN/CVC/secret sentinels | G05/G09 | PASS |


## E.1 G05 exact-head certification evidence

- **Certified code head:** `b61e7907f66cb1d1e51341cac53df94d9bf4f21e`
- **Protected main at certification run:** `a244d02aacd1789214e0e0de3cc6ffc936b5db93`
- **CI run:** `34613965153` — SUCCESS
- **Compile Diagnostics:** `34613965021` — SUCCESS
- **Maven Test Suite job:** `103311137936` — SUCCESS
- **PostgreSQL Acceptance job:** `103311137636` — SUCCESS, host-native PostgreSQL
- **CRM Integration job:** `103311138058` — SUCCESS
- **Surefire artifact:** `10270828975`
- **Surefire aggregate:** 3326 tests / 0 failures / 0 errors / 31 skipped
- **R0C13G05WebhookPostgresTest:** 9 / 0 / 0 / 0
- **R0C13ArchitectureBoundaryTest:** 16 / 0 / 0 / 0
- **Sensitive evidence sentinel scan:** 0 hits for `PAN_SHOULD_NOT_PERSIST`, `CVC_SHOULD_NOT_PERSIST`, and `SENSITIVE_SENTINEL_SHOULD_NOT_PERSIST`
- **Main drift after the certification run:** one Workflow release-authorization evidence file only; re-anchored by `600ed98c22c52ef57dbe600a64239b49e0083b5d` with no Billing/Finance/Commerce/migration overlap.


## E.2 G02-G04 exact-head evidence normalization

The repository-wide audit found that G02/G03/G04 had successful implementation/test evidence but their
acceptance rows had not been carried into this ledger. The rows marked PASS above were backfilled
**only** where the predicate is directly proven on the exact normalization head.

- **Normalization code head:** `9388c140f42844ae4949565d4dbb6286a53c7b46`
- **Protected main:** `81f5ec0a2e13a5eb12204439e00a85da25b842fd`
- **Compile Diagnostics:** `34623561479` — SUCCESS
- **CI:** `34623561685` — SUCCESS
- **Maven Test Suite:** `103343100356` — SUCCESS
- **PostgreSQL Acceptance:** `103343100450` — SUCCESS, host-native PostgreSQL
- **CRM Integration:** `103343100072` — SUCCESS
- **Surefire artifact:** `10273874809`
- **Aggregate:** 3328 tests / 0 failures / 0 errors / 31 skipped
- **R0C13G02SchemaPostgresTest:** 8 / 0 / 0 / 0
- **R0C13G03FinanceIntegrationPostgresTest:** 6 / 0 / 0 / 0
- **R0C13G04BillingProviderContractTest:** 6 / 0 / 0 / 0
- **R0C13G05WebhookPostgresTest:** 9 / 0 / 0 / 0
- **R0C13ArchitectureBoundaryTest:** 17 / 0 / 0 / 0
- **FinanceModuleIntegrationTest:** 11 / 0 / 0 / 0
- **Sensitive G05 sentinel scan:** 0 hits.
- **Not promoted here:** predicates whose complete acceptance evidence belongs to G06/G07/G08/G09/G10/G11 remain `NOT_STARTED`.

## F. Lifecycle / dunning / reconciliation acceptance

| ID | Acceptance predicate | Required evidence | Gate | Initial |
|---|---|---|---|---|
| R13-LC-001 | provider code never writes subscription status directly | `R0C13ArchitectureBoundaryTest.r0c13BillingMustNotWriteSubscriptionLifecycleOrBillingStateDirectly` + `g06BillingAndLifecycleWritersMustRemainUnique`; exact tested head `a7b637318425f771bae71fdddeb64accd5678313`; CI `34662026186`; Maven `103466325831`; PostgreSQL Direct `103466325979`; CRM `103466325898`; Surefire `10288467878`; aggregate 3403 / 0F / 0E / 31S | G06 | PASS |
| R13-LC-002 | provider code never writes billing_state directly | `R0C13ArchitectureBoundaryTest.r0c13BillingMustNotWriteSubscriptionLifecycleOrBillingStateDirectly` + `g06BillingAndLifecycleWritersMustRemainUnique`; exact tested head `a7b637318425f771bae71fdddeb64accd5678313`; CI `34662026186`; Maven `103466325831`; PostgreSQL Direct `103466325979`; CRM `103466325898`; Surefire `10288467878`; aggregate 3403 / 0F / 0E / 31S | G06 | PASS |
| R13-LC-003 | successful settlement can recover valid PAST_DUE/SUSPENDED through canonical path | G06 `providerSuccessSettlesFinanceThenProjectionThenCanonicalLifecycle` proves PAST_DUE recovery; `BillingStateServiceIntegrationTest.evaluateAndTransition_recoversToCurrentWhenAllInvoicesPaid` + `SubscriptionLifecycleTest` PAYMENT_RECEIVED SUSPENDED→ACTIVE prove suspended canonical recovery; exact tested head `a7b637318425f771bae71fdddeb64accd5678313`; CI `34662026186`; Maven `103466325831`; PostgreSQL Direct `103466325979`; CRM `103466325898`; Surefire `10288467878`; aggregate 3403 / 0F / 0E / 31S | G06 | PASS |
| R13-LC-004 | terminal subscription cannot be resurrected by late payment | `R0C13G06SettlementReconciliationPostgresTest.lateSuccessfulPaymentCannotResurrectTerminalSubscription`; exact tested head `a7b637318425f771bae71fdddeb64accd5678313`; CI `34662026186`; Maven `103466325831`; PostgreSQL Direct `103466325979`; CRM `103466325898`; Surefire `10288467878`; aggregate 3403 / 0F / 0E / 31S | G06 | PASS |
| R13-LC-005 | historical subscription invoice cannot dunn/recover successor | `R0C13G06SettlementReconciliationPostgresTest.historicalSubscriptionPaymentCannotDunnOrRecoverSuccessor`; exact tested head `a7b637318425f771bae71fdddeb64accd5678313`; CI `34662026186`; Maven `103466325831`; PostgreSQL Direct `103466325979`; CRM `103466325898`; Surefire `10288467878`; aggregate 3403 / 0F / 0E / 31S | G06/G08 | PASS |
| R13-LC-006 | existing dunning cadence/grace semantics unchanged | `R0C13ArchitectureBoundaryTest.g06DunningCadenceAndGraceSemanticsMustRemainUnchanged` + `BillingStateServiceIntegrationTest` 8/0/0/0; exact tested head `a7b637318425f771bae71fdddeb64accd5678313`; CI `34662026186`; Maven `103466325831`; PostgreSQL Direct `103466325979`; CRM `103466325898`; Surefire `10288467878`; aggregate 3403 / 0F / 0E / 31S | G06 | PASS |
| R13-LC-007 | reconciliation detects amount mismatch | `R0C13G06SettlementReconciliationPostgresTest.reconciliationDetectsAmountCurrencyAndMissingProviderReference`; exact tested head `a7b637318425f771bae71fdddeb64accd5678313`; CI `34662026186`; Maven `103466325831`; PostgreSQL Direct `103466325979`; CRM `103466325898`; Surefire `10288467878`; aggregate 3403 / 0F / 0E / 31S | G06 | PASS |
| R13-LC-008 | reconciliation detects currency mismatch | `R0C13G06SettlementReconciliationPostgresTest.reconciliationDetectsAmountCurrencyAndMissingProviderReference`; exact tested head `a7b637318425f771bae71fdddeb64accd5678313`; CI `34662026186`; Maven `103466325831`; PostgreSQL Direct `103466325979`; CRM `103466325898`; Surefire `10288467878`; aggregate 3403 / 0F / 0E / 31S | G06 | PASS |
| R13-LC-009 | reconciliation detects missing Finance/provider linkage | `reconciliationDetectsMissingFinanceAndIsReadOnlyReplaySafe` + `reconciliationDetectsAmountCurrencyAndMissingProviderReference`; exact tested head `a7b637318425f771bae71fdddeb64accd5678313`; CI `34662026186`; Maven `103466325831`; PostgreSQL Direct `103466325979`; CRM `103466325898`; Surefire `10288467878`; aggregate 3403 / 0F / 0E / 31S | G06 | PASS |
| R13-LC-010 | reconciliation is read-only by default | `R0C13G06SettlementReconciliationPostgresTest.reconciliationDetectsMissingFinanceAndIsReadOnlyReplaySafe` + `R0C13G06RequiredContractsRedTest.reconciliationMustBeReadOnlyOutsideItsEvidenceTables`; exact tested head `a7b637318425f771bae71fdddeb64accd5678313`; CI `34662026186`; Maven `103466325831`; PostgreSQL Direct `103466325979`; CRM `103466325898`; Surefire `10288467878`; aggregate 3403 / 0F / 0E / 31S | G06 | PASS |
| R13-LC-011 | repair action requires explicit capability + audit | Deferred by approved G06 design: G06 exposes no repair API/action; capability + audit acceptance belongs to G07 | G06/G07 | NOT_STARTED |


## F.1 G06 exact-head implementation evidence

G06 implementation was proven on exact tested head
`a7b637318425f771bae71fdddeb64accd5678313` before the final documentation/main re-anchor.

- **CI:** `34662026186` — SUCCESS
- **Compile Diagnostics:** `34662025960` — SUCCESS
- **Maven:** `103466325831` — SUCCESS
- **PostgreSQL Direct:** `103466325979` — SUCCESS
- **CRM Integration:** `103466325898` — SUCCESS
- **Surefire artifact:** `10288467878`
- **Aggregate:** 3403 tests / 0 failures / 0 errors / 31 skipped
- **R0C13G06SettlementReconciliationPostgresTest:** 10 / 0 / 0 / 0
- **R0C13G06RequiredContractsRedTest:** 4 / 0 / 0 / 0
- **R0C13ArchitectureBoundaryTest:** 19 / 0 / 0 / 0
- **BillingStateServiceIntegrationTest:** 8 / 0 / 0 / 0
- **LifecycleSingleWriterPostgresTest:** 19 / 0 / 0 / 0
- **SubscriptionLifecycleTest:** 43 / 0 / 0 / 0
- **Sensitive sentinel leakage:** 0
- **G06 rows promoted here:** 18 direct predicates.
- **Not promoted:** `R13-LC-011` remains `NOT_STARTED` because repair capability + audit is a G07 surface and no repair action/API is introduced by G06.

The final certification head additionally incorporates protected-main commit
`56827d76b3383c17e81ed545dbff2cc51f4cf7cb`, whose only drift file is the
unrelated HR test `HrApiV2AssignmentContractTest.java`. Final G06 closure still
requires exact-head CI success on the combined certification commit; no PASS is
inferred from this parent evidence alone.

## G. RBAC / API / UI acceptance

| ID | Acceptance predicate | Required evidence | Gate | Initial |
|---|---|---|---|---|
| R13-SEC-001 | BILLING.READ is deny-by-default | auth tests | G07 | NOT_STARTED |
| R13-SEC-002 | BILLING.MANAGE is required for billing mutations | auth tests | G07 | NOT_STARTED |
| R13-SEC-003 | BILLING.RECONCILE protects repair commands | auth tests | G07 | NOT_STARTED |
| R13-SEC-004 | BILLING.REFUND is separate from generic manage | auth tests | G07 | NOT_STARTED |
| R13-SEC-005 | BILLING.PROVIDER_ADMIN protects provider diagnostics/admin | auth tests | G07 | NOT_STARTED |
| R13-SEC-006 | existing EXECUTIVE_BILLING callers remain compatible | regression tests | G07/G08 | NOT_STARTED |
| R13-SEC-007 | cross-tenant API access denied | API + PG tests | G07 | NOT_STARTED |
| R13-SEC-008 | webhook path has signature auth, not tenant JWT dependency | `R0C13ArchitectureBoundaryTest.webhookIngressMustBeJwtFreeButSignatureGuarded` + invalid-signature HTTP test | G05/G07 | PASS |
| R13-WEB-001 | all new strings have Arabic/English parity | i18n gate | G07 | NOT_STARTED |
| R13-WEB-002 | SDS/logo/brand governance passes | web CI | G07/G08 | NOT_STARTED |
| R13-WEB-003 | no card/payment-secret UI fields exist | source/UI tests | G07/G09 | NOT_STARTED |
| R13-WEB-004 | mutating UI controls follow exact backend capability model | component/API tests | G07 | NOT_STARTED |

## H. Concurrency / failure-injection acceptance

| ID | Acceptance predicate | Required evidence | Gate | Initial |
|---|---|---|---|---|
| R13-FI-001 | concurrent duplicate webhooks yield one settlement | `R0C13G06SettlementReconciliationPostgresTest.concurrentDuplicateSuccessWebhookCreatesExactlyOneSettlement` (6 callers → one Finance settlement); exact tested head `a7b637318425f771bae71fdddeb64accd5678313`; CI `34662026186`; Maven `103466325831`; PostgreSQL Direct `103466325979`; CRM `103466325898`; Surefire `10288467878`; aggregate 3403 / 0F / 0E / 31S | G06/G09 | PASS |
| R13-FI-002 | webhook/reconciliation race converges deterministically | `R0C13G06SettlementReconciliationPostgresTest.webhookAndReconciliationRaceConvergesDeterministicallyToMatched`; exact tested head `a7b637318425f771bae71fdddeb64accd5678313`; CI `34662026186`; Maven `103466325831`; PostgreSQL Direct `103466325979`; CRM `103466325898`; Surefire `10288467878`; aggregate 3403 / 0F / 0E / 31S | G06/G09 | PASS |
| R13-FI-003 | Finance failure after provider success does not falsely activate subscription | failure injection | G09 | NOT_STARTED |
| R13-FI-004 | outbox failure rolls back required local mutation | failure injection | G09 | NOT_STARTED |
| R13-FI-005 | audit failure rolls back required privileged mutation | failure injection | G09 | NOT_STARTED |
| R13-FI-006 | provider timeout produces retryable state, not paid state | failure injection | G09 | NOT_STARTED |
| R13-FI-007 | unauthorized refund has zero provider/domain side effect | security test | G07/G09 | NOT_STARTED |
| R13-FI-008 | LIVE mode without authority fails closed | startup/config failure test | G04/G09 | NOT_STARTED |

## I. Regression / CI acceptance

| ID | Acceptance predicate | Required evidence | Gate | Initial |
|---|---|---|---|---|
| R13-CI-001 | full Maven suite passes on exact candidate SHA | canonical CI | G08 | NOT_STARTED |
| R13-CI-002 | PostgreSQL Direct acceptance passes host-native | CI logs/evidence | G08 | NOT_STARTED |
| R13-CI-003 | R0C12 regression passes | dedicated matrix | G08 | NOT_STARTED |
| R13-CI-004 | Finance regression passes | test evidence | G08 | NOT_STARTED |
| R13-CI-005 | Commerce production-safe payment regression passes | test evidence | G08 | NOT_STARTED |
| R13-CI-006 | Web lint/typecheck/tests/build pass | Web CI | G08 | NOT_STARTED |
| R13-CI-007 | security/workflow/secret scans pass | CI | G09 | NOT_STARTED |
| R13-CI-008 | unexplained skips = 0 | test reconciliation | G08 | NOT_STARTED |
| R13-CI-009 | immutable evidence manifest binds exact SHA | artifact | G09/G10 | NOT_STARTED |

## J. Protected release / production-safe closure acceptance

| ID | Acceptance predicate | Required evidence | Gate | Initial |
|---|---|---|---|---|
| R13-REL-001 | implementation PR exact head independently APPROVED | GitHub review | G10 | NOT_STARTED |
| R13-REL-002 | required exact-head checks all green | GitHub checks | G10 | NOT_STARTED |
| R13-REL-003 | PMV success on merged exact main | PMV manifest | G10/G11 | NOT_STARTED |
| R13-REL-004 | immutable exact-SHA image/provenance exists | artifact/OCI evidence | G10/G11 | NOT_STARTED |
| R13-REL-005 | production deployment exact SHA/image binding passes | release evidence | G11 | NOT_STARTED |
| R13-REL-006 | production provider mode = DISABLED | sanitized runtime evidence | G11 | NOT_STARTED |
| R13-REL-007 | no TEST/simulated provider active in production | runtime evidence | G11 | NOT_STARTED |
| R13-REL-008 | unsigned webhook rejected in production | safe smoke | G11 | NOT_STARTED |
| R13-REL-009 | health/readiness/Flyway/security/R0C12 smoke pass | release evidence | G11 | NOT_STARTED |
| R13-REL-010 | a live charge cannot be created in R0C13 engineering closure | controlled negative smoke | G11 | NOT_STARTED |
| R13-REL-011 | rollback/deactivation path is defined and executable | runbook/workflow evidence | G11 | NOT_STARTED |
| R13-REL-012 | no baseline drift at closure | main/Render recheck | G11/S12 | NOT_STARTED |

## K. Final closure

R0C13 engineering closure requires every mandatory item above to be PASS or N/A_WITH_PROOF and:

```text
R13-G01 = PASS
R13-G02 = PASS
R13-G03 = PASS
R13-G04 = PASS
R13-G05 = PASS
R13-G06 = PASS
R13-G07 = PASS
R13-G08 = PASS
R13-G09 = PASS
R13-G10 = PASS
R13-G11 = PASS

R0C13_ENGINEERING = CLOSED_PRODUCTION_VERIFIED
LIVE_PAYMENT_COLLECTION = NOT_ACTIVATED
LIVE_PAYMENT_AUTHORITY = SEPARATE_GATE
```

Any live-payment activation requires a new explicit human decision and cannot be inferred from R0C13 engineering closure.
