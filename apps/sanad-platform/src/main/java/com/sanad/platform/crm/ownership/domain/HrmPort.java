package com.sanad.platform.crm.ownership.domain;

import java.util.UUID;

/** Boundary to HRM for future absence-driven ownership reassignment. */
public interface HrmPort {

    boolean isAbsent(UUID tenantId, UUID userId);

    /** True until the approved production HRM integration is installed. */
    boolean isStub();
}
