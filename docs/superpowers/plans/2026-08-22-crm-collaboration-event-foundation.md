# CRM Collaboration & Event Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the tenant-safe collaboration membership and durable event primitives that Contacts, Tasks, Cases, Notes, and Notifications will reuse, without creating a second ownership system or changing any domain lifecycle in this plan.

**Architecture:** Keep the current `crm/ownership` system as the ownership/assignment path, keep PostgreSQL as the state source of truth, and add a focused `crm/collaboration` module only for secondary participation (`COLLABORATOR`, `WATCHER`). Extend the existing timeline port compatibly so old callers continue to compile, and add a durable CRM event outbox persistence port that later domain commands and the Notification Platform can compose inside their own transactions.

**Tech Stack:** Java 21, Spring Boot 3, Spring JDBC (`NamedParameterJdbcTemplate`), PostgreSQL, Flyway, Jackson, JUnit 5, AssertJ, Spring transactions, existing `CapabilityEvaluationService`, `AuditPort`, `CorrelationContextPort`, and `TimelineEventPort`.

**Spec:** `docs/superpowers/specs/2026-08-22-crm-collaboration-notifications-design.md`

## Global Constraints

- Planning baseline: `ffb856fa9b7ffb2a7294d8a5094937150f74841b`. Rebase before implementation. If `V20260822_1` or `V20260822_2` is occupied after rebase, use the next two free ordered `V20260822_N` versions and update this plan's commands during execution.
- PostgreSQL remains the source of truth. Do not introduce Event Sourcing.
- PostgreSQL Direct is the governing database test path. Do not add Docker or Testcontainers.
- Do not create a second owner source. Primary owner/assignee stays on the domain record and/or existing ownership projection.
- `crm_entity_participants` stores only `COLLABORATOR` and `WATCHER`; never `OWNER` or `REVIEWER`.
- Participation never creates or grants RBAC permissions.
- Reuse `AuditPort`/`JdbcAuditAdapter`; do not create a CRM-local audit ledger.
- Reuse `crm_idempotency_records` in later command plans; do not add another request-idempotency table here.
- Preserve the existing abstract `TimelineEventPort.record(UUID, String, UUID, String, String, String, UUID, UUID, Instant)` signature so current lambda test doubles remain valid.
- Structured timeline/event writes carry correlation ID and schema version.
- Migrations are forward-only, additive-first, tenant-aware, and non-destructive.
- New tenant-owned persistence has explicit tenant predicates plus PostgreSQL RLS using `app.tenant_id`.
- Do not expose a generic cross-entity collaboration mutation REST endpoint in this foundation.
- Every task follows TDD and ends in an independently reviewable commit.

---

## Repository facts that control implementation

- `crm_contacts` already has `owner_user_id`.
- `OwnershipCommandUseCases` already handles assignment/reassignment and projects owner changes into CRM records.
- Existing `AssignmentRecordType` covers `ACCOUNT`, `CONTACT`, `LEAD`, `OPPORTUNITY`, `ACTIVITY`, and `TASK`, but not `CASE`; this plan therefore does not force Case ownership into the current assignment enum.
- `crm_timeline_events` already exists with legacy columns `subject_type`, `subject_id`, `event_type`, `summary`, `source_type`, `source_id`, `occurred_at`, and `created_by`.
- `JdbcAuditAdapter` already pulls correlation from `CorrelationContextPort`.
- `CapabilityEvaluationService.evaluate(tenantId, userId, capabilityCode, organizationId)` returns `AccessDecisionResponse` with `allowed()` and `reason()`.
- No durable general CRM outbox was found at the planning baseline; CRM intelligence currently has in-process Spring events, which this plan does not remove.

---

## File map

**Create**

- `apps/sanad-platform/src/main/resources/db/migration/V20260822_1__crm_collaboration_event_foundation.sql`
- `apps/sanad-platform/src/main/resources/db/migration/V20260822_2__crm_collaboration_event_rls.sql`
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/collaboration/domain/CollaborationEntityType.java`
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/collaboration/domain/ParticipantRole.java`
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/collaboration/domain/EntityParticipant.java`
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/collaboration/domain/EntityParticipantRepository.java`
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/collaboration/domain/RecipientEligibilityPort.java`
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/collaboration/application/CollaborationMembershipService.java`
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/collaboration/application/CollaborationConflictException.java`
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/collaboration/application/CollaborationModuleConfiguration.java`
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/collaboration/infrastructure/JdbcEntityParticipantRepository.java`
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/collaboration/infrastructure/PlatformRecipientEligibilityAdapter.java`
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/integration/domain/CrmEventOutboxPort.java`
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/integration/infrastructure/JdbcCrmEventOutboxAdapter.java`
- `apps/sanad-platform/src/test/java/com/sanad/platform/crm/collaboration/CrmCollaborationSchemaPostgresTest.java`
- `apps/sanad-platform/src/test/java/com/sanad/platform/crm/collaboration/domain/EntityParticipantTest.java`
- `apps/sanad-platform/src/test/java/com/sanad/platform/crm/collaboration/infrastructure/JdbcEntityParticipantRepositoryPostgresTest.java`
- `apps/sanad-platform/src/test/java/com/sanad/platform/crm/collaboration/infrastructure/PlatformRecipientEligibilityAdapterTest.java`
- `apps/sanad-platform/src/test/java/com/sanad/platform/crm/collaboration/application/CollaborationMembershipServiceTest.java`
- `apps/sanad-platform/src/test/java/com/sanad/platform/crm/collaboration/application/CollaborationModuleConfigurationTest.java`
- `apps/sanad-platform/src/test/java/com/sanad/platform/crm/collaboration/CollaborationFoundationAtomicityPostgresTest.java`
- `apps/sanad-platform/src/test/java/com/sanad/platform/crm/collaboration/CollaborationFoundationRlsPostgresTest.java`
- `apps/sanad-platform/src/test/java/com/sanad/platform/crm/integration/JdbcStructuredTimelineEventPostgresTest.java`
- `apps/sanad-platform/src/test/java/com/sanad/platform/crm/integration/JdbcCrmEventOutboxPostgresTest.java`
- `docs/crm/collaboration/CRM-COLLABORATION-FOUNDATION-ACCEPTANCE.md`

