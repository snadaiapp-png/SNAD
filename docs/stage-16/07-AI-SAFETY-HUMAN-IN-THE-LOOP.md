# Stage 16 — AI Safety & Human-in-the-Loop Controls

**Date**: 2026-07-08

---

## AI Safety Principles

```
1. Human Confirmation: No high-impact AI action without human approval
2. Explainability: Every AI recommendation is explainable
3. Permission Filtering: AI respects RBAC and tenant isolation
4. Tenant Isolation: No cross-tenant data access or training
5. Prompt Injection Safeguards: Input validation and sanitization
6. Data Leakage Controls: Sensitive data redacted before AI
7. Output Review: AI outputs reviewed before action
8. Kill Switch: AI can be disabled globally or per-tenant
```

## 1. Human Confirmation

```
Mandatory for:
  - Financial transactions
  - Data deletion
  - Permission changes
  - Communication sending
  - Workflow execution (high-impact)
  - Contract changes
  - Payroll actions
  - Inventory adjustments (large)

Implementation:
  - AI generates recommendation
  - UI shows recommendation with explanation
  - Human reviews and explicitly approves
  - Only on approval: action executes
  - Audit log records both AI and human decisions

No silent AI actions.
No auto-approval.
No bypass for any role (including admin).
```

## 2. Explainability

```
Every AI output includes:
  - Recommendation/draft
  - Confidence score (0-100%)
  - Reasoning (text explanation)
  - Factors considered (list)
  - Alternatives (other options)
  - Risk assessment (potential impact)

Example:
  AI: "Recommend changing deal stage to 'Closed Won'"
  Confidence: 87%
  Reasoning: "Customer signed proposal, payment received, delivery confirmed"
  Factors: ["Proposal signed: 2026-07-05", "Payment: $50k received", "Delivery: confirmed"]
  Alternatives: ["Keep in 'Negotiation' (risk: delay reporting)"]
  Risk: "Low — action is reversible"

If AI cannot explain: recommendation is BLOCKED (safety fallback)
```

## 3. Permission Filtering

```
AI respects all RBAC rules:
  - ADMIN: Can see all tenant data, configure AI
  - MANAGER: Can see team data, approve AI actions
  - USER: Can see own data, use AI (read/recommend only)
  - VIEWER: Can see AI insights, cannot trigger AI

AI cannot:
  - Elevate privileges (no role escalation)
  - Access data the user cannot access
  - Bypass tenant isolation
  - Modify audit logs

Permission check flow:
  1. User triggers AI feature
  2. AI Gateway checks user role permission
  3. If denied: return 403 (no AI request sent)
  4. If allowed: AI request proceeds with user's data scope
```

## 4. Tenant Isolation

```
AI enforces tenant isolation:
  - tenant_id from JWT included in every AI request
  - AI Gateway validates tenant_id matches user's session
  - Data sent to AI is scoped to tenant_id
  - AI provider receives only tenant-scoped data
  - No tenant data in shared model training

Cross-tenant access:
  - BLOCKED at AI Gateway
  - BLOCKED at data layer (WHERE tenant_id = ?)
  - BLOCKED at audit log (tenant_id verified)

Violation:
  - Any cross-tenant AI access = Critical security incident
  - Immediate AI disable
  - Owner notification
  - Incident investigation
```

## 5. Prompt Injection Safeguards

```
Threat: Malicious input designed to manipulate AI behavior.

Safeguards:
  1. Input sanitization:
     - Strip system prompt overrides
     - Remove "ignore previous instructions" patterns
     - Filter special characters that affect AI parsing

  2. Input validation:
     - Max input length (4,000 tokens)
     - Allowed content types (text only, no code execution)
     - Suspicious pattern detection

  3. System prompt protection:
     - System prompts are server-side only (never from user)
     - User input is clearly delimited as "user data"
     - AI instructed to treat user input as data, not instructions

  4. Output validation:
     - AI output checked for sensitive data leakage
     - Output checked for prompt injection markers
     - Output checked for unauthorized action suggestions

  5. Monitoring:
     - Log all AI inputs (redacted)
     - Detect anomalous patterns
     - Alert on potential injection attempts
```

## 6. Data Leakage Controls

```
Sensitive data never sent to AI:
  - Passwords, tokens, API keys
  - Credit card numbers, bank accounts
  - National IDs, medical records
  - Tenant secrets

Sensitive data redacted before AI:
  - Email addresses: masked (a***@example.com)
  - Phone numbers: masked (+966*** ****)
  - Addresses: generalized (city level)
  - Salary: ranges ($50k-70k)

Implementation:
  - Pre-AI data processor: redacts/masks sensitive fields
  - Post-AI output checker: verifies no sensitive data in output
  - Audit log: records what was redacted

Data leakage incident:
  - If detected: immediate AI disable
  - Incident investigation
  - Affected tenants notified
  - Root cause fixed before re-enable
```

## 7. Output Review

```
AI outputs are reviewed before action:

Low-impact (autonomous):
  - Summaries, classifications, translations
  - Displayed directly to user
  - User can dismiss or use

Medium-impact (recommendation):
  - Suggestions, insights, next-best-actions
  - Displayed with "AI Recommendation" label
  - User can accept, modify, or dismiss
  - Acceptance logged

High-impact (human approval):
  - Drafts of actions (emails, entries, changes)
  - Displayed with explanation and risk
  - Requires explicit "Approve" button
  - Approval logged with approver ID

Blocked (no AI):
  - Direct execution of sensitive actions
  - AI can only recommend, never execute
```

## 8. Kill Switch (AI Disable)

```
Global AI disable (owner only):
  Trigger: Emergency, security incident, cost overrun
  Effect: All AI requests return 503
  Re-enable: Owner approval + root cause resolved

Per-tenant AI disable (admin or owner):
  Trigger: Tenant request, cost ceiling, policy violation
  Effect: AI features hidden for that tenant
  Re-enable: Admin re-enables or owner approves

Per-feature disable:
  Trigger: Feature-specific issue (e.g., AI CRM intelligence bug)
  Effect: Specific feature disabled, others remain
  Re-enable: After fix deployed and tested

Behavior when AI disabled:
  - Core platform functions normally (AI is additive)
  - Users see "AI features temporarily unavailable"
  - No data loss
  - No production impact
  - Audit log continues (records disable/enable events)
```

## AI Safety Summary

```
Safety controls: 8 categories implemented
  1. Human confirmation (high-impact) ✅
  2. Explainability (all outputs) ✅
  3. Permission filtering (RBAC) ✅
  4. Tenant isolation (no cross-tenant) ✅
  5. Prompt injection safeguards ✅
  6. Data leakage controls ✅
  7. Output review (tiered) ✅
  8. Kill switch (global/per-tenant/per-feature) ✅

Mandatory rule:
  No high-impact AI decision may execute without human confirmation.

AI Safety Status: CONTROLS DOCUMENTED
  → Safety framework defined
  → Implementation deferred to Stage 17 (with AI Gateway)
  → Kill switch available before AI goes live
```
