package com.sanad.platform.subscription.read;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Paginated subscription grid read model — one row per subscription with
 * tenant, plan, items, billing and trial columns. Server-side filtering,
 * search, sorting and pagination (replaces the legacy load-everything call).
 */
@Service
public class SubscriptionGridQueryService {

    private static final Set<String> SORTABLE = Set.of(
            "created_at", "status", "current_period_end", "tenant_name", "trial_ends_at");

    public record SubscriptionRow(
            UUID id, UUID tenantId, String tenantName, String tenantCountry,
            String status, String billingCycle, int seatQuantity,
            UUID planId, String planName, String planCode, String planVersion,
            String currencyCode, Long monthlyPriceMinor, int itemCount,
            boolean trial, boolean cancelAtPeriodEnd) {
    }

    private final JdbcTemplate jdbc;

    public SubscriptionGridQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public PageResponse<SubscriptionRow> search(UUID tenantId, String status,
                                                String countryCode, String search,
                                                boolean trialOnly,
                                                int page, int size,
                                                String sort, String direction) {
        int safeSize = Math.min(Math.max(size, 1), 200);
        int safePage = Math.max(page, 0);
        String sortColumn = sort != null && SORTABLE.contains(sort) ? sort : "created_at";
        String sortDirection = "ASC".equalsIgnoreCase(direction) ? "ASC" : "DESC";

        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");
        List<Object> args = new ArrayList<>();
        if (tenantId != null) {
            where.append(" AND s.tenant_id = ? ");
            args.add(tenantId);
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND s.status = ? ");
            args.add(status.trim().toUpperCase());
        }
        if (countryCode != null && !countryCode.isBlank()) {
            where.append(" AND t.country_code = ? ");
            args.add(countryCode.trim().toUpperCase());
        }
        if (trialOnly) {
            where.append(" AND s.status IN ('TRIAL', 'TRIALING') ");
        }
        if (search != null && !search.isBlank()) {
            where.append(" AND (t.name ILIKE ? OR t.subdomain ILIKE ? OR p.name ILIKE ?) ");
            String pattern = "%" + search.trim() + "%";
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
        }

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tenant_subscriptions s "
                        + "JOIN tenants t ON t.id = s.tenant_id "
                        + "LEFT JOIN saas_plans p ON p.id = s.plan_id" + where,
                Long.class, args.toArray());

        String orderColumn = switch (sortColumn) {
            case "tenant_name" -> "t.name";
            default -> "s." + sortColumn;
        };

        List<Map<String, Object>> rows = jdbc.queryForList("""
                        SELECT s.id, s.tenant_id, t.name AS tenant_name, t.country_code,
                               s.status, s.billing_cycle, s.seat_quantity,
                               s.plan_id, p.name AS plan_name, p.code AS plan_code,
                               pv.version_number AS plan_version, p.currency_code,
                               CASE s.billing_cycle WHEN 'ANNUAL' THEN pv.annual_price_minor / 12
                                                    ELSE pv.monthly_price_minor END AS monthly_price_minor,
                               (SELECT COUNT(*) FROM subscription_items si
                                 WHERE si.subscription_id = s.id AND si.status = 'ACTIVE') AS item_count,
                               (s.status IN ('TRIAL', 'TRIALING')) AS trial,
                               s.cancel_at_period_end
                        FROM tenant_subscriptions s
                        JOIN tenants t ON t.id = s.tenant_id
                        LEFT JOIN saas_plans p ON p.id = s.plan_id
                        LEFT JOIN plan_versions pv ON pv.id = s.plan_version_id
                        """ + where
                        + " ORDER BY " + orderColumn + " " + sortDirection
                        + " LIMIT ? OFFSET ?",
                append(args, List.of(safeSize, safePage * safeSize)));

        List<SubscriptionRow> content = rows.stream().map(r -> new SubscriptionRow(
                (UUID) r.get("id"),
                (UUID) r.get("tenant_id"),
                (String) r.get("tenant_name"),
                (String) r.get("country_code"),
                (String) r.get("status"),
                (String) r.get("billing_cycle"),
                ((Number) r.getOrDefault("seat_quantity", 0)).intValue(),
                (UUID) r.get("plan_id"),
                (String) r.get("plan_name"),
                (String) r.get("plan_code"),
                r.get("plan_version") == null ? null
                        : "v" + ((Number) r.get("plan_version")).intValue(),
                (String) r.get("currency_code"),
                r.get("monthly_price_minor") == null ? null
                        : ((Number) r.get("monthly_price_minor")).longValue(),
                ((Number) r.getOrDefault("item_count", 0)).intValue(),
                Boolean.TRUE.equals(r.get("trial")),
                Boolean.TRUE.equals(r.get("cancel_at_period_end")))).toList();

        return PageResponse.of(content, safePage, safeSize, total == null ? 0 : total);
    }

    private static List<Object> append(List<Object> base, List<Object> extra) {
        List<Object> all = new ArrayList<>(base);
        all.addAll(extra);
        return all;
    }
}
