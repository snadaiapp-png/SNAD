# 05 — Support SLA, Escalation, and Incident Handling

## Purpose

This document defines support operations for first paid SNAD customers.

The intent is to provide governed, traceable, and customer-safe support operations without weakening production controls.

## Support Principles

```text
Every paid customer must have an assigned support path.
Every customer-impacting issue must be traceable.
Critical incidents must be escalated immediately.
Support status must inform customer success health.
No support action may bypass security, tenant isolation, or production governance.
```

## Support Channels

| Channel | Purpose |
| --- | --- |
| Customer success contact | Adoption, enablement, value realization, account health |
| Support queue | Product issues, access problems, service requests |
| Escalation path | Critical production, billing, or customer-impacting incidents |
| Governance review | Material commercial, compliance, security, or AI-impact decisions |

## SLA Classification

| Severity | Definition | Target Response | Escalation |
| --- | --- | --- | --- |
| SEV-1 | Production unavailable or critical paid customer workflow blocked | Immediate / highest priority | Support Lead + PM Governance |
| SEV-2 | Major feature unavailable with workaround limited | High priority | Support Lead |
| SEV-3 | Standard product issue or operational request | Normal priority | Support Owner |
| SEV-4 | Question, training, or low-impact request | Scheduled handling | Customer Success Owner |

## Incident Lifecycle

```text
Reported → Classified → Assigned → Investigated → Mitigated → Resolved → Reviewed → Closed
```

## Escalation Triggers

```text
Paid customer cannot use core workflow.
Security or privacy concern is reported.
Billing issue affects customer trust or payment.
Production error affects tenant access or data integrity.
Customer threatens cancellation.
SLA is at risk of breach.
Issue requires product, engineering, finance, or executive decision.
```

## Incident Record Template

```text
Incident ID: <id>
Customer: <customer>
Severity: <SEV-1/2/3/4>
Reported at: <timestamp>
Owner: <support owner>
Customer success owner: <owner>
Impact: <description>
Root cause: <known/unknown>
Mitigation: <action>
Resolution: <action>
Customer communication: <summary>
Post-incident review required: <yes/no>
```

## Customer Communication Rules

```text
Use clear status updates.
Do not overpromise resolution times.
Do not disclose internal secrets, credentials, or private infrastructure details.
Do not make legal/compliance guarantees without specialist approval.
Document all material communication.
```

## Post-Incident Review

A review is required when:

```text
Incident severity is SEV-1 or SEV-2.
Customer billing or revenue is affected.
Security/privacy concern is involved.
Customer trust is materially impacted.
Manual production intervention was required.
```

## Exit Criteria

```text
Support SLA model: READY
Incident lifecycle: DEFINED
Escalation triggers: READY
Customer communication rules: READY
Post-incident review criteria: READY
```
