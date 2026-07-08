# Stage 19 — Stripe Billing Integration Readiness

**Date**: 2026-07-08
**Status**: DOCUMENTED

---

## Subscription Model

```
SNAD uses a SaaS subscription model with per-tenant billing:

- Free Pilot: $0/month (up to 5 users, limited features)
- Professional: $49/month per tenant (up to 25 users, standard features)
- Enterprise: $199+/month per tenant (unlimited users, full features + custom)

Billing cycle: Monthly (auto-renew) or Annual (2 months free)
```

## Tiers

```
Free Pilot:
  Users: Up to 5
  Tenants: 1
  Storage: 1GB
  AI: No access
  Support: Community (GitHub Issues)
  Price: $0

Professional:
  Users: Up to 25
  Tenants: 1
  Storage: 10GB
  AI: Limited (1,000 requests/month)
  Support: Email (24h response)
  API: Rate-limited
  Price: $49/month or $490/year (2 months free)

Enterprise:
  Users: Unlimited
  Tenants: Multiple (negotiable)
  Storage: 100GB+
  AI: Full (10,000+ requests/month, custom)
  Support: Priority (4h response, phone)
  API: Unlimited
  SSO: SAML 2.0 (Stage 20)
  Custom SLA: Available
  White-label: Available
  Price: $199+/month or custom
```

## Billing Periods

```
Monthly:
  - Billed on the 1st of each month
  - Auto-renew unless cancelled
  - Prorated for mid-cycle changes

Annual:
  - Billed on signup date each year
  - 2 months free (pay for 10, get 12)
  - Auto-renew unless cancelled
  - Prorated for mid-cycle changes
```

## Free Trial

```
Duration: 14 days
Tier: Professional (all features)
Credit card: NOT required
Auto-downgrade: To Free Pilot after trial ends
Notifications:
  - Day 1: Welcome + onboarding
  - Day 7: Mid-trial check-in
  - Day 11: Upgrade reminder (3 days left)
  - Day 13: Final reminder (1 day left)
  - Day 14: Trial ends → Free Pilot
Conversion target: 30%
```

## Subscription Lifecycle

```
1. Trial (14 days, no payment)
2. Trial expired → Free Pilot (no payment)
3. Upgrade to Professional (payment required)
4. Active (paid, monthly or annual)
5. Past Due (payment failed, 7-day grace)
6. Suspended (grace expired, features disabled)
7. Cancelled (customer or SNAD initiated)
8. Expired (90 days post-cancellation, data deleted)

State transitions:
  Trial → Free Pilot (automatic on trial end)
  Free Pilot → Professional (customer upgrades, payment)
  Professional → Enterprise (customer upgrades, payment)
  Active → Past Due (payment failed)
  Past Due → Active (payment recovered)
  Past Due → Suspended (grace expired)
  Suspended → Active (payment recovered)
  Active → Cancelled (customer cancels)
  Cancelled → Expired (90 days post-cancellation)
```

## Upgrade/Downgrade

```
Upgrade (e.g., Professional → Enterprise):
  - Immediate effect
  - Prorated charge for remaining period
  - New features unlocked immediately
  - No data migration needed (same tenant)

Downgrade (e.g., Enterprise → Professional):
  - Effective at next billing cycle
  - Current cycle remains at higher tier
  - Features restricted at next cycle start
  - Data not deleted (but may exceed new limits — customer notified)

No-tier downgrade (e.g., Professional → Free Pilot):
  - Effective at next billing cycle
  - Features restricted
  - Data retained (within Free tier limits)
  - Excess data: customer warned, may be archived
```

## Cancellation

```
Customer-initiated:
  - Customer cancels in Settings or via support
  - Effective: End of current billing cycle
  - No refund for partial period (monthly) or partial year (annual)
  - Data retained 90 days for reactivation
  - After 90 days: Data hard deleted

SNAD-initiated (rare):
  - Violation of ToS/AUP
  - Non-payment (after grace + suspension)
  - Security risk
  - Effective: Immediate
  - Data retained 90 days then deleted
  - Customer notified with reason
```

## Invoices

```
Generation:
  - Automated via Stripe
  - Sent on billing date (monthly or annual)
  - PDF attached to email
  - Online payment link included
  - Accessible in customer dashboard

Content:
  - SNAD branding (bilingual)
  - Customer info (name, email, company)
  - Tenant ID
  - Billing period
  - Line items (tier, add-ons, prorations)
  - Subtotal, tax, total
  - Payment method
  - Payment status
  - Due date

Languages: Arabic (primary), English (secondary)
```

## Taxes

```
VAT (Value Added Tax):
  - Saudi Arabia: 15% VAT on all subscriptions
  - GCC countries: Per local VAT rate (5-15%)
  - Non-GCC: Per local tax laws

Implementation:
  - Stripe Tax (if available in region)
  - Manual tax calculation (if Stripe Tax not available)
  - Tax ID collected from enterprise customers
  - Tax-exempt status: Supported (with documentation)

Tax invoices:
  - Compliant with Saudi ZATCA requirements (e-compliance)
  - Arabic + English
  - Tax ID shown
  - Sequential invoice numbering
```

## Required Webhooks

