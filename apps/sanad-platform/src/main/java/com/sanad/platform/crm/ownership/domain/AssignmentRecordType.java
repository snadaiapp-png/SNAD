package com.sanad.platform.crm.ownership.domain;

/**
 * CRM record types that can be assigned/owned.
 * Matches the subject_type CHECK constraint on crm_assignments (CRM-G1).
 */
public enum AssignmentRecordType {
    ACCOUNT,
    CONTACT,
    LEAD,
    OPPORTUNITY,
    ACTIVITY,
    TASK
}
