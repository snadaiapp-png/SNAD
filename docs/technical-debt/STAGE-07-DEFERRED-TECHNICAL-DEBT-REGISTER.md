# SANAD Stage 07 — Deferred Technical Debt Register

**Register ID:** `SANAD-TDR-ST07-001`
**Stage:** 07 — Commercial Go-Live & Production Readiness
**Status:** DEFERRED CLOSURE — TECHNICAL DEBT OPEN
**Date Opened:** 2026-07-06
**Owner:** SANAD Project Manager
**Linked Milestone (GitHub):** `Stage 07 Deferred Technical Debt`
**Linked Stage:** Stage 08 (parallel execution authorized)
**Final Closure Gate:** Mandatory before final program closure and unconditional commercial authorization

---

## 1. Purpose

This register records every Stage 07 gap that was *not* fully closed before Stage 08 was authorized to start. The Project Manager decision (see SANAD-Z-STAGE-08-FULL-EXECUTION §1) explicitly:

* Allows Stage 08 to begin in parallel with Stage 07 debt remediation.
* Does **not** close Stage 07.
* Does **not** waive any Stage 07 requirement.
* Forbids final program closure while any debt below is open.

No debt may be silently waived, no issue may be closed without evidence, and no Stage 08 feature may bypass a Stage 07 production safety control.

---

## 2. Debt Policy

```text
NO DEBT MAY BE SILENTLY WAIVED
NO ISSUE MAY BE CLOSED WITHOUT EVIDENCE
NO TECHNICAL DEBT MAY BE DOWNGRADED WITHOUT PM APPROVAL
NO STAGE 08 FEATURE MAY REMOVE OR BYPASS A STAGE 07 CONTROL
NO FINAL PROGRAM CLOSURE WHILE STAGE 07 DEBT IS OPEN
```

Each debt item MUST carry:

```text
Debt ID
Title
Category
Blocking? (yes/no)
Linked Stage 07 Issue(s)
Owner
Evidence required
Acceptance criteria
Current status
Residual risk if deferred
SHA at closure
Workflow Run at closure
Closure approval
```

---

## 3. Debt Inventory

### TD-07-001 — OWASP Final Security Assessment

* **Category:** Security
* **Blocking Final Closure:** YES
* **Linked Issues:** #29, #109, #173
* **Owner:** Security Owner
* **Initial Status:** `OPEN — BLOCKING FINAL CLOSURE`

**Required evidence:**

* Production SAST report.
* Production DAST report.
* Container Image CVE scan report.
* Dependency vulnerability validation.
* API security validation.
* Authentication and session testing.
* Tenant-isolation security testing.
* Penetration test report OR formally accepted alternative scope.
* Vulnerability register.
* Closure of all Critical and High findings.
* Formal acceptance of residual risks by Security Owner.

**Acceptance Criteria:**

1. All Critical findings remediated and verified.
2. All High findings remediated, mitigated, or formally risk-accepted.
3. Penetration test report attached to closure issue.
4. Tenant isolation test PASS for cross-tenant read/write denial.
5. Security Owner approval recorded with SHA, run ID, and timestamp UTC.

---

### TD-07-002 — Production Backup and Restore Validation

* **Category:** Reliability / DR
* **Blocking Final Closure:** YES
* **Linked Issues:** #29
* **Owner:** Infrastructure Owner
* **Initial Status:** `OPEN — BLOCKING FINAL CLOSURE`

**Required evidence (production-grade, not local CI only):**

* Production backup configuration.
* Encryption at rest and in transit.
* Backup retention policy.
* Point-in-time recovery validation.
* Restore into an isolated recovery environment.
* Schema validation after restore.
* Flyway history validation after restore.
* Application startup after restore.
* Data-integrity validation.
* Recovery time measurement.
* Approved RPO and RTO.
* Restore runbook published.
* Owner approval recorded.

**Acceptance Criteria:**

1. Restore executed into isolated environment matching production schema.
2. RPO and RTO measured and within approved targets.
3. Runbook tested by an operator other than the author.
4. Infrastructure Owner approval recorded with SHA, run ID, and timestamp UTC.

---

### TD-07-003 — Monitoring, Alerting and Incident Response

* **Category:** Observability / Operations
* **Blocking Final Closure:** YES
* **Linked Issues:** #29
* **Owner:** Infrastructure Owner
* **Initial Status:** `OPEN — BLOCKING FINAL CLOSURE`

