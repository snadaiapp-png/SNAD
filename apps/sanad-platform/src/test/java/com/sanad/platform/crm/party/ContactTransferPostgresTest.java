package com.sanad.platform.crm.party;

import com.sanad.platform.crm.collaboration.application.CollaborationMembershipService;
import com.sanad.platform.crm.collaboration.domain.CollaborationEntityType;
import com.sanad.platform.crm.collaboration.domain.EntityParticipant;
import com.sanad.platform.crm.collaboration.domain.EntityParticipantRepository;
import com.sanad.platform.crm.collaboration.domain.ParticipantRole;
import com.sanad.platform.crm.collaboration.domain.RecipientEligibilityPort;
import com.sanad.platform.crm.collaboration.domain.RecipientEligibilityPort.EligibilityDecision;
import com.sanad.platform.crm.collaboration.infrastructure.JdbcEntityParticipantRepository;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.crm.party.application.ContactTransferUseCases;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Task C5 — Canonical Contact owner transfer — PostgreSQL Direct proof.
 *
 * <p>Proves the C5 atomicity, version-increment, lock-ordering, and
 * rollback contracts against the disposable {@code test_migration}
 * database via real JDBC repositories.</p>
 *
 * <h3>Test matrix (per C5 spec Sections 17–24)</h3>
 * <ul>
 *   <li>18. PostgreSQL success proof — owner transferred, version +1,
 *       previous owner WATCHER, no active COLLABORATOR for previous owner.</li>
 *   <li>19. Target participant normalization proof — COLLABORATOR target
 *       removed before becoming owner; participant history preserved.</li>
 *   <li>20. Stale-version atomicity — CRM_CONCURRENCY_CONFLICT, no mutation.</li>
 *   <li>21. Genuine rollback after owner UPDATE — watcher-eligibility
 *       denial rolls back target removal + owner UPDATE + WATCHER add.</li>
 *   <li>22. retainPreviousOwnerAsWatcher=false — watcher eligibility NOT
 *       evaluated; transfer succeeds.</li>
 *   <li>23. Concurrent transfer — exactly one succeeds, exactly one conflict,
 *       final version = N+1 (never N+2).</li>
 *   <li>24. Spring {@code @Transactional} metadata present on transferContact.</li>
 * </ul>
 */
@DisplayName("Task C5 — Canonical Contact owner transfer (PostgreSQL Direct)")
class ContactTransferPostgresTest {

    private static final UUID TENANT_A = UUID.fromString("c5c50000-0000-4000-8000-00000000a001");
    private static final UUID USER_A = UUID.fromString("c5c50000-0000-4000-8000-00000000a002");
    private static final UUID USER_B = UUID.fromString("c5c50000-0000-4000-8000-00000000b002");
    private static final UUID USER_C = UUID.fromString("c5c50000-0000-4000-8000-00000000c002");
    private static final UUID ACTOR_ID = UUID.fromString("c5c50000-0000-4000-8000-00000000d001");
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-23T12:00:00Z");

    private static NamedParameterJdbcTemplate jdbc;
    private static TransactionTemplate transactions;
    private static JdbcContactRepository contactRepo;
    private static JdbcEntityParticipantRepository participantRepo;
    private static ContactTransferUseCases service;
    private static RecipientEligibilityPort stubEligibility;

