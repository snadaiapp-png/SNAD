# CRM Collaboration & Event Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the shared, tenant-safe collaboration membership and durable event primitives that Contacts, Tasks, Cases, Notes, and Notifications will reuse, without creating a second ownership system or changing domain lifecycles yet.

**Architecture:** Preserve the existing `crm/ownership` module as the ownership/assignment path and preserve the existing `crm_timeline_events`, `AuditPort`, `CorrelationContextPort`, and `crm_idempotency_records`. Add a focused `crm/collaboration` module for secondary participation (`COLLABORATOR`, `WATCHER`), extend the existing timeline port compatibly for structured events, and add a durable CRM event outbox port/repository. Domain-specific transfer/share/review/resolve APIs remain for later plans; this foundation exposes reusable application/domain ports only.

**Tech Stack:** Java 21, Spring Boot 3, Spring JDBC (`NamedParameterJdbcTemplate`), PostgreSQL, Flyway, Jackson, JUnit 5, AssertJ, Spring transactions, existing SNAD RBAC (`CapabilityEvaluationService`), existing centralized audit/timeline integration.

**Spec:** `docs/superpowers/specs/2026-08-22-crm-collaboration-notifications-design.md`

## Global Constraints

- Baseline for this plan: `ffb856fa9b7ffb2a7294d8a5094937150f74841b`; rebase before execution and resolve migration-number collisions before writing migrations.
- PostgreSQL remains the source of truth; do not introduce Event Sourcing.
- PostgreSQL Direct is the governing database test path; do not introduce Docker or Testcontainers.
- Do not create a second owner source. Primary owner/assignee remains on the domain entity and/or the existing ownership/assignment path.
- `crm_entity_participants` stores only `COLLABORATOR` and `WATCHER`; it must never store `OWNER` or `REVIEWER`.
- Participation never grants RBAC capabilities. Recipient eligibility is an additional condition, not an authorization grant.
- Preserve existing `OwnershipCommandUseCases`, `crm_assignments`, `crm_ownership_history`, `AuditPort`, `JdbcAuditAdapter`, `CorrelationContextPort`, `crm_timeline_events`, and `crm_idempotency_records` unless a task below explicitly extends them.
- Existing callers of the legacy `TimelineEventPort.record(...)` signature must continue to compile and behave unchanged.
- New structured timeline/event writes must carry a correlation identifier and schema version.
- Migrations are forward-only and additive-first. Do not drop legacy timeline columns or existing constraints needed by G7/G8.
- Every new tenant-owned table/query must be tenant-scoped and protected by PostgreSQL RLS consistent with the platform `app.tenant_id` convention.
- No generic cross-entity mutation REST endpoint is introduced in this plan. Domain-specific APIs come in Contacts/Tasks/Cases/Notes plans.
- All implementation tasks follow TDD: failing test, observed failure, minimal implementation, passing test, commit.

---

## Repository Facts That Control This Plan

1. `crm_contacts` already contains `owner_user_id`; the master design must not move ownership into participants.
2. `crm/ownership/application/OwnershipCommandUseCases.java` already handles generic ownership assignment/reassignment and projects owner changes into CRM records.
3. `crm_timeline_events` already exists with legacy columns `subject_type`, `subject_id`, `event_type`, `summary`, `source_type`, `source_id`, `occurred_at`, `created_by`; extend it instead of creating another timeline table.
4. `TimelineEventPort` is currently a single-abstract-method interface used by lambdas in tests. Compatibility must be preserved.
5. `JdbcAuditAdapter` already obtains correlation through `CorrelationContextPort`; do not create a CRM-local audit ledger.
6. `crm_idempotency_records` already provides the CRM request-idempotency store; later command plans must reuse it rather than create another request ledger.
7. CRM intelligence uses in-process Spring events, but no durable general CRM outbox was found at the baseline. This plan adds the durable persistence primitive; it does not replace existing in-process intelligence events.

---

## File Structure Locked by This Plan

**Create**

- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/collaboration/domain/CollaborationEntityType.java` — supported polymorphic collaboration subjects.
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/collaboration/domain/ParticipantRole.java` — `COLLABORATOR` / `WATCHER` only.
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/collaboration/domain/EntityParticipant.java` — participant domain record and invariants.
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/collaboration/domain/EntityParticipantRepository.java` — persistence port.
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/collaboration/domain/RecipientEligibilityPort.java` — active-user + capability eligibility port.
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/collaboration/application/CollaborationMembershipService.java` — reusable membership service; no ownership mutation.
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/collaboration/application/CollaborationModuleConfiguration.java` — bean wiring.
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/collaboration/infrastructure/JdbcEntityParticipantRepository.java` — tenant-scoped JDBC persistence.
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/collaboration/infrastructure/PlatformRecipientEligibilityAdapter.java` — adapter over active-user validation and `CapabilityEvaluationService`.
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/integration/domain/CrmEventOutboxPort.java` — durable event-outbox port and event envelope.
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/integration/infrastructure/JdbcCrmEventOutboxAdapter.java` — JDBC outbox implementation.
- `apps/sanad-platform/src/main/resources/db/migration/V20260822_1__crm_collaboration_event_foundation.sql` — participants, outbox, timeline extension, indexes.
- `apps/sanad-platform/src/main/resources/db/migration/V20260822_2__crm_collaboration_event_rls.sql` — RLS/force-RLS policies. If either version is occupied after rebase, rename both to the next free ordered `V20260822_N` values before implementation.
- `apps/sanad-platform/src/test/java/com/sanad/platform/crm/collaboration/CrmCollaborationSchemaPostgresTest.java`
- `apps/sanad-platform/src/test/java/com/sanad/platform/crm/collaboration/domain/EntityParticipantTest.java`
- `apps/sanad-platform/src/test/java/com/sanad/platform/crm/collaboration/infrastructure/JdbcEntityParticipantRepositoryPostgresTest.java`
- `apps/sanad-platform/src/test/java/com/sanad/platform/crm/collaboration/application/CollaborationMembershipServiceTest.java`
- `apps/sanad-platform/src/test/java/com/sanad/platform/crm/integration/JdbcStructuredTimelineEventPostgresTest.java`
- `apps/sanad-platform/src/test/java/com/sanad/platform/crm/integration/JdbcCrmEventOutboxPostgresTest.java`

**Modify**

- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/integration/domain/TimelineEventPort.java` — add a structured-event default overload without breaking the existing SAM signature.
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/integration/infrastructure/JdbcTimelineEventAdapter.java` — persist structured fields while preserving legacy writes.

**Explicitly not modified in this plan**

- Contact/Task/Case domain lifecycle classes.
- `OwnershipCommandUseCases` transfer semantics.
- Web CRM pages/controllers.
- Notification Center, Email, WhatsApp, SLA, Notes sharing.
- RBAC capability seed matrix for domain actions such as `CRM.TASK.SHARE`; those belong to the domain plans that consume this foundation.

---

### Task 1: Prove and install the collaboration/event schema

**Files:**
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/crm/collaboration/CrmCollaborationSchemaPostgresTest.java`
- Create: `apps/sanad-platform/src/main/resources/db/migration/V20260822_1__crm_collaboration_event_foundation.sql`
- Create: `apps/sanad-platform/src/main/resources/db/migration/V20260822_2__crm_collaboration_event_rls.sql`

