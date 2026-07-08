# Stage 16 — AI Gateway Foundation

**Date**: 2026-07-08

---

## AI Gateway Role

The AI Gateway is the centralized entry point for all AI model access within
SNAD. It sits between SNAD modules (CRM, ERP, HRM, Accounting, Workflow) and
external AI providers (OpenAI, Anthropic, local models).

```
[SNAD Modules] → [AI Gateway] → [AI Providers]
                      ↓
              [Policy Engine]
              [Cost Tracker]
              [Audit Log]
              [Cache]
```

## Supported Request Types

```
1. Text Generation (completion, chat, summarization)
2. Embeddings (vector generation for search/clustering)
3. Classification (sentiment, intent, category)
4. Extraction (entity recognition, data parsing)
5. Translation (ar ↔ en, multi-language)
6. Code Generation (workflow scripts, formulas)
7. Image Analysis (OCR, document understanding — future)
8. Speech-to-Text (voice input — future)
```

## Integration Interfaces (Future)

```
The AI Gateway will connect to:

1. CRM Module:
   - Lead scoring
   - Contact enrichment
   - Deal prediction
   - Communication summarization

2. ERP Module:
   - Operations analysis
   - Inventory recommendations
   - Procurement suggestions

3. Accounting Module:
   - Entry review
   - Anomaly detection
   - Expense analysis

4. HRM Module:
   - Employee summaries
   - Attendance analysis
   - Training recommendations

5. Workflow Engine:
   - Step suggestions
   - Bottleneck detection
   - Next best action

6. Analytics:
   - Natural language queries
   - Insight generation
   - Predictive analytics

7. Customer Support:
   - Ticket classification
   - Response suggestions
   - Sentiment analysis
```

## Authentication & Authorization

```
Authentication:
  - All AI Gateway requests require valid JWT (user session)
  - Tenant ID extracted from token
  - User role checked for AI feature access

Authorization:
  - Per-tenant AI feature flags
  - Per-user role-based AI access
  - Per-tier limits (Free: no AI, Professional: limited, Enterprise: full)
  - Rate limiting per tenant and per user

Authorization flow:
  1. Request received with JWT
  2. Gateway validates JWT
  3. Gateway checks tenant AI feature flag
  4. Gateway checks user role AI permission
  5. Gateway checks rate limit
  6. Gateway checks cost ceiling
  7. If all pass: forward to AI provider
  8. If any fail: return 403 Forbidden with reason
```

## Usage Limits

```
Per-tenant limits (by tier):
  Free Pilot: 0 AI requests/month (no AI access)
  Professional: 1,000 AI requests/month
  Enterprise: 10,000 AI requests/month (or custom)

Per-user limits:
  Default: 100 requests/day
  Admin: 500 requests/day
  Custom: Configurable per tenant

Rate limiting:
  - 10 requests per minute per user
  - 100 requests per minute per tenant
  - Burst: 20 requests in 10 seconds (then throttle)

Cost limits:
  - Per-tenant monthly cost ceiling (e.g., $50/month)
  - Per-request cost estimate (based on token count)
  - Hard stop when ceiling reached (unless owner approves overage)
```

## Audit Logging

```
Every AI request is logged:
  - Timestamp (UTC)
  - Tenant ID
  - User ID
  - Request type (generation, classification, etc.)
  - Input (redacted/sanitized)
  - Output (redacted/sanitized)
  - Model used
  - Token count (input + output)
  - Cost (estimated)
  - Latency
  - Status (success/error)
  - Human approval (if required)

Log retention: 1 year
Log access: Owner + tenant admin (own tenant only)
Log storage: Encrypted at rest
```

## Cost Monitoring

```
Real-time cost tracking:
  - Per-request cost (tokens × price per token)
  - Per-tenant daily/monthly accumulation
  - Per-module cost breakdown
  - Cost alerts (50%, 80%, 100% of ceiling)

Cost reporting:
  - Daily cost summary (owner dashboard)
  - Monthly cost report per tenant
  - Cost optimization recommendations
  - Model comparison (cost vs quality)

Cost control:
  - Hard ceiling per tenant (stops AI when exceeded)
  - Soft warning at 80% (notifies admin)
  - Overage requires owner approval
  - Free tier: no AI access (cost = $0)
```

## AI Disable Switch

```
The AI Gateway has a kill switch that can disable all AI functionality:

Triggers:
  1. Owner manual disable (emergency)
  2. Cost ceiling exceeded (per tenant or global)
  3. Security incident (AI misuse detected)
  4. AI provider outage (fallback to cached/no-AI mode)
  5. Compliance hold (legal review required)

Behavior when disabled:
  - All AI requests return 503 Service Unavailable
  - Modules fall back to non-AI behavior
  - Users see "AI features temporarily unavailable"
  - No data loss (AI is additive, not core)
  - Production remains LIVE (AI is not required for core function)

Re-enable:
  1. Owner approval
  2. Root cause resolved
  3. Cost ceiling adjusted (if cost-related)
  4. Security review complete (if security-related)
```

## Future Provider Integrations

```
Phase 1 (Stage 17): OpenAI (GPT-4, GPT-3.5, embeddings)
Phase 2 (Stage 18): Anthropic (Claude), local models (Llama)
Phase 3 (Stage 19+): Azure OpenAI, AWS Bedrock, custom models

Provider abstraction:
  - Unified API interface
  - Provider-specific adapters
  - Fallback chain (if primary fails, try secondary)
  - Model selection per use case (cost vs quality)
```

## AI Gateway Readiness

```
Foundation document: COMPLETE ✅
Provider integrations: NOT YET IMPLEMENTED ⚠️ (Stage 17)
Policy engine: DOCUMENTED ✅ (see 03-AI-POLICY-GOVERNANCE-ENGINE.md)
Cost tracking: DESIGNED ✅
Audit logging: DESIGNED ✅
Disable switch: DESIGNED ✅

AI Gateway Foundation: BASELINED
  → Architecture defined
  → Implementation deferred to Stage 17
  → No AI requests until gateway is deployed
```
