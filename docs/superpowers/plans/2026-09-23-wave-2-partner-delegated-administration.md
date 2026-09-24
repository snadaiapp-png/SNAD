# WAVE 2 — Partner Principal + Delegated Administration (Implementation Plan)

> For agentic workers: REQUIRED SUB-SKILL: verification-before-completion — never report PASS without same-SHA machine evidence; every task below is RED → GREEN with exact commands and commit boundaries.

**Spec:** Revision B — §3 (hierarchy/boundaries), §5 (protected roles: `AGENT_CUSTOM_ADMIN` NOT protected), §6.1 (Capability Contract Table), §13 (delegated administration via canonical capabilities), §13.1 (deterministic partner membership), §20 (partner tables), §21 (owner notifications pre-wiring), §24.1 (executive Partners section), §30 (isolation invariants).
**Depends on:** W1 merged. **Migrations:** `V20260925_1`..`V20260925_6` · **Flag:** `SANAD_PARTNER_ENABLED` (default `false`).

## Goal

Introduce the partner as a first-class isolated principal (own tables, own RLS GUC `app.partner_id`), close the W1 `user_permission_overrides.partner_id` forward reference with a real FK, make partner user membership deterministic (at most one ACTIVE membership per user, DB-enforced), and expose delegated administration through the SAME canonical capability codes wrapped by `PartnerDelegationGate`.

## Architecture

New `partner/` package extends — never forks — existing provisioning/subscription services. Partner scope comes ONLY from the signed JWT `partner_id` claim (server-side resolution; `?partnerId=` mismatch ⇒ 403). `app.partner_id` is a NEW GUC set by `TenantRlsConnectionHandler` next to `app.tenant_id` (verified net-new at baseline: the only GUC today is `app.tenant_id`). Membership determinism is DB-enforced with a partial unique index (precedent: `uk_tenant_subscriptions_effective`, `V20260906_2`).

## Tech Stack

Java 21 · Spring Boot single-module Maven (`apps/sanad-platform`; commands use `mvn` per ci.yml) · Flyway additive migrations · PostgreSQL 16 (FORCE RLS, partial unique indexes, DO-loop per-tenant seeding per `V20260921_1`) · Next.js `apps/web`.

## Spec

Header references authoritative. §13.1 Rev B is binding: one ACTIVE membership per user; suspension/removal invalidates sessions immediately via `users.session_version` (+ `SessionVersionCache.invalidate`), never via the 5 s JWT membership cache.

## Implementation Baseline

Repository evidence at `8d0d49c7`: NO partner tables exist (searched `db/migration` — only `'PARTNER_CONTACT'` enum literal in `V20260911_2` line 94); `TenantRlsConnectionHandler` sets exactly one GUC `app.tenant_id` (line 77) from JWT details map written by `JwtAuthenticationFilter` (line 147); `JwtTokenProvider.mintAccessToken(UUID,UUID,String,boolean,long)` embeds `session_version` claim; `users.session_version` exists (`V13`); `SessionVersionCache` TTL 5 s with `invalidate(tenantId,userId)`; `user_permission_overrides.partner_id` created FK-less in W1 `V20260924_1` (documented forward reference); `UserRoleGrantService.grant` is the role-grant entry point; `ControlPlaneAccessGuard.require(Authentication)` reads configured control-plane tenant (`sanad.control-plane.tenant-id`); control-plane tenant UUID `00000000-0000-0000-0000-000000000001` (SQL constant `control_tenant`, `V20260921_1` line 18; seeded `V20260813_1` lines 77–78).

## Global Constraints

1. DOCS-ONLY branch: not executed until the owner authorizes WAVE 2.
2. `user_permission_overrides.partner_id` MUST have a real FK after this wave; no orphan partner UUID may remain possible.
3. `UNIQUE(partner_id, user_id)` on memberships is FORBIDDEN as the ACTIVE-determinism mechanism; the enforcement is `UNIQUE(user_id) WHERE status='ACTIVE'`.
4. Protected-role logic references EXACTLY `PLATFORM_OWNER`, `PLATFORM_ADMIN`, `AGENT_SUPER_ADMIN` (plus `TENANT_ADMIN` where tenant-plane relevant) from the W1 registry — `AGENT_CUSTOM_ADMIN` is never treated as protected.
5. Delegation allowlist codes must exist in `access_capabilities` (seeded here in `V20260925_5`); canonical `BILLING.READ` already exists (canonicalized `V20260830_2`/`V20260901_1`); `BILLING.MANAGE` is created here once.
6. Every partner table: ENABLE + FORCE RLS + `DROP POLICY IF EXISTS` first, same migration.
7. All partner admin mutations audited (`PlatformAuditWriter`) + `authorization_change_events`.

