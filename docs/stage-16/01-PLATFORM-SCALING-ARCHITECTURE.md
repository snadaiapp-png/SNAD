# Stage 16 — Platform Scaling Architecture

**Date**: 2026-07-08

---

## Current Production State

```
Frontend: Vercel (auto-scaling, CDN-cached)
Backend: Single instance (Spring Boot on Render or equivalent)
Database: Single PostgreSQL instance (H2 for dev)
Cache: None (no Redis)
Monitoring: GitHub Actions CI + Vercel deployment tracking
```

## Current Architecture Limits

```
1. Backend: Single instance — no horizontal scaling, no redundancy
   Limit: ~1,000 concurrent requests/second
   Risk: Single point of failure

2. Database: Single PostgreSQL instance
   Limit: 20 connections (free tier), 100+ (paid)
   Risk: No read replicas, no failover

3. Cache: None
   Limit: All requests hit backend + database
   Risk: High latency under load, database overload

4. Monitoring: CI-based only
   Limit: No real-time alerting, no RUM
   Risk: Issues detected late (manual health checks)
```

## Expected Bottlenecks

```
At 30 tenants (~300 users):
  - Backend CPU: 30-50% (single instance)
  - Database connections: 40-60 (approaching limit)
  - Response time: 500ms-1s (degrading)

At 100 tenants (~1,000 users):
  - Backend CPU: 70-90% (single instance, critical)
  - Database connections: 100+ (exceeded)
  - Response time: 2-5s (unacceptable)
  - Database storage: 1-5GB (approaching limit)

At 200 tenants (~2,000 users):
  - Backend: OVERWHELMED (single instance)
  - Database: OVERWHELMED (single instance)
  - Production: DOWN without scaling
```

## Horizontal Scaling Plan

### Backend (Multi-Instance)

```
Phase 1 (30 tenants): 2 instances behind load balancer
  - Render: 2 web services + load balancer
  - Session: Stateless (JWT-based, no server session)
  - Cost: $50/month

Phase 2 (100 tenants): 3-5 instances
  - Auto-scaling group
  - CPU > 70% → scale up
  - Cost: $100-200/month

Phase 3 (200+ tenants): Kubernetes or equivalent
  - Auto-scaling pods
  - Health-based routing
  - Cost: $300-500/month
```

### Database (Read Replicas)

```
Phase 1 (30 tenants): Primary + 1 read replica
  - Writes: Primary
  - Reads: Replica (80% of traffic)
  - Failover: Manual (promote replica)
  - Cost: $40/month

Phase 2 (100 tenants): Primary + 2 read replicas
  - Connection pooling: PgBouncer
  - Failover: Automated (Patroni or equivalent)
  - Cost: $80/month

Phase 3 (200+ tenants): Primary + 3 replicas + sharding
  - Read-heavy queries: Replicas
  - Write-heavy: Primary
  - Sharding: By tenant_id (if needed)
  - Cost: $150-300/month
```

## Vertical Scaling Plan

### Backend Instance Sizes

```
Current: 512MB RAM, 0.1 CPU (free)
Phase 1: 2GB RAM, 1 CPU (Starter $7/month)
Phase 2: 4GB RAM, 2 CPU (Standard $25/month)
Phase 3: 8GB RAM, 4 CPU (Pro $100/month)
```

### Database Instance Sizes

```
Current: 1GB storage (free)
Phase 1: 10GB storage ($7/month)
Phase 2: 50GB storage ($20/month)
Phase 3: 100GB+ storage ($50/month)
```

## Database Requirements

```
Connection pooling: PgBouncer (Phase 1+)
Read replicas: Phase 1+
Automated backups: Daily pg_dump (Phase 1+)
Point-in-time recovery: Phase 2+
Automated failover: Phase 2+
Sharding: Phase 3 (if needed)
```

## Storage Requirements

```
Database storage:
  Phase 1: 10GB
  Phase 2: 50GB
  Phase 3: 100GB+

File storage (when file upload added):
  Service: Vercel Blob or AWS S3
  Phase 1: 10GB
  Phase 2: 100GB
  Phase 3: 1TB+
```

## Monitoring Requirements

```
Phase 1:
  - UptimeRobot (uptime monitoring)
  - Vercel Analytics (RUM)
  - Basic backend metrics (CPU, memory)

Phase 2:
  - Sentry (error tracking)
  - Centralized logging (Logtail or equivalent)
  - Database metrics (slow queries, connections)
  - Custom dashboards (Grafana)

Phase 3:
  - APM (Application Performance Monitoring)
  - Distributed tracing
  - Custom alerting
  - SLI/SLO dashboards
```

## Service Separation (Future)

```
Phase 3+ (200+ tenants):
  - Auth Service (separate from main backend)
  - CRM Service (separate module)
  - AI Gateway Service (separate)
  - Notification Service (separate)
  - Search Service (Elasticsearch or equivalent)

Architecture: Microservices or modular monolith
Decision: Based on team size and traffic patterns
```

## Scaling Decision Authority

```
Scaling decisions require:
  1. Owner approval (snadaiapp-png)
  2. Cost budget approval
  3. Architecture review
  4. Rollback plan (revert to previous instance size)
  5. No production downtime (blue-green or rolling deploy)
```

## Scaling Architecture Summary

```
Current: Single instance (sufficient for pilot)
Phase 1 (30 tenants): 2 backend + 1 DB replica + Redis
Phase 2 (100 tenants): 3-5 backend + 2 DB replicas + monitoring
Phase 3 (200+ tenants): Kubernetes + 3+ replicas + sharding

No destructive architecture changes without separate decision.
Scaling is additive (add instances, add replicas).
Production must remain LIVE during scaling operations.
```
