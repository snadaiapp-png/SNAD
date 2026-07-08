# Stage 14 — Scalability Assessment

**Date**: 2026-07-08

---

## Current Scale Capacity

### Frontend (Vercel)

```
Platform: Vercel (serverless)
Scaling: Automatic (Vercel handles traffic spikes)
Concurrent users: Unlimited (CDN-cached static pages)
Build output: 11 routes (static + dynamic)
Edge caching: Enabled
Expected capacity: 10,000+ concurrent users
```

### Backend (Spring Boot on Render)

```
Platform: Render (or equivalent)
Current: Single instance
Scaling: Vertical (bigger instance) or horizontal (multiple instances)
Expected capacity: 1,000 concurrent requests/second (single instance)
Database connection pool: 20 connections (configurable)
```

### Database (PostgreSQL)

```
Current: H2 (in-memory for dev) / PostgreSQL (production)
Expected capacity: 100GB data, 100 concurrent connections
Scaling: Vertical (bigger instance) or read replicas
```

## Expected Growth (12 months)

```
Month 1-3 (Pilot):
  Tenants: 3-5
  Users: 15-25
  Data: < 1GB
  Traffic: < 100 requests/day

Month 4-6 (Soft Launch):
  Tenants: 10-30
  Users: 100-300
  Data: 1-5GB
  Traffic: 1,000-5,000 requests/day

Month 7-12 (Full Launch):
  Tenants: 50-200
  Users: 500-2,000
  Data: 5-50GB
  Traffic: 10,000-50,000 requests/day
```

## Bottleneck Analysis

### Current Bottlenecks

```
1. Backend: Single instance (no horizontal scaling yet)
   Mitigation: Deploy multiple instances behind load balancer (Stage 15)

2. Database: Single PostgreSQL instance
   Mitigation: Add read replicas for read-heavy workloads (Stage 15)

3. No caching layer (Redis)
   Mitigation: Add Redis for session cache and hot data (Stage 15)

4. No CDN for API responses
   Mitigation: Cache GET responses at edge (Vercel Edge Functions)
```

### Scalability Plan

```
Horizontal scaling:
  - Backend: 2-3 instances behind load balancer
  - Database: Primary + 1-2 read replicas
  - Cache: Redis cluster (3 nodes)

Vertical scaling:
  - Backend: 2GB → 4GB → 8GB RAM
  - Database: 2GB → 8GB → 16GB RAM
  - Cache: 512MB → 2GB → 4GB

Auto-scaling:
  - CPU > 70% → scale up
  - Memory > 80% → scale up
  - Response time > 500ms → scale up
```

## Scalability Readiness

```
Frontend: READY (Vercel auto-scales) ✅
Backend: PARTIAL (single instance, horizontal scaling needed) ⚠️
Database: PARTIAL (single instance, replicas needed) ⚠️
Cache: NOT CONFIGURED (Redis recommended) ⚠️
CDN: PARTIAL (Vercel Edge for static, API not cached) ⚠️

Scalability Assessment: COMPLETE
  → Current capacity sufficient for pilot (3-5 tenants)
  → Horizontal scaling needed for 30+ tenants (Stage 15)
  → Full enterprise scale (200+ tenants) requires architecture review
```
