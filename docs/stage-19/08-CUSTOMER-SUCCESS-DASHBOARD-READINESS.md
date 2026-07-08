# Stage 19 — Customer Success Dashboard Readiness

**Date**: 2026-07-08
**Status**: DEFINED

---

## Dashboard Purpose

The Customer Success Dashboard provides SNAD's customer success team (and
owner) with a real-time view of customer health, usage, risk, and expansion
opportunities. It enables proactive engagement to reduce churn and drive
expansion.

## Activation Metrics

```
1. Signup to First Login
   Target: < 5 minutes
   Status: Tracked (auth event log)

2. First Login to Workspace Access
   Target: < 10 minutes
   Status: Tracked (workspace page view)

3. First Feature Use (CRM contact, deal, etc.)
   Target: < 24 hours
   Status: Tracked (module usage log)

4. Second Feature Use
   Target: < 7 days
   Status: Tracked (module usage log)

5. Bilingual Feature Use (language switch)
   Target: < 7 days
   Status: To be tracked (language change event)

6. Team Member Invitation
   Target: < 14 days
   Status: To be tracked (invitation event)

7. Activation Rate (all criteria met within 7 days)
   Target: > 60%
   Status: Calculated from above metrics
```

## Usage Metrics

```
1. Daily Active Users (DAU)
   Definition: Unique users who logged in today
   Target: Growing trend
   Status: To be tracked (login events)

2. Weekly Active Users (WAU)
   Definition: Unique users who logged in this week
   Target: > 60% of total users
   Status: To be tracked

3. Feature Adoption Breadth
   Definition: Number of distinct features used per tenant
   Target: > 3 features (CRM, workspace, control plane)
   Status: To be tracked

4. Feature Adoption Depth
   Definition: Frequency of feature use per tenant per week
   Target: > 10 interactions/week
   Status: To be tracked

5. Session Duration
   Definition: Average time per session
   Target: > 10 minutes
   Status: To be tracked

6. Return Rate
   Definition: % of users who return within 7 days
   Target: > 50%
   Status: To be tracked
```

## Health Score

```
Health Score: 0-100 (calculated weekly)

Components (weighted):
  - Usage frequency (30%): DAU/WAU trend over 30 days
  - Feature adoption (20%): Breadth + depth score
  - Support sentiment (15%): Ticket satisfaction + volume
  - Growth signals (15%): User additions, module adds, tier upgrades
  - Engagement (10%): Login frequency, session length
  - Payment health (10%): On-time payments, no dunning

Calculation:
  score = (usage * 0.30) + (adoption * 0.20) + (support * 0.15) +
          (growth * 0.15) + (engagement * 0.10) + (payment * 0.10)

Health tiers:
  80-100: GOOD (healthy, expansion candidate)
  60-79: OK (stable, monitor)
  40-59: WARNING (at risk, intervention needed)
  0-39: CRITICAL (churn risk, immediate action)

Display:
  - Color-coded: Green (good), Yellow (ok), Orange (warning), Red (critical)
  - Trend arrow: ↑ (improving), → (stable), ↓ (declining)
  - Drill-down: Click tenant to see component breakdown
```

## Churn Risk

```
Churn risk indicators:
  - Health score < 40 (CRITICAL)
  - DAU drop > 50% in 30 days
  - No login in 14 days (per user)
  - No new user invitations in 30 days
  - Support ticket volume increasing
  - NPS score < 20
  - Payment delays or dunning triggered
  - Downgrade request

Risk levels:
  HIGH: Health score < 40 OR DAU drop > 50%
    Action: Account manager outreach within 48h, executive escalation

  MEDIUM: Health score 40-59 OR no login in 14 days
    Action: Automated email, account manager review

  LOW: Health score 60-79 OR slight usage decline
    Action: Monitor, include in weekly review

Dashboard display:
  - Churn risk panel: List of tenants by risk level
  - Red flags: Specific indicators per tenant
  - Action buttons: "Contact customer", "Schedule call", "Send resource"
```

## Support Load

```
Metrics:
  - Open tickets per tenant
  - Average resolution time
  - Ticket satisfaction (CSAT)
  - Escalation rate
  - Repeated issues (same tenant, same problem)

Display:
  - Support load panel: Bar chart (tickets per tenant)
  - Hot tenants: Tenants with > 3 open tickets
  - Trends: Ticket volume over time
  - CSAT: Average satisfaction per tenant
```

## Renewal Status

```
Metrics:
  - Renewal date per tenant
  - Days to renewal
  - Renewal status: PENDING, IN_PROGRESS, RENEWED, AT_RISK, LOST
  - Renewal probability (based on health score)

Display:
  - Renewal calendar: Next 90 days
  - At-risk renewals: Health score < 60 with renewal in 90 days
  - Renewal pipeline: Estimated revenue
  - Actions: "Send renewal proposal", "Schedule QBR", "Escalate"
```

## Expansion Opportunities

