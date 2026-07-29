# CRM-010 Risk Register

> **Module:** CRM-010 — Customer 360 & Unified Customer Intelligence
> **Date:** 2026-07-29
> **Status:** DEFINED

---

## 1. Risk Overview

| Metric | Value |
|--------|-------|
| Total Risks | 14 |
| Critical | 1 |
| High | 4 |
| Medium | 5 |
| Low | 4 |

---

## 2. Critical Risks

| # | Risk | Probability | Impact | Mitigation | Owner |
|---|------|-------------|--------|------------|-------|
| R-01 | AI Gateway unavailable during scoring | MEDIUM | CRITICAL | Fail-closed design, cached scores, outbox retry | Agent 5 |

---

## 3. High Risks

| # | Risk | Probability | Impact | Mitigation | Owner |
|---|------|-------------|--------|------------|-------|
| R-02 | External module data unavailable | HIGH | HIGH | CRM-internal data only for v1 | Agent 1 |
| R-03 | Score model inaccuracy | MEDIUM | HIGH | Configurable models, audit trail, AI-assisted | Agent 5 |
| R-04 | Performance with large datasets | MEDIUM | HIGH | Batch scoring, pagination, indexing | Agent 3 |
| R-05 | AI latency exceeds timeout | MEDIUM | HIGH | Async outbox, configurable timeout | Agent 5 |

---

## 4. Medium Risks

| # | Risk | Probability | Impact | Mitigation | Owner |
|---|------|-------------|--------|------------|-------|
| R-06 | Score drift over time | HIGH | MEDIUM | Scheduled rescoring, threshold alerts | Agent 3 |
| R-07 | Segment assignment conflicts | LOW | MEDIUM | Atomic transitions, idempotency | Agent 3 |
| R-08 | AI bias in scoring | LOW | MEDIUM | Human confirmation, audit trail, configurable | Agent 5 |
| R-09 | Concurrent rescoring | MEDIUM | MEDIUM | Optimistic locking, claim tokens | Agent 3 |
| R-10 | Timeline aggregation slowness | MEDIUM | MEDIUM | Materialized views, caching | Agent 3 |

---

## 5. Low Risks

| # | Risk | Probability | Impact | Mitigation | Owner |
|---|------|-------------|--------|------------|-------|
| R-11 | Frontend dashboard performance | LOW | LOW | Pagination, lazy loading | Frontend |
| R-12 | Search index staleness | LOW | LOW | Refresh on write, periodic reindex | Agent 4 |
| R-13 | Configuration errors | MEDIUM | LOW | Production guard, validation | Agent 7 |
| R-14 | Documentation gaps | LOW | LOW | Automated generation | Agent 8 |

---

## 6. Risk Assessment Matrix

|  | Low Impact | Medium Impact | High Impact | Critical Impact |
---|------------|---------------|-------------|-----------------|
| **High Probability** | — | R-06 | R-02 | — |
| **Medium Probability** | R-13 | R-09, R-10 | R-03, R-04, R-05 | R-01 |
| **Low Probability** | R-11, R-12, R-14 | R-07, R-08 | — | — |

---

## 7. Risk Mitigation Strategies

### 7.1 Fail-Closed AI Design

All AI operations through CRM-009's governed infrastructure:
- AiGatewayPort with fail-closed adapters
- Transactional outbox for async processing
- Human confirmation for actionable outputs
- Configurable timeouts

### 7.2 Configurable Scoring Models

- Tenant-specific weight configuration
- Version-controlled model definitions
- Score change audit trail
- Threshold-based alerts

### 7.3 Performance Safeguards

- Batch scoring (configurable batch size)
- Database indexing on all query paths
- Pagination on all list endpoints
- Materialized views for analytics

### 7.4 Data Scope

- CRM-internal data only for v1
- External module integration deferred
- Clear extension points for future data sources

---

## 8. Risk Monitoring

