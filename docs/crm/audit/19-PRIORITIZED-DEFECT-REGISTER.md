# Prioritized Defect Register — CRM v2.0.0

**Audit Date:** 2026-07-30  
**Scope:** All Critical, High, and Medium findings from the CRM forensic audit  
**Total Findings:** 45 (12 Critical, 18 High, 15 Medium)

---

## Priority Classification

| Priority | Definition | Action Required |
|----------|-----------|-----------------|
| **P0** | Immediate — blocks production correctness | Fix within days; stop other work if necessary |
| **P1** | Short-term — significant risk within 1-2 sprints | Fix within current or next sprint |
| **P2** | Medium-term — important but non-blocking | Fix within 2-3 sprints |
| **P3** | Low — cosmetic or minor improvement | Fix when convenient or during refactoring |

---

## P0 — Immediate (Must Fix Before G5)

| ID | Title | Severity | Category | Phase | Complexity | Blocks |
|----|-------|----------|----------|-------|-----------|--------|
| C-01 | Mock adapters active by default (matchIfMissing=true) | CRITICAL | Production Risk | CRM-019 | LOW (2-3 days) | Yes |
| C-07 | CustomerScoringService.refreshAllScores() hardcoded fake values | CRITICAL | Data Integrity | CRM-019 | LOW (1 day) to disable; MEDIUM (1-2 weeks) to fix | Yes |
| C-03 | TransferUseCases.decide() throws on MULTI_APPROVER final approval | CRITICAL | Functional Defect | CRM-008B | MEDIUM (3-5 days) | Yes (if required feature) |
| C-04 | Event publication failures silently swallowed | CRITICAL | Data Integrity | CRM-019 | MEDIUM (1 week) | Yes |
| C-12 | No domain events for core entity operations | CRITICAL | Architecture | CRM-002-CRM-007 | HIGH (3-4 weeks) | Yes (perpetuates pattern) |
| C-05 | Missing FK constraints on 5 CRM-010 tables | CRITICAL | Data Integrity | CRM-019 | LOW (2-3 days) | Yes |
| C-08 | Hardcoded zero-UUID tenant in seed migration | CRITICAL | Security/Architecture | CRM-019 | MEDIUM (3-5 days) | No (but high risk) |
| H-13 | Misleading test name: login_wrongTenant_returns401 returns 200 | HIGH | Test Accuracy | General | LOW (1 day) | Yes (security test broken) |

**Total P0: 8 findings**

**Immediate Action Items:**
1. Remove `matchIfMissing=true` from all 5 mock adapters — this is a 2-3 day change that eliminates the highest-risk defect in the codebase
2. Disable `refreshAllScores()` by removing @Scheduled and admin trigger — 1 day to stop data corruption risk
3. Fix MULTI_APPROVER in TransferUseCases.decide() or remove the policy option
4. Fix event publisher to surface failures; implement outbox pattern
5. Add FK constraints to CRM-010 tables
6. Fix misleading test assertion (returns401 -> returns200)

---

## P1 — Short-Term (Within 1-2 Sprints)

