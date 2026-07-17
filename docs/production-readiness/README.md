> **DOCUMENT STATUS: PLANNING BASELINE — NOT CURRENT APPROVAL**  
> Snapshot: 2026-06-25. This file defines target architecture and gate work. It does not prove those targets are deployed or accepted.  
> Current status: `docs/governance/CURRENT-IMPLEMENTATION-STATUS.md` and GitHub Issue #516.

# SANAD Production Readiness & Go-Live Gate

## Status

- Pilot operational validation: **Closed**
- Commercial production gate: **Open**
- Production approval: **Not granted**
- Current Render/Vercel/Supabase free-tier environment: **Pilot only**

## Approved production targets

- Target cloud: AWS-first
- Runtime: Kubernetes/EKS with GitOps through GitHub Actions and ArgoCD
- Database: PostgreSQL/RDS, Multi-AZ
- Cache: Redis/ElastiCache
- Object storage: S3
- Secrets and encryption: Vault/KMS
- Observability: OpenTelemetry, Prometheus, Grafana, centralized logs and SIEM
- Availability target: 99.95%
- RPO: 15 minutes
- RTO: 60 minutes
- Disaster recovery: secondary independent AWS region
- Backup retention baseline: 35 days with immutable copies

## Mandatory gate workstreams

| Gate | Issue | Deliverable | Exit condition |
|---|---:|---|---|
| Backup and restore | #30 | Backup policy, restore runbook, restore test report | RPO/RTO demonstrated |
| Monitoring and alerting | #31 | Dashboards, alerts, escalation matrix | No critical blind spot |
| Capacity and performance | #32 | Load model and test report | Safe operating limits approved |
| Reliability and availability | #33 | Paid production plan and recovery evidence | No pilot-only dependency |
| Security hardening | #34 | Security review and remediation record | No unresolved critical/high blocker |
| Compliance and residency | #35 | Data inventory and compliance checklist | Blocking gaps closed |
| Operational readiness | #36 | Runbooks, incident exercise, launch checklist | Operations sign-off complete |
| Final Go/No-Go | #37 | Evidence package and decision record | Owner records GO or NO-GO |

## Execution order

1. Establish backup, restore, RPO and RTO controls.
2. Implement monitoring, logging and alerting.
3. Execute capacity, load and endurance validation.
4. Move the runtime from pilot-only plans to approved production infrastructure.
5. Complete security hardening and secrets governance.
6. Complete PDPL, residency, retention and auditability review.
7. Execute incident and launch readiness exercises.
8. Hold the final Go/No-Go review.

## Global acceptance rules

Production approval is prohibited until all child gates are closed, evidence is committed, residual risks are documented, and the project owner grants final approval. Direct work on `main` is prohibited; every change follows Branch → Commit → Pull Request → CI → Review → Merge → Deployment → Smoke Verification.

## Required evidence package

- Architecture and provider configuration records
- Backup and restore logs
- RPO/RTO measurement
- Monitoring dashboards and alert tests
- Load, stress and endurance reports
- Dependency and vulnerability scan results
- Secrets and access review
- Data-flow, residency, retention and deletion records
- Runbooks, escalation matrix and incident exercise report
- Final launch checklist and signed Go/No-Go decision
