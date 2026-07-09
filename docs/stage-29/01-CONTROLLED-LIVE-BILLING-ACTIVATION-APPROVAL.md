# Stage 29 — Controlled Live Billing Activation Approval

## Purpose

This document defines the controlled approval model for moving from revenue activation readiness into a governed paid launch path.

Stage 29 prepares the approval, evidence, operational controls, and rollback requirements for controlled billing activation. It does not permit unmanaged live billing or uncontrolled payment collection.

## Governing Status

```text
Stage 29: OPEN
Stage 28: CLOSED / COMPLETE / APPROVED
Production: LIVE
Backend Runtime: READY
Gate 8F: CLOSED BY GOVERNANCE WAIVER
Reference: SANAD-ST08-GOV-AMENDMENT-002
Final Platform Release: GO
Rollback Required: NO
```

## Activation Principle

Live billing may proceed only when a specific controlled billing activation approval record exists for the target customer, target plan, target billing route, and target production window.

## Required Approval Record

```text
Customer legal name
Tenant identifier
Commercial owner
Billing owner
Customer success owner
Approved plan
Approved price
Billing cadence
Tax/invoice review status
Payment processor route
Activation window
Rollback owner
Risk acceptance if any
Final approval decision
```

## Approval Gates

| Gate | Required Evidence | Decision |
|---|---|---|
| Commercial approval | Approved paid offer | Go / Hold |
| Customer acceptance | Signed or confirmed acceptance path | Go / Hold |
| Billing configuration | Processor route and plan mapping | Go / Hold |
| Tax/invoice review | Review completed or risk accepted | Go / Hold |
| Security review | No secret exposure; no cardholder storage | Go / Hold |
| Support readiness | Payment support process ready | Go / Hold |
| Rollback readiness | Disable/refund/escalation plan ready | Go / Hold |

## Prohibited Actions

```text
Do not expose Stripe, Supabase, Render, database, webhook, or admin secrets.
Do not store cardholder data in SNAD systems.
Do not activate live billing for any customer without a customer-specific approval record.
Do not bypass tax, legal, billing, or support readiness gates.
Do not claim PCI, VAT, tax, legal, or revenue-recognition certification without specialist review.
```

## Activation Decision States

```text
Draft
Under Review
Approved for Controlled Activation
Activated
Rejected
Paused
Rolled Back
Closed
```

## Rollback Requirements

```text
Disable live billing path
Pause subscription if needed
Stop invoice dispatch if incorrect
Notify customer success owner
Preserve audit evidence
Record incident and decision owner
Do not delete payment evidence or audit logs
```

## Acceptance Criteria

```text
Controlled Billing Activation Approval: READY
Approval record model: READY
Gate model: READY
Prohibited actions: DOCUMENTED
Rollback requirements: READY
Live billing activation: REQUIRES CUSTOMER-SPECIFIC APPROVAL
Production impact: NONE FROM THIS DOCUMENT
```
