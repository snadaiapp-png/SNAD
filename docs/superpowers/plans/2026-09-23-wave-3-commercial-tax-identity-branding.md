# WAVE 3 — Commercial / Tax Identity + Branding (Implementation Plan)

> For agentic workers: REQUIRED SUB-SKILL: verification-before-completion — never report PASS without same-SHA machine evidence; every task below is RED → GREEN with exact commands and commit boundaries.

**Spec:** Revision B — §14 (business identity fields, reusable screen), §14.3 (platform_files repository-grounded extension — NO `kind` column), §15 (billing identity snapshots — table created here, bound in W4), §20.1 (principal uniqueness via partial unique indexes), §24 (screens for platform/partner/tenant), §31.6 (principal-uniqueness + platform_files tests).
**Depends on:** W2 (partner principal context, `app.partner_id` GUC). **Migrations:** `V20260926_1`..`V20260926_7` · **Flag:** `SANAD_COMMERCIAL_IDENTITY_ENABLED` (default `false`).

## Goal

Give PLATFORM, PARTNER, and TENANT principals independent commercial/tax identities with DB-enforced one-principal-per-partner/tenant uniqueness (partial unique indexes — NOT `NULLS NOT DISTINCT`), immutable invoice party snapshots, and partner-safe brand-logo storage reusing the REAL `platform_files` schema (`source_module='COMMERCIAL'`, `source_entity_type='BRAND_LOGO'`, new nullable `partner_id`) under one fail-closed principal-aware RLS policy.

## Architecture

New `commercial/` package. `platform_files` is SHARED infrastructure (only FK consumer today: `workflow_attachments.fk_wf_attachment_file` — `V20260911_2` lines 62–64); every change is additive and every existing consumer is regression-tested. `business_principals` enforces uniqueness with partial unique indexes `WHERE tenant_id IS NOT NULL` / `WHERE partner_id IS NOT NULL` (house precedent: `uk_tenant_subscriptions_effective`, `uk_plan_versions_one_active`, `uk_finance_invoices_tenant_external_ref`) because `NULLS NOT DISTINCT` would collapse all PLATFORM rows into one NULL value and break the polymorphic model.

## Tech Stack

Java 21 · Spring Boot single-module Maven · Flyway · PostgreSQL 16 (partial unique indexes, immutable-by-trigger rows, `set_config` FORCE-RLS writes) · Next.js `apps/web`.

## Spec

§14.3 Rev B is binding: no `platform_files.kind`; ownership matrix platform/partner/tenant; ambiguous-ownership CHECK; ONE fail-closed policy; full workflow attachment regression. Agreement schemas and temporal integrity live in W4 (`V20260927_1`), not here.

## Implementation Baseline

Repository evidence at `8d0d49c7`: `platform_files` DDL is `V20260911_2__r1_attachments_external_foundation.sql` lines 17–41 — columns exactly `id, tenant_id NOT NULL, source_module VARCHAR(50) NOT NULL, source_entity_type VARCHAR(100), source_entity_id UUID, mime_type, size_bytes, checksum_sha256 CHAR(64), classification (CHECK 'PUBLIC','INTERNAL','CONFIDENTIAL','RESTRICTED'), storage_reference NOT NULL, uploaded_by, created_at` + `uk_platform_files_tenant_id UNIQUE (tenant_id, id)`; NO `kind`, NO `partner_id`, and NO RLS clauses in its DDL; writes go through `storage/PlatformFileReferenceService.register(UUID tenantId, String sourceModule, String sourceEntityType, UUID sourceEntityId, String mimeType, Long sizeBytes, String checksumSha256, String classification, String storageReference, UUID uploadedBy)` (lines 40–58) and `workflow/application/WorkflowAttachmentService.attach(...)` (line 45); test precedent `workflow/integration/WorkflowAttachmentExternalFoundationTest.java`. No `business_principals`/`business_*` tables exist. Control-plane tenant carrier `00000000-0000-0000-0000-000000000001` (SQL constant `control_tenant`, `V20260921_1` line 18). `btree_gist`/EXCLUDE precedent exists but is NOT needed in W3 (agreement EXCLUDE is W4).

## Global Constraints

