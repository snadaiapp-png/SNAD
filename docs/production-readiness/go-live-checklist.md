> **DOCUMENT STATUS: PLANNING BASELINE — NOT CURRENT APPROVAL**  
> Snapshot: 2026-06-25. Unchecked items are targets, not deployed controls.  
> Current status: `docs/governance/CURRENT-IMPLEMENTATION-STATUS.md` and GitHub Issue #516.

# SANAD Commercial Production Go-Live Checklist

## Governance

- [ ] Issues #30 through #36 are closed with evidence.
- [ ] No unresolved critical or high-severity blocker exists.
- [ ] Residual risks are documented and accepted.
- [ ] Go-Live Committee review is complete.
- [ ] Project owner records the final GO decision in issue #37.

## Infrastructure and reliability

- [ ] AWS production landing zone is approved.
- [ ] EKS production cluster is operational across multiple availability zones.
- [ ] RDS PostgreSQL Multi-AZ is operational.
- [ ] Secondary-region disaster recovery capability is validated.
- [ ] Availability target of 99.95% is monitored.
- [ ] Pilot-only free-tier sleep behavior is removed.
- [ ] GitOps deployment through GitHub Actions and ArgoCD is enforced.
- [ ] Rollback and restart procedures are tested.

## Backup and recovery

- [ ] RPO 15 minutes is demonstrated.
- [ ] RTO 60 minutes is demonstrated.
- [ ] Automated backups and point-in-time recovery are enabled.
- [ ] Backup retention is at least 35 days.
- [ ] Immutable encrypted recovery copies exist.
- [ ] Cross-region recovery copies are verified.
- [ ] Full restore exercise has passed.

## Monitoring and operations

- [ ] Frontend, backend, database and dependency monitoring is live.
- [ ] Latency, error rate, saturation and availability dashboards are live.
- [ ] Central logs and audit trails are searchable.
- [ ] Alert routing and escalation are tested.
- [ ] Incident commander and operational owners are assigned.
- [ ] Runbooks and maintenance procedures are approved.
- [ ] Launch-day command center is scheduled.

## Capacity and quality

- [ ] Workload model and peak assumptions are approved.
- [ ] Load, stress and endurance tests pass.
- [ ] p50, p95 and p99 latency results are accepted.
- [ ] Database connection-pool limits are validated.
- [ ] UAT and regression testing pass.
- [ ] Production smoke suite passes.

## Security

- [ ] OAuth2/OIDC controls are implemented.
- [ ] MFA is enforced for administrators.
- [ ] RBAC and ABAC are validated.
- [ ] TLS 1.3 and approved encryption controls are enforced.
- [ ] Secrets are managed through approved Vault/KMS controls.
- [ ] Tenant isolation tests pass.
- [ ] Dependency, container and infrastructure scans pass.
- [ ] No credential is stored in source control or evidence.
- [ ] SIEM integration and security alerting are operational.

## Compliance and data governance

- [ ] PDPL data inventory and processing map are complete.
- [ ] Data residency decision is approved.
- [ ] Retention and deletion rules are implemented.
- [ ] Audit logging is complete and protected.
- [ ] Provider and processor responsibilities are documented.
- [ ] ISO 27001, SOC 2 and GDPR-readiness gaps are recorded and assigned.

## Launch sequence

- [ ] Internal launch approved.
- [ ] Beta launch approved and exit criteria met.
- [ ] Public launch approved.
- [ ] Customer support and communications are ready.
- [ ] Post-launch monitoring window and rollback authority are active.

## Decision

- Final decision: `GO / NO-GO`
- Decision owner: Project Owner
- Date:
- Approved release:
- Residual risks accepted:
- Conditions:
