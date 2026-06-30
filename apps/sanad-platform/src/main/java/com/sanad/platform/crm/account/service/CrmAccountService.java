package com.sanad.platform.crm.account.service;

import com.sanad.platform.crm.account.api.CreateCrmAccountRequest;
import com.sanad.platform.crm.account.api.CrmAccountResponse;
import com.sanad.platform.crm.account.domain.CrmAccount;
import com.sanad.platform.crm.account.domain.CrmAccountStatus;
import com.sanad.platform.crm.account.repository.CrmAccountRepository;
import com.sanad.platform.tenant.domain.Tenant;
import com.sanad.platform.tenant.repository.TenantRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CrmAccountService {
    private final TenantRepository tenants;
    private final CrmAccountRepository accounts;

    public CrmAccountService(TenantRepository tenants, CrmAccountRepository accounts) {
        this.tenants = tenants;
        this.accounts = accounts;
    }

    @Transactional
    public CrmAccountResponse create(UUID tenantId, UUID actorId, CreateCrmAccountRequest request) {
        Tenant tenant = tenants.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));
        CrmAccount account = new CrmAccount(
                tenant,
                request.displayName(),
                request.accountType(),
                request.ownerUserId(),
                request.primaryCurrencyCode(),
                request.preferredLocale(),
                request.timeZone(),
                request.source(),
                actorId
        );
        return toResponse(accounts.save(account));
    }

    @Transactional(readOnly = true)
    public CrmAccountResponse get(UUID tenantId, UUID accountId) {
        return toResponse(find(tenantId, accountId));
    }

    @Transactional(readOnly = true)
    public List<CrmAccountResponse> list(UUID tenantId) {
        return accounts
                .findByTenant_IdAndLifecycleStatusNotOrderByDisplayNameAsc(
                        tenantId, CrmAccountStatus.ARCHIVED)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CrmAccountResponse archive(UUID tenantId, UUID actorId, UUID accountId) {
        CrmAccount account = find(tenantId, accountId);
        account.archive(actorId);
        return toResponse(accounts.save(account));
    }

    private CrmAccount find(UUID tenantId, UUID accountId) {
        return accounts.findByTenant_IdAndId(tenantId, accountId)
                .orElseThrow(() -> new EntityNotFoundException("CRM account not found"));
    }

    private CrmAccountResponse toResponse(CrmAccount account) {
        return new CrmAccountResponse(
                account.getId(), account.getVersion(), account.getDisplayName(),
                account.getAccountType(), account.getLifecycleStatus(),
                account.getOwnerUserId(), account.getPrimaryCurrencyCode(),
                account.getPreferredLocale(), account.getTimeZone(),
                account.getSource(), account.getCreatedAt(), account.getUpdatedAt()
        );
    }
}