| ID | Title | Severity | Category | Phase | Complexity |
|----|-------|----------|----------|-------|-----------|
| C-02 | LegacyCrmInfrastructureService 2044-line god class | CRITICAL | Architecture | CRM-003-CRM-007 | HIGH (3-4 weeks) |
| C-10 | V1 and V2 controller duplication | CRITICAL | Architecture | CRM-008B | MEDIUM (1-2 weeks) |
| C-09 | Frontend snake_case vs backend camelCase | CRITICAL | Inconsistency | CRM-008/014/015/016 | HIGH (2-3 weeks) |
| C-11 | Inconsistent migration naming conventions | CRITICAL | Standards | CRM-004 | LOW (1-2 days) |
| C-06 | Missing audit columns on 6 CRM-010 tables | CRITICAL | Compliance | CRM-019 | LOW (1-2 days) |
| I-03 | Duplicated auth context extraction in 9 V1 controllers | HIGH | Duplication | CRM-003 | LOW (2-3 days) |
| I-08 | URL path hardcoding in AOP aspects | HIGH | Hardcoded Values | CRM-008 | LOW (1-2 days) |
| I-10 | DisabledHrmOwnershipAdapter active in all profiles | HIGH | Misconfiguration | CRM-008 | LOW (1 day) |
| H-09 | Only 3 E2E tests for entire platform | HIGH | Testing | General | HIGH (2-3 weeks) |
| H-10 | Docker tests silently skipped (~25-30% coverage) | HIGH | Testing | General | MEDIUM (1 week) |
| H-11 | No CI job for CRM integration tests | HIGH | CI/CD | General | MEDIUM (3-5 days) |
| H-12 | Fragile reflection in tests (reflectSet) | HIGH | Testing | General | MEDIUM (1 week) |
| H-15 | Low assertion quality, missing negative tests | HIGH | Testing | General | MEDIUM (1-2 weeks) |
| H-20 | Missing NOT NULL on crm_customer_segments.criteria | HIGH | Schema | CRM-019 | LOW (1 day) |
| H-04 | Hardcoded cache TTL (5 min) and max size (10K) | HIGH | Configuration | CRM-019 | LOW (2-3 days) |
| H-05 | Hardcoded AI Gateway timeout (30s) | HIGH | Configuration | CRM-019 | LOW (1-2 days) |
| H-21 | No smoke tests or automated production health checks | HIGH | Operations | General | MEDIUM (1-2 weeks) |
| H-16 | Missing API documentation for V2 endpoints | HIGH | Documentation | General | MEDIUM (1 week) |
| H-17 | Missing ADRs for key architectural decisions | HIGH | Documentation | General | MEDIUM (1 week) |
| H-18 | Missing documentation for intelligence module | HIGH | Documentation | CRM-019 | MEDIUM (1 week) |
| H-06 | Pipeline board no virtualization for large lists | MEDIUM | UI Performance | CRM-020 | MEDIUM (3-5 days) |
| H-07 | customer360() uses @SuppressWarnings("unchecked") | MEDIUM | Type Safety | CRM-017 | MEDIUM (3-5 days) |

**Total P1: 22 findings**

**Key Priorities:**
1. Begin LegacyCrmInfrastructureService decomposition incrementally (can be parallelized with other work)
2. Deprecate V1 controllers; plan removal
3. Align frontend/backend naming convention (requires coordinated release)
4. Add E2E tests for critical business flows
5. Add CI job for CRM integration tests with Docker
6. Standardize migration naming conventions
7. Add audit columns and NOT NULL constraints to CRM-010 tables
8. Externalize cache and timeout configurations
9. Add smoke tests and health checks
10. Centralize auth context extraction

---

## P2 — Medium-Term (Within 2-3 Sprints)

| ID | Title | Severity | Category | Phase | Complexity |
|----|-------|----------|----------|-------|-----------|
| I-01 | Anemic domain model | HIGH | Architecture | CRM-002 | HIGH (3-4 weeks) |
| H-14 | No @DisplayName on several test classes | MEDIUM | Testing | General | LOW (2-3 days) |
| M-01 | No pagination on listCustomFields | MEDIUM | Performance | CRM-006 | LOW (2-3 days) |
| M-02 | CrmCoreCursorPaginationAspect reflection overhead | MEDIUM | Performance | General | LOW (2-3 days) |
| M-03 | No pagination for search and activity queries | MEDIUM | Performance | CRM Search/Activity | LOW (3-5 days) |
| M-04 | ScoreValueObjectsTest not exhaustive on boundaries | MEDIUM | Testing | CRM-019 | LOW (2-3 days) |
| M-05 | OrganizationServiceTest uses fragile reflection | MEDIUM | Testing | General | LOW (1-2 days) |
| M-06 | AccountUseCasesIntegrationTest no concurrent update test | MEDIUM | Testing | CRM-008 | LOW (3-5 days) |
| M-07 | Test method names > 80 characters | MEDIUM | Style | General | LOW (1 day) |
| M-08 | Missing requirements traceability | MEDIUM | Documentation | General | MEDIUM (1 week) |
| M-09 | Missing acceptance criteria documentation | MEDIUM | Documentation | General | MEDIUM (1 week) |
| M-10 | ReportsUseCases stub implementations | MEDIUM | Dead Code | Reports | LOW (1 day to remove) |
| M-11 | SearchUseCases missing full-text search | MEDIUM | Incomplete | CRM Search | MEDIUM (1-2 weeks) |
| M-12 | Transactional boundary issues in outbox | MEDIUM | Data Integrity | CRM Integration | MEDIUM (3-5 days) |
| M-13 | Dead code assessment (V1, stubs, unused interfaces) | MEDIUM | Dead Code | General | LOW (2-3 days) |
| H-19 | No production runbooks for CRM-specific operations | MEDIUM | Documentation | General | MEDIUM (1 week) |

