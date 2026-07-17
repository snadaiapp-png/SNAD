# SANAD Current Implementation Status

<!-- STATUS_AUTHORITY: CURRENT -->

**As of:** 2026-07-17, Asia/Riyadh  
**Accountable owner:** Project Owner  
**Authoritative tracker:** GitHub Issue #516

## 1. Controlling decision

```text
PROJECT_STATUS: CONDITIONAL_CONTINUE
CONTROLLED_DEVELOPMENT: ALLOWED
LIMITED_PILOT: ALLOWED
BROAD_COMMERCIAL_GO_LIVE: NOT_APPROVED
ISSUE_101: CLOSED / HISTORICAL
ISSUE_516: AUTHORITATIVE_REMEDIATION_TRACKER
```

Issue #101 closed on 2026-07-06. Any document that still describes it as open is obsolete and cannot control the current project state.

## 2. Evidence model

SANAD distinguishes:

1. **Documented** — requirement or design exists.
2. **Implemented** — code or operating control exists.
3. **Verified** — reproducible evidence passes on an exact version.
4. **Deployed** — the exact version is active in the target environment.
5. **Accepted** — the accountable owner records a scope-specific decision.

Stage closure, a passing CI job, an HTTP `200` response or a healthy endpoint does not by itself prove broad production readiness.

## 3. Current runtime boundary

| Area | Current state | Decision |
|---|---|---|
| Frontend | Vercel application at `https://snad-app.vercel.app` | Reachable at last executive review |
| Backend hosting | Temporary development tunnel | Deferred; not accepted as final enterprise hosting |
| BFF/authentication | Bounded BFF retry/timeout, refresh single-flight, cookie safety and hourly production synthetic implemented | Open pending deployment evidence, 72-hour observation and REM-P0-001 dependency |
| Commercial production | Critical gates remain open | Not approved |

Historical Render, Supabase, stage-release and provider observations remain valid only for their stated date and SHA.

## 4. Closed findings

### REM-P0-003 — Executor #23

Closed through PR #522 and implementation SHA `e026cdb99393c2ca8c7e5a86fd549622105492ab`, with a 440-item importable backlog and Jira, Azure DevOps and GitHub structural validation.

### REM-P1-008 — Service levels and incident operations

Closed through PR #525 and merge SHA `6472be6a8a0252a52d977bc281757cd469bbb7db`. Internal SLO governance is active; external SLA targets are not contractual until approved in customer agreements.

### REM-P1-010 — Status-document reconciliation

Closed through PR #529 and merge SHA `e6b7cb7e9dde8b603bc282fb5c491c5fdad6a8e0` after Status Documentation Validation run `29544935675`, job `87775027749`, completed successfully on exact PR SHA `903da584bdd3ff63a21c59da3a965a3c7beb7e49`.

The correction established a machine-readable current status, an explicit authority order, historical/planning classifications, visible warnings on high-risk legacy records, and a fail-closed CI gate.

## 5. Open, deferred and remediated findings

| Finding | State | Owner domain |
|---|---|---|
| REM-P0-001 — backend development tunnel | Deferred / open | Infrastructure & DevOps |
| REM-P0-002 — BFF/authentication/session reliability | Application controls implemented / open pending Production observation and REM-P0-001 | Identity, Operations and Infrastructure |
| REM-P0-004 — later-deliverable governance sequence | Open | Executive Steering Committee |
| REM-P0-005 — backup, restore and disaster recovery | Open | Infrastructure and Data Platform |
| REM-P0-006 — independent security assurance | Open | Security Governance |
| REM-P1-007 — cross-module E2E business evidence | Remediation in progress; governed framework implemented and Sales Qualification slice added, but no full cross-module process is verified | QA and Business Product Owners |
| REM-P1-009 — repository visibility decision | Open | Project Owner and Security Governance |

The REM-P0-002 control and closure contract is `docs/operations/reliability/AUTH-SESSION-RELIABILITY.md`.

The REM-P1-007 execution authority consists of:

- `docs/quality/e2e/business-process-catalog.json`.
- `docs/quality/e2e/REM-P1-007-EXECUTION-PLAN.md`.
- `com.sanad.platform.e2e.SalesQualificationBusinessProcessE2ETest`.
- `.github/workflows/business-process-e2e-validation.yml`.

The implemented foundation slice proves Lead → Qualification → Account/Contact → Opportunity → Won, plus idempotency, tenant isolation, RBAC denial, audit, timeline, dashboard consistency and rejected-mutation rollback. It remains `PARTIAL_VERIFIED`; Quotation, Sales Order, Inventory Reservation, Delivery, Invoice, Ledger Posting, Collection and reconciled Analytics remain blocked. Procure-to-Pay, Hire-to-Pay and Commerce Order-to-Refund remain `NOT_EXECUTABLE` under the governed catalog. REM-P1-007 is therefore still open and closure is not authorized.

The detailed current report is `docs/governance/UNRESOLVED-RISKS-REPORT-2026-07-17.md`. It contains the impact, ownership and closure conditions for all seven open findings.

Deferral changes work priority only. It does not close or reduce the severity of a risk. Implemented controls do not close a finding until production verification and acceptance criteria are complete.

## 6. Status-document interpretation

- `docs/stage-*` records historical stage evidence.
- `docs/execution/` records execution-scope evidence.
- `docs/crm/` records CRM scope evidence.
- `docs/quality/e2e/` records current REM-P1-007 process evidence and must not be interpreted as full closure while any process is partial or not executable.
- `docs/production-readiness/` contains plans, targets and checklists unless explicitly promoted by this authority.
- `READY`, `GO`, `LIVE`, `COMPLETE` and `PASS` inside those records apply only to their date, SHA and declared scope.

The classification registry is `docs/governance/status-document-registry.json`.

## 7. Current sources of truth

- GitHub Issue #516.
- `docs/governance/CURRENT-STATUS.json`.
- This document.
- `docs/governance/UNRESOLVED-RISKS-REPORT-2026-07-17.md`.
- `docs/governance/EXECUTIVE-REVIEW-REMEDIATION-2026-07-17.md` for remediation evidence and closure history.
- Exact-SHA workflow, deployment and runtime evidence linked by those sources.

## 8. Update rule

A material status change must update both current-status documents, identify exact evidence, classify superseded records and pass `.github/workflows/status-documentation-validation.yml`.
