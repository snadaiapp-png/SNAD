# SNAD Documentation Index

This directory is the canonical documentation entry point for the SNAD Business Operating System.

## System reference

- [System overview and boundaries](system/SNAD-SYSTEM-REFERENCE.md)
- [Current implementation status](governance/CURRENT-IMPLEMENTATION-STATUS.md)
- [Acceptance evidence matrix](testing/ACCEPTANCE-EVIDENCE.md)

## Product identity

- [Visual identity implementation](brand/SNAD-VISUAL-IDENTITY-IMPLEMENTATION.md)

## Security and account recovery

- [Password recovery operating model](security/PASSWORD-RECOVERY-NOTIFICATION-OPERATING-MODEL.md)
- [Account recovery and email runbook](operations/ACCOUNT-RECOVERY-EMAIL-RUNBOOK.md)
- [OWASP Dependency-Check operating model](security/OWASP-DEPENDENCY-CHECK-OPERATING-MODEL.md)

## Deployment and runtime

- [Runtime configuration matrix](deployment/RUNTIME-CONFIGURATION-MATRIX.md)
- [Backend runtime](deployment/backend-runtime.md)
- [Render backend deployment](deployment/render-backend-deployment.md)
- [Backend monitoring baseline](operations/backend-monitoring.md)

## Engineering

- [Frontend API client guide](development/frontend-api-client.md)
- [Architecture decision records](architecture/adr/)
- [Execution progress](execution/progress-report.md)

## Security data pipeline

- NVD bulk feed architecture, operating model, recovery runbook, and failure classification are maintained under `docs/security/`.

## Governance rules

1. Repository code and immutable GitHub evidence are the source of truth.
2. Secrets are stored only in the deployment platform secret manager.
3. Documentation must distinguish development integration from production authorization.
4. Issue #101 remains the controlling security gate until explicitly closed by the project owner.
5. A passing build or successful pilot deployment does not authorize commercial go-live.
