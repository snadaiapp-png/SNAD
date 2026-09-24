# WAVE 1 — Unified Authorization Core (Implementation Plan)

> For agentic workers: REQUIRED SUB-SKILL: verification-before-completion — never report PASS without same-SHA machine evidence. **Revision D two-class doctrine:** implementation tasks follow test-first RED → minimal implementation → GREEN → affected regression → commit; verification/evidence/cutover-stage tasks follow PRECONDITION → VERIFY → EVIDENCE with untracked evidence and NO tracked evidence commit after the gate (no fabricated RED). Exact commands and commit boundaries are listed per task.

**Spec:** `docs/superpowers/specs/2026-09-23-unified-authorization-partner-billing-design.md` **Revision D** — §4.1 (runtime decision algorithm A–E), §5 (protected roles + break-glass reframe), §6.1 (Capability Contract Table), §7 (RBAC/ABAC/ReBAC), §9 (overrides + Direct DENY v1 invariant), §10 (scopes), §11 (explainability/cache), §12 (entitlements separate), §25 (admin UI), §29 Phase 2–3, §31.6 (correction-mandated tests).
**IMPLEMENTATION_BASE_SHA:** `8d0d49c7bdd3ad3a886a23cffc1e735e61998712`
**Correction branch:** `review/unified-control-plane-revision-c-reconstructed` (Revision D / R3 final correction docs; provenance: Rev C plans on the recovered R2 branch, Rev B base `cb5ef42c7ece6bca5aa401356d4b9ae52219720c`)
**Migrations:** `V20260924_1`..`V20260924_6` · **Flag:** `SANAD_UAC_PIPELINE_ENABLED` (default `false`)
**Revision D (R3) changes in this wave:** the wave header identifies the effective planning contract as Revision D / R3; the two-class TDD doctrine replaces the blanket RED→GREEN statement; Task 17 is restated as a PRECONDITION → VERIFY → EVIDENCE task ending with NO TRACKED COMMIT — evidence external/untracked.
**Historical Rev C (R2) changes in this wave (provenance):** (1) Task 1 overrides active-index predicate made IMMUTABLE (`WHERE valid_until IS NULL` + companion plain expiry index — a `valid_until > now()` predicate is not IMMUTABLE and would fail `CREATE INDEX`, and is semantically wrong for an index anyway); (2) Task 1 `authorization_change_events` RLS rewritten as explicit two-branch USING + WITH CHECK (fail-closed, no permissive-when-unset window); (3) Task 6 RED-then-later-GREEN commit pattern FORBIDDEN — the structural audit contract test is created in Task 10 where its GREEN lands; (4) Task 17 wave-evidence gate pinned to the FINAL_WAVE_SHA same-SHA contract.

## Goal

Make the existing `CapabilityEvaluationService` a strict-superset facade over a unified five-stage decision engine (hard guards → explicit DENY → candidate ALLOW sources → scope union → default DENY), with capability-wide Direct DENY v1, dynamic scopes, explainable decisions, protected-role invariants, break-glass as a normally-evaluated time-bounded grant, and an audited access administration surface.

## Architecture

Extend, never replace: `CapabilityEvaluationService.evaluate()` (874 `@RequireCapability` sites in 104 files) keeps its exact signature (`apps/sanad-platform/src/main/java/com/sanad/platform/access/evaluation/CapabilityEvaluationService.java`, lines 43–75) and delegates to the new pipeline. `DecisionSource.PROTECTED_SAFETY` does NOT exist — protected roles are mutation invariants (`ProtectedRoleGuard`, `LastAdminGuard`), not a runtime allow layer. New tables are additive and FORCE-RLS fail-closed (template: `V20260905_5__harden_hr_fail_closed_rls.sql` lines 57–71).

## Tech Stack

Java 21 · Spring Boot (`apps/sanad-platform`, single-module Maven, `apps/sanad-platform/mvnw`; CI uses bare `mvn` with `working-directory: apps/sanad-platform`) · Flyway (`classpath:db/migration,classpath:db/vendor/{vendor}`, `validate-on-migrate: true`) · PostgreSQL 16 (CI: host-native, `jdbc:postgresql://127.0.0.1:5432/pg_acceptance?prepareThreshold=0`; local battery: `jdbc:postgresql://127.0.0.1:5433/sanad`) · Caffeine cache · Next.js `apps/web` (npm scripts: `typecheck`/`lint`/`test`/`build`).

## Spec

See header. The authoritative runtime algorithm is spec §4.1 (A–E), effective as of Revision D. It supersedes any prior "12-step" decision-order text.

## Implementation Baseline

Repository evidence at `8d0d49c7`: `CapabilityEvaluationService.evaluate()` returns `AccessDecisionResponse` (`access/AccessDecisionResponse.java`: `tenantId, userId, organizationId, capabilityCode, allowed, reason, matchedRoleId, matchedRoleCode`); `AccessCapabilityService.loadByCode` normalizes codes to uppercase; `users.session_version` exists (`V13__add_session_version.sql`), `authorization_version` does NOT (added here); `roles.role_origin/template_key/template_version` exist (`V20260820_5`), `role_template_bindings` in `V20260820_6`; registration-time tenant-admin provisioning is `security/service/RoleTemplateProvisioner.java`; audit via `admin/service/PlatformAuditWriter.writeSuccess/writeFailure` into `platform_audit_logs` (`V17`); break-glass pattern precedent `workflow/application/WorkflowBreakGlassService.java` + `WorkflowBreakGlassTest`; RLS GUC today is `app.tenant_id` only (`security/rls/TenantRlsConnectionHandler.java` line 77); CI trio + inline Python expected map live in `.github/workflows/ci.yml` (pg-acceptance job, lines 500–539).

## Global Constraints

1. DOCS-ONLY branch: this plan is not executed until the owner authorizes WAVE 1.
2. No product code, no migrations are created by this correction; this document defines them exactly for future execution.
3. `effect='DENY' ⇒ scope_type IS NULL AND scope_reference IS NULL` is a DATABASE CHECK — scoped DENY is future work.
4. No `PROTECTED_SAFETY` source anywhere. Active direct DENY beats every candidate ALLOW (role, direct ALLOW, relationship policy, delegated grant, break-glass).
5. Protected registry = exactly `PLATFORM_OWNER`, `PLATFORM_ADMIN`, `AGENT_SUPER_ADMIN`, `TENANT_ADMIN`. `AGENT_CUSTOM_ADMIN` is NOT protected (spec §5 Rev B).
6. Every migration: `ENABLE ROW LEVEL SECURITY; FORCE ROW LEVEL SECURITY; DROP POLICY IF EXISTS tenant_isolation; CREATE POLICY tenant_isolation` with fail-closed `USING`/`WITH CHECK` written in full, in the SAME migration (F-C template).
7. All new admin mutations write `platform_audit_logs` via `PlatformAuditWriter` and an `authorization_change_events` row.
8. Feature flag `SANAD_UAC_PIPELINE_ENABLED=false` must restore legacy behavior bit-for-bit.

