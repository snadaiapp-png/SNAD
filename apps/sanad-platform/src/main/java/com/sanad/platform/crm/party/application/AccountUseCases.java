package com.sanad.platform.crm.party.application;

import com.sanad.platform.crm.party.domain.AccountRepository;
import com.sanad.platform.crm.party.domain.AccountRepository.AccountRecord;
import com.sanad.platform.crm.party.domain.AccountRepository.CreateAccountCommand;
import com.sanad.platform.crm.party.domain.AccountRepository.UpdateAccountCommand;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

/**
 * Application use cases for Account operations.
 * Orchestrates domain logic + repository port — no SQL, no HTTP.
 */
public class AccountUseCases {
    private final AccountRepository repo;

    public AccountUseCases(AccountRepository repo) { this.repo = repo; }

    @Transactional
    public AccountRecord create(UUID tenantId, UUID actorId, CreateAccountCommand cmd) { return repo.create(tenantId, actorId, cmd); }
    public AccountRecord getById(UUID tenantId, UUID accountId) { return repo.findById(tenantId, accountId); }
    public List<AccountRecord> list(UUID tenantId, int limit, String search) { return repo.findAll(tenantId, limit, search); }
    @Transactional
    public AccountRecord update(UUID tenantId, UUID actorId, UUID accountId, UpdateAccountCommand cmd, long expectedVersion) { return repo.update(tenantId, actorId, accountId, cmd, expectedVersion); }
    @Transactional
    public AccountRecord archive(UUID tenantId, UUID actorId, UUID accountId, long expectedVersion) { return repo.archive(tenantId, actorId, accountId, expectedVersion); }
    @Transactional
    public AccountRecord restore(UUID tenantId, UUID actorId, UUID accountId, long expectedVersion) { return repo.restore(tenantId, actorId, accountId, expectedVersion); }
}
