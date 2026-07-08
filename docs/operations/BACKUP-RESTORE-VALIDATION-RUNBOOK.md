# Backup and Restore Validation Runbook

Status: #293 remains open until backup and restore evidence is complete.

## Required validation

| Area | Required proof | Status |
|---|---|---|
| Backup configuration | scheduled backup enabled | TBD |
| Encryption | encryption at rest and in transit | TBD |
| Retention | retention policy documented | TBD |
| PITR | point-in-time recovery capability | TBD |
| Isolated restore | restore completed outside production | TBD |
| Schema validation | schema matches expected version | TBD |
| Flyway history | migration history intact | TBD |
| App startup | application starts after restore | TBD |
| Data integrity | sampled records verified | TBD |
| RTO/RPO | measured and approved | TBD |

## Execution sequence

1. Record source environment and backup timestamp.
2. Restore into isolated non-production environment.
3. Validate schema and migration history.
4. Start application against restored database.
5. Execute smoke checks.
6. Verify representative data integrity.
7. Measure recovery time and recovery point.
8. Attach evidence to #293.
9. Obtain Infrastructure Owner approval.

## Closure condition

#293 can close only after a successful isolated restore and owner approval.
