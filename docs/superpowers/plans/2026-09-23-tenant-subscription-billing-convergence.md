# Tenant–Subscription–Billing Convergence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Executive tenant provisioning, access eligibility, effective-subscription resolution, subscription actions, recurring-price semantics, billing/Finance presentation, diagnostics, and dunning behavior converge on canonical backend authorities with fail-closed behavior and full governed verification.

**Architecture:** Add one derived commercial-state service around `SubscriptionResolutionService`, keep `tenant.status` distinct from subscription lifecycle, route Executive provisioning through a single commercial orchestration boundary, expose deterministic commercial actions to the web client, and build an Executive billing read model that links SCP billing projection to Finance/reconciliation without changing source-of-truth ownership. Preserve `SubscriptionCommandService` as lifecycle writer and `BillingStateService` as billing-state writer.

**Tech Stack:** Java 21 / Spring Boot / JdbcTemplate / PostgreSQL / Flyway / Maven; Next.js 16 / React 19 / TypeScript 5.9 / Vitest / Testing Library / ESLint; GitHub Actions; Render image deployment.

**Spec:** `docs/superpowers/specs/2026-09-23-tenant-subscription-billing-convergence-design.md`

## Global Constraints

- `tenant.status` remains an operational/account state and must not become a mirror of `tenant_subscriptions.status`.
- Current commercial state must resolve through `SubscriptionResolutionService.findEffectiveSubscription(UUID)`; terminal history is `CANCELLED`, `EXPIRED`, `TERMINATED`.
- `SubscriptionCommandService` remains the canonical subscription lifecycle writer.
- `BillingStateService` remains the sole runtime writer of `billing_state`.
- Finance remains the accounting source of truth; `billing_invoices` remains SCP compatibility/dunning projection.
- New Executive commercial commands fail closed on incomplete, ambiguous, or illegal state.
- No plan or plan-version creation is permitted merely to satisfy provisioning/upgrade flows; select existing ACTIVE catalog data.
- No live payment-provider activation or live payment collection is authorized by this work.
- Production data anomalies are diagnosed read-only; no automatic guessing/backfill of commercial state.
- PostgreSQL Direct is authoritative for governed DB acceptance; Docker/Testcontainers do not satisfy final DB acceptance.
- Full closure requires same-exact-head verification with backend failures=0/errors=0, PostgreSQL Direct PASS, web tests PASS, TypeScript PASS, ESLint PASS, i18n PASS, production build PASS, and required exact-head CI PASS.

## Review Focus

1. Unknown or newly introduced subscription status must fail closed rather than grant access; pin this in Task 1.
2. ACTIVE tenant with no effective subscription must never receive a login-link action; pin this in Tasks 1 and 3.
3. A latest historical terminal row must never override a valid older/newer effective row; pin parity behavior in Task 2.
4. Annual-plan UI must never present monthly equivalent as the recurring invoice amount; pin this in Tasks 6 and 8.
5. Billing rows missing optional Finance/reconciliation links must render as explicitly unlinked/unreconciled, not as paid/valid by inference; pin this in Tasks 7 and 8.

---

## File Structure Map

### New backend files

- `apps/sanad-platform/src/main/java/com/sanad/platform/subscription/commercial/TenantCommercialStateService.java` — derived tenant/subscription access and action policy.
- `apps/sanad-platform/src/main/java/com/sanad/platform/executive/service/ExecutiveTenantProvisioningService.java` — single commercial tenant provisioning orchestrator.
- `apps/sanad-platform/src/main/java/com/sanad/platform/subscription/read/ExecutiveBillingQueryService.java` — joined SCP/Finance/reconciliation billing read model.
- `apps/sanad-platform/src/main/java/com/sanad/platform/subscription/read/CommercialConsistencyDiagnosticsService.java` — read-only anomaly queries.
- `apps/sanad-platform/src/test/java/com/sanad/platform/subscription/commercial/TenantCommercialStateServiceTest.java`
- `apps/sanad-platform/src/test/java/com/sanad/platform/executive/service/ExecutiveTenantProvisioningPostgresTest.java`
- `apps/sanad-platform/src/test/java/com/sanad/platform/subscription/read/TenantDirectoryCommercialStatePostgresTest.java`
- `apps/sanad-platform/src/test/java/com/sanad/platform/subscription/read/ExecutiveBillingQueryServicePostgresTest.java`
- `apps/sanad-platform/src/test/java/com/sanad/platform/subscription/read/CommercialConsistencyDiagnosticsPostgresTest.java`
- `apps/sanad-platform/src/test/java/com/sanad/platform/admin/BillingStateConvergencePostgresTest.java`

### Existing backend files to modify

- `apps/sanad-platform/src/main/java/com/sanad/platform/executive/service/ExecutivePlatformService.java`
- `apps/sanad-platform/src/main/java/com/sanad/platform/executive/api/PlatformOperationsCommandController.java`
- `apps/sanad-platform/src/main/java/com/sanad/platform/executive/api/SaasAdministrationQueryController.java`
- `apps/sanad-platform/src/main/java/com/sanad/platform/executive/api/SaasAdministrationCommandController.java`
- `apps/sanad-platform/src/main/java/com/sanad/platform/subscription/api/ExecutiveReadController.java`
- `apps/sanad-platform/src/main/java/com/sanad/platform/subscription/read/TenantDirectoryQueryService.java`
- `apps/sanad-platform/src/main/java/com/sanad/platform/subscription/read/SubscriptionGridQueryService.java`
- `apps/sanad-platform/src/main/java/com/sanad/platform/admin/service/AdminPlatformService.java` — remove/redirect duplicate commercial provisioning behavior; do not silently preserve a second authority.
- Existing tests under `apps/sanad-platform/src/test/java/com/sanad/platform/executive/**`, `subscription/read/**`, `subscription/lifecycle/**`, and `subscription/billing/**` as required for regression/parity.

