package com.sanad.platform.organization.membership.mapper;

import com.sanad.platform.organization.membership.domain.OrganizationMembership;
import com.sanad.platform.organization.membership.dto.OrganizationMembershipResponse;
import org.springframework.stereotype.Component;

/**
 * Maps between the {@link OrganizationMembership} domain entity and the
 * {@link OrganizationMembershipResponse} transport DTO.
 *
 * <p>One-directional (domain -> DTO). New memberships are constructed
 * inside the application service, never by mapping a DTO into an entity.</p>
 */
@Component
public class OrganizationMembershipMapper {

    public OrganizationMembershipResponse toResponse(OrganizationMembership membership) {
        if (membership == null) {
            return null;
        }
        return new OrganizationMembershipResponse(
                membership.getId(),
                membership.getTenantId(),
                membership.getOrganizationId(),
                membership.getEmail(),
                membership.getDisplayName(),
                membership.getStatus(),
                membership.getCreatedAt(),
                membership.getUpdatedAt()
        );
    }
}
