package com.sanad.platform.finance.application;

import com.sanad.platform.finance.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service for {@link FinanceAccount} lifecycle management.
 */
@Service
public class FinanceAccountService {

    private static final Logger log = LoggerFactory.getLogger(FinanceAccountService.class);
    private final FinanceAccountRepository accountRepo;

    public FinanceAccountService(FinanceAccountRepository accountRepo) {
        this.accountRepo = accountRepo;
    }

    @Transactional
    public FinanceAccount create(FinanceAccount account) {
        var saved = accountRepo.save(account);
        log.info("FinanceAccount created: tenant={} code={} type={}", saved.tenantId(), saved.code(), saved.accountType());
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<FinanceAccount> findById(UUID tenantId, UUID id) {
        return accountRepo.findById(tenantId, id);
    }

    @Transactional(readOnly = true)
    public List<FinanceAccount> findByTenant(UUID tenantId, int limit) {
        return accountRepo.findByTenant(tenantId, limit);
    }

    @Transactional(readOnly = true)
    public List<FinanceAccount> findByType(UUID tenantId, FinanceAccount.AccountType type, int limit) {
        return accountRepo.findByTenantAndType(tenantId, type, limit);
    }

    @Transactional
    public FinanceAccount deactivate(UUID tenantId, UUID id) {
        var account = load(tenantId, id);
        var updated = accountRepo.save(account.deactivate());
        log.info("FinanceAccount deactivated: tenant={} code={}", tenantId, updated.code());
        return updated;
    }

    @Transactional
    public FinanceAccount archive(UUID tenantId, UUID id) {
        var account = load(tenantId, id);
        var updated = accountRepo.save(account.archive());
        log.info("FinanceAccount archived: tenant={} code={}", tenantId, updated.code());
        return updated;
    }

    private FinanceAccount load(UUID tenantId, UUID id) {
        return accountRepo.findById(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("FinanceAccount not found: " + id));
    }
}