## Review Focus

DENY dominance; facade binary compatibility; RLS fail-closed; break-glass evaluated normally; protected-set exactness; audit completeness; no placeholders (every command below is exact).

---

Java paths below are relative to `apps/sanad-platform/src/main/java/com/sanad/platform/`; tests to `apps/sanad-platform/src/test/java/com/sanad/platform/`; migrations to `apps/sanad-platform/src/main/resources/db/migration/`.

### Task 1: Overrides, relationships, change-events schema with Direct-DENY v1 invariant

Files:
- Create: `db/migration/V20260924_1__uac_overrides_relationships_events.sql`
- Modify: none
- Test: `security/rls/UserPermissionOverrideRlsPostgresTest.java` (Create)

Interfaces:
- Consumes: existing `tenants(id)`, `access_capabilities(id)`, `users(id)` tables; `MigrationTestSchemaSupport.ensureDatabase(...)` harness (`src/test/java/com/sanad/platform/test/MigrationTestSchemaSupport.java`).
- Produces: `user_permission_overrides`, `subject_relationships`, `authorization_change_events` tables (FORCE RLS).

- [ ] Step 1: exact failing test — create `security/rls/UserPermissionOverrideRlsPostgresTest.java` (plain-JDBC over `MigrationTestSchemaSupport`): asserts (a) table exists; (b) `INSERT INTO user_permission_overrides (tenant_id, user_id, capability_id, effect, scope_type, scope_reference, reason, created_by) VALUES (tenant-A, user-U, capability-C, 'DENY', 'TEAM', team-uuid, 'test: scoped deny', actor)` throws a `SQLException` whose `SQLState` is `23514` (check_violation); (c) `effect='ALLOW'` with scope succeeds; (d) with GUC `app.tenant_id` set to tenant A, rows of tenant B are invisible and cross-tenant insert is blocked.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.rls.UserPermissionOverrideRlsPostgresTest test` → fails with `org.postgresql.util.PSQLException: ERROR: relation "user_permission_overrides" does not exist`.
- [ ] Step 3: exact minimal implementation — write `V20260924_1__uac_overrides_relationships_events.sql`:
  `user_permission_overrides(id uuid pk default gen_random_uuid(), tenant_id uuid NOT NULL REFERENCES tenants(id), partner_id uuid NULL, user_id uuid NOT NULL, capability_id uuid NOT NULL REFERENCES access_capabilities(id), effect text NOT NULL CHECK (effect IN ('ALLOW','DENY')), scope_type text NULL CHECK (scope_type IN ('SELF','OWN','DIRECT_REPORTS','REPORTING_TREE','TEAM','DEPARTMENT','ORG_UNIT','ORGANIZATION','BRANCH','BUSINESS_UNIT','LEGAL_ENTITY','PROJECT','TENANT_ALL')), scope_reference uuid NULL, reason text NOT NULL, valid_from timestamptz NOT NULL DEFAULT now(), valid_until timestamptz NULL, created_by uuid NOT NULL, created_at/updated_at timestamptz NOT NULL DEFAULT now(), version integer NOT NULL DEFAULT 0, CHECK (valid_until IS NULL OR valid_until > valid_from), CONSTRAINT ck_upo_deny_is_capability_wide CHECK (effect <> 'DENY' OR (scope_type IS NULL AND scope_reference IS NULL)))` — with a header comment: `partner_id` intentionally has NO FK in W1 (partners table does not exist at this SHA); W2 `V20260925_2` adds `FOREIGN KEY (partner_id) REFERENCES partners(id)` plus an orphan scan; `uq_user_permission_overrides_identity UNIQUE (tenant_id, user_id, capability_id, effect, scope_type, scope_reference, valid_from)`; partial index `idx_upo_active ON (tenant_id, user_id) WHERE valid_until IS NULL` (IMMUTABLE predicate — Rev C correction: the Rev B form `WHERE valid_until IS NULL OR valid_until > now()` is rejected by PostgreSQL because `now()` is not IMMUTABLE in index predicates, and even where tolerated it mis-prunes rows as time passes); companion plain index `idx_upo_expiry ON (tenant_id, user_id, valid_until)` for expiry-filtered lookups; FORCE-RLS fail-closed policy using `tenant_id::text = current_setting('app.tenant_id', true)` (V20260905_5 template verbatim).
  `subject_relationships(id uuid pk, tenant_id uuid NOT NULL REFERENCES tenants(id), subject_user_id uuid NOT NULL, relationship_type text NOT NULL CHECK (relationship_type IN ('MANAGES','MEMBER_OF','BELONGS_TO','ADMIN_OF','PARTNER_MANAGES')), object_type text NOT NULL CHECK (object_type IN ('EMPLOYEE','TEAM','DEPARTMENT','BRANCH','ORG_UNIT','TENANT','PARTNER')), object_id uuid NOT NULL, valid_from/valid_until timestamptz, source text NOT NULL DEFAULT 'EXPLICIT', created_by uuid NOT NULL, timestamps, UNIQUE (tenant_id, subject_user_id, relationship_type, object_type, object_id))` — header comment documents why `object_id` has NO FK: it is polymorphic across `object_type` targets (employees, org units, tenants, partners); integrity is enforced by `object_type`-specific service validation + the CHECK above; FORCE RLS.
  `authorization_change_events(id uuid pk, tenant_id uuid NULL, event_type text NOT NULL, actor_user_id uuid, target_type text, target_id uuid, payload jsonb NOT NULL DEFAULT '{}', correlation_id uuid, created_at timestamptz NOT NULL DEFAULT now())` + index `(tenant_id, event_type, created_at DESC)`; FORCE RLS with EXPLICIT two-branch policy — Rev C correction (the Rev B `current_setting(...) IS NULL OR ...` form was permissive-when-unset, i.e. fail-open for the whole table when the GUC is missing): `CREATE POLICY authorization_change_events_isolation ON authorization_change_events FOR ALL USING ( (tenant_id IS NOT NULL AND tenant_id::text = current_setting('app.tenant_id', true)) OR (tenant_id IS NULL AND current_setting('app.tenant_id', true) = '<CONTROL_PLANE_TENANT_UUID>' AND current_setting('app.partner_id', true) IS NULL) ) WITH CHECK ( (tenant_id IS NOT NULL AND tenant_id::text = current_setting('app.tenant_id', true)) OR (tenant_id IS NULL AND current_setting('app.tenant_id', true) = '<CONTROL_PLANE_TENANT_UUID>' AND current_setting('app.partner_id', true) IS NULL) );` where `<CONTROL_PLANE_TENANT_UUID>` is the same canonical control-plane carrier constant used by V20260921_1/W2 — unset GUCs make BOTH branches false ⇒ zero rows (fail-closed). A PostgreSQL migration test asserts: (i) NULL GUC ⇒ 0 rows even as table owner (FORCE); (ii) tenant GUC ⇒ own tenant rows only, platform rows invisible; (iii) control-plane context (carrier tenant GUC + `app.partner_id` NULL) ⇒ platform rows visible; (iv) `pg_policy` shows exactly this one policy with non-empty `polqual` and `polwithcheck`.
- [ ] Step 4: exact command proving GREEN — same command as Step 2 → `Tests run: 0 failures` (all 4 assertions green) and `flyway.validate()` inside the test passes.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.rls.FlywayJavaMigrationsChainConsistencyTest test` → green (migration chain consistent).
- [ ] Step 6: exact commit — `git add -A && git commit -m "wave1(schema): overrides relationships events with deny-capability-wide invariant (C1)"` — commit green at this SHA.

