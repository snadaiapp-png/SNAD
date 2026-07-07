package com.sanad.platform.crm.ai;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Applies the minimum mandatory Stage 10 controls before CRM context can be
 * sent to a central AI gateway.
 */
@Service
public class CrmAiPolicyService {
    private static final Set<String> ALLOWED_LEAD_FIELDS = Set.of(
            "companyName", "source", "annualRevenue", "employeeCount");
    private static final Pattern EMAIL = Pattern.compile(
            "(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(?<!\\w)\\+?[0-9][0-9 ()-]{6,}[0-9](?!\\w)");
    private static final Pattern PROMPT_INJECTION = Pattern.compile(
            "(?i)(ignore|disregard|override).{0,40}(instruction|policy|system|developer)|"
                    + "(?i)(system prompt|developer message|reveal secrets?|api key|execute command)");

    public PolicyDecision sanitizeLeadContext(Map<String, Object> input) {
        Map<String, Object> safe = new LinkedHashMap<>();
        boolean sensitiveDataRedacted = false;
        boolean promptInjectionDetected = false;

        if (input != null) {
            for (Map.Entry<String, Object> entry : input.entrySet()) {
                if (!ALLOWED_LEAD_FIELDS.contains(entry.getKey()) || entry.getValue() == null) {
                    continue;
                }
                Object value = entry.getValue();
                if (value instanceof String text) {
                    String normalized = text.trim();
                    if (PROMPT_INJECTION.matcher(normalized).find()) {
                        promptInjectionDetected = true;
                        continue;
                    }
                    String redacted = EMAIL.matcher(normalized).replaceAll("[REDACTED_EMAIL]");
                    redacted = PHONE.matcher(redacted).replaceAll("[REDACTED_PHONE]");
                    sensitiveDataRedacted |= !redacted.equals(normalized);
                    safe.put(entry.getKey(), redacted.substring(0, Math.min(redacted.length(), 500)));
                } else if (value instanceof Number || value instanceof Boolean) {
                    safe.put(entry.getKey(), value);
                }
            }
        }

        return new PolicyDecision(
                !promptInjectionDetected,
                Map.copyOf(safe),
                sensitiveDataRedacted,
                promptInjectionDetected,
                promptInjectionDetected ? "PROMPT_INJECTION_REJECTED" : "ALLOWED");
    }

    public boolean isSupportedUseCase(String useCase) {
        return useCase != null && Set.of("LEAD_SCORING", "CUSTOMER_SUMMARY", "NEXT_BEST_ACTION")
                .contains(useCase.trim().toUpperCase(Locale.ROOT));
    }

    public record PolicyDecision(
            boolean allowed,
            Map<String, Object> sanitizedContext,
            boolean sensitiveDataRedacted,
            boolean promptInjectionDetected,
            String reasonCode) {
    }
}
