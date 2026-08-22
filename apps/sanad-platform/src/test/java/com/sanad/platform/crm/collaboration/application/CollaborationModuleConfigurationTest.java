package com.sanad.platform.crm.collaboration.application;

import com.sanad.platform.crm.collaboration.domain.EntityParticipant;
import com.sanad.platform.crm.collaboration.domain.EntityParticipantRepository;
import com.sanad.platform.crm.collaboration.domain.RecipientEligibilityPort;
import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.CrmEventOutboxPort;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 8 — Module wiring proof for {@link CollaborationModuleConfiguration}.
 *
 * <p>Uses {@link ApplicationContextRunner} to load a minimal Spring context
 * that contains ONLY the collaboration module configuration plus test
 * doubles for the dependency ports. This avoids booting unrelated
 * infrastructure (no DB, no security filters, no RBAC bootstrap).
 *
 * <p>Three independent proofs:
 * <ol>
 *   <li>BEFORE the configuration exists, the runner WITHOUT
 *       {@link CollaborationModuleConfiguration} reports zero
 *       {@link CollaborationMembershipService} beans.</li>
 *   <li>WITH the configuration, the runner reports EXACTLY ONE
 *       {@link CollaborationMembershipService} bean.</li>
 *   <li>No bean from {@code com.sanad.platform.crm.collaboration} is a
 *       REST controller (no class annotated {@code @RestController} /
 *       {@code @Controller}).</li>
 * </ol>
 *
 * <p>The test deliberately inspects actual bean classes / annotations
 * (not bean names) so that a same-named controller bean from another
 * package cannot fool the assertion.
 */
@DisplayName("Task 8 — Collaboration module wiring (ApplicationContextRunner)")
class CollaborationModuleConfigurationTest {

    private static final String PACKAGE_COLLAB = "com.sanad.platform.crm.collaboration";

    private final ApplicationContextRunner runner = new ApplicationContextRunner();

    @Test
    @DisplayName("membership service bean count = 0 BEFORE configuration")
    void noMembershipServiceBeanBeforeConfiguration() {
        runner
                .withBean(EntityParticipantRepository.class, () -> new NoopParticipantRepository())
                .withBean(RecipientEligibilityPort.class, () -> new NoopEligibilityPort())
                .run(context -> {
                    Map<String, CollaborationMembershipService> beans =
                            context.getBeansOfType(CollaborationMembershipService.class);
                    assertThat(beans)
                            .as("membership service bean count must be 0 BEFORE Task 8 configuration")
                            .isEmpty();
                });
    }

    @Test
    @DisplayName("membership service bean count = 1 AFTER configuration (single @Bean)")
    void exactlyOneMembershipServiceBeanAfterConfiguration() {
        runner
                .withUserConfiguration(CollaborationModuleConfiguration.class)
                .withBean(EntityParticipantRepository.class, () -> new NoopParticipantRepository())
                .withBean(RecipientEligibilityPort.class, () -> new NoopEligibilityPort())
                .run(context -> {
                    assertThat(context).hasSingleBean(CollaborationMembershipService.class);
                    Map<String, CollaborationMembershipService> beans =
                            context.getBeansOfType(CollaborationMembershipService.class);
                    assertThat(beans).hasSize(1);
                    CollaborationMembershipService bean =
                            context.getBean(CollaborationMembershipService.class);
                    assertThat(bean)
                            .as("bean must be a real CollaborationMembershipService instance, not a proxy")
                            .isExactlyInstanceOf(CollaborationMembershipService.class);
                });
    }

    @Test
    @DisplayName("CollaborationMembershipService class is NOT annotated @Service / @Component")
    void membershipServiceClassIsNotComponentAnnotated() {
        Class<?> clazz = CollaborationMembershipService.class;
        assertThat(clazz.isAnnotationPresent(org.springframework.stereotype.Service.class))
                .as("@Service must NOT be present on CollaborationMembershipService")
                .isFalse();
        assertThat(clazz.isAnnotationPresent(org.springframework.stereotype.Component.class))
                .as("@Component must NOT be present on CollaborationMembershipService")
                .isFalse();
        assertThat(clazz.isAnnotationPresent(org.springframework.context.annotation.Configuration.class))
                .as("@Configuration must NOT be present on CollaborationMembershipService")
                .isFalse();
    }

