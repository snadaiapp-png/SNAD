# SANAD — Protected Production Remediation Change Plan (G1 / Issue #1462)

**Status: DRAFT — NOT APPROVED — DO NOT EXECUTE**
**Prepared:** 2026-11-10; **baseline reference:** `main@e7c876c2f8d0e9fe972c4ede56e4d2188e91b405`
**Scope:** Supabase production `tkbrvupemreqabwzdpyq`, Render backend `srv-d8ragqkm0tmc73bviqq0`, Vercel frontend `snad-app.vercel.app`.
**Change ticket:** UNASSIGNED. **Authorized production approver:** UNRECORDED for this new change. **Maintenance window:** NOT APPROVED.
**Nonnegotiables:** NO Docker anywhere, NO production writes/migrations without scoped approval, NO privilege/RLS bypass, NO secret or customer-data publication, NO payment activation. No unsafe blind `FORCE RLS` toggle.

## A. Evidence-anchored initial state (READ-ONLY verified; must recheck before execution)

| Boundary | Observed fact | Gate consequence |
|---|---|---|
| Source SHA | GitHub main `e7c876c2...` | Freeze full approved SHA after any remediation PR is merged; never deploy stale SHA |
| Frontend | Vercel live `/api/system/release` matched `e7c876c2` in the authorized operator's audit | Re-check with protected probes at release time |
| Backend | Render service image `ghcr.io/snadaiapp-png/snad-backend:48322d0bae97082d69ee143b7af26962d6e14ef8`; latest LIVE deployment `dep-dbgjqbjl550s73cnln40`, digest `sha256:f1df37f53d3db2751b651540720097a46817a0cac9a1c47ed85d192c567c0008` | Backend/image drift; green orchestration alone was not actual deployment |
| Database | Supabase project active on PostgreSQL 17.6, `current_database() = postgres`; Flyway latest successful `20261021.1` | Security migrations beyond baseline are NOT APPLIED |
| Migrations | Successful history lacks `20261028.2`, `20261031.1`, `20261101.1`, `20261102.1`, `20261102.2`, `20261103.1`, `20261103.2` | Forward-only lineage needs complete ordered isolated rehearsal; source HEAD may be later than listed versions |
| Roles | `sanad`: NOSUPERUSER/NOBYPASSRLS, LOGIN; `sanad_migrator` absent; `sanad` SELECT on Flyway history but no UPDATE/CREATE | Migrator bootstrap and role/ownership migration are NOT ready |
| Five tenant tables | `refresh_tokens`, `password_reset_tokens`, `provisioning_jobs`, `provisioning_job_steps`, `subscription_commands`: RLS=OFF, FORCE=OFF, policies=0, effective `sanad` full SELECT/INSERT/UPDATE/DELETE | **P0 production catalog drift** from reviewed intended isolation contract |
| Pre-auth index | `login_tenant_index`: RLS=OFF intentionally; effective `sanad UPDATE` granted (unexpected) | Correct excessive ACL without breaking GUC-less pre-auth discovery |
| Audit record | Draft PR #1492 + Issue #1462; source CI PMV #42714247463 SUCCESS, protected backup #42623864874 SUCCESS and ACL structural comparison #42638822139 SUCCESS | Those outcomes are scoped; no automatic live-environment certification |

**Important technical constraint:** Supabase SQL editor executes as administrative `postgres` (reported `rolbypassrls=true`); inspection through that identity **cannot** prove effective row isolation for the runtime `sanad`. Run all tenant A/B negative checks with an authorized restricted-role test connection in an isolated production-derived database, and later via a specifically approved production-safe path. Never create or inspect real tenant rows for testing.

## B. Stop-the-line prerequisites and chain of custody