**Total P2: 16 findings**

---

## P3 — Low / Deferred

| ID | Title | Severity | Category | Phase | Complexity |
|----|-------|----------|----------|-------|-----------|
| I-09 | Assignment.recordType() null validation missing | LOW | Defensive Coding | CRM-008 | LOW (1 day) |
| M-10b | Export controllers lack @RequireCapability | LOW | Security | CRM Exports | LOW (1 day) |
| C-11b | Constraint naming inconsistency in crm_integration_* tables | HIGH (inconsistent severity; deferred to P3) | Standards | CRM-009 | LOW (2-3 days) |
| H-22 | No unique constraint on duplicate active segment memberships | MEDIUM | Schema | CRM-019 | LOW (1 day) |
| H-23 | TagUseCases lack bulk operations | MEDIUM | Functional | CRM Tags | LOW (3-5 days) |
| H-24 | NoteUseCases lack subject-type polymorphic queries | MEDIUM | Functional | CRM Notes | LOW (3-5 days) |
| H-25 | ActivityUseCases may lack complete activity lifecycle | MEDIUM | Functional | CRM Activity | MEDIUM (1 week) |

**Total P3: 7 findings**

---

## Complete Defect Register (All 45 Findings)

| Priority | ID | Title | Severity | Category | Phase | Fix Complexity |
|----------|----|-------|----------|----------|-------|---------------|
| **P0** | C-01 | Mock adapters active by default (matchIfMissing=true) | CRITICAL | Production Risk | CRM-019 | LOW |
| **P0** | C-07 | refreshAllScores() hardcoded fake values | CRITICAL | Data Integrity | CRM-019 | MEDIUM |
| **P0** | C-03 | TransferUseCases.decide() throws on MULTI_APPROVER final approval | CRITICAL | Functional Defect | CRM-008B | MEDIUM |
| **P0** | C-04 | Event publication failures silently swallowed | CRITICAL | Data Integrity | CRM-019 | MEDIUM |
| **P0** | C-12 | No domain events for core entity operations | CRITICAL | Architecture | CRM-002-CRM-007 | HIGH |
| **P0** | C-05 | Missing FK constraints on 5 CRM-010 tables | CRITICAL | Data Integrity | CRM-019 | LOW |
| **P0** | C-08 | Hardcoded zero-UUID tenant | CRITICAL | Security | CRM-019 | MEDIUM |
| **P0** | H-13 | Misleading test name returns401 returns 200 | HIGH | Test Accuracy | General | LOW |
| **P1** | C-02 | LegacyCrmInfrastructureService god class (2044 lines) | CRITICAL | Architecture | CRM-003-CRM-007 | HIGH |
| **P1** | C-10 | V1/V2 controller duplication | CRITICAL | Architecture | CRM-008B | MEDIUM |
| **P1** | C-09 | Frontend snake_case vs backend camelCase | CRITICAL | Inconsistency | CRM-008/014/015/016 | HIGH |
| **P1** | C-11 | Inconsistent migration naming | CRITICAL | Standards | CRM-004 | LOW |
| **P1** | C-06 | Missing audit columns on 6 CRM-010 tables | CRITICAL | Compliance | CRM-019 | LOW |
| **P1** | I-03 | Duplicated auth context in 9 V1 controllers | HIGH | Duplication | CRM-003 | LOW |
| **P1** | I-08 | URL path hardcoding in AOP aspects | HIGH | Hardcoded | CRM-008 | LOW |
| **P1** | I-10 | DisabledHrmOwnershipAdapter active in all profiles | HIGH | Misconfiguration | CRM-008 | LOW |
| **P1** | H-09 | Only 3 E2E tests | HIGH | Testing | General | HIGH |
| **P1** | H-10 | Docker tests silently skipped | HIGH | Testing | General | MEDIUM |
| **P1** | H-11 | No CI job for CRM integration tests | HIGH | CI/CD | General | MEDIUM |
| **P1** | H-12 | Fragile reflection in tests | HIGH | Testing | General | MEDIUM |
| **P1** | H-15 | Low assertion quality, missing negative tests | HIGH | Testing | General | MEDIUM |
| **P1** | H-20 | Missing NOT NULL on criteria column | HIGH | Schema | CRM-019 | LOW |
| **P1** | H-04 | Hardcoded cache TTL and max size | HIGH | Configuration | CRM-019 | LOW |
| **P1** | H-05 | Hardcoded AI Gateway timeout | HIGH | Configuration | CRM-019 | LOW |
| **P1** | H-21 | No smoke tests or health checks | HIGH | Operations | General | MEDIUM |
| **P1** | H-16 | Missing API documentation for V2 endpoints | HIGH | Documentation | General | MEDIUM |
| **P1** | H-17 | Missing ADRs for key decisions | HIGH | Documentation | General | MEDIUM |
| **P1** | H-18 | Missing intelligence module documentation | HIGH | Documentation | CRM-019 | MEDIUM |
| **P1** | H-06 | Pipeline board no virtualization | MEDIUM | UI Performance | CRM-020 | MEDIUM |
| **P1** | H-07 | customer360() raw type suppression | MEDIUM | Type Safety | CRM-017 | MEDIUM |
| **P2** | I-01 | Anemic domain model | HIGH | Architecture | CRM-002 | HIGH |
| **P2** | H-14 | No @DisplayName on test classes | MEDIUM | Testing | General | LOW |
| **P2** | M-01 | No pagination on listCustomFields | MEDIUM | Performance | CRM-006 | LOW |
| **P2** | M-02 | Cursor pagination aspect reflection overhead | MEDIUM | Performance | General | LOW |
| **P2** | M-03 | No pagination for search and activity queries | MEDIUM | Performance | General | LOW |
| **P2** | M-04 | ScoreValueObjectsTest not exhaustive | MEDIUM | Testing | CRM-019 | LOW |
| **P2** | M-05 | OrganizationServiceTest fragile reflection | MEDIUM | Testing | General | LOW |
| **P2** | M-06 | No concurrent update test | MEDIUM | Testing | CRM-008 | LOW |
| **P2** | M-07 | Test method names > 80 chars | MEDIUM | Style | General | LOW |
| **P2** | M-08 | Missing requirements traceability | MEDIUM | Documentation | General | MEDIUM |
| **P2** | M-09 | Missing acceptance criteria documentation | MEDIUM | Documentation | General | MEDIUM |
| **P2** | M-10 | ReportsUseCases stub implementations | MEDIUM | Dead Code | Reports | LOW |
| **P2** | M-11 | SearchUseCases missing full-text search | MEDIUM | Incomplete | CRM Search | MEDIUM |
| **P2** | M-12 | Transactional boundary issues in outbox | MEDIUM | Data Integrity | CRM Integration | MEDIUM |
| **P2** | M-13 | Dead code assessment | MEDIUM | Dead Code | General | LOW |
| **P2** | H-19 | No production runbooks for CRM ops | MEDIUM | Documentation | General | MEDIUM |
| **P3** | I-09 | Assignment.recordType() null validation | LOW | Defensive | CRM-008 | LOW |
| **P3** | M-10b | Export controllers lack @RequireCapability | LOW | Security | CRM Exports | LOW |
| **P3** | C-11b | Constraint naming inconsistency | HIGH | Standards | CRM-009 | LOW |
| **P3** | H-22 | No unique constraint on duplicate segment memberships | MEDIUM | Schema | CRM-019 | LOW |
| **P3** | H-23 | TagUseCases lack bulk operations | MEDIUM | Functional | CRM Tags | LOW |
| **P3** | H-24 | NoteUseCases lack polymorphic queries | MEDIUM | Functional | CRM Notes | LOW |
| **P3** | H-25 | ActivityUseCases lifecycle completeness | MEDIUM | Functional | CRM Activity | MEDIUM |