### Existing web files to modify

- `apps/web/lib/api/executive-api.ts`
- `apps/web/lib/api/scp-api.ts`
- `apps/web/app/executive/tenants/page.tsx`
- `apps/web/app/executive/tenants/tenant-management.test.tsx`
- `apps/web/app/executive/subscriptions/page.tsx`
- `apps/web/app/executive/subscriptions/[id]/page.tsx`
- `apps/web/app/executive/billing/page.tsx`
- `apps/web/app/executive/billing/billing-page.test.tsx`
- `apps/web/lib/i18n/locales/ar.ts`
- `apps/web/lib/i18n/locales/en.ts`

---

### Task 1: Canonical Tenant Commercial-State Policy

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/subscription/commercial/TenantCommercialStateService.java`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/subscription/commercial/TenantCommercialStateServiceTest.java`
- Reuse: `apps/sanad-platform/src/main/java/com/sanad/platform/subscription/lifecycle/SubscriptionResolutionService.java`
- Reuse: `apps/sanad-platform/src/main/java/com/sanad/platform/subscription/lifecycle/SubscriptionLifecycle.java`

**Interfaces:**
- Consumes: `SubscriptionResolutionService.findEffectiveSubscription(UUID)`, `findLatestHistorical(UUID)`, tenant operational status.
- Produces:
  - `TenantCommercialStateService.AccessDecision`
  - `TenantCommercialStateService.CommercialAction`
  - `TenantCommercialStateService.TenantCommercialState`
  - `TenantCommercialStateService.resolve(UUID tenantId, String tenantStatus)`

- [ ] **Step 1: Write the failing policy matrix test**

```java
@Test
void activeTenantWithoutEffectiveSubscriptionFailsClosed() {
    when(resolution.findEffectiveSubscription(TENANT)).thenReturn(Optional.empty());
    when(resolution.findLatestHistorical(TENANT)).thenReturn(Optional.empty());

    var state = service.resolve(TENANT, "ACTIVE");

    assertThat(state.accessDecision()).isEqualTo(AccessDecision.NO_EFFECTIVE_SUBSCRIPTION);
    assertThat(state.commercialAction()).isEqualTo(CommercialAction.CREATE_SUBSCRIPTION);
    assertThat(state.loginAllowed()).isFalse();
}

@Test
void unknownSubscriptionStatusFailsClosed() {
    when(resolution.findEffectiveSubscription(TENANT)).thenReturn(Optional.of(
        new EffectiveSubscription(SUB, TENANT, PLAN, "FUTURE_STATUS", "CURRENT", Instant.now())));

    var state = service.resolve(TENANT, "ACTIVE");

    assertThat(state.accessDecision()).isEqualTo(AccessDecision.BLOCKED_UNKNOWN_STATE);
    assertThat(state.loginAllowed()).isFalse();
    assertThat(state.commercialAction()).isEqualTo(CommercialAction.BLOCKED);
}
```

- [ ] **Step 2: Run the focused test and preserve RED evidence**

Run:
```bash
cd apps/sanad-platform
mvn -Dtest=TenantCommercialStateServiceTest test
```
Expected: compile/test failure because `TenantCommercialStateService` and enums do not yet exist.

- [ ] **Step 3: Implement the minimal immutable decision model**

```java
public record TenantCommercialState(
        UUID tenantId,
        String tenantStatus,
        UUID effectiveSubscriptionId,
        String effectiveSubscriptionStatus,
        String billingState,
        AccessDecision accessDecision,
        CommercialAction commercialAction,
        String anomalyCode,
        boolean loginAllowed) {}
```

Use an explicit switch over normalized known lifecycle states. At minimum:
- tenant not `ACTIVE` -> `TENANT_NOT_ACTIVE`, login false;
- ACTIVE + no effective + no history -> `NO_EFFECTIVE_SUBSCRIPTION`, `CREATE_SUBSCRIPTION`;
- ACTIVE + latest `EXPIRED` and no effective -> `SUBSCRIPTION_TERMINAL`, `CREATE_SUCCESSOR`;
- ACTIVE + latest `CANCELLED` and no effective -> `SUBSCRIPTION_TERMINAL`, `RESUME`;
- ACTIVE + latest `TERMINATED` and no effective -> `SUBSCRIPTION_TERMINAL`, `BLOCKED`;
- effective `ACTIVE` -> access allowed, `UPGRADE`, login true;
- effective `TRIAL`/`TRIALING` -> access allowed under existing product policy, `UPGRADE`, login true;
- effective `PAST_DUE`/`GRACE_PERIOD` -> explicit restricted decision, login policy covered by test;
- effective `PAUSED`/`SUSPENDED`/pending states -> login false;
- unknown -> fail closed.

- [ ] **Step 4: Add the full decision table tests**

