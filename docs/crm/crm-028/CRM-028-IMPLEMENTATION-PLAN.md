# CRM-028 IMPLEMENTATION PLAN

**Date:** 2026-07-31
**Ticket:** CRM-028 — Add Flyway-history assertion test for production Supabase
**Status:** READY TO IMPLEMENT

---

## 1. Implementation Tasks

### Task 1: Create Flyway History Assertion Test

**File:** `apps/sanad-platform/src/test/java/com/sanad/platform/crm/web/CrmFlywayHistoryAssertionTest.java`

**Test Flow:**
1. Start PostgreSQL Testcontainer
2. Run Flyway migrations
3. Query `flyway_schema_history` table
4. Assert expected CRM versions exist
5. Assert versions are in correct order
6. Assert all versions succeeded

**Implementation:**
```java
@Testcontainers
class CrmFlywayHistoryAssertionTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");
    
    private static final List<String> EXPECTED_CRM_VERSIONS = List.of(
        "20260702.1",  // CRM Core
        "20260702.2",  // Reconciler
        "20260702.3",  // CRM Completion
        "20260706.1",  // Tenant Quota
        "20260711.1",  // Subscription Change Events
        "20260713.1",  // CRM Idempotency
        "20260713.2",  // CRM Pipeline Version Column
        "20260716.1",  // CRM Tasks
        "20260716.2",  // CRM Notes
        "20260716.3",  // CRM Tags
        "20260716.4",  // CRM Customer Master
        "20260717.1",  // CRM Contact Relationship
        "20260717.2",  // CRM Contact Relationship RBAC
        "20260717.3",  // CRM Timeline Tenant Lifecycle
        "20260717.4",  // Business Process Backbone
        "20260717.5",  // Business Process RBAC
        "20260717.6",  // CRM G1 Extension
        "20260717.100", // CRM Address Communication
        "20260717.101", // CRM Address Communication RBAC
        "20260718.1",  // Vendor Reconcile G1
        "20260721.1",  // Vendor Reconcile Contact Rel
        "20260721.2",  // Vendor Reconcile Idempotency
        "20260722.1",  // CRM 008B Sales Teams
        "20260722.2",  // CRM 008B Queues
        "20260722.3",  // CRM 008B Territories
        "20260722.4",  // CRM 008B Assignment Rules
        "20260722.5",  // CRM 008B Assignments
        "20260722.6",  // CRM 008B Transfer Requests
        "20260722.7",  // CRM 008B Owner Columns
        "20260722.8",  // CRM 008B Capabilities
        "20260722.9",  // CRM 008B Counters
        "20260723.1",  // CRM 009 Integration
        "20260724.1",  // CRM 009 Command Executions
        "20260724.2",  // CRM 009 Command Artifacts
        "20260729.1",  // CRM 010 Intelligence
        "20260730.2"   // CRM 010 Scoring Models
    );
    
    @Test
    void assertFlywayHistoryContainsExpectedCrmVersions() {
        // Query flyway_schema_history
        // Assert all expected versions exist
        // Assert versions are in correct order
        // Assert all migrations succeeded
    }
}
```

### Task 2: Verify CI Integration

**File:** `.github/workflows/ci.yml`

**Verification:**
- Ensure `crm` job picks up new test class
- Verify class name matches `com.sanad.platform.crm.**` pattern

---

## 2. Task Order

| # | Task | Depends On | Estimated Time |
|---|------|-----------|----------------|
| 1 | Create Flyway history assertion test | None | 30 min |
| 2 | Verify CI integration | Task 1 | 10 min |
| 3 | Test locally | Tasks 1-2 | 15 min |
| 4 | Push to feature branch | Task 3 | 5 min |
| 5 | Verify CI passes | Task 4 | 10 min |
| 6 | Merge to main | Task 5 | 5 min |

**Total Estimated Time:** 75 min

---

## 3. Validation Strategy

### 3.1 Local Validation

```bash
cd apps/sanad-platform
mvn test -Dtest=CrmFlywayHistoryAssertionTest
```

### 3.2 CI Validation

1. Push to feature branch
2. Verify `crm` job runs new test
3. Verify test passes in CI

---

## 4. Rollback Strategy

| Step | Action |
|------|--------|
| 1 | Revert test file deletion |
| 2 | Revert CI changes if any |
| 3 | Redeploy previous version |

---

## 5. Authorization

✅ **CRM-028 IMPLEMENTATION PLAN APPROVED**

All tasks defined. Ready to proceed with implementation.
