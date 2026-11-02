# G1-CERTIFICATION-READINESS.md

**Flags (unchanged by this command):** `P0_SECURITY_HOLD=ACTIVE` · `G1_CERTIFICATION=HOLD` · `PAYMENTS=OFF` · `PRODUCTION_RELEASE=NO-GO` · `PRODUCTION_DEPLOYMENT=PROHIBITED` · `PRODUCTION_SQL_WRITE=PROHIBITED` · `AUTOMATIC_MERGE=PROHIBITED` · `SECURITY_GATE_BYPASS=PROHIBITED`

## Decisions requested by the command

```text
SOURCE_REMEDIATION:        PASS
PR1465_MERGE_READINESS:    HOLD
G1_CERTIFICATION:          READY_FOR_AUTHORIZED_CERTIFICATION? → NO — HOLD
PRODUCTION_RELEASE:        NO-GO
```

### SOURCE_REMEDIATION = PASS (conditional scope)
The ACL defect class (restricted runtime role with zero table privileges on FORCE-RLS'd identity/operation tables) was reproduced RED on the real migration chain, fixed forward-only (`V20261102_1` from PR #1465; `V20261102_2` added by this run), and verified GREEN by runtime evidence at the exact branch tree: fresh 250/250 + validate 0, six-table matrix 57/57, historical upgrade and out-of-order catch-up data-preserving with identical checksums. The self-hosted RLS-bypass path (compose superuser) is remediated by a separate PR (#1466) with init-script regression evidence. Scope condition: this PASS certifies the **source remediation**, not a merge, not a certification, not production.

### PR1465_MERGE_READINESS = HOLD
1. CI on the final head `8e93beaec` had **not reached terminal state** at report time (stop condition "required check without terminal result").
2. **No active independent review** exists (the only review was dismissed; no new review recorded).
3. Merge itself remains prohibited by this command regardless of readiness.

### G1_CERTIFICATION = HOLD (BLOCKED inputs)
1. `INDEPENDENT_REVIEW=BLOCKED` — the executor of the remediation (this agent) cannot also be the independent reviewer; no qualified independent reviewer was available in this environment. A formal review request has been posted on PR #1465 and Issue #1462 with SHA-pinned pointers.
2. `HISTORICAL_BACKUP_REHEARSAL=BLOCKED` — no authorized production backup was provided (Scenario D); synthetic databases were NOT substituted.
3. Required checks on the final SHA not yet terminal (above).
4. Production runtime re-verification (checkbox 5: live catalog, G1 certificate, login/refresh/subscription lifecycle, background workers) requires an authorized production operation — prohibited in this command.

### PRODUCTION_RELEASE = NO-GO
Unchanged; release requires the blocked inputs above plus a separate explicit authorization.

## What WOULD unlock each gate (checklist)

| Gate | Unlocked by |
|---|---|
| Merge readiness | terminal required checks on `8e93beaec` (or later) + recorded independent review on that SHA + explicit authorization to merge |
| G1 certification | authorized backup-restore rehearsal + recorded independent review + production read-only re-verification of catalog/roles/ledger + G1 certificate workflow executed by an authorized operator |
| Production release | separate decision after certification; out-of-order catch-up of pre-#1458 databases during documented maintenance windows; compose identity split (#1466) applied for any self-hosted path |

## Residual risks (open, non-blocking for source, blocking for certification)

| Risk | Class | Notes |
|---|---|---|
| Independent review missing | BLOCKED | requested on PR #1465 / Issue #1462 |
| Authorized backup rehearsal missing | BLOCKED | Scenario D |
| `login_tenant_index` plaintext identifiers | accepted residual (documented in V20261031_1) | hashing follow-up recommended |
| `sanad` can forge pre-auth index rows | accepted residual | downstream reads fail-closed under FORCE RLS (proven `PA04`) |
| Five exposed chat tokens | CRITICAL credential hygiene | all five must be rotated/revoked; only the GitHub PAT was used, transiently, and never written to reports/logs/repo |
| Stuck-runner anomaly (run 41357909105) | operational | superseded; watch for recurrence |
| Pre-#1458 databases (incl. prod at `20261021.1`) | operational | Scenario B/C evidence covers the upgrade path; execution needs authorized windows |

## Independence statement

Every role in this command (architect, DB security engineer, red team, QA/certification auditor) was executed by the same agent. Per the non-waivable independence requirement, the reviewer role is **not** claimed to be satisfied; the certification stays BLOCKED on a genuinely independent review of the exact final SHA.