**Required:**

* Production dashboards (infra, app, DB, API, auth, tenant isolation, email, backup, deployment, security events).
* Alert routing and escalation matrix.
* On-call model.
* Incident response runbooks.
* Synthetic uptime monitoring.
* Tenant-isolation alerts.
* Email-delivery alerts.
* Backup-failure alerts.
* Deployment-failure alerts.
* Security-event alerts.

**Acceptance Criteria:**

1. At least one dashboard per domain online and populated with real production signals.
2. Alert routing validated by a synthetic test incident.
3. On-call schedule published.
4. Runbooks reviewed and approved by Operations Owner.

---

### TD-07-004 — Commercial Infrastructure and Paid Production Plan

* **Category:** Infrastructure / Commercial
* **Blocking Final Closure:** YES
* **Linked Issues:** #29
* **Owner:** Infrastructure Owner + Project Manager
* **Initial Status:** `OPEN — BLOCKING FINAL CLOSURE`

**Required evidence:**

* Paid production hosting plan (no Free Tier dependency for commercial workloads).
* No unapproved Sleep / Cold Start behavior.
* Approved CPU and memory allocations.
* Database production tier.
* Backup and restore support.
* High availability configuration.
* Autoscaling or capacity-expansion plan.
* Provider SLA documented.
* Capacity thresholds published.
* Cost baseline approved.
* Financial approval by Project Manager.

**Residual risk currently accepted (Stage 07):** Render FREE TIER remains in use. This is recorded as a residual risk and must be removed before final closure.

**Acceptance Criteria:**

1. Production tier plan document attached.
2. Financial approval recorded.
3. No Free Tier dependency in production path.
4. Capacity thresholds documented and monitored.

---

### TD-07-005 — Fail-Closed Commercial Workflow Completion

* **Category:** Release Engineering / Governance
* **Blocking Final Closure:** YES
* **Linked Issues:** #29, #101
* **Owner:** QA & Release Owner
* **Initial Status:** `OPEN — BLOCKING FINAL CLOSURE`

**Required workflow guarantees:**

* No `continue-on-error` anywhere in the gate path.
* Governance failure causes workflow failure.
* GO is not published before successful Artifact upload.
* Artifact upload failure produces NO-GO.
* Release Tag is revoked on any subsequent failure.
* Summary records `COMPLETED`.
* Summary records `releaseAuthorized: true`.
* Summary records `tagShaMatch: PASS`.
* Summary records `taggedSha`.
* NO-GO path records `failedGate`.
* GitHub UI shows Failure when decision is NO-GO.
* Regression Policy Check runs automatically.

**Status update (Stage 07 closure attempt):**

* PR #279 removed `continue-on-error` and added fail-closed policy checker (`scripts/ci/check-commercial-go-live-fail-closed.py`).
* Residual items (release tag revocation, `taggedSha` in summary, regression policy auto-trigger) remain open.

**Acceptance Criteria:**

1. Commercial Go-Live workflow runs end-to-end with a forced failure producing NO-GO and GitHub Failure status.
2. Forced success path produces GO with all required summary fields populated.
3. Regression Policy Check runs automatically on GO and NO-GO paths.

---

### TD-07-006 — Email Delivery Evidence Hardening

* **Category:** Governance / Email
* **Blocking Final Closure:** YES
* **Linked Issues:** #150, #173
* **Owner:** QA & Release Owner
* **Initial Status:** `OPEN — BLOCKING FINAL CLOSURE`

**Governance MUST verify explicitly:**

```text
result == PASS
messageId is non-empty
createdAt is non-empty
deliveryStatus == delivered
releaseSha == expected SHA
```

**Governance MUST reject:**

```text
queued
sent
pending
processing
unknown
empty
```

**Additional required evidence:**

* Password recovery end-to-end test.
* One-time token validation.
* Token reuse rejection.
* Expired token rejection.
* Session revocation after recovery.
* Confirmation notification delivered.
* Unauthorized email-proxy rejection (401/403).

**Acceptance Criteria:**

1. Governance step rejects any non-`delivered` status.
2. End-to-end password recovery test PASS in production.
3. Email proxy rejects unauthorized bearer tokens.

---

### TD-07-007 — Independent Human Approvals

