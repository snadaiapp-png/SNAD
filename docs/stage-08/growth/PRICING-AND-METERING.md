# SANAD Stage 08 — Pricing and Metering

**Document ID:** `SANAD-ST08-GROW-PRICE-001`
**Track:** 8.9
**Date:** 2026-07-06

---

## 1. Pricing Models

* Per-seat (Starter, Growth).
* Per-usage (AI tokens, API calls).
* Per-feature (Industry Packs, AI Agents).
* Enterprise contract (custom).

---

## 2. Metering

* Per-event metering (API call, AI token, email, webhook).
* Aggregated per tenant per day.
* Stored for 13 months (rolling).
* Used for billing and analytics.

---

## 3. Billing Cycle

* Monthly invoice for subscription + usage.
* Annual invoice for annual plans (with discount).
* Enterprise: quarterly or annual per contract.

---

## 4. Plans

| Plan      | Price/mo | Seats | AI Tokens | API Calls |
|-----------|----------|-------|-----------|-----------|
| Starter   | $99      | 5     | 100K      | 10K       |
| Growth    | $499     | 25    | 1M        | 100K      |
| Scale     | $1,999   | 100   | 5M        | 1M        |
| Enterprise| Custom   | Custom| Custom    | Custom    |
