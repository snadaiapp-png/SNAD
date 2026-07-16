# SNAD Executive Review Remediation Report

**Review date:** 2026-07-17 (Asia/Riyadh)  
**Repository:** `snadaiapp-png/SNAD`  
**Reviewed production application SHA:** `63b29ba33cff192529046a682b06dae121dc68a1`  
**Executive remediation merges:** `5a2f337ec2502db32f94985110c900ab472d0c9c`, `afb159e33066dcb92c6df28820ef1108f7333a47`  
**Authoritative remediation tracker:** GitHub issue `#516`  
**Decision:** **CONTINUE CONDITIONALLY — BROAD COMMERCIAL GO-LIVE NOT APPROVED**

## 1. Review scope

This review applies the executive-management requirements to the actual repository and production chain. It distinguishes evidence from declarations and uses the following status model:

1. **Documented** — a design or requirement exists.
2. **Implemented** — code exists on a reviewed branch.
3. **Verified** — automated or reproducible evidence passes on an exact SHA.
4. **Deployed** — the exact SHA is deployed to the target environment.
5. **Accepted** — the responsible gate owner records a scope-specific decision.

No component is considered enterprise-production-ready merely because a document, screen, endpoint, pull request, or isolated test exists.

## 2. Current verified production state

The following checks were executed against the production environment on 2026-07-17 at approximately 01:45 Asia/Riyadh:

| Check | Verified result | Acceptance |
|---|---|---|
| Vercel production UI | HTTP `200`; Arabic RTL and SNAD markers present | Pass |
| Frontend-to-backend status | HTTP `200`; `configured=true`, `reachable=true`, `statusCode=200` | Pass at check time |
| BFF authentication path without session | Multiple HTTP `401` responses, followed by two HTTP `502` timeouts | **Intermittent failure — not accepted** |
| Backend target reported by production | `streak-train-empower.ngrok-free.dev` | Operational but unacceptable as final enterprise hosting |
| Latest application deployment | Vercel `READY`, target `production`, SHA `63b29ba...` | Pass for the Next.js deployment scope only |

The production web application is live and connected, but authentication-chain availability is intermittent. This state does **not** close infrastructure, identity reliability, disaster recovery, security assurance, or commercial go-live gates.

## 3. Corrections executed

### COR-001 — Replace the UI-only smoke test with an end-to-end readiness gate

**Previous defect:** `.github/workflows/production-smoke.yml` validated only the HTML page. A green result did not prove backend configuration, BFF connectivity, authentication routing, approved backend identity, or backend health.

**Correction:**

- Added `scripts/ci/check-production-readiness.py`.
- The probe fails closed unless all of the following pass:
  - Production UI returns HTTP `200` and expected bilingual/RTL markers.
  - `/api/system/backend-status` returns HTTP `200` with `configured=true`, `reachable=true`, and `statusCode=200`.
  - `/api/platform/api/v1/auth/me` returns HTTP `401` without a session.
  - The backend host exactly matches the explicitly approved production host.
  - The approved backend Actuator endpoint returns HTTP `200` with `status=UP`.
- Added bounded retries, fail-fast behavior, Python compilation validation, and HTTPS-only URL enforcement.
- Added protection against redirecting the probe to an unapproved host.
- Produces structured JSON evidence for every run.
- Runs after relevant frontend/backend changes, on pull requests, manually, and hourly.
- Uploads evidence for 30 days and publishes a GitHub Actions summary.

**Status:** Completed and merged through PR `#517` as `5a2f337ec2502db32f94985110c900ab472d0c9c`.

### COR-002 — Remove contradictory duplicate CRM implementation state

**Previous defect:** PR `#514` remained open as an `EXEC-PROMPT-CRM-005` implementation after PR `#515` had already been independently verified, accepted, merged, and deployed for the same governed prompt. The branches contained competing domain implementations.

**Correction:**

- Added an explicit supersession record to PR `#514`.
- Renamed it with `[SUPERSEDED BY #515]`.
- Closed it without deleting the branch, preserving recoverability while removing execution ambiguity.
- Clarified on PR `#515` that `UNRESOLVED BLOCKERS: 0` applied only to the CRM-005 delivery scope, not full-platform commercial production readiness.

**Status:** Completed.

