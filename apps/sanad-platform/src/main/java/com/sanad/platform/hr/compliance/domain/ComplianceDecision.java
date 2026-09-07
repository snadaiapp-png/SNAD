package com.sanad.platform.hr.compliance.domain;

import java.util.List;

/**
 * Deterministic compliance decision with full provenance. Persisted per
 * evaluated compliance-sensitive command. Contains identifiers, codes and
 * warning metadata only — never raw PII or secrets.
 */
public record ComplianceDecision(
        ComplianceDecisionType type,
        String laborJurisdiction,
        CountryOperatingMode operatingMode,
        String packCode,
        String packVersion,
        String ruleCode,
        String ruleVersion,
        String reasonCode,
        List<String> warnings) {

    public ComplianceDecision {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
