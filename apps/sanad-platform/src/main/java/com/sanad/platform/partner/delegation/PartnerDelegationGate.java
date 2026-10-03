package com.sanad.platform.partner.delegation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Authorization gate for delegated partner administration (Wave 2 Task 5).
 *
 * <p>Decides whether a partner principal may perform a delegated capability
 * against a target tenant. Delegation is EXPLICIT: partner membership plus an
 * ACTIVE partner-tenant binding never authorize anything by themselves — the
 * exact capability must be granted through {@code partner_delegation_grants}
 * and every element of the chain must currently be valid.</p>
 *
 * <p>The gate reads partner-plane governance tables under the platform
 * control-plane scope (mirroring {@code LastAdminGuard}/{@code ProtectedRoleGuard}
 * connection-scoping), then applies the partner/tenant match logic itself. It
 * is the single decision point consumed by the partner portal boundary (T6),
 * the executive control plane (T7) and the provisioning adapter (T11). Direct
 * table access remains independently protected by FORCE RLS.</p>
 *
 * <p>Decision order (fail-closed, first failure wins):</p>
 * <ol>
 *   <li>authenticated partner identity present</li>
 *   <li>target tenant identity present</li>
 *   <li>capability registered and ACTIVE</li>
 *   <li>binding resolution: ACTIVE binding for the target tenant must belong
 *       to the authenticated partner (foreign binding → PARTNER_MISMATCH,
 *       no binding → TENANT_MISMATCH)</li>
 *   <li>binding window valid</li>
 *   <li>partner principal ACTIVE</li>
 *   <li>delegation grant present, ACTIVE and in-window</li>
 * </ol>
 */
@Component
public class PartnerDelegationGate {

    /** Canonical platform control-plane tenant (mirrors W1/W2 RLS policies). */
    static final UUID DEFAULT_CONTROL_TENANT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final JdbcTemplate jdbc;
    private final UUID controlTenantId;

    public PartnerDelegationGate(
            JdbcTemplate jdbc,
            @Value("${sanad.control-plane.tenant-id:00000000-0000-0000-0000-000000000001}")
            String configuredControlTenantId) {
        this.jdbc = jdbc;
        UUID parsed = null;
        try {
            parsed = (configuredControlTenantId == null || configuredControlTenantId.isBlank())
                    ? null : UUID.fromString(configuredControlTenantId);
        } catch (IllegalArgumentException ignored) {
            parsed = null;
        }
        this.controlTenantId = parsed != null ? parsed : DEFAULT_CONTROL_TENANT_ID;
    }

