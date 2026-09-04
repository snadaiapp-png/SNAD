package com.sanad.platform.hr.api.v2.dto;

import com.sanad.platform.hr.contract.domain.EmploymentContract;
import com.sanad.platform.hr.contract.domain.EmploymentContractVersion;
import com.sanad.platform.hr.contract.domain.EmploymentContractVersion;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** HRM-G0 / WS5 Task 5 — canonical Contract view (root + current version fields). */
public record ContractResponse(
        UUID contractId,
        UUID employmentId,
        String contractNumber,
        boolean isPrimary,
        Integer versionNumber,
        String status,
        String contractTermType,
        LocalDate contractStartDate,
        LocalDate contractEndDate,
        LocalDate effectiveDate,
        String documentReference,
        String complianceStatus,
        String packCode,
        String packVersion
) {

    public static ContractResponse from(EmploymentContract contract) {
        return new ContractResponse(contract.id(), contract.employmentId(), contract.contractNumber(),
                contract.isPrimary(), null, null, null, null, null, null, null, null, null, null);
    }

    public static ContractResponse from(EmploymentContract contract, EmploymentContractVersion version) {
        return new ContractResponse(contract.id(), contract.employmentId(), contract.contractNumber(),
                contract.isPrimary(), version.versionNumber(), version.status() == null ? null : version.status().name(),
                version.contractTermType(), version.contractStartDate(), version.contractEndDate(),
                version.effectiveFrom(), version.documentReference(), null, null, null);
    }

    public static ContractResponse from(EmploymentContract contract, EmploymentContractVersion version,
                                        String complianceStatus, String packCode, String packVersion) {
        return new ContractResponse(contract.id(), contract.employmentId(), contract.contractNumber(),
                contract.isPrimary(), version.versionNumber(), version.status() == null ? null : version.status().name(),
                version.contractTermType(), version.contractStartDate(), version.contractEndDate(),
                version.effectiveFrom(), version.documentReference(), complianceStatus, packCode, packVersion);
    }

    /** Command-result variant: the version carries the contract identity. */
    public static ContractResponse fromVersion(EmploymentContractVersion version,
                                               String complianceStatus, String packCode, String packVersion) {
        return new ContractResponse(version.contractId(), version.employmentId(), null,
                version.isPrimary(), version.versionNumber(), version.status() == null ? null : version.status().name(),
                version.contractTermType(), version.contractStartDate(), version.contractEndDate(),
                version.effectiveFrom(), version.documentReference(), complianceStatus, packCode, packVersion);
    }
}
