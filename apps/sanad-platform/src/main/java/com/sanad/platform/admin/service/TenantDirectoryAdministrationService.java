package com.sanad.platform.admin.service;

import com.sanad.platform.admin.api.SaasAdminDtos.CreateMembershipAdminRequest;
import com.sanad.platform.admin.api.SaasAdminDtos.CreateOrganizationAdminRequest;
import com.sanad.platform.admin.api.SaasAdminDtos.MembershipAdminResponse;
import com.sanad.platform.admin.api.SaasAdminDtos.OrganizationAdminResponse;
import com.sanad.platform.admin.api.SaasAdminDtos.UpdateMembershipAdminRequest;
import com.sanad.platform.admin.api.SaasAdminDtos.UpdateOrganizationAdminRequest;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Privileged directory administration for organizations and memberships across tenants. */
@Service
public class TenantDirectoryAdministrationService {

    private static final Set<String> ORGANIZATION_STATUSES = Set.of("ACTIVE", "INACTIVE", "ARCHIVED");
    private static final Set<String> MEMBERSHIP_STATUSES = Set.of("INVITED", "ACTIVE", "INACTIVE", "REMOVED");

    private final JdbcTemplate jdbcTemplate;
    private final PlatformAuditService auditService;

    public TenantDirectoryAdministrationService(JdbcTemplate jdbcTemplate, PlatformAuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<OrganizationAdminResponse> listOrganizations(UUID tenantId) {
        ensureTenant(tenantId);
        return jdbcTemplate.query(
                "SELECT id, tenant_id, name, description, status, created_at, updated_at "
                        + "FROM organizations WHERE tenant_id = ? ORDER BY created_at",
                this::mapOrganization,
                tenantId
        );
    }

    @Transactional
    public OrganizationAdminResponse createOrganization(
            UUID tenantId,
            CreateOrganizationAdminRequest request,
            Authentication authentication
    ) {
        ensureTenant(tenantId);
        LimitSnapshot limits = limits(tenantId);
        long current = count(
                "SELECT COUNT(*) FROM organizations WHERE tenant_id = ? AND status <> 'ARCHIVED'",
                tenantId);
        if (current >= limits.maxOrganizations()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Organization limit for the subscription has been reached");
        }
        String name = request.name().trim();
        if (count("SELECT COUNT(*) FROM organizations WHERE tenant_id = ? AND LOWER(name) = LOWER(?)", tenantId, name) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Organization name already exists for the tenant");
        }

        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO organizations (id, tenant_id, name, description, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)",
                id, tenantId, name, blankToNull(request.description()), now, now);
        OrganizationAdminResponse created = getOrganization(tenantId, id);
        auditService.success(authentication, tenantId, "ORGANIZATION.CREATE", "ORGANIZATION", id.toString(),
                "Created from control plane", null, created);
        return created;
    }

    @Transactional
    public OrganizationAdminResponse updateOrganization(
            UUID tenantId,
            UUID organizationId,
            UpdateOrganizationAdminRequest request,
            Authentication authentication
    ) {
        OrganizationAdminResponse before = getOrganization(tenantId, organizationId);
        String name = request.name().trim();
        if (count(
                "SELECT COUNT(*) FROM organizations WHERE tenant_id = ? AND id <> ? AND LOWER(name) = LOWER(?)",
                tenantId, organizationId, name) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Organization name already exists for the tenant");
        }
        jdbcTemplate.update(
                "UPDATE organizations SET name = ?, description = ?, updated_at = ? WHERE tenant_id = ? AND id = ?",
                name, blankToNull(request.description()), Instant.now(), tenantId, organizationId);
        OrganizationAdminResponse after = getOrganization(tenantId, organizationId);
        auditService.success(authentication, tenantId, "ORGANIZATION.UPDATE", "ORGANIZATION", organizationId.toString(),
                "Updated from control plane", before, after);
        return after;
    }

    @Transactional
    public OrganizationAdminResponse changeOrganizationStatus(
            UUID tenantId,
            UUID organizationId,
            String requestedStatus,
            String reason,
            Authentication authentication
    ) {
        OrganizationAdminResponse before = getOrganization(tenantId, organizationId);
        String status = normalize(requestedStatus);
        if (!ORGANIZATION_STATUSES.contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported organization status");
        }
        jdbcTemplate.update(
                "UPDATE organizations SET status = ?, updated_at = ? WHERE tenant_id = ? AND id = ?",
                status, Instant.now(), tenantId, organizationId);
        OrganizationAdminResponse after = getOrganization(tenantId, organizationId);
        auditService.success(authentication, tenantId, "ORGANIZATION.STATUS.CHANGE", "ORGANIZATION",
                organizationId.toString(), reason, before, after);
        return after;
    }

