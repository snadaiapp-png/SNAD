# Stage 28 — Revenue Operations Dashboard

## Purpose

This document defines the Stage 28 Revenue Operations Dashboard MVP.

The dashboard provides executive visibility into first paid customer conversion readiness, billing approval status, subscription lifecycle, invoice/tax readiness, customer renewal signals, and revenue activation risks.

## Dashboard Objectives

```text
Track first paid conversion readiness.
Track billing approval status.
Track subscription lifecycle readiness.
Track invoice/tax review status.
Track customer success renewal/expansion signals.
Track revenue activation risks and decisions.
```

## Core Widgets

| Widget | Purpose |
|---|---|
| Revenue activation status | Overall readiness summary |
| First paid customer status | Conversion stage and blockers |
| Billing approval gate | Approval status and owner |
| Subscription lifecycle | Current subscription state |
| Invoice/tax readiness | Review status and blockers |
| Customer success health | Adoption and support status |
| Renewal/expansion signals | Future growth indicators |
| Risk register | Critical revenue risks |

## KPI Model

```text
First paid conversion readiness score
Billing gate status
Commercial approval status
Invoice/tax review status
Subscription lifecycle status
Customer health score
Expansion signal count
Open revenue risks
```

## Readiness Score

The readiness score uses a 0–100 model:

| Signal | Weight |
|---|---:|
| Customer conversion readiness | 20 |
| Commercial approval | 15 |
| Billing approval gate | 15 |
| Invoice/tax review | 15 |
| Subscription lifecycle readiness | 10 |
| Customer success readiness | 10 |
| Support readiness | 5 |
| Risk status | 10 |

## Status Bands

```text
80–100: Ready for controlled paid activation review
60–79: Mostly ready, blockers remain
40–59: Not ready, material gaps
Below 40: High risk / hold
```

## Revenue Risk Register Fields

```text
Risk
Severity
Owner
Impact
Control
Decision required
Status
Target date
```

## Decision Log Fields

```text
Decision
Owner
Date
Evidence
Risk accepted if any
Follow-up action
Status
```

## Data Protection Rules

```text
No cardholder data in dashboard.
No API keys or webhook secrets.
No raw payment credentials.
No customer confidential data beyond approved operational fields.
```

## MVP Acceptance Criteria

```text
Revenue Operations Dashboard: READY
Core widgets: DEFINED
KPI model: READY
Readiness score: READY
Risk register: READY
Decision log: READY
Data protection rules: READY
Production impact: NONE
```
