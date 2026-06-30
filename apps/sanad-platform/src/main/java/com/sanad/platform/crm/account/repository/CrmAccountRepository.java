package com.sanad.platform.crm.account.repository;

import com.sanad.platform.crm.account.domain.CrmAccount;
import com.sanad.platform.crm.account.domain.CrmAccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CrmAccountRepository extends JpaRepository<CrmAccount, UUID> {
    Optional<CrmAccount> findByTenant_IdAndId(UUID tenantId, UUID accountId);
    List<CrmAccount> findByTenant_IdAndLifecycleStatusNotOrderByDisplayNameAsc(
            UUID tenantId, CrmAccountStatus excludedStatus);
}
