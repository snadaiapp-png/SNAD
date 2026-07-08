# Stage 27 — First Tenant Configuration

## Purpose

This document defines the controlled process for configuring the first customer tenant in SNAD during Stage 27.

The configuration process must preserve multi-tenant isolation, access control, auditability, and governance requirements.

## Mandatory Controls

```text
No production customer data without authorization.
No cross-tenant data exposure.
No direct partner access without approval.
No secrets in repository.
No production billing activation without separate approval.
```

## Tenant Setup Checklist

| Area | Required Action |
|---|---|
| Tenant identity | Define organization name and tenant identifier |
| Organization profile | Capture business profile and industry |
| Admin user | Assign customer admin |
| Roles | Configure baseline RBAC roles |
| Modules | Enable pilot-approved modules only |
| Workflows | Configure approved pilot workflows |
| Notifications | Configure safe notification rules |
| Audit | Confirm audit event expectations |
| Support | Assign support and escalation contact |
| Partner access | Approve or deny partner access explicitly |

## Organization Profile

Required fields:

```text
Organization legal/display name
Industry
Country/region
Primary contact
Executive sponsor
Implementation owner
Support contact
Pilot scope
Target modules
Data/security notes
```

## Users

Initial users must be classified as:

- Customer admin
- Customer manager
- Customer user
- SNAD support user
- Partner implementation user, only if approved

## Roles and RBAC

Baseline roles:

```text
Tenant Owner
Admin
Manager
Standard User
Read-only User
Support Observer
Partner Implementation User
```

RBAC rules:

- Least privilege by default
- No partner admin access unless explicitly approved
- No cross-tenant visibility
- No direct database access
- No access to secrets

## Modules

Only modules approved in the pilot scope may be enabled.

Example module categories:

```text
Core
CRM
Workflow
Accounting readiness
HRM readiness
Commerce readiness
Customer success readiness
```

## Workflows

Workflow configuration must include:

- Workflow name
- Owner
- Trigger
- Steps
- Approvers
- SLA target
- Notifications
- Audit requirements
- Acceptance test

## Notifications

Notification rules must not expose sensitive data. Use role-aware and tenant-aware notifications only.

## Audit Requirements

Audit expectations:

```text
User creation
Role change
Module enablement
Workflow configuration
Permission change
Partner access grant
Support access event
```

## Configuration Acceptance Checklist

```text
Tenant created
Organization profile completed
Admin assigned
Users configured
Roles configured
Modules enabled by pilot scope
Workflows configured
Notifications checked
Audit requirements documented
Partner access decision recorded
Security constraints confirmed
```

## Acceptance Criteria

```text
First Tenant Configuration: READY
Tenant setup checklist: READY
RBAC rules: READY
Module scope: READY
Workflow setup: READY
Audit requirements: READY
Configuration acceptance checklist: READY
Production impact: CONTROLLED / NONE UNTIL AUTHORIZED
```
