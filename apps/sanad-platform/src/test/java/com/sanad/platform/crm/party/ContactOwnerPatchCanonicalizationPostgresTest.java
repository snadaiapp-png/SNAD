package com.sanad.platform.crm.party;

import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import com.sanad.platform.crm.collaboration.application.CollaborationMembershipService;
import com.sanad.platform.crm.collaboration.domain.CollaborationEntityType;
import com.sanad.platform.crm.collaboration.domain.EntityParticipant;
import com.sanad.platform.crm.collaboration.domain.EntityParticipantRepository;
import com.sanad.platform.crm.collaboration.domain.ParticipantRole;
import com.sanad.platform.crm.collaboration.domain.RecipientEligibilityPort;
import com.sanad.platform.crm.collaboration.domain.RecipientEligibilityPort.EligibilityDecision;
import com.sanad.platform.crm.collaboration.infrastructure.JdbcEntityParticipantRepository;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.party.application.ContactTransferUseCases;
import com.sanad.platform.crm.party.application.ContactUseCases;
import com.sanad.platform.crm.party.domain.ContactRepository;
import com.sanad.platform.crm.party.domain.ContactRepository.ContactRecord;
import com.sanad.platform.crm.party.domain.ContactRepository.CreateContactCommand;
import com.sanad.platform.crm.party.domain.ContactRepository.UpdateContactCommand;
import com.sanad.platform.crm.party.infrastructure.JdbcContactRepository;
import com.sanad.platform.security.rls.TenantRlsDataSource;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Task C6-A-R1 — PostgreSQL Direct mixed-patch atomicity certification.
 *
 * <p>Proves that {@link ContactUseCases#update} — the A1 PATCH adapter —
 * executes a mixed owner + ordinary update through the REAL production
 * Spring transaction proxy + {@link TenantRlsDataSource} +
 * {@link SecurityContextHolder} tenant path, and that a failure injected
 * AFTER the real ordinary SQL has executed causes the ENTIRE transaction
 * to roll back (canonical transfer + participant normalization + owner
 * UPDATE + previous-owner WATCHER + ordinary field UPDATE).</p>
 *
 * <h3>Test matrix</h3>
 * <ul>
 *   <li>1. Spring AOP proxy exists on ContactUseCases + ContactTransferUseCases.</li>
 *   <li>2. Mixed success: owner=A→B + givenName change → version N+2, USER_A=WATCHER.</li>
 *   <li>3. Genuine rollback: failure AFTER real ordinary SQL → everything restored.</li>
 * </ul>
 */
@DisplayName("Task C6-A-R1 — Mixed contact patch atomicity (PostgreSQL Direct)")
class ContactOwnerPatchCanonicalizationPostgresTest {

    private static final UUID TENANT_A = UUID.fromString("c6a10000-0000-4000-8000-00000000a001");
    private static final UUID USER_A = UUID.fromString("c6a10000-0000-4000-8000-00000000a002");
    private static final UUID USER_B = UUID.fromString("c6a10000-0000-4000-8000-00000000b002");
    private static final UUID ACTOR_ID = UUID.fromString("c6a10000-0000-4000-8000-00000000d001");
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-23T12:00:00Z");

    private static DriverManagerDataSource rawDataSource;
    private static NamedParameterJdbcTemplate rawJdbc;
    private static TransactionTemplate rawTransactions;
    private static TenantRlsDataSource tenantRlsDataSource;
    private static NamedParameterJdbcTemplate tenantJdbc;
    private static JdbcContactRepository realContactRepo;
    private static JdbcEntityParticipantRepository realParticipantRepo;
    private static RecordingFailingContactRepository recordingRepo;
    /** RAW repos — use rawJdbc for fixture/verification ONLY. */
    private static JdbcContactRepository rawContactRepo;
    private static JdbcEntityParticipantRepository rawParticipantRepo;
    private static AnnotationConfigApplicationContext ctx;

    @BeforeAll
    static void setup() {
        boolean ok;
        try {
            ok = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "ContactOwnerPatchCanonicalizationPostgresTest");
        } catch (Throwable ignored) {
            ok = false;
        }
        Assumptions.assumeTrue(ok, "PostgreSQL Direct required");
        MigrationTestSchemaSupport.ensureDatabase(
                System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""));
        Flyway flyway = Flyway.configure()
                .dataSource(MigrationTestSchemaSupport.getIsolatedJdbcUrl(
                                System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad")),
                        System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"),
                        System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""))
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .javaMigrations(new V15__seed_rbac_roles_and_capabilities())
                .cleanDisabled(false).validateOnMigrate(true).load();
                // Self-sufficiency: always start from a canonical clean state so the
                // shared test_migration history never depends on prior test order.
                flyway.clean();
                flyway.migrate();

        rawDataSource = new DriverManagerDataSource(
                MigrationTestSchemaSupport.getIsolatedJdbcUrl(
                        System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad")),
                System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""));
        rawJdbc = new NamedParameterJdbcTemplate(rawDataSource);
        rawTransactions = new TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(rawDataSource));

        tenantRlsDataSource = new TenantRlsDataSource(rawDataSource);
        tenantJdbc = new NamedParameterJdbcTemplate(tenantRlsDataSource);
        realContactRepo = new JdbcContactRepository(tenantJdbc);
        realParticipantRepo = new JdbcEntityParticipantRepository(tenantJdbc);
        recordingRepo = new RecordingFailingContactRepository(realContactRepo);
        rawContactRepo = new JdbcContactRepository(rawJdbc);
        rawParticipantRepo = new JdbcEntityParticipantRepository(rawJdbc);

        ctx = new AnnotationConfigApplicationContext();
        ctx.getBeanFactory().registerSingleton("tenantRlsDataSource", tenantRlsDataSource);
        ctx.getBeanFactory().registerSingleton("tenantJdbc", tenantJdbc);
        ctx.getBeanFactory().registerSingleton("contactRepository", recordingRepo);
        ctx.getBeanFactory().registerSingleton("participantRepository", realParticipantRepo);
        ctx.register(C6AR1TestConfig.class);
        ctx.refresh();
    }

    @Configuration
    @EnableTransactionManagement
    static class C6AR1TestConfig {
        @Bean
        public PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource);
        }

        @Bean
        public RecipientEligibilityPort recipientEligibilityPort() {
            return (tenantId, userId, orgId, cap) ->
                    new EligibilityDecision(true, "ELIGIBLE_STUB");
        }

        @Bean
        public CollaborationMembershipService membershipService(
                EntityParticipantRepository participantRepository,
                RecipientEligibilityPort eligibility) {
            return new CollaborationMembershipService(participantRepository, eligibility);
        }

        @Bean
        public ContactTransferUseCases contactTransferUseCases(
                ContactRepository contactRepository,
                CollaborationMembershipService membershipService,
                RecipientEligibilityPort eligibilityPort) {
            return new ContactTransferUseCases(contactRepository, membershipService, eligibilityPort);
        }

        @Bean
        public AuditPort auditPort() {
            return (tenantId, actorId, action, entityType, entityId, change, occurredAt) -> {};
        }

        @Bean
        public TimelineEventPort timelineEventPort() {
            return (tenantId, subjectType, subjectId, eventType, summary,
                    sourceType, sourceId, actorId, occurredAt) -> {};
        }

        @Bean
        public ObjectMapper objectMapper() {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            return mapper;
        }

        @Bean
        public ContactUseCases contactUseCases(
                ContactRepository contactRepository,
                AuditPort auditPort,
                TimelineEventPort timelineEventPort,
                ObjectMapper objectMapper,
                ContactTransferUseCases contactTransferUseCases) {
            return new ContactUseCases(contactRepository, auditPort, timelineEventPort,
                    objectMapper, contactTransferUseCases);
        }
    }

    @BeforeEach
    void seed() {
        rawTransactions.executeWithoutResult(s -> {
            setRawGuc(TENANT_A);
            rawJdbc.update("DELETE FROM crm_entity_participants WHERE tenant_id = :t",
                    new MapSqlParameterSource().addValue("t", TENANT_A));
            rawJdbc.update("DELETE FROM crm_contacts WHERE tenant_id = :t",
                    new MapSqlParameterSource().addValue("t", TENANT_A));
            rawJdbc.update("DELETE FROM users WHERE tenant_id = :t",
                    new MapSqlParameterSource().addValue("t", TENANT_A));
            rawJdbc.update("DELETE FROM tenants WHERE id = :t",
                    new MapSqlParameterSource().addValue("t", TENANT_A));
        });
        ensureTenant(TENANT_A);
        ensureUser(USER_A, TENANT_A);
        ensureUser(USER_B, TENANT_A);
        ensureUser(ACTOR_ID, TENANT_A);
        recordingRepo.reset();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ── 1. Spring AOP proxy verification ──────────────────────────────────

    @Test
    @DisplayName("C6-A-R1.1. ContactUseCases + ContactTransferUseCases are Spring AOP proxies")
    void serviceBeansAreSpringAopProxies() {
        ContactUseCases useCases = ctx.getBean(ContactUseCases.class);
        ContactTransferUseCases transfer = ctx.getBean(ContactTransferUseCases.class);
        assertThat(AopUtils.isAopProxy(useCases))
                .as("ContactUseCases must be a Spring AOP proxy")
                .isTrue();
        assertThat(AopUtils.isAopProxy(transfer))
                .as("ContactTransferUseCases must be a Spring AOP proxy")
                .isTrue();
    }

    // ── 2. Mixed success: owner + givenName → version N+2 ────────────────

    @Test
    @DisplayName("C6-A-R1.2. Mixed success: owner A→B + givenName change → version N+2, USER_A=WATCHER")
    void mixedSuccessProvenViaPostgres() {
        ContactUseCases useCases = ctx.getBean(ContactUseCases.class);
        UUID contactId = seedContact(USER_A);
        long initialVersion = contactVersion(contactId);

        ContactRecord result = withTenantSecurityContext(TENANT_A, () ->
                useCases.update(TENANT_A, ACTOR_ID, contactId,
                        new UpdateContactCommand(
                                null, "After", null, null, null, null, null,
                                USER_B, null),
                        initialVersion));

        assertThat(result.ownerUserId()).isEqualTo(USER_B);
        assertThat(result.givenName()).isEqualTo("After");
        assertThat(result.version()).isEqualTo(initialVersion + 2);

        // USER_B (new owner) must NOT be an active participant.
        assertThat(activeParticipantFor(contactId, USER_B)).isEmpty();
        // USER_A (previous owner) must be exactly one active WATCHER.
        List<EntityParticipant> userAParticipants = activeParticipantsFor(contactId, USER_A);
        assertThat(userAParticipants).hasSize(1);
        assertThat(userAParticipants.get(0).role()).isEqualTo(ParticipantRole.WATCHER);
    }

    // ── 3. Genuine rollback: failure AFTER real ordinary SQL ──────────────

    @Test
    @DisplayName("C6-A-R1.3. Mixed rollback: failure AFTER real ordinary SQL → entire transaction rolls back")
    void mixedRollbackAfterRealOrdinarySql() {
        ContactUseCases useCases = ctx.getBean(ContactUseCases.class);
        UUID contactId = seedContact(USER_A);
        long initialVersion = contactVersion(contactId);

        // Seed USER_B as active COLLABORATOR (must be removed during transfer).
        rawTransactions.executeWithoutResult(s -> {
            setRawGuc(TENANT_A);
            rawParticipantRepo.insert(EntityParticipant.active(
                    UUID.randomUUID(), TENANT_A, CollaborationEntityType.CONTACT, contactId,
                    USER_B, ParticipantRole.COLLABORATOR, ACTOR_ID, OCCURRED_AT));
        });
        Optional<EntityParticipant> originalCollaboratorOpt =
                activeParticipantFor(contactId, USER_B);
        assertThat(originalCollaboratorOpt).isPresent();
        EntityParticipant originalCollaborator = originalCollaboratorOpt.get();
        UUID originalParticipantId = originalCollaborator.id();
        long originalParticipantVersion = originalCollaborator.version();

        // Arm the recording repo to throw AFTER the real ordinary update SQL completes.
        recordingRepo.armFailureAfterNextUpdate();

        // Invoke the mixed update through the real Spring proxy.
        // NO outer TransactionTemplate. NO manual GUC.
        assertThatThrownBy(() ->
                withTenantSecurityContext(TENANT_A, () ->
                        useCases.update(TENANT_A, ACTOR_ID, contactId,
                                new UpdateContactCommand(
                                        null, "After", null, null, null, null, null,
                                        USER_B, null),
                                initialVersion)))
                .isInstanceOf(InjectedOrdinaryUpdateFailure.class);

        // Verify the recording repo observed the real ordinary SQL execution.
        assertThat(recordingRepo.ordinaryUpdateDelegateCalled.get())
                .as("Ordinary update delegate must have been called")
                .isTrue();
        assertThat(recordingRepo.ordinarySqlCompleted.get())
                .as("Ordinary SQL must have completed (real UPDATE executed)")
                .isTrue();

        // Verify ROLLBACK terminal state in a NEW RAW transaction.
        ContactRecord currentContact = readContact(contactId);
        assertThat(currentContact.ownerUserId())
                .as("Contact owner must be restored to USER_A after rollback")
                .isEqualTo(USER_A);
        assertThat(currentContact.givenName())
                .as("Contact givenName must be restored to 'Before' after rollback")
                .isEqualTo("Before");
        assertThat(currentContact.version())
                .as("Contact version must be restored to N after rollback")
                .isEqualTo(initialVersion);

        // USER_B's COLLABORATOR row must be restored.
        Optional<EntityParticipant> restoredParticipant =
                activeParticipantFor(contactId, USER_B);
        assertThat(restoredParticipant)
                .as("USER_B COLLABORATOR must be restored after rollback")
                .isPresent();
        assertThat(restoredParticipant.get().id())
                .as("USER_B participant id must be preserved")
                .isEqualTo(originalParticipantId);
        assertThat(restoredParticipant.get().version())
                .as("USER_B participant version must be preserved")
                .isEqualTo(originalParticipantVersion);
        assertThat(restoredParticipant.get().removedAt())
                .as("USER_B participant removed_at must be NULL")
                .isNull();

        // USER_A must NOT have a WATCHER row.
        assertThat(activeParticipantsFor(contactId, USER_A))
                .as("USER_A must NOT have any active participant after rollback")
                .isEmpty();
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private <T> T withTenantSecurityContext(UUID tenantId, java.util.function.Supplier<T> work) {
        UsernamePasswordAuthenticationToken auth =
                UsernamePasswordAuthenticationToken.authenticated(
                        ACTOR_ID.toString(), "n/a", java.util.List.of());
        auth.setDetails(Map.of(
                "tenant_id", tenantId.toString(),
                "user_id", ACTOR_ID.toString()));
        SecurityContextHolder.getContext().setAuthentication(auth);
        return work.get();
    }

    private static void setRawGuc(UUID t) {
        rawJdbc.queryForObject("SELECT set_config('app.tenant_id', :t, true)",
                new MapSqlParameterSource().addValue("t", t.toString()), String.class);
    }

    private UUID seedContact(UUID ownerId) {
        return rawTransactions.execute(s -> {
            setRawGuc(TENANT_A);
            ContactRecord created = rawContactRepo.create(TENANT_A, ACTOR_ID,
                    new CreateContactCommand(
                            null, "Before", "Doe", null, null, null, null, ownerId, null));
            return created.id();
        });
    }

    private long contactVersion(UUID contactId) {
        return rawTransactions.execute(s -> {
            setRawGuc(TENANT_A);
            return rawJdbc.queryForObject(
                    "SELECT version FROM crm_contacts WHERE id = :id",
                    new MapSqlParameterSource().addValue("id", contactId),
                    Long.class);
        });
    }

    private ContactRecord readContact(UUID contactId) {
        return rawTransactions.execute(s -> {
            setRawGuc(TENANT_A);
            return rawContactRepo.findById(TENANT_A, contactId);
        });
    }

    private List<EntityParticipant> activeParticipantsFor(UUID contactId, UUID userId) {
        return rawTransactions.execute(s -> {
            setRawGuc(TENANT_A);
            return rawParticipantRepo.listActive(TENANT_A, CollaborationEntityType.CONTACT, contactId)
                    .stream()
                    .filter(p -> p.userId().equals(userId))
                    .toList();
        });
    }

    private Optional<EntityParticipant> activeParticipantFor(UUID contactId, UUID userId) {
        return activeParticipantsFor(contactId, userId).stream().findFirst();
    }

    private void ensureTenant(UUID id) {
        rawJdbc.update("""
                INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at)
                VALUES (:id, :name, :sub, 'ACTIVE', NOW(), NOW())
                ON CONFLICT (id) DO UPDATE SET subdomain = EXCLUDED.subdomain, name = EXCLUDED.name,
                                                status = EXCLUDED.status, updated_at = NOW()
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("name", "C6AR1 Tenant " + id)
                .addValue("sub", "c6ar1-" + id));
    }

    private void ensureUser(UUID id, UUID tenant) {
        rawJdbc.update("""
                INSERT INTO users (id, tenant_id, email, display_name, status, password_hash, created_at, updated_at)
                VALUES (:id, :t, :email, :name, 'ACTIVE', 'dummy', NOW(), NOW())
                ON CONFLICT (id) DO NOTHING
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("t", tenant)
                .addValue("email", "c6ar1-" + id + "@snad.test")
                .addValue("name", "C6AR1 User " + id));
    }

    // ── Test-only ContactRepository decorator ─────────────────────────────

    /**
     * Delegates all operations to the real {@link JdbcContactRepository}.
     * When armed, throws {@link InjectedOrdinaryUpdateFailure} AFTER
     * {@code update(...)} returns (i.e., AFTER the real SQL has executed).
     */
    static class RecordingFailingContactRepository implements ContactRepository {
        private final ContactRepository delegate;
        final AtomicBoolean ordinaryUpdateDelegateCalled = new AtomicBoolean(false);
        final AtomicBoolean ordinarySqlCompleted = new AtomicBoolean(false);
        private final AtomicBoolean failAfterNextUpdate = new AtomicBoolean(false);

        RecordingFailingContactRepository(ContactRepository delegate) {
            this.delegate = delegate;
        }

        void reset() {
            ordinaryUpdateDelegateCalled.set(false);
            ordinarySqlCompleted.set(false);
            failAfterNextUpdate.set(false);
        }

        void armFailureAfterNextUpdate() {
            failAfterNextUpdate.set(true);
        }

        @Override
        public ContactRecord update(UUID tenantId, UUID actorId, UUID contactId,
                                     UpdateContactCommand command, long expectedVersion) {
            ordinaryUpdateDelegateCalled.set(true);
            ContactRecord result = delegate.update(tenantId, actorId, contactId, command, expectedVersion);
            ordinarySqlCompleted.set(true);
            if (failAfterNextUpdate.compareAndSet(true, false)) {
                throw new InjectedOrdinaryUpdateFailure(
                        "injected failure after real ordinary SQL: version=" + result.version()
                                + " givenName=" + result.givenName()
                                + " owner=" + result.ownerUserId());
            }
            return result;
        }

        @Override
        public ContactRecord findByIdForUpdate(UUID tenantId, UUID contactId) {
            return delegate.findByIdForUpdate(tenantId, contactId);
        }

        @Override
        public ContactRecord findById(UUID tenantId, UUID contactId) {
            return delegate.findById(tenantId, contactId);
        }

        @Override
        public ContactRecord transferOwner(UUID tenantId, UUID actorId, UUID contactId,
                                             UUID newOwnerUserId, long expectedVersion,
                                             Instant occurredAt) {
            return delegate.transferOwner(tenantId, actorId, contactId,
                    newOwnerUserId, expectedVersion, occurredAt);
        }

        @Override
        public List<ContactRecord> findAll(UUID tenantId, int limit, UUID accountId, String search) {
            return delegate.findAll(tenantId, limit, accountId, search);
        }

        @Override
        public ContactRecord create(UUID tenantId, UUID actorId, CreateContactCommand command) {
            return delegate.create(tenantId, actorId, command);
        }

        @Override
        public ContactRecord archive(UUID tenantId, UUID actorId, UUID contactId, long expectedVersion) {
            return delegate.archive(tenantId, actorId, contactId, expectedVersion);
        }

        @Override
        public ContactRecord restore(UUID tenantId, UUID actorId, UUID contactId, long expectedVersion) {
            return delegate.restore(tenantId, actorId, contactId, expectedVersion);
        }
    }

    static final class InjectedOrdinaryUpdateFailure extends RuntimeException {
        InjectedOrdinaryUpdateFailure(String message) {
            super(message);
        }
    }
}
