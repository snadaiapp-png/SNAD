# SHA Reference Decision — REM-P0-006 (effective)

| Field | Value |
|---|---|
| **Decision ID** | `SHA-REF-DECISION-REM-P0-006-001` |
| **Deciding authority** | Project Owner — Abdulrahman Sinan — GitHub `snadaiapp-png` (user id 294245491) |
| **Decided (UTC)** | 2026-10-23 |
| **Instrument executed** | Decision formula of `cert-phase-b-scope-decision-memo.md` §4, executed by the authority named above |
| **Recording** | Owner governance comment on Issue #784 (2026-10-23) + this owner-attributed commit |
| **Status** | **EFFECTIVE** (replaces the analytic, non-effective status of the Phase B memo) |

## 1. Decision

The reference binding for REM-P0-006 / Issue #784 is fixed as **three explicit references**:

1. **REF-PROD-LIVE = `1d80c573305778e1c0d4453a47b72c9bd41ffd23`** — the release actually
   running in production. Verified live on 2026-10-23 from the owner's Render API (deploy
   `dep-dbb65vu7bikc73d79bog`, `status: live`, image
   `ghcr.io/snadaiapp-png/snad-backend:1d80c573…`, digest `sha256:812eac76…`) and from the
   owner's Vercel API (project `snad-app`, latest `READY` production deployment
   `dpl_7daGsf4XFhNfZPNc276K95MZFPVR`, same commit SHA). Web and backend therefore run one
   and the same production release.

2. **REF-CODE-BASE = `0f460ee9e4eb6e7c03ed309d3ca33bfa2ebbe0a9`** — the current verified
   `main` head (officially merged and web-flow-signed squash of PR #1431; tree-identical to
   the PR head; cryptographically verified 2026-10-22).

3. **REF-CANDIDATE = head of `main` immediately after the merge of the unified-fix PR**
   (branch `fix/rem-p0-006-unified-trust-validation`, opening commit `28220469`, plus the
   governance records of 2026-10-23). This reference becomes the assessment reference of
   record when, and only when, the PR is merged through the protected review path.

## 2. Retirement of prior references

| Prior reference | Former role | Disposition | Reason |
|---|---|---|---|
| `f34f2dd71743e6361a49e86643944c089622bd4c` | `assessed_release.repository_sha` in the manifest (July assessment window) | **Retired as a live reference; preserved as a historical assessment record** | The July assessment ran on this SHA and its record must not be falsified; it no longer represents production (1261 commits behind) |
| `cb65421e2c7e9bf9330dd620259cd60e9ec30d1d` | Reference cited in the Issue #784 body (governance comment 2026-07-27) | **Retired as an assessment reference** | 1193 commits behind; its merge itself was executed by the conflicted assessor account (extends B9) |
| `a8d77d1512a632f721a54b8bd6d1ca5094ab6810` | October candidate (#1425) | **Retired — candidate period ended** | Superseded by REF-CODE-BASE |

**Genealogy basis:** all candidates are linear ancestors of the current `main` head — the
historical "conflict" is reference staleness, not divergent content (commit-genealogy proof
2026-10-22/23, `scripts/sha_genealogy.sh`). No cross-branch divergence exists.

## 3. Manifest handling (schema-disciplined)

`assessment-manifest.json` is **not modified** by this decision:

- `assessed_release.repository_sha` stays `f34f2dd7…` because that block truthfully
  describes the **July assessment**; overwriting it would falsify the historical record.
- No new fields are added without a schema decision (command §9 discipline).
- **Pin runbook (to be executed by the assessor engagement, not by this commit):** when the
  unified-fix PR merges and CI is green on the new head, a dedicated governance PR records a
  **new assessment window** with `repository_sha` = the new head and a fresh
  `deployment_id`, preserving the July block as history. Until then, the references of
  record are the ones in §1 of this document.

## 4. Scope decision (Phase B §3)

**Pre-release/candidate evaluation scope** is selected for the current phase: testing and
verification bind to REF-CODE-BASE / REF-CANDIDATE in non-production environments, with
production verified read-only (as done in this phase). A **production evaluation** of the
eventual next release remains mandatory before any closure and requires the standing NO
DEPLOY hold to be lifted by its own explicit owner decision at that time.

## 5. Effect on the 19-case coverage matrix

No case status changes with this decision. Case execution binds to REF-CANDIDATE after the
pin runbook completes; execution against retired references would be out of scope.
