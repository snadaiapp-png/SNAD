# Risk Register — CRM v2.0.0

**Audit Date:** 2026-07-30  
**Scope:** Production risk, security risk, scalability risk, maintainability risk, performance risk, architecture risk, compliance risk  
**Assessment:** CRITICAL

---

## Risk Scoring Methodology

Each risk is assessed across 6 dimensions, scored 1-5:

| Score | Likelihood | Impact | Detection Difficulty |
|-------|-----------|--------|---------------------|
| 1 | Rare | Negligible | Immediately obvious |
| 2 | Unlikely | Minor | Easily detectable |
| 3 | Possible | Moderate | Detectable with monitoring |
| 4 | Likely | Major | Difficult to detect |
| 5 | Almost Certain | Catastrophic | Undetectable without specific tools |

**Risk Score = Likelihood x Impact**  
- **Critical (20-25):** Immediate action required
- **High (15-19):** Action required within 1 sprint
- **Medium (10-14):** Action required within 2-3 sprints
- **Low (5-9):** Monitor and address during normal maintenance
- **Informational (1-4):** Accept or track

---

## Risk Register

### R-001: Mock Adapters Serving Synthetic Data in Production

| Attribute | Value |
|-----------|-------|
| **Risk ID** | R-001 |
| **Finding ID** | C-01 |
| **Title** | Mock adapters active by default in production |
| **Category** | Production Risk |
| **Likelihood** | 5 — Almost Certain (misconfiguration triggers fallback) |
| **Impact** | 5 — Catastrophic (synthetic data contaminates production) |
| **Risk Score** | **25 — CRITICAL** |
| **Detection Difficulty** | 4 — Difficult (no logging when mock adapters activate) |
| **Description** | Five mock adapters (MockPosDataAdapter, MockHrmDataAdapter, MockErpDataAdapter, MockCommerceDataAdapter, MockAccountingDataAdapter) use matchIfMissing=true, activating when real adapter beans are absent. Any production misconfiguration will silently serve synthetic customer intelligence data. |
| **Mitigation** | Remove matchIfMissing=true; restrict mocks to test/dev profiles; add health check verifying real adapters in production |
| **Contingency** | If synthetic data is already in production, run data repair to recalculate intelligence data from source systems |
| **Owner** | Platform Engineering |

---

### R-002: LegacyCrmInfrastructureService Maintainability Collapse

| Attribute | Value |
|-----------|-------|
| **Risk ID** | R-002 |
| **Finding ID** | C-02 |
| **Title** | 2044-line god class blocks maintenance and evolution |
| **Category** | Maintainability Risk |
| **Likelihood** | 5 — Almost Certain (any change risks regression) |
| **Impact** | 4 — Major (production incidents from unintended side effects) |
| **Risk Score** | **20 — CRITICAL** |
| **Detection Difficulty** | 3 — Moderate (regressions caught by tests if they exist) |
| **Description** | The LegacyCrmInfrastructureService contains business logic, validation, encryption, data access, and event publishing in a single 2044-line class. Any modification risks breaking unrelated functionality. New features are likely to duplicate or further entangle existing code. |
| **Mitigation** | Decompose into bounded services by domain concern; extract persistence to repositories; move business logic to domain layer |
| **Contingency** | Feature freeze on LegacyCrmInfrastructureService until decomposition is complete |
| **Owner** | CRM Domain Team |

---

### R-003: TransferUseCases.decide() MULTI_APPROVER Broken

