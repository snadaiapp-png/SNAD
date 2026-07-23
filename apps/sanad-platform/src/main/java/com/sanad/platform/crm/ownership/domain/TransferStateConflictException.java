package com.sanad.platform.crm.ownership.domain;

import java.util.UUID;

public class TransferStateConflictException extends OwnershipDomainException {
    public TransferStateConflictException(UUID tenantId, UUID transferId, TransferState currentState, TransferState targetState) {
        super("Transfer state conflict: tenant=" + tenantId + " transfer=" + transferId + " current=" + currentState + " target=" + targetState);
    }
}
