package com.sanad.platform.crm.ownership.integration;

import java.util.Set;

/**
 * AI capability definitions for CRM-008 Team Management.
 *
 * <p>Defines the AI extension points available for team management operations.
 * These capabilities can be requested via the AiGatewayPort.
 */
public final class TeamManagementAiCapabilities {

    private TeamManagementAiCapabilities() {}

    /**
     * AI capabilities for CRM-008.
     */
    public enum TeamAiCapability {
        /** Optimize workforce allocation across teams */
        WORKFORCE_OPTIMIZATION,

        /** Forecast capacity requirements for future periods */
        CAPACITY_FORECASTING,

        /** Recommend optimal staff-to-service assignments */
        SMART_ASSIGNMENT,

        /** Generate shift scheduling recommendations */
        SCHEDULING_RECOMMENDATIONS,

        /** Analyze workload distribution and suggest balancing */
        WORKLOAD_ANALYSIS,

        /** Predict staff availability patterns */
        AVAILABILITY_PREDICTION
    }

    /**
     * Contract names for AI dispatch.
     */
    public static final String CONTRACT_PREFIX = "crm.team_management.ai.";

    public static String contractName(TeamAiCapability capability) {
        return CONTRACT_PREFIX + capability.name().toLowerCase();
    }

    /**
     * AI capabilities that require human confirmation before execution.
     */
    public static final Set<TeamAiCapability> REQUIRES_CONFIRMATION = Set.of(
            TeamAiCapability.WORKFORCE_OPTIMIZATION,
            TeamAiCapability.SMART_ASSIGNMENT,
            TeamAiCapability.SCHEDULING_RECOMMENDATIONS
    );

    /**
     * AI capabilities that are read-only (no execution required).
     */
    public static final Set<TeamAiCapability> READ_ONLY = Set.of(
            TeamAiCapability.CAPACITY_FORECASTING,
            TeamAiCapability.WORKLOAD_ANALYSIS,
            TeamAiCapability.AVAILABILITY_PREDICTION
    );

    /**
     * AI capability to required capability mapping.
     */
    public static String requiredCapability(TeamAiCapability capability) {
        return switch (capability) {
            case WORKFORCE_OPTIMIZATION, SMART_ASSIGNMENT, SCHEDULING_RECOMMENDATIONS ->
                    "CRM.TEAM.MANAGE";
            case CAPACITY_FORECASTING, WORKLOAD_ANALYSIS ->
                    "CRM.CAPACITY.READ";
            case AVAILABILITY_PREDICTION ->
                    "CRM.AVAILABILITY.READ";
        };
    }
}