1. **G0 — Authoritative baseline / freeze:** At change-window opening, verify GitHub current main, signed/attested backend artifact SHA and OCI digest, Render live deployment ID, Vercel identity, Supabase project/database identity, Flyway successful head/checksums, current five-table RLS/policy/ACL, pre-auth index ACL, role membership, runtime `DATABASE_USERNAME` and `FLYWAY_ENABLED` in Render **without printing secrets**. Record UTC timestamps and who observed each. If any differs, STOP and rebase the plan.
2. **G1 — Approved change and review:** A production owner, DBA/migration operator, security reviewer and release operator review a numbered change request with named owners, exact release SHA/OCI digest, blast radius, read/write scope, maintenance window, client communications, expected Flyway head, rollback/restore decision rights and risk register. Record explicit `APPROVED` state; a PR approval or ticket format by itself is NOT change authorization. Under the 2026-10-24 governance decision, external independent certification is not universally required, but G1 security-control signoff and separation of duties still need documented disposition.
3. **G2 — Credential hygiene:** Validate current production/backup credentials in approved secrets storage and rotate any credibly exposed tokens before privileged action, with connection checks. Never log credentials, dump customer records to Issues, or treat timestamps of secret updates as proof of rotation.
4. **G3 — Recovery custody:** Confirm recent authorized **production-derived**, encrypted, access-limited backup, retention independent of ephemeral runner and acceptable RPO/RTO. Verify integrity and decryptability in a controlled isolated environment. Previous successful workflows are historic evidence, not a replacement for a sufficiently fresh pre-change recovery point.
5. **G4 — Restore scope:** Existing `g1-production-backup-restore-proof.yml` selectively restores `public` while omitting platform extensions and ACL/default ACLs; `g1-acl-rls-certification.yml` separately checks retained ACLs. Neither proves a full Supabase platform restore. For this change, require a separately documented full/supported recovery strategy for all affected components plus tested application-level recovery. Explicitly reconcile omitted schema/owner/role/default privilege surfaces before approval.

## C. Rehearsal on production-derived isolated PostgreSQL 17 (no Docker)

1. Restore the newly approved backup into a private, network-restricted PostgreSQL 17 environment using native `pg_dump`/`pg_restore`/`psql` and approved secrets only. Mask or avoid exposing customer data; no public runner artifacts of raw dumps. Confirm source archive digest, object/row counts without contents, Flyway history, role/ACL/RLS catalogs and migration checksums.
2. Simulate the exact **current** production starting point `20261021.1`, including existing owner `postgres` and app-role grants. Inspect pending migrations and any nontransactional DDL or Supabase-specific dependencies. Compare the target SHA's exact Flyway file list and checksums; resolve missing/failed/out-of-order history *before* run; never use global `outOfOrder`, `repair`, `baseline`, `clean` or fabricated history to get a PASS.
3. Prepare a reviewed **role-provisioning/ownership-transfer dry run** in the isolate using `docs/security/migrator-runtime-separation/RUNBOOK.md` and `scripts/ops/migrator-separation/transfer-ownership-to-migrator.sql`. The current production role is absent, and the script transfers all non-extension public-schema owners — scope and Supabase-managed dependencies must be inspected against catalog before approval. Validate no app/grant disruption, no accidental ownership of platform-managed objects, no runtime membership of migrator, and `sanad` denied DDL/TRUNCATE/history mutation.
4. Verify topology `separated`; migrate only as `sanad_migrator`, exact SHA with Flyway 11.7.2 `validateOnMigrate=true` and without prohibited bypass flags. Capture full `info` (before), migration transaction outcome, `validate` (after), target head, catalog diffs and failure-injection responses. Confirm maintenance identity has no superuser/BYPASSRLS and application receives no migrator secret.
5. Validate **real object names**: `provisioning_job_steps` (not `provisioning_steps`). Expected runtime ACL: token tables SELECT/INSERT/UPDATE/DELETE; `login_tenant_index` SELECT/INSERT/DELETE **without RLS by pre-auth design**; jobs/steps SELECT/INSERT/UPDATE (no DELETE); subscription commands SELECT/INSERT only (no UPDATE/DELETE). The other five use ENABLE+FORCE, fail-closed tenant policies. Explicitly confirm revoked PUBLIC, owner, effective inherited grants, function search_path and SECURITY DEFINER EXECUTE surface.
6. Run PostgreSQL Direct as restricted `sanad` on synthetic tenant A/B sessions: same-tenant positive access, cross-tenant SELECT/UPDATE/DELETE denied, cross-tenant INSERT `WITH CHECK` denies (record SQLSTATE 42501 or intended effect), missing/empty/invalid tenant context denies, pool recycling, tenant-scope trigger/index function negatives and pre-auth login/refresh/reset. Test provisioning steps parent-child isolation, subscription command append-only and background worker tenant enumeration. Recheck F05 login-index **missing=0, stale=0, duplicates=0** if this is the approved F05 acceptance contract; never mutate existing production data under a test harness.
7. Run user creation/login/refresh/logout/reset, subscription lifecycle, provisioning and worker E2E, Flyway consistency, Render/Vercel integration smoke in an isolated, non-payment environment. Compare passing test IDs, logs and SQLSTATE to Issue #1462 acceptance list. Report actual failures; do not conflate test-plan presence with test execution.
8. Rehearse a **controlled recovery/failure path** (including mid-migration failure and backend/probe failure) on the isolate. Quantify RPO/RTO, validate restored data/ACL/RLS/roles and documented incident escalation. A previous backend image rollback after a forward DB migration may be incompatible; explicitly test forward/backward compatibility or require approved restore, not an automatic unsafe database downgrade.

