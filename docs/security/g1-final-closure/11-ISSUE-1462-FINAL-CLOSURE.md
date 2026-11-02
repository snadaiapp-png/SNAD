# 11 — ISSUE #1462 FINAL CLOSURE DECISION (Phase 8)

**ISSUE_1462 = OPEN (remains open; no checkbox was modified; no closure claim is made).**

The six original acceptance gates were mapped against evidence produced **in this command** at the certified SHA `f95a30b64e75815d571e582e1b555c01bcd53abe`:

| # | Original gate | Closure criterion (command §8) | Status at f95a30b6 | Evidence |
|---|---|---|---|---|
| 1 | Record terminal Maven and Post-Merge outcomes on exact SHA, attach Surefire and PostgreSQL Direct artifacts | Terminal PASS on the certified SHA + artifacts | **IN PROGRESS — expected PASS** | post-merge run `41415645715` (in flight; jobs A/B/E green already); predecessor `41415592079` fully green incl. PostgreSQL Direct Maven suite; artifacts attach on terminal (02). *This gate alone, once terminal, is the only remaining CI-side input.* |
| 2 | Review every T2-deferred table (six-table scope, correct threat model, no blind FORCE RLS) | Full matrix with security acceptance | **SATISFIED** | 03 — 57/57 GREEN, real table names verified, pre-auth plane designed deliberately (not FORCE-RLS'd), append-only ledger enforced, chain-only grants proven |
| 3 | Verify runtime DB role NOBYPASSRLS/non-super; ENABLE/FORCE, CRUD, WITH CHECK, missing GUC, Tenant A/B on isolated PostgreSQL | Proof of privileges, isolation, correct denial | **SATISFIED** | 03 (matrix incl. role contract, FORCE state, 42501/23514/empty/missing/invalid GUC tests) + 04 (lifecycle E2E) + 05 (chain provenance) |
| 4 | Independently review migrations, owners, SECURITY DEFINER search_path/ACLs; review fixture-limited cases | Independent approval covering the certified version | **NOT SATISFIED — BLOCKED** | 07 — all approvals are owner self-approvals; independent-assessor slots PENDING; nothing covers f95a30b6 |
| 5 | Only after review & approval: protected deployment + production Flyway-version check; live catalog & G1 certificate, user creation, login, session refresh, subscription lifecycle, background workers | Authorized, successful production verification | **NOT SATISFIED — BLOCKED** | 08 (no authorized read channel; probe set ready) + 09 (change request drafted; stop point honored; Y2 refusal correct) |
| 6 | Attach approved G1 certification evidence and final release decision | Formal certificate + recorded decision | **NOT SATISFIED — HOLD** | 10 — no independent issuance authority exists; certification correctly held |

## Why the Issue is not closed and no `[x]` was touched

- Per the command: `[x]` is permitted **only after the item is fully proven**, and closure only when **all six** gates are satisfied. Items 4/5/6 are blocked on authorization/artifacts that only the owner can supply (independent reviewer, backup, production read/change grants).
- The six checkboxes in the Issue body were left **exactly as found** (all `- [ ]`, verified live before and after; no edit was made to the issue by this execution). Nothing in this package should be read as claiming any checkbox state change.
- Closing the issue now would convert three unproven gates into apparent success — precisely the anti-pattern this gate exists to prevent.

## What remains for closure (owner action list, minimal)

1. Wait for run `41415645715` terminal PASS (in flight) → attach Surefire + PostgreSQL Direct artifacts → item 1 provable.
2. Deliver an authorized production backup via a secure channel → 06 converts to PASS/FAIL with measured numbers.
3. Appoint an actually independent reviewer for the defined minimal scope (07) → item 4 provable.
4. Then, and only then: production read verification (08) → owner-approved change request (09) → protected production verification → formal G1 issuance through an authorized (independent) issuer → items 5/6 → closure.

## Governance notes for the closure record

- **G-02:** CODEOWNERS exists but `require_code_owner_reviews=false` on `main` — enable it so security-path approvals are structurally attributable (already proposed in `docs/security/g1-governance-controls.md` C3).
- **G-03:** the only certificate instrument is owner-attested; the independent-assessor framework has been `PENDING` since July. Until an independent issuer exists, every "approved" state is self-attested and the G1 gate must stay held — this is the systemic reason item 4/6 remain open despite genuine technical progress.
- **F-01 (P2):** `tenant_subscriptions` runtime ACL gap under the split identity topology (fix contract verified; recommend follow-up PR `V20261102_3`-class with the same precondition/exit-guard pattern, after this cycle).
