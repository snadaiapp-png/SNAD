# SANAD G1 — exact-SHA production GO / NO-GO handoff (DRAFT — NO APPROVAL)

**Status:** `P0_SECURITY_HOLD=ACTIVE` / `G1=NO-GO` / `PAYMENTS=OFF`. This document is a reviewer checklist, not a release decision or an authorization to access production data.

**Source evidence candidate at preparation:** `main@3b1445dddef52ff2b4827fb6354c563b427104cf`. A merge of this PR necessarily changes `main`; **invalidate the old candidate and re-pin the actual 40-character post-merge SHA** with new tests, a bound immutable OCI digest, and the real change approval.

**References:** [production change #1496](https://github.com/snadaiapp-png/SNAD/issues/1496), [independent security hold #1462](https://github.com/snadaiapp-png/SNAD/issues/1462), [backup approval #1481](https://github.com/snadaiapp-png/SNAD/issues/1481), [protected production plan](G1-PROTECTED-PRODUCTION-CHANGE-PLAN-2026-11-10.md).

## A. Evidence already obtained — exact limits

| Evidence | Observed result | Not proven |
| --- | --- | --- |
| [CI #42840308867](https://github.com/snadaiapp-png/SNAD/actions/runs/42840308867) and [Post-Merge #42840309008](https://github.com/snadaiapp-png/SNAD/actions/runs/42840309008) | Terminal SUCCESS on source `3b1445dd...` | Production tenant isolation, approved migration or later code |
| [Isolated PG17.6 separation #42880314820](https://github.com/snadaiapp-png/SNAD/actions/runs/42880314820) | Actual 62/62 security probes and 11/11 catalog checks, Flyway `20261107.1`; artifact 13793127852 | Production-derived restoration or production-control repair; cleanup emitted INCOMPLETE |
| [Durable encrypted backup #42623864874](https://github.com/snadaiapp-png/SNAD/actions/runs/42623864874) | Historic 2026-11-09 artifact 13678480223, encrypted roundtrip PASS | Fresh pre-cutover recovery point; custody/key rotation/RPO/RTO; full Supabase restore |
| [ACL/RLS equivalence #42638822139](https://github.com/snadaiapp-png/SNAD/actions/runs/42638822139) | Historic comparison PASS for `public`-scope structures; artifact 13684103698 | Supabase platform schemas, cross-service restore, operational application recovery |
| Last live Supabase catalog check | Five tenant tables RLS disabled; `login_tenant_index` overbroad UPDATE; `sanad_migrator` absent; Flyway head `20261021.1` | Exact current state at approved change window |
| Last live Render check | Historic image ref `48322d0...`, digest `sha256:f1df37f53d3db2751b651540720097a46817a0cac9a1c47ed85d192c567c0008` | Alignment with candidate source or approval to deploy |

## B. Four required signed decisions; no auto-approval

### 1. DBA + independent G1 security reviewer — role/RLS/ACL

- [ ] Authorize controlled production-derived isolated restore access separately from any production mutation.
- [ ] Preserve exact current Flyway `20261021.1` start, relevant owners, default privileges, ACL, `SECURITY DEFINER` routines, policies and tenant resolver behavior.
- [ ] Execute reviewed `sanad_migrator` bootstrap, ownership transfer, complete Flyway chain and `validate` **only in isolation** first; expected head must derive from frozen source, not a guess.
- [ ] Prove allowed same-tenant reads/writes; rejected Tenant B access to A; no/empty/invalid GUC; SQLSTATE; pool-reset behavior; user signup/login/refresh/reset/logout; F05 missing/stale/duplicates=0; provisioning worker flows; append-only `subscription_commands`.
- [ ] Five tenant tables: reviewed FORCE RLS and least-privilege ACLs. **`login_tenant_index` remains intentionally without RLS for pre-auth resolution; revoke overbroad UPDATE only after resolver compatibility tests.**
- [ ] Verify runtime `sanad` is non-superuser/NOBYPASSRLS, lacks access to migrator identity, no DDL/Flyway history access, and has no transitive membership escape path.
- [ ] Reviewer records independent results, exceptions and signature against exact release SHA. **Isolated 62/62 is useful evidence, not production proof.**

### 2. Backup/recovery authority — recoverability and scope

- [ ] Explicit pre-backup scope approval; approved secret custody; recent encrypted production-derived recovery point retained outside an ephemeral runner, with encryption SHA-256, owner, storage location/access controls and retention.
- [ ] Controlled isolated PostgreSQL 17 restore of approved snapshot, including application tables/data, roles/owners/ACL/default privileges/tenancy policies; reconcile Supabase-managed schemas and external services with a supported recovery plan.
- [ ] Verify backup hashes and Flyway history, negative auth/RLS cases after restore, synthetic failure-injection and application restore; **measure** actual RPO and RTO vs *specified and signed* thresholds.
- [ ] Sign off missing objects/exceptions and immutable backup ID. Never upload plaintext customer records, a DB connection URL or decryption keys to GitHub.
- [ ] Previous artifacts prove earlier bounded exercises; do not treat them as the fresh cutover recovery point.

### 3. CAB and separation of duties

**To be completed by actual authorized humans, not automation or the source author.**

| CAB field | Current decision |
| --- | --- |
| Formal approved change ID | **UNASSIGNED** |
| Business owner and DBA | **PENDING** |
| Security reviewer and release operator | **PENDING** |
| Exact latest `main` full SHA | **RE-FREEZE REQUIRED AFTER PR MERGE** |
| Backend immutable OCI digest and SBOM/provenance review | **UNVERIFIED** |
| Maintenance start/end, timezone Asia/Riyadh | **PENDING** |
| Authorized backup/recovery location and measured RPO/RTO | **PENDING** |
| Stop/rollback or forward-fix authority and compatibility analysis | **PENDING** |
| Final independent decision | **NOT APPROVED** |

**STOP** on missing backup, SHA/digest drift, Docker use under the stated restriction, unexpected migration checksums, ACL drift, unresolved user/worker regressions, cross-tenant disclosure, failed reconciliation, or missing owner approval. A pattern-matching ticket field, PR review, `G1_SECURITY_APPROVED=true` variable, or successful workflow is **not** a CAB decision.

### 4. Protected production cutover — ONLY after the above is actually signed

1. Confirm post-merge `main` and exact immutable Backend artifact; rerun exact-SHA CI, isolated role/RLS acceptance, SBOM and attestation checks. Audit Docker-free Buildah/Skopeo publication implementation and verify actual non-Docker build.
2. Gate migration on fresh recovery point and approved isolated rehearsal; execute dedicated `sanad_migrator` workflow with exact SHA and measured stop/restore contract. **Runtime `FLYWAY_ENABLED=false`**, not true. Never use `repair/clean/baseline/outOfOrder` shortcuts.
3. Recheck live RLS/ACL/roles, Flyway head, application compatibility, pre-auth, worker authorization and identity. Verify Vercel `/api/system/release` exact SHA before Render change.
4. Deploy exact immutable OCI digest through the protected Render workflow. Verify actual Render image **digest**, readiness, auth flows, isolated tenant A/B, subscription/provisioning E2E and errors.
5. Trigger and inspect real protected **Production 3-User Final Gate** for Employee/Manager/HR after deployment, with complete actual results, no `SKIPPED` substitution. Observe agreed monitoring window and record rollback/incident disposition.
6. Issue independent exact-SHA security GO only if every mandatory gate is evidenced and approved. **Keep payments OFF** pending distinct legal/financial/provider authorization.

## C. Open implementation parity items in this PR (must not be silently passed)

- Docker-free OCI Buildah publisher/preflight **requires actual CI and publication results** before acceptance. The new test workflow never publishes to GHCR or deploys. First isolated build run #42883744152 passed actual OCI build/archive checks, but no registry publication was executed. OCI format **does not embed HEALTHCHECK**; current Render service was observed with `serviceDetails.healthCheckPath=/actuator/health` and the candidate production workflow now fails closed unless that external probe remains configured.
- Prior BuildKit publisher emitted an SBOM and BuildKit provenance. The replacement uses a GitHub OCI digest attestation, but **equivalent SBOM generation/review and provenance policy must be independently verified**; do not claim parity by source review alone.
- The existing `scripts/production/verify-flyway.sh` reads `flyway_schema_history` using runtime DB credentials. In separated topology, runtime SELECT to Flyway history must be denied. Before any Render deployment, change that audit to a separately governed **read-only audit identity** and test it; **do not regrant Flyway history SELECT to runtime**. The production release workflow is intentionally fail-closed until this incompatibility is resolved.
- Historic Docker-related workflows and developer documentation outside this narrow publisher/release path are not hereby certified as globally Docker-free.
- The old waiting Vercel production workflow run #42840150626 targets `5604d288...`, not this candidate; do not approve it on behalf of the newly frozen SHA.

**Do not run any production migration, ownership transfer, forced RLS enablement, credential rotation, backup export or deployment solely from this runbook or its PR.**