1. DOCS-ONLY branch: not executed until the owner authorizes WAVE 3.
2. NO `platform_files.kind` column may be created; NO fabricated `kind='BRAND_LOGO'` value.
3. Principal uniqueness: `CREATE UNIQUE INDEX` partial forms `WHERE tenant_id IS NOT NULL` / `WHERE partner_id IS NOT NULL`; single PLATFORM row via partial unique index `ON business_principals (principal_type) WHERE principal_type='PLATFORM'` (predicate-index form, independent of NULL semantics).
4. `platform_files` RLS becomes ENABLE + FORCE with ONE fail-closed principal-aware policy; `DROP POLICY IF EXISTS` first (none exists today — the DROP is defensive).
5. Ambiguous-ownership CHECK: a row with `partner_id` set must carry the control-plane carrier tenant (`tenant_id = '00000000-0000-0000-0000-000000000001'`); tenant-owned rows must have `partner_id IS NULL`.
6. Every existing `platform_files` consumer (workflow attachments) must stay green.

## Review Focus

Uniqueness matrix (2 partners coexist, 2 tenants coexist, duplicates rejected, second PLATFORM rejected); ownership CHECK; RLS fail-closed for all three principals; shared-table regression.

---

Java paths relative to `apps/sanad-platform/src/main/java/com/sanad/platform/` (new package `commercial/`); tests to `apps/sanad-platform/src/test/java/com/sanad/platform/`.

### Task 1: Business identity schema with partial unique indexes

Files:
- Create: `db/migration/V20260926_1__business_identity_schema.sql`
- Test: `security/rls/BusinessIdentityRlsPostgresTest.java` (Create)

Interfaces:
- Consumes: `partners` (W2), `tenants`; FORCE-RLS template `V20260905_5`.
- Produces: `business_principals`, `business_profiles`, `business_addresses`, `business_contacts` (all FORCE RLS composite).

- [ ] Step 1: exact failing test — `security/rls/BusinessIdentityRlsPostgresTest.java` (plain-JDBC + `MigrationTestSchemaSupport`), spec §31.6 uniqueness matrix: two different PARTNER principals coexist; two different TENANT principals coexist; duplicate PARTNER principal (same partner_id) rejected SQLState 23505; duplicate TENANT principal rejected; second PLATFORM principal rejected (partial unique on `principal_type WHERE principal_type='PLATFORM'`); type/congruence CHECKs enforce PLATFORM⇒both NULL, PARTNER⇒partner only, TENANT⇒tenant only; composite RLS: tenant context sees only tenant rows, partner context (`app.partner_id`) only partner rows, control-plane context sees platform rows; cross-context inserts blocked.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.rls.BusinessIdentityRlsPostgresTest test` → `PSQLException: relation "business_principals" does not exist`.
- [ ] Step 3: exact minimal implementation — `V20260926_1__business_identity_schema.sql`:
  `business_principals(id uuid pk, principal_type text NOT NULL CHECK (principal_type IN ('PLATFORM','PARTNER','TENANT')), partner_id uuid NULL REFERENCES partners(id), tenant_id uuid NULL REFERENCES tenants(id), status text NOT NULL CHECK (status IN ('ACTIVE','ARCHIVED')), created_by uuid NOT NULL, created_at/updated_at, CHECK ((principal_type='PLATFORM' AND partner_id IS NULL AND tenant_id IS NULL) OR (principal_type='PARTNER' AND partner_id IS NOT NULL AND tenant_id IS NULL) OR (principal_type='TENANT' AND tenant_id IS NOT NULL AND partner_id IS NULL)))` + `CREATE UNIQUE INDEX uq_business_principals_partner ON business_principals (partner_id) WHERE partner_id IS NOT NULL` + `CREATE UNIQUE INDEX uq_business_principals_tenant ON business_principals (tenant_id) WHERE tenant_id IS NOT NULL` + `CREATE UNIQUE INDEX uq_business_principals_platform ON business_principals (principal_type) WHERE principal_type = 'PLATFORM'` (replaces the invalid `NULLS NOT DISTINCT` design — here all-NULL PLATFORM rows would have collapsed into one NULL key) + composite FORCE RLS exactly as the test's context matrix.
  `business_profiles(id uuid pk, principal_id uuid NOT NULL UNIQUE REFERENCES business_principals(id), legal_name text NOT NULL, trade_name text, commercial_registration_number text, tax_registration_number text, country char(2), city text, verification_status text NOT NULL DEFAULT 'PENDING' CHECK (verification_status IN ('PENDING','VERIFIED','REJECTED')), business_email text, finance_email text, website text, timestamps, version bigint NOT NULL DEFAULT 0)` + denormalized `tenant_id`/`partner_id` (NOT NULL per principal type, same CHECK discipline) + same composite RLS + tenant-leading indexes.
  `business_addresses(id uuid pk, principal_id uuid NOT NULL REFERENCES business_principals(id), kind text NOT NULL CHECK (kind IN ('LEGAL','BILLING','NATIONAL','BRANCH')), line1, line2, district, city, region, postal_code, country char(2), is_primary boolean NOT NULL DEFAULT false, timestamps)` + `CREATE UNIQUE INDEX uq_primary_per_kind ON business_addresses (principal_id, kind) WHERE is_primary` + composite RLS.
  `business_contacts(id uuid pk, principal_id uuid NOT NULL REFERENCES business_principals(id), kind text NOT NULL CHECK (kind IN ('BUSINESS','FINANCE','SUPPORT')), name text, email text, phone text, timestamps)` + composite RLS.
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green (full uniqueness + RLS matrix).
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.rls.PartnerPrincipalRlsPostgresTest test` → green (W2 RLS intact).
- [ ] Step 6: exact commit — `git commit -m "wave3(schema): business identity with partial unique principal indexes (C1)"`.

