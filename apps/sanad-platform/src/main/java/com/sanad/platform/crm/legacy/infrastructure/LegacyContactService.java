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

    public LegacyContactService(LegacySupport support) {
        this.support = support;
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
        Map<String, Object> existing =
                support.one("crm_contacts", tenantId, contactId, "CRM contact not found");
        if ("ARCHIVED".equals(existing.get("lifecycle_status"))) {
            throw conflict("Archived CRM contact cannot be updated");
        }
        if (request.accountId() != null) {
            support.one("crm_accounts", tenantId, request.accountId(), "CRM account not found");
        }
        support.validateOwner(tenantId, request.ownerUserId());
        String given = optional(request.givenName(), 120, "givenName");
        String family = optional(request.familyName(), 120, "familyName");
        String displayName = null;
        if (given != null || family != null) {
            String actualGiven = given == null ? String.valueOf(existing.get("given_name")) : given;
            Object currentFamily = existing.get("family_name");
            String actualFamily =
                    family == null && currentFamily != null ? currentFamily.toString() : family;
            displayName = actualFamily == null || actualFamily.isBlank()
                    ? actualGiven : actualGiven + " " + actualFamily;
        }
        Instant now = Instant.now();
        support.jdbc.update(
                "UPDATE crm_contacts SET account_id=COALESCE(:accountId,account_id)," +
                        "given_name=COALESCE(:givenName,given_name),family_name=COALESCE(:familyName,family_name)," +
                        "display_name=COALESCE(:displayName,display_name),normalized_name=COALESCE(:normalizedName,normalized_name)," +
                        "primary_email=COALESCE(:email,primary_email),normalized_email=COALESCE(:normalizedEmail,normalized_email)," +
                        "primary_phone=COALESCE(:phone,primary_phone),preferred_locale=COALESCE(:locale,preferred_locale)," +
                        "time_zone=COALESCE(:timeZone,time_zone),owner_user_id=COALESCE(:ownerUserId,owner_user_id)," +
                        "consent_summary=COALESCE(:consent,consent_summary),updated_by=:actorId,updated_at=:now,version=version+1 " +
                        "WHERE tenant_id=:tenantId AND id=:id",
                p().addValue("tenantId", tenantId).addValue("id", contactId)
                        .addValue("accountId", request.accountId()).addValue("givenName", given)
                        .addValue("familyName", family).addValue("displayName", displayName)
                        .addValue("normalizedName", displayName == null ? null : normalize(displayName))
                        .addValue("email", optional(request.primaryEmail(), 255, "primaryEmail"))
                        .addValue("normalizedEmail", normalizeEmail(request.primaryEmail()))
                        .addValue("phone", optional(request.primaryPhone(), 64, "primaryPhone"))
                        .addValue("locale", optional(request.preferredLocale(), 35, "preferredLocale"))
                        .addValue("timeZone", optional(request.timeZone(), 64, "timeZone"))
                        .addValue("ownerUserId", request.ownerUserId())
                        .addValue("consent", upper(request.consentSummary()))
                        .addValue("actorId", actorId).addValue("now", Timestamp.from(now)));
        support.timeline(tenantId, "CONTACT", contactId, "crm.contact.updated",
                "Contact updated", "CRM_CONTACT", contactId, actorId, now);
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
