# WAVE 7 — Progressive Cutover + Security Hardening + Release Verification (Implementation Plan)

> For agentic workers: REQUIRED SUB-SKILL: verification-before-completion — never report PASS without same-SHA machine evidence; every task below is RED → GREEN with exact commands and commit boundaries.

**Spec:** Revision B — §29 Phase 9 + §29.1 (progressive cutover G7-A..G7-G — NO big bang; flags stay default OFF until each stage's own gate passes), §30 (10 release-blocking invariants), §31.1–§31.2 (matrix + break-glass rejections), §32 (release acceptance), §5 Rev B (protected set = 4 roles; `AGENT_CUSTOM_ADMIN` NOT protected).
**Depends on:** W1–W6 merged. **Migrations:** `V20260930_1`..`V20260930_5` · **Flags:** flipped stage-by-stage under the G7 gates — never all at once.

## Goal

Harden the remaining fail-open surfaces, prove the §30/§31 invariants as exact-count CI acceptance gates, and execute an ordered, independently rollback-capable cutover of seven stages with per-stage evidence.

## Architecture

Security migrations are forward-only hardenings (FORCE RLS closes ENABLE-only tables; the vendor DO-loop permissive `crm_%` policies are DROPped and replaced fail-closed per `V20260905_5` doctrine — permissive policies OR-combine, so DROP is mandatory). Cutover is staged: each G7 stage flips exactly its own flag(s) in its own commit/deployment, after its own gates, with its own rollback trigger and smoke.

## Tech Stack

Java 21 · Spring Boot single-module Maven (`mvn`, cwd `apps/sanad-platform`) · Flyway (additive) · PostgreSQL 16 · GitHub Actions `.github/workflows/ci.yml` (pg-acceptance job with the inline Python `expected` map at lines 535–539 — there is NO `tests/ci/*.py` expected-count map and NO web job in ci.yml; web gates run in `.github/workflows/post-merge-verification.yml`) · Next.js `apps/web`.

## Spec

§29.1 Rev B stage list: G7-A Unified Authorization shadow/equivalence · G7-B Unified Authorization authoritative · G7-C Partner Principal + Delegated Administration · G7-D Commercial Identity · G7-E Partner Billing / Trial Continuation · G7-F Settlement · G7-G Notifications + Dashboards.

## Implementation Baseline

Repository evidence at `8d0d49c7`: finance tables are ENABLE-not-FORCE (`V20260815_16` family); the permissive-when-unset vendor loop is `db/vendor/postgresql/V20260730_1__enable_crm_rls.sql` (+ `V20260802_1` re-run) — `current_setting('app.tenant_id', true) IS NULL OR ...`; `ci.yml` pg-acceptance trio = `CommerceOrderPostgresConcurrencyTest:6, RbacAccessCheckPostgresAcceptanceTest:15, ModuleRegistryUatPostgresAcceptanceTest:10` (31 total, inline Python map); the `crm` job runs `mvn test -B -ntp -Dtest='com.sanad.platform.crm.**.*IntegrationTest'` (16 classes); `WorkflowDomainActionAuthorizer`, `ControlPlaneAccessService`, CRM authorization call sites currently use raw `evaluate()`; `application.yml` flag wiring precedents: `sanad.tenancy.billing.dunning-enabled: ${SANAD_DUNNING_ENABLED:false}` (line 146), `sanad.subscription.billing.provider.mode: ${R0C13_PROVIDER_MODE:DISABLED}` (lines 129–131); `RoleTemplateProvisioner` provisions registration templates; `V20260820_5` provenance columns + `V20260820_6` `role_template_bindings`.

## Global Constraints

1. DOCS-ONLY branch: not executed until the owner authorizes WAVE 7.
2. NO stage turns on more than its own flags; NO stage combines flag flips into one commit/deployment.
3. Protected-role seeding covers EXACTLY `PLATFORM_OWNER`, `PLATFORM_ADMIN`, `AGENT_SUPER_ADMIN`, `TENANT_ADMIN` — `AGENT_CUSTOM_ADMIN` is seeded (where the partner hierarchy needs it) as a NORMAL customizable partner-admin role, never entered into `protected_system_roles`.
4. Live payments remain NOT activated (`R0C13_PROVIDER_MODE` unchanged) — spec §19/§33.
5. Any red gate ⇒ cutover aborted, flags stay OFF, `EXECUTION_BLOCKER_REPORT` only.

## Review Focus

Per-stage gate completeness (precondition / same-SHA tests / negative security gates / observability / rollback flag / rollback trigger / post-enable smoke / evidence path); CI lockstep exactness; 4-role seeding.

---

Java paths relative to `apps/sanad-platform/src/main/java/com/sanad/platform/`; tests to `apps/sanad-platform/src/test/java/com/sanad/platform/`.

### Task 1: Finance tables FORCE RLS

Files:
- Create: `db/migration/V20260930_1__finance_tables_force_rls.sql`
- Test: `security/rls/FinanceForceRlsPostgresTest.java` (Create)

Interfaces:
- Consumes: `finance_accounts`, `finance_invoices`, `finance_invoice_lines`, `finance_payments`, `finance_journal_entries`, `finance_journal_lines` (`V20260815_16` family, ENABLE-not-FORCE today — closes S2).
- Produces: FORCE + fail-closed policy per table (V20260905_5 template verbatim).

- [ ] Step 1: exact failing test — for each of the 6 tables: with NULL GUCs ⇒ 0 rows even as table owner (FORCE); cross-tenant blocked; owner-context path with GUC set works.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.rls.FinanceForceRlsPostgresTest test` → NULL-GUC reads return rows (not FORCE) ⇒ red.
- [ ] Step 3: exact minimal implementation — `V20260930_1__finance_tables_force_rls.sql`: for each of the six tables listed above: `ALTER TABLE finance_accounts ENABLE ROW LEVEL SECURITY; ALTER TABLE finance_accounts FORCE ROW LEVEL SECURITY; DROP POLICY IF EXISTS tenant_isolation ON finance_accounts; CREATE POLICY tenant_isolation ON finance_accounts FOR ALL USING (tenant_id::text = current_setting('app.tenant_id', true)) WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true));` and the identical block repeated for `finance_invoices`, `finance_invoice_lines`, `finance_payments`, `finance_journal_entries`, `finance_journal_lines` (background/owner contexts already set the GUC via `TenantRlsConnectionHandler` — proven by existing FORCE tables used in the same flows).
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.subscription.billing.R0C13G03FinanceIntegrationPostgresTest test` → green (context-setting paths proven).
- [ ] Step 6: exact commit — `git commit -m "wave7(rls): finance tables force rls fail-closed (C1)"`.

