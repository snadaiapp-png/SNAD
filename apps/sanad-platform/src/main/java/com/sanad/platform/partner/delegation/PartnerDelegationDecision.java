package com.sanad.platform.partner.delegation;

import java.util.UUID;

/**
 * Immutable result of a partner delegation evaluation.
 *
 * <p>The {@code reason} code is stable and machine-readable. It is part of the
 * W2 delegation contract and must not be renamed without an owner-approved
 * design decision:</p>
 *
 * <ul>
 *   <li>{@code PARTNER_CONTEXT_REQUIRED} — no authenticated partner identity</li>
 *   <li>{@code TENANT_CONTEXT_REQUIRED} — no target tenant supplied</li>
 *   <li>{@code CAPABILITY_UNKNOWN} — capability absent from the delegation
 *       registry (includes every business-data capability such as
 *       {@code CRM.READ}/{@code HRM.READ}: commercial delegation can never
 *       become implicit business-data access)</li>
 *   <li>{@code CAPABILITY_INACTIVE} — registry row exists but is INACTIVE</li>
 *   <li>{@code PARTNER_MISMATCH} — the target tenant is bound to a different
 *       partner than the authenticated one</li>
 *   <li>{@code TENANT_MISMATCH} — the target tenant has no ACTIVE partner
 *       binding at all, or is unknown</li>
 *   <li>{@code BINDING_INACTIVE} — the binding between the authenticated
 *       partner and the target tenant exists but is not ACTIVE</li>
 *   <li>{@code BINDING_EXPIRED} — the binding is ACTIVE but outside its
 *       validity window</li>
 *   <li>{@code PARTNER_INACTIVE} — the authenticated partner principal exists
 *       but is not ACTIVE</li>
 *   <li>{@code DELEGATION_MISSING} — no delegation grant row for the exact
 *       (partner, tenant, capability) triple</li>
 *   <li>{@code DELEGATION_INACTIVE} — newest grant row is INACTIVE</li>
 *   <li>{@code DELEGATION_SUSPENDED} — newest grant row is SUSPENDED</li>
 *   <li>{@code DELEGATION_REVOKED} — newest grant row is REVOKED (terminal)</li>
 *   <li>{@code DELEGATION_EXPIRED} — grant is ACTIVE but outside its
 *       validity window</li>
 * </ul>
 *
 * <p>Evaluation is fail-closed: any missing, malformed, expired, inactive or
 * foreign element produces DENY with one of the reasons above, never ALLOW.</p>
 */
public record PartnerDelegationDecision(
        boolean allowed,
        String reason,
        String capabilityCode,
        UUID partnerId,
        UUID tenantId,
        UUID grantId,
        UUID bindingId
) {

    public static PartnerDelegationDecision allow(
            String capabilityCode, UUID partnerId, UUID tenantId,
            UUID grantId, UUID bindingId) {
        return new PartnerDelegationDecision(
                true, "DELEGATION_ALLOWED", capabilityCode,
                partnerId, tenantId, grantId, bindingId);
    }

    public static PartnerDelegationDecision deny(
            String reason, String capabilityCode, UUID partnerId, UUID tenantId) {
        return new PartnerDelegationDecision(
                false, reason, capabilityCode, partnerId, tenantId, null, null);
    }
}