**Modify**

- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/integration/domain/TimelineEventPort.java`
- `apps/sanad-platform/src/main/java/com/sanad/platform/crm/integration/infrastructure/JdbcTimelineEventAdapter.java`

**Do not modify in this plan**

- Contact/Task/Case lifecycle code.
- `OwnershipCommandUseCases` transfer semantics.
- Notes sharing tables/API.
- Notification Center, Email, WhatsApp, SLA.
- Web or Android UI.
- Domain RBAC seed matrix for `CRM.CONTACT.SHARE`, `CRM.TASK.SHARE`, `CRM.CASE.SHARE`; each domain plan owns its action capabilities.

---

### Task 1: Add and verify the collaboration/event schema

**Files:**
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/crm/collaboration/CrmCollaborationSchemaPostgresTest.java`
- Create: `apps/sanad-platform/src/main/resources/db/migration/V20260822_1__crm_collaboration_event_foundation.sql`
- Create: `apps/sanad-platform/src/main/resources/db/migration/V20260822_2__crm_collaboration_event_rls.sql`

**Interfaces:**
- Produces `crm_entity_participants`.
- Produces `crm_event_outbox`.
- Adds structured fields to existing `crm_timeline_events` without removing legacy fields.

- [ ] **Step 1: Write the failing schema test**

Use the same PostgreSQL Direct setup pattern as `OwnershipCommandUseCasesPostgresTest`: `Crm009TestEnvironment.requirePostgreSqlDirectOrSkip`, Flyway locations `classpath:db/migration` and `classpath:db/vendor/postgresql`, and `NamedParameterJdbcTemplate`.

The test class must contain these concrete assertions:

```java
@Test
void foundationSchemaHasRequiredColumnsAndRoleConstraint() {
    assertThat(columnExists("crm_entity_participants", "role")).isTrue();
    assertThat(columnExists("crm_event_outbox", "correlation_id")).isTrue();
    assertThat(columnExists("crm_timeline_events", "summary_key")).isTrue();
    assertThat(columnExists("crm_timeline_events", "metadata_json")).isTrue();
    assertThat(columnExists("crm_timeline_events", "correlation_id")).isTrue();
    assertThat(columnExists("crm_timeline_events", "causation_id")).isTrue();
    assertThat(columnExists("crm_timeline_events", "schema_version")).isTrue();

    String definition = checkConstraintDefinition("ck_crm_entity_participants_role");
    assertThat(definition).contains("COLLABORATOR").contains("WATCHER");
    assertThat(definition).doesNotContain("OWNER").doesNotContain("REVIEWER");

    assertThat(columnExists("crm_timeline_events", "summary")).isTrue();
    assertThat(columnExists("crm_timeline_events", "source_type")).isTrue();
    assertThat(columnExists("crm_timeline_events", "source_id")).isTrue();
}

@Test
void participantAndOutboxTablesUseForcedRls() {
    assertThat(forceRls("crm_entity_participants")).isTrue();
    assertThat(forceRls("crm_event_outbox")).isTrue();
}
```

Implement `columnExists` from `information_schema.columns`, `checkConstraintDefinition` from `pg_constraint` + `pg_get_constraintdef`, and `forceRls` from `pg_class.relforcerowsecurity`.

- [ ] **Step 2: Run the test and confirm the expected failure**

```bash
cd apps/sanad-platform
./mvnw -Dtest=CrmCollaborationSchemaPostgresTest test
```

Expected: test failure because the new tables/columns do not exist.

- [ ] **Step 3: Create the additive schema migration**

Use exactly this table/column contract:

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
    CONSTRAINT ck_crm_event_outbox_attempts CHECK (attempt_count >= 0)
);

CREATE INDEX idx_crm_event_outbox_due
    ON crm_event_outbox (available_at ASC, created_at ASC, id ASC)
    WHERE status IN ('PENDING','FAILED');

CREATE INDEX idx_crm_event_outbox_correlation
    ON crm_event_outbox (tenant_id, correlation_id, created_at DESC);
```

Do not make the new structured timeline fields mandatory: old rows and old writers remain valid.

- [ ] **Step 4: Create the RLS migration**

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

If the existing timeline path fails because a production caller does not establish the platform tenant GUC, fix that caller's transaction context rather than removing or bypassing the policy.

- [ ] **Step 5: Run migration/timeline regression tests**

```bash
cd apps/sanad-platform
./mvnw -Dtest=CrmCollaborationSchemaPostgresTest,CrmWorkflowIntegrationPostgresTest test
```

Expected: both test classes pass.

- [ ] **Step 6: Commit**

```bash
git add apps/sanad-platform/src/main/resources/db/migration/V20260822_1__crm_collaboration_event_foundation.sql \
        apps/sanad-platform/src/main/resources/db/migration/V20260822_2__crm_collaboration_event_rls.sql \
        apps/sanad-platform/src/test/java/com/sanad/platform/crm/collaboration/CrmCollaborationSchemaPostgresTest.java
