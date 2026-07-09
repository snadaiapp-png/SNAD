# Stage 29 — First Paid Customer Subscription Activation

## Purpose

This document defines the operational runbook for activating the first paid customer subscription after a controlled billing activation approval exists.

## Subscription Activation Scope

Activation includes:

- Confirming customer acceptance
- Mapping the approved plan to the tenant
- Confirming subscription lifecycle state
- Confirming billing configuration readiness
- Confirming customer success ownership
- Verifying post-activation access and support path

## Preconditions

```text
Stage 28: COMPLETE
Controlled billing activation approval: APPROVED
Customer acceptance: CONFIRMED
Tenant exists and is production-ready
Plan and pricing approved
Invoice/tax readiness reviewed
Payment support process ready
Rollback owner assigned
No critical production incident open
```

## Activation Runbook

```text
1. Confirm activation approval record.
2. Confirm target tenant and customer identity.
3. Confirm plan, modules, users, limits, and billing cadence.
4. Confirm no secret value is being copied into the repository.
5. Activate subscription state in the approved system of record.
6. Verify customer access and subscription entitlements.
7. Verify invoice/payment event creation if billing is enabled.
8. Verify customer success dashboard update.
9. Notify commercial, billing, and customer success owners.
10. Record evidence without secrets.
```

## Subscription State Model

```text
Approved for Activation
Activation in Progress
Active Paid Subscription
Activation Failed
Paused
Cancelled
Closed
```

## Verification Checklist

| Area | Expected Result |
|---|---|
| Customer | Correct customer and tenant selected |
| Plan | Approved plan applied |
| Entitlements | Expected modules and limits active |
| Billing | Events generated only after approval |
| Customer success | Owner assigned and dashboard updated |
| Support | Payment support path available |
| Audit | Evidence recorded without secrets |

## Failure Handling

```text
If tenant activation fails: stop and revert subscription state.
If billing event fails: stop billing path and classify event issue.
If wrong customer/tenant selected: pause activation and escalate.
If tax/invoice blocker appears: hold subscription activation.
If security issue appears: stop activation and open incident.
```

## Acceptance Criteria

```text
First Paid Customer Subscription Activation: READY
Activation runbook: READY
Preconditions: READY
State model: READY
Verification checklist: READY
Failure handling: READY
Production impact: CONTROLLED ONLY AFTER APPROVAL
```
