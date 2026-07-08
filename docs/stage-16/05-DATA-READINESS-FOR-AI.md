# Stage 16 — Data Readiness for AI

**Date**: 2026-07-08

---

## Available Data Sources

```
1. CRM Data:
   - Contacts (name, email, phone, company)
   - Deals (value, stage, expected close date)
   - Activities (calls, emails, meetings, notes)
   - Pipeline history

2. User Data:
   - Profile (name, email, role, tenant)
   - Activity log (login, feature usage)
   - Preferences (language, theme)

3. Organization Data:
   - Tenant info (name, plan, created date)
   - Membership (user-tenant relationships)
   - Role grants (RBAC)

4. Workflow Data (when implemented):
   - Workflow definitions
   - Execution history
   - Step timings
   - Approval records

5. Audit Log:
   - API request log
   - Authentication events
   - Data access events
   - AI request log (when AI enabled)
```

## Data Quality Assessment

```
Completeness:
  - CRM contacts: 60-80% complete (depends on tenant usage)
  - Deal data: 70-90% complete (required fields enforced)
  - Activity log: 50-70% complete (manual entry dependent)

Accuracy:
  - User data: High (validated on signup)
  - CRM data: Medium (user-entered, may have errors)
  - Audit log: High (system-generated)

Consistency:
  - Cross-module: Medium (some data duplication)
  - Historical: High (immutable audit log)

Timeliness:
  - Real-time: Activities logged immediately
  - Batch: Analytics aggregated daily

Overall quality: SUFFICIENT for AI features with preprocessing
```

## Sensitive Data Classification

```
Highly Sensitive (never sent to AI):
  - Passwords (hashed, never plaintext)
  - Access tokens / refresh tokens
  - API keys / secrets
  - Credit card numbers
  - Bank account numbers
  - National ID numbers
  - Medical records

Sensitive (redacted/masked before AI):
  - Email addresses (mask: a***@example.com)
  - Phone numbers (mask: +966*** ****)
  - Home addresses
  - Salary information
  - Contract terms

Internal (sent to AI with tenant scope):
  - Deal values
  - Pipeline stages
  - Activity summaries
  - Workflow states
  - Aggregate metrics

Public (safe to send to AI):
  - Industry codes
  - Public company info
  - General knowledge
```

## Customer Data Usage Limits

```
1. Tenant data is ONLY used for that tenant's AI requests
   - No cross-tenant data in prompts
   - No tenant data used to train shared models
   - AI provider receives only the requesting tenant's data

2. Data minimization
   - Send only necessary data to AI
   - Redact PII before sending
   - Aggregate where possible (e.g., "5 deals" not "Deal A, B, C, D, E")

3. Purpose limitation
   - AI requests must have explicit purpose
   - No exploratory data mining
   - No profiling without consent

4. Retention limits
   - AI request/response logs: 1 year (redacted)
   - AI-generated content: Tenant-owned, per tenant retention
   - AI provider data: Per provider policy (verified)

5. Consent
   - Tenant admin must enable AI features
   - Users informed when AI is used
   - Opt-out available per feature
```

## Retention Policies

```
CRM Data:
  - Active: Retained while tenant is active
  - On deletion: Hard delete after 90 days (grace period)

User Data:
  - Active: Retained while account active
  - On deletion: Hard delete after 30 days

Audit Log:
  - Retained: 1 year
  - After 1 year: Archived (cold storage) for 2 years
  - After 3 years: Permanently deleted

AI Request/Response Log:
  - Retained: 1 year (redacted)
  - After 1 year: Permanently deleted

AI-Generated Content:
  - Owned by tenant
  - Retained per tenant data retention
  - Deleted when tenant data is deleted
```

## Deletion Policies

```
User deletion:
  - Soft delete: Mark as deleted
  - Grace period: 30 days (can restore)
  - Hard delete: After 30 days, all user data removed
  - Audit log: Retains user_id (anonymized) for 1 year

Tenant deletion:
  - Soft delete: Mark tenant as inactive
  - Grace period: 90 days (can restore)
  - Hard delete: After 90 days, all tenant data removed
  - AI logs: Tenant_id retained (anonymized) for 1 year

AI data deletion:
  - On user request: Delete AI-generated content for that user
  - On tenant request: Delete all AI data for that tenant
  - On AI disable: Stop new AI requests, retain existing logs per policy
```

## Cross-Tenant Data Training Prevention

```
MANDATORY RULE:
  No tenant's data may be used to train models that serve other tenants.

Implementation:
  1. AI provider API calls include "no_training" flag
  2. Data sent to AI is ephemeral (not stored by provider)
  3. Local models (if used) are per-tenant or use no tenant data
  4. Audit log verifies "no_training" flag is set
  5. Periodic compliance check on AI provider terms

Verification:
  - Review AI provider's data usage policy
  - Verify API parameters (no_training=true)
  - Monitor for unauthorized data retention
  - Annual compliance audit
```

## Audit Trail Requirements

```
Every AI data access is logged:
  - What data was accessed
  - Which tenant owns the data
  - Who requested the access (user_id)
  - When (timestamp)
  - Why (purpose/feature)
  - What was sent to AI (redacted summary)
  - What AI returned (redacted summary)
  - Whether training flag was set

Audit trail is:
  - Immutable (append-only)
  - Encrypted at rest
  - Accessible by tenant admin (own tenant) and owner (all)
  - Retained for 1 year
```

## Data Readiness Summary

```
Data sources: IDENTIFIED (5 sources)
Data quality: SUFFICIENT (with preprocessing)
Sensitive data: CLASSIFIED (3 tiers: highly sensitive, sensitive, internal)
Usage limits: DEFINED (tenant-scoped, minimized, purpose-limited)
Retention: DOCUMENTED (per data type)
Deletion: DOCUMENTED (soft + hard delete)
Cross-tenant training: PROHIBITED (enforced)
Audit trail: DESIGNED (immutable, encrypted)

Data Readiness: BASELINED for AI
  → Ready for AI Gateway integration (Stage 17)
  → Data governance policies enforced
  → No cross-tenant data leakage
```
