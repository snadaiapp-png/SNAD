# G7 M7 — DECISION CONFLICT REGISTER

> **Report ID:** G7-M7-CONFLICT-REGISTER-V1
> **Date:** 2026-08-12
> **Status:** COMPLETE
> **Purpose:** Identify any conflicts between G7 baseline, ADR, architecture docs, security docs, existing implementation, and previous Mission outputs

---

## 1. CONFLICT SEARCH SCOPE

Searched for conflicts between:
- G7 baseline (66 requirements)
- ADR-G7-001 (conflict resolution policy)
- Architecture docs (C2/C3 decisions, existing architecture)
- Security docs (existing security patterns)
- Existing implementation (source code)
- Previous Mission outputs (Mission 4, 5, 6)

---

## 2. CONFLICT ANALYSIS

### 2.1 G7 Baseline vs ADR-G7-001

| Aspect | Baseline Claim | ADR Claim | Conflict? |
|--------|---------------|-----------|-----------|
| Conflict resolution approach | 12 classes (C1-C12) | Hybrid Policy (Option I) | ✅ CONSISTENT — ADR defines the 12 classes |
| Entity-specific policies | Not defined in baseline | 10 entities with specific policies | ✅ CONSISTENT — ADR extends baseline |
| Server authority for critical data | Not defined in baseline | Server Authority + Reject + Manual | ✅ CONSISTENT — ADR adds preemptive policy |
| Push-only entities | Not defined in baseline | Activity, Note = Push-Only | ✅ CONSISTENT — ADR defines per-entity |
| Pull-only entities | Not defined in baseline | Pipeline, Tags, Custom Fields = Reject + User Resolution | ⚠️ MINOR — ADR revised from Pull-Only to Reject + User Resolution (documented in revision notes) |

**CONFLICT-001: MINOR** — ADR revision changed Pipeline/Tags/Custom Fields from Pull-Only to Reject + User Resolution. This is documented in ADR revision notes (lines 479-489) and is a legitimate correction based on source code validation. NOT a conflict — it's a documented correction.

### 2.2 G7 Baseline vs C2/C3 Decisions

| Aspect | Baseline Claim | C2/C3 Claim | Conflict? |
|--------|---------------|-------------|-----------|
| Offline duration | Not specified in baseline | 7-day refresh token (Option B) | ✅ CONSISTENT — C2 defines what baseline doesn't |
| Conflict retention | Not specified in baseline | 1 year retention (Option C) | ✅ CONSISTENT — C3 defines what baseline doesn't |
| ADR status | REQUIRES_REVISION | ADR_STATUS = REQUIRES_REVISION | ✅ CONSISTENT |

**NO CONFLICTS FOUND.**

### 2.3 G7 Baseline vs Existing Implementation

| Aspect | Baseline Claim | Implementation Claim | Conflict? |
|--------|---------------|---------------------|-----------|
| Version column | All CRM entities have version BIGINT | ✅ Validated in ADR (Flyway migrations) | ✅ CONSISTENT |
| ETag/If-Match | Required for PATCH endpoints | ✅ Validated in ADR (ETagService.java) | ✅ CONSISTENT |
| Idempotency | Required for POST endpoints | ✅ Validated in ADR (IdempotencyService.java) | ✅ CONSISTENT |
| Audit trail | All mutations logged | ✅ Validated in ADR (PlatformAuditWriter.java) | ✅ CONSISTENT |
| CRM_CONCURRENCY_CONFLICT | HTTP 412 for stale mutations | ✅ Validated in ADR (CrmErrorCode.java) | ✅ CONSISTENT |

**NO CONFLICTS FOUND.**

### 2.4 G7 Baseline vs Previous Mission Outputs

| Aspect | Mission 5 Claim | Mission 6 Claim | Mission 7 Claim | Conflict? |
|--------|----------------|-----------------|-----------------|-----------|
| Total requirements | 66 | 66 | 66 | ✅ CONSISTENT |
| P0 count | 18 | 18 | 18 | ✅ CONSISTENT |
| P1 count | 35 | 35 | 35 | ✅ CONSISTENT |
| P2 count | 13 | 13 | 13 | ✅ CONSISTENT |
| P3 count | 0 | 0 | 0 | ✅ CONSISTENT |
| Disposition APPROVED | 18 | 18 | 18 | ✅ CONSISTENT |
| Disposition DEFERRED | 9 | 9 | 9 | ✅ CONSISTENT |
| Disposition BLOCKED | 39 | 39 | 39 | ✅ CONSISTENT |
| Conflicts resolved | 14/14 | 14/14 | 14/14 | ✅ CONSISTENT |
| Critical blockers | 4 | 4 | 4 | ✅ CONSISTENT |
| Blocking unknowns | 3 | 3 | 3 | ✅ CONSISTENT |
| ADR status | REQUIRES_REVISION | REQUIRES_REVISION | REQUIRES_REVISION | ✅ CONSISTENT |
| Baseline status | NOT_APPROVED | NOT_APPROVED | NOT_APPROVED | ✅ CONSISTENT |

**NO CONFLICTS FOUND.**

### 2.5 ADR-G7-001 vs Existing Server Architecture

| Aspect | ADR Claim | Server Architecture | Conflict? |
|--------|-----------|---------------------|-----------|
| No LWW | ADR rejects LWW | 13-TESTING-AUDIT.md line 290: "Last-write-wins scenario is prevented" | ✅ CONSISTENT |
| No Client Wins | ADR rejects Client Wins | Existing pattern: reject stale mutations | ✅ CONSISTENT |
| Optimistic concurrency | ADR extends existing pattern | All UPDATE statements use WHERE version = :expectedVersion | ✅ CONSISTENT |
| Tenant isolation on sync | ADR requires RLS | Existing RLS pattern on all CRM tables | ✅ CONSISTENT |

**NO CONFLICTS FOUND.**

---

## 3. CONFLICT SUMMARY

| Conflict ID | Severity | Description | Resolution |
|-------------|----------|-------------|------------|
| CONFLICT-001 | MINOR | ADR revised Pipeline/Tags/Custom Fields from Pull-Only to Reject + User Resolution | Documented in ADR revision notes. Legitimate correction. NOT a true conflict. |

**TOTAL CONFLICTS: 0 BLOCKING, 1 MINOR (resolved via documentation)**

---

## 4. DECISION CONFLICT VERDICT

```
CONFLICTS_FOUND = 1 (MINOR, resolved)
BLOCKING_CONFLICTS = 0
NEW_CONFLICTS_SINCE_MISSION6 = 0
DECISION_CONFLICT_STATUS = PASS
```

**No conflicts exist between the G7 baseline, ADR, architecture decisions, existing implementation, and previous Mission outputs.**

---

*Generated: 2026-08-12*
*G7 Mission 7 — Decision Conflict Register*
