package com.sanad.platform.crm.web;

import com.sanad.platform.crm.concurrency.ETagService;
import com.sanad.platform.crm.pagination.CrmEnvelopes;
import com.sanad.platform.crm.pagination.CrmEnvelopes.ListResponse;
import com.sanad.platform.crm.pagination.CrmEnvelopes.SingleResponse;
import com.sanad.platform.crm.pagination.CursorCodec;
import com.sanad.platform.crm.party.application.ContactRelationshipUseCases;
import com.sanad.platform.crm.party.domain.ContactRelationshipRepository.ContactProfileRecord;
import com.sanad.platform.crm.party.domain.ContactRelationshipRepository.CreateRelationshipCommand;
import com.sanad.platform.crm.party.domain.ContactRelationshipRepository.CreateRelationshipRoleCommand;
import com.sanad.platform.crm.party.domain.ContactRelationshipRepository.OwnershipHistoryRecord;
import com.sanad.platform.crm.party.domain.ContactRelationshipRepository.RelationshipHistoryRecord;
import com.sanad.platform.crm.party.domain.ContactRelationshipRepository.RelationshipRecord;
import com.sanad.platform.crm.party.domain.ContactRelationshipRepository.RelationshipRoleRecord;
import com.sanad.platform.crm.party.domain.ContactRelationshipRepository.UpdateContactProfileCommand;
import com.sanad.platform.crm.party.domain.ContactRelationshipRepository.UpdateRelationshipCommand;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Typed CRM-006 API for person profiles and multi-account relationships. */
@RestController
@RequestMapping("/api/v2/crm")
public class CrmContactRelationshipController {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final ContactRelationshipUseCases useCases;
    private final ETagService etags;
    private final CursorCodec cursors;

    public CrmContactRelationshipController(
            ContactRelationshipUseCases useCases,
            ETagService etags,
            CursorCodec cursors) {
        this.useCases = useCases;
        this.etags = etags;
        this.cursors = cursors;
    }

    @RequireCapability("CRM.CONTACT.SENSITIVE.READ")
    @GetMapping("/contacts/{contactId}/profile")
    public ResponseEntity<SingleResponse<ContactProfileResponse>> profile(
            Authentication authentication,
            @PathVariable UUID contactId,
            HttpServletRequest request) {
        ContactProfileResponse response = profileResponse(
                useCases.profile(tenantId(authentication), contactId));
        return single(response, "contact", contactId, response.version(), request);
    }

    @RequireCapability("CRM.CONTACT.WRITE")
    @PatchMapping("/contacts/{contactId}/profile")
    public ResponseEntity<SingleResponse<ContactProfileResponse>> updateProfile(
            Authentication authentication,
            @PathVariable UUID contactId,
            @Valid @RequestBody UpdateContactProfileRequest body,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            HttpServletRequest request) {
        ContactProfileRecord current = useCases.profile(tenantId(authentication), contactId);
        etags.validateIfMatch(ifMatch, "contact", contactId, current.version());
        ContactProfileRecord updated = useCases.updateProfile(
                tenantId(authentication), userId(authentication), contactId,
                new UpdateContactProfileCommand(
                        body.legalName(), body.preferredName(), body.givenName(), body.middleName(),
                        body.familyName(), body.primaryEmail(), body.primaryPhone(),
                        body.preferredLocale(), body.timeZone(), body.pronouns(),
                        body.ownerUserId(), body.source(), body.ownerChangeReason()),
                current.version());
        ContactProfileResponse response = profileResponse(updated);
        return single(response, "contact", contactId, response.version(), request);
    }

    @RequireCapability("CRM.RELATIONSHIP.READ")
    @GetMapping("/contact-relationships/{relationshipId}")
    public ResponseEntity<SingleResponse<ContactRelationshipResponse>> relationship(
            Authentication authentication,
            @PathVariable UUID relationshipId,
            HttpServletRequest request) {
        ContactRelationshipResponse response = relationshipResponse(
                useCases.relationship(tenantId(authentication), relationshipId));
        return single(response, "contact-relationship", relationshipId, response.version(), request);
    }