### Task 2: platform_files extension + fail-closed RLS (repository-grounded)

Files:
- Create: `db/migration/V20260926_2__platform_files_partner_extension_rls.sql`
- Test: `security/rls/PlatformFilesForceRlsPostgresTest.java` (Create)

Interfaces:
- Consumes: real `platform_files` DDL (`V20260911_2` lines 17–41 — NO `kind`); control-plane carrier UUID.
- Produces: nullable `platform_files.partner_id`; ownership CHECK; ENABLE+FORCE RLS with ONE principal-aware policy.

- [ ] Step 1: exact failing test — `security/rls/PlatformFilesForceRlsPostgresTest.java`: (a) ambiguous ownership INSERT (`partner_id` set AND `tenant_id` = a customer tenant) rejected SQLState 23514 by `ck_platform_files_ownership`; (b) partner-owned row (`tenant_id` = control-plane carrier `00000000-0000-0000-0000-000000000001`, `partner_id` set) accepted; (c) tenant-owned row (`tenant_id` = actual tenant, `partner_id` NULL) accepted; (d) platform-owned row (carrier tenant, `partner_id` NULL) accepted; (e) RLS: with GUC `app.tenant_id` = customer tenant A → sees only tenant-A rows, NOT partner rows, NOT other tenants; with GUC `app.partner_id` = partner A and carrier tenant context → sees only partner-A asset rows; with NULL GUCs → ZERO rows (fail-closed FORCE — even table owner); (f) `platform_files.kind` does NOT exist (`information_schema.columns` assertion).
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.rls.PlatformFilesForceRlsPostgresTest test` → `partner_id` column missing; NULL-GUC read returns rows (no FORCE RLS today) ⇒ red.
- [ ] Step 3: exact minimal implementation — `V20260926_2__platform_files_partner_extension_rls.sql`:
  `ALTER TABLE platform_files ADD COLUMN partner_id uuid REFERENCES partners(id);`
  `ALTER TABLE platform_files ADD CONSTRAINT ck_platform_files_ownership CHECK ((partner_id IS NULL) OR (partner_id IS NOT NULL AND tenant_id = '00000000-0000-0000-0000-000000000001'::uuid));`
  ONE fail-closed principal-aware policy: `ALTER TABLE platform_files ENABLE ROW LEVEL SECURITY; ALTER TABLE platform_files FORCE ROW LEVEL SECURITY; DROP POLICY IF EXISTS tenant_isolation ON platform_files; CREATE POLICY principal_isolation ON platform_files FOR ALL USING ( (tenant_id IS NOT NULL AND tenant_id::text = current_setting('app.tenant_id', true) AND tenant_id::text <> '00000000-0000-0000-0000-000000000001') OR (partner_id IS NOT NULL AND partner_id::text = current_setting('app.partner_id', true) AND tenant_id::text = '00000000-0000-0000-0000-000000000001') OR (tenant_id::text = '00000000-0000-0000-0000-000000000001' AND partner_id IS NULL AND current_setting('app.tenant_id', true) = '00000000-0000-0000-0000-000000000001') ) WITH CHECK (same predicates);` — one policy, three disjoint principal branches (tenant-owned / partner-owned-carrier / platform-owned), no OR-combining with any permissive legacy policy (DROP-first), NULL-GUC ⇒ no branch matches ⇒ zero rows.
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — Task 3's suite (next task) is the dedicated regression; here also run `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.rls.FlywayJavaMigrationsChainConsistencyTest test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave3(schema): platform_files partner extension + single fail-closed principal policy (C1)"`.

