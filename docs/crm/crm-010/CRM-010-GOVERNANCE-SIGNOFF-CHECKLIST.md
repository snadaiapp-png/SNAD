# CRM-010 Governance Signoff Checklist

**Date:** 2026-07-29
**Issue:** #705
**PR:** #818
**Prepared for:** Issue #705 Owner

---

## Checklist for Issue #705 Owner

Use this checklist to verify every governance requirement before authorizing the transition.

---

### Section 1: Technical Completion

| # | Requirement | Verified | Evidence |
|---|-------------|----------|----------|
| 1.1 | Domain layer implemented | ⬜ | 33 files in `intelligence/domain/` |
| 1.2 | Application layer implemented | ⬜ | 13 files in `intelligence/application/` |
| 1.3 | Infrastructure layer implemented | ⬜ | 11 files in `intelligence/infrastructure/` |
| 1.4 | Database migrations created | ⬜ | V20260729_1, V20260729_2 |
| 1.5 | Tests written and passing | ⬜ | 134/134 tests pass |

### Section 2: Architecture Review

| # | Requirement | Verified | Evidence |
|---|-------------|----------|----------|
| 2.1 | DDD principles followed | ⬜ | `CRM-010-ARCHITECTURE-REVIEW.md` |
| 2.2 | Hexagonal architecture | ⬜ | Port/adapter pattern verified |
| 2.3 | Dependency inversion | ⬜ | CachePort interface in domain layer |
| 2.4 | Tenant isolation | ⬜ | All queries include tenant_id |

### Section 3: Security Review

| # | Requirement | Verified | Evidence |
|---|-------------|----------|----------|
| 3.1 | No SQL injection | ⬜ | NamedParameterJdbcTemplate verified |
| 3.2 | Tenant isolation complete | ⬜ | All adapters verified |
| 3.3 | Authentication enforced | ⬜ | @RequireCapability on controllers |
| 3.4 | No sensitive data in logs | ⬜ | Log audit passed |
| 3.5 | Secrets externalized | ⬜ | No hardcoded values |

### Section 4: Performance Review

| # | Requirement | Verified | Evidence |
|---|-------------|----------|----------|
| 4.1 | Cache tenant-scoped | ⬜ | Keys include tenantId |
| 4.2 | Cache TTL appropriate | ⬜ | 5-minute TTL |
| 4.3 | AI timeout bounded | ⬜ | Configurable timeout |

### Section 5: Mandatory Deliverables

| # | Deliverable | Verified | File |
|---|-------------|----------|------|
| 5.1 | Baseline SHA and dependency inventory | ⬜ | `CRM-010-AGENT-DEPENDENCIES.md` |
| 5.2 | Endpoint/capability/tenant-isolation inventory | ⬜ | `CRM-010-ENDPOINT-CAPABILITY-INVENTORY.md` |
| 5.3 | Test architecture and CI gate map | ⬜ | `CRM-010-CI-REPORT.md` |
| 5.4 | Migration/recovery acceptance design | ⬜ | `CRM-010-MIGRATION-RECOVERY-DESIGN.md` |
| 5.5 | API/event compatibility strategy | ⬜ | `CRM-010-API-EVENT-COMPATIBILITY.md` |
| 5.6 | Localization and accessibility test matrix | ⬜ | `CRM-010-LOCALIZATION-ACCESSIBILITY.md` |
| 5.7 | Observability semantic conventions | ⬜ | `CRM-010-OBSERVABILITY-CONVENTIONS.md` |
| 5.8 | SLI/SLO/alert candidate package | ⬜ | `CRM-010-SLI-SLO-ALERTS.md` |
| 5.9 | Performance methodology and baselines | ⬜ | `CRM-010-PERFORMANCE-REVIEW.md` |
| 5.10 | Runbook and recovery guide | ⬜ | `CRM-010-RUNBOOK.md` |
| 5.11 | Risk register and traceability matrix | ⬜ | `CRM-010-RISK-REGISTER.md` |
| 5.12 | PR with preparation artifacts | ⬜ | PR #818 |

### Section 6: Acceptance Criteria

| # | Criterion | Verified | Evidence |
|---|-----------|----------|----------|
| 6.1 | Backlog maps to files | ⬜ | All 12 deliverables exist |
| 6.2 | No "production-ready" claims | ⬜ | grep returns no matches |
| 6.3 | No finding hidden | ⬜ | 10 waivers in document |
| 6.4 | Production gated | ⬜ | No deployment commits |

### Section 7: Governance Violations

| # | Violation | Verified | Evidence |
|---|-----------|----------|----------|
| 7.1 | F-01 resolved | ⬜ | AGENT-003-AUDIT.md updated |
| 7.2 | F-02 resolved | ⬜ | W-10 added to waiver |

### Section 8: Deferred Findings

| # | Finding | Waiver | Verified |
|---|---------|--------|----------|
| 8.1 | CRITICAL #5: Missing ADR | W-01 | ⬜ |
| 8.2 | CRITICAL #8: Domain validation | W-02 | ⬜ |
| 8.3 | HIGH #11: Missing API layer | W-03 | ⬜ |
| 8.4 | HIGH #15: Missing indexes | W-04 | ⬜ |
| 8.5 | HIGH #16: Unbounded queries | W-05 | ⬜ |
| 8.6 | HIGH #17: QueryPortAdapter | W-06 | ⬜ |
| 8.7 | HIGH #18: Correlation IDs | W-07 | ⬜ |
| 8.8 | HIGH #21: Dependency docs | W-08 | ⬜ |
| 8.9 | HIGH #22: Wrong test counts | W-09 | ⬜ |
| 8.10 | HIGH #23: Missing use cases | W-10 | ⬜ |

### Section 9: CI and Build

| # | Requirement | Verified | Evidence |
|---|-------------|----------|----------|
| 9.1 | Build compiles | ⬜ | mvn compile — BUILD SUCCESS |
| 9.2 | All tests pass | ⬜ | 134/134 |
| 9.3 | All CI checks pass | ⬜ | 25/25 |
| 9.4 | PR #818 mergeable | ⬜ | mergeable: MERGEABLE |

### Section 10: Final Authorization

| # | Requirement | Verified |
|---|-------------|----------|
| 10.1 | All sections above verified | ⬜ |
| 10.2 | Issue #705 updated to MERGE: AUTHORIZED | ⬜ |
| 10.3 | PR #818 approved for merge | ⬜ |

---

## Signoff

| Role | Name | Date | Signature |
|------|------|------|-----------|
| Issue #705 Owner | _________________ | __________ | _________________ |
| Technical Lead | _________________ | __________ | _________________ |
| Security Reviewer | _________________ | __________ | _________________ |

---

## Notes

- This checklist is prepared by the Governance Approval Coordinator.
- The Issue #705 owner must complete and sign off before authorizing merge.
- All evidence is available in `docs/crm/crm-010/`.

---

**Checklist Authority:** Governance Approval Coordinator
**Date:** 2026-07-29
