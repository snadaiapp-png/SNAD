package com.sanad.platform.hr.compliance.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.hr.compliance.domain.ComplianceDecision;
import com.sanad.platform.hr.compliance.domain.ComplianceDecisionType;
import com.sanad.platform.hr.compliance.domain.ComplianceEnforcementLevel;
import com.sanad.platform.hr.compliance.domain.ComplianceEvaluationContext;
import com.sanad.platform.hr.compliance.domain.ComplianceOperationType;
import com.sanad.platform.hr.compliance.domain.ComplianceResource;
import com.sanad.platform.hr.compliance.domain.ComplianceRule;
import com.sanad.platform.hr.compliance.domain.CountryOperatingMode;
import com.sanad.platform.hr.compliance.domain.HrCommandContext;
import com.sanad.platform.hr.compliance.domain.ResolvedCountryPolicy;
import com.sanad.platform.hr.compliance.domain.RuleEvaluation;
import com.sanad.platform.hr.compliance.infrastructure.JdbcComplianceDecisionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * HRM-G0 WS3 deterministic compliance engine with safe Global Mode semantics.
 *
 * <p>Decision contract:</p>
 * <ul>
 *   <li>LOCALIZED + no blocking rule + GENERIC_HR → COMPLIANT</li>
 *   <li>GLOBAL + GENERIC_HR → GLOBAL_MODE_ALLOWED</li>
 *   <li>GLOBAL + LOCAL_STATUTORY → LEGAL_REVIEW_REQUIRED (fail closed)</li>
 *   <li>LOCALIZED + MANDATORY_HARD violation → BLOCKED</li>
 *   <li>LOCALIZED + MANDATORY_WITH_EXCEPTION violation → CONTROLLED_EXCEPTION_REQUIRED
 *       (only when the rule explicitly permits the legal exception path, otherwise BLOCKED)</li>
 *   <li>LOCALIZED + guidance-only violation → COMPLIANT with warning metadata</li>
 *   <li>Localized statutory operation with missing effective rule/handler → LEGAL_REVIEW_REQUIRED</li>
 *   <li>Missing effective Employment jurisdiction → the resolver fails closed</li>
 * </ul>
 *
 * <p>Safety invariants: rule parameters are typed DATA interpreted by registered
 * {@link ComplianceRuleHandler} implementations; this engine never executes dynamic
 * Java/JavaScript/SQL/SpEL/Groovy or any other expression language. Rule and pack
 * selection always use the supplied operation effective date, never the server
 * current date alone. Every decision is persisted with provenance.</p>
 */
@Service
public class ComplianceEngine {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final CountryPolicyResolver countryPolicyResolver;
    private final JdbcComplianceDecisionRepository decisionRepository;
    private final Map<String, ComplianceRuleHandler> handlersByOperationCode;

    public ComplianceEngine(
            CountryPolicyResolver countryPolicyResolver,
            List<ComplianceRuleHandler> handlers,
            JdbcComplianceDecisionRepository decisionRepository) {
        this.countryPolicyResolver = Objects.requireNonNull(countryPolicyResolver, "countryPolicyResolver");
        this.decisionRepository = Objects.requireNonNull(decisionRepository, "decisionRepository");
        this.handlersByOperationCode = handlers == null ? Map.of()
                : handlers.stream().collect(Collectors.toUnmodifiableMap(
                        ComplianceRuleHandler::operationCode, Function.identity()));
    }

    public ComplianceDecision evaluate(
            HrCommandContext context,
            String operationCode,
            ComplianceOperationType operationType,
            LocalDate effectiveDate,
            ComplianceResource resource) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(operationCode, "operationCode");
        Objects.requireNonNull(operationType, "operationType");
        Objects.requireNonNull(effectiveDate, "effectiveDate");

        ResolvedCountryPolicy policy = countryPolicyResolver.resolve(
                context.tenantId(), context.employmentId(), effectiveDate);

        ComplianceDecision decision;
        if (policy.mode() == CountryOperatingMode.GLOBAL) {
            decision = evaluateGlobalMode(policy, operationType);
        } else {
            decision = evaluateLocalizedMode(context, operationCode, operationType, effectiveDate, resource, policy);
        }

