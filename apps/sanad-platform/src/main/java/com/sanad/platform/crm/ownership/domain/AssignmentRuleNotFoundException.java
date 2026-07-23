package com.sanad.platform.crm.ownership.domain;

import java.util.UUID;

public class AssignmentRuleNotFoundException extends OwnershipDomainException {
    public AssignmentRuleNotFoundException(UUID tenantId, UUID ruleId) {
        super("Assignment rule not found: tenant=" + tenantId + " rule=" + ruleId);
    }
}
