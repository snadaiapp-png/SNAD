# SNAD Documentation Index

<!-- STATUS_AUTHORITY: CURRENT -->

**Current as of:** 2026-07-17, Asia/Riyadh

This directory is the canonical documentation entry point. Status claims must follow the authority order below and the [Status Documentation Policy](governance/STATUS-DOCUMENTATION-POLICY.md).

## Current status authority

1. GitHub Issue `#516` — executive remediation tracker.
2. [Machine-readable current status](governance/CURRENT-STATUS.json).
3. [Current implementation status](governance/CURRENT-IMPLEMENTATION-STATUS.md).
4. [Current unresolved errors and risks](governance/UNRESOLVED-RISKS-REPORT-2026-07-17.md).
5. [Executive remediation evidence and closure history](governance/EXECUTIVE-REVIEW-REMEDIATION-2026-07-17.md).
6. [Status document registry](governance/status-document-registry.json).

Issue `#101` is closed and historical. It is not the current controlling gate.

## Current decision

```text
CONDITIONAL CONTINUE
CONTROLLED DEVELOPMENT AND LIMITED PILOT: ALLOWED
BROAD COMMERCIAL GO-LIVE: NOT APPROVED
OPEN FINDINGS: 7 (P0=5, P1=2)
```

## System and architecture

- [System overview and boundaries](system/SNAD-SYSTEM-REFERENCE.md)
- [Architecture decision records](architecture/adr/)
- [Service-level and incident operations](operations/reliability/README.md)

## Deployment and runtime

- [Runtime configuration matrix](deployment/RUNTIME-CONFIGURATION-MATRIX.md)
- [Backend runtime](deployment/backend-runtime.md)
- [Backend monitoring](operations/backend-monitoring.md)

Deployment guides and old provider-specific records describe their own date and scope. They are not current approval evidence unless cited by the current authority.

## Security and account recovery

- [Password recovery operating model](security/PASSWORD-RECOVERY-NOTIFICATION-OPERATING-MODEL.md)
- [Account recovery runbook](operations/ACCOUNT-RECOVERY-EMAIL-RUNBOOK.md)
- [OWASP Dependency-Check operating model](security/OWASP-DEPENDENCY-CHECK-OPERATING-MODEL.md)

## Engineering and execution

- [Frontend API client guide](development/frontend-api-client.md)
- [Executor #23 Master Execution Backlog](execution/executor-23/README.md)
- [Acceptance evidence matrix](testing/ACCEPTANCE-EVIDENCE.md)

Execution, module and stage records close only their declared scope. They cannot declare full-platform readiness or zero blockers unless the executive authority confirms it.

## Historical and planning records

- `docs/stage-*`: stage-scoped historical evidence.
- `docs/execution/`: prompt and execution-scope evidence.
- `docs/crm/`: CRM module evidence.
- `docs/production-readiness/`: plans, targets and checklists until promoted by current authority.

Historical records are retained for auditability and must not be silently rewritten to simulate current evidence.

## Governance rules

1. Repository code, exact-SHA CI, deployment evidence and runtime evidence are factual sources.
2. Current status is published only through the authority chain above.
3. Secrets remain outside repository documentation.
4. A passing build, preview deployment, HTTP `200`, stage closure or module acceptance is not broad commercial approval.
5. Missing telemetry is unknown, not success.
6. Any material status change must update the machine-readable status and pass Status Documentation Validation.