**Interfaces:**
- Produces table `crm_entity_participants`.
- Produces table `crm_event_outbox`.
- Extends existing `crm_timeline_events` with `summary_key`, `metadata_json`, `correlation_id`, `causation_id`, `schema_version`.
- Preserves legacy timeline columns and old rows.
- Enables tenant RLS for the two new tables and the timeline table using `app.tenant_id`.

- [ ] **Step 1: Write the failing PostgreSQL schema test**

Create `CrmCollaborationSchemaPostgresTest.java` following the existing `Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(...)` + Flyway setup pattern. The core assertions must be concrete:

```java
@Test
void collaborationFoundationSchemaExistsWithRequiredConstraints() {
    assertThat(columnExists("crm_entity_participants", "role")).isTrue();
    assertThat(columnExists("crm_event_outbox", "correlation_id")).isTrue();
    assertThat(columnExists("crm_timeline_events", "summary_key")).isTrue();
    assertThat(columnExists("crm_timeline_events", "metadata_json")).isTrue();
    assertThat(columnExists("crm_timeline_events", "schema_version")).isTrue();

    assertThat(checkConstraintDefinition("ck_crm_entity_participants_role"))
            .contains("COLLABORATOR")
            .contains("WATCHER")
            .doesNotContain("OWNER")
            .doesNotContain("REVIEWER");
}

@Test
void newTenantOwnedTablesHaveForcedRls() {
    assertThat(forceRls("crm_entity_participants")).isTrue();
    assertThat(forceRls("crm_event_outbox")).isTrue();
}
```

Use `information_schema.columns`, `pg_constraint`, and `pg_class.relforcerowsecurity` for the helper queries. Include an assertion that the existing legacy columns `summary`, `source_type`, and `source_id` still exist after migration.

- [ ] **Step 2: Run the test and verify it fails for missing schema**

Run:

```bash
cd apps/sanad-platform
./mvnw -Dtest=CrmCollaborationSchemaPostgresTest test
```

Expected: FAIL because `crm_entity_participants`, `crm_event_outbox`, and the structured timeline columns do not yet exist.

- [ ] **Step 3: Add the additive foundation migration**

Create `V20260822_1__crm_collaboration_event_foundation.sql` with this concrete shape:

```sql
CREATE TABLE crm_entity_participants (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    entity_type VARCHAR(32) NOT NULL,
    entity_id UUID NOT NULL,
    user_id UUID NOT NULL,
    role VARCHAR(24) NOT NULL,
    added_by_user_id UUID NOT NULL,
    added_at TIMESTAMP WITH TIME ZONE NOT NULL,
    removed_by_user_id UUID,
    removed_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_crm_entity_participants PRIMARY KEY (id),
    CONSTRAINT fk_crm_entity_participants_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT fk_crm_entity_participants_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_crm_entity_participants_added_by FOREIGN KEY (added_by_user_id) REFERENCES users(id),
    CONSTRAINT fk_crm_entity_participants_removed_by FOREIGN KEY (removed_by_user_id) REFERENCES users(id),
    CONSTRAINT ck_crm_entity_participants_entity_type CHECK (entity_type IN ('CONTACT','TASK','CASE')),
    CONSTRAINT ck_crm_entity_participants_role CHECK (role IN ('COLLABORATOR','WATCHER')),
    CONSTRAINT ck_crm_entity_participants_removed_state CHECK (
        (removed_at IS NULL AND removed_by_user_id IS NULL)
        OR (removed_at IS NOT NULL AND removed_by_user_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uk_crm_entity_participants_active
    ON crm_entity_participants (tenant_id, entity_type, entity_id, user_id, role)
    WHERE removed_at IS NULL;

CREATE INDEX idx_crm_entity_participants_entity
    ON crm_entity_participants (tenant_id, entity_type, entity_id, role, added_at DESC);

CREATE INDEX idx_crm_entity_participants_user
    ON crm_entity_participants (tenant_id, user_id, role, added_at DESC)
    WHERE removed_at IS NULL;

ALTER TABLE crm_timeline_events ADD COLUMN summary_key VARCHAR(160);
ALTER TABLE crm_timeline_events ADD COLUMN metadata_json TEXT;
ALTER TABLE crm_timeline_events ADD COLUMN correlation_id VARCHAR(160);
ALTER TABLE crm_timeline_events ADD COLUMN causation_id VARCHAR(160);
ALTER TABLE crm_timeline_events ADD COLUMN schema_version INTEGER NOT NULL DEFAULT 1;

CREATE INDEX idx_crm_timeline_correlation
    ON crm_timeline_events (tenant_id, correlation_id, occurred_at DESC)
    WHERE correlation_id IS NOT NULL;

CREATE TABLE crm_event_outbox (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    event_type VARCHAR(160) NOT NULL,
    schema_version INTEGER NOT NULL,
    aggregate_type VARCHAR(40) NOT NULL,
    aggregate_id UUID NOT NULL,
    correlation_id VARCHAR(160) NOT NULL,
    causation_id VARCHAR(160),
    payload_json TEXT NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMP WITH TIME ZONE NOT NULL,
    claimed_at TIMESTAMP WITH TIME ZONE,
    published_at TIMESTAMP WITH TIME ZONE,
    last_error VARCHAR(2000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_crm_event_outbox PRIMARY KEY (id),
    CONSTRAINT fk_crm_event_outbox_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT ck_crm_event_outbox_schema_version CHECK (schema_version > 0),
    CONSTRAINT ck_crm_event_outbox_status CHECK (status IN ('PENDING','PROCESSING','PUBLISHED','FAILED')),
    CONSTRAINT ck_crm_event_outbox_attempt_count CHECK (attempt_count >= 0)
);

CREATE UNIQUE INDEX uk_crm_event_outbox_event
    ON crm_event_outbox (tenant_id, id);
CREATE INDEX idx_crm_event_outbox_pending
    ON crm_event_outbox (status, available_at, created_at)
    WHERE status IN ('PENDING','FAILED');
CREATE INDEX idx_crm_event_outbox_correlation
    ON crm_event_outbox (tenant_id, correlation_id, created_at DESC);
```

