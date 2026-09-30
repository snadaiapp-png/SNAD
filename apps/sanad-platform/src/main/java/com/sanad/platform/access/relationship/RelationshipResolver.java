package com.sanad.platform.access.relationship;

import java.util.Optional;
import java.util.UUID;

/**
 * Relationship (ABAC/ReBAC) policy resolution for the decision pipeline
 * (Wave 1 Task 7).
 *
 * <p>The relationship source is the canonical {@code subject_relationships}
 * table (Wave 1 Task 1 schema, FORCE RLS). Wave 1 Task 12 extends this
 * surface with the HR-scoped resolver and the relationship administration
 * service — no parallel relationship subsystem is created.</p>
 */
public interface RelationshipResolver {

    /**
     * Returns the first applicable relationship-policy grant for the subject
     * and capability, or empty when no active relationship applies. The
     * returned candidate still has to win scope resolution (Stage D) — a
     * relationship match never bypasses scope checks.
     */
    Optional<RelationshipPolicyGrant> resolveRelationshipGrant(
            UUID tenantId, UUID userId, UUID capabilityId, UUID organizationId);

    /**
     * Whether the subject's active relationships cover the requested scope
     * family (scope type + optional scope reference) — used by Stage D to
     * resolve fine-grained data scopes.
     */
    boolean supportsRelationshipScope(
            UUID tenantId, UUID userId, String scopeType, UUID scopeReference);

    /**
     * One relationship-policy candidate derived from an active
     * {@code subject_relationships} row.
     */
    record RelationshipPolicyGrant(
            UUID relationshipId,
            String relationshipType,
            String objectType,
            UUID objectId,
            String scopeType) {
    }
}
