# CRM-010 Execution Plan

> **Module:** CRM-010 — Customer 360 & Unified Customer Intelligence
> **Date:** 2026-07-29
> **Status:** STARTED
> **Authority:** Program Execution Coordinator

---

## 1. Executive Summary

CRM-010 transforms the SANAD platform from a CRM-centric view into a **unified Customer 360 platform** with AI-native customer intelligence. It builds on the existing basic Customer 360 read model (CRM-007) and the governed AI Gateway integration (CRM-009) to deliver a complete, AI-ready, cross-module view of every customer.

This sprint creates:
- A **unified customer profile** aggregating CRM data into one authoritative view
- A **customer intelligence engine** generating Health Score, CLV, Engagement Score, Risk Score, and Loyalty Score
- An **AI-native insight layer** supporting Next Best Action, Churn Prediction, and Opportunity Detection
- A **unified customer timeline** aggregating every interaction chronologically
- A **customer scoring engine** with configurable, auditable scoring models

---

## 2. Scope

### 2.1 In Scope

| # | Capability | Description |
|---|-----------|-------------|
| 1 | Unified Customer Profile | Single authoritative customer record across CRM |
| 2 | Customer Intelligence Engine | Health Score, CLV, Engagement Score, Risk Score, Loyalty Score |
| 3 | Unified Customer Timeline | Aggregated chronological interaction view |
| 4 | Customer Scoring Engine | Configurable, auditable scoring models |
| 5 | AI Insight Engine | Next Best Action, Churn Prediction, Opportunity Detection |
| 6 | REST APIs | Customer 360, Intelligence, Scores, Insights endpoints |
| 7 | Search Services | Customer search with intelligence filters |
| 8 | Analytics Services | Customer segment analytics, trend analysis |
| 9 | Workflow Integration | Score-triggered workflows via CRM-009 engine |
| 10 | Audit Integration | All intelligence operations audited |
| 11 | Notification Integration | Score threshold alerts |
| 12 | Reporting | Customer intelligence reports |
| 13 | Dashboards | Customer 360 dashboard with intelligence panels |
| 14 | RBAC | Capability-based access control |
| 15 | Testing | Unit, integration, PostgreSQL, contract tests |
| 16 | Production Readiness | Deployment, monitoring, rollback |

### 2.2 Out of Scope (Deferred)

| # | Item | Reason |
|---|------|--------|
| 1 | ERP/Accounting/HR data integration | External systems not yet implemented |
| 2 | Ecommerce/POS data integration | External systems not yet implemented |
| 3 | Real-time streaming analytics | Batch/scheduled scoring sufficient for v1 |
| 4 | Custom ML model training | Use AI Gateway's pre-trained models |
| 5 | Customer data platform (CDP) export | Separate future module |

### 2.3 Current State

| Asset | Status | Notes |
|-------|--------|-------|
| Customer360QueryPort | EXISTS (basic) | Read-only, no scores/insights |
| CustomerMasterRepository | EXISTS | Has customer_segment, customer_tier, risk_rating |
| AiGatewayPort | EXISTS (CRM-009) | Capability enum has NEXT_BEST_ACTION, SCORING |
| HttpAiGatewayAdapter | EXISTS (CRM-009) | Provider-neutral, fail-closed |
| Transactional Outbox | EXISTS (CRM-009) | Reusable for score-triggered actions |
| AuditPort | EXISTS | JdbcAuditAdapter |
| TimelineEventPort | EXISTS | JdbcTimelineEventAdapter |
| DashboardQueryPort | EXISTS (basic) | Simple KPIs, no intelligence |
| Latest Migration | V20260728_1 | CRM-010 starts at V20260729_1 |

---

## 3. Architecture Overview

CRM-010 follows the established DDD Hexagonal Architecture:

```
┌─────────────────────────────────────────────────────────────┐
│                      Web Layer (REST)                        │
│  Customer360Controller  IntelligenceController  SearchAPI   │
├─────────────────────────────────────────────────────────────┤
│                   Application Layer                          │
│  CustomerProfileUseCases  ScoringUseCases  InsightUseCases  │
│  TimelineAggregator  SegmentUseCases  AnalyticsUseCases     │
├─────────────────────────────────────────────────────────────┤
│                     Domain Layer                             │
│  CustomerProfile  HealthScore  CLV  EngagementScore          │
│  RiskScore  LoyaltyScore  Segment  NextBestAction            │
│  ScoringModel  ScoreSnapshot  IntelligenceEnvelope           │
├─────────────────────────────────────────────────────────────┤
│                  Infrastructure Layer                        │
│  JdbcCustomerProfileAdapter  JdbcScoringAdapter              │
│  JdbcTimelineAggregator  JdbcSegmentAdapter                  │
│  CustomerIntelligenceAiAdapter (uses AiGatewayPort)          │
│  ScoringOutboxWorker  InsightOutboxWorker                    │
├─────────────────────────────────────────────────────────────┤
│              Platform Integration Ports                      │
│  AuditPort  TimelineEventPort  AiGatewayPort                 │
│  WorkflowIntegrationPort  NotificationPort                   │
└─────────────────────────────────────────────────────────────┘
```