git commit -m "feat(crm): add collaboration event foundation schema"
```

---

### Task 2: Define participant domain invariants and repository port

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/collaboration/domain/CollaborationEntityType.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/collaboration/domain/ParticipantRole.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/collaboration/domain/EntityParticipant.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/collaboration/domain/EntityParticipantRepository.java`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/crm/collaboration/domain/EntityParticipantTest.java`

**Interfaces:**
- Produces `CollaborationEntityType { CONTACT, TASK, CASE }`.
- Produces `ParticipantRole { COLLABORATOR, WATCHER }`.
- Produces the repository contract used by Tasks 3 and 7.

- [ ] **Step 1: Write failing domain tests**

```java
@Test
void activeParticipantCanBeRemovedExactlyOnce() {
    UUID tenant = UUID.randomUUID();
    UUID entity = UUID.randomUUID();
    UUID user = UUID.randomUUID();
    UUID actor = UUID.randomUUID();
    Instant addedAt = Instant.parse("2026-08-21T20:00:00Z");
    Instant removedAt = Instant.parse("2026-08-21T21:00:00Z");

    EntityParticipant active = EntityParticipant.active(
            UUID.randomUUID(), tenant, CollaborationEntityType.TASK, entity,
            user, ParticipantRole.COLLABORATOR, actor, addedAt);
    EntityParticipant removed = active.remove(actor, removedAt);

    assertThat(active.isActive()).isTrue();
    assertThat(removed.isActive()).isFalse();
    assertThat(removed.removedByUserId()).isEqualTo(actor);
    assertThat(removed.removedAt()).isEqualTo(removedAt);
    assertThat(removed.version()).isEqualTo(1L);
    assertThatThrownBy(() -> removed.remove(actor, removedAt.plusSeconds(1)))
            .isInstanceOf(IllegalStateException.class);
}

@Test
void participantRequiresCompleteIdentity() {
    assertThatThrownBy(() -> EntityParticipant.active(
            UUID.randomUUID(), null, CollaborationEntityType.CONTACT,
            UUID.randomUUID(), UUID.randomUUID(), ParticipantRole.WATCHER,
            UUID.randomUUID(), Instant.now()))
            .isInstanceOf(NullPointerException.class);
}
```

- [ ] **Step 2: Run and confirm failure**

```bash
cd apps/sanad-platform
./mvnw -Dtest=EntityParticipantTest test
```

Expected: compilation/test failure because the types do not exist.

- [ ] **Step 3: Implement the domain types**

```java
public enum CollaborationEntityType {
    CONTACT, TASK, CASE
}
```

```java
public enum ParticipantRole {
    COLLABORATOR, WATCHER
}
```

Implement `EntityParticipant` with this exact public surface:

```java
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

    public EntityParticipant {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(addedByUserId, "addedByUserId");
        Objects.requireNonNull(addedAt, "addedAt");
        if ((removedByUserId == null) != (removedAt == null)) {
            throw new IllegalArgumentException("removal actor and timestamp must be set together");
        }
        if (version < 0) throw new IllegalArgumentException("version must be non-negative");
    }

    public static EntityParticipant active(UUID id, UUID tenantId,
            CollaborationEntityType entityType, UUID entityId, UUID userId,
            ParticipantRole role, UUID actorId, Instant addedAt) {
        return new EntityParticipant(id, tenantId, entityType, entityId, userId,
                role, actorId, addedAt, null, null, 0L);
    }

    public boolean isActive() {
        return removedAt == null;
    }

    public EntityParticipant remove(UUID actorId, Instant at) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(at, "at");
        if (!isActive()) throw new IllegalStateException("participant already removed");
        return new EntityParticipant(id, tenantId, entityType, entityId, userId,
                role, addedByUserId, addedAt, actorId, at, version + 1);
    }
}
```

Repository port:

```java
public interface EntityParticipantRepository {
    EntityParticipant insert(EntityParticipant participant);
    Optional<EntityParticipant> findActive(UUID tenantId, CollaborationEntityType entityType,
            UUID entityId, UUID userId, ParticipantRole role);
    Optional<EntityParticipant> findById(UUID tenantId, UUID participantId);
    List<EntityParticipant> listActive(UUID tenantId, CollaborationEntityType entityType, UUID entityId);
    boolean markRemoved(UUID tenantId, UUID participantId, long expectedVersion,
            UUID removedByUserId, Instant removedAt);
}
```

- [ ] **Step 4: Run the domain test**

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

### Task 3: Implement tenant-scoped JDBC participant persistence

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/collaboration/infrastructure/JdbcEntityParticipantRepository.java`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/crm/collaboration/infrastructure/JdbcEntityParticipantRepositoryPostgresTest.java`

**Interfaces:**
- Consumes Task 2 repository/domain contract.
- Produces `JdbcEntityParticipantRepository`.

- [ ] **Step 1: Write failing PostgreSQL repository tests**

Use four test methods with these exact outcomes:

```java
@Test
void insertFindAndListReturnOnlyCurrentTenant() {
    EntityParticipant saved = repository.insert(participantFor(tenantA, ParticipantRole.COLLABORATOR));
    assertThat(repository.findById(tenantA, saved.id())).contains(saved);
    assertThat(repository.findById(tenantB, saved.id())).isEmpty();
    assertThat(repository.listActive(tenantA, saved.entityType(), saved.entityId())).containsExactly(saved);
}

@Test
void duplicateActiveRelationIsRejected() {
    EntityParticipant first = participantFor(tenantA, ParticipantRole.WATCHER);
    repository.insert(first);
    EntityParticipant duplicate = EntityParticipant.active(
            UUID.randomUUID(), first.tenantId(), first.entityType(), first.entityId(),
            first.userId(), first.role(), first.addedByUserId(), first.addedAt().plusSeconds(1));
    assertThatThrownBy(() -> repository.insert(duplicate))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
}