### Task 3: platform_files shared-infrastructure regression (workflow attachments)

Files:
- Modify: `storage/PlatformFileReferenceService.java` (register signature gains optional partner-aware overload `registerForPartner(UUID partnerId, String sourceModule, String sourceEntityType, UUID sourceEntityId, ...)` writing carrier-tenant + partner_id; existing `register(...)` unchanged)
- Test: run the EXISTING workflow attachment suite (no new test needed for legacy behavior; new behavior covered in Task 8)

Interfaces:
- Consumes: `platform_files` (post-Task 2), `workflow/application/WorkflowAttachmentService.attach(...)`.
- Produces: proof that ALL existing attachment/file-reference behavior is unchanged (spec §14.3 mandate).

- [ ] Step 1: exact failing test — none new; the guard is the EXISTING suite `workflow/integration/WorkflowAttachmentExternalFoundationTest.java` — it must stay green after Task 2's RLS hardening; the RED risk is that FORCE RLS breaks flows whose connection context lacks `app.tenant_id` (they must already set it — `TenantRlsConnectionHandler` — otherwise the suite exposes it).
- [ ] Step 2: exact command proving RED (risk probe) — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.workflow.integration.WorkflowAttachmentExternalFoundationTest test` — if RED after Task 2, the fix belongs in `security/rls/TenantRlsConnectionHandler.java` (ensure GUC set for the affected path), never by weakening the policy.
- [ ] Step 3: exact minimal implementation — the partner-aware `registerForPartner` overload only (additive); legacy `register(...)` byte-identical.
- [ ] Step 4: exact command proving GREEN — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.workflow.integration.WorkflowAttachmentExternalFoundationTest test` → green.
- [ ] Step 5: exact affected regression — full CRM integration job (other attachment consumers): `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest='com.sanad.platform.crm.**.*IntegrationTest' test` → green (16 classes, ci.yml `crm` job parity).
- [ ] Step 6: exact commit — `git commit -m "wave3(compat): platform_files shared-infrastructure regression green (C1)"`.

### Task 4: Invoice party snapshots schema

Files:
- Create: `db/migration/V20260926_3__invoice_party_snapshots.sql`
- Test: `commercial/domain/InvoiceSnapshotSchemaPostgresTest.java` (Create)

Interfaces:
- Consumes: `business_principals` (Task 1).
- Produces: `invoice_party_snapshots` (FORCE RLS composite; partial unique per invoice side — FK to `billing_invoices` added W4 `V20260927_4` after that table gains its columns).

