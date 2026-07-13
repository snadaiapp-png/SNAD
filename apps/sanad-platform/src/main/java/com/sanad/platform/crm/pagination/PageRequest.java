package com.sanad.platform.crm.pagination;

import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;

import java.util.Set;

/**
 * CRM API Contract — Stable-Sort Validator + Page Request.
 * <p>
 * Enforces the contract rules for list endpoints:
 *   - {@code limit} defaults to 50, clamped to [1, 200].
 *   - {@code sort} must be in the endpoint's allowed sort field whitelist
 *     (prevents SQL injection via the {@code sort} query parameter).
 *   - {@code direction} must be {@code asc} or {@code desc} (case-insensitive).
 *   - The combined {@code (sort, id)} ordering is always stable — see
 *     {@link #stableOrderByClause(String, String)} which emits
 *     {@code ORDER BY <sort> <dir>, id <dir>}.
 * <p>
 * Branch: crm/003-stable-api-contracts
 */
public class PageRequest {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 200;
    public static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "updatedAt", "createdAt", "displayName", "name", "status", "amount", "priority");

    private final int limit;
    private final String cursor;
    private final String sort;
    private final String direction;

    public PageRequest(Integer limit, String cursor, String sort, String direction) {
        this.limit = clampLimit(limit);
        this.cursor = cursor;
        this.sort = normalizeSort(sort);
        this.direction = normalizeDirection(direction);
    }

    public int limit() { return limit; }
    public String cursor() { return cursor; }
    public String sort() { return sort; }
    public String direction() { return direction; }
    public boolean hasCursor() { return cursor != null && !cursor.isBlank(); }

    /**
     * Emit a safe, stable {@code ORDER BY} clause. The sort field is
     * validated against {@link #ALLOWED_SORT_FIELDS} so an attacker cannot
     * inject SQL via the {@code sort} parameter. The tie-breaker on
     * {@code id} guarantees stability when the sort field has ties.
     */
    public String stableOrderByClause() {
        // snake_case column name — the DB layer uses snake_case.
        String column = camelToSnake(sort);
        return "ORDER BY " + column + " " + direction.toUpperCase() + ", id " + direction.toUpperCase();
    }

    private static int clampLimit(Integer requested) {
        if (requested == null) return DEFAULT_LIMIT;
        if (requested < 1) return 1;
        return Math.min(requested, MAX_LIMIT);
    }

    private static String normalizeSort(String sort) {
        if (sort == null || sort.isBlank()) return "updatedAt";
        String trimmed = sort.trim();
        // Reject any non-alpha character to prevent SQL injection even if
        // the whitelist check is somehow bypassed.
        if (!trimmed.matches("[a-zA-Z]+")) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR,
                    "Sort field must be a single identifier.");
        }
        if (!ALLOWED_SORT_FIELDS.contains(trimmed)) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR,
                    "Sort field '" + trimmed + "' is not allowed. Allowed: " + ALLOWED_SORT_FIELDS);
        }
        return trimmed;
    }

    private static String normalizeDirection(String direction) {
        if (direction == null || direction.isBlank()) return "desc";
        String trimmed = direction.trim().toLowerCase();
        if (!trimmed.equals("asc") && !trimmed.equals("desc")) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR,
                    "Direction must be 'asc' or 'desc'.");
        }
        return trimmed;
    }

    private static String camelToSnake(String camel) {
        StringBuilder sb = new StringBuilder(camel.length() + 4);
        for (int i = 0; i < camel.length(); i++) {
            char c = camel.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) sb.append('_');
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