    @Test
    @DisplayName("no bean from com.sanad.platform.crm.collaboration is a REST controller")
    void noCollaborationControllerBeanIsRegistered() {
        runner
                .withUserConfiguration(CollaborationModuleConfiguration.class)
                .withBean(EntityParticipantRepository.class, () -> new NoopParticipantRepository())
                .withBean(RecipientEligibilityPort.class, () -> new NoopEligibilityPort())
                .run(context -> {
                    int controllerCount = 0;
                    for (String name : context.getBeanDefinitionNames()) {
                        if (context.isTypeMatch(name, Object.class)) {
                            Class<?> type = context.getType(name);
                            if (type == null) {
                                continue;
                            }
                            if (type.getName().startsWith(PACKAGE_COLLAB)
                                    && (type.isAnnotationPresent(
                                            org.springframework.stereotype.Controller.class)
                                        || type.isAnnotationPresent(
                                            org.springframework.web.bind.annotation.RestController.class))) {
                                controllerCount++;
                            }
                        }
                    }
                    assertThat(controllerCount)
                            .as("no bean from com.sanad.platform.crm.collaboration may be a REST controller")
                            .isZero();
                });
    }

    @Test
    @DisplayName("configuration does NOT inject TimelineEventPort / AuditPort / CrmEventOutboxPort / CapabilityEvaluationService")
    void configurationDoesNotInjectUnauthorizedPorts() {
        runner
                .withUserConfiguration(CollaborationModuleConfiguration.class)
                .withBean(EntityParticipantRepository.class, () -> new NoopParticipantRepository())
                .withBean(RecipientEligibilityPort.class, () -> new NoopEligibilityPort())
                .run(context -> {
                    // The membership bean is the only collaboration bean — so
                    // none of these unauthorized ports should be present.
                    assertThat(context)
                            .as("TimelineEventPort must NOT be wired by CollaborationModuleConfiguration")
                            .doesNotHaveBean(TimelineEventPort.class);
                    assertThat(context)
                            .as("AuditPort must NOT be wired by CollaborationModuleConfiguration")
                            .doesNotHaveBean(AuditPort.class);
                    assertThat(context)
                            .as("CrmEventOutboxPort must NOT be wired by CollaborationModuleConfiguration")
                            .doesNotHaveBean(CrmEventOutboxPort.class);
                    assertThat(context)
                            .as("CapabilityEvaluationService must NOT be wired by CollaborationModuleConfiguration")
                            .doesNotHaveBean(com.sanad.platform.access.evaluation.CapabilityEvaluationService.class);
                });
    }

    // ---------- test doubles ----------

    /** In-memory no-op repository — sufficient for wiring proof only. */
    static final class NoopParticipantRepository implements EntityParticipantRepository {
        @Override
        public EntityParticipant insert(EntityParticipant participant) {
            return participant;
        }

        @Override
        public java.util.Optional<EntityParticipant> findActive(
                java.util.UUID tenantId,
                com.sanad.platform.crm.collaboration.domain.CollaborationEntityType entityType,
                java.util.UUID entityId,
                java.util.UUID userId,
                com.sanad.platform.crm.collaboration.domain.ParticipantRole role) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<EntityParticipant> findById(
                java.util.UUID tenantId, java.util.UUID participantId) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.List<EntityParticipant> listActive(
                java.util.UUID tenantId,
                com.sanad.platform.crm.collaboration.domain.CollaborationEntityType entityType,
                java.util.UUID entityId) {
            return java.util.List.of();
        }

        @Override
        public boolean markRemoved(
                java.util.UUID tenantId, java.util.UUID participantId,
                long expectedVersion, java.util.UUID removedByUserId,
                java.time.Instant removedAt) {
            return false;
        }
    }

    /** Always-eligible test double — sufficient for wiring proof only. */
    static final class NoopEligibilityPort implements RecipientEligibilityPort {
        @Override
        public EligibilityDecision evaluate(
                java.util.UUID tenantId, java.util.UUID userId,
                java.util.UUID organizationId, String requiredCapability) {
            return new EligibilityDecision(true, "ELIGIBLE");
        }
    }
}
