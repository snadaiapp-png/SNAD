package com.sanad.platform.partner.executive;

import com.sanad.platform.security.SecurityContextUtils;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Executive control-plane service for partner principals.
 *
 * <p>Partner governance records remain separate from tenant business data.
 * Subresource reads deliberately execute under {@code app.partner_id} so the
 * existing FORCE-RLS partner isolation remains authoritative.</p>
 */
@Service
public class ExecutivePartnerService {

    private static final UUID CONTROL_TENANT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Set<String> PARTNER_TYPES =
            Set.of("PARTNER", "AGENT", "RESELLER", "DISTRIBUTOR", "SELLER");
    private static final Set<String> STATUSES =
            Set.of("ACTIVE", "INACTIVE", "SUSPENDED", "TERMINATED");

    private final JdbcTemplate jdbc;

    public ExecutivePartnerService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<PartnerView> listPartners() {
        platformScope();
        return jdbc.query("""
                SELECT id, partner_type, status, suspended_at, suspended_reason,
                       terminated_at, terminated_reason, created_at, updated_at, version
                  FROM partners
                 ORDER BY created_at DESC, id DESC
                """, (rs, i) -> partnerView(rs));
    }

    @Transactional(readOnly = true)
    public PartnerView getPartner(UUID partnerId) {
        platformScope();
        return requirePartner(partnerId);
    }

    @Transactional
    public PartnerView createPartner(String partnerType, Authentication authentication) {
        platformScope();
        if (!PARTNER_TYPES.contains(partnerType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PARTNER_TYPE_INVALID");
        }
        UUID actor = SecurityContextUtils.userId(authentication);
        UUID id = UUID.randomUUID();

        jdbc.update("""
                INSERT INTO partners
                    (id, partner_type, status, created_by, updated_by)
                VALUES (?, ?, 'ACTIVE', ?, ?)
                """, id, partnerType, actor, actor);

        jdbc.update("""
                INSERT INTO authorization_change_events
                    (id, tenant_id, event_type, actor_user_id,
                     target_type, target_id, payload, created_at)
                VALUES (?, NULL, 'PARTNER_CREATED', ?, 'PARTNER', ?, ?::jsonb, NOW())
                """,
                UUID.randomUUID(),
                actor,
                id,
                "{\"partner_id\":\"" + id + "\",\"partner_type\":\"" + partnerType
                        + "\",\"status\":\"ACTIVE\"}");

        return requirePartner(id);
    }

    @Transactional
    public PartnerView changeStatus(
            UUID partnerId, String targetStatus, String reason, Authentication authentication) {
        platformScope();
        if (!STATUSES.contains(targetStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PARTNER_STATUS_INVALID");
        }
        PartnerView current = lockPartner(partnerId);
        if ("TERMINATED".equals(current.status()) && !"TERMINATED".equals(targetStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "PARTNER_TERMINATED_TERMINAL");
        }
        if (current.status().equals(targetStatus)) {
            return current;
        }
        if (("SUSPENDED".equals(targetStatus) || "TERMINATED".equals(targetStatus))
                && (reason == null || reason.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PARTNER_STATUS_REASON_REQUIRED");
        }

        UUID actor = SecurityContextUtils.userId(authentication);
        jdbc.update("""
                UPDATE partners
                   SET status = ?,
                       suspended_at = CASE WHEN ? = 'SUSPENDED' THEN NOW() ELSE NULL END,
                       suspended_reason = CASE WHEN ? = 'SUSPENDED' THEN ? ELSE NULL END,
                       terminated_at = CASE WHEN ? = 'TERMINATED' THEN NOW() ELSE NULL END,
                       terminated_reason = CASE WHEN ? = 'TERMINATED' THEN ? ELSE NULL END,
                       updated_by = ?,
                       version = version + 1
                 WHERE id = ?
                """,
                targetStatus,
                targetStatus, targetStatus, reason,
                targetStatus, targetStatus, reason,
                actor, partnerId);

        return requirePartner(partnerId);
    }

    @Transactional(readOnly = true)
    public List<PartnerMembershipView> memberships(UUID partnerId) {
        requirePartnerExistsWithoutRls(partnerId);
        partnerScope(partnerId);
        return jdbc.query("""
                SELECT id, user_id, membership_role, status, valid_from, valid_until, version
                  FROM partner_memberships
                 WHERE partner_id = ?
                 ORDER BY created_at DESC, id DESC
                """, (rs, i) -> new PartnerMembershipView(
                        rs.getObject("id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getString("membership_role"),
                        rs.getString("status"),
                        toInstant(rs.getTimestamp("valid_from")),
                        toInstant(rs.getTimestamp("valid_until")),
                        rs.getInt("version")), partnerId);
    }

    @Transactional(readOnly = true)
    public List<PartnerTenantView> tenants(UUID partnerId) {
        requirePartnerExistsWithoutRls(partnerId);
        partnerScope(partnerId);
        return jdbc.query("""
                SELECT id, tenant_id, status, valid_from, valid_until, version
                  FROM partner_tenant_bindings
                 WHERE partner_id = ?
                 ORDER BY created_at DESC, id DESC
                """, (rs, i) -> new PartnerTenantView(
                        rs.getObject("id", UUID.class),
                        rs.getObject("tenant_id", UUID.class),
                        rs.getString("status"),
                        toInstant(rs.getTimestamp("valid_from")),
                        toInstant(rs.getTimestamp("valid_until")),
                        rs.getInt("version")), partnerId);
    }

    @Transactional(readOnly = true)
    public List<PartnerDelegationView> delegations(UUID partnerId) {
        requirePartnerExistsWithoutRls(partnerId);
        partnerScope(partnerId);
        return jdbc.query("""
                SELECT id, tenant_id, capability_code, status, valid_from, valid_until, version
                  FROM partner_delegation_grants
                 WHERE partner_id = ?
                 ORDER BY created_at DESC, id DESC
                """, (rs, i) -> new PartnerDelegationView(
                        rs.getObject("id", UUID.class),
                        rs.getObject("tenant_id", UUID.class),
                        rs.getString("capability_code"),
                        rs.getString("status"),
                        toInstant(rs.getTimestamp("valid_from")),
                        toInstant(rs.getTimestamp("valid_until")),
                        rs.getInt("version")), partnerId);
    }

    private PartnerView lockPartner(UUID partnerId) {
        List<PartnerView> rows = jdbc.query("""
                SELECT id, partner_type, status, suspended_at, suspended_reason,
                       terminated_at, terminated_reason, created_at, updated_at, version
                  FROM partners WHERE id = ? FOR UPDATE
                """, (rs, i) -> partnerView(rs), partnerId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "PARTNER_NOT_FOUND");
        }
        return rows.get(0);
    }

    private PartnerView requirePartner(UUID partnerId) {
        List<PartnerView> rows = jdbc.query("""
                SELECT id, partner_type, status, suspended_at, suspended_reason,
                       terminated_at, terminated_reason, created_at, updated_at, version
                  FROM partners WHERE id = ?
                """, (rs, i) -> partnerView(rs), partnerId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "PARTNER_NOT_FOUND");
        }
        return rows.get(0);
    }

    private void requirePartnerExistsWithoutRls(UUID partnerId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM partners WHERE id = ?", Integer.class, partnerId);
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "PARTNER_NOT_FOUND");
        }
    }

