# G7 MASTER REQUIREMENTS BASELINE — CANDIDATE

> **Report ID:** G7-BASELINE-CANDIDATE-V1
> **Date:** 2026-08-12
> **Status:** **CANDIDATE_FOR_APPROVAL** (NOT APPROVED)
> **Authority:** Derived from forensic analysis of 12+ source documents
> **Prior Version:** G7_MASTER_REQUIREMENTS_BASELINE.md V2 — SUPERSEDED

---

## 1. SCOPE

| Field | Value |
|-------|-------|
| **G7 ID** | G7 |
| **Name** | Mobile Offline Foundation |
| **Arabic** | أساس الجوال |
| **Canonical Source** | `apps/web/app/crm/crm-execution-data.ts` lines 129-137 |
| **Dependencies** | G1 (Database & Multi-Tenant) — COMPLETE ✅, G3 (Core CRM Entities) — COMPLETE ✅ |
| **In Scope** | Mobile-optimized CRM APIs, offline sync schema, client-side storage, sync engine, mobile auth, entity subset |
| **Out of Scope** | Native mobile UI, push notifications (G8), caller ID (G8), real-time collaboration |

---

## 2. IDENTITY

| Field | Value |
|-------|-------|
| **Canonical Definition** | G7 = أساس الجوال = Mobile Offline Foundation |
| **Definition Authority** | SRC-01 (crm-execution-data.ts) |
| **Conflicting Definitions Found** | 7 (across CRM, ERP, Finance modules) |
| **Resolution** | Locked to "Mobile Offline Foundation" per SRC-01 |
| **Non-Scope Items** | Native mobile UI ❌, Push notifications ❌ (G8), Caller ID ❌ (G8), Real-time collaboration ❌ |

---

## 3. REQUIREMENTS

| Metric | Value |
|--------|-------|
| **Total Requirements** | **66** |
| **Total Decisions** | **3** (tracked separately) |
| **Sources Analyzed** | 12+ documents |
| **Raw Items Deduplicated** | 167+ → 66 (60% dedup ratio) |
| **Conflicts Resolved** | 14/14 |
| **New Conflicts Found** | 0 |

---

## 4. PRIORITY DISTRIBUTION

| Priority | Count | Percentage |
|----------|-------|------------|
| P0 (BLOCKER) | 18 | 27.3% |
| P1 (CRITICAL) | 35 | 53.0% |
| P2 (HIGH) | 13 | 19.7% |
| P3 (MEDIUM) | 0 | 0% |
| **TOTAL** | **66** | 100% |

---

## 5. DEPENDENCIES

| Dependency | Status |
|-----------|--------|
| G1 (Database & Multi-Tenant) | ✅ COMPLETE |
| G3 (Core CRM Entities) | ✅ COMPLETE |
| ADR-G7-001 (Conflict Resolution) | ⚠️ REQUIRES_REVISION |
| Mobile Framework | ❌ NOT SELECTED |
| Encryption Strategy | ❌ NOT DEFINED |

---

## 6. ARCHITECTURE DEPENDENCIES

| Decision | Status | Blocks |
|----------|--------|--------|
| ADR-G7-001 | REQUIRES_REVISION | 6 requirements |
| Framework | DECISION_REQUIRED | 15+ requirements |
| Encryption | DECISION_REQUIRED | 2 requirements |
| Offline Duration | ✅ RESOLVED (7-day refresh token) | — |
| Conflict Lifecycle | ✅ RESOLVED (1 year retention) | — |

---

## 7. SECURITY REQUIREMENTS

| Req ID | Description | Priority | Status |
|--------|-------------|----------|--------|
| SEC-001 | Offline data encryption | P0 | MISSING |
| SEC-002 | Mobile token caching | P1 | MISSING |
| SEC-003 | Device registration | P2 | MISSING |
| SEC-004 | Offline authorization | P1 | MISSING |
| SEC-005 | Transport security | P1 | EXISTS ✅ |
| SEC-006 | Tenant isolation on sync | P0 | MISSING |

