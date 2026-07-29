# CRM-010 AI Capability Specifications

> **Module:** CRM-010 — Customer 360 & Unified Customer Intelligence
> **Date:** 2026-07-29
> **Status:** APPROVED

---

## 1. Overview

Six AI capabilities power CRM-010's customer intelligence. Each specification defines the business objective, inputs, implementation approach, outputs, and validation strategy.

---

## 2. Customer Health Scoring

| Attribute | Value |
|-----------|-------|
| Business Objective | Identify customer health to prioritize account management |
| Implementation | **Hybrid** (rule-based baseline + ML refinement) |
| Contract | crm.customer_intelligence.ai.health_scoring |

### Required Inputs
| Input | Source | Required |
|-------|--------|----------|
| daysSinceLastActivity | CRM activities | ✅ |
| openOpportunities | CRM opportunities | ✅ |
| totalPipelineAmount | CRM opportunities | ✅ |
| meetingFrequency30d | CRM activities | ✅ |
| responseTimeAvgHours | CRM activities | ✅ |
| supportTicketsOpen | CRM (future: support) | Optional |
| npsScore | CRM (future: surveys) | Optional |
| lifecycleStatus | CRM accounts | ✅ |

### Expected Outputs
| Output | Range | Description |
|--------|-------|-------------|
| score | 0.0–100.0 | Overall health |
| band | CRITICAL/AT_RISK/HEALTHY/THRIVING | Categorical |
| components | JSON | Weighted factor breakdown |
| confidence | 0.0–1.0 | Model confidence |

### Explainability
- Each component contributes a weighted score
- Top 3 contributing factors explained in `explanation` field
- Score history shows trend over time

### KPIs
| KPI | Target |
|-----|--------|
| Score accuracy vs. manual review | ≥80% agreement |
| Score stability (week-over-week) | <10% variance without events |
| Time to calculate | <3s per customer |

### Validation Strategy
- Compare AI scores with manual account manager assessments (quarterly)
- A/B test: score-based prioritization vs. intuition-based
- Track outcome correlation (did "healthy" customers retain?)

### Monitoring Metrics
| Metric | Alert Threshold |
|--------|----------------|
| Calculation failures | >5% |
| Score variance (unexplained) | >15% |
| AI confidence <0.6 | >10% of scores |

---

## 3. Customer Lifetime Value (CLV)

| Attribute | Value |
|-----------|-------|
| Business Objective | Predict total revenue a customer will generate |
| Implementation | **ML** (time-series forecasting) |
| Contract | crm.customer_intelligence.ai.clv_forecast |

### Required Inputs
| Input | Source | Required |
|-------|--------|----------|
| totalRevenue (historical) | CRM opportunities (won) | ✅ |
| transactionCount | CRM opportunities | ✅ |
| avgDealSize | Computed from opportunities | ✅ |
| customerSinceMonths | crm_accounts.created_at | ✅ |
| growthRate | Computed from revenue trend | ✅ |

### Expected Outputs
| Output | Range | Description |
|--------|-------|-------------|
| predictedValue | >0 | 36-month projected revenue |
| historicalValue | >0 | Actual revenue to date |
| tier | HIGH_VALUE/MID_VALUE/LOW_VALUE | Categorical |
| confidence | 0.0–1.0 | Forecast confidence |
| horizonMonths | 36 | Fixed forecast horizon |

### Explainability
- Historical contribution shown
- Growth rate assumption documented
- Confidence interval provided

### KPIs
| KPI | Target |
|-----|--------|
| Forecast accuracy (12-month lookback) | ±20% of actual |
| Confidence calibration | Within 10% of stated confidence |

---

## 4. Churn Prediction

| Attribute | Value |
|-----------|-------|
| Business Objective | Predict customers likely to churn for retention action |
| Implementation | **ML** (classification model) |
| Contract | crm.customer_intelligence.ai.churn_prediction |

### Required Inputs
| Input | Source | Required |
|-------|--------|----------|
| daysSinceLastActivity | CRM activities | ✅ |
| engagementDeclinePct | Computed (30d vs 90d) | ✅ |
| openIssuesUnresolved | CRM (future: support) | Optional |
| contractRenewalDays | CRM (future: contracts) | Optional |
| competitorSignals | CRM notes (NLP) | Optional |

### Expected Outputs
| Output | Range | Description |
|--------|-------|-------------|
| churnProbability | 0.0–1.0 | Probability of churn in 90 days |
| riskBand | LOW_RISK/MEDIUM_RISK/HIGH_RISK | Categorical |
| topRiskFactors | Array | Ranked contributing factors |

### Explainability
- Top 3 risk factors listed with individual contribution
- Trend over time (probability increasing/decreasing)
- Comparable cohort benchmarks

