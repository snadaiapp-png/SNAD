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
                            java.time.Instant createdAt) {
    }

    private final JdbcTemplate jdbc;

    public TenantDirectoryQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
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

        List<Map<String, Object>> rows = jdbc.queryForList("""
                        SELECT t.id, t.name, t.subdomain AS code, t.status, t.country_code, t.currency_code,
                               t.created_at,
                               (SELECT COUNT(*) FROM tenant_subscriptions s WHERE s.tenant_id = t.id) AS subscription_count,
                               (SELECT s.status FROM tenant_subscriptions s WHERE s.tenant_id = t.id
                                ORDER BY s.created_at DESC LIMIT 1) AS subscription_status
                        FROM tenants t
                        """ + where
                        + " ORDER BY t." + sortColumn + " " + sortDirection
                        + " LIMIT ? OFFSET ?",
                append(args, List.of(safeSize, safePage * safeSize)));

        List<TenantRow> content = rows.stream()
                .map(r -> new TenantRow(
                        (UUID) r.get("id"),
                        (String) r.get("name"),
                        (String) r.get("code"),
                        (String) r.get("status"),
                        (String) r.get("country_code"),
                        (String) r.get("currency_code"),
                        ((Number) r.getOrDefault("subscription_count", 0)).intValue(),
                        (String) r.get("subscription_status"),
                        r.get("created_at") == null ? null
                                : ((java.sql.Timestamp) r.get("created_at")).toInstant()))
                .toList();

        return PageResponse.of(content, safePage, safeSize, total == null ? 0 : total);
    }

    private static List<Object> append(List<Object> base, List<Object> extra) {
        List<Object> all = new ArrayList<>(base);
        all.addAll(extra);
        return all;
    }
}
