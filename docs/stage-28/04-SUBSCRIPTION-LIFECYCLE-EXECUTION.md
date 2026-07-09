# Stage 28 — Subscription Lifecycle Execution

## Purpose

This document defines the subscription lifecycle execution model for first paid customer readiness.

It prepares how subscriptions should move through commercial, operational, billing, customer success, and governance states without activating live billing prematurely.

## Lifecycle States

```text
Draft Offer
Commercial Review
Approved Offer
Customer Accepted
Billing Approval Pending
Billing Ready
Active Subscription Pending Activation
Active Subscription
Suspended
Renewal Pending
Cancelled
Closed
```

## State Ownership

| State | Owner |
|---|---|
| Draft Offer | Commercial owner |
| Commercial Review | Owner / commercial reviewer |
| Customer Accepted | Sales / customer success |
| Billing Approval Pending | Billing governance owner |
| Billing Ready | Billing owner |
| Active Subscription | Customer success + billing owner |
| Renewal Pending | Customer success |
| Cancelled / Closed | Owner + billing owner |

## Lifecycle Rules

```text
No subscription becomes active without customer acceptance.
No live billing starts without separate explicit approval.
No billing credential is committed to repository.
No tax/compliance final claim without specialist review.
No partner may modify subscription terms without SNAD approval.
```

## Subscription Data Model

Minimum fields:

```text
Customer
Tenant
Plan
Modules
Seats/users
Billing cadence
Start date
Renewal date
Trial/pilot reference
Commercial owner
Billing status
Legal/tax review status
Customer success owner
```

## Lifecycle Transitions

| From | To | Required Evidence |
|---|---|---|
| Draft Offer | Commercial Review | Offer fields complete |
| Commercial Review | Approved Offer | Owner approval |
| Approved Offer | Customer Accepted | Customer acceptance |
| Customer Accepted | Billing Approval Pending | Billing gate opened |
| Billing Approval Pending | Billing Ready | Separate approval |
| Billing Ready | Active Subscription | Activation record |
| Active Subscription | Renewal Pending | Renewal window reached |
| Active Subscription | Suspended | Approved suspension reason |
| Any active state | Cancelled | Cancellation approval and record |

## Operational Controls

- Keep subscription changes auditable.
- Separate commercial approval from billing activation.
- Preserve customer success ownership.
- Track renewal dates and expansion signals.
- Protect billing configuration and secrets.

## Failure Handling

```text
If billing configuration fails: stop activation and record blocker.
If customer acceptance changes: return to commercial review.
If legal/tax review blocks: hold activation.
If production risk appears: do not activate billing.
```

## Acceptance Criteria

```text
Subscription Lifecycle: READY
Lifecycle states: DEFINED
State ownership: READY
Transition rules: READY
Data model: READY
Failure handling: READY
Live billing: NOT ACTIVATED
Production impact: NONE
```
