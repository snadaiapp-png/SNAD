package com.sanad.platform.finance.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repository interface for {@link FinanceAccount}. */
public interface FinanceAccountRepository {
    FinanceAccount save(FinanceAccount account);
    Optional<FinanceAccount> findById(UUID tenantId, UUID id);
    Optional<FinanceAccount> findByCode(UUID tenantId, String code);
    List<FinanceAccount> findByTenant(UUID tenantId, int limit);
    List<FinanceAccount> findByTenantAndType(UUID tenantId, FinanceAccount.AccountType type, int limit);
}