### Task 2: CRM fail-open closure

Files:
- Create: `db/migration/V20260930_2__crm_fail_open_rls_closure.sql`
- Test: `security/rls/CrmRlsFailClosedPostgresTest.java` (Create)

Interfaces:
- Consumes: the vendor-DO-loop permissive `crm_%` subset (`V20260730_1`/`V20260802_1` — `current_setting('app.tenant_id', true) IS NULL OR tenant_id::text = current_setting('app.tenant_id', true)`, no FORCE).
- Produces: enumerated ENABLE/FORCE + `DROP POLICY IF EXISTS tenant_isolation` + fail-closed policy for EVERY `crm_%` table with `tenant_id`; the exact table list is frozen from a live `information_schema` audit whose query ships INSIDE the migration header as a commented verification block.

- [ ] Step 1: exact failing test — for a sample of the audited tables (at minimum `crm_accounts`, `crm_contacts`, `crm_deals`, `crm_activities`): NULL GUC ⇒ 0 rows for BOTH `sanad` (owner, because FORCE) and `crm_contact_rls_test_user`; cross-tenant blocked.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.rls.CrmRlsFailClosedPostgresTest test` → NULL-GUC reads return rows (permissive window) ⇒ red.
- [ ] Step 3: exact minimal implementation — `V20260930_2__crm_fail_open_rls_closure.sql` as specified. GATED MERGE: this migration merges only after `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest='com.sanad.platform.crm.**.*IntegrationTest' test` (16 classes) AND `CrmRlsTenantIsolationPostgresTest` AND this task's new class are green (S1).
- [ ] Step 4: exact command proving GREEN — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.rls.CrmRlsFailClosedPostgresTest test` → green; then the full CRM job green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest='com.sanad.platform.crm.**.*IntegrationTest' test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave7(rls): crm fail-open closure, enumerated fail-closed policies (C2)"`.

### Task 3: Authorization projection backfill + protected template seeding

Files:
- Create: `db/migration/V20260930_3__uac_projection_backfill.sql`, `db/migration/V20260930_4__protected_role_template_seeding.sql`, `db/migration/V20260930_5__cutover_defaults_documentation.sql`
- Test: `access/evaluation/ProjectionBackfillPostgresTest.java`, `security/authorization/ProtectedSeedFourRolesPostgresTest.java` (Create both)

Interfaces:
- Consumes: `effective_permission_projection` (W1); `roles`/`role_template_bindings` (`V20260820_5/_6`); per-tenant `set_config` idiom (`V20260921_1` line 157).
- Produces: projection rows for all existing ACTIVE grants; `authorization_version = 1` baseline; template `roles` rows for the 4 protected codes + `AGENT_CUSTOM_ADMIN` as a NON-protected hierarchy role where partner contexts need it; comment-only cutover-decision migration.

