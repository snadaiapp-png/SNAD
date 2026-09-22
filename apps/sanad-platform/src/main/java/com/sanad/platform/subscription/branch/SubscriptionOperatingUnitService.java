package com.sanad.platform.subscription.branch;

import com.sanad.platform.admin.service.PlatformAuditService;
import com.sanad.platform.security.rls.TenantRlsTransactionContext;
import com.sanad.platform.module.entitlement.EntitlementResolver;
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
import java.util.Set;
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

    private static final Set<String> MUTABLE_SUBSCRIPTION_STATUSES =
            Set.of("TRIALING", "TRIAL", "ACTIVE", "PAST_DUE", "GRACE_PERIOD");

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

    public record UnitApplication(
            UUID applicationId,
            String applicationCode,
            String applicationName,
            boolean enabled
    ) {}

    public record ResourceBinding(
            UUID organizationId,
            String resourceType,
            UUID resourceId,
            String resourceName,
            String status
    ) {}

    public record AvailableResource(
            String resourceType,
            UUID resourceId,
            String resourceName,
            String resourceStatus,
            UUID boundOrganizationId
    ) {}

    private final JdbcTemplate jdbc;
    private final PlatformAuditService audit;
    private final TenantRlsTransactionContext tenantRlsContext;
    private final EntitlementResolver entitlementResolver;

    public SubscriptionOperatingUnitService(
            JdbcTemplate jdbc,
            PlatformAuditService audit,
            TenantRlsTransactionContext tenantRlsContext,
            EntitlementResolver entitlementResolver
    ) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.tenantRlsContext = tenantRlsContext;
        this.entitlementResolver = entitlementResolver;
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

    @Transactional(readOnly = true)
    public List<UnitApplication> listApplications(UUID subscriptionId, UUID organizationId) {
        UUID tenantId = tenantId(subscriptionId);
        requireActiveBinding(tenantId, subscriptionId, organizationId);
        return jdbc.query("""
                SELECT a.id, a.code, a.name,
                       COALESCE(sua.enabled, FALSE) AS enabled
                  FROM applications a
                  LEFT JOIN subscription_unit_applications sua
                    ON sua.tenant_id = ?
                   AND sua.subscription_id = ?
                   AND sua.organization_id = ?
                   AND sua.application_id = a.id
                 WHERE a.status = 'ACTIVE'
                   AND (
                       EXISTS (
                           SELECT 1
                             FROM subscription_items si
                            WHERE si.subscription_id = ?
                              AND si.status = 'ACTIVE'
                              AND (si.application_id = a.id
                                   OR si.product_id IN (
                                       SELECT p.id FROM products p WHERE p.application_id = a.id
                                   ))
                       )
                       OR EXISTS (
                           SELECT 1
                             FROM tenant_subscriptions ts
                             JOIN plan_module_entitlements pme
                               ON pme.plan_id = ts.plan_id
                              AND pme.module_enabled = TRUE
                             JOIN modules m ON m.id = pme.module_id
                            WHERE ts.id = ?
                              AND upper(m.code) = upper(a.code)
                       )
                   )
                 ORDER BY a.display_order, a.code
                """,
                (rs, rowNum) -> new UnitApplication(
                        rs.getObject("id", UUID.class),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getBoolean("enabled")),
                tenantId, subscriptionId, organizationId,
                subscriptionId, subscriptionId);
    }

    @Transactional(readOnly = true)
    public List<BillingProfile> listBillingProfiles(UUID subscriptionId) {
        UUID tenantId = tenantId(subscriptionId);
        return jdbc.query("""
                SELECT id, organization_id, profile_name, billing_email, currency_code,
                       billing_mode, status
                  FROM subscription_billing_profiles
                 WHERE tenant_id = ? AND subscription_id = ?
                 ORDER BY status DESC, organization_id NULLS FIRST, profile_name
                """,
                (rs, rowNum) -> new BillingProfile(
                        rs.getObject("id", UUID.class),
                        rs.getObject("organization_id", UUID.class),
                        rs.getString("profile_name"),
                        rs.getString("billing_email"),
                        rs.getString("currency_code"),
                        rs.getString("billing_mode"),
                        rs.getString("status")),
                tenantId, subscriptionId);
    }

    @Transactional(readOnly = true)
    public List<ResourceBinding> listResourceBindings(UUID subscriptionId) {
        UUID tenantId = tenantId(subscriptionId);
        return jdbc.query("""
                SELECT srb.organization_id, srb.resource_type, srb.resource_id, srb.status,
                       CASE srb.resource_type
                           WHEN 'WEBSITE' THEN (SELECT w.name FROM websites w
                                                WHERE w.tenant_id = srb.tenant_id
                                                  AND w.id = srb.resource_id)
                           WHEN 'STORE' THEN (SELECT s.name FROM commerce_stores s
                                              WHERE s.tenant_id = srb.tenant_id
                                                AND s.id = srb.resource_id)
                           ELSE NULL
                       END AS resource_name
                  FROM subscription_resource_bindings srb
                 WHERE srb.tenant_id = ? AND srb.subscription_id = ?
                 ORDER BY srb.status DESC, srb.resource_type, resource_name, srb.resource_id
                """,
                (rs, rowNum) -> new ResourceBinding(
                        rs.getObject("organization_id", UUID.class),
                        rs.getString("resource_type"),
                        rs.getObject("resource_id", UUID.class),
                        rs.getString("resource_name"),
                        rs.getString("status")),
                tenantId, subscriptionId);
    }

    @Transactional(readOnly = true)
    public List<AvailableResource> listAvailableResources(UUID subscriptionId) {
        UUID tenantId = tenantId(subscriptionId);
        boolean websitesEnabled = entitlementResolver.isModuleEnabled(tenantId, "WEBSITES");
        boolean storesEnabled = entitlementResolver.isModuleEnabled(tenantId, "ECOMMERCE_CX");
        List<AvailableResource> resources = jdbc.query("""
                SELECT resource_type, resource_id, resource_name, resource_status,
                       bound_organization_id
                  FROM (
                        SELECT 'WEBSITE' AS resource_type, w.id AS resource_id,
                               w.name AS resource_name, w.status AS resource_status,
                               srb.organization_id AS bound_organization_id
                          FROM websites w
                          LEFT JOIN subscription_resource_bindings srb
                            ON srb.tenant_id = w.tenant_id
                           AND srb.resource_type = 'WEBSITE'
                           AND srb.resource_id = w.id
                           AND srb.status = 'ACTIVE'
                         WHERE w.tenant_id = ? AND w.status <> 'ARCHIVED'
                        UNION ALL
                        SELECT 'STORE' AS resource_type, s.id AS resource_id,
                               s.name AS resource_name, s.status AS resource_status,
                               srb.organization_id AS bound_organization_id
                          FROM commerce_stores s
                          LEFT JOIN subscription_resource_bindings srb
                            ON srb.tenant_id = s.tenant_id
                           AND srb.resource_type = 'STORE'
                           AND srb.resource_id = s.id
                           AND srb.status = 'ACTIVE'
                         WHERE s.tenant_id = ? AND s.status <> 'ARCHIVED'
                  ) resources
                 ORDER BY resource_type, resource_name, resource_id
                """,
                (rs, rowNum) -> new AvailableResource(
                        rs.getString("resource_type"),
                        rs.getObject("resource_id", UUID.class),
                        rs.getString("resource_name"),
                        rs.getString("resource_status"),
                        rs.getObject("bound_organization_id", UUID.class)),
                tenantId, tenantId);
        return resources.stream()
                .filter(resource -> ("WEBSITE".equals(resource.resourceType()) && websitesEnabled)
                        || ("STORE".equals(resource.resourceType()) && storesEnabled))
                .toList();
    }

    @Transactional
    public OperatingUnit bind(
            UUID subscriptionId,
            UUID organizationId,
            String billingMode,
            Authentication authentication
    ) {
        UUID tenantId = mutableTenantId(subscriptionId);
        requireOrganization(tenantId, organizationId);
        String mode = normalizeBillingMode(billingMode);
        if ("SEPARATE".equals(mode)) {
            Integer profileCount = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM subscription_billing_profiles
                     WHERE tenant_id = ? AND subscription_id = ?
                       AND organization_id = ? AND status = 'ACTIVE'
                    """, Integer.class, tenantId, subscriptionId, organizationId);
            if (profileCount == null || profileCount != 1) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "SEPARATE billing requires one active operating-unit billing profile");
            }
        }
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

        if ("CONSOLIDATED".equals(mode)) {
            jdbc.update("""
                    UPDATE subscription_billing_profiles
                       SET status = 'INACTIVE', updated_at = NOW()
                     WHERE tenant_id = ? AND subscription_id = ?
                       AND organization_id = ? AND status = 'ACTIVE'
                    """, tenantId, subscriptionId, organizationId);
        }

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
        UUID tenantId = mutableTenantId(subscriptionId);
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
        // Keep resource ownership projection consistent with the binding ledger.
        // A deactivated branch must not remain attached to websites or stores.
        jdbc.update("""
                UPDATE websites
                   SET organization_id = NULL
                 WHERE tenant_id = ? AND organization_id = ?
                   AND id IN (
                       SELECT resource_id FROM subscription_resource_bindings
                        WHERE tenant_id = ? AND subscription_id = ?
                          AND organization_id = ? AND resource_type = 'WEBSITE'
                          AND status = 'ACTIVE'
                   )
                """, tenantId, organizationId, tenantId, subscriptionId, organizationId);
        jdbc.update("""
                UPDATE commerce_stores
                   SET organization_id = NULL
                 WHERE tenant_id = ? AND organization_id = ?
                   AND id IN (
                       SELECT resource_id FROM subscription_resource_bindings
                        WHERE tenant_id = ? AND subscription_id = ?
                          AND organization_id = ? AND resource_type = 'STORE'
                          AND status = 'ACTIVE'
                   )
                """, tenantId, organizationId, tenantId, subscriptionId, organizationId);
        jdbc.update("""
                UPDATE subscription_resource_bindings
                   SET status = 'INACTIVE', updated_at = NOW()
                 WHERE tenant_id = ? AND subscription_id = ? AND organization_id = ?
                """, tenantId, subscriptionId, organizationId);
        jdbc.update("""
                UPDATE subscription_billing_profiles
                   SET status = 'INACTIVE', updated_at = NOW()
                 WHERE tenant_id = ? AND subscription_id = ? AND organization_id = ?
                   AND status = 'ACTIVE'
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
        UUID tenantId = mutableTenantId(subscriptionId);
        requireActiveBinding(tenantId, subscriptionId, organizationId);
        if (enabled) {
            requireApplicationEntitled(subscriptionId, applicationId);
        }
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
        SubscriptionScope scope = mutableSubscriptionScope(subscriptionId);
        UUID tenantId = scope.tenantId();
        if (organizationId != null) {
            requireActiveBinding(tenantId, subscriptionId, organizationId);
        }
        String mode = normalizeBillingMode(billingMode);
        if (organizationId == null && !"CONSOLIDATED".equals(mode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "tenant billing profile must use CONSOLIDATED mode");
        }
        if (organizationId != null && !"SEPARATE".equals(mode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "operating-unit billing profile must use SEPARATE mode");
        }
        String currency = currencyCode == null ? "" : currencyCode.trim().toUpperCase();
        if (!currency.matches("^[A-Z]{3}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "currencyCode must be ISO-4217");
        }
        if (scope.currencyCode() == null || !currency.equals(scope.currencyCode())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "billing profile currency must match the subscription pinned currency");
        }
        if (profileName == null || profileName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "profileName is required");
        }
        if (profileName.trim().length() > 160) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "profileName is too long");
        }
        String normalizedEmail = blankToNull(billingEmail);
        if (normalizedEmail != null
                && (normalizedEmail.length() > 255
                    || !normalizedEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "billingEmail is invalid");
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
                    normalizedEmail, currency, mode, Timestamp.from(now), Timestamp.from(now));
        } else {
            jdbc.update("""
                    UPDATE subscription_billing_profiles
                       SET profile_name = ?, billing_email = ?, currency_code = ?,
                           billing_mode = ?, updated_at = ?
                     WHERE id = ? AND tenant_id = ?
                    """,
                    profileName.trim(), normalizedEmail, currency, mode,
                    Timestamp.from(now), id, tenantId);
        }

        if (organizationId != null) {
            jdbc.update("""
                    UPDATE subscription_operating_units
                       SET billing_mode = 'SEPARATE', updated_at = NOW()
                     WHERE tenant_id = ? AND subscription_id = ?
                       AND organization_id = ? AND status = 'ACTIVE'
                    """, tenantId, subscriptionId, organizationId);
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
        UUID tenantId = mutableTenantId(subscriptionId);
        requireActiveBinding(tenantId, subscriptionId, organizationId);
        String type = normalizeResourceType(resourceType);
        requireResourceEntitlement(tenantId, type);
        requireResourceOwnership(tenantId, type, resourceId);
        Integer conflicting = jdbc.queryForObject("""
                SELECT COUNT(*) FROM subscription_resource_bindings
                 WHERE tenant_id = ? AND resource_type = ? AND resource_id = ?
                   AND status = 'ACTIVE' AND subscription_id <> ?
                """, Integer.class, tenantId, type, resourceId, subscriptionId);
        if (conflicting != null && conflicting > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "resource is already bound to another active subscription");
        }

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

    @Transactional
    public void unbindResource(
            UUID subscriptionId,
            String resourceType,
            UUID resourceId,
            Authentication authentication
    ) {
        UUID tenantId = mutableTenantId(subscriptionId);
        String type = normalizeResourceType(resourceType);
        List<UUID> organizations = jdbc.queryForList("""
                SELECT organization_id
                  FROM subscription_resource_bindings
                 WHERE tenant_id = ? AND subscription_id = ?
                   AND resource_type = ? AND resource_id = ?
                   AND status = 'ACTIVE'
                """, UUID.class, tenantId, subscriptionId, type, resourceId);
        if (organizations.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "active resource binding not found");
        }
        UUID organizationId = organizations.get(0);
        jdbc.update("""
                UPDATE subscription_resource_bindings
                   SET status = 'INACTIVE', updated_at = NOW()
                 WHERE tenant_id = ? AND subscription_id = ?
                   AND resource_type = ? AND resource_id = ?
                   AND status = 'ACTIVE'
                """, tenantId, subscriptionId, type, resourceId);

        if ("WEBSITE".equals(type)) {
            jdbc.update("""
                    UPDATE websites SET organization_id = NULL
                     WHERE tenant_id = ? AND id = ? AND organization_id = ?
                    """, tenantId, resourceId, organizationId);
        } else if ("STORE".equals(type)) {
            jdbc.update("""
                    UPDATE commerce_stores SET organization_id = NULL
                     WHERE tenant_id = ? AND id = ? AND organization_id = ?
                    """, tenantId, resourceId, organizationId);
        }

        audit.success(authentication, tenantId, "SUBSCRIPTION_RESOURCE_UNBOUND",
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

    private record SubscriptionScope(UUID tenantId, String status, String currencyCode) {}

    private UUID tenantId(UUID subscriptionId) {
        SubscriptionScope scope = subscriptionScope(subscriptionId);
        tenantRlsContext.applyForCurrentTransaction(scope.tenantId());
        return scope.tenantId();
    }

    private UUID mutableTenantId(UUID subscriptionId) {
        return mutableSubscriptionScope(subscriptionId).tenantId();
    }

    private SubscriptionScope mutableSubscriptionScope(UUID subscriptionId) {
        SubscriptionScope scope = subscriptionScope(subscriptionId);
        if (!MUTABLE_SUBSCRIPTION_STATUSES.contains(scope.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "terminal subscription cannot be mutated");
        }
        tenantRlsContext.applyForCurrentTransaction(scope.tenantId());
        return scope;
    }

    private SubscriptionScope subscriptionScope(UUID subscriptionId) {
        List<SubscriptionScope> rows = jdbc.query("""
                SELECT s.tenant_id, s.status,
                       COALESCE(pv.currency_code, p.currency_code) AS currency_code
                  FROM tenant_subscriptions s
                  LEFT JOIN plan_versions pv ON pv.id = s.plan_version_id
                  LEFT JOIN saas_plans p ON p.id = s.plan_id
                 WHERE s.id = ?
                """,
                (rs, rowNum) -> new SubscriptionScope(
                        rs.getObject("tenant_id", UUID.class),
                        rs.getString("status"),
                        rs.getString("currency_code")),
                subscriptionId);
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
                  FROM applications a
                 WHERE a.id = ?
                   AND a.status = 'ACTIVE'
                   AND (
                       EXISTS (
                           SELECT 1
                             FROM subscription_items si
                            WHERE si.subscription_id = ?
                              AND si.status = 'ACTIVE'
                              AND (si.application_id = a.id
                                   OR si.product_id IN (
                                       SELECT p.id FROM products p WHERE p.application_id = a.id
                                   ))
                       )
                       OR EXISTS (
                           SELECT 1
                             FROM tenant_subscriptions ts
                             JOIN plan_module_entitlements pme
                               ON pme.plan_id = ts.plan_id
                              AND pme.module_enabled = TRUE
                             JOIN modules m ON m.id = pme.module_id
                            WHERE ts.id = ?
                              AND upper(m.code) = upper(a.code)
                       )
                   )
                """, Integer.class, applicationId, subscriptionId, subscriptionId);
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "application is not entitled by this subscription");
        }
    }

    private void requireResourceEntitlement(UUID tenantId, String type) {
        String moduleCode = switch (type) {
            case "WEBSITE" -> "WEBSITES";
            case "STORE" -> "ECOMMERCE_CX";
            case "POS_LOCATION" -> "POS";
            default -> null;
        };
        if (moduleCode == null || !entitlementResolver.isModuleEnabled(tenantId, moduleCode)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "resource module is not entitled by the effective subscription");
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
                "SELECT COUNT(*) FROM " + table
                        + " WHERE tenant_id = ? AND id = ? AND status <> 'ARCHIVED'",
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
