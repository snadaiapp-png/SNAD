package com.sanad.platform.management.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Executive Health Snapshot — an immutable record of the organization's
 * executive health at a point in time.
 *
 * <p>Append-only: records are never updated or deleted.
 *
 * <p>Health Score is computed deterministically from:
 * <ul>
 *   <li>Strategy score: based on objective progress and status distribution</li>
 *   <li>KPI score: based on KPI measurement status distribution</li>
 *   <li>Decision score: based on pending/overdue decision ratio</li>
 *   <li>Risk score: based on risk severity distribution (inverted — more critical = lower score)</li>
 *   <li>Issue score: based on issue severity + open count</li>
 *   <li>Escalation score: based on active/overdue escalation count</li>
 * </ul>
 *
 * <p>Overall health_score = weighted average of the 6 domain scores.
 * Weights: Strategy 25%, KPI 25%, Risk 15%, Issue 15%, Decision 10%, Escalation 10%
 */
public record ExecutiveHealthSnapshot(
        UUID id,
        UUID tenantId,
        int healthScore,      // 0-100, weighted average
        int strategyScore,   // 0-100
        int kpiScore,        // 0-100
        int decisionScore,   // 0-100
        int riskScore,        // 0-100 (inverted — 100 = no critical risks)
        int issueScore,      // 0-100
        int escalationScore, // 0-100
        int totalObjectives,
        int activeObjectives,
        int atRiskObjectives,
        int offTrackObjectives,
        int totalKpis,
        int onTrackKpis,
        int atRiskKpis,
        int offTrackKpis,
        int pendingDecisions,
        int overdueDecisions,
        int criticalRisks,
        int highRisks,
        int openIssues,
        int criticalIssues,
        int activeEscalations,
        int overdueEscalations,
        int activeAlerts,
        Instant snapshotAt
) {
    /**
     * Compute the overall health score from domain scores.
     *
     * Weights:
     *   Strategy:     25%
     *   KPI:          25%
     *   Risk:         15%
     *   Issue:        15%
     *   Decision:     10%
     *   Escalation:   10%
     */
    public static int computeHealthScore(
            int strategyScore, int kpiScore, int decisionScore,
            int riskScore, int issueScore, int escalationScore) {
        return Math.round(
                strategyScore * 0.25f +
                kpiScore * 0.25f +
                riskScore * 0.15f +
                issueScore * 0.15f +
                decisionScore * 0.10f +
                escalationScore * 0.10f
        );
    }

    /**
     * Compute the strategy score from objective counts.
     *
     * Algorithm:
     *   - If no objectives: 100 (no risk)
     *   - ACHIEVED = 100% progress
     *   - ACTIVE = 70% of progress
     *   - AT_RISK = 30% of progress
     *   - OFF_TRACK = 0%
     *   - CLOSED/CANCELLED = excluded
     */
    public static int computeStrategyScore(
            int total, int active, int atRisk, int offTrack, int achieved) {
        if (total == 0) return 100;
        int score = (achieved * 100 + active * 70 + atRisk * 30 + offTrack * 0) / total;
        return Math.max(0, Math.min(100, score));
    }

    /**
     * Compute the KPI score from KPI status counts.
     *
     * Algorithm:
     *   - ON_TRACK = 100
     *   - ACHIEVED = 100
     *   - AT_RISK = 50
     *   - OFF_TRACK / NO_DATA / NOT_STARTED = 0
     */
    public static int computeKpiScore(
            int total, int onTrack, int atRisk, int offTrack) {
        if (total == 0) return 100;
        int achieved = total - onTrack - atRisk - offTrack;  // approximate achieved count
        int score = ((onTrack + achieved) * 100 + atRisk * 50) / total;
        return Math.max(0, Math.min(100, score));
    }

    /**
     * Compute the risk score (inverted — fewer critical risks = higher score).
     *
     * Algorithm:
     *   - 0 critical risks = 100
     *   - Each critical risk: -20
     *   - Each high risk: -10
     *   - Minimum: 0
     */
    public static int computeRiskScore(int criticalRisks, int highRisks) {
        int score = 100 - (criticalRisks * 20 + highRisks * 10);
        return Math.max(0, score);
    }

    /**
     * Compute the issue score.
     *
     * Algorithm:
     *   - 0 open issues = 100
     *   - Each critical issue: -15
     *   - Each other open issue: -5
     *   - Minimum: 0
     */
    public static int computeIssueScore(int openIssues, int criticalIssues) {
        int otherIssues = openIssues - criticalIssues;
        int score = 100 - (criticalIssues * 15 + Math.max(0, otherIssues) * 5);
        return Math.max(0, score);
    }

    /**
     * Compute the decision score from pending/overdue counts.
     *
     * Algorithm:
     *   - 0 pending = 100
     *   - Each pending: -5
     *   - Each overdue: -15
     *   - Minimum: 0
     */
    public static int computeDecisionScore(int pendingDecisions, int overdueDecisions) {
        int score = 100 - (pendingDecisions * 5 + overdueDecisions * 15);
        return Math.max(0, score);
    }

    /**
     * Compute the escalation score.
     *
     * Algorithm:
     *   - 0 active = 100
     *   - Each active: -5
     *   - Each overdue: -20
     *   - Minimum: 0
     */
    public static int computeEscalationScore(int activeEscalations, int overdueEscalations) {
        int score = 100 - (activeEscalations * 5 + overdueEscalations * 20);
        return Math.max(0, score);
    }
}