    private void platformScope() {
        jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)",
                String.class, CONTROL_TENANT_ID.toString());
    }

    private void partnerScope(UUID partnerId) {
        jdbc.queryForObject("SELECT set_config('app.partner_id', ?, true)",
                String.class, partnerId.toString());
    }

    private static PartnerView partnerView(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new PartnerView(
                rs.getObject("id", UUID.class),
                rs.getString("partner_type"),
                rs.getString("status"),
                toInstant(rs.getTimestamp("suspended_at")),
                rs.getString("suspended_reason"),
                toInstant(rs.getTimestamp("terminated_at")),
                rs.getString("terminated_reason"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at")),
                rs.getInt("version"));
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    public record PartnerView(
            UUID id, String partnerType, String status,
            Instant suspendedAt, String suspendedReason,
            Instant terminatedAt, String terminatedReason,
            Instant createdAt, Instant updatedAt, int version) {}

    public record PartnerMembershipView(
            UUID membershipId, UUID userId, String membershipRole, String status,
            Instant validFrom, Instant validUntil, int version) {}

    public record PartnerTenantView(
            UUID bindingId, UUID tenantId, String status,
            Instant validFrom, Instant validUntil, int version) {}

    public record PartnerDelegationView(
            UUID grantId, UUID tenantId, String capabilityCode, String status,
            Instant validFrom, Instant validUntil, int version) {}
}