### Task 2: Effective-permission projection + `users.authorization_version`

Files:
- Create: `db/migration/V20260924_2__uac_effective_permission_projection.sql`
- Modify: none
- Test: `access/evaluation/EffectivePermissionProjectionSchemaPostgresTest.java` (Create)

Interfaces:
- Consumes: `user_permission_overrides`, `roles`, `role_capabilities`, `user_role_assignments`.
- Produces: `effective_permission_projection` (FORCE RLS); `users.authorization_version bigint NOT NULL DEFAULT 0`.

- [ ] Step 1: exact failing test — `access/evaluation/EffectivePermissionProjectionSchemaPostgresTest.java`: assert column `users.authorization_version` exists; `effective_permission_projection` exists with unique index `uq_epp` on `(tenant_id,user_id,capability_id,scope_type,scope_reference) NULLS NOT DISTINCT` (PG16); projection effect CHECK allows only `ALLOW`; source CHECK allows only `('ROLE','OVERRIDE','BREAK_GLASS')`; FORCE-RLS cross-tenant invisible.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.access.evaluation.EffectivePermissionProjectionSchemaPostgresTest test` → `PSQLException: ERROR: column "authorization_version" ... does not exist`.
- [ ] Step 3: exact minimal implementation — `V20260924_2__uac_effective_permission_projection.sql`: `effective_permission_projection(id uuid pk, tenant_id uuid NOT NULL, user_id uuid NOT NULL, capability_id uuid NOT NULL, effect text NOT NULL CHECK (effect IN ('ALLOW')), scope_type text NOT NULL DEFAULT 'TENANT_ALL', scope_reference uuid NULL, source text NOT NULL CHECK (source IN ('ROLE','OVERRIDE','BREAK_GLASS')), matched_role_id uuid NULL, authorization_version bigint NOT NULL, computed_at timestamptz NOT NULL DEFAULT now())` + `CREATE UNIQUE INDEX uq_epp ON effective_permission_projection (tenant_id,user_id,capability_id,scope_type,scope_reference) NULLS NOT DISTINCT` + FORCE RLS; `ALTER TABLE users ADD COLUMN authorization_version bigint NOT NULL DEFAULT 0;`.
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.rls.FlywayJavaMigrationsChainConsistencyTest test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave1(schema): effective permission projection + users.authorization_version (C1)"`.

### Task 3: Protected-roles registry — EXACTLY 4 codes (AGENT_CUSTOM_ADMIN excluded)

Files:
- Create: `db/migration/V20260924_3__uac_protected_roles_registry.sql`
- Modify: none
- Test: `access/evaluation/ProtectedRoleRegistrySchemaPostgresTest.java` (Create)

Interfaces:
- Consumes: `roles` (`role_origin`, `template_key` — V20260820_5), `role_capabilities`, `user_role_assignments`.
- Produces: `protected_system_roles` registry with EXACTLY 4 rows; per-tenant `TENANT_ADMIN` role rows.

- [ ] Step 1: exact failing test — `access/evaluation/ProtectedRoleRegistrySchemaPostgresTest.java`: assert `SELECT code FROM protected_system_roles ORDER BY code` returns EXACTLY `AGENT_SUPER_ADMIN, PLATFORM_ADMIN, PLATFORM_OWNER, TENANT_ADMIN`; assert `AGENT_CUSTOM_ADMIN` is absent; assert CHECK constraint `ck_psr_code` rejects `'AGENT_CUSTOM_ADMIN'` insert with SQLState 23514; assert per-tenant `TENANT_ADMIN` role exists with `role_origin='SNAD_TEMPLATE'`.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.access.evaluation.ProtectedRoleRegistrySchemaPostgresTest test` → `PSQLException: relation "protected_system_roles" does not exist`.
- [ ] Step 3: exact minimal implementation — `V20260924_3__uac_protected_roles_registry.sql`: `protected_system_roles(code text pk CONSTRAINT ck_psr_code CHECK (code IN ('PLATFORM_OWNER','PLATFORM_ADMIN','AGENT_SUPER_ADMIN','TENANT_ADMIN')), description text, immutability_level text CHECK (immutability_level IN ('LOCKED','MANAGED')), min_authority int NOT NULL)`; seed EXACTLY the 4 rows (`PLATFORM_OWNER`/`PLATFORM_ADMIN` LOCKED, `AGENT_SUPER_ADMIN`/`TENANT_ADMIN` MANAGED); header comment: `AGENT_CUSTOM_ADMIN` is an administrative hierarchy level / customizable partner-admin role and is deliberately NOT protected (spec §5 Rev B); reclassification requires a new owner-approved design decision. Per-tenant seed: DO-loop per tenant with `PERFORM set_config('app.tenant_id', tenant_id::text, TRUE)` (idiom of `V20260921_1` line 157): if no `roles` row with `code='TENANT_ADMIN'`, insert one (`role_origin='SNAD_TEMPLATE'`, `template_key='TENANT_ADMIN'`) copying `role_capabilities` of that tenant's `ADMIN` role; existing `ADMIN` stays active for backward compatibility; code-side switch happens in `RoleTemplateProvisioner` (NOT a class named `RegistrationProvisioner` — verified: only `security/service/RoleTemplateProvisioner.java` exists).
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.rls.FlywayJavaMigrationsChainConsistencyTest,com.sanad.platform.access.capability.AccessCapabilityCodeCanonicalizationPostgresTest test` → green (both classes).
- [ ] Step 6: exact commit — `git commit -m "wave1(schema): protected roles registry 4 codes agent-custom-admin excluded (C1)"`.

