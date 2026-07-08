# Stage 13 — Support & Helpdesk Readiness

**Date**: 2026-07-08

---

## Support Channels

### 1. In-App Help

```
Location: Login screen → "Need help signing in?" link
Content: Contact instructions for account recovery
Route: /auth/forgot-password (password reset)
```

### 2. GitHub Issues (Current)

```
Repository: github.com/snadaiapp-png/SNAD
Usage: Bug reports, feature requests, security notices
Response time: Best-effort (owner-managed)
Labels: critical, high, medium, low, security, enhancement
```

### 3. Email Support (Future)

```
Email: support@snad.app (to be configured)
Tier 1 (Free): 48h response time
Tier 2 (Professional): 24h response time
Tier 3 (Enterprise): 4h response time
```

### 4. Helpdesk System (Future)

```
Recommended: Zendesk, Intercom, or Crisp
Integration: In-app chat widget
Features:
  - Ticket creation
  - Knowledge base
  - Live chat (business hours)
  - Email-to-ticket
```

## Ticket Classification

```
Severity mapping:
  Critical → Production down, data loss, security breach
  High → Feature broken, significant UX impact
  Medium → Minor feature issue, workaround available
  Low → Cosmetic, enhancement request

Response times by tier:
  Free: Critical 8h, High 24h, Medium 48h, Low 1 week
  Professional: Critical 2h, High 8h, Medium 24h, Low 3 days
  Enterprise: Critical 1h, High 4h, Medium 8h, Low 2 days
```

## Escalation Matrix

```
Level 1: Support agent (initial triage)
Level 2: Senior support (complex issues)
Level 3: Engineering (bugs, infrastructure)
Level 4: Owner (security, production decisions)

Escalation triggers:
  Critical → Immediate Level 3+4 notification
  High → Level 3 within 4h if unresolved
  Medium → Level 2 within 24h if unresolved
  Low → Stays at Level 1
```

## Knowledge Base (Initial)

```
Articles to create:
  1. Getting started with SNAD
  2. Switching language (Arabic/English)
  3. Switching theme (Light/Dark/System)
  4. Resetting your password
  5. Inviting team members
  6. Assigning roles
  7. Using the CRM module
  8. Using the Control Plane
  9. Understanding tenant isolation
  10. Troubleshooting login issues
```

## Response Templates

```
Template 1 — Login issue:
  "Thank you for reporting the login issue. Please try:
   1. Clear your browser cache
   2. Use the forgot password link: /auth/forgot-password
   3. If the issue persists, provide your email and browser version."

Template 2 — Feature request:
  "Thank you for the feature request. We've logged this as an enhancement.
   We'll evaluate it for a future release. You can track progress at [GitHub Issue link]."

Template 3 — Bug report:
  "Thank you for the bug report. We've reproduced the issue and are investigating.
   We'll provide an update within [response time] based on your support tier."
```

## Support Readiness

```
In-app help: READY ✅
GitHub Issues: ACTIVE ✅
Email support: NOT YET CONFIGURED ⚠️
Helpdesk system: NOT YET IMPLEMENTED ⚠️
Knowledge base: PLANNED ⚠️
Response templates: DOCUMENTED ✅
Ticket classification: DOCUMENTED ✅
Escalation matrix: DOCUMENTED ✅

Support Readiness: PARTIAL (GitHub-based support active, email/helpdesk pending)
```

## Recommendation

Start with GitHub Issues for pilot customers. Add email support when
first paid customer signs up. Implement helpdesk system (Intercom or
Crisp) in Stage 14 when customer base grows beyond 10 tenants.
