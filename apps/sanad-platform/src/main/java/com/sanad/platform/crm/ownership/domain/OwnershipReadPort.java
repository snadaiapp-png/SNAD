package com.sanad.platform.crm.ownership.domain;

import java.util.UUID;

/**
 * Read-side port for ownership queries (CRM-008A design).
 *
 * <p><b>Design-only marker interface.</b> The full method set will be declared
 * in CRM-008B (Foundation) when the JDBC adapter is implemented. Until then,
 * this port exists to:</p>
 * <ul>
 *   <li>Document the architectural boundary: reads go through this port.</li>
 *   <li>Provide a wiring point for future implementations.</li>
 *   <li>Allow architecture tests to assert that no application service bypasses
 *       the ownership read path with direct SQL.</li>
 * </ul>
 *
 * <p><b>Planned methods</b> (to be added in CRM-008B with their value objects):
 * <pre>
 *   Optional&lt;Assignment&gt; findActiveAssignment(UUID tenantId, AssignmentRecordType recordType, UUID recordId);
 *   OwnershipHistoryPage findOwnershipHistory(UUID tenantId, AssignmentRecordType recordType, UUID recordId, OwnershipHistoryCursor cursor, int pageSize);
 *   WorkloadSummary findUserWorkload(UUID tenantId, UUID userId);
 *   int findUserQueueClaimCount(UUID tenantId, UUID queueId, UUID userId);
 * </pre>
 * </p>
 *
 * <p>The corresponding value objects ({@code Assignment}, {@code AssignmentRecordType},
 * {@code OwnershipHistoryPage}, {@code OwnershipHistoryCursor}, {@code WorkloadSummary})
 * are also part of CRM-008B and are intentionally not defined here to keep
 * CRM-008A as a pure design stage with zero compilation dependencies on
 * unbuilt types.</p>
 *
 * <p>Implementation note: the JDBC adapter will read from {@code crm_assignments}
 * (the current active row) plus denormalized {@code owner_user_id} columns on
 * the CRM record tables for fast lookup.</p>
 */
public interface OwnershipReadPort {
    // Marker interface — methods added in CRM-008B.
    // See Javadoc above for the planned contract.
}