Do not make new structured columns `NOT NULL`; existing timeline rows and legacy writers must remain valid. `schema_version` may safely default to `1` for legacy rows.

- [ ] **Step 4: Add PostgreSQL RLS migration**

Create `V20260822_2__crm_collaboration_event_rls.sql` using the platform-standard GUC:

```sql
ALTER TABLE crm_entity_participants ENABLE ROW LEVEL SECURITY;
ALTER TABLE crm_entity_participants FORCE ROW LEVEL SECURITY;
CREATE POLICY crm_entity_participants_tenant_isolation ON crm_entity_participants
    USING (tenant_id = current_setting('app.tenant_id', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::UUID);

ALTER TABLE crm_event_outbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE crm_event_outbox FORCE ROW LEVEL SECURITY;
CREATE POLICY crm_event_outbox_tenant_isolation ON crm_event_outbox
    USING (tenant_id = current_setting('app.tenant_id', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::UUID);

ALTER TABLE crm_timeline_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE crm_timeline_events FORCE ROW LEVEL SECURITY;
CREATE POLICY crm_timeline_events_tenant_isolation ON crm_timeline_events
    USING (tenant_id = current_setting('app.tenant_id', true)::UUID)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::UUID);
```

Before committing, run the existing timeline/integration PostgreSQL tests as part of Step 5. If an existing runtime path writes timeline rows without the platform tenant transaction context, fix that caller/transaction context in the same task; do not weaken RLS to make tests green.

- [ ] **Step 5: Run schema plus existing integration tests**

Run:

```bash
cd apps/sanad-platform
./mvnw -Dtest=CrmCollaborationSchemaPostgresTest,CrmWorkflowIntegrationPostgresTest test
```

Expected: PASS. Also confirm Flyway validation succeeds with both migration locations used by `Crm009TestEnvironment` tests.

- [ ] **Step 6: Commit**

```bash
git add apps/sanad-platform/src/main/resources/db/migration/V20260822_1__crm_collaboration_event_foundation.sql \
        apps/sanad-platform/src/main/resources/db/migration/V20260822_2__crm_collaboration_event_rls.sql \
        apps/sanad-platform/src/test/java/com/sanad/platform/crm/collaboration/CrmCollaborationSchemaPostgresTest.java
git commit -m "feat(crm): add collaboration event foundation schema"
```

---

### Task 2: Define the collaboration participant domain contract

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/collaboration/domain/CollaborationEntityType.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/collaboration/domain/ParticipantRole.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/collaboration/domain/EntityParticipant.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/collaboration/domain/EntityParticipantRepository.java`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/crm/collaboration/domain/EntityParticipantTest.java`

**Interfaces:**
- Produces `CollaborationEntityType { CONTACT, TASK, CASE }`.
- Produces `ParticipantRole { COLLABORATOR, WATCHER }`.
- Produces immutable `EntityParticipant` and repository port consumed by Tasks 3 and 6.

- [ ] **Step 1: Write failing domain tests**

```java
@Test
void activeParticipantHasNoRemovalFacts() {
    EntityParticipant participant = EntityParticipant.active(
            UUID.randomUUID(), UUID.randomUUID(), CollaborationEntityType.TASK,
            UUID.randomUUID(), UUID.randomUUID(), ParticipantRole.COLLABORATOR,
            UUID.randomUUID(), Instant.parse("2026-08-22T00:00:00Z"));

    assertThat(participant.isActive()).isTrue();
    assertThat(participant.removedAt()).isNull();
    assertThat(participant.removedByUserId()).isNull();
}

@Test
void removedParticipantCarriesActorAndTimestamp() {
    EntityParticipant active = participant();
    UUID remover = UUID.randomUUID();
    Instant at = Instant.parse("2026-08-22T01:00:00Z");

    EntityParticipant removed = active.remove(remover, at);

    assertThat(removed.isActive()).isFalse();
    assertThat(removed.removedByUserId()).isEqualTo(remover);
    assertThat(removed.removedAt()).isEqualTo(at);
    assertThat(removed.version()).isEqualTo(active.version() + 1);
}
```