@Test
void removedRelationCanBeAddedAgainAsNewHistoryRow() {
    EntityParticipant first = repository.insert(participantFor(tenantA, ParticipantRole.WATCHER));
    assertThat(repository.markRemoved(tenantA, first.id(), 0L, actorId, Instant.now())).isTrue();
    EntityParticipant second = repository.insert(EntityParticipant.active(
            UUID.randomUUID(), first.tenantId(), first.entityType(), first.entityId(),
            first.userId(), first.role(), actorId, Instant.now()));
    assertThat(second.id()).isNotEqualTo(first.id());
    assertThat(repository.findActive(tenantA, first.entityType(), first.entityId(),
            first.userId(), first.role())).contains(second);
}

@Test
void staleVersionCannotRemoveRelation() {
    EntityParticipant first = repository.insert(participantFor(tenantA, ParticipantRole.COLLABORATOR));
    assertThat(repository.markRemoved(tenantA, first.id(), 7L, actorId, Instant.now())).isFalse();
    assertThat(repository.findById(tenantA, first.id())).get().extracting(EntityParticipant::isActive).isEqualTo(true);
}
```

Each test transaction must set the current tenant GUC before accessing a forced-RLS table using `SELECT set_config('app.tenant_id', :tenant, true)`.

- [ ] **Step 2: Run and confirm failure**

```bash
cd apps/sanad-platform
./mvnw -Dtest=JdbcEntityParticipantRepositoryPostgresTest test
```

Expected: compilation/test failure because the JDBC repository does not exist.

- [ ] **Step 3: Implement JDBC queries**

Every read/update contains `tenant_id = :tenantId` even though RLS also applies. `findActive` uses:

```sql
SELECT id, tenant_id, entity_type, entity_id, user_id, role,
       added_by_user_id, added_at, removed_by_user_id, removed_at, version
FROM crm_entity_participants
WHERE tenant_id = :tenantId
  AND entity_type = :entityType
  AND entity_id = :entityId
  AND user_id = :userId
  AND role = :role
  AND removed_at IS NULL
```

`listActive` orders by `added_at ASC, id ASC`.

`markRemoved` uses:

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

Map timestamps with the same JDBC conventions used by current CRM repositories.

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

### Task 4: Extend `TimelineEventPort` with structured events without breaking legacy callers

**Files:**
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/integration/domain/TimelineEventPort.java`
- Modify: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/integration/infrastructure/JdbcTimelineEventAdapter.java`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/crm/integration/JdbcStructuredTimelineEventPostgresTest.java`

**Interfaces:**
- Legacy abstract method remains unchanged.
- New `StructuredTimelineEvent` record and default overload are added.
- `JdbcTimelineEventAdapter` overrides the new overload to persist structured columns.

- [ ] **Step 1: Write failing compatibility/persistence test**

```java
@Test
void timelinePortRemainsLambdaCompatible() {
    TimelineEventPort port = (tenant, type, id, event, summary, source, sourceId, actor, at) -> { };
    assertThat(port).isNotNull();
}

@Test
void jdbcAdapterPersistsStructuredMetadataAndCorrelation() {
    ObjectNode metadata = mapper.createObjectNode();
    metadata.put("participantUserId", userId.toString());
    TimelineEventPort.StructuredTimelineEvent event = new TimelineEventPort.StructuredTimelineEvent(
            tenantId, "TASK", taskId, "TASK_COLLABORATOR_ADDED",
            "crm.task.collaborator_added", "CRM participant added",
            "COLLABORATION_PARTICIPANT", participantId, actorId,
            Instant.parse("2026-08-21T22:00:00Z"),
            "corr-123", "cause-456", 1, metadata);

    adapter.record(event);

    Map<String, Object> row = loadTimelineRow(tenantId, taskId);
    assertThat(row.get("summary_key")).isEqualTo("crm.task.collaborator_added");
    assertThat(row.get("correlation_id")).isEqualTo("corr-123");
    assertThat(row.get("causation_id")).isEqualTo("cause-456");
    assertThat(row.get("schema_version")).isEqualTo(1);
    assertThat(String.valueOf(row.get("metadata_json"))).contains(userId.toString());
}
```

- [ ] **Step 2: Run and confirm failure**

```bash
cd apps/sanad-platform
./mvnw -Dtest=JdbcStructuredTimelineEventPostgresTest test
```

Expected: compilation failure because the structured event contract does not exist.

- [ ] **Step 3: Add the compatible structured contract**

Keep the current abstract `record(...)` exactly as-is. Add imports for `JsonNode` and `Objects`, then add:

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
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(subjectType, "subjectType");
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(sourceType, "sourceType");
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(correlationId, "correlationId");
        if (schemaVersion < 1) throw new IllegalArgumentException("schemaVersion must be >= 1");
    }
}

default void record(StructuredTimelineEvent event) {
    record(event.tenantId(), event.subjectType(), event.subjectId(), event.eventType(),
            event.summary(), event.sourceType(), event.sourceId(), event.actorId(), event.occurredAt());
}
```

The default fallback deliberately preserves compatibility for lightweight test adapters. Production `JdbcTimelineEventAdapter` overrides it.

- [ ] **Step 4: Implement the structured JDBC insert**

Inject `ObjectMapper` into `JdbcTimelineEventAdapter` and keep its existing legacy method. Add the overload that inserts legacy plus new columns:

```sql
INSERT INTO crm_timeline_events
(id, tenant_id, subject_type, subject_id, event_type, summary, source_type, source_id,
 occurred_at, created_by, summary_key, metadata_json, correlation_id, causation_id, schema_version)
