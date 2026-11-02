# BASELINE-VERIFICATION.md

**Command:** SANAD-G1-P0-UNIFIED-2026-11-02 · **Prepared:** 2026-11-02 (UTC) · **Method:** live GitHub REST API + `git fetch` into an isolated clone. Historical references were treated as pointers only; every value below was re-queried.

## 1. Verified baseline

| Entity | Value at verification | Evidence |
|---|---|---|
| `main` HEAD | `12f92d7cfaa3bff45dee214029fa418b84080310` (= squash-merge of PR #1458, 2026-11-01T21:58:49Z by `snadaiapp-png`) | `GET /commits/12f92d7cf…`, `git fetch` |
| Issue #1462 | OPEN; six acceptance checkboxes UNCHECKED (0 `[x]` / 6 `[ ]`); created 2026-10-31T16:57:58Z | `GET /issues/1462` |
| PR #1465 | OPEN, base `main`, `mergeable_state=blocked` (required check not terminal); head advanced during this run | `GET /pulls/1465` |
| PR #1465 head (start of run) | `81b5585fdc3254523b69572ca0fd812a6063249e` (2 commits ahead of main, 0 behind, 6 changed files) | `GET /compare/main...head` |
| PR #1465 head (end of run) | `d0694a00f…` — this run pushed 2 remediation commits (see §3) | `git push` output |
| PR #1458 | MERGED (squash) → `12f92d7cf`; 1 APPROVED review by `abdulrhmansenan1985-creator` **on the merged head** `1dbb1db9d5…` at 2026-11-01T21:48:31Z, **empty review body** | `GET /pulls/1458`, `/pulls/1458/reviews` |
| PR #1460, #1463, #1464 | All MERGED (heads `60d369f2`, `b16f4449`, `911689df`) | `GET /pulls/{n}` |
| Required status checks (branch protection `main`) | `Build Next.js Web`, `provenance`, `CRM Integration Tests`, `Maven Test Suite`, `CRM Deployment Readiness`, `Verify 8 tables, 26 indexes, and tenant isolation` | `GET /branches/main/protection` |
| Review requirements | 1 approving review; **`dismiss_stale_reviews=false`**; **`require_code_owner_reviews=false`**; `require_last_push_approval=false`; `enforce_admins=true`; force-push/deletion disabled | same |
| CODEOWNERS | **Not present** on `main` (API 404) | `GET /contents/.github/CODEOWNERS` |
| Migrations on `main` | 220 chain files (head `V20261101_1`); PR #1465 adds `V20261102_1` and this run adds `V20261102_2` | worktree file counts |
| CI on main `12f92d7cf` | 25 success / 2 skipped / 1 none; `Maven Test Suite=success`, `PostgreSQL Acceptance Tests=success`, `Verify 8 tables…=success` | `GET /commits/{sha}/check-runs` |
| CI on PR #1465 `81b5585f` (superseded) | 47 success / 2 skipped / **`Maven Test Suite` stuck `in_progress` > 12 h** (run 41357909105, started 2026-11-01T22:37:13Z) — never reached terminal state | `GET /commits/{sha}/check-runs`, `GET /actions/runs/41357909105` |
| Revisions of reference block | PR #1465 head, PR #1458 merge SHA, PR state and checkbox count all matched the historical reference → baseline re-binding not required for main/head identity | this document |

## 2. Environment identity

- Test environment: isolated Linux sandbox clone (`/home/z/my-project/workspace/SNAD`), local PostgreSQL **17.6** (same major as the production Supabase instance inspected read-only on 2026-11-01: PostgreSQL 17.6, project `snad-prod-fresh`), Flyway CLI **11.7.2** (the project's Spring Boot 3.5.6 BOM version).
- No production credentials used; no production connection made in this run; the Supabase/Vercel/Render/Resend tokens pasted in chat were **refused** and must be rotated (they remain exposed).

## 3. Baseline changes created by this run (re-bound tests)

PR #1465 head advanced twice, both fast-forward, no force push:

1. `4a0cf08028cc59045353f82faa3fb9bfbef90593` — adds `V20261102_2__t2_batch2_runtime_grants.sql` + advances five test version pins `20261102.1 → 20261102.2` (six-table matrix finding, see SIX-TABLE-RLS-ACCEPTANCE-MATRIX.md).
2. `d0694a00f…` — scopes the `V20261102_2` append-only guard to the separate-owner model after CI reproduced a fixture-model conflict (see PR1465-SECURITY-REVIEW.md §5).

All runtime evidence in this package was re-executed at the tree of `d0694a00f` (checksum `237166129` for `V20261102_2`). CI results are bound per-SHA in G1-EXACT-SHA-TEST-EVIDENCE.md.
