package com.sanad.platform.hr.contract.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Contract command result (WS6 Task 2).
 *
 * <p>Exposes the compliance posture of the accepted command: in Global Mode
 * the generic terms are stored but statutory correctness is NOT certified —
 * the response carries {@code LOCAL_COMPLIANCE_UNVERIFIED}.</p>
 */
public record ContractCommandResult(
        EmploymentContractVersion version,
        String complianceStatus,
        String packCode,
        String packVersion,
        String reasonCode) {

    public static final String LOCAL_COMPLIANCE_UNVERIFIED = "LOCAL_COMPLIANCE_UNVERIFIED";
    public static final String LOCALIZED = "LOCALIZED";

    public ContractCommandResult {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(complianceStatus, "complianceStatus");
    }

    public static ContractCommandResult global(EmploymentContractVersion version, String reasonCode) {
        return new ContractCommandResult(version, LOCAL_COMPLIANCE_UNVERIFIED, null, null, reasonCode);
    }

    public static ContractCommandResult localized(EmploymentContractVersion version,
                                                  String packCode, String packVersion, String reasonCode) {
        return new ContractCommandResult(version, LOCALIZED, packCode, packVersion, reasonCode);
    }
}
