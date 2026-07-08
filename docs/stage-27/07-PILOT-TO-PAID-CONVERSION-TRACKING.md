# Stage 27 — Pilot-to-Paid Conversion Tracking

## Purpose

This document defines how SNAD will track conversion from first customer pilot to paid customer readiness.

Stage 27 does not activate live billing. Any production billing activation requires a separate explicit approval.

## Pilot Definition

A pilot is a controlled customer implementation with:

- Defined scope
- Defined duration
- Named executive sponsor
- Named implementation owner
- Approved modules
- Approved success criteria
- Support path
- Conversion decision checkpoint

## Pilot Start Conditions

A pilot may start only when:

```text
Customer is qualified
Pilot scope is documented
Tenant configuration plan is ready
Legal/commercial track is active
Data/security constraints are reviewed
Partner role is approved, if applicable
Success criteria are documented
Support model is ready
```

## Pilot Success Conditions

Pilot success requires measurable evidence across:

| Area | Success Signal |
|---|---|
| Activation | Admin and pilot users active |
| Workflow | At least one approved workflow executed |
| Adoption | Target users complete pilot tasks |
| Support | No unresolved critical support blocker |
| Business value | Customer confirms measurable operational value |
| Commercial | Proposal path is ready |
| Legal | Review blockers tracked |

## Progress Tracking

Track the pilot through stages:

```text
Pilot Proposed
Pilot Approved
Tenant Prepared
Users Activated
Workflows Configured
Training Completed
UAT Started
UAT Completed
Pilot Review
Commercial Decision
Paid Conversion Candidate
```

## Paid Conversion Criteria

A pilot becomes a paid conversion candidate when:

- Pilot success criteria are met
- Executive sponsor confirms value
- Commercial proposal is accepted for review
- Legal/commercial blockers are manageable
- Support risks are not critical
- Billing activation path is approved separately

## Commercial Approval Path

```text
1. Pilot value review
2. Commercial proposal preparation
3. Pricing exception review, if any
4. Owner approval
5. Legal/commercial review
6. Billing readiness review
7. Paid conversion decision
```

## Billing Readiness Dependency

```text
No live billing activation without separate explicit approval.
No Stripe live mode activation from Stage 27 alone.
No production payment collection without billing governance approval.
```

## Customer Decision Checkpoints

| Checkpoint | Decision |
|---|---|
| Discovery complete | Continue / revise / stop |
| Demo complete | Pilot fit / not fit |
| Tenant configured | Proceed to training / hold |
| UAT complete | Review value / extend pilot |
| Pilot review | Convert / extend / close |

## Conversion Risk Register

| Risk | Severity | Treatment |
|---|---|---|
| Pilot scope too broad | Medium | Limit pilot scope |
| Legal delay | Medium | Track legal/commercial pack |
| Billing not approved | High | Separate approval required |
| Low adoption | Medium | Customer success intervention |
| Support blockers | High | Escalation model |
| Partner delivery delay | Medium | Partner governance review |

## Reporting Template

```text
Customer:
Pilot start date:
Pilot owner:
Executive sponsor:
Target modules:
Success criteria:
Current stage:
Health score:
Open risks:
Conversion readiness:
Next decision:
```

## Acceptance Criteria

```text
Pilot-to-Paid Tracking: READY
Pilot definition: READY
Start conditions: READY
Success conditions: READY
Progress stages: READY
Conversion criteria: READY
Billing constraint: DOCUMENTED
Risk register: READY
Production impact: NONE
```