## Review Focus

FK completeness; membership determinism + concurrency; immediate session invalidation; claim-only partner scope; allowlist/registry consistency with spec §6.1.

---

Java paths relative to `apps/sanad-platform/src/main/java/com/sanad/platform/` (new package `partner/`); tests to `apps/sanad-platform/src/test/java/com/sanad/platform/`.

### Task 1: Partner principal schema with deterministic membership

Files:
- Create: `db/migration/V20260925_1__partner_principal_schema.sql`
- Test: `security/rls/PartnerPrincipalRlsPostgresTest.java` (Create)

Interfaces:
- Consumes: `tenants`, `access_capabilities` (FK targets); FORCE-RLS template `V20260905_5`.
- Produces: `partners`, `partner_users` (partial-unique ACTIVE membership), `partner_tenant_bindings`, `partner_delegation_grants`.

- [ ] Step 1: exact failing test — `security/rls/PartnerPrincipalRlsPostgresTest.java` (plain-JDBC + `MigrationTestSchemaSupport`): two partners seeded; with GUC `app.partner_id` = partner A, partner B rows invisible, cross insert blocked; with NULL `app.partner_id` and control-plane `app.tenant_id` = `00000000-0000-0000-0000-000000000001` all rows visible; `INSERT INTO partner_users (..., user_id, status, ...) VALUES (..., <same user>, 'ACTIVE', ...)` twice on different partners ⇒ second insert fails (SQLState 23505, unique violation on partial index); SUSPENDED historical rows may coexist.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.rls.PartnerPrincipalRlsPostgresTest test` → `PSQLException: relation "partners" does not exist`.
- [ ] Step 3: exact minimal implementation — `V20260925_1__partner_principal_schema.sql`:
  `partners(id uuid pk, code text NOT NULL UNIQUE, display_name text NOT NULL, status text NOT NULL CHECK (status IN ('PROSPECT','ACTIVE','SUSPENDED','TERMINATED')), created_by uuid NOT NULL, created_at/updated_at, version int NOT NULL DEFAULT 0)` — platform-scope table, NO tenant_id; FORCE RLS: `USING (id::text = current_setting('app.partner_id', true) OR (current_setting('app.partner_id', true) IS NULL AND current_setting('app.tenant_id', true) = '00000000-0000-0000-0000-000000000001'))` + same WITH CHECK (SQL constant mirrors `control_tenant` of `V20260921_1`).
  `partner_users(id uuid pk, partner_id uuid NOT NULL REFERENCES partners(id), user_id uuid NOT NULL, role_code text NOT NULL CHECK (role_code IN ('AGENT_SUPER_ADMIN','AGENT_CUSTOM_ADMIN','AGENT_USER')), status text NOT NULL CHECK (status IN ('ACTIVE','SUSPENDED')), granted_by uuid NOT NULL, granted_at timestamptz NOT NULL DEFAULT now(), ended_at timestamptz NULL, timestamps)` + `CREATE UNIQUE INDEX uq_partner_users_one_active_per_user ON partner_users (user_id) WHERE status = 'ACTIVE'` (spec §13.1; NO `UNIQUE(partner_id,user_id)` — historical SUSPENDED rows may repeat) + partner FORCE RLS.
  `partner_tenant_bindings(id uuid pk, partner_id uuid NOT NULL REFERENCES partners(id), tenant_id uuid NOT NULL REFERENCES tenants(id), status text NOT NULL CHECK (status IN ('ACTIVE','SUSPENDED','TERMINATED')), commercial_model text NOT NULL DEFAULT 'RESELLER' CHECK (commercial_model IN ('DIRECT','RESELLER','COMMISSION','HYBRID')), bound_by uuid NOT NULL, bound_at timestamptz NOT NULL DEFAULT now(), timestamps)` + `CREATE UNIQUE INDEX uq_active_binding_per_tenant ON partner_tenant_bindings(tenant_id) WHERE status='ACTIVE'` + partner FORCE RLS + index `(partner_id, status)`.
  `partner_delegation_grants(id uuid pk, partner_id uuid NOT NULL REFERENCES partners(id), tenant_id uuid NOT NULL REFERENCES tenants(id), capability_id uuid NOT NULL REFERENCES access_capabilities(id), valid_from timestamptz NOT NULL DEFAULT now(), valid_until timestamptz NULL, reason text NOT NULL, granted_by uuid NOT NULL, timestamps, UNIQUE (partner_id, tenant_id, capability_id, valid_from))` + partner FORCE RLS.
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green (all GUC + uniqueness assertions pass).
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.rls.FlywayJavaMigrationsChainConsistencyTest test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave2(schema): partner principal, one-active-membership-per-user partial unique (C1)"`.