```
Indicators:
  - User count > 80% of tier limit
  - Feature requests for higher tier
  - Health score > 80 (healthy, ready to expand)
  - Module usage suggesting need for more
  - Customer-initiated upgrade inquiry

Display:
  - Expansion panel: Tenants with expansion indicators
  - Opportunity value: Estimated additional revenue
  - Suggested action: "Upgrade tier", "Add module", "Add users"
  - Status: IDENTIFIED, CONTACTED, PROPOSAL SENT, CLOSED
```

## Customer Journey Status

```
Stages:
  1. TRIAL (14-day trial active)
  2. ONBOARDING (0-30 days post-signup)
  3. ADOPTION (30-90 days post-signup)
  4. VALUE REALIZATION (90-180 days)
  5. RENEWAL (approaching renewal date)
  6. EXPANSION (expansion opportunity identified)
  7. CHURN RISK (at-risk indicators present)
  8. CHURNED (cancelled)

Display:
  - Journey funnel: Tenants per stage
  - Stage transitions: Time spent in each stage
  - Bottlenecks: Stages with longest dwell time
  - Drop-off points: Stages with highest exit rate
```

## QBR Readiness

```
QBR (Quarterly Business Review) status per tenant:
  - QBR due: Next QBR date
  - QBR status: SCHEDULED, COMPLETED, OVERDUE
  - Last QBR date and outcomes
  - Action items from last QBR

Display:
  - QBR panel: Tenants grouped by QBR status
  - Overdue QBRs: Highlighted (red)
  - QBR template: Standard agenda
  - Action item tracker: Open items from last QBR
```

## At-Risk Customer Alerts

```
Automated alerts (email + dashboard notification):

1. Health score dropped to WARNING (40-59)
   Alert: Account manager
   Timing: On score update (weekly)

2. Health score dropped to CRITICAL (< 40)
   Alert: Account manager + Owner
   Timing: Immediate

3. No login in 14 days (per user)
   Alert: Account manager
   Timing: Daily check

4. DAU drop > 50% in 7 days
   Alert: Account manager
   Timing: Daily check

5. Support ticket open > 7 days
   Alert: Support lead
   Timing: Daily check

6. Payment failed (dunning triggered)
   Alert: Owner
   Timing: On event

7. Renewal in 90 days with health < 60
   Alert: Account manager + Owner
   Timing: On calculation (weekly)

8. Downgrade request received
   Alert: Account manager
   Timing: On event
```

## Data Requirements

```
Data sources needed:
  1. Authentication log (login events, timestamps)
  2. Feature usage log (module access, actions)
  3. Support ticket system (ticket volume, CSAT)
  4. Billing system (payment status, subscription tier)
  5. CRM (tenant info, user count, contacts)
  6. Audit log (API requests, data access)

Data storage:
  - Aggregated metrics: PostgreSQL (materialized views)
  - Raw events: Audit log (1-year retention)
  - Historical trends: Time-series table (daily snapshots)

Data refresh:
  - Real-time: Authentication, support tickets
  - Hourly: Feature usage aggregation
  - Daily: Health score calculation, churn risk assessment
  - Weekly: Expansion opportunity review
```

## UI Requirements

```
Dashboard layout:
  - Summary cards: Total tenants, active tenants, at-risk, churn rate
  - Health score distribution: Pie chart (good/ok/warning/critical)
  - Churn risk panel: Table (tenant, risk level, indicators, action)
  - Renewal calendar: Timeline (next 90 days)
  - Expansion panel: Table (tenant, opportunity, value, status)
  - Support load: Bar chart (tickets per tenant)
  - Customer journey: Funnel chart (tenants per stage)
  - Alerts: List (alert type, tenant, severity, time)

Filters:
  - By tier (Free, Professional, Enterprise)
  - By health score range
  - By risk level
  - By renewal date range
  - By QBR status

Access:
  - Owner: Full dashboard (all tenants)
  - Account manager: Assigned tenants only
  - Customer success: All tenants (read-only)

Languages: Arabic (primary), English (secondary)
```

## Customer Success Dashboard Readiness Summary

```
Metrics defined:
  - Activation: 7 metrics
  - Usage: 6 metrics
  - Health score: 6 components, 0-100 scale
  - Churn risk: 3 levels, 8 indicators
  - Support load: 5 metrics
  - Renewal: 4 statuses, 90-day view
  - Expansion: 5 indicators, 4 statuses
  - Customer journey: 8 stages
  - QBR: 4 statuses
  - Alerts: 8 automated alert types

Data requirements: DEFINED (6 data sources, refresh cadence)
UI requirements: DEFINED (layout, filters, access, languages)

Implementation: NOT YET STARTED
  → Dashboard spec defined
  → Data sources identified
  → Build in Stage 20
  → Use existing data (auth log, audit log, CRM) + new aggregation

Customer Success Dashboard: DEFINED
  → Ready for development in Stage 20
  → Metrics, alerts, and UI fully specified
```
