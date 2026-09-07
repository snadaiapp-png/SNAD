package com.sanad.platform.hr.compliance.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.sanad.platform.hr.compliance.domain.ComplianceDecision;
import com.sanad.platform.hr.compliance.domain.HrCommandContext;
import com.sanad.platform.hr.compliance.domain.ComplianceResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Persistence for compliance decision provenance. Writes a row per evaluated
 * compliance-sensitive command containing the exact pack/rule versions and
 * effective date. Only identifiers, codes and redacted reason metadata are
 * persisted — never raw PII or secrets.
 */
@Repository
public class JdbcComplianceDecisionRepository {

    private final JdbcTemplate jdbc;

    public JdbcComplianceDecisionRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    /** Shared producer-local template used by the engine for effective-rule loading. */
    public JdbcTemplate jdbc() {
        return jdbc;
    }

    public void persist(
            ComplianceDecision decision,
            HrCommandContext context,
            ComplianceResource resource,
            String operationCode,
            String operationType,
            LocalDate effectiveDate) {
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(effectiveDate, "effectiveDate");

        jdbc.update(
                "INSERT INTO hr_compliance_decisions " +
                        "(tenant_id, employment_id, resource_type, resource_id, operation_code, operation_type, " +
                        " effective_date, labor_jurisdiction, operating_mode, pack_code, pack_version, " +
                        " rule_code, rule_version, decision_type, reason) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                context.tenantId(),
                context.employmentId(),
                resource == null ? null : resource.resourceType(),
                resource == null ? null : resource.resourceId(),
                Objects.requireNonNull(operationCode, "operationCode"),
                operationType,
                effectiveDate,
                decision.laborJurisdiction(),
                decision.operatingMode() == null ? null : decision.operatingMode().name(),
                decision.packCode(),
                decision.packVersion(),
                decision.ruleCode(),
                decision.ruleVersion(),
                decision.type().name(),
                decision.reasonCode());
    }
}