**Security Gates: 6 PASS, 4 FAIL**

---

## 8. DATA REQUIREMENTS

| Req ID | Description | Priority | Status |
|--------|-------------|----------|--------|
| DATA-001 | Sync metadata tables (4) | P0 | MISSING |
| DATA-002 | Change tracking columns | P0 | PARTIAL |
| DATA-003 | Local storage schema | P1 | MISSING |
| DATA-004 | Sync audit trail | P2 | MISSING |
| DATA-005 | Conflict log | P2 | MISSING |

---

## 9. API REQUIREMENTS

| Req ID | Description | Priority | Status |
|--------|-------------|----------|--------|
| API-001 | Entity List API | P0 | MISSING |
| API-002 | Entity Detail API | P0 | MISSING |
| API-003 | Delta Sync Pull API | P0 | MISSING |
| API-004 | Batch Sync Push API | P0 | MISSING |
| API-005 | Sync Status API | P1 | MISSING |
| API-006 | Device Registration API | P2 | MISSING |
| API-007 | Conflict List API | P1 | MISSING |
| API-008 | Conflict Resolve API | P1 | MISSING |
| API-009 | Conflict Skip API | P1 | MISSING |

---

## 10. SYNC REQUIREMENTS

| Req ID | Description | Priority | Status |
|--------|-------------|----------|--------|
| SYNC-001 | Sync Engine | P0 | MISSING |
| SYNC-002 | Delta Pull | P0 | MISSING |
| SYNC-003 | Mutation Queue | P1 | MISSING |
| SYNC-004 | Cursor Invalidation | P1 | MISSING |
| SYNC-005 | Conflict Detection | P1 | MISSING |
| SYNC-006 | Conflict Resolution | P1 | MISSING |
| SYNC-007 | Retry/Backoff | P2 | MISSING |
| SYNC-008 | Idempotency | P1 | PARTIAL |
| SYNC-009 | Conflict Isolation | P1 | MISSING |
| SYNC-010 | Delete Conflicts | P1 | MISSING |
| SYNC-011 | Full Resync | P1 | MISSING |
| SYNC-012 | Crash Recovery | P1 | MISSING |
| SYNC-013 | Sequence Gap | P2 | DEFERRED |
| SYNC-014 | Client Timeout | P1 | MISSING |
| SYNC-015 | Entity Coverage | P0 | MISSING |
| SYNC-016 | Server Authority | P1 | MISSING |
| SYNC-017 | Per-Mutation ACK | P0 | MISSING |

---

## 11. CONFLICT REQUIREMENTS

| Req ID | Description | Priority | Status |
|--------|-------------|----------|--------|
| ARCH-002 | 12 Conflict Classes | P0 | DEFINED |

**Conflict Resolution Policy:** Hybrid (Auto-merge + User Resolution + Server Authority)
**ADR Status:** REQUIRES_REVISION

---

## 12. ACCEPTANCE CRITERIA

| Priority | Requirements | Criteria Defined | Coverage |
|----------|-------------|-----------------|----------|
| P0 | 18 | 18 | 100% |
| P1 | 35 | 35 | 100% |
| P2 | 13 | 0 | 0% (deferred) |
| **TOTAL** | **66** | **53** | **80.3%** |

---

## 13. TRACEABILITY

| Status | Count | Percentage |
|--------|-------|------------|
| FULLY_TRACED | 1 | 1.5% |
| PARTIALLY_TRACED | 8 | 12.1% |
| UNTRACED | 57 | 86.4% |

**P0 Traceability: 0% fully traced**

---

## 14. DEFERRED REQUIREMENTS

