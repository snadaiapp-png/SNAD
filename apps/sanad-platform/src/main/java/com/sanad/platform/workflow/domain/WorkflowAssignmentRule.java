package com.sanad.platform.workflow.domain;

import java.util.UUID;

/**
 * Assignment rule configuration (design decision D3). Rules are persisted as
 * discriminated JSON and parsed through this closed set — polymorphic
 * deserialization of untrusted classes is forbidden.
 *
 * <p>Assignment is not authorization: resolution produces concrete
 * {@code Employee.id} candidates only; authorization is revalidated
 * server-side at action time.</p>
 */
public sealed interface WorkflowAssignmentRule {

    /** Direct assignment to one concrete employee. */
    record Employee(UUID employeeId) implements WorkflowAssignmentRule {}

    /** The ACTIVE manager of the subject employee. */
    record Manager(UUID subjectEmployeeId) implements WorkflowAssignmentRule {}

    /** All ACTIVE employees holding the position. */
    record Position(UUID positionId) implements WorkflowAssignmentRule {}

    /** All ACTIVE employees in the department. */
    record Department(UUID departmentId) implements WorkflowAssignmentRule {}

    /** ACTIVE employees whose linked ACTIVE user holds the role code. */
    record Role(String roleCode) implements WorkflowAssignmentRule {}

    /** ACTIVE employees whose linked ACTIVE user currently grants the capability. */
    record Permission(String capabilityCode) implements WorkflowAssignmentRule {}
}
