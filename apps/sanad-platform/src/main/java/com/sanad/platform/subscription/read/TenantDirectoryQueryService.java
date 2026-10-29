package com.sanad.platform.subscription.read;

import com.sanad.platform.security.rls.TenantRlsTransactionContext;
import com.sanad.platform.subscription.commercial.TenantCommercialStateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Paginated, searchable tenant directory for the executive console — replaces
 * the legacy load-everything pattern. Sort columns are whitelisted.
 */
@Service
public class TenantDirectoryQueryService {

    private static final Set<String> SORTABLE =
            Set.of("name", "subdomain", "status", "country_code", "created_at");

    public record TenantRow(UUID id, String name, String code, String status,
                            String countryCode, String currencyCode,
                            int subscriptionCount, String subscriptionStatus,
                            String applicationHostname,
                            UUID effectiveSubscriptionId, String billingState,
                            String accessDecision, String commercialAction,
                            String anomalyCode, boolean loginAllowed,
                            java.time.Instant createdAt) {
    }

    private final JdbcTemplate jdbc;
    private final TenantCommercialStateService commercialStateService;
    private final TransactionTemplate transactions;
    private final TenantRlsTransactionContext tenantRlsContext;

    @Autowired
    public TenantDirectoryQueryService(
            JdbcTemplate jdbc,
            TenantCommercialStateService commercialStateService,
            PlatformTransactionManager transactionManager,
            TenantRlsTransactionContext tenantRlsContext
    ) {
        this.jdbc = jdbc;
        this.commercialStateService = commercialStateService;
        this.transactions = new TransactionTemplate(transactionManager);
        this.tenantRlsContext = tenantRlsContext;
    }

    /**
     * Backward-compatible direct-test constructor. Production Spring wiring
     * always uses the canonical commercial-state service above. In this mode
     * the per-tenant FORCE RLS enrichment degrades to the raw SQL subqueries
     * (no transaction manager is available to scope a GUC).
     */
    public TenantDirectoryQueryService(JdbcTemplate jdbc) {
        this(jdbc, null, null, null);
    }

    /**
     * Two-argument harness constructor (commercial state wired, no FORCE RLS
     * scoping). Used by pure-JDBC unit harnesses that run against mocked or
     * non-PostgreSQL datasources where no transaction manager exists.
     */
    public TenantDirectoryQueryService(JdbcTemplate jdbc, TenantCommercialStateService commercialStateService) {
        this(jdbc, commercialStateService, null, null);
    }

    /**
     * Runs a per-tenant read inside a transaction-local {@code app.tenant_id}
     * scope. tenant_subscriptions / tenant_domains are fail-closed FORCE RLS
     * (V20261029_1): the executive console directory is a control-plane view
     * across tenants, so each tenant's commercial evidence is resolved through
     * the trusted scoped path — the tenant ids come from the platform tenant
     * directory (a non-RLS table), never from user input. Without a wired
     * transaction manager (direct-test harness) the action runs unscoped.
     */
    private <T> T scopedPerTenant(UUID tenantId, java.util.function.Supplier<T> action) {
        if (transactions == null || tenantRlsContext == null) {
            return action.get();
        }
        return transactions.execute(status -> {
            tenantRlsContext.applyForCurrentTransaction(tenantId);
            return action.get();
        });
    }

