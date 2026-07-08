# Stage 16 — Tenant Growth Scaling Plan

**Date**: 2026-07-08

---

## Growth Tiers

### 10 Tenants (~100 users)

```
Performance requirements:
  - Page load: < 2s
  - API response: < 500ms
  - Backend CPU: < 30%
  - Database connections: < 20

Database requirements:
  - Storage: < 500MB
  - Connections: 20 (free tier sufficient)
  - Read replica: NOT REQUIRED
  - Backup: Weekly manual

Storage requirements:
  - Database: 1GB plan
  - File storage: Not needed (no file upload)
  - Logs: GitHub Actions (90 days)

Monitoring requirements:
  - UptimeRobot (free tier, 5-min checks)
  - Vercel Analytics (free tier)
  - GitHub Actions CI

Estimated cost: $0-20/month
  - Vercel: Free (Hobby)
  - Backend: Free (Render free tier) or $7 (Starter)
  - Database: Free (1GB) or $7 (10GB)
  - Monitoring: Free (UptimeRobot + Vercel Analytics)

Risks:
  - Single backend instance (no redundancy)
  - Manual backups (no automation)
  - Limited monitoring

Mitigation:
  - Daily health checks
  - Weekly manual database backup
  - Monitor for performance degradation
```

### 50 Tenants (~500 users)

```
Performance requirements:
  - Page load: < 2s
  - API response: < 500ms
  - Backend CPU: < 50%
  - Database connections: < 50

Database requirements:
  - Storage: < 5GB
  - Connections: 50-100 (paid tier)
  - Read replica: RECOMMENDED (1 replica)
  - Backup: Daily automated

Storage requirements:
  - Database: 10GB plan
  - File storage: 10GB (if file upload added)
  - Logs: Centralized logging (Logtail or equivalent)

Monitoring requirements:
  - UptimeRobot (paid, 1-min checks)
  - Vercel Analytics (Pro)
  - Sentry (error tracking)
  - Backend metrics (CPU, memory, response time)

Estimated cost: $50-100/month
  - Vercel: $20 (Pro)
  - Backend: $25 (Standard, 2GB RAM)
  - Database: $20 (50GB)
  - Redis: $15 (cache)
  - Monitoring: $0-30 (free tiers + paid)

Risks:
  - Backend may hit CPU limits during peak
  - Database connections may be tight
  - No automated failover

Mitigation:
  - Add 2nd backend instance (horizontal scaling)
  - Add Redis cache (reduces DB load)
  - Add PgBouncer (connection pooling)
  - Set up alerts for CPU > 70%
```

### 100 Tenants (~1,000 users)

```
Performance requirements:
  - Page load: < 2s
  - API response: < 500ms
  - Backend CPU: < 60% (across instances)
  - Database connections: < 100

Database requirements:
  - Storage: < 10GB
  - Connections: 100+ (with pooling)
  - Read replica: REQUIRED (2 replicas)
  - Backup: Daily automated + point-in-time recovery

Storage requirements:
  - Database: 50GB plan
  - File storage: 50GB
  - Logs: Centralized logging with 30-day retention

Monitoring requirements:
  - Full monitoring stack
  - APM (Application Performance Monitoring)
  - Custom dashboards (Grafana)
  - Alerting (Slack/email)

Estimated cost: $150-300/month
  - Vercel: $20 (Pro)
  - Backend: $50-100 (2-3 instances, Standard/Pro)
  - Database: $40 (50GB + replicas)
  - Redis: $30 (larger cache)
  - Monitoring: $30-50 (Sentry, Logtail, UptimeRobot)

Risks:
  - Database write contention
  - Cache invalidation complexity
  - Monitoring cost growth

Mitigation:
  - 3 backend instances (load balanced)
  - 2 read replicas (read/write split)
  - Redis cluster (3 nodes)
  - Automated failover (Patroni)
  - Query optimization
```

### 250 Tenants (~2,500 users)

```
Performance requirements:
  - Page load: < 2s
  - API response: < 500ms
  - Backend CPU: < 70% (across instances)
  - Database connections: < 200 (with pooling)

Database requirements:
  - Storage: < 25GB
  - Connections: 200+ (with pooling)
  - Read replica: REQUIRED (3 replicas)
  - Backup: Continuous (WAL archiving)
  - Sharding: Evaluate (by tenant_id if needed)

Storage requirements:
  - Database: 100GB+ plan
  - File storage: 200GB
  - Logs: Centralized with 90-day retention

Monitoring requirements:
  - Full stack + distributed tracing
  - Real-time alerting
  - Capacity planning dashboard
  - SLI/SLO tracking

Estimated cost: $400-700/month
  - Vercel: $20 (Pro)
  - Backend: $150-300 (4-5 instances, Pro)
  - Database: $100-150 (100GB + 3 replicas)
  - Redis: $50 (cluster)
  - Monitoring: $50-100

Risks:
  - Architecture may need refactoring (microservices)
  - Team size may be insufficient
  - Cost growth outpacing revenue

Mitigation:
  - Evaluate microservices architecture
  - Hire additional engineering (if revenue supports)
  - Optimize queries and cache strategy
  - Consider database sharding
```

### 500 Tenants (~5,000 users)

```
Performance requirements:
  - Page load: < 2s
  - API response: < 500ms
  - Backend CPU: < 70% (across instances)
  - Database connections: < 500 (with pooling + sharding)

Database requirements:
  - Storage: < 50GB
  - Connections: 500+ (with sharding)
  - Read replica: REQUIRED (per shard)
  - Backup: Continuous + cross-region
  - Sharding: REQUIRED (by tenant_id)

Storage requirements:
  - Database: 500GB+ (across shards)
  - File storage: 1TB+
  - Logs: Centralized with 1-year retention (compliance)

Monitoring requirements:
  - Full enterprise monitoring
  - Distributed tracing across services
  - Custom ML-based anomaly detection
  - 24/7 alerting with on-call rotation

Estimated cost: $1,000-2,000/month
  - Vercel: $20 (Pro) or $150 (Enterprise)
  - Backend: $400-800 (6-10 instances or Kubernetes)
  - Database: $300-500 (sharded cluster)
  - Redis: $100 (cluster)
  - Monitoring: $100-200

Risks:
  - Significant architecture overhaul
  - Team scaling required
  - Compliance complexity (SOC2, ISO27001)
  - Cost management critical

Mitigation:
  - Migrate to Kubernetes
  - Hire DevOps + backend engineers
  - Engage compliance auditor
  - Implement cost optimization (reserved instances, autoscaling)
```

## Tenant Growth Scaling Summary

```
10 tenants: $0-20/month, single instance, free tiers
50 tenants: $50-100/month, 2 instances, 1 DB replica, Redis
100 tenants: $150-300/month, 3 instances, 2 replicas, full monitoring
250 tenants: $400-700/month, 4-5 instances, 3 replicas, sharding eval
500 tenants: $1,000-2,000/month, Kubernetes, sharded DB, enterprise monitoring

Scaling triggers:
  - CPU > 70% sustained → add instance
  - DB connections > 80% → add PgBouncer/replica
  - Response time > 500ms → investigate and scale
  - Storage > 80% → upgrade plan

Decision authority: Owner (snadaiapp-png) with cost budget approval
```
