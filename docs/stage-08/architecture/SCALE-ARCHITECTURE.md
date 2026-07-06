# SANAD Stage 08 — Scale Architecture

**Document ID:** `SANAD-ST08-ARCH-SCALE-001`
**Track:** 8.1 — Scale Architecture and Capacity Platform
**Date:** 2026-07-06
**Status:** APPROVED

---

## 1. Purpose

Defines the scaling architecture for SANAD Stage 08. Establishes capacity baseline, target tiers, scaling policies, and resource isolation.

---

## 2. Current Capacity Baseline (Stage 07)

| Dimension                | Current (Stage 07)        | Target (Stage 08 End)        |
|--------------------------|---------------------------|------------------------------|
| Tenants                  | 1 (bootstrap tenant)      | 100+ active tenants          |
| Users per tenant         | < 10                      | 1,000+                       |
| Concurrent API requests  | < 50 RPS                  | 5,000+ RPS                   |
| DB connections           | 20 (Render free)          | 200+ pooled                  |
| AI tokens/day            | < 10,000                  | 10M+                         |
| Background jobs          | < 100/day                 | 100,000+/day                 |
| Storage                  | < 1 GB                    | 1 TB+                        |

---

## 3. Target Capacity Tiers

| Tier      | Tenants | Users/Tenant | RPS    | AI Tokens/Day | Storage |
|-----------|---------|--------------|--------|---------------|---------|
| Starter   | 1–10    | 100          | 100    | 100K          | 10 GB   |
| Growth    | 10–50   | 1,000        | 1,000  | 1M            | 100 GB  |
| Scale     | 50–100  | 5,000        | 5,000  | 5M            | 500 GB  |
| Enterprise| 100+    | 10,000+      | 10,000+| 10M+          | 1 TB+   |

---

## 4. Growth Models

### 4.1 Tenant Growth Model

* Linear projection: 10 tenants/month for first 6 months.
* Accelerated: 25 tenants/month after product-market fit signal.
* Churn assumption: 5% monthly (conservative).

### 4.2 User Growth Model

* Average 5 users/tenant at onboarding.
* Grows to 50 users/tenant within 90 days (typical ERP adoption).

### 4.3 Transaction Volume Model

* Average 100 transactions/user/day.
* Peak 10x average during month-end close.

### 4.4 API Throughput Model

* 80% reads, 20% writes.
* p95 latency target < 200ms for tenant-scoped reads.
* p95 latency target < 500ms for cross-domain writes.

### 4.5 Storage Growth Model

* 1 KB average record.
* 1M records/tenant/year.
* Append-only audit logs are 80% of storage.

### 4.6 Queue and Event Growth Model

* 1,000 events/tenant/day average.
* 100x burst during sync operations.

### 4.7 AI Token and Inference Model

* Average 10K tokens/user/day.
* Peak 100K tokens/user/day for power users.
* Cost ceiling: $0.05/user/day average.

### 4.8 Search and Analytics Load Model

* Search index updated in real-time.
* Analytics warehouse refresh every 15 minutes.

### 4.9 Email and Notification Volume Model

* 5 emails/user/day average.
* Peak 50 emails/user/day during onboarding.

---

## 5. Scaling Policies

### 5.1 Service-Level Scaling

* Stateless API horizontally scalable.
* Autoscale on CPU > 70% sustained 5 minutes.
* Scale-in on idle 10 minutes.

### 5.2 Database Scaling

* Connection pool max: 200.
* Statement timeout: 30s.
* Read replicas for analytics.
* Partitioning for audit, events, agent_runs.

### 5.3 Caching Strategy

* Redis per-tenant namespace.
* TTL: 5 minutes for hot data, 1 hour for warm.
* Cache invalidation via domain events.

### 5.4 Background Jobs

* Per-domain queues.
* Concurrency limit per tenant.
* Dead-letter queue with alerting.

### 5.5 Rate Limiting

* Per-tenant API quota (RPM/RPD).
* Per-tenant AI token quota.
* Burst allowance 2x sustained.

### 5.6 Resource Isolation

* Per-tenant worker pools for AI agents.
* Noisy-neighbor protection via quotas + circuit breakers.
* Tenant-aware scheduler.

### 5.7 Circuit Breakers

* Open on 5 errors in 30 seconds.
* Half-open after 30 seconds.
* Closed after 10 successful half-open calls.

### 5.8 Timeout Policies

* API gateway: 30s.
* Internal service: 10s.
* Database: 5s.
* AI inference: 60s.

### 5.9 Backpressure

* Reject new tasks at 80% queue depth.
* Emitter retries with exponential backoff (1s, 2s, 4s, 8s, max 60s).

### 5.10 Graceful Degradation

* Non-critical surfaces (analytics, dashboards) return read-only banner.
* Critical surfaces (auth, billing) maintain full functionality.

### 5.11 Load Shedding

* Low-priority requests shed first.
* Audit records shed count for post-incident review.

---

## 6. Capacity Model Document

See `docs/stage-08/architecture/CAPACITY-MODEL.md` for detailed capacity calculations and tier-by-tier resource allocations.

---

## 7. Multi-Region Readiness

See `docs/stage-08/architecture/MULTI-REGION-READINESS.md`.

---

## 8. Resilience Model

See `docs/stage-08/architecture/RESILIENCE-MODEL.md`.

---

## 9. Cost Scaling Model

See `docs/stage-08/architecture/COST-SCALING-MODEL.md`.
