# Stage 17 — AI CRM Intelligence Productization

**Date**: 2026-07-08

---

## CRM AI Features

### 1. Lead Scoring

```
Feature: AI scores leads based on likelihood to convert.
Input: Contact info, engagement history, company data
Output: Score 0-100 with explanation

Example:
  Lead: Ahmed Hassan, ahmed@acme.com
  Score: 85/100 (High Priority)
  Reasoning: "High engagement (5 emails opened), decision-maker title,
              company size matches ICP, recent website visit"
  Factors: ["Email opens: 5", "Title: CTO", "Company size: 50-200", "Last visit: 2 days ago"]
  Recommendation: "Assign to senior sales rep, schedule call within 48h"

Implementation:
  - AI Gateway processes lead data (tenant-scoped)
  - Score updated daily or on new activity
  - Sales rep sees score + explanation in CRM
  - Rep decides action (no auto-action)
```

### 2. Opportunity Intelligence

```
Feature: AI analyzes deals/opportunities for win probability.
Input: Deal stage, value, contact history, competitor info
Output: Win probability + risk factors + recommendations

Example:
  Deal: Acme Corp — $50,000 — Negotiation stage
  Win Probability: 72%
  Risk Factors: ["No response in 5 days", "Competitor mentioned in last call"]
  Recommendations: ["Send revised proposal", "Schedule executive meeting"]
  Confidence: 78%

Human action required: Rep reviews and decides next step
```

### 3. Customer Summary

```
Feature: AI generates summary of customer relationship.
Input: All interactions, deals, support tickets
Output: Concise summary with key points

Example:
  Customer: Globex Inc
  Summary: "Customer since 2025-03-15. 3 active deals ($120k total pipeline).
            Last contact: 2026-07-05 (positive call). 2 support tickets (both resolved).
            Health: GOOD. Renewal risk: LOW."
  Key Contacts: ["John (CEO)", "Sarah (Ops Manager)"]
  Next Steps: ["Renewal discussion in 30 days", "Upsell opportunity: ERP module"]

Implementation:
  - AI summarizes on-demand (button click)
  - Summary displayed in CRM customer view
  - No automatic actions
```

### 4. Next Best Action

```
Feature: AI recommends most impactful next action per contact/deal.
Input: All active items, priorities, timing
Output: Ranked list of recommended actions

Example:
  Top actions for today:
    1. Call Ahmed (deal closing this week, $50k) — Impact: HIGH
    2. Send proposal to Sarah (requested 3 days ago) — Impact: MEDIUM
    3. Follow up with Globex (renewal in 30 days) — Impact: MEDIUM

Each action includes:
  - Why it's recommended (explanation)
  - Expected impact
  - Best time to execute
  - Required resources
```

### 5. Follow-up Recommendation

```
Feature: AI suggests when and how to follow up.
Input: Contact history, engagement patterns, deal stage
Output: Follow-up recommendation with timing and method

Example:
  Contact: Ahmed Hassan
  Recommendation: "Email follow-up in 2 days"
  Reasoning: "Last call was positive but no response to proposal.
              Email is preferred channel (90% open rate).
              Tuesday 10am is optimal send time."
  Draft email: [AI-generated draft for review]

Human action: Rep reviews draft, edits if needed, sends
```

### 6. Sales Pipeline Risk

```
Feature: AI identifies at-risk deals in pipeline.
Input: Deal stage, time in stage, activity history
Output: Risk report with flagged deals

Example:
  At-risk deals:
    1. Deal: Acme Corp — $50k — In Negotiation for 45 days (avg: 20 days)
       Risk: STALLED — No activity in 7 days
       Recommendation: "Schedule meeting or move to Lost"
    2. Deal: TechCorp — $30k — In Proposal for 30 days (avg: 10 days)
       Risk: DELAYED — Proposal not opened
       Recommendation: "Resend proposal or follow up directly"
```

### 7. Customer Health Signal

```
Feature: AI calculates customer health score.
Input: Usage data, support tickets, payment history, engagement
Output: Health score (0-100) with status (GOOD/WARNING/CRITICAL)

Example:
  Customer: Initech
  Health Score: 45/100 (WARNING)
  Status: "Usage declining (40% drop in 30 days), 3 support tickets open"
  Trend: "Declining for 60 days"
  Recommendation: "Schedule customer success call, review onboarding"

Implementation:
  - Score updated weekly
  - Alert on WARNING → CRITICAL transition
  - Customer success team notified
```

### 8. Permission-Filtered Outputs

```
All AI CRM outputs respect permissions:
  - ADMIN: Sees all deals, all contacts, all AI insights
  - MANAGER: Sees team deals, team contacts, team AI insights
  - USER: Sees own deals, own contacts, own AI insights
  - VIEWER: Sees AI insights (read-only), cannot trigger AI

AI cannot:
  - Show data outside user's permission scope
  - Recommend actions user cannot perform
  - Access cross-tenant CRM data

All outputs are:
  - Explainable (reasoning provided)
  - Permission-aware (filtered by role)
  - Tenant-isolated (no cross-tenant data)
  - Human-confirmed for high-impact actions
```

## CRM AI Productization Summary

```
Features defined: 8
  1. Lead scoring
  2. Opportunity intelligence
  3. Customer summary
  4. Next best action
  5. Follow-up recommendation
  6. Sales pipeline risk
  7. Customer health signal
  8. Permission-filtered outputs

All features:
  - Explainable ✅
  - Permission-aware ✅
  - Tenant-isolated ✅
  - Human-confirmed for high-impact ✅

Implementation: DOCUMENTED (ready for development)
Dependencies: AI Gateway (Stage 16 foundation), CRM module (exists)
```