### COR-003 — Establish a single executive evidence and remediation record

**Previous defect:** Project status was spread across pull-request descriptions, historical transition documents, deployment metadata, and narrative reports. This allowed scope-specific claims such as “zero unresolved blockers” to coexist with temporary backend hosting and open enterprise gates.

**Correction:**

- Added this dated remediation report to the repository.
- Defined evidence levels and a scope-specific production decision.
- Recorded unresolved findings with severity, root cause, accountable owner role, and closure criteria.
- Opened issue `#516` as the authoritative P0/P1 remediation gate.
- Added execution evidence and current runtime findings to that issue.

**Status:** Completed and active; the report and issue must be updated after material releases or risk decisions.

### COR-004 — Remove deprecated workflows that generated false failed runs

**Previous defect:** Two superseded NVD workflow files remained under `.github/workflows` with empty trigger definitions. Ordinary pushes generated failed GitHub Actions runs reporting `No jobs were run`, contaminating commit status without executing a valid control.

**Correction:**

- Removed `.github/workflows/nvd-database-maintenance.yml`.
- Removed `.github/workflows/nvd-feed-mirror-publisher.yml`.
- Preserved their supersession rationale under `docs/archive/workflows/NVD-DEPRECATED-WORKFLOWS.md`.
- Left the approved successor workflows unchanged.

**Status:** Completed and merged through PR `#518` as `afb159e33066dcb92c6df28820ef1108f7333a47`.

## 4. Errors and gaps not corrected

### REM-P0-001 — Production backend depends on a development tunnel

- **Severity:** P0 / Critical operational risk
- **Observed state:** Production points to `streak-train-empower.ngrok-free.dev`.
- **Root cause:** The Java backend is running outside a managed production hosting environment and is exposed through an ngrok tunnel.
- **Why not corrected here:** Requires infrastructure provisioning, database/network design, secrets, deployment ownership, and controlled cutover authorization.
- **Owner:** Infrastructure & DevOps
- **Required closure:** Deploy the backend to a managed production platform with a stable domain, TLS, capacity controls, health checks, centralized logs, controlled secrets, rollback, and an availability commitment. Update production variables and the approved-host policy, then prove cutover with the readiness gate.

### REM-P0-002 — BFF authentication reliability is intermittent

- **Severity:** P0 / User-access and session reliability risk
- **Observed evidence:** Runtime logs showed repeated expected HTTP `401` responses for unauthenticated `/api/v1/auth/me`, followed by HTTP `502` responses caused by `TimeoutError` at approximately `2026-07-16T22:45:38Z` and `22:45:44Z`. Earlier logs also contained login `503` and refresh `502` failures.
- **Root cause:** Backend response latency/availability through the tunnel, combined with the absence of a managed backend SLA and an authenticated synthetic transaction.
- **Why not corrected here:** Reliable closure requires production infrastructure changes plus a dedicated synthetic account, controlled credentials, rotation policy, and non-destructive test tenant.
- **Owner:** Identity & Access + Platform Operations + Infrastructure
- **Required closure:** Eliminate tunnel dependency, establish backend capacity and latency controls, and add an approved synthetic flow covering login, session restoration, refresh, logout, lockout, and audit evidence. Demonstrate sustained success over an agreed observation window.

### REM-P0-003 — Master Execution Backlog gate is not evidenced as closed

- **Severity:** P0 / Governance and delivery risk
- **Observed state:** The executive gate requires a complete, importable backlog covering Epics → Features → Stories → Tasks, estimates, priorities, dependencies, acceptance criteria, sprints, risks, QA, security, migration, and integrations. No current exact-version artifact and validated import evidence were verified during this review.
- **Root cause:** Delivery documentation and code-execution streams evolved independently.
- **Owner:** Program Management / Product Operations
- **Required closure:** Publish the authoritative backlog in an importable format, validate a dry-run import, map every active prompt and module to work items, and record formal approval.

### REM-P0-004 — Governance sequence for later architecture and organization outputs remains unreconciled

- **Severity:** P0 / Decision integrity risk
- **Observed state:** Historical governance blocked later outputs until the execution-backlog gate closed, while later architecture and operating-model materials were produced.
- **Root cause:** No single reconciliation decision distinguishes draft advisory work from formally authorized execution.
- **Owner:** Executive Steering Committee
- **Required closure:** Issue a dated decision classifying each later deliverable as draft, exception-authorized, or approved after gate closure. Update every source-of-truth status record to the same decision.

