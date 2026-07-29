# CRM-010 Endpoint / Capability / Tenant-Isolation Inventory

**Date:** 2026-07-29
**Issue:** #705 — Mandatory Deliverable #2
**Scope:** CRM-010 Customer Intelligence domain endpoints, capability requirements, and tenant-isolation verification

---

## 1. CRM-010 Endpoint Inventory

### 1.1 Customer 360 Endpoint (CRM-010)

| Method | Path | Capability | Controller | Description |
|--------|------|------------|------------|-------------|
| GET | `/api/v2/crm/accounts/{accountId}/customer-360` | `CRM.ACCOUNT.READ` | `CrmContractController` | Loads unified customer profile with intelligence data |

**Source:** `apps/sanad-platform/src/main/java/com/sanad/platform/crm/web/CrmContractController.java:186-199`

**Response:** `SingleResponse<Customer360Response>` containing:
- Base customer profile (from CRM-007/CRM-008B)
- Health score, CLV, risk score, engagement score, loyalty score
- Active segments
- Pending next-best-actions
- Score history

### 1.2 Related Endpoints (Upstream Consumers)

These endpoints consume CRM-010 intelligence data indirectly:

| Method | Path | Capability | Consumer |
|--------|------|------------|----------|
| GET | `/api/v2/crm/accounts/{accountId}` | `CRM.ACCOUNT.READ` | Account detail view (includes scores) |
| GET | `/api/v2/crm/accounts` | `CRM.ACCOUNT.READ` | Account list (includes score summaries) |

---

## 2. CRM-010 Capability Inventory

### 2.1 Capabilities Seeded by CRM-010

| Capability | Description | Migration | Source |
|------------|-------------|-----------|--------|
| `CRM.CUSTOMER_360.READ` | View unified customer 360 profile | V20260729_1 | `V20260729_1__create_crm_customer_intelligence.sql:170` |
| `CRM.CUSTOMER_INTELLIGENCE.READ` | View customer scores, insights, and AI predictions | V20260729_1 | `V20260729_1__create_crm_customer_intelligence.sql:171` |
| `CRM.CUSTOMER_INTELLIGENCE.WRITE` | Manually trigger customer intelligence recalculation | V20260729_1 | `V20260729_1__create_crm_customer_intelligence.sql:172` |
| `CRM.CUSTOMER_INTELLIGENCE.ADMIN` | Configure and manage scoring models | V20260729_1 | `V20260729_1__create_crm_customer_intelligence.sql:173` |
| `CRM.CUSTOMER_SEGMENT.MANAGE` | Create, update, and manage customer segments | V20260729_1 | `V20260729_1__create_crm_customer_intelligence.sql:174` |

### 2.2 Capability-to-Endpoint Mapping

| Endpoint | Required Capability | Enforcement |
|----------|-------------------|-------------|
| `GET /api/v2/crm/accounts/{accountId}/customer-360` | `CRM.ACCOUNT.READ` | `@RequireCapability("CRM.ACCOUNT.READ")` on controller method |

**Note:** CRM-010 intelligence capabilities (`CRM.CUSTOMER_360.READ`, etc.) are enforced at the application service layer via `CustomerIntelligenceValidator`, not at the controller level. The controller uses `CRM.ACCOUNT.READ` as the entry-point capability.

### 2.3 Capability-to-Service Mapping

| Service Method | Required Capability | Enforcement |
|---------------|---------------------|-------------|
| `Customer360ApplicationService.loadCustomer360()` | `CRM.CUSTOMER_360.READ` | `CustomerIntelligenceValidator` |
| `CustomerScoringService.calculateHealthScore()` | `CRM.CUSTOMER_INTELLIGENCE.WRITE` | `CustomerIntelligenceValidator` |
| `CustomerScoringService.refreshAllScores()` | `CRM.CUSTOMER_INTELLIGENCE.WRITE` | `CustomerIntelligenceValidator` |
| `CustomerLifetimeValueService.calculateCLV()` | `CRM.CUSTOMER_INTELLIGENCE.WRITE` | `CustomerIntelligenceValidator` |
| `ChurnPredictionService.calculateChurnRisk()` | `CRM.CUSTOMER_INTELLIGENCE.WRITE` | `CustomerIntelligenceValidator` |
| `CustomerSegmentationService.createSegment()` | `CRM.CUSTOMER_SEGMENT.MANAGE` | `CustomerIntelligenceValidator` |
| `CustomerSegmentationService.addCustomerToSegment()` | `CRM.CUSTOMER_SEGMENT.MANAGE` | `CustomerIntelligenceValidator` |
| `CustomerSegmentationService.removeCustomerFromSegment()` | `CRM.CUSTOMER_SEGMENT.MANAGE` | `CustomerIntelligenceValidator` |
| `NextBestActionService.generateRecommendation()` | `CRM.CUSTOMER_INTELLIGENCE.WRITE` | `CustomerIntelligenceValidator` |
| `NextBestActionService.acceptRecommendation()` | `CRM.CUSTOMER_INTELLIGENCE.WRITE` | `CustomerIntelligenceValidator` |
| `NextBestActionService.rejectRecommendation()` | `CRM.CUSTOMER_INTELLIGENCE.WRITE` | `CustomerIntelligenceValidator` |
| `NextBestActionService.expireStaleRecommendations()` | `CRM.CUSTOMER_INTELLIGENCE.ADMIN` | `CustomerIntelligenceValidator` |