Cover tenant statuses `PENDING/TRIAL/ACTIVE/PAST_DUE/SUSPENDED/CANCELLED/ARCHIVED`, all lifecycle statuses in `SubscriptionLifecycle.STATUSES`, terminal-history continuation behavior, null billing state, and unknown values.

- [ ] **Step 5: Run focused tests GREEN**

```bash
mvn -Dtest=TenantCommercialStateServiceTest test
```
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add apps/sanad-platform/src/main/java/com/sanad/platform/subscription/commercial/TenantCommercialStateService.java \
        apps/sanad-platform/src/test/java/com/sanad/platform/subscription/commercial/TenantCommercialStateServiceTest.java
git commit -m "feat: add canonical tenant commercial state policy"
```

---

### Task 2: Tenant Directory Uses Effective Subscription Semantics

**Files:**
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/subscription/read/TenantDirectoryQueryService.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/subscription/api/ExecutiveReadController.java`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/subscription/read/TenantDirectoryCommercialStatePostgresTest.java`
- Modify: `apps/sanad-platform/src/test/java/com/sanad/platform/subscription/read/ExecutiveReadModelsTest.java`

**Interfaces:**
- Consumes: Task 1 `TenantCommercialStateService` or a SQL read model whose effective predicate is parity-tested against `SubscriptionResolutionService`.
- Produces `TenantRow` fields: `effectiveSubscriptionId`, `effectiveSubscriptionStatus`, `accessDecision`, `commercialAction`, `anomalyCode`, while retaining `subscriptionCount` for history.

- [ ] **Step 1: Write RED test reproducing latest-history bug**

Seed a tenant with:
- terminal historical row created later;
- one non-terminal effective row permitted by the multiplicity schema.

Assert the directory returns the effective row status, not simply the latest historical status.

- [ ] **Step 2: Run RED**

```bash
mvn -Dtest=TenantDirectoryCommercialStatePostgresTest test
```
Expected: FAIL because current query uses `ORDER BY s.created_at DESC, s.id DESC LIMIT 1` without the effective predicate.

- [ ] **Step 3: Change the read model**

Replace the current generic `subscription_status` subquery with an effective-subscription projection equivalent to:

```sql
SELECT s.status
FROM tenant_subscriptions s
WHERE s.tenant_id = t.id
  AND s.status NOT IN ('CANCELLED','EXPIRED','TERMINATED')
```

Do not add `LIMIT 1` as an ambiguity mask. If the schema invariant is violated, diagnostics/tests must expose it rather than silently selecting one row.

- [ ] **Step 4: Add parity tests**

For each seeded tenant, compare directory `effectiveSubscriptionStatus` against `SubscriptionResolutionService.findEffectiveSubscription(tenantId)`.

- [ ] **Step 5: Run read-model suites**

```bash
mvn -Dtest=TenantDirectoryCommercialStatePostgresTest,ExecutiveReadModelsTest test
```
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add apps/sanad-platform/src/main/java/com/sanad/platform/subscription/read/TenantDirectoryQueryService.java \
        apps/sanad-platform/src/main/java/com/sanad/platform/subscription/api/ExecutiveReadController.java \
        apps/sanad-platform/src/test/java/com/sanad/platform/subscription/read/TenantDirectoryCommercialStatePostgresTest.java \
        apps/sanad-platform/src/test/java/com/sanad/platform/subscription/read/ExecutiveReadModelsTest.java
git commit -m "fix: resolve effective subscription in tenant directory"
```

---

### Task 3: Enforce Login-Link Eligibility on the Backend

**Files:**
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/executive/service/ExecutivePlatformService.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/executive/api/PlatformOperationsCommandController.java`
- Modify: `apps/sanad-platform/src/test/java/com/sanad/platform/executive/service/ExecutivePlatformLoginLinkTest.java`
- Modify: `apps/sanad-platform/src/test/java/com/sanad/platform/executive/api/PlatformOperationsTenantManagementContractTest.java`

**Interfaces:**
- Consumes: `TenantCommercialStateService.resolve(tenantId, tenant.status())`.
- Produces: deterministic HTTP conflict for commercially ineligible login-link actions; existing successful audit event remains for eligible tenants.

- [ ] **Step 1: Add failing tests**

```java
@Test
void rejectsActiveTenantWithNoEffectiveSubscription() {
    // tenant row is ACTIVE; commercialState.loginAllowed() == false
    assertThatThrownBy(() -> service.recordTenantLoginLinkEvent(TENANT_ID, "OPEN", authentication))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(409));
    verifyNoInteractions(audit);
}
```

Also cover `CANCELLED`, `EXPIRED`, `TERMINATED`, `PAUSED`, `SUSPENDED`, unknown status, and eligible `ACTIVE`/trial policy.

- [ ] **Step 2: Run RED**

```bash
mvn -Dtest=ExecutivePlatformLoginLinkTest,PlatformOperationsTenantManagementContractTest test
```
Expected: current ACTIVE-only backend guard permits at least the no-subscription case.

- [ ] **Step 3: Inject and enforce commercial state**

Replace the existing `if (!"ACTIVE".equals(tenant.status()))`-only policy with commercial-state resolution. Keep `EXECUTIVE_MANAGE` and `ControlPlaneAccessGuard` untouched.

- [ ] **Step 4: Run GREEN**

```bash
mvn -Dtest=ExecutivePlatformLoginLinkTest,PlatformOperationsTenantManagementContractTest test
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/sanad-platform/src/main/java/com/sanad/platform/executive/service/ExecutivePlatformService.java \
        apps/sanad-platform/src/main/java/com/sanad/platform/executive/api/PlatformOperationsCommandController.java \
        apps/sanad-platform/src/test/java/com/sanad/platform/executive/service/ExecutivePlatformLoginLinkTest.java \
        apps/sanad-platform/src/test/java/com/sanad/platform/executive/api/PlatformOperationsTenantManagementContractTest.java
