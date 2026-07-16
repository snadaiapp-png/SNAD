package com.sanad.platform.crm.party.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.AuditPort.AuditChange;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.party.domain.ContactRepository;
import com.sanad.platform.crm.party.domain.ContactRepository.ContactRecord;
import com.sanad.platform.crm.party.domain.ContactRepository.CreateContactCommand;
import com.sanad.platform.crm.party.domain.ContactRepository.UpdateContactCommand;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ContactUseCases {
    private final ContactRepository repo;
    private final AuditPort audit;
    private final TimelineEventPort timeline;
    private final ObjectMapper objectMapper;

    public ContactUseCases(
            ContactRepository repo,
            AuditPort audit,
            TimelineEventPort timeline,
            ObjectMapper objectMapper) {
        this.repo = repo;
        this.audit = audit;
        this.timeline = timeline;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ContactRecord create(UUID tenantId, UUID actorId, CreateContactCommand command) {
        ContactRecord created = repo.create(tenantId, actorId, command);
        Instant now = Instant.now();
        timeline.record(tenantId, "CONTACT", created.id(), "crm.contact.created", "Contact created",
                "CRM_CONTACT", created.id(), actorId, now);
        audit.record(tenantId, actorId, "CREATE", "CONTACT", created.id(),
                new AuditChange(null, json(created)), now);
        if (created.accountId() != null) {
            timeline.record(tenantId, "ACCOUNT", created.accountId(), "crm.contact.relationship.created",
                    "Contact relationship created", "CRM_CONTACT", created.id(), actorId, now);
        }
        return created;
    }

    public ContactRecord getById(UUID tenantId, UUID contactId) {
        return repo.findById(tenantId, contactId);
    }

    public List<ContactRecord> list(UUID tenantId, int limit, UUID accountId, String search) {
        return repo.findAll(tenantId, limit, accountId, search);
    }

    @Transactional
    public ContactRecord update(
            UUID tenantId,
            UUID actorId,
            UUID contactId,
            UpdateContactCommand command,
            long expectedVersion) {
        ContactRecord before = repo.findById(tenantId, contactId);
        ContactRecord after = repo.update(tenantId, actorId, contactId, command, expectedVersion);
        Instant now = Instant.now();
        audit.record(tenantId, actorId, "UPDATE", "CONTACT", contactId,
                new AuditChange(json(before), json(after)), now);
        timeline.record(tenantId, "CONTACT", contactId, "crm.contact.updated", "Contact updated",
                "CRM_CONTACT", contactId, actorId, now);
        return after;
    }

    @Transactional
    public ContactRecord archive(UUID tenantId, UUID actorId, UUID contactId, long expectedVersion) {
        ContactRecord before = repo.findById(tenantId, contactId);
        ContactRecord after = repo.archive(tenantId, actorId, contactId, expectedVersion);
        Instant now = Instant.now();
        audit.record(tenantId, actorId, "ARCHIVE", "CONTACT", contactId,
                new AuditChange(json(before), json(after)), now);
        timeline.record(tenantId, "CONTACT", contactId, "crm.contact.archived", "Contact archived",
                "CRM_CONTACT", contactId, actorId, now);
        return after;
    }

    @Transactional
    public ContactRecord restore(UUID tenantId, UUID actorId, UUID contactId, long expectedVersion) {
        ContactRecord before = repo.findById(tenantId, contactId);
        ContactRecord after = repo.restore(tenantId, actorId, contactId, expectedVersion);
        Instant now = Instant.now();
        audit.record(tenantId, actorId, "RESTORE", "CONTACT", contactId,
                new AuditChange(json(before), json(after)), now);
        timeline.record(tenantId, "CONTACT", contactId, "crm.contact.restored", "Contact restored",
                "CRM_CONTACT", contactId, actorId, now);
        return after;
    }

    private JsonNode json(ContactRecord record) {
        return record == null ? null : objectMapper.valueToTree(record);
    }
}
