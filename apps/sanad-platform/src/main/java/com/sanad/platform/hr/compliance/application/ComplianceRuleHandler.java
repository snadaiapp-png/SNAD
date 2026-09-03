package com.sanad.platform.hr.compliance.application;

import com.sanad.platform.hr.compliance.domain.ComplianceEvaluationContext;
import com.sanad.platform.hr.compliance.domain.ComplianceRule;
import com.sanad.platform.hr.compliance.domain.RuleEvaluation;

/**
 * Contract for interpreting a versioned compliance rule whose parameters are
 * typed DATA. Implementations must be deterministic and must NEVER execute
 * dynamic Java/JavaScript/SQL/SpEL/Groovy or any other script/expression
 * language. A statutory operation without a registered handler fails closed
 * (LEGAL_REVIEW_REQUIRED) inside the ComplianceEngine.
 */
public interface ComplianceRuleHandler {

    /** The operation code this handler can interpret. */
    String operationCode();

    /** Interpret the rule parameters against the evaluation context. */
    RuleEvaluation evaluate(ComplianceRule rule, ComplianceEvaluationContext context);
}