### Task 4: Capability metadata + `AUTHORIZATION.*` seeds

Files:
- Create: `db/migration/V20260924_4__uac_capability_metadata_and_authz_capabilities.sql`
- Modify: none
- Test: `access/capability/CapabilityMetadataMigrationContractTest.java` (Create)

Interfaces:
- Consumes: `access_capabilities` (`V7__create_access_capabilities.sql`: `code UNIQUE`, `status CHECK IN ('ACTIVE','INACTIVE')`); uppercase canonicalizer precedent `V20260901_1`.
- Produces: additive metadata columns; 6 new `AUTHORIZATION.*` rows (all `system_protected=true`).

- [ ] Step 1: exact failing test — `access/capability/CapabilityMetadataMigrationContractTest.java`: assert columns `application/module/resource/action/risk_level/supports_scope/system_protected` exist on `access_capabilities`; assert `risk_level` CHECK rejects `'EXTREME'` (23514); assert the 6 codes exist and are `ACTIVE`: `AUTHORIZATION.OVERRIDE.MANAGE`, `AUTHORIZATION.RELATIONSHIP.MANAGE`, `AUTHORIZATION.RESYNC`, `AUTHORIZATION.BREAK_GLASS`, `AUTHORIZATION.RECOVER`, `AUTHORIZATION.PLATFORM.MANAGE` with `system_protected=true`, `risk_level='CRITICAL'`.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.access.capability.CapabilityMetadataMigrationContractTest test` → `PSQLException: ERROR: column "application" ... does not exist`.
- [ ] Step 3: exact minimal implementation — `V20260924_4__uac_capability_metadata_and_authz_capabilities.sql`: `ALTER TABLE access_capabilities ADD COLUMN application text NOT NULL DEFAULT 'PLATFORM', ADD COLUMN module text, ADD COLUMN resource text, ADD COLUMN action text, ADD COLUMN risk_level text NOT NULL DEFAULT 'MEDIUM' CHECK (risk_level IN ('LOW','MEDIUM','HIGH','CRITICAL')), ADD COLUMN supports_scope boolean NOT NULL DEFAULT false, ADD COLUMN system_protected boolean NOT NULL DEFAULT false;` backfill `module/resource/action` by splitting `code` on `.` (UPDATE with `split_part`); INSERT the 6 codes (standard 7-column insert idiom of `V20260815_23`).
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.access.capability.AccessCapabilityCodeCanonicalizationPostgresTest test` → green (uppercase canonicalization unaffected).
- [ ] Step 6: exact commit — `git commit -m "wave1(schema): capability metadata + authorization capability seeds (C1)"`.

### Task 5: Access lookup indexes

Files:
- Create: `db/migration/V20260924_5__uac_projection_indexes.sql`
- Modify: none
- Test: covered by Tasks 1–4 schema tests (no separate class — index presence asserted in `EffectivePermissionProjectionSchemaPostgresTest` extended assertions)

Interfaces:
- Consumes: `user_permission_overrides`, `subject_relationships`, `authorization_change_events`, `effective_permission_projection`.
- Produces: covering indexes used by the pipeline hot path.

- [ ] Step 1: exact failing test — extend `access/evaluation/EffectivePermissionProjectionSchemaPostgresTest.java` with index-existence assertions: `idx_upo_lookup ON user_permission_overrides(tenant_id,user_id,capability_id)`; `idx_rel_lookup ON subject_relationships(tenant_id,subject_user_id,relationship_type)`; `idx_epp_user ON effective_permission_projection(tenant_id,user_id)`.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.access.evaluation.EffectivePermissionProjectionSchemaPostgresTest test` → index assertions fail (`expected index idx_upo_lookup`).
- [ ] Step 3: exact minimal implementation — `V20260924_5__uac_projection_indexes.sql` with exactly those three `CREATE INDEX IF NOT EXISTS` statements.
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.rls.FlywayJavaMigrationsChainConsistencyTest test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave1(schema): access lookup indexes (C1)"`.

### Task 6: Access-admin audit backstop

Files:
- Create: `db/migration/V20260924_6__uac_admin_audit_backfill_guard.sql`
- Modify: none
- Test: none in this task (Rev C: `AccessAdminAuditContractTest` is created wholly in Task 10 where its RED→GREEN completes in one task)

Interfaces:
- Consumes: `platform_audit_logs` (`V17__create_platform_audit_logs.sql`), `PlatformAuditWriter` (`admin/service/PlatformAuditWriter.java`).
- Produces: audit-scan support index; documented audit mandate.

- [ ] Step 1: exact verification gate (no RED test in this task) — migration-only task; correctness gate is the chain-consistency test in Step 4. The audit-mandate enforcement test (`AccessAdminAuditContractTest`: parse `src/main/java/com/sanad/platform/access/**/*Service.java`, fail listing any mutation method whose class does not reference `PlatformAuditWriter` — baseline list: `RoleService`, `RoleCapabilityService`, `UserRoleGrantService`, `AccessCapabilityService`) is created in Task 10 together with the audit wiring, so its RED and GREEN occur inside one task and no commit ever contains a deliberately-red test.
- [ ] Step 2: exact precondition command — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.rls.FlywayJavaMigrationsChainConsistencyTest test` → green BEFORE the new migration is added (baseline state recorded).
- [ ] Step 3: exact minimal implementation — `V20260924_6__uac_admin_audit_backfill_guard.sql`: partial index `CREATE INDEX idx_pal_resource_type_time ON platform_audit_logs (resource_type, created_at DESC);` plus a comment block documenting the audit mandate (enforced by the contract test; wiring completed in Task 9/10).
- [ ] Step 4: exact command proving GREEN — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.rls.FlywayJavaMigrationsChainConsistencyTest test` → green. REV C RULE: this task ships NO failing-or-deferred test. The behavioral/structural `AccessAdminAuditContractTest` is created entirely in Task 10 (where its RED and its GREEN land in the same task) — committing a RED test with a `KNOWN_RED_DEFERRED` marker and deferring GREEN to a later commit is FORBIDDEN (every commit boundary must be green per the TDD protocol).
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.rls.FlywayJavaMigrationsChainConsistencyTest test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave1(schema): audit scan index + access audit mandate documentation (C1)"`.

### Task 7: Five-stage decision engine (NO PROTECTED_SAFETY source)

Files:
- Create: `access/evaluation/AuthorizationDecision.java`, `access/evaluation/DecisionSource.java`
- Modify: `access/evaluation/CapabilityEvaluationService.java`
- Test: `access/evaluation/UnifiedDecisionPipelineTest.java` (Create)