- [ ] Step 1: exact failing test — `commercial/domain/InvoiceSnapshotSchemaPostgresTest.java`: table exists with §15 fields; `snapshot_for` CHECK `('SELLER','BUYER')`; `uq_snapshot_per_invoice_side (invoice_id, snapshot_for) WHERE invoice_id IS NOT NULL`; denormalized tenant/partner scope columns with the same CHECK discipline; composite FORCE RLS isolation; `invoice_id` nullable with NO FK yet (documented W4 forward reference in migration header).
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.commercial.domain.InvoiceSnapshotSchemaPostgresTest test` → relation missing (red).
- [ ] Step 3: exact minimal implementation — `V20260926_3__invoice_party_snapshots.sql`: `invoice_party_snapshots(id uuid pk, snapshot_for text NOT NULL CHECK (snapshot_for IN ('SELLER','BUYER')), principal_id uuid NOT NULL REFERENCES business_principals(id), invoice_id uuid NULL, legal_name text NOT NULL, trade_name text, commercial_registration_number text, tax_registration_number text, country char(2), billing_address jsonb NOT NULL, logo_file_id uuid NULL, currency_code char(3) NOT NULL, captured_at timestamptz NOT NULL DEFAULT now(), captured_by uuid NOT NULL, tenant_id uuid NULL, partner_id uuid NULL, <type-discipline CHECKs>)` + `CREATE UNIQUE INDEX uq_snapshot_per_invoice_side ON invoice_party_snapshots (invoice_id, snapshot_for) WHERE invoice_id IS NOT NULL` + composite FORCE RLS.
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.rls.FlywayJavaMigrationsChainConsistencyTest test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave3(schema): invoice party snapshots (C2)"`.

### Task 5: Snapshot immutability trigger

Files:
- Create: `db/migration/V20260926_4__snapshot_immutability_trigger.sql`
- Test: `commercial/domain/SnapshotImmutabilityPostgresTest.java` (Create)

Interfaces:
- Consumes: `invoice_party_snapshots` (Task 4).
- Produces: physically immutable snapshots (spec §15/§30 "Tenant cannot alter seller identity snapshot on issued invoice").

- [ ] Step 1: exact failing test — `UPDATE invoice_party_snapshots SET legal_name='x'` ⇒ exception `invoice_party_snapshots are immutable (spec §15): use credit notes/adjustments`; `DELETE` ⇒ same exception; INSERT ok.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.commercial.domain.SnapshotImmutabilityPostgresTest test` → UPDATE/DELETE succeed (no trigger) ⇒ red.
- [ ] Step 3: exact minimal implementation — `V20260926_4__snapshot_immutability_trigger.sql`: `CREATE FUNCTION commercial_forbid_snapshot_mutation() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'invoice_party_snapshots are immutable (spec §15): use credit notes/adjustments'; END $$;` + `CREATE TRIGGER trg_snapshots_immutable BEFORE UPDATE OR DELETE ON invoice_party_snapshots FOR EACH ROW EXECUTE FUNCTION commercial_forbid_snapshot_mutation();`.
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.commercial.domain.InvoiceSnapshotSchemaPostgresTest test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave3(schema): snapshot immutability trigger (C2)"`.

### Task 6: Commercial capability seeds (canonical — spec §6.1)

Files:
- Create: `db/migration/V20260926_5__commercial_capability_seeds.sql`
- Test: `commercial/application/CommercialCapabilitySeedContractTest.java` (Create)

Interfaces:
- Consumes: `access_capabilities`.
- Produces: `COMMERCIAL.PROFILE.READ`, `COMMERCIAL.PROFILE.WRITE`, `PARTNER.COMMERCIAL.READ`, `PARTNER.COMMERCIAL.WRITE` (all ACTIVE, `system_protected=false`).

- [ ] Step 1: exact failing test — assert the 4 codes exist ACTIVE with expected metadata; assert NO `PARTNER.COMMERCIAL.*` code is marked delegatable anywhere in the W2 allowlist (they are principal-self capabilities, not delegation targets).
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.commercial.application.CommercialCapabilitySeedContractTest test` → `capability not found: COMMERCIAL.PROFILE.READ` (red).
- [ ] Step 3: exact minimal implementation — `V20260926_5__commercial_capability_seeds.sql` (7-column INSERT idiom).
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.access.capability.AccessCapabilityCodeCanonicalizationPostgresTest test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave3(schema): commercial capability seeds (C2)"`.

### Task 7: Indexes + PLATFORM principal seed

Files:
- Create: `db/migration/V20260926_6__commercial_support_indexes.sql`, `db/migration/V20260926_7__seed_platform_business_principal.sql`
- Test: `commercial/domain/PlatformPrincipalSeedPostgresTest.java` (Create)

Interfaces:
- Consumes: commercial tables (Tasks 1–4); control-plane constants.
- Produces: lookup indexes; exactly ONE PLATFORM principal row.

