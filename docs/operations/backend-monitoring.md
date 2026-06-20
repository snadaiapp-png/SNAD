# Backend Monitoring Baseline

## Status

**PLANNED — NOT YET CONFIGURED**

This document defines the intended monitoring and alerting baseline for the SANAD backend after Render resources are provisioned. Actual availability, retention, restart behavior, notifications, and database monitoring must be verified in the Render Dashboard.

## Planned Monitoring Channels

### Render Dashboard

The intended operational view includes:

- Service status and deployment history
- CPU and memory utilization
- Response-time indicators
- Application log stream
- PostgreSQL availability, size, and connection activity

All items remain pending provisioning and verification.

### Application Logs

The Spring Boot application writes structured logs to stdout/stderr. Render log collection and retention must be confirmed after the service is created.

### Actuator Health Endpoints

| Endpoint | Expected production result |
|---|---|
| `/actuator/health` | HTTP 200 and `UP` |
| `/actuator/health/liveness` | HTTP 200 and `UP` |
| `/actuator/health/readiness` | HTTP 200 and `UP` |

## Operational Thresholds

The following are initial operational targets, not configured alerts:

| Condition | Initial target action |
|---|---|
| Health endpoint failure | Investigate deployment and service logs |
| Repeated restarts | Stop rollout and inspect runtime/database failures |
| Memory above 90% | Investigate memory pressure or increase service capacity |
| CPU above 90% for five minutes | Investigate load or increase service capacity |
| Database pool exhaustion | Inspect connection leaks and pool sizing |
| HTTP 5xx above 5% for five minutes | Inspect logs, database connectivity, and recent deployment |
| Deployment failure | Retain previous known-good deployment and investigate |

## Planned During Provisioning

- **Render deployment notifications:** Pending provisioning.
- **Render service-health notifications:** Pending provisioning and verification.
- **Auto-restart behavior:** Pending verification after service creation.
- **CPU and memory metrics:** Pending verification after service creation.
- **Database monitoring:** Pending verification after database provisioning.
- **Log retention:** Pending confirmation against the selected Render plan.

## Future Integrations

- External uptime monitoring
- Slack, email, or PagerDuty alerts
- Application Performance Monitoring
- Database slow-query monitoring
- Centralized operational dashboards

## Retention, Backups, and Recovery

| Capability | Current status |
|---|---|
| Log-retention duration | Pending confirmation in Render Dashboard |
| Backup frequency | Pending confirmation during provisioning |
| PITR availability | Pending confirmation during provisioning |
| Recovery window | Dependent on active Render Workspace and database capabilities; not verified |
| Restore procedure | To be validated after database provisioning |

No production backup, PITR, log-retention, or restore configuration is currently verified.

## Incident Response Baseline

1. Detect the failure through health checks, platform status, or user reports.
2. Assess service status, deployment history, and application logs.
3. Mitigate by stopping rollout, restoring the last known-good deployment, or correcting configuration.
4. Verify health, liveness, readiness, CORS, and frontend integration.
5. Document root cause and preventive action.

## Provisioning Disclaimer

All monitoring items in this document are planned, not configured. None should be treated as active until Render resources are provisioned and the relevant behavior is verified.
