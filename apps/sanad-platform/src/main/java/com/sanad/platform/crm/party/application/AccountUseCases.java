package com.sanad.platform.crm.party.application;

import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.AuditPort.AuditChange;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.party.domain.AccountHierarchyPort;
import com.sanad.platform.crm.party.domain.AccountMasterRepository;
import com.sanad.platform.crm.party.domain.AccountPolicy;
import com.sanad.platform.crm.party.domain.AccountRepository;
import com.sanad.platform.crm.party.domain.AccountRepository.AccountRecord;
import com.sanad.platform.crm.party.domain.AccountRepository.CreateAccountCommand;
import com.sanad.platform.crm.party.domain.AccountRepository.UpdateAccountCommand;
import com.sanad.platform.crm.party.domain.OwnerValidationPort;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class AccountUseCases {
    private final AccountRepository repo;
    private final AccountHierarchyPort hierarchy;
    private final OwnerValidationPort ownerValidation;
    private final AccountMasterRepository accountMaster;
    private final AuditPort audit;
    private final TimelineEventPort timeline;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper;

    public AccountUseCases(
            AccountRepository repo,
            AccountHierarchyPort hierarchy,
            OwnerValidationPort ownerValidation,
            AccountMasterRepository accountMaster,
            AuditPort audit,
            TimelineEventPort timeline,
            com.fasterxml.jackson.databind.ObjectMapper mapper) {
        this.repo = repo;
        this.hierarchy = hierarchy;
        this.ownerValidation = ownerValidation;
        this.accountMaster = accountMaster;
        this.audit = audit;
        this.timeline = timeline;
        this.mapper = mapper;
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
        accountMaster.initializeProfile(
                tenantId, actorId, created.id(), created.displayName(), created.displayName());
        accountMaster.recordStatusChange(
                tenantId, actorId, created.id(), null, created.lifecycleStatus(), "Account created", now);
        if (created.ownerUserId() != null) {
            accountMaster.recordOwnershipChange(
                    tenantId, actorId, created.id(), null, created.ownerUserId(), "Account created", now);
        }
        timeline.record(tenantId, "ACCOUNT", created.id(), "crm.account.created", "Account created",
                "CRM_ACCOUNT", created.id(), actorId, now);
        audit.record(tenantId, actorId, "CREATE", "ACCOUNT", created.id(),
                new AuditChange(null, serializeAccount(created)), now);
        return created;
    }

    public AccountRecord getById(UUID tenantId, UUID accountId) {
        return repo.findById(tenantId, accountId);
    }

    public List<AccountRecord> list(UUID tenantId, int limit, String search) {
        return repo.findAll(tenantId, limit, search);
    }

    @Transactional
    public AccountRecord update(
            UUID tenantId,
            UUID actorId,
            UUID accountId,
            UpdateAccountCommand cmd,
            long expectedVersion) {
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
        if (!Objects.equals(current.ownerUserId(), updated.ownerUserId())) {
            accountMaster.recordOwnershipChange(
                    tenantId, actorId, accountId, current.ownerUserId(), updated.ownerUserId(),
                    "Account owner updated", now);
        }
        timeline.record(tenantId, "ACCOUNT", accountId, "crm.account.updated", "Account updated",
                "CRM_ACCOUNT", accountId, actorId, now);
        audit.record(tenantId, actorId, "UPDATE", "ACCOUNT", accountId,
                new AuditChange(serializeAccount(current), serializeAccount(updated)), now);
        return updated;
    }

    @Transactional
    public AccountRecord archive(UUID tenantId, UUID actorId, UUID accountId, long expectedVersion) {
        if (hierarchy.hasActiveChildren(tenantId, accountId)) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR,
                    "Cannot archive account with active child accounts");
        }
        AccountRecord current = repo.findById(tenantId, accountId);
        AccountRecord archived = repo.archive(tenantId, actorId, accountId, expectedVersion);
        Instant now = Instant.now();
        accountMaster.recordStatusChange(
                tenantId, actorId, accountId, current.lifecycleStatus(), archived.lifecycleStatus(),
                "Account archived", now);
        timeline.record(tenantId, "ACCOUNT", accountId, "crm.account.archived", "Account archived",
                "CRM_ACCOUNT", accountId, actorId, now);
        audit.record(tenantId, actorId, "ARCHIVE", "ACCOUNT", accountId,
                new AuditChange(serializeAccount(current), serializeAccount(archived)), now);
        return archived;
    }

    @Transactional
    public AccountRecord restore(UUID tenantId, UUID actorId, UUID accountId, long expectedVersion) {
        AccountRecord before = repo.findById(tenantId, accountId);
        AccountRecord restored = repo.restore(tenantId, actorId, accountId, expectedVersion);
        Instant now = Instant.now();
        accountMaster.recordStatusChange(
                tenantId, actorId, accountId, before.lifecycleStatus(), restored.lifecycleStatus(),
                "Account reactivated", now);
        timeline.record(tenantId, "ACCOUNT", accountId, "crm.account.restored", "Account restored",
                "CRM_ACCOUNT", accountId, actorId, now);
        audit.record(tenantId, actorId, "RESTORE", "ACCOUNT", accountId,
                new AuditChange(serializeAccount(before), serializeAccount(restored)), now);
        return restored;
    }

    private com.fasterxml.jackson.databind.JsonNode serializeAccount(AccountRecord r) {
        if (r == null) return null;
        var node = mapper.createObjectNode();
        node.put("id", r.id() == null ? null : r.id().toString());
        node.put("version", r.version());
        node.put("displayName", r.displayName());
        node.put("accountType", r.accountType());
        node.put("lifecycleStatus", r.lifecycleStatus());
        node.put("primaryCurrencyCode", r.primaryCurrencyCode());
        if (r.ownerUserId() != null) node.put("ownerUserId", r.ownerUserId().toString());
        if (r.parentAccountId() != null) node.put("parentAccountId", r.parentAccountId().toString());
        return node;
    }
}
