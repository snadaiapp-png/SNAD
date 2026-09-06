package com.sanad.platform.hr.compliance.domain;

import java.time.LocalDate;

public record ResolvedCountryPolicy(
        String laborJurisdiction,
        CountryOperatingMode mode,
        String packCode,
        String packVersion,
        String workerClassification,
        LocalDate effectiveDate) {
}