- [ ] Step 1: exact failing test — `PlatformPrincipalSeedPostgresTest`: `SELECT count(*) FROM business_principals WHERE principal_type='PLATFORM'` = 1; second insert attempt rejected by `uq_business_principals_platform` (23505).
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.commercial.domain.PlatformPrincipalSeedPostgresTest test` → count = 0 (red).
- [ ] Step 3: exact minimal implementation — `V20260926_6`: indexes `(principal_id)` on addresses/contacts, `(invoice_id)` on snapshots, principal lookups; `V20260926_7`: insert the PLATFORM principal + its `business_profiles` skeleton row (executed under control-plane context via `set_config('app.tenant_id', control_tenant, TRUE)` idiom of `V20260921_1`).
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.rls.BusinessIdentityRlsPostgresTest test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave3(schema): commercial indexes + single platform principal seed (C2)"`.

### Task 8: Business identity service

Files:
- Create: `commercial/domain/BusinessPrincipal.java`, `commercial/domain/BusinessProfile.java`, `commercial/domain/BusinessAddress.java`, `commercial/domain/BusinessContact.java` + repositories (JDBC-style where FORCE-RLS writes need explicit GUC — house finance-repo idiom), `commercial/application/BusinessIdentityService.java`
- Modify: none
- Test: `commercial/application/BusinessIdentityServiceTest.java` (Create)

Interfaces:
- Consumes: capability map — TENANT profile ⇒ `COMMERCIAL.PROFILE.READ/WRITE`; PARTNER profile ⇒ `PARTNER.COMMERCIAL.READ/WRITE` via `PartnerDelegationGate` self-context; PLATFORM ⇒ control-plane guard; `PlatformAuditWriter`; `authorization_change_events` (`BusinessProfileChanged`).
- Produces: `get/update` per principal type; verification-status transitions PENDING→VERIFIED/REJECTED (platform authority only).

- [ ] Step 1: exact failing test — capability matrix per principal type; cross-principal write rejected; verification transition audited with before/after; `BusinessProfileChanged` event row written in same transaction; tax fields stored free-format (NO hard-coded legal rules — spec §33).
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.commercial.application.BusinessIdentityServiceTest test` → classes missing (red).
- [ ] Step 3: exact minimal implementation — domain + repos + service.
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.access.audit.AccessAdminAuditContractTest test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave3(identity): business identity service with audited transitions (C3)"`.

### Task 9: Logo upload via real platform_files (source_module='COMMERCIAL')

Files:
- Create: `commercial/application/LogoUploadService.java`
- Modify: `storage/PlatformFileReferenceService.java` (consume the Task 3 `registerForPartner` overload)
- Test: `commercial/application/LogoUploadServiceTest.java` (Create)

Interfaces:
- Consumes: `platform_files` with `source_module='COMMERCIAL'`, `source_entity_type='BRAND_LOGO'`, `source_entity_id=<business principal id>`; NO `kind` field (spec §14.3).
- Produces: `upload(principalId, multipart)` — magic-byte validation (PNG `%PNG` / JPEG `FF D8 FF` only; SVG/script rejected regardless of declared mime), size ≤ 2_097_152 bytes; atomic `brand_profiles.primary_logo_file_id`/`invoice_logo_file_id` update (new `brand_profiles` table in `V20260926_2` — `id uuid pk, principal_id uuid NOT NULL UNIQUE REFERENCES business_principals(id), primary_logo_file_id uuid NULL, invoice_logo_file_id uuid NULL, theme_accent text NULL, timestamps, version bigint NOT NULL DEFAULT 0`, composite FORCE RLS); authorized GET read-back only (no public bucket).

- [ ] Step 1: exact failing test — 3 MB file rejected; `.svg` with `image/png` mime rejected by magic bytes; 1 MB PNG accepted and stored with `source_module='COMMERCIAL'` + `source_entity_type='BRAND_LOGO'`; partner-owned upload lands with carrier tenant + partner_id set (ambiguous-ownership CHECK satisfied); DB row + brand_profile update atomic (rollback on failure); re-upload replaces pointer, old file row retained (history).
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.commercial.application.LogoUploadServiceTest test` → class missing (red).
- [ ] Step 3: exact minimal implementation — `LogoUploadService` + `brand_profiles` DDL appended to `V20260926_2__platform_files_partner_extension_rls.sql` (same migration, FORCE RLS composite).
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.workflow.integration.WorkflowAttachmentExternalFoundationTest test` → green (shared table intact).
- [ ] Step 6: exact commit — `git commit -m "wave3(branding): logo upload via source_module=COMMERCIAL, no kind column (C3)"`.

