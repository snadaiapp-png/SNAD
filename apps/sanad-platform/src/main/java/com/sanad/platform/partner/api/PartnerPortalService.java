package com.sanad.platform.partner.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Partner-facing governance reads. Every query runs under app.partner_id so
 * FORCE RLS remains authoritative even if an application predicate regresses.
 */
@Service
public class PartnerPortalService {

    private final JdbcTemplate jdbc;

    public PartnerPortalService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public PartnerSummary requireActiveMembership(UUID userId, UUID partnerId) {
        partnerScope(partnerId);
        List<PartnerSummary> rows = jdbc.query("""
                SELECT partner_id, user_id, membership_role, status
                  FROM partner_memberships
                 WHERE partner_id = ?
                   AND user_id = ?
                   AND status = 'ACTIVE'
                   AND valid_from <= NOW()
                   AND (valid_until IS NULL OR valid_until > NOW())
                 ORDER BY created_at DESC, id DESC
                 LIMIT 1
                """,
                (rs, i) -> new PartnerSummary(
                        rs.getObject("partner_id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getString("membership_role"),
                        rs.getString("status")),
                partnerId, userId);

        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "PARTNER_MEMBERSHIP_REQUIRED");
        }
        return rows.get(0);
    }

    @Transactional(readOnly = true)
    public List<PartnerTenantBindingView> listOwnTenants(UUID userId, UUID partnerId) {
        requireActiveMembership(userId, partnerId);
        return jdbc.query("""
                SELECT id, tenant_id, status, valid_from, valid_until
                  FROM partner_tenant_bindings
                 WHERE partner_id = ?
                 ORDER BY created_at DESC, id DESC
                """,
                (rs, i) -> new PartnerTenantBindingView(
                        rs.getObject("id", UUID.class),
                        rs.getObject("tenant_id", UUID.class),
                        rs.getString("status"),
                        toInstant(rs.getTimestamp("valid_from")),
                        toInstant(rs.getTimestamp("valid_until"))),
                partnerId);
    }

    @Transactional(readOnly = true)
    public List<PartnerDelegationView> listOwnDelegations(UUID userId, UUID partnerId) {
        requireActiveMembership(userId, partnerId);
        return jdbc.query("""
                SELECT id, tenant_id, capability_code, status, valid_from, valid_until
                  FROM partner_delegation_grants
                 WHERE partner_id = ?
                 ORDER BY created_at DESC, id DESC
                """,
                (rs, i) -> new PartnerDelegationView(
                        rs.getObject("id", UUID.class),
                        rs.getObject("tenant_id", UUID.class),
                        rs.getString("capability_code"),
                        rs.getString("status"),
                        toInstant(rs.getTimestamp("valid_from")),
                        toInstant(rs.getTimestamp("valid_until"))),
                partnerId);
    }

    private void partnerScope(UUID partnerId) {
        jdbc.queryForObject(
                "SELECT set_config('app.partner_id', ?, true)",
                String.class,
                partnerId.toString());
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    public record PartnerSummary(UUID partnerId, UUID userId, String membershipRole, String status) {}

    public record PartnerTenantBindingView(
            UUID bindingId, UUID tenantId, String status, Instant validFrom, Instant validUntil) {}

    public record PartnerDelegationView(
            UUID grantId, UUID tenantId, String capabilityCode, String status,
            Instant validFrom, Instant validUntil) {}
}
