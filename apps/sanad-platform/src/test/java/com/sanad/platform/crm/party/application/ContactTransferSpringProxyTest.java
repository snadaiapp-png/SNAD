package com.sanad.platform.crm.party.application;

import com.sanad.platform.crm.collaboration.application.CollaborationMembershipService;
import com.sanad.platform.crm.collaboration.domain.CollaborationEntityType;
import com.sanad.platform.crm.collaboration.domain.EntityParticipant;
import com.sanad.platform.crm.collaboration.domain.EntityParticipantRepository;
import com.sanad.platform.crm.collaboration.domain.ParticipantRole;
import com.sanad.platform.crm.collaboration.domain.RecipientEligibilityPort;
import com.sanad.platform.crm.collaboration.domain.RecipientEligibilityPort.EligibilityDecision;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.crm.party.domain.ContactRepository;
import com.sanad.platform.crm.party.domain.ContactRepository.ContactRecord;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.AopProxy;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task C5-R1 — Real Spring proxy proof that the default overload
 * {@code transferContact(UUID, UUID, UUID, long, UUID, Instant)} is
 * transaction-safe at the Spring AOP proxy boundary.
 *
 * <p>Builds a minimal Spring context containing:</p>
 * <ul>
 *   <li>A stubbed {@link ContactRepository} that records when
 *       {@code findByIdForUpdate} is invoked and (critically) inspects
 *       {@link TransactionSynchronizationManager#isActualTransactionActive()}
 *       at the moment of the call.</li>
 *   <li>Stubbed {@link CollaborationMembershipService}.</li>
 *   <li>Stubbed {@link RecipientEligibilityPort}.</li>
 *   <li>{@link ContactTransferUseCases} as a Spring bean.</li>
 *   <li>{@link DataSource} (PostgreSQL Direct) + {@link PlatformTransactionManager}.</li>
 *   <li>{@link EnableTransactionManagement} so Spring weaves the proxy.</li>
 * </ul>
 *
 * <p>The test obtains the bean from Spring (so it IS a proxy) and invokes
 * the DEFAULT overload from OUTSIDE the proxy. At the first dependency
 * boundary ({@code ContactRepository.findByIdForUpdate}), the stub asserts
 * that a transaction is active. This proves Spring annotation interception
 * is occurring.</p>
 *
 * <p>RED before fix: the default overload has NO @Transactional, so Spring
 * does not intercept it, no transaction starts, and
 * {@code TransactionSynchronizationManager.isActualTransactionActive()}
 * returns false inside the stub. (After fix, the test passes.)</p>
 */
class ContactTransferSpringProxyTest {

    private static final String PG_URL = System.getenv().getOrDefault("SPRING_DATASOURCE_URL",
            "jdbc:postgresql://localhost:5432/sanad");
    private static final String PG_USER = System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String PG_PASS = System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "");

    @BeforeAll
    static void requirePostgreSql() {
        Assumptions.assumeTrue(
                Crm009TestEnvironment.requirePostgreSqlDirectOrSkip("ContactTransferSpringProxyTest"),
                "PostgreSQL Direct required");
    }

    private static final UUID TENANT_ID = UUID.fromString("c5c50000-0000-4000-8000-000000000001");
    private static final UUID CONTACT_ID = UUID.fromString("c5c50000-0000-4000-8000-000000000002");
    private static final UUID USER_A = UUID.fromString("c5c50000-0000-4000-8000-00000000a001");
    private static final UUID USER_B = UUID.fromString("c5c50000-0000-4000-8000-00000000b001");
    private static final UUID ACTOR_ID = UUID.fromString("c5c50000-0000-4000-8000-00000000d001");
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-23T12:00:00Z");

    @Configuration
    @EnableTransactionManagement
    static class TestConfig {
        @Bean
        public DataSource dataSource() {
            // PostgreSQL Direct DataSource — we only need a DataSource for
            // the DataSourceTransactionManager to bind a real connection.
            // H2 was removed from the test classpath: PostgreSQL is the only
            // acceptance database.
            return new DriverManagerDataSource(PG_URL, PG_USER, PG_PASS);
        }

        @Bean
        public PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource);
        }

        @Bean
        public RecordingContactRepository contactRepository() {
            return new RecordingContactRepository();
        }

        @Bean
        public CollaborationMembershipService membershipService(EntityParticipantRepository repo,
                                                                 RecipientEligibilityPort eligibility) {
            return new CollaborationMembershipService(repo, eligibility);
        }

        @Bean
        public EntityParticipantRepository entityParticipantRepository() {
            return new NoopEntityParticipantRepository();
        }

        @Bean
        public RecipientEligibilityPort recipientEligibilityPort() {
            return (tenantId, userId, orgId, cap) ->
                    new EligibilityDecision(true, "ELIGIBLE_STUB");
        }

        @Bean
        public ContactTransferUseCases contactTransferUseCases(
                ContactRepository contactRepository,
                CollaborationMembershipService membershipService,
                RecipientEligibilityPort eligibilityPort) {
            return new ContactTransferUseCases(contactRepository, membershipService, eligibilityPort);
        }
    }

    /**
     * Recording stub that captures whether a Spring-managed transaction
     * was active at the moment {@code findByIdForUpdate} was invoked.
     * The actual Contact row contents are returned as a fixed active record.
     */
    static class RecordingContactRepository implements ContactRepository {
        final AtomicBoolean transactionActiveAtFindByIdForUpdate = new AtomicBoolean(false);
        boolean wasFindByIdForUpdateInvoked = false;

        @Override
        public ContactRecord findByIdForUpdate(UUID tenantId, UUID contactId) {
            wasFindByIdForUpdateInvoked = true;
            transactionActiveAtFindByIdForUpdate.set(
                    TransactionSynchronizationManager.isActualTransactionActive());
            return new ContactRecord(
                    contactId, 0L, null, "Jane", "Doe", "Jane Doe",
                    null, null, null, null, null,
                    "ACTIVE", USER_A, "UNKNOWN", OCCURRED_AT, OCCURRED_AT);
        }

        @Override
        public ContactRecord findById(UUID tenantId, UUID contactId) {
            return findByIdForUpdate(tenantId, contactId);
        }

        @Override
        public ContactRecord transferOwner(UUID tenantId, UUID actorId, UUID contactId,
                                             UUID newOwnerUserId, long expectedVersion,
                                             Instant occurredAt) {
            return new ContactRecord(
                    contactId, 1L, null, "Jane", "Doe", "Jane Doe",
                    null, null, null, null, null,
                    "ACTIVE", newOwnerUserId, "UNKNOWN", OCCURRED_AT, OCCURRED_AT);
        }

        @Override
        public List<ContactRecord> findAll(UUID tenantId, int limit, UUID accountId, String search) {
            return List.of();
        }

        @Override
        public ContactRecord create(UUID tenantId, UUID actorId, CreateContactCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ContactRecord update(UUID tenantId, UUID actorId, UUID contactId,
                                     UpdateContactCommand command, long expectedVersion) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ContactRecord archive(UUID tenantId, UUID actorId, UUID contactId, long expectedVersion) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ContactRecord restore(UUID tenantId, UUID actorId, UUID contactId, long expectedVersion) {
            throw new UnsupportedOperationException();
        }
    }

    /** No-op repository — we only need the type for the membership bean. */
    static class NoopEntityParticipantRepository implements EntityParticipantRepository {
        @Override
        public EntityParticipant insert(EntityParticipant participant) {
            // Return the participant unchanged — the C5-R1 proxy test only
            // needs the insert to succeed so the transfer completes; we are
            // not asserting on participant rows in this test.
            return participant;
        }

        @Override
        public Optional<EntityParticipant> findActive(UUID tenantId, CollaborationEntityType entityType,
                                                         UUID entityId, UUID userId, ParticipantRole role) {
            return Optional.empty();
        }

        @Override
        public Optional<EntityParticipant> findById(UUID tenantId, UUID participantId) {
            return Optional.empty();
        }

        @Override
        public List<EntityParticipant> listActive(UUID tenantId, CollaborationEntityType entityType,
                                                   UUID entityId) {
            return List.of();
        }

        @Override
        public boolean markRemoved(UUID tenantId, UUID participantId, long expectedVersion,
                                    UUID removedByUserId, Instant removedAt) {
            return false;
        }
    }

    private AnnotationConfigApplicationContext ctx;
    private ContactTransferUseCases service;
    private RecordingContactRepository recordingRepo;

    @BeforeEach
    void setUp() {
        ctx = new AnnotationConfigApplicationContext(TestConfig.class);
        service = ctx.getBean(ContactTransferUseCases.class);
        recordingRepo = ctx.getBean(RecordingContactRepository.class);
    }

    @Test
    @DisplayName("C5-R1. ContactTransferUseCases bean is a Spring AOP proxy")
    void serviceIsSpringAopProxy() {
        assertThat(AopUtils.isAopProxy(service))
                .as("ContactTransferUseCases bean must be a Spring AOP proxy so @Transactional "
                        + "annotation interception actually fires on external invocation")
                .isTrue();
    }

    @Test
    @DisplayName("C5-R1. Default overload invocation enters a Spring transaction at findByIdForUpdate")
    void defaultOverloadInvocationIsTransactionalAtProxyBoundary() {
        // Invoke the DEFAULT overload from OUTSIDE the proxy.
        // No TransactionTemplate. No manually-created transaction.
        // Spring's @Transactional interceptor (if the annotation is present)
        // is responsible for starting the transaction.
        service.transferContact(TENANT_ID, CONTACT_ID, USER_B, 0L, ACTOR_ID, OCCURRED_AT);

        assertThat(recordingRepo.wasFindByIdForUpdateInvoked)
                .as("findByIdForUpdate must be invoked during default overload execution")
                .isTrue();
        assertThat(recordingRepo.transactionActiveAtFindByIdForUpdate.get())
                .as("Spring transaction must be active at findByIdForUpdate boundary when the "
                        + "default overload is invoked externally — proves @Transactional "
                        + "annotation is intercepted by the Spring AOP proxy on the default "
                        + "entrypoint (not just the inner command overload).")
                .isTrue();
    }
}