### Task 10: Party snapshot service

Files:
- Create: `commercial/application/PartySnapshotService.java`
- Modify: none
- Test: `commercial/application/PartySnapshotServiceTest.java` (Create)

Interfaces:
- Consumes: `business_profiles`/`brand_profiles`/`business_addresses`; `invoice_party_snapshots` (Task 4/5).
- Produces: `capture(UUID principalId, String snapshotFor)` — renders ALL §15 fields into a NEW immutable row per call (history, never update); consumed by W4 issuance.

- [ ] Step 1: exact failing test — capture renders legal/trade identity, tax identity, billing address jsonb, logo pointer, currency; second capture for same principal = second row (history); any subsequent UPDATE attempt on the row hits the immutability trigger.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.commercial.application.PartySnapshotServiceTest test` → class missing (red).
- [ ] Step 3: exact minimal implementation — the service.
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.commercial.domain.SnapshotImmutabilityPostgresTest test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave3(snapshots): party snapshot capture service (C4)"`.

### Task 11: Controllers — tenant, partner, executive

Files:
- Create: `commercial/api/TenantCommercialController.java` (`/api/v1/commercial/profile|branding|logo`), `commercial/api/PartnerCommercialController.java` (`/api/v1/partner/commercial/**`, claim-scoped), `commercial/api/ExecutiveCommercialController.java` (`/api/v1/executive/commercial/platform` + `/partners/{partnerId}` read/verify — verification transitions require `AUTHORIZATION.PLATFORM.MANAGE`), DTOs `CommercialProfileResponse`/`UpdateCommercialProfileRequest`/`BrandProfileResponse`/`SnapshotResponse`
- Test: `commercial/api/TenantCommercialControllerIT.java`, `commercial/api/PartnerCommercialControllerIT.java`, `commercial/api/ExecutiveCommercialControllerIT.java` (Create all)

Interfaces:
- Consumes: `BusinessIdentityService` (Task 8), `LogoUploadService` (Task 9), `PartySnapshotService` (Task 10), `PartnerClaimResolver`.
- Produces: the §14.1 API surface for all three mounts.

- [ ] Step 1: exact failing test — tenant IT: GET/PUT happy path; 403 without `COMMERCIAL.PROFILE.WRITE`; `?tenantId=` mismatch ⇒ 403. Partner IT: claim context reads/updates own profile; foreign partner id ⇒ 403. Executive IT: verification status change audited; partner token cannot reach the endpoint.
- [ ] Step 2: exact command proving RED — `cd apps/sanad-platform && SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5433/sanad SPRING_DATASOURCE_USERNAME=sanad SPRING_DATASOURCE_PASSWORD=sanad_pass mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.commercial.api.TenantCommercialControllerIT,com.sanad.platform.commercial.api.PartnerCommercialControllerIT,com.sanad.platform.commercial.api.ExecutiveCommercialControllerIT test` → 404 routes (red).
- [ ] Step 3: exact minimal implementation — the three controllers + DTOs (bean validation, free-format tax fields).
- [ ] Step 4: exact command proving GREEN — same as Step 2 → green.
- [ ] Step 5: exact affected regression — `cd apps/sanad-platform && mvn -B -ntp -Dsurefire.useFile=false -Dtest=com.sanad.platform.security.TenantBindingSecurityIntegrationTest test` → green.
- [ ] Step 6: exact commit — `git commit -m "wave3(api): commercial identity surfaces for tenant/partner/executive (C5)"`.

### Task 12: Frontend — Commercial & Tax Information screen + branding panel

