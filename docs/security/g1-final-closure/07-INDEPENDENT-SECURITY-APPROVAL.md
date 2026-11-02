# 07 — INDEPENDENT SECURITY APPROVAL (Phase 5)

**Requirement:** an approval by a reviewer **independent of the remediation executor**, recorded on the certified scope, covering SHA `f95a30b64e75815d571e582e1b555c01bcd53abe`.
**Verdict: INDEPENDENT_SECURITY_REVIEW = BLOCKED (not satisfied for the certified scope).** The user's assertion that independent approvals are complete was checked against actual records rather than assumed — the records do not support it **for this scope and SHA**.

## Evidence audited (live, 2026-11-02)

### 1. PR reviews on the three G1 PRs (GitHub API, `pr{1465,1466,1467}-reviews.json`)

| PR | Reviewers | States |
|---|---|---|
| #1465 | `abdulrhmansenan1985-creator` | DISMISSED + APPROVED + APPROVED |
| #1466 | `abdulrhmansenan1985-creator` | APPROVED + APPROVED |
| #1467 | `abdulrhmansenan1985-creator` | APPROVED |

- Every approval originates from **one account**: `abdulrhmansenan1985-creator`.
- The project owner has formally declared (recorded in `docs/security/independent-assurance/certification/OWNER-PHASE-APPROVAL-2026-10-23.md`, identity statement §1) that `snadaiapp-png` and the owner are **the same person**: "I, Abdulrahman Sinan, the project owner (GitHub account `snadaiapp-png`)… confirming that the access tokens supplied… belong to my own official accounts". The remediation-fixing sessions in this engagement were likewise executed under the owner's own authority/tokens.
- Therefore the approvals are **owner self-approvals**. Under the governing rule ("لا يغلق بوابة أمنية بقرار منفذ الإصلاح وحده" / a security gate is not closed by the remediation executor alone; self-described independence does not count), these are valid *authorizations* but are **not an independent security review**.
- The DISMISSED review on #1465 is consistent with re-review after a force-free rebase/re-push during that PR's lifetime; it is not itself an adverse finding (dismissal reason fields were not populated by GitHub's UI action).

### 2. The formal independent-assessment framework in the repo (`docs/security/independent-assurance/`)

- `assessment-manifest.json`: `approvals.independent_assessor.decision = "PENDING"` (name empty), `approvals.project_owner.decision = "PENDING"`, `approvals.security_governance.decision = "PENDING"`, `assessor.independence_status = "PENDING_VERIFICATION"`, `closure_state = "NOT_READY"`, `commercial_go_live = "NOT_APPROVED"`.
- The `assessor.lead_assessor` is recorded as `abdulrhmansenan1985-creator` ("External independent individual assessor") — i.e., **the owner appointed himself**; the framework's own independence verification is still pending.
- `assessed_release.repository_sha = f34f2dd71743e6361a49e86643944c089622bd4c` (2026-07-26) — the only assessed release predates the entire G1 scope (six-table closure began with PR #1458 on 2026-11-01) and covers neither the current SHA nor this scope.

### 3. The only issued certificate (`certification/CERTIFICATION-REM-P0-006-PHASE-2026-10-23.md`)

- Self-described: "**phase certification issued by the Project Owner in his own authority**" (`Owner-Attested`), engagement `REM-P0-006` / Issue #784, references `REF-PROD-LIVE=1d80c573…` and `REF-CODE-BASE=0f460ee9…` (2026-10-23) — **predates and does not cover** the G1 six-table scope or `f95a30b6`.
- It is honest about what it is (owner-attested, internal) and does not claim independence. It therefore cannot satisfy this gate.

## Scope check per the command ("المراجعة على SHA سابق لا تغطي تلقائيًا تغييرات لاحقة")

Changes requiring review coverage at `f95a30b6`: `V20261102_1`/`V20261102_2` (PR #1465), the runtime-identity separation (`docker-compose.prod.yml`, `docker-compose.self-hosted.yml`, `docker/postgres-init/01-create-restricted-runtime-role.sh`, PR #1466), governance docs (PR #1467). **No review record by any second party covers any of these.**

## What would satisfy the gate (minimal, per the command's "تكميلية محددة" provision)

A targeted independent review of exactly: (a) the two new migrations' diff at `f95a30b6`, (b) the compose/init runtime-identity change, (c) the six-table acceptance matrix (03) and the E2E finding F-01 (04) — performed by a person who is not the owner/remediation executor, with their GitHub identity + the reviewed SHA recorded. The evidence package (01–06) is structured so this supplementary review can be executed in one sitting.

**Until such a record exists: this gate stays BLOCKED, `G1_CERTIFICATION=HOLD`, and Issue #1462 items 4/6 cannot be checked.**