VALUES
(:id, :tenantId, :subjectType, :subjectId, :eventType, :summary, :sourceType, :sourceId,
 :occurredAt, :createdBy, :summaryKey, :metadataJson, :correlationId, :causationId, :schemaVersion)
```

Serialize a null metadata node as SQL null; serialize non-null metadata with `ObjectMapper.writeValueAsString` and wrap checked JSON exceptions as an `IllegalStateException` with a stable message.

- [ ] **Step 5: Run new plus legacy regression**

```bash
cd apps/sanad-platform
./mvnw -Dtest=JdbcStructuredTimelineEventPostgresTest,OwnershipCommandUseCasesPostgresTest,CrmWorkflowIntegrationPostgresTest test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add apps/sanad-platform/src/main/java/com/sanad/platform/crm/integration/domain/TimelineEventPort.java \
        apps/sanad-platform/src/main/java/com/sanad/platform/crm/integration/infrastructure/JdbcTimelineEventAdapter.java \
        apps/sanad-platform/src/test/java/com/sanad/platform/crm/integration/JdbcStructuredTimelineEventPostgresTest.java
git commit -m "feat(crm): add structured timeline events"
```

---

### Task 5: Add durable CRM event outbox persistence

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/integration/domain/CrmEventOutboxPort.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/integration/infrastructure/JdbcCrmEventOutboxAdapter.java`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/crm/integration/JdbcCrmEventOutboxPostgresTest.java`

**Interfaces:**
- Produces durable event envelope persistence.
- Does not create a scheduler, worker, Email call, WhatsApp call, or Notification record.

- [ ] **Step 1: Write failing outbox tests**

```java
@Test
void appendStoresEnvelopeExactlyOnce() {
    CrmEventOutboxPort.CrmEventEnvelope event = envelope(tenantA, "TASK_COLLABORATOR_ADDED");
    adapter.append(event);
    Map<String, Object> row = loadOutboxRow(tenantA, event.id());
    assertThat(row.get("event_type")).isEqualTo("TASK_COLLABORATOR_ADDED");
    assertThat(row.get("correlation_id")).isEqualTo(event.correlationId());
    assertThat(row.get("status")).isEqualTo("PENDING");
    assertThat(row.get("attempt_count")).isEqualTo(0);
}

@Test
void claimDueUsesTenantScopeAndChangesStateToProcessing() {
    CrmEventOutboxPort.CrmEventEnvelope a = envelope(tenantA, "TASK_COLLABORATOR_ADDED");
    CrmEventOutboxPort.CrmEventEnvelope b = envelope(tenantB, "CASE_WATCHER_ADDED");
    adapter.append(a);
    setTenant(tenantB);
    adapter.append(b);
    setTenant(tenantA);
    List<CrmEventOutboxPort.CrmEventEnvelope> claimed = adapter.claimDue(
            tenantA, Instant.parse("2026-08-22T00:00:00Z"), 10);
    assertThat(claimed).extracting(CrmEventOutboxPort.CrmEventEnvelope::id).containsExactly(a.id());
    assertThat(outboxStatus(tenantA, a.id())).isEqualTo("PROCESSING");
}

@Test
void publishAndFailureTransitionsRequireProcessingState() {
    CrmEventOutboxPort.CrmEventEnvelope event = envelope(tenantA, "TASK_COLLABORATOR_ADDED");
    adapter.append(event);
    assertThat(adapter.markPublished(tenantA, event.id(), Instant.now())).isFalse();
    adapter.claimDue(tenantA, Instant.now().plusSeconds(1), 1);
    assertThat(adapter.markFailed(tenantA, event.id(), Instant.now().plusSeconds(60), "provider unavailable")).isTrue();
    assertThat(outboxAttemptCount(tenantA, event.id())).isEqualTo(1);
}
```

- [ ] **Step 2: Run and confirm failure**

```bash
cd apps/sanad-platform
./mvnw -Dtest=JdbcCrmEventOutboxPostgresTest test
```

Expected: compilation failure because the outbox port/adapter do not exist.

- [ ] **Step 3: Define the outbox port**

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
            Instant createdAt) {
        public CrmEventEnvelope {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(eventType, "eventType");
            Objects.requireNonNull(aggregateType, "aggregateType");
            Objects.requireNonNull(aggregateId, "aggregateId");
            Objects.requireNonNull(correlationId, "correlationId");
            Objects.requireNonNull(payload, "payload");
            Objects.requireNonNull(availableAt, "availableAt");
            Objects.requireNonNull(createdAt, "createdAt");
            if (schemaVersion < 1) throw new IllegalArgumentException("schemaVersion must be >= 1");
        }
    }
}
```

- [ ] **Step 4: Implement the JDBC outbox adapter**

`append` writes status `PENDING` and attempt count `0`. `claimDue` rejects limits outside `1..100`, executes inside a transaction, selects with:

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

Update claimed rows to `PROCESSING`, set `claimed_at`, then map and return those rows in deterministic order.

`markPublished` must update only `PROCESSING` rows. `markFailed` must update only `PROCESSING` rows, increment `attempt_count`, clear `claimed_at`, set `available_at` to `nextAttemptAt`, and store a deterministic maximum 2000-character error string.

- [ ] **Step 5: Run tests**

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

### Task 6: Add recipient eligibility using existing identity and RBAC services

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/collaboration/domain/RecipientEligibilityPort.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/collaboration/infrastructure/PlatformRecipientEligibilityAdapter.java`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/crm/collaboration/infrastructure/PlatformRecipientEligibilityAdapterTest.java`

**Interfaces:**
- Consumes existing `OwnershipUserValidationPort`.
- Consumes existing `CapabilityEvaluationService`.
- Produces a read-only eligibility decision; no role/grant mutation.

- [ ] **Step 1: Write failing eligibility tests**