| Attribute | Value |
|-----------|-------|
| **Risk ID** | R-003 |
| **Finding ID** | C-03 |
| **Title** | Multi-approver transfers functionally broken |
| **Category** | Architecture Risk / Production Risk |
| **Likelihood** | 4 — Likely (any MULTI_APPROVER transfer fails) |
| **Impact** | 4 — Major (core business feature non-functional) |
| **Risk Score** | **16 — HIGH** |
| **Detection Difficulty** | 2 — Easy (test fails; stack trace on execution) |
| **Description** | TransferUseCases.decide() throws an exception on final approval for MULTI_APPROVER transfers. This means the multi-approver policy option cannot be used in production. Any transfer configured with MULTI_APPROVER policy will fail with an exception. |
| **Mitigation** | Fix the decide() method to properly handle MULTI_APPROVER state transition, or remove the MULTI_APPROVER policy option |
| **Contingency** | Configure all transfers to use single-approver policy as a workaround |
| **Owner** | CRM Domain Team |

---

### R-004: Event Publication Failures Cause Silent Data Loss

| Attribute | Value |
|-----------|-------|
| **Risk ID** | R-004 |
| **Finding ID** | C-04 |
| **Title** | Event publication failures silently swallowed |
| **Category** | Production Risk / Data Integrity Risk |
| **Likelihood** | 4 — Likely (broker disruptions occur in production) |
| **Impact** | 4 — Major (state inconsistency across systems) |
| **Risk Score** | **16 — HIGH** |
| **Detection Difficulty** | 5 — Undetectable without specific monitoring (logged at DEBUG) |
| **Description** | SpringCustomerIntelligenceEventPublisher catches all exceptions and logs at DEBUG level. Event publication failures are invisible to operators. Downstream systems do not receive score/segment updates. |
| **Mitigation** | Re-throw exceptions; add failure metrics; implement retry with backoff; implement outbox pattern |
| **Contingency** | Manual event replay after broker recovery (no tooling exists) |
| **Owner** | Platform Engineering |

---

### R-005: Missing FK Constraints — Referential Integrity Violations

| Attribute | Value |
|-----------|-------|
| **Risk ID** | R-005 |
| **Finding ID** | C-05 |
| **Title** | Missing foreign key constraints on 5 CRM-010 tables |
| **Category** | Data Integrity Risk / Compliance Risk |
| **Likelihood** | 3 — Possible (application bugs can create orphaned records) |
| **Impact** | 4 — Major (data corruption, reporting inaccuracies) |
| **Risk Score** | **12 — MEDIUM** |
| **Detection Difficulty** | 3 — Moderate (orphaned records only detected via specific queries) |
| **Description** | Five tables in the CRM-010 schema lack foreign key constraints, allowing orphaned records and referential integrity violations. Application bugs or manual database operations can create data that references non-existent parent records. |
| **Mitigation** | Add FK constraints via new migration; audit existing data for orphans |
| **Contingency** | Periodic data integrity checks to identify and repair orphaned records |
| **Owner** | Data Engineering |

---

### R-006: Missing Audit Columns — No Accountability Trail

| Attribute | Value |
|-----------|-------|
| **Risk ID** | R-006 |
| **Finding ID** | C-06 |
| **Title** | Missing created_by/updated_by on 6 CRM-010 tables |
| **Category** | Compliance Risk / Security Risk |
| **Likelihood** | 3 — Possible (required for compliance audits) |
| **Impact** | 3 — Moderate (cannot determine who modified intelligence data) |
| **Risk Score** | **9 — LOW** |
| **Detection Difficulty** | 4 — Difficult (missing columns not visible in normal operation) |
| **Description** | Six CRM-010 tables lack audit columns (created_by, updated_by). In the event of data integrity issues, there is no way to trace who or what process modified the data. This may violate compliance requirements for audit trails. |
| **Mitigation** | Add audit columns via new migration; back-fill with default values |
| **Contingency** | Accept the compliance gap until migration is deployed |
| **Owner** | CRM Domain Team |

---

### R-007: CustomerScoringService Overwrites Real Scores with Fake Data

