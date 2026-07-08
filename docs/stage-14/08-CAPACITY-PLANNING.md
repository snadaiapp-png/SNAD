# Stage 14 — Capacity Planning

**Date**: 2026-07-08

---

## Current Capacity

### Frontend (Vercel)

```
Plan: Hobby (free) or Pro ($20/month)
Bandwidth: 100GB/month (Hobby), 1TB/month (Pro)
Build minutes: 6,000/month (Hobby), 24,000/month (Pro)
Serverless function executions: 100/month (Hobby), 1M/month (Pro)
Current usage: Minimal (pilot phase)
Recommendation: Upgrade to Pro when traffic exceeds 10GB/month
```

### Backend (Render or equivalent)

```
Current: Single web service instance
Instance size: 512MB RAM, 0.1 CPU (free tier) or 2GB RAM, 1 CPU (Starter $7/month)
Expected capacity: 100-1,000 concurrent requests (depending on instance size)
Database connections: 20 (configurable)
Recommendation: Upgrade to Standard ($25/month) when pilot expands
```

### Database (PostgreSQL)

```
Current: H2 (in-memory) for dev, PostgreSQL for production
Render PostgreSQL: 1GB storage (free), 10GB ($7/month), 50GB ($20/month)
Connection limit: 20 (free), 100+ (paid)
Current usage: < 100MB (pilot phase)
Recommendation: 10GB plan when reaching 30+ tenants
```

## Capacity Projections

### 3-Month Horizon (Pilot, 5 tenants)

```
Frontend:
  Bandwidth: < 10GB/month → Hobby sufficient
  Build: < 1,000 minutes → Hobby sufficient
  
Backend:
  RAM: < 512MB → Free tier sufficient
  CPU: < 10% → Free tier sufficient

Database:
  Storage: < 100MB → Free tier sufficient
  Connections: < 20 → Free tier sufficient

Cost: $0/month (all free tiers)
```

### 6-Month Horizon (Soft Launch, 30 tenants)

```
Frontend:
  Bandwidth: 10-50GB/month → Upgrade to Pro ($20/month)
  Build: 1,000-5,000 minutes → Pro sufficient

Backend:
  RAM: 512MB-2GB → Upgrade to Starter ($7/month) or Standard ($25/month)
  CPU: 10-30% → Paid tier needed

Database:
  Storage: 100MB-1GB → 10GB plan ($7/month)
  Connections: 20-50 → Paid tier needed

Cost: ~$35-55/month
```

### 12-Month Horizon (Full Launch, 200 tenants)

```
Frontend:
  Bandwidth: 50-200GB/month → Pro sufficient
  Build: 5,000-15,000 minutes → Pro sufficient

Backend:
  RAM: 2-8GB → Standard or Pro ($25-100/month)
  CPU: 30-70% → Multiple instances recommended
  Instances: 2-3 (horizontal scaling)

Database:
  Storage: 1-5GB → 50GB plan ($20/month)
  Connections: 50-100 → Paid tier with connection pooling
  Read replica: Recommended ($20/month)

Cache (Redis):
  RAM: 512MB-2GB → Add Redis ($15/month)

Cost: ~$100-250/month
```

## Scaling Triggers

```
Frontend:
  Bandwidth > 80% of limit → Upgrade Vercel plan
  Build minutes > 80% → Upgrade Vercel plan

Backend:
  CPU > 70% sustained → Scale up or add instance
  Memory > 80% sustained → Scale up
  Response time > 500ms → Scale up or add instance
  5xx errors > 0.1% → Investigate and scale

Database:
  Storage > 80% → Upgrade plan
  Connections > 80% → Upgrade plan or add pooling
  Query time > 1s → Optimize queries or add read replica
  CPU > 70% → Upgrade plan or add read replica

Cache:
  Hit rate < 80% → Review cache strategy
  Memory > 80% → Upgrade Redis plan
```

## Capacity Review Process

```
Weekly (pilot):
  - Review Vercel Analytics (traffic, bandwidth)
  - Review backend metrics (CPU, memory, response time)
  - Review database metrics (storage, connections, slow queries)

Monthly (soft launch+):
  - Full capacity review
  - Compare actual vs projected growth
  - Adjust capacity plan
  - Budget for upcoming upgrades

Quarterly (full launch+):
  - Architecture review
  - Cost optimization review
  - Capacity plan update for next quarter
```

## Capacity Plan Readiness

```
Current capacity: SUFFICIENT for pilot ✅
3-month projection: DOCUMENTED ✅
6-month projection: DOCUMENTED ✅
12-month projection: DOCUMENTED ✅
Scaling triggers: DEFINED ✅
Review process: DOCUMENTED ✅
Cost estimates: DOCUMENTED ✅

Capacity Plan: APPROVED
  → Pilot: $0/month (free tiers)
  → Soft Launch: $35-55/month
  → Full Launch: $100-250/month
  → Enterprise: Custom (multi-instance, dedicated resources)
```
