# Backup and Restore Runbook

## Control objectives

- RPO: 15 minutes
- RTO: 60 minutes
- Availability target: 99.95%
- Production database: PostgreSQL on AWS RDS, Multi-AZ
- Disaster recovery: secondary independent AWS region
- Backup retention: 35 days
- Immutable backup copy: required

## Backup design

1. Enable automated RDS backups and point-in-time recovery.
2. Retain database backups for 35 days.
3. Export scheduled immutable backup copies to a protected S3 bucket with versioning, Object Lock, KMS encryption and restricted IAM access.
4. Replicate recovery copies to the approved secondary AWS region.
5. Back up deployment manifests, Helm values, ArgoCD configuration, infrastructure-as-code state and critical runtime configuration without storing plaintext secrets in Git.
6. Record each backup job, failure and recovery action in the central audit trail and monitoring system.

## Restore test procedure

1. Open an approved recovery change record and identify the recovery point.
2. Restore the database to an isolated recovery environment.
3. Apply network restrictions so the restored environment cannot send production notifications or external side effects.
4. Deploy the matching application release through GitOps.
5. Start the backend with the production-equivalent profile.
6. Confirm Flyway validates the expected schema version without destructive migration.
7. Run integrity checks for tenants, organizations, memberships and users.
8. Run backend health, readiness and critical API smoke tests.
9. Run frontend-to-backend connectivity checks.
10. Measure elapsed restore time and calculate the actual data-loss window.
11. Compare measured results with RTO 60 minutes and RPO 15 minutes.
12. Destroy the isolated recovery environment after evidence is preserved.

## Mandatory evidence

- Recovery point timestamp
- Restore start and finish timestamps
- Measured RPO and RTO
- Provider backup configuration record
- Database integrity results
- Flyway validation output
- Health and smoke-test results
- Operator and reviewer names
- Deviations and residual risks

## Prohibited actions

- Do not run `flyway clean` against production or recovery copies.
- Do not overwrite the live production database during a test.
- Do not store database credentials in repository files or evidence screenshots.
- Do not declare this gate passed based only on provider configuration; an actual restore test is mandatory.

## Gate exit

Issue #30 may close only when the restore test passes, measured RPO/RTO meet approved targets, evidence is committed, and no critical recovery gap remains.
