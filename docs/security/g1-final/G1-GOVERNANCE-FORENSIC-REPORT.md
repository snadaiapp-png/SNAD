# G1-GOVERNANCE-FORENSIC-REPORT.md

**Scope:** Issue #1462 (the G1 reference gate) — how its acceptance state was managed, how PR #1458 was approved/merged, and which governance conditions enabled the drift. Findings are classified per the command: `VERIFIED / LIKELY / HYPOTHESIS / UNRESOLVED / DISPROVEN`. No accusation of intent is made or implied.

## 1. Issue #1462 acceptance-state history

| Question | Finding | Classification |
|---|---|---|
| Current checkbox state | 0 of 6 checked (verified live 2026-11-02). The user confirmed the six boxes were manually un-checked; per mandate, the un-checking is **not** treated as evidence of anything. | `VERIFIED` |
| Who/when checked the `[x]` marks previously | **Not provable from the API.** `GET /issues/1462/timeline` returns 21 events (comments, cross-references, references) but **zero `edited` events**; GitHub's REST API does not expose issue-body revision history. The window in which boxes were checked is bounded by comment evidence: 2026-11-01T00:20Z ("No checkbox is ticked — they stay open") and the user's manual un-check. | `UNRESOLVED` |
| Evidence available at the time boxes were checked | The comments published around the bounded window (00:20:04Z and 00:20:31Z) explicitly stated flags HOLD and that CI/PG-direct results were "GREEN on `b16f4449`" for targeted gates, while acceptance checkboxes remained unticked. At least one later comment (22:27Z) contains full phase evidence. Whether that evidence mapped 1:1 to the six boxes cannot be established retroactively. | `LIKELY` that boxes were ticked without a per-requirement evidence link (no evidence linkage exists in any recorded comment); actor and exact moment unproven. |
| Automation modifying acceptance state | All review events on #1458/#1465 come from two human collaborator accounts; the timeline shows no bot-authored edits; workflow runs observed were marker-gated non-releases (verified in the 2026-11-01T22:06Z comment and re-checked this run: ERP preview + R0C-12 skipped by design). | `DISPROVEN` (for this window) |
| Six checkboxes ticked without linked evidence | See above; combined with §2 conditions below this is the recorded governance defect. | `LIKELY` |

## 2. PR #1458 approval and merge mechanics

| Question | Finding | Classification |
|---|---|---|
| Was the approval on the merged SHA? | Yes — APPROVED on `1dbb1db9d5…` (the merge head) at 21:48:31Z; squash-merged 21:58:49Z. | `VERIFIED` |
| Was the approval a substantive security review? | The review body is **empty** (zero technical notes), reviewer is a collaborator account with no documented independence attestation. An empty-body approval cannot demonstrate coverage of the G1 requirement set. | `VERIFIED` (empty body); `LIKELY` (insufficient as G1 review) |
| Did the merge bypass a documented condition? | Branch protection allows merge with 1 approval + required checks; nothing mechanical was bypassed. However the documented G1 gate (Issue #1462 checkbox 5/6: independent review + certification) was **not enforced by branch protection** — it was advisory, not automatic. | `VERIFIED` (condition was advisory) |
| Protection settings that made this possible | `dismiss_stale_reviews=false` (approvals survive new pushes), `require_code_owner_reviews=false`, **no CODEOWNERS file**, `required_approving_review_count=1`, `enforce_admins=true` (admins cannot bypass — no bypass occurred). | `VERIFIED` |
| Stale-review risk for #1465 | The only review on #1465 (on `f5434274e3`) was **dismissed** by `snadaiapp-png` at 2026-11-01T22:35:32Z, 2 s after the test-pins push was referenced, with no dismissal message. No active approving review exists on #1465 now. With `dismiss_stale_reviews=false`, a future approval on an old SHA would remain valid while content changes — an acceptance-drift enabler. | `VERIFIED` |

## 3. Root-cause model (governance, not individuals)

1. Acceptance checkboxes on the G1 issue are editable by any collaborator and are not mechanically bound to evidence (no required issue-link, no protected fields).
2. Branch protection enforces code health, not G1 certification: nothing in the required-check set corresponds to "independent security review recorded" or "certification attached".
3. Empty-body approvals satisfy the only review requirement that exists.
4. CI success was (historically) treated as acceptance evidence even where a required check had not reached a terminal state (the `Maven Test Suite` run 41357909105 on `81b5585f` was stuck `in_progress` > 12 h while the PR carried a "CI green" narrative).

## 4. Remediation (implemented or proposed)

Implemented by this command in `docs/security/g1-governance-controls.md` + `.github/CODEOWNERS` (branch `docs/g1-final-evidence-20261102`): evidence-bound checkbox protocol, CODEOWNERS for migration/security paths, exact branch-protection settings diff (`dismiss_stale_reviews=true`, `require_code_owner_reviews=true`, required-check additions), SHA-pinned review validity rule, and a rule that SKIPPED/non-terminal checks are not success. Enforcement of protection changes requires an authorized administrator action (attempting it with the current token was not authorized; recorded as a manual step).