Interfaces:
- Consumes: `AccessCapabilityService.loadByCode` (uppercase normalization), `UserRoleGrantService.activeGrants`, `RoleService.load`, `RoleCapabilityService.roleHasCapability`, `UserPermissionOverrideRepository` (Task 9), `RelationshipResolver` (Task 12), existing entitlement gate (`subscription/rbac/ControlPlaneAccessService` capability model + `@RequireCapability` aspect).
- Produces: `AuthorizationDecision evaluateDetailed(UUID tenantId, UUID userId, String capabilityCode, UUID organizationId)`; `DecisionSource` enum with EXACTLY `EXPLICIT_DENY, EXPLICIT_ALLOW, ROLE_GRANT, RELATIONSHIP_POLICY, DELEGATED_GRANT, DEFAULT_DENY` (no `PROTECTED_SAFETY`).

- [ ] Step 1: exact failing test — `access/evaluation/UnifiedDecisionPipelineTest.java` (unit, Mockito). Required cases: (a) unknown capability → `DEFAULT_DENY` reason `CAPABILITY_NOT_FOUND`; (b) inactive capability → DENY `CAPABILITY_INACTIVE`; (c) active direct DENY + active role grant on same capability → DENY, source `EXPLICIT_DENY` (DENY dominance over role ALLOW); (d) direct DENY + direct ALLOW on same capability → DENY `EXPLICIT_DENY`; (e) direct DENY + relationship-policy match → DENY `EXPLICIT_DENY`; (f) expired override ignored → falls through; (g) ALLOW-only path with `TENANT_ALL` scope → ALLOW; (h) two matching ALLOW scopes (OVERRIDE `TEAM` + ROLE `TENANT_ALL`) → ALLOW with scope union trace; (i) no candidate + no scope → `DEFAULT_DENY`; (j) source `PROTECTED_SAFETY` does not compile — asserted by reflection scan over `DecisionSource.values()`.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.access.evaluation.UnifiedDecisionPipelineTest test` → compilation error: `cannot find symbol: method evaluateDetailed` / `cannot find symbol: class DecisionSource`.
- [ ] Step 3: exact minimal implementation — `DecisionSource` enum exactly as listed above. `AuthorizationDecision` record: `(decision, capability, source, reason, matchedRoleId, matchedRoleCode, scopeType, policy, decisionId, evaluatedAt, trace List<String>)`. Pipeline in `CapabilityEvaluationService` (new `@Transactional(readOnly=true)` method, legacy `evaluate()` untouched here): Stage A hard guards — security context present (tenant+user non-null), capability known and `ACTIVE`, tenant boundary (`validateOrganization`), entitlement check for commercially-gated capabilities; Stage B — any active `user_permission_overrides` row `effect='DENY'` for (tenant,user,capability) → DENY `EXPLICIT_DENY` (capability-wide by schema: a DENY row has NULL scope, so it dominates every data scope); Stage C candidate ALLOWs — active-window direct ALLOW override, active role grants with `roleHasCapability`, relationship policy match via `RelationshipResolver`, delegated grant check stub (returns absent in W1; implemented W2), break-glass override rows are ordinary ALLOW overrides and reach Stage D like any direct ALLOW; Stage D — first candidate whose scope matches (`supports_scope` validation; `TENANT_ALL` = current tenant only) wins; multiple valid scopes union in `trace`; Stage E — otherwise DENY `DEFAULT_DENY`.
- [ ] Step 4: exact command proving GREEN — same as Step 2 → all 10 cases green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.access.evaluation.CapabilityEvaluationServiceTest test` → green (legacy behavior unchanged).
- [ ] Step 6: exact commit — `git commit -m "wave1(engine): five-stage decision pipeline, deny dominance, no protected-safety source (C2)"`.

### Task 8: Legacy facade binary compatibility

Files:
- Modify: `access/evaluation/CapabilityEvaluationService.java`
- Test: `security/authorization/EvaluateFacadeCompatibilityTest.java` (Create)

Interfaces:
- Consumes: `evaluateDetailed` (Task 7); `AccessDecisionResponse` record (unchanged shape — `access/AccessDecisionResponse.java`).
- Produces: `evaluate(UUID,UUID,String,UUID)` delegating; `CapabilityAuthorizationAspect` behavior unchanged (403 on DENY).

- [ ] Step 1: exact failing test — `security/authorization/EvaluateFacadeCompatibilityTest.java`: for role-grant scenarios the legacy `evaluate()` returns `AccessDecisionResponse` with `allowed=true`, `reason="ROLE_CAPABILITY_MATCH"`, matched role id/code identical to baseline values recorded from `8d0d49c7`; for unknown capability `reason="CAPABILITY_NOT_FOUND"`; signature `public AccessDecisionResponse evaluate(UUID tenantId, UUID userId, String capabilityCode, UUID organizationId)` still present via reflection.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.authorization.EvaluateFacadeCompatibilityTest test` → fails: pipeline absent, baseline mapping not exercised (`evaluate` short-circuits before pipeline).
- [ ] Step 3: exact minimal implementation — `evaluate()` body becomes: `AuthorizationDecision d = evaluateDetailed(tenantId, userId, capabilityCode, organizationId); return new AccessDecisionResponse(tenantId, userId, organizationId, capabilityCode, "ALLOW".equals(d.decision()), mapReason(d), d.matchedRoleId(), d.matchedRoleCode());` with `mapReason` producing the baseline strings (`ROLE_CAPABILITY_MATCH`, `CAPABILITY_NOT_FOUND`, `CAPABILITY_INACTIVE`, `NO_MATCHING_ACTIVE_ROLE`). No signature change — 874 `@RequireCapability` sites stay binary-compatible.
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.authorization.CapabilityAuthorizationAspectTest test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave1(engine): legacy evaluate delegates to pipeline, response mapping preserved (C2)"`.

### Task 9: Override service — validation matrix incl. scoped-DENY rejection

Files:
- Create: `access/override/UserPermissionOverride.java`, `access/override/UserPermissionOverrideRepository.java`, `access/override/UserPermissionOverrideService.java`, `access/override/dto/CreateOverrideRequest.java`, `access/override/dto/OverrideResponse.java`
- Modify: none
- Test: `access/override/UserPermissionOverrideServiceTest.java` (Create)

Interfaces:
- Consumes: `AccessCapabilityService`, `user_permission_overrides` (Task 1), `PlatformAuditWriter`, `authorization_change_events` inserts, `AuthorizationVersionService` (Task 12).
- Produces: `create(CreateOverrideRequest)`, `list(UUID tenantId, UUID userId)`, `revoke(UUID tenantId, UUID id, UUID actor)`.

