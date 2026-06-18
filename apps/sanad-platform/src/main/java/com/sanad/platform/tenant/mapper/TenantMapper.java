package com.sanad.platform.tenant.mapper;

import com.sanad.platform.tenant.domain.Tenant;
import com.sanad.platform.tenant.dto.TenantResponse;
import org.springframework.stereotype.Component;

/**
 * Maps between the {@link Tenant} domain aggregate and the
 * {@link TenantResponse} transport DTO.
 *
 * <p>One-directional (domain -> DTO). New Tenants are constructed via
 * the {@code Tenant(...)} constructor inside the application service,
 * never by mapping a DTO into an entity.</p>
 */
@Component
public class TenantMapper {

    public TenantResponse toResponse(Tenant tenant) {
        if (tenant == null) {
            return null;
        }
        return new TenantResponse(
                tenant.getId(),
                tenant.getName(),
                tenant.getSubdomain(),
                tenant.getStatus(),
                tenant.getCreatedAt(),
                tenant.getUpdatedAt()
        );
    }
}