### Task 2: Partner FK integrity — close the W1 forward reference

Files:
- Create: `db/migration/V20260925_2__partner_fk_integrity.sql`
- Test: `security/rls/PartnerFkIntegrityPostgresTest.java` (Create)

Interfaces:
- Consumes: `user_permission_overrides` (W1 `V20260924_1`, `partner_id` FK-less); `partners` (Task 1).
- Produces: `fk_upo_partner` constraint; documented audit of every W1 forward reference.

- [ ] Step 1: exact failing test — `security/rls/PartnerFkIntegrityPostgresTest.java`: (a) `INSERT INTO user_permission_overrides (..., partner_id, ...) VALUES (..., '<random-uuid>', ...)` fails with SQLState 23503 (foreign_key_violation); (b) insert with a real `partners.id` succeeds; (c) migration's orphan-scan DO-block passes on clean data.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.rls.PartnerFkIntegrityPostgresTest test` → insert succeeds (no constraint) ⇒ assertion fails (red).
- [ ] Step 3: exact minimal implementation — `V20260925_2__partner_fk_integrity.sql`: DO-block scanning for orphan `user_permission_overrides.partner_id` values not in `partners(id)` — `RAISE EXCEPTION` if any exist (fail-closed before constraint); then `ALTER TABLE user_permission_overrides ADD CONSTRAINT fk_upo_partner FOREIGN KEY (partner_id) REFERENCES partners(id);`. Header comment records the complete W1 forward-reference audit: (1) `user_permission_overrides.partner_id` → FK added here; (2) `subject_relationships.object_id` (`relationship_type='PARTNER_MANAGES'`) → polymorphic by design (`object_type` CHECK constrains the target set; service-layer existence validation) — intentionally NO FK, documented; (3) `authorization_change_events.target_id` → polymorphic event target — intentionally NO FK, documented. No other W1 table references a partner principal (verified against `V20260924_1.._6`).
- [ ] Step 4: exact command proving GREEN — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.rls.PartnerFkIntegrityPostgresTest test` → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.rls.UserPermissionOverrideRlsPostgresTest test` → green (override table still behaves with FK attached).
- [ ] Step 6: exact commit — `git commit -m "wave2(fk): user_permission_overrides.partner_id references partners, orphan scan fail-closed (C1)"`.

### Task 3: Supporting partner RLS policies

Files:
- Create: `db/migration/V20260925_3__partner_rls_supporting_policies.sql`
- Test: `security/rls/PartnerSupportingPoliciesPostgresTest.java` (Create)

Interfaces:
- Consumes: partner tables (Task 1); `app.current_user_id` GUC set by `TenantRlsConnectionHandler` (extension landed in Task 6).
- Produces: cross-context read policies (platform SELECT all; partner INSERT/UPDATE own; user reads own membership row).

- [ ] Step 1: exact failing test — `security/rls/PartnerSupportingPoliciesPostgresTest.java`: control-plane context SELECTs all partner rows; partner context sees own rows only; with `app.current_user_id` set, a user reads exactly own `partner_users` row even when `app.partner_id` is unset (login path).
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.rls.PartnerSupportingPoliciesPostgresTest test` → own-membership read returns 0 rows (policy absent).
- [ ] Step 3: exact minimal implementation — `V20260925_3__partner_rls_supporting_policies.sql` with the three named policies (one per table need); every policy fail-closed (`current_setting(..., true)` NULL-safe, `::text` compare per `V20260905_5`).
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.rls.PartnerPrincipalRlsPostgresTest test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave2(rls): partner supporting policies incl own-membership read (C2)"`.

### Task 4: Binding history — append-only audit triggers

Files:
- Create: `db/migration/V20260925_4__partner_tenant_binding_audit_triggers.sql`
- Test: `partner/application/PartnerBindingAuditTriggerPostgresTest.java` (Create)

