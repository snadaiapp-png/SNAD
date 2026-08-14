package com.sanad.platform.management.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Risk — a management risk with probability × impact scoring.
 *
 * <p>Risk Score = probability × impact (both 1-5 scale, so score = 1-25).
 * Severity is derived deterministically from score:
 * <ul>
 *   <li>1-4 → LOW</li>
 *   <li>5-9 → MEDIUM</li>
 *   <li>10-15 → HIGH</li>
 *   <li>16-25 → CRITICAL</li>
 * </ul>
 *
 * <p>Lifecycle:
 * <pre>
 *   IDENTIFIED → ASSESSED → MITIGATING → MONITORED → ACCEPTED → CLOSED
 *                                       ↘ CLOSED (directly from MITIGATING)
 * </pre>
 */
public record Risk(
        UUID id,
        UUID tenantId,
        String code,
        String title,
        String description,
        String category,
        Status status,
        int probability,
        int impact,
        int riskScore,
        Severity severity,
        UUID ownerUserId,
        UUID identifiedBy,
        Instant identifiedAt,
        LocalDate dueDate,
        String mitigation,
        String contingency,
        String treatmentStrategy,
        String residualRisk,
        Instant closedAt,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public enum Status {
        IDENTIFIED, ASSESSED, MITIGATING, MONITORED, ACCEPTED, CLOSED
    }

    public enum Severity {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    public static Risk create(
            UUID tenantId, String code, String title, String description,
            String category, int probability, int impact,
            UUID ownerUserId, UUID identifiedBy, LocalDate dueDate) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (probability < 1 || probability > 5) {
            throw new IllegalArgumentException("probability must be 1-5");
        }
        if (impact < 1 || impact > 5) {
            throw new IllegalArgumentException("impact must be 1-5");
        }
        var score = probability * impact;
        var sev = severityFromScore(score);
        var now = Instant.now();
        return new Risk(
                UUID.randomUUID(), tenantId, code, title, description, category,
                Status.IDENTIFIED, probability, impact, score, sev,
                ownerUserId, identifiedBy, now, dueDate,
                null, null, null, null, null, 0, now, now
        );
    }

    /** Derive severity from risk score (deterministic). */
    public static Severity severityFromScore(int score) {
        if (score <= 4) return Severity.LOW;
        if (score <= 9) return Severity.MEDIUM;
        if (score <= 15) return Severity.HIGH;
        return Severity.CRITICAL;
    }

    /** Reassess the risk with new probability and impact. Recomputes score + severity. */
    public Risk reassess(int newProbability, int newImpact) {
        if (newProbability < 1 || newProbability > 5) {
            throw new IllegalArgumentException("probability must be 1-5");
        }
        if (newImpact < 1 || newImpact > 5) {
            throw new IllegalArgumentException("impact must be 1-5");
        }
        var newScore = newProbability * newImpact;
        var newSeverity = severityFromScore(newScore);
        return new Risk(
                id, tenantId, code, title, description, category,
                Status.ASSESSED, newProbability, newImpact, newScore, newSeverity,
                ownerUserId, identifiedBy, identifiedAt, dueDate,
                mitigation, contingency, treatmentStrategy, residualRisk, closedAt,
                version + 1, createdAt, Instant.now()
        );
    }

    public Risk startMitigation(String mitigation, String contingency, String treatmentStrategy) {
        requireStatus(Status.ASSESSED, "startMitigation");
        return new Risk(
                id, tenantId, code, title, description, category,
                Status.MITIGATING, probability, impact, riskScore, severity,
                ownerUserId, identifiedBy, identifiedAt, dueDate,
                mitigation, contingency, treatmentStrategy, residualRisk, closedAt,
                version + 1, createdAt, Instant.now()
        );
    }

    public Risk monitor() {
        requireStatus(Status.MITIGATING, "monitor");
        return withStatus(Status.MONITORED);
    }

    public Risk accept(String residualRisk) {
        if (status != Status.ASSESSED && status != Status.MONITORED) {
            throw new IllegalStateException("Cannot accept from " + status);
        }
        return new Risk(
                id, tenantId, code, title, description, category,
                Status.ACCEPTED, probability, impact, riskScore, severity,
                ownerUserId, identifiedBy, identifiedAt, dueDate,
                mitigation, contingency, treatmentStrategy, residualRisk, closedAt,
                version + 1, createdAt, Instant.now()
        );
    }

    public Risk close() {
        if (status == Status.CLOSED) {
            throw new IllegalStateException("Risk already CLOSED");
        }
        return new Risk(
                id, tenantId, code, title, description, category,
                Status.CLOSED, probability, impact, riskScore, severity,
                ownerUserId, identifiedBy, identifiedAt, dueDate,
                mitigation, contingency, treatmentStrategy, residualRisk, Instant.now(),
                version + 1, createdAt, Instant.now()
        );
    }

    private Risk withStatus(Status newStatus) {
        return new Risk(
                id, tenantId, code, title, description, category,
                newStatus, probability, impact, riskScore, severity,
                ownerUserId, identifiedBy, identifiedAt, dueDate,
                mitigation, contingency, treatmentStrategy, residualRisk, closedAt,
                version + 1, createdAt, Instant.now()
        );
    }

    private void requireStatus(Status expected, String action) {
        if (status != expected) {
            throw new IllegalStateException(
                    "Cannot " + action + " from " + status + " (requires " + expected + ")");
        }
    }
}
