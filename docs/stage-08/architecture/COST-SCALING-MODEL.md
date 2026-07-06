# SANAD Stage 08 — Cost Scaling Model

**Document ID:** `SANAD-ST08-COST-001`
**Track:** 8.1
**Date:** 2026-07-06

---

## 1. Cost Categories

| Category                | Driver                          | Unit Cost (USD, indicative)        |
|-------------------------|---------------------------------|------------------------------------|
| Compute (API)           | Replica-hours                   | $0.05/hour/replica                 |
| Compute (Worker)        | Worker-hours                    | $0.04/hour/worker                  |
| Database                | vCPU-hours + storage            | $0.10/hour/vCPU + $0.10/GB/month   |
| Redis                   | Memory-hours                    | $0.02/hour/GB                      |
| AI inference            | Tokens                          | $0.000002/token (blended)          |
| Email                   | Messages sent                   | $0.0001/message                    |
| Webhook                 | Deliveries                      | $0.00001/delivery                  |
| Storage (object)        | GB-month                        | $0.023/GB/month                    |
| Bandwidth               | GB egress                       | $0.09/GB                           |

---

## 2. Per-Tenant Cost Ceiling

| Tier      | Monthly Cost Ceiling (USD) |
|-----------|----------------------------|
| Starter   | $50                        |
| Growth    | $500                       |
| Scale     | $5,000                     |
| Enterprise| Negotiated                 |

---

## 3. Cost Allocation

* Per-tenant telemetry: compute, storage, AI tokens, email, webhook.
* Allocation by tenant ID.
* Cost dashboard updated daily.
* Cost anomaly detection (spike > 2x daily average triggers alert).

---

## 4. Cost Governance

* Monthly cost review with PM.
* Cost budget per tenant.
* AI token budget per tenant (enforced).
* Quota throttling when budget breached.

---

## 5. Projected Monthly Cost at Scale

| Tier      | Tenants | Monthly Cost (USD) |
|-----------|---------|--------------------|
| Starter   | 100     | $5,000             |
| Growth    | 50      | $25,000            |
| Scale     | 20      | $100,000           |
| Enterprise| 10      | Negotiated (>$50K) |
| **Total** | 180     | ~$180K+            |