Interfaces:
- Consumes: `partner_tenant_bindings`; `authorization_change_events` (W1).
- Produces: BEFORE UPDATE trigger writing old→new into `authorization_change_events(event_type='PARTNER_BINDING_CHANGED')`; DELETE blocked.

- [ ] Step 1: exact failing test — status transition writes exactly one event row with before/after payload; `DELETE FROM partner_tenant_bindings` raises exception (append-only); no event row on failed no-op update.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.partner.application.PartnerBindingAuditTriggerPostgresTest test` → `PSQLException: relation "partner_tenant_bindings" has no rule/trigger` / delete succeeds ⇒ red.
- [ ] Step 3: exact minimal implementation — `V20260925_4__partner_tenant_binding_audit_triggers.sql`: plpgsql trigger function + `CREATE TRIGGER ... BEFORE UPDATE OR DELETE` (trigger idiom per `V20260905_18` advisory/trigger style).
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.rls.FlywayJavaMigrationsChainConsistencyTest test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave2(audit): binding history append-only with change events (C2)"`.

### Task 5: Delegation capability seeds (canonical vocabulary — spec §6.1)

Files:
- Create: `db/migration/V20260925_5__partner_authorization_capability_seeds.sql`
- Test: `partner/application/PartnerCapabilitySeedContractTest.java` (Create)

Interfaces:
- Consumes: `access_capabilities` (V7 + uppercase canonicalizer `V20260901_1`).
- Produces: 11 new codes — `BILLING.MANAGE`, `PARTNER.PLATFORM.MANAGE`, `TENANT.CREATE`, `TENANT.ACTIVATE`, `TENANT.SUSPEND`, `TENANT.USER.MANAGE`, `TENANT.AUTHORIZATION.MANAGE`, `SUBSCRIPTION.CREATE`, `SUBSCRIPTION.UPGRADE`, `SUBSCRIPTION.DOWNGRADE`, `SUBSCRIPTION.CANCEL` (all ACTIVE; `PARTNER.PLATFORM.MANAGE` with `system_protected=true`, `risk_level='CRITICAL'`; `BILLING.MANAGE` `supports_scope=false` — partner billing capability is partner-scoped, not data-scoped).

- [ ] Step 1: exact failing test — `partner/application/PartnerCapabilitySeedContractTest.java`: assert the 11 codes exist ACTIVE with correct `system_protected`; assert `BILLING.READ` ALSO exists (pre-existing canonical row — from `billing.read` via `V20260901_1`) so the delegation allowlist is fully backed; assert NO code `PARTNER.BILLING.MANAGE` or `PARTNER.BILLING.READ` exists anywhere (semantic-duplicate ban, spec §6.1).
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.partner.application.PartnerCapabilitySeedContractTest test` → `capability not found: BILLING.MANAGE` (red).
- [ ] Step 3: exact minimal implementation — `V20260925_5__partner_authorization_capability_seeds.sql`: 7-column INSERT idiom of `V20260815_23`; header comment: canonical vocabulary per spec §6.1 — no duplicate namespace.
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.access.capability.AccessCapabilityCodeCanonicalizationPostgresTest test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave2(schema): delegation capability seeds, canonical vocabulary, no partner-billing duplicates (C2)"`.

### Task 6: Partner GUC wiring — claim, filter, connection handler

Files:
- Create: `partner/security/PartnerClaimResolver.java`
- Modify: `security/service/JwtTokenProvider.java` (mintAccessToken adds `.claim("partner_id", <uuid>)` when the session user has exactly one ACTIVE `partner_users` row — single indexed query, Caffeine-cached 5 s keyed by `tenant:user:session_version`), `security/filter/JwtAuthenticationFilter.java` (read `partner_id` into details AFTER `session_version`, BEFORE `credential_rotation_required` block; malformed ⇒ 401 fail-closed), `security/rls/TenantRlsConnectionHandler.java` (when details contain `partner_id`, also `SET LOCAL app.partner_id = '<uuid>'` beside `app.tenant_id` line 77), `security/config/SecurityConfig.java` (`/api/v1/partner/**` added to authenticated chain — NO permitAll additions)
- Test: `security/filter/PartnerClaimBindingTest.java`, `security/rls/TenantRlsPartnerGucTest.java` (Create both)

Interfaces:
- Consumes: `partner_users` partial unique (Task 1) — at most one ACTIVE row ⇒ deterministic claim.
- Produces: `app.partner_id` GUC; JWT claim; details map entry.

