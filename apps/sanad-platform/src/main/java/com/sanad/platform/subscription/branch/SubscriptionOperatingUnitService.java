package com.sanad.platform.subscription.branch;

import com.sanad.platform.admin.service.PlatformAuditService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Canonical operating-unit / branch subscription authority.
 *
 * <p>Organizations remain the tenant-owned operating-unit identities. This
 * service owns subscription binding, application enablement, branch billing
 * profiles and generic resource attribution. Every write verifies that the
 * subscription and organization belong to the same tenant and is audit-atomic.
 */
@Service
public class SubscriptionOperatingUnitService {

    public record OperatingUnit(
            UUID organizationId,
            String organizationName,
            String unitType,
            String status,
            String billingMode
    ) {}

    public record BillingProfile(
            UUID id,
            UUID organizationId,
            String profileName,
            String billingEmail,
            String currencyCode,
            String billingMode,
            String status
    ) {}

    private final JdbcTemplate jdbc;
    private final PlatformAuditService audit;

    public SubscriptionOperatingUnitService(JdbcTemplate jdbc, PlatformAuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<OperatingUnit> list(UUID subscriptionId) {
        UUID tenantId = tenantId(subscriptionId);
        return jdbc.query("""
                SELECT o.id, o.name, o.unit_type, sou.status, sou.billing_mode
                  FROM subscription_operating_units sou
                  JOIN organizations o
                    ON o.tenant_id = sou.tenant_id
                   AND o.id = sou.organization_id
                 WHERE sou.subscription_id = ?
                   AND sou.tenant_id = ?
                 ORDER BY o.name, o.id
                """,
                (rs, rowNum) -> new OperatingUnit(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getString("unit_type"),
                        rs.getString("status"),
                        rs.getString("billing_mode")),
                subscriptionId, tenantId);
    }

    @Transactional
    public OperatingUnit bind(
            UUID subscriptionId,
            UUID organizationId,
            String billingMode,
            Authentication authentication
    ) {
        UUID tenantId = tenantId(subscriptionId);
        requireOrganization(tenantId, organizationId);
        String mode = normalizeBillingMode(billingMode);
        Instant now = Instant.now();

        jdbc.update("""
                INSERT INTO subscription_operating_units
                    (id, tenant_id, subscription_id, organization_id, status,
                     billing_mode, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?, ?)
                ON CONFLICT (subscription_id, organization_id)
                DO UPDATE SET status = 'ACTIVE',
                              billing_mode = EXCLUDED.billing_mode,
                              updated_at = EXCLUDED.updated_at
                """,
                UUID.randomUUID(), tenantId, subscriptionId, organizationId,
                mode, Timestamp.from(now), Timestamp.from(now));

        audit.success(authentication, tenantId, "SUBSCRIPTION_OPERATING_UNIT_BOUND",
                "SUBSCRIPTION_OPERATING_UNIT", organizationId.toString(),
                "subscription=" + subscriptionId + ";billingMode=" + mode,
                null, null);
        return find(subscriptionId, organizationId);
    }

    @Transactional
    public void deactivate(
            UUID subscriptionId,
            UUID organizationId,
            Authentication authentication
    ) {
        UUID tenantId = tenantId(subscriptionId);
        requireOrganization(tenantId, organizationId);
        int changed = jdbc.update("""
                UPDATE subscription_operating_units
                   SET status = 'INACTIVE', updated_at = NOW()
                 WHERE tenant_id = ? AND subscription_id = ? AND organization_id = ?
                """, tenantId, subscriptionId, organizationId);
        if (changed == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "operating-unit binding not found");
        }
        jdbc.update("""
                UPDATE subscription_unit_applications
                   SET enabled = FALSE, updated_at = NOW()
                 WHERE tenant_id = ? AND subscription_id = ? AND organization_id = ?
                """, tenantId, subscriptionId, organizationId);
        jdbc.update("""
                UPDATE subscription_resource_bindings
                   SET status = 'INACTIVE', updated_at = NOW()
                 WHERE tenant_id = ? AND subscription_id = ? AND organization_id = ?
                """, tenantId, subscriptionId, organizationId);
        audit.success(authentication, tenantId, "SUBSCRIPTION_OPERATING_UNIT_DEACTIVATED",
                "SUBSCRIPTION_OPERATING_UNIT", organizationId.toString(),
                "subscription=" + subscriptionId, null, null);
    }

