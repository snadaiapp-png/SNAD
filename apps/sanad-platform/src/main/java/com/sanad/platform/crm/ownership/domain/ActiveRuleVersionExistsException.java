package com.sanad.platform.crm.ownership.domain;

import java.util.UUID;

public class ActiveRuleVersionExistsException extends OwnershipDomainException {
    public ActiveRuleVersionExistsException(UUID tenantId, UUID ruleId) {
        super("An ACTIVE version already exists for this rule: tenant=" + tenantId + " rule=" + ruleId);
    }
}