- [ ] Step 1: exact failing test — `PartnerClaimBindingTest` (`local` IT): login of partner user ⇒ minted JWT carries `partner_id` = the ACTIVE membership's partner; non-partner user ⇒ no claim; user with only SUSPENDED memberships ⇒ no claim (deterministic: no ACTIVE row). `TenantRlsPartnerGucTest`: connection handler sets `app.partner_id` when details carry it; FORCE-RLS partner table honors it (`current_setting('app.partner_id', true)` returns the UUID inside the transaction).
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5433/sanad SPRING_DATASOURCE_USERNAME=sanad SPRING_DATASOURCE_PASSWORD=sanad_pass mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.filter.PartnerClaimBindingTest,com.sanad.platform.security.rls.TenantRlsPartnerGucTest test` → claim absent / GUC absent (red).
- [ ] Step 3: exact minimal implementation — the four modifications above; `PartnerClaimResolver` reads `partner_id` from `Authentication.details`.
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.filter.JwtAuthenticationFilterControlPlaneTenantBindingTest test` → green (unknown claims ignored by legacy consumers).
- [ ] Step 6: exact commit — `git commit -m "wave2(security): deterministic partner claim + app.partner_id GUC (C3)"`.

### Task 7: Membership concurrency + immediate session invalidation (spec §13.1)

