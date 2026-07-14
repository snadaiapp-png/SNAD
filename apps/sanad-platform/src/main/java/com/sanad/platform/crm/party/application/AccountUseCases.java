package com.sanad.platform.crm.party.application;

import com.sanad.platform.crm.party.domain.*;
import com.sanad.platform.crm.party.domain.AccountRepository.AccountRecord;
import com.sanad.platform.crm.party.domain.AccountRepository.CreateAccountCommand;
import com.sanad.platform.crm.party.domain.AccountRepository.UpdateAccountCommand;
import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Application use cases for Account operations.
 * Orchestrates domain policies, repository, audit, and timeline — all within
 * a single transaction. No SQL, no HTTP.
 */
public class AccountUseCases {
    private final AccountRepository repo;
    private final AccountHierarchyPort hierarchy;
    private final OwnerValidationPort ownerValidation;
    private final AuditPort audit;
    private final TimelineEventPort timeline;

    public AccountUseCases(AccountRepository repo, AccountHierarchyPort hierarchy,
                           OwnerValidationPort ownerValidation, AuditPort audit,
                           TimelineEventPort timeline) {
        this.repo = repo;
        this.hierarchy = hierarchy;
        this.ownerValidation = ownerValidation;
        this.audit = audit;
        this.timeline = timeline;
    }

    @Transactional
    public AccountRecord create(UUID tenantId, UUID actorId, CreateAccountCommand cmd) {
        AccountPolicy.validateOwner(cmd.ownerUserId());
        if (!ownerValidation.isValidOwner(tenantId, cmd.ownerUserId())) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR, "Invalid owner");
        }
        if (cmd.parentAccountId() != null) {
            AccountPolicy.assertNotSelfParent(null, cmd.parentAccountId());
            if (!hierarchy.parentExists(tenantId, cmd.parentAccountId())) {
                throw new CrmContractException(CrmErrorCode.CRM_ACCOUNT_NOT_FOUND, "Parent account not found");
            }
        }
        AccountRecord created = repo.create(tenantId, actorId, cmd);
        Instant now = Instant.now();
        timeline.record(tenantId, "ACCOUNT", created.id(), "crm.account.created", "Account created",
                "CRM_ACCOUNT", created.id(), actorId, now);
        audit.record(tenantId, actorId, "CREATE", "ACCOUNT", created.id(), null, now);
        return created;
    }

    public AccountRecord getById(UUID tenantId, UUID accountId) {
        return repo.findById(tenantId, accountId);
    }

    public List<AccountRecord> list(UUID tenantId, int limit, String search) {
        return repo.findAll(tenantId, limit, search);
    }

    @Transactional
    public AccountRecord update(UUID tenantId, UUID actorId, UUID accountId, UpdateAccountCommand cmd, long expectedVersion) {
        AccountRecord current = repo.findById(tenantId, accountId);
        AccountPolicy.assertNotArchived(current);
        if (cmd.ownerUserId() != null && !ownerValidation.isValidOwner(tenantId, cmd.ownerUserId())) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR, "Invalid owner");
        }
        if (cmd.parentAccountId() != null) {
            AccountPolicy.assertNotSelfParent(accountId, cmd.parentAccountId());
            if (!hierarchy.parentExists(tenantId, cmd.parentAccountId())) {
                throw new CrmContractException(CrmErrorCode.CRM_ACCOUNT_NOT_FOUND, "Parent account not found");
            }
            if (hierarchy.wouldCreateCycle(tenantId, accountId, cmd.parentAccountId())) {
                throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR, "Account hierarchy cycle detected");
            }
        }
        AccountRecord updated = repo.update(tenantId, actorId, accountId, cmd, expectedVersion);
        Instant now = Instant.now();
        timeline.record(tenantId, "ACCOUNT", accountId, "crm.account.updated", "Account updated",
                "CRM_ACCOUNT", accountId, actorId, now);
        audit.record(tenantId, actorId, "UPDATE", "ACCOUNT", accountId, null, now);
        return updated;
    }

    @Transactional
    public AccountRecord archive(UUID tenantId, UUID actorId, UUID accountId, long expectedVersion) {
        if (hierarchy.hasActiveChildren(tenantId, accountId)) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR,
                    "Cannot archive account with active child accounts");
        }
        AccountRecord archived = repo.archive(tenantId, actorId, accountId, expectedVersion);
        Instant now = Instant.now();
        timeline.record(tenantId, "ACCOUNT", accountId, "crm.account.archived", "Account archived",
                "CRM_ACCOUNT", accountId, actorId, now);
        audit.record(tenantId, actorId, "ARCHIVE", "ACCOUNT", accountId, null, now);
        return archived;
    }

    @Transactional
    public AccountRecord restore(UUID tenantId, UUID actorId, UUID accountId, long expectedVersion) {
        AccountRecord restored = repo.restore(tenantId, actorId, accountId, expectedVersion);
        Instant now = Instant.now();
        timeline.record(tenantId, "ACCOUNT", accountId, "crm.account.restored", "Account restored",
                "CRM_ACCOUNT", accountId, actorId, now);
        audit.record(tenantId, actorId, "RESTORE", "ACCOUNT", accountId, null, now);
        return restored;
    }
}
