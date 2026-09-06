package com.sanad.platform.hr.contract.application;

import com.sanad.platform.hr.compliance.domain.CountryOperatingMode;
import com.sanad.platform.hr.compliance.domain.ResolvedCountryPolicy;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

/**
 * Country contract terms validator (WS6 Task 2).
 *
 * <p>Country-specific contract extension fields are typed/validated through a
 * Country Pack schema/handler — scripts or executable content stored in JSON
 * are never executed. In Global Mode ONLY the generic schema is accepted:
 * jurisdiction-specific extension keys are rejected as a structured
 * compliance violation (no silent statutory assumption). In LOCALIZED mode
 * the active pack's known extension schema is enforced; until the pack is
 * legally reviewed, unknown pack-specific keys are rejected (fail closed).</p>
 */
@Component
public class CountryContractTermsValidator {

    /** Generic (jurisdiction-neutral) contract term keys accepted in Global Mode. */
    private static final Map<String, String> GENERIC_TERM_KEYS = Map.of(
            "probationMonths", "integer",
            "noticePeriodDays", "integer",
            "workingHoursPerWeek", "integer",
            "documentLanguage", "string");

    /** Keys that are NEVER acceptable inside country terms (no executable content). */
    private static final java.util.Set<String> FORBIDDEN_KEYS = java.util.Set.of(
            "script", "expression", "handler", "code", "eval", "function", "sql");

    public void validate(ResolvedCountryPolicy policy, JsonNode countryTerms) {
        Objects.requireNonNull(policy, "policy");
        if (countryTerms == null || countryTerms.isNull()) {
            return; // absent extension terms are always structurally valid
        }
        if (!countryTerms.isObject()) {
            throw new IllegalArgumentException("HRM_CONTRACT_TERMS_INVALID: country_terms must be a JSON object");
        }
        Iterator<String> keys = countryTerms.fieldNames();
        while (keys.hasNext()) {
            String key = keys.next();
            if (FORBIDDEN_KEYS.contains(key.toLowerCase())) {
                throw new IllegalArgumentException("HRM_CONTRACT_TERMS_INVALID: executable content is not "
                        + "permitted in country terms (key=" + key + ")");
            }
        }
        if (policy.mode() == CountryOperatingMode.GLOBAL) {
            validateGlobalMode(countryTerms);
        } else {
            validateLocalized(policy, countryTerms);
        }
    }

    private void validateGlobalMode(JsonNode countryTerms) {
        Iterator<String> keys = countryTerms.fieldNames();
        while (keys.hasNext()) {
            String key = keys.next();
            String expectedType = GENERIC_TERM_KEYS.get(key);
            if (expectedType == null) {
                // Jurisdiction-specific extension without a reviewed pack —
                // Global Mode cannot certify it.
                throw new IllegalArgumentException(
                        "HRM_CONTRACT_TERMS_NOT_CERTIFIED: country-specific contract term '" + key
                                + "' requires a legally reviewed Country Pack (Global Mode accepts generic terms only)");
            }
            validateType(countryTerms.get(key), expectedType, key);
        }
    }

    private void validateLocalized(ResolvedCountryPolicy policy, JsonNode countryTerms) {
        // G0: no pack has completed legal review yet (SA pack is DRAFT), so the
        // pack-specific extension schema is intentionally NOT implemented —
        // any pack-specific key would be uncertified. Generic keys validate;
        // unknown keys fail closed with a structured violation.
        Iterator<String> keys = countryTerms.fieldNames();
        while (keys.hasNext()) {
            String key = keys.next();
            String expectedType = GENERIC_TERM_KEYS.get(key);
            if (expectedType == null) {
                throw new IllegalArgumentException(
                        "HRM_CONTRACT_TERMS_NOT_CERTIFIED: pack-specific contract term '" + key
                                + "' awaits legal review of pack " + policy.packCode() + "/" + policy.packVersion());
            }
            validateType(countryTerms.get(key), expectedType, key);
        }
    }

    private void validateType(JsonNode value, String expectedType, String key) {
        boolean ok = switch (expectedType) {
            case "integer" -> value.isInt() || value.isLong();
            case "string" -> value.isTextual();
            default -> false;
        };
        if (!ok) {
            throw new IllegalArgumentException("HRM_CONTRACT_TERMS_INVALID: term '" + key + "' must be " + expectedType);
        }
    }
}
