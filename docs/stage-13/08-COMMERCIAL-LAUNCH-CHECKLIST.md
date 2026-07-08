# Stage 13 — Commercial Launch Checklist

**Date**: 2026-07-08

---

## Pre-Launch Checklist

### Product Readiness

```
✅ Production LIVE at https://snad-app.vercel.app/
✅ Bilingual UI (Arabic/English, 168 translation keys)
✅ RTL/LTR switching
✅ Theme switching (light/dark/system)
✅ Login and authentication
✅ Tenant provisioning and selection
✅ Workspace dashboard
✅ Control Plane (admin)
✅ CRM module
✅ All 6 routes return HTTP 200
✅ CI/CD pipeline (Post-Merge Verification, Production Smoke, Playwright)
✅ Secret scan PASS
✅ Security baseline PASS
```

### Legal & Compliance

```
⚠️ Terms of Service: TO BE DRAFTED
⚠️ Privacy Policy: TO BE DRAFTED
⚠️ Pilot Agreement: TO BE DRAFTED
⚠️ PDPL compliance review: PENDING
✅ Data isolation: VERIFIED
✅ RBAC enforcement: VERIFIED
```

### Commercial

```
✅ Pricing model: DOCUMENTED (Free, Professional, Enterprise)
⚠️ Billing integration (Stripe): NOT YET IMPLEMENTED
⚠️ Payment processing: NOT YET AVAILABLE
✅ Free trial policy: DOCUMENTED
✅ Upgrade/downgrade policy: DOCUMENTED
```

### Support

```
✅ GitHub Issues: ACTIVE
⚠️ Email support: NOT YET CONFIGURED
⚠️ Helpdesk system: NOT YET IMPLEMENTED
✅ Knowledge base: PLANNED
✅ Response templates: DOCUMENTED
✅ Escalation matrix: DOCUMENTED
```

### Operations

```
✅ Production runbook: READY
✅ Incident runbook: READY
✅ Rollback runbook: READY
✅ Secret exposure runbook: READY
✅ Deployment verification runbook: READY
✅ Monitoring: BASELINED (CI active, uptime monitoring recommended)
⚠️ Backup & restore: PARTIAL (code ready, database pending)
```

### Monitoring

```
✅ GitHub Actions CI: ACTIVE
✅ Vercel deployment: ACTIVE
✅ Secret scan: ACTIVE
⚠️ Uptime monitoring: RECOMMENDED (UptimeRobot)
⚠️ Error tracking: RECOMMENDED (Sentry)
⚠️ RUM/Analytics: RECOMMENDED (Vercel Analytics)
```

## Launch Phases

### Phase 1: Pilot Launch (Current)

```
Status: READY TO START
Actions:
  1. Draft Pilot Agreement (legal)
  2. Recruit 3 pilot customers
  3. Onboard pilots (create tenants, train users)
  4. Run 90-day pilot program
  5. Gather feedback and iterate
  6. Produce case studies
```

### Phase 2: Soft Launch

```
Status: AFTER PILOT (Stage 14)
Actions:
  1. Implement billing integration (Stripe)
  2. Launch email support
  3. Publish Terms of Service and Privacy Policy
  4. Open self-service signup
  5. Accept first paid customers (Professional tier)
  6. Implement helpdesk system
```

### Phase 3: Full Commercial Launch

```
Status: AFTER SOFT LAUNCH (Stage 15+)
Actions:
  1. Marketing campaign
  2. Enterprise sales outreach
  3. Partner program
  4. Marketplace integration
  5. Public API launch
```

## Launch Decision

```
Commercial Launch Readiness: NOT YET READY for full launch
  → Legal documents required (Terms of Service, Privacy Policy)
  → Billing integration required for paid tiers

Pilot Launch Readiness: READY (after Pilot Agreement drafted)
  → Production is LIVE and stable
  → Bilingual UI verified
  → Support process documented
  → Feedback loop established

Recommendation: Proceed with Phase 1 (Pilot Launch) immediately
after legal documents are drafted. Defer Phase 2 (Soft Launch) to
Stage 14 after pilot feedback is incorporated.
```

## Commercial Launch Checklist Summary

```
Product: READY ✅
Legal: NOT READY ⚠️ (draft required)
Commercial: PARTIAL ⚠️ (billing pending)
Support: PARTIAL ⚠️ (GitHub active, email pending)
Operations: READY ✅
Monitoring: PARTIAL ⚠️ (CI active, uptime pending)

Pilot Launch: READY (after legal)
Soft Launch: NOT READY (Stage 14)
Full Launch: NOT READY (Stage 15+)
```
