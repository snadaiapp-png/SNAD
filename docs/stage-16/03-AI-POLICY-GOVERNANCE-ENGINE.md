# Stage 16 — AI Policy & Governance Engine

**Date**: 2026-07-08

---

## AI Usage Permissions

### Per-Tier Permissions

```
Free Pilot:
  - No AI access
  - AI features hidden in UI
  - Upgrade prompt shown

Professional:
  - Read-only AI (summaries, insights)
  - Recommend-only AI (suggestions, not actions)
  - Limited to 1,000 requests/month
  - No high-impact decision AI

Enterprise:
  - Full AI access (read, recommend, draft)
  - Human-approved action AI (with explicit confirmation)
  - 10,000+ requests/month (custom)
  - Custom AI policies per tenant
```

### Per-Role Permissions

```
ADMIN:
  - Configure AI feature flags
  - Set AI usage limits
  - View AI audit logs
  - Approve high-impact AI actions
  - Disable AI for tenant

MANAGER:
  - Use AI features (read + recommend)
  - Approve AI-drafted actions
  - View team AI usage
  - Cannot configure limits

USER:
  - Use AI features (read + recommend)
  - Cannot approve AI actions
  - Cannot view usage stats

VIEWER:
  - View AI-generated insights (read-only)
  - Cannot trigger AI requests
```

## High-Impact Decision Classification

```
High-impact AI decisions (require human confirmation):
  1. Financial transactions (payments, transfers, refunds)
  2. Data deletion (any data removal)
  3. Permission changes (role grants, access changes)
  4. Communication sending (emails, SMS to customers)
  5. Workflow execution (high-impact workflows)
  6. Contract changes (customer/vendor agreements)
  7. Payroll actions (salary changes, bonuses)
  8. Inventory adjustments (large quantity changes)

Medium-impact (recommendation only):
  1. Lead scoring updates
  2. Deal stage changes
  3. Contact enrichment
  4. Task assignment suggestions
  5. Training recommendations

Low-impact (autonomous allowed):
  1. Summarization (text, conversations)
  2. Classification (sentiment, category)
  3. Translation
  4. Search (natural language query)
  5. Insights (read-only analytics)
```

## Human Approval Requirement

```
MANDATORY RULE:
  No high-impact AI decision may execute without human confirmation.

Implementation:
  1. AI generates recommendation/draft
  2. System presents to authorized human
  3. Human reviews (can see AI explanation)
  4. Human approves, modifies, or rejects
  5. Only on approval: action executes
  6. Audit log records: AI recommendation + human decision + action

Approval UI:
  - Clear labeling: "AI-Generated Recommendation"
  - Explanation: Why AI recommends this (explainability)
  - Confidence: AI confidence score (if available)
  - Alternatives: Other options considered
  - Risk: Potential impact highlighted
  - Confirmation: Explicit "Approve" button (no auto-confirm)
```

## Preventing Autonomous Sensitive Actions

```
BLOCKED (AI cannot do, even with approval):
  1. Delete production data
  2. Execute financial transfers
  3. Change user passwords
  4. Modify audit logs
  5. Disable security controls
  6. Access cross-tenant data
  7. Train models on tenant data
  8. Send communications to all customers

AI can only:
  - Recommend these actions
  - Draft the action for human review
  - Provide context and explanation
  - Wait for human execution
```

## Tenant Data Protection

```
1. No cross-tenant data access:
   - AI requests include tenant_id from JWT
   - AI Gateway enforces tenant isolation
   - AI provider receives only tenant-scoped data
   - No tenant data in prompts from other tenants

2. No tenant data for model training:
   - AI provider API calls use "no training" flag
   - Data sent to AI providers is ephemeral (not stored)
   - Local models (if used) are per-tenant or shared without tenant data
   - Audit log verifies no training usage

3. Data classification:
   - PII (personally identifiable information): Redacted before AI
   - Financial data: Masked in prompts
   - Credentials: Never sent to AI
   - Tenant secrets: Never sent to AI

4. Data retention:
   - AI request/response logs: 1 year (redacted)
   - AI-generated content: Stored with tenant data (tenant owns it)
   - AI provider data: Per provider policy (verified)
```

## AI Decision Audit Trail

```
Every AI decision or recommendation is logged:

{
  "timestamp": "2026-07-08T12:00:00Z",
  "tenant_id": "uuid",
  "user_id": "uuid",
  "request_type": "lead_scoring",
  "input_summary": "Contact: Ahmed (redacted)",
  "output_summary": "Score: 85/100 (High priority)",
  "model": "gpt-4",
  "tokens": {"input": 150, "output": 50},
  "cost": 0.003,
  "latency_ms": 1200,
  "status": "success",
  "human_approval": "not_required",
  "action_taken": "none (recommendation only)",
  "explanation": "Based on engagement history and deal size"
}

For high-impact decisions:
  "human_approval": "required",
  "approver": "uuid",
  "approval_time": "2026-07-08T12:05:00Z",
  "approval_decision": "approved",
  "action_executed": "deal_stage_changed_to_negotiation"
```

## Owner Review Mechanism

```
Owner (snadaiapp-png) can review:
  1. All AI audit logs (all tenants)
  2. AI cost reports (global)
  3. AI usage patterns (anomaly detection)
  4. AI incident reports
  5. AI policy compliance

Owner can:
  1. Disable AI globally (emergency)
  2. Disable AI per tenant
  3. Adjust AI policies
  4. Approve AI overage (cost)
  5. Review and close AI incidents

Owner cannot:
  1. Access tenant data without tenant consent
  2. Modify AI audit logs
  3. Bypass human approval for high-impact actions
```

## AI Policy Enforcement

```
Enforcement points:
  1. AI Gateway (request validation)
  2. Module layer (action validation)
  3. Database layer (data access validation)

Enforcement is fail-closed:
  - If policy engine is unavailable: AI requests blocked
  - If tenant policy is ambiguous: deny by default
  - If human approval cannot be verified: action blocked
```

## Governing Rule

```
No high-impact AI decision may execute without human confirmation.
This rule is non-negotiable and enforced at multiple layers.
Violation of this rule is a Critical security incident.
```
