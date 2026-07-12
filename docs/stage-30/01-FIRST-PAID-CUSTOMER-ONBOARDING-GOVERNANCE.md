# 01 — First Paid Customer Onboarding Governance

## Purpose

This document defines the governed onboarding process for the first paid SNAD customers after Stage 29 closure.

It converts paid launch readiness into a controlled operating process without weakening production governance.

## Entry Conditions

```text
Production: LIVE
Backend Runtime: READY
Stage 29: CLOSED / COMPLETE / APPROVED
Customer-specific commercial approval: REQUIRED
Billing activation approval: REQUIRED
Support readiness: REQUIRED
Customer success owner: ASSIGNED
Rollback Required: NO
```

## Customer Qualification

A first paid customer may be onboarded only when all criteria are met:

| Area | Requirement |
| --- | --- |
| Commercial fit | Customer has a clear business use case aligned with SNAD capabilities. |
| Operational fit | Customer workflow can be supported without custom production changes. |
| Support fit | Support capacity exists for onboarding and early stabilization. |
| Billing fit | Subscription, invoicing, and collection controls are ready. |
| Governance fit | Customer activation is explicitly approved and traceable. |

## Onboarding Gates

### Gate 30.1 — Customer Selection Gate

```text
Customer profile reviewed: YES
Use case documented: YES
Scope boundaries defined: YES
Unsupported requirements excluded: YES
Customer-specific owner assigned: YES
```

### Gate 30.2 — Activation Approval Gate

```text
Commercial approval: REQUIRED
Billing approval: REQUIRED
Support readiness approval: REQUIRED
Customer success approval: REQUIRED
Security / privacy review: REQUIRED when customer data or integrations are involved
```

### Gate 30.3 — Workspace Preparation Gate

```text
Tenant readiness: VERIFIED
Admin contact identified: VERIFIED
User invitation process: READY
Module access model: READY
Support channel: READY
Initial success plan: READY
```

### Gate 30.4 — Go-Live Customer Gate

```text
Customer acceptance: REQUIRED
Billing event approval: REQUIRED
Support handoff: REQUIRED
Incident escalation path: REQUIRED
Operational monitoring: REQUIRED
```

## Onboarding Checklist

```text
1. Confirm customer identity and approved commercial terms.
2. Confirm customer-specific billing activation approval.
3. Confirm tenant or workspace provisioning readiness.
4. Assign customer success owner.
5. Assign support owner.
6. Confirm admin contact and onboarding contact.
7. Prepare onboarding meeting agenda.
8. Configure allowed modules according to approved subscription.
9. Confirm initial users and roles.
10. Confirm support SLA and escalation path.
11. Confirm billing event tracking.
12. Confirm post-onboarding success review date.
```

## Prohibited Actions

```text
Do not activate billing without customer-specific approval.
Do not store cardholder data in SNAD.
Do not bypass tenant isolation or access controls.
Do not provide unsupported custom commitments.
Do not promise compliance certification without specialist review.
Do not allow AI-driven high-impact decisions without human confirmation.
```

## Evidence Required

```text
Customer onboarding record: REQUIRED
Activation approval record: REQUIRED
Billing approval record: REQUIRED
Customer success owner assignment: REQUIRED
Support owner assignment: REQUIRED
Customer acceptance record: REQUIRED
```

## Exit Criteria

```text
First paid customer onboarding governance: READY
Activation flow: CONTROLLED
Operational ownership: ASSIGNED
Billing controls: LINKED
Support readiness: CONFIRMED
```
