# CRM-010 AI Gateway Contracts

> **Module:** CRM-010 — Customer 360 & Unified Customer Intelligence
> **Date:** 2026-07-29
> **Status:** APPROVED

---

## 1. Overview

All AI capabilities flow through the governed `AiGatewayPort` established in CRM-009. Each capability uses contract name `crm.customer_intelligence.ai.<capability>` and is dispatched via the transactional outbox pattern.

---

## 2. Customer Health Scoring

### Endpoint
`POST {ai-gateway-base-url}/v1/ai/execute`

### Request Schema
```json
{
  "contractName": "crm.customer_intelligence.ai.health_scoring",
  "contractVersion": "1.0",
  "tenantId": "UUID",
  "capability": "HEALTH_SCORING",
  "payload": {
    "accountId": "UUID",
    "sourceEntityType": "ACCOUNT",
    "sourceEntityId": "UUID",
    "sourceEntityVersion": 5,
    "indicators": {
      "daysSinceLastActivity": 14,
      "openOpportunities": 2,
      "totalPipelineAmount": 150000,
      "meetingFrequency30d": 4,
      "responseTimeAvgHours": 6,
      "supportTicketsOpen": 1,
      "npsScore": 8,
      "lifecycleStatus": "ACTIVE"
    }
  }
}
```

### Response Schema
```json
{
  "status": "AVAILABLE",
  "generatedText": "Customer is healthy with active engagement.",
  "actionCode": null,
  "explanation": "Health score 78.5 derived from engagement(65%), pipeline(80%), response(85%), support(90%)",
  "confidence": 0.89,
  "generatedAt": "2026-07-29T00:00:00Z",
  "expiresAt": "2026-07-29T00:05:00Z",
  "humanConfirmationRequired": false,
  "sourceReferences": [],
  "policyVersion": "health-v2.1",
  "modelVersion": "gradient-boost-v3"
}
```

### Confidence Score: 0.0–1.0 (minimum threshold 0.6 for BAND assignment)
### Threshold Policy: <0.6 confidence → band=UNCERTAIN, no automated workflow
### Timeout: 5000ms
### Retry: Exponential backoff (2^n, max 3 attempts, retryable on TIMED_OUT/UNAVAILABLE)
### Failure Behavior: UNAVAILABLE status, cached score retained, alert if >24h stale
### Fallback: Last known score with `stale=true` flag
### RBAC: CRM.CUSTOMER_INTELLIGENCE.READ to request; CRM.CUSTOMER_INTELLIGENCE.WRITE to rescore

---

## 3. Customer Lifetime Value (CLV)

### Endpoint
`POST {ai-gateway-base-url}/v1/ai/execute`

### Request Schema
```json
{
  "contractName": "crm.customer_intelligence.ai.clv_forecast",
  "tenantId": "UUID",
  "capability": "CLV_FORECAST",
  "payload": {
    "accountId": "UUID",
    "historicalData": {
      "totalRevenue": 250000,
      "transactionCount": 42,
      "avgDealSize": 5952,
      "customerSinceMonths": 18,
      "growthRate": 0.12
    }
  }
}
```

### Response Schema
```json
{
  "status": "AVAILABLE",
  "actionCode": null,
  "explanation": "CLV of 320,000 SAR projected over 36 months",
  "confidence": 0.82,
  "customFields": {
    "predictedValue": 320000,
    "historicalValue": 250000,
    "tier": "HIGH_VALUE",
    "horizonMonths": 36
  },
  "humanConfirmationRequired": false
}
```

### Confidence Score: 0.0–1.0 (minimum 0.5 for tier assignment)
### Timeout: 8000ms (longer due to forecasting)
### Retry: max 2 attempts
### Fallback: historicalValue as predictedValue with confidence=0.0
### RBAC: CRM.CUSTOMER_INTELLIGENCE.READ

---

## 4. Churn Prediction

### Request Schema
```json
{
  "contractName": "crm.customer_intelligence.ai.churn_prediction",
  "capability": "CHURN_PREDICTION",
  "payload": {
    "accountId": "UUID",
    "riskIndicators": {
      "daysSinceLastActivity": 45,
      "engagementDeclinePct": 35,
      "openIssuesUnresolved": 2,
      "contractRenewalDays": 60,
      "competitorSignals": "none"
    }
  }
}
```

