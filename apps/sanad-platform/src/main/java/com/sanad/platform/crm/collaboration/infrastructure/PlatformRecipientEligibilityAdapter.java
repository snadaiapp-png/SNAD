package com.sanad.platform.crm.collaboration.infrastructure;

import com.sanad.platform.access.AccessDecisionResponse;
import com.sanad.platform.access.evaluation.CapabilityEvaluationService;
import com.sanad.platform.crm.collaboration.domain.RecipientEligibilityPort;
import com.sanad.platform.crm.ownership.domain.OwnershipUserValidationPort;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

/**
 * Platform adapter that composes {@link OwnershipUserValidationPort} with
 * {@link CapabilityEvaluationService} to produce a
 * {@link RecipientEligibilityPort.EligibilityDecision}.
 *
 * <p>Security boundary:
 * <ul>
 *   <li>Uses ONLY {@link OwnershipUserValidationPort#isActiveUser(UUID, UUID)}
 *       — NEVER uses {@code lockActiveUser} (which writes a row lock for
 *       ownership use cases).</li>
 *   <li>Does NOT mutate RBAC state — no UserRoleGrantService, RoleService,
 *       RoleCapabilityService, or AccessCapabilityService calls.</li>
 *   <li>Does NOT read SecurityContextHolder or mutate the tenant GUC.</li>
 *   <li>Does NOT use JdbcTemplate / NamedParameterJdbcTemplate.</li>
 *   <li>Passes {@code requiredCapability} unchanged to CapabilityEvaluationService
 *       — does not normalize it locally.</li>
 * </ul>
 *
 * <p>Decision algorithm:
 * <ol>
 *   <li>Validate {@code tenantId}, {@code userId} non-null.</li>
 *   <li>Validate {@code requiredCapability} non-null + non-blank.</li>
 *   <li>If user is inactive → {@code (false, "USER_NOT_ACTIVE_IN_TENANT")}
 *       without invoking RBAC.</li>
 *   <li>Delegate to {@link CapabilityEvaluationService#evaluate}.</li>
 *   <li>If {@code allowed=true} → {@code (true, "ELIGIBLE")}.</li>
 *   <li>If {@code allowed=false} and reason is non-null →
 *       {@code (false, reason)}.</li>
 *   <li>If {@code allowed=false} and reason is null →
 *       {@code (false, "RBAC_DENIED")} (failsafe).</li>
 * </ol>
 */
@Component
public class PlatformRecipientEligibilityAdapter implements RecipientEligibilityPort {

    private static final String REASON_ELIGIBLE = "ELIGIBLE";
    private static final String REASON_USER_NOT_ACTIVE = "USER_NOT_ACTIVE_IN_TENANT";
    private static final String REASON_RBAC_DENIED_FALLBACK = "RBAC_DENIED";

    private final OwnershipUserValidationPort users;
    private final CapabilityEvaluationService capabilities;

    public PlatformRecipientEligibilityAdapter(OwnershipUserValidationPort users,
                                                CapabilityEvaluationService capabilities) {
        this.users = users;
        this.capabilities = capabilities;
    }

    @Override
    public EligibilityDecision evaluate(UUID tenantId,
                                         UUID userId,
                                         UUID organizationId,
                                         String requiredCapability) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(userId, "userId");
        if (requiredCapability == null || requiredCapability.isBlank()) {
            throw new IllegalArgumentException("requiredCapability is required");
        }

        if (!users.isActiveUser(tenantId, userId)) {
            return new EligibilityDecision(false, REASON_USER_NOT_ACTIVE);
        }

        AccessDecisionResponse access =
                capabilities.evaluate(tenantId, userId, requiredCapability, organizationId);

        if (access.allowed()) {
            return new EligibilityDecision(true, REASON_ELIGIBLE);
        }

        String denialReason = access.reason() != null
                ? access.reason()
                : REASON_RBAC_DENIED_FALLBACK;
        return new EligibilityDecision(false, denialReason);
    }
}