- [ ] Step 1: exact failing test — `access/override/UserPermissionOverrideServiceTest.java` (unit, Mockito). Cases: create ALLOW with scope when `supports_scope=false` → `IllegalArgumentException` (`SCOPE_NOT_SUPPORTED`); create ALLOW with unknown scope type → rejected; create DENY with scope → rejected in service (`DENY_IS_CAPABILITY_WIDE`) BEFORE hitting DB (DB CHECK is the backstop, proven in Task 1); create DENY with NULL scope → persisted + `authorization_version` bumped + audit SUCCESS row + `authorization_change_events` row `USER_OVERRIDE_CHANGED`; expired-window request (`valid_until <= valid_from`) → rejected; capability INACTIVE → rejected; cross-tenant congruence mismatch → rejected; revoke writes audit + event + version bump.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.access.override.UserPermissionOverrideServiceTest test` → compilation error: classes missing.
- [ ] Step 3: exact minimal implementation — the five classes above; service transactional; every mutation writes `platform_audit_logs` via `PlatformAuditWriter.writeSuccess/writeFailure` and `authorization_change_events(event_type='USER_OVERRIDE_CHANGED')` and increments `users.authorization_version`.
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.access.capability.AccessCapabilityServiceTest test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave1(override): override service validation matrix, capability-wide deny enforced (C3)"`.

### Task 10: Access administration controllers + audit wiring (closes Task 6 contract)

Files:
- Create: `access/api/UserOverrideController.java`, `access/api/RelationshipController.java`, `access/api/EffectivePermissionController.java`
- Modify: `access/role/RoleService.java`, `access/role/RoleCapabilityService.java`, `access/capability/AccessCapabilityService.java`, `access/grant/UserRoleGrantService.java` (each mutation writes `PlatformAuditWriter` before/after JSON)
- Test: `access/api/UserOverrideControllerIT.java` (Create)

Interfaces:
- Consumes: `UserPermissionOverrideService` (Task 9), `RelationshipResolver`/`AccessRelationshipService` (Task 12), `@RequireCapability` aspect.
- Produces: `POST/GET/PATCH /api/v1/access/overrides` (`@RequireCapability("AUTHORIZATION.OVERRIDE.MANAGE")`); `/api/v1/access/relationships` (`AUTHORIZATION.RELATIONSHIP.MANAGE`); `GET /api/v1/access/effective-permissions?userId=` (`ROLE.READ`), `POST /api/v1/access/effective-permissions/resync` (`AUTHORIZATION.RESYNC`, recovery-only, audited).

- [ ] Step 1: exact failing test — `access/api/UserOverrideControllerIT.java` (`@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("local")`): POST `/api/v1/access/overrides` with capability → 201 and one `platform_audit_logs` SUCCESS row; without capability → 403; DENY-with-scope request → 400 `DENY_IS_CAPABILITY_WIDE`; cross-tenant read (JWT tenant A, override rows tenant B) → empty list; resync without `AUTHORIZATION.RESYNC` → 403.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5433/sanad SPRING_DATASOURCE_USERNAME=sanad SPRING_DATASOURCE_PASSWORD=sanad_pass mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.access.api.UserOverrideControllerIT test` → 404 route not found (red).
- [ ] Step 3: exact minimal implementation — the three controllers + `AccessRelationshipService` audit wiring + audit writes added to the four access services (this turns `AccessAdminAuditContractTest` green).
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green; then `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.access.audit.AccessAdminAuditContractTest test` → green (RED→GREEN completed inside this task; no deferred-red commit exists anywhere in the wave).
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.TenantBindingSecurityIntegrationTest test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave1(api): access override/relationship/effective-permission controllers + full audit wiring (C3)"`.

### Task 11: DENY-dominance matrix on PostgreSQL (release-gate semantics)

Files:
- Create: `access/evaluation/DenyDominancePostgresTest.java`
- Modify: none
- Test: this class (Create)

Interfaces:
- Consumes: seeded `user_permission_overrides` (DENY, capability-wide), role grants, direct ALLOWs, relationship rows; real PostgreSQL (spec §31.6 lines 2–5).
- Produces: machine proof that capability-wide DENY beats every candidate ALLOW source across every data scope.

- [ ] Step 1: exact failing test — parameterized PG test (plain JDBC + `MigrationTestSchemaSupport`): fixtures for user U in tenant T with capability C; four scenarios each asserting final `evaluateDetailed` decision is DENY with source `EXPLICIT_DENY`: (1) U has role grant ALLOW C; (2) U has direct ALLOW override C scope `TENANT_ALL`; (3) U has `subject_relationships` row satisfying relationship policy for C; (4) same three ALLOW sources combined AND separately evaluated with each scope type in the §10 family — DENY applies across every data scope; plus (5) INSERT `effect='DENY', scope_type='TEAM'` → SQLState 23514.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.access.evaluation.DenyDominancePostgresTest test` → scenarios 1–4 fail (pipeline returns ALLOW because DENY evaluation missing in this test build) — recorded as red before Task 7 wiring is exercised in PG context.
- [ ] Step 3: exact minimal implementation — none (this is a proof test; if any scenario is red after Task 7, fix `CapabilityEvaluationService` Stage B ordering until green — the fix belongs to `access/evaluation/CapabilityEvaluationService.java`).
- [ ] Step 4: exact command proving GREEN — same as Step 2 → all scenarios green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.access.evaluation.UnifiedDecisionPipelineTest test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave1(engine): deny dominance matrix proven on postgres (C4)"`.

### Task 12: Projection rebuild + relationship resolver

Files:
- Create: `access/relationship/SubjectRelationshipRepository.java`, `access/relationship/RelationshipResolver.java`, `access/relationship/HrScopedRelationshipResolver.java`, `access/relationship/AccessRelationshipService.java`, `access/evaluation/EffectivePermissionProjectionService.java`, `access/evaluation/AuthorizationVersionService.java`
- Modify: none
- Test: `access/evaluation/EffectivePermissionProjectionServiceTest.java` (Create)

Interfaces:
- Consumes: `subject_relationships` (Task 1); HR predicates via `hr/security/HrResourceContextResolver.java`; `effective_permission_projection` (Task 2).
- Produces: `boolean hasRelationship(UUID tenant, UUID subject, String type, String objectType, UUID objectId)`; `rebuild(UUID tenantId, UUID userId)`; `long currentVersion(UUID tenantId, UUID userId)`.

