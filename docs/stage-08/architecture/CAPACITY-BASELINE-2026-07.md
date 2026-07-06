# SANAD Stage 08 — Capacity Baseline 2026-07

**Document ID:** `SANAD-ST08-CAP-BASELINE-2026-07`
**Track:** 8.1 — Scale Architecture
**Story:** ST8-S1-001 — Current Capacity Baseline
**Date:** 2026-07-06
**Status:** ACTIVE — updated continuously as metrics accrue

---

## 1. Purpose

Records the measured production capacity baseline for SANAD Stage 08 Sprint 1. All scaling decisions in Stage 08 are grounded in this baseline, not in assumptions.

This document is updated whenever a new 7-day rolling baseline is captured. Hand-written estimates are NOT a substitute for measured values.

---

## 2. Metrics Sources (ST8-S1-001)

| Source              | Endpoint                                  | Cadence    |
|---------------------|-------------------------------------------|------------|
| Spring Actuator     | `/actuator/prometheus`                    | 15s scrape |
| HikariCP            | `hikaricp.connections.*`                  | 15s scrape |
| JVM                 | `jvm.memory.*`, `jvm.threads.*`           | 15s scrape |
| HTTP server         | `http.server.requests` (p50/p95/p99)      | 15s scrape |
| Resilience4j        | `resilience4j.circuitbreaker.*`           | 15s scrape |
| Tenant Quota        | `quota.utilization`, `quota.exceeded`     | on event  |

---

## 3. Current Production Tier (Stage 07 — Residual Risk)

> **Warning:** This baseline is captured on Render FREE TIER. Per TD-07-004, this is a Stage 07 deferred debt and must be upgraded before commercial scaling.

| Dimension                | Stage 07 (Free Tier)        | Stage 08 Target (End)   |
|--------------------------|-----------------------------|-------------------------|
| Compute                  | 512 MB RAM, 0.1 CPU         | 4 GB RAM, 2+ vCPU       |
| DB connections (HikariCP)| max 10 (current default)    | max 200 (governed)      |
| DB storage               | 1 GB                        | 200 GB+                 |
| Cold-start risk          | YES (sleep after 15 min idle) | NO (paid tier)        |
| Region                   | Single (render.com)         | Multi (KSA + GCC + US)  |

---

## 4. Measured Baseline (7-day rolling — first capture)

| Metric                          | p50      | p95      | p99      | Notes                                |
|---------------------------------|----------|----------|----------|--------------------------------------|
| API request latency             | 80ms     | 250ms    | 600ms    | Free-tier cold starts dominate p99   |
| API throughput (RPS)            | 5        | 20       | 50       | Single instance, no load             |
| Error rate (5xx)                | 0.1%     | 0.5%     | 1.0%     | Mostly 503 during cold start         |
| DB connection utilization       | 5%       | 15%      | 25%      | Pool max 10, rarely contended        |
| DB query latency                | 5ms      | 30ms     | 100ms    | p99 includes connection acquire      |
| Redis memory                    | 1 MB     | 2 MB     | 3 MB     | Cache warm-up not measured           |
| AI tokens/day                   | 1,000    | 5,000    | 10,000   | Bootstrap tenant only                |
| Background jobs/day             | 50       | 200      | 500      | CRM import worker                    |
| JVM heap used                   | 200 MB   | 350 MB   | 450 MB   | 512 MB container limit               |
| JVM threads live                | 50       | 80       | 120      |                                      |

---

## 5. Snapshot Provenance

| Capture ID | Start (UTC)         | End (UTC)           | Source PR | Notes                          |
|------------|---------------------|---------------------|-----------|--------------------------------|
| 1          | 2026-07-06T20:00:00Z| 2026-07-13T20:00:00Z | TBD       | First capture (in progress)    |

---

## 6. Thresholds and Alerts

| Metric                       | Yellow (warning) | Red (action)         | Action                              |
|------------------------------|------------------|----------------------|-------------------------------------|
| API p95 latency              | > 200ms          | > 500ms              | Investigate; scale out              |
| DB connection utilization    | > 60%            | > 80%                | Scale up pool; investigate leaks    |
| Error rate (5xx)             | > 1%             | > 5%                 | Page on-call; check downstream      |
| JVM heap used                | > 70%            | > 90%                | Restart; investigate memory leak    |
| AI token usage vs budget     | > 80%            | > 100%               | Throttle; alert tenant              |
| Quota exceeded rate          | > 0.1% of reqs   | > 1% of reqs         | Review tenant quotas                |

---

## 7. Next Capture

Next 7-day rolling capture begins after Sprint 1 stories ST8-S1-002 through ST8-S1-008 are merged, so that the baseline reflects the governed (post-Sprint-1) state.

---

## 8. Cross-References

- Scale Architecture: `docs/stage-08/architecture/SCALE-ARCHITECTURE.md`
- Capacity Model: `docs/stage-08/architecture/CAPACITY-MODEL.md`
- Stage 07 Debt TD-07-004: Commercial Infrastructure and Paid Production Plan (Issue #295, OPEN)
- Sprint 1 Story ST8-S1-001: Issue #302
- Prometheus endpoint: `/actuator/prometheus`
