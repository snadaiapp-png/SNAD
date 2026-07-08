# Stage 18 — Enterprise Compliance Readiness

**Date**: 2026-07-08

---

## Compliance Framework

### PDPL (Saudi Personal Data Protection Law)

```
Status: TO BE REVIEWED by legal counsel
Applicability: All Saudi customers (mandatory)
Effective: September 2023

Requirements:
  1. Consent: Obtain explicit consent for data collection
  2. Purpose limitation: Use data only for stated purpose
  3. Data minimization: Collect only necessary data
  4. Access rights: Users can access their data
  5. Correction rights: Users can correct their data
  6. Deletion rights: Users can request deletion
  7. Data localization: Personal data stored in Saudi Arabia (if required)
  8. Breach notification: Notify within 72 hours of breach
  9. Privacy policy: Publish clear privacy policy
  10. DPO: Appoint Data Protection Officer (if required)

SNAD readiness:
  - Consent: To be implemented (signup flow)
  - Purpose limitation: DOCUMENTED (data used for service only)
  - Data minimization: PARTIAL (review data collection)
  - Access rights: To be implemented (DSAR process)
  - Correction rights: Available (profile editing)
  - Deletion rights: Available (account deletion with grace period)
  - Data localization: DEPENDS (Vercel/Render region)
  - Breach notification: DOCUMENTED (incident runbook)
  - Privacy policy: TO BE DRAFTED
  - DPO: Owner acts as DPO (small organization)

Compliance gap: MEDIUM
  → Privacy policy needed
  → DSAR process needed
  → Data localization review needed
  → Legal counsel review required
```

### GDPR (General Data Protection Regulation)

```
Status: TO BE REVIEWED if targeting EU customers
Applicability: EU customers only (if any)

Requirements:
  1. Lawful basis for processing
  2. Right to access
  3. Right to rectification
  4. Right to erasure (right to be forgotten)
  5. Right to data portability
  6. Right to object
  7. Right to restrict processing
  8. Data protection by design
  9. DPIA (Data Protection Impact Assessment)
  10. SCC (Standard Contractual Clauses) for cross-border

SNAD readiness:
  - Lawful basis: Consent + legitimate interest
  - Access: To be implemented (DSAR)
  - Rectification: Available
  - Erasure: Available (with grace period)
  - Portability: To be implemented (data export)
  - Object: To be implemented (opt-out)
  - Restrict: To be implemented
  - By design: PARTIAL (privacy-conscious architecture)
  - DPIA: To be conducted (if high-risk processing)
  - SCC: To be added to contracts (if cross-border)

Compliance gap: HIGH (if targeting EU)
  → Not ready for EU customers
  → Defer EU expansion until GDPR compliance achieved
  → Focus on Saudi/GCC market first
```

### SOC 2 Type II

```
Status: NOT STARTED
Applicability: Enterprise customers (especially US-based)
Timeline: Year 2 (after 50+ enterprise customers)

Requirements:
  - Security controls (access control, encryption, monitoring)
  - Availability controls (uptime, disaster recovery, incident response)
  - Processing integrity (data accuracy, error handling)
  - Confidentiality (data classification, NDA)
  - Privacy (PDPL/GDPR compliance)

Process:
  1. Engage SOC 2 auditor (CPA firm)
  2. Readiness assessment (3-6 months)
  3. Control implementation (3-6 months)
  4. Observation period (6-12 months)
  5. Audit (1-2 months)
  6. Report issued

Cost: $30,000-$80,000 (auditor fees)
Timeline: 12-18 months total

SNAD readiness:
  - Security: PARTIAL (RBAC, encryption in transit, secret scan)
  - Availability: PARTIAL (uptime monitoring, incident runbook)
  - Processing integrity: PARTIAL (audit log, validation)
  - Confidentiality: PARTIAL (NDA with partners, data classification)
  - Privacy: TO BE IMPLEMENTED (PDPL/GDPR)

Compliance gap: HIGH
  → Not ready for SOC 2 audit
  → Year 2 priority (after revenue justifies cost)
  → Controls being built incrementally (Stages 12-18)
```

### ISO 27001

```
Status: NOT STARTED
Applicability: Large enterprise customers (global)
Timeline: Year 2-3

Requirements:
  - ISMS (Information Security Management System)
  - Risk assessment and treatment
  - Security controls (Annex A, 114 controls)
  - Continuous improvement
  - Internal audits
  - Management reviews

Process:
  1. Gap assessment
  2. ISMS implementation
  3. Internal audit
  4. Certification audit (Stage 1 + Stage 2)
  5. Surveillance audits (annual)
  6. Recertification (3 years)

Cost: $20,000-$50,000 (certification body)
Timeline: 12-24 months

Compliance gap: HIGH
  → Not ready for ISO 27001
  → Year 2-3 priority
  → Will benefit from SOC 2 preparation
```

