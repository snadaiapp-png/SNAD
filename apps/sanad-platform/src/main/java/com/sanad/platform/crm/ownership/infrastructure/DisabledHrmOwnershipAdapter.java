package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.crm.ownership.domain.HrmPort;
import com.sanad.platform.crm.ownership.domain.OwnershipDomainException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Production-visible disabled boundary until a real HRM integration is approved. */
@Component
public class DisabledHrmOwnershipAdapter implements HrmPort {

    @Override
    public boolean isAbsent(UUID tenantId, UUID userId) {
        throw new OwnershipDomainException(
                "HRM absence-driven reassignment is disabled until real integration is approved");
    }

    @Override
    public boolean isStub() {
        return true;
    }
}