```java
@Test
void activeUserWithRequiredCapabilityIsEligible() {
    when(users.isActiveUser(tenantId, userId)).thenReturn(true);
    when(capabilities.evaluate(tenantId, userId, "CRM.TASK.READ", organizationId))
            .thenReturn(new AccessDecisionResponse(tenantId, userId, organizationId,
                    "CRM.TASK.READ", true, "ROLE_CAPABILITY_MATCH", roleId, "AGENT"));

    RecipientEligibilityPort.EligibilityDecision decision = adapter.evaluate(
            tenantId, userId, organizationId, "CRM.TASK.READ");

    assertThat(decision.eligible()).isTrue();
    assertThat(decision.reason()).isEqualTo("ELIGIBLE");
}

@Test
void inactiveUserIsRejectedBeforeRbacEvaluation() {
    when(users.isActiveUser(tenantId, userId)).thenReturn(false);
    RecipientEligibilityPort.EligibilityDecision decision = adapter.evaluate(
            tenantId, userId, organizationId, "CRM.TASK.READ");
    assertThat(decision).isEqualTo(new RecipientEligibilityPort.EligibilityDecision(
            false, "USER_NOT_ACTIVE_IN_TENANT"));
    verifyNoInteractions(capabilities);
}

@Test
void activeUserWithoutCapabilityIsRejected() {
    when(users.isActiveUser(tenantId, userId)).thenReturn(true);
    when(capabilities.evaluate(tenantId, userId, "CRM.TASK.READ", organizationId))
            .thenReturn(new AccessDecisionResponse(tenantId, userId, organizationId,
                    "CRM.TASK.READ", false, "NO_MATCHING_ACTIVE_ROLE", null, null));
    assertThat(adapter.evaluate(tenantId, userId, organizationId, "CRM.TASK.READ"))
            .isEqualTo(new RecipientEligibilityPort.EligibilityDecision(
                    false, "NO_MATCHING_ACTIVE_ROLE"));
}
```

- [ ] **Step 2: Run and confirm failure**

```bash
cd apps/sanad-platform
./mvnw -Dtest=PlatformRecipientEligibilityAdapterTest test
```

Expected: compilation failure because the port/adapter do not exist.

- [ ] **Step 3: Define and implement the port/adapter**

Port:

```java
public interface RecipientEligibilityPort {
    EligibilityDecision evaluate(UUID tenantId, UUID userId, UUID organizationId, String requiredCapability);
    record EligibilityDecision(boolean eligible, String reason) { }
}
```

Adapter core logic:

```java
if (requiredCapability == null || requiredCapability.isBlank()) {
    throw new IllegalArgumentException("requiredCapability is required");
}
if (!users.isActiveUser(tenantId, userId)) {
    return new EligibilityDecision(false, "USER_NOT_ACTIVE_IN_TENANT");
}
AccessDecisionResponse access = capabilities.evaluate(
        tenantId, userId, requiredCapability, organizationId);
return access.allowed()
        ? new EligibilityDecision(true, "ELIGIBLE")
        : new EligibilityDecision(false, access.reason());
```

Do not call `UserRoleGrantService`, `RoleService`, or any write method directly; `CapabilityEvaluationService` remains the authorization source.

- [ ] **Step 4: Run tests**

```bash
cd apps/sanad-platform
./mvnw -Dtest=PlatformRecipientEligibilityAdapterTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/sanad-platform/src/main/java/com/sanad/platform/crm/collaboration/domain/RecipientEligibilityPort.java \
        apps/sanad-platform/src/main/java/com/sanad/platform/crm/collaboration/infrastructure/PlatformRecipientEligibilityAdapter.java \
        apps/sanad-platform/src/test/java/com/sanad/platform/crm/collaboration/infrastructure/PlatformRecipientEligibilityAdapterTest.java
git commit -m "feat(crm): add collaboration recipient eligibility"
```

---

