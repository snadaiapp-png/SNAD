# 10 — G1 CERTIFICATION DECISION (Phase 7)

**Decision timestamp:** 2026-11-02 (evidence window 09:09Z→10:05Z; post-merge run `41415645715` still `in_progress` at record time — polling continues; if it turns terminal-PASS this document's checklist row 1 converts to PASS without any other change)
**Certified SHA:** `f95a30b64e75815d571e582e1b555c01bcd53abe`

## 7.1 The certification checklist — item-by-item, evidence-bound

| # | Condition | Status | Evidence |
|---|---|---|---|
| 1 | Final SHA verified | **PASS** | 01 §0.1 (API + clone; no drift) |
| 2 | Post-Merge terminal PASS | **PENDING→ (expected PASS)** | run `41415645715`@f95a30b6 in_progress (jobs A/B/E already success); predecessor `41415592079`@177e27c2 fully green incl. PostgreSQL Direct Maven suite (02) |
| 3 | Maven terminal PASS | **PENDING (same run, job C)** — predecessor fully green | 02 |
| 4 | PostgreSQL Direct PASS | **PENDING (same run, job C)** — predecessor fully green | 02 |
| 5 | Six-table RLS acceptance PASS | **PASS** | 03 — 57/57 MATRIX=GREEN, chain-only grants, FORCE RLS + policies `TO public`, restricted role proven |
| 6 | Runtime identity verified | **PASS (code + rehearsal level)** | 03/04 (`sanad` NOSUPERUSER/NOBYPASSRLS); production-live identity = 08 BLOCKED |
| 7 | Auth/User lifecycle PASS | **PASS** | 04 AUTH01–AUTH18 |
| 8 | Subscription lifecycle PASS | **PASS with finding F-01 (P2, fix-verified, follow-up PR required)** | 04 SUB01–SUB08 |
| 9 | Provisioning workers PASS | **PASS** | 04 WRK01–WRK12 |
| 10 | Flyway historical upgrade PASS | **PASS** | 05 Scenarios A/B3/C3 |
| 11 | Authorized backup restore PASS | **FAIL→BLOCKED** | 06 — no authorized backup delivered |
| 12 | Independent security review APPROVED | **BLOCKED** | 07 — owner self-approvals only; assessor framework PENDING; no coverage of f95a30b6 |
| 13 | Protected production verification PASS | **BLOCKED** | 08/09 — production read/change authorization absent; stop point honored |
| 14 | No unresolved P0/P1 certification blocker | **PASS for P0/P1** (F-01 is P2 and fix-verified; no P0/P1 open) | 03/04/05/07/08 |
| 15 | Evidence manifest complete | **PASS** | EVIDENCE-MANIFEST.md (12 documents + raw artifacts) |
| 16 | Authorized certification decision recorded | **RECORDING NOW = HOLD** | this document; formal issuance requires the official G1 certification workflow |

## 7.2 Official-issuance-path check (mandated)

The repository's certification issuance instrument today is the **owner-attested** class (`docs/security/independent-assurance/certification/`, self-described "issued by the Project Owner in his own authority") plus the `independent-assurance` framework whose assessor slots are all `PENDING` and whose `independence_status` is `PENDING_VERIFICATION`. There is **no workflow or authorized issuer that can currently produce an independent G1 certificate**. Per the command: this is recorded as an obstacle; **no Markdown file produced by the executor claims to be a production certificate, and none is issued.**

## 7.3 Decision

```text
G1_CERTIFICATION = HOLD

SOURCE_INTEGRATION:              PASS     (PRs #1458/#1459/#1460/#1461/#1463/#1464/#1465/#1466/#1467 merged; code at f95a30b6)
POST_MERGE_VERIFICATION:         BLOCKED  (exact-SHA run in_progress; predecessor green — converts on terminal)
SIX_TABLE_RLS_ACCEPTANCE:        PASS     (57/57 + chain-only grants)
AUTH_E2E:                        PASS (18/18)
SUBSCRIPTION_E2E:                PASS with P2 finding F-01 (fix-verified; follow-up PR required)
WORKERS_E2E:                     PASS (12/12)
FLYWAY_VERIFICATION:             PASS (A/B/C; no repair/baseline/outOfOrder)
BACKUP_RESTORE_CERTIFICATION:    BLOCKED  (no authorized backup — 06)
INDEPENDENT_SECURITY_REVIEW:     BLOCKED  (no independent reviewer record for f95a30b6 — 07)
PRODUCTION_VERIFICATION:         BLOCKED  (no authorized read channel — 08)
PROTECTED_PRODUCTION_CHANGE:     NOT_REQUESTED (draft CR only; stop point honored — 09)
G1_CERTIFICATION:                HOLD
ISSUE_1462:                      OPEN
PRODUCTION_RELEASE:              NO-GO
PAYMENTS:                        OFF
```

**Immutable state unchanged:** `P0_SECURITY_HOLD=ACTIVE`, `PRODUCTION_DEPLOYMENT=PROHIBITED`, `PRODUCTION_SQL_WRITE=PROHIBITED`, `AUTOMATIC_MERGE=PROHIBITED`, `SECURITY_GATE_BYPASS=PROHIBITED`. (Lifting `P0_SECURITY_HOLD` requires the formal security decision that depends on items 2–4/11–13; it is not the executor's call and is not made here.)

## 7.4 The three remaining keys to certification (in order)

1. **Terminal PASS of run `41415645715` on `f95a30b6`** (in flight; expected).
2. **Authorized backup restore rehearsal** (owner delivers backup + authorization — 06).
3. **Independent security review recorded on `f95a30b6`** (a named second-party reviewer; minimal scope defined in 07) — and only then the production gates (08→09) under explicit owner approval.
