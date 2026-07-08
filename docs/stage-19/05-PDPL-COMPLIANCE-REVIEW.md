# Stage 19 — PDPL Compliance Review

**Date**: 2026-07-08
**Status**: BASELINED

---

## PDPL Overview

The Saudi Personal Data Protection Law (PDPL) regulates the processing of
personal data in the Kingdom of Saudi Arabia. It was published in September
2021 and became effective in September 2023.

This review is a **preliminary self-assessment** and is NOT a substitute
for formal legal counsel review.

## Personal Data Types in SNAD

```
1. Account Data (user-provided):
   - Full name
   - Email address
   - Phone number (optional)
   - Password (hashed, never plaintext)
   - Display name
   - Profile picture (future)

2. Usage Data (system-generated):
   - Login timestamps
   - Feature usage logs
   - IP addresses (in audit log)
   - User agent strings
   - Session tokens (encrypted)

3. CRM Data (customer-entered):
   - Contact names
   - Contact emails
   - Contact phone numbers
   - Company names
   - Deal information
   - Activity notes

4. Organization Data:
   - Organization name
   - Tenant ID
   - Membership records
   - Role assignments

5. Audit Log Data:
   - API request logs (timestamp, user, action)
   - Authentication events
   - Data access events
   - AI request logs (when AI enabled)

Sensitivity classification:
  Highly sensitive: Passwords (hashed), tokens, credit card numbers (never stored)
  Sensitive: Email, phone, name, CRM contact data
  Internal: Usage logs, audit logs, organization data
```

## Legal Basis for Processing

```
PDPL requires a legal basis for processing personal data:

1. Consent (Article 4)
   - User consents to data collection at signup
   - Consent is freely given, specific, informed
   - User can withdraw consent (account deletion)

2. Contract Performance (Article 4)
   - Data processed to provide the SNAD service
   - Necessary for contract performance (user account, CRM features)

3. Legitimate Interest (Article 4)
   - Audit logging for security
   - Usage analytics for service improvement
   - Fraud prevention

4. Legal Obligation (Article 4)
   - Data retention per regulatory requirements
   - Breach notification per PDPL

SNAD primary basis: Consent + Contract Performance
```

## User Consent

```
Consent mechanism:
  - At signup: User checks "I agree to Terms of Service and Privacy Policy"
  - Privacy policy link provided
  - Consent logged with timestamp and IP
  - User can review consent in account settings

Consent withdrawal:
  - User can delete account (Settings → Delete Account)
  - Data soft-deleted for 30 days (grace period)
  - Hard delete after 30 days
  - Audit log retained (anonymized) for 1 year

Consent for specific features:
  - AI features: Separate consent (tenant admin enables)
  - Marketing emails: Separate opt-in (default: NO)
  - Analytics: Separate opt-in (default: YES, can disable)

Children's data:
  - SNAD is B2B (not intended for children)
  - No age verification needed (enterprise users are adults)
  - If child data entered by customer: customer's responsibility
```

## Data Subject Rights

```
PDPL grants data subjects the following rights:

1. Right to Access (Article 17)
   - User can request all their personal data
   - Implementation: Data export feature (to be built)
   - Response time: 30 days

2. Right to Correction (Article 18)
   - User can correct inaccurate data
   - Implementation: Profile editing (available)
   - Response time: Immediate (self-service)

3. Right to Deletion (Article 19)
   - User can request data deletion
   - Implementation: Account deletion (available with 30-day grace)
   - Response time: 30 days (hard delete after grace period)

4. Right to Data Portability (Article 20)
   - User can receive data in machine-readable format
   - Implementation: Data export feature (to be built)
   - Response time: 30 days

5. Right to Object (Article 21)
   - User can object to processing for marketing
   - Implementation: Unsubscribe link in emails, settings toggle
   - Response time: Immediate

6. Right to Restrict Processing (Article 22)
   - User can request processing restriction
   - Implementation: Account suspension (temporary)
   - Response time: 30 days

DSAR (Data Subject Access Request) process:
  - User emails privacy@snad.app (to be configured)
  - Request logged in Issue tracker
  - Identity verification (email confirmation)
  - Data collected within 30 days
  - Data provided to user (export or summary)
```