    @BeforeAll
    static void setup() {
        boolean ok;
        try {
            ok = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "ContactTransferPostgresTest");
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

        contactRepo = new JdbcContactRepository(jdbc);
        participantRepo = new JdbcEntityParticipantRepository(jdbc);

        // Default stub: all users eligible. Individual tests override via
        // service re-wiring when they need to inject a denial.
        stubEligibility = (tenantId, userId, orgId, cap) ->
                new EligibilityDecision(true, "ELIGIBLE_STUB");

        CollaborationMembershipService membershipService =
                new CollaborationMembershipService(participantRepo, stubEligibility);

        // Wrap the @Transactional service in a programmatic TransactionTemplate
        // so transferContact executes inside a real Spring-managed transaction
        // (the @Transactional annotation requires a Spring proxy; in this
        // unit-style test we drive the transaction manually via TransactionTemplate,
        // which exercises the exact same DataSourceTransactionManager rollback
        // semantics). The annotation metadata is verified separately by
        // Section 24 reflection test.
        //
        // The override sets the tenant GUC transaction-locally before each
        // transferContact invocation — required because V20260823_1 FORCE RLS
        // on crm_contacts rejects SELECT/INSERT/UPDATE without a matching
        // app.tenant_id GUC. In production this GUC is applied by the
        // TenantRlsConnectionHandler under the @Transactional boundary; in
        // this unit-style test we use a plain DriverManagerDataSource so the
        // handler is not present and we must apply the GUC explicitly.
        service = new ContactTransferUseCases(contactRepo, membershipService, stubEligibility) {
            @Override
            public ContactRecord transferContact(ContactTransferUseCases.TransferContactCommand command) {
                return transactions.execute(s -> {
                    setGuc(command.tenantId());
                    return super.transferContact(command);
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
        ensureUser(USER_A, TENANT_A);
        ensureUser(USER_B, TENANT_A);
        ensureUser(USER_C, TENANT_A);
        ensureUser(ACTOR_ID, TENANT_A);
    }

    // ── 18. PostgreSQL success proof ─────────────────────────────────────

    @Test
    @DisplayName("18. Successful transfer: owner=USER_B, version=N+1, USER_A=WATCHER, no USER_A COLLABORATOR")
    void successfulTransferProof() {
        UUID contactId = seedContact(USER_A);
        long initialVersion = contactVersion(contactId);

        ContactRecord transferred = service.transferContact(
                TENANT_A, contactId, USER_B, initialVersion, ACTOR_ID, OCCURRED_AT);

        assertThat(transferred.ownerUserId()).isEqualTo(USER_B);
        assertThat(transferred.version()).isEqualTo(initialVersion + 1);

        // USER_B must NOT be an active participant (the C5 service normalized
        // their participant membership away before the owner update).
        assertThat(activeParticipantFor(contactId, USER_B)).isEmpty();

        // USER_A must be exactly one active WATCHER (default retention=true).
        List<EntityParticipant> userAParticipants = activeParticipantsFor(contactId, USER_A);
        assertThat(userAParticipants).hasSize(1);
        assertThat(userAParticipants.get(0).role()).isEqualTo(ParticipantRole.WATCHER);

        // No active COLLABORATOR for USER_A on this contact.
        assertThat(userAParticipants.stream()
                .anyMatch(p -> p.role() == ParticipantRole.COLLABORATOR)).isFalse();
    }

    // ── 19. Target participant normalization proof ───────────────────────

    @Test
    @DisplayName("19. Target COLLABORATOR removed before becoming owner; participant history preserved")
    void targetCollaboratorNormalizedBeforeOwnerUpdate() {
        UUID contactId = seedContact(USER_A);
        long initialVersion = contactVersion(contactId);

        // Make USER_B an active COLLABORATOR via the C4 share path.
        CollaborationMembershipService membership =
                new CollaborationMembershipService(participantRepo, stubEligibility);
        transactions.executeWithoutResult(s -> {
            setGuc(TENANT_A);
            membership.addParticipant(
                    new CollaborationMembershipService.AddParticipantCommand(
                            TENANT_A, CollaborationEntityType.CONTACT, contactId, USER_B,
                            ParticipantRole.COLLABORATOR, ACTOR_ID, OCCURRED_AT),
                    new CollaborationMembershipService.EligibilityPolicy(null, "CRM.CONTACT.READ"));
        });

        Optional<EntityParticipant> existingCollaborator =
                activeParticipantFor(contactId, USER_B);
        assertThat(existingCollaborator).isPresent();
        UUID originalParticipantId = existingCollaborator.get().id();

        // Now transfer ownership to USER_B.
        ContactRecord transferred = service.transferContact(
                TENANT_A, contactId, USER_B, initialVersion, ACTOR_ID, OCCURRED_AT);

        assertThat(transferred.ownerUserId()).isEqualTo(USER_B);
        assertThat(transferred.version()).isEqualTo(initialVersion + 1);

        // USER_B's COLLABORATOR must be removed (removed_at != null).
        Optional<EntityParticipant> currentParticipant =
                activeParticipantFor(contactId, USER_B);
        assertThat(currentParticipant)
                .as("USER_B must not be an active participant after becoming owner")
                .isEmpty();

        // Participant history remains — the row is still in the database, just marked removed.
        long historicalCount = transactions.execute(s -> {
            setGuc(TENANT_A);
            Long count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM crm_entity_participants "
                            + "WHERE tenant_id = :t AND entity_type = 'CONTACT' AND entity_id = :c "
                            + "AND user_id = :u AND removed_at IS NOT NULL",
                    new MapSqlParameterSource()
                            .addValue("t", TENANT_A)
                            .addValue("c", contactId)
                            .addValue("u", USER_B),
                    Long.class);
            return count != null ? count : 0L;
        });
        assertThat(historicalCount)
                .as("USER_B's old COLLABORATOR row must still exist as historical (removed_at IS NOT NULL)")
                .isEqualTo(1L);
    }

    // ── 20. Stale-version atomicity ──────────────────────────────────────

    @Test
    @DisplayName("20. Stale expectedVersion → CRM_CONCURRENCY_CONFLICT; no mutation")
    void staleVersionAtomicity() {
        UUID contactId = seedContact(USER_A);
        long initialVersion = contactVersion(contactId);
        long staleVersion = initialVersion + 999L;

        assertThatThrownBy(() ->
                service.transferContact(
                        TENANT_A, contactId, USER_B, staleVersion, ACTOR_ID, OCCURRED_AT))
                .isInstanceOf(com.sanad.platform.crm.error.CrmContractException.class)
                .satisfies(ex -> assertThat(
                        ((com.sanad.platform.crm.error.CrmContractException) ex).code())
                        .isEqualTo(com.sanad.platform.crm.error.CrmErrorCode.CRM_CONCURRENCY_CONFLICT));

        // Terminal state — nothing changed.
        ContactRecord current = readContact(contactId);
        assertThat(current.ownerUserId()).isEqualTo(USER_A);
        assertThat(current.version()).isEqualTo(initialVersion);
        assertThat(activeParticipantsFor(contactId, USER_A)).isEmpty();
        assertThat(activeParticipantsFor(contactId, USER_B)).isEmpty();
    }

    // ── 21. Genuine rollback after owner UPDATE ──────────────────────────

    @Test
    @DisplayName("21. Watcher eligibility denial AFTER owner UPDATE rolls back entire transfer")
    void genuineRollbackAfterOwnerUpdate() {
        UUID contactId = seedContact(USER_A);
        long initialVersion = contactVersion(contactId);

        // Make USER_B an active COLLABORATOR (must be removed before owner update).
        CollaborationMembershipService membership =
                new CollaborationMembershipService(participantRepo, stubEligibility);
        transactions.executeWithoutResult(s -> {
            setGuc(TENANT_A);
            membership.addParticipant(
                    new CollaborationMembershipService.AddParticipantCommand(
                            TENANT_A, CollaborationEntityType.CONTACT, contactId, USER_B,
                            ParticipantRole.COLLABORATOR, ACTOR_ID, OCCURRED_AT),
                    new CollaborationMembershipService.EligibilityPolicy(null, "CRM.CONTACT.READ"));
        });
        Optional<EntityParticipant> originalCollaboratorOpt =
                activeParticipantFor(contactId, USER_B);
        assertThat(originalCollaboratorOpt).isPresent();
        EntityParticipant originalCollaborator = originalCollaboratorOpt.get();
        UUID originalParticipantId = originalCollaborator.id();
        long originalParticipantVersion = originalCollaborator.version();

        // Re-wire the C5 service with an eligibility port that:
        //   - approves USER_B (target owner)
        //   - DENIES USER_A (previous owner) WATCHER retention
        RecipientEligibilityPort denialEligibility = (tenantId, userId, orgId, cap) -> {
            if (USER_A.equals(userId)) {
                return new EligibilityDecision(false, "FORCED_DENIAL_PREVIOUS_OWNER");
            }
            return new EligibilityDecision(true, "ELIGIBLE");
        };
        CollaborationMembershipService denialMembership =
                new CollaborationMembershipService(participantRepo, denialEligibility);
        ContactTransferUseCases denialService = new ContactTransferUseCases(
                contactRepo, denialMembership, denialEligibility) {
            @Override
            public ContactRecord transferContact(
                    ContactTransferUseCases.TransferContactCommand command) {
                return transactions.execute(s -> {
                    setGuc(command.tenantId());
                    return super.transferContact(command);
                });
            }
        };

        // Execute transfer — should fail because USER_A's WATCHER add is denied.
        assertThatThrownBy(() ->
                denialService.transferContact(
                        TENANT_A, contactId, USER_B, initialVersion, ACTOR_ID, OCCURRED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FORCED_DENIAL_PREVIOUS_OWNER");

        // Verify ROLLBACK terminal state:
        ContactRecord currentContact = readContact(contactId);
        assertThat(currentContact.ownerUserId())
                .as("Contact owner must be restored to USER_A after rollback")
                .isEqualTo(USER_A);
        assertThat(currentContact.version())
                .as("Contact version must be unchanged after rollback")
                .isEqualTo(initialVersion);

        // USER_B's COLLABORATOR row must be restored (active again, same id/version).
        Optional<EntityParticipant> restoredParticipant =
                activeParticipantFor(contactId, USER_B);
        assertThat(restoredParticipant)
                .as("USER_B COLLABORATOR row must be restored after rollback")
                .isPresent();
        assertThat(restoredParticipant.get().id()).isEqualTo(originalParticipantId);
        assertThat(restoredParticipant.get().version())
                .isEqualTo(originalParticipantVersion);
        assertThat(restoredParticipant.get().removedAt()).isNull();

        // USER_A must NOT have a WATCHER row added.
        assertThat(activeParticipantsFor(contactId, USER_A))
                .as("USER_A must NOT have any active participant after rollback")
                .isEmpty();
    }

    // ── 22. retainPreviousOwnerAsWatcher=false proof ────────────────────

    @Test
    @DisplayName("22. retainPreviousOwnerAsWatcher=false → transfer succeeds despite previous-owner denial")
    void retentionFalseWithIneligiblePreviousOwner() {
        UUID contactId = seedContact(USER_A);
        long initialVersion = contactVersion(contactId);

        // Eligibility denies USER_A but approves USER_B.
        RecipientEligibilityPort denialEligibility = (tenantId, userId, orgId, cap) -> {
            if (USER_A.equals(userId)) {
                return new EligibilityDecision(false, "FORCED_DENIAL_PREVIOUS_OWNER");
            }
            return new EligibilityDecision(true, "ELIGIBLE");
        };
        CollaborationMembershipService denialMembership =
                new CollaborationMembershipService(participantRepo, denialEligibility);
        ContactTransferUseCases denialService = new ContactTransferUseCases(
                contactRepo, denialMembership, denialEligibility) {
            @Override
            public ContactRecord transferContact(
                    ContactTransferUseCases.TransferContactCommand command) {
                return transactions.execute(s -> {
                    setGuc(command.tenantId());
                    return super.transferContact(command);
                });
            }
        };

        ContactRecord transferred = denialService.transferContact(
                new ContactTransferUseCases.TransferContactCommand(
                        TENANT_A, contactId, USER_B, initialVersion, ACTOR_ID,
                        OCCURRED_AT, false));

        assertThat(transferred.ownerUserId()).isEqualTo(USER_B);
        assertThat(transferred.version()).isEqualTo(initialVersion + 1);
        // No participant added for USER_A.
        assertThat(activeParticipantsFor(contactId, USER_A)).isEmpty();
    }

    // ── 23. Concurrent transfer proof ────────────────────────────────────

    @Test
    @DisplayName("23. Concurrent transfer: exactly one succeeds, exactly one conflict, final version = N+1")
    void concurrentTransferProof() throws Exception {
        UUID contactId = seedContact(USER_A);
        long initialVersion = contactVersion(contactId);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> errorA = new AtomicReference<>();
        AtomicReference<Throwable> errorB = new AtomicReference<>();
        AtomicReference<ContactRecord> resultA = new AtomicReference<>();
        AtomicReference<ContactRecord> resultB = new AtomicReference<>();

        Thread tA = new Thread(() -> {
            try {
                ready.countDown();
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                ContactRecord r = service.transferContact(
                        TENANT_A, contactId, USER_B, initialVersion, ACTOR_ID, OCCURRED_AT);
                resultA.set(r);
            } catch (Throwable t) {
                errorA.set(t);
            }
        });
        Thread tB = new Thread(() -> {
            try {
                ready.countDown();
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                ContactRecord r = service.transferContact(
                        TENANT_A, contactId, USER_C, initialVersion, ACTOR_ID, OCCURRED_AT.plusSeconds(1));
                resultB.set(r);
            } catch (Throwable t) {
                errorB.set(t);
            }
        });

        tA.start();
        tB.start();
        ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        tA.join(30_000);
        tB.join(30_000);

        boolean aSuccess = errorA.get() == null && resultA.get() != null;
        boolean bSuccess = errorB.get() == null && resultB.get() != null;
        long successes = (aSuccess ? 1 : 0) + (bSuccess ? 1 : 0);
        long conflicts = 0;
        if (errorA.get() instanceof com.sanad.platform.crm.error.CrmContractException cce
                && cce.code() == com.sanad.platform.crm.error.CrmErrorCode.CRM_CONCURRENCY_CONFLICT) {
            conflicts++;
        }
        if (errorB.get() instanceof com.sanad.platform.crm.error.CrmContractException cce
                && cce.code() == com.sanad.platform.crm.error.CrmErrorCode.CRM_CONCURRENCY_CONFLICT) {
            conflicts++;
        }

        assertThat(successes)
                .as("Exactly one concurrent transfer must succeed")
                .isEqualTo(1);
        assertThat(conflicts)
                .as("Exactly one concurrent transfer must fail with CRM_CONCURRENCY_CONFLICT")
                .isEqualTo(1);

        ContactRecord finalContact = readContact(contactId);
        assertThat(finalContact.version())
                .as("Final version must be exactly N+1 (never N+2)")
                .isEqualTo(initialVersion + 1);
        assertThat(finalContact.ownerUserId())
                .as("Final owner must be exactly one of USER_B / USER_C")
                .isIn(USER_B, USER_C);
    }

    // ── 24. Spring @Transactional metadata present ───────────────────────

    @Test
    @DisplayName("24. transferContact carries Spring @Transactional metadata")
    void transferContactHasTransactionalMetadata() throws Exception {
        org.springframework.transaction.annotation.AnnotationTransactionAttributeSource source =
                new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource();
        java.lang.reflect.Method method = ContactTransferUseCases.class.getMethod(
                "transferContact",
                ContactTransferUseCases.TransferContactCommand.class);
        org.springframework.transaction.interceptor.TransactionAttribute attr =
                source.getTransactionAttribute(method, ContactTransferUseCases.class);
        assertThat(attr)
                .as("transferContact must carry @Transactional metadata")
                .isNotNull();
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private static void setGuc(UUID t) {
        jdbc.queryForObject("SELECT set_config('app.tenant_id', :t, true)",
                new MapSqlParameterSource().addValue("t", t.toString()), String.class);
    }

    private UUID seedContact(UUID ownerId) {
        return transactions.execute(s -> {
            setGuc(TENANT_A);
            ContactRecord created = contactRepo.create(TENANT_A, ACTOR_ID,
                    new ContactRepository.CreateContactCommand(
                            null, "Jane", "Doe", null, null, null, null, ownerId, null));
            return created.id();
        });
    }

    private long contactVersion(UUID contactId) {
        return transactions.execute(s -> {
            setGuc(TENANT_A);
            return jdbc.queryForObject(
                    "SELECT version FROM crm_contacts WHERE id = :id",
                    new MapSqlParameterSource().addValue("id", contactId),
                    Long.class);
        });
    }

    private ContactRecord readContact(UUID contactId) {
        return transactions.execute(s -> {
            setGuc(TENANT_A);
            return contactRepo.findById(TENANT_A, contactId);
        });
    }

    private List<EntityParticipant> activeParticipantsFor(UUID contactId, UUID userId) {
        return transactions.execute(s -> {
            setGuc(TENANT_A);
            return participantRepo.listActive(TENANT_A, CollaborationEntityType.CONTACT, contactId)
                    .stream()
                    .filter(p -> p.userId().equals(userId))
                    .toList();
        });
    }

    private Optional<EntityParticipant> activeParticipantFor(UUID contactId, UUID userId) {
        return activeParticipantsFor(contactId, userId).stream().findFirst();
    }

    private void ensureTenant(UUID id) {
        jdbc.update("""
                INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at)
                VALUES (:id, :name, :sub, 'ACTIVE', NOW(), NOW())
                ON CONFLICT (id) DO NOTHING
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("name", "C5 Tenant " + id.toString().substring(0, 8))
                .addValue("sub", "c5-" + id.toString().substring(0, 8)));
    }

    private void ensureUser(UUID id, UUID tenant) {
        jdbc.update("""
                INSERT INTO users (id, tenant_id, email, display_name, status, password_hash, created_at, updated_at)
                VALUES (:id, :t, :email, :name, 'ACTIVE', 'dummy', NOW(), NOW())
                ON CONFLICT (id) DO NOTHING
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("t", tenant)
                .addValue("email", "c5-" + id + "@snad.test")
                .addValue("name", "C5 User " + id.toString().substring(0, 8)));
    }
}
