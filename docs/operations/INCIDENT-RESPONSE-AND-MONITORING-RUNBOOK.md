# Incident Response and Monitoring Runbook

Status: #294 remains open until monitoring, alerting, and incident response evidence is complete.

## Required dashboards

| Dashboard | Required signals | Status |
|---|---|---|
| Infrastructure | CPU, memory, restarts, availability | TBD |
| Application | request rate, error rate, latency | TBD |
| Database | connections, slow queries, storage, locks | TBD |
| API | 4xx, 5xx, p95/p99 latency | TBD |
| Auth/session | login failures, refresh failures, logout events | TBD |
| Tenant isolation | denied cross-tenant attempts, policy violations | TBD |
| Email | delivery failures, recovery flow failures | TBD |
| Backup | backup success/failure, restore test age | TBD |
| Deployment | deployment success/failure, rollback events | TBD |
| Security | scan failures, critical/high findings | TBD |

## Required alert routing

| Severity | Response target | Escalation |
|---|---|---|
| Critical | immediate response | Project Manager + Security/Infra Owner |
| High | same business day | Responsible owner |
| Medium | triage queue | Engineering owner |
| Low | backlog | Product/engineering owner |

## Incident workflow

1. Detect alert.
2. Classify severity.
3. Assign incident owner.
4. Preserve evidence.
5. Mitigate or rollback.
6. Communicate status.
7. Complete root cause analysis.
8. Add regression guard.
9. Close incident after verification.

## Closure condition

#294 can close only after dashboards, alert tests, escalation matrix, on-call ownership, and runbooks are approved.
