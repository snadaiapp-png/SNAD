# Backend Monitoring Baseline

## Overview

This document defines the initial monitoring and alerting baseline for the SANAD backend deployed on Render.

## Monitoring Channels

### 1. Render Dashboard

| Metric | Source | Frequency |
|---|---|---|
| Service status (Live/Stopped/Deploying) | Render dashboard | Real-time |
| Deployment history | Render dashboard | Per deployment |
| CPU usage | Render dashboard | Real-time |
| Memory usage | Render dashboard | Real-time |
| Response time | Render dashboard | Real-time |
| Log stream | Render dashboard | Real-time |

### 2. Application Logs

Logs are written to stdout/stderr in the format:
```
2026-06-20 12:00:00.000 INFO  [main] com.sanad.platform.SanadPlatformApplication - Started
```

Render captures all stdout/stderr output and makes it available in the dashboard log stream.

### 3. Actuator Health Endpoints

| Endpoint | Check | Expected |
|---|---|---|
| `/actuator/health` | Overall health | 200, `{"status":"UP"}` |
| `/actuator/health/liveness` | Liveness probe | 200, `{"status":"UP"}` |
| `/actuator/health/readiness` | Readiness probe | 200, `{"status":"UP"}` |

## Operational Thresholds

| Threshold | Trigger | Action |
|---|---|---|
| Health endpoint failure | 3 consecutive non-200 responses | Render auto-restart |
| Repeated restart | Service restarts > 3 times in 10 minutes | Manual investigation; check logs |
| High memory | Memory > 90% of plan limit | Upgrade plan or investigate leak |
| High CPU | CPU > 90% for 5 minutes | Upgrade plan or investigate hot path |
| Database connection exhaustion | HikariCP pool exhausted | Increase `DATABASE_POOL_MAX` or investigate connection leak |
| HTTP 5xx increase | > 5% of requests return 5xx for 5 minutes | Check application logs; verify database connectivity |
| Deployment failure | Deploy build fails or health check times out | Rollback to previous deployment |

## Alerting

### Currently Configured

- **Render deployment notifications**: Email notifications on deployment success/failure (configured in Render dashboard)
- **Render service health**: Auto-restart on health check failure

### Not Yet Configured (Future)

- External alerting (Slack, email, PagerDuty)
- Uptime monitoring (e.g., UptimeRobot, BetterUptime)
- APM (Application Performance Monitoring)
- Database-specific monitoring (connection count, slow queries)
- Custom metric dashboards

## Log Retention

| Plan | Log Retention |
|---|---|
| Render Starter | 7 days |
| Render Standard | 30 days |

## Database Monitoring

Render PostgreSQL dashboard provides:
- Connection count
- Database size
- Query activity
- Backup status

## Backup and Recovery

| Aspect | Starter Plan | Standard Plan |
|---|---|---|
| Backup frequency | Daily | Daily |
| Retention | 7 days | 1 year |
| PITR (Point-in-Time Recovery) | Not available | Available |
| Manual restore | Via dashboard | Via dashboard |

## Incident Response

1. **Detect**: Health check failure, user report, or monitoring alert
2. **Assess**: Check Render dashboard for service status and logs
3. **Mitigate**: Restart service, rollback deployment, or scale up
4. **Resolve**: Fix root cause, deploy fix, verify health
5. **Post-mortem**: Document incident, update thresholds if needed
