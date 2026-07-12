# 04 — Revenue Governance and Approval Matrix

## Purpose

This document defines the approval matrix and revenue governance controls for first paid customer operations.

Stage 30 revenue governance ensures that commercial actions are traceable, approved, and separated from engineering or platform release decisions.

## Governance Rule

```text
Production Live does not equal commercial activation for every customer.
Stage 29 closure does not equal automatic live billing for any customer.
Stage 30 governs customer-specific paid operations.
```

## Decision Domains

| Domain | Decision Type | Approval Required |
| --- | --- | --- |
| Customer Activation | First paid customer onboarding | Customer-specific activation approval |
| Billing | Live billing start | Revenue + finance approval |
| Pricing | Discount / exception | Revenue lead approval; executive approval if material |
| Support | SLA exception | Support lead approval |
| Product | Custom request | Product owner review |
| Security | Sensitive data / integration | Security review |
| Compliance | Tax/legal/PCI/VAT claims | Specialist review required |
| AI | High-impact decision support | Human confirmation required |

## Approval Matrix

| Action | Owner | Required Approver | Evidence |
| --- | --- | --- | --- |
| Select first paid customer | Revenue Operations | Executive / PM Governance | Customer selection record |
| Approve customer onboarding | Customer Success | Revenue + Support Leads | Onboarding approval record |
| Activate live billing | Revenue Operations | Revenue Lead + Finance Review | Billing activation record |
| Issue invoice | Revenue Operations | Revenue Lead | Invoice record |
| Apply discount | Revenue Owner | Revenue Lead / Executive if material | Discount approval record |
| Resolve billing dispute | Revenue + Customer Success | Finance / Executive if material | Dispute resolution record |
| Commit custom feature | Product Owner | Governance Review | Product decision record |
| Escalate incident | Support Owner | Support Lead | Incident record |
| Use AI recommendation for customer decision | Assigned business owner | Human approver | Human confirmation record |

## Materiality Triggers

An issue becomes material when any of the following occur:

```text
Revenue impact exceeds approved threshold.
Customer threatens cancellation.
Security/privacy concern is raised.
Payment dispute remains unresolved.
Manual financial adjustment is requested.
Custom contractual commitment is requested.
Compliance certification claim is requested.
High-impact AI decision is proposed.
```

## Required Governance Artifacts

```text
Customer activation approval record
Billing activation approval record
Invoice / collection / reconciliation record
Customer success review record
SLA / support escalation record
Revenue exception approval record
Risk and compliance review record when applicable
```

## Prohibited Decisions Without Approval

```text
Unapproved discounting.
Unapproved refund/credit.
Unapproved customer-specific production customization.
Unapproved billing activation.
Unapproved compliance claims.
Automated high-impact AI decisions.
```

## Exit Criteria

```text
Revenue governance matrix: READY
Approval model: DEFINED
Decision ownership: DEFINED
Materiality triggers: DEFINED
Evidence requirements: READY
```
