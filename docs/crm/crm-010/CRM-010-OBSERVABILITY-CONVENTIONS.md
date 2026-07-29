# CRM-010 Observability Semantic Conventions and Dashboard Contract

**Date:** 2026-07-29
**Issue:** #705 — Mandatory Deliverable #7
**Scope:** CRM-010 logging, metrics, tracing, and dashboard specifications

---

## 1. Semantic Conventions

### 1.1 Logging Conventions

| Convention | Standard | Example |
|------------|----------|---------|
| Logger name | Class FQCN | `LoggerFactory.getLogger(CustomerScoringService.class)` |
| Log level | SLF4J standard | ERROR, WARN, INFO, DEBUG |
| Structured format | Key-value pairs in message | `log.info("Refreshing all scores for account {}", accountId)` |
| Tenant context | Included in log message where available | `log.error("Failed to publish event {} for tenant {}", event.eventType(), event.tenantId())` |

### 1.2 Log Levels by Component

| Component | ERROR | WARN | INFO | DEBUG |
|-----------|-------|------|------|-------|
| `AiScoreOrchestrator` | AI Gateway failure | Below confidence threshold | — | — |
| `CustomerScoringService` | — | — | Score refresh operation | — |
| `SpringCustomerIntelligenceEventPublisher` | Event publish failure | — | — | — |
| `CustomerIntelligenceCache` | — | — | — | Cache hit/miss (via Caffeine stats) |

### 1.3 Correlation ID Convention

| Source | Prefix | Example |
|--------|--------|---------|
| Health scoring | `score-` | `score-a1b2c3d4` |
| CLV calculation | `clv-` | `clv-e5f6g7h8` |
| Risk assessment | `risk-` | `risk-i9j0k1l2` |
| Segment change | `segment-` | `segment-m3n4o5p6` |
| NBA generation | `nba-` | `nba-q7r8s9t0` |
| Opportunity scoring | `opp-` | `opp-u1v2w3x4` |
| Intelligence aggregate | `intel-` | `intel-y5z6a7b8` |

---

## 2. Metrics Specifications

### 2.1 Application Metrics (Caffeine Cache)

| Metric | Source | Type | Description |
|--------|--------|------|-------------|
| `cache.scores.hit` | `CustomerIntelligenceCache.scoresStats()` | Counter | Score cache hits |
| `cache.scores.miss` | `CustomerIntelligenceCache.scoresStats()` | Counter | Score cache misses |
| `cache.scores.eviction` | `CustomerIntelligenceCache.scoresStats()` | Counter | Score cache evictions |
| `cache.view.hit` | `CustomerIntelligenceCache.viewStats()` | Counter | View cache hits |
| `cache.view.miss` | `CustomerIntelligenceCache.viewStats()` | Counter | View cache misses |
| `cache.view.eviction` | `CustomerIntelligenceCache.viewStats()` | Counter | View cache evictions |

### 2.2 Audit Metrics (via AuditPort)

| Metric | Source | Type | Description |
|--------|--------|------|-------------|
| `audit.ai_request` | `AiScoreOrchestrator` | Event | AI request recorded |
| `audit.ai_request_failed` | `AiScoreOrchestrator` | Event | AI request failure recorded |

### 2.3 Event Metrics

| Metric | Source | Type | Description |
|--------|--------|------|-------------|
| `event.published` | `SpringCustomerIntelligenceEventPublisher` | Counter | Events published successfully |
| `event.publish_failed` | `SpringCustomerIntelligenceEventPublisher` | Counter | Event publish failures |

---

## 3. Tracing Specifications

### 3.1 Trace Context

| Field | Source | Propagation |
|-------|--------|-------------|
| `correlationId` | `CorrelationContextPort` | Included in all events and audit records |
| `tenantId` | `TenantContextPort` | Included in all events and audit records |
| `accountId` | Request parameter | Included in all events and audit records |

### 3.2 Trace Span Points

| Operation | Span Name | Attributes |
|-----------|-----------|------------|
| Customer 360 load | `crm.intelligence.customer360` | `tenant_id`, `account_id` |
| Score calculation | `crm.intelligence.score.calculate` | `tenant_id`, `account_id`, `score_type` |
| AI Gateway call | `crm.intelligence.ai.request` | `capability`, `confidence`, `latency_ms` |
| Segment membership | `crm.intelligence.segment.membership` | `tenant_id`, `account_id`, `segment_id` |
| NBA generation | `crm.intelligence.nba.generate` | `tenant_id`, `account_id` |

---

## 4. Dashboard Contract

### 4.1 CRM-010 Dashboard Panels

| Panel | Metric Source | Refresh Rate | Threshold |
|-------|--------------|--------------|-----------|
| Cache Hit Ratio (Scores) | `cache.scores.hit / (hit + miss)` | 1 min | >80% |
| Cache Hit Ratio (View) | `cache.view.hit / (hit + miss)` | 1 min | >80% |
| AI Gateway Success Rate | `audit.ai_request / (ai_request + ai_request_failed)` | 5 min | >95% |
| Event Publication Rate | `event.published` | 1 min | N/A (informational) |
| Event Failure Rate | `event.publish_failed` | 1 min | <1% |
| Score Calculation Latency | `crm.intelligence.score.calculate` span duration | 1 min | p95 <500ms |
| Customer 360 Load Latency | `crm.intelligence.customer360` span duration | 1 min | p95 <200ms |

### 4.2 Dashboard Layout

```
┌─────────────────────────────────────────────────────────┐
│  CRM-010 Intelligence Dashboard                         │
├─────────────────────┬───────────────────────────────────┤
│  Cache Hit Ratio    │  AI Gateway Success Rate          │
│  [Gauge: >80%]      │  [Gauge: >95%]                    │
├─────────────────────┼───────────────────────────────────┤
│  Score Calc Latency │  Customer 360 Load Latency        │
│  [Time series: p95] │  [Time series: p95]               │
├─────────────────────┼───────────────────────────────────┤
│  Event Publication  │  Event Failure Rate               │
│  [Counter: rate]    │  [Counter: rate, <1%]             │
├─────────────────────┴───────────────────────────────────┤
│  Audit Trail: Recent AI Requests                        │
│  [Table: timestamp, capability, status, confidence]     │
└─────────────────────────────────────────────────────────┘
```

### 4.3 Alert Thresholds

| Alert | Condition | Severity | Action |
|-------|-----------|----------|--------|
| Low cache hit ratio | <80% for 5 min | WARNING | Investigate cache configuration |
| AI Gateway failure spike | >5% failures for 2 min | CRITICAL | Check AI Gateway health |
| Event publication failure | >1% failures for 1 min | WARNING | Check event publisher |
| Score calculation slow | p95 >1s for 5 min | WARNING | Investigate DB performance |

---

## 5. Dashboard Implementation Notes

| Aspect | Recommendation | Status |
|--------|---------------|--------|
| Metrics export | Caffeine stats → Micrometer → Prometheus | ⚠️ Requires Micrometer setup |
| Tracing export | Spring tracing → OpenTelemetry → Jaeger/Zipkin | ⚠️ Requires OTel setup |
| Dashboard tool | Grafana with Prometheus datasource | ⚠️ Requires Grafana setup |
| Log aggregation | ELK stack or similar | ⚠️ Requires log pipeline |

**Note:** The semantic conventions and dashboard contract defined here are the **specification**. Actual dashboard implementation requires infrastructure setup (Prometheus, Grafana, OTel) which is outside CRM-010 scope but documented here for observability readiness.

---

**Convention Authority:** Governance Remediation Agent
**Date:** 2026-07-29
**Status:** ✅ COMPLETE
