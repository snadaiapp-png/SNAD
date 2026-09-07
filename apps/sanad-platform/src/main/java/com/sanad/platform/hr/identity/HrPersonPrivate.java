package com.sanad.platform.hr.identity;

import java.time.LocalDate;
import java.util.UUID;

/**
 * HR Person private profile (WS5 Task 3 slice 2) — PII fields held one-to-one
 * with {@link HrPerson} in {@code hr_person_private}.
 *
 * <p>Any read that surfaces these fields through the API is a restricted
 * read and must pass the fail-closed sensitive-read audit before the
 * response is returned. {@code version} supports optimistic concurrency on
 * PATCH mutations; a row that was never written reports version 0.
 */
public record HrPersonPrivate(
        UUID tenantId,
        UUID personId,
        LocalDate dateOfBirth,
        String nationalityCountryCode,
        String maritalStatus,
        long version) {
}
