# Stage 28 — Stripe Billing Approval & Controlled Activation

## Purpose

This document defines the approval gate and controlled activation model for Stripe/billing readiness.

Stage 28 may document billing readiness and activation controls, but it must not activate live payment collection without separate explicit approval.

## Billing Governance Status

```text
Billing readiness: PREPARED
Live payment collection: NOT ACTIVATED
Separate approval required: YES
Cardholder data storage in SNAD: PROHIBITED
Secret values in repository: PROHIBITED
```

## Controlled Activation Principles

```text
Use payment processor-hosted flows where possible.
Do not store cardholder data in SNAD systems.
Do not commit API keys, webhook secrets, or credentials.
Do not enable live mode without explicit approval record.
Do not mutate production billing configuration through unapproved automation.
```

## Billing Approval Gate

Billing activation requires:

| Requirement | Status Needed |
|---|---|
| Commercial approval | Approved |
| Legal/tax review | Reviewed or risk-accepted |
| Pricing model | Approved |
| Invoice/tax readiness | Completed for first customer context |
| Production secret handling | Secure external secret store only |
| Webhook validation | Documented and tested without secret exposure |
| Rollback/deactivation plan | Ready |
| Owner approval | Explicit |

## Stripe Readiness Checklist

```text
Stripe account ownership confirmed
Live mode access controlled
Webhook endpoint defined
Webhook secret stored outside repository
Product/price model mapped
Tax handling reviewed
Invoice behavior reviewed
Customer portal policy reviewed
Refund/cancellation policy reviewed
Audit record ready
```

## Prohibited Actions

```text
Do not place STRIPE_SECRET_KEY in repository.
Do not place webhook signing secret in repository.
Do not store card numbers or cardholder data in SNAD.
Do not activate live billing from this document alone.
Do not bypass legal/tax review.
Do not make final PCI/compliance claims without specialist review.
```

## Activation Flow

```text
1. Confirm paid customer conversion readiness
2. Confirm commercial approval
3. Confirm legal/tax readiness
4. Confirm billing configuration plan
5. Confirm secret storage approach
6. Confirm webhook validation approach
7. Obtain separate explicit billing activation approval
8. Activate controlled billing
9. Verify invoice/payment event handling
10. Record outcome
```

## Rollback / Deactivation Plan

If billing activation causes an issue:

```text
Disable live checkout or payment collection path
Pause affected subscription action
Notify owner and customer success
Record incident
Assess invoice/payment state
Do not delete audit evidence
```

## Risk Register

| Risk | Severity | Control |
|---|---|---|
| Secret exposure | Critical | External secret store only |
| Premature live billing | High | Separate approval gate |
| Tax misconfiguration | High | Specialist review |
| Webhook misuse | Medium | Signature verification and audit |
| Customer billing dispute | Medium | Clear proposal and billing terms |

## Acceptance Criteria

```text
Stripe Billing Approval Gate: READY
Controlled activation model: READY
Secret handling controls: READY
Prohibited actions: DOCUMENTED
Rollback/deactivation plan: READY
Live billing: NOT ACTIVATED
Production impact: NONE
```
