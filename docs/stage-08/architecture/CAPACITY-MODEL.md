# SANAD Stage 08 — Capacity Model

**Document ID:** `SANAD-ST08-CAP-001`
**Track:** 8.1
**Date:** 2026-07-06

---

## 1. Per-Tier Resource Allocation

| Resource                | Starter | Growth | Scale  | Enterprise |
|-------------------------|---------|--------|--------|------------|
| API replicas            | 2       | 4      | 8      | 16+        |
| Worker replicas         | 1       | 2      | 4      | 8+         |
| DB vCPU                 | 2       | 4      | 8      | 16+        |
| DB memory (GB)          | 4       | 8      | 16     | 32+        |
| DB storage (GB)         | 50      | 200    | 1,000  | 5,000+     |
| Redis memory (GB)       | 1       | 4      | 16     | 64+        |
| AI tokens/day           | 100K    | 1M     | 5M     | 10M+       |
| Webhook deliveries/day  | 10K     | 100K   | 1M     | 5M+        |
| Background jobs/day     | 1K      | 10K    | 100K   | 500K+      |

---

## 2. Capacity Thresholds and Triggers

| Metric                       | Yellow (warning) | Red (action)         | Action                              |
|------------------------------|------------------|----------------------|-------------------------------------|
| API p95 latency              | > 200ms          | > 500ms              | Scale out API replicas              |
| DB CPU                       | > 60%            | > 80%                | Scale up DB; investigate slow queries |
| DB connection utilization    | > 60%            | > 80%                | Scale up pool; investigate leaks    |
| Redis memory                 | > 70%            | > 90%                | Scale up Redis; review TTLs         |
| Queue depth                  | > 1,000          | > 10,000             | Scale out workers; backpressure     |
| AI token usage vs budget     | > 80%            | > 100%               | Throttle; alert tenant              |
| Error rate                   | > 1%             | > 5%                 | Investigate; circuit breakers       |

---

## 3. Capacity Review Cadence

* Weekly: Capacity dashboard review by Infra Owner.
* Monthly: Capacity planning meeting with PM.
* Quarterly: Capacity model re-baselining.

---

## 4. Capacity Forecast

| Quarter | Tenants | Users   | RPS    | AI Tokens/Day | Storage |
|---------|---------|---------|--------|---------------|---------|
| Q3 2026 | 10      | 100     | 100    | 100K          | 10 GB   |
| Q4 2026 | 25      | 1,000   | 500    | 500K          | 50 GB   |
| Q1 2027 | 50      | 5,000   | 2,000  | 2M            | 200 GB  |
| Q2 2027 | 100+    | 10,000+ | 5,000+ | 5M+           | 500 GB  |