- [ ] Step 1: exact failing test — `access/evaluation/EffectivePermissionProjectionServiceTest.java` (unit) + PG rebuild case: seed user with role grant + ALLOW override → `rebuild` writes exactly 2 projection rows (sources `ROLE`, `OVERRIDE`), `authorization_version` matches `users.authorization_version`; DENY rows never appear in projection (effect CHECK only ALLOW); rebuild is idempotent (unique index `uq_epp` — second run updates `computed_at` only); HR adapter delegates to `HrResourceContextResolver` predicates without modifying them.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.access.evaluation.EffectivePermissionProjectionServiceTest test` → compilation error: classes missing.
- [ ] Step 3: exact minimal implementation — the six classes; JDBC-style writes with explicit `SET LOCAL app.tenant_id` for FORCE-RLS tables (house idiom: `finance` JDBC repos / `V20260921_1` `set_config`).
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && SPRING_PROFILES_ACTIVE=pg-acceptance PG_ACCEPTANCE_JDBC_URL='jdbc:postgresql://127.0.0.1:5432/pg_acceptance?prepareThreshold=0' PG_ACCEPTANCE_USERNAME=sanad PG_ACCEPTANCE_PASSWORD=sanad_pass mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.rls.RbacAccessCheckPostgresAcceptanceTest test` → 15/15 green (count unchanged — CI map untouched in W1).
- [ ] Step 6: exact commit — `git commit -m "wave1(projection): rebuild service + relationship resolver + version service (C4)"`.

### Task 13: Event-driven invalidation + decision cache

Files:
- Create: `security/authorization/AuthorizationChangedEvent.java`, `security/authorization/AuthorizationInvalidationListener.java`, `security/authorization/AuthorizationDecisionCache.java`
- Modify: `access/override/UserPermissionOverrideService.java`, `access/grant/UserRoleGrantService.java`, `access/role/RoleService.java`, `access/role/RoleCapabilityService.java` (publish `AuthorizationChangedEvent` on mutation)
- Test: `security/authorization/AuthorizationInvalidationListenerTest.java` (Create)

Interfaces:
- Consumes: Spring `@EventListener`; Caffeine (pattern of `security/filter/SessionVersionCache.java`, TTL 5 s, `maximumSize(50_000)`).
- Produces: cache key `tenant:user:version:cap:org`; stale ⇒ re-evaluate, never expand access; `authorization_version` increments + `authorization_change_events` rows on every change.

- [ ] Step 1: exact failing test — `security/authorization/AuthorizationInvalidationListenerTest.java`: role grant change ⇒ cache entry for `tenant:user` evicted, `users.authorization_version` incremented, one `authorization_change_events` row with `event_type='USER_ROLE_CHANGED'`; override change ⇒ same with `USER_OVERRIDE_CHANGED`; uncertain cache state ⇒ re-evaluate (no stale ALLOW can be returned).
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.authorization.AuthorizationInvalidationListenerTest test` → compilation error: classes missing.
- [ ] Step 3: exact minimal implementation — the three classes + four publish points.
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.filter.SessionVersionCacheTest test` → green (session cache untouched).
- [ ] Step 6: exact commit — `git commit -m "wave1(invalidation): authz change events + listener + decision cache (C4)"`.

### Task 14: Protected-role and last-admin guards (mutation invariants — NOT runtime sources)

Files:
- Create: `security/authorization/ProtectedRoleGuard.java`, `access/service/LastAdminGuard.java`, `access/service/AccessConflictException.java`
- Modify: `access/role/RoleService.java` (changeStatus), `access/role/RoleCapabilityService.java` (detach), `access/grant/UserRoleGrantService.java` (revoke), `security/service/AuthService.java` (deactivation path)
- Test: `security/authorization/ProtectedRoleGuardTest.java`, `access/service/LastAdminGuardTest.java` (Create both)

Interfaces:
- Consumes: `protected_system_roles` (Task 3); `user_role_assignments` counts; roles bound to `TENANT_ADMIN` template via `role_template_bindings` (`V20260820_6`).
- Produces: 409 `AccessConflictException` on protected mutation without `AUTHORIZATION.PLATFORM.MANAGE`; last-admin survival simulation before commit.

- [ ] Step 1: exact failing test — `ProtectedRoleGuardTest`: archive role with code `TENANT_ADMIN` → `AccessConflictException`; detach capability from `PLATFORM_ADMIN` → 409; same operations by actor holding `AUTHORIZATION.PLATFORM.MANAGE` → allowed. `LastAdminGuardTest`: removing the last ACTIVE grant bound to `TENANT_ADMIN` template (or holding `TENANT.ACTIVATE`+`AUTHORIZATION.RECOVER`) → 409; a second active admin exists → allowed; `assertTenantSurvives` simulation rejects BEFORE any write.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.authorization.ProtectedRoleGuardTest,com.sanad.platform.access.service.LastAdminGuardTest test` → compilation error: classes missing.
- [ ] Step 3: exact minimal implementation — both guards + wiring into the four call sites.
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.service.RoleTemplateProvisionerTest test` → green (registration provisioning unaffected; TENANT_ADMIN template path still provisions).
- [ ] Step 6: exact commit — `git commit -m "wave1(guards): protected role + last admin mutation invariants (C5)"`.

### Task 15: Break-glass as a normally-evaluated time-boxed grant

Files:
- Create: `security/authorization/BreakGlassAccessService.java`
- Modify: none
- Test: `security/authorization/BreakGlassAccessServiceTest.java` (Create)

Interfaces:
- Consumes: `user_permission_overrides` (ALLOW rows, `valid_until`), `PlatformAuditWriter`, `authorization_change_events`; pattern precedent `workflow/application/WorkflowBreakGlassService.java` + `workflow/WorkflowBreakGlassTest.java`.
- Produces: `grantEmergencyOverride(UUID tenantId, UUID userId, String capabilityCode, String reason, Instant validUntil)`, `listActive(...)`, `revoke(...)` — grants are ordinary ALLOW overrides evaluated by the §4.1 algorithm; an active direct DENY still beats them.

- [ ] Step 1: exact failing test — `BreakGlassAccessServiceTest`: reason shorter than 20 chars → rejected; `validUntil` later than now+4h → rejected; success path writes ALLOW override row (`source` trace `BREAK_GLASS` in `authorization_change_events` payload) + `OVERRIDE` audit row; revoke writes audit; and the pipeline case: break-glass ALLOW + active direct DENY on same capability ⇒ DENY (spec §5.1 Rev B).
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.authorization.BreakGlassAccessServiceTest test` → compilation error: class missing.
- [ ] Step 3: exact minimal implementation — `BreakGlassAccessService` as above.
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.workflow.WorkflowBreakGlassTest test` → green (workflow break-glass untouched; pattern parity only).
- [ ] Step 6: exact commit — `git commit -m "wave1(breakglass): time-boxed audited grant evaluated normally, deny still dominates (C5)"`.

### Task 16: Frontend — access API client + authorization admin pages