### Response Schema
```json
{
  "status": "AVAILABLE",
  "actionCode": "SCHEDULE_RETENTION_CALL",
  "explanation": "Churn probability 72% — engagement declining, renewal approaching",
  "confidence": 0.78,
  "humanConfirmationRequired": true,
  "customFields": {
    "churnProbability": 0.72,
    "riskBand": "HIGH_RISK",
    "topRiskFactors": ["engagement_decline", "renewal_approaching"]
  }
}
```

### Confidence Score: 0.0–1.0 (minimum 0.65 for HIGH_RISK action)
### Threshold Policy: >0.7 probability + >0.65 confidence → auto-create retention workflow (human-confirmed)
### Timeout: 5000ms
### Fallback: No prediction, manual review flag
### RBAC: CRM.CUSTOMER_INTELLIGENCE.READ to view; CRM.CUSTOMER_INTELLIGENCE.WRITE to act

---

## 5. Next Best Action

### Request Schema
```json
{
  "contractName": "crm.customer_intelligence.ai.next_best_action",
  "capability": "NEXT_BEST_ACTION",
  "payload": {
    "accountId": "UUID",
    "context": {
      "currentStage": "PROPOSAL",
      "lastInteraction": "MEETING",
      "openOpportunities": 2,
      "segmentTier": "GOLD"
    }
  }
}
```

### Response Schema
```json
{
  "status": "AVAILABLE",
  "actionCode": "SEND_PROPOSAL_FOLLOWUP",
  "explanation": "Customer in proposal stage, 7 days since last meeting — follow up recommended",
  "confidence": 0.85,
  "humanConfirmationRequired": true,
  "customFields": {
    "priority": "HIGH",
    "suggestedTiming": "within_48h",
    "channel": "EMAIL"
  }
}
```

### Timeout: 5000ms
### Fallback: Rule-based fallback (stage-based heuristics)
### RBAC: CRM.CUSTOMER_INTELLIGENCE.READ to view; CRM.AI.CONFIRM to execute

---

## 6. Intelligent Segmentation

### Request Schema
```json
{
  "contractName": "crm.customer_intelligence.ai.segmentation",
  "capability": "SEGMENTATION",
  "payload": {
    "accountIds": ["UUID"],
    "segmentCriteria": {
      "method": "KMEANS",
      "features": ["revenue", "engagement", "recency", "tenure"],
      "k": 5
    }
  }
}
```

### Response Schema
```json
{
  "status": "AVAILABLE",
  "explanation": "5 segments identified based on revenue-engagement patterns",
  "confidence": 0.80,
  "humanConfirmationRequired": true,
  "customFields": {
    "segments": [
      { "segmentId": "champions", "accountCount": 45, "centroid": {} },
      { "segmentId": "at_risk", "accountCount": 23, "centroid": {} }
    ]
  }
}
```

### Timeout: 30000ms (batch operation)
### RBAC: CRM.CUSTOMER_SEGMENT.MANAGE

---

## 7. Opportunity Scoring

### Request Schema
```json
{
  "contractName": "crm.customer_intelligence.ai.opportunity_scoring",
  "capability": "OPPORTUNITY_DETECTION",
  "payload": {
    "accountId": "UUID",
    "signals": {
      "recentInquiries": ["product_upgrade", "additional_license"],
      "budgetIndicators": "positive",
      "decisionMakerEngagement": "high"
    }
  }
}
```

### Response Schema
```json
{
  "status": "AVAILABLE",
  "actionCode": "CREATE_UPSELL_OPPORTUNITY",
  "explanation": "Strong upsell signal — product upgrade inquiry + budget positive",
  "confidence": 0.83,
  "humanConfirmationRequired": true,
  "customFields": {
    "opportunityScore": 87,
    "estimatedValue": 45000,
    "type": "UPSSELL"
  }
}
```

### Timeout: 5000ms
### RBAC: CRM.CUSTOMER_INTELLIGENCE.READ; CRM.AI.CONFIRM to create opportunity

---

## 8. Common Policies

| Policy | Value |
|--------|-------|
| Authentication | Service JWT (HMAC-SHA256, 32-byte min secret) |
| Transport | HTTPS only |
| Envelope Expiry | 30 seconds |
| Human Confirmation | Required for all actionable outputs (actionCode != null) |
| Audit | Every AI request/response audited via AuditPort |
| Outbox | All requests via transactional outbox (no synchronous AI in request path) |
| Fail-Closed | UNAVAILABLE on any error — never throw to caller |

---

**Contract Authority:** Program Execution Coordinator
**Date:** 2026-07-29
**Status:** ✅ APPROVED