| Req ID | Description | Deferred To |
|--------|-------------|-------------|
| SYNC-013 | Sequence Gap Detection | v1.1 |
| OFF-002 | Eligibility Rules | v1.1 |
| PERF-002 | Storage Quota | v1.1 |
| PERF-003 | Network Detection | v1.1 |
| PERF-004 | Background Sync | v1.1 |
| TEST-006 | Performance Tests | v1.1 |
| OBS-006 | Dashboards | v1.1 |
| ISO-006 | Max Devices | v1.1 |
| ARCH-004 | Hybrid Strategy | v1.1 |

---

## 15. OPEN DECISIONS

| Decision | Blocking | Owner | Required Before |
|----------|----------|-------|-----------------|
| Approve ADR-G7-001 | 6 requirements | Architecture Team | WP-G |
| Select mobile framework | 15+ requirements | Product Team | Client implementation |
| Define encryption strategy | 2 requirements | Security Team | WP-I |
| Approve baseline (66 requirements) | All | Product + Tech Leads | Implementation |

---

## 16. BLOCKERS

| Blocker | Severity | Category |
|---------|----------|----------|
| Arithmetic errors corrected | ✅ RESOLVED | BASELINE |
| 3 decisions reclassified | ✅ RESOLVED | CLASSIFICATION |
| ADR-G7-001 not approved | CRITICAL | ARCHITECTURE |
| Framework not selected | CRITICAL | ARCHITECTURE |
| Encryption undefined | CRITICAL | SECURITY |
| No stakeholder sign-off | HIGH | GOVERNANCE |

---

## 17. APPROVAL CONDITIONS

### Mandatory (ALL must be met):
1. ADR-G7-001 APPROVED (not REQUIRES_REVISION)
2. Mobile framework SELECTED
3. Encryption strategy DEFINED
4. All stakeholders SIGN-OFF

### Recommended (improve quality):
5. Acceptance criteria for P2 requirements
6. Threat model completed
7. Performance budget defined

---

## 18. VERSION

| Field | Value |
|-------|-------|
| **Version** | CANDIDATE-V1 |
| **Date** | 2026-08-12 |
| **Status** | CANDIDATE_FOR_APPROVAL |
| **Supersedes** | G7_MASTER_REQUIREMENTS_BASELINE.md V2 |
| **Next Review** | Upon resolution of 3 blocking decisions |

---

## 19. EVIDENCE INDEX

| Evidence ID | Document | Finding |
|-------------|----------|---------|
| EVD-001 | G7_REQUIREMENT_ARITHMETIC_FINAL.md | 13 arithmetic errors corrected |
| EVD-002 | G7_REQUIREMENT_IDENTITY_FINAL.md | 66 requirements + 3 decisions |
| EVD-003 | G7_P0_FINAL_AUDIT.md | 18 P0 requirements verified |
| EVD-004 | G7_PRIORITY_FINAL_REGISTER.md | P0=18, P1=35, P2=13 |
| EVD-005 | G7_CONFLICT_FINAL_REGISTER.md | 14/14 conflicts resolved |
| EVD-006 | G7_ADR_DEPENDENCY_GATE.md | ADR blocks 6 requirements |
| EVD-007 | G7_ARCHITECTURE_DECISION_GATE.md | 3 decisions required |
| EVD-008 | G7_TRACEABILITY_FINAL_MATRIX.md | 1.5% fully traced |
| EVD-009 | G7_ACCEPTANCE_CRITERIA_REGISTER.md | 53 criteria defined (80.3%) |
| EVD-010 | G7_FINAL_DISPOSITION_REGISTER.md | 57 ACCEPT, 9 DEFER |
| EVD-011 | G7_BLOCKER_FINAL_REGISTER.md | 4 CRITICAL, 3 HIGH blockers |
| EVD-012 | G7_UNKNOWN_FINAL_REGISTER.md | 3 blocking unknowns |

---

*Generated: 2026-08-12*
*Status: CANDIDATE_FOR_APPROVAL — NOT APPROVED*