    @RequireCapability("CRM.RELATIONSHIP.READ")
    @GetMapping("/contacts/{contactId}/relationships")
    public ListResponse<ContactRelationshipResponse> relationshipsByContact(
            Authentication authentication,
            @PathVariable UUID contactId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor,
            HttpServletRequest request) {
        return relationshipPage(authentication, limit, cursor, request,
                (tenant, fetchLimit, beforeTime, beforeId) ->
                        useCases.relationshipsByContact(tenant, contactId, fetchLimit, beforeTime, beforeId));
    }

    @RequireCapability("CRM.RELATIONSHIP.READ")
    @GetMapping("/accounts/{accountId}/contact-relationships")
    public ListResponse<ContactRelationshipResponse> relationshipsByAccount(
            Authentication authentication,
            @PathVariable UUID accountId,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor,
            HttpServletRequest request) {
        return relationshipPage(authentication, limit, cursor, request,
                (tenant, fetchLimit, beforeTime, beforeId) ->
                        useCases.relationshipsByAccount(tenant, accountId, fetchLimit, beforeTime, beforeId));
    }

    @RequireCapability("CRM.RELATIONSHIP.WRITE")
    @PostMapping("/contacts/{contactId}/relationships")
    public ResponseEntity<SingleResponse<ContactRelationshipResponse>> createRelationship(
            Authentication authentication,
            @PathVariable UUID contactId,
            @Valid @RequestBody CreateContactRelationshipRequest body,
            HttpServletRequest request) {
        RelationshipRecord created = useCases.createRelationship(
                tenantId(authentication), userId(authentication), contactId,
                new CreateRelationshipCommand(
                        body.accountId(), body.roleCode(), body.customRoleId(), body.primaryRelationship(),
                        body.validFrom(), body.validTo(), body.jobTitle(), body.department(),
                        body.decisionAuthority(), body.ownerUserId()));
        ContactRelationshipResponse response = relationshipResponse(created);
        return ResponseEntity.status(201)
                .eTag(etags.etag("contact-relationship", response.id(), response.version()))
                .body(SingleResponse.of(response, requestId(request)));
    }

    @RequireCapability("CRM.RELATIONSHIP.WRITE")
    @PatchMapping("/contact-relationships/{relationshipId}")
    public ResponseEntity<SingleResponse<ContactRelationshipResponse>> updateRelationship(
            Authentication authentication,
            @PathVariable UUID relationshipId,
            @Valid @RequestBody UpdateContactRelationshipRequest body,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            HttpServletRequest request) {
        RelationshipRecord current = useCases.relationship(tenantId(authentication), relationshipId);
        etags.validateIfMatch(ifMatch, "contact-relationship", relationshipId, current.version());
        RelationshipRecord updated = useCases.updateRelationship(
                tenantId(authentication), userId(authentication), relationshipId,
                new UpdateRelationshipCommand(
                        body.roleCode(), body.customRoleId(), body.validFrom(), body.validTo(),
                        body.jobTitle(), body.department(), body.decisionAuthority(), body.ownerUserId()),
                current.version());
        ContactRelationshipResponse response = relationshipResponse(updated);
        return single(response, "contact-relationship", relationshipId, response.version(), request);
    }

    @RequireCapability("CRM.RELATIONSHIP.ADMIN")
    @PatchMapping("/contact-relationships/{relationshipId}/primary")
    public ResponseEntity<SingleResponse<ContactRelationshipResponse>> setPrimary(
            Authentication authentication,
            @PathVariable UUID relationshipId,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            HttpServletRequest request) {
        return lifecycle(authentication, relationshipId, ifMatch, request, LifecycleOperation.PRIMARY);
    }

    @RequireCapability("CRM.RELATIONSHIP.WRITE")
    @PatchMapping("/contact-relationships/{relationshipId}/activate")
    public ResponseEntity<SingleResponse<ContactRelationshipResponse>> activate(
            Authentication authentication,
            @PathVariable UUID relationshipId,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            HttpServletRequest request) {
        return lifecycle(authentication, relationshipId, ifMatch, request, LifecycleOperation.ACTIVATE);
    }

    @RequireCapability("CRM.RELATIONSHIP.WRITE")
    @PatchMapping("/contact-relationships/{relationshipId}/deactivate")
    public ResponseEntity<SingleResponse<ContactRelationshipResponse>> deactivate(
            Authentication authentication,
            @PathVariable UUID relationshipId,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            HttpServletRequest request) {
        return lifecycle(authentication, relationshipId, ifMatch, request, LifecycleOperation.DEACTIVATE);
    }

