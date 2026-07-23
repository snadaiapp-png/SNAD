package com.sanad.platform.crm.ownership.domain;

import java.util.UUID;

/** Raised when another transaction is activating a version of the same rule. */
public class ConcurrentRuleActivationConflictException extends OwnershipDomainException {
    public ConcurrentRuleActivationConflictException(UUID tenantId, UUID ruleId) {
        super("Concurrent assignment-rule activation conflict: tenant="
                + tenantId + " rule=" + ruleId);
    }
}
