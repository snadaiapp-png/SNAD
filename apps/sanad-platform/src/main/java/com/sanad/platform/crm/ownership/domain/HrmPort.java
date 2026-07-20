package com.sanad.platform.crm.ownership.domain;

/**
 * Port for HRM (Human Resources Module) — CRM-008A design.
 *
 * <p><b>Design-only marker interface.</b> The full method set will be declared
 * in CRM-008D (Transfers) when absence-driven reassignment is implemented.</p>
 *
 * <p>CRM-008 references employee data (manager, department, employment status,
 * absence) via this port — it does NOT store its own copy of the employee
 * master record. This preserves the architectural invariant that HRM is the
 * sole source of truth for employee organizational data.</p>
 *
 * <p>Until HRM is built, a stub adapter returns "active, no absence, no manager"
 * for any user. This is explicitly marked and health-checked so production
 * cannot accidentally rely on the stub for absence-driven reassignment.</p>
 *
 * <p><b>Planned methods</b> (to be added in CRM-008D):
 * <pre>
 *   EmploymentStatus getEmploymentStatus(UUID tenantId, UUID userId);
 *   Optional&lt;UUID&gt; findManager(UUID tenantId, UUID userId);
 *   boolean isAbsent(UUID tenantId, UUID userId);
 *   boolean isStub();
 * </pre>
 * </p>
 */
public interface HrmPort {
    // Marker interface — methods added in CRM-008D.
    // See Javadoc above for the planned contract.
}
