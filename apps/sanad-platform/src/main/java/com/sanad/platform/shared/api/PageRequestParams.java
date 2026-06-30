package com.sanad.platform.shared.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.List;

/**
 * Standard pagination request parameters (Stage 03A).
 *
 * <p>Controllers accept this as a {@code @Valid @ModelAttribute}. The
 * {@code sort} parameter is a list to support multiple sort criteria
 * ({@code ?sort=name,asc&sort=createdAt,desc}). Each sort entry has the
 * shape {@code field,direction} where direction is {@code asc} or
 * {@code desc} (case-insensitive); if direction is omitted, {@code asc}
 * is assumed.</p>
 */
public record PageRequestParams(
    @Min(0) Integer page,
    @Min(1) @Max(100) Integer size,
    List<String> sort
) {
    public PageRequestParams {
        if (page == null) page = 0;
        if (size == null) size = 20;
        // Stage 03A: enforce hard limits — do NOT clamp. Validation violations
        // must surface as 400 (ConstraintViolationException) so the caller
        // knows their input was rejected, not silently rewritten.
        if (page < 0) {
            throw new com.sanad.platform.shared.api.exceptions.InvalidPaginationException(
                "Page number must be zero or a positive integer.",
                com.sanad.platform.shared.api.ErrorCode.SANAD_PAG_001);
        }
        if (size < 1 || size > 100) {
            throw new com.sanad.platform.shared.api.exceptions.InvalidPaginationException(
                "Page size must be between 1 and 100.",
                com.sanad.platform.shared.api.ErrorCode.SANAD_PAG_001);
        }
        if (sort == null) sort = List.of();
    }
}
