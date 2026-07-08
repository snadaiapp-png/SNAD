# Stage 19 — Legal Document Pack

**Date**: 2026-07-08
**Status**: DRAFTED FOR REVIEW

---

## Important Notice

These documents are **operational drafts** and are NOT a substitute for
specialized legal review. A qualified legal counsel admitted in Saudi Arabia
must review and approve all legal documents before use with paying customers.

---

## Document Index

### 1. Terms of Service (ToS)

```
Status: DRAFT OUTLINE
Required: Before accepting any paying customer

Key sections:
  - Service description (SNAD Business Operating System)
  - Acceptable use policy (reference)
  - User responsibilities (accurate information, compliance)
  - Limitation of liability (service provided "as-is")
  - Intellectual property (SNAD owns platform, customer owns data)
  - Termination (by either party, data retention 90 days)
  - Governing law (Kingdom of Saudi Arabia)
  - Dispute resolution (arbitration in Riyadh)
  - Modifications (30-day notice for changes)

Languages: Arabic (primary), English (secondary)
```

### 2. Privacy Policy

```
Status: DRAFT OUTLINE
Required: Before accepting any customer (pilot or paid)

Key sections:
  - Data controller: SNAD (snadaiapp-png)
  - Data collected: account info, usage data, CRM data, audit logs
  - Purpose: service provision, improvement, security
  - Legal basis: consent, contract performance, legitimate interest
  - Data sharing: sub-processors (Vercel, Render, Stripe, AI providers)
  - Data retention: active while account active, 90-day post-termination
  - User rights: access, correction, deletion, portability, objection
  - Cookies: minimal (session only), localStorage for preferences
  - International transfers: data stored in EU (Vercel) and/or Saudi (if configured)
  - Breach notification: within 72 hours per PDPL
  - Contact: privacy@snad.app (to be configured)
  - Updates: 30-day notice for material changes

Languages: Arabic (primary), English (secondary)
```

### 3. Data Processing Agreement (DPA)

```
Status: DRAFT OUTLINE
Required: Before enterprise customer contract

Key sections:
  - Data processing scope: CRM, user, audit log data
  - Sub-processors: Vercel (hosting), Render (backend), Stripe (billing), AI providers
  - Data security measures: encryption in transit, RBAC, tenant isolation, audit log
  - Data breach notification: 72 hours to customer
  - Data return/deletion: within 90 days of termination
  - Audit rights: annual, with 30-day notice
  - International transfers: SCC (Standard Contractual Clauses) if applicable
  - Sub-processor changes: 30-day notice, customer right to object

Languages: Arabic (primary), English (secondary)
```

### 4. Master Services Agreement (MSA)

```
Status: DRAFT OUTLINE
Required: Before enterprise customer contract

Key sections:
  - Parties: SNAD (provider) and Customer
  - Services: SNAD platform access per selected tier
  - Term: annual (auto-renew unless cancelled)
  - Fees: per tier, invoiced monthly or annually
  - Payment: net 30 days, late fee 1.5%/month
  - Service levels: 99.9% (professional), 99.95% (enterprise)
  - Service credits: 10% (pro), 20% (enterprise) per hour below SLA
  - Confidentiality: mutual NDA
  - Warranties: service per documentation, no malware
  - Indemnification: mutual (IP infringement, data breach)
  - Limitation of liability: cap at 12 months fees
  - Termination: material breach (30-day cure), convenience (90-day notice)
  - Transition: data export assistance, 90-day data retention

Languages: Arabic (primary), English (secondary)
```

### 5. Acceptable Use Policy (AUP)

```
Status: DRAFT OUTLINE
Required: Part of ToS

Key sections:
  - Permitted use: business operations only
  - Prohibited use: illegal, abusive, infringing, malicious
  - No reverse engineering, scraping, or automated access (without API)
  - No sharing credentials (except authorized users)
  - No uploading malware or malicious content
  - No attempts to access other tenants' data
  - No circumventing security controls
  - Violation consequences: warning, suspension, termination

Languages: Arabic (primary), English (secondary)
```

### 6. Service Level Agreement (SLA)

```
Status: DRAFT OUTLINE
Required: Part of MSA (enterprise)

Key sections:
  - Availability target: 99.9% (pro), 99.95% (enterprise)
  - Measurement: synthetic monitoring, 5-minute intervals
  - Exclusions: scheduled maintenance (with notice), force majeure
  - Service credits: calculated per hour below target
  - Incident notification: within 1 hour of detection (enterprise)
  - Incident resolution: best effort, target 4 hours (critical, enterprise)
  - Scheduled maintenance: 14-day notice (enterprise), 7-day (pro)

Languages: Arabic (primary), English (secondary)
```

### 7. Security Addendum

```
Status: DRAFT OUTLINE
Required: Part of MSA (enterprise)

Key sections:
  - Encryption: TLS 1.2+ in transit, encryption at rest (provider-dependent)
  - Access control: RBAC, MFA (future), audit logging
  - Data isolation: logical (tenant_id), no cross-tenant access
  - Vulnerability management: CI secret scan, dependency check, annual pen test
  - Incident response: documented runbooks, 72-hour notification
  - Employee access: least privilege, audited, no access to tenant data without consent
  - Sub-processor security: reviewed annually, DPA required
  - Data retention: per policy, 90-day post-termination
  - Security certifications: SOC 2 (Year 2 roadmap), ISO 27001 (Year 2-3)

Languages: Arabic (primary), English (secondary)
```

### 8. Partner Agreement (Outline)

```
Status: DRAFT OUTLINE
Required: Before signing first partner

Key sections:
  - Partner type: implementation, integration, consulting, sales, marketplace
  - Partner tier: Registered, Certified, Premier
  - Term: annual (renewable)
  - Commission: 5-15% (type-dependent)
  - Partner obligations: certification, quality, customer satisfaction
  - SNAD obligations: tools, support, lead sharing
  - Confidentiality: mutual NDA
  - Termination: 30-day notice, transition plan
  - Intellectual property: partner owns extensions, SNAD owns platform
  - Liability: partner liable for their services, SNAD liable for platform

Languages: Arabic (primary), English (secondary)
```

## Legal Pack Summary

```
Documents: 8
  1. Terms of Service — DRAFT OUTLINE
  2. Privacy Policy — DRAFT OUTLINE
  3. Data Processing Agreement — DRAFT OUTLINE
  4. Master Services Agreement — DRAFT OUTLINE
  5. Acceptable Use Policy — DRAFT OUTLINE
  6. Service Level Agreement — DRAFT OUTLINE
  7. Security Addendum — DRAFT OUTLINE
  8. Partner Agreement — DRAFT OUTLINE

Status: DRAFTED FOR REVIEW
  → All outlines created with key sections defined
  → Legal counsel review required before use with paying customers
  → Arabic translations required (primary language)
  → PDPL compliance review needed (see 05-PDPL-COMPLIANCE-REVIEW.md)

Important: These are operational drafts, NOT legal advice.
  A qualified Saudi Arabian legal counsel must review and approve.
```
