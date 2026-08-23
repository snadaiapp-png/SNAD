# CRM Contacts Collaboration Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL:
> Use superpowers:subagent-driven-development (recommended)
> or superpowers:executing-plans to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:**
Integrate Contacts with the accepted Collaboration/Event Foundation
using canonical ownership transfer, tenant-safe collaboration,
Contact RLS, Contact→Lead lineage, protected hard delete, structured
timeline/audit/outbox, and PostgreSQL Direct acceptance without
creating parallel ownership, audit, collaboration, or event systems.

**Architecture:**
Primary Contact ownership remains authoritative on crm_contacts /
existing ownership subsystem. Collaboration reuses
crm_entity_participants for COLLABORATOR and WATCHER only.
Sensitive operations are explicit commands. Domain mutation,
timeline, audit and outbox are transactionally atomic.

**Tech Stack:**
Java 21, Spring Boot 3, Spring JDBC, PostgreSQL 17, Flyway,
JUnit 5, AssertJ, existing AuditPort, existing TimelineEventPort
structured overload, existing CrmEventOutboxPort,
existing RecipientEligibilityPort, existing TenantRlsTransactionContext,
existing idempotency infrastructure.

**Spec:**
docs/superpowers/specs/2026-08-22-crm-collaboration-notifications-design.md

## Global Constraints

- PostgreSQL Direct is authoritative.
- No Docker. No Testcontainers.
- `bash ./mvnw` only. Never chmod mvnw.
- Migrations immutable. Never modify V20260822_1..4.
- No Flyway repair. No Flyway clean against shared sanad.
- New migrations forward-only.
- app.tenant_id transaction-local.
- No SUPERUSER/BYPASSRLS solution.
- No second ownership model, collaboration table, Audit store, or Outbox.
- No generic cross-entity collaboration mutation REST API.
- Participation never grants RBAC.
- Owner never participates as COLLABORATOR/WATCHER.
- Web UI implementation deferred.
- main must not be modified during implementation.
- Every implementation task follows RED → GREEN → REFACTOR.
- Every task ends with an independently reviewable commit.

## Locked Design Decisions

| Decision | Value |
|---|---|
| OWNERSHIP_STRATEGY | A (canonical transfer command) |
| MIXED_UPDATE_OWNER_STRATEGY | A1 (one atomic transaction) |
| SHARE_VERSION_STRATEGY | P2 (participant has own version; Contact version unchanged) |
| WATCH_ROLE_POLICY | W2 (mutually exclusive; role change replaces) |
| CONTACT_LEAD_LINEAGE_STRATEGY | L1 (source_contact_id on crm_leads) |
| CONTACT_RLS | ENABLE + FORCE + fail-closed |
| OWNER_PARTICIPANT_INVARIANT | BOTH (application + DB) |

## Migration Version Gate

- CURRENT_HIGHEST_MIGRATION: V20260822_4
- NEXT_FREE_MIGRATION: V20260823_1

## File Map

### FILES_TO_CREATE