### Task 7: Implement reusable membership operations

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/collaboration/application/CollaborationConflictException.java`
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/collaboration/application/CollaborationMembershipService.java`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/crm/collaboration/application/CollaborationMembershipServiceTest.java`

**Interfaces:**
- Consumes `EntityParticipantRepository` and `RecipientEligibilityPort`.
- Produces membership add/remove/list operations used by later domain command orchestrators.
- Does not mutate ownership and does not emit domain-specific audit/timeline/outbox events itself.

- [ ] **Step 1: Write failing service tests**

```java
@Test
void addRejectsIneligibleRecipientWithoutInsert() {
    when(eligibility.evaluate(tenantId, userId, organizationId, "CRM.TASK.READ"))
            .thenReturn(new RecipientEligibilityPort.EligibilityDecision(false, "NO_MATCHING_ACTIVE_ROLE"));
    assertThatThrownBy(() -> service.addParticipant(addCommand(), policy()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("NO_MATCHING_ACTIVE_ROLE");
    verify(participants, never()).insert(any());
}

@Test
void addReturnsExistingActiveSameRoleWithoutDuplicateInsert() {
    EntityParticipant existing = activeParticipant();
    when(eligibility.evaluate(tenantId, userId, organizationId, "CRM.TASK.READ"))
            .thenReturn(new RecipientEligibilityPort.EligibilityDecision(true, "ELIGIBLE"));
    when(participants.findActive(tenantId, CollaborationEntityType.TASK, entityId,
            userId, ParticipantRole.COLLABORATOR)).thenReturn(Optional.of(existing));
    assertThat(service.addParticipant(addCommand(), policy())).isEqualTo(existing);
    verify(participants, never()).insert(any());
}

@Test
void removeFailsOnStaleExpectedVersion() {
    EntityParticipant existing = activeParticipant();
    when(participants.findById(tenantId, existing.id())).thenReturn(Optional.of(existing));
    when(participants.markRemoved(tenantId, existing.id(), existing.version(), actorId, occurredAt))
            .thenReturn(false);
    assertThatThrownBy(() -> service.removeParticipant(new CollaborationMembershipService.RemoveParticipantCommand(
            tenantId, existing.id(), existing.version(), actorId, occurredAt)))
            .isInstanceOf(CollaborationConflictException.class);
}
```

Add a fourth test proving the same user can have both a `COLLABORATOR` and a `WATCHER` record because they are distinct explicit roles.

- [ ] **Step 2: Run and confirm failure**

```bash
cd apps/sanad-platform
./mvnw -Dtest=CollaborationMembershipServiceTest test
```

Expected: compilation failure because the service does not exist.

- [ ] **Step 3: Implement service contract**

Public surface:

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
        Instant occurredAt) { }

public record RemoveParticipantCommand(
        UUID tenantId,
        UUID participantId,
        long expectedVersion,
        UUID actorId,
        Instant occurredAt) { }

public record EligibilityPolicy(UUID organizationId, String requiredCapability) { }
```

`addParticipant` validates command/policy, evaluates eligibility, returns the existing active same-role relation when one already exists, otherwise inserts a new `EntityParticipant.active(UUID.randomUUID(), ...)`.

`removeParticipant` loads by tenant + participant ID, rejects absent/inactive records, calls `markRemoved` with the supplied version, throws `CollaborationConflictException` when the update count indicates stale state, then returns `existing.remove(actorId, occurredAt)`.

- [ ] **Step 4: Run tests**

```bash
cd apps/sanad-platform
./mvnw -Dtest=CollaborationMembershipServiceTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/sanad-platform/src/main/java/com/sanad/platform/crm/collaboration/application \
        apps/sanad-platform/src/test/java/com/sanad/platform/crm/collaboration/application/CollaborationMembershipServiceTest.java
git commit -m "feat(crm): add collaboration membership service"
```

---

### Task 8: Wire the module and prove transactional composition/RLS

**Files:**
- Create: `apps/sanad-platform/src/main/java/com/sanad/platform/crm/collaboration/application/CollaborationModuleConfiguration.java`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/crm/collaboration/application/CollaborationModuleConfigurationTest.java`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/crm/collaboration/CollaborationFoundationAtomicityPostgresTest.java`
- Create: `apps/sanad-platform/src/test/java/com/sanad/platform/crm/collaboration/CollaborationFoundationRlsPostgresTest.java`

**Interfaces:**
- Wires `CollaborationMembershipService`.
- Proves later domain commands can atomically compose membership + structured timeline + durable outbox while continuing to use centralized audit.

- [ ] **Step 1: Write failing configuration test**

The Spring test resolves exactly one bean for each of these types: `CollaborationMembershipService`, `EntityParticipantRepository`, `RecipientEligibilityPort`, and `CrmEventOutboxPort`. It also asserts there is no controller class in package `com.sanad.platform.crm.collaboration` by checking the configured test context contains no bean name ending in `Controller` from that package.

- [ ] **Step 2: Run configuration test and confirm failure**

```bash
cd apps/sanad-platform
./mvnw -Dtest=CollaborationModuleConfigurationTest test
```

Expected: failure because `CollaborationMembershipService` is not wired.

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

Use `@Component` on the two JDBC/platform adapters or explicit bean methods consistently with current CRM integration patterns. Do not add a generic REST controller.

- [ ] **Step 4: Write atomicity test**

In one `TransactionTemplate` transaction, set the tenant GUC and then execute these concrete calls in order:

```java
EntityParticipant participant = membership.addParticipant(command, policy);
timeline.record(new TimelineEventPort.StructuredTimelineEvent(
        tenantId, "TASK", taskId, "TASK_COLLABORATOR_ADDED",
        "crm.task.collaborator_added", "CRM participant added",
        "COLLABORATION_PARTICIPANT", participant.id(), actorId, now,
        correlationId, requestId.toString(), 1,
        mapper.createObjectNode().put("participantUserId", participant.userId().toString())));
audit.record(tenantId, actorId, "ADD_COLLABORATOR", "TASK", taskId,
        new AuditPort.AuditChange(null, mapper.valueToTree(participant)), now);
outbox.append(eventEnvelopeFor(participant, correlationId, requestId.toString(), now));
```

After commit, assert participant row, timeline row, central audit fact, and outbox row are visible for the tenant.

Then execute the same transaction with a test `CrmEventOutboxPort` whose `append` method throws `IllegalStateException("OUTBOX_FAILPOINT")`. Assert the participant and timeline rows from that second transaction do not exist afterward. Verify centralized audit behavior against the actual `PlatformAuditWriter` transaction semantics: if its row participates in the transaction, assert rollback; if it intentionally records attempted/failed activity independently, assert that documented existing behavior and do not change it in this plan.

- [ ] **Step 5: Write RLS test**

Seed tenant A and B participant/outbox/timeline rows using a tenant-scoped transaction for each. In a tenant-A transaction, prove direct SQL for tenant-B IDs returns zero rows and updates zero rows. In a transaction where `app.tenant_id` is reset/unset, prove forced-RLS tables return no participant/outbox/timeline tenant rows.

- [ ] **Step 6: Run configuration, atomicity, and RLS tests**

```bash
cd apps/sanad-platform
./mvnw -Dtest=CollaborationModuleConfigurationTest,CollaborationFoundationAtomicityPostgresTest,CollaborationFoundationRlsPostgresTest test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add apps/sanad-platform/src/main/java/com/sanad/platform/crm/collaboration/application/CollaborationModuleConfiguration.java \
        apps/sanad-platform/src/test/java/com/sanad/platform/crm/collaboration/application/CollaborationModuleConfigurationTest.java \
        apps/sanad-platform/src/test/java/com/sanad/platform/crm/collaboration/CollaborationFoundationAtomicityPostgresTest.java \
        apps/sanad-platform/src/test/java/com/sanad/platform/crm/collaboration/CollaborationFoundationRlsPostgresTest.java
git commit -m "test(crm): prove collaboration foundation atomicity and isolation"
```

---

### Task 9: Run regression and commit acceptance evidence

**Files:**
- Create: `docs/crm/collaboration/CRM-COLLABORATION-FOUNDATION-ACCEPTANCE.md`

**Interfaces:**
- Produces the evidence gate that Contacts, Tasks, Cases, Notes, and Notification plans require before consuming this foundation.

- [ ] **Step 1: Run focused foundation tests**

```bash
cd apps/sanad-platform
./mvnw -Dtest='CrmCollaborationSchemaPostgresTest,EntityParticipantTest,JdbcEntityParticipantRepositoryPostgresTest,PlatformRecipientEligibilityAdapterTest,CollaborationMembershipServiceTest,CollaborationModuleConfigurationTest,JdbcStructuredTimelineEventPostgresTest,JdbcCrmEventOutboxPostgresTest,CollaborationFoundationAtomicityPostgresTest,CollaborationFoundationRlsPostgresTest' test
```

Expected: `FAILURES=0`, `ERRORS=0`.

- [ ] **Step 2: Run affected existing CRM regressions**

```bash
cd apps/sanad-platform
./mvnw -Dtest='OwnershipCommandUseCasesPostgresTest,TransferUseCasesPostgresTest,CrmOwnershipRbacPostgresTest,CrmWorkflowIntegrationPostgresTest' test
```

Expected: `FAILURES=0`, `ERRORS=0`.

- [ ] **Step 3: Run the full backend suite on PostgreSQL Direct**

```bash
cd apps/sanad-platform
./mvnw test
```

Expected release-quality result: `FAILURES=0`, `ERRORS=0`. Reconcile any skip count with the repository's current PostgreSQL Direct acceptance policy before declaring this workstream complete.

- [ ] **Step 4: Capture migration/test evidence without manual placeholders**

From the repository root, generate the evidence file using actual Git values and the exact executed migration names:

```bash
BASELINE_SHA=ffb856fa9b7ffb2a7294d8a5094937150f74841b
IMPLEMENTATION_SHA=$(git rev-parse HEAD)
MIGRATIONS=$(git diff --name-only "$BASELINE_SHA"..HEAD -- 'apps/sanad-platform/src/main/resources/db/migration/*collaboration*' 'apps/sanad-platform/src/main/resources/db/migration/*event_rls*' | paste -sd ', ' -)
mkdir -p docs/crm/collaboration
cat > docs/crm/collaboration/CRM-COLLABORATION-FOUNDATION-ACCEPTANCE.md <<EOF
# CRM Collaboration Foundation Acceptance

- Baseline SHA: $BASELINE_SHA
- Implementation SHA: $IMPLEMENTATION_SHA
- PostgreSQL Direct: PASS
- New migrations: $MIGRATIONS
- Foundation focused suite: PASS — failures 0, errors 0
- Ownership/transfer regression: PASS — failures 0, errors 0
- Full backend suite: PASS — failures 0, errors 0
- RLS tenant-isolation proof: PASS
- Duplicate active participant constraint: PASS
- Structured timeline legacy compatibility: PASS
- Durable outbox persistence/claim primitives: PASS
- Central AuditPort reused: YES
- crm_idempotency_records reused rather than duplicated: YES
- Docker/Testcontainers introduced: NO
- Domain lifecycle changes in this plan: NONE
- Generic collaboration mutation API exposed: NO
EOF
```

If the actual implementation migration filenames differ because of an execution-time version collision, the `git diff` command records the names that were really committed.

- [ ] **Step 5: Review evidence and commit**

```bash
cat docs/crm/collaboration/CRM-COLLABORATION-FOUNDATION-ACCEPTANCE.md
git add docs/crm/collaboration/CRM-COLLABORATION-FOUNDATION-ACCEPTANCE.md
git commit -m "docs(crm): certify collaboration event foundation"
```

Expected: the evidence file contains actual SHAs and migration paths and contains no `TODO`, `TBD`, angle-bracket replacement text, or unverified PASS statement.

---

## Handoff contracts for later implementation plans

- **Contacts:** use `CollaborationMembershipService`, structured `TimelineEventPort`, `CrmEventOutboxPort`; primary ownership remains the Contact owner/current ownership projection.
- **Tasks:** use the same primitives; task review/lifecycle remains a Task-domain command concern.
- **Cases:** use collaboration membership/event primitives without forcing `CASE` into the existing ownership enum in this foundation; the Case plan decides its domain-specific transfer integration.
- **Notes:** reuse recipient eligibility and outbox, but Note recipients/mentions are separate records, not participants.
- **Notification Platform:** consume `crm_event_outbox`; create durable in-app Notification records and optional channel deliveries in its own plan.
- **Unified UX:** consume domain-specific eligible-user/action APIs created by later plans, never the collaboration repository directly.

## Completion gate

This workstream is complete only when all of the following are true: the participant schema and outbox exist; new tables and timeline are tenant-isolated; only collaborator/watcher membership is represented; legacy timeline callers still pass; structured timeline correlation persists; outbox claim/state transitions pass; eligibility uses existing RBAC without grants; no generic collaboration mutation endpoint exists; focused and ownership-regression tests have zero failures/errors; full backend acceptance is reconciled; and no Docker/Testcontainers path was added.
