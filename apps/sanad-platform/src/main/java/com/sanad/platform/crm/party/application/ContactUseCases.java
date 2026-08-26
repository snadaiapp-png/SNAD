package com.sanad.platform.crm.party.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
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
import java.util.Objects;
import java.util.UUID;

/**
 * Contact application service — Strategy A1 adapter (C6-A).
 *
 * <p>The {@link #update} method is the single A1 orchestration point for
 * layered Contact PATCH behavior. It detects whether the command requests
 * an owner change, an ordinary field change, or both, and routes
 * accordingly:</p>
 *
 * <ul>
 *   <li><strong>Owner-only changed</strong>: delegates to
 *       {@link ContactTransferUseCases#transferContact} (canonical C5
 *       transfer). No generic ordinary UPDATE audit/timeline emitted —
 *       C8 will own dedicated transfer observability.</li>
 *   <li><strong>Ordinary-only changed</strong>: delegates to
 *       {@link ContactRepository#update} with a sanitized command
 *       (ownerUserId=null). Emits generic
 *       {@code crm.contact.updated} audit/timeline once.</li>
 *   <li><strong>Mixed (owner + ordinary)</strong>: executes canonical
 *       transfer first (version N → N+1), then ordinary sanitized update
 *       with expectedVersion = afterTransfer.version() (version N+1 →
 *       N+2). Both execute inside the same {@code @Transactional}
 *       boundary — atomic commit or rollback.</li>
 *   <li><strong>No-op (same owner, no ordinary fields)</strong>: returns
 *       current Contact unchanged. No SQL, no version increment, no
 *       audit/timeline.</li>
 * </ul>
 *
 * <h3>Version contract</h3>
 * <ul>
 *   <li>Owner-only: N → N+1</li>
 *   <li>Ordinary-only: N → N+1</li>
 *   <li>Mixed: N → N+2 (transfer +1, ordinary +1)</li>
 *   <li>Same-owner-only: N (no-op)</li>
 *   <li>Empty update: N (no-op)</li>
 *   <li>Same-owner + ordinary: N → N+1 (ordinary only)</li>
 * </ul>
 */
public class ContactUseCases {
    private static final String ARCHIVED_STATUS = "ARCHIVED";

    private final ContactRepository repo;
    private final AuditPort audit;
    private final TimelineEventPort timeline;
    private final ObjectMapper objectMapper;
    private final ContactTransferUseCases contactTransferUseCases;

    public ContactUseCases(
            ContactRepository repo,
            AuditPort audit,
            TimelineEventPort timeline,
            ObjectMapper objectMapper,
            ContactTransferUseCases contactTransferUseCases) {
        this.repo = repo;
        this.audit = audit;
        this.timeline = timeline;
        this.objectMapper = objectMapper;
        this.contactTransferUseCases = Objects.requireNonNull(contactTransferUseCases,
                "contactTransferUseCases");
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

    /**
     * A1 PATCH orchestration — see class Javadoc for the full algorithm.
     */
    @Transactional
    public ContactRecord update(
            UUID tenantId,
            UUID actorId,
            UUID contactId,
            UpdateContactCommand command,
            long expectedVersion) {
        // 1. Load current Contact.
        ContactRecord current = repo.findById(tenantId, contactId);

        // 2. Reject ARCHIVED.
        if (ARCHIVED_STATUS.equals(current.lifecycleStatus())) {
            throw new CrmContractException(CrmErrorCode.CONFLICT,
                    "Archived contacts cannot be modified without restoration.");
        }

        // 3. Enforce expectedVersion.
        if (current.version() != expectedVersion) {
            throw new CrmContractException(CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
        }

        // 4. Determine what changed.
        UUID requestedOwner = command.ownerUserId();
        boolean ownerChanged = requestedOwner != null
                && !requestedOwner.equals(current.ownerUserId());
        boolean ordinaryChanged = command.accountId() != null
                || command.givenName() != null
                || command.familyName() != null
                || command.primaryEmail() != null
                || command.primaryPhone() != null
                || command.preferredLocale() != null
                || command.timeZone() != null
                || command.consentSummary() != null;

        // 5. No-op: nothing changed.
        if (!ownerChanged && !ordinaryChanged) {
            return current;
        }

        // 6. Owner change → canonical transfer.
        ContactRecord afterTransfer = current;
        if (ownerChanged) {
            afterTransfer = contactTransferUseCases.transferContact(
                    tenantId,
                    contactId,
                    requestedOwner,
                    current.version(),
                    actorId,
                    Instant.now());
        }

        // 7. If no ordinary change → return after transfer (no generic audit).
        if (!ordinaryChanged) {
            return afterTransfer;
        }

        // 8. Ordinary update with sanitized command (ownerUserId=null).
        UpdateContactCommand sanitizedCommand = new UpdateContactCommand(
                command.accountId(),
                command.givenName(),
                command.familyName(),
                command.primaryEmail(),
                command.primaryPhone(),
                command.preferredLocale(),
                command.timeZone(),
                null, // ownerUserId must be null — repo.update rejects non-null
                command.consentSummary());

        long ordinaryExpectedVersion = ownerChanged
                ? afterTransfer.version()
                : current.version();

        // For audit, use the post-transfer state as the "before" so the
        // generic UPDATE audit reflects only ordinary field changes, not
        // the owner change (C8 will own dedicated transfer audit).
        ContactRecord auditBefore = ownerChanged ? afterTransfer : current;
        ContactRecord after = repo.update(
                tenantId, actorId, contactId, sanitizedCommand, ordinaryExpectedVersion);

        // 9. Generic ordinary audit/timeline (once only).
        Instant now = Instant.now();
        audit.record(tenantId, actorId, "UPDATE", "CONTACT", contactId,
                new AuditChange(json(auditBefore), json(after)), now);
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
