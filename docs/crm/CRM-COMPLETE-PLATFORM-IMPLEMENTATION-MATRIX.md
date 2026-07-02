# SNAD CRM Complete Platform Implementation Matrix

## Status

```text
Scope: CRM runtime systems requested after gap review
Branch: feat/crm-runtime-environment
PR: #196
Result: Source-controlled platform implementation added
Production release: Not authorized
```

## Implementation matrix

| Area | Implemented artifact |
|---|---|
| Event platform | RabbitMQ overlay, durable queues, DLX, event outbox dispatcher |
| Async dispatching | Outbox, timer, import, export, notification and webhook dispatchers |
| Workflow foundation | Workflow definition, instance, task and timer tables |
| Scheduler | Quartz configuration and scheduled CRM dispatchers |
| Notification foundation | Template/message tables and notification dispatcher |
| Object storage | MinIO local S3 overlay and CRM object-storage CloudFormation |
| Malware scanning | ClamAV service and client integration |
| Cache | Valkey service plus Redis cache configuration |
| Search | OpenSearch service, template installer and projection queue |
| Import/export | Job tables and dispatcher skeletons |
| API gateway | Nginx gateway overlay and gateway health endpoint |
| Observability | Prometheus, Grafana, OpenTelemetry Collector, Tempo, Loki and Promtail |
| Alerting | Prometheus alert rules for CRM runtime |
| Kubernetes | Namespace, service account, backend and web deployment scaffolds |
| Backup and restore | PostgreSQL backup and isolated restore verification scripts |
| Privacy controls | Consent, retention and privacy request database contracts |
| Integration hub | Integration endpoint and webhook delivery contracts |
| AI integration point | AI Gateway configuration block, disabled until approved provider exists |
| Security scanning | OWASP terminal gate with HTML and JSON evidence |
| AWS activation | OIDC/S3/NVD full activation workflow with fail-closed readiness |

## Backend integration dependencies

The backend build now includes AMQP, Redis, cache, Quartz, OpenTelemetry tracing and AWS S3/STS SDK support.

## Complete platform commands

```bash
make crm-platform-config
make crm-platform-build
make crm-platform-up
make crm-platform-readiness
make crm-platform-test
make crm-platform-logs
make crm-platform-down
```

## Database contracts

The CRM platform schema includes operational tables for events, dead letters, workflows, timers, notifications, imports, exports, webhooks, consent, retention, privacy requests, pipelines, leads, opportunities, activities, custom fields and integration endpoints.

## Validation gates

```text
CRM Platform Completeness CI
CRM Platform Runtime CI
CRM Runtime CI
CRM Image CI
CRM Storage Security CI
Security Scan / OWASP
AWS NVD Bootstrap CI
AWS NVD Full Activation
```

## Non-waived release gates

The repository cannot replace live managed infrastructure. Production remains blocked until the approved deployment environment provides database HA, managed search, managed cache, production object storage, TLS/DNS/WAF/CDN, provider credentials, secret manager integration, on-call alert routing, disaster-recovery evidence, privacy approvals and credential-rotation closure.

## Decision

```text
Previous CRM runtime gap list: implemented as source-controlled platform layer
Local complete platform: implemented
Static completeness gates: implemented
Runtime gate: implemented
External production gates: still fail-closed
Production release: NO-GO
```
