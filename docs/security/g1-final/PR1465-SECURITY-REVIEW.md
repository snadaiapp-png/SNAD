# PR1465-SECURITY-REVIEW.md

**Object:** PR #1465 `fix/d6-auth-token-runtime-acl-20261102` — commits `f5434274e3` (V20261102_1), `81b5585fd` (test pins), `4a0cf0802` (V20261102_2 + pins), `d0694a00f` (guard scoping). **Reviewed tree:** `d0694a00f…`. **Reviewer role:** Principal Software Architect / PostgreSQL RLS engineer acting inside this command (the fixer and this reviewer are the same executor — independence is NOT claimed; INDEPENDENT_REVIEW is recorded BLOCKED in G1-CERTIFICATION-READINESS.md).

## 1. Original gap — reproduced before the fix

- Rehearsal at the pre-`V20261102_1` state: as `sanad`, `SELECT … FROM refresh_tokens` → `permission denied for table refresh_tokens` (SQLSTATE 42501). The 248-migration chain contained **zero** `GRANT` to `sanad` on `refresh_tokens`/`password_reset_tokens` (`information_schema.role_table_grants` probe: 0 rows), while the chain itself demands the restricted role as the runtime identity and its CI contract harness granted the surface out-of-band (harness lines 11-12), masking the gap.
- Second gap found by this command's six-table matrix: same class on `provisioning_jobs`/`provisioning_job_steps`/`subscription_commands` (`V20261031_1` enforced RLS, granted nothing). Fixed by `V20261102_2` in this PR (see SIX-TABLE-RLS-ACCEPTANCE-MATRIX.md).

## 2. V20261102_1 review — auth-token runtime ACL

| Check | Verdict |
|---|---|
| Grants limited to the runtime role `sanad` | PASS — `GRANT USAGE ON SCHEMA public`, `GRANT SELECT,INSERT,UPDATE,DELETE` on the two token tables to `sanad` only; mirrors the harness contract |
| No BYPASSRLS / no SUPERUSER | PASS — precondition requires `NOT rolsuper AND NOT rolbypassrls`; no role is created or altered |
| No RLS/policy weakening | PASS — no REVOKE, no `CREATE POLICY`, no `ALTER TABLE … ROW LEVEL SECURITY`; exit guard re-asserts `relrowsecurity AND relforcerowsecurity` on both tables |
| No out-of-band grants outside the Flyway chain | PASS — everything is in the migration file; the harness out-of-band grant becomes redundant (kept for contract parity) |
| Prior migrations + checksums preserved | PASS — forward-only; `V20261028_2=304457615`, `V20261101_1=−144985054` reproduce identically across fresh/upgrade/out-of-order databases |
| Works fresh + historical upgrade + out-of-order | PASS — scenarios A/B/C in FLYWAY-HISTORICAL-UPGRADE-EVIDENCE.md |
| No cross-tenant data path introduced | PASS — FORCE RLS still binds every `sanad` operation; 57/57 matrix incl. cross-tenant negative probes |
| refresh / reset / cleanup / concurrency intact | PASS — revoked/expired non-resolvable, index sync verified, concurrent A/B sessions isolated |

## 3. V20261102_2 review — T2 batch 2 runtime ACL (added by this run)

Least-privilege justification (call-site audited): `provisioning_jobs` SELECT/INSERT/UPDATE; `provisioning_job_steps` SELECT/INSERT/UPDATE; `subscription_commands` **SELECT/INSERT only** (append-only ledger; zero UPDATE/DELETE call sites). Preconditions + exit guard; separate-owner append-only assertion scoped to `current_user <> 'sanad'` after CI (runs 41364348714 / 41364348777) demonstrated the owner-as-`sanad` fixture model — six gates failed with `T2_BATCH2_RUNTIME_GRANTS_VERIFY` before scoping; root-caused to the guard, fixed in `d0694a00f`, CI re-run on the new head. The in-place edit of the unmerged PR migration follows the documented pre-merge-edit precedent; no persistent environment ever applied the earlier checksum.

## 4. Test file changes

Five test files change **version pins only** (`20261101.1→20261102.1→20261102.2`), one ledger list gains two documented entries, and `CrmPostgresMigrationTest` intermediate/completion paths add the two versions. No assertion weakening found (diff read in full).

## 5. Defects raised and their state

| Defect | Severity | State |
|---|---|---|
| Token tables missing runtime ACL (pre-#1465) | P0 (availability for the restricted identity; fail-closed) | FIXED by `f5434274e3`, verified live |
| T2 batch 2 tables missing runtime ACL | P0 (same class) | FIXED by `4a0cf0802` + `d0694a00f`, verified live (57/57) |
| Guard fixture-model conflict (self-inflicted during this run) | blocker on 6 CI gates | FIXED by `d0694a00f`; CI on the new head is the arbiter |

## 6. Merge-readiness inputs

- Required-review state: **no active approval** (the single review was dismissed 2026-11-01T22:35:32Z).
- Required checks on the final head: pending at report time (see G1-EXACT-SHA-TEST-EVIDENCE.md).
- Command prohibition on automatic merge remains in force.
