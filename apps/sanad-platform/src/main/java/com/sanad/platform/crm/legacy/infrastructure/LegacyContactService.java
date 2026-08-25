package com.sanad.platform.crm.legacy.infrastructure;

import com.sanad.platform.crm.web.UpdateContactRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static com.sanad.platform.crm.legacy.infrastructure.LegacySupport.*;

@Service
public class LegacyContactService {

    private final LegacySupport support;
    private final com.sanad.platform.crm.party.application.ContactUseCases contactUseCases;

    public LegacyContactService(LegacySupport support,
                                com.sanad.platform.crm.party.application.ContactUseCases contactUseCases) {
        this.support = support;
        this.contactUseCases = contactUseCases;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getContact(Authentication authentication, UUID contactId) {
        UUID tenantId = support.tenantId(authentication);
        LinkedHashMap<String, Object> result =
                new LinkedHashMap<>(support.one("crm_contacts", tenantId, contactId, "CRM contact not found"));
        return result;
    }

    @Transactional
    public Map<String, Object> updateContact(
            Authentication authentication, UUID contactId, UpdateContactRequest request) {
        UUID tenantId = support.tenantId(authentication);
        UUID actorId = support.userId(authentication);
        // C6-B: delegate to canonical A1 adapter — no direct Contact business UPDATE SQL.
        var current = contactUseCases.getById(tenantId, contactId);
        // C6-B-R1: restore account existence validation (compatibility regression fix).
        if (request.accountId() != null) {
            support.one("crm_accounts", tenantId, request.accountId(), "CRM account not found");
        }
        var command = new com.sanad.platform.crm.party.domain.ContactRepository.UpdateContactCommand(
                request.accountId(),
                optional(request.givenName(), 120, "givenName"),
                optional(request.familyName(), 120, "familyName"),
                optional(request.primaryEmail(), 255, "primaryEmail"),
                optional(request.primaryPhone(), 64, "primaryPhone"),
                optional(request.preferredLocale(), 35, "preferredLocale"),
                optional(request.timeZone(), 64, "timeZone"),
                request.ownerUserId(),
                upper(request.consentSummary()));
        contactUseCases.update(tenantId, actorId, contactId, command, current.version());
        return getContact(authentication, contactId);
    }

    @Transactional
    public Map<String, Object> archiveContact(Authentication authentication, UUID contactId) {
        return changeContactArchive(authentication, contactId, true);
    }

    @Transactional
    public Map<String, Object> restoreContact(Authentication authentication, UUID contactId) {
        return changeContactArchive(authentication, contactId, false);
    }

    private Map<String, Object> changeContactArchive(
            Authentication authentication, UUID contactId, boolean archive) {
        UUID tenantId = support.tenantId(authentication);
        UUID actorId = support.userId(authentication);
        Instant now = Instant.now();
        String expected = archive ? "ACTIVE" : "ARCHIVED";
        String next = archive ? "ARCHIVED" : "ACTIVE";
        int changed = support.jdbc.update(
                "UPDATE crm_contacts SET lifecycle_status=:nextStatus,archived_at=:archivedAt," +
                        "updated_by=:actorId,updated_at=:now,version=version+1 " +
                        "WHERE tenant_id=:tenantId AND id=:id AND lifecycle_status=:expectedStatus",
                p().addValue("tenantId", tenantId).addValue("id", contactId)
                        .addValue("nextStatus", next).addValue("expectedStatus", expected)
                        .addValue("archivedAt", archive ? Timestamp.from(now) : null)
                        .addValue("actorId", actorId).addValue("now", Timestamp.from(now)));
        if (changed != 1) throw conflict("CRM contact lifecycle transition is not allowed");
        support.timeline(tenantId, "CONTACT", contactId,
                archive ? "crm.contact.archived" : "crm.contact.restored",
                archive ? "Contact archived" : "Contact restored",
                "CRM_CONTACT", contactId, actorId, now);
        return support.one("crm_contacts", tenantId, contactId, "CRM contact not found");
    }
}