| Metric | Threshold | Action |
|--------|-----------|--------|
| AI Gateway failures | > 10% | Alert engineering |
| Score calculation time | > 5s per customer | Optimize model |
| Outbox dead letters | > 5/hour | Alert operations |
| Concurrent rescoring conflicts | > 10/hour | Review locking |
| Timeline query latency | > 2s | Add caching |

---

**Risk Register Authority:** Program Execution Coordinator
**Date:** 2026-07-29
**Status:** ✅ DEFINED

---

## 9. Traceability Matrix

### 9.1 Risk → Requirement → Test → Code Traceability

| Risk ID | Risk Description | Requirement Source | Test Coverage | Code Evidence |
|---------|-----------------|-------------------|---------------|---------------|
| R-01 | AI Gateway unavailable during scoring | CRM-010-F2: AI integration reliability | `AiScoreOrchestratorTest` (6 tests) | `AiScoreOrchestrator.java` — fail-closed design, cached scores, outbox retry |
| R-02 | External module data unavailable | CRM-010-F1: Data scope definition | `CustomerIntelligenceIntegrationTest` (9 tests) | `Customer360ApplicationService.java` — CRM-internal data only |
| R-03 | Score model inaccuracy | CRM-010-F2: Scoring model configuration | `CustomerScoringServiceTest` (9 tests) | `ScoringModel.java`, `JdbcScoringAdapter.java` — configurable weights, audit trail |
| R-04 | Performance with large datasets | CRM-010-F2: Performance baselines | `CustomerIntelligenceCacheTest` (7 tests) | `CustomerIntelligenceCache.java` — Caffeine cache, 5-min TTL, 10K max |
| R-05 | AI latency exceeds timeout | CRM-010-F2: AI timeout handling | `AiScoreOrchestratorTest` (6 tests) | `AiScoreOrchestrator.java` — configurable timeout, fallback |
| R-06 | Score drift over time | CRM-010-F2: Score refresh scheduling | `CustomerScoringServiceTest` (9 tests) | `CustomerScoringService.java` — `refreshAllScores()`, threshold alerts |
| R-07 | Segment assignment conflicts | CRM-010-F1: Segment management | `CustomerSegmentationServiceTest` (10 tests) | `JdbcSegmentAdapter.java` — atomic transitions, idempotency |
| R-08 | AI bias in scoring | CRM-010-F2: AI governance | `AiScoreOrchestratorTest` (6 tests) | `AiScoreOrchestrator.java` — human confirmation, audit trail |
| R-09 | Concurrent rescoring | CRM-010-F1: Concurrency control | `CustomerScoringServiceTest` (9 tests) | `JdbcScoringAdapter.java` — RETURNING clause, claim tokens |
| R-10 | Timeline aggregation slowness | CRM-010-F2: Event processing | `CustomerIntelligenceEventsTest` (11 tests) | `SpringCustomerIntelligenceEventPublisher.java` — async event publication |
| R-11 | Frontend dashboard performance | CRM-010-F1: UI performance | `Playwright E2E` (CI) | Frontend pagination, lazy loading |
| R-12 | Search index staleness | CRM-010-F2: Index refresh | `CrmPostgresMigrationTest` (CI) | Database indexes on all query paths |
| R-13 | Configuration errors | CRM-010-F2: Configuration validation | `CustomerIntelligencePropertiesTest` (3 tests) | `CustomerIntelligenceProperties.java` — typed configuration |
| R-14 | Documentation gaps | CRM-010-F2: Documentation completeness | N/A (meta-risk) | 34 documentation files in `docs/crm/crm-010/` |

### 9.2 Requirement → Deliverable Traceability

