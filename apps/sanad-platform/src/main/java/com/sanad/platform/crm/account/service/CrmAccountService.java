package com.sanad.platform.crm.account.service;

import com.sanad.platform.crm.account.api.CreateCrmAccountRequest;
import com.sanad.platform.crm.account.api.CrmAccountResponse;
import com.sanad.platform.crm.account.domain.CrmAccount;
import com.sanad.platform.crm.account.domain.CrmAccountStatus;
import com.sanad.platform.crm.account.repository.CrmAccountQueryRepository;
import com.sanad.platform.crm.account.repository.CrmAccountRepository;
import com.sanad.platform.tenant.domain.Tenant;
import com.sanad.platform.tenant.domain.TenantStatus;
import com.sanad.platform.tenant.repository.TenantRepository;
import com.sanad.platform.user.domain.User;
import com.sanad.platform.user.domain.UserStatus;
import com.sanad.platform.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CrmAccountService {
    private static final int MAX_LIST_SIZE = 100;

    private final TenantRepository tenants;
    private final UserRepository users;
    private final CrmAccountRepository accounts;
    private final CrmAccountQueryRepository accountQueries;

    public CrmAccountService(
            TenantRepository tenants,
            UserRepository users,
            CrmAccountRepository accounts,
            CrmAccountQueryRepository accountQueries) {
        this.tenants = tenants;
        this.users = users;
        this.accounts = accounts;
        this.accountQueries = accountQueries;
    }

    @Transactional
    public CrmAccountResponse create(UUID tenantId, UUID actorId, CreateCrmAccountRequest request) {
        Tenant tenant = tenants.findById(tenantId)
                .filter(value -> value.getStatus() == TenantStatus.ACTIVE)
                .orElseThrow(() -> new EntityNotFoundException("Active tenant not found"));
        validateOwner(tenantId, request.ownerUserId());

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
        PageRequest request = PageRequest.of(
                0,
                MAX_LIST_SIZE,
                Sort.by(Sort.Direction.ASC, "displayName"));
        return accountQueries
                .findByTenant_IdAndLifecycleStatusNot(tenantId, CrmAccountStatus.ARCHIVED, request)
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

    private void validateOwner(UUID tenantId, UUID ownerUserId) {
        if (ownerUserId == null) return;
        User owner = users.findByTenantIdAndId(tenantId, ownerUserId)
                .orElseThrow(() -> new EntityNotFoundException("CRM owner not found in tenant"));
        if (owner.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalArgumentException("CRM owner must be active");
        }
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