Also assert construction rejects null tenant/entity/user/role and rejects removal with only one of removal actor/time.

- [ ] **Step 2: Run test and verify it fails**

```bash
cd apps/sanad-platform
./mvnw -Dtest=EntityParticipantTest test
```

Expected: FAIL because the collaboration domain types do not exist.

- [ ] **Step 3: Implement the minimal domain types**

Use these signatures:

```java
public enum CollaborationEntityType { CONTACT, TASK, CASE }
public enum ParticipantRole { COLLABORATOR, WATCHER }

public record EntityParticipant(
        UUID id,
        UUID tenantId,
        CollaborationEntityType entityType,
        UUID entityId,
        UUID userId,
        ParticipantRole role,
        UUID addedByUserId,
        Instant addedAt,
        UUID removedByUserId,
        Instant removedAt,
        long version) {

    public static EntityParticipant active(UUID id, UUID tenantId,
            CollaborationEntityType entityType, UUID entityId, UUID userId,
            ParticipantRole role, UUID addedByUserId, Instant addedAt) { /* validate + return */ }

    public boolean isActive() { return removedAt == null; }

    public EntityParticipant remove(UUID actorId, Instant at) { /* validate + copy with version+1 */ }
}
```

Repository contract:

```java
public interface EntityParticipantRepository {
    EntityParticipant insert(EntityParticipant participant);
    Optional<EntityParticipant> findActive(UUID tenantId, CollaborationEntityType entityType,
            UUID entityId, UUID userId, ParticipantRole role);
    List<EntityParticipant> listActive(UUID tenantId, CollaborationEntityType entityType,
            UUID entityId);
    boolean markRemoved(UUID tenantId, UUID participantId, long expectedVersion,
            UUID removedByUserId, Instant removedAt);
}
```

- [ ] **Step 4: Run domain test**

```bash
cd apps/sanad-platform
./mvnw -Dtest=EntityParticipantTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/sanad-platform/src/main/java/com/sanad/platform/crm/collaboration/domain \
        apps/sanad-platform/src/test/java/com/sanad/platform/crm/collaboration/domain/EntityParticipantTest.java
git commit -m "feat(crm): define collaboration participant domain"
```

---

### Task 3: Implement tenant-scoped participant persistence

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/collaboration/infrastructure/JdbcEntityParticipantRepository.java`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/crm/collaboration/infrastructure/JdbcEntityParticipantRepositoryPostgresTest.java`

**Interfaces:**
- Consumes: `EntityParticipantRepository`, `EntityParticipant`, `CollaborationEntityType`, `ParticipantRole` from Task 2.
- Produces: `JdbcEntityParticipantRepository` for application services.

- [ ] **Step 1: Write failing PostgreSQL repository tests**

Cover these exact cases:

```java
@Test
void insertFindListAndSoftRemoveAreTenantScoped() { /* tenant A data is not returned for tenant B */ }

@Test
void duplicateActiveRoleForSameUserAndEntityIsRejectedByDatabase() { /* second insert throws DataIntegrityViolationException */ }

@Test
void removedRoleCanBeAddedAgainAsNewHistoryRow() { /* remove first, insert second, two rows total, one active */ }

@Test
void staleVersionCannotRemoveParticipant() { /* markRemoved(... expectedVersion+1 ...) returns false */ }
```

Use the existing PostgreSQL Direct Flyway setup pattern. For each transactional query against forced-RLS tables, set the tenant locally before repository calls:

```java
jdbc.getJdbcTemplate().execute("SELECT set_config('app.tenant_id', '" + tenantId + "', true)");
```

Prefer parameterized `SELECT set_config('app.tenant_id', :tenantId, true)` through `NamedParameterJdbcTemplate` where practical.

- [ ] **Step 2: Run and verify failure**

```bash
cd apps/sanad-platform
./mvnw -Dtest=JdbcEntityParticipantRepositoryPostgresTest test
```

Expected: FAIL because `JdbcEntityParticipantRepository` does not exist.

- [ ] **Step 3: Implement JDBC repository**

Use explicit tenant predicates in every query even though RLS exists. `insert` writes all domain columns. `findActive` must include:

```sql
WHERE tenant_id = :tenantId
  AND entity_type = :entityType
  AND entity_id = :entityId
  AND user_id = :userId
  AND role = :role
  AND removed_at IS NULL
```

`listActive` orders deterministically by `added_at ASC, id ASC`.

`markRemoved` uses optimistic concurrency:

```sql
UPDATE crm_entity_participants
SET removed_by_user_id = :removedBy,
    removed_at = :removedAt,
    version = version + 1
WHERE tenant_id = :tenantId
  AND id = :id
  AND version = :expectedVersion
  AND removed_at IS NULL
```

Return `updated == 1`; never retry a stale version silently.

- [ ] **Step 4: Run repository tests**

```bash
cd apps/sanad-platform
./mvnw -Dtest=JdbcEntityParticipantRepositoryPostgresTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/sanad-platform/src/main/java/com/sanad/platform/crm/collaboration/infrastructure/JdbcEntityParticipantRepository.java \
        apps/sanad-platform/src/test/java/com/sanad/platform/crm/collaboration/infrastructure/JdbcEntityParticipantRepositoryPostgresTest.java
git commit -m "feat(crm): persist collaboration participants"
```

---

### Task 4: Extend the existing timeline with a backward-compatible structured event API

**Files:**
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/integration/domain/TimelineEventPort.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/integration/infrastructure/JdbcTimelineEventAdapter.java`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/crm/integration/JdbcStructuredTimelineEventPostgresTest.java`

**Interfaces:**
- Preserves existing abstract method `record(UUID, String, UUID, String, String, String, UUID, UUID, Instant)` exactly.
- Adds `record(StructuredTimelineEvent event)` as a **default** method so existing lambda-based tests remain source-compatible.
- `JdbcTimelineEventAdapter` overrides both methods; legacy calls write the old columns, structured calls also write the new columns.