### REM-P0-005 — Production backup, restore, and disaster-recovery evidence is incomplete

- **Severity:** P0 / Data-loss and continuity risk
- **Observed state:** Repository CI contains database-related tests, but this review did not verify a production database backup, isolated restore, recovery drill, RPO, or RTO for the live backend environment.
- **Root cause:** Production backend and database hosting are not yet managed as formal enterprise services.
- **Owner:** Infrastructure & DevOps + Data Platform
- **Required closure:** Define RPO/RTO, automate encrypted backups, complete an isolated restore test, perform a disaster-recovery exercise, and retain auditable evidence.

### REM-P0-006 — Broad commercial go-live security assurance is not closed

- **Severity:** P0 / Security and compliance risk
- **Observed state:** Repository security and tenant-isolation tests exist, but no independent full-scope penetration test, production configuration review, privacy assessment, or executive residual-risk acceptance was verified.
- **Root cause:** Component-level CI evidence has been treated as equivalent to independent system assurance.
- **Owner:** Security, Governance & Compliance
- **Required closure:** Complete independent penetration testing, tenant-boundary testing, authorization review, secrets review, dependency assessment, threat-model validation, remediation verification, and formal residual-risk acceptance.

### REM-P1-007 — End-to-end business-process proof is incomplete

- **Severity:** P1 / Product integrity risk
- **Observed state:** CRM and platform tests are extensive, but the executive review requires complete scenarios across sales, inventory, accounting, purchasing, HR/payroll, ecommerce/returns, workflow, audit, and analytics. A unified evidence suite was not verified.
- **Root cause:** Test evidence is organized mainly by technical component or execution prompt.
- **Owner:** QA & Release + Business Product Owners
- **Required closure:** Implement traceable end-to-end scenarios with financial assertions, tenant isolation, rollback behavior, audit records, and release evidence.

### REM-P1-008 — Enterprise service objectives and incident ownership are incomplete

- **Severity:** P1 / Operational governance risk
- **Observed state:** Health endpoints and monitoring exist, but no approved SLA/SLO, error budget, on-call ownership, escalation matrix, or measured availability report was verified.
- **Root cause:** Monitoring was implemented before the operating model was finalized.
- **Owner:** Platform Operations
- **Required closure:** Approve service objectives, alert thresholds, ownership, escalation, maintenance policy, incident severity model, post-incident process, and monthly availability reporting.

### REM-P1-009 — Repository visibility requires an explicit governance decision

- **Severity:** P1 / Information-governance risk
- **Observed state:** Current deployment metadata reports the GitHub repository visibility as `public`, while older transition material described it as private.
- **Root cause:** Repository visibility changed or historical documentation is stale.
- **Owner:** Project Owner + Security Governance
- **Required closure:** Confirm intended visibility, review repository contents and history for sensitive information, document the decision, and update every source-of-truth document.

### REM-P1-010 — Legacy status documents can still misstate current reality

- **Severity:** P1 / Reporting risk
- **Observed state:** Historical documents contain obsolete deployment SHAs, previous outage states, and module-status declarations that are no longer current.
- **Root cause:** Status was embedded in long-lived narrative documents rather than generated from live evidence.
- **Owner:** Program Management
- **Required closure:** Mark historical documents clearly, link them to this report, and generate future release/status summaries from repository, CI, deployment, and runtime evidence.

## 5. Executive decision

The review produced concrete repository and governance corrections, and production remains reachable. The new monitoring gate now exposes failures that the previous UI-only test could not detect.

However, SANAD remains **conditionally approved for controlled development and limited pilot use only**. Broad commercial production approval is blocked by:

1. Temporary backend hosting through a development tunnel.
2. Intermittent BFF/authentication timeouts.
3. Missing production continuity and disaster-recovery evidence.
4. Incomplete independent security assurance.
5. Unclosed execution-backlog and governance reconciliation gates.
6. Incomplete cross-module business-process evidence.

No report may state “no blockers” for the full platform until every P0 item is closed with reproducible evidence or explicitly accepted as residual risk by the authorized executive and security owners.
