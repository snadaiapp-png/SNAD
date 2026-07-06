# SANAD Stage 08 — AI Cost Governance

**Document ID:** `SANAD-ST08-AI-COST-001`
**Track:** 8.5
**Date:** 2026-07-06

---

## 1. Per-Tenant Budgets

| Tier      | Daily Token Budget | Monthly Cost Ceiling |
|-----------|--------------------|----------------------|
| Starter   | 100K               | $50                  |
| Growth    | 1M                 | $500                 |
| Scale     | 5M                 | $2,500               |
| Enterprise| 10M+               | Negotiated           |

---

## 2. Enforcement

* Soft limit at 80%: alert tenant admin.
* Hard limit at 100%: throttle (queue, not reject) until reset.
* Optional auto-purchase of additional budget (Enterprise only).

---

## 3. Model Routing

* Default: cheaper model first (e.g., GPT-4o-mini class).
* Fallback: stronger model if confidence low.
* Critical reasoning: strong model explicitly selected per agent.

---

## 4. Cost Telemetry

* Per-execution cost recorded.
* Per-tenant daily aggregate.
* Per-agent daily aggregate.
* Cost dashboard for tenant admin and SANAD ops.