```
Stripe webhooks (to be implemented):

1. checkout.session.completed
   Action: Activate subscription, create tenant if new

2. customer.subscription.created
   Action: Log subscription creation

3. customer.subscription.updated
   Action: Update tier, prorate, unlock/restrict features

4. customer.subscription.deleted
   Action: Mark subscription cancelled, start 90-day retention

5. invoice.payment_succeeded
   Action: Mark invoice paid, extend subscription

6. invoice.payment_failed
   Action: Mark past due, send dunning email

7. invoice.finalized
   Action: Send invoice to customer

8. customer.subscription.trial_will_end
   Action: Send trial ending reminder (3 days before)

9. charge.failed
   Action: Log payment failure, trigger dunning

10. charge.dispute.created
    Action: Alert owner, freeze disputed amount

Webhook endpoint: POST /api/billing/webhook
Security: Stripe webhook signature verification
Idempotency: Event ID tracking (prevent duplicate processing)
```

## Tenant-Subscription Binding

```
Each tenant has one subscription:
  - Tenant created → Free Pilot (no payment)
  - Owner upgrades → Stripe checkout → subscription created
  - Stripe customer ID linked to tenant
  - Subscription ID linked to tenant
  - Tier stored in tenant record

Data model:
  tenants.stripe_customer_id (nullable, set on first payment)
  tenants.stripe_subscription_id (nullable, set on subscription)
  tenants.subscription_tier (enum: FREE, PROFESSIONAL, ENTERPRISE)
  tenants.subscription_status (enum: TRIAL, ACTIVE, PAST_DUE, SUSPENDED, CANCELLED)
  tenants.billing_cycle (enum: MONTHLY, ANNUAL)
  tenants.current_period_end (timestamp)

Access control:
  - Tenant admin can view subscription
  - Tenant admin can upgrade/cancel
  - System checks tier on feature access
  - System checks status on login (suspended → show message)
```

## Payment Failure Handling

```
Dunning sequence (automated):

Day 0: Payment fails
  - Stripe retries (smart retries, up to 4 attempts in 3 days)
  - Email 1: "Payment failed — please update your payment method"

Day 3: Retry fails
  - Email 2: "Action required — subscription at risk"

Day 7: Final retry fails
  - Email 3: "Final notice — subscription will be suspended"
  - Subscription marked: PAST_DUE
  - Features continue (good faith, 7-day grace)

Day 10: Grace expires
  - Subscription marked: SUSPENDED
  - Features disabled (read-only mode)
  - Email 4: "Subscription suspended — update payment to restore"

Day 90: Suspension expires
  - Subscription marked: CANCELLED
  - Data retention: 90 days (can reactivate with payment)
  - After 90 days: Data hard deleted
```

## Security Requirements

```
1. Stripe API keys:
  - Secret key: Server-side only (never exposed to client)
  - Publishable key: Client-side (safe to expose)
  - Webhook secret: Server-side only

2. PCI compliance:
  - SNAD never handles credit card data directly
  - Stripe handles all payment processing
  - SNAD uses Stripe Elements (iframe-based, PCI-compliant)
  - No credit card numbers stored in SNAD database

3. Webhook security:
  - Stripe signature verification (prevent spoofing)
  - HTTPS only
  - Idempotency (prevent duplicate processing)
  - Event logging (audit trail)

4. Customer data:
  - Stripe customer ID stored in tenant record
  - No payment methods stored in SNAD
  - No billing addresses stored in SNAD (Stripe manages)
  - Invoice history: Retrieved from Stripe API (not stored)
```

## Test Environment

```
Stripe provides test mode for development:

Test cards:
  - Successful: 4242 4242 4242 4242
  - Declined: 4000 0000 0000 0002
  - 3D Secure: 4000 0027 6000 3184

Test environment:
  - Stripe test mode (separate API keys)
  - No real charges
  - Webhook testing via Stripe CLI
  - Full feature parity with production

Test plan:
  1. Create test customer → subscribe to Professional
  2. Test upgrade to Enterprise (proration)
  3. Test downgrade to Free Pilot
  4. Test payment failure (dunning sequence)
  5. Test cancellation
  6. Test webhook handling
  7. Test invoice generation
  8. Test tax calculation

Production activation:
  - Switch to live API keys
  - Requires owner approval (business + security decision)
  - NOT activated until explicit decision (per executive order)
```

## Stripe Billing Readiness Summary

```
Subscription model: DEFINED (3 tiers, monthly/annual)
Free trial: DEFINED (14 days, no credit card)
Lifecycle: DEFINED (8 states)
Upgrade/downgrade: DEFINED (proration, effective dates)
Cancellation: DEFINED (90-day retention)
Invoices: DEFINED (Stripe automated, bilingual)
Taxes: DEFINED (VAT 15% Saudi, Stripe Tax)
Webhooks: 10 webhooks defined
Tenant binding: DESIGNED (per-tenant subscription)
Payment failure: DEFINED (dunning sequence, 10-day grace)
Security: DEFINED (no PCI scope, Stripe handles)
Test environment: AVAILABLE (Stripe test mode)

Production activation: NOT YET (requires owner decision)
  → No real payments until explicitly authorized
  → Test environment ready for development

Stripe Billing Readiness: DOCUMENTED
  → Architecture defined
  → Implementation planned for Stage 20
  → Production activation requires separate business + security decision
```
