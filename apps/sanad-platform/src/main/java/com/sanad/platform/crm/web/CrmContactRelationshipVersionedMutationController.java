package com.sanad.platform.crm.web;

import com.sanad.platform.crm.concurrency.ETagService;
import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.pagination.CrmEnvelopes.SingleResponse;
import com.sanad.platform.crm.party.application.ContactRelationshipUseCases;
import com.sanad.platform.crm.party.domain.ContactRelationshipRepository.ContactProfileRecord;
import com.sanad.platform.crm.party.domain.ContactRelationshipRepository.RelationshipRecord;
import com.sanad.platform.crm.party.domain.ContactRelationshipRepository.UpdateContactProfileCommand;
import com.sanad.platform.crm.party.domain.ContactRelationshipRepository.UpdateRelationshipCommand;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Expected-version mutation surface for browser clients that cannot reliably
 * read response ETag headers through an intermediary. The canonical ETag/
 * If-Match operations remain available on {@link CrmContactRelationshipController}.
 */
@RestController
@RequestMapping("/api/v2/crm")
public class CrmContactRelationshipVersionedMutationController {

    private final ContactRelationshipUseCases useCases;
    private final ETagService etags;

    public CrmContactRelationshipVersionedMutationController(
            ContactRelationshipUseCases useCases,
            ETagService etags) {
        this.useCases = useCases;
        this.etags = etags;
    }

    @RequireCapability("CRM.CONTACT.WRITE")
    @PatchMapping("/contacts/{contactId}/profile-versioned")
    public ResponseEntity<SingleResponse<CrmContactRelationshipController.ContactProfileResponse>> updateProfile(
            Authentication authentication,
            @PathVariable UUID contactId,
            @Valid @RequestBody VersionedContactProfileRequest body,
            HttpServletRequest request) {
        UUID tenantId = tenantId(authentication);
        ContactProfileRecord current = useCases.profile(tenantId, contactId);
        assertExpectedVersion(current.version(), body.expectedVersion());
        ContactProfileRecord updated = useCases.updateProfile(
                tenantId,
                userId(authentication),
                contactId,
                new UpdateContactProfileCommand(
                        body.legalName(), body.preferredName(), body.givenName(), body.middleName(),
                        body.familyName(), body.primaryEmail(), body.primaryPhone(),
                        body.preferredLocale(), body.timeZone(), body.pronouns(),
                        body.ownerUserId(), body.source(), body.ownerChangeReason()),
                body.expectedVersion());
        var response = profileResponse(updated);
        return ResponseEntity.ok()
                .eTag(etags.etag("contact", contactId, updated.version()))
                .body(SingleResponse.of(response, requestId(request)));
    }

    @RequireCapability("CRM.RELATIONSHIP.WRITE")
    @PatchMapping("/contact-relationships/{relationshipId}/versioned")
    public ResponseEntity<SingleResponse<CrmContactRelationshipController.ContactRelationshipResponse>> updateRelationship(
            Authentication authentication,
            @PathVariable UUID relationshipId,
            @Valid @RequestBody VersionedRelationshipRequest body,
            HttpServletRequest request) {
        UUID tenantId = tenantId(authentication);
        RelationshipRecord current = useCases.relationship(tenantId, relationshipId);
        assertExpectedVersion(current.version(), body.expectedVersion());
        RelationshipRecord updated = useCases.updateRelationship(
                tenantId,
                userId(authentication),
                relationshipId,
                new UpdateRelationshipCommand(
                        body.roleCode(), body.customRoleId(), body.validFrom(), body.validTo(),
                        body.jobTitle(), body.department(), body.decisionAuthority(), body.ownerUserId()),
                body.expectedVersion());
        return relationshipResponse(updated, request);
    }