Files:
- Create: `partner/application/PartnerUserMembershipService.java`
- Modify: `partner/application/PartnerAdminService.java` (create/suspend partner users; suspension and role downgrade increments the affected user's `users.session_version` and calls `SessionVersionCache.invalidate(tenantId, userId)` in the SAME transaction)
- Test: `partner/application/PartnerMembershipConcurrencyPostgresTest.java`, `partner/application/PartnerSessionInvalidationTest.java` (Create both)

Interfaces:
- Consumes: `partner_users` (Task 1), `users.session_version` (`V13`), `SessionVersionCache.invalidate`.
- Produces: `assignUser(partnerId, userId, roleCode)`; `suspendUser(partnerId, userId)`; immediate 401 on the suspended user's next request.

- [ ] Step 1: exact failing test — `PartnerMembershipConcurrencyPostgresTest` (TransactionTemplate, two threads): two simultaneous INSERTs of ACTIVE memberships for the SAME user on DIFFERENT partners ⇒ exactly one commits, loser gets `DataIntegrityViolationException` (SQLState 23505); same test with SUSPENDED target status ⇒ both commits allowed (history preserved). `PartnerSessionInvalidationTest` (`local` IT): suspend the partner user ⇒ within the same request cycle the suspended user's existing JWT fails with 401 (`session_version` mismatch) — NOT after any 5 s cache TTL; removal of last AGENT_SUPER_ADMIN also covered by Task 11 guard.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.partner.application.PartnerMembershipConcurrencyPostgresTest,com.sanad.platform.partner.application.PartnerSessionInvalidationTest test` → concurrency test fails (no service path) and invalidation test fails (session_version not bumped).
- [ ] Step 3: exact minimal implementation — `PartnerUserMembershipService` + `PartnerAdminService` suspension path bumping `session_version` + cache invalidate.
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.filter.SessionVersionCacheTest test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave2(membership): one-active concurrency proof + immediate session_version invalidation (C3)"`.

### Task 8: Partner service (platform CRUD)

Files:
- Create: `partner/domain/Partner.java`, `partner/repository/PartnerRepository.java`, `partner/application/PartnerService.java`
- Modify: none
- Test: `partner/application/PartnerServiceTest.java` (Create)

Interfaces:
- Consumes: `partners` (Task 1); `@RequireCapability("PARTNER.PLATFORM.MANAGE")`; `PlatformAuditWriter`.
- Produces: `create/suspend/terminate/list` with status machine PROSPECT→ACTIVE→SUSPENDED/TERMINATED; creates `AGENT_SUPER_ADMIN` role template in partner context.

- [ ] Step 1: exact failing test — `PartnerServiceTest` (unit): status machine transitions legal/illegal; every mutation writes audit SUCCESS/FAILURE with before/after; terminate blocks when ACTIVE bindings exist (`TenantBindingActiveException`).
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.partner.application.PartnerServiceTest test` → compilation error: classes missing.
- [ ] Step 3: exact minimal implementation — the three classes.
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.admin.service.PlatformAuditWriterTest test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave2(partner): partner service with status machine + audit (C3)"`.

### Task 9: Tenant binding service + concurrent-bind race

Files:
- Create: `partner/application/PartnerTenantBindingService.java`
- Modify: none
- Test: `partner/application/PartnerTenantBindingServiceTest.java`, `partner/application/PartnerBindingConcurrentPostgresTest.java` (Create both)

Interfaces:
- Consumes: `partner_tenant_bindings` partial unique `uq_active_binding_per_tenant` (Task 1); `@RequireCapability("PARTNER.PLATFORM.MANAGE")`; R0C-9 dead-end parity (refuses TERMINATED tenants).
- Produces: `bind/suspend/unbind`; audit + `authorization_change_events`; notification event pre-wiring (consumed by W6).

- [ ] Step 1: exact failing test — service test: bind requires capability; refuses TERMINATED tenants; refuses second ACTIVE binding for same tenant (unique index); suspend/unbind transitions audited. Concurrency test (TransactionTemplate, two threads binding the SAME tenant to two partners): exactly one ACTIVE binding wins, loser 409 `DataIntegrityViolationException` mapped to `AccessConflictException`.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.partner.application.PartnerTenantBindingServiceTest,com.sanad.platform.partner.application.PartnerBindingConcurrentPostgresTest test` → classes missing (red).
- [ ] Step 3: exact minimal implementation — the service (race-safe BY the partial unique index, not SELECT-before-INSERT).
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.commerce.ConcurrentSuccessorCreationPostgresTest test` → green (idiom parity intact).
- [ ] Step 6: exact commit — `git commit -m "wave2(binding): race-safe single active binding per tenant (C4)"`.

### Task 10: Delegation gate over canonical capabilities

Files:
- Create: `partner/application/PartnerDelegationGate.java`
- Modify: none
- Test: `partner/application/PartnerDelegationGateTest.java` (Create)

Interfaces:
- Consumes: `partner_delegation_grants`, `partner_tenant_bindings`, `access_capabilities` (Task 5 seeds + pre-existing `BILLING.READ`).
- Produces: `assertDelegated(UUID partnerId, UUID tenantId, String capabilityCode)` — binding ACTIVE + grant window valid + code in the spec §13/§6.1 allowlist (`TENANT.CREATE`, `TENANT.ACTIVATE`, `TENANT.SUSPEND`, `TENANT.USER.MANAGE`, `TENANT.AUTHORIZATION.MANAGE`, `SUBSCRIPTION.CREATE`, `SUBSCRIPTION.UPGRADE`, `SUBSCRIPTION.DOWNGRADE`, `SUBSCRIPTION.CANCEL`, `BILLING.READ`, `BILLING.MANAGE`); deny reasons `DELEGATION_MISSING`, `DELEGATION_EXPIRED`, `BINDING_INACTIVE`, `CODE_NOT_DELEGATABLE`.

- [ ] Step 1: exact failing test — expired grant ⇒ `DELEGATION_EXPIRED`; missing ⇒ `DELEGATION_MISSING`; no ACTIVE binding ⇒ `BINDING_INACTIVE`; code outside allowlist (e.g. `CRM.CONTACT.READ`) ⇒ `CODE_NOT_DELEGATABLE`; canonical `BILLING.MANAGE` and `BILLING.READ` both pass when granted (proves canonical-vocabulary delegation, no `PARTNER.BILLING.*`).
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.partner.application.PartnerDelegationGateTest test` → class missing (red).
- [ ] Step 3: exact minimal implementation — the gate with the allowlist as a static set mirroring spec §6.1 exactly.
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.access.evaluation.CapabilityEvaluationServiceTest test` → green (engine untouched; gate composes with it).
- [ ] Step 6: exact commit — `git commit -m "wave2(delegation): gate over canonical capability vocabulary (C4)"`.

### Task 11: Partner last-admin guard + protected-role grant escalation guard

Files:
- Modify: `partner/application/PartnerAdminService.java` (LastAdminGuard wiring), `access/grant/UserRoleGrantService.java` (escalation guard)
- Create: none (guards reused from W1: `LastAdminGuard.assertPartnerSurvives`)
- Test: `partner/application/PartnerLastAdminGuardTest.java`, `access/grant/ProtectedRoleGrantGuardTest.java` (Create both)

Interfaces:
- Consumes: `protected_system_roles` (W1 Task 3 — EXACTLY `PLATFORM_OWNER`,`PLATFORM_ADMIN`,`AGENT_SUPER_ADMIN`,`TENANT_ADMIN`), `LastAdminGuard` (W1 Task 14).
- Produces: suspending/removing the last ACTIVE `AGENT_SUPER_ADMIN` ⇒ 409; granting protected codes to a user with an ACTIVE `partner_users` row ⇒ 409 `PROTECTED_ROLE_ESCALATION` unless actor holds `AUTHORIZATION.PLATFORM.MANAGE`; `AGENT_CUSTOM_ADMIN` grants are NOT blocked by protected-role logic (spec §5 Rev B).

- [ ] Step 1: exact failing test — `PartnerLastAdminGuardTest`: suspend last active AGENT_SUPER_ADMIN ⇒ 409; suspend one of two ⇒ allowed. `ProtectedRoleGrantGuardTest`: partner-context user + `AGENT_SUPER_ADMIN` grant attempt ⇒ 409; partner-context user + `AGENT_CUSTOM_ADMIN` grant ⇒ allowed (NOT protected); platform owner path ⇒ allowed.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.partner.application.PartnerLastAdminGuardTest,com.sanad.platform.access.grant.ProtectedRoleGrantGuardTest test` → guards absent (red).
- [ ] Step 3: exact minimal implementation — wiring as above.
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && SPRING_PROFILES_ACTIVE=pg-acceptance PG_ACCEPTANCE_JDBC_URL='jdbc:postgresql://127.0.0.1:5432/pg_acceptance?prepareThreshold=0' PG_ACCEPTANCE_USERNAME=sanad PG_ACCEPTANCE_PASSWORD=sanad_pass mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.rls.RbacAccessCheckPostgresAcceptanceTest test` → 15/15 (count unchanged).
- [ ] Step 6: exact commit — `git commit -m "wave2(guards): partner last-admin + protected-role escalation, agent-custom-admin exempt (C5)"`.

### Task 12: Partner portal + executive controllers

Files:
- Create: `partner/api/PartnerPortalController.java`, `partner/api/ExecutivePartnerController.java`, `partner/application/PartnerTenantProvisioningAdapter.java`
- Test: `partner/api/PartnerPortalControllerIT.java`, `partner/api/ExecutivePartnerControllerIT.java`, `partner/application/PartnerTenantProvisioningAdapterTest.java` (Create all)

Interfaces:
- Consumes: `PartnerClaimResolver` (Task 6), `PartnerDelegationGate` (Task 10), existing provisioning/bootstrap services + `SaasAdministrationService.createSubscription` (SUBSCRIPTION.* delegation), `ControlPlaneAccessGuard`.
- Produces: `/api/v1/partner/**` (me, tenants, delegations, users — ALL resolved from JWT claim; `?partnerId=` mismatch ⇒ 403 `PARTNER_SCOPE_MISMATCH`); `/api/v1/executive/partners/**` (`PARTNER.PLATFORM.MANAGE` + control-plane guard).

- [ ] Step 1: exact failing test — portal IT: partner token lists own tenants; `?partnerId=<other>` ⇒ 403 `PARTNER_SCOPE_MISMATCH`; TENANT.SUSPEND path without delegation ⇒ 403. Executive IT: platform authority CRUD green; tenant-plane token ⇒ 403. Adapter test: delegated TENANT.CREATE creates tenant via the existing provisioning service; audit row `source=PARTNER_DELEGATED`; failed delegation writes audit FAILURE and never bypasses provisioning guards.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5433/sanad SPRING_DATASOURCE_USERNAME=sanad SPRING_DATASOURCE_PASSWORD=sanad_pass mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.partner.api.PartnerPortalControllerIT,com.sanad.platform.partner.api.ExecutivePartnerControllerIT,com.sanad.platform.partner.application.PartnerTenantProvisioningAdapterTest test` → 404 routes (red).
- [ ] Step 3: exact minimal implementation — the three classes.
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.TenantBindingSecurityIntegrationTest test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave2(api): partner portal + executive surfaces, claim-only scope (C5)"`.

### Task 13: Frontend — partner admin surfaces

Files:
- Create: `apps/web/lib/api/partner-admin-api.ts`, `apps/web/lib/api/partner-admin-api.test.ts`, `apps/web/app/executive/partners/page.tsx`, `apps/web/app/executive/partners/[id]/page.tsx`
- Modify: `apps/web/lib/i18n/locales/ar.ts`, `apps/web/lib/i18n/locales/en.ts` (`partner.admin.*` keys, ar+en same commit)
- Test: the API test file + a page gating test

Interfaces:
- Consumes: apiClient (`apps/web/lib/api/client.ts`), `ScpAccessProvider` fail-closed `has()` (e.g. `has("PARTNER.PLATFORM.MANAGE")`).
- Produces: partner registry grid + partner detail tabs per spec §24.1 (Commercial Agreement tab renders `ScpEmpty` with i18n key `partner.admin.agreement.wave3` until W3).

- [ ] Step 1: exact failing test — `apps/web/lib/api/partner-admin-api.test.ts`: assert exact URLs `/api/v1/executive/partners`, `/api/v1/executive/partners/{id}`, `/api/v1/executive/partners/{id}/users`, `/api/v1/executive/partners/{id}/tenants`, `/api/v1/executive/partners/{id}/delegations` and exports `listPartners/createPartner/suspendPartner/partnerDetail/partnerUsers/partnerTenants/bindTenant/unbindTenant/partnerDelegations`.
- [ ] Step 2: exact command proving RED — `cd apps/web && npm test -- partner-admin-api` → red: exports/URLs missing.
- [ ] Step 3: exact minimal implementation — client + the two pages + i18n keys.
- [ ] Step 4: exact command proving GREEN — `cd apps/web && npm test -- partner-admin-api` → green.
- [ ] Step 5: exact affected regression — `cd apps/web && npm run typecheck && npm run lint && npm test && python3 scripts/ci/check_i18n_keys.py` → green.
- [ ] Step 6: exact commit — `git commit -m "wave2(web): partner admin surfaces + i18n parity (C6)"`.

### Task 14: Wave exit evidence battery

Files:
- Modify: none (evidence only)
- Test: full local battery

Interfaces:
- Consumes: PostgreSQL 16 local battery (`127.0.0.1:5433` + pg-acceptance profile).
- Produces: `snad-evidence/evidence-<HEAD-SHA>.log`.

- [ ] Step 1: exact failing test — none.
- [ ] Step 2: exact command proving RED — none.
- [ ] Step 3: exact minimal implementation — none.
- [ ] Step 4: exact command proving GREEN —
  `cd apps/web && npm ci && npm run lint && npx tsc --noEmit && npm test -- --reporter=verbose && npm run build`
  `cd apps/sanad-platform && mvn test -B -ntp -Dsurefire.useFile=false`
  `cd apps/sanad-platform && SPRING_PROFILES_ACTIVE=pg-acceptance PG_ACCEPTANCE_JDBC_URL='jdbc:postgresql://127.0.0.1:5432/pg_acceptance?prepareThreshold=0' PG_ACCEPTANCE_USERNAME=sanad PG_ACCEPTANCE_PASSWORD=sanad_pass mvn test -B -ntp -Dsurefire.useFile=true -DfailIfNoTests=true -Dtest='CommerceOrderPostgresConcurrencyTest,RbacAccessCheckPostgresAcceptanceTest,ModuleRegistryUatPostgresAcceptanceTest'` → 31/31 (6/15/10 unchanged).
- [ ] Step 5: exact affected regression — battery IS the regression; log at `snad-evidence/evidence-<HEAD-SHA>.log`.
- [ ] Step 6: exact commit — `git commit -m "wave2(evidence): gate run @ <HEAD-SHA> (C7)"`.

## Dependencies, security, rollback

**Dependencies:** W1 (overrides/events/projection/guards/LastAdminGuard). Blocks W3–W6. **Security:** partner scope ONLY from signed claim (never caller param — spec §13/§22.3); partner tables FORCE RLS with new `app.partner_id` GUC; escalation guard over the 4-code protected registry with `AGENT_CUSTOM_ADMIN` exempt; membership determinism DB-enforced and concurrency-proven; suspension invalidates sessions immediately via `session_version`; all partner admin mutations audited + `authorization_change_events`; business-data access still requires explicit tenant-scoped role grant (NOT auto-granted). **Rollback:** flag `SANAD_PARTNER_ENABLED=false` hides `/api/v1/partner/**` (404 via guard) and disables membership-cache minting of the claim (legacy consumers already ignore unknown claims — proven by Task 6 regression); DDL additive; FK on `user_permission_overrides.partner_id` is forward-only hardening (existing rows carry NULL partner_id); revert commits safe.
