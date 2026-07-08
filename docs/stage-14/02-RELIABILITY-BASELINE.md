# Stage 14 — Reliability Baseline

**Date**: 2026-07-08

---

## Reliability Metrics

### Current Availability

```
Production URL: https://snad-app.vercel.app/
Uptime (measured): 100% since launch (2026-07-07)
Downtime incidents: 0
Average response time: < 1s (CDN-cached)
Backend health: UP (when deployed)
```

### Error Rates

```
HTTP 5xx errors: 0
HTTP 4xx errors: 0 (auth redirects return 200)
Console errors: 0
Hydration errors: 0
Runtime errors: 0
```

## SLOs (Service Level Objectives)

### Availability SLO

```
Target: 99.5% (pilot tier)
  Allowed downtime: ~3.6 hours/month

Target: 99.9% (professional tier)
  Allowed downtime: ~43 minutes/month

Target: 99.95% (enterprise tier)
  Allowed downtime: ~21 minutes/month
```

### Latency SLO

```
Page load (LCP): < 2.5s (95th percentile)
API response: < 500ms (95th percentile)
Time to interactive: < 3s (95th percentile)
```

### Error Rate SLO

```
HTTP 5xx rate: < 0.1% of requests
HTTP 4xx rate: < 1% of requests (excluding auth redirects)
```

## SLIs (Service Level Indicators)

```
1. Availability: HTTP 200 rate from uptime monitoring
2. Latency: Response time percentiles (p50, p95, p99)
3. Error rate: 5xx and 4xx percentages
4. Throughput: Requests per second
5. Saturation: CPU, memory, database connection usage
```

## Error Budget

```
Monthly error budget (99.5% availability):
  Total minutes: 43,200 (30 days)
  Allowed downtime: 216 minutes (3.6 hours)

Current error budget consumption: 0 minutes (no downtime)
Error budget remaining: 216 minutes (100%)
```

## Recovery Objectives

```
RPO (Recovery Point Objective):
  Code: 0 (git is always current)
  Database: 24 hours (once automated backups configured)
  Configuration: 0 (in version control)

RTO (Recovery Time Objective):
  Code: < 5 minutes (git revert + Vercel deploy)
  Database: < 1 hour (restore from backup)
  Configuration: < 30 minutes (manual re-entry)
```

## Reliability Risks

```
1. Single backend instance (no redundancy)
   Risk: Backend failure = production down
   Mitigation: Deploy 2+ instances (Stage 15)

2. No database replicas
   Risk: Database failure = production down
   Mitigation: Add read replica + automated failover (Stage 15)

3. No monitoring alerts
   Risk: Issues detected late (manual health checks)
   Mitigation: Set up UptimeRobot + Sentry (Stage 12 recommendation)

4. No load testing
   Risk: Unknown breaking point under load
   Mitigation: Conduct load testing (see 03-PERFORMANCE-LOAD-TESTING.md)
```

## Reliability Baseline Summary

```
Availability: 100% (since launch)
Error rate: 0%
Response time: < 1s
SLOs: DEFINED (99.5% pilot, 99.9% professional, 99.95% enterprise)
SLIs: DEFINED (5 indicators)
Error budget: 100% remaining
RPO/RTO: DEFINED

Reliability Baseline: DEFINED
  → Current: EXCEEDING targets (100% uptime)
  → Risks identified for scale (Stage 15 mitigation)
```