git commit -m "fix: gate tenant login links by commercial state"
```

---

### Task 4: Single Executive Commercial Provisioning Orchestrator

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/executive/service/ExecutiveTenantProvisioningService.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/executive/service/ExecutivePlatformService.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/admin/service/AdminPlatformService.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/executive/api/PlatformOperationsCommandController.java`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/executive/service/ExecutiveTenantProvisioningPostgresTest.java`

**Interfaces:**
- Consumes: `RegistrationProvisioner`, `SaasAdministrationService.listPlans()`, `SaasAdministrationService.createSubscription(CreateSubscriptionRequest, Authentication)`, Task 1 commercial state.
- Produces: `ProvisionedExecutiveTenant(TenantResponse tenant, TenantCommercialState commercialState)` or existing response-compatible projection if API compatibility requires retaining `TenantResponse`.

- [ ] **Step 1: Write the RED atomicity tests**

Required cases:
- plan by `planId`;
- plan by `planCode`;
- documented default `STARTER` only when request omits both and current contract still requires defaulting;
- inactive/archived/missing plan -> 4xx and no persisted partial tenant;
- subscription creation failure -> whole Executive creation transaction rolls back;
- valid request -> exactly one tenant and exactly one effective subscription;
- no implicit creation of catalog plan/version;
- `billingCycle` and `seatQuantity` are honored.

- [ ] **Step 2: Run RED on PostgreSQL Direct harness**

```bash
mvn -Dtest=ExecutiveTenantProvisioningPostgresTest test
```
Expected: failure because `ExecutivePlatformService.createTenant` currently provisions tenant profile only.

- [ ] **Step 3: Implement plan resolution and orchestration**

Use a single `@Transactional` application service. Resolve existing plan before provisioning where possible. Build the existing subscription DTO explicitly:

```java
new CreateSubscriptionRequest(
    tenantId,
    resolvedPlanId,
    request.billingCycle() == null ? "MONTHLY" : request.billingCycle(),
    request.seatQuantity() == null ? 1 : request.seatQuantity(),
    request.trialDays())
```

Do not catch-and-log subscription creation errors in the Executive commercial path.

- [ ] **Step 4: Remove duplicate commercial authority**

Make `AdminPlatformService` either delegate to the orchestrator for the same commercial contract or retain only explicitly non-commercial tenant-only onboarding. Delete the silent `subscription auto-creation failed` success behavior for any route classified as Executive commercial provisioning.

- [ ] **Step 5: Run focused GREEN**

```bash
mvn -Dtest=ExecutiveTenantProvisioningPostgresTest,PlatformOperationsTenantManagementContractTest test
```
Expected: PASS.

- [ ] **Step 6: Run registration/subscription regressions**

```bash
mvn -Dtest='*Registration*Test,*SaasAdministration*Test,*ExpiredSuccessor*Test,*ConcurrentSuccessorCreationPostgresTest' test
```
Expected: PASS with no new duplicate-subscription creation path.

- [ ] **Step 7: Commit**

```bash
git add apps/sanad-platform/src/main/java/com/sanad/platform/executive/service/ExecutiveTenantProvisioningService.java \
        apps/sanad-platform/src/main/java/com/sanad/platform/executive/service/ExecutivePlatformService.java \
        apps/sanad-platform/src/main/java/com/sanad/platform/admin/service/AdminPlatformService.java \
        apps/sanad-platform/src/main/java/com/sanad/platform/executive/api/PlatformOperationsCommandController.java \
        apps/sanad-platform/src/test/java/com/sanad/platform/executive/service/ExecutiveTenantProvisioningPostgresTest.java
git commit -m "fix: make executive tenant provisioning commercially atomic"
```

---

### Task 5: Deterministic Create / Resume / Successor / Upgrade Actions

**Files:**
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/executive/api/SaasAdministrationCommandController.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/admin/service/SaasAdministrationService.java` only if a thin governed continuation method is missing; do not bypass existing successor guards.
- Modify/add tests under `apps/sanad-platform/src/test/java/com/sanad/platform/subscription/lifecycle/`.
- Modify: `apps/web/lib/api/executive-api.ts`

**Interfaces:**
- Consumes: Task 1 `CommercialAction`, existing `createSubscription`, `resumeSubscription`, successor guard, change-plan flow.
- Produces frontend methods:
  - `createSubscription(body)`
  - existing `resumeSubscription(id)`
  - route/action metadata from tenant directory for `UPGRADE`, `RESUME`, `CREATE_SUBSCRIPTION`, `CREATE_SUCCESSOR`, `BLOCKED`.

- [ ] **Step 1: Add RED backend route/action tests**

Assert:
- `CREATE_SUBSCRIPTION` succeeds only with no effective subscription and legal history;
- `CREATE_SUCCESSOR` succeeds only under existing EXPIRED successor contract;
- CANCELLED uses resume-only semantics;
- TERMINATED remains blocked if repository contract says no successor;
- an effective subscription returns 409 for duplicate create.

