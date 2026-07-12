# Stage 29 — Payment & Invoice Event Verification

## Purpose

This document defines the verification model for payment, invoice, subscription, and billing events during the controlled paid launch.

The goal is to confirm event integrity without exposing secrets or storing cardholder data in SNAD systems.

## Event Categories

```text
Customer created
Subscription created
Subscription activated
Invoice drafted
Invoice finalized
Payment initiated
Payment succeeded
Payment failed
Refund initiated
Credit note issued
Subscription cancelled
Webhook received
Webhook rejected
```

## Verification Principles

```text
No cardholder data in SNAD.
No webhook secret in repository.
No API key in logs.
No payment event accepted without signature validation where applicable.
No invoice/tax final claim without specialist review.
```

## Event Verification Checklist

| Event | Verification |
|---|---|
| Customer created | Customer ID matches approved tenant/customer |
| Subscription activated | Plan, cadence, and amount match approval |
| Invoice generated | Legal name, address, currency, and line items reviewed |
| Payment event | Event source verified and no sensitive data stored |
| Webhook event | Signature validation and idempotency checked |
| Payment failed | Customer support workflow triggered |
| Refund/credit | Approval and audit evidence recorded |

## Required Evidence

```text
Event type
Event timestamp
Customer reference
Tenant reference
Subscription reference
Invoice reference
Verification result
Owner
Follow-up action
No-secret confirmation
```

## Failure Classifications

| Classification | Example | Action |
|---|---|---|
| Configuration | Wrong plan or price | Pause and fix configuration |
| Payment | Payment failed | Trigger support workflow |
| Invoice | Wrong tax/line item | Hold invoice and review |
| Security | Invalid webhook signature | Reject and investigate |
| Data | Wrong customer mapping | Stop and escalate |

## Audit Controls

- Record event references, not raw secrets.
- Preserve timestamps and owners.
- Keep retry and idempotency evidence.
- Keep customer support actions linked to payment events.
- Do not delete failed event evidence.

## Acceptance Criteria

```text
Payment and Invoice Event Verification: READY
Event categories: DEFINED
Verification checklist: READY
Failure classifications: READY
Audit controls: READY
No cardholder data stored: ENFORCED
No secret value republished: ENFORCED
```
