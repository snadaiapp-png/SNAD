# Stage 26 — Finance, Legal & Governance Readiness

## Purpose

This document defines the finance, legal, and governance readiness required to operate SNAD as a market-facing company. It aligns billing, revenue tracking, commercial approvals, legal review, risk acceptance, and governance records.

## Finance Readiness

```text
- Subscription pricing governance
- Billing activation readiness
- Revenue recognition assumptions
- Invoice and tax readiness
- Payment failure process
- Refund and cancellation policy
- Customer contract to billing handoff
- Monthly revenue reporting baseline
```

## Legal Readiness

```text
- Terms of Service review
- Privacy Policy review
- Data Processing Agreement review
- Master Services Agreement review
- SLA review
- Acceptable Use Policy review
- Partner Agreement review
- Security Addendum review
```

## Governance Readiness

```text
- Decision authority documented
- Risk acceptance process documented
- Security exception process documented
- Production release rule preserved
- Gate 8F closure basis preserved
- No secret republication rule preserved
- Stage closure criteria documented
- Post-production decision log maintained
```

## Approval Matrix

```text
Pricing exception: Project Owner / Commercial Owner
Contract exception: Project Owner with legal review
Security exception: Project Owner with risk record
Production change: PR + CI + Security Baseline + Vercel success
Partner approval: Project Owner / Partner Lead
Customer go-live approval: Project Owner / Implementation Lead
Rollback approval: Project Owner / Engineering Lead
```

## Risk Categories

```text
Commercial risk
Legal risk
Security risk
Compliance risk
Operational risk
Financial risk
Partner risk
Customer delivery risk
```

## Required Registers

```text
- Commercial decision register
- Legal review register
- Finance readiness register
- Billing activation register
- Security exception register
- Risk acceptance register
- Partner approval register
- Customer go-live register
```

## Mandatory Security Rule

```text
No token, secret, credential, private key, webhook secret, payment key, or environment value may be written into repository files, issues, PR bodies, comments, logs, or release notes.
```

## Acceptance Criteria

```text
Finance readiness documented
Legal readiness documented
Governance readiness documented
Approval matrix defined
Risk categories defined
Registers listed
Mandatory security rule preserved
Production remains LIVE
```

## Stage 26 Status

```text
Finance, Legal & Governance Readiness: READY
```
