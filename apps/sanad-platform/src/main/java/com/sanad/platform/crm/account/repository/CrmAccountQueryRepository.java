package com.sanad.platform.crm.account.repository;

import com.sanad.platform.crm.account.domain.CrmAccount;
import com.sanad.platform.crm.account.domain.CrmAccountStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CrmAccountQueryRepository extends JpaRepository<CrmAccount, UUID> {
    Page<CrmAccount> findByTenant_IdAndLifecycleStatusNot(
            UUID tenantId,
            CrmAccountStatus excludedStatus,
            Pageable pageable);
}
