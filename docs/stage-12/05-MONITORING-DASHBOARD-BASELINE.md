# Stage 12 — Monitoring Dashboard Baseline

**Date**: 2026-07-08

---

## Monitoring Infrastructure

### Current Monitoring Capabilities

```
1. GitHub Actions CI:
   - Post-Merge Main Verification (on push to main)
   - Production Smoke (on push to main)
   - Playwright E2E & Visual Regression (on PR + push to main)
   - Current Tree Secret Scan (on PR)
   - Security Baseline (on PR)

2. Vercel:
   - Auto-deploy on main push
   - Deployment status tracking via GitHub Deployments API
   - Preview deployments on PRs

3. GitHub:
   - Branch protection (required checks, review requirements)
   - Issue tracking
   - PR review tracking
```

### Monitoring Gaps (To Address)

```
1. No uptime monitoring (external service like UptimeRobot)
2. No real user monitoring (RUM) via Vercel Analytics
3. No error tracking (Sentry or similar)
4. No performance monitoring (Lighthouse CI in workflow)
5. No log aggregation (centralized logging)
```

## Dashboard Baseline

### Daily Health Check Dashboard

```
Manual checks (daily):
  ✅ Production URL returns HTTP 200
  ✅ Brand identity present (SNAD + سند)
  ✅ HTML attributes correct (lang=ar, dir=rtl, data-theme=light)
  ✅ All 6 routes return HTTP 200
  ✅ Vercel deployment state = success
  ✅ No new GitHub Actions failures

Command:
  curl -sS -o /dev/null -w "%{http_code}" https://snad-app.vercel.app/
```

### Weekly Review Dashboard

```
Weekly checks:
  - Review GitHub Actions run history
  - Review Vercel deployment history
  - Review open Issues and PRs
  - Run full Playwright E2E suite locally
  - Check for new secret scan findings
  - Review collaborator list
```

### CI Monitoring (Automated)

```
Active workflows on main:
  - Post-Merge Main Verification: triggers on push to main
  - Production Smoke: triggers on push to main
  - CI (Maven Test Suite): triggers on PR + push to main
  - Playwright E2E & Visual Regression: triggers on PR + push to main

Active workflows on PR:
  - Build Next.js Web (required)
  - provenance (required)
  - Current Tree Secret Scan
  - Backend Container Hardening
  - Maven Test Suite
  - And others
```

## Recommended Monitoring Additions (Stage 12 Baseline)

### 1. Uptime Monitoring

```
Service: UptimeRobot (free tier) or equivalent
URL: https://snad-app.vercel.app/
Interval: 5 minutes
Alert on: HTTP non-200, timeout, SSL expiry
```

### 2. Vercel Analytics

```
Enable Vercel Analytics for:
  - Core Web Vitals (LCP, FID, CLS)
  - Real user monitoring
  - Page view tracking
  - Top routes
```

### 3. Error Tracking (Future)

```
Service: Sentry (free tier) or equivalent
Integration: Next.js Error Boundary + API error logging
Alert on: Unhandled exceptions, 5xx errors
```

### 4. Lighthouse CI (Future)

```
Add Lighthouse CI to GitHub Actions:
  - Performance score
  - Accessibility score
  - Best practices score
  - SEO score
  - Enforce minimum thresholds
```

## Monitoring Dashboard Summary

```
Current monitoring: GitHub Actions + Vercel + GitHub Issues
Daily health check: MANUAL (curl + brand verification)
Weekly review: MANUAL (GitHub Actions history)
Automated CI: ACTIVE (Post-Merge, Production Smoke, Playwright, Secret Scan)

Recommended additions:
  - Uptime monitoring (UptimeRobot)
  - Vercel Analytics (RUM)
  - Error tracking (Sentry)
  - Lighthouse CI

Monitoring Status: BASELINED (improvements planned)
```