## D. Production cutover — PROHIBITED until G0–G4 + C all verified and authorized

**Execution in production requires separate explicit approval at that time. These are planned stages only.**

1. Freeze change window, stop competing schema writers/jobs, retain live service baseline and restore readiness. Reconfirm current full SHA (may change after non-Docker workflow remediation PR), OCI image digest, schema baseline and approved plan.
2. Ensure backend delivery path is **Docker-free**. The current `.github/workflows/production-release.yml` invokes `docker login` / `docker manifest inspect` and currently has `cancel-in-progress: true`. It is **BLOCKED** under this user's no-Docker constraint. Submit and test a separate PR replacing those steps with a pinned OCI registry client or standards-based OCI HTTPS manifest check, and review cancellation/rollback behavior **before** any dispatch. Do not dispatch old release workflows.
3. Only with protected operator approval: provision dedicated migrator identity and credential, perform explicitly authorized one-time ownership transfer/role topology bootstrap **after** backup/rehearsal, then run `.github/workflows/database-migrate-production-migrator.yml` from the newly approved exact SHA. The workflow fails closed without the role, approved SHA, `confirm=migrate-as-migrator`, supplied expected head and protected Production environment approval. Stop at any mismatch.
4. Verify Flyway head and checksums, effective table/sequence/function/schema privileges, `sanad` runtime identity from actual Render configuration and database catalog, five-table ENABLE+FORCE plus exact `WITH CHECK`/USING shapes, pre-auth index without RLS but with restricted ACL, and no elevated grant/membership. Perform only approved non-destructive synthetic tenant boundary tests (never live customer data).
5. Deploy exact SHA-bound Backend OCI artifact to Render using an independently approved Docker-free pipeline; record live Render deployment ID, image ref **and digest** from Render API and verify actual health/readiness. Recheck Vercel live SHA/BFF compatibility; no claim of aligned production if backend is still `48322d0`. Keep Flyway disabled in runtime and payments disabled.
6. Conduct controlled smoke and 60-minute observation (or approved monitoring duration): tenant A/B, auth reset/refresh, subscriptions, provisioning workers, error-rate, health, control-plane BFF and auditing. Distinguish release-success jobs from skipped dispatch/recovery steps; successful non-release orchestration is not deployment evidence.
7. Close change only with signed evidence of all required conditions, exact production SHA/digest, test reports, incident/rollback record and authorized G1 decision. **PAYMENTS stay OFF** pending a separate financial/provider/operational security approval and negative-to-positive payment activation tests.

## E. Halt / rollback / fail-closed matrix

