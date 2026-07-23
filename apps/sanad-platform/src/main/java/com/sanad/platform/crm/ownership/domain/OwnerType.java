package com.sanad.platform.crm.ownership.domain;

/**
 * Type of owner for a CRM record assignment.
 *
 * <p>USER: individual ownership (CRM-G1 compatible).
 * TEAM: sales team ownership.
 * QUEUE: queue-based ownership (claim/release model).
 */
public enum OwnerType {
    USER,
    TEAM,
    QUEUE
}