- [ ] **Step 1: Write failing compatibility and persistence tests**

First, retain a compile-time legacy lambda in the test:

```java
TimelineEventPort legacy = (tenant, type, id, event, summary, source, sourceId, actor, at) -> {};
assertThat(legacy).isNotNull();
```

Then test a structured insert:

```java
TimelineEventPort.StructuredTimelineEvent event = new TimelineEventPort.StructuredTimelineEvent(
        tenantId, "TASK", taskId, "TASK_COLLABORATOR_ADDED",
        "crm.task.collaborator_added", "CRM participant added",
        "COLLABORATION_PARTICIPANT", participantId, actorId,
        occurredAt, "corr-123", "cause-456", 1,
        mapper.createObjectNode().put("participantUserId", userId.toString()));

adapter.record(event);
```

Assert the DB row stores `summary_key='crm.task.collaborator_added'`, `correlation_id='corr-123'`, `causation_id='cause-456'`, `schema_version=1`, and metadata JSON containing `participantUserId`.

- [ ] **Step 2: Run and verify failure**

```bash
cd apps/sanad-platform
./mvnw -Dtest=JdbcStructuredTimelineEventPostgresTest test
```

Expected: FAIL because `StructuredTimelineEvent`/overload does not exist.

- [ ] **Step 3: Add the compatible port extension**

Keep the current abstract method unchanged. Add:

```java
record StructuredTimelineEvent(
        UUID tenantId,
        String subjectType,
        UUID subjectId,
        String eventType,
        String summaryKey,
        String summary,
        String sourceType,
        UUID sourceId,
        UUID actorId,
        Instant occurredAt,
        String correlationId,
        String causationId,
        int schemaVersion,
        JsonNode metadata) {
    public StructuredTimelineEvent {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(subjectType);
        Objects.requireNonNull(subjectId);
        Objects.requireNonNull(eventType);
        Objects.requireNonNull(summary);
        Objects.requireNonNull(sourceType);
        Objects.requireNonNull(sourceId);
        Objects.requireNonNull(actorId);
        Objects.requireNonNull(occurredAt);
        if (schemaVersion < 1) throw new IllegalArgumentException("schemaVersion must be >= 1");
    }
}

default void record(StructuredTimelineEvent event) {
    record(event.tenantId(), event.subjectType(), event.subjectId(), event.eventType(),
            event.summary(), event.sourceType(), event.sourceId(), event.actorId(), event.occurredAt());
}
```

The default method intentionally degrades to legacy storage for non-JDBC test doubles/adapters. `JdbcTimelineEventAdapter` must override it and persist every structured field.

- [ ] **Step 4: Implement structured JDBC insert**

Serialize metadata with the existing Jackson `ObjectMapper`; inject it into `JdbcTimelineEventAdapter` if the adapter does not already receive one. Structured insert must target the same `crm_timeline_events` table and include both legacy and new columns. Do not create a parallel table.

- [ ] **Step 5: Run new and existing timeline/ownership tests**

```bash
cd apps/sanad-platform
./mvnw -Dtest=JdbcStructuredTimelineEventPostgresTest,OwnershipCommandUseCasesPostgresTest,CrmWorkflowIntegrationPostgresTest test
```

Expected: PASS, proving the old functional-interface usage still works.

- [ ] **Step 6: Commit**

```bash
git add apps/sanad-platform/src/main/java/com/sanad/platform/crm/integration/domain/TimelineEventPort.java \
        apps/sanad-platform/src/main/java/com/sanad/platform/crm/integration/infrastructure/JdbcTimelineEventAdapter.java \
        apps/sanad-platform/src/test/java/com/sanad/platform/crm/integration/JdbcStructuredTimelineEventPostgresTest.java
git commit -m "feat(crm): add structured timeline events"
```

---

### Task 5: Add the durable CRM event outbox persistence port

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/integration/domain/CrmEventOutboxPort.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/integration/infrastructure/JdbcCrmEventOutboxAdapter.java`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/crm/integration/JdbcCrmEventOutboxPostgresTest.java`

**Interfaces:**
- Produces durable `CrmEventEnvelope` persistence consumed by domain command plans and the later Notification Platform plan.
- This task does **not** create a scheduler/worker or call Email/WhatsApp.

- [ ] **Step 1: Write failing outbox tests**

Cover persistence and claim semantics:

```java
@Test
void appendPersistsTenantScopedEnvelopeWithCorrelation() { /* assert every envelope field */ }

@Test
void claimBatchOnlyClaimsDuePendingRowsForCurrentTenant() { /* tenant B is invisible */ }

@Test
void markPublishedIsExpectedStateTransitionOnly() { /* PROCESSING -> PUBLISHED; stale transition returns false */ }

@Test
void markFailedIncrementsAttemptAndSchedulesRetry() { /* status FAILED, attempt_count + 1, available_at set */ }
```

- [ ] **Step 2: Run and verify failure**

```bash
cd apps/sanad-platform
./mvnw -Dtest=JdbcCrmEventOutboxPostgresTest test
```

Expected: FAIL because the outbox port/adapter do not exist.

- [ ] **Step 3: Define the outbox interface and event envelope**

Use this stable contract:

```java
public interface CrmEventOutboxPort {
    void append(CrmEventEnvelope event);
    List<CrmEventEnvelope> claimDue(UUID tenantId, Instant now, int limit);
    boolean markPublished(UUID tenantId, UUID eventId, Instant publishedAt);
    boolean markFailed(UUID tenantId, UUID eventId, Instant nextAttemptAt, String error);

    record CrmEventEnvelope(
            UUID id,
            UUID tenantId,
            String eventType,
            int schemaVersion,
            String aggregateType,
            UUID aggregateId,
            String correlationId,
            String causationId,
            JsonNode payload,
            Instant availableAt,
            Instant createdAt) { /* validate required values; schemaVersion >= 1 */ }
}
```

