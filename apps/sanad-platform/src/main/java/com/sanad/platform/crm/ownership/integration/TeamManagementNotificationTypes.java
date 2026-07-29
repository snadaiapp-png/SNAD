package com.sanad.platform.crm.ownership.integration;

import java.util.Set;

/**
 * Notification type definitions for CRM-008 Team Management.
 *
 * <p>Defines the notification types that can be triggered by CRM-008 events.
 * Notifications are dispatched via the notification port when implemented.
 */
public final class TeamManagementNotificationTypes {

    private TeamManagementNotificationTypes() {}

    // ── Assignment Notifications ─────────────────────────────────────────

    /** Notify staff member when assigned to a shift */
    public static final String SHIFT_ASSIGNED_TO_STAFF = "crm.notification.shift.assigned_to_staff";

    /** Notify team manager when shift is assigned */
    public static final String SHIFT_ASSIGNED_TO_TEAM = "crm.notification.shift.assigned_to_team";

    /** Notify staff when workload is assigned */
    public static final String WORKLOAD_ASSIGNED = "crm.notification.workload.assigned";

    /** Notify team when service is assigned */
    public static final String SERVICE_ASSIGNED_TO_TEAM = "crm.notification.service.assigned_to_team";

    // ── Shift Change Notifications ───────────────────────────────────────

    /** Notify staff when shift is cancelled */
    public static final String SHIFT_CANCELLED = "crm.notification.shift.cancelled";

    /** Notify staff when shift is updated */
    public static final String SHIFT_UPDATED = "crm.notification.shift.updated";

    /** Notify staff when shift template is published */
    public static final String SHIFT_TEMPLATE_PUBLISHED = "crm.notification.shift_template.published";

    // ── Availability Notifications ───────────────────────────────────────

    /** Notify manager when availability is submitted */
    public static final String AVAILABILITY_SUBMITTED = "crm.notification.availability.submitted";

    /** Notify staff when availability is approved */
    public static final String AVAILABILITY_APPROVED = "crm.notification.availability.approved";

    /** Notify staff when availability is rejected */
    public static final String AVAILABILITY_REJECTED = "crm.notification.availability.rejected";

    // ── Capacity Notifications ───────────────────────────────────────────

    /** Alert when team capacity exceeds threshold */
    public static final String CAPACITY_ALERT = "crm.notification.capacity.alert";

    /** Alert when team capacity is forecasted to be exceeded */
    public static final String CAPACITY_FORECAST_ALERT = "crm.notification.capacity.forecast_alert";

    // ── Workload Notifications ───────────────────────────────────────────

    /** Notify staff when workload is reassigned */
    public static final String WORKLOAD_REASSIGNED = "crm.notification.workload.reassigned";

    /** Notify staff when workload is released */
    public static final String WORKLOAD_RELEASED = "crm.notification.workload.released";

    // ── Service Assignment Notifications ─────────────────────────────────

    /** Notify team when service is reassigned */
    public static final String SERVICE_REASSIGNED = "crm.notification.service.reassigned";

    /** Notify team when service assignment is completed */
    public static final String SERVICE_COMPLETED = "crm.notification.service.completed";

    /**
     * Notification types that require manager approval.
     */
    public static final Set<String> MANAGER_APPROVAL_REQUIRED = Set.of(
            AVAILABILITY_SUBMITTED,
            CAPACITY_ALERT,
            CAPACITY_FORECAST_ALERT
    );

    /**
     * Notification types that are sent to staff members.
     */
    public static final Set<String> STAFF_NOTIFICATIONS = Set.of(
            SHIFT_ASSIGNED_TO_STAFF,
            SHIFT_CANCELLED,
            SHIFT_UPDATED,
            AVAILABILITY_APPROVED,
            AVAILABILITY_REJECTED,
            WORKLOAD_ASSIGNED,
            WORKLOAD_REASSIGNED,
            WORKLOAD_RELEASED
    );

    /**
     * Notification types that are sent to team managers.
     */
    public static final Set<String> MANAGER_NOTIFICATIONS = Set.of(
            SHIFT_ASSIGNED_TO_TEAM,
            AVAILABILITY_SUBMITTED,
            SERVICE_ASSIGNED_TO_TEAM,
            CAPACITY_ALERT,
            CAPACITY_FORECAST_ALERT,
            SERVICE_REASSIGNED,
            SERVICE_COMPLETED
    );
}