| Path | Responsibility |
|---|---|
| `apps/sanad-platform/src/main/resources/db/migration/V20260823_1__crm_contacts_force_rls.sql` | Enable FORCE RLS on crm_contacts with fail-closed tenant policy |
| `apps/sanad-platform/src/main/resources/db/migration/V20260823_2__crm_participant_role_exclusivity.sql` | W2 invariant: one active participant per tenant/entity/user regardless of role + owner-participant exclusion trigger |
| `apps/sanad-platform/src/main/resources/db/migration/V20260823_3__crm_leads_source_contact_lineage.sql` | Add source_contact_id to crm_leads + unique active linked Lead guard |
| `apps/sanad-platform/src/main/resources/db/migration/V20260823_4__seed_crm_contact_collaboration_capabilities.sql` | Seed CRM.CONTACT.SHARE/WATCH/TRANSFER/HARD_DELETE/CONVERT_TO_LEAD capabilities + role bindings |
| `apps/sanad-platform/src/main/java/com/sanad/platform/crm/party/application/ContactCollaborationService.java` | Contact-specific participant orchestration (share, watch, remove, list) |
| `apps/sanad-platform/src/main/java/com/sanad/platform/crm/party/application/ContactTransferUseCases.java` | Canonical Contact owner transfer command orchestration |
| `apps/sanad-platform/src/main/java/com/sanad/platform/crm/party/application/ContactLeadConversionUseCases.java` | Non-destructive Contact→Lead conversion with lineage |
| `apps/sanad-platform/src/main/java/com/sanad/platform/crm/party/application/ContactHardDeleteUseCases.java` | Protected hard-delete with impact analysis |
| `apps/sanad-platform/src/main/java/com/sanad/platform/crm/party/web/ContactCollaborationController.java` | REST API for share/watch/participants/transfer/convert-to-lead/hard-delete |
| `apps/sanad-platform/src/main/java/com/sanad/platform/crm/party/web/ContactCollaborationDtos.java` | Request/response DTOs for collaboration operations |
| `apps/sanad-platform/src/test/java/com/sanad/platform/crm/party/ContactRlsPostgresTest.java` | RLS verification for crm_contacts |
| `apps/sanad-platform/src/test/java/com/sanad/platform/crm/party/ContactParticipantRoleExclusivityPostgresTest.java` | W2 + owner-participant invariant tests |
| `apps/sanad-platform/src/test/java/com/sanad/platform/crm/party/ContactTransferPostgresTest.java` | Canonical transfer + concurrency + edge cases |
| `apps/sanad-platform/src/test/java/com/sanad/platform/crm/party/ContactCollaborationHttpIntegrationTest.java` | HTTP integration for share/watch/remove/list |
| `apps/sanad-platform/src/test/java/com/sanad/platform/crm/party/ContactLeadConversionPostgresTest.java` | Contact→Lead non-destructive + duplicate + lineage |
| `apps/sanad-platform/src/test/java/com/sanad/platform/crm/party/ContactHardDeletePostgresTest.java` | Hard-delete guards + impact + audit |

### FILES_TO_MODIFY

| Path | Responsibility |
|---|---|
| `apps/sanad-platform/src/main/java/com/sanad/platform/crm/party/application/ContactUseCases.java` | Delegate owner change to ContactTransferUseCases; add structured timeline/outbox to archive/restore |
| `apps/sanad-platform/src/main/java/com/sanad/platform/crm/party/infrastructure/JdbcContactRepository.java` | Add tenant GUC for non-SpringBoot test paths; add hardDelete method |
| `apps/sanad-platform/src/main/java/com/sanad/platform/crm/web/CrmController.java` | Deprecate direct owner mutation in PATCH; delegate to canonical transfer |
| `apps/sanad-platform/src/main/java/com/sanad/platform/crm/web/CrmContractController.java` | Deprecate direct owner mutation in v2 PATCH; add hard-delete endpoint |

### FILES_TO_DELETE

None. FILES_TO_DELETE=0.

## RLS Caller Closure (Task C0 output — reconciled in Prompt 04)

28 production files reference `crm_contacts`. Reconciled classification:

| # | File | Class | R/W | Tx? | Sets GUC? | Classification | Change Required |
|---|---|---|---|---|---|---|---|
| 1 | CallerDatasetService | @Component @Service | READ | @Transactional | NO | REQUIRES_REMEDIATION | Upstream @Transactional + SecurityContextHolder from HTTP request; TenantRlsConnectionHandler applies GUC automatically. No direct change needed — verify upstream tx owner sets SecurityContext. |
| 2 | JdbcCallerIdentificationRepository | @Repository | READ | NO (called within upstream tx) | NO | NO_CHANGE_REQUIRED | Consumed by CallerDatasetService which is @Transactional. GUC applied by TenantRlsConnectionHandler from SecurityContextHolder. |
| 3 | JdbcExportRepository | @Repository | READ+DELETE | NO (called within upstream tx) | NO | NO_CHANGE_REQUIRED | Consumed by ExportController which is @Transactional. GUC applied by TenantRlsConnectionHandler. |
| 4 | JdbcCrmEntitySnapshotAdapter | @Repository | INDIRECT | NO | NO | NO_CHANGE_REQUIRED | References crm_contacts in snapshot queries; called within upstream @Transactional. GUC applied by TenantRlsConnectionHandler. |
| 5 | CrmV2AtomicMutationInfrastructureService | @Component | WRITE | @Transactional | NO | REQUIRES_REMEDIATION | Has @Transactional but no SecurityContextHolder usage for GUC. Verify upstream caller sets SecurityContext. If called from background, needs TenantRlsTransactionContext. |
| 6 | LegacyContactService | @Component | WRITE | @Transactional | NO | REQUIRES_REMEDIATION | Same as #5 — verify upstream SecurityContext or add TenantRlsTransactionContext for background paths. |
| 7 | LegacyCrmInfrastructureService | @Component | READ+WRITE | @Transactional | YES (TenantRlsTransactionContext) | ALREADY_RLS_SAFE | Already uses TenantRlsTransactionContext.applyForCurrentTransaction(). |
| 8 | LegacyDashboardService | @Component | READ+DELETE | @Transactional | NO | REQUIRES_REMEDIATION | Verify upstream SecurityContext. |
| 9 | LegacyImportService | @Component | INSERT | @Transactional | NO | REQUIRES_REMEDIATION | Verify upstream SecurityContext or TenantRlsTransactionContext for import worker path. |
| 10 | LegacySupport | @Component | INDIRECT | NO | NO | NO_CHANGE_REQUIRED | Utility/helper; called within upstream @Transactional. |
| 11 | PullSyncService | @Component | INDIRECT | @Transactional | YES (TenantRlsTransactionContext) | ALREADY_RLS_SAFE | Already uses TenantRlsTransactionContext. |
| 12 | PushSyncService | @Component | INDIRECT | NO | YES (TenantRlsTransactionContext) | ALREADY_RLS_SAFE | Already uses TenantRlsTransactionContext. |
| 13 | JdbcOwnershipRecordAdapter | @Repository | UPDATE | NO (called within upstream tx) | NO | NO_CHANGE_REQUIRED | Consumed by OwnershipCommandUseCases which is @Transactional. GUC applied by TenantRlsConnectionHandler. |
| 14 | CrmCoreCursorPaginationAspect | @Component (Aspect) | INDIRECT | NO (aspect around controller) | NO | NO_CHANGE_REQUIRED | Aspect wraps controller calls; operates within Spring-managed tx + SecurityContextHolder. GUC applied by TenantRlsConnectionHandler. |
| 15 | AuditedAddressCommunicationRepository | @Repository | INDIRECT | NO | NO | NO_CHANGE_REQUIRED | Called within upstream @Transactional. |
| 16 | JdbcAddressCommunicationRepository | @Repository | INDIRECT | NO | NO | NO_CHANGE_REQUIRED | Called within upstream @Transactional. |
| 17 | JdbcContactRelationshipRepository | @Repository | READ+WRITE | NO | NO | NO_CHANGE_REQUIRED | Called within upstream @Transactional (ContactRelationshipUseCases). |
| 18 | JdbcContactRepository | @Repository | READ+WRITE | NO | NO | NO_CHANGE_REQUIRED | Called within ContactUseCases which is @Transactional. GUC applied by TenantRlsConnectionHandler. |
| 19 | JdbcCustomerMasterRepository | @Repository | UPDATE | NO | NO | NO_CHANGE_REQUIRED | Called within upstream @Transactional. |
| 20 | JdbcPortalRepository | @Repository | READ+WRITE | NO | NO | NO_CHANGE_REQUIRED | Called within upstream @Transactional. |
| 21 | JdbcCustomer360QueryAdapter | @Repository | READ | NO | NO | NO_CHANGE_REQUIRED | Called within upstream @Transactional. |
| 22 | JdbcDashboardQueryAdapter | @Repository | READ+DELETE | NO | NO | NO_CHANGE_REQUIRED | Called within upstream @Transactional. |
| 23 | JdbcReportRepository | @Repository | READ+DELETE | NO | NO | NO_CHANGE_REQUIRED | Called within upstream @Transactional. |
| 24 | JdbcSearchRepository | @Repository | READ+DELETE | NO | NO | NO_CHANGE_REQUIRED | Called within upstream @Transactional. |
| 25 | CrmManagementIntegrationService | @Component | READ+DELETE | @Transactional | NO | REQUIRES_REMEDIATION | Verify upstream SecurityContext. |
| 26 | DataClassification | enum | NONE | NO | NO | DEAD_OR_UNREACHABLE | Enum — references crm_contacts in a static classification table list. No SQL execution. |
| 27 | ModuleResetRegistry | class | NONE | NO | NO | DEAD_OR_UNREACHABLE | Static registry of table names for module reset. No direct SQL. ModuleResetService handles GUC separately. |
| 28 | PerfTestBootstrapConfig | @Configuration | INSERT | NO | NO | DEAD_OR_UNREACHABLE | Test-only bootstrap configuration; not active in production. |