    @RequireCapability("CRM.RELATIONSHIP.ADMIN")
    @PatchMapping("/contact-relationships/{relationshipId}/archive")
    public ResponseEntity<SingleResponse<ContactRelationshipResponse>> archive(
            Authentication authentication,
            @PathVariable UUID relationshipId,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            HttpServletRequest request) {
        return lifecycle(authentication, relationshipId, ifMatch, request, LifecycleOperation.ARCHIVE);
    }

    @RequireCapability("CRM.RELATIONSHIP.ADMIN")
    @PatchMapping("/contact-relationships/{relationshipId}/reactivate")
    public ResponseEntity<SingleResponse<ContactRelationshipResponse>> reactivate(
            Authentication authentication,
            @PathVariable UUID relationshipId,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            HttpServletRequest request) {
        return lifecycle(authentication, relationshipId, ifMatch, request, LifecycleOperation.REACTIVATE);
    }

    @RequireCapability("CRM.RELATIONSHIP.READ")
    @GetMapping("/contact-relationships/{relationshipId}/history")
    public ListResponse<RelationshipHistoryResponse> relationshipHistory(
            Authentication authentication,
            @PathVariable UUID relationshipId,
            @RequestParam(required = false) Integer limit,
            HttpServletRequest request) {
        List<RelationshipHistoryResponse> data = useCases.relationshipHistory(
                        tenantId(authentication), relationshipId, pageLimit(limit))
                .stream().map(CrmContactRelationshipController::historyResponse).toList();
        return ListResponse.of(data, CrmEnvelopes.Page.empty(pageLimit(limit)), requestId(request));
    }

    @RequireCapability("CRM.CONTACT.SENSITIVE.READ")
    @GetMapping("/contacts/{contactId}/ownership-history")
    public ListResponse<OwnershipHistoryResponse> ownershipHistory(
            Authentication authentication,
            @PathVariable UUID contactId,
            @RequestParam(required = false) Integer limit,
            HttpServletRequest request) {
        List<OwnershipHistoryResponse> data = useCases.ownershipHistory(
                        tenantId(authentication), contactId, pageLimit(limit))
                .stream().map(CrmContactRelationshipController::ownershipResponse).toList();
        return ListResponse.of(data, CrmEnvelopes.Page.empty(pageLimit(limit)), requestId(request));
    }

    @RequireCapability("CRM.RELATIONSHIP.READ")
    @GetMapping("/relationship-roles")
    public ListResponse<RelationshipRoleResponse> roles(
            Authentication authentication,
            @RequestParam(defaultValue = "false") boolean includeInactive,
            HttpServletRequest request) {
        List<RelationshipRoleResponse> roles = new ArrayList<>(standardRoleResponses());
        roles.addAll(useCases.customRoles(tenantId(authentication), includeInactive)
                .stream().map(CrmContactRelationshipController::roleResponse).toList());
        return ListResponse.of(roles, CrmEnvelopes.Page.empty(roles.size()), requestId(request));
    }

    @RequireCapability("CRM.RELATIONSHIP.ADMIN")
    @PostMapping("/relationship-roles")
    public ResponseEntity<SingleResponse<RelationshipRoleResponse>> createRole(
            Authentication authentication,
            @Valid @RequestBody CreateRelationshipRoleRequest body,
            HttpServletRequest request) {
        RelationshipRoleRecord created = useCases.createRole(
                tenantId(authentication), userId(authentication),
                new CreateRelationshipRoleCommand(body.code(), body.nameAr(), body.nameEn()));
        RelationshipRoleResponse response = roleResponse(created);
        return ResponseEntity.status(201)
                .eTag(etags.etag("contact-relationship-role", created.id(), created.version()))
                .body(SingleResponse.of(response, requestId(request)));
    }

