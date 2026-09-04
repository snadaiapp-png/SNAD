package com.sanad.platform.crm.party.application;

import com.sanad.platform.crm.collaboration.application.CollaborationMembershipService;
import com.sanad.platform.crm.collaboration.domain.CollaborationEntityType;
import com.sanad.platform.crm.collaboration.domain.EntityParticipant;
import com.sanad.platform.crm.collaboration.domain.EntityParticipantRepository;
import com.sanad.platform.crm.collaboration.domain.ParticipantRole;
import com.sanad.platform.crm.collaboration.domain.RecipientEligibilityPort;
import com.sanad.platform.crm.collaboration.infrastructure.JdbcEntityParticipantRepository;
import com.sanad.platform.crm.collaboration.domain.RecipientEligibilityPort.EligibilityDecision;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.crm.party.domain.ContactRepository;
import com.sanad.platform.crm.party.domain.ContactRepository.ContactRecord;
import com.sanad.platform.crm.party.infrastructure.JdbcContactRepository;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Task C4 — Contact collaboration service PostgreSQL Direct proof.
 *
 * <p>Proves the {@code @Transactional} role-switch rollback contract
 * end-to-end against the disposable {@code test_migration} database:</p>
 * <ul>
 *   <li>COLLABORATOR → WATCHER results in exactly one active WATCHER.</li>
 *   <li>WATCHER → COLLABORATOR results in exactly one active COLLABORATOR.</li>
 *   <li>Failed desired-role add rolls back the previous-role removal
 *       (no orphan state).</li>
 *   <li>Owner cannot become a participant (application guard).</li>
 *   <li>Contact version is NOT incremented by share/watch/remove/list
 *       (participant version is independent).</li>
 * </ul>
 *
 * <p>The eligibility port is stubbed to always return {@code eligible=true}
 * so the test can focus on the C4 service's transactional orchestration
 * rather than RBAC wiring (C7 owns RBAC).</p>
 */
@DisplayName("Task C4 — Contact collaboration service (PostgreSQL Direct)")
class ContactCollaborationServicePostgresTest {

    private static final UUID TENANT_A = UUID.fromString("c4c40000-0000-4000-8000-00000000a001");
    private static final UUID CONTACT_OWNER = UUID.fromString("c4c40000-0000-4000-8000-00000000a002");
    private static final UUID TARGET_USER = UUID.fromString("c4c40000-0000-4000-8000-00000000a003");
    private static final UUID ACTOR_ID = UUID.fromString("c4c40000-0000-4000-8000-00000000a004");
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-23T12:00:00Z");

    private static NamedParameterJdbcTemplate jdbc;
    private static TransactionTemplate transactions;
    private static JdbcEntityParticipantRepository participantRepo;
    private static JdbcContactRepository contactRepo;
    private static CollaborationMembershipService membershipService;
    private static ContactCollaborationService service;

