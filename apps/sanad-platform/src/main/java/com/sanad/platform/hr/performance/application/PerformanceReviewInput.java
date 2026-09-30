package com.sanad.platform.hr.performance.application;

import java.time.LocalDate;

/**
 * G3 review creation input (HRM-G3 Task 2).
 *
 * <p>Reviews are created in {@code DRAFT}; the rating may be provided at
 * creation but is mandatory at submission. Structural validation of the
 * period, cycle, and rating happens here and is mirrored by PostgreSQL
 * constraints as the final boundary.</p>
 */
public record PerformanceReviewInput(
        String cycle,
        LocalDate periodStart,
        LocalDate periodEnd,
        Integer rating,
        String comments
) {

    public PerformanceReviewInput {
        if (cycle != null) {
            cycle = cycle.trim();
        }
    }
}
