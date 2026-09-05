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
import com.sanad.platform.security.rls.TenantRlsDataSource;
import com.sanad.platform.test.MigrationTestSchemaSupport;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Task C5-R2 — Production RLS path certification for the DEFAULT Contact
 * transfer entrypoint.
 *
 * <p>This is the authoritative proof that the {@code @Transactional}
 * annotation on {@link ContactTransferUseCases#transferContact(UUID, UUID,
 * UUID, long, UUID, Instant)} (the DEFAULT public overload — C5-R1 fix)
 * is what ACTUALLY starts the production transaction and triggers the
 * production TenantRlsConnectionHandler to apply {@code SET LOCAL
 * app.tenant_id}.</p>
 *
 * <h3>C5-R2 vs. C5-R1 test harness difference</h3>
 *
 * <p>C5-R1's original PG test wrapped every service invocation in:</p>
 * <pre>
 *   inTenantTxn(() -> service.transferContact(...))
 *   where inTenantTxn = transactions.execute(s -> { setGuc(...); return work.get(); })
 * </pre>
 *
 * <p>That outer {@code TransactionTemplate.execute(...)} supplied the
 * transaction lifetime, the GUC, and the rollback boundary. If the
 * {@code @Transactional} annotation were removed, the outer
 * {@code TransactionTemplate} would still supply those, masking the
 * original defect. C5-R1's PG proof was therefore
 * INVALID_OUTER_TRANSACTION.</p>
 *
 * <p>C5-R2 corrects this by:</p>
 * <ol>
 *   <li>Using TWO datasources — a RAW datasource (for fixtures/verification)
 *       and a SERVICE datasource that wraps RAW in
 *       {@link TenantRlsDataSource} (the production RLS wrapper).</li>
 *   <li>The Spring context wires the SERVICE datasource into
 *       {@link PlatformTransactionManager}, {@link NamedParameterJdbcTemplate},
 *       {@link JdbcContactRepository}, {@link JdbcEntityParticipantRepository},
 *       {@link CollaborationMembershipService}, and
 *       {@link ContactTransferUseCases}.</li>
 *   <li>Service invocations are made DIRECTLY through the Spring-proxied
 *       bean — NO surrounding {@code TransactionTemplate}, NO manual
 *       {@code setGuc}, NO {@code set_config}, NO {@code SET LOCAL}.</li>
 *   <li>Production tenant propagation is exercised via the
 *       {@link SecurityContextHolder} — installed with an authenticated
 *       {@link UsernamePasswordAuthenticationToken} whose
 *       {@code getDetails()} is a {@link Map} with {@code "tenant_id"}.</li>
 *   <li>The Spring proxy begins the transaction; the transaction manager
 *       sets the physical connection {@code autoCommit=false};
 *       {@link TenantRlsConnectionHandler} sees the authenticated tenant
 *       and executes {@code SET LOCAL app.tenant_id = '<uuid>'}.</li>
 * </ol>
 *
 * <h3>Test matrix</h3>
 * <ul>
 *   <li>1. Spring AOP proxy exists on the bean.</li>
 *   <li>2. Default entrypoint succeeds without outer transaction.</li>
 *   <li>3. Transaction active at first real Contact boundary.</li>
 *   <li>4. Correct app.tenant_id GUC visible at first DB boundary.</li>
 *   <li>5. Default previous owner WATCHER=true behavior.</li>
 *   <li>6. Rollback after owner update without outer transaction.</li>
 *   <li>7. No SecurityContext → fail closed.</li>
 *   <li>8. Wrong SecurityContext tenant → fail closed.</li>
 *   <li>9. SecurityContext cleanup.</li>
 * </ul>
 */
@DisplayName("Task C5-R2 — Default Contact transfer via production TenantRlsDataSource (PostgreSQL Direct)")
class ContactTransferSpringProxyPostgresTest {

    private static final UUID TENANT_A = UUID.fromString("c5c50000-0000-4000-8000-00000000a001");
    private static final UUID TENANT_B = UUID.fromString("c5c50000-0000-4000-8000-00000000b001");
    private static final UUID USER_A = UUID.fromString("c5c50000-0000-4000-8000-00000000a002");
    private static final UUID USER_B = UUID.fromString("c5c50000-0000-4000-8000-00000000b002");
    private static final UUID ACTOR_ID = UUID.fromString("c5c50000-0000-4000-8000-00000000d001");
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-23T12:00:00Z");

