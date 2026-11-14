# G1 Governance Controls — preventing recurrence

**Target:** the four failure modes behind the G1 governance drift (Issue #1462 forensic report). Implemented here as repository controls where possible; the branch-protection diff requires an authorized administrator.

## Policy scope (2026-11-14 proposed revision)

The new continuous-security model removes mandatory independent *certification paperwork*, not the technical G1 closure/evidence or actual configured GitHub branch protections. Where an older issue checkbox still says independent review, it remains unchecked until formally revised through approved governance; do not tick it merely because reviewer #1529 was cancelled. [Canonical policy](../governance/SANAD-CONTINUOUS-SECURITY-RELEASE-GOVERNANCE-2026-11-14.md).

## C1 — Acceptance checkboxes are not evidence (Issue-body protocol)

1. Each of the six G1 checkboxes may only be ticked **after** a comment on Issue #1462 that contains: the exact SHA tested, the CI run URL(s), the artifact link(s), and a PASS/FAIL per requirement.
2. The tick comment must quote the checkbox text verbatim.
3. Unchecking is always allowed; checking requires the comment (this command restored the unchecked state and did not tick anything).
4. Recommended mechanical control: a scheduled workflow that comments a diff of checkbox-state changes with the last evidence comment before each change (informational, not blocking — GitHub does not allow required issue rules).

## C2 — CI success is not a certification

1. `G1_CERTIFICATION` may only move off HOLD via the certification checklist in `G1-CERTIFICATION-READINESS.md` — green CI is necessary, never sufficient.
2. A required check that is `in_progress`/`skipped`/`stale` is treated as NOT PASSED in all evidence documents.
3. No evidence document may combine results from different SHAs into one PASS.

## C3 — Security PRs cannot merge with unsatisfied mandatory conditions

Branch-protection settings to apply on `main` (administrator action):

| Setting | Current | Required |
|---|---|---|
| `dismiss_stale_reviews` | `false` | **`true`** |
| `require_code_owner_reviews` | `false` | **`true`** |
| `required_approving_review_count` | 1 | keep 1 (CODEOWNERS now scopes it) |
| Required checks | 6 contexts | keep + consider `Security Gate Summary`, `Backup Restore Validation` |
| `enforce_admins` | `true` | keep `true` |

## C4 — Tests must not grant privileges the migrations do not

1. Any fixture that grants runtime roles privileges out-of-band must mirror the migration chain exactly (the `V20261102_1` harness parity rule); owner-as-`sanad` fixture databases must be explicitly labelled as such and must not be cited as evidence for separate-owner deployments.
2. In-migration exit guards assert the ACL surface (pattern of `V20261102_1`/`V20261102_2`); fixture-only grants cannot mask a missing migration.

## C5 — Reviews bind to a SHA

1. A review approval is only valid for the exact head SHA it was submitted on; any push invalidates it (this is what `dismiss_stale_reviews=true` enforces mechanically).
2. Security-sensitive changes require an evidence-backed, SHA-bound technical assessment identifying reviewer/owner, findings, test scope and limitations. A separately **independent** G1 reviewer or certificate is **not** the default internal prerequisite under the [continuous security governance](../governance/SANAD-CONTINUOUS-SECURITY-RELEASE-GOVERNANCE-2026-11-14.md). An empty-body PR approval satisfies only GitHub's configured code-review workflow, not any unexecuted security test or production GO. Retain any independent/external review requirement arising from documented high-risk circumstances, regulations or contracts.

## C6 — Certification binds to a tested version

`G1_CERTIFICATION` must reference one exact SHA; certification claims for any other version are void.

## C7 — Restore and review prerequisites

1. An authorized backup-restore rehearsal (`HISTORICAL_BACKUP_REHEARSAL`) is a hard prerequisite for certification.
2. Out-of-order catch-ups are per-database, window-bound, and reverted immediately; `outOfOrder` is never committed as a global default.

## CODEOWNERS

`.github/CODEOWNERS` added with this package routes `db/migration/**`, `tests/security/**`, `docker/postgres-init/**`, and `docs/security/**` changes to the repository owners for required review (once `require_code_owner_reviews=true` is applied).