* **Category:** Governance / Approvals
* **Blocking Final Closure:** YES
* **Linked Issues:** #29
* **Owner:** Project Manager
* **Initial Status:** `OPEN — BLOCKING FINAL CLOSURE`

**Required approvals (each from a distinct accountable account):**

* Security Owner
* Infrastructure Owner
* QA & Release Owner
* System Owner
* Project Manager

**Each approval MUST record:**

```text
Approver name
Approver role
Decision
Release SHA
Timestamp UTC
Accepted residual risks
Approval evidence
```

**Constraint:** Five roles recorded from a single GitHub account do NOT count as five independent approvals.

**Residual risk currently accepted (Stage 07):** All five approvals were issued by the same operator due to single-account limitation. This is recorded as residual risk and must be replaced by distinct accounts before final closure.

**Acceptance Criteria:**

1. Five distinct GitHub accounts recorded.
2. Each approval row complete with all required fields.
3. Approval evidence (PR comment, signed file, or signed audit record) attached.

---

### TD-07-008 — Controlling Issues Evidence Reconciliation

* **Category:** Governance / Issue Hygiene
* **Blocking Final Closure:** REVIEW REQUIRED
* **Linked Issues:** #29, #101, #109, #150, #173
* **Owner:** Project Manager
* **Initial Status:** `REVIEW REQUIRED`

**Required per issue:**

* Linked Evidence (PR, run, artifact).
* SHA documented.
* Workflow Run documented.
* Artifact documented.
* Verification timestamp documented.
* Fulfilled requirements documented.
* Residual risks documented.
* Owner approval recorded.
* Closure AFTER evidence, not before.

**Action:** Reopen any issue closed before its exit criteria were met.

---

## 4. Closure Workflow

For each debt item:

```text
1. Owner produces evidence package.
2. Owner opens a PR attaching evidence to the closure issue.
3. QA & Release Owner verifies evidence.
4. Project Manager records decision (CLOSE / DEFER / DOWNGRADE).
5. If CLOSE: update this register, close GitHub issue, record SHA + Run ID.
6. If DEFER: re-add to this register with new due date.
7. If DOWNGRADE: requires PM approval and explicit residual risk acceptance.
```

---

## 5. Summary Table

| Debt ID  | Title                                          | Blocking | Owner                    | Status                          |
|----------|------------------------------------------------|----------|--------------------------|---------------------------------|
| TD-07-001| OWASP Final Security Assessment                | YES      | Security Owner           | OPEN — BLOCKING FINAL CLOSURE   |
| TD-07-002| Production Backup and Restore Validation       | YES      | Infrastructure Owner     | OPEN — BLOCKING FINAL CLOSURE   |
| TD-07-003| Monitoring, Alerting and Incident Response     | YES      | Infrastructure Owner     | OPEN — BLOCKING FINAL CLOSURE   |
| TD-07-004| Commercial Infrastructure and Paid Plan        | YES      | Infrastructure Owner + PM| OPEN — BLOCKING FINAL CLOSURE   |
| TD-07-005| Fail-Closed Commercial Workflow Completion     | YES      | QA & Release Owner       | OPEN — BLOCKING FINAL CLOSURE   |
| TD-07-006| Email Delivery Evidence Hardening              | YES      | QA & Release Owner       | OPEN — BLOCKING FINAL CLOSURE   |
| TD-07-007| Independent Human Approvals                    | YES      | Project Manager          | OPEN — BLOCKING FINAL CLOSURE   |
| TD-07-008| Controlling Issues Evidence Reconciliation     | REVIEW   | Project Manager          | REVIEW REQUIRED                 |

---

## 6. Cross-References

* Stage 08 Executive Charter: `docs/stage-08/STAGE-08-EXECUTIVE-CHARTER.md`
* Stage 08 Master Backlog: `docs/stage-08/backlog/STAGE-08-MASTER-BACKLOG.md`
* Stage 08 Risk Register: `docs/stage-08/risk/STAGE-08-RISK-REGISTER.md`
* Stage 08 Acceptance Report: `docs/stage-08/acceptance/STAGE-08-ACCEPTANCE-REPORT.md`

---

## 7. Change Log

| Date       | Change                                                       | Author             |
|------------|--------------------------------------------------------------|--------------------|
| 2026-07-06 | Register created per SANAD-Z-STAGE-08-FULL-EXECUTION §2      | SANAD Platform (Z) |
