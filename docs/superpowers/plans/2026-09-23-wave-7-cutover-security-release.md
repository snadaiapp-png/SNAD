# WAVE 7 — Progressive Cutover + Security Hardening + Release Verification (Implementation Plan)

> For agentic workers: REQUIRED SUB-SKILL: verification-before-completion — never report PASS without same-SHA machine evidence. **Revision D two-class doctrine:** implementation tasks follow test-first RED → minimal implementation → GREEN → affected regression → commit; verification/evidence/cutover-stage tasks follow PRECONDITION → VERIFY → EVIDENCE with untracked evidence and NO tracked evidence commit after the gate (no fabricated RED). Exact commands and commit boundaries are listed per task.

**Spec:** **Revision D** — §29 Phase 9 + §29.1 (progressive cutover G7-A..G7-G — NO big bang; flags stay default OFF/legacy in every committed file until each stage's own gate passes), §30 (10 release-blocking invariants), §31.1–§31.2 (matrix + break-glass rejections), §32 (release acceptance), §5 (protected set = 4 roles; `AGENT_CUSTOM_ADMIN` NOT protected).
**Depends on:** W1–W6 merged. **Migrations:** `V20260930_1`..`V20260930_5` · **Flags:** flipped stage-by-stage under the G7 gates — never all at once.
**Revision D (R3) changes in this wave:** (1) Task 3 `authorization_version` backfill is DETERMINISTIC by ranked mapping — the migration locks the `users` set, computes `base_version = max(existing authorization_version)`, materializes a ranked mapping `ROW_NUMBER() OVER (ORDER BY created_at, id)`, assigns the PRECOMPUTED ranked values (never `nextval()` inside an unordered UPDATE), then positions `uac_authorization_version_seq` with `setval(..., is_called)` so every future bump via `nextval` lands above every assigned version; rerunning changes no non-zero version; (2) Tasks 4/5/6 acceptance classes are EXPLICITLY created by their own tasks (no `none` while the class is absent); (3) G7-B..G7-G stage tasks commit NO default flip — code/config plumbing is committed once with false/legacy defaults and stage activation is a DEPLOYMENT-ENV change only (evidence records the deployment env values + source SHA; rollback changes env values only; `git grep` proves no committed true/authoritative default at every gate); (4) every EXACT PromQL query uses a concrete range vector (`[5m]`, `[24h]`, or the concrete soak window `[168h]` for the 7-day G7-A soak — never a placeholder like `[soak]`); (5) Task 15 restated as PRECONDITION → VERIFY → EVIDENCE ending with NO TRACKED COMMIT — evidence external/untracked; (6) `RELEASE_REQUIRED_CHECKS_DISCOVERY` remains a mandatory release gate whose failure blocks a RELEASE CLAIM only, not docs-only planning or earlier-wave implementation.

**Historical Rev C (R2) changes in this wave (provenance):** (1) baseline corrected — finance tables are NOT "ENABLE-not-FORCE": 6 of 7 have NO RLS AT ALL (only `finance_invoice_number_sequences` is ENABLE+FORCE, `V20260820_6`), and the task now carries the full FORCE-RLS ACCESS-PATH MATRIX; (2) Task 2 freezes the FULL 51-table CRM list with per-table state (46 closures enumerated, 5 already conforming); (3) Task 3 — `authorization_version` backfill made MONOTONIC + canonical projection-backfill semantics; (4) NEW Task 6 creates `PartnerIsolationPostgresAcceptanceTest` (Rev B referenced it in the CI list but NO task created it — the phantom is resolved); (5) Task 7 — exact CI count lockstep rules; (6) G7-A carries a NUMERIC shadow soak (≥ 7 days AND ≥ 50,000 decisions, divergence budget 0, p99 regression ≤ 10 %); (7) every stage record now names the implemented metric, the exact query, and the evidence artifact; (8) security-hardening rollback is FORWARD-ONLY (no permissive-RLS compensating scripts); (9) all committed flag defaults OFF/legacy; (10) Task 15 — complete protected release chain incl. the BLOCKED required-checks discovery gate and the authenticated desktop/mobile + RTL/accessibility final-gate procedure.

## Goal

Harden the remaining fail-open surfaces, prove the §30/§31 invariants as exact-count CI acceptance gates, and execute an ordered, independently rollback-capable cutover of seven stages with per-stage evidence.

## Architecture

Security migrations are forward-only hardenings (FORCE RLS closes ENABLE-only tables; the vendor DO-loop permissive `crm_%` policies are DROPped and replaced fail-closed per `V20260905_5` doctrine — permissive policies OR-combine, so DROP is mandatory). Cutover is staged: each G7 stage flips exactly its own flag(s) in its own commit/deployment, after its own gates, with its own rollback trigger and smoke.

## Tech Stack

Java 21 · Spring Boot single-module Maven (`mvn`, cwd `apps/sanad-platform`) · Flyway (additive) · PostgreSQL 16 · GitHub Actions `.github/workflows/ci.yml` (pg-acceptance job with the inline Python `expected` map at lines 535–539 — there is NO `tests/ci/*.py` expected-count map and NO web job in ci.yml; web gates run in `.github/workflows/post-merge-verification.yml`) · Next.js `apps/web`.

## Spec

Spec §29.1 stage list (Revision D): G7-A Unified Authorization shadow/equivalence · G7-B Unified Authorization authoritative · G7-C Partner Principal + Delegated Administration · G7-D Commercial Identity · G7-E Partner Billing / Trial Continuation · G7-F Settlement · G7-G Notifications + Dashboards.

## Implementation Baseline

Repository evidence at `8d0d49c7` (REV C RE-MEASURED): `finance_accounts`, `finance_invoices`, `finance_invoice_lines`, `finance_payments`, `finance_journal_entries`, `finance_journal_lines` have NO RLS AT ALL (no ENABLE, no FORCE, no policy — `rg "ALTER TABLE .*finance_(accounts|invoices|invoice_lines|payments|journal_entries|journal_lines) ENABLE"` over `db/migration` returns nothing); `finance_invoice_number_sequences` ALREADY has ENABLE+FORCE (`V20260820_6`); the permissive-when-unset vendor loop is `db/vendor/postgresql/V20260730_1__enable_crm_rls.sql` (+ `V20260802_1` re-run) — `current_setting('app.tenant_id', true) IS NULL OR ...`; of the 51 `crm_%` tables, exactly 5 already have ENABLE+FORCE+conforming policy (`crm_contacts`, `crm_call_events`, `crm_entity_participants`, `crm_event_outbox` from their creation migrations + `crm_timeline_events` via `V20260822_2`); `ci.yml` pg-acceptance trio = `CommerceOrderPostgresConcurrencyTest:6, RbacAccessCheckPostgresAcceptanceTest:15, ModuleRegistryUatPostgresAcceptanceTest:10` (31 total, inline Python `expected` dict at lines 535–539 + `ALLOWED` dict at line 1058); the `crm` job runs `mvn test -B -ntp -Dtest='com.sanad.platform.crm.**.*IntegrationTest'` (16 classes); finance-table access paths OUTSIDE `com/sanad/platform/finance/`: `commerce/application/CommerceFinanceAdapter.java` (line 78 `INSERT INTO finance_invoices` + reads), `management/application/FinanceManagementIntegrationService.java` (6 direct reads against `finance_invoices`/`finance_payments`), `subscription/billing/application/BillingReconciliationService.java` (line 93 LEFT JOIN `finance_invoices`); GUC applier `TenantRlsTransactionContext.applyForCurrentTransaction(tenantId)` (used by `SubscriptionFinanceAdapter`, `BillingSettlementService`, `BillingReconciliationService`); `WorkflowDomainActionAuthorizer`, `ControlPlaneAccessService`, CRM authorization call sites currently use raw `evaluate()`; `application.yml` flag wiring precedents: `sanad.tenancy.billing.dunning-enabled: ${SANAD_DUNNING_ENABLED:false}` (line 146), `sanad.subscription.billing.provider.mode: ${R0C13_PROVIDER_MODE:DISABLED}` (lines 129–131); `RoleTemplateProvisioner` provisions registration templates; `V20260820_5` provenance columns + `V20260820_6` `role_template_bindings`; branch-protection required checks are UNDISCOVERABLE at planning time (GitHub API HTTP 403 unauthenticated; `gh` CLI absent) — `RELEASE_REQUIRED_CHECKS_DISCOVERY=BLOCKED`, never assumed.

## Global Constraints

1. DOCS-ONLY branch: not executed until the owner authorizes WAVE 7.
2. NO stage turns on more than its own flags; NO stage combines flag flips into one commit/deployment.
3. **Committed defaults OFF/legacy (Rev C):** every flag ships as `${ENV:...:false}` (or `SANAD_UAC_MODE: ${SANAD_UAC_MODE:legacy}`) in ALL committed `application*.yml` files for the entire program; stage "default true" exists ONLY as a deployment-time env/profile value — a `git grep -n "ENABLED:true\|enabled: true\|_MODE:authoritative" apps/sanad-platform/src/main/resources/` audit is run at EVERY stage gate and must return zero non-legacy `true` defaults in committed config.
4. **Same-SHA stage contract (Rev C):** every stage gate executes at ONE HEAD SHA — the evidence log records `STAGE_SHA=$(git rev-parse HEAD)` and `git status --short` must be clean at run time; any tracked commit between gate runs invalidates the stage evidence and restarts the FULL stage gate at the new SHA. Evidence artifacts live in `snad-evidence/` (untracked) or CI artifacts — never as tracked commits after the gate.
5. Protected-role seeding covers EXACTLY `PLATFORM_OWNER`, `PLATFORM_ADMIN`, `AGENT_SUPER_ADMIN`, `TENANT_ADMIN` — `AGENT_CUSTOM_ADMIN` is seeded (where the partner hierarchy needs it) as a NORMAL customizable partner-admin role, never entered into `protected_system_roles`.
6. Live payments remain NOT activated (`R0C13_PROVIDER_MODE` unchanged) — spec §19/§33.
7. Any red gate ⇒ cutover aborted, flags stay OFF, `EXECUTION_BLOCKER_REPORT` only.

## Review Focus

Per-stage gate completeness (precondition / same-SHA tests / negative security gates / observability / rollback flag / rollback trigger / post-enable smoke / evidence path); CI lockstep exactness; 4-role seeding.

---

Java paths relative to `apps/sanad-platform/src/main/java/com/sanad/platform/`; tests to `apps/sanad-platform/src/test/java/com/sanad/platform/`.

### Task 1: Finance tables FORCE RLS (6 tables with NO RLS today) + access-path matrix

Files:
- Create: `db/migration/V20260930_1__finance_tables_force_rls.sql`
- Test: `security/rls/FinanceForceRlsPostgresTest.java` (Create), `security/rls/FinanceAccessPathGucAuditTest.java` (Create — Rev C access-path matrix proof)

Interfaces:
- Consumes: `finance_accounts`, `finance_invoices`, `finance_invoice_lines`, `finance_payments`, `finance_journal_entries`, `finance_journal_lines` — REV C MEASURED BASELINE: NO RLS of any kind today (the Rev B "ENABLE-not-FORCE" claim was wrong); `finance_invoice_number_sequences` is EXCLUDED (already ENABLE+FORCE via `V20260820_6` — asserted, not re-altered).
- Produces: ENABLE + FORCE + fail-closed policy per table (V20260905_5 template verbatim) + the ACCESS-PATH MATRIX below proven GREEN before this migration may merge.

**REV C FINANCE FORCE-RLS ACCESS-PATH MATRIX (every path that touches the 6 tables must set `app.tenant_id` — and where applicable `app.partner_id`/`app.current_user_id` — BEFORE the migration merges; each row is a named proof in `FinanceAccessPathGucAuditTest`):**

| # | Access path | Tables touched | Current GUC discipline | Required proof |
|---|---|---|---|---|
| 1 | `finance/**` services + `finance/integration/SubscriptionFinanceAdapter` (Finance-owned) | all 6 | `TenantRlsTransactionContext.applyForCurrentTransaction(tenantId)` per transaction | NULL-GUC probe fails; adapter paths set GUC (existing tests green) |
| 2 | `finance/integration/FinanceCorrectionAdapter` + `finance/readmodel/CollectedCashReadAdapter` + `finance/principalinvoice/*` (W4/W5 Rev C ports) | all 6 / read set | same `applyForCurrentTransaction` idiom | same proof per adapter class |
| 3 | `commerce/application/CommerceFinanceAdapter` (line 78 INSERT + reads) | `finance_invoices` (+lines/sequences) | MUST set `app.tenant_id` before FORCE (audit test asserts the adapter applies `TenantRlsTransactionContext` — fix in this task if not) | write+read under GUC green as non-owner role |
| 4 | `management/application/FinanceManagementIntegrationService` (6 reads) | `finance_invoices`, `finance_payments` | MUST set `app.tenant_id` (management read path) | read under GUC green; NULL-GUC read returns 0 |
| 5 | `subscription/billing/application/BillingReconciliationService` (line 93 JOIN) + `BillingSettlementService` | `finance_invoices` (+payments) | `applyForCurrentTransaction` already used (verified) | R0C13G03/G06 integration classes green post-FORCE |
| 6 | `admin/service/BillingStateService` / `SaasAdministrationService` scheduled/admin contexts reaching finance reads | read set | set `app.tenant_id` from the tenant under administration | probe per call site |
| 7 | Every `@Scheduled` worker reaching paths 1–6 (`SlaMonitoringService`, `ExecutiveReportService`, `TrialExpirationService`, outbox workers, etc.) | transitively via services | background threads hold NO implicit bypass — each job's transaction must establish the tenant GUC (or use the Finance-owned port with explicit tenant argument) | per-worker NULL-GUC probe in the audit test |

- [ ] Step 1: exact failing test — for each of the 6 tables: with NULL GUCs ⇒ 0 rows for a NON-owner role (no policy) AND for the table owner (no FORCE) — Rev C red conditions reflect the no-RLS baseline; cross-tenant blocked; owner-context path with GUC set works; PLUS the access-path matrix proofs (table above) in `FinanceAccessPathGucAuditTest`.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.rls.FinanceForceRlsPostgresTest,com.sanad.platform.security.rls.FinanceAccessPathGucAuditTest test` → NULL-GUC reads return rows (no RLS today) ⇒ red.
- [ ] Step 3: exact minimal implementation — `V20260930_1__finance_tables_force_rls.sql`: for each of the six tables listed above: `ALTER TABLE finance_accounts ENABLE ROW LEVEL SECURITY; ALTER TABLE finance_accounts FORCE ROW LEVEL SECURITY; DROP POLICY IF EXISTS tenant_isolation ON finance_accounts; CREATE POLICY tenant_isolation ON finance_accounts FOR ALL USING (tenant_id::text = current_setting('app.tenant_id', true)) WITH CHECK (tenant_id::text = current_setting('app.tenant_id', true));` and the identical block repeated for `finance_invoices`, `finance_invoice_lines`, `finance_payments`, `finance_journal_entries`, `finance_journal_lines`; `finance_invoice_number_sequences` asserted ENABLE+FORCE and untouched; ANY matrix path that cannot set the GUC is fixed in its own class in this task (never by weakening the policy) — if a path is structurally unable to establish a tenant context, that path is BLOCKED and the migration does not merge until it is re-routed through a Finance-owned port.
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.subscription.billing.R0C13G03FinanceIntegrationPostgresTest,com.sanad.platform.subscription.billing.R0C13G06SettlementReconciliationPostgresTest test` → green (context-setting paths proven).
- [ ] Step 6: exact commit — `git commit -m "wave7(rls): finance tables enable+force rls fail-closed with access-path matrix (C1)"`.

### Task 2: CRM fail-open closure

Files:
- Create: `db/migration/V20260930_2__crm_fail_open_rls_closure.sql`
- Test: `security/rls/CrmRlsFailClosedPostgresTest.java` (Create)

Interfaces:
- Consumes: the vendor-DO-loop permissive `crm_%` subset (`V20260730_1`/`V20260802_1` — `current_setting('app.tenant_id', true) IS NULL OR tenant_id::text = current_setting('app.tenant_id', true)`, no FORCE).
- Produces: enumerated ENABLE/FORCE + `DROP POLICY IF EXISTS` + fail-closed policy for EVERY `crm_%` table with `tenant_id` — the table list is FROZEN in this plan (Rev C) from the live `information_schema` audit re-run at correction time, and the migration header ships the audit query as a commented verification block that MUST re-derive the same list at implementation; any drift between the frozen list and the live audit at implementation time is a BLOCKER, not a silent superset.

**FROZEN FULL CRM TABLE LIST (Rev C — 51 tables):** already conforming ENABLE+FORCE+conforming policy (5, NOT re-altered, only asserted): `crm_contacts`, `crm_call_events`, `crm_entity_participants`, `crm_event_outbox`, `crm_timeline_events` (the last via `V20260822_2`). Closed by THIS migration (46, each receiving ENABLE + FORCE + `DROP POLICY IF EXISTS tenant_isolation` + fail-closed `tenant_isolation` policy): `crm_account_addresses`, `crm_account_identifiers`, `crm_account_merge_history`, `crm_account_relationships`, `crm_account_status_history`, `crm_accounts`, `crm_activities`, `crm_assignments`, `crm_audit_logs`, `crm_capacity_plans`, `crm_cases`, `crm_communication_method_history`, `crm_communication_methods`, `crm_communication_policies`, `crm_contact_account_relationships`, `crm_contact_lookup_index`, `crm_contact_ownership_history`, `crm_contact_relationship_history`, `crm_contact_relationship_roles`, `crm_custom_field_definitions`, `crm_custom_field_values`, `crm_email_logs`, `crm_idempotency_records`, `crm_import_errors`, `crm_import_files`, `crm_import_jobs`, `crm_leads`, `crm_notes`, `crm_opportunities`, `crm_opportunity_stage_history`, `crm_party_address_history`, `crm_party_addresses`, `crm_phone_numbers`, `crm_pipeline_stages`, `crm_pipelines`, `crm_reports`, `crm_service_assignments`, `crm_shift_assignments`, `crm_shift_templates`, `crm_staff_availability`, `crm_staff_skills`, `crm_tag_assignments`, `crm_tags`, `crm_tasks`, `crm_transfers`, `crm_workload_assignments`.

- [ ] Step 1: exact failing test — for a REAL sample of the audited tables (Rev C correction — the Rev B sample named `crm_deals`, which does NOT exist; any `crm_%` table absent from the frozen list fails the test): at minimum `crm_accounts`, `crm_contacts`, `crm_leads`, `crm_opportunities`, `crm_activities`: NULL GUC ⇒ 0 rows for BOTH `sanad` (owner, because FORCE) and `crm_contact_rls_test_user`; cross-tenant blocked; PLUS a list-integrity assertion: `SELECT tablename FROM pg_tables WHERE tablename LIKE 'crm\_%'` at test time equals the frozen 51-name set (no new unlisted crm table, none missing).
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
- Produces: projection rows for all existing ACTIVE grants (canonical backfill semantics below); `authorization_version` MONOTONIC baseline; template `roles` rows for the 4 protected codes + `AGENT_CUSTOM_ADMIN` as a NON-protected hierarchy role where partner contexts need it; comment-only cutover-decision migration.

**CANONICAL PROJECTION-BACKFILL SEMANTICS (Rev C — binding for `effective_permission_projection` here and for the W6 dashboard projections by reference):** a backfill is (a) a DETERMINISTIC FULL REBUILD from the named source-of-truth queries (overrides + role capabilities + relationship policy — never an incremental guess), (b) IDEMPOTENT (running it twice yields row-identical results — proven by a run-twice count+checksum assertion), (c) VERSION-STAMPED (every produced row carries the `authorization_version` it was computed at, and the version advances monotonically — see below), (d) executed inside ONE migration with per-tenant GUC established before each tenant's rebuild, and (e) verified by a COUNT INVARIANT against the source query in the same migration (count mismatch ⇒ migration fails).

**`authorization_version` DETERMINISTIC RANKED-MAPPING BACKFILL (Revision D):** the migration MUST NOT depend on PostgreSQL UPDATE row execution order and MUST NOT assign `nextval()` inside an UPDATE. Canonical deterministic algorithm: (1) lock the relevant `users` set for the migration; (2) compute `base_version = max(existing authorization_version)`; (3) materialize a ranked mapping for rows still at zero — `WITH ranked AS MATERIALIZED (SELECT id, :base_version + ROW_NUMBER() OVER (ORDER BY created_at, id) AS assigned_version FROM users WHERE authorization_version = 0) UPDATE users u SET authorization_version = r.assigned_version FROM ranked r WHERE u.id = r.id;` — the migration assigns the PRECOMPUTED ranked value, never `nextval()` in an unordered UPDATE; (4) create/use `uac_authorization_version_seq` and position it to the maximum assigned/current version with correct `setval(..., is_called)` semantics; (5) every future invalidation bump uses `nextval('uac_authorization_version_seq')` (W1 Task 13 rewired in implementation to consume it), and the sequence position guarantees every next value is greater than every assigned version; (6) rerunning the migration changes no non-zero version (WHERE authorization_version = 0 — never a renumbering of existing non-zero versions); (7) tests prove ordering, uniqueness/monotonicity, rerun idempotency, and sequence-next-value greater than every assigned version. An equivalent deterministic CTE/temp-table form is acceptable as long as the precomputed ranked value is assigned.

- [ ] Step 1: exact failing test — backfill: after migration, projection row exists per ACTIVE grant; count invariant vs source query; `users.authorization_version` = the assigned monotonic values (non-zero, strictly increasing with `(created_at, id)` order, re-run changes nothing — idempotency assertion); rebuild-twice yields identical row checksums. Seeding: `protected_system_roles` still contains EXACTLY 4 codes; template roles exist with `role_origin='SNAD_TEMPLATE'` for the 4 protected codes; `AGENT_CUSTOM_ADMIN` role rows (partner contexts) exist WITHOUT any `protected_system_roles` entry (spec §5 Rev C).
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.access.evaluation.ProjectionBackfillPostgresTest,com.sanad.platform.security.authorization.ProtectedSeedFourRolesPostgresTest test` → projection empty / 5-code or missing-AGENT_CUSTOM_ADMIN drift (red).
- [ ] Step 3: exact minimal implementation — the three migrations (backfill DO-loop sets `app.tenant_id` per tenant and executes the canonical rebuild + monotonic version assignment per the semantics block above; seeding inserts where missing; `V20260930_5` records the cutover decision, stage list, flag defaults, rollback runbook pointer — no schema change).
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
- [ ] Step 3: exact minimal implementation — EXPLICITLY CREATE the listed parameterized acceptance class `security/invariants/SecurityInvariantsPostgresAcceptanceTest.java` and its fixtures (this task owns the class's creation; no product-code change is expected, but the class MUST exist after this task — `none` is not an acceptable state while the class is absent). Any acceptance case that turns red after the class exists is fixed in the OWNING earlier-wave behavior, the owning regression is rerun, and the acceptance class is rerun at the same candidate SHA.
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
- [ ] Step 3: exact minimal implementation — EXPLICITLY CREATE the two listed acceptance classes `security/breakglass/BreakGlassInvariantPostgresAcceptanceTest.java` + `access/matrix/AuthorizationMatrixPostgresAcceptanceTest.java` with their parameterized fixtures (this task owns the classes' creation; no product-code change is expected, but the classes MUST exist after this task — `none` is not an acceptable state while the classes are absent). Any acceptance case that turns red after the classes exist is fixed in the OWNING earlier-wave behavior, the owning regression is rerun, and the acceptance classes are rerun at the same candidate SHA.
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && SPRING_PROFILES_ACTIVE=pg-acceptance PG_ACCEPTANCE_JDBC_URL='jdbc:postgresql://127.0.0.1:5432/pg_acceptance?prepareThreshold=0' PG_ACCEPTANCE_USERNAME=sanad PG_ACCEPTANCE_PASSWORD=sanad_pass mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.rls.RbacAccessCheckPostgresAcceptanceTest test` → 15/15.
- [ ] Step 6: exact commit — `git commit -m "wave7(acceptance): break-glass rejections + authorization matrix (C4)"`.

### Task 6: Partner isolation acceptance class (Rev C — resolves the Rev B phantom reference)

Files:
- Create: `partner/isolation/PartnerIsolationPostgresAcceptanceTest.java`
- Modify: none
- Test: this class

Interfaces:
- REV C FINDING: Rev B added `PartnerIsolationPostgresAcceptanceTest` to the W7 CI-lockstep `-Dtest` lists and count maps, but NO task in any wave ever created the class (verified — zero occurrences in the repository at the base SHA). This task creates it so the CI lockstep consumes a REAL class.
- Consumes: W2 isolation guards + W4/W5 billing/settlement partner scoping; pg-acceptance profile.
- Produces: the partner-isolation acceptance battery: Partner A → Partner B tenant data DENY; partner invoice for foreign tenant 403; `?partnerId=` forging 403; partner cannot read PLATFORM-owner deliveries (with W6); partner cannot read another partner's settlement periods; partner cannot acknowledge another recipient's delivery.

- [ ] Step 1: exact failing test — the six parameterized negatives above, each asserting the concrete denial path (SQLState/HTTP status/empty result) on the pg-acceptance profile.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && SPRING_PROFILES_ACTIVE=pg-acceptance PG_ACCEPTANCE_JDBC_URL='jdbc:postgresql://127.0.0.1:5432/pg_acceptance?prepareThreshold=0' PG_ACCEPTANCE_USERNAME=sanad PG_ACCEPTANCE_PASSWORD=sanad_pass mvn -B -ntp -Dsurefire.useFile=true -DfailIfNoTests=true -Dtest=com.sanad.platform.partner.isolation.PartnerIsolationPostgresAcceptanceTest test` → class absent (red).
- [ ] Step 3: exact minimal implementation — EXPLICITLY CREATE the listed acceptance class `partner/isolation/PartnerIsolationPostgresAcceptanceTest.java` with its parameterized fixtures assembled from the W2/W4/W5/W6 guards (this task owns the class's creation; no product-code change is expected, but the class MUST exist after this task — `none` is not an acceptable state while the class is absent). Any red case is fixed in the OWNING wave's class, the owning regression is rerun, and the acceptance class is rerun at the same candidate SHA.
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — W2 isolation classes (`PartnerPrincipalRlsPostgresTest`, `PartnerMembershipConcurrencyPostgresTest`) → green.
- [ ] Step 6: exact commit — `git commit -m "wave7(acceptance): partner isolation acceptance battery (C4)"`.

### Task 7: CI lockstep — ci.yml inline expected map

Files:
- Modify: `.github/workflows/ci.yml` (pg-acceptance `-Dtest=` list += `PartnerIsolationPostgresAcceptanceTest,SecurityInvariantsPostgresAcceptanceTest,BreakGlassInvariantPostgresAcceptanceTest,AuthorizationMatrixPostgresAcceptanceTest`; the INLINE Python `expected` dict (lines 535–539) and the `r0c12-canonical-gate-g` `ALLOWED` dict (line 1058) gain the four new per-class counts — counts DERIVED mechanically from the surefire XML of the same run in the same commit, never invented); no `tests/ci/*.py` map exists — do not invent one
- Test: `tests/ci/test_production_readiness.py`, `tests/ci/test_workflow_final_closure_unified.py` (existing governance tests, run unchanged)

Interfaces:
- Consumes: the exact-count gate doctrine (F-C risk 5).
- Produces: CI failing on count drift.

- [ ] Step 1: exact failing test — run the governance tests BEFORE the ci.yml edit with the intended new list absent: `cd /home/z/my-project/SNAD && python3 tests/ci/test_production_readiness.py && python3 tests/ci/test_workflow_final_closure_unified.py` — the lockstep invariant is enforced by the workflow-summary step itself; a count not matching machine output ⇒ CI red (this is the designed failure mode; the pre-edit state is simply the current green baseline recorded in the evidence log).
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && rm -rf target/surefire-reports && SPRING_PROFILES_ACTIVE=pg-acceptance PG_ACCEPTANCE_JDBC_URL='jdbc:postgresql://127.0.0.1:5432/pg_acceptance?prepareThreshold=0' PG_ACCEPTANCE_USERNAME=sanad PG_ACCEPTANCE_PASSWORD=sanad_pass mvn test -B -ntp -Dsurefire.useFile=true -DfailIfNoTests=true -Dtest='CommerceOrderPostgresConcurrencyTest,RbacAccessCheckPostgresAcceptanceTest,ModuleRegistryUatPostgresAcceptanceTest,PartnerIsolationPostgresAcceptanceTest,SecurityInvariantsPostgresAcceptanceTest,BreakGlassInvariantPostgresAcceptanceTest,AuthorizationMatrixPostgresAcceptanceTest'` → the new classes' counts are read mechanically from `target/surefire-reports/*.xml` (e.g. `grep -h 'tests=' target/surefire-reports/TEST-*.xml`) BEFORE the map is updated.
- [ ] Step 3: exact minimal implementation — the ci.yml edit: add the 4 classes to both `-Dtest` lists and set each `expected`/`ALLOWED` entry to the machine-derived count; totals asserted by the existing inline Python summary step. EXACT LOCKSTEP RULE (Rev C): the SAME commit must make the three touch-points 1:1 — (i) the `-Dtest=` list of the pg-acceptance job, (ii) the `expected` dict, (iii) the `ALLOWED` dict — verified in the same commit by `rg -c "PartnerIsolationPostgresAcceptanceTest|SecurityInvariantsPostgresAcceptanceTest|BreakGlassInvariantPostgresAcceptanceTest|AuthorizationMatrixPostgresAcceptanceTest" .github/workflows/ci.yml` returning an even, balanced count across exactly those three locations, and `git diff --stat` for the commit touching ONLY `.github/workflows/ci.yml` plus the surefire-derived evidence log; a count that cannot be derived from surefire XML in the same run is NEVER committed (no invented number ships, even provisionally).
- [ ] Step 4: exact command proving GREEN — the same trio-extended command as Step 2 → all 7 classes green with counts equal to the map; then `cd /home/z/my-project/SNAD && python3 tests/ci/test_production_readiness.py && python3 tests/ci/test_workflow_final_closure_unified.py` → green.
- [ ] Step 5: exact affected regression — `cd /home/z/my-project/SNAD && python3 tests/ci/test_workflow_security_policy.py` → green (workflow security policy intact). If that file does not exist at implementation time, substitute `python3 tests/ci/test_workflow_y2_production_orchestrator.py` and record the substitution in the evidence log.
- [ ] Step 6: exact commit — `git commit -m "wave7(ci): pg-acceptance gate lockstep with machine-derived counts (C5)"`.

### Task 8: STAGE G7-A — Unified Authorization shadow/equivalence

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
- Precondition: Tasks 1–7 merged; pg-acceptance extended gate green.
- Same-SHA tests: `ShadowEquivalencePostgresTest` + full battery at the stage commit.
- Negative security gates: DENY-dominance classes green in shadow (`DenyDominancePostgresTest`, W1 Task 11).
- Observability (Rev C — implementation / exact query / evidence): metric `sanad_authz_shadow_divergence_total` (Micrometer counter, implemented in this task) + `sanad_authz_shadow_evaluations_total` + `sanad_authz_decision_latency_seconds` histogram; EXACT scrape: `rate(sanad_authz_shadow_divergence_total[5m])` and `histogram_quantile(0.99, rate(sanad_authz_decision_latency_seconds_bucket[5m]))` captured as DAILY rows (timestamp, both metric values, run SHA) in `snad-evidence/g7a-shadow.log`.
- NUMERIC SHADOW SOAK (Rev C — all four must hold): (i) duration ≥ 7 consecutive calendar days of production-shadow traffic; (ii) volume: `increase(sanad_authz_shadow_evaluations_total[168h])` ≥ 50,000 evaluated decisions (concrete 7-day range vector `168h`; the runbook substitutes the concrete window actually used — placeholder windows like `[soak]` are forbidden); (iii) divergence: `increase(sanad_authz_shadow_divergence_total[168h])` == 0 EXACTLY; (iv) latency: authz p99 regression ≤ 10 % vs the pre-stage baseline measured over the SAME window (baseline captured before enabling shadow).
- Rollback flag: `SANAD_UAC_MODE=legacy` (env; instant).
- Rollback trigger: any divergence > 0 during soak, or `authz` p99 latency regression > 10 % vs baseline.
- Post-enable smoke: `/api/v1/access/effective-permissions` 200 with owner token; one `@RequireCapability` CRUD path green.
- Evidence path: `snad-evidence/g7a-<sha>.log` (with the daily soak rows + the numeric gate evaluation).

### Task 9: STAGE G7-B — Unified Authorization authoritative

Files:
- Modify: `WorkflowDomainActionAuthorizer.java`, `ControlPlaneAccessService.java`, CRM party/collaboration authorization call sites (switch from raw `evaluate()` to `evaluateDetailed()` — behavior-preserving, deny reasons enriched; HR scope adapters NO change — already port-based via `ScopedAuthorizationService`)
- Test: rerun of the FULL battery at the stage commit (no new test class; equivalence already proven in G7-A)

Interfaces:
- Consumes: G7-A zero-divergence evidence.
- Produces: `SANAD_UAC_MODE=authoritative` — pipeline serves decisions; legacy `evaluate()` remains as facade.

- [ ] Step 1: exact failing test — none new; the gate is the G7-A ledger + full battery.
- [ ] Step 2: exact command proving RED — none (stage task).
- [ ] Step 3: exact minimal implementation — call-site switches ONLY (behavior-preserving, deny reasons enriched). NO committed config change: every committed `application*.yml` value remains `legacy`/false; the stage transition sets `SANAD_UAC_MODE=authoritative` as a DEPLOYMENT-ENVIRONMENT value outside the repository (env-overridable back to `shadow`/`legacy`); the stage evidence records the deployment environment values and the source SHA; rollback changes environment values only.
- [ ] Step 4: exact command proving GREEN — full battery at stage SHA:
  `cd apps/web && npm ci && npm run lint && npx tsc --noEmit && npm test -- --reporter=verbose && npm run build`
  `cd apps/sanad-platform && mvn test -B -ntp -Dsurefire.useFile=false`
  `cd apps/sanad-platform && SPRING_PROFILES_ACTIVE=pg-acceptance PG_ACCEPTANCE_JDBC_URL='jdbc:postgresql://127.0.0.1:5432/pg_acceptance?prepareThreshold=0' PG_ACCEPTANCE_USERNAME=sanad PG_ACCEPTANCE_PASSWORD=sanad_pass mvn test -B -ntp -Dsurefire.useFile=true -DfailIfNoTests=true -Dtest='CommerceOrderPostgresConcurrencyTest,RbacAccessCheckPostgresAcceptanceTest,ModuleRegistryUatPostgresAcceptanceTest,PartnerIsolationPostgresAcceptanceTest,SecurityInvariantsPostgresAcceptanceTest,BreakGlassInvariantPostgresAcceptanceTest,AuthorizationMatrixPostgresAcceptanceTest'` → 7-class extended gate green with the Task 7 map.
- [ ] Step 5: exact affected regression — the battery.
- [ ] Step 6: exact commit — `git commit -m "wave7(cutover): G7-B unified authorization authoritative (C7)"`.

Stage record (G7-B):
- Precondition: G7-A evidence with `sanad_authz_shadow_divergence_total == 0` across the soak window.
- Same-SHA tests: 7-class extended pg-acceptance gate + full battery.
- Negative security gates: `SecurityInvariantsPostgresAcceptanceTest` 10/10; `DenyDominancePostgresTest` green.
- Observability (Rev C): implementation `sanad_uac_authoritative_decisions_total` counter + decision-latency histogram (added with the call-site switches); EXACT query `rate(sanad_uac_authoritative_decisions_total[5m])` and deny-reason distribution `sum by (reason) (rate(sanad_uac_denials_total[5m]))`; evidence `snad-evidence/g7b-decisions.log` (per-interval rows + run SHA).
- Rollback flag: `SANAD_UAC_MODE=legacy` (instant revert; shadow keeps parity data flowing).
- Rollback trigger: any §30 invariant red, or unexplained ALLOW-expansion incident.
- Post-enable smoke: owner + tenant-admin + partner-admin happy paths; one deliberate DENY verified per boundary.
- Evidence path: `snad-evidence/g7b-<sha>.log`.

### Task 10: STAGE G7-C — Partner Principal + Delegated Administration

Files:
- Modify: none (the flag plumbing `sanad.partner.enabled: ${SANAD_PARTNER_ENABLED:false}` was committed ONCE with its false/legacy default in W2; this stage commits no config flip — activation is deployment-env only)
- Test: `partner/api/PartnerCutoverSmokeIT.java` (Create — thin smoke: mint partner user, bind tenant, delegated TENANT.ACTIVATE, forbidden cross-partner read)

Interfaces:
- Consumes: W2 full task set; G7-B evidence.
- Produces: partner surfaces live.

- [ ] Step 1: exact failing test — smoke IT red until flag-on path exercised end-to-end (created only in this task).
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5433/sanad SPRING_DATASOURCE_USERNAME=sanad SPRING_DATASOURCE_PASSWORD=sanad_pass mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.partner.api.PartnerCutoverSmokeIT test` → smoke assertions fail with flag off.
- [ ] Step 3: exact minimal implementation — smoke IT only (the stage's activation is `SANAD_PARTNER_ENABLED=true` as a DEPLOYMENT-ENVIRONMENT value outside the repository; committed config stays false; evidence records the deployment env values + source SHA; rollback changes env values only).
- [ ] Step 4: exact command proving GREEN — same as Step 2 with the deployment env value `SANAD_PARTNER_ENABLED=true` (env only — no committed default changes) → green.
- [ ] Step 5: exact affected regression — W2 isolation classes: `PartnerPrincipalRlsPostgresTest`, `PartnerMembershipConcurrencyPostgresTest`, `ProtectedRoleGrantGuardTest` → green.
- [ ] Step 6: exact commit — `git commit -m "wave7(cutover): G7-C partner principal live (C8)"`.

Stage record (G7-C):
- Precondition: G7-B evidence; W2 exit battery green.
- Same-SHA tests: smoke IT + W2 regression list at stage SHA.
- Negative security gates: `PARTNER_SCOPE_MISMATCH` 403 path; `ProtectedRoleGrantGuardTest`; membership concurrency class.
- Observability (Rev C): implementation `sanad_partner_surface_requests_total` + `sanad_partner_scope_mismatch_total` counters (wired in the partner filter/gate); EXACT query `sum by (code) (rate(sanad_partner_scope_mismatch_total[5m]))`; evidence `snad-evidence/g7c-mismatch.log`.
- Rollback flag: `SANAD_PARTNER_ENABLED=false` (instant; surfaces 404).
- Rollback trigger: any cross-partner data leak signal, or FK/orphan anomaly.
- Post-enable smoke: partner portal login → `/api/v1/partner/me` 200; bound-tenant list non-empty for seeded partner.
- Evidence path: `snad-evidence/g7c-<sha>.log`.

### Task 11: STAGE G7-D — Commercial Identity

Files:
- Modify: none (the flag plumbing `sanad.commercial.identity-enabled: ${SANAD_COMMERCIAL_IDENTITY_ENABLED:false}` was committed ONCE with its false/legacy default in W3; this stage commits no config flip — activation is deployment-env only)
- Test: `commercial/api/CommercialCutoverSmokeIT.java` (Create — tenant profile PUT, partner logo REGISTRATION of an already-stored object via `source_module='COMMERCIAL'` (Rev C W3 scope — no byte upload path exists), executive verification transition)

Interfaces:
- Consumes: W3 full task set.
- Produces: commercial identity surfaces live.

- [ ] Step 1: exact failing test — smoke IT red with flag off.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5433/sanad SPRING_DATASOURCE_USERNAME=sanad SPRING_DATASOURCE_PASSWORD=sanad_pass mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.commercial.api.CommercialCutoverSmokeIT test` → red.
- [ ] Step 3: exact minimal implementation — smoke IT only (activation is `SANAD_COMMERCIAL_IDENTITY_ENABLED=true` as a DEPLOYMENT-ENVIRONMENT value outside the repository; committed config stays false; evidence records the deployment env values + source SHA; rollback changes env values only).
- [ ] Step 4: exact command proving GREEN — same as Step 2 with the deployment env value `SANAD_COMMERCIAL_IDENTITY_ENABLED=true` (env only) → green.
- [ ] Step 5: exact affected regression — `WorkflowAttachmentExternalFoundationTest` (shared platform_files intact) + `PlatformFilesForceRlsPostgresTest` → green.
- [ ] Step 6: exact commit — `git commit -m "wave7(cutover): G7-D commercial identity live (C8)"`.

Stage record (G7-D):
- Precondition: G7-C evidence.
- Same-SHA tests: smoke IT + W3 regression list.
- Negative security gates: `PlatformFilesForceRlsPostgresTest`; principal-uniqueness matrix (W3 Task 1).
- Observability (Rev C): implementation `sanad_commercial_uploads_total` counter + RLS-denial counter `sanad_rls_denials_total{table="platform_files"}`; EXACT query `rate(sanad_commercial_uploads_total[5m])`, `sum by (table) (rate(sanad_rls_denials_total[5m]))`; evidence `snad-evidence/g7d-uploads.log`.
- Rollback flag: `SANAD_COMMERCIAL_IDENTITY_ENABLED=false`.
- Rollback trigger: any platform_files regression, or ambiguous-ownership insertion attempt succeeding.
- Post-enable smoke: tenant settings screen save + logo reference registration + executive verify.
- Evidence path: `snad-evidence/g7d-<sha>.log`.

### Task 12: STAGE G7-E — Partner Billing / Trial Continuation

Files:
- Modify: none (the flag plumbing `sanad.trial-continuation.enabled: ${SANAD_TRIAL_CONTINUATION_ENABLED:false}` + `sanad.partner-billing.enabled: ${SANAD_PARTNER_BILLING_ENABLED:false}` was committed ONCE with false/legacy defaults in W4; this stage commits no config flip — BOTH flags activate as deployment-env values, ONE subsystem, ONE stage)
- Test: `partner/billing/BillingCutoverSmokeIT.java` (Create — continuation confirm → ACTIVE_BILLABLE → five-condition automatic invoice → markInvoicePaid rejection on partner invoice)

Interfaces:
- Consumes: W4 full task set.
- Produces: billing surfaces + schedulers live.

- [ ] Step 1: exact failing test — smoke IT red with flags off.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5433/sanad SPRING_DATASOURCE_USERNAME=sanad SPRING_DATASOURCE_PASSWORD=sanad_pass mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.partner.billing.BillingCutoverSmokeIT test` → red.
- [ ] Step 3: exact minimal implementation — smoke IT only (both flags activate as DEPLOYMENT-ENVIRONMENT values outside the repository; committed config stays false/legacy; evidence records the deployment env values + source SHA; rollback changes env values only).
- [ ] Step 4: exact command proving GREEN — same as Step 2 with both deployment env values true (env only — no committed default changes) → green.
- [ ] Step 5: exact affected regression — `AutomaticBillingPostgresTest`, `PartnerInvoiceGuardPostgresTest`, `TrialContinuationSchedulerPostgresTest` → green.
- [ ] Step 6: exact commit — `git commit -m "wave7(cutover): G7-E partner billing live (C9)"`.

Stage record (G7-E):
- Precondition: G7-D evidence; W4 exit battery green.
- Same-SHA tests: smoke IT + W4 regression list.
- Negative security gates: `TRIAL_NOT_BILLABLE` path; `PARTNER_INVOICE_FINANCE_AUTHORITY` 409; five-condition negatives (Task 17 of W4).
- Observability (Rev C): implementation `sanad_billing_scheduler_runs_total` + `sanad_invoice_issuance_total` counters + `sanad_finance_mirror_lag_seconds` gauge; EXACT queries `rate(sanad_billing_scheduler_runs_total[5m])`, `rate(sanad_invoice_issuance_total[5m])`, `max(sanad_finance_mirror_lag_seconds)`; evidence `snad-evidence/g7e-mirror-lag.log` (scheduler runs, issuance counts, mirror-lag samples with SHAs).
- Rollback flag: `SANAD_TRIAL_CONTINUATION_ENABLED=false` + `SANAD_PARTNER_BILLING_ENABLED=false` (schedulers no-op instantly).
- Rollback trigger: any Finance mirror mismatch, or trial invoice without confirmation.
- Post-enable smoke: seeded trial → confirm → automatic invoice exactly once; `markInvoicePaid` on it ⇒ 409.
- Evidence path: `snad-evidence/g7e-<sha>.log`.

### Task 13: STAGE G7-F — Settlement

Files:
- Modify: none (the flag plumbing `sanad.settlement.enabled: ${SANAD_SETTLEMENT_ENABLED:false}` was committed ONCE with its false/legacy default in W5; this stage commits no config flip — activation is deployment-env only)
- Test: `partner/settlement/SettlementCutoverSmokeIT.java` (Create — calculate → replay no-op → new-key replace → approve → finalize → SANAD invoice issued via platform sequence → reconcile green)

Interfaces:
- Consumes: W5 full task set.
- Produces: settlement live.

- [ ] Step 1: exact failing test — smoke IT red with flag off.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5433/sanad SPRING_DATASOURCE_USERNAME=sanad SPRING_DATASOURCE_PASSWORD=sanad_pass mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.partner.settlement.SettlementCutoverSmokeIT test` → red.
- [ ] Step 3: exact minimal implementation — smoke IT only (activation is `SANAD_SETTLEMENT_ENABLED=true` as a DEPLOYMENT-ENVIRONMENT value outside the repository; committed config stays false; evidence records the deployment env values + source SHA; rollback changes env values only).
- [ ] Step 4: exact command proving GREEN — same as Step 2 with the deployment env value `SANAD_SETTLEMENT_ENABLED=true` (env only) → green.
- [ ] Step 5: exact affected regression — `SettlementReplayReplacePostgresTest`, `SettlementFinalizeConcurrencyPostgresTest`, `LateAdjustmentPostgresTest` → green.
- [ ] Step 6: exact commit — `git commit -m "wave7(cutover): G7-F settlement live (C9)"`.

Stage record (G7-F):
- Precondition: G7-E evidence; W5 exit battery green.
- Same-SHA tests: smoke IT + W5 regression list.
- Negative security gates: partner finalize 403; `PERIOD_CURRENCY_MIX`; manual-payment exclusion.
- Observability (Rev C): implementation `sanad_settlement_reconcile_drift_total` counter + finalize counter `sanad_settlement_finalizations_total`; the reconciliation report artifact is written PER RUN to `snad-evidence/g7f-reconcile-<runId>.log` (path recorded in the evidence log); EXACT query `increase(sanad_settlement_reconcile_drift_total[24h]) == 0` as the drift identity; evidence `snad-evidence/g7f-<sha>.log` + per-run artifacts.
- Rollback flag: `SANAD_SETTLEMENT_ENABLED=false`.
- Rollback trigger: reconciliation drift, or any finalized-period mutation signal.
- Post-enable smoke: one full period cycle on seeded data ending in reconcile green.
- Evidence path: `snad-evidence/g7f-<sha>.log`.

### Task 14: STAGE G7-G — Notifications + Dashboards

Files:
- Modify: none (the flag plumbing `sanad.notifications.enabled: ${SANAD_NOTIFICATIONS_ENABLED:false}` + `sanad.dashboards.enabled: ${SANAD_DASHBOARDS_ENABLED:false}` was committed ONCE with false/legacy defaults in W6; this stage commits no config flip — activation is deployment-env only)
- Test: `dashboard/DashboardCutoverSmokeIT.java` (Create — owner notification present for a partner mutation; global dashboard reconciles on the seeded DIRECT+Σ(PARTNERS) fixture; partner self-dashboard claim-scoped)

Interfaces:
- Consumes: W6 full task set.
- Produces: notifications + dashboards live; cutover complete.

- [ ] Step 1: exact failing test — smoke IT red with flags off.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5433/sanad SPRING_DATASOURCE_USERNAME=sanad SPRING_DATASOURCE_PASSWORD=sanad_pass mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.dashboard.DashboardCutoverSmokeIT test` → red.
- [ ] Step 3: exact minimal implementation — smoke IT only (both flags activate as DEPLOYMENT-ENVIRONMENT values outside the repository; committed config stays false; evidence records the deployment env values + source SHA; rollback changes env values only).
- [ ] Step 4: exact command proving GREEN — same as Step 2 with both deployment env values true (env only — no committed default changes) → green.
- [ ] Step 5: exact affected regression — `DashboardReconciliationPostgresTest`, `NotificationIsolationPostgresTest`, `MandatoryOwnerNotificationPostgresTest` → green.
- [ ] Step 6: exact commit — `git commit -m "wave7(cutover): G7-G notifications and dashboards live (C10)"`.

Stage record (G7-G):
- Precondition: G7-F evidence; W6 exit battery green.
- Same-SHA tests: smoke IT + W6 regression list.
- Negative security gates: forged `?partnerId=` 403; cross-recipient ack denial; partner cannot read owner deliveries.
- Observability (Rev C): implementation `sanad_projection_freshness_seconds` gauge (per projection table) + the reconciliation identity metric `sanad_dashboard_reconciliation_identity` (1/0 boolean computed by the W6 Task 7 reconciliation check); EXACT queries `max by (table) (sanad_projection_freshness_seconds)` and `sanad_dashboard_reconciliation_identity == 1`; evidence `snad-evidence/g7g-freshness.log` (freshness samples + identity evaluations with SHAs).
- Rollback flag: `SANAD_NOTIFICATIONS_ENABLED=false` + `SANAD_DASHBOARDS_ENABLED=false`.
- Rollback trigger: reconciliation identity false, or notification cross-scope leak.
- Post-enable smoke: owner feed shows a fresh partner mutation; global vs per-partner drill-down spot check.
- Evidence path: `snad-evidence/g7g-<sha>.log`.

### Task 15: Release evidence bundle + final verification runbook

Files:
- Modify: none (evidence only)
- Test: full runbook execution

Interfaces:
- Consumes: all stage logs; CI.
- Produces: `snad-evidence/evidence-<sha>.log` + CI run URLs.

- [ ] Step 1: PRECONDITION — the release-candidate SHA exists with `git status --short` clean and every stage gate G7-A..G7-G closed; all stage logs present under `snad-evidence/`.
- [ ] Step 2: VERIFY — no new test is invented for this task (verification/evidence task class); the runbook below IS the verification.
- [ ] Step 3: EVIDENCE ARTIFACT PLAN — one untracked bundle `snad-evidence/evidence-<sha>.log` + CI run URLs; no tracked file is created or modified by this task.
- [ ] Step 4: exact command proving GREEN — executed in order, all at ONE HEAD SHA:
  1. `cd apps/sanad-platform && mvn test -B -ntp -Dsurefire.useFile=false` (full suite green, count recorded).
  2. `cd apps/sanad-platform && mvn test -B -ntp -Dtest='com.sanad.platform.crm.**.*IntegrationTest' test` (green).
  3. pg-acceptance job (7-class extended gate green; new expected total enforced).
  4. `cd apps/web && npm ci && npm run lint && npx tsc --noEmit && npm test -- --reporter=verbose && npm run build` (green).
  5. `cd apps/sanad-platform && SPRING_PROFILES_ACTIVE=pg-acceptance PG_ACCEPTANCE_JDBC_URL='jdbc:postgresql://127.0.0.1:5432/pg_acceptance?prepareThreshold=0' PG_ACCEPTANCE_USERNAME=sanad PG_ACCEPTANCE_PASSWORD=sanad_pass mvn test -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.dashboard.DashboardReconciliationPostgresTest test` + live reconcile report attached.
  6. Billing/settlement matrices (W4 Tasks 14–18, W5 Tasks 4–13 classes) green at the same SHA.
  7. Authenticated UI FINAL GATE (Rev C — desktop + mobile + RTL/accessibility, all AUTHENTICATED via the existing `apps/web/e2e/crm-auth-session.ts` helper — no anonymous runs count):
     a. Desktop authenticated suite: `cd apps/web && npx playwright test --config=playwright.standard.config.ts --reporter=list` (green — includes the accessibility specs `crm-accessibility-ci.spec.ts` using `@axe-core/playwright` and the RBAC/authenticated acceptance specs).
     b. Mobile authenticated viewport matrix: the same command with the mobile projects of `playwright.standard.config.ts` (viewport 390×844 / 414×896) green; screenshots land in the run artifact.
     c. RTL/unit component gates: `npm test` (vitest + `@testing-library/react` suite, 48+ component test files) green; i18n key parity `python3 scripts/ci/check_i18n_keys.py` green.
     d. Accessibility axe evidence: the axe-core violation report (0 critical/serious violations on the covered authenticated pages) attached from the CI artifact of the same SHA.
  8. Exact-HEAD CI run on the release branch: all required checks green. REV C PROTECTED RELEASE CHAIN (complete, ordered):
     (a) REQUIRED-CHECKS DISCOVERY GATE — the release owner (or a GH admin/token holder) runs `GET /repos/snadaiapp-png/SNAD/branches/<release-branch>/protection` with an authenticated token and records the required-check set into `snad-evidence/required-checks.json` BEFORE any release claim. REVISION D RULE: failure to discover the current protected-branch requirements blocks a RELEASE CLAIM only — never docs-only planning or implementation of earlier waves. PLANNING-TIME STATUS: `RELEASE_REQUIRED_CHECKS_DISCOVERY=BLOCKED` (GitHub API HTTP 403 unauthenticated; no `gh` CLI) — the current required-check set is NOT known and is NEVER assumed; the CI job names `test`, `crm`, `pg-acceptance` (extended map), `r0c12-canonical-gate-g`, and the web gates of `.github/workflows/post-merge-verification.yml` are recorded as CANDIDATES pending discovery, not as required checks.
     (b) merge to the release branch only after (a) has recorded evidence;
     (c) all DISCOVERED required checks green at the release SHA (not merely the candidate set);
     (d) `post-merge-verification.yml` web gates green;
     (e) production smoke (`production-smoke.yml` / `backend-production-smoke.yml`) green;
     (f) the merge base re-verified: `git merge-base` of the release branch and current `origin/main` recorded, and the 7-class pg-acceptance gate re-run on the MERGED head (master risk M5) before any release claim.
- [ ] Step 5: exact affected regression — the runbook IS the regression.
- [ ] Step 6: **NO TRACKED COMMIT — evidence external/untracked; report STAGE_SHA/release SHA** — the entire runbook executes at ONE identical HEAD SHA; any tracked commit after the run invalidates the evidence and the FULL runbook must be re-run at the new SHA. Evidence artifacts live ONLY in `snad-evidence/` (untracked) or CI artifacts — never as tracked commits after the gate.

## Security implications, rollback, failure semantics

**Security:** §30 becomes release-blocking in CI (exact-count, fail-closed); finance ENABLE+FORCE RLS (6 tables hardened from a NO-RLS baseline) + CRM fail-open closure shrink the fail-open surface to zero for tenant-scoped data reachable by the `sanad` role; the 4-code protected registry is enforced end-to-end with `AGENT_CUSTOM_ADMIN` never protected. **Rollback (Rev C — FORWARD-ONLY for security hardening):** each G7 stage's FLAG reverts instantly (all waves flag-gated) — `SANAD_UAC_MODE=legacy`, `SANAD_*_ENABLED=false` — and that is the ONLY runtime rollback axis. `V20260930_1/_2` (and every FORCE-RLS/immunity trigger migration in the program) are FORWARD-ONLY: there is NO down-migration and NO compensating script that re-enables permissive or fail-open RLS, re-drops FORCE, or restores pre-hardening policy state — such a script would deliberately reintroduce the vulnerability and is FORBIDDEN in all repositories, runbooks, and owner tooling. If a hardening migration causes a regression, the fix is ANOTHER FORWARD migration or a flag-level revert of the consuming stage, never a weakening of the policy; emergency access goes through the §5.1 break-glass grant (time-boxed, audited, DENY-dominated), not through RLS surgery. No destructive DDL anywhere in the program. **Failure semantics:** any red gate ⇒ cutover aborted at the current stage, all later stages stay OFF, `EXECUTION_BLOCKER_REPORT` issued with the failing evidence path — implementation is never declared complete on partial evidence.
