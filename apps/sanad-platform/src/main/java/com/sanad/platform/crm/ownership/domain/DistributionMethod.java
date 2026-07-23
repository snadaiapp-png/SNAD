package com.sanad.platform.crm.ownership.domain;

/** Distribution method for an assignment rule version. */
public enum DistributionMethod {
    DIRECT_OWNER,
    TEAM_ASSIGNMENT,
    QUEUE_ASSIGNMENT,
    ROUND_ROBIN,
    LEAST_LOADED,
    WEIGHTED,
    TERRITORY_BASED,
    SKILL_BASED,
    RULE_CHAIN
}
