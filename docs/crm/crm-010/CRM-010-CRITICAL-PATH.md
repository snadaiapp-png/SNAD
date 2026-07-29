# CRM-010 Critical Path Analysis

> **Module:** CRM-010 — Customer 360 & Unified Customer Intelligence
> **Date:** 2026-07-29
> **Status:** DEFINED

---

## 1. Critical Path

The critical path is the longest sequence of dependent tasks that determines the minimum project duration.

```
E1-002 (Migration)
  → E2-001 (CustomerProfile domain)
    → E2-002 (HealthScore domain)
      → E3-002 (ScoringUseCases)
        → E5-002 (HealthScoreAiCalculator)
          → E8-005 (ScoringOutboxWorker)
            → E10-003 (PostgreSQL scoring tests)
              → E11-006 (Production acceptance)
                → E12-004 (Governance closure)
```

**Critical Path Length: 10 tasks, 13 days**

---

## 2. Blocking Stories

| # | Story | Blocks | Reason |
|---|-------|--------|--------|
| 1 | E1-002 (Migration) | All E2+ stories | Schema must exist before domain |
| 2 | E2-001 (CustomerProfile) | E3-001, E3-002 | Domain model required for use cases |
| 3 | E3-002 (ScoringUseCases) | E5-002, E8-005 | Scoring logic required for AI + worker |
| 4 | E5-002 (HealthScoreAiCalculator) | E8-005, E10-005 | AI integration required before worker + tests |
| 5 | E8-005 (ScoringOutboxWorker) | E10-003 | Worker required for integration tests |
| 6 | E10-003 (PostgreSQL tests) | E11-006 | Tests must pass before production acceptance |
| 7 | E11-006 (Production acceptance) | E12-004 | Production must be validated before closure |

---

## 3. Milestones

| Milestone | Date | Critical Path Task |
|-----------|------|-------------------|
| M1: Schema Deployed | 2026-07-31 | E1-002 complete |
| M2: Domain Complete | 2026-08-02 | E2-002 complete |
| M3: Scoring Logic Complete | 2026-08-04 | E3-002 complete |
| M4: AI Integration Complete | 2026-08-07 | E5-002 complete |
| M5: Outbox Worker Complete | 2026-08-07 | E8-005 complete |
| M6: All Tests Pass | 2026-08-08 | E10-003 complete |
| M7: Production Ready | 2026-08-09 | E11-006 complete |
| M8: Governance Closed | 2026-08-11 | E12-004 complete |

---

## 4. Delivery Checkpoints

| Checkpoint | Date | Reviewer | Gate |
|------------|------|----------|------|
| CP1: Architecture Review | 2026-07-31 | Architecture | M1 |
| CP2: Domain Review | 2026-08-02 | Engineering | M2 |
| CP3: Integration Review | 2026-08-07 | Engineering | M4+M5 |
| CP4: QA Review | 2026-08-08 | QA Lead | M6 |
| CP5: Production Review | 2026-08-09 | Operations | M7 |
| CP6: Governance Review | 2026-08-11 | Product Owner | M8 |

---

## 5. Slack Analysis

| Agent | Duration | Slack | Notes |
|-------|----------|-------|-------|
| Agent 1 | 2 days | 0 | Critical path start |
| Agent 2 | 2 days | 0 | Blocks Agent 3 |
| Agent 3 | 2 days | 0 | Blocks Agent 4+5 |
| Agent 4 | 1 day | 1 day | Can overlap with Agent 5 start |
| Agent 5 | 2 days | 0 | Critical path |
| Agent 6 | 1 day | 0 | Blocks Agent 7 |
| Agent 7 | 1 day | 0 | Blocks Agent 8 |
| Agent 8 | 1 day | 0 | Blocks Agent 9 |
| Agent 9 | 1 day | 0 | End of critical path |

**Total Slack: 1 day** (on Agent 4 only)

---

## 6. Schedule Risks

| # | Risk | Probability | Impact | Affected Milestone |
|---|------|-------------|--------|-------------------|
| SR-01 | Migration design issues | LOW | HIGH | M1 |
| SR-02 | Domain model rework | MEDIUM | HIGH | M2 |
| SR-03 | AI Gateway integration issues | MEDIUM | HIGH | M4 |
| SR-04 | Test failures requiring rework | MEDIUM | MEDIUM | M6 |
| SR-05 | Performance issues in scoring | MEDIUM | HIGH | M6 |
| SR-06 | Production deployment issues | LOW | HIGH | M7 |

---

## 7. Recovery Scenarios

### 7.1 Migration Failure (SR-01)

| Step | Action |
|------|--------|
| 1 | Rollback to pre-migration backup |
| 2 | Fix migration script |
| 3 | Re-apply on staging |
| 4 | Validate postconditions |
| 5 | Re-apply on production |
| **Delay** | +1 day |

### 7.2 AI Gateway Integration Failure (SR-03)

| Step | Action |
|------|--------|
| 1 | Implement rule-based fallback calculators |
| 2 | Deploy with fallback active |
| 3 | Fix AI integration in parallel |
| 4 | Switch to AI when ready |
| **Delay** | +0 days (fallback maintains schedule) |

### 7.3 Test Failures (SR-04)

| Step | Action |
|------|--------|
| 1 | Identify failing tests |
| 2 | Fix production code (not tests) |
| 3 | Re-run affected test suite |
| 4 | Proceed to production when green |
| **Delay** | +0.5–1 day |

### 7.4 Performance Issues (SR-05)

| Step | Action |
|------|--------|
| 1 | Profile slow operations |
| 2 | Add missing indexes |
| 3 | Implement caching layer |
| 4 | Reduce batch size |
| **Delay** | +0.5 day |

---

## 8. Critical Path Optimization

| Optimization | Saving | Risk |
|-------------|--------|------|
| Parallelize Agent 4 + Agent 5 | 1 day | Integration complexity |
| Frontend in parallel with QA | 1 day | Low risk |
| Pre-build AI calculators during Agent 3 | 0.5 day | Medium risk |

**Optimized critical path: 11 days** (down from 13)

---

**Critical Path Authority:** Program Execution Coordinator
**Date:** 2026-07-29
**Status:** ✅ DEFINED
