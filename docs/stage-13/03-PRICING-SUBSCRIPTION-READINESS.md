# Stage 13 — Pricing & Subscription Readiness

**Date**: 2026-07-08

---

## Pricing Model (Proposed)

### Tier 1: Free Pilot

```
Price: $0/month
Tenants: 1
Users: Up to 5
Features:
  - Bilingual UI (Arabic/English)
  - Basic workspace
  - CRM (basic)
  - Community support
Limitations:
  - No AI features
  - No advanced analytics
  - No API access
  - 1GB storage
```

### Tier 2: Professional

```
Price: $49/month per tenant
Tenants: 1
Users: Up to 25
Features:
  - All Free Pilot features
  - Control Plane access
  - Advanced CRM
  - Email support (24h response)
  - 10GB storage
  - API access (rate-limited)
Limitations:
  - No custom AI workflows
  - No white-label
```

### Tier 3: Enterprise

```
Price: $199/month per tenant (or custom)
Tenants: Multiple (negotiable)
Users: Unlimited
Features:
  - All Professional features
  - Custom AI workflows
  - White-label option
  - Priority support (4h response)
  - 100GB+ storage
  - Unlimited API access
  - SSO/SAML
  - Custom SLA
```

## Subscription Process

```
1. Customer signs up (Free Pilot)
2. Customer upgrades via Control Plane → Billing
3. Payment processed (Stripe integration - future)
4. Tenant subscription updated
5. Features unlocked based on tier
6. Monthly billing cycle
```

## Free Trial Policy

```
Duration: 14 days
Tier: Professional (all features)
No credit card required
Auto-downgrade to Free Pilot after trial
Notification: 3 days before trial ends
```

## Upgrade/Downgrade Policy

```
Upgrade: Immediate, prorated charge
Downgrade: Effective at next billing cycle
Cancel: Effective at end of billing cycle, no refund for partial month
```

## Billing Integration (Future)

```
Payment processor: Stripe (recommended)
Webhook: /api/billing/webhook (Stripe events)
Subscription sync: Stripe → tenant subscription status
Invoice generation: Automated via Stripe
Tax handling: Stripe Tax (if available in region)
```

## Pricing Readiness

```
Pricing model: DOCUMENTED ✅
Subscription tiers: DEFINED ✅
Free trial policy: DOCUMENTED ✅
Upgrade/downgrade policy: DOCUMENTED ✅
Billing integration: NOT YET IMPLEMENTED ⚠️ (requires Stripe setup)
Payment processing: NOT YET AVAILABLE ⚠️

Pricing Readiness: DOCUMENTED (implementation deferred to Stage 14+)
```

## Recommendation

Start with Free Pilot tier only for initial customers. Add paid tiers
after validating product-market fit with pilot customers (Stage 13 Pilot
Program). Implement Stripe billing in Stage 14 when first paid customer
is ready.