**CORRECTED COUNTS:**
- **CONTACT_RLS_CALLERS_TOTAL=28**
- **CONTACT_RLS_ALREADY_SAFE=3** (LegacyCrmInfrastructureService, PullSyncService, PushSyncService)
- **CONTACT_RLS_REQUIRES_REMEDIATION=6** (CallerDatasetService, CrmV2AtomicMutationInfrastructureService, LegacyContactService, LegacyDashboardService, LegacyImportService, CrmManagementIntegrationService)
- **CONTACT_RLS_NO_CHANGE_REQUIRED=16** (all @Repository/@Component callers consumed within upstream @Transactional that sets SecurityContextHolder)
- **CONTACT_RLS_TEST_ONLY_REMEDIATION=0**
- **CONTACT_RLS_DEAD_OR_UNREACHABLE=3** (DataClassification, ModuleResetRegistry, PerfTestBootstrapConfig)
- **CONTACT_RLS_UNKNOWN=0**
- **RLS_ARITHMETIC_SUM=3+6+16+0+3+0=28** ✓

**Key insight:** The 6 REQUIRES_REMEDIATION callers are @Component/@Service classes that have @Transactional but don't explicitly verify that SecurityContextHolder is populated. When called from HTTP request paths, `TenantRlsConnectionHandler` automatically applies `SET LOCAL app.tenant_id` from `SecurityContextHolder` — these are already safe in the HTTP path. The remediation is to verify/ensure that any background-job call path also sets SecurityContextHolder or uses `TenantRlsTransactionContext`. Most likely these 6 callers are already safe via the HTTP path and only need verification, not code changes.

## Task Decomposition

### Task C0: RLS Caller Closure Map

**Goal:** Classify every crm_contacts access path and confirm remediation strategy.

**Files:** Read-only analysis — no modifications.

**Done when:** Every caller is classified as SAFE or REQUIRING_FIX with exact remediation strategy documented.

- [ ] Step 1 — Enumerate all 28 production callers
- [ ] Step 2 — For each @Component caller, verify it executes within @Transactional + SecurityContextHolder
- [ ] Step 3 — Identify background job callers needing TenantRlsTransactionContext
- [ ] Step 4 — Verify no test caller would break after FORCE RLS (tests using MigrationTestSchemaSupport are already isolated)
- [ ] Step 5 — Document closure map in plan

### Task C1: Contact RLS Caller Remediation

**Goal:** Ensure all crm_contacts callers establish transaction-local tenant GUC.

