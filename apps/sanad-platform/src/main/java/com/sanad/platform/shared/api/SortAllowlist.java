package com.sanad.platform.shared.api;

import com.sanad.platform.shared.api.exceptions.InvalidPaginationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Stage 03A §13 — Sort allowlist validator.
 *
 * <p>Each resource defines an allowlist of sortable fields. The validator
 * rejects:</p>
 * <ul>
 *   <li>Field not in the allowlist</li>
 *   <li>Direction other than {@code asc} or {@code desc}</li>
 *   <li>Nested property paths (e.g. {@code tenant.name})</li>
 *   <li>Sensitive fields (e.g. {@code passwordHash}, {@code email})</li>
 *   <li>SQL expressions or function calls</li>
 * </ul>
 *
 * <p>Spring's default behavior for {@code @ModelAttribute List<String> sort}
 * splits each {@code sort} parameter value on commas. So {@code ?sort=name,desc}
 * arrives as {@code ["name", "desc"]} (2 elements). To support BOTH
 * {@code ?sort=name,desc} and {@code ?sort=name&sort=desc} and
 * {@code ?sort=name,asc&sort=createdAt,desc}, this parser:</p>
 * <ol>
 *   <li>Iterates through the entries.</li>
 *   <li>If an entry contains a comma, splits it into (field, direction).</li>
 *   <li>If an entry is a direction keyword ("asc"/"desc"), pairs it with
 *       the previous entry as its field.</li>
 *   <li>Otherwise treats it as a field name with default direction "asc".</li>
 * </ol>
 */
public final class SortAllowlist {

    /** Sensitive field names that may never appear in a sort expression. */
    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "password", "passwordhash", "passwordhashstring",
            "secret", "token", "apikey", "credential", "credentials");

    /** Direction keywords recognized by the parser. */
    private static final Set<String> DIRECTIONS = Set.of("asc", "desc");

    private SortAllowlist() {}

    public static Pageable toPageable(PageRequestParams params, Set<String> allowed) {
        int page = params.page();
        int size = params.size();
        List<String> entries = params.sort();

        if (entries == null || entries.isEmpty()) {
            return PageRequest.of(page, size);
        }

        // Normalize entries: split on commas, but preserve field+direction pairs
        List<String[]> pairs = new ArrayList<>();
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) continue;
            // If the entry contains a comma, split into (field, direction)
            if (entry.contains(",")) {
                String[] parts = entry.split(",", 2);
                String field = parts[0].trim();
                String dir = parts.length > 1 ? parts[1].trim().toLowerCase() : "asc";
                pairs.add(new String[]{field, dir});
            } else {
                // Entry is a single token — could be a field or a direction
                String token = entry.trim();
                String lower = token.toLowerCase();
                if (DIRECTIONS.contains(lower)) {
                    // This is a direction — pair with the previous field (if any)
                    if (!pairs.isEmpty()) {
                        String[] last = pairs.get(pairs.size() - 1);
                        // Override the direction (Spring's comma-split delivered
                        // the direction as a separate list entry).
                        last[1] = lower;
                    }
                    // else: orphan direction — ignore
                } else {
                    // It's a field name
                    pairs.add(new String[]{token, "asc"});
                }
            }
        }

        // Build Sort
        Sort sort = null;
        for (String[] pair : pairs) {
            String field = pair[0];
            String dirStr = (pair[1] == null || pair[1].isBlank()) ? "asc" : pair[1];

            if (!DIRECTIONS.contains(dirStr)) {
                throw new InvalidPaginationException(
                    "Sort direction must be 'asc' or 'desc' (got: '" + dirStr + "').",
                    com.sanad.platform.shared.api.ErrorCode.SANAD_PAG_002);
            }
            Sort.Direction direction = Sort.Direction.fromOptionalString(dirStr).orElse(Sort.Direction.ASC);

            // Reject nested paths (no dots allowed)
            if (field.contains(".")) {
                throw InvalidPaginationException.invalidSort(field);
            }
            // Reject SQL-ish expressions
            if (field.contains("(") || field.contains(")") || field.contains(" ")
                    || field.contains(";") || field.contains("'") || field.contains("\"")) {
                throw InvalidPaginationException.invalidSort(field);
            }
            // Reject sensitive fields
            if (SENSITIVE_FIELDS.contains(field.toLowerCase())) {
                throw InvalidPaginationException.invalidSort(field);
            }
            // Reject non-allowlisted fields
            if (allowed == null || !allowed.contains(field)) {
                throw InvalidPaginationException.invalidSort(field);
            }

            Sort.Order order = new Sort.Order(direction, field);
            sort = (sort == null) ? Sort.by(order) : sort.and(Sort.by(order));
        }

        return sort == null
                ? PageRequest.of(page, size)
                : PageRequest.of(page, size, sort);
    }

    /** Common allowlist fields shared across most resources. */
    public static final Set<String> COMMON_FIELDS = Set.of(
            "id", "status", "createdAt", "updatedAt");
}