Cap `claimDue` to `1..100`. `last_error` persistence must truncate/reject beyond the database limit deterministically rather than allowing an uncontrolled SQL error.

- [ ] **Step 4: Implement JDBC adapter**

`append` writes `PENDING`, attempt `0`. Claiming must be safe for multiple workers using PostgreSQL row locking:

```sql
SELECT id
FROM crm_event_outbox
WHERE tenant_id = :tenantId
  AND status IN ('PENDING','FAILED')
  AND available_at <= :now
ORDER BY available_at ASC, created_at ASC, id ASC
FOR UPDATE SKIP LOCKED
LIMIT :limit
```

Within the same transaction, update selected rows to `PROCESSING`, set `claimed_at=:now`, then return their envelopes. Do not implement network publication in this adapter.

`markPublished` only updates rows currently `PROCESSING`. `markFailed` only updates `PROCESSING`, increments `attempt_count`, clears `claimed_at`, stores bounded error text, and sets the caller-supplied `nextAttemptAt`.

- [ ] **Step 5: Run outbox test**

```bash
cd apps/sanad-platform
./mvnw -Dtest=JdbcCrmEventOutboxPostgresTest test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add apps/sanad-platform/src/main/java/com/sanad/platform/crm/integration/domain/CrmEventOutboxPort.java \
        apps/sanad-platform/src/main/java/com/sanad/platform/crm/integration/infrastructure/JdbcCrmEventOutboxAdapter.java \
        apps/sanad-platform/src/test/java/com/sanad/platform/crm/integration/JdbcCrmEventOutboxPostgresTest.java
git commit -m "feat(crm): add durable CRM event outbox"
```

---

### Task 6: Add recipient eligibility without granting permissions

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/collaboration/domain/RecipientEligibilityPort.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/collaboration/infrastructure/PlatformRecipientEligibilityAdapter.java`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/crm/collaboration/infrastructure/PlatformRecipientEligibilityAdapterTest.java`

**Interfaces:**
- Consumes existing `OwnershipUserValidationPort.isActiveUser(tenantId, userId)`.
- Consumes existing `CapabilityEvaluationService.evaluate(tenantId, userId, capabilityCode, organizationId)`.
- Produces an eligibility decision only; it never creates grants/roles/capabilities.

- [ ] **Step 1: Write failing eligibility tests**

```java
@Test
void sameTenantActiveUserWithCapabilityIsEligible() { /* active=true, access.allowed=true -> eligible */ }

@Test
void inactiveOrCrossTenantUserIsIneligibleWithoutCapabilityEvaluation() { /* active=false -> deny */ }

@Test
void activeUserWithoutCapabilityIsIneligible() { /* access.allowed=false -> deny */ }
```

Also verify the adapter never invokes any role/grant mutation service.

- [ ] **Step 2: Run and verify failure**

```bash
cd apps/sanad-platform
./mvnw -Dtest=PlatformRecipientEligibilityAdapterTest test
```

Expected: FAIL because the port/adapter do not exist.

- [ ] **Step 3: Define the port**

```java
public interface RecipientEligibilityPort {
    EligibilityDecision evaluate(UUID tenantId, UUID userId, UUID organizationId, String requiredCapability);

    record EligibilityDecision(boolean eligible, String reason) {}
}
```

- [ ] **Step 4: Implement platform adapter**

Algorithm:

```java
if (!users.isActiveUser(tenantId, userId)) {
    return new EligibilityDecision(false, "USER_NOT_ACTIVE_IN_TENANT");
}
AccessDecisionResponse access = capabilities.evaluate(
        tenantId, userId, requiredCapability, organizationId);
return new EligibilityDecision(access.isAllowed(),
        access.isAllowed() ? "ELIGIBLE" : access.getReason());
```

Use the actual accessor names on `AccessDecisionResponse` from the repository at implementation time; do not add a duplicate authorization model. A null/blank `requiredCapability` is an implementation error and must be rejected.

- [ ] **Step 5: Run test**

```bash
cd apps/sanad-platform
./mvnw -Dtest=PlatformRecipientEligibilityAdapterTest test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add apps/sanad-platform/src/main/java/com/sanad/platform/crm/collaboration/domain/RecipientEligibilityPort.java \
        apps/sanad-platform/src/main/java/com/sanad/platform/crm/collaboration/infrastructure/PlatformRecipientEligibilityAdapter.java \
        apps/sanad-platform/src/test/java/com/sanad/platform/crm/collaboration/infrastructure/PlatformRecipientEligibilityAdapterTest.java
git commit -m "feat(crm): add collaboration recipient eligibility"
```

---

### Task 7: Build the reusable collaboration membership service

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/collaboration/application/CollaborationMembershipService.java`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/crm/collaboration/application/CollaborationMembershipServiceTest.java`

**Interfaces:**
- Consumes `EntityParticipantRepository` and `RecipientEligibilityPort`.
- Produces `addParticipant`, `removeParticipant`, `listParticipants` operations for domain-specific command orchestrators.
- Does not change primary owner, domain status, audit, timeline, or outbox itself; orchestration of those side effects remains in the Contact/Task/Case/Note command use case transaction so business event names remain domain-specific.

- [ ] **Step 1: Write failing service tests**

Required cases:

```java
@Test
void addParticipantRejectsIneligibleRecipientBeforeInsert() { /* repository insert never called */ }

@Test
void addParticipantIsIdempotentForAlreadyActiveSameRole() { /* returns existing relation; no duplicate insert */ }

@Test
void collaboratorAndWatcherCanCoexistForSameUserWhenExplicitlyRequested() { /* distinct roles */ }

@Test
void removeParticipantUsesExpectedVersionAndRejectsStaleState() { /* false update -> conflict */ }
```

