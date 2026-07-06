# SANAD Stage 08 — Human-in-the-Loop Policy

**Document ID:** `SANAD-ST08-AI-HITL-001`
**Track:** 8.5
**Date:** 2026-07-06

---

## 1. Autonomy Levels Recap

```text
L0 — Suggest Only       (no action, only suggestions)
L1 — Draft              (creates draft for human review)
L2 — Execute with Approval (requires approval before execution)
L3 — Execute within Policy  (auto-executes within approved policy)
L4 — Autonomous Limited Scope (auto-executes within bounded scope)
```

---

## 2. Approval Requirements

| Action Risk | Required Autonomy | Approval Workflow       |
|-------------|-------------------|-------------------------|
| Low         | L0–L3             | None for L3 within policy|
| Medium      | L1–L2             | Single human approval   |
| High        | L0–L2             | Two human approvals     |
| Critical    | L0–L1             | Two human approvals + PM notification |

---

## 3. Approval SLA

* Approval requests expire after 24 hours.
* Expired approvals trigger re-approval or cancellation.
* Approval evidence recorded with approver, timestamp UTC, decision.

---

## 4. Override

* Tenant admin can override L2→L1 (reduce autonomy).
* Tenant admin cannot override L1→L3 (increase autonomy) without PM approval.
