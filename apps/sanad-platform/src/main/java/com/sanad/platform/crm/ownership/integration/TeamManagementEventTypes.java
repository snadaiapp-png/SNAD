package com.sanad.platform.crm.ownership.integration;

/**
 * Domain event type definitions for CRM-008 Team Management.
 *
 * <p>Defines the event types published by CRM-008 UseCases.
 * Events are recorded via TimelineEventPort and AuditPort.
 */
public final class TeamManagementEventTypes {

    private TeamManagementEventTypes() {}

    // ── Team Events ──────────────────────────────────────────────────────

    public static final String TEAM_CREATED = "crm.team.created";
    public static final String TEAM_UPDATED = "crm.team.updated";
    public static final String TEAM_ARCHIVED = "crm.team.archived";
    public static final String TEAM_ACTIVATED = "crm.team.activated";

    // ── Shift Events ─────────────────────────────────────────────────────

    public static final String SHIFT_TEMPLATE_CREATED = "crm.shift_template.created";
    public static final String SHIFT_TEMPLATE_UPDATED = "crm.shift_template.updated";
    public static final String SHIFT_TEMPLATE_PUBLISHED = "crm.shift_template.published";
    public static final String SHIFT_TEMPLATE_CANCELLED = "crm.shift_template.cancelled";
    public static final String SHIFT_ASSIGNED = "crm.shift.assigned";
    public static final String SHIFT_ASSIGNMENT_UPDATED = "crm.shift_assignment.updated";
    public static final String SHIFT_ASSIGNMENT_CANCELLED = "crm.shift_assignment.cancelled";

    // ── Availability Events ──────────────────────────────────────────────

    public static final String AVAILABILITY_SUBMITTED = "crm.availability.submitted";
    public static final String AVAILABILITY_APPROVED = "crm.availability.approved";
    public static final String AVAILABILITY_REJECTED = "crm.availability.rejected";
    public static final String AVAILABILITY_DELETED = "crm.availability.deleted";

    // ── Skill Events ─────────────────────────────────────────────────────

    public static final String SKILL_REGISTERED = "crm.skill.registered";
    public static final String SKILL_UPDATED = "crm.skill.updated";
    public static final String SKILL_DELETED = "crm.skill.deleted";

    // ── Capacity Events ──────────────────────────────────────────────────

    public static final String CAPACITY_CREATED = "crm.capacity.created";
    public static final String CAPACITY_ADJUSTED = "crm.capacity.adjusted";
    public static final String CAPACITY_CHANGED = "crm.capacity.changed";

    // ── Workload Events ──────────────────────────────────────────────────

    public static final String WORKLOAD_ASSIGNED = "crm.workload.assigned";
    public static final String WORKLOAD_REASSIGNED = "crm.workload.reassigned";
    public static final String WORKLOAD_RELEASED = "crm.workload.released";
    public static final String WORKLOAD_BALANCED = "crm.workload.balanced";

    // ── Service Assignment Events ────────────────────────────────────────

    public static final String SERVICE_ASSIGNED = "crm.service.assigned";
    public static final String SERVICE_REASSIGNED = "crm.service.reassigned";
    public static final String SERVICE_COMPLETED = "crm.service.completed";
    public static final String SERVICE_CANCELLED = "crm.service.cancelled";
}