    private ResponseEntity<SingleResponse<ContactRelationshipResponse>> lifecycle(
            Authentication authentication,
            UUID relationshipId,
            String ifMatch,
            HttpServletRequest request,
            LifecycleOperation operation) {
        UUID tenantId = tenantId(authentication);
        RelationshipRecord current = useCases.relationship(tenantId, relationshipId);
        etags.validateIfMatch(ifMatch, "contact-relationship", relationshipId, current.version());
        RelationshipRecord updated = switch (operation) {
            case PRIMARY -> useCases.setPrimary(tenantId, userId(authentication), relationshipId, current.version());
            case ACTIVATE -> useCases.activate(tenantId, userId(authentication), relationshipId, current.version());
            case DEACTIVATE -> useCases.deactivate(tenantId, userId(authentication), relationshipId, current.version());
            case ARCHIVE -> useCases.archive(tenantId, userId(authentication), relationshipId, current.version());
            case REACTIVATE -> useCases.reactivate(tenantId, userId(authentication), relationshipId, current.version());
        };
        ContactRelationshipResponse response = relationshipResponse(updated);
        return single(response, "contact-relationship", relationshipId, response.version(), request);
    }

    private ListResponse<ContactRelationshipResponse> relationshipPage(
            Authentication authentication,
            Integer requestedLimit,
            String cursor,
            HttpServletRequest request,
            RelationshipFetcher fetcher) {
        UUID tenantId = tenantId(authentication);
        int limit = pageLimit(requestedLimit);
        Instant beforeTime = null;
        UUID beforeId = null;
        if (cursor != null && !cursor.isBlank()) {
            CursorCodec.DecodedCursor decoded = cursors.decode(cursor, tenantId, "updatedAt", "desc");
            beforeTime = decoded.sortValue() == null ? null : Instant.parse(decoded.sortValue());
            beforeId = decoded.tieBreakerId();
        }
        List<RelationshipRecord> rows = fetcher.fetch(tenantId, limit + 1, beforeTime, beforeId);
        boolean hasMore = rows.size() > limit;
        List<RelationshipRecord> pageRows = hasMore ? rows.subList(0, limit) : rows;
        List<ContactRelationshipResponse> data = pageRows.stream()
                .map(CrmContactRelationshipController::relationshipResponse).toList();
        String nextCursor = null;
        if (hasMore && !pageRows.isEmpty()) {
            RelationshipRecord last = pageRows.get(pageRows.size() - 1);
            nextCursor = cursors.encode(tenantId, "updatedAt", "desc",
                    last.updatedAt() == null ? null : last.updatedAt().toString(), last.id());
        }
        return ListResponse.of(data, CrmEnvelopes.Page.of(nextCursor, hasMore, limit), requestId(request));
    }

    private <T> ResponseEntity<SingleResponse<T>> single(
            T body, String entityType, UUID id, long version, HttpServletRequest request) {
        return ResponseEntity.ok()
                .eTag(etags.etag(entityType, id, version))
                .body(SingleResponse.of(body, requestId(request)));
    }

    private static ContactProfileResponse profileResponse(ContactProfileRecord record) {
        return new ContactProfileResponse(
                record.id(), record.version(), record.legalName(), record.preferredName(),
                record.givenName(), record.middleName(), record.familyName(), record.displayName(),
                record.primaryEmail(), record.primaryPhone(), record.preferredLocale(), record.timeZone(),
                record.pronouns(), record.lifecycleStatus(), record.ownerUserId(), record.source(),
                record.createdAt(), record.updatedAt());
    }

    private static ContactRelationshipResponse relationshipResponse(RelationshipRecord record) {
        return new ContactRelationshipResponse(
                record.id(), record.version(), record.contactId(), record.accountId(),
                record.contactDisplayName(), record.accountDisplayName(), record.roleCode(),
                record.customRoleId(), record.customRoleNameAr(), record.customRoleNameEn(),
                record.status(), record.primaryRelationship(), record.validFrom(), record.validTo(),
                record.jobTitle(), record.department(), record.decisionAuthority(), record.ownerUserId(),
                record.createdAt(), record.updatedAt());
    }

    private static RelationshipHistoryResponse historyResponse(RelationshipHistoryRecord record) {
        return new RelationshipHistoryResponse(
                record.id(), record.relationshipId(), record.contactId(), record.accountId(),
                record.eventType(), record.previousVersion(), record.newVersion(), record.snapshot(),
                record.changedBy(), record.changedAt());
    }

    private static OwnershipHistoryResponse ownershipResponse(OwnershipHistoryRecord record) {
        return new OwnershipHistoryResponse(
                record.id(), record.contactId(), record.previousOwnerUserId(), record.newOwnerUserId(),
                record.changedBy(), record.changedAt(), record.reason());
    }

