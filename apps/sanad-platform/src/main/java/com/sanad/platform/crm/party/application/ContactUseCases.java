package com.sanad.platform.crm.party.application;

import com.sanad.platform.crm.party.domain.ContactRepository;
import com.sanad.platform.crm.party.domain.ContactRepository.ContactRecord;
import com.sanad.platform.crm.party.domain.ContactRepository.CreateContactCommand;
import com.sanad.platform.crm.party.domain.ContactRepository.UpdateContactCommand;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

public class ContactUseCases {
    private final ContactRepository repo;
    public ContactUseCases(ContactRepository repo) { this.repo = repo; }
    @Transactional
    public ContactRecord create(UUID tenantId, UUID actorId, CreateContactCommand cmd) { return repo.create(tenantId, actorId, cmd); }
    public ContactRecord getById(UUID tenantId, UUID contactId) { return repo.findById(tenantId, contactId); }
    public List<ContactRecord> list(UUID tenantId, int limit, UUID accountId, String search) { return repo.findAll(tenantId, limit, accountId, search); }
    @Transactional
    public ContactRecord update(UUID tenantId, UUID actorId, UUID contactId, UpdateContactCommand cmd, long expectedVersion) { return repo.update(tenantId, actorId, contactId, cmd, expectedVersion); }
    @Transactional
    public ContactRecord archive(UUID tenantId, UUID actorId, UUID contactId, long expectedVersion) { return repo.archive(tenantId, actorId, contactId, expectedVersion); }
    @Transactional
    public ContactRecord restore(UUID tenantId, UUID actorId, UUID contactId, long expectedVersion) { return repo.restore(tenantId, actorId, contactId, expectedVersion); }
}
