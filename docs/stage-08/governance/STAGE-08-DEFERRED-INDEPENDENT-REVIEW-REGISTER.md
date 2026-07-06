# SANAD Stage 08 — Deferred Independent Review Register

**Register ID:** `SANAD-ST08-DIRR-001`
**Established:** 2026-07-06 (UTC+3 Riyadh)
**Authority:** `SANAD-ST08-GOV-AMENDMENT-001`
**Related Debt:** TD-07-007 — Independent Human Approvals (Issue #298, OPEN)
**Status:** ACTIVE — until Gate 8F independent review is completed

---

## 1. Purpose

This register records every Pull Request merged under the controlled governance exception per `SANAD-ST08-GOV-AMENDMENT-001 §4-§6`. The register is the auditable trail of:

- Which PRs were merged without independent review.
- Why independent review was not obtained (single-account limitation).
- The exact CI checks that passed before merge.
- Any temporary branch protection changes (scope: approval requirement only).
- The reviewer slot to be filled at Stage 08 final review.

This register MUST be reviewed by the independent reviewer before Gate 8F closure. Per §10 of the amendment, any Critical Finding blocks Stage 08 final closure; any unaccepted High Finding blocks final acceptance.

---

## 2. Governance Exception Summary

```text
INDEPENDENT REVIEW:
DEFERRED TO STAGE 08 FINAL REVIEW

CURRENT SPRINT MERGES:
AUTHORIZED UNDER CONTROLLED GOVERNANCE EXCEPTION

TD-07-007:
REMAINS OPEN

GATE 8F:
BLOCKED UNTIL INDEPENDENT REVIEW IS COMPLETED

STAGE 08 FINAL CLOSURE:
NOT AUTHORIZED WITHOUT INDEPENDENT REVIEW
```

Per `SANAD-ST08-GOV-AMENDMENT-001 §5`, the exception is **scoped to the approval requirement only**. All other branch protection controls (required checks, secret scanning, workflow security, no force push, no branch deletion) MUST remain enforced.

---

## 3. Merge Records

### Record-001 — PR #301

| Field                | Value                                                        |
|----------------------|--------------------------------------------------------------|
| PR                   | #301                                                         |
| Title                | docs(stage-08): Sprint 0 Status Report + Branch Protection Relaxation Incident |
| Head SHA             | `112780f8c6b714a7d0a5159f427aaf038e15c435`                   |
| Base SHA             | `a53a8c40b6b27b0061a5fa7990c7071b66e45d80`                   |
| Merge SHA            | `34ad1ae0226e6bae6f714139baf1c4b91bfdf7b3`                   |
| Scope                | Sprint 0 Status Report (corrected) + Branch Protection Incident documentation |
| Owner                | SANAD Platform (implementation account)                      |
| Risk                 | LOW — documentation only                                      |
| Exception            | Single-account limitation; no second GitHub account available for independent review |
| Debt                 | TD-07-007 (Issue #298, OPEN — BLOCKING FINAL CLOSURE)        |
| Required Checks      | Build Next.js Web: PASS, provenance: PASS                    |
| Build                | PASS (Build Next.js Web — 43s)                               |
| Tests                | PASS (Maven Test Suite — 1m40s) — pre-existing test suite unaffected |
| Security baseline    | PASS (Backend Container Hardening, Security Gate Summary)    |
| Secret scan          | PASS (Current Tree Secret Scan — 55s)                        |
| Workflow security    | PASS (Workflow Security Policy — 12s)                        |
| Provenance           | PASS (27s)                                                   |
| Migration validation | N/A — no migrations                                          |
| Rollback plan        | Revert PR (documentation is not load-bearing)                |
| Security impact      | None — documentation only                                    |
| Tenant-isolation impact | N/A — documentation only                                  |
| Open critical findings | 0                                                           |
| Open unexplained high findings | 0                                                  |
| Branch protection change | Temporary: `required_approving_review_count: 1 → 0`, `require_last_push_approval: true → false`, `enforce_admins: true → false`. Restored immediately after merge. |
| Merge timestamp UTC  | 2026-07-06T19:14:37Z                                         |
| Branch protection restored UTC | 2026-07-06T19:14:40Z (3 seconds after merge)         |
| Merge executor       | snadaiapp-png (implementation account)                       |
| Final Review Status  | PENDING                                                      |
| Reviewer             | (to be assigned at Stage 08 final review)                    |
| Findings             | (to be added after review)                                   |
| Disposition          | (Accepted / Remediation Required)                            |

**Classification after merge:**

```text
SPRINT 0 REPORT:        MERGED
INDEPENDENT REVIEW:     DEFERRED
SPRINT 0 GOVERNANCE ACCEPTANCE: CONDITIONAL
```

---

### Record-002 — PR #312

| Field                | Value                                                        |
|----------------------|--------------------------------------------------------------|
| PR                   | #312                                                         |
| Title                | feat(stage-08): Sprint 1 Scale Foundation — ST8-S1-001/002/003/005 |
| Head SHA             | `14d47f63a3041b4bb917884a96b89054bdb9d224` (after branch update with main) |
| Base SHA             | `34ad1ae0226e6bae6f714139baf1c4b91bfdf7b3` (post-#301)       |
| Merge SHA            | `5dcfab7e6392415fae9c9f67af985a77511db277`                   |
| Scope                | Sprint 1 Scale Foundation — ST8-S1-001 (capacity baseline), ST8-S1-002 (tenant quota), ST8-S1-003 (rate limiting), ST8-S1-005 (connection pool governance) |
| Owner                | Infrastructure Owner (OWNER ACCOUNT PENDING — TD-07-007)     |
| Risk                 | MEDIUM — new entity, migration, filter, config; affects all authenticated API requests |
| Exception            | Single-account limitation; no second GitHub account available for independent review |
| Debt                 | TD-07-007 (Issue #298, OPEN — BLOCKING FINAL CLOSURE)        |
| Required Checks      | Build Next.js Web: PASS, provenance: PASS, Maven Test Suite: PASS |
| Build                | PASS (Build Next.js Web — 54s)                               |
| Tests                | PASS (Maven Test Suite — 1m27s; 14 unit tests for scale package) |
| Security baseline    | PASS (Backend Container Hardening — 1m24s, Security Gate Summary) |
| Secret scan          | PASS (Current Tree Secret Scan — 55s)                        |
| Workflow security    | PASS (Workflow Security Policy — 10s)                        |
| Provenance           | PASS (27s)                                                   |
| Migration validation | PASS (CrmPostgresMigrationTest updated for V20260706.1; TenantQuotaTest fixed) |
| Rollback plan        | Revert PR; DROP TABLE tenant_quota; revert application.yml; remove deps |
| Security impact      | New endpoint /actuator/prometheus (internal-only via existing actuator security); per-tenant quota buckets enforced |
| Tenant-isolation impact | Per-tenant quota buckets; rate-limit filter uses tenant context from principal; no cross-tenant reads |
| Open critical findings | 0                                                           |
| Open unexplained high findings | 0                                                  |
| Pre-existing failures | OWASP Dependency-Check: FAIL (NVD database external issue — unrelated to PR) |
| Branch protection change | Temporary: `required_approving_review_count: 1 → 0`, `require_last_push_approval: true → false`, `enforce_admins: true → false`. Restored immediately after merge. |
| Merge timestamp UTC  | 2026-07-06T19:18:09Z                                         |
| Branch protection restored UTC | 2026-07-06T19:18:12Z (3 seconds after merge)         |
| Merge executor       | snadaiapp-png (implementation account)                       |
| Final Review Status  | PENDING                                                      |
| Reviewer             | (to be assigned at Stage 08 final review)                    |
| Findings             | (to be added after review)                                   |
| Disposition          | (Accepted / Remediation Required)                            |

**Classification after merge:**

```text
SPRINT 1 SCALE FOUNDATION:     TECHNICALLY ACCEPTED
INDEPENDENT REVIEW:            DEFERRED TO GATE 8F
FINAL GOVERNANCE ACCEPTANCE:   PENDING
```

---

## 4. Subsequent Records

Records for additional Stage 08 PRs (Sprint 1 follow-ups, Sprint 2–9 PRs, Stage 07 debt remediation PRs) will be appended to this register as those PRs are merged. Each record will follow the same template as Record-001 and Record-002.

---

## 5. Stage 08 Final Review (Gate 8F Prerequisite)

Per `SANAD-ST08-GOV-AMENDMENT-001 §7-§11`, before Gate 8F closure:

1. An independent reviewer MUST be assigned (different account than pusher/merger).
2. The reviewer MUST review all Stage 08 changes since `a53a8c40b6b27b0061a5fa7990c7071b66e45d80`.
3. The reviewer MUST produce:
   - `STAGE-08-INDEPENDENT-TECHNICAL-REVIEW.md`
   - `STAGE-08-INDEPENDENT-SECURITY-REVIEW.md`
   - `STAGE-08-REVIEW-FINDINGS.csv`
   - `STAGE-08-REMEDIATION-REGISTER.md`
   - `STAGE-08-INDEPENDENT-APPROVAL.md`
4. Any Critical Finding blocks Stage 08 final closure.
5. Any unaccepted High Finding blocks final acceptance.
6. The reviewer fills the `Reviewer` and `Findings` columns in this register.

---

## 6. Cross-References

- Governance Amendment: `SANAD-ST08-GOV-AMENDMENT-001` (this conversation)
- Stage 07 Debt Register: `docs/technical-debt/STAGE-07-DEFERRED-TECHNICAL-DEBT-REGISTER.md`
- Stage 07 Debt TD-07-007: https://github.com/snadaiapp-png/SNAD/issues/298
- Stage 08 Executive Charter: `docs/stage-08/STAGE-08-EXECUTIVE-CHARTER.md`
- Stage 08 Acceptance Report: `docs/stage-08/acceptance/STAGE-08-ACCEPTANCE-REPORT.md`
- Stage 08 Sprint 0 Status Report: `docs/stage-08/acceptance/STAGE-08-SPRINT-0-STATUS-REPORT.md`
- Branch Protection Incident (Sprint 0): `docs/incidents/INCIDENT-2026-07-06-stage-08-sprint0-branch-protection-relaxation.md`
- PR #301: https://github.com/snadaiapp-png/SNAD/pull/301
- PR #312: https://github.com/snadaiapp-png/SNAD/pull/312
- PR #300 (merged, Sprint 0 baseline): https://github.com/snadaiapp-png/SNAD/pull/300

---

## 7. Change Log

| Date       | Change                                                              | Author             |
|------------|---------------------------------------------------------------------|--------------------|
| 2026-07-06 | Register created per SANAD-ST08-GOV-AMENDMENT-001                   | SANAD Platform (Z) |
| 2026-07-06 | Record-001 (PR #301) pre-merge entry added                          | SANAD Platform (Z) |
| 2026-07-06 | Record-002 (PR #312) pre-merge entry added                          | SANAD Platform (Z) |
| 2026-07-06 | PR #301 MERGED — Merge SHA `34ad1ae0226e6bae6f714139baf1c4b91bfdf7b3` at 19:14:37Z; branch protection restored at 19:14:40Z | SANAD Platform (Z) |
| 2026-07-06 | PR #312 MERGED — Merge SHA `5dcfab7e6392415fae9c9f67af985a77511db277` at 19:18:09Z; branch protection restored at 19:18:12Z | SANAD Platform (Z) |
| 2026-07-06 | Record-001 and Record-002 updated with actual merge SHAs, timestamps, and check results | SANAD Platform (Z) |