    @Transactional
    public void setApplication(
            UUID subscriptionId,
            UUID organizationId,
            UUID applicationId,
            boolean enabled,
            Authentication authentication
    ) {
        UUID tenantId = tenantId(subscriptionId);
        requireActiveBinding(tenantId, subscriptionId, organizationId);
        requireApplicationEntitled(subscriptionId, applicationId);
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO subscription_unit_applications
                    (id, tenant_id, subscription_id, organization_id, application_id,
                     enabled, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (subscription_id, organization_id, application_id)
                DO UPDATE SET enabled = EXCLUDED.enabled, updated_at = EXCLUDED.updated_at
                """,
                UUID.randomUUID(), tenantId, subscriptionId, organizationId,
                applicationId, enabled, Timestamp.from(now), Timestamp.from(now));
        audit.success(authentication, tenantId, "SUBSCRIPTION_UNIT_APPLICATION_CHANGED",
                "SUBSCRIPTION_UNIT_APPLICATION", applicationId.toString(),
                "subscription=" + subscriptionId + ";organization=" + organizationId
                        + ";enabled=" + enabled,
                null, null);
    }

    @Transactional
    public BillingProfile upsertBillingProfile(
            UUID subscriptionId,
            UUID organizationId,
            String profileName,
            String billingEmail,
            String currencyCode,
            String billingMode,
            Authentication authentication
    ) {
        UUID tenantId = tenantId(subscriptionId);
        if (organizationId != null) {
            requireActiveBinding(tenantId, subscriptionId, organizationId);
        }
        String mode = normalizeBillingMode(billingMode);
        String currency = currencyCode == null ? "" : currencyCode.trim().toUpperCase();
        if (!currency.matches("^[A-Z]{3}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "currencyCode must be ISO-4217");
        }
        if (profileName == null || profileName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "profileName is required");
        }

        List<UUID> existing = organizationId == null
                ? jdbc.queryForList("""
                        SELECT id FROM subscription_billing_profiles
                         WHERE subscription_id = ? AND organization_id IS NULL AND status = 'ACTIVE'
                        """, UUID.class, subscriptionId)
                : jdbc.queryForList("""
                        SELECT id FROM subscription_billing_profiles
                         WHERE subscription_id = ? AND organization_id = ? AND status = 'ACTIVE'
                        """, UUID.class, subscriptionId, organizationId);

        UUID id = existing.isEmpty() ? UUID.randomUUID() : existing.get(0);
        Instant now = Instant.now();
        if (existing.isEmpty()) {
            jdbc.update("""
                    INSERT INTO subscription_billing_profiles
                        (id, tenant_id, subscription_id, organization_id, profile_name,
                         billing_email, currency_code, billing_mode, status, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?)
                    """,
                    id, tenantId, subscriptionId, organizationId, profileName.trim(),
                    blankToNull(billingEmail), currency, mode, Timestamp.from(now), Timestamp.from(now));
        } else {
            jdbc.update("""
                    UPDATE subscription_billing_profiles
                       SET profile_name = ?, billing_email = ?, currency_code = ?,
                           billing_mode = ?, updated_at = ?
                     WHERE id = ? AND tenant_id = ?
                    """,
                    profileName.trim(), blankToNull(billingEmail), currency, mode,
                    Timestamp.from(now), id, tenantId);
        }

        audit.success(authentication, tenantId, "SUBSCRIPTION_BILLING_PROFILE_UPSERTED",
                "SUBSCRIPTION_BILLING_PROFILE", id.toString(),
                "subscription=" + subscriptionId + ";organization=" + organizationId,
                null, null);
        return billingProfile(id, tenantId);
    }

    @Transactional
    public void bindResource(
            UUID subscriptionId,
            UUID organizationId,
            String resourceType,
            UUID resourceId,
            Authentication authentication
    ) {
        UUID tenantId = tenantId(subscriptionId);
        requireActiveBinding(tenantId, subscriptionId, organizationId);
        String type = normalizeResourceType(resourceType);
        requireResourceOwnership(tenantId, type, resourceId);

        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO subscription_resource_bindings
                    (id, tenant_id, subscription_id, organization_id, resource_type,
                     resource_id, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?)
                ON CONFLICT (subscription_id, resource_type, resource_id)
                DO UPDATE SET organization_id = EXCLUDED.organization_id,
                              status = 'ACTIVE',
                              updated_at = EXCLUDED.updated_at
                """,
                UUID.randomUUID(), tenantId, subscriptionId, organizationId,
                type, resourceId, Timestamp.from(now), Timestamp.from(now));

        if ("WEBSITE".equals(type)) {
            jdbc.update("UPDATE websites SET organization_id = ? WHERE tenant_id = ? AND id = ?",
                    organizationId, tenantId, resourceId);
        } else if ("STORE".equals(type)) {
            jdbc.update("UPDATE commerce_stores SET organization_id = ? WHERE tenant_id = ? AND id = ?",
                    organizationId, tenantId, resourceId);
        }

        audit.success(authentication, tenantId, "SUBSCRIPTION_RESOURCE_BOUND",
                type, resourceId.toString(),
                "subscription=" + subscriptionId + ";organization=" + organizationId,
                null, null);
    }

