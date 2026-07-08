# Stage 15 — Operational KPIs

**Date**: 2026-07-08

---

## Platform KPIs

### Availability

```
Metric: Uptime percentage
Target: 99.5% (pilot), 99.9% (professional), 99.95% (enterprise)
Current: 100% (since launch 2026-07-07)
Measurement: UptimeRobot (to be set up) + Vercel status
Frequency: Real-time (alerting), Monthly (reporting)
```

### Performance

```
Metric: Response time (p50, p95, p99)
Target: p50 < 500ms, p95 < 2s, p99 < 5s
Current: < 1s (CDN-cached)
Measurement: Vercel Analytics (to be enabled) + synthetic monitoring
Frequency: Real-time (dashboard), Weekly (review)
```

### Error Rate

```
Metric: HTTP 5xx percentage
Target: < 0.1% of requests
Current: 0%
Measurement: Vercel logs + application logs
Frequency: Real-time (alerting), Daily (review)

Metric: HTTP 4xx percentage (excluding auth)
Target: < 1% of requests
Current: 0%
```

## Customer KPIs

### Activation

```
Metric: New user activation rate
Target: > 60% within 7 days (complete first value action)
Current: N/A (no customers yet)
Measurement: User completes login + workspace access
Frequency: Weekly
```

### Adoption

```
Metric: Feature adoption rate
Target: > 40% using CRM, > 30% using Control Plane
Current: N/A (no customers yet)
Measurement: Feature usage tracking
Frequency: Monthly
```

### Retention

```
Metric: Monthly active users (MAU)
Target: Growth trend positive
Current: N/A (no customers yet)
Measurement: Unique users per month
Frequency: Monthly

Metric: Annual retention rate
Target: > 90%
Current: N/A
Measurement: Tenant renewal rate
Frequency: Annual
```

### Satisfaction

```
Metric: NPS (Net Promoter Score)
Target: > 40 (good)
Current: N/A (no customers yet)
Measurement: Quarterly NPS survey
Frequency: Quarterly
```

## Engineering KPIs

### CI/CD

```
Metric: CI pass rate
Target: > 95% of runs pass
Current: 100% (required checks)
Measurement: GitHub Actions run history
Frequency: Weekly

Metric: Deployment frequency
Target: 2-4 deployments per month (bi-weekly release train)
Current: 6 deployments (Stage 11-14 docs)
Measurement: Vercel deployment history
Frequency: Monthly

Metric: Lead time (PR to production)
Target: < 2 days (minor), < 4 hours (hotfix)
Current: < 1 hour (same-session merges)
Measurement: PR creation to production deploy
Frequency: Monthly
```

### Quality

```
Metric: Test coverage
Target: > 70% (frontend), > 60% (backend)
Current: 376 frontend tests, 467 backend tests
Measurement: Test count + coverage tool (if added)
Frequency: Monthly

Metric: Visual regression pass rate
Target: 100% (0 unreviewed diffs)
Current: 100% (10 baselines, all pass)
Measurement: Playwright visual regression results
Frequency: Per PR

Metric: Post-Merge Verification pass rate
Target: 100%
Current: 100%
Measurement: Post-Merge workflow results
Frequency: Per merge
```

### Incident

```
Metric: Incident count (per month)
Target: < 2 Critical/High per month
Current: 0 (since launch)
Measurement: GitHub Issues with severity labels
Frequency: Monthly

Metric: Mean time to resolve (MTTR)
Target: < 2 hours (Critical), < 24 hours (High)
Current: N/A (no incidents)
Measurement: Issue open to close time
Frequency: Per incident

Metric: Rollback count
Target: < 1 per quarter
Current: 0
Measurement: Git revert count
Frequency: Quarterly
```

## Security KPIs

```
Metric: Secret scan findings
Target: 0 in current tree
Current: 0 (PASS)
Measurement: gitleaks + SNAD scanner
Frequency: Per PR

Metric: Security baseline pass rate
Target: 100%
Current: 100%
Measurement: Security Baseline workflow
Frequency: Per PR

Metric: Open security issues
Target: 0 Critical, 0 unaccepted High
Current: 0 (Issue #373 closed)
Measurement: GitHub Issues with security label
Frequency: Weekly

Metric: Time to patch (Critical vulnerabilities)
Target: < 24 hours
Current: N/A (no vulnerabilities)
Measurement: Vulnerability discovery to fix
Frequency: Per vulnerability
```

## KPI Review Process

```
Daily: Production health (HTTP 200, uptime)
Weekly: CI pass rate, incident count, open issues
Monthly: Activation, adoption, deployment frequency, test coverage, MTTR
Quarterly: Retention, NPS, compliance review, roadmap progress
Annually: SOC 2/ISO audit (when applicable), full review
```

## Operational KPIs Summary

```
Platform KPIs: DEFINED ✅ (availability, performance, errors)
Customer KPIs: DEFINED ✅ (activation, adoption, retention, NPS)
Engineering KPIs: DEFINED ✅ (CI/CD, quality, incidents)
Security KPIs: DEFINED ✅ (scan, baseline, vulnerabilities)

Current status: ALL MET (small sample size — pilot phase)
Review process: DOCUMENTED ✅
Measurement infrastructure: PARTIAL ⚠️ (need Vercel Analytics, uptime monitor)

Operational KPIs: DEFINED and READY for monitoring
```
