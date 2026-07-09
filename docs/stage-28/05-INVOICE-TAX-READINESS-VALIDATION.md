# Stage 28 — Invoice & Tax Readiness Validation

## Purpose

This document defines the invoice and tax readiness validation model for first paid customer conversion.

This is a readiness and review tracker. It does not assert final tax compliance or legal approval.

## Readiness Status

```text
Invoice readiness: READY FOR REVIEW
Tax readiness: READY FOR SPECIALIST REVIEW
Live invoicing/payment collection: NOT ACTIVATED
Final tax/compliance certification: NOT CLAIMED
```

## Invoice Readiness Checklist

```text
Customer legal name
Customer billing address
Tax/VAT identifier if applicable
Billing contact
Currency
Billing cadence
Subscription plan
Line items
Discounts or credits
Payment terms
Invoice delivery method
Support contact
```

## Tax Readiness Checklist

```text
Customer jurisdiction
Seller jurisdiction
VAT/tax registration status
Tax treatment assumption
Tax invoice fields
Reverse charge applicability if any
Exemption status if any
Specialist review status
Risk acceptance if review pending
```

## Required Review Areas

| Area | Review Needed |
|---|---|
| VAT/tax treatment | Specialist tax review |
| Invoice format | Finance/tax review |
| Payment terms | Commercial review |
| Refund/credit notes | Finance review |
| Multi-country sale | Specialist review |
| Marketplace/partner revenue share | Finance/legal review |

## Prohibited Claims

Do not claim any of the following unless backed by documented specialist review:

```text
Tax compliant
VAT certified
Legally approved
Final invoice compliance
Fully compliant
Certified
```

## Invoice Lifecycle

```text
Draft invoice data
Finance review
Tax review
Customer validation
Billing approval gate
Invoice generation readiness
Invoice issued only after activation approval
```

## First Customer Invoice Evidence Template

```text
Customer:
Country:
Billing contact:
Plan:
Currency:
Billing period:
Tax treatment assumption:
Review status:
Approval status:
Billing activation approval:
```

## Risks

| Risk | Severity | Control |
|---|---|---|
| Incorrect tax treatment | High | Specialist review |
| Missing invoice fields | Medium | Invoice readiness checklist |
| Unauthorized invoice issuance | High | Billing approval gate |
| Tax overclaim | High | Prohibited claims list |
| Partner revenue share confusion | Medium | Finance/legal review |

## Acceptance Criteria

```text
Invoice and Tax Readiness: READY FOR REVIEW
Invoice checklist: READY
Tax checklist: READY
Review areas: DOCUMENTED
Prohibited claims: DOCUMENTED
Evidence template: READY
Live invoicing: NOT ACTIVATED
Production impact: NONE
```