**Files:**
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/mobile/sync/service/PullSyncService.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/mobile/sync/service/PushSyncService.java`
- Test: `apps/sanad-platform/src/test/java/com/sanad/platform/crm/party/ContactRlsPostgresTest.java`

**Done when:** All callers either (a) use Spring-managed DataSource with TenantRlsConnectionHandler (HTTP path), or (b) explicitly set TenantRlsTransactionContext (background path).

### Task C2: Contact FORCE RLS Migration

**Goal:** Enable FORCE RLS on crm_contacts with fail-closed tenant policy.

**Files:**
- Create: `apps/sanad-platform/src/main/resources/db/migration/V20260823_1__crm_contacts_force_rls.sql`
- Test: `apps/sanad-platform/src/test/java/com/sanad/platform/crm/party/ContactRlsPostgresTest.java`

**Migration DDL:**
```sql
ALTER TABLE crm_contacts ENABLE ROW LEVEL SECURITY;
ALTER TABLE crm_contacts FORCE ROW LEVEL SECURITY;
CREATE POLICY crm_contacts_tenant_isolation ON crm_contacts
    USING (tenant_id = current_setting('app.tenant_id', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::UUID);
```

- [ ] Step 1 — Write failing RLS test (missing GUC → 0 rows; wrong tenant → 0 rows; correct tenant → rows visible)
- [ ] Step 2 — Run failing test (RED)
- [ ] Step 3 — Create migration V20260823_1
- [ ] Step 4 — Run test (GREEN)
- [ ] Step 5 — Run foundation regression
- [ ] Step 6 — Commit

### Task C3: Participant Role Exclusivity + Owner-Participant Invariant

**Goal:** W2 DB invariant + owner != participant DB enforcement.

**Files:**
- Create: `apps/sanad-platform/src/main/resources/db/migration/V20260823_2__crm_participant_role_exclusivity.sql`
- Test: `apps/sanad-platform/src/test/java/com/sanad/platform/crm/party/ContactParticipantRoleExclusivityPostgresTest.java`

**Migration DDL:**
```sql
-- Drop old per-role unique index
DROP INDEX IF EXISTS uk_crm_entity_participants_active;
-- Create new per-user unique index (one active role per user per entity)
CREATE UNIQUE INDEX uk_crm_entity_participants_active
    ON crm_entity_participants (tenant_id, entity_type, entity_id, user_id)
    WHERE removed_at IS NULL;

-- Owner-participant exclusion trigger for CONTACT
CREATE OR REPLACE FUNCTION crm_check_contact_owner_not_participant()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_owner UUID;
BEGIN
    SELECT owner_user_id INTO v_owner FROM crm_contacts
    WHERE tenant_id = NEW.tenant_id AND id = NEW.entity_id
    FOR SHARE;
    IF v_owner IS NOT NULL AND v_owner = NEW.user_id THEN
        RAISE EXCEPTION 'CRM_OWNER_IS_PARTICIPANT: owner cannot be participant on contact %', NEW.entity_id
        USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END; $$;
CREATE TRIGGER trg_contact_owner_not_participant
    BEFORE INSERT OR UPDATE OF user_id ON crm_entity_participants
    FOR EACH ROW
    WHEN (NEW.entity_type = 'CONTACT')
    EXECUTE FUNCTION crm_check_contact_owner_not_participant();
```

- [ ] Step 1 — Write failing tests: same user cannot hold both roles; owner cannot be participant; concurrent role insertion
- [ ] Step 2 — Run failing tests (RED)
- [ ] Step 3 — Create migration V20260823_2
- [ ] Step 4 — Run tests (GREEN)
- [ ] Step 5 — Run participant concurrency regression
- [ ] Step 6 — Commit

### Task C4: Contact Participant Application Service

**Goal:** Contact-specific orchestration over CollaborationMembershipService.

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/party/application/ContactCollaborationService.java`
- Test: unit test + `ContactCollaborationHttpIntegrationTest.java`

**Commands:**
- `shareContact(tenantId, contactId, userId, actorId, occurredAt)` → adds COLLABORATOR
- `watchContact(tenantId, contactId, userId, actorId, occurredAt)` → adds WATCHER
- `removeParticipant(tenantId, contactId, participantId, actorId, occurredAt)`
- `listParticipants(tenantId, contactId)`

**W2 normalization:** If user is COLLABORATOR and WATCH is requested → mark existing removed, insert WATCHER. Same for reverse.

- [ ] Steps 1-7 (TDD cycle)

### Task C5: Canonical Contact Transfer

**Goal:** Single authoritative Contact owner-transfer orchestration.

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/party/application/ContactTransferUseCases.java`
- Test: `apps/sanad-platform/src/test/java/com/sanad/platform/crm/party/ContactTransferPostgresTest.java`

**Transaction:**
1. Lock Contact row (`SELECT ... FOR UPDATE`)
2. Validate actor RBAC (`CRM.CONTACT.TRANSFER`)
3. Validate recipient eligibility (active, same tenant, `CRM.CONTACT.READ`)
4. Validate `expectedVersion` matches
5. Capture `previousOwnerId`
6. Normalize target owner's participant membership (remove if active)
7. `UPDATE crm_contacts SET owner_user_id = :newOwner, version = version + 1 WHERE id = :id AND version = :expected`
8. If `retainPreviousOwnerAsWatcher=true` and `previousOwnerId != null` and `previousOwnerId != newOwner`: add WATCHER via `CollaborationMembershipService`
9. Timeline: `StructuredTimelineEvent` with `crm.contact.owner.transferred`
10. Audit: `OWNER_TRANSFER` action
11. Outbox: `contact.owner.transferred`
12. Commit

- [ ] Steps 1-7 (TDD cycle with all edge cases)

### Task C6: Legacy Owner Patch Adapter

**Goal:** Delegate legacy PATCH owner mutation to canonical transfer.

**Strategy A1:** If `UpdateContactRequest.ownerUserId` differs from current owner, invoke `ContactTransferUseCases.transferContact()` within the same `@Transactional` as ordinary field update.

**Files:**
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/party/application/ContactUseCases.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/web/CrmController.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/web/CrmContractController.java`

- [ ] Steps 1-7 (TDD cycle with regression)

### Task C7: RBAC + Recipient Eligibility

**Goal:** Seed new capabilities + wire eligibility.

**Files:**
- Create: `apps/sanad-platform/src/main/resources/db/migration/V20260823_4__seed_crm_contact_collaboration_capabilities.sql`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/party/web/ContactCollaborationController.java`

**New capabilities:** `CRM.CONTACT.SHARE`, `CRM.CONTACT.WATCH`, `CRM.CONTACT.TRANSFER`, `CRM.CONTACT.HARD_DELETE`, `CRM.CONTACT.CONVERT_TO_LEAD`

- [ ] Steps 1-7

### Task C8: Structured Timeline / Audit / Outbox

**Goal:** Wire structured events for all new + existing Contact operations.

**Files:**
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/party/application/ContactUseCases.java` (archive/restore structured timeline + outbox)
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/party/application/ContactCollaborationService.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/party/application/ContactTransferUseCases.java`

**Event catalog:**
| Operation | Timeline eventType | Audit action | Outbox eventType |
|---|---|---|---|
| Transfer | `crm.contact.owner.transferred` | `OWNER_TRANSFER` | `contact.owner.transferred` |
| Share | `crm.contact.collaborator.added` | `COLLABORATOR_ADD` | `contact.collaborator.added` |
| Remove | `crm.contact.collaborator.removed` | `COLLABORATOR_REMOVE` | `contact.collaborator.removed` |
| Watch | `crm.contact.watcher.added` | `WATCHER_ADD` | `contact.watcher.added` |
| Archive | `crm.contact.archived` (existing) | `ARCHIVE` (existing) | `contact.archived` (new) |
| Restore | `crm.contact.restored` (existing) | `RESTORE` (existing) | `contact.restored` (new) |
| Convert | `crm.contact.converted_to_lead` | `CONTACT_TO_LEAD` | `contact.converted_to_lead` |
| Hard Delete | `crm.contact.hard_deleted` | `HARD_DELETE` | `contact.hard_deleted` |

- [ ] Steps 1-7

### Task C9: Contact→Lead Lineage

**Goal:** Non-destructive Contact→Lead with L1 lineage.

**Files:**
- Create: `apps/sanad-platform/src/main/resources/db/migration/V20260823_3__crm_leads_source_contact_lineage.sql`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/party/application/ContactLeadConversionUseCases.java`
- Test: `apps/sanad-platform/src/test/java/com/sanad/platform/crm/party/ContactLeadConversionPostgresTest.java`

**Migration DDL:**
```sql
ALTER TABLE crm_leads ADD COLUMN source_contact_id UUID;
ALTER TABLE crm_leads ADD CONSTRAINT fk_crm_leads_source_contact_same_tenant
    FOREIGN KEY (tenant_id, source_contact_id) REFERENCES crm_contacts (tenant_id, id);
CREATE UNIQUE INDEX uk_crm_leads_active_source_contact
    ON crm_leads (tenant_id, source_contact_id)
    WHERE source_contact_id IS NOT NULL AND status NOT IN ('DISQUALIFIED', 'ARCHIVED');
```

**Duplicate policy:** If active Lead with same `source_contact_id` exists → return existing Lead reference (HTTP 200 with warning). No second Lead created.

- [ ] Steps 1-7

### Task C10: Protected Hard Delete

**Goal:** Explicit exceptional Contact hard-delete command.

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/party/application/ContactHardDeleteUseCases.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/party/infrastructure/JdbcContactRepository.java` (add `hardDelete` method)
- Test: `apps/sanad-platform/src/test/java/com/sanad/platform/crm/party/ContactHardDeletePostgresTest.java`

**Contract:**
- `DELETE /api/v2/crm/contacts/{contactId}` with `If-Match: {version}`
- Capability: `CRM.CONTACT.HARD_DELETE`
- Checks: participant history (existing trigger), activities, relationships, custom fields
- If blocked: 409 `DELETE_BLOCKED_BY_DEPENDENCIES`
- If no dependencies: DELETE row, audit `HARD_DELETE` (minimal PII), outbox `contact.hard_deleted`
- Audit fact preserved: `platform_audit_logs` retains the action record with minimal before-state

- [ ] Steps 1-7

### Task C11: HTTP API Surface

**Goal:** REST endpoints following repository conventions.

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/party/web/ContactCollaborationController.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/party/web/ContactCollaborationDtos.java`

**API Matrix:**

| Method | Path | Capability | Request | Response |
|---|---|---|---|---|
| POST | `/api/v2/crm/contacts/{id}/transfer` | `CRM.CONTACT.TRANSFER` | `{targetOwnerUserId, expectedVersion, retainPreviousOwnerAsWatcher}` | `200 ContactResponse` |
| POST | `/api/v2/crm/contacts/{id}/share` | `CRM.CONTACT.SHARE` | `{userId}` | `201 ParticipantResponse` |
| POST | `/api/v2/crm/contacts/{id}/watch` | `CRM.CONTACT.WATCH` | `{userId}` | `201 ParticipantResponse` |
| GET | `/api/v2/crm/contacts/{id}/participants` | `CRM.CONTACT.READ` | — | `200 [ParticipantResponse]` |
| DELETE | `/api/v2/crm/contacts/{id}/participants/{participantId}` | `CRM.CONTACT.WRITE` | `?expectedVersion=` | `204` |
| POST | `/api/v2/crm/contacts/{id}/convert-to-lead` | `CRM.CONTACT.CONVERT_TO_LEAD` | `{expectedVersion}` | `201 LeadResponse` or `200 {existing: true}` |
| DELETE | `/api/v2/crm/contacts/{id}` | `CRM.CONTACT.HARD_DELETE` | `If-Match: {version}` | `204` or `409` |

- [ ] Steps 1-7

### Task C12: PostgreSQL Direct Acceptance

**Goal:** Focused + regression + full suite acceptance.

- [ ] Step 1 — Run Contact-focused PostgreSQL Direct tests (RLS, participant, transfer, collaboration, lead, hard-delete)
- [ ] Step 2 — Run Contact regression (existing CRUD)
- [ ] Step 3 — Run CRM regression (Ownership, Transfer, Workflow)
- [ ] Step 4 — Run security/RLS regression
- [ ] Step 5 — Run full backend suite
- [ ] Step 6 — Verify `FAILURES=0, ERRORS=0, MANDATORY_POSTGRESQL_SKIPPED=0`

## Deferred UI

| Gap ID | Description | Deferred To |
|---|---|---|
| CONTACT-GAP-013 | Restore UI | Unified Web UX |
| CONTACT-GAP-014 | Timeline UI | Unified Web UX |
| — | Transfer modal | Unified Web UX |
| — | Share modal | Unified Web UX |
| — | Collaborator/Watcher picker | Unified Web UX |
| — | Participant list UI | Unified Web UX |
| — | Contact→Lead action UI | Unified Web UX |
| — | Hard-delete impact dialog | Unified Web UX |

**UI_IMPLEMENTATION_THIS_PHASE=NO**

## Gap Reclassification

| GAP_ID | FORENSIC_STATUS | EXECUTION_DECISION | IN_SCOPE | RESOLUTION_TASK |
|---|---|---|---|---|
| CONTACT-GAP-001 | ABSENT | Implement Share | YES | C4 |
| CONTACT-GAP-002 | ABSENT | Implement Watcher | YES | C4 |
| CONTACT-GAP-003 | ABSENT | Implement List | YES | C4 |
| CONTACT-GAP-004 | ABSENT | Implement Remove | YES | C4 |
| CONTACT-GAP-005 | ABSENT | Implement Transfer | YES | C5 |
| CONTACT-GAP-006 | ABSENT | Implement prev-owner-watcher | YES | C5 |
| CONTACT-GAP-007 | ABSENT | Implement Contact→Lead | YES | C9 |
| CONTACT-GAP-008 | ABSENT | Implement RLS | YES | C2 |
| CONTACT-GAP-009 | ABSENT | Wire outbox | YES | C8 |
| CONTACT-GAP-010 | ABSENT | Wire structured timeline | YES | C8 |
| CONTACT-GAP-011 | ABSENT | Add dedicated RBAC | YES | C7 |
| CONTACT-GAP-012 | ABSENT | Enforce owner exclusion | YES | C3 |
| CONTACT-GAP-013 | ABSENT | Restore UI | NO | Deferred |
| CONTACT-GAP-014 | ABSENT | Timeline UI | NO | Deferred |

**TOTAL_REVALIDATED_GAPS=14**
**TOTAL_BACKEND_IN_SCOPE_GAPS=12**
**TOTAL_DEFERRED_UI_GAPS=2**

## Risk Reassessment

| RISK_ID | Severity | Mitigation | Blocks |
|---|---|---|---|
| CONTACT-RISK-001 | HIGH | C2 resolves — FORCE RLS on crm_contacts | NO (after C0+C1) |
| CONTACT-RISK-002 | MEDIUM | C5+C6 resolve — canonical transfer + legacy delegation | NO |
| CONTACT-RISK-003 | MEDIUM | C8 resolves — outbox for all Contact mutations | NO |
| CONTACT-RISK-004 | LOW | C4 resolves — foundation wiring | NO |
| CONTACT-RISK-005 (new) | MEDIUM | C3 resolves — W2 race safety + owner-participant invariant | NO |
| CONTACT-RISK-006 (new) | MEDIUM | C9 resolves — Contact→Lead duplicate race | NO |

## Plan Self-Review

1. **Spec coverage:** All approved design invariants covered ✓
2. **Prompt 02 contract:** Ownership A, mixed A1, P2, W2, L1, RLS, both-invariant ✓
3. **Mandatory corrections:** W2 DB invariant (C3), owner-participant DB (C3), gap counts corrected (12+2), RLS caller closure (C0+C1) ✓
4. **Placeholder scan:** No TODO/TBD/placeholder ✓
5. **Migration collision:** V20260823_1..4 — next free after V20260822_4 ✓
6. **Type consistency:** All commands use UUID tenantId/contactId/userId, long expectedVersion, Instant occurredAt ✓
7. **Task dependency:** C0→C1→C2→C3→C4→C5→C6→C7+C8→C9→C10→C11→C12 ✓
8. **Test coverage:** Each task has dedicated PostgreSQL Direct test ✓
9. **UI scope leak:** No UI implementation in any task ✓
10. **Docker/Testcontainers scan:** None ✓
