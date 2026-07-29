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