Files:
- Create: `apps/web/lib/api/access-api.ts`, `apps/web/lib/api/access-api.test.ts`, `apps/web/app/executive/authorization/page.tsx`, `apps/web/app/executive/authorization/users/[id]/page.tsx`, `apps/web/app/executive/authorization/authorization-page-gating.test.tsx`
- Modify: `apps/web/lib/i18n/locales/ar.ts`, `apps/web/lib/i18n/locales/en.ts` (add `authorization.*` keys to BOTH in the same commit; parity gate `scripts/ci/check_i18n_keys.py`)
- Test: the two test files above

Interfaces:
- Consumes: `apps/web/lib/api/client.ts` apiClient (scp-api pattern `apps/web/lib/api/scp-api.ts`), `ScpLayout`/`ScpAccessProvider`/`ScpStates`/`scp.module.css`.
- Produces: `/executive/authorization` hub (Users/Roles/Capabilities/Policies & Scopes/Access Audit tabs) + user detail (Overview/Roles/Direct Permissions/Data Scopes/Effective Permissions/Audit).

- [ ] Step 1: exact failing test — `apps/web/lib/api/access-api.test.ts`: mock `./client`; assert exact URLs `/api/v1/access/overrides`, `/api/v1/access/relationships`, `/api/v1/access/effective-permissions`, `/api/v1/access/effective-permissions/resync` and typed exports `listOverrides/createOverride/revokeOverride/listRelationships/effectivePermissions/resync`.
- [ ] Step 2: exact command proving RED — `cd apps/web && npm test -- access-api` → red: exports/URLs missing.
- [ ] Step 3: exact minimal implementation — client + the two pages + i18n keys; fail-closed gating via `ScpAccessProvider.has("AUTHORIZATION.OVERRIDE.MANAGE")` etc.
- [ ] Step 4: exact command proving GREEN — `cd apps/web && npm test -- access-api && npm test -- authorization-page-gating` → green.
- [ ] Step 5: exact affected regression — `cd apps/web && npm run typecheck && npm run lint && npm test && python3 scripts/ci/check_i18n_keys.py` → all green (protected-roots test untouched: `apps/web/app/providers-protected-roots.test.ts`).
- [ ] Step 6: exact commit — `git commit -m "wave1(web): access admin surfaces + i18n parity (C6)"`.

### Task 17: Wave exit evidence battery (verification/evidence task — PRECONDITION → VERIFY → EVIDENCE)

Files:
- Modify: none (evidence only)
- Test: full local battery mirroring `.github/workflows/ci.yml` jobs

Interfaces:
- Consumes: PostgreSQL 16 on `127.0.0.1:5433` (DBs `sanad`/`test_migration`/`pg_acceptance`; roles `sanad` NOBYPASSRLS, `crm_contact_rls_test_user`).
- Produces: `snad-evidence/evidence-<FINAL_WAVE_SHA>.log` with all commands + counts (untracked).

- [ ] Step 1: PRECONDITION — the FINAL_WAVE_SHA candidate exists, `git status --short` is clean, and every W1 implementation task is committed green; confirm the battery environment (PostgreSQL 16 on `127.0.0.1:5433`, DBs `sanad`/`test_migration`/`pg_acceptance`; roles `sanad` NOBYPASSRLS, `crm_contact_rls_test_user`) is up.
- [ ] Step 2: VERIFY — no new test is invented for this task (verification/evidence task class); the battery below IS the verification.
- [ ] Step 3: EVIDENCE ARTIFACT PLAN — one untracked log `snad-evidence/evidence-<FINAL_WAVE_SHA>.log` records `git rev-parse HEAD`, `git status --short`, every command, and every count; no tracked file is created or modified by this task.
- [ ] Step 4: exact command proving GREEN — run and record, all from repo root:
  `cd apps/web && npm ci && npm run lint && npx tsc --noEmit && npm test -- --reporter=verbose && npm run build`
  `cd apps/sanad-platform && mvn test -B -ntp -Dsurefire.useFile=false`
  `cd apps/sanad-platform && SPRING_PROFILES_ACTIVE=pg-acceptance PG_ACCEPTANCE_JDBC_URL='jdbc:postgresql://127.0.0.1:5432/pg_acceptance?prepareThreshold=0' PG_ACCEPTANCE_USERNAME=sanad PG_ACCEPTANCE_PASSWORD=sanad_pass mvn test -B -ntp -Dsurefire.useFile=true -DfailIfNoTests=true -Dtest='CommerceOrderPostgresConcurrencyTest,RbacAccessCheckPostgresAcceptanceTest,ModuleRegistryUatPostgresAcceptanceTest'` → 31/31 with the exact ci.yml map (6/15/10) unchanged.
- [ ] Step 5: exact affected regression — the battery IS the regression; log written to `snad-evidence/evidence-<FINAL_WAVE_SHA>.log`.
- [ ] Step 6: **NO TRACKED COMMIT — evidence external/untracked; report FINAL_WAVE_SHA** — the gate runs at ONE identical HEAD SHA recorded as `FINAL_WAVE_SHA` in the evidence log header via `git rev-parse HEAD`, with `git status --short` clean at run time; any tracked commit made after the gate invalidates the evidence and the FULL battery must be re-run at the new SHA before the wave is reported complete. Evidence artifacts (logs, surefire XML, CI run URLs) live ONLY in `snad-evidence/` (gitignored/untracked) or CI artifacts — never as tracked commits after the gate.

**FINAL_WAVE_SHA same-SHA evidence contract (Revision D):** the wave exit gate is valid only when every command above ran at ONE identical HEAD SHA — recorded as `FINAL_WAVE_SHA` in the evidence log header via `git rev-parse HEAD` — with `git status --short` clean (no uncommitted changes) at run time; any tracked commit made after the gate invalidates the evidence and the FULL battery must be re-run at the new SHA before the wave is reported complete. Evidence artifacts (logs, surefire XML, CI run URLs) live ONLY in `snad-evidence/` (gitignored/untracked) or CI artifacts — never as tracked commits after the gate.

## Dependencies, security, rollback

**Dependencies:** none upstream; blocks W2–W7. **Security:** fail-closed five-stage pipeline with no implicit allow; overrides tenant-jailed by FORCE RLS + service tenant congruence; Direct DENY v1 is capability-wide by DB CHECK and dominates all candidate ALLOW sources (Task 11 machine proof); protected registry is exactly 4 codes; `AGENT_CUSTOM_ADMIN` carries no protected status; break-glass ≤ 4 h + mandatory ≥ 20-char reason + audit, evaluated normally, never beats DENY; cache never expands access; `AUTHORIZATION.*` rows are `system_protected=true` (deactivation guarded in `AccessCapabilityService`). **Rollback:** flag `SANAD_UAC_PIPELINE_ENABLED=false` routes every decision through the legacy path instantly (aspect untouched); all DDL additive and unused when the flag is off ⇒ forward-compatible with revert to the pre-wave commit; projection rebuild idempotent; no destructive DDL anywhere in the wave.

