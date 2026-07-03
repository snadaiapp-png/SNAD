package com.sanad.platform.organization.membership.mapper;

import com.sanad.platform.organization.membership.domain.OrganizationMembership;
import com.sanad.platform.organization.membership.dto.OrganizationMembershipResponse;
import org.springframework.stereotype.Component;

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
                membership.getUserId(),
                membership.getEmail(),
                membership.getDisplayName(),
                membership.getStatus(),
                membership.getCreatedAt(),
                membership.getUpdatedAt()
        );
    }
}
