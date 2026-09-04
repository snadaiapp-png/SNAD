package com.sanad.platform.workflow.application;

import java.util.List;
import java.util.UUID;

/**
 * Focused read port over the canonical RBAC tables for workflow assignment
 * resolution. Workflow must not infer roles from HR position names —
 * role/capability truth comes from the authorization catalog only.
 */
public interface WorkflowAuthorizationReadPort {

    /** User IDs in the tenant whose assignment to {@code roleCode} is ACTIVE. */
    List<UUID> findActiveUserIdsByRole(UUID tenantId, String roleCode);

    /** User IDs in the tenant whose roles currently grant {@code capabilityCode}. */
    List<UUID> findActiveUserIdsByCapability(UUID tenantId, String capabilityCode);
}