## Data Retention

```
Account data:
  - Active: Retained while account is active
  - On deletion: Soft delete for 30 days, then hard delete

CRM data:
  - Active: Retained while tenant is active
  - On tenant cancellation: Hard delete after 90 days

Audit log:
  - Retained: 1 year
  - After 1 year: Archived (cold storage) for 2 years
  - After 3 years: Permanently deleted

AI request/response log:
  - Retained: 1 year (redacted)
  - After 1 year: Permanently deleted

Backup data:
  - Retained: 30 days (rolling)
  - Deleted after 30 days
```

## Data Deletion

```
User deletion:
  1. User requests deletion (Settings → Delete Account)
  2. Soft delete: Account marked inactive
  3. Grace period: 30 days (user can restore)
  4. Hard delete: All user data removed from database
  5. Audit log: user_id anonymized, retained 1 year
  6. Backups: Deleted within 30 days (rolling backup cycle)

Tenant deletion:
  1. Owner/admin requests tenant deletion
  2. Soft delete: Tenant marked inactive
  3. Grace period: 90 days (can restore)
  4. Hard delete: All tenant data removed
  5. Audit log: tenant_id anonymized, retained 1 year
  6. AI logs: Anonymized, retained 1 year
  7. Backups: Deleted within 30 days
```

## Data Transfer

```
Cross-border data transfer:
  Current: Data stored on Vercel (EU/global) and Render (Frankfurt)
  PDPL requirement: Personal data of Saudi subjects should be stored in KSA
    (unless adequate protection is ensured)

Options:
  1. Data localization: Deploy SNAD on Saudi cloud provider (e.g., Oracle Saudi, Google Saudi)
     Status: NOT YET IMPLEMENTED
     Timeline: When first enterprise customer requires it

  2. Adequacy mechanism: Standard Contractual Clauses (SCC) with sub-processors
     Status: TO BE DRAFTED in DPA
     Timeline: Before first enterprise customer

  3. Explicit consent: User consents to cross-border transfer
     Status: To be added to Privacy Policy and signup flow
     Timeline: Before first paying customer

Current gap: Data is stored outside KSA (Vercel EU, Render Frankfurt)
  Risk: MEDIUM (for Saudi enterprise customers)
  Mitigation: SCC + consent (short-term), data localization (long-term)
```

## Third-Party Data Sharing

```
Sub-processors used by SNAD:

1. Vercel (frontend hosting)
   Data: Static assets, serverless functions, environment variables
   Location: Global (EU/US)
   DPA: Required

2. Render (backend hosting)
   Data: Application code, database, environment variables
   Location: Frankfurt, EU
   DPA: Required

3. Stripe (billing — future)
   Data: Payment method, billing address, transaction history
   Location: Global
   DPA: Required (Stripe DPA available)

4. AI providers (OpenAI, Anthropic — future)
   Data: Redacted/sanitized text for AI processing
   Location: Global (US)
   DPA: Required
   Special: "No training" flag enforced

5. Email service (future)
   Data: Email addresses, email content
   Location: Depends on provider
   DPA: Required

Data sharing rules:
  - No tenant data shared without tenant consent
  - Sub-processor list published in Privacy Policy
  - Sub-processor changes: 30-day notice, right to object
  - All sub-processors must have DPA
```

## Processing Record

```
PDPL Article 40 requires maintaining a record of processing activities.

SNAD processing record (to be maintained):

1. Processing activity: User account management
   Purpose: Provide SNAD service
   Data: Name, email, phone, password (hashed)
   Subjects: SNAD users
   Retention: While account active + 30 days

2. Processing activity: CRM data management
   Purpose: Enable CRM features
   Data: Contact names, emails, phones, deals, activities
   Subjects: Customer's contacts (data subjects)
   Retention: While tenant active + 90 days

3. Processing activity: Audit logging
   Purpose: Security, compliance, troubleshooting
   Data: API requests, auth events, user actions
   Subjects: SNAD users
   Retention: 1 year (+ 2 years archived)

4. Processing activity: AI processing (future)
   Purpose: AI-powered features (CRM, ERP, HRM)
   Data: Redacted text, summaries, recommendations
   Subjects: SNAD users (and their data)
   Retention: 1 year (redacted logs)
```

