# Stage 27 — Customer Success Dashboard MVP

## Purpose

This document defines the MVP specification for the Stage 27 Customer Success Dashboard.

The dashboard is intended to track first customer onboarding, activation, adoption, support, partner delivery, pilot progress, renewal risk, and expansion signals.

## MVP Objectives

```text
Track first customer onboarding progress.
Measure activation and adoption.
Surface support and implementation risks.
Provide executive visibility.
Support pilot-to-paid conversion tracking.
```

## Health Score Model

The initial customer health score uses a 0–100 model:

| Signal | Weight |
|---|---:|
| Onboarding progress | 20 |
| User activation | 15 |
| Workflow adoption | 15 |
| Support severity trend | 15 |
| UAT progress | 10 |
| Executive engagement | 10 |
| Partner delivery status | 10 |
| Expansion signal | 5 |

Health score bands:

```text
80–100: Healthy
60–79: Watch
40–59: At risk
Below 40: Critical attention
```

## Activation Metrics

```text
Tenant configured
Admin user activated
Users invited
Users logged in
Pilot modules enabled
First workflow executed
Training completed
UAT started
```

## Adoption Metrics

```text
Active users
Workflow runs
Feature usage
Completed tasks
Approval cycle time
Module adoption
Training completion
```

## Support Metrics

```text
Open tickets
Tickets by severity
Time to first response
Time to resolution
Repeated issues
Escalations
Partner-related blockers
```

## Renewal and Conversion Signals

For Stage 27, renewal is treated as future lifecycle readiness while pilot-to-paid is the main conversion focus.

Signals:

- Executive sponsor engagement
- Pilot success progress
- Low unresolved blockers
- Strong adoption trend
- Commercial proposal readiness
- Legal/commercial review progress

## Expansion Signals

```text
Additional department interest
Additional module request
Workflow automation demand
Reporting/analytics demand
Partner implementation expansion
```

## Partner Delivery Status

Partner dashboard fields:

```text
Partner name
Implementation owner
Assigned tasks
Open blockers
Delivery status
Customer feedback
Escalation status
```

## Pilot Progress Widgets

Minimum widgets:

1. Pilot stage
2. Onboarding completion
3. UAT progress
4. Health score
5. Open risks
6. Support load
7. Partner delivery
8. Commercial readiness

## Executive Summary Widgets

```text
Customer status
Pilot readiness
Conversion readiness
Top 3 risks
Next decision required
Production status
Gate 8F status
Final Platform Release status
```

## MVP Acceptance Criteria

```text
Customer Success Dashboard MVP: READY
Health score model: DEFINED
Activation metrics: READY
Adoption metrics: READY
Support metrics: READY
Partner status: READY
Pilot progress widgets: READY
Executive widgets: READY
Production impact: NONE
```
