# CRM-010 Agent Breakdown

> **Module:** CRM-010 — Customer 360 & Unified Customer Intelligence
> **Date:** 2026-07-29
> **Status:** DEFINED

---

## 1. Agent Overview

| Agent | Role | Scope | Duration |
|-------|------|-------|----------|
| Agent 1 | Architecture & Data Foundation | Blueprint, migrations, config | 2 days |
| Agent 2 | Domain Models & Repository Layer | Domain, ports, adapters | 2 days |
| Agent 3 | Application Layer & Use Cases | Use cases, workers | 2 days |
| Agent 4 | REST API & RBAC | Controllers, DTOs, RBAC | 1 day |
| Agent 5 | Platform Integration & AI Intelligence | AI calculators, integrations | 2 days |
| Agent 6 | QA & System Certification | Tests, validation | 1 day |
| Agent 7 | Production Readiness | Deployment, monitoring | 1 day |
| Agent 8 | Final Closure Package | Evidence, traceability | 1 day |
| Agent 9 | Official Governance Closure | Baseline, certification | 1 day |

---

## 2. Agent 1 — Architecture & Data Foundation

### 2.1 Responsibilities

| # | Responsibility | Deliverable |
|---|----------------|-------------|
| 1 | Define architecture blueprint | CRM-010-ARCHITECTURE-BLUEPRINT.md |
| 2 | Create database migrations | V20260729_1 (6 tables, indexes) |
| 3 | Create H2 test migrations | H2 mirror |
| 4 | Seed RBAC capabilities | 5 capabilities |
| 5 | Define scoring model defaults | Default weights |
| 6 | Create configuration properties | CustomerIntelligenceProperties |

### 2.2 Output Files

| File | Description |
|------|-------------|
| V20260729_1__create_crm_customer_intelligence.sql | PostgreSQL migration |
| V20260729_1 (H2) | H2 test mirror |
| CustomerIntelligenceProperties.java | Configuration |

---

## 3. Agent 2 — Domain Models & Repository Layer

### 3.1 Responsibilities

| # | Responsibility | Deliverable |
|---|----------------|-------------|
| 1 | Implement CustomerProfile aggregate | Domain model |
| 2 | Implement 5 score value objects | Health, CLV, Engagement, Risk, Loyalty |
| 3 | Implement Segment domain | Segment, SegmentMembership |
| 4 | Implement NextBestAction domain | Domain model |
| 5 | Implement ScoringModel domain | Configurable model |
| 6 | Implement repository ports | CustomerIntelligenceQueryPort, ScoringPort, SegmentPort |
| 7 | Implement JDBC adapters | 3 adapters |

---

## 4. Agent 3 — Application Layer & Use Cases

### 4.1 Responsibilities

| # | Responsibility | Deliverable |
|---|----------------|-------------|
| 1 | CustomerProfileUseCases | Read unified profile |
| 2 | ScoringUseCases | Calculate scores |
| 3 | RescoreUseCases | Manual rescore trigger |
| 4 | SegmentUseCases | Segment management |
| 5 | NextBestActionUseCases | NBA lifecycle |
| 6 | TimelineAggregatorUseCases | Unified timeline |
| 7 | AnalyticsUseCases | Segment/trend analytics |
| 8 | ScoreThresholdUseCases | Workflow triggers |

---

## 5. Agent 4 — REST API & RBAC

### 5.1 Responsibilities

| # | Responsibility | Deliverable |
|---|----------------|-------------|
| 1 | Customer360Controller | Profile endpoints |
| 2 | IntelligenceController | Score endpoints |
| 3 | ScoreController | Score details/history |
| 4 | InsightController | AI insights |
| 5 | SegmentController | Segment management |
| 6 | AnalyticsController | Analytics endpoints |
| 7 | SearchController | Customer search |
| 8 | Enforce RBAC | @RequireCapability |

---

## 6. Agent 5 — Platform Integration & AI Intelligence

### 6.1 Responsibilities

| # | Responsibility | Deliverable |
|---|----------------|-------------|
| 1 | CustomerIntelligenceAiCapabilities | Capability registry |
| 2 | HealthScoreAiCalculator | Via AiGatewayPort |
| 3 | ChurnPredictionAiCalculator | Via AiGatewayPort |
| 4 | NextBestActionAiCalculator | Via AiGatewayPort |
| 5 | AuditPort integration | All operations audited |
| 6 | TimelineEventPort integration | All events recorded |
| 7 | WorkflowIntegrationPort | Score thresholds |
| 8 | ScoringOutboxWorker | Scheduled rescoring |

---

## 7. Agent 6 — QA & System Certification

### 7.1 Responsibilities

| # | Responsibility | Deliverable |
|---|----------------|-------------|
| 1 | Domain unit tests | 8 test classes |
| 2 | Integration tests (H2) | 5 test classes |
| 3 | PostgreSQL scoring tests | 4 test classes |
| 4 | API controller tests | 4 test classes |
| 5 | AI contract tests | 3 test classes |
| 6 | RBAC security tests | 3 test classes |
| 7 | Migration tests | 2 test classes |

---

## 8. Agent 7 — Production Readiness

### 8.1 Responsibilities

| # | Responsibility | Deliverable |
|---|----------------|-------------|
| 1 | Production guard | Intelligence stub guard |
| 2 | Monitoring config | Score metrics |
| 3 | Deployment validation | 5 platforms |
| 4 | Rollback procedures | Documented |
| 5 | Operational runbooks | 10+ scripts |

---

## 9. Agent 8 — Final Closure Package

### 9.1 Responsibilities

| # | Responsibility | Deliverable |
|---|----------------|-------------|
| 1 | Evidence collection | Evidence doc |
| 2 | Traceability matrix | 75 stories |
| 3 | Quality summary | Quality report |
| 4 | Risk review | Risk assessment |
| 5 | Executive summary | Executive report |
| 6 | Final certificate | Closure certificate |

---

## 10. Agent 9 — Official Governance Closure

### 10.1 Responsibilities

| # | Responsibility | Deliverable |
|---|----------------|-------------|
| 1 | Final certification | Technical cert |
| 2 | Official closure record | Closure record |
| 3 | Approval matrix | 5 approvals |
| 4 | Baseline update | CRM-CURRENT-BASELINE.md |
| 5 | Governance validation | Validation record |

---

**Agent Breakdown Authority:** Program Execution Coordinator
**Date:** 2026-07-29
**Status:** ✅ DEFINED