| Requirement (Issue #705) | Deliverable | Document |
|--------------------------|-------------|----------|
| CRM-010-F1: Tenant-isolation matrix | Endpoint/capability/tenant-isolation inventory | `CRM-010-ENDPOINT-CAPABILITY-INVENTORY.md` |
| CRM-010-F1: Capability coverage | Endpoint/capability/tenant-isolation inventory | `CRM-010-ENDPOINT-CAPABILITY-INVENTORY.md` |
| CRM-010-F1: Migration/recovery tests | Migration/recovery acceptance design | `CRM-010-MIGRATION-RECOVERY-DESIGN.md` |
| CRM-010-F1: API/event compatibility | API/event compatibility strategy | `CRM-010-API-EVENT-COMPATIBILITY.md` |
| CRM-010-F1: Arabic/English UI acceptance | Localization and accessibility test matrix | `CRM-010-LOCALIZATION-ACCESSIBILITY.md` |
| CRM-010-F2: Logs/metrics/traces/dashboard | Observability conventions and dashboard contract | `CRM-010-OBSERVABILITY-CONVENTIONS.md` |
| CRM-010-F2: SLOs/SLIs/alerts | SLI/SLO/alert candidate package | `CRM-010-SLI-SLO-ALERTS.md` |
| CRM-010-F2: Runbook/recovery guide | Runbook and recovery guide | `CRM-010-RUNBOOK.md` |
| CRM-010-F2: Risk register | Risk register and traceability matrix | `CRM-010-RISK-REGISTER.md` (this document) |

### 9.3 Test → Code Traceability

| Test Class | File | Tests | Code Under Test |
|------------|------|-------|-----------------|
| `ScoreValueObjectsTest` | `intelligence/ScoreValueObjectsTest.java` | 22 | `HealthScore.java`, `CustomerLifetimeValue.java`, `RiskScore.java`, etc. |
| `CustomerIntelligenceValidatorTest` | `intelligence/application/CustomerIntelligenceValidatorTest.java` | 12 | `CustomerIntelligenceValidator.java` |
| `CustomerIntelligenceContractTest` | `intelligence/application/CustomerIntelligenceContractTest.java` | 11 | `QueryPort`, `ScoringPort`, `SegmentPort`, `EventPublisher` |
| `CustomerIntelligenceEventsTest` | `intelligence/domain/event/CustomerIntelligenceEventsTest.java` | 11 | All 6 event records |
| `CustomerSegmentationServiceTest` | `intelligence/application/CustomerSegmentationServiceTest.java` | 10 | `CustomerSegmentationService.java` |
| `CustomerIntelligenceIntegrationTest` | `intelligence/application/CustomerIntelligenceIntegrationTest.java` | 9 | Full integration flow |
| `CustomerScoringServiceTest` | `intelligence/application/CustomerScoringServiceTest.java` | 9 | `CustomerScoringService.java` |
| `MockAdaptersTest` | `intelligence/MockAdaptersTest.java` | 8 | Mock adapter implementations |
| `NextBestActionServiceTest` | `intelligence/application/NextBestActionServiceTest.java` | 7 | `NextBestActionService.java` |
| `CustomerIntelligenceCacheTest` | `intelligence/infrastructure/CustomerIntelligenceCacheTest.java` | 7 | `CustomerIntelligenceCache.java` |
| `AiScoreOrchestratorTest` | `intelligence/application/AiScoreOrchestratorTest.java` | 6 | `AiScoreOrchestrator.java` |
| `Customer360ApplicationServiceTest` | `intelligence/application/Customer360ApplicationServiceTest.java` | 6 | `Customer360ApplicationService.java` |
| `ChurnPredictionServiceTest` | `intelligence/application/ChurnPredictionServiceTest.java` | 5 | `ChurnPredictionService.java` |
| `CustomerLifetimeValueServiceTest` | `intelligence/application/CustomerLifetimeValueServiceTest.java` | 5 | `CustomerLifetimeValueService.java` |
| `CustomerIntelligencePropertiesTest` | `intelligence/CustomerIntelligencePropertiesTest.java` | 3 | `CustomerIntelligenceProperties.java` |
| `CustomerInsightServiceTest` | `intelligence/application/CustomerInsightServiceTest.java` | 3 | `CustomerInsightService.java` |

**Total:** 134 tests across 16 test classes covering all CRM-010 code.