    /**
     * Evaluates whether {@code authenticatedPartnerId} may perform
     * {@code capabilityCode} against {@code targetTenantId}.
     *
     * @param authenticatedPartnerId partner identity resolved from the
     *        authenticated/signed context — NEVER a caller-supplied parameter
     * @param targetTenantId tenant the partner wants to act upon
     * @param capabilityCode delegation capability from the registry
     * @return ALLOW or DENY decision with a stable reason code
     */
    @Transactional(readOnly = true)
    public PartnerDelegationDecision check(
            UUID authenticatedPartnerId, UUID targetTenantId, String capabilityCode) {

        if (authenticatedPartnerId == null) {
            return PartnerDelegationDecision.deny(
                    "PARTNER_CONTEXT_REQUIRED", capabilityCode, null, targetTenantId);
        }
        if (targetTenantId == null) {
            return PartnerDelegationDecision.deny(
                    "TENANT_CONTEXT_REQUIRED", capabilityCode, authenticatedPartnerId, null);
        }

        platformScope();

        // 1. Registry: unknown capability = DENY, inactive capability = DENY.
        //    Business-data capabilities (CRM/HRM/Payroll/Accounting/ERP) are not
        //    registered, so they can never be delegated.
        List<String> registryStatus = jdbc.query(
                "SELECT status FROM partner_delegation_capabilities WHERE code = ?",
                (rs, i) -> rs.getString(1), capabilityCode);
        if (registryStatus.isEmpty()) {
            return PartnerDelegationDecision.deny(
                    "CAPABILITY_UNKNOWN", capabilityCode, authenticatedPartnerId, targetTenantId);
        }
        if (!"ACTIVE".equals(registryStatus.get(0))) {
            return PartnerDelegationDecision.deny(
                    "CAPABILITY_INACTIVE", capabilityCode, authenticatedPartnerId, targetTenantId);
        }

        // 2. Binding resolution for the target tenant. Deterministic: the ACTIVE
        //    binding wins; otherwise the newest row decides the outcome.
        BindingRow binding = jdbc.query("""
                SELECT id, partner_id, status, valid_from, valid_until
                  FROM partner_tenant_bindings
                 WHERE tenant_id = ?
                 ORDER BY (status = 'ACTIVE') DESC, created_at DESC, id DESC
                 LIMIT 1
                """,
                rs -> rs.next()
                        ? new BindingRow(
                                rs.getObject("id", UUID.class),
                                rs.getObject("partner_id", UUID.class),
                                rs.getString("status"),
                                toInstant(rs.getTimestamp("valid_from")),
                                toInstant(rs.getTimestamp("valid_until")))
                        : null,
                targetTenantId);

        Instant now = Instant.now();
        if (binding == null) {
            return PartnerDelegationDecision.deny(
                    "TENANT_MISMATCH", capabilityCode, authenticatedPartnerId, targetTenantId);
        }
        if (!binding.partnerId().equals(authenticatedPartnerId)) {
            return PartnerDelegationDecision.deny(
                    "PARTNER_MISMATCH", capabilityCode, authenticatedPartnerId, targetTenantId);
        }
        if (!"ACTIVE".equals(binding.status())) {
            return PartnerDelegationDecision.deny(
                    "BINDING_INACTIVE", capabilityCode, authenticatedPartnerId, targetTenantId);
        }
        if (!inWindow(binding.validFrom(), binding.validUntil(), now)) {
            return PartnerDelegationDecision.deny(
                    "BINDING_EXPIRED", capabilityCode, authenticatedPartnerId, targetTenantId);
        }

        // 3. Partner principal must currently be ACTIVE.
        List<String> partnerStatus = jdbc.query(
                "SELECT status FROM partners WHERE id = ?",
                (rs, i) -> rs.getString(1), authenticatedPartnerId);
        if (partnerStatus.isEmpty()) {
            return PartnerDelegationDecision.deny(
                    "PARTNER_CONTEXT_REQUIRED", capabilityCode, authenticatedPartnerId, targetTenantId);
        }
        if (!"ACTIVE".equals(partnerStatus.get(0))) {
            return PartnerDelegationDecision.deny(
                    "PARTNER_INACTIVE", capabilityCode, authenticatedPartnerId, targetTenantId);
        }

        // 4. Explicit delegation grant: exact (partner, tenant, capability).
        //    Deterministic: newest row wins; replay after REVOKED is a NEW row.
        GrantRow grant = jdbc.query("""
                SELECT id, status, valid_from, valid_until
                  FROM partner_delegation_grants
                 WHERE partner_id = ? AND tenant_id = ? AND capability_code = ?
                 ORDER BY created_at DESC, id DESC
                 LIMIT 1
                """,
                rs -> rs.next()
                        ? new GrantRow(
                                rs.getObject("id", UUID.class),
                                rs.getString("status"),
                                toInstant(rs.getTimestamp("valid_from")),
                                toInstant(rs.getTimestamp("valid_until")))
                        : null,
                authenticatedPartnerId, targetTenantId, capabilityCode);

        if (grant == null) {
            return PartnerDelegationDecision.deny(
                    "DELEGATION_MISSING", capabilityCode, authenticatedPartnerId, targetTenantId);
        }
        switch (grant.status()) {
            case "SUSPENDED" -> {
                return PartnerDelegationDecision.deny(
                        "DELEGATION_SUSPENDED", capabilityCode, authenticatedPartnerId, targetTenantId);
            }
            case "REVOKED" -> {
                return PartnerDelegationDecision.deny(
                        "DELEGATION_REVOKED", capabilityCode, authenticatedPartnerId, targetTenantId);
            }
            case "INACTIVE" -> {
                return PartnerDelegationDecision.deny(
                        "DELEGATION_INACTIVE", capabilityCode, authenticatedPartnerId, targetTenantId);
            }
            default -> { /* ACTIVE: fall through to window validation */ }
        }
        if (!"ACTIVE".equals(grant.status())
                || !inWindow(grant.validFrom(), grant.validUntil(), now)) {
            return PartnerDelegationDecision.deny(
                    "DELEGATION_EXPIRED", capabilityCode, authenticatedPartnerId, targetTenantId);
        }

        return PartnerDelegationDecision.allow(
                capabilityCode, authenticatedPartnerId, targetTenantId, grant.id(), binding.id());
    }

    private void platformScope() {
        // Transaction-scoped platform control-plane scope. PG resets local GUCs
        // at transaction end, so no cross-transaction pool contamination is
        // possible. app.partner_id is deliberately NOT touched: the audit RLS
        // platform branch requires it to be UNSET (IS NULL), and empty-string
        // blanking would break authorization_change_events writes that share
        // this transaction.
        jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)",
                String.class, controlTenantId.toString());
    }

    private static boolean inWindow(Instant validFrom, Instant validUntil, Instant now) {
        if (validFrom != null && now.isBefore(validFrom)) {
            return false;
        }
        return validUntil == null || now.isBefore(validUntil);
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record BindingRow(UUID id, UUID partnerId, String status, Instant validFrom, Instant validUntil) {}

    private record GrantRow(UUID id, String status, Instant validFrom, Instant validUntil) {}
}