### KPIs
| KPI | Target |
|-----|--------|
| Prediction accuracy (90-day actual churn) | ≥75% precision |
| False positive rate | <20% |
| Retention success on HIGH_RISK | ≥30% retained |

### Validation Strategy
- Track actual churn outcomes vs. predictions
- Quarterly model recalibration
- Compare with rule-based baseline (days inactive > 60)

---

## 5. Next Best Action

| Attribute | Value |
|-----------|-------|
| Business Objective | Recommend the optimal next action for each customer |
| Implementation | **Hybrid** (rule-based fallback + ML recommendation) |
| Contract | crm.customer_intelligence.ai.next_best_action |

### Required Inputs
| Input | Source | Required |
|-------|--------|----------|
| currentStage | CRM opportunity stage | ✅ |
| lastInteraction | CRM activities | ✅ |
| openOpportunities | CRM opportunities | ✅ |
| segmentTier | crm_accounts.customer_tier | ✅ |
| healthScore | crm_customer_scores | ✅ |

### Expected Outputs
| Output | Description |
|--------|-------------|
| actionCode | SCHEDULE_FOLLOWUP / SEND_CONTENT / CREATE_OPPORTUNITY / etc. |
| description | Human-readable action |
| confidence | 0.0–1.0 |
| priority | HIGH/MEDIUM/LOW |
| suggestedTiming | within_48h / within_week / within_month |
| channel | EMAIL / PHONE / MEETING / IN_PERSON |

### Explainability
- Why this action (context-based reasoning)
- Why not other actions (ranking)
- Historical success rate of this action type

### KPIs
| KPI | Target |
|-----|--------|
| Action acceptance rate | ≥40% |
| Outcome improvement (accepted vs. not) | ≥15% better engagement |

---

## 6. Intelligent Segmentation

| Attribute | Value |
|-----------|-------|
| Business Objective | Automatically group customers by behavioral patterns |
| Implementation | **ML** (unsupervised clustering) |
| Contract | crm.customer_intelligence.ai.segmentation |

### Required Inputs
| Input | Source | Required |
|-------|--------|----------|
| accountIds | CRM accounts | ✅ |
| features (revenue, engagement, recency, tenure) | Computed | ✅ |
| method | KMEANS / DBSCAN | ✅ |
| k (cluster count) | Configurable | ✅ |

### Expected Outputs
| Output | Description |
|--------|-------------|
| segments | Array of {segmentId, accountCount, centroid} |
| accountAssignments | accountId → segmentId mapping |

### Explainability
- Segment characteristics (centroid description)
- Feature importance per segment
- Segment stability over time

### KPIs
| KPI | Target |
|-----|--------|
| Segment stability (month-over-month) | ≥70% membership retention |
| Segment differentiation | >2σ between centroids |

---

## 7. Opportunity Scoring

| Attribute | Value |
|-----------|-------|
| Business Objective | Detect and score upsell/cross-sell opportunities |
| Implementation | **ML** (classification + scoring) |
| Contract | crm.customer_intelligence.ai.opportunity_scoring |

### Required Inputs
| Input | Source | Required |
|-------|--------|----------|
| recentInquiries | CRM notes/activities (NLP) | ✅ |
| budgetIndicators | CRM opportunities | ✅ |
| decisionMakerEngagement | CRM activities | ✅ |
| productUsage | CRM (future: telemetry) | Optional |

### Expected Outputs
| Output | Description |
|--------|-------------|
| opportunityScore | 0–100 |
| estimatedValue | Currency amount |
| type | UPSELL / CROSS_SELL / RENEWAL / EXPANSION |

### Explainability
- Signal strength per indicator
- Comparable historical conversions
- Confidence interval on estimated value

### KPIs
| KPI | Target |
|-----|--------|
| Conversion rate (scored opportunities → won) | ≥25% for score >70 |
| Estimated value accuracy | ±30% of actual |

---

## 8. Common Validation Strategy

| Method | Description |
|--------|-------------|
| Backtesting | Compare predictions with historical outcomes |
| Shadow mode | Run AI alongside manual process, compare results |
| A/B testing | Compare AI-guided vs. control group outcomes |
| Human review | Sample 10% of AI outputs for manual validation |
| Drift detection | Monitor input distribution changes over time |

---

## 9. Common Monitoring Framework

| Metric | Description | Alert |
|--------|-------------|-------|
| Request rate | AI requests per minute | >100/min |
| Latency p99 | 99th percentile response time | >8s |
| Error rate | Failed requests percentage | >5% |
| Confidence trend | Average confidence over time | Declining >10% |
| Human override rate | Accepted vs. rejected recommendations | Reject >60% |

---

**AI Capability Specifications Authority:** Program Execution Coordinator
**Date:** 2026-07-29
**Status:** ✅ APPROVED