- [ ] Step 1: exact failing test — backfill: after migration, projection row exists per ACTIVE grant; count invariant vs source query; `users.authorization_version = 1` for all users. Seeding: `protected_system_roles` still contains EXACTLY 4 codes; template roles exist with `role_origin='SNAD_TEMPLATE'` for the 4 protected codes; `AGENT_CUSTOM_ADMIN` role rows (partner contexts) exist WITHOUT any `protected_system_roles` entry (spec §5 Rev B).
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.access.evaluation.ProjectionBackfillPostgresTest,com.sanad.platform.security.authorization.ProtectedSeedFourRolesPostgresTest test` → projection empty / 5-code or missing-AGENT_CUSTOM_ADMIN drift (red).
- [ ] Step 3: exact minimal implementation — the three migrations (backfill DO-loop sets `app.tenant_id` per tenant; seeding inserts where missing; `V20260930_5` records the cutover decision, stage list, flag defaults, rollback runbook pointer — no schema change).
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.service.RoleTemplateProvisionerTest test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave7(schema): projection backfill + four-role protected seeding (C3)"`.

### Task 4: Security invariants acceptance (10 invariants, §30)

Files:
- Create: `security/invariants/SecurityInvariantsPostgresAcceptanceTest.java`
- Modify: none
- Test: this class

Interfaces:
- Consumes: W1–W6 guards; pg-acceptance profile.
- Produces: the 10 §30 invariants as parameterized negatives.

