package com.sanad.platform.hr.contract.domain;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable effective-dated contract version (WS6 Task 2).
 *
 * <p>Historical effective terms are IMMUTABLE — an amendment creates a NEW
 * version; it never overwrites this record's term fields. Country-specific
 * extension terms travel in typed/validated {@code countryTerms} JSON —
 * never as executable content.</p>
 */
public record EmploymentContractVersion(
        UUID id,
        UUID tenantId,
        UUID contractId,
        UUID employmentId,
        int versionNumber,
        EmploymentContractStatus status,
        boolean isPrimary,
        String contractTermType,
        LocalDate contractStartDate,
        LocalDate contractEndDate,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String documentReference,
        JsonNode countryTerms,
        UUID createdBy,
        Instant createdAt) {

    public EmploymentContractVersion {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(contractId, "contractId");
        Objects.requireNonNull(employmentId, "employmentId");
        Objects.requireNonNull(contractTermType, "contractTermType");
        Objects.requireNonNull(contractStartDate, "contractStartDate");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        if (contractEndDate != null && contractEndDate.isBefore(contractStartDate)) {
            throw new IllegalArgumentException("HRM_CONTRACT_INVALID_DATES: contract_end_date before contract_start_date");
        }
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new IllegalArgumentException("HRM_CONTRACT_INVALID_DATES: effective_to before effective_from");
        }
    }
}
