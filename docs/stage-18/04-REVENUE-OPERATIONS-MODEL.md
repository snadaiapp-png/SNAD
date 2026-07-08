# Stage 18 — Revenue Operations Model

**Date**: 2026-07-08

---

## Revenue Streams

```
1. Subscription Revenue (primary)
   - Free Pilot: $0
   - Professional: $49/month per tenant
   - Enterprise: $199+/month per tenant (custom)

2. Marketplace Revenue
   - Platform fee: 20-30% of extension sales
   - SNAD-built extensions: 100% revenue

3. Professional Services
   - Implementation: $5,000-$20,000 per customer
   - Custom development: $100-$200/hour
   - Training: $500-$2,000 per session

4. Partner Revenue
   - Referral commissions: 5-15% of first year
   - Reseller margin: 20% off list price
```

## Lead to Customer Journey

```
1. Lead Generation
   Sources: Website, referrals, partners, marketing, events
   Tool: CRM (SNAD's own CRM module)
   
2. Lead Qualification
   Criteria: Company size, budget, authority, need, timeline
   Tool: Lead scoring (AI-assisted, Stage 17)
   
3. Demo / Trial
   Process: Product demo, 14-day trial
   Conversion target: 30% trial-to-paid
   
4. Proposal / Quote
   Process: Custom quote for enterprise, self-serve for professional
   Tool: Proposal template (Stage 13)
   
5. Contract / Agreement
   Process: Sign MSA, SLA, DPA (enterprise) or ToS (professional)
   Tool: Contract templates (Stage 13)
   
6. Onboarding
   Process: Tenant creation, user setup, training
   Tool: Onboarding checklist (Stage 13)
   
7. Activation
   Criteria: First value action completed (login + workspace use)
   Target: 60% within 7 days
   
8. Expansion
   Process: Upsell modules, add users, upgrade tier
   Trigger: Usage patterns, customer success outreach
```

## Trial to Paid Conversion

```
Trial parameters:
  Duration: 14 days
  Tier: Professional (all features)
  No credit card required
  Auto-downgrade to Free after trial

Conversion process:
  Day 1: Welcome email + onboarding guide
  Day 3: Check-in email (how's it going?)
  Day 7: Mid-trial review (usage, feedback)
  Day 11: Upgrade reminder (3 days left)
  Day 13: Final reminder (1 day left)
  Day 14: Trial ends, downgrade to Free
  Day 15+: Follow-up (why didn't you convert?)

Conversion target: 30% trial-to-paid
Tracking: CRM trial status, conversion rate by source
```

## Subscription Lifecycle

```
1. Active (paid)
   - Subscription current
   - Billing active
   - Full feature access
   
2. Past Due
   - Payment failed
   - 7-day grace period
   - Features continue (good faith)
   - Automated dunning emails
   
3. Suspended
   - Grace period expired
   - Features disabled (data retained)
   - Manual reactivation on payment
   
4. Cancelled
   - Customer initiated
   - Effective end of billing cycle
   - Data retained 90 days (for reactivation)
   
5. Expired
   - 90 days after cancellation
   - Data hard deleted
   - Anonymized audit log retained 1 year
```

## Renewal Process

```
Annual renewal:
  Day -90: Renewal notification sent
  Day -60: Account manager outreach
  Day -30: Renewal proposal sent
  Day -14: Follow-up if not renewed
  Day -7: Final reminder
  Day 0: Renewal or expiration

Renewal metrics:
  - Renewal rate target: > 90%
  - Upsell rate: > 30% (add modules or users)
  - Churn rate: < 10% annual

Renewal drivers:
  - Product adoption (active usage)
  - Customer satisfaction (NPS)
  - Support quality
  - Value realization (ROI)
```

## Expansion Process

```
Expansion triggers:
  - User count approaching limit
  - Feature requests for higher tier
  - Usage patterns indicate need for more
  - Customer success identifies opportunity

Expansion types:
  1. User expansion: Add more users
  2. Module expansion: Add CRM, ERP, HRM, etc.
  3. Tier expansion: Professional → Enterprise
  4. Storage expansion: Add more storage
  5. AI expansion: Add AI credits

Expansion process:
  1. Customer success identifies opportunity
  2. Outreach to customer (value proposition)
  3. Demo of new features/modules
  4. Quote and proposal
  5. Contract amendment
  6. Feature activation
  7. Training (if needed)

Expansion target: > 30% of customers expand within first year
```

## Churn Prevention

```
Churn indicators:
  - Login frequency drops > 50%
  - No new user invitations in 30 days
  - Support ticket volume increases
  - NPS score < 20
  - Payment delays
  - Downgrade requests

Churn prevention actions:
  1. Identify at-risk customers (weekly)
  2. Account manager outreach (within 48h)
  3. Root cause analysis
  4. Targeted intervention:
     - Training (if adoption issue)
     - Support (if technical issue)
     - Pricing (if cost issue)
     - Features (if gap issue)
  5. Escalation to owner if high-value customer at risk

Churn target: < 10% annual
```

## Billing Readiness

```
Payment processor: Stripe (to be integrated)
Billing models:
  - Monthly subscription (auto-renew)
  - Annual subscription (discount: 2 months free)
  - Per-user pricing (professional tier)
  - Per-tenant pricing (enterprise tier)

Invoice generation:
  - Automated via Stripe
  - Sent on billing date
  - PDF + online payment link
  - Tax handling: Stripe Tax (if available)

Dunning management:
  - Day 1: Payment failed notification
  - Day 3: Retry charge
  - Day 7: Final notice
  - Day 10: Suspend account
  - Day 90: Expire account

Revenue recognition:
  - Monthly: Recognized in month of service
  - Annual: Deferred revenue, recognized monthly
  - Professional services: Recognized on delivery

Status: NOT YET IMPLEMENTED (Stripe integration in Stage 19)
```

## Revenue Reporting

```
Metrics tracked:
  - MRR (Monthly Recurring Revenue)
  - ARR (Annual Recurring Revenue)
  - ARPA (Average Revenue Per Account)
  - LTV (Lifetime Value)
  - CAC (Customer Acquisition Cost)
  - Churn rate (monthly, annual)
  - Net revenue retention (expansion - churn)
  - Trial conversion rate
  - Sales cycle length

Reports:
  - Daily: New signups, trials started
  - Weekly: Pipeline, conversion rates
  - Monthly: MRR, churn, expansion, LTV/CAC
  - Quarterly: Board-level metrics, forecast
  - Annual: Full P&L, growth analysis

Tools:
  - Stripe (billing data)
  - CRM (sales pipeline)
  - Custom dashboard (revenue metrics)
```

## Revenue Operations Summary

```
Revenue streams: 4 (subscription, marketplace, services, partner)
Lead-to-customer: 8-step process documented
Trial-to-paid: 14-day trial, 30% conversion target
Subscription lifecycle: 5 states (active, past due, suspended, cancelled, expired)
Renewal: 90-day process, > 90% target
Expansion: 5 types, > 30% target
Churn prevention: 5 indicators, < 10% target
Billing: Stripe (to be integrated Stage 19)
Reporting: Daily/weekly/monthly/quarterly/annual

Implementation: DOCUMENTED (ready for execution)
Dependencies: Stripe integration (Stage 19), CRM module (exists)
```
