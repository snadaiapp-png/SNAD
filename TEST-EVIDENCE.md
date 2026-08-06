# TEST EVIDENCE

**Audit Date:** 2026-08-03
**HEAD SHA:** `1356b902e11da10384cad00e537369c672ee6752`

---

## Java Backend Tests

### Summary

| Category | Files | @Test Methods | @Disabled | Status |
|----------|-------|---------------|-----------|--------|
| Java Backend (Total) | 109 | 579 | 0 | ✅ ALL ACTIVE |
| Contract Tests | 15 | — | 0 | ✅ ALL ACTIVE |
| PostgresTest (Integration) | 39 | — | 0 | ✅ ALL ACTIVE |
| IntegrationTest | 22 | — | 0 | ✅ ALL ACTIVE |
| Architecture Tests | 2 | — | 0 | ✅ ALL ACTIVE |
| Intelligence Tests | 15 | — | 0 | ✅ ALL ACTIVE |
| Ownership Tests | 15 | — | 0 | ✅ ALL ACTIVE |
| Party Tests | 15 | — | 0 | ✅ ALL ACTIVE |
| Web Tests | 13 | — | 0 | ✅ ALL ACTIVE |

---

### G1-Specific Test Files (4 files, 22 methods)

| # | File | Methods | Testcontainers | @Disabled |
|---|------|---------|----------------|-----------|
| 1 | `CrmG1TenantIsolationPostgresTest.java` | 2 | postgres:16-alpine | 0 |
| 2 | `Crm008bFoundationAcceptanceTest.java` | 11 | postgres:16-alpine | 0 |
| 3 | `CrmFlywayHistoryAssertionTest.java` | 5 | postgres:16-alpine | 0 |
| 4 | `CrmPostgresMigrationTest.java` | 4 | postgres:16-alpine | 0 |

**G1 Test Methods: 22 total, 0 disabled**

#### CrmG1TenantIsolationPostgresTest.java (2 tests)
- `rejectsCrossTenantContactLookupReferenceAndAcceptsSameTenantReference`
  - Creates tenantA and tenantB
  - Inserts crm_accounts and crm_contacts under tenantA
  - Attempts INSERT INTO crm_contact_lookup_index with tenantB + contactA → expects `DataIntegrityViolationException`
  - Inserts with tenantA + contactA → expects success
  - Asserts same-tenant record exists, cross-tenant record does not

#### Crm008bFoundationAcceptanceTest.java (11 tests)
- `g1AssignmentDataPreservedThroughV20260722_5_Backfill`
- `g1BackfillFailsClosedOnUnmappableRow`
- `v20260722_1_FailsClosedWhenTargetTableExists`
- `v20260722_5_FailsClosedWhenTargetColumnsExist`
- `v20260722_8_FailsClosedOnConflictingCapability`
- `v20260722_8_IdempotentWhenSalesManagerRoleAlreadyExists`
- `v20260722_7_FailsClosedWhenOwnerColumnsExist`
- `jsonbColumnsArePostgresNativeJsonb`
- `partialUniqueIndexesHaveCorrectPredicates`
- `cleanInstallProducesExpectedSchema`
- `v20260722_5_RollsBackTransactionOnFailure`

#### CrmFlywayHistoryAssertionTest.java (5 tests)
- `flywayHistoryContainsExactlyExpectedCrmVersionsInOrder`
- `flywayHistoryContainsNoDuplicateVersions`
- `flywayHistoryLatestVersionMatchesExpected`
- `allFlywayMigrationsSuccessful`
- `flywayHistoryTotalMigrationCountIncludesAllCrmVersions`

#### CrmPostgresMigrationTest.java (4 tests)
- `upgradesExistingPlatformThroughCrmRbacAndCompletion`
- `upgradesUnifiedCrmCoreThroughReconciliationAndCompletion`
- `installsCompletedCrmOnCleanPostgresDatabase`
- `jsonbColumnsHaveExactPostgresCatalogValues`

---

### G2-Specific Test Files (14 contract tests)

| # | File | Coverage |
|---|------|----------|
| 1 | `CrmAccountContractTest.java` | DTO shape verification |
| 2 | `CrmActivityContractTest.java` | Activity contract |
| 3 | `CrmConcurrencyContractTest.java` | Optimistic Concurrency (AC-05) |
| 4 | `CrmContactContractTest.java` | Contact contract |
| 5 | `CrmCustomFieldContractTest.java` | CustomField contract |
| 6 | `CrmErrorContractTest.java` | Error Envelope (AC-13) |
| 7 | `CrmIdempotencyContractTest.java` | Idempotency (AC-06, AC-07, AC-08) |
| 8 | `CrmImportContractTest.java` | Import contract |
| 9 | `CrmLeadContractTest.java` | Lead contract |
| 10 | `CrmMapperContractTest.java` | snake_case → camelCase DTO |
| 11 | `CrmOpportunityContractTest.java` | Opportunity contract |
| 12 | `CrmPaginationContractTest.java` | Cursor Pagination (AC-03, AC-04) |
| 13 | `CrmRbacContractTest.java` | RBAC matrix (AC-09) |
| 14 | `CrmTenantIsolationContractTest.java` | Tenant Isolation cursor/ETag/hash (AC-04, AC-10) |

