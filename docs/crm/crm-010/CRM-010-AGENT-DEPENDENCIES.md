# CRM-010 Agent Dependency Graph

> **Module:** CRM-010 — Customer 360 & Unified Customer Intelligence
> **Date:** 2026-07-29
> **Status:** DEFINED

---

## 1. Agent Execution Order

```
Agent 1 (Foundation)
    │
    ▼
Agent 2 (Domain)
    │
    ▼
Agent 3 (Application)
    │
    ▼
Agent 4 (API)
    │
    ▼
Agent 5 (Integration & AI)
    │
    ▼
Agent 6 (QA)
    │
    ▼
Agent 7 (Production)
    │
    ▼
Agent 8 (Closure)
    │
    ▼
Agent 9 (Governance)
```

---

## 2. Dependency Graph (DAG)

```
                    ┌─────────────┐
                    │   Agent 1   │
                    │ Foundation  │
                    └──────┬──────┘
                           │
              ┌────────────┼────────────┐
              ▼            │            │
       ┌──────────┐        │            │
       │ Agent 2  │        │            │
       │ Domain   │        │            │
       └────┬─────┘        │            │
            │              │            │
            ▼              │            │
       ┌──────────┐        │            │
       │ Agent 3  │        │            │
       │ App Layer│        │            │
       └────┬─────┘        │            │
            │              │            │
            ▼              │            │
       ┌──────────┐        │            │
       │ Agent 4  │────────┘            │
       │ API/RBAC │                     │
       └────┬─────┘                     │
            │                           │
            ▼                           │
       ┌──────────┐                     │
       │ Agent 5  │─────────────────────┘
       │Integr/AI │
       └────┬─────┘
            │
            ▼
       ┌──────────┐
       │ Agent 6  │
       │   QA     │
       └────┬─────┘
            │
            ▼
       ┌──────────┐
       │ Agent 7  │
       │ Prod     │
       └────┬─────┘
            │
            ▼
       ┌──────────┐
       │ Agent 8  │
       │ Closure  │
       └────┬─────┘
            │
            ▼
       ┌──────────┐
       │ Agent 9  │
       │Governance│
       └──────────┘
```

---

## 3. Parallel Execution Opportunities

| Opportunity | Agents | Condition |
|-------------|--------|-----------|
| Agent 4 (API) + Agent 5 (Integration) | 4, 5 | If Agent 3 delivers interfaces early, Agent 5 can start AI calculators in parallel with Agent 4's controllers |
| Frontend (E9) + Backend testing | Frontend, 6 | Frontend dashboard development can proceed in parallel with Agent 6 QA |

**Primary path remains sequential** to ensure clean integration points.

---

## 4. Merge Strategy

| Aspect | Strategy |
|--------|----------|
| Branching | Single feature branch `feature/CRM-010` |
| Merge model | Squash-merge to `develop` after all agents pass |
| Conflict prevention | Agents work on disjoint file sets (domain vs application vs web) |
| Integration point | Agent 5 (Integration) is the merge validation point |

---

## 5. Conflict Resolution Strategy

| Conflict Type | Resolution |
|---------------|------------|
| Domain model changes after Agent 2 | Agent 3 owns application layer; if domain changes needed, escalate to Agent 2 |
| API contract changes after Agent 4 | Agent 5 must adapt; if API contract breaks, escalate to Agent 4 |
| Migration conflicts | Single migration file per agent; Agent 1 owns schema |
| Test conflicts | Agent 6 owns all test files; production code owned by respective agents |

---

## 6. Deliverables per Agent

### Agent 1 — Architecture & Data Foundation

| Deliverable | Exit Criterion |
|-------------|----------------|
| V20260729_1 migration | ✅ Tables created, postconditions pass |
| V20260729_2 seed | ✅ 5 capabilities inserted |
| V20260729_3 seed | ✅ Default scoring models inserted |
| H2 test migration | ✅ Tests can run in-memory |
| CustomerIntelligenceProperties | ✅ Configuration class compiles |

### Agent 2 — Domain Models & Repository

| Deliverable | Exit Criterion |
|-------------|----------------|
| 8 domain model classes | ✅ Compiles, unit tests pass |
| 3 port interfaces | ✅ Compiles |
| 3 JDBC adapters | ✅ Integration tests pass |
| ScoreSnapshot factory | ✅ History recording works |

### Agent 3 — Application Layer

| Deliverable | Exit Criterion |
|-------------|----------------|
| 10 use case classes | ✅ Compiles, business logic verified |
| Timeline aggregator | ✅ Cross-module query works |
| Score threshold logic | ✅ Workflow trigger fires correctly |

### Agent 4 — REST API & RBAC

| Deliverable | Exit Criterion |
|-------------|----------------|
| 7 controller classes | ✅ All endpoints respond |
| @RequireCapability on all | ✅ Unauthorized → 403 |
| DTO classes | ✅ Request/response serialization |
| Pagination support | ✅ Page/size parameters work |

### Agent 5 — Integration & AI

| Deliverable | Exit Criterion |
|-------------|----------------|
| 6 AI calculators | ✅ AiGatewayPort integration verified |
| AuditPort integration | ✅ All operations audited |
| TimelineEventPort integration | ✅ All events recorded |
| ScoringOutboxWorker | ✅ Scheduled rescore works |
| 5 mock adapters | ✅ Return synthetic data |

### Agent 6 — QA

| Deliverable | Exit Criterion |
|-------------|----------------|
| 25+ test classes | ✅ All pass |
| PostgreSQL tests | ✅ Testcontainers pass in CI |
| Coverage report | ✅ ≥80% on intelligence module |

### Agent 7 — Production

| Deliverable | Exit Criterion |
|-------------|----------------|
| Production guard | ✅ Refuses startup with mocks |
| Monitoring config | ✅ Metrics exported |
| Runbooks | ✅ 10+ operational scripts |

### Agent 8 — Closure

| Deliverable | Exit Criterion |
|-------------|----------------|
| Evidence collection | ✅ All files inventoried |
| Traceability matrix | ✅ 75 stories mapped |
| Closure certificate | ✅ Certified |

### Agent 9 — Governance

| Deliverable | Exit Criterion |
|-------------|----------------|
| Baseline update | ✅ CRM-CURRENT-BASELINE.md updated |
| Approval matrix | ✅ 5/5 approvals |
| Final certification | ✅ CERTIFIED |

---

## 7. Exit Criteria Summary

| Agent | Must Achieve |
|-------|-------------|
| Agent 1 | Schema deployed, config compiles |
| Agent 2 | Domain layer compiles, repository tests pass |
| Agent 3 | Use cases compile, business logic verified |
| Agent 4 | All endpoints respond, RBAC enforced |
| Agent 5 | AI integration verified, audit/timeline emit |
| Agent 6 | All tests pass, coverage ≥80% |
| Agent 7 | Production guard active, deployment validated |
| Agent 8 | Closure package complete |
| Agent 9 | Baseline updated, governance certified |

---

**Agent Dependency Authority:** Program Execution Coordinator
**Date:** 2026-07-29
**Status:** ✅ DEFINED
