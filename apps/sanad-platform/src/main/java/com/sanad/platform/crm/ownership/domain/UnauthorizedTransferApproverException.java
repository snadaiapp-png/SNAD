package com.sanad.platform.crm.ownership.domain;

import java.util.UUID;

public class UnauthorizedTransferApproverException extends OwnershipDomainException {
    public UnauthorizedTransferApproverException(UUID tenantId, UUID transferId, UUID approverId) {
        super("Unauthorized transfer approver: tenant=" + tenantId + " transfer=" + transferId + " approver=" + approverId);
    }
}