## Privacy Notice Requirements

```
Privacy notice must include (PDPL Article 8):
  1. Data controller identity (SNAD / snadaiapp-png)
  2. Purpose of processing
  3. Legal basis
  4. Data categories
  5. Data recipients (sub-processors)
  6. Retention period
  7. Data subject rights
  8. Right to complain to SDAIA (Saudi Data & AI Authority)
  9. Cross-border transfer information
  10. Contact information (Data Protection Officer or contact)

Status: TO BE DRAFTED (Privacy Policy — see 03-LEGAL-DOCUMENT-PACK.md)
```

## Compliance Gaps

```
Gap 1: Privacy Policy not yet drafted
  Impact: HIGH (required before any paying customer)
  Remediation: Draft Privacy Policy (legal counsel)
  Timeline: Before first paying customer

Gap 2: DSAR (Data Subject Access Request) process not implemented
  Impact: MEDIUM (required for compliance)
  Remediation: Build data export feature + DSAR workflow
  Timeline: Stage 20

Gap 3: Data localization not implemented
  Impact: MEDIUM (for Saudi enterprise customers)
  Remediation: Deploy on Saudi cloud (when enterprise customer requires)
  Timeline: Stage 20-21 (customer-driven)

Gap 4: Cross-border transfer mechanism not formalized
  Impact: MEDIUM (SCC needed for non-Saudi data storage)
  Remediation: Draft SCC, add consent to signup
  Timeline: Before first paying customer

Gap 5: Sub-processor DPA not in place
  Impact: MEDIUM (required for compliance)
  Remediation: Sign DPA with Vercel, Render
  Timeline: Before first enterprise customer

Gap 6: Processing record not formalized
  Impact: LOW (internal document)
  Remediation: Formalize processing record (this document serves as initial draft)
  Timeline: Before first paying customer

Gap 7: Data Protection Officer not appointed
  Impact: LOW (small organization, owner acts as DPO)
  Remediation: Designate owner as DPO, document in Privacy Policy
  Timeline: Before first paying customer
```

## Closure Plan

```
Priority 1 (Before first paying customer):
  - Draft Privacy Policy (legal counsel)
  - Add consent to signup flow
  - Draft SCC for cross-border transfer
  - Formalize processing record
  - Designate DPO (owner)

Priority 2 (Stage 20):
  - Implement DSAR process (data export)
  - Sign sub-processor DPAs (Vercel, Render)
  - Penetration test (verify data security)

Priority 3 (Stage 20-21, customer-driven):
  - Data localization on Saudi cloud (if required by enterprise customer)
  - Annual compliance audit
  - SDAIA registration (if required)
```

## PDPL Compliance Summary

```
Data types: CLASSIFIED (5 categories, 3 sensitivity tiers)
Legal basis: IDENTIFIED (consent + contract + legitimate interest)
Consent: DESIGNED (signup flow, feature-level)
Data subject rights: 6 rights documented (DSAR process to be built)
Retention: DEFINED (per data type)
Deletion: DEFINED (soft + hard delete with grace periods)
Transfer: GAP IDENTIFIED (SCC + consent needed, localization optional)
Third-party sharing: 5 sub-processors identified (DPAs needed)
Processing record: DRAFTED (this document)
Privacy notice: TO BE DRAFTED (legal counsel)
Compliance gaps: 7 identified with remediation plan

PDPL Compliance Review: BASELINED
  → Initial self-assessment complete
  → Legal counsel review required
  → Gaps documented with priority remediation plan
  → Priority 1 items needed before first paying customer
```