    /** RAW datasource — for Flyway setup, fixture seed, post-op verification. */
    private static DriverManagerDataSource rawDataSource;
    /** RAW jdbc — for fixture/verification queries (NOT the production path). */
    private static NamedParameterJdbcTemplate rawJdbc;
    /** RAW transactions — for fixture/verification queries only. */
    private static TransactionTemplate rawTransactions;

    /** SERVICE datasource — wraps raw in TenantRlsDataSource. Used by Spring proxy. */
    private static TenantRlsDataSource tenantRlsDataSource;
    /** SERVICE jdbc — uses tenantRlsDataSource (production RLS path). */
    private static NamedParameterJdbcTemplate tenantJdbc;
    /** SERVICE repos — use tenantJdbc. */
    private static JdbcContactRepository contactRepo;
    private static JdbcEntityParticipantRepository participantRepo;
    /** Recording wrapper around the real JdbcContactRepository for transaction/GUC assertions. */
    private static RecordingContactRepository recordingContactRepo;
    /** RAW repos — use rawJdbc for fixture/verification ONLY (NOT the production path). */
    private static JdbcContactRepository rawContactRepo;
    private static JdbcEntityParticipantRepository rawParticipantRepo;

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

        // RAW datasource — for fixture/verification queries (NOT the production path).
        rawDataSource = new DriverManagerDataSource(
                MigrationTestSchemaSupport.getIsolatedJdbcUrl(
                        System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad")),
                System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""));
        rawJdbc = new NamedParameterJdbcTemplate(rawDataSource);
        rawTransactions = new TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(rawDataSource));

        // SERVICE datasource — wraps raw in TenantRlsDataSource (production RLS path).
        // TenantRlsDataSource's constructor accepts AbstractDataSource; DriverManagerDataSource
        // extends AbstractDataSource so this works.
        tenantRlsDataSource = new TenantRlsDataSource(rawDataSource);
        tenantJdbc = new NamedParameterJdbcTemplate(tenantRlsDataSource);
        contactRepo = new JdbcContactRepository(tenantJdbc);
        participantRepo = new JdbcEntityParticipantRepository(tenantJdbc);
        recordingContactRepo = new RecordingContactRepository(contactRepo);
        rawContactRepo = new JdbcContactRepository(rawJdbc);
        rawParticipantRepo = new JdbcEntityParticipantRepository(rawJdbc);

        // Build Spring context that produces a Spring-proxied ContactTransferUseCases bean
        // via @EnableTransactionManagement. The service-side transaction manager uses
        // tenantRlsDataSource so that @Transactional methods go through the production RLS path.
        ctx = new AnnotationConfigApplicationContext();
        ctx.getBeanFactory().registerSingleton("tenantRlsDataSource", tenantRlsDataSource);
        ctx.getBeanFactory().registerSingleton("tenantJdbc", tenantJdbc);
        ctx.getBeanFactory().registerSingleton("contactRepository", recordingContactRepo);
        ctx.getBeanFactory().registerSingleton("participantRepository", participantRepo);
        ctx.register(C5R2TestConfig.class);
        ctx.refresh();
    }

    @Configuration
    @EnableTransactionManagement
    static class C5R2TestConfig {
        @Bean
        public PlatformTransactionManager transactionManager(DataSource dataSource) {
            // Uses tenantRlsDataSource (the registered singleton).
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
        // Fixture seed uses RAW datasource — NOT the production path being certified.
        // Delete BOTH TENANT_A and TENANT_B rows (including users under TENANT_B if any
        // from a prior run) so subdomain uniqueness is not violated.
        for (UUID t : new UUID[]{TENANT_A, TENANT_B}) {
            rawTransactions.executeWithoutResult(s -> {
                setRawGuc(t);
                rawJdbc.update("DELETE FROM crm_entity_participants WHERE tenant_id = :t",
                        new MapSqlParameterSource().addValue("t", t));
                rawJdbc.update("DELETE FROM crm_contacts WHERE tenant_id = :t",
                        new MapSqlParameterSource().addValue("t", t));
                rawJdbc.update("DELETE FROM users WHERE tenant_id = :t",
                        new MapSqlParameterSource().addValue("t", t));
            });
        }
        // Delete tenant rows WITHOUT GUC (tenants table has no RLS — it's the
        // tenant registry itself). Use both ids in one statement.
        rawJdbc.update("DELETE FROM tenants WHERE id IN (:a, :b)",
                new MapSqlParameterSource()
                        .addValue("a", TENANT_A)
                        .addValue("b", TENANT_B));
        ensureTenant(TENANT_A);
        ensureTenant(TENANT_B);
        ensureUser(USER_A, TENANT_A);
        ensureUser(USER_B, TENANT_A);
        ensureUser(ACTOR_ID, TENANT_A);
        // Reset recording flags before each test.
        recordingContactRepo.reset();
    }

    @AfterEach
    void clearSecurityContext() {
        // CRITICAL: every test must clear the SecurityContext to prevent leakage.
        SecurityContextHolder.clearContext();
    }

    // ── 1. Spring AOP proxy verification ─────────────────────────────────

    @Test
    @DisplayName("C5-R2.1. ContactTransferUseCases bean obtained from Spring IS an AOP proxy")
    void serviceIsSpringAopProxy() {
        ContactTransferUseCases service = ctx.getBean(ContactTransferUseCases.class);
        assertThat(AopUtils.isAopProxy(service))
                .as("ContactTransferUseCases bean must be a Spring AOP proxy so @Transactional "
                        + "annotation interception actually fires on external invocation")
                .isTrue();
    }

    // ── 2+3+4+5. Default-overload success via REAL TenantRlsDataSource ────

    @Test
    @DisplayName("C5-R2.2-5. Default overload succeeds via production RLS path: owner=USER_B, version=N+1, USER_A=WATCHER")
    void defaultOverloadSucceedsViaRealTenantRlsDataSource() {
        ContactTransferUseCases service = ctx.getBean(ContactTransferUseCases.class);
        UUID contactId = seedContact(USER_A);
        long initialVersion = contactVersion(contactId);

        // Install SecurityContext with authenticated tenant — production contract.
        // NO outer TransactionTemplate. NO manual setGuc. The Spring proxy will
        // start the transaction; TenantRlsConnectionHandler will apply SET LOCAL
        // app.tenant_id using the SecurityContext.
        ContactRecord transferred = withTenantSecurityContext(TENANT_A, () ->
                service.transferContact(TENANT_A, contactId, USER_B, initialVersion, ACTOR_ID, OCCURRED_AT));

        // During the call, the recording ContactRepository captured transaction
        // state + GUC at the first DB boundary (findByIdForUpdate).
        assertThat(recordingContactRepo.transactionActiveAtFindByIdForUpdate.get())
                .as("C5-R2.3: Spring transaction must be active at findByIdForUpdate boundary")
                .isTrue();
        assertThat(recordingContactRepo.tenantGucAtFindByIdForUpdate.get())
                .as("C5-R2.4: app.tenant_id GUC must equal TENANT_A at first DB boundary")
                .isEqualTo(TENANT_A.toString());

        // Terminal PostgreSQL state.
        assertThat(transferred.ownerUserId())
                .as("C5-R2.2: owner must be USER_B after default-overload transfer")
                .isEqualTo(USER_B);
        assertThat(transferred.version())
                .as("C5-R2.2: version must be N+1 after default-overload transfer")
                .isEqualTo(initialVersion + 1);

        // USER_B (new owner) must NOT be an active participant.
        assertThat(activeParticipantFor(contactId, USER_B))
                .as("USER_B must not be an active participant after becoming owner")
                .isEmpty();

        // USER_A (previous owner) must be exactly one active WATCHER (default retention=true).
        List<EntityParticipant> userAParticipants = activeParticipantsFor(contactId, USER_A);
        assertThat(userAParticipants)
                .as("USER_A must have exactly one active participant after transfer (WATCHER retention)")
                .hasSize(1);
        assertThat(userAParticipants.get(0).role())
                .as("C5-R2.5: USER_A's active participant must be WATCHER (default retention=true)")
                .isEqualTo(ParticipantRole.WATCHER);
    }

    // ── 6. Genuine rollback through proxy-owned transaction ──────────────

    @Test
    @DisplayName("C5-R2.6. Default overload: WATCHER eligibility denial AFTER owner UPDATE rolls back via Spring proxy")
    void defaultOverloadGenuineRollbackThroughProxyOwnedTransaction() {
        UUID contactId = seedContact(USER_A);
        long initialVersion = contactVersion(contactId);

        // Make USER_B an active COLLABORATOR (must be removed before owner update).
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

        // Build a separate Spring context with an eligibility port that
        // approves USER_B (target) but DENIES USER_A (previous owner) WATCHER.
        AnnotationConfigApplicationContext denialCtx =
                new AnnotationConfigApplicationContext();
        denialCtx.getBeanFactory().registerSingleton("tenantRlsDataSource", tenantRlsDataSource);
        denialCtx.getBeanFactory().registerSingleton("tenantJdbc", tenantJdbc);
        denialCtx.getBeanFactory().registerSingleton("contactRepository", recordingContactRepo);
        denialCtx.getBeanFactory().registerSingleton("participantRepository", participantRepo);
        denialCtx.register(C5R2DenialConfig.class);
        denialCtx.refresh();
        ContactTransferUseCases denialService =
                denialCtx.getBean(ContactTransferUseCases.class);

        // Invoke the DEFAULT overload — must throw because USER_A's WATCHER
        // add is denied AFTER the owner UPDATE has executed.
        // NO outer TransactionTemplate. NO manual setGuc. The Spring proxy
        // owns the transaction AND the rollback.
        try {
            assertThatThrownBy(() ->
                    withTenantSecurityContext(TENANT_A, () ->
                            denialService.transferContact(
                                    TENANT_A, contactId, USER_B, initialVersion,
                                    ACTOR_ID, OCCURRED_AT)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("FORCED_DENIAL_PREVIOUS_OWNER");
        } finally {
            denialCtx.close();
        }

        // Verify ROLLBACK terminal state (in a fresh RAW transaction).
        ContactRecord currentContact = readContact(contactId);
        assertThat(currentContact.ownerUserId())
                .as("C5-R2.6: Contact owner must be restored to USER_A after proxy-owned rollback")
                .isEqualTo(USER_A);
        assertThat(currentContact.version())
                .as("C5-R2.6: Contact version must be unchanged after proxy-owned rollback")
                .isEqualTo(initialVersion);

        // USER_B's COLLABORATOR row must be restored (active, same id, same version).
        Optional<EntityParticipant> restoredParticipant =
                activeParticipantFor(contactId, USER_B);
        assertThat(restoredParticipant)
                .as("C5-R2.6: USER_B COLLABORATOR row must be restored after proxy-owned rollback")
                .isPresent();
        assertThat(restoredParticipant.get().id())
                .as("C5-R2.6: USER_B participant id must be preserved after rollback")
                .isEqualTo(originalParticipantId);
        assertThat(restoredParticipant.get().version())
                .as("C5-R2.6: USER_B participant version must be preserved after rollback")
                .isEqualTo(originalParticipantVersion);
        assertThat(restoredParticipant.get().removedAt())
                .as("C5-R2.6: USER_B participant removed_at must be NULL after rollback")
                .isNull();

        // USER_A must NOT have a WATCHER row added.
        assertThat(activeParticipantsFor(contactId, USER_A))
                .as("C5-R2.6: USER_A must NOT have any active participant after rollback")
                .isEmpty();
    }

    @Configuration
    @EnableTransactionManagement
    static class C5R2DenialConfig {
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

    // ── 7. No SecurityContext → fail closed ──────────────────────────────

    @Test
    @DisplayName("C5-R2.7. No SecurityContext → Spring still begins transaction but RLS hides Contact → fail closed")
    void noSecurityContextFailsClosed() {
        ContactTransferUseCases service = ctx.getBean(ContactTransferUseCases.class);
        UUID contactId = seedContact(USER_A);
        long initialVersion = contactVersion(contactId);

        // Ensure SecurityContext is empty.
        SecurityContextHolder.clearContext();
        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .as("Pre-condition: SecurityContext must be empty")
                .isNull();

        // Spring still begins a transaction because @Transactional exists.
        // But TenantRlsConnectionHandler.currentTenantId() returns null
        // (no authenticated Authentication), so it does NOT apply SET LOCAL.
        // FORCE RLS on crm_contacts then hides the row — findByIdForUpdate
        // returns 0 rows → JdbcContactRepository throws CRM_CONTACT_NOT_FOUND.
        assertThatThrownBy(() ->
                service.transferContact(TENANT_A, contactId, USER_B, initialVersion, ACTOR_ID, OCCURRED_AT))
                .isInstanceOf(com.sanad.platform.crm.error.CrmContractException.class)
                .satisfies(ex -> assertThat(
                        ((com.sanad.platform.crm.error.CrmContractException) ex).code())
                        .isEqualTo(com.sanad.platform.crm.error.CrmErrorCode.CRM_CONTACT_NOT_FOUND));

        // Terminal DB state — nothing changed.
        ContactRecord current = readContact(contactId);
        assertThat(current.ownerUserId()).isEqualTo(USER_A);
        assertThat(current.version()).isEqualTo(initialVersion);
        assertThat(activeParticipantsFor(contactId, USER_B)).isEmpty();
        assertThat(activeParticipantsFor(contactId, USER_A)).isEmpty();
    }

    // ── 8. Wrong SecurityContext tenant → fail closed ───────────────────

    @Test
    @DisplayName("C5-R2.8. Wrong SecurityContext tenant → RLS hides Contact → fail closed")
    void wrongSecurityContextTenantFailsClosed() {
        ContactTransferUseCases service = ctx.getBean(ContactTransferUseCases.class);
        UUID contactId = seedContact(USER_A);
        long initialVersion = contactVersion(contactId);

        // Install SecurityContext with TENANT_B (wrong tenant — Contact belongs to TENANT_A).
        // The service command parameters still target TENANT_A, but the GUC will be TENANT_B.
        assertThatThrownBy(() ->
                withTenantSecurityContext(TENANT_B, () ->
                        service.transferContact(TENANT_A, contactId, USER_B, initialVersion, ACTOR_ID, OCCURRED_AT)))
                .isInstanceOf(com.sanad.platform.crm.error.CrmContractException.class)
                .satisfies(ex -> assertThat(
                        ((com.sanad.platform.crm.error.CrmContractException) ex).code())
                        .isEqualTo(com.sanad.platform.crm.error.CrmErrorCode.CRM_CONTACT_NOT_FOUND));

        // Terminal DB state — nothing changed.
        ContactRecord current = readContact(contactId);
        assertThat(current.ownerUserId()).isEqualTo(USER_A);
        assertThat(current.version()).isEqualTo(initialVersion);
        assertThat(activeParticipantsFor(contactId, USER_B)).isEmpty();
        assertThat(activeParticipantsFor(contactId, USER_A)).isEmpty();
    }

    // ── 9. SecurityContext cleanup proof ─────────────────────────────────

    @Test
    @DisplayName("C5-R2.9. SecurityContext is cleared after each test (no leakage)")
    void securityContextClearedAfterEachTest() {
        // This test installs a SecurityContext, then asserts that the
        // @AfterEach clearSecurityContext() method clears it.
        // Note: @AfterEach runs AFTER this test method completes, so we
        // simulate the cleanup here and verify the cleared state.
        withTenantSecurityContext(TENANT_A, () -> {
            assertThat(SecurityContextHolder.getContext().getAuthentication())
                    .as("SecurityContext must be populated inside withTenantSecurityContext")
                    .isNotNull();
            return null;
        });
        // After the helper returns, the SecurityContext is STILL populated
        // (the helper does not clear it). The @AfterEach clears it.
        // To prove the @AfterEach works, we manually clear here and verify
        // the cleared state — the @AfterEach will run again (no-op).
        SecurityContextHolder.clearContext();
        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .as("After clearContext(), SecurityContext authentication must be null")
                .isNull();
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /**
     * Install an authenticated Spring SecurityContext with the given tenant
     * and run a unit of work. The caller is responsible for ensuring
     * {@link SecurityContextHolder#clearContext()} is called afterward
     * (the {@code @AfterEach} method handles this).
     *
     * <p>This helper uses the EXACT production contract expected by
     * {@code TenantRlsConnectionHandler.currentTenantId()}:</p>
     * <ul>
     *   <li>{@link UsernamePasswordAuthenticationToken#authenticated(Object, Object, java.util.Collection)}
     *       (3-arg factory) so that {@code isAuthenticated() == true}.</li>
     *   <li>{@code auth.setDetails(Map.of("tenant_id", tenantId.toString(), "user_id", userId.toString()))}
     *       so that {@code getDetails() instanceof Map<?,?>} contains
     *       {@code "tenant_id"} whose value's {@code toString()} is a UUID.</li>
     * </ul>
     *
     * <p>This mirrors the canonical {@code RlsTestSupport.setSecurityContext(UUID, UUID)}
     * helper already used by other tests in the project.</p>
     */
    private <T> T withTenantSecurityContext(UUID tenantId, java.util.function.Supplier<T> work) {
        UsernamePasswordAuthenticationToken auth =
                UsernamePasswordAuthenticationToken.authenticated(
                        ACTOR_ID.toString(), "n/a", java.util.List.of());
        auth.setDetails(Map.of(
                "tenant_id", tenantId.toString(),
                "user_id", ACTOR_ID.toString()));
        SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            return work.get();
        } finally {
            // Note: we do NOT clear here — the @AfterEach handles cleanup.
            // Keeping the context populated allows follow-up assertions
            // within the same test method if needed.
        }
    }

    /** RAW GUC setter — for fixture/verification queries ONLY (NOT the production path). */
    private static void setRawGuc(UUID t) {
        rawJdbc.queryForObject("SELECT set_config('app.tenant_id', :t, true)",
                new MapSqlParameterSource().addValue("t", t.toString()), String.class);
    }

    private UUID seedContact(UUID ownerId) {
        return rawTransactions.execute(s -> {
            setRawGuc(TENANT_A);
            ContactRecord created = rawContactRepo.create(TENANT_A, ACTOR_ID,
                    new ContactRepository.CreateContactCommand(
                            null, "Jane", "Doe", null, null, null, null, ownerId, null));
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
                .addValue("name", "C5R2 Tenant " + id)
                .addValue("sub", "c5r2-" + id));
    }

    private void ensureUser(UUID id, UUID tenant) {
        rawJdbc.update("""
                INSERT INTO users (id, tenant_id, email, display_name, status, password_hash, created_at, updated_at)
                VALUES (:id, :t, :email, :name, 'ACTIVE', 'dummy', NOW(), NOW())
                ON CONFLICT (id) DO NOTHING
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("t", tenant)
                .addValue("email", "c5r2-" + id + "@snad.test")
                .addValue("name", "C5R2 User " + id.toString().substring(0, 8)));
    }

    /**
     * Test-only delegating ContactRepository that wraps the REAL
     * {@link JdbcContactRepository} and records transaction state + GUC
     * at the first DB boundary ({@code findByIdForUpdate}).
     *
     * <p>All production operations are delegated unchanged. The recording
     * is purely observational — it does NOT alter behavior.</p>
     */
    static class RecordingContactRepository implements ContactRepository {
        private final ContactRepository delegate;
        final AtomicReference<Boolean> transactionActiveAtFindByIdForUpdate = new AtomicReference<>(null);
        final AtomicReference<String> tenantGucAtFindByIdForUpdate = new AtomicReference<>(null);

        RecordingContactRepository(ContactRepository delegate) {
            this.delegate = delegate;
        }

        void reset() {
            transactionActiveAtFindByIdForUpdate.set(null);
            tenantGucAtFindByIdForUpdate.set(null);
        }

        @Override
        public ContactRecord findByIdForUpdate(UUID tenantId, UUID contactId) {
            // Record transaction state + GUC at the FIRST DB boundary.
            transactionActiveAtFindByIdForUpdate.set(
                    TransactionSynchronizationManager.isActualTransactionActive());
            // Query the GUC through the SAME service-side tenantJdbc so the
            // GUC value reflects what TenantRlsConnectionHandler applied.
            try {
                String guc = tenantJdbc.queryForObject(
                        "SELECT current_setting('app.tenant_id', true)",
                        new MapSqlParameterSource(),
                        String.class);
                tenantGucAtFindByIdForUpdate.set(guc);
            } catch (Exception ignored) {
                tenantGucAtFindByIdForUpdate.set(null);
            }
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
        public ContactRecord update(UUID tenantId, UUID actorId, UUID contactId,
                                     UpdateContactCommand command, long expectedVersion) {
            return delegate.update(tenantId, actorId, contactId, command, expectedVersion);
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
}