- [ ] **Step 2: Run RED/GREEN around existing lifecycle guards**

```bash
mvn -Dtest='*ExpiredSuccessor*Test,*ExpiredContinuation*Test,*ConcurrentSuccessorCreationPostgresTest,*Lifecycle*Test' test
```
Implement only the missing controller/application glue; preserve the established lifecycle model.

- [ ] **Step 3: Add typed web API**

In `executive-api.ts` add a complete request type matching backend fields rather than the current minimal tenant-create shape:

```ts
export interface CreateSubscriptionRequest {
  tenantId: string;
  planId: string;
  billingCycle: "MONTHLY" | "ANNUAL";
  seatQuantity: number;
  trialDays?: number | null;
}
```

- [ ] **Step 4: Commit**

```bash
git add apps/sanad-platform/src/main/java/com/sanad/platform/executive/api/SaasAdministrationCommandController.java \
        apps/sanad-platform/src/main/java/com/sanad/platform/admin/service/SaasAdministrationService.java \
        apps/sanad-platform/src/test/java/com/sanad/platform/subscription/lifecycle \
        apps/web/lib/api/executive-api.ts
git commit -m "feat: expose deterministic tenant subscription continuation actions"
```

---

### Task 6: Correct Recurring Price Semantics

**Files:**
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/subscription/read/SubscriptionGridQueryService.java`
- Modify: `apps/sanad-platform/src/test/java/com/sanad/platform/subscription/read/ExecutiveReadModelsTest.java`
- Modify: `apps/web/lib/api/scp-api.ts`
- Modify: `apps/web/app/executive/subscriptions/page.tsx`
- Add/modify subscription page tests in the same directory.

**Interfaces:**
- Produces `recurringAmountMinor` and optional `monthlyEquivalentMinor`; deprecate ambiguous `monthlyPriceMinor` in the v2 read model.

- [ ] **Step 1: Write failing annual-price test**

For annual price `120000` minor units and 2 seats assert:
- `recurringAmountMinor == 240000`;
- `billingCycle == ANNUAL`;
- `monthlyEquivalentMinor == 20000` under an explicit rounding policy.

- [ ] **Step 2: Run RED**

```bash
mvn -Dtest=ExecutiveReadModelsTest test
```
Expected: current query exposes annual amount divided by 12 in `monthly_price_minor`.

- [ ] **Step 3: Change SQL/read DTO**

Use:

```sql
CASE s.billing_cycle
  WHEN 'ANNUAL' THEN COALESCE(pv.annual_price_minor, p.annual_price_minor) * s.seat_quantity
  ELSE COALESCE(pv.monthly_price_minor, p.monthly_price_minor) * s.seat_quantity
END AS recurring_amount_minor
```

Compute monthly equivalent separately and never use it as invoice/recurring amount.

- [ ] **Step 4: Update web labels/types/tests**

Render `Annual recurring amount` / Arabic equivalent for ANNUAL and `Monthly recurring amount` for MONTHLY. If monthly equivalent is shown, label it explicitly.

- [ ] **Step 5: Run backend + web focused tests**

```bash
cd apps/sanad-platform && mvn -Dtest=ExecutiveReadModelsTest test
cd ../web && npm test -- --runInBand apps/web/app/executive/subscriptions
```
If Vitest rejects `--runInBand`, run `npm test -- apps/web/app/executive/subscriptions`.

- [ ] **Step 6: Commit**

```bash
git add apps/sanad-platform/src/main/java/com/sanad/platform/subscription/read/SubscriptionGridQueryService.java \
        apps/sanad-platform/src/test/java/com/sanad/platform/subscription/read/ExecutiveReadModelsTest.java \
        apps/web/lib/api/scp-api.ts apps/web/app/executive/subscriptions
