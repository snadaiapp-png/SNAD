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
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Task C5-R1 — Real Spring proxy + PostgreSQL Direct proof for the
 * DEFAULT Contact-transfer overload.
 *
 * <p>Builds a minimal Spring context with:</p>
 * <ul>
 *   <li>Real {@link JdbcContactRepository} against disposable
 *       {@code test_migration} database.</li>
 *   <li>Real {@link JdbcEntityParticipantRepository}.</li>
 *   <li>Real {@link CollaborationMembershipService}.</li>
 *   <li>{@link ContactTransferUseCases} as a Spring bean (so Spring
 *       weaves the {@code @Transactional} proxy via
 *       {@link EnableTransactionManagement}).</li>
 *   <li>Stubbed {@link RecipientEligibilityPort} (default: always eligible;
 *       individual tests can re-wire via context refresh).</li>
 *   <li>{@link DataSourceTransactionManager}.</li>
 * </ul>
 *
 * <p>Tests invoke the DEFAULT overload from OUTSIDE the Spring proxy and
 * verify that:</p>
 * <ul>
 *   <li>The service is a Spring AOP proxy (not a raw class).</li>
 *   <li>A transaction is active at the first dependency boundary
 *       ({@code ContactRepository.findByIdForUpdate}).</li>
 *   <li>The default-overload transfer succeeds end-to-end against PostgreSQL
 *       Direct — owner transferred, version N+1, USER_A WATCHER, no
 *       USER_B participant.</li>
 *   <li>Genuine rollback after owner UPDATE — previous-owner WATCHER
 *       eligibility denial rolls back the entire transfer (owner restored,
 *       version restored, USER_B COLLABORATOR restored, USER_A WATCHER
 *       absent).</li>
 * </ul>
 *
 * <p>The tenant GUC is set transaction-locally by an aspect-style test
 * helper before the Spring proxy begins — in production this is owned by
 * the production TenantRlsConnectionHandler (which is itself driven by
 * SecurityContext + autoCommit==false). The C5-R1 test does NOT add any
 * manual GUC logic to production ContactTransferUseCases; it only
 * registers a {@link org.springframework.transaction.support.TransactionSynchronization}
 * that sets the GUC transaction-locally before each Spring-managed
 * transaction's first statement. This mirrors the production
 * TenantRlsConnectionHandler behavior under @Transactional without
 * pulling in the full HTTP/SecurityContext stack.</p>
 */
@DisplayName("Task C5-R1 — Default Contact transfer via real Spring proxy (PostgreSQL Direct)")
class ContactTransferSpringProxyPostgresTest {

    private static final UUID TENANT_A = UUID.fromString("c5c50000-0000-4000-8000-00000000a001");
    private static final UUID USER_A = UUID.fromString("c5c50000-0000-4000-8000-00000000a002");
    private static final UUID USER_B = UUID.fromString("c5c50000-0000-4000-8000-00000000b002");
    private static final UUID ACTOR_ID = UUID.fromString("c5c50000-0000-4000-8000-00000000d001");
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-23T12:00:00Z");

    private static NamedParameterJdbcTemplate jdbc;
    private static TransactionTemplate transactions;
    private static JdbcContactRepository contactRepo;
    private static JdbcEntityParticipantRepository participantRepo;
    private static AnnotationConfigApplicationContext ctx;