- [ ] **Step 2: Run and verify failure**

```bash
cd apps/sanad-platform
./mvnw -Dtest=CollaborationMembershipServiceTest test
```

Expected: FAIL because the service does not exist.

- [ ] **Step 3: Implement the application service**

Use explicit command objects that are internal application contracts:

```java
public EntityParticipant addParticipant(AddParticipantCommand command, EligibilityPolicy policy)
public EntityParticipant removeParticipant(RemoveParticipantCommand command)
public List<EntityParticipant> listParticipants(UUID tenantId, CollaborationEntityType entityType, UUID entityId)

public record AddParticipantCommand(
        UUID tenantId,
        CollaborationEntityType entityType,
        UUID entityId,
        UUID userId,
        ParticipantRole role,
        UUID actorId,
        Instant occurredAt) {}

public record RemoveParticipantCommand(
        UUID tenantId,
        UUID participantId,
        long expectedVersion,
        UUID actorId,
        Instant occurredAt) {}

public record EligibilityPolicy(UUID organizationId, String requiredCapability) {}
```

`addParticipant` evaluates eligibility first, returns the existing active same-role record if present, otherwise inserts a new active record. It must not silently switch roles: adding `WATCHER` when the user is an active `COLLABORATOR` produces a second explicit role relation, because role semantics are distinct.

`removeParticipant` fails closed on stale version. Define a focused `CollaborationConflictException` in the application/domain package rather than leaking JDBC update counts to callers.

- [ ] **Step 4: Run service tests**

```bash
cd apps/sanad-platform
./mvnw -Dtest=CollaborationMembershipServiceTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/sanad-platform/src/main/java/com/sanad/platform/crm/collaboration/application/CollaborationMembershipService.java \
        apps/sanad-platform/src/test/java/com/sanad/platform/crm/collaboration/application/CollaborationMembershipServiceTest.java
git commit -m "feat(crm): add collaboration membership service"
```

---

### Task 8: Wire the collaboration foundation without exposing a generic mutation API

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/collaboration/application/CollaborationModuleConfiguration.java`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/crm/collaboration/application/CollaborationModuleConfigurationTest.java`

**Interfaces:**
- Produces a Spring bean for `CollaborationMembershipService`.
- Infrastructure adapters remain discoverable as Spring components or explicit beans following repository conventions.
- No controller is produced.

- [ ] **Step 1: Write failing wiring test**

Use a focused Spring context test or `ApplicationContextRunner` (whichever dependency already exists in the module) to assert exactly one `CollaborationMembershipService`, `EntityParticipantRepository`, `RecipientEligibilityPort`, and `CrmEventOutboxPort` bean can be resolved in the normal application context.

- [ ] **Step 2: Run and verify failure**

```bash
cd apps/sanad-platform
./mvnw -Dtest=CollaborationModuleConfigurationTest test
```

Expected: FAIL because the configuration bean is absent.

- [ ] **Step 3: Add module configuration**

```java
@Configuration
public class CollaborationModuleConfiguration {
    @Bean
    CollaborationMembershipService collaborationMembershipService(
            EntityParticipantRepository participants,
            RecipientEligibilityPort eligibility) {
        return new CollaborationMembershipService(participants, eligibility);
    }
}
```

Do not add `@RestController` or a generic endpoint such as `/api/v2/crm/collaboration/{entityType}/{entityId}/participants`. Domain plans will call the service behind action-specific authorization and commands.

- [ ] **Step 4: Run wiring test**

```bash
cd apps/sanad-platform
./mvnw -Dtest=CollaborationModuleConfigurationTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/sanad-platform/src/main/java/com/sanad/platform/crm/collaboration/application/CollaborationModuleConfiguration.java \
        apps/sanad-platform/src/test/java/com/sanad/platform/crm/collaboration/application/CollaborationModuleConfigurationTest.java
git commit -m "feat(crm): wire collaboration foundation"
```

---

### Task 9: Prove atomic composition of membership + timeline + audit + outbox

**Files:**
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/crm/collaboration/CollaborationFoundationAtomicityPostgresTest.java`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/crm/collaboration/CollaborationFoundationRlsPostgresTest.java`

**Interfaces:**
- Proves later domain commands can compose participant changes, `AuditPort`, structured `TimelineEventPort`, and `CrmEventOutboxPort` inside one transaction.
- Does not create a production generic orchestrator.

- [ ] **Step 1: Write atomicity integration test**

Inside one `TransactionTemplate`, perform:

```java
EntityParticipant participant = membership.addParticipant(command, policy);
timeline.record(structuredEvent(participant));
audit.record(tenantId, actorId, "ADD_COLLABORATOR", "TASK", taskId, change, now);
outbox.append(eventEnvelope(participant));
```

Assert all four persistence effects are present after commit.

Then run the same composition with a test `CrmEventOutboxPort` that throws after participant/timeline/audit calls; assert the transaction rolls back participant and timeline rows. Audit uses the existing platform writer; assert the chosen test setup is transactionally participating. If the centralized audit writer is intentionally transaction-independent, document that fact in the test name/assertions and require the participant+timeline+outbox trio to roll back together while audit records a failed/attempted operation according to existing audit semantics. Do not silently assume one model.

- [ ] **Step 2: Write RLS isolation test**

Create tenant A and B rows. Within a transaction set `app.tenant_id` to A and prove direct SQL cannot read/update B participant/outbox/timeline rows. Clear the tenant GUC and prove forced-RLS tables expose no tenant rows.

- [ ] **Step 3: Run tests and observe any failure**

```bash
cd apps/sanad-platform
./mvnw -Dtest=CollaborationFoundationAtomicityPostgresTest,CollaborationFoundationRlsPostgresTest test
```