git commit -m "fix: separate recurring and monthly-equivalent subscription pricing"
```

---

### Task 7: Executive Billing Read Model With Finance/Reconciliation Context

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/subscription/read/ExecutiveBillingQueryService.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/executive/api/SaasAdministrationQueryController.java`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/subscription/read/ExecutiveBillingQueryServicePostgresTest.java`
- Reuse R0C13 structures under `apps/sanad-platform/src/main/java/com/sanad/platform/subscription/billing/**` and Finance repositories; do not mutate them merely for presentation.

**Interfaces:**
- Produces a read DTO with SCP invoice projection fields plus optional Finance/reconciliation linkage.
- GET route remains `EXECUTIVE_VIEW` guarded and tenant-filterable.

- [ ] **Step 1: Write RED read-model tests**

Seed cases:
- linked projection + Finance invoice + successful settlement + reconciled;
- projection with no Finance link;
- partially paid invoice;
- currency mismatch;
- tenant A request cannot expose tenant B row;
- missing reconciliation evidence remains `UNRECONCILED/UNKNOWN`, never inferred as success.

- [ ] **Step 2: Run RED**

```bash
mvn -Dtest=ExecutiveBillingQueryServicePostgresTest test
```
Expected: class/endpoint does not yet exist.

- [ ] **Step 3: Implement read-only query service**

Return fields including:
`subtotalMinor`, `creditAppliedMinor`, `taxMinor`, `totalMinor`, `amountPaidMinor`, derived `outstandingMinor = max(totalMinor - amountPaidMinor, 0)`, `paymentReference`, `financeLinkId`, `financeInvoiceId`, finance state, payment/settlement state, reconciliation classification/status, and `sourceOfTruth = "FINANCE"` for accounting state while clearly labeling SCP projection fields.

Do not write Finance, settlement, or billing tables from this service.

- [ ] **Step 4: Add controller route and authorization tests**

Prefer a v2/read-model endpoint if changing the legacy response would break clients. Preserve `EXECUTIVE_VIEW` and `ControlPlaneAccessGuard`.

- [ ] **Step 5: Run R0C13 regression suites**

```bash
mvn -Dtest='R0C13G03FinanceIntegrationPostgresTest,R0C13G05WebhookPostgresTest,R0C13G06SettlementReconciliationPostgresTest,R0C13G07DisabledReconciliationPostgresTest,R0C13G07CorrectivePostgresTest,ExecutiveBillingQueryServicePostgresTest' test
```
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add apps/sanad-platform/src/main/java/com/sanad/platform/subscription/read/ExecutiveBillingQueryService.java \
        apps/sanad-platform/src/main/java/com/sanad/platform/executive/api/SaasAdministrationQueryController.java \
        apps/sanad-platform/src/test/java/com/sanad/platform/subscription/read/ExecutiveBillingQueryServicePostgresTest.java
git commit -m "feat: expose finance-aware executive billing read model"
```

---

### Task 8: Web Tenant, Subscription, and Billing Journeys

**Files:**
- Modify: `apps/web/lib/api/executive-api.ts`
- Modify: `apps/web/lib/api/scp-api.ts`
- Modify: `apps/web/app/executive/tenants/page.tsx`
- Modify: `apps/web/app/executive/tenants/tenant-management.test.tsx`
- Modify: `apps/web/app/executive/subscriptions/page.tsx`
- Modify: `apps/web/app/executive/subscriptions/[id]/page.tsx`
- Modify: `apps/web/app/executive/billing/page.tsx`
- Modify: `apps/web/app/executive/billing/billing-page.test.tsx`
- Modify: `apps/web/lib/i18n/locales/ar.ts`
- Modify: `apps/web/lib/i18n/locales/en.ts`

**Interfaces:**
- Consumes backend-derived `accessDecision`, `commercialAction`, effective subscription id/status, corrected recurring amount, billing read model.
- The client no longer derives access with raw checks such as `subscriptionStatus !== "TERMINATED"`.

- [ ] **Step 1: Add RED tenant-management tests**

Required assertions:
- ACTIVE + `NO_EFFECTIVE_SUBSCRIPTION` does not show login controls;
- `UPGRADE` opens current subscription flow;
- `RESUME` invokes canonical resume;
- `CREATE_SUBSCRIPTION` presents existing ACTIVE plans only;
- `CREATE_SUCCESSOR` follows backend-authorized flow;
- `BLOCKED` renders a reason and no mutation button;
- archived/suspended restrictions remain intact.

- [ ] **Step 2: Add RED billing tests**

Assert visible distinction among:
- SCP projection status;
- Finance/accounting state;
- total / paid / outstanding;
- tax / credits;
- due/paid timestamps;
- payment reference;
- reconciliation state;
- missing Finance link shown as `Not linked`/Arabic equivalent, not hidden or inferred.

- [ ] **Step 3: Run RED**

```bash
cd apps/web
npm test -- app/executive/tenants/tenant-management.test.tsx app/executive/billing/billing-page.test.tsx
```
Expected: failures for new derived-state behavior.

- [ ] **Step 4: Implement API types and UI**

Define enums/unions in TypeScript mirroring backend strings exactly, e.g.:

```ts
export type CommercialAction =
  | "UPGRADE" | "RESUME" | "CREATE_SUBSCRIPTION" | "CREATE_SUCCESSOR" | "NONE" | "BLOCKED";
```

Do not infer a fallback action for unknown strings; render blocked/error state.

- [ ] **Step 5: Update Arabic/English translations together**

Add keys for access decisions, commercial actions, recurring amount labels, Finance source-of-truth notice, outstanding amount, reconciliation, and missing-link states. Run the repository's i18n parity check if present; otherwise use the existing test/validation command that currently guards locale parity.

- [ ] **Step 6: Run web focused GREEN**

```bash
npm test -- app/executive/tenants/tenant-management.test.tsx app/executive/billing/billing-page.test.tsx
npm run typecheck
npm run lint
```
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add apps/web/lib/api/executive-api.ts apps/web/lib/api/scp-api.ts \
        apps/web/app/executive/tenants apps/web/app/executive/subscriptions apps/web/app/executive/billing \
        apps/web/lib/i18n/locales/ar.ts apps/web/lib/i18n/locales/en.ts
git commit -m "feat: converge executive commercial and billing journeys"
```

---

### Task 9: Read-Only Commercial Consistency Diagnostics

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/subscription/read/CommercialConsistencyDiagnosticsService.java`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/subscription/read/CommercialConsistencyDiagnosticsPostgresTest.java`
- Optionally expose through an existing `EXECUTIVE_VIEW`/audit diagnostics controller only if repository conventions already provide a safe admin diagnostics surface; otherwise keep service/test evidence internal for this change.

**Interfaces:**
- Produces immutable `CommercialAnomaly(code, tenantId, subscriptionId, invoiceId, evidence)` records.
- Performs SELECT only.

- [ ] **Step 1: Write RED tests for each required anomaly class**

Pin at least:
`ACTIVE_WITHOUT_EFFECTIVE_SUBSCRIPTION`, `ACTIVE_WITH_TERMINAL_HISTORY_ONLY`, `MULTIPLE_EFFECTIVE_SUBSCRIPTIONS`, `DUNNING_STATE_MISMATCH`, `FINANCE_LINK_MISSING`, `SETTLEMENT_RECONCILIATION_MISMATCH`, `CURRENCY_MISMATCH`, `INVALID_EFFECTIVE_PLAN_REFERENCE`.

- [ ] **Step 2: Run RED**

```bash
mvn -Dtest=CommercialConsistencyDiagnosticsPostgresTest test
```
Expected: class missing.

- [ ] **Step 3: Implement SELECT-only diagnostics**

No UPDATE/INSERT/DELETE. Return evidence fields needed for human remediation; do not invent a plan or status repair.

- [ ] **Step 4: Add a source scan test**

Assert the diagnostics service contains no write SQL tokens (`UPDATE`, `INSERT`, `DELETE`) outside comments/test fixtures.

- [ ] **Step 5: Run GREEN and commit**

```bash
mvn -Dtest=CommercialConsistencyDiagnosticsPostgresTest test
git add apps/sanad-platform/src/main/java/com/sanad/platform/subscription/read/CommercialConsistencyDiagnosticsService.java \
        apps/sanad-platform/src/test/java/com/sanad/platform/subscription/read/CommercialConsistencyDiagnosticsPostgresTest.java
git commit -m "feat: add read-only commercial consistency diagnostics"
```

---

### Task 10: Dunning and Lifecycle Convergence Verification

**Files:**
- Modify only if tests prove a defect: `apps/sanad-platform/src/main/java/com/sanad/platform/admin/service/BillingStateService.java`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/admin/BillingStateConvergencePostgresTest.java`
- Reuse existing billing/lifecycle tests.

**Interfaces:**
- `BillingStateService` remains sole billing-state writer.
- Lifecycle transitions continue through `SubscriptionCommandService.applyCanonicalTransition`.

- [ ] **Step 1: Add PostgreSQL Direct convergence tests**

Pin:
- CURRENT + invoice overdue > 3 days -> PAST_DUE and canonical lifecycle status matches;
- PAST_DUE + overdue > 7 days -> SUSPENDED;
- payment clearing overdue -> CURRENT/ACTIVE where legal;
- terminal rows excluded;
- historical invoice cannot dunn successor;
- repeated dunning run is idempotent;
- one tenant SQL failure does not abort the next tenant's isolated cycle.

- [ ] **Step 2: Run tests before modifying production code**

```bash
mvn -Dtest=BillingStateConvergencePostgresTest,BillingStateServiceIntegrationTest test
```
If all pass, make no runtime code change. If one fails, fix only the proven defect and rerun the same test to GREEN.

- [ ] **Step 3: Verify deployment configuration non-destructively**

Read current deployment metadata/log evidence for `SANAD_DUNNING_ENABLED`; do not change it in this task. Record whether scheduler execution is enabled, disabled, or unverifiable from the available deployment tooling.

- [ ] **Step 4: Commit tests and any proven minimal fix**

```bash
git add apps/sanad-platform/src/test/java/com/sanad/platform/admin/BillingStateConvergencePostgresTest.java \
        apps/sanad-platform/src/main/java/com/sanad/platform/admin/service/BillingStateService.java
git commit -m "test: certify billing dunning lifecycle convergence"
```

---

### Task 11: PostgreSQL Direct Security, Atomicity, Concurrency, and Finance Acceptance

**Files:**
- Add/update governed PostgreSQL Direct tests under:
  - `apps/sanad-platform/src/test/java/com/sanad/platform/executive/**`
  - `apps/sanad-platform/src/test/java/com/sanad/platform/subscription/**`
  - `apps/sanad-platform/src/test/java/com/sanad/platform/security/**`
- Do not add Testcontainers as acceptance infrastructure.

**Interfaces:**
- Uses repository-approved direct PostgreSQL environment variables/connection setup.
- Produces test evidence on one exact SHA.

- [ ] **Step 1: Run Flyway validation/migration against the governed direct PostgreSQL test database**

Use the repository's existing direct-PostgreSQL Maven profile/environment. Expected: Flyway cleanly reaches head with no checksum or out-of-order errors.

- [ ] **Step 2: Run tenant isolation / RLS suites**

Include cross-tenant read/write denial for any changed/new query route and existing owner/control-plane exceptions only where explicitly authorized.

- [ ] **Step 3: Run atomic provisioning rollback test**

Force subscription creation failure inside the transaction and assert zero durable tenant/user/org/subscription commercial partial state for the Executive path.

- [ ] **Step 4: Run concurrency test**

Race two initial/successor subscription creates for the same tenant. Expected: exactly one legal effective result; loser receives deterministic conflict; no duplicate effective row.

- [ ] **Step 5: Run billing/Finance/reconciliation acceptance tests**

```bash
mvn -Dtest='ExecutiveTenantProvisioningPostgresTest,TenantDirectoryCommercialStatePostgresTest,ExecutiveBillingQueryServicePostgresTest,CommercialConsistencyDiagnosticsPostgresTest,BillingStateConvergencePostgresTest,R0C13G03FinanceIntegrationPostgresTest,R0C13G05WebhookPostgresTest,R0C13G06SettlementReconciliationPostgresTest,R0C13G07DisabledReconciliationPostgresTest,R0C13G07CorrectivePostgresTest,*Tenant*Rls*Test,*Isolation*Test' test
```
Expected: `FAILURES=0`, `ERRORS=0`.

- [ ] **Step 6: Commit acceptance tests**

```bash
git add apps/sanad-platform/src/test/java
git commit -m "test: add postgres direct commercial convergence acceptance"
```

---

### Task 12: Full Backend and Web Regression Gate

**Files:**
- No new feature scope. Fix only regressions caused by Tasks 1–11.

- [ ] **Step 1: Full backend Maven suite**

```bash
cd apps/sanad-platform
mvn test
```
Acceptance: Surefire/Failsafe reports show `Failures: 0, Errors: 0`. Any skipped test that is part of this plan's required acceptance is a FAIL unless a pre-existing governed exception explicitly allows it.

- [ ] **Step 2: Full web tests**

```bash
cd ../web
npm test
```
Acceptance: all Vitest suites pass.

- [ ] **Step 3: TypeScript**

```bash
npm run typecheck
```
Acceptance: 0 errors.

- [ ] **Step 4: ESLint**

```bash
npm run lint
```
Acceptance: 0 policy-blocking errors/warnings.

- [ ] **Step 5: Integrity and identity checks**

```bash
npm run validate:integrity
npm run brand:check
```
Acceptance: PASS.

- [ ] **Step 6: Production web build**

```bash
npm run build
```
Acceptance: Next.js production build PASS.

- [ ] **Step 7: Inspect git diff for scope discipline**

```bash
git status --short
git diff --check
git diff --stat
```
Acceptance: no unrelated changes, no whitespace errors, no secrets, no generated noise.

- [ ] **Step 8: Commit any regression-only fixes and record exact head SHA**

```bash
git rev-parse HEAD
```
Use this SHA for every following CI/review statement.

---

### Task 13: Protected Review, Exact-Head CI, Merge, and Production Verification

**Files:**
- PR metadata/evidence only unless CI reveals a code defect.

**Interfaces:**
- Input: exact implementation head SHA from Task 12.
- Output: protected merged main SHA and post-merge evidence.

- [ ] **Step 1: Open PR from `fix/tenant-subscription-billing-convergence` to the protected base branch**

PR body must list:
- root causes fixed;
- exact RED evidence;
- exact GREEN commands/results;
- PostgreSQL Direct evidence;
- security/RLS invariants;
- R0C13 Finance boundary preserved;
- live payment collection still not authorized;
- production-data repair explicitly not performed.

- [ ] **Step 2: Wait for exact-head CI and independent review**

Do not merge while any required check is pending, skipped unexpectedly, failed, or bound to an older SHA.

- [ ] **Step 3: Re-run affected suites after every code-changing review fix**

A code change invalidates prior exact-head claims; record the new SHA and repeat required CI.

- [ ] **Step 4: Merge through the protected approved path only**

Record the merge SHA. Do not force-push main or bypass required checks.

- [ ] **Step 5: Verify exact-main release artifact**

Confirm deployed backend image corresponds to the approved merged SHA. Render service auto-deploy is currently off; deployment must follow the repository's authorized production release workflow rather than an ad-hoc manual image change.

- [ ] **Step 6: Post-merge verification**

Run required Post-Merge A–F / repository-defined gates on the same release baseline.

- [ ] **Step 7: Authenticated production smoke**

Verify, without inventing data repairs:
- tenant list commercial state;
- ACTIVE tenant with no effective subscription has no login access;
- valid effective subscriber can use permitted login flow;
- subscription action routes are deterministic;
- annual recurring amount is correctly labeled;
- billing view distinguishes projection vs Finance truth;
- no cross-tenant exposure.

- [ ] **Step 8: Run read-only production diagnostics if the governed external PostgreSQL connection is available**

Record anomaly counts and evidence. Do not mutate production commercial state as part of diagnostics.

- [ ] **Step 9: Closure criterion**

Declare this convergence work COMPLETE only if:
- required backend tests: failures=0/errors=0;
- PostgreSQL Direct governed acceptance: PASS;
- web tests/typecheck/lint/build: PASS;
- exact-head CI: PASS;
- protected merge: complete;
- exact-main/post-merge gates: PASS;
- authenticated production smoke: PASS;
- no unresolved P0/P1 anomaly introduced by the change.

If any item is incomplete, report the precise blocker and keep the work OPEN.

---

## Plan Self-Review Record

- Spec coverage: provisioning, commercial state, login gating, effective read model, continuation actions, price semantics, billing/Finance read model, diagnostics, dunning, security/RLS, PostgreSQL Direct, web, CI/release, rollback boundaries are all assigned to tasks.
- Placeholder scan: no `TBD`, `TODO`, or unspecified implementation steps remain.
- Type consistency: backend `TenantCommercialState` drives `AccessDecision`/`CommercialAction`; the web consumes those exact concepts rather than deriving raw lifecycle policy.
- Review Focus coverage: unknown statuses Task 1; no-subscription login Tasks 1/3/8; historical/effective parity Task 2; annual price Tasks 6/8; missing Finance link Tasks 7/8.
- Scope discipline: no payment-provider activation, no Commerce payment rewrite, no automatic production data repair, and no plan catalog creation are included.