    @Transactional(readOnly = true)
    public int activeBranchCount(UUID subscriptionId) {
        UUID tenantId = tenantId(subscriptionId);
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM subscription_operating_units sou
                  JOIN organizations o
                    ON o.tenant_id = sou.tenant_id
                   AND o.id = sou.organization_id
                 WHERE sou.tenant_id = ?
                   AND sou.subscription_id = ?
                   AND sou.status = 'ACTIVE'
                   AND o.status = 'ACTIVE'
                   AND o.unit_type = 'BRANCH'
                """, Integer.class, tenantId, subscriptionId);
        return count == null ? 0 : count;
    }

    private OperatingUnit find(UUID subscriptionId, UUID organizationId) {
        return list(subscriptionId).stream()
                .filter(unit -> unit.organizationId().equals(organizationId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "operating-unit binding not found"));
    }

    private BillingProfile billingProfile(UUID id, UUID tenantId) {
        return jdbc.queryForObject("""
                SELECT id, organization_id, profile_name, billing_email, currency_code,
                       billing_mode, status
                  FROM subscription_billing_profiles
                 WHERE id = ? AND tenant_id = ?
                """,
                (rs, rowNum) -> new BillingProfile(
                        rs.getObject("id", UUID.class),
                        rs.getObject("organization_id", UUID.class),
                        rs.getString("profile_name"),
                        rs.getString("billing_email"),
                        rs.getString("currency_code"),
                        rs.getString("billing_mode"),
                        rs.getString("status")),
                id, tenantId);
    }

    private UUID tenantId(UUID subscriptionId) {
        List<UUID> rows = jdbc.queryForList(
                "SELECT tenant_id FROM tenant_subscriptions WHERE id = ?",
                UUID.class, subscriptionId);
        if (rows.size() != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "subscription not found");
        }
        return rows.get(0);
    }

    private void requireOrganization(UUID tenantId, UUID organizationId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM organizations WHERE tenant_id = ? AND id = ? AND status = 'ACTIVE'",
                Integer.class, tenantId, organizationId);
        if (count == null || count != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "active operating unit not found");
        }
    }

    private void requireActiveBinding(UUID tenantId, UUID subscriptionId, UUID organizationId) {
        requireOrganization(tenantId, organizationId);
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM subscription_operating_units
                 WHERE tenant_id = ? AND subscription_id = ?
                   AND organization_id = ? AND status = 'ACTIVE'
                """, Integer.class, tenantId, subscriptionId, organizationId);
        if (count == null || count != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "operating unit is not active on subscription");
        }
    }

    private void requireApplicationEntitled(UUID subscriptionId, UUID applicationId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM subscription_items si
                 WHERE si.subscription_id = ?
                   AND si.status = 'ACTIVE'
                   AND (si.application_id = ?
                        OR si.product_id IN (
                            SELECT p.id FROM products p WHERE p.application_id = ?
                        ))
                """, Integer.class, subscriptionId, applicationId, applicationId);
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "application is not entitled by this subscription");
        }
    }

    private void requireResourceOwnership(UUID tenantId, String type, UUID resourceId) {
        String table = switch (type) {
            case "WEBSITE" -> "websites";
            case "STORE" -> "commerce_stores";
            case "POS_LOCATION" -> null;
            default -> null;
        };
        if (table == null) {
            // POS business tables are intentionally not created before the POS
            // module gate. The generic binding model reserves the contract, but
            // runtime binding must fail closed until that module exists.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "POS location binding is unavailable until the POS module is active");
        }
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE tenant_id = ? AND id = ?",
                Integer.class, tenantId, resourceId);
        if (count == null || count != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "resource not found in tenant");
        }
    }

    private static String normalizeBillingMode(String value) {
        String mode = value == null ? "CONSOLIDATED" : value.trim().toUpperCase();
        if (!List.of("CONSOLIDATED", "SEPARATE").contains(mode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported billingMode");
        }
        return mode;
    }

    private static String normalizeResourceType(String value) {
        String type = value == null ? "" : value.trim().toUpperCase();
        if (!List.of("WEBSITE", "STORE", "POS_LOCATION").contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported resourceType");
        }
        return type;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
