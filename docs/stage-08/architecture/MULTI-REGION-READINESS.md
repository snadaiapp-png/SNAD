# SANAD Stage 08 — Multi-Region Readiness

**Document ID:** `SANAD-ST08-MULTIREGION-001`
**Track:** 8.1 / 8.2
**Date:** 2026-07-06

---

## 1. Purpose

Defines SANAD's approach to multi-region deployment for data residency, latency optimization, and disaster recovery.

---

## 2. Residency Zones

| Zone Code | Region               | Provider   | Status      |
|-----------|----------------------|------------|-------------|
| KSA       | Saudi Arabia (Riyadh)| Local CSP  | Planned     |
| GCC       | Bahrain / UAE        | AWS / GCP  | Planned     |
| EU        | Frankfurt            | AWS        | Planned     |
| US        | Virginia             | AWS        | Planned     |
| APAC      | Singapore            | AWS        | Planned     |

---

## 3. Active-Active vs Active-Passive

* Stateless services: active-active across regions.
* Tenant data: pinned to residency zone (active-passive with cross-region backup).
* AI inference: routed to nearest available model endpoint.
* Analytics warehouse: per-region, replicated nightly to a central warehouse.

---

## 4. Data Residency Routing

* Tenant onboarding selects residency zone.
* All tenant writes routed to residency zone.
* Cross-region reads only for global aggregates (anonymized).

---

## 5. Latency Targets

| Path                                | Target p95 |
|-------------------------------------|------------|
| Same-region API call                | < 100ms    |
| Cross-region API call (read)        | < 300ms    |
| Cross-region replication lag        | < 60s      |
| Backup cross-region replication     | < 15 min   |

---

## 6. Disaster Recovery

* RPO: 5 minutes (PITR + cross-region backup).
* RTO: 30 minutes (automated failover for stateless; manual for DB).

---

## 7. Related Documents

* Data Residency Matrix: `docs/stage-08/globalization/DATA-RESIDENCY-MATRIX.md`
* Resilience Model: `docs/stage-08/architecture/RESILIENCE-MODEL.md`
