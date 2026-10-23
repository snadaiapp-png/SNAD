# Independent Assessor Handoff Addendum — 2026-10-23

**Supplements:** `ASSESSOR-HANDOFF.md` (2026-07-27), which remains in force for the parts it
does not contradict.
**Audience:** whoever executes the remaining independent assessment work — ideally a
genuinely external assessor (different natural person and organization from the Project
Owner), per `certification/B9-ROLE-CONFLICT-RESOLUTION-2026-10-23.md`.
**Status of this addendum:** informational handoff; it changes no statuses by itself.

## 1. Current engagement state (as of this addendum)

| Item | State |
|---|---|
| Assessment reference (pinned, documentation layer) | `eb52a57b463a0c3da78e4af55ede89c1c7068b8f` — head of `main` after the squash-merge of PR #1435 (merged 2026-10-23T02:07:10Z, merge commit signed by GitHub). The manifest `assessed_release` block remains verbatim July (`f34f2dd7`) because the evidence-reconciliation control binds it to the July appointment evidence chain — see §5 |
| Reference binding of record | `certification/SHA-REFERENCE-DECISION-2026-10-23.md`: REF-PROD-LIVE `1d80c573` (live production web+backend) / REF-CODE-BASE `0f460ee9` / REF-CANDIDATE `eb52a57b` (pinned at the documentation layer; manifest re-pin deferred per §5) |
| Assessment scope for this window | **Candidate/pre-release** (non-production verification environments; production read-only) — a production evaluation of a deployed release remains a separate, later requirement |
| Trust-policy interim evaluation | 3/5 PASS (`OIDC-IDENTITY`, `ENVIRONMENT-PROTECTION`, `RELEASE-SHA-AUTH`); 2/5 FAIL (`GIT-SIGNATURES`, `ASSESSOR-TRUST`) — see `CERTIFICATION-REM-P0-006-PHASE-2026-10-23.md` §3 |
| Coverage matrix | 0/19 `COMPLETED` — unchanged; execution against the pinned SHA is the assessor's work |
| Three closure approvals | all `PENDING` |
| `closure_state` | `NOT_READY` |
| Assessor independence | `PENDING_VERIFICATION`; no attestation submitted; the named account (`abdulrhmansenan1985-creator`) performed merge/release-operator actions inside the assessed range and cannot truthfully attest clause 1 of the appointment at this time |
| Owner-attested artifacts | Phase approval, phase certification (owner-attested, explicitly NOT independent), B9 resolution, risk acceptances `RISK-REM-P0-006-001..004` — all in `certification/` and the risk register |
| PR #1433 | build-provenance attestation workflow — audit failure remediated via main's dependency update (2453d7a7) merged into the branch; pending green CI + required review |

## 2. What the assessor must do (binding sequence)

1. **Accept or challenge the reference.** Verify `eb52a57b` is the intended assessment
   target (ancestry, signed merge, CI green on the head). Any challenge goes to Issue #784
   before work starts.
2. **Submit the independence position** required by `APPOINTMENT-REM-P0-006-2026-07-27.md`.
   For an external assessor this is a normal attestation. For the named account, clause 1
   is currently unassertable (see B9 resolution §2.2) — dual-control co-recording with the
   owner account applies until resolved.
3. **Execute the 19 coverage cases** in `TEST-COVERAGE-MATRIX.json` against the pinned SHA,
   in non-production verification environments (candidate scope), with immutable evidence
   per case. Cases executed against any other SHA are out of scope.
4. **Record results** only through the governed paths (matrix updates + evidence ids via a
   governance PR; no direct main pushes — branch protection enforces review).
5. **Retest remediations** per `RETEST-AND-CLOSURE.md` and produce the findings-register
   reconciliation.
6. **Request the three approvals** after assessment completion; the closure workflow then
   runs in the `rem-p0-006-closure` environment (required reviewers: both accounts;
   `can_admins_bypass=false`).

## 3. Facts the assessor can rely on (already verified, with evidence)

- Main-branch protection verified live 2026-10-23 (legacy HTTP 200: `enforce_admins=true`,
  6 required checks, 1 approving review, force-push/deletion denied; ruleset `min`
  17903112 active) — gap: `required_signatures=false` (accepted risk RISK-REM-P0-006-001).
- Environment protection verified (corrects the Phase A misreading).
- Supply chain: live production deploy `dep-dbb65vu7` = `ghcr.io/snadaiapp-png/snad-backend:1d80c573…`
  = index `sha256:2a887ec7…` = child `sha256:812eac76…`, verified from three independent
  sources (GHCR, owner dashboard read, owner Render API). Vercel web production READY on
  the same commit. No newer live deploy.
- Fail-closed behavior observed: protected closure path never executed; post-merge
  deployment records ran empty lifecycle jobs with substantive steps skipped.
- Local verification evidence: verifier suite 58/58; `tests/ci` 355 pass / 5 pre-existing
  failures on clean base (zero regression); RLS runtime-role verification 23/23.
- Next.js production dependency audit: failing condition remediated 2026-10-23 (main
  `2453d7a7` + PR #1435 lockfile alignment); the audit check is green on the merged head.

## 4. Explicit boundaries for this addendum

- No coverage-case status was upgraded by the owner or by automation; the matrix remains
  0/19 as a matter of record integrity.
- The 2026-10-23 phase certification is owner-attested and must not be treated as
  independent assurance by any successor work.
- All standing holds (P0 HOLD, G1 HOLD, NO MERGE without review, NO DEPLOY, PAYMENTS OFF)
  and `commercial_go_live=NOT_APPROVED` remain until their own governed changes.

## 5. Why the manifest keeps the July window (reconciliation control, verified working)

An initial attempt to re-point `assessed_release.repository_sha` to `eb52a57b` in this PR
was **rejected by the repository's own evidence-reconciliation validator**
(`scripts/ci/validate_independent_security_assurance.py`: `evidence release SHA mismatch:
EV-APPOINTMENT`). The control requires every record in the evidence index to carry
`assessed_release_sha` equal to the manifest's release SHA; all eight July records
(`EV-APPOINTMENT`, `EV-ROOT-INTEGRITY`, `EV-PEN-AUTOMATED`, …) are bound to `f34f2dd7`,
which is what the July appointment actually assessed. Re-pointing the manifest without
re-basing the evidence chain would have forged that binding.

**Consequence (the corrected pin model):**

1. **Documentation layer (effective now):** the reference of record for ongoing governance
   is the three-reference binding in `certification/SHA-REFERENCE-DECISION-2026-10-23.md`
   — REF-CANDIDATE = `eb52a57b` (materialized by the #1435 merge). The owner's decision
   authority is recorded there and on Issue #784.
2. **Manifest layer (deferred to the assessor engagement):** `assessed_release` is
   re-pointed to a new SHA only when a new assessment window opens **with its own evidence
   records** (new appointment/scope evidence or an explicit amendment, plus per-case
   evidence) whose `assessed_release_sha` values match — i.e., together with step 2–3 of
   §2, executed by the assessor through a governance PR.

The failed-then-corrected attempt is itself recorded as positive evidence: the
reconciliation control enforced cross-file consistency fail-closed on a governance change,
exactly as designed.
