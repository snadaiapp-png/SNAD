package com.sanad.platform.organization.mapper;

import com.sanad.platform.organization.domain.Organization;
import com.sanad.platform.organization.dto.OrganizationResponse;
import org.springframework.stereotype.Component;

/**
 * Maps between the {@link Organization} domain aggregate and the
 * {@link OrganizationResponse} transport DTO.
 *
 * <p>The mapper is intentionally one-directional (domain → DTO) at this
 * stage. The reverse direction is intentionally omitted because new
 * Organizations are constructed via the {@code Organization(Tenant, ...)}
 * constructor inside the application service — never by mapping a DTO
 * directly into an entity, which would bypass aggregate invariants.</p>
 */
@Component
public class OrganizationMapper {

    /**
     * Convert a persisted {@link Organization} into an {@link OrganizationResponse}.
     *
     * @param organization the domain aggregate (must be persisted, i.e. have non-null id
     *                     and tenant reference)
     * @return a fully populated response DTO
     */
    public OrganizationResponse toResponse(Organization organization) {
        if (organization == null) {
            return null;
        }
        return new OrganizationResponse(
                organization.getId(),
                organization.getTenant() != null ? organization.getTenant().getId() : null,
                organization.getName(),
                organization.getDescription(),
                organization.getStatus(),
                organization.getCreatedAt(),
                organization.getUpdatedAt()
        );
    }
}
