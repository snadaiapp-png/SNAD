package com.sanad.platform.crm.party.domain;

import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.party.domain.AccountRepository.AccountRecord;
import java.util.UUID;

/**
 * Domain policies for Account lifecycle operations.
 * Enforces business rules that were previously embedded in CrmService.
 */
public final class AccountPolicy {

    /** Reject updates to archived accounts. */
    public static void assertNotArchived(AccountRecord account) {
        if ("ARCHIVED".equals(account.lifecycleStatus())) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR,
                    "Archived CRM account cannot be updated");
        }
    }

    /** Reject self-parenting. */
    public static void assertNotSelfParent(UUID accountId, UUID parentAccountId) {
        if (parentAccountId != null && parentAccountId.equals(accountId)) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR,
                    "Account cannot be its own parent");
        }
    }

    /** Validate that owner is not null — existence check is done by application layer. */
    public static void validateOwner(UUID ownerUserId) {
        if (ownerUserId == null) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR,
                    "Owner is required");
        }
    }

    private AccountPolicy() {}
}