---

## 4. Agent Breakdown

| Agent | Role | Duration |
|-------|------|----------|
| Agent 1 | Architecture & Data Foundation | 2 days |
| Agent 2 | Domain Models & Repository Layer | 2 days |
| Agent 3 | Application Layer & Use Cases | 2 days |
| Agent 4 | REST API & RBAC | 1 day |
| Agent 5 | Platform Integration & AI Intelligence | 2 days |
| Agent 6 | QA & System Certification | 1 day |
| Agent 7 | Production Readiness | 1 day |
| Agent 8 | Final Closure Package | 1 day |
| Agent 9 | Official Governance Closure | 1 day |
| **Total** | | **13 days** |

---

## 5. Timeline

| Phase | Start | End | Agent |
|-------|-------|-----|-------|
| Foundation | 2026-07-30 | 2026-07-31 | Agent 1 |
| Domain | 2026-08-01 | 2026-08-02 | Agent 2 |
| Application | 2026-08-03 | 2026-08-04 | Agent 3 |
| API | 2026-08-05 | 2026-08-05 | Agent 4 |
| Integration | 2026-08-06 | 2026-08-07 | Agent 5 |
| QA | 2026-08-08 | 2026-08-08 | Agent 6 |
| Production | 2026-08-09 | 2026-08-09 | Agent 7 |
| Closure | 2026-08-10 | 2026-08-11 | Agent 8-9 |

---

## 6. Dependencies

| Dependency | Status | Notes |
|------------|--------|-------|
| CRM-007 (Customer Master) | ✅ CLOSED | Foundation data |
| CRM-008 (Team Management) | ✅ CLOSED | Ownership context |
| CRM-009 (Workflow & AI Gateway) | ✅ CLOSED | AI integration infrastructure |
| PostgreSQL 16 | ✅ AVAILABLE | Database platform |
| Spring Boot 3.5.6 | ✅ AVAILABLE | Application framework |
| Flyway | ✅ AVAILABLE | Forward-only migrations |
| External AI Gateway | ✅ AVAILABLE | Via CRM-009 port |

---

## 7. Success Criteria

| # | Criterion | Verification |
|---|-----------|-------------|
| 1 | Customer 360 architecture approved | Architecture blueprint |
| 2 | Unified customer profile defined | Domain model + migration |
| 3 | Cross-module integrations mapped | Integration readiness doc |
| 4 | AI capabilities identified | AI capability registry |
| 5 | Execution backlog complete | Implementation backlog |
| 6 | Governance plan approved | Agent breakdown + schedule |

---

## 8. Risk Summary

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| External module data unavailable | HIGH | MEDIUM | CRM-internal data only for v1 |
| AI Gateway latency | MEDIUM | MEDIUM | Async outbox pattern |
| Score model accuracy | MEDIUM | MEDIUM | Configurable, auditable models |
| Performance with large datasets | MEDIUM | HIGH | Pagination, caching, batch scoring |

---

## 9. Pre-Execution Artifacts

| Document | Status |
|----------|--------|
| CRM-010-CUSTOMER360-QUERYPORT-CONTRACT.md | ✅ APPROVED |
| CRM-010-AI-GATEWAY-CONTRACT.md | ✅ APPROVED |
| CRM-010-CUSTOMER360-ARCHITECTURE-ADR.md | ✅ ACCEPTED |
| CRM-010-DATABASE-MIGRATION.md | ✅ APPROVED |
| CRM-010-INTEGRATION-MOCKS.md | ✅ DEFINED |
| CRM-010-AGENT-DEPENDENCIES.md | ✅ DEFINED |
| CRM-010-STORY-GOVERNANCE.md | ✅ APPROVED |
| CRM-010-CRITICAL-PATH.md | ✅ DEFINED |
| CRM-010-AI-CAPABILITY-SPECS.md | ✅ APPROVED |

---

**Execution Plan Authority:** Program Execution Coordinator
**Date:** 2026-07-29
**Status:** ✅ STARTED