---

## 3. Tenant-Isolation Inventory

### 3.1 Database-Level Isolation

| Table | Tenant Column | Unique Constraint | Index |
|-------|--------------|-------------------|-------|
| `crm_customer_scores` | `tenant_id UUID NOT NULL` | `(tenant_id, account_id, score_type, calculated_at)` | `crm_customer_scores_tenant_account_type_idx` |
| `crm_customer_score_history` | `tenant_id UUID NOT NULL` | — | `crm_customer_score_history_tenant_account_idx` |
| `crm_customer_segments` | `tenant_id UUID NOT NULL` | `(tenant_id, segment_code)` | `crm_customer_segments_tenant_code_idx` |
| `crm_segment_memberships` | `tenant_id UUID NOT NULL` | — | `crm_segment_memberships_tenant_account_idx`, `crm_segment_memberships_tenant_segment_idx` |
| `crm_next_best_actions` | `tenant_id UUID NOT NULL` | — | `crm_next_best_actions_tenant_account_status_idx` |
| `crm_scoring_models` | `tenant_id UUID NOT NULL` | `(tenant_id, score_type, active)` | `crm_scoring_models_tenant_type_active_idx` |

**Source:** `V20260729_1__create_crm_customer_intelligence.sql`

### 3.2 Query-Level Isolation

Every SQL query in the intelligence infrastructure includes `tenant_id = :tenantId` in the WHERE clause:

| Adapter | File | Queries Verified | Tenant Filter |
|---------|------|-----------------|---------------|
| `JdbcCustomerIntelligenceQueryAdapter` | `intelligence/infrastructure/JdbcCustomerIntelligenceQueryAdapter.java` | 6 | All include `tenant_id = :tenantId` |
| `JdbcScoringAdapter` | `intelligence/infrastructure/JdbcScoringAdapter.java` | 2 | All include `tenant_id = :tenantId` |
| `JdbcNextBestActionAdapter` | `intelligence/infrastructure/JdbcNextBestActionAdapter.java` | 2 | All include `tenant_id = :tenantId` |
| `JdbcSegmentAdapter` | `intelligence/infrastructure/JdbcSegmentAdapter.java` | 3 | All include `tenant_id = :tenantId` |

**Total queries verified:** 13/13 include tenant isolation filter.

### 3.3 Cache-Level Isolation

| Cache | Key Pattern | Isolation |
|-------|-------------|-----------|
| `scores` | `scores:v1:{tenantId}:{accountId}` | ✅ Tenant-scoped |
| `view` | `view:v1:{tenantId}:{accountId}` | ✅ Tenant-scoped |

**Source:** `CustomerIntelligenceCache.java:51,72`

### 3.4 Application-Level Isolation

| Layer | Mechanism | Verification |
|-------|-----------|-------------|
| Controller | `@RequireCapability` annotation | `CrmContractController.java:186` |
| Application Service | `CustomerIntelligenceValidator` checks tenant ownership | `CustomerIntelligenceValidator.java` |
| Infrastructure | `SpringTenantContextAdapter` extracts tenant from JWT | `SpringTenantContextAdapter.java` |
| Domain | `TenantContextPort` interface (never reads from request body) | `TenantContextPort.java` |

### 3.5 Tenant-Isolation Test Coverage

| Test | File | Coverage |
|------|------|----------|
| `CrmG1TenantIsolationPostgresTest` | `crm/web/CrmG1TenantIsolationPostgresTest.java` | End-to-end PostgreSQL tenant isolation |
| `CrmTenantIsolationContractTest` | `crm/contract/CrmTenantIsolationContractTest.java` | Cursor pagination isolation, hash non-reversibility |
| `CustomerIntelligenceIntegrationTest` | `intelligence/application/CustomerIntelligenceIntegrationTest.java` | Intelligence-specific isolation |

---

## 4. Verification Checklist

| Check | Status | Evidence |
|-------|--------|----------|
| Every CRM-010 table has `tenant_id UUID NOT NULL` | ✅ PASS | 6/6 tables verified in V20260729_1 |
| Every query includes `tenant_id` filter | ✅ PASS | 13/13 queries verified |
| Cache keys are tenant-scoped | ✅ PASS | 2/2 caches verified |
| Controller enforces capability check | ✅ PASS | `@RequireCapability` on Customer 360 endpoint |
| Application service enforces tenant ownership | ✅ PASS | `CustomerIntelligenceValidator` |
| Tenant extracted from JWT, not request body | ✅ PASS | `SpringTenantContextAdapter` |
| Tenant isolation tests exist | ✅ PASS | 3 test classes verified |

---

**Inventory Authority:** Governance Remediation Agent
**Date:** 2026-07-29
**Status:** ✅ COMPLETE