---

## Priority Distribution

```
Priority Distribution by Count
P0:  ████████████ 8  (17.8%)
P1:  █████████████████████████████████ 22 (48.9%)
P2:  █████████████████████ 16 (35.6%)
P3:  ████████ 7  (15.6%)
```

```
Priority Distribution by Severity
           Critical    High    Medium    Low
P0:        7           1       0         0
P1:        5           16      2         0
P2:        0           1       13        0
P3:        0           1       3         3
```

---

## Effort Estimation Summary

| Scope | Estimated Effort | Team Size |
|-------|-----------------|-----------|
| P0 fixes only | 2-3 weeks | 2 engineers |
| P0 + P1 fixes | 6-10 weeks | 2-3 engineers |
| All findings (P0-P3) | 12-16 weeks | 2-3 engineers |

---

## Blocking Dependencies

The following findings block or are blocked by other work:

| Finding | Blocks | Blocked By |
|---------|--------|------------|
| C-01 (mock adapters) | G5 intelligence module work | None |
| C-07 (fake scores) | Scoring feature reliability | C-02 (god class contains scoring logic) |
| C-03 (MULTI_APPROVER) | Transfer workflow | None |
| C-04 (event failures) | Event-driven features in G5 | C-12 (no domain events) |
| C-12 (no domain events) | Event-driven architecture adoption | None |
| C-05 (FK constraints) | Data integrity | None |
| C-08 (zero-UUID) | Security/compliance | None |
| H-13 (misleading test) | Test reliability | None |
| C-02 (god class) | Clean architecture adoption | None (can be decomposed incrementally) |
| C-10 (V1/V2 duplication) | API consolidation | None |
| C-09 (naming mismatch) | Frontend/backend alignment | None |

