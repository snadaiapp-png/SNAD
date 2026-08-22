package com.sanad.platform.crm.collaboration.application;

import com.sanad.platform.crm.collaboration.domain.EntityParticipantRepository;
import com.sanad.platform.crm.collaboration.domain.RecipientEligibilityPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring wiring for the CRM collaboration module.
 *
 * <p>Owns the {@link CollaborationMembershipService} bean — Task 7's service
 * is intentionally a plain class (no {@code @Service} / {@code @Component})
 * so this configuration is the single authoritative wiring point.
 *
 * <p>Wiring boundary:
 * <ul>
 *   <li>Injects ONLY {@link EntityParticipantRepository} and
 *       {@link RecipientEligibilityPort} into the membership bean.</li>
 *   <li>Does NOT inject {@code TimelineEventPort}, {@code AuditPort},
 *       {@code CrmEventOutboxPort}, or {@code CapabilityEvaluationService}
 *       into the membership bean — those ports are owned by higher-level
 *       command orchestrators in a later module.</li>
 *   <li>Does NOT use {@code @ComponentScan} / {@code @Enable...} / controller
 *       scanning — wiring is explicit.</li>
 *   <li>Does NOT contain business mutation logic.</li>
 *   <li>Does NOT reference {@code JdbcTemplate},
 *       {@code NamedParameterJdbcTemplate}, {@code SecurityContextHolder},
 *       or {@code TenantRlsTransactionContext}.</li>
 * </ul>
 */
@Configuration
public class CollaborationModuleConfiguration {

    @Bean
    CollaborationMembershipService collaborationMembershipService(
            EntityParticipantRepository participants,
            RecipientEligibilityPort eligibility) {
        return new CollaborationMembershipService(participants, eligibility);
    }
}