- [ ] Step 1: exact failing test — Partner A→B data; Tenant A→B; TenantAdmin A→B; `AGENT_SUPER_ADMIN` A→Partner B; override→foreign tenant; `TENANT_ALL`→current tenant only; partner ownership ⇏ implicit business-data access; partner edits own fee ⇒ denied; partner invoice for foreign tenant ⇒ denied; snapshot mutation ⇒ exception.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && SPRING_PROFILES_ACTIVE=pg-acceptance PG_ACCEPTANCE_JDBC_URL='jdbc:postgresql://127.0.0.1:5432/pg_acceptance?prepareThreshold=0' PG_ACCEPTANCE_USERNAME=sanad PG_ACCEPTANCE_PASSWORD=sanad_pass mvn -B -ntp -Dsurefire.useFile=true -DfailIfNoTests=true -Dtest=com.sanad.platform.security.invariants.SecurityInvariantsPostgresAcceptanceTest test` → class absent (red); after creation any failing invariant is a REAL defect to fix before cutover.
- [ ] Step 3: exact minimal implementation — none expected (earlier waves' guards cover; any red ⇒ fix in the owning wave's class).
- [ ] Step 4: exact command proving GREEN — same as Step 2 → 10/10 green.
- [ ] Step 5: exact affected regression — full W1–W6 RLS class list (W1 Tasks 1/2/12, W2 Task 1, W3 Task 1/2, W4 Task 19, W5 Task 12, W6 Task 1 classes) → green.
- [ ] Step 6: exact commit — `git commit -m "wave7(acceptance): ten release-blocking security invariants (C4)"`.

### Task 5: Break-glass + authorization matrix acceptance

Files:
- Create: `security/breakglass/BreakGlassInvariantPostgresAcceptanceTest.java`, `access/matrix/AuthorizationMatrixPostgresAcceptanceTest.java`
- Modify: none
- Test: both classes

Interfaces:
- Consumes: §31.2 seven rejections + simulation assertion; §31.1 subjects×dimensions table.
- Produces: acceptance-profile proof; break-glass cases re-prove that DENY still beats break-glass grants (spec §5.1 Rev B).

- [ ] Step 1: exact failing test — seven rejections (last tenant admin, last agent admin, final recovery capability, archive protected role, partner self-grants platform role, partner changes own rate, partner binds foreign tenant) + simulation-before-commit; matrix table-driven cases (Platform Owner, Platform Admin, Agent Super Admin, Agent Custom Admin, Tenant Admin, Custom User, Employee, Manager, Service Account, No-role User) × (ALLOW/DENY, inactive roles, expired overrides, scope match/mismatch, entitlement disabled, delegation present/absent).
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && SPRING_PROFILES_ACTIVE=pg-acceptance PG_ACCEPTANCE_JDBC_URL='jdbc:postgresql://127.0.0.1:5432/pg_acceptance?prepareThreshold=0' PG_ACCEPTANCE_USERNAME=sanad PG_ACCEPTANCE_PASSWORD=sanad_pass mvn -B -ntp -Dsurefire.useFile=true -DfailIfNoTests=true -Dtest=com.sanad.platform.security.breakglass.BreakGlassInvariantPostgresAcceptanceTest,com.sanad.platform.access.matrix.AuthorizationMatrixPostgresAcceptanceTest test` → classes absent (red).
- [ ] Step 3: exact minimal implementation — assemble from W1/W2 guards into the acceptance profile.
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && SPRING_PROFILES_ACTIVE=pg-acceptance PG_ACCEPTANCE_JDBC_URL='jdbc:postgresql://127.0.0.1:5432/pg_acceptance?prepareThreshold=0' PG_ACCEPTANCE_USERNAME=sanad PG_ACCEPTANCE_PASSWORD=sanad_pass mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.rls.RbacAccessCheckPostgresAcceptanceTest test` → 15/15.
- [ ] Step 6: exact commit — `git commit -m "wave7(acceptance): break-glass rejections + authorization matrix (C4)"`.

### Task 6: CI lockstep — ci.yml inline expected map

Files:
- Modify: `.github/workflows/ci.yml` (pg-acceptance `-Dtest=` list += `PartnerIsolationPostgresAcceptanceTest,SecurityInvariantsPostgresAcceptanceTest,BreakGlassInvariantPostgresAcceptanceTest,AuthorizationMatrixPostgresAcceptanceTest`; the INLINE Python `expected` dict (lines 535–539) and the `r0c12-canonical-gate-g` `ALLOWED` dict (line 1058) gain the four new per-class counts — counts DERIVED mechanically from the surefire XML of the same run in the same commit, never invented); no `tests/ci/*.py` map exists — do not invent one
- Test: `tests/ci/test_production_readiness.py`, `tests/ci/test_workflow_final_closure_unified.py` (existing governance tests, run unchanged)

Interfaces:
- Consumes: the exact-count gate doctrine (F-C risk 5).
- Produces: CI failing on count drift.

- [ ] Step 1: exact failing test — run the governance tests BEFORE the ci.yml edit with the intended new list absent: `cd /home/z/my-project/SNAD && python3 tests/ci/test_production_readiness.py && python3 tests/ci/test_workflow_final_closure_unified.py` — the lockstep invariant is enforced by the workflow-summary step itself; a count not matching machine output ⇒ CI red (this is the designed failure mode; the pre-edit state is simply the current green baseline recorded in the evidence log).
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && rm -rf target/surefire-reports && SPRING_PROFILES_ACTIVE=pg-acceptance PG_ACCEPTANCE_JDBC_URL='jdbc:postgresql://127.0.0.1:5432/pg_acceptance?prepareThreshold=0' PG_ACCEPTANCE_USERNAME=sanad PG_ACCEPTANCE_PASSWORD=sanad_pass mvn test -B -ntp -Dsurefire.useFile=true -DfailIfNoTests=true -Dtest='CommerceOrderPostgresConcurrencyTest,RbacAccessCheckPostgresAcceptanceTest,ModuleRegistryUatPostgresAcceptanceTest,PartnerIsolationPostgresAcceptanceTest,SecurityInvariantsPostgresAcceptanceTest,BreakGlassInvariantPostgresAcceptanceTest,AuthorizationMatrixPostgresAcceptanceTest'` → the new classes' counts are read mechanically from `target/surefire-reports/*.xml` (e.g. `grep -h 'tests=' target/surefire-reports/TEST-*.xml`) BEFORE the map is updated.
- [ ] Step 3: exact minimal implementation — the ci.yml edit: add the 4 classes to both `-Dtest` lists and set each `expected`/`ALLOWED` entry to the machine-derived count; totals asserted by the existing inline Python summary step.
- [ ] Step 4: exact command proving GREEN — the same trio-extended command as Step 2 → all 7 classes green with counts equal to the map; then `cd /home/z/my-project/SNAD && python3 tests/ci/test_production_readiness.py && python3 tests/ci/test_workflow_final_closure_unified.py` → green.
- [ ] Step 5: exact affected regression — `cd /home/z/my-project/SNAD && python3 tests/ci/test_workflow_security_policy.py` → green (workflow security policy intact). If that file does not exist at implementation time, substitute `python3 tests/ci/test_workflow_y2_production_orchestrator.py` and record the substitution in the evidence log.
- [ ] Step 6: exact commit — `git commit -m "wave7(ci): pg-acceptance gate lockstep with machine-derived counts (C5)"`.

### Task 7: STAGE G7-A — Unified Authorization shadow/equivalence

Files:
- Modify: `apps/sanad-platform/src/main/resources/application.yml` (`sanad.authorization.unified-enabled: ${SANAD_UAC_PIPELINE_ENABLED:false}` + `sanad.authorization.unified-mode: ${SANAD_UAC_MODE:legacy}` where `legacy|shadow|authoritative`)
- Test: `security/authorization/ShadowEquivalencePostgresTest.java` (Create)

Interfaces:
- Consumes: W1 pipeline; `WorkflowDomainActionAuthorizer`, `ControlPlaneAccessService`, CRM call sites still on legacy path.
- Produces: shadow mode — `evaluateDetailed` computed and LOGGED beside legacy `evaluate()`; any divergence is a metric + log line, decisions still served by legacy.

- [ ] Step 1: exact failing test — in shadow mode, for a seeded 200-case workload, pipeline and legacy agree on every case (equivalence ledger written to the evidence log); divergences fail the test with the case dump.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.authorization.ShadowEquivalencePostgresTest test` → mode plumbing absent (red).
- [ ] Step 3: exact minimal implementation — mode property + shadow instrumentation around call sites (decision logger + Micrometer counter `sanad_authz_shadow_divergence_total`).
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.access.evaluation.CapabilityEvaluationServiceTest test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave7(cutover): G7-A shadow equivalence instrumentation (C6)"`.

Stage record (G7-A):
- Precondition: W1–W7 Tasks 1–6 merged; pg-acceptance extended gate green.
- Same-SHA tests: `ShadowEquivalencePostgresTest` + full battery at the stage commit.
- Negative security gates: DENY-dominance classes green in shadow (`DenyDominancePostgresTest`, W1 Task 11).
- Observability: `sanad_authz_shadow_divergence_total == 0` for the soak window; divergence log path `snad-evidence/g7a-shadow.log`.
- Rollback flag: `SANAD_UAC_MODE=legacy` (env; instant).
- Rollback trigger: any divergence > 0 during soak, or `authz` p99 latency regression > 10 % vs baseline.
- Post-enable smoke: `/api/v1/access/effective-permissions` 200 with owner token; one `@RequireCapability` CRUD path green.
- Evidence path: `snad-evidence/g7a-<sha>.log`.

### Task 8: STAGE G7-B — Unified Authorization authoritative

Files:
- Modify: `WorkflowDomainActionAuthorizer.java`, `ControlPlaneAccessService.java`, CRM party/collaboration authorization call sites (switch from raw `evaluate()` to `evaluateDetailed()` — behavior-preserving, deny reasons enriched; HR scope adapters NO change — already port-based via `ScopedAuthorizationService`)
- Test: rerun of the FULL battery at the stage commit (no new test class; equivalence already proven in G7-A)

Interfaces:
- Consumes: G7-A zero-divergence evidence.
- Produces: `SANAD_UAC_MODE=authoritative` — pipeline serves decisions; legacy `evaluate()` remains as facade.

- [ ] Step 1: exact failing test — none new; the gate is the G7-A ledger + full battery.
- [ ] Step 2: exact command proving RED — none (stage task).
- [ ] Step 3: exact minimal implementation — call-site switches + config default `SANAD_UAC_MODE:authoritative` for the stage deployment (env-overridable back to `shadow`/`legacy`).
- [ ] Step 4: exact command proving GREEN — full battery at stage SHA:
  `cd apps/web && npm ci && npm run lint && npx tsc --noEmit && npm test -- --reporter=verbose && npm run build`
  `cd apps/sanad-platform && mvn test -B -ntp -Dsurefire.useFile=false`
  `cd apps/sanad-platform && SPRING_PROFILES_ACTIVE=pg-acceptance PG_ACCEPTANCE_JDBC_URL='jdbc:postgresql://127.0.0.1:5432/pg_acceptance?prepareThreshold=0' PG_ACCEPTANCE_USERNAME=sanad PG_ACCEPTANCE_PASSWORD=sanad_pass mvn test -B -ntp -Dsurefire.useFile=true -DfailIfNoTests=true -Dtest='CommerceOrderPostgresConcurrencyTest,RbacAccessCheckPostgresAcceptanceTest,ModuleRegistryUatPostgresAcceptanceTest,PartnerIsolationPostgresAcceptanceTest,SecurityInvariantsPostgresAcceptanceTest,BreakGlassInvariantPostgresAcceptanceTest,AuthorizationMatrixPostgresAcceptanceTest'` → 7-class extended gate green with the Task 6 map.
- [ ] Step 5: exact affected regression — the battery.
- [ ] Step 6: exact commit — `git commit -m "wave7(cutover): G7-B unified authorization authoritative (C7)"`.

Stage record (G7-B):
- Precondition: G7-A evidence with `sanad_authz_shadow_divergence_total == 0` across the soak window.
- Same-SHA tests: 7-class extended pg-acceptance gate + full battery.
- Negative security gates: `SecurityInvariantsPostgresAcceptanceTest` 10/10; `DenyDominancePostgresTest` green.
- Observability: decision-latency metric; deny-reason distribution log `snad-evidence/g7b-decisions.log`.
- Rollback flag: `SANAD_UAC_MODE=legacy` (instant revert; shadow keeps parity data flowing).
- Rollback trigger: any §30 invariant red, or unexplained ALLOW-expansion incident.
- Post-enable smoke: owner + tenant-admin + partner-admin happy paths; one deliberate DENY verified per boundary.
- Evidence path: `snad-evidence/g7b-<sha>.log`.

### Task 9: STAGE G7-C — Partner Principal + Delegated Administration

Files:
- Modify: `apps/sanad-platform/src/main/resources/application.yml` + `application-prod.yml` (`sanad.partner.enabled: ${SANAD_PARTNER_ENABLED:false}` → stage default true in prod profile only at this stage)
- Test: `partner/api/PartnerCutoverSmokeIT.java` (Create — thin smoke: mint partner user, bind tenant, delegated TENANT.ACTIVATE, forbidden cross-partner read)

Interfaces:
- Consumes: W2 full task set; G7-B evidence.
- Produces: partner surfaces live.

- [ ] Step 1: exact failing test — smoke IT red until flag-on path exercised end-to-end (created only in this task).
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5433/sanad SPRING_DATASOURCE_USERNAME=sanad SPRING_DATASOURCE_PASSWORD=sanad_pass mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.partner.api.PartnerCutoverSmokeIT test` → smoke assertions fail with flag off.
- [ ] Step 3: exact minimal implementation — smoke IT + stage flag default.
- [ ] Step 4: exact command proving GREEN — same as Step 2 with `SANAD_PARTNER_ENABLED=true` → green.
- [ ] Step 5: exact affected regression — W2 isolation classes: `PartnerPrincipalRlsPostgresTest`, `PartnerMembershipConcurrencyPostgresTest`, `ProtectedRoleGrantGuardTest` → green.
- [ ] Step 6: exact commit — `git commit -m "wave7(cutover): G7-C partner principal live (C8)"`.

Stage record (G7-C):
- Precondition: G7-B evidence; W2 exit battery green.
- Same-SHA tests: smoke IT + W2 regression list at stage SHA.
- Negative security gates: `PARTNER_SCOPE_MISMATCH` 403 path; `ProtectedRoleGrantGuardTest`; membership concurrency class.
- Observability: partner-surface request counters; `PARTNER_SCOPE_MISMATCH` counter.
- Rollback flag: `SANAD_PARTNER_ENABLED=false` (instant; surfaces 404).
- Rollback trigger: any cross-partner data leak signal, or FK/orphan anomaly.
- Post-enable smoke: partner portal login → `/api/v1/partner/me` 200; bound-tenant list non-empty for seeded partner.
- Evidence path: `snad-evidence/g7c-<sha>.log`.

### Task 10: STAGE G7-D — Commercial Identity

Files:
- Modify: `application.yml`/`application-prod.yml` (`sanad.commercial.identity-enabled: ${SANAD_COMMERCIAL_IDENTITY_ENABLED:false}` → stage default true)
- Test: `commercial/api/CommercialCutoverSmokeIT.java` (Create — tenant profile PUT, partner logo upload via `source_module='COMMERCIAL'`, executive verification transition)

Interfaces:
- Consumes: W3 full task set.
- Produces: commercial identity surfaces live.

- [ ] Step 1: exact failing test — smoke IT red with flag off.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5433/sanad SPRING_DATASOURCE_USERNAME=sanad SPRING_DATASOURCE_PASSWORD=sanad_pass mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.commercial.api.CommercialCutoverSmokeIT test` → red.
- [ ] Step 3: exact minimal implementation — smoke IT + stage flag default.
- [ ] Step 4: exact command proving GREEN — same as Step 2 with `SANAD_COMMERCIAL_IDENTITY_ENABLED=true` → green.
- [ ] Step 5: exact affected regression — `WorkflowAttachmentExternalFoundationTest` (shared platform_files intact) + `PlatformFilesForceRlsPostgresTest` → green.
- [ ] Step 6: exact commit — `git commit -m "wave7(cutover): G7-D commercial identity live (C8)"`.

Stage record (G7-D):
- Precondition: G7-C evidence.
- Same-SHA tests: smoke IT + W3 regression list.
- Negative security gates: `PlatformFilesForceRlsPostgresTest`; principal-uniqueness matrix (W3 Task 1).
- Observability: upload counters; RLS-denial counters on platform_files.
- Rollback flag: `SANAD_COMMERCIAL_IDENTITY_ENABLED=false`.
- Rollback trigger: any platform_files regression, or ambiguous-ownership insertion attempt succeeding.
- Post-enable smoke: tenant settings screen save + logo upload + executive verify.
- Evidence path: `snad-evidence/g7d-<sha>.log`.

### Task 11: STAGE G7-E — Partner Billing / Trial Continuation

Files:
- Modify: `application.yml`/`application-prod.yml` (`sanad.trial-continuation.enabled: ${SANAD_TRIAL_CONTINUATION_ENABLED:false}`, `sanad.partner-billing.enabled: ${SANAD_PARTNER_BILLING_ENABLED:false}` → stage defaults true — two flags, ONE subsystem, one stage commit)
- Test: `partner/billing/BillingCutoverSmokeIT.java` (Create — continuation confirm → ACTIVE_BILLABLE → five-condition automatic invoice → markInvoicePaid rejection on partner invoice)

Interfaces:
- Consumes: W4 full task set.
- Produces: billing surfaces + schedulers live.

- [ ] Step 1: exact failing test — smoke IT red with flags off.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5433/sanad SPRING_DATASOURCE_USERNAME=sanad SPRING_DATASOURCE_PASSWORD=sanad_pass mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.partner.billing.BillingCutoverSmokeIT test` → red.
- [ ] Step 3: exact minimal implementation — smoke IT + stage flag defaults.
- [ ] Step 4: exact command proving GREEN — same as Step 2 with both flags true → green.
- [ ] Step 5: exact affected regression — `AutomaticBillingPostgresTest`, `PartnerInvoiceGuardPostgresTest`, `TrialContinuationSchedulerPostgresTest` → green.
- [ ] Step 6: exact commit — `git commit -m "wave7(cutover): G7-E partner billing live (C9)"`.

Stage record (G7-E):
- Precondition: G7-D evidence; W4 exit battery green.
- Same-SHA tests: smoke IT + W4 regression list.
- Negative security gates: `TRIAL_NOT_BILLABLE` path; `PARTNER_INVOICE_FINANCE_AUTHORITY` 409; five-condition negatives (Task 17 of W4).
- Observability: scheduler run counters; invoice issuance counters; finance-mirror lag.
- Rollback flag: `SANAD_TRIAL_CONTINUATION_ENABLED=false` + `SANAD_PARTNER_BILLING_ENABLED=false` (schedulers no-op instantly).
- Rollback trigger: any Finance mirror mismatch, or trial invoice without confirmation.
- Post-enable smoke: seeded trial → confirm → automatic invoice exactly once; `markInvoicePaid` on it ⇒ 409.
- Evidence path: `snad-evidence/g7e-<sha>.log`.

### Task 12: STAGE G7-F — Settlement

Files:
- Modify: `application.yml`/`application-prod.yml` (`sanad.settlement.enabled: ${SANAD_SETTLEMENT_ENABLED:false}` → stage default true)
- Test: `partner/settlement/SettlementCutoverSmokeIT.java` (Create — calculate → replay no-op → new-key replace → approve → finalize → SANAD invoice issued via platform sequence → reconcile green)

Interfaces:
- Consumes: W5 full task set.
- Produces: settlement live.

- [ ] Step 1: exact failing test — smoke IT red with flag off.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5433/sanad SPRING_DATASOURCE_USERNAME=sanad SPRING_DATASOURCE_PASSWORD=sanad_pass mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.partner.settlement.SettlementCutoverSmokeIT test` → red.
- [ ] Step 3: exact minimal implementation — smoke IT + stage flag default.
- [ ] Step 4: exact command proving GREEN — same as Step 2 with flag true → green.
- [ ] Step 5: exact affected regression — `SettlementReplayReplacePostgresTest`, `SettlementFinalizeConcurrencyPostgresTest`, `LateAdjustmentPostgresTest` → green.
- [ ] Step 6: exact commit — `git commit -m "wave7(cutover): G7-F settlement live (C9)"`.

Stage record (G7-F):
- Precondition: G7-E evidence; W5 exit battery green.
- Same-SHA tests: smoke IT + W5 regression list.
- Negative security gates: partner finalize 403; `PERIOD_CURRENCY_MIX`; manual-payment exclusion.
- Observability: reconciliation report artifact per run; finalize counters.
- Rollback flag: `SANAD_SETTLEMENT_ENABLED=false`.
- Rollback trigger: reconciliation drift, or any finalized-period mutation signal.
- Post-enable smoke: one full period cycle on seeded data ending in reconcile green.
- Evidence path: `snad-evidence/g7f-<sha>.log`.

### Task 13: STAGE G7-G — Notifications + Dashboards

Files:
- Modify: `application.yml`/`application-prod.yml` (`sanad.notifications.enabled: ${SANAD_NOTIFICATIONS_ENABLED:false}`, `sanad.dashboards.enabled: ${SANAD_DASHBOARDS_ENABLED:false}` → stage defaults true)
- Test: `dashboard/DashboardCutoverSmokeIT.java` (Create — owner notification present for a partner mutation; global dashboard reconciles on the seeded DIRECT+Σ(PARTNERS) fixture; partner self-dashboard claim-scoped)

Interfaces:
- Consumes: W6 full task set.
- Produces: notifications + dashboards live; cutover complete.

- [ ] Step 1: exact failing test — smoke IT red with flags off.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5433/sanad SPRING_DATASOURCE_USERNAME=sanad SPRING_DATASOURCE_PASSWORD=sanad_pass mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.dashboard.DashboardCutoverSmokeIT test` → red.
- [ ] Step 3: exact minimal implementation — smoke IT + stage flag defaults.
- [ ] Step 4: exact command proving GREEN — same as Step 2 with both flags true → green.
- [ ] Step 5: exact affected regression — `DashboardReconciliationPostgresTest`, `NotificationIsolationPostgresTest`, `MandatoryOwnerNotificationPostgresTest` → green.
- [ ] Step 6: exact commit — `git commit -m "wave7(cutover): G7-G notifications and dashboards live (C10)"`.

Stage record (G7-G):
- Precondition: G7-F evidence; W6 exit battery green.
- Same-SHA tests: smoke IT + W6 regression list.
- Negative security gates: forged `?partnerId=` 403; cross-recipient ack denial; partner cannot read owner deliveries.
- Observability: projection freshness (computed_at lag); reconciliation identity metric `GLOBAL == DIRECT + Σ(PARTNERS)` boolean.
- Rollback flag: `SANAD_NOTIFICATIONS_ENABLED=false` + `SANAD_DASHBOARDS_ENABLED=false`.
- Rollback trigger: reconciliation identity false, or notification cross-scope leak.
- Post-enable smoke: owner feed shows a fresh partner mutation; global vs per-partner drill-down spot check.
- Evidence path: `snad-evidence/g7g-<sha>.log`.

### Task 14: Release evidence bundle + final verification runbook

Files:
- Modify: none (evidence only)
- Test: full runbook execution

Interfaces:
- Consumes: all stage logs; CI.
- Produces: `snad-evidence/evidence-<sha>.log` + CI run URLs.

- [ ] Step 1: exact failing test — none.
- [ ] Step 2: exact command proving RED — none.
- [ ] Step 3: exact minimal implementation — none.
- [ ] Step 4: exact command proving GREEN — executed in order, all at ONE HEAD SHA:
  1. `cd apps/sanad-platform && mvn test -B -ntp -Dsurefire.useFile=false` (full suite green, count recorded).
  2. `cd apps/sanad-platform && mvn test -B -ntp -Dtest='com.sanad.platform.crm.**.*IntegrationTest' test` (green).
  3. pg-acceptance job (7-class extended gate green; new expected total enforced).
  4. `cd apps/web && npm ci && npm run lint && npx tsc --noEmit && npm test -- --reporter=verbose && npm run build` (green).
  5. `cd apps/sanad-platform && SPRING_PROFILES_ACTIVE=pg-acceptance PG_ACCEPTANCE_JDBC_URL='jdbc:postgresql://127.0.0.1:5432/pg_acceptance?prepareThreshold=0' PG_ACCEPTANCE_USERNAME=sanad PG_ACCEPTANCE_PASSWORD=sanad_pass mvn test -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.dashboard.DashboardReconciliationPostgresTest test` + live reconcile report attached.
  6. Billing/settlement matrices (W4 Tasks 14–18, W5 Tasks 4–12 classes) green at the same SHA.
  7. Exact-HEAD CI run on the release branch: all required checks green (test, crm, pg-acceptance with the extended map, r0c12-canonical-gate-g; web gates per `.github/workflows/post-merge-verification.yml`).
- [ ] Step 5: exact affected regression — the runbook IS the regression.
- [ ] Step 6: exact commit — `git commit -m "wave7(evidence): release verification bundle @ <sha> (C11)"`.

## Security implications, rollback, failure semantics

**Security:** §30 becomes release-blocking in CI (exact-count, fail-closed); finance FORCE RLS + CRM fail-open closure shrink the fail-open surface to zero for tenant-scoped data reachable by the `sanad` role; the 4-code protected registry is enforced end-to-end with `AGENT_CUSTOM_ADMIN` never protected. **Rollback:** each G7 stage's flag reverts instantly (all waves flag-gated); `V20260930_1/_2` are forward-only hardenings with owner-held compensating scripts (re-enabling permissive policies) for emergency only; no destructive DDL anywhere in the program. **Failure semantics:** any red gate ⇒ cutover aborted at the current stage, all later stages stay OFF, `EXECUTION_BLOCKER_REPORT` issued with the failing evidence path — implementation is never declared complete on partial evidence.