    @RequireCapability("CRM.RELATIONSHIP.WRITE")
    @PostMapping("/contact-relationships/{relationshipId}/commands")
    public ResponseEntity<SingleResponse<CrmContactRelationshipController.ContactRelationshipResponse>> command(
            Authentication authentication,
            @PathVariable UUID relationshipId,
            @Valid @RequestBody RelationshipCommandRequest body,
            HttpServletRequest request) {
        UUID tenantId = tenantId(authentication);
        RelationshipRecord current = useCases.relationship(tenantId, relationshipId);
        assertExpectedVersion(current.version(), body.expectedVersion());
        String action = body.action() == null ? "" : body.action().trim().toUpperCase(Locale.ROOT);
        RelationshipRecord updated = switch (action) {
            case "SET_PRIMARY" -> useCases.setPrimary(
                    tenantId, userId(authentication), relationshipId, body.expectedVersion());
            case "ACTIVATE" -> useCases.activate(
                    tenantId, userId(authentication), relationshipId, body.expectedVersion());
            case "DEACTIVATE" -> useCases.deactivate(
                    tenantId, userId(authentication), relationshipId, body.expectedVersion());
            case "ARCHIVE" -> useCases.archive(
                    tenantId, userId(authentication), relationshipId, body.expectedVersion());
            case "REACTIVATE" -> useCases.reactivate(
                    tenantId, userId(authentication), relationshipId, body.expectedVersion());
            default -> throw new CrmContractException(
                    CrmErrorCode.VALIDATION_ERROR,
                    "Unsupported relationship command.");
        };
        return relationshipResponse(updated, request);
    }

    private ResponseEntity<SingleResponse<CrmContactRelationshipController.ContactRelationshipResponse>> relationshipResponse(
            RelationshipRecord record,
            HttpServletRequest request) {
        var response = new CrmContactRelationshipController.ContactRelationshipResponse(
                record.id(), record.version(), record.contactId(), record.accountId(),
                record.contactDisplayName(), record.accountDisplayName(), record.roleCode(),
                record.customRoleId(), record.customRoleNameAr(), record.customRoleNameEn(),
                record.status(), record.primaryRelationship(), record.validFrom(), record.validTo(),
                record.jobTitle(), record.department(), record.decisionAuthority(), record.ownerUserId(),
                record.createdAt(), record.updatedAt());
        return ResponseEntity.ok()
                .eTag(etags.etag("contact-relationship", record.id(), record.version()))
                .body(SingleResponse.of(response, requestId(request)));
    }

    private static CrmContactRelationshipController.ContactProfileResponse profileResponse(
            ContactProfileRecord record) {
        return new CrmContactRelationshipController.ContactProfileResponse(
                record.id(), record.version(), record.legalName(), record.preferredName(),
                record.givenName(), record.middleName(), record.familyName(), record.displayName(),
                record.primaryEmail(), record.primaryPhone(), record.preferredLocale(), record.timeZone(),
                record.pronouns(), record.lifecycleStatus(), record.ownerUserId(), record.source(),
                record.createdAt(), record.updatedAt());
    }

    private static void assertExpectedVersion(long actual, long expected) {
        if (actual != expected) {
            throw new CrmContractException(CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
        }
    }

    private static UUID requestId(HttpServletRequest request) {
        String value = request == null ? null : request.getHeader("X-Request-ID");
        if (value != null && !value.isBlank()) {
            try {
                return UUID.fromString(value);
            } catch (IllegalArgumentException ignored) {
                // Use a server-generated correlation identifier below.
            }
        }
        return UUID.randomUUID();
    }

    private static UUID tenantId(Authentication authentication) {
        return contextId(authentication, "tenant_id");
    }

    private static UUID userId(Authentication authentication) {
        return contextId(authentication, "user_id");
    }

    private static UUID contextId(Authentication authentication, String key) {
        if (authentication == null || authentication.getDetails() == null) return null;
        Object details = authentication.getDetails();
        if (details instanceof Map<?, ?> map && map.get(key) != null) {
            try {
                return UUID.fromString(map.get(key).toString());
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    public record VersionedContactProfileRequest(
            long expectedVersion,
            String legalName,
            String preferredName,
            String givenName,
            String middleName,
            String familyName,
            String primaryEmail,
            String primaryPhone,
            String preferredLocale,
            String timeZone,
            String pronouns,
            UUID ownerUserId,
            String source,
            String ownerChangeReason) {}

    public record VersionedRelationshipRequest(
            long expectedVersion,
            String roleCode,
            UUID customRoleId,
            java.time.LocalDate validFrom,
            java.time.LocalDate validTo,
            String jobTitle,
            String department,
            String decisionAuthority,
            UUID ownerUserId) {}

    public record RelationshipCommandRequest(long expectedVersion, String action) {}
}
