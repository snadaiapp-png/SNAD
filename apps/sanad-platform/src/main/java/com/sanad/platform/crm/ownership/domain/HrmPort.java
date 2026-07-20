package com.sanad.platform.crm.ownership.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Port for HRM (Human Resources Module).
 *
 * <p>CRM-008 references employee data (manager, department, employment status,
 * absence) via this port — it does NOT store its own copy of the employee
 * master record. This preserves the architectural invariant that HRM is the
 * sole source of truth for employee organizational data.</p>
 *
 * <p>Until HRM is built, a stub adapter returns "active, no absence, no manager"
 * for any user. This is explicitly marked and health-checked so production
 * cannot accidentally rely on the stub for absence-driven reassignment.</p>
 */
public interface HrmPort {

    /**
     * Returns the employment status of the given user in the given tenant.
     * Used by the absence-driven reassignment workflow (CRM-008 §9.4).
     */
    EmploymentStatus getEmploymentStatus(UUID tenantId, UUID userId);

    /**
     * Returns the user's manager, or empty if no manager is configured.
     * Used by the transfer approval chain (CRM-008D).
     */
    java.util.Optional<UUID> findManager(UUID tenantId, UUID userId);

    /**
     * Returns true if the user is currently marked as absent (vacation, leave, etc.).
     * Used by the absence-driven reassignment workflow.
     */
    boolean isAbsent(UUID tenantId, UUID userId);

    /**
     * Stub indicator: returns true when the implementation is a placeholder.
     */
    boolean isStub();

    /**
     * Employment status as reported by HRM.
     */
    enum EmploymentStatus {
        ACTIVE,
        ON_LEAVE,
        SUSPENDED,
        TERMINATED
    }
}
