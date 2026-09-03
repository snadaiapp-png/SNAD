package com.sanad.platform.hr.assignment.domain;

/**
 * Assignment type — PRIMARY or SECONDARY.
 *
 * <p>Every ACTIVE Employment must have exactly one effective PRIMARY
 * Assignment at any given time. SECONDARY assignments are additional
 * and optional.</p>
 */
public enum AssignmentType {
    PRIMARY,
    SECONDARY
}
