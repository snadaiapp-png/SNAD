package com.sanad.platform.hr.compliance.domain;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

/**
 * A versioned, officially-sourced compliance rule belonging to a Country Pack.
 * {@code parameters} are typed DATA interpreted by known rule handlers — this
 * model must never store or execute Java/SQL/SpEL/Groovy/script expressions.
 */
public record ComplianceRule(
        UUID id,
        String ruleCode,
        String ruleVersion,
        String operationCode,
        ComplianceEnforcementLevel enforcementLevel,
        boolean exceptionAllowed,
        JsonNode parameters) {
}
