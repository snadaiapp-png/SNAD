# Stage 15 — Compliance Monitoring

**Date**: 2026-07-08

---

## Compliance Framework

### Saudi PDPL (Personal Data Protection Law)

```
Status: TO BE REVIEWED by legal counsel
Applicability: All Saudi customers
Requirements:
  - Consent for data collection
  - Right to access and correct personal data
  - Right to request deletion
  - Data localization (if required)
  - Breach notification within 72 hours
  - Privacy policy published
  - Data processing agreement with sub-processors

Monitoring:
  - Annual compliance review
  - Data subject access request (DSAR) process
  - Breach response plan
  - Data retention audit
```

### GDPR (General Data Protection Regulation)

```
Status: TO BE REVIEWED if targeting EU customers
Applicability: EU customers only
Requirements:
  - Lawful basis for processing
  - Right to access, rectify, erase
  - Data portability
  - Right to object
  - DPO appointment (if required)
  - Data Protection Impact Assessment (DPIA)
  - Standard Contractual Clauses (SCC) for cross-border

Monitoring:
  - Annual compliance review
  - DSAR process
  - DPIA for high-risk processing
  - Sub-processor audit
```

### SOC 2 Type II

```
Status: NOT YET STARTED
Applicability: Enterprise customers
Timeline: Year 2 (after 50+ enterprise customers)
Requirements:
  - Security controls
  - Availability controls
  - Processing integrity
  - Confidentiality
  - Privacy

Monitoring:
  - Continuous control monitoring
  - Annual audit
  - Remediation tracking
```

### ISO 27001

```
Status: NOT YET STARTED
Applicability: Large enterprise customers
Timeline: Year 2-3
Requirements:
  - Information Security Management System (ISMS)
  - Risk assessment
  - Security controls
  - Continuous improvement

Monitoring:
  - Internal audits
  - Management reviews
  - Corrective actions
```

## Compliance Monitoring Process

### Daily

```
- Secret scan (CI automated on every PR)
- Security baseline (CI automated on every PR)
- Production health check (manual or automated via uptime monitor)
```

### Weekly

```
- Review open security Issues
- Review access logs (if available)
- Review collaborator list
- Check for new CVEs in dependencies
```

### Monthly

```
- Compliance metric review
- Data retention audit
- Access review (who has access to what)
- Dependency vulnerability scan review
```

### Quarterly

```
- Full compliance review (PDPL, GDPR if applicable)
- Risk assessment update
- Policy review and update
- Training review (if team exists)
```

### Annually

```
- SOC 2 audit (when applicable)
- ISO 27001 audit (when applicable)
- Privacy policy review
- Terms of Service review
- SLA review
```

## Compliance Metrics

```
1. Security findings: 0 open Critical, 0 unaccepted High
2. Data subject requests: Response within 30 days (PDPL/GDPR)
3. Breach incidents: 0
4. Audit findings: 0 open Critical
5. Training completion: 100% (when team exists)
6. Policy acknowledgments: 100% (when team exists)
```

## Compliance Monitoring Readiness

```
PDPL: TO BE REVIEWED ⚠️
GDPR: TO BE REVIEWED (if EU) ⚠️
SOC 2: NOT STARTED ⚠️
ISO 27001: NOT STARTED ⚠️
Daily monitoring: ACTIVE (CI secret scan + security baseline) ✅
Weekly review: DOCUMENTED ✅
Monthly audit: DOCUMENTED ✅
Quarterly review: DOCUMENTED ✅
Annual audit: PLANNED ✅

Compliance Monitoring: ACTIVE (automated CI checks)
  → Legal compliance review needed before enterprise customers
  → SOC 2 / ISO 27001 for large enterprise (Year 2+)
```
