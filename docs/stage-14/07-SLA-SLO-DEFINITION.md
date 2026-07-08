# Stage 14 — SLA/SLO Definition

**Date**: 2026-07-08

---

## Service Level Agreements (SLA)

### Free Pilot Tier

```
Availability: 99.5% (best effort)
  Monthly downtime allowed: 3.6 hours
  No service credits for downtime
Support: Best effort via GitHub Issues
Response time: No guarantee
Maintenance: Scheduled, 48h notice
```

### Professional Tier

```
Availability: 99.9%
  Monthly downtime allowed: 43 minutes
  Service credit: 10% of monthly fee for each hour below target
Support: Email (24h response)
  Critical: 2h response
  High: 8h response
  Medium: 24h response
  Low: 3 days response
Maintenance: Scheduled, 7 days notice
```

### Enterprise Tier

```
Availability: 99.95%
  Monthly downtime allowed: 21 minutes
  Service credit: 20% of monthly fee for each hour below target
Support: Priority email + phone
  Critical: 1h response, 4h resolution target
  High: 4h response, 1 business day resolution target
  Medium: 8h response, 3 business day resolution target
  Low: 2 days response, 1 week resolution target
Maintenance: Scheduled, 14 days notice, customer-approved window
Custom SLA: Negotiable
```

## Service Level Objectives (SLOs)

### Availability SLOs

```
Measurement: HTTP 200 rate from synthetic monitoring
  - UptimeRobot checks every 5 minutes
  - Status: Success if HTTP 200, Failure if non-200 or timeout

Calculation:
  Availability = (Total minutes - Downtime minutes) / Total minutes × 100

Targets:
  Pilot: 99.5%
  Professional: 99.9%
  Enterprise: 99.95%
```

### Latency SLOs

```
Measurement: Response time percentiles
  - p50 (median): < 500ms
  - p95: < 2s
  - p99: < 5s

Measurement method:
  - Vercel Analytics (RUM)
  - Synthetic monitoring (WebPageTest)
```

### Error Rate SLOs

```
HTTP 5xx rate: < 0.1% of requests
HTTP 4xx rate: < 1% of requests (excluding 401 auth redirects)
Console errors: 0 critical errors
Hydration errors: 0
```

## Service Level Indicators (SLIs)

```
1. Availability
   - Metric: HTTP success rate
   - Source: UptimeRobot + Vercel Analytics

2. Latency
   - Metric: Response time percentiles (p50, p95, p99)
   - Source: Vercel Analytics + synthetic monitoring

3. Error rate
   - Metric: 5xx and 4xx percentage
   - Source: Vercel logs + application logs

4. Throughput
   - Metric: Requests per second
   - Source: Vercel Analytics + backend metrics

5. Saturation
   - Metric: CPU, memory, DB connection usage
   - Source: Render/infrastructure metrics
```

## Error Budget Policy

```
Error budget = (1 - SLO target) × total time

Pilot (99.5%): 3.6 hours/month error budget
Professional (99.9%): 43 minutes/month error budget
Enterprise (99.95%): 21 minutes/month error budget

Error budget consumption:
  - Unplanned downtime consumes budget
  - Planned maintenance does NOT consume budget (with notice)

Error budget exhausted:
  - Freeze non-critical feature deployments
  - Focus on reliability improvements
  - Notify affected customers
```

## SLA/SLO Readiness

```
SLA definitions: DOCUMENTED ✅
SLO targets: DEFINED ✅
SLI metrics: DEFINED ✅
Error budget policy: DOCUMENTED ✅
Measurement infrastructure: PARTIAL ⚠️ (need UptimeRobot + Vercel Analytics)
Service credit process: DOCUMENTED ✅

SLA/SLO Definition: COMPLETE
  → Ready to publish in Terms of Service
  → Measurement infrastructure to be set up (Stage 15)
```