    private static RelationshipRoleResponse roleResponse(RelationshipRoleRecord record) {
        return new RelationshipRoleResponse(
                record.id(), record.version(), record.code(), record.nameAr(), record.nameEn(),
                false, record.active(), record.createdAt(), record.updatedAt());
    }

    private static List<RelationshipRoleResponse> standardRoleResponses() {
        return List.of(
                standardRole("DECISION_MAKER", "صانع قرار", "Decision maker"),
                standardRole("BILLING", "الفوترة", "Billing"),
                standardRole("TECHNICAL", "تقني", "Technical"),
                standardRole("INFLUENCER", "مؤثر", "Influencer"),
                standardRole("EMPLOYEE", "موظف", "Employee"),
                standardRole("PARTNER", "شريك", "Partner"));
    }

    private static RelationshipRoleResponse standardRole(String code, String nameAr, String nameEn) {
        return new RelationshipRoleResponse(null, 0, code, nameAr, nameEn, true, true, null, null);
    }

    private static int pageLimit(Integer value) {
        if (value == null || value <= 0) return DEFAULT_LIMIT;
        return Math.min(value, MAX_LIMIT);
    }

    private static UUID requestId(HttpServletRequest request) {
        if (request != null) {
            String value = request.getHeader("X-Request-ID");
            if (value != null && !value.isBlank()) {
                try { return UUID.fromString(value); }
                catch (IllegalArgumentException ignored) { }
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
            try { return UUID.fromString(map.get(key).toString()); }
            catch (IllegalArgumentException ignored) { return null; }
        }
        return null;
    }

    private enum LifecycleOperation { PRIMARY, ACTIVATE, DEACTIVATE, ARCHIVE, REACTIVATE }

    @FunctionalInterface
    private interface RelationshipFetcher {
        List<RelationshipRecord> fetch(UUID tenantId, int limit, Instant beforeUpdatedAt, UUID beforeId);
    }

    public record ContactProfileResponse(
            UUID id,
            long version,
            String legalName,
            String preferredName,
            String givenName,
            String middleName,
            String familyName,
            String displayName,
            String primaryEmail,
            String primaryPhone,
            String preferredLocale,
            String timeZone,
            String pronouns,
            String lifecycleStatus,
            UUID ownerUserId,
            String source,
            Instant createdAt,
            Instant updatedAt) {}

    public record UpdateContactProfileRequest(
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

    public record ContactRelationshipResponse(
            UUID id,
            long version,
            UUID contactId,
            UUID accountId,
            String contactDisplayName,
            String accountDisplayName,
            String roleCode,
            UUID customRoleId,
            String customRoleNameAr,
            String customRoleNameEn,
            String status,
            boolean primaryRelationship,
            LocalDate validFrom,
            LocalDate validTo,
            String jobTitle,
            String department,
            String decisionAuthority,
            UUID ownerUserId,
            Instant createdAt,
            Instant updatedAt) {}

    public record CreateContactRelationshipRequest(
            UUID accountId,
            String roleCode,
            UUID customRoleId,
            boolean primaryRelationship,
            LocalDate validFrom,
            LocalDate validTo,
            String jobTitle,
            String department,
            String decisionAuthority,
            UUID ownerUserId) {}

    public record UpdateContactRelationshipRequest(
            String roleCode,
            UUID customRoleId,
            LocalDate validFrom,
            LocalDate validTo,
            String jobTitle,
            String department,
            String decisionAuthority,
            UUID ownerUserId) {}

    public record RelationshipHistoryResponse(
            UUID id,
            UUID relationshipId,
            UUID contactId,
            UUID accountId,
            String eventType,
            Long previousVersion,
            long newVersion,
            String snapshot,
            UUID changedBy,
            Instant changedAt) {}

    public record OwnershipHistoryResponse(
            UUID id,
            UUID contactId,
            UUID previousOwnerUserId,
            UUID newOwnerUserId,
            UUID changedBy,
            Instant changedAt,
            String reason) {}

    public record RelationshipRoleResponse(
            UUID id,
            long version,
            String code,
            String nameAr,
            String nameEn,
            boolean standard,
            boolean active,
            Instant createdAt,
            Instant updatedAt) {}

    public record CreateRelationshipRoleRequest(String code, String nameAr, String nameEn) {}
}
