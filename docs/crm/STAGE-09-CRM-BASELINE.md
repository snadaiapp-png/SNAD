# Stage 09 CRM Baseline Governance

Status: baseline not accepted until evidence is complete.

## Objective

Create a reviewable CRM baseline for Stage 09 before runtime, UX, AI, and final acceptance gates are closed.

## Required inventory

| Area | Required evidence | Status |
|---|---|---|
| APIs | Endpoint list, auth policy, tenant scope | TBD |
| Migrations | Migration files, checksums, rollback/recovery note | TBD |
| Domain modules | Module ownership and boundary map | TBD |
| Services | Service list and responsibilities | TBD |
| Repositories | Repository access policy | TBD |
| Events | Published/consumed events | TBD |
| Tests | Unit, integration, tenant isolation, contract tests | TBD |

## Boundary rules

- CRM owns deterministic CRM records and workflows.
- CRM must not directly access unrelated domain repositories.
- CRM must use approved workflow contracts for process automation.
- CRM must use the central AI Gateway for AI features.
- CRM must never call model providers directly.

## Tenant rules

- Every CRM API must derive tenant context from the approved authenticated context.
- Cross-tenant reads and writes must fail.
- Global unscoped queries are not allowed for tenant data.
- Audit records must include tenant, actor, action, entity, timestamp, and correlation ID.

## Findings register

| ID | Finding | Severity | Owner | Status | Evidence |
|---|---|---|---|---|---|
| CRM-BASELINE-001 | Baseline evidence pending | High | System Owner | Open | #325 |

## Acceptance

#325 can close only when this baseline is completed with exact SHA, test evidence, and no unresolved Critical/High finding.