| Attribute | Value |
|-----------|-------|
| **Risk ID** | R-007 |
| **Finding ID** | C-07 |
| **Title** | refreshAllScores() hardcoded fake values overwrite real data |
| **Category** | Production Risk / Data Integrity Risk |
| **Likelihood** | 4 — Likely (scheduled or admin-triggered execution) |
| **Impact** | 5 — Catastrophic (all customer scores corrupted) |
| **Risk Score** | **20 — CRITICAL** |
| **Detection Difficulty** | 3 — Moderate (score changes detected by monitoring if set up) |
| **Description** | The refreshAllScores() method in CustomerScoringService overwrites real customer scores with hardcoded synthetic values. Any invocation — via scheduler, admin endpoint, or auto-scaling initialization — corrupts production scoring data. |
| **Mitigation** | Immediately disable the method; remove hardcoded values; implement real scoring or call external service |
| **Contingency** | Data restore from backup if scores are corrupted; manual recalculation from source data |
| **Owner** | CRM Domain Team |

---

### R-008: Zero-UUID Tenant Breaks Multi-Tenant Isolation

| Attribute | Value |
|-----------|-------|
| **Risk ID** | R-008 |
| **Finding ID** | C-08 |
| **Title** | Hardcoded zero-UUID tenant in seed migration |
| **Category** | Security Risk / Architecture Risk |
| **Likelihood** | 3 — Possible (misconfiguration or RLS bypass) |
| **Impact** | 4 — Major (cross-tenant data leakage) |
| **Risk Score** | **12 — MEDIUM** |
| **Detection Difficulty** | 4 — Difficult (RLS policies must be carefully audited) |
| **Description** | The seed migration uses a zero-UUID tenant ID for reference data. This sentinel value requires special-case handling in RLS policies, application code, and queries. If RLS policies do not correctly exclude or include the zero-UUID, cross-tenant data leakage or data invisibility can occur. |
| **Mitigation** | Replace zero-UUID with proper reference tenant; ensure RLS policies handle reference tenant correctly |
| **Contingency** | Audit all RLS policies for zero-UUID handling; add integration tests covering reference data access |
| **Owner** | Security Engineering |

---

### R-009: Frontend/Backend Naming Mismatch Causes Serialization Bugs

| Attribute | Value |
|-----------|-------|
| **Risk ID** | R-009 |
| **Finding ID** | C-09 |
| **Title** | Frontend snake_case vs backend camelCase |
| **Category** | Production Risk |
| **Likelihood** | 4 — Likely (new fields frequently miss annotations) |
| **Impact** | 3 — Moderate (field silently null, feature partially broken) |
| **Risk Score** | **12 — MEDIUM** |
| **Detection Difficulty** | 3 — Moderate (silent null fields unless tested) |
| **Description** | Frontend TypeScript types use snake_case while Java DTOs use camelCase. Serialization relies on Jackson annotations to bridge the gap. New fields are frequently added without matching annotations, causing runtime failures where fields are silently null. |
| **Mitigation** | Standardize on one convention; prefer camelCase globally; implement automated check for serialization annotations |
| **Contingency** | Add integration tests that verify serialization of all DTOs |
| **Owner** | Full-Stack Team |

---

### R-010: V1/V2 Controller Duplication Causes Behavioral Divergence

| Attribute | Value |
|-----------|-------|
| **Risk ID** | R-010 |
| **Finding ID** | C-10 |
| **Title** | V1 and V2 controller duplication |
| **Category** | Maintainability Risk / Scalability Risk |
| **Likelihood** | 5 — Almost Certain (two code paths will diverge) |
| **Impact** | 3 — Moderate (confusing bugs, inconsistent behavior) |
| **Risk Score** | **15 — HIGH** |
| **Detection Difficulty** | 3 — Moderate (divergence only visible when endpoints compared) |
| **Description** | Two complete controller layers serve the same domain concepts. The two code paths will inevitably diverge as changes are applied to one but not the other, creating inconsistent API behavior and duplicated maintenance effort. |
| **Mitigation** | Deprecate V1 controllers; route all traffic through V2; remove V1 after transition period |
| **Contingency** | Maintain parity checklist for any API change affecting both versions |
| **Owner** | CRM Domain Team |