## Security Questionnaire Readiness

```
Enterprise customers require security questionnaires.

Standard questions:
  1. Data encryption (at rest, in transit)
  2. Access controls (RBAC, MFA)
  3. Incident response
  4. Vulnerability management
  5. Penetration testing
  6. Data residency
  7. Sub-processor list
  8. Compliance certifications
  9. Employee training
  10. Physical security (cloud provider)

SNAD readiness:
  - Encryption: In transit (TLS), at rest (depends on provider)
  - Access controls: RBAC implemented, MFA to be added
  - Incident response: DOCUMENTED (Stage 12 runbook)
  - Vulnerability management: Secret scan + dependency check (CI)
  - Penetration testing: NOT YET CONDUCTED
  - Data residency: DEPENDS (Vercel/Render region)
  - Sub-processors: To be documented
  - Compliance: TO BE ACHIEVED (PDPL first, then SOC 2)
  - Employee training: N/A (single owner)
  - Physical security: Cloud provider (Vercel, Render)

Questionnaire template: TO BE PREPARED
```

## DPA (Data Processing Agreement) Readiness

```
Status: TO BE DRAFTED

Required for: All enterprise customers (and EU customers if applicable)

Contents:
  1. Data processing scope
  2. Sub-processor list
  3. Data security measures
  4. Data breach notification
  5. Data return/deletion on termination
  6. Audit rights
  7. International data transfers (if applicable)

SNAD readiness:
  - Scope: DEFINED (CRM, user data, audit log)
  - Sub-processors: TO BE DOCUMENTED (Vercel, Render, Stripe, AI providers)
  - Security: DOCUMENTED (encryption, RBAC, monitoring)
  - Breach: DOCUMENTED (72-hour notification)
  - Return/deletion: DOCUMENTED (90-day retention then hard delete)
  - Audit: TO BE DEFINED (annual, customer right)
  - Transfers: DEPENDS (if non-Saudi data residency)

DPA template: TO BE DRAFTED (with legal counsel)
```

## Privacy Policy Readiness

```
Status: TO BE DRAFTED

Required: Before accepting any customer (pilot or paid)

Contents:
  1. Data collected (account, usage, telemetry)
  2. Purpose of collection
  3. Legal basis (consent, contract, legitimate interest)
  4. Data sharing (sub-processors)
  5. Data retention
  6. User rights (access, correction, deletion, portability)
  7. Cookies and local storage
  8. International transfers
  9. Children's privacy
  10. Contact information
  11. Updates to policy

Languages: Arabic (primary), English (secondary)

Privacy policy: TO BE DRAFTED (with legal counsel)
```

## Audit Evidence Readiness

```
Enterprise customers may request audit evidence.

Evidence available:
  - CI/CD logs (GitHub Actions)
  - Deployment history (Vercel + GitHub Deployments API)
  - Secret scan reports (CI)
  - Security baseline reports (CI)
  - Post-Merge Verification reports
  - Audit log (application-level)
  - Incident reports (Issue #367, #373 — closed)

Evidence gaps:
  - Penetration test report (not conducted)
  - SOC 2 report (not started)
  - ISO 27001 certificate (not started)
  - Vulnerability scan reports (CI-based only)
  - Access review logs (not formalized)

Audit evidence readiness: PARTIAL
  → CI evidence available
  → Formal reports needed (pen test, SOC 2)
  → Year 1: Rely on CI evidence + documentation
  → Year 2: Formal reports (pen test, SOC 2)
```

## Enterprise Compliance Summary

```
PDPL: TO BE REVIEWED (legal counsel needed) ⚠️
GDPR: NOT READY (defer EU expansion) ⚠️
SOC 2: NOT STARTED (Year 2) ⚠️
ISO 27001: NOT STARTED (Year 2-3) ⚠️
Security questionnaire: TO BE PREPARED ⚠️
DPA: TO BE DRAFTED ⚠️
Privacy policy: TO BE DRAFTED ⚠️
Audit evidence: PARTIAL ⚠️

Enterprise Compliance Readiness: NOT YET READY
  → Legal counsel engagement required
  → Privacy policy and DPA needed before paid customers
  → PDPL compliance needed before Saudi enterprise customers
  → SOC 2 and ISO 27001 for large enterprise (Year 2+)

Priority:
  1. Privacy policy (before any paid customer) — IMMEDIATE
  2. DPA template (before enterprise customer) — Stage 19
  3. PDPL review (before Saudi enterprise) — Stage 19
  4. Penetration test (before enterprise sales) — Stage 19
  5. SOC 2 (Year 2, after 50+ enterprise customers)
  6. ISO 27001 (Year 2-3, if required by large enterprise)
```