        decisionRepository.persist(decision, context, resource, operationCode, operationType.name(), effectiveDate);
        return decision;
    }

    private ComplianceDecision evaluateGlobalMode(ResolvedCountryPolicy policy, ComplianceOperationType operationType) {
        if (operationType == ComplianceOperationType.GENERIC_HR) {
            return new ComplianceDecision(
                    ComplianceDecisionType.GLOBAL_MODE_ALLOWED,
                    policy.laborJurisdiction(),
                    policy.mode(),
                    null, null, null, null,
                    "GLOBAL_MODE_GENERIC_HR",
                    List.of("Generic HR proceeds in Global Mode; no statutory certification is claimed."));
        }
        return new ComplianceDecision(
                ComplianceDecisionType.LEGAL_REVIEW_REQUIRED,
                policy.laborJurisdiction(),
                policy.mode(),
                null, null, null, null,
                "GLOBAL_MODE_STATUTORY_NOT_AUTHORIZED",
                List.of("Statutory-sensitive operations require an authoritative localized rule."));
    }

    private ComplianceDecision evaluateLocalizedMode(
            HrCommandContext context,
            String operationCode,
            ComplianceOperationType operationType,
            LocalDate effectiveDate,
            ComplianceResource resource,
            ResolvedCountryPolicy policy) {

        List<ComplianceRule> rules = loadEffectiveRules(policy.laborJurisdiction(), operationCode, effectiveDate);
        ComplianceRuleHandler handler = handlersByOperationCode.get(operationCode);

        if (rules.isEmpty()) {
            if (operationType == ComplianceOperationType.LOCAL_STATUTORY) {
                // Fail closed: a localized statutory operation without an effective
                // authoritative rule is never guessed.
                return new ComplianceDecision(
                        ComplianceDecisionType.LEGAL_REVIEW_REQUIRED,
                        policy.laborJurisdiction(),
                        policy.mode(),
                        policy.packCode(),
                        policy.packVersion(),
                        null, null,
                        "HRM_LEGAL_REVIEW_REQUIRED",
                        List.of("No effective authoritative rule for the localized statutory operation."));
            }
            // Generic HR with no applicable localized rule is legally neutral.
            return new ComplianceDecision(
                    ComplianceDecisionType.COMPLIANT,
                    policy.laborJurisdiction(),
                    policy.mode(),
                    policy.packCode(),
                    policy.packVersion(),
                    null, null,
                    "NO_APPLICABLE_RULE_GENERIC_HR",
                    List.of());
        }

        if (handler == null) {
            // A rule exists but no registered handler can interpret its typed parameters.
            return new ComplianceDecision(
                    ComplianceDecisionType.LEGAL_REVIEW_REQUIRED,
                    policy.laborJurisdiction(),
                    policy.mode(),
                    policy.packCode(),
                    policy.packVersion(),
                    null, null,
                    "HRM_LEGAL_REVIEW_REQUIRED",
                    List.of("No registered handler for the effective localized rule."));
        }

        ComplianceEvaluationContext evaluationContext = new ComplianceEvaluationContext(
                context.tenantId(),
                context.employmentId(),
                context.actorUserId(),
                operationCode,
                operationType,
                effectiveDate,
                policy.laborJurisdiction(),
                policy.mode(),
                policy.packCode(),
                policy.packVersion(),
                policy.workerClassification(),
                resource == null ? null : resource.resourceType(),
                resource == null ? null : resource.resourceId());

        ComplianceDecisionType worst = ComplianceDecisionType.COMPLIANT;
        String worstRuleCode = null;
        String worstRuleVersion = null;
        String worstReason = null;
        List<String> warnings = new ArrayList<>();

        for (ComplianceRule rule : rules) {
            RuleEvaluation evaluation = handler.evaluate(rule, evaluationContext);
            List<String> evaluationWarnings = evaluation == null ? List.of() : evaluation.warnings();
            boolean violation = evaluation != null && evaluation.violation();

            if (!violation) {
                warnings.addAll(evaluationWarnings);
                continue;
            }

            ComplianceDecisionType outcome = outcomeForViolation(rule);
            if (outcome == ComplianceDecisionType.COMPLIANT) {
                // Guidance / tenant-policy violations stay compliant but surface warnings.
                warnings.add("GUIDANCE:" + rule.ruleCode() + ":" + (
                        evaluation.reasonCode() == null ? "GUIDANCE_VIOLATION" : evaluation.reasonCode()));
                warnings.addAll(evaluationWarnings);
                continue;
            }

            if (severity(outcome) > severity(worst)) {
                worst = outcome;
                worstRuleCode = rule.ruleCode();
                worstRuleVersion = rule.ruleVersion();
                worstReason = evaluation.reasonCode() == null ? outcome.name() : evaluation.reasonCode();
            }
            warnings.addAll(evaluationWarnings);
        }

        return new ComplianceDecision(
                worst,
                policy.laborJurisdiction(),
                policy.mode(),
                policy.packCode(),
                policy.packVersion(),
                worstRuleCode,
                worstRuleVersion,
                worstReason,
                warnings);
    }

    private ComplianceDecisionType outcomeForViolation(ComplianceRule rule) {
        if (rule.enforcementLevel() == ComplianceEnforcementLevel.MANDATORY_HARD) {
            return ComplianceDecisionType.BLOCKED;
        }
        if (rule.enforcementLevel() == ComplianceEnforcementLevel.MANDATORY_WITH_EXCEPTION) {
            return rule.exceptionAllowed()
                    ? ComplianceDecisionType.CONTROLLED_EXCEPTION_REQUIRED
                    : ComplianceDecisionType.BLOCKED;
        }
        return ComplianceDecisionType.COMPLIANT;
    }

    private int severity(ComplianceDecisionType type) {
        return switch (type) {
            case BLOCKED -> 3;
            case CONTROLLED_EXCEPTION_REQUIRED -> 2;
            case LEGAL_REVIEW_REQUIRED -> 2;
            case COMPLIANT, GLOBAL_MODE_ALLOWED -> 0;
        };
    }

    /**
     * Loads effective rules for the resolved jurisdiction/pack using the supplied
     * effective date (never the server current date alone). Pack-level legal review
     * and certification presence are re-verified here as defense in depth.
     */
    private List<ComplianceRule> loadEffectiveRules(String jurisdiction, String operationCode, LocalDate effectiveDate) {
        JdbcTemplate jdbc = decisionRepository.jdbc();
        return jdbc.query(
                "SELECT r.id, r.rule_code, r.rule_version, r.operation_code, r.enforcement_level, " +
                        "r.exception_allowed, r.parameters " +
                        "FROM hr_compliance_rules r " +
                        "JOIN hr_country_packs p ON p.id = r.country_pack_id " +
                        "WHERE p.country_code = ? " +
                        "AND p.status IN ('ACTIVE','CERTIFIED') " +
                        "AND p.legal_reviewed_at IS NOT NULL " +
                        "AND NULLIF(BTRIM(p.legal_reviewed_by), '') IS NOT NULL " +
                        "AND NULLIF(BTRIM(p.certification_reference), '') IS NOT NULL " +
                        "AND p.effective_from <= ? AND (p.effective_to IS NULL OR p.effective_to >= ?) " +
                        "AND r.status = 'ACTIVE' " +
                        "AND r.operation_code = ? " +
                        "AND r.effective_from <= ? AND (r.effective_to IS NULL OR r.effective_to >= ?) " +
                        "ORDER BY r.effective_from DESC, r.rule_version DESC",
                (ResultSet rs, int rowNum) -> mapRule(rs),
                jurisdiction, effectiveDate, effectiveDate, operationCode, effectiveDate, effectiveDate);
    }

    private ComplianceRule mapRule(ResultSet rs) throws SQLException {
        JsonNode parameters;
        try {
            String raw = rs.getString("parameters");
            parameters = raw == null ? OBJECT_MAPPER.createObjectNode() : OBJECT_MAPPER.readTree(raw);
        } catch (Exception e) {
            throw new IllegalStateException("HRM_LEGAL_REVIEW_REQUIRED: unreadable rule parameters", e);
        }
        return new ComplianceRule(
                UUID.fromString(rs.getString("id")),
                rs.getString("rule_code"),
                rs.getString("rule_version"),
                rs.getString("operation_code"),
                ComplianceEnforcementLevel.valueOf(rs.getString("enforcement_level")),
                rs.getBoolean("exception_allowed"),
                parameters);
    }
}