---

### R-011: Inconsistent Migration Naming Causes Ordering Confusion

| Attribute | Value |
|-----------|-------|
| **Risk ID** | R-011 |
| **Finding ID** | C-11 |
| **Title** | Inconsistent migration naming conventions |
| **Category** | Maintainability Risk |
| **Likelihood** | 3 — Possible (merge conflicts, ordering ambiguity) |
| **Impact** | 2 — Minor (manual ordering verification needed) |
| **Risk Score** | **6 — LOW** |
| **Detection Difficulty** | 1 — Immediately obvious (naming mismatch visible in directory) |
| **Description** | Core migrations use incrementing numbers (V1-V19) while newer migrations use date stamps (V20260729_*). The mixed naming creates ordering ambiguity and potential merge conflicts. |
| **Mitigation** | Standardize on date-based naming; document convention |
| **Contingency** | Accept the inconsistency; address during next migration cleanup |
| **Owner** | Platform Engineering |

---

### R-012: No Domain Events for Core Operations

| Attribute | Value |
|-----------|-------|
| **Risk ID** | R-012 |
| **Finding ID** | C-12 |
| **Title** | No domain events for core entity operations |
| **Category** | Architecture Risk / Scalability Risk |
| **Likelihood** | 4 — Likely (tight coupling blocks evolution) |
| **Impact** | 4 — Major (scalability and extensibility blocked) |
| **Risk Score** | **16 — HIGH** |
| **Detection Difficulty** | 4 — Difficult (no compile-time enforcement of event-driven patterns) |
| **Description** | Core entity operations (create, update, archive) do not emit domain events. Cross-domain communication relies on direct service calls, creating tight coupling between bounded contexts and preventing reactive workflows. |
| **Mitigation** | Implement domain events for all core entity operations; adopt outbox pattern for reliable delivery |
| **Contingency** | Continue with direct service calls (increasing coupling and maintenance cost) |
| **Owner** | CRM Domain Team |

---

### R-013: No Smoke Tests or Production Health Checks

| Attribute | Value |
|-----------|-------|
| **Risk ID** | R-013 |
| **Finding ID** | H-21 |
| **Title** | No smoke tests or automated production health checks |
| **Category** | Production Risk |
| **Likelihood** | 4 — Likely (deployments without verification) |
| **Impact** | 4 — Major (deployment failures detected by users) |
| **Risk Score** | **16 — HIGH** |
| **Detection Difficulty** | 4 — Difficult (no automated detection mechanism) |
| **Description** | The platform lacks smoke tests and automated health checks for CRM functionality post-deployment. Deployments are verified only by CI tests that may not reflect production behavior. Production issues are detected by users or not at all. |
| **Mitigation** | Implement smoke tests that run post-deployment; add synthetic monitoring for critical CRM flows |
| **Contingency** | Manual verification checklist executed by operations after each deployment |
| **Owner** | Platform Engineering |

---

### R-014: AI Gateway 30s Timeout Causes Thread Pool Exhaustion

| Attribute | Value |
|-----------|-------|
| **Risk ID** | R-014 |
| **Finding ID** | H-05 |
| **Title** | Hardcoded AI Gateway timeout (30s) |
| **Category** | Performance Risk / Scalability Risk |
| **Likelihood** | 3 — Possible (upstream AI Gateway latency spikes) |
| **Impact** | 4 — Major (thread pool exhaustion, cascading failures) |
| **Risk Score** | **12 — MEDIUM** |
| **Detection Difficulty** | 3 — Moderate (detected via thread dump or latency monitoring) |
| **Description** | The AI Gateway HTTP client has a hardcoded 30-second timeout without circuit breaker protection. Under upstream degradation, multiple concurrent requests can exhaust the application server thread pool, causing cascading failures across all CRM functionality. |
| **Mitigation** | Reduce timeout and make configurable; add circuit breaker with failover; implement graceful degradation |
| **Contingency** | Request rate limiting to limit concurrent AI Gateway calls |
| **Owner** | Platform Engineering |