| Trigger | Immediate containment | Recovery decision / evidence needed |
|---|---|---|
| SHA/digest drift, missing role or secret, incorrect Flyway lineage | STOP before production writes | Re-baseline and reapprove; no `repair`, `baseline`, `outOfOrder`, bypass credentials or Docker workaround |
| Insufficient backup, unavailable encrypted restore, catalog/ACL mismatch | STOP | Repeat isolated recovery until proved; do not touch production schema |
| Ownership transfer unexpected impact, role membership/privilege escalation | STOP, preserve evidence and change window | Security/DBA-led forward remediation or approved controlled restore; do not continue migrations blindly |
| Flyway migration failure or checksum divergence | STOP, preserve logs and previous state | Confirm transactional vs nontransactional effects; reviewed forward-only fix or recovery from approved backup, not forced rerun |
| Tenant B can access A, absent context leaks, pre-auth ACL drift, wrong SQLSTATE | SEV-1 HOLD; disable exposed flows if authorized | Isolation/privilege investigation, rollback/restore decision; no payment activation |
| Backend starts unhealthy or target image/digest not live | STOP rollout, hold integrations and payments | If DB compatibility confirmed, revert app to known-good image; else coordinated DB recovery plan with explicit approval |
| Restore simulation/failure-trigger rehearsal unproven | NO-GO | Obtain actual rehearsal logs before scheduled production cutover |

## F. Required evidence package and signoffs

- Change ticket ID (real, not invented), target fully qualified SHA and immutable OCI digest, authorized environment, window, rollback RPO/RTO, blast radius and signatories.
- Linked versioned database catalog snapshots **metadata only**: Flyway version/checksum, role membership, owners, ACL, policies/USING/WITH CHECK, security-definer search_path; avoid user/customer rows and secrets.
- Backup custody evidence and separate restore proof (including documented omissions); isolated migration, negative/positive RLS/Surefire E2E and failure-injection logs; a checklist row can be PASS only on real terminal test evidence.
- Post-cutover Render deployment/image digest, frontend/backend identity, exact tests and release outcome, live observation and signed residual-risk exception register.
- Explicit project-owner production authorization, qualified DBA operational signoff, security reviewer acceptance with recorded separation/any conflict. External third-party certification is optional under revocation record unless new law/contract requires it. G1 decision **must** be exact-SHA and must not be inferred from PR approval or all-green CI.
- **Do not edit historical G1 certificates or automatically tick Issue #1462 checkboxes**; link new evidence and independent decision without rewriting prior history. Payments remain separately gated.

## G. Current execution ledger

| Gate | State at drafting |
|---|---|
| G0 Baseline inventory | PASS for last observed facts; recheck at change time |
| G1 Approved production change / maintenance window | **NOT APPROVED** |
| G2 Credential hygiene / protected migrator role | **UNVERIFIED / NOT PROVISIONED** |
| G3 Fresh recovery point for proposed cutover | **NOT PROVEN** (older backup run exists) |
| G4 Complete restore-scope / ACL/roles reconciliation | **PARTIAL** (structural evidence with known exclusions) |
| C Production-derived isolated migration/role rehearsal | **NOT RUN under this plan** |
| D Docker-free release pipeline verified | **BLOCKED** (current canonical release invokes Docker) |
| D Real production change | **NOT EXECUTED** |
| Final G1 exact-SHA certification | **NO-GO** |
| Commercial payments | **OFF** |

## Source references

- [G1 live findings](https://github.com/snadaiapp-png/SNAD/issues/1462#issuecomment-6754989438); [Draft evidence PR #1492](https://github.com/snadaiapp-png/SNAD/pull/1492).
- [Migrator/runtime runbook](../security/migrator-runtime-separation/RUNBOOK.md); [migrator workflow](../../.github/workflows/database-migrate-production-migrator.yml); [one-time transfer script](../../scripts/ops/migrator-separation/transfer-ownership-to-migrator.sql).
- [Backup/restore native workflow](../../.github/workflows/g1-production-backup-restore-proof.yml); [ACL/RLS restore comparison](../../.github/workflows/g1-acl-rls-certification.yml); [canonical production release](../../.github/workflows/production-release.yml).
- [Source six-table RLS acceptance](../security/g1-final-closure/03-SIX-TABLE-RLS-ACCEPTANCE.md); [2026-10-24 external certificate revocation](../governance/SANAD-SECURITY-CERTIFICATION-REQUIREMENT-REVOCATION-2026-10-24.md).

**Prepared plan only — no production action, no privilege changes, no Docker, no G1 approval, no risk acceptance.**
