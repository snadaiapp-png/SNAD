# Gate evidence remediation — 2026-10-31
Base source: 46278fe24502325dc81d4894910c42f50bff0fdf. Work branch: forensic/full-audit-20261031.

Implemented:
- RPC probes verify selected role, function existence, revoked EXECUTE, and SQLSTATE 42501. Setup failures are BLOCKED, unrelated exceptions ERROR, and granted EXECUTE FAIL (including trigger-context errors).
- Both RLS runners use a shared fail-closed summary. Empty/incomplete/duplicate table coverage cannot PASS; a prior failure cannot be overwritten by a duplicate successful table result.
- Per-table write/missing/empty/malformed context checks preserve SQLSTATE and reject unrelated errors as isolation proof. Rebind B after a write probe may have rolled back its SET LOCAL.
- CI and manual Gate G run the evidence-classifier regression tests. Gate G retains manual dispatch and exact SHA binding.
- Replaced stale literal-source assertions with execution of the actual shell capability loop and jq subscription selectors. Every missing capability is rejected; cross-tenant/inactive subscription selection remains excluded.
- Performance CWD fixtures now include a real referenced nonempty JS chunk; added missing-chunk negative test. Production budget limits unchanged.

Verification:
- New oracle RED: 5 tests, 8 failing subcases before remediation (raw log retained locally).
- Final Python regression: `python3 -m pytest tests/ci tests/security/test_rls_evidence.py -q`: 402 passed, 55 subtests passed; exit 0.
- Before fixture/contract fixes the same broad selection had 5 failures, 396 passes. Two stale-source failures reproduced on extracted baseline; an earlier broad extracted-baseline attempt lacked docs and was not a valid full baseline comparison.
- Python syntax compilation, CI YAML parse, git diff --check: exit 0.
- Java target test attempts: initial proxy/dependency failure; proxy retry downloaded dependencies; later process exited 1 without terminal test summary during compilation. No Java pass claimed. One intermediate bounded attempt timed out (124).
- PostgreSQL Direct runtime for these changes: NOT RUN locally. No database server provisioned; no RLS or migration modified.

Limitations:
These tests validate the evidence classifier and shell/fixture contracts, not actual tenant isolation. Existing Users/RLS source-contract failures from the initial checkpoint remain unresolved. Remaining generic fixture limitations, pre-auth permission-model decision, full database tests, Maven/Gate G and independent review still block certification. A fully green local Python subset is not a fully green project.
No production changes, merge, deployment, payments or certificates.