---

### R-015: No CI Job for CRM Integration Tests

| Attribute | Value |
|-----------|-------|
| **Risk ID** | R-015 |
| **Finding ID** | H-11 |
| **Title** | No CI job for CRM integration tests |
| **Category** | Scalability Risk / Production Risk |
| **Likelihood** | 4 — Likely (schema/query regressions undetected) |
| **Impact** | 3 — Moderate (production issues from untested integration paths) |
| **Risk Score** | **12 — MEDIUM** |
| **Detection Difficulty** | 3 — Moderate (regressions caught in production monitoring if configured) |
| **Description** | No CI job runs CRM integration tests against a real database. Database schema changes, migration issues, and repository-layer bugs are not detected in CI. |
| **Mitigation** | Add CI job with PostgreSQL; make it a required check for merging |
| **Contingency** | Run integration tests manually before production deployments |
| **Owner** | Platform Engineering |

---

### R-016: ReportsUseCases Stubs in Production

| Attribute | Value |
|-----------|-------|
| **Risk ID** | R-016 |
| **Finding ID** | M-10 |
| **Title** | ReportsUseCases stub implementations deployed to production |
| **Category** | Production Risk |
| **Likelihood** | 3 — Possible (users navigate to reporting features) |
| **Impact** | 2 — Minor (error or empty state) |
| **Risk Score** | **6 — LOW** |
| **Detection Difficulty** | 1 — Immediately obvious (user sees error or empty data) |
| **Description** | ReportsUseCases contains stub implementations that throw UnsupportedOperationException or return empty data. These stubs are deployed to production and will fail if users access reporting features. |
| **Mitigation** | Implement reports or remove the feature; add feature flag |
| **Contingency** | Document that reporting features are not yet available |
| **Owner** | CRM Domain Team |

---

## Risk Matrix

```
Likelihood
5 | R-001(25)                   R-010(15)
4 | R-002(20) R-007(20)  R-003(16) R-004(16) R-009(12) R-012(16) R-013(16) R-015(12)
3 |                   R-005(12) R-008(12) R-011(6)  R-014(12) R-016(6)
2 |
1 |
   +---------------------------------------------------------------
     1          2          3          4          5
                           Impact
```

**Legend:**
- **CRITICAL (20-25):** R-001 (25), R-002 (20), R-007 (20)
- **HIGH (15-19):** R-003 (16), R-004 (16), R-010 (15), R-012 (16), R-013 (16)
- **MEDIUM (10-14):** R-005 (12), R-008 (12), R-009 (12), R-014 (12), R-015 (12)
- **LOW (5-9):** R-006 (9), R-011 (6), R-016 (6)

---

## Risk Reduction Roadmap

**Immediate (R-001, R-002, R-007):**
1. Remove matchIfMissing from mock adapters — reduces risk from 25 to acceptable level
2. Disable refreshAllScores() hardcoded values — eliminates 20-score risk
3. Begin LegacyCrmInfrastructureService decomposition — reduces 20-score risk over time

**Short-term (R-003, R-004, R-012, R-013):**
4. Fix MULTI_APPROVER decision logic
5. Implement proper event error handling and outbox
6. Implement domain events for core operations
7. Add smoke tests and health checks

**Medium-term (R-005, R-008, R-009, R-010, R-014, R-015):**
8. Add FK constraints and audit columns
9. Fix zero-UUID tenant issue
10. Unify frontend/backend naming
11. Consolidate V1/V2 controllers
12. Externalize timeout values; add circuit breaker
13. Add CI job for CRM integration tests

**Low/Defer (R-006, R-011, R-016):**
14. Accept or address during normal maintenance

---

*Report generated by independent forensic audit. 16 risks identified across 7 risk categories.*