**Additional:** `CrmOpenApiContractTest.java` — 5 tests validating OpenAPI spec (107 paths, 140 operations)

---

## Playwright E2E Tests (12 spec files, 78 test() calls)

| # | Spec File | Purpose |
|---|-----------|---------|
| 1 | `crm-007-production-closure.spec.ts` | Production closure verification |
| 2 | `crm-008r-production-closure.spec.ts` | Production closure verification |
| 3 | `crm-035-terminal-leads.spec.ts` | Terminal leads behavior |
| 4 | `crm-accessibility-ci.spec.ts` | Accessibility CI |
| 5 | `crm-accessibility.spec.ts` | Accessibility |
| 6 | `crm-authenticated-acceptance.spec.ts` | Full-stack acceptance |
| 7 | `crm-integration-workspace.spec.ts` | Integration workspace + RTL test |
| 8 | `crm-lifecycle.spec.ts` | CRM lifecycle |
| 9 | `crm-operational.spec.ts` | Operational flows |
| 10 | `crm-rbac-acceptance.spec.ts` | RBAC acceptance |
| 11 | `crm-route-smoke.spec.ts` | Route smoke |
| 12 | `crm-tenant-isolation.spec.ts` | Cross-tenant isolation |

### crm-tenant-isolation.spec.ts (13 tests)
- 4 API fetch tests (account, contact, lead, opportunity)
- 4 SPA detail page tests
- 4 list page tests
- 1 dashboard KPI test

### crm-integration-workspace.spec.ts
- Arabic RTL test: renders all governed panels in Arabic RTL
- Verifies `dir="rtl"` attribute and Arabic headings

---

## Vitest Frontend Tests (4 files, 56 it() test cases)

| # | File | it() Cases | Coverage |
|---|------|------------|----------|
| 1 | `leads-tab.test.tsx` | 34 | Leads tab |
| 2 | `crm-routes.test.tsx` | 11 | Route rendering (I18nProvider wrapper) |
| 3 | `crm-rbac.test.tsx` | 6 | RBAC (I18nProvider wrapper) |
| 4 | `crm-interactions.test.tsx` | 5 | Pipeline accessibility, RTL, virtualization |

### crm-interactions.test.tsx (5 tests)
- `resolves adjacent stages in sequence order`
- `moves an opportunity with the explicit keyboard alternative` (Arabic button label)
- `moves an opportunity with Alt plus arrow keys`
- `calculates an overscanned visible window`
- `renders only visible rows while publishing the full row count`

---

## Disabled/Skipped Tests

| Type | Count | Details |
|------|-------|---------|
| Java @Disabled | 0 | None found |
| Java @Ignore | 0 | None found |
| Playwright conditional skip | 3 | `crm-035-terminal-leads.spec.ts` — conditional on test data availability |
| Flaky markers | 0 | None found |

---

## Test Infrastructure

| Component | Technology |
|-----------|-----------|
| Java integration | Testcontainers + PostgreSQL 16-alpine |
| Migration validation | Flyway |
| E2E | Playwright |
| Frontend unit | Vitest + React Testing Library |
| CI required checks | 7 (see CI-CD-VERIFICATION.md) |

---

## TEST EVIDENCE SUMMARY

| Layer | Files | Test Cases | Disabled | Status |
|-------|-------|------------|----------|--------|
| Java Backend | 109 | 579 @Test | 0 | ✅ |
| G1-specific Java | 4 | 22 @Test | 0 | ✅ |
| G2 contract tests | 15 | ~80+ @Test | 0 | ✅ |
| Playwright E2E | 12 | 78 test() | 3 conditional | ✅ |
| Vitest Frontend | 4 | 56 it() | 0 | ✅ |
| **TOTAL** | **144** | **713+** | **0 structural** | **✅** |

**RESULT: G1+G2 TEST EVIDENCE VERIFIED. 109 Java files, 579 @Test methods, 12 Playwright specs (78 tests), 4 Vitest files (56 tests), 0 disabled tests, 0 flaky markers. Grand total: 713+ test cases.**
