package com.sanad.platform.subscription.audit;

import com.sanad.platform.subscription.read.PageResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Paginated read access to {@code platform_audit_logs} for the executive
 * console. Read-only; writes go through {@code PlatformAuditWriter} only.
 */
@Service
public class AuditQueryService {

    /** Whitelisted sort columns — never interpolate unvalidated input into SQL. */
    private static final Set<String> SORTABLE = Set.of("created_at", "action", "resource_type");

    private final JdbcTemplate jdbc;

    public AuditQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> query(UUID targetTenantId, String action,
                                                   String resourceType, int page, int size,
                                                   String sort, String direction) {
        int safeSize = Math.min(Math.max(size, 1), 200);
        int safePage = Math.max(page, 0);
        String sortColumn = SORTABLE.contains(sort) ? sort : "created_at";
        String sortDirection = "ASC".equalsIgnoreCase(direction) ? "ASC" : "DESC";

        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");
        List<Object> filterArgs = new java.util.ArrayList<>();
        if (targetTenantId != null) {
            where.append(" AND target_tenant_id = ? ");
            filterArgs.add(targetTenantId);
        }
        if (action != null && !action.isBlank()) {
            where.append(" AND action = ? ");
            filterArgs.add(action);
        }
        if (resourceType != null && !resourceType.isBlank()) {
            where.append(" AND resource_type = ? ");
            filterArgs.add(resourceType);
        }

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM platform_audit_logs" + where, Long.class,
                filterArgs.toArray());

        String orderSql = " ORDER BY " + sortColumn + " " + sortDirection
                + " LIMIT ? OFFSET ?";
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, actor_tenant_id, actor_user_id, target_tenant_id, action, "
                        + "resource_type, resource_id, reason, result, correlation_id, created_at"
                        + " FROM platform_audit_logs" + where + orderSql,
                appendAll(filterArgs, List.of(safeSize, safePage * safeSize)).toArray());

        return PageResponse.of(rows, safePage, safeSize, total == null ? 0 : total);
    }

    private static List<Object> appendAll(List<Object> base, List<Object> extra) {
        List<Object> all = new java.util.ArrayList<>(base);
        all.addAll(extra);
        return all;
    }
}