    @Transactional(readOnly = true)
    public PageResponse<TenantRow> search(String search, String status, String countryCode,
                                          int page, int size, String sort, String direction) {
        int safeSize = Math.min(Math.max(size, 1), 200);
        int safePage = Math.max(page, 0);
        // "code" is an accepted alias that sorts by subdomain (the tenant code)
        String requestedSort = "code".equals(sort) ? "subdomain" : sort;
        String sortColumn = requestedSort != null && SORTABLE.contains(requestedSort)
                ? requestedSort : "name";
        String sortDirection = "ASC".equalsIgnoreCase(direction) ? "ASC" : "DESC";

        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");
        List<Object> args = new ArrayList<>();
        if (search != null && !search.isBlank()) {
            where.append(" AND (t.name ILIKE ? OR t.subdomain ILIKE ? OR t.legal_name ILIKE ?) ");
            String pattern = "%" + search.trim() + "%";
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND t.status = ? ");
            args.add(status.trim().toUpperCase());
        }
        if (countryCode != null && !countryCode.isBlank()) {
            where.append(" AND t.country_code = ? ");
            args.add(countryCode.trim().toUpperCase());
        }

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tenants t" + where, Long.class, args.toArray());

        // Current subscription state follows the same EFFECTIVE predicate as
        // SubscriptionResolutionService. Terminal rows are historical evidence
        // only and must never replace the tenant's current commercial state.
        List<Map<String, Object>> rows = jdbc.queryForList("""
                        SELECT t.id, t.name, t.subdomain AS code, t.status, t.country_code, t.currency_code,
                               t.created_at,
                               (SELECT COUNT(*) FROM tenant_subscriptions s WHERE s.tenant_id = t.id) AS subscription_count,
                               (SELECT s.status FROM tenant_subscriptions s WHERE s.tenant_id = t.id
                                  AND s.status NOT IN ('CANCELLED', 'EXPIRED', 'TERMINATED')
                                ORDER BY s.created_at DESC, s.id DESC LIMIT 1) AS subscription_status,
                               (SELECT d.hostname FROM tenant_domains d
                                WHERE d.tenant_id = t.id
                                  AND d.domain_type = 'APPLICATION'
                                  AND d.origin = 'DEFAULT_GENERATED'
                                  AND d.status = 'ACTIVE'
                                ORDER BY d.is_primary DESC, d.created_at ASC, d.id ASC
                                LIMIT 1) AS application_hostname
                        FROM tenants t
                        """ + where
                        + " ORDER BY t." + sortColumn + " " + sortDirection
                        + " LIMIT ? OFFSET ?",
                append(args, List.of(safeSize, safePage * safeSize)).toArray());

        List<TenantRow> content = rows.stream()
                .map(r -> {
                    UUID tenantId = (UUID) r.get("id");
                    String tenantStatus = (String) r.get("status");
                    boolean scoped = transactions != null && tenantRlsContext != null;
                    int subscriptionCount;
                    String applicationHostname;
                    String subscriptionStatus = (String) r.get("subscription_status");
                    if (scoped) {
                        // FORCE RLS (V20261029_1): resolve this tenant's
                        // subscription evidence through the trusted scoped path
                        // instead of the caller-ambient GUC (which cannot see
                        // OTHER tenants' rows).
                        subscriptionCount = scopedPerTenant(tenantId, () -> jdbc.queryForObject(
                                "SELECT COUNT(*) FROM tenant_subscriptions WHERE tenant_id = ?",
                                Integer.class, tenantId));
                        applicationHostname = scopedPerTenant(tenantId, () -> {
                            List<String> hostnames = jdbc.queryForList(
                                    "SELECT d.hostname FROM tenant_domains d " +
                                    "WHERE d.tenant_id = ? AND d.domain_type = 'APPLICATION' " +
                                    "  AND d.origin = 'DEFAULT_GENERATED' AND d.status = 'ACTIVE' " +
                                    "ORDER BY d.is_primary DESC, d.created_at ASC, d.id ASC LIMIT 1",
                                    String.class, tenantId);
                            return hostnames.isEmpty() ? null : hostnames.get(0);
                        });
                        if (commercialStateService == null) {
                            // Direct-harness mode still needs the effective
                            // subscription status, resolved under the same scope.
                            subscriptionStatus = scopedPerTenant(tenantId, () -> jdbc.queryForObject(
                                    "SELECT s.status FROM tenant_subscriptions s WHERE s.tenant_id = ? " +
                                    "  AND s.status NOT IN ('CANCELLED', 'EXPIRED', 'TERMINATED') " +
                                    "ORDER BY s.created_at DESC, s.id DESC LIMIT 1",
                                    String.class, tenantId));
                        }
                    } else {
                        subscriptionCount = ((Number) r.getOrDefault("subscription_count", 0)).intValue();
                        applicationHostname = (String) r.get("application_hostname");
                    }
                    TenantCommercialStateService.TenantCommercialState commercial = commercialStateService != null
                            ? scopedPerTenant(tenantId, () -> commercialStateService.resolve(tenantId, tenantStatus))
                            : directHarnessState(tenantId, tenantStatus, subscriptionStatus);
                    return new TenantRow(
                            tenantId,
                            (String) r.get("name"),
                            (String) r.get("code"),
                            tenantStatus,
                            (String) r.get("country_code"),
                            (String) r.get("currency_code"),
                            subscriptionCount,
                            commercial.effectiveSubscriptionStatus(),
                            applicationHostname,
                            commercial.effectiveSubscriptionId(),
                            commercial.billingState(),
                            commercial.accessDecision().name(),
                            commercial.commercialAction().name(),
                            commercial.anomalyCode(),
                            commercial.loginAllowed(),
                            r.get("created_at") == null ? null
                                    : ((java.sql.Timestamp) r.get("created_at")).toInstant());
                })
                .toList();

        return PageResponse.of(content, safePage, safeSize, total == null ? 0 : total);
    }

    private static TenantCommercialStateService.TenantCommercialState directHarnessState(
            UUID tenantId, String tenantStatus, String subscriptionStatus
    ) {
        boolean allowed = "ACTIVE".equals(tenantStatus) && "ACTIVE".equals(subscriptionStatus);
        return new TenantCommercialStateService.TenantCommercialState(
                tenantId,
                tenantStatus,
                null,
                subscriptionStatus,
                null,
                allowed
                        ? TenantCommercialStateService.AccessDecision.ACCESS_ALLOWED
                        : subscriptionStatus == null
                                ? TenantCommercialStateService.AccessDecision.NO_EFFECTIVE_SUBSCRIPTION
                                : TenantCommercialStateService.AccessDecision.BLOCKED_UNKNOWN_STATE,
                allowed
                        ? TenantCommercialStateService.CommercialAction.UPGRADE
                        : subscriptionStatus == null
                                ? TenantCommercialStateService.CommercialAction.CREATE_SUBSCRIPTION
                                : TenantCommercialStateService.CommercialAction.BLOCKED,
                allowed ? null : "DIRECT_TEST_HARNESS_FAIL_CLOSED",
                allowed);
    }

    private static List<Object> append(List<Object> base, List<Object> extra) {
        List<Object> all = new ArrayList<>(base);
        all.addAll(extra);
        return all;
    }
}