    @Transactional(readOnly = true)
    public List<MembershipAdminResponse> listMemberships(UUID tenantId, UUID organizationId) {
        getOrganization(tenantId, organizationId);
        return jdbcTemplate.query(
                "SELECT id, tenant_id, organization_id, user_id, email, display_name, role_code, status, created_at, updated_at "
                        + "FROM organization_memberships WHERE tenant_id = ? AND organization_id = ? ORDER BY created_at",
                this::mapMembership,
                tenantId, organizationId
        );
    }

    @Transactional
    public MembershipAdminResponse createMembership(
            UUID tenantId,
            UUID organizationId,
            CreateMembershipAdminRequest request,
            Authentication authentication
    ) {
        getOrganization(tenantId, organizationId);
        LimitSnapshot limits = limits(tenantId);
        long occupiedSeats = count(
                "SELECT COUNT(DISTINCT LOWER(email)) FROM organization_memberships "
                        + "WHERE tenant_id = ? AND status IN ('INVITED', 'ACTIVE')",
                tenantId);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        boolean existingSeat = count(
                "SELECT COUNT(*) FROM organization_memberships WHERE tenant_id = ? AND LOWER(email) = LOWER(?) "
                        + "AND status IN ('INVITED', 'ACTIVE')",
                tenantId, email) > 0;
        if (!existingSeat && occupiedSeats >= limits.maxUsers()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Seat limit for the subscription has been reached");
        }
        if (count(
                "SELECT COUNT(*) FROM organization_memberships WHERE tenant_id = ? AND organization_id = ? AND LOWER(email) = LOWER(?)",
                tenantId, organizationId, email) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Membership already exists for this organization");
        }

        UUID userId = optionalUuid(
                "SELECT id FROM users WHERE tenant_id = ? AND LOWER(email) = LOWER(?)",
                tenantId, email);
        String roleCode = normalizeRoleCode(request.roleCode());
        UUID roleId = ensureRole(tenantId, roleCode);
        String status = userId == null ? "INVITED" : "ACTIVE";
        UUID membershipId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO organization_memberships "
                        + "(id, tenant_id, organization_id, user_id, email, display_name, role_code, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                membershipId, tenantId, organizationId, userId, email,
                blankToNull(request.displayName()), roleCode, status, now, now);
        if (userId != null) {
            activateGrant(tenantId, organizationId, userId, roleId);
        }
        MembershipAdminResponse created = getMembership(tenantId, organizationId, membershipId);
        auditService.success(authentication, tenantId, "MEMBERSHIP.CREATE", "ORGANIZATION_MEMBERSHIP",
                membershipId.toString(), "Created from control plane", null, created);
        return created;
    }

    @Transactional
    public MembershipAdminResponse updateMembership(
            UUID tenantId,
            UUID organizationId,
            UUID membershipId,
            UpdateMembershipAdminRequest request,
            Authentication authentication
    ) {
        MembershipAdminResponse before = getMembership(tenantId, organizationId, membershipId);
        String status = normalize(request.status());
        if (!MEMBERSHIP_STATUSES.contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported membership status");
        }
        String roleCode = normalizeRoleCode(request.roleCode());
        UUID roleId = ensureRole(tenantId, roleCode);
        Instant now = Instant.now();
        jdbcTemplate.update(
                "UPDATE organization_memberships SET status = ?, role_code = ?, updated_at = ? "
                        + "WHERE tenant_id = ? AND organization_id = ? AND id = ?",
                status, roleCode, now, tenantId, organizationId, membershipId);

        if (before.userId() != null) {
            revokeOrganizationGrants(tenantId, organizationId, before.userId());
            if ("ACTIVE".equals(status)) {
                activateGrant(tenantId, organizationId, before.userId(), roleId);
            }
        }
        MembershipAdminResponse after = getMembership(tenantId, organizationId, membershipId);
        auditService.success(authentication, tenantId, "MEMBERSHIP.UPDATE", "ORGANIZATION_MEMBERSHIP",
                membershipId.toString(), request.reason(), before, after);
        return after;
    }

    @Transactional(readOnly = true)
    public OrganizationAdminResponse getOrganization(UUID tenantId, UUID organizationId) {
        List<OrganizationAdminResponse> rows = jdbcTemplate.query(
                "SELECT id, tenant_id, name, description, status, created_at, updated_at "
                        + "FROM organizations WHERE tenant_id = ? AND id = ?",
                this::mapOrganization,
                tenantId, organizationId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found");
        }
        return rows.get(0);
    }

    @Transactional(readOnly = true)
    public MembershipAdminResponse getMembership(UUID tenantId, UUID organizationId, UUID membershipId) {
        List<MembershipAdminResponse> rows = jdbcTemplate.query(
                "SELECT id, tenant_id, organization_id, user_id, email, display_name, role_code, status, created_at, updated_at "
                        + "FROM organization_memberships WHERE tenant_id = ? AND organization_id = ? AND id = ?",
                this::mapMembership,
                tenantId, organizationId, membershipId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Membership not found");
        }
        return rows.get(0);
    }

    private LimitSnapshot limits(UUID tenantId) {
        List<LimitSnapshot> rows = jdbcTemplate.query(
                "SELECT p.max_users, p.max_organizations FROM tenant_subscriptions s "
                        + "JOIN saas_plans p ON p.id = s.plan_id "
                        + "WHERE s.tenant_id = ? AND s.status IN ('TRIALING', 'ACTIVE', 'PAST_DUE')",
                (rs, rowNum) -> new LimitSnapshot(rs.getInt("max_users"), rs.getInt("max_organizations")),
                tenantId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An active subscription is required for directory changes");
        }
        return rows.get(0);
    }

    private UUID ensureRole(UUID tenantId, String roleCode) {
        UUID existing = optionalUuid(
                "SELECT id FROM roles WHERE tenant_id = ? AND code = ? AND status = 'ACTIVE'",
                tenantId, roleCode);
        if (existing != null) return existing;

        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO roles (id, tenant_id, code, name, description, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?)",
                id, tenantId, roleCode, roleCode, "Created by control-plane membership administration", now, now);
        return id;
    }

    private void activateGrant(UUID tenantId, UUID organizationId, UUID userId, UUID roleId) {
        List<UUID> existing = jdbcTemplate.query(
                "SELECT id FROM user_role_assignments WHERE tenant_id = ? AND user_id = ? AND role_id = ? AND organization_id = ?",
                (rs, rowNum) -> rs.getObject("id", UUID.class),
                tenantId, userId, roleId, organizationId);
        Instant now = Instant.now();
        if (existing.isEmpty()) {
            jdbcTemplate.update(
                    "INSERT INTO user_role_assignments "
                            + "(id, tenant_id, user_id, role_id, organization_id, status, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?)",
                    UUID.randomUUID(), tenantId, userId, roleId, organizationId, now, now);
        } else {
            jdbcTemplate.update(
                    "UPDATE user_role_assignments SET status = 'ACTIVE', updated_at = ? WHERE id = ?",
                    now, existing.get(0));
        }
    }

    private void revokeOrganizationGrants(UUID tenantId, UUID organizationId, UUID userId) {
        jdbcTemplate.update(
                "UPDATE user_role_assignments SET status = 'REVOKED', updated_at = ? "
                        + "WHERE tenant_id = ? AND organization_id = ? AND user_id = ?",
                Instant.now(), tenantId, organizationId, userId);
    }

    private void ensureTenant(UUID tenantId) {
        if (count("SELECT COUNT(*) FROM tenants WHERE id = ?", tenantId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found");
        }
    }

    private long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    private UUID optionalUuid(String sql, Object... args) {
        try {
            return jdbcTemplate.queryForObject(sql, UUID.class, args);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private OrganizationAdminResponse mapOrganization(ResultSet rs, int rowNum) throws SQLException {
        return new OrganizationAdminResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("status"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
        );
    }

    private MembershipAdminResponse mapMembership(ResultSet rs, int rowNum) throws SQLException {
        return new MembershipAdminResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getObject("organization_id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("email"),
                rs.getString("display_name"),
                rs.getString("role_code"),
                rs.getString("status"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
        );
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime offsetDateTime) return offsetDateTime.toInstant();
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        throw new SQLException("Unsupported timestamp type for " + column);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeRoleCode(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role code is required");
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record LimitSnapshot(int maxUsers, int maxOrganizations) {
    }
}