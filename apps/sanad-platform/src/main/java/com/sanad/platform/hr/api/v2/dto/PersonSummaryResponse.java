package com.sanad.platform.hr.api.v2.dto;

import com.sanad.platform.hr.identity.HrPerson;

import java.util.UUID;

/**
 * HRM-G0 / WS5 Task 3 slice 2 — safe directory summary for a Person.
 *
 * <p>Carries directory-visible identity fields only. Private PII (date of
 * birth, nationality, marital status) and identity-document data are
 * structurally absent from this shape — they are exclusively reachable
 * through the capability-gated, audit-protected private operations.
 */
public record PersonSummaryResponse(
        UUID personId,
        UUID userId,
        String firstName,
        String middleName,
        String lastName,
        String displayName,
        long version
) {

    public static PersonSummaryResponse from(HrPerson person) {
        return new PersonSummaryResponse(person.id(), person.userId(), person.firstName(),
                person.middleName(), person.lastName(), person.displayName(), person.version());
    }
}