Expected: PASS after Tasks 1–8. Any failure is a foundation defect; fix the responsible implementation rather than weakening the test.

- [ ] **Step 4: Commit tests/fixes**

```bash
git add apps/sanad-platform/src/test/java/com/sanad/platform/crm/collaboration/CollaborationFoundationAtomicityPostgresTest.java \
        apps/sanad-platform/src/test/java/com/sanad/platform/crm/collaboration/CollaborationFoundationRlsPostgresTest.java \
        apps/sanad-platform/src/main/java/com/sanad/platform/crm/collaboration \
        apps/sanad-platform/src/main/java/com/sanad/platform/crm/integration
git commit -m "test(crm): prove collaboration foundation atomicity and isolation"
```

---

### Task 10: Run regression and produce the foundation acceptance evidence

**Files:**
- Create: `docs/crm/collaboration/CRM-COLLABORATION-FOUNDATION-ACCEPTANCE.md`

**Interfaces:**
- Produces the evidence gate required before Contacts/Tasks/Cases/Notes plans consume the foundation.

- [ ] **Step 1: Run focused foundation suite**

```bash
cd apps/sanad-platform
./mvnw -Dtest='CrmCollaborationSchemaPostgresTest,EntityParticipantTest,JdbcEntityParticipantRepositoryPostgresTest,PlatformRecipientEligibilityAdapterTest,CollaborationMembershipServiceTest,CollaborationModuleConfigurationTest,JdbcStructuredTimelineEventPostgresTest,JdbcCrmEventOutboxPostgresTest,CollaborationFoundationAtomicityPostgresTest,CollaborationFoundationRlsPostgresTest' test
```

Expected: `FAILURES=0`, `ERRORS=0`.

- [ ] **Step 2: Run affected existing CRM regression tests**

```bash
cd apps/sanad-platform
./mvnw -Dtest='OwnershipCommandUseCasesPostgresTest,TransferUseCasesPostgresTest,CrmOwnershipRbacPostgresTest,CrmWorkflowIntegrationPostgresTest' test
```

Expected: `FAILURES=0`, `ERRORS=0`. This specifically proves the structured timeline extension did not break existing ownership/transfer behavior.

- [ ] **Step 3: Run the full backend Maven test suite on PostgreSQL Direct**

```bash
cd apps/sanad-platform
./mvnw test
```

Expected: project acceptance thresholds remain satisfied; for release gating, `FAILURES=0` and `ERRORS=0`. Any environment-skipped tests must be reconciled against the repository's current PostgreSQL Direct policy rather than accepted blindly.

- [ ] **Step 4: Record concrete evidence**

Create `docs/crm/collaboration/CRM-COLLABORATION-FOUNDATION-ACCEPTANCE.md` containing:

```markdown
# CRM Collaboration Foundation Acceptance

- Baseline SHA: <actual execution baseline SHA>
- Implementation SHA: <actual final implementation SHA>
- PostgreSQL Direct: PASS
- New migrations: <actual migration filenames>
- Foundation focused suite: PASS — failures 0, errors 0
- Ownership/transfer regression: PASS — failures 0, errors 0
- Full backend suite: PASS — failures 0, errors 0
- RLS tenant-isolation proof: PASS
- Duplicate active participant constraint: PASS
- Structured timeline legacy compatibility: PASS
- Outbox crash/concurrency persistence primitives: PASS
- Docker/Testcontainers introduced: NO
- Domain lifecycle changes in this plan: NONE
- Generic collaboration mutation API exposed: NO
```

Replace the angle-bracket fields with actual values from execution; the acceptance file must never be committed with placeholders.

- [ ] **Step 5: Commit acceptance evidence**

```bash
git add docs/crm/collaboration/CRM-COLLABORATION-FOUNDATION-ACCEPTANCE.md
git commit -m "docs(crm): certify collaboration event foundation"
```

---

## Dependency and Handoff Matrix

After this plan passes, later plans may rely on these contracts:

- **Contacts plan:** `CollaborationMembershipService`, structured `TimelineEventPort`, `CrmEventOutboxPort`; owner remains `crm_contacts.owner_user_id`/existing ownership projection.
- **Tasks plan:** same collaboration/event primitives plus domain-specific task review/status commands; no participant-based owner substitution.
- **Cases plan:** same collaboration/event primitives; Case remains a domain-specific ownership/lifecycle/SLA concern even though existing `AssignmentRecordType` does not currently include `CASE`.
- **Notes plan:** uses recipient/mention tables defined in that plan; may reuse eligibility and outbox, but Notes do not become participants.
- **Notification Platform plan:** consumes `crm_event_outbox` envelopes and creates durable in-app notifications plus optional delivery records/channels.
- **Unified UX plan:** receives domain-specific eligible-user/query APIs from later plans; it must not call collaboration persistence directly.

## Out of Scope / Guardrails

This plan is complete when the reusable foundation is safe and testable. Do **not** expand it during execution to add Contact→Lead, task statuses, Case SLA, Note sharing, notification workers, Email, WhatsApp, web components, Android screens, or broad refactoring of the existing ownership module. Those are independent implementation plans governed by the same master spec.

## Self-Review Checklist for the Executor

Before declaring the plan complete, verify:

- Every new table and query is tenant-scoped.
- `crm_entity_participants.role` cannot contain `OWNER` or `REVIEWER`.
- No second owner field/source was added.
- Existing `TimelineEventPort` lambda callers compile unchanged.
- Structured timeline rows persist correlation/schema metadata.
- Outbox persistence is durable, transactional, claim-safe, and does not perform network I/O.
- Recipient eligibility evaluates existing RBAC and never grants permissions.
- Existing centralized audit is reused.
- Existing `crm_idempotency_records` is not duplicated.
- No generic collaboration mutation controller exists.
- PostgreSQL Direct tests and current ownership regressions pass.
- No Docker/Testcontainers dependency or path was added.