---

## Recommended Fix Order

The following sequence minimizes risk and unblocks dependent work:

**Sprint 1-2: Stop the Bleeding (P0)**
1. Fix mock adapters (C-01)
2. Disable/fix refreshAllScores() (C-07)
3. Fix MULTI_APPROVER (C-03)
4. Fix event publisher (C-04)
5. Add FK constraints (C-05)
6. Fix zero-UUID (C-08)
7. Fix misleading test (H-13)
8. Add audit columns (C-06)

**Sprint 3-5: Foundation (P1)**
9. Begin LegacyCrmInfrastructureService decomposition (C-02)
10. Deprecate V1 controllers (C-10)
11. Plan naming alignment (C-09)
12. Standardize migration naming (C-11)
13. Add CI job for integration tests (H-11)
14. Add E2E tests for critical flows (H-09)
15. Externalize cache and timeout configs (H-04, H-05)
16. Add smoke tests and health checks (H-21)

**Sprint 5-8: Quality (P1 continued + P2)**
17. Centralize auth extraction (I-03)
18. Fix URL hardcoding in aspects (I-08)
19. Fix DisabledHrmOwnershipAdapter (I-10)
20. Replace reflection in tests (H-12)
21. Improve assertion quality, add negative tests (H-15)
22. Add NOT NULL constraint (H-20)
23. Implement domain events (C-12)
24. Begin domain model enrichment (I-01)

**Sprint 8-12: Polish (P2 + P3)**
25. Address remaining P2 testing and documentation findings
26. Address P3 findings
27. Implement full-text search (M-11)
28. Remove dead code (M-13, M-10)

---

*Report generated by independent forensic audit. 45 total findings across 4 priority levels.*
