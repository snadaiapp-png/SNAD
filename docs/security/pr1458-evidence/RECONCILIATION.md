# PR 1458 reconciliation checkpoint — 2026-11-01

Status: INCOMPLETE / NOT APPROVED FOR MERGE OR DEPLOYMENT.

## Source identity
- main: 2e88419cb6bd03f3d431fe1fe551018dc7ebc73a (1460 and 1463 already merged).
- incoming PR: 6b99c89e7b62373b19895c6de684d3e45f6c8603.
- Merge is additive; no reimplementation of either merged PR.
- V20261028_2 bytes retained: SHA256 7dd365bf61cdde2adce359a73e4ca49f3b2d3f894aaa309d445d1eb1ed1f0eaf.

## Database history evidence
Raw database inventory and history evidence are withheld from this public repository. No live certification or upgrade validation is claimed.

## Conflict resolution
Seven files actually conflicted:
- Crm008bFoundationAcceptanceTest: retain main latest 20261031.1.
- CrmFlywayHistoryAssertionTest: retain both ordered chains, including 20261028.2.
- CrmPostgresMigrationTest: retain main latest 20261031.1 and insert D6 version in both upgrade expectations.
- HrG1MigrationTest: retain main latest 20261031.1.
- R0C13G02SchemaPostgresTest: retain main latest 20261031.1.
- RefreshTokenConcurrencyPostgresTest: tenant-scoped token and subscription cleanup; preserve USED=1 / REVOKED=1 / ACTIVE=0 assertions.
- G1GDynamic403ForensicPostgresTest: move token cleanup into the existing tenant-scoped transaction alongside main's cleanup.

## Migration strategy boundary
Do not edit or rename the existing V20261028_2: its two published historical revisions and previous isolated execution are known. Any ACL/trigger correction must be a new migration newer than 20261031.1. A database already at main 20261031.1 without 20261028.2 needs an explicitly tested, history-preserving catch-up strategy; default outOfOrder=false must NOT silently ignore the gap. Do not enable blanket ignoreMigrationPatterns or use repair/baseline to hide it. Strategy approval awaits fresh, original-PR-history, main-history and actual-history upgrade evidence.

## Open security findings / work
- Original migration lacks explicit auth_token_tenant_index runtime table grants/revocations. Invoker triggers require appropriate DML as well as resolver SELECT when the migrator is postgres and runtime is sanad. Owner=runtime-only tests cannot prove this deployment contract.
- Verify PUBLIC/Supabase-role ACLs and search_path, including temporary schema ordering, in a new migration.
- Trigger UPDATE currently assumes hash immutability. Test hash/identity changes or enforce the invariant, and test status transitions, revocation and replay.
- Application refresh/reset/context and all negative isolation cases need tests on the reconciled SHA.
- Fresh database and isolated actual-history upgrades have NOT run here.

## Validation
- git merge-tree reproduced seven conflicts; all conflict markers resolved.
- git diff --check completed successfully (format only).
- Maven 3.9.6 / Java 17.0.20 available. test-compile BLOCKED before compilation: Maven Central DNS failure while resolving Spring Boot parent 3.5.6. See local-test-compile-blocked.log.
- No local PostgreSQL client/server/docker available. apt setup failed due process privilege restrictions. No database tests claimed.
- CI outcomes must be recorded against the pushed SHA separately; none claimed by this checkpoint.

P0_SECURITY_HOLD=ACTIVE
G1_CERTIFICATION=HOLD
PAYMENTS=OFF
PRODUCTION_RELEASE=NO-GO

## Forward correction implementation (awaiting CI)
RED reproduction: bdf240e0, run 41321842226, job 124893537215, psql exit 3 at pre-auth SELECT: permission denied for table auth_token_tenant_index. Distinct migration owner and restricted runtime role are explicitly asserted.

V20261101_1 adds runtime SELECT/INSERT/UPDATE/DELETE for the invoker trigger and resolver, denies PUBLIC and optional Supabase API roles, pins the function to pg_catalog/pg_temp, and rejects changes to token hash/tenant/user identity while allowing status/revocation transitions. No historical migration changes; no new role, SECURITY DEFINER, BYPASSRLS or token-table RLS weakening. Latest-version assertions advance to 20261101.1 while retaining all intermediate versions.

The D6 test no longer catches the central mandatory PostgreSQL availability exception and turns it into a skip. Owner-separation CI uploads to a dedicated temporary directory. Initial RED artifact 13089324460 included pre-existing repository evidence because its first upload directory overlapped evidence/; it is not a production-data export.

Implementation is not yet verified. Full-chain fresh/upgrade, split-owner direct tests, full Maven and security review remain required. No G1 readiness/closure claim.
