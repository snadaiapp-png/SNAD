package com.sanad.platform.crm.ownership.integration;

import java.util.Set;

/**
 * Workflow type definitions for CRM-008 Team Management.
 *
 * <p>Defines the workflow types that can be dispatched to the external
 * workflow engine for team management operations.
 */
public final class TeamManagementWorkflowTypes {

    private TeamManagementWorkflowTypes() {}

    /**
     * Workflow types specific to CRM-008 Team Management.
     */
    public enum TeamWorkflowType {
        /** Team lifecycle: creation, activation, archival */
        TEAM_LIFECYCLE,

        /** Shift scheduling: template creation, assignment, publishing */
        SHIFT_SCHEDULING,

        /** Availability management: submission, approval, rejection */
        AVAILABILITY_APPROVAL,

        /** Capacity planning: plan creation, adjustment, forecasting */
        CAPACITY_PLANNING,

        /** Workload assignment: assignment, reassignment, balancing */
        WORKLOAD_ASSIGNMENT,

        /** Service assignment: assignment, reassignment, completion */
        SERVICE_ASSIGNMENT
    }

    /**
     * Contract names for workflow dispatch.
     */
    public static final String CONTRACT_PREFIX = "crm.team_management.";

    public static String contractName(TeamWorkflowType type) {
        return CONTRACT_PREFIX + type.name().toLowerCase();
    }

    /**
     * Terminal states for workflow callbacks.
     */
    public static final Set<String> TERMINAL_STATES = Set.of(
            "COMPLETED", "REJECTED", "CANCELLED", "TIMED_OUT", "UNAVAILABLE");

    /**
     * Source entity types for CRM-008.
     */
    public static final String ENTITY_TYPE_SALES_TEAM = "CRM_SALES_TEAM";
    public static final String ENTITY_TYPE_SHIFT_TEMPLATE = "CRM_SHIFT_TEMPLATE";
    public static final String ENTITY_TYPE_SHIFT_ASSIGNMENT = "CRM_SHIFT_ASSIGNMENT";
    public static final String ENTITY_TYPE_STAFF_AVAILABILITY = "CRM_STAFF_AVAILABILITY";
    public static final String ENTITY_TYPE_CAPACITY_PLAN = "CRM_CAPACITY_PLAN";
    public static final String ENTITY_TYPE_WORKLOAD_ASSIGNMENT = "CRM_WORKLOAD_ASSIGNMENT";
    public static final String ENTITY_TYPE_SERVICE_ASSIGNMENT = "CRM_SERVICE_ASSIGNMENT";
}
