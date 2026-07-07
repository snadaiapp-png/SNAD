package com.sanad.platform.crm.ai;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic, provider-independent fallback for CRM lead scoring.
 *
 * <p>This service is intentionally advisory. It provides stable behavior when
 * the central AI Gateway is unavailable or disabled and never mutates CRM
 * records. The result includes reason codes so users can understand and
 * override the recommendation.</p>
 */
@Service
public class DeterministicLeadScoringService {

    public LeadScore score(LeadScoringInput input) {
        if (input == null) {
            throw new IllegalArgumentException("input must not be null");
        }
        String tenantId = requireText(input.tenantId(), "tenantId");
        String leadId = requireText(input.leadId(), "leadId");

        int score = 10;
        List<String> reasons = new ArrayList<>();

        if (hasText(input.email())) {
            score += 20;
            reasons.add("CONTACTABLE_EMAIL");
        }
        if (hasText(input.phone())) {
            score += 15;
            reasons.add("CONTACTABLE_PHONE");
        }
        if (hasText(input.companyName())) {
            score += 15;
            reasons.add("COMPANY_IDENTIFIED");
        }
        if (input.annualRevenue() != null
                && input.annualRevenue().compareTo(new BigDecimal("100000")) >= 0) {
            score += 15;
            reasons.add("REVENUE_THRESHOLD_MET");
        }
        if (input.employeeCount() != null && input.employeeCount() >= 10) {
            score += 10;
            reasons.add("EMPLOYEE_THRESHOLD_MET");
        }
        if (isPreferredSource(input.source())) {
            score += 15;
            reasons.add("PREFERRED_SOURCE");
        }

        int boundedScore = Math.min(100, score);
        String grade = gradeFor(boundedScore);
        double confidence = confidenceFor(reasons.size());

        if (reasons.isEmpty()) {
            reasons.add("INSUFFICIENT_SIGNAL");
        }

        return new LeadScore(
                tenantId,
                leadId,
                boundedScore,
                grade,
                confidence,
                List.copyOf(reasons),
                true,
                "deterministic-fallback-v1");
    }

    private boolean isPreferredSource(String source) {
        if (!hasText(source)) {
            return false;
        }
        String normalized = source.trim().toLowerCase();
        return normalized.equals("referral")
                || normalized.equals("partner")
                || normalized.equals("customer-referral");
    }

    private String gradeFor(int score) {
        if (score >= 80) {
            return "A";
        }
        if (score >= 60) {
            return "B";
        }
        if (score >= 40) {
            return "C";
        }
        return "D";
    }

    private double confidenceFor(int signalCount) {
        return Math.min(0.95, 0.35 + (signalCount * 0.10));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String requireText(String value, String field) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    public record LeadScoringInput(
            String tenantId,
            String leadId,
            String email,
            String phone,
            String companyName,
            BigDecimal annualRevenue,
            Integer employeeCount,
            String source) {
    }

    public record LeadScore(
            String tenantId,
            String leadId,
            int score,
            String grade,
            double confidence,
            List<String> reasonCodes,
            boolean advisoryOnly,
            String modelReference) {
    }
}