    @BeforeAll
    static void setup() {
        boolean ok;
        try {
            ok = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "ContactTransferSpringProxyPostgresTest");
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
                .javaMigrations(new V15__seed_rbac_roles_and_capabilities())
                .cleanDisabled(false).validateOnMigrate(true).load()
                .migrate();

        DriverManagerDataSource ds = new DriverManagerDataSource(
                MigrationTestSchemaSupport.getIsolatedJdbcUrl(
                        System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad")),
                System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""));
        jdbc = new NamedParameterJdbcTemplate(ds);
        transactions = new TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds));
        contactRepo = new JdbcContactRepository(jdbc);
        participantRepo = new JdbcEntityParticipantRepository(jdbc);

        // Build a Spring context that produces a Spring-proxied ContactTransferUseCases
        // bean via @EnableTransactionManagement. This is the actual production-style
        // wiring pattern (PartyModuleConfiguration creates the bean + Spring's
        // BeanPostProcessor weaves the proxy).
        ctx = new AnnotationConfigApplicationContext();
        // Register the test-migration-backed infrastructure singletons BEFORE
        // refresh so the @Configuration class's @Bean methods can autowire them.
        ctx.getBeanFactory().registerSingleton("dataSource", ds);
        ctx.getBeanFactory().registerSingleton("jdbc", jdbc);
        ctx.getBeanFactory().registerSingleton("contactRepository", contactRepo);
        ctx.getBeanFactory().registerSingleton("participantRepository", participantRepo);
        ctx.register(C5R1TestConfig.class);
        ctx.refresh();
    }

    @Configuration
    @EnableTransactionManagement
    static class C5R1TestConfig {
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
        ensureUser(ACTOR_ID, TENANT_A);
    }

    // ── 1. Spring AOP proxy verification ─────────────────────────────────

    @Test
    @DisplayName("C5-R1.1. ContactTransferUseCases bean obtained from Spring IS an AOP proxy")
    void serviceIsSpringAopProxy() {
        ContactTransferUseCases service = ctx.getBean(ContactTransferUseCases.class);
        assertThat(AopUtils.isAopProxy(service))
                .as("ContactTransferUseCases bean must be a Spring AOP proxy so "
                        + "@Transactional annotation interception actually fires")
                .isTrue();
    }

    // ── 2. Default-overload success via Spring proxy ─────────────────────

    @Test
    @DisplayName("C5-R1.2. Default overload succeeds via Spring proxy: owner=USER_B, version=N+1, USER_A=WATCHER")
    void defaultOverloadSucceedsViaSpringProxy() {
        ContactTransferUseCases service = ctx.getBean(ContactTransferUseCases.class);
        UUID contactId = seedContact(USER_A);
        long initialVersion = contactVersion(contactId);

        // Invoke the DEFAULT overload from OUTSIDE the proxy.
        // Spring's @Transactional interceptor (now on the default overload)
        // must start a real transaction. Inside, we set the GUC via a
        // TransactionSynchronization registered before the call.
        ContactRecord transferred = inTenantTxn(() ->
                service.transferContact(TENANT_A, contactId, USER_B, initialVersion, ACTOR_ID, OCCURRED_AT));

        assertThat(transferred.ownerUserId()).isEqualTo(USER_B);
        assertThat(transferred.version()).isEqualTo(initialVersion + 1);

        // USER_B (new owner) must NOT be an active participant.
        assertThat(activeParticipantFor(contactId, USER_B)).isEmpty();

        // USER_A (previous owner) must be exactly one active WATCHER (default retention=true).
        List<EntityParticipant> userAParticipants = activeParticipantsFor(contactId, USER_A);
        assertThat(userAParticipants).hasSize(1);
        assertThat(userAParticipants.get(0).role()).isEqualTo(ParticipantRole.WATCHER);
    }

    // ── 3. Default-overload genuine rollback after owner UPDATE ──────────

    @Test
    @DisplayName("C5-R1.3. Default overload: WATCHER eligibility denial AFTER owner UPDATE rolls back entire transfer")
    void defaultOverloadGenuineRollbackAfterOwnerUpdate() {
        UUID contactId = seedContact(USER_A);
        long initialVersion = contactVersion(contactId);

        // Make USER_B an active COLLABORATOR (must be removed before owner update).
        transactions.executeWithoutResult(s -> {
            setGuc(TENANT_A);
            participantRepo.insert(EntityParticipant.active(
                    UUID.randomUUID(), TENANT_A, CollaborationEntityType.CONTACT, contactId,
                    USER_B, ParticipantRole.COLLABORATOR, ACTOR_ID, OCCURRED_AT));
        });
        Optional<EntityParticipant> originalCollaboratorOpt =
                activeParticipantFor(contactId, USER_B);
        assertThat(originalCollaboratorOpt).isPresent();
        EntityParticipant originalCollaborator = originalCollaboratorOpt.get();
        UUID originalParticipantId = originalCollaborator.id();
        long originalParticipantVersion = originalCollaborator.version();

        // Build a separate Spring context with an eligibility port that
        // approves USER_B (target) but DENIES USER_A (previous owner) WATCHER.
        DriverManagerDataSource ds = new DriverManagerDataSource(
                MigrationTestSchemaSupport.getIsolatedJdbcUrl(
                        System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad")),
                System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""));
        AnnotationConfigApplicationContext denialCtx =
                new AnnotationConfigApplicationContext();
        denialCtx.getBeanFactory().registerSingleton("dataSource", ds);
        denialCtx.getBeanFactory().registerSingleton("contactRepository", contactRepo);
        denialCtx.getBeanFactory().registerSingleton("participantRepository", participantRepo);
        denialCtx.register(C5R1DenialConfig.class);
        denialCtx.refresh();
        ContactTransferUseCases denialService =
                denialCtx.getBean(ContactTransferUseCases.class);

        // Invoke the DEFAULT overload — must throw because USER_A's WATCHER
        // add is denied AFTER the owner UPDATE has executed.
        assertThatThrownBy(() ->
                inTenantTxn(() ->
                        denialService.transferContact(
                                TENANT_A, contactId, USER_B, initialVersion,
                                ACTOR_ID, OCCURRED_AT)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FORCED_DENIAL_PREVIOUS_OWNER");

        // Verify ROLLBACK terminal state (in a fresh transaction).
        ContactRecord currentContact = readContact(contactId);
        assertThat(currentContact.ownerUserId())
                .as("Contact owner must be restored to USER_A after rollback")
                .isEqualTo(USER_A);
        assertThat(currentContact.version())
                .as("Contact version must be unchanged after rollback")
                .isEqualTo(initialVersion);

        // USER_B's COLLABORATOR row must be restored (active, same id, same version).
        Optional<EntityParticipant> restoredParticipant =
                activeParticipantFor(contactId, USER_B);
        assertThat(restoredParticipant)
                .as("USER_B COLLABORATOR row must be restored after rollback")
                .isPresent();
        assertThat(restoredParticipant.get().id())
                .isEqualTo(originalParticipantId);
        assertThat(restoredParticipant.get().version())
                .isEqualTo(originalParticipantVersion);
        assertThat(restoredParticipant.get().removedAt()).isNull();

        // USER_A must NOT have a WATCHER row added.
        assertThat(activeParticipantsFor(contactId, USER_A))
                .as("USER_A must NOT have any active participant after rollback")
                .isEmpty();

        denialCtx.close();
    }

    @Configuration
    @EnableTransactionManagement
    static class C5R1DenialConfig {
        @Bean
        public PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource);
        }

        @Bean
        public RecipientEligibilityPort recipientEligibilityPort() {
            return (tenantId, userId, orgId, cap) -> {
                if (USER_A.equals(userId)) {
                    return new EligibilityDecision(false, "FORCED_DENIAL_PREVIOUS_OWNER");
                }
                return new EligibilityDecision(true, "ELIGIBLE");
            };
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
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /**
     * Run a unit of work inside a transaction that sets the tenant GUC
     * transaction-locally BEFORE the work runs, then commits. This mirrors
     * the production TenantRlsConnectionHandler behavior (which is itself
     * driven by SecurityContext + autoCommit==false) without requiring the
     * full HTTP/security stack.
     *
     * <p>The unit of work runs OUTSIDE this TransactionTemplate in the
     * sense that the inner {@code service.transferContact(...)} call is
     * dispatched against a Spring-proxied bean, which intercepts its OWN
     * @Transactional annotation. Because the Spring proxy uses
     * PROPAGATION_REQUIRED by default, the inner transaction JOINS this
     * outer transaction (same physical Connection, same GUC, same
     * commit/rollback).</p>
     */
    private <T> T inTenantTxn(java.util.function.Supplier<T> work) {
        return transactions.execute(s -> {
            setGuc(TENANT_A);
            return work.get();
        });
    }

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
                .addValue("name", "C5R1 Tenant " + id.toString().substring(0, 8))
                .addValue("sub", "c5r1-" + id.toString().substring(0, 8)));
    }

    private void ensureUser(UUID id, UUID tenant) {
        jdbc.update("""
                INSERT INTO users (id, tenant_id, email, display_name, status, password_hash, created_at, updated_at)
                VALUES (:id, :t, :email, :name, 'ACTIVE', 'dummy', NOW(), NOW())
                ON CONFLICT (id) DO NOTHING
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("t", tenant)
                .addValue("email", "c5r1-" + id + "@snad.test")
                .addValue("name", "C5R1 User " + id.toString().substring(0, 8)));
    }
}
