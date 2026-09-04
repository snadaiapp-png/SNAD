package com.sanad.platform.hr.api.v2.dto;

import com.sanad.platform.hr.identity.HrPersonPrivate;

import java.time.LocalDate;
import java.util.UUID;

/**
 * HRM-G0 / WS5 Task 3 slice 2 — private PII view for a Person, returned only
 * by GET /api/v2/hr/people/{personId}/private after the HRM.PII.VIEW
 * capability gate AND the fail-closed sensitive-read audit both succeed.
 */
public record PersonPrivateResponse(
        UUID personId,
        LocalDate dateOfBirth,
        String nationalityCountryCode,
        String maritalStatus,
        long version
) {

    public static PersonPrivateResponse from(HrPersonPrivate profile) {
        return new PersonPrivateResponse(profile.personId(), profile.dateOfBirth(),
                profile.nationalityCountryCode(), profile.maritalStatus(), profile.version());
    }

    /** Empty profile for a person whose private row was never written. */
    public static PersonPrivateResponse empty(UUID personId) {
        return new PersonPrivateResponse(personId, null, null, null, 0L);
    }
}