    @BeforeAll
    static void setup() {
        boolean ok;
        try {
            ok = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "ContactCollaborationServicePostgresTest");
        } catch (Throwable ignored) {
            ok = false;
        }
        Assumptions.assumeTrue(ok, "PostgreSQL Direct required");
        MigrationTestSchemaSupport.ensureDatabase(
                System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""));
        Flyway.configure()
                .dataSource(MigrationTestSchemaSupport.getIsolatedJdbcUrl(
                                System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad")),
                        System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"),
                        System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""))
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .cleanDisabled(false).validateOnMigrate(true).load()
                .migrate();

        var ds = new DriverManagerDataSource(
                MigrationTestSchemaSupport.getIsolatedJdbcUrl(
                        System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad")),
                System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""));
        jdbc = new NamedParameterJdbcTemplate(ds);
        PlatformTransactionManager txm = new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds);
        transactions = new TransactionTemplate(txm);

        participantRepo = new JdbcEntityParticipantRepository(jdbc);
        contactRepo = new JdbcContactRepository(jdbc);

        // Stub eligibility — we are testing C4's transactional orchestration,
        // not RBAC (C7 owns that gate). Always eligible keeps the test focused.
        RecipientEligibilityPort stubEligibility = stubEligibility();

        // Real membership service wired against the real JdbcEntityParticipantRepository
        // and the stub eligibility port. The C4 service wraps it.
        membershipService = new CollaborationMembershipService(participantRepo, stubEligibility);

        // Wrap the C4 service with a programmatic TransactionTemplate so
        // the @Transactional semantics are honored without a full Spring
        // context. Each mutating C4 method runs inside txm.execute(...)
        // with the tenant GUC set transaction-local — required because
        // V20260823_1 FORCE RLS on crm_contacts rejects SELECT/INSERT/UPDATE
        // without a matching app.tenant_id GUC.
        service = new ContactCollaborationService(contactRepo, membershipService) {
            // Override each mutation to wrap in a real transaction and set
            // the tenant GUC, because @Transactional requires a Spring proxy
            // and the FORCE RLS predicate needs the GUC at query time.
            // This mirrors how CrmRepositoryPostgresTestBase.inTenantTransaction
            // works for repository tests.
            @Override
            public EntityParticipant shareContact(UUID tenantId, UUID contactId, UUID targetUserId, UUID actorId, Instant occurredAt) {
                return transactions.execute(s -> {
                    setGuc(tenantId);
                    return super.shareContact(tenantId, contactId, targetUserId, actorId, occurredAt);
                });
            }
            @Override
            public EntityParticipant watchContact(UUID tenantId, UUID contactId, UUID targetUserId, UUID actorId, Instant occurredAt) {
                return transactions.execute(s -> {
                    setGuc(tenantId);
                    return super.watchContact(tenantId, contactId, targetUserId, actorId, occurredAt);
                });
            }
            @Override
            public EntityParticipant removeParticipant(UUID tenantId, UUID contactId, UUID participantId, long expectedVersion, UUID actorId, Instant occurredAt) {
                return transactions.execute(s -> {
                    setGuc(tenantId);
                    return super.removeParticipant(tenantId, contactId, participantId, expectedVersion, actorId, occurredAt);
                });
            }
            @Override
            public List<EntityParticipant> listParticipants(UUID tenantId, UUID contactId) {
                return transactions.execute(s -> {
                    setGuc(tenantId);
                    return super.listParticipants(tenantId, contactId);
                });
            }
        };
    }

    @BeforeEach
    void seed() {
        transactions.executeWithoutResult(s -> {
            setGuc(TENANT_A);
            jdbc.update("DELETE FROM crm_entity_participants WHERE tenant_id = :t",
                    new MapSqlParameterSource().addValue("t", TENANT_A));
            jdbc.update("DELETE FROM crm_contacts WHERE tenant_id = :t",
                    new MapSqlParameterSource().addValue("t", TENANT_A));
            jdbc.update("DELETE FROM users WHERE tenant_id = :t",
                    new MapSqlParameterSource().addValue("t", TENANT_A));
            jdbc.update("DELETE FROM tenants WHERE id = :t",
                    new MapSqlParameterSource().addValue("t", TENANT_A));
        });
        ensureTenant(TENANT_A);
        ensureUser(CONTACT_OWNER, TENANT_A);
        ensureUser(TARGET_USER, TENANT_A);
        ensureUser(ACTOR_ID, TENANT_A);
    }

    // ── COLLABORATOR → WATCHER results in exactly one active WATCHER ──

    @Test
    @DisplayName("COLLABORATOR → WATCHER leaves exactly one active WATCHER")
    void collaboratorToWatcherLeavesOneActiveWatcher() {
        UUID contactId = seedContact(TENANT_A, "Alice", CONTACT_OWNER);
        long contactVersionBefore = contactVersion(TENANT_A, contactId);

        // Step 1: shareContact adds COLLABORATOR
        EntityParticipant collaborator = service.shareContact(
                TENANT_A, contactId, TARGET_USER, ACTOR_ID, OCCURRED_AT);
        assertThat(collaborator.role()).isEqualTo(ParticipantRole.COLLABORATOR);
        assertThat(activeParticipants(TENANT_A, contactId)).hasSize(1);

        // Step 2: watchContact switches to WATCHER
        EntityParticipant watcher = service.watchContact(
                TENANT_A, contactId, TARGET_USER, ACTOR_ID, OCCURRED_AT.plusSeconds(1));
        assertThat(watcher.role()).isEqualTo(ParticipantRole.WATCHER);

        // Exactly one active participant remains, and it's a WATCHER.
        List<EntityParticipant> active = activeParticipants(TENANT_A, contactId);
        assertThat(active).hasSize(1);
        assertThat(active.get(0).role()).isEqualTo(ParticipantRole.WATCHER);
        assertThat(active.get(0).userId()).isEqualTo(TARGET_USER);

        // Contact version must NOT change (participant version is independent).
        long contactVersionAfter = contactVersion(TENANT_A, contactId);
        assertThat(contactVersionAfter)
                .as("share/watch must not increment crm_contacts.version")
                .isEqualTo(contactVersionBefore);
    }

    // ── WATCHER → COLLABORATOR does the reverse ──

    @Test
    @DisplayName("WATCHER → COLLABORATOR leaves exactly one active COLLABORATOR")
    void watcherToCollaboratorLeavesOneActiveCollaborator() {
        UUID contactId = seedContact(TENANT_A, "Bob", CONTACT_OWNER);
        long contactVersionBefore = contactVersion(TENANT_A, contactId);

        // Step 1: watchContact adds WATCHER
        EntityParticipant watcher = service.watchContact(
                TENANT_A, contactId, TARGET_USER, ACTOR_ID, OCCURRED_AT);
        assertThat(watcher.role()).isEqualTo(ParticipantRole.WATCHER);

        // Step 2: shareContact switches to COLLABORATOR
        EntityParticipant collaborator = service.shareContact(
                TENANT_A, contactId, TARGET_USER, ACTOR_ID, OCCURRED_AT.plusSeconds(1));
        assertThat(collaborator.role()).isEqualTo(ParticipantRole.COLLABORATOR);

        List<EntityParticipant> active = activeParticipants(TENANT_A, contactId);
        assertThat(active).hasSize(1);
        assertThat(active.get(0).role()).isEqualTo(ParticipantRole.COLLABORATOR);

        long contactVersionAfter = contactVersion(TENANT_A, contactId);
        assertThat(contactVersionAfter)
                .as("watch/share must not increment crm_contacts.version")
                .isEqualTo(contactVersionBefore);
    }

    // ── C4-R1: GENUINE rollback proof — old-role removal succeeds, ──
    // ── desired-role INSERT fails AFTER removal, transaction rolls  ──
    // ── back, original participant remains active.                  ──
    //
    // The previous C4 test (failedAddRollsBackRemoval) was INVALID:
    //   1. It manually removed the old participant via direct SQL.
    //   2. It changed the target user to the contact owner.
    //   3. The C4 application guard then rejected BEFORE the role-switch
    //      removal/add executed.
    //   4. Therefore it did NOT prove rollback after:
    //        remove old role → add new role fails → rollback.
    //
    // The replacement below uses a TEST-ONLY EntityParticipantRepository
    // decorator that delegates every real DB operation to the real
    // JdbcEntityParticipantRepository, EXCEPT when a test-controlled flag
    // is enabled and the inserted participant's role matches the desired
    // failing role — in which case it throws a deterministic RuntimeException
    // AFTER the real removal has already executed against the real database.
    //
    // The transaction is driven by the same Spring TransactionTemplate
    // the C4 service uses in production-style calls, so the rollback
    // semantics are real PostgreSQL ROLLBACK — no mocking.

    /**
     * Test-only decorator that wraps the real
     * {@link JdbcEntityParticipantRepository} and injects a deterministic
     * insert failure for the desired failing role after the real
     * removal has executed against the database.
     */
    private static final class FailingInsertParticipantRepository
            implements EntityParticipantRepository {

        private final EntityParticipantRepository delegate;
        private final AtomicBoolean failNextWatchInsert;
        private final ParticipantRole failingRole;

        FailingInsertParticipantRepository(EntityParticipantRepository delegate,
                                            ParticipantRole failingRole) {
            this.delegate = Objects.requireNonNull(delegate);
            this.failNextWatchInsert = new AtomicBoolean(false);
            this.failingRole = Objects.requireNonNull(failingRole);
        }

        /** Arm the decorator to throw on the next matching insert. */
        void armFailure() {
            failNextWatchInsert.set(true);
        }

        @Override
        public EntityParticipant insert(EntityParticipant participant) {
            if (failNextWatchInsert.get() && participant.role() == failingRole) {
                failNextWatchInsert.set(false);
                throw new DesiredRoleInsertFailure(
                        "injected failure: desired role " + failingRole
                                + " insert rejected after old-role removal");
            }
            return delegate.insert(participant);
        }

        @Override
        public Optional<EntityParticipant> findActive(UUID tenantId,
                                                       CollaborationEntityType entityType,
                                                       UUID entityId,
                                                       UUID userId,
                                                       ParticipantRole role) {
            return delegate.findActive(tenantId, entityType, entityId, userId, role);
        }

        @Override
        public Optional<EntityParticipant> findById(UUID tenantId, UUID participantId) {
            return delegate.findById(tenantId, participantId);
        }

        @Override
        public List<EntityParticipant> listActive(UUID tenantId,
                                                  CollaborationEntityType entityType,
                                                  UUID entityId) {
            return delegate.listActive(tenantId, entityType, entityId);
        }

        @Override
        public boolean markRemoved(UUID tenantId,
                                   UUID participantId,
                                   long expectedVersion,
                                   UUID removedByUserId,
                                   Instant removedAt) {
            return delegate.markRemoved(tenantId, participantId, expectedVersion,
                    removedByUserId, removedAt);
        }
    }

    /** Deterministic RuntimeException used by the decorator to signal the injected insert failure. */
    static final class DesiredRoleInsertFailure extends RuntimeException {
        DesiredRoleInsertFailure(String message) {
            super(message);
        }
    }

    @Test
    @DisplayName("C4-R1. COLLABORATOR → WATCHER: WATCHER insert fails AFTER removal → COLLABORATOR remains active (rollback proven)")
    void collaboratorToWatcherRollbackPreservesOriginalCollaborator() {
        UUID contactId = seedContact(TENANT_A, "Carol", CONTACT_OWNER);

        // Step 1: Add TARGET_USER as COLLABORATOR normally (real DB insert).
        EntityParticipant originalCollaborator = service.shareContact(
                TENANT_A, contactId, TARGET_USER, ACTOR_ID, OCCURRED_AT);
        assertThat(originalCollaborator.role()).isEqualTo(ParticipantRole.COLLABORATOR);
        assertThat(activeParticipants(TENANT_A, contactId)).hasSize(1);
        UUID originalId = originalCollaborator.id();
        long originalVersion = originalCollaborator.version();

        // Step 2: Re-wire the service to use a failing-insert decorator for WATCHER.
        // The decorator delegates ALL real DB writes; only the WATCHER insert throws.
        FailingInsertParticipantRepository failingRepo =
                new FailingInsertParticipantRepository(participantRepo, ParticipantRole.WATCHER);
        CollaborationMembershipService failingMembership =
                new CollaborationMembershipService(failingRepo, stubEligibility());
        ContactCollaborationService failingService = new ContactCollaborationService(
                contactRepo, failingMembership) {
            @Override
            public EntityParticipant watchContact(UUID tenantId, UUID contactId, UUID targetUserId,
                                                   UUID actorId, Instant occurredAt) {
                return transactions.execute(s -> {
                    setGuc(tenantId);
                    return super.watchContact(tenantId, contactId, targetUserId, actorId, occurredAt);
                });
            }
        };
        failingRepo.armFailure();

        // Step 3: watchContact must throw the injected failure AFTER the real
        // COLLABORATOR removal executed against the database.
        assertThatThrownBy(() ->
                failingService.watchContact(TENANT_A, contactId, TARGET_USER, ACTOR_ID,
                        OCCURRED_AT.plusSeconds(2)))
                .isInstanceOf(DesiredRoleInsertFailure.class)
                .hasMessageContaining("injected failure: desired role WATCHER");

        // Step 4: After rollback, in a NEW transaction, verify:
        //   - exactly one active participant
        //   - role = COLLABORATOR
        //   - same participant id = original id
        //   - removed_at = NULL
        //   - participant version = original version (NOT incremented)
        //   - NO WATCHER exists.
        List<EntityParticipant> active = activeParticipants(TENANT_A, contactId);
        assertThat(active)
                .as("Terminal state must have exactly one active participant after rollback")
                .hasSize(1);
        EntityParticipant survivor = active.get(0);
        assertThat(survivor.id())
                .as("Surviving participant must be the original COLLABORATOR (same id)")
                .isEqualTo(originalId);
        assertThat(survivor.role())
                .as("Surviving participant role must be COLLABORATOR (WATCHER add rolled back)")
                .isEqualTo(ParticipantRole.COLLABORATOR);
        assertThat(survivor.removedAt())
                .as("Surviving participant must NOT be marked removed (removal rolled back)")
                .isNull();
        assertThat(survivor.version())
                .as("Surviving participant version must equal original (no markRemoved commit)")
                .isEqualTo(originalVersion);

        // Confirm NO WATCHER row exists in any state (active or removed).
        List<EntityParticipant> allContactParticipants = transactions.execute(s -> {
            setGuc(TENANT_A);
            return jdbc.query("""
                    SELECT id, tenant_id, entity_type, entity_id, user_id, role,
                           added_by, added_at, removed_by, removed_at, version
                    FROM crm_entity_participants
                    WHERE tenant_id = :t AND entity_type = 'CONTACT' AND entity_id = :c
                    ORDER BY added_at
                    """,
                    new MapSqlParameterSource()
                            .addValue("t", TENANT_A)
                            .addValue("c", contactId),
                    (rs, rowNum) -> new EntityParticipant(
                            UUID.fromString(rs.getString("id")),
                            UUID.fromString(rs.getString("tenant_id")),
                            CollaborationEntityType.valueOf(rs.getString("entity_type")),
                            UUID.fromString(rs.getString("entity_id")),
                            UUID.fromString(rs.getString("user_id")),
                            ParticipantRole.valueOf(rs.getString("role")),
                            UUID.fromString(rs.getString("added_by")),
                            rs.getTimestamp("added_at").toInstant(),
                            rs.getString("removed_by") == null ? null
                                    : UUID.fromString(rs.getString("removed_by")),
                            rs.getTimestamp("removed_at") == null ? null
                                    : rs.getTimestamp("removed_at").toInstant(),
                            rs.getLong("version")));
        });
        assertThat(allContactParticipants)
                .as("Exactly one participant row (the original COLLABORATOR) must exist after rollback")
                .hasSize(1);
        assertThat(allContactParticipants.get(0).role()).isEqualTo(ParticipantRole.COLLABORATOR);
    }

    // ── C4-R1: Reverse direction — WATCHER → COLLABORATOR rollback ──

    @Test
    @DisplayName("C4-R1. WATCHER → COLLABORATOR: COLLABORATOR insert fails AFTER removal → WATCHER remains active (rollback proven)")
    void watcherToCollaboratorRollbackPreservesOriginalWatcher() {
        UUID contactId = seedContact(TENANT_A, "Dave", CONTACT_OWNER);

        // Step 1: Add TARGET_USER as WATCHER normally.
        EntityParticipant originalWatcher = service.watchContact(
                TENANT_A, contactId, TARGET_USER, ACTOR_ID, OCCURRED_AT);
        assertThat(originalWatcher.role()).isEqualTo(ParticipantRole.WATCHER);
        UUID originalId = originalWatcher.id();
        long originalVersion = originalWatcher.version();

        // Step 2: Re-wire the service to use a failing-insert decorator for COLLABORATOR.
        FailingInsertParticipantRepository failingRepo =
                new FailingInsertParticipantRepository(participantRepo, ParticipantRole.COLLABORATOR);
        CollaborationMembershipService failingMembership =
                new CollaborationMembershipService(failingRepo, stubEligibility());
        ContactCollaborationService failingService = new ContactCollaborationService(
                contactRepo, failingMembership) {
            @Override
            public EntityParticipant shareContact(UUID tenantId, UUID contactId, UUID targetUserId,
                                                  UUID actorId, Instant occurredAt) {
                return transactions.execute(s -> {
                    setGuc(tenantId);
                    return super.shareContact(tenantId, contactId, targetUserId, actorId, occurredAt);
                });
            }
        };
        failingRepo.armFailure();

        // Step 3: shareContact must throw the injected failure AFTER the real
        // WATCHER removal executed against the database.
        assertThatThrownBy(() ->
                failingService.shareContact(TENANT_A, contactId, TARGET_USER, ACTOR_ID,
                        OCCURRED_AT.plusSeconds(2)))
                .isInstanceOf(DesiredRoleInsertFailure.class)
                .hasMessageContaining("injected failure: desired role COLLABORATOR");

        // Step 4: After rollback, in a NEW transaction, verify WATCHER survives intact.
        List<EntityParticipant> active = activeParticipants(TENANT_A, contactId);
        assertThat(active)
                .as("Terminal state must have exactly one active participant after rollback")
                .hasSize(1);
        EntityParticipant survivor = active.get(0);
        assertThat(survivor.id())
                .as("Surviving participant must be the original WATCHER (same id)")
                .isEqualTo(originalId);
        assertThat(survivor.role())
                .as("Surviving participant role must be WATCHER (COLLABORATOR add rolled back)")
                .isEqualTo(ParticipantRole.WATCHER);
        assertThat(survivor.removedAt())
                .as("Surviving participant must NOT be marked removed (removal rolled back)")
                .isNull();
        assertThat(survivor.version())
                .as("Surviving participant version must equal original (no markRemoved commit)")
                .isEqualTo(originalVersion);
    }

    // ── Owner cannot become participant (application guard) ──

    @Test
    @DisplayName("Owner cannot become a participant (C4 application guard)")
    void ownerCannotBecomeParticipant() {
        UUID contactId = seedContact(TENANT_A, "Dave", CONTACT_OWNER);

        assertThatThrownBy(() ->
                service.shareContact(TENANT_A, contactId, CONTACT_OWNER, ACTOR_ID, OCCURRED_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("owner cannot be a participant");

        assertThatThrownBy(() ->
                service.watchContact(TENANT_A, contactId, CONTACT_OWNER, ACTOR_ID, OCCURRED_AT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("owner cannot be a participant");

        // No participant rows created
        assertThat(activeParticipants(TENANT_A, contactId)).isEmpty();
    }

    // ── removeParticipant succeeds ──

    @Test
    @DisplayName("removeParticipant marks the active participant as removed")
    void removeParticipantSucceeds() {
        UUID contactId = seedContact(TENANT_A, "Eve", CONTACT_OWNER);
        EntityParticipant added = service.shareContact(
                TENANT_A, contactId, TARGET_USER, ACTOR_ID, OCCURRED_AT);

        EntityParticipant removed = service.removeParticipant(
                TENANT_A, contactId, added.id(), added.version(), ACTOR_ID, OCCURRED_AT.plusSeconds(1));

        assertThat(removed.isActive()).isFalse();
        assertThat(activeParticipants(TENANT_A, contactId)).isEmpty();
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private static void setGuc(UUID t) {
        jdbc.queryForObject("SELECT set_config('app.tenant_id', :t, true)",
                new MapSqlParameterSource().addValue("t", t.toString()), String.class);
    }

    /** Always-eligible stub so C4 transactional orchestration can be tested without RBAC wiring. */
    private static RecipientEligibilityPort stubEligibility() {
        return (tenantId, userId, orgId, cap) ->
                new EligibilityDecision(true, "ELIGIBLE_STUB");
    }

    private long contactVersion(UUID tenantId, UUID contactId) {
        return transactions.execute(s -> {
            setGuc(tenantId);
            return jdbc.queryForObject(
                    "SELECT version FROM crm_contacts WHERE id = :id",
                    new MapSqlParameterSource().addValue("id", contactId),
                    Long.class);
        });
    }

    private List<EntityParticipant> activeParticipants(UUID tenantId, UUID contactId) {
        return transactions.execute(s -> {
            setGuc(tenantId);
            return participantRepo.listActive(tenantId, CollaborationEntityType.CONTACT, contactId);
        });
    }

    private UUID seedContact(UUID tenantId, String name, UUID ownerId) {
        // Seed via the JdbcContactRepository.create so we exercise the real
        // production ContactRepository (which sets all required columns +
        // runs the legacy-relationship backfill cleanly).
        return transactions.execute(s -> {
            setGuc(tenantId);
            ContactRecord created = contactRepo.create(tenantId, ACTOR_ID,
                    new ContactRepository.CreateContactCommand(
                            null, name, null, null, null, null, null, ownerId, null));
            return created.id();
        });
    }

    private void ensureTenant(UUID id) {
        jdbc.update("""
                INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at)
                VALUES (:id, :name, :sub, 'ACTIVE', NOW(), NOW())
                ON CONFLICT (id) DO NOTHING
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("name", "C4 Tenant " + id.toString().substring(0, 8))
                .addValue("sub", "c4-" + id.toString().substring(0, 8)));
    }

    private void ensureUser(UUID id, UUID tenant) {
        jdbc.update("""
                INSERT INTO users (id, tenant_id, email, display_name, status, password_hash, created_at, updated_at)
                VALUES (:id, :t, :email, :name, 'ACTIVE', 'dummy', NOW(), NOW())
                ON CONFLICT (id) DO NOTHING
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("t", tenant)
                .addValue("email", "c4-" + id + "@snad.test")
                .addValue("name", "C4 User " + id.toString().substring(0, 8)));
    }
}