Files:
- Create: `apps/web/components/commercial/CommercialTaxInformationForm.tsx`, `apps/web/components/commercial/BrandingPanel.tsx`, `apps/web/app/workspace/settings/commercial/page.tsx`, `apps/web/app/executive/commercial/platform/page.tsx`, `apps/web/lib/api/commercial-api.ts`, `apps/web/lib/api/commercial-api.test.ts`
- Modify: `apps/web/app/executive/partners/[id]/page.tsx` (agreement tab now renders the real screen, replacing the W2 `ScpEmpty` placeholder), i18n `ar.ts`/`en.ts` (`commercial.*` keys, same commit)
- Test: `commercial-api.test.ts` + form component tests

Interfaces:
- Consumes: apiClient FormData passthrough with `Idempotency-Key` (crm-imports precedent); `--snad-*` tokens; logical CSS properties.
- Produces: the single reusable §14.1 screen mounted for tenant (`/workspace/settings/commercial`), platform owner (`/executive/commercial/platform`), partner (executive partner detail tab).

- [ ] Step 1: exact failing test — `apps/web/lib/api/commercial-api.test.ts`: exact URLs `/api/v1/commercial/profile`, `/api/v1/commercial/branding`, `/api/v1/commercial/logo`, `/api/v1/partner/commercial/profile`, `/api/v1/executive/commercial/platform` + typed exports.
- [ ] Step 2: exact command proving RED — `cd apps/web && npm test -- commercial` → red.
- [ ] Step 3: exact minimal implementation — client + form + branding panel + three mounts + i18n.
- [ ] Step 4: exact command proving GREEN — `cd apps/web && npm test -- commercial` → green.
- [ ] Step 5: exact affected regression — `cd apps/web && npm run typecheck && npm run lint && npm test && python3 scripts/ci/check_i18n_keys.py` → green.
- [ ] Step 6: exact commit — `git commit -m "wave3(web): commercial tax information screen + branding (C6)"`.

### Task 13: Wave exit evidence battery

Files:
- Modify: none (evidence only)
- Test: full local battery

Interfaces:
- Consumes: PostgreSQL 16 local battery.
- Produces: `snad-evidence/evidence-<HEAD-SHA>.log`.

- [ ] Step 1: exact failing test — none.
- [ ] Step 2: exact command proving RED — none.
- [ ] Step 3: exact minimal implementation — none.
- [ ] Step 4: exact command proving GREEN —
  `cd apps/web && npm ci && npm run lint && npx tsc --noEmit && npm test -- --reporter=verbose && npm run build`
  `cd apps/sanad-platform && mvn test -B -ntp -Dsurefire.useFile=false`
  `cd apps/sanad-platform && SPRING_PROFILES_ACTIVE=pg-acceptance PG_ACCEPTANCE_JDBC_URL='jdbc:postgresql://127.0.0.1:5432/pg_acceptance?prepareThreshold=0' PG_ACCEPTANCE_USERNAME=sanad PG_ACCEPTANCE_PASSWORD=sanad_pass mvn test -B -ntp -Dsurefire.useFile=true -DfailIfNoTests=true -Dtest='CommerceOrderPostgresConcurrencyTest,RbacAccessCheckPostgresAcceptanceTest,ModuleRegistryUatPostgresAcceptanceTest'` → 31/31 (6/15/10 unchanged).
- [ ] Step 5: exact affected regression — battery IS the regression; log at `snad-evidence/evidence-<HEAD-SHA>.log`.
- [ ] Step 6: exact commit — `git commit -m "wave3(evidence): gate run @ <HEAD-SHA> (C7)"`.

## Dependencies, security, rollback

**Dependencies:** W2 (partner context, delegation gate). Blocks W4 (snapshots + agreement identity). **Security:** profiles tenant/partner-jailed (composite FORCE RLS); principal uniqueness DB-enforced (partial indexes); snapshots physically immutable (trigger); `platform_files` FORCE hardening closes an unguarded shared table with ONE non-overbroad policy; uploads magic-byte-validated, size-capped, authorized reads only; no legal/tax rules hard-coded (§33). **Rollback:** flag `SANAD_COMMERCIAL_IDENTITY_ENABLED=false` ⇒ controllers 404-guarded and W4 issuance refuses snapshot capture; DDL additive; the platform_files RLS hardening is forward-only security improvement with a documented owner-held compensating script (re-creating the previous no-policy state) for emergency only; trigger removal script documented in the migration header (owner-run, never shipped as auto-down); revert commits safe.
