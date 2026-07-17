# SANAD Current Implementation Status

<!-- STATUS_AUTHORITY: CURRENT -->

**As of:** 2026-07-18, Asia/Riyadh  
**Accountable owner:** Project Owner  
**Authoritative tracker:** GitHub Issue #516

## 1. Controlling decision

```text
PROJECT_STATUS: CONDITIONAL_CONTINUE
CONTROLLED_DEVELOPMENT: ALLOWED
VERIFICATION: ALLOWED
LIMITED_PILOT: ALLOWED
TEMPORARY_RISK_ACCEPTANCE: ACTIVE_FOR_CONTROLLED_SCOPE_ONLY
BROAD_COMMERCIAL_GO_LIVE: NOT_APPROVED
ISSUE_101: CLOSED / HISTORICAL
ISSUE_516: AUTHORITATIVE_REMEDIATION_TRACKER
```

Issue #101 closed on 2026-07-06. Any document that still describes it as open is obsolete and cannot control the current project state.

## 2. Evidence model

SANAD distinguishes documented, implemented, verified, deployed and accepted states. Stage closure, passing CI, HTTP `200` or a healthy endpoint does not by itself prove broad production readiness.

Temporary risk acceptance is a separate governance state. It permits only the declared controlled scope and does not close a finding, reduce its severity, replace owner-specific assurance or authorize broad commercial production.

## 3. Current runtime boundary

| Area | Current state | Decision |
|---|---|---|
| Frontend | Vercel application at `https://snad-app.vercel.app` | Reachable at last executive review |
| Backend hosting | Temporary development tunnel | Open; temporarily accepted for controlled development and limited pilot only |
| BFF/authentication | Application controls and hourly synthetic implemented | Open pending production observation and REM-P0-001; temporarily accepted for controlled scope |
| Commercial production | Critical gates remain open | Not approved |

Historical Render, Supabase, stage-release and provider observations remain valid only for their stated date and SHA.

## 4. Temporary residual-risk acceptance

The Project Owner accepted the six remaining findings temporarily on 2026-07-18 under:

`docs/governance/TEMPORARY-RISK-ACCEPTANCE-2026-07-18.md`

The acceptance applies only to:

- controlled development;
- verification and remediation;
- limited, non-contractual pilot operation.

It does not authorize:

- broad commercial go-live;
- external contractual SLA commitments;
- enterprise-production-ready claims;
- closure or severity reduction of any accepted finding.

The acceptance must be reviewed every 30 days and expires automatically when broad commercial-go-live review begins, when the review interval is missed, after a related SEV0/SEV1 incident, after confirmed breach/data loss/tenant-isolation failure, after material architecture change, or after pilot expansion beyond the approved boundary.

## 5. Closed findings

### REM-P0-003 — Executor #23

Closed through PR #522 and implementation SHA `e026cdb99393c2ca8c7e5a86fd549622105492ab`, with a 440-item importable backlog and Jira, Azure DevOps and GitHub structural validation.

### REM-P1-007 — Integrated business-process E2E evidence

Closed after implementing and verifying four tenant-scoped business-process vertical slices:

- Sales Order-to-Cash.
- Procure-to-Pay.
- Hire-to-Pay.
- Commerce Order-to-Refund.

The accepted evidence includes HTTP execution, PostgreSQL 16 Testcontainers execution, tenant isolation, RBAC denial, idempotent replay, centralized audit, workflow approvals, transaction rollback, balanced double-entry accounting, payment reconciliation, inventory conservation and analytics reconciliation. All four processes are `FULLY_VERIFIED`, every required step is verified and no blocked step remains.

Closure authority:

- `docs/governance/REM-P1-007-CLOSURE-DECISION-2026-07-17.md`.
- `docs/quality/e2e/business-process-catalog.json`.
- `docs/quality/e2e/REM-P1-007-EXECUTION-PLAN.md`.
- `.github/workflows/business-process-e2e-validation.yml`.

This closure corrects the integrated-evidence defect only. It does not approve broad commercial go-live or assert completion of every ERP feature and industry variant.

### REM-P1-008 — Service levels and incident operations

Closed through PR #525 and merge SHA `6472be6a8a0252a52d977bc281757cd469bbb7db`. Internal SLO governance is active; external SLA targets are not contractual until approved in customer agreements.

### REM-P1-010 — Status-document reconciliation

Closed through PR #529 and merge SHA `e6b7cb7e9dde8b603bc282fb5c491c5fdad6a8e0` after Status Documentation Validation run `29544935675`, job `87775027749`, completed successfully on exact PR SHA `903da584bdd3ff63a21c59da3a965a3c7beb7e49`.

## 6. Open findings under temporary acceptance

| Finding | State | Owner domain |
|---|---|---|
| REM-P0-001 — backend development tunnel | Deferred / open / temporarily accepted for controlled scope | Infrastructure & DevOps |
| REM-P0-002 — BFF/authentication/session reliability | Application controls implemented / open / temporarily accepted pending production observation and REM-P0-001 | Identity, Operations and Infrastructure |
| REM-P0-004 — later-deliverable governance sequence | Open / temporarily accepted for controlled scope | Executive Steering Committee |
| REM-P0-005 — backup, restore and disaster recovery | Open / temporarily accepted for controlled scope | Infrastructure and Data Platform |
| REM-P0-006 — independent security assurance | Open / temporarily accepted for controlled scope | Security Governance |
| REM-P1-009 — repository visibility decision | Open / temporarily accepted for controlled scope | Project Owner and Security Governance |

The REM-P0-002 control and closure contract is `docs/operations/reliability/AUTH-SESSION-RELIABILITY.md`.

The detailed unresolved-risk report remains `docs/governance/UNRESOLVED-RISKS-REPORT-2026-07-17.md`. Temporary acceptance changes the allowed operating boundary only; it does not close findings, reduce severity or satisfy closure criteria.

## 7. Status-document interpretation

- `docs/stage-*` records historical stage evidence.
- `docs/execution/` records execution-scope evidence.
- `docs/crm/` records CRM-scope evidence.
- `docs/quality/e2e/` records REM-P1-007 process evidence and its accepted closure boundary.
- `docs/production-readiness/` contains plans, targets and checklists unless explicitly promoted by this authority.
- `READY`, `GO`, `LIVE`, `COMPLETE` and `PASS` apply only to their declared date, SHA and scope.

The classification registry is `docs/governance/status-document-registry.json`.

## 8. Current sources of truth

- GitHub Issue #516.
- `docs/governance/CURRENT-STATUS.json`.
- This document.
- `docs/governance/TEMPORARY-RISK-ACCEPTANCE-2026-07-18.md`.
- `docs/governance/UNRESOLVED-RISKS-REPORT-2026-07-17.md`.
- `docs/governance/EXECUTIVE-REVIEW-REMEDIATION-2026-07-17.md` for remediation history.
- Exact-SHA workflow, deployment and runtime evidence linked by those sources.

## 9. Update rule

A material status change must update both current-status documents, identify exact evidence, classify superseded records and pass `.github/workflows/status-documentation-validation.yml`.
