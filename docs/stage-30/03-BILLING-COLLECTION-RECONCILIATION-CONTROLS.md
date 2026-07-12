# 03 — Billing, Collection, and Reconciliation Controls

## Purpose

This document defines the controlled billing, collection, and reconciliation model for first paid SNAD customer operations.

It does not activate live billing by itself. Any customer billing activation requires explicit customer-specific approval.

## Core Rule

```text
Live billing is customer-specific.
Live billing requires explicit approval.
SNAD must not store cardholder data.
Billing evidence must be traceable.
Invoice, collection, and reconciliation states must be reviewable.
```

## Billing State Model

```text
Draft → Approved → Issued → Sent → Partially Paid → Paid → Reconciled
                         ↘ Disputed → Resolved
                         ↘ Cancelled / Voided
```

## Billing Activation Requirements

| Requirement | Status |
| --- | --- |
| Customer-specific approval | REQUIRED |
| Approved commercial terms | REQUIRED |
| Subscription plan / module scope | REQUIRED |
| Billing owner | REQUIRED |
| Collection owner | REQUIRED |
| Invoice evidence location | REQUIRED |
| Reconciliation method | REQUIRED |
| Customer contact for billing | REQUIRED |

## Collection Control Model

| Collection State | Meaning | Required Action |
| --- | --- | --- |
| Current | Payment received or not yet due | Continue monitoring |
| Due Soon | Invoice approaching due date | Send reminder if applicable |
| Overdue | Due date passed | Escalate to revenue owner |
| Disputed | Customer contests invoice | Freeze collection pressure; resolve dispute |
| Written Off | Uncollectible after approval | Requires formal financial approval |

## Reconciliation Rules

```text
Invoice record must match approved subscription terms.
Payment record must match customer, invoice, amount, date, and reference.
Partial payments must remain traceable.
Refunds or credits require approval.
Manual adjustments require reason and owner.
No revenue recognition claim is made without accounting review.
```

## Segregation of Duties

| Activity | Owner | Approver |
| --- | --- | --- |
| Draft invoice | Revenue Operations | Revenue Lead |
| Approve live billing | Revenue Lead | Executive / Finance Approval |
| Record payment | Revenue Operations | Finance Review |
| Resolve dispute | Revenue + Customer Success | Finance / Executive if material |
| Issue credit/refund | Finance | Executive / Legal if required |

## Evidence Required

```text
Customer billing approval record: REQUIRED
Subscription scope record: REQUIRED
Invoice evidence: REQUIRED
Payment evidence: REQUIRED when payment occurs
Collection state record: REQUIRED
Reconciliation record: REQUIRED
Dispute resolution record: REQUIRED when applicable
```

## Prohibited Actions

```text
Do not store raw cardholder data.
Do not process unsupported payment flows inside SNAD.
Do not infer revenue recognition automatically from payment receipt.
Do not alter invoice amounts without approval.
Do not activate billing for any customer by default.
```

## Exit Criteria

```text
Billing controls: READY
Collection tracking: READY
Reconciliation evidence model: READY
Segregation of duties: READY
Live billing restriction: ENFORCED BY GOVERNANCE
```
