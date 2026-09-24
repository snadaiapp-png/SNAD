package com.sanad.platform.crm.tag.web;

import com.sanad.platform.crm.concurrency.ETagService;
import com.sanad.platform.crm.dto.CrmDtos.TagAssignmentResponse;
import com.sanad.platform.crm.dto.CrmDtos.TagResponse;
import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.mapper.CrmDtoMapper;
import com.sanad.platform.crm.pagination.CrmEnvelopes;
import com.sanad.platform.crm.pagination.CrmEnvelopes.ListResponse;
import com.sanad.platform.crm.pagination.CrmEnvelopes.SingleResponse;
import com.sanad.platform.crm.pagination.PageRequest;
import com.sanad.platform.crm.tag.application.TagUseCases;
import com.sanad.platform.crm.tag.domain.TagRepository.CreateTagCommand;
import com.sanad.platform.crm.tag.domain.TagRepository.TagAssignmentRecord;
import com.sanad.platform.crm.tag.domain.TagRepository.TagRecord;
import com.sanad.platform.crm.tag.domain.TagRepository.UpdateTagCommand;
import com.sanad.platform.crm.web.CrmIdempotencyHttpSupport;
import com.sanad.platform.crm.web.CrmUpdateDtos.AssignTagRequest;
import com.sanad.platform.crm.web.CrmUpdateDtos.CreateTagRequest;
import com.sanad.platform.crm.web.CrmUpdateDtos.UpdateTagRequest;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * V2 REST controller for CRM Tags.
 * <p>
 * Mounted under {@code /api/v2/crm/tags}. Full V2 contract:
 *   - Typed DTOs (camelCase) via {@link TagResponse} / {@link TagAssignmentResponse}
 *   - ETag concurrency on PATCH via {@link ETagService}
 *   - Idempotency on POST via {@link CrmIdempotencyHttpSupport}
 *   - Response envelopes: {@link SingleResponse} / {@link ListResponse}
 *   - RBAC via {@code @RequireCapability}
 *   - V1 Deprecation headers
 * <p>
 * Branch: feature/crm-tags-v2
 */
@RestController
@RequestMapping("/api/v2/crm/tags")
public class CrmTagControllerV2 {

    private final TagUseCases tags;
    private final CrmDtoMapper mapper;
    private final ETagService etags;
    private final CrmIdempotencyHttpSupport idempotency;

    public CrmTagControllerV2(
            TagUseCases tags,
            CrmDtoMapper mapper,
            ETagService etags,
            CrmIdempotencyHttpSupport idempotency) {
        this.tags = tags;
        this.mapper = mapper;
        this.etags = etags;
        this.idempotency = idempotency;
    }

    // ────────────────────────────────────────────────────────────────────
    // Tag definitions — GET (list + single)
    // ────────────────────────────────────────────────────────────────────

    @RequireCapability("CRM.TAG.READ")
    @GetMapping
    public ListResponse<TagResponse> listTags(
            Authentication auth,
            @RequestParam(defaultValue = "50") Integer limit,
            @RequestParam(required = false) String search,
            HttpServletRequest request) {
        UUID tenantId = tenantId(auth);
        int safeLimit = Math.max(1, Math.min(limit != null ? limit : 50, 200));
        List<TagResponse> data = tags.list(tenantId, safeLimit, search).stream()
                .map(mapper::toTagResponse)
                .toList();
        return ListResponse.of(data, CrmEnvelopes.Page.empty(safeLimit), requestId(request));
    }

    @RequireCapability("CRM.TAG.READ")
    @GetMapping("/{tagId}")
    public ResponseEntity<SingleResponse<TagResponse>> getTag(
            Authentication auth,
            @PathVariable UUID tagId,
            HttpServletRequest request) {
        UUID tenantId = tenantId(auth);
        TagRecord record = tags.getById(tenantId, tagId);
        if (record == null) {
            throw new CrmContractException(CrmErrorCode.CRM_TAG_NOT_FOUND);
        }
        TagResponse response = mapper.toTagResponse(record);
        return withEtag(response, "tag", tagId, response.version(), request);
    }

    // ────────────────────────────────────────────────────────────────────
    // Tag definitions — POST (create, idempotent)
    // ────────────────────────────────────────────────────────────────────

    @RequireCapability("CRM.TAG.WRITE")
    @PostMapping
    public ResponseEntity<SingleResponse<TagResponse>> createTag(
            Authentication auth,
            @Valid @RequestBody CreateTagRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            HttpServletRequest request) {
        String endpoint = "POST:/api/v2/crm/tags";
        var guard = idempotency.begin(auth, endpoint, key, body, request);
        if (guard.isReplay()) return idempotency.replay(guard, TagResponse.class);
        try {
            UUID tenantId = tenantId(auth);
            UUID actorId = userId(auth);
            TagRecord created;
            try {
                created = tags.create(tenantId, actorId,
                        new CreateTagCommand(body.name(), body.color()));
            } catch (DuplicateKeyException e) {
                idempotency.fail(guard);
                throw new CrmContractException(CrmErrorCode.CRM_DUPLICATE_TAG);
            }
            TagResponse response = mapper.toTagResponse(created);
            return idempotency.complete(guard, response, "tag", response.version(), HttpStatus.CREATED);
        } catch (RuntimeException e) {
            if (!(e instanceof CrmContractException)) idempotency.fail(guard);
            throw e;
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // Tag definitions — PATCH (update, ETag concurrency)
    // ────────────────────────────────────────────────────────────────────

    @RequireCapability("CRM.TAG.WRITE")
    @PatchMapping("/{tagId}")
    public ResponseEntity<SingleResponse<TagResponse>> updateTag(
            Authentication auth,
            @PathVariable UUID tagId,
            @Valid @RequestBody UpdateTagRequest body,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            HttpServletRequest request) {
        UUID tenantId = tenantId(auth);
        UUID actorId = userId(auth);
        TagRecord current = tags.getById(tenantId, tagId);
        if (current == null) {
            throw new CrmContractException(CrmErrorCode.CRM_TAG_NOT_FOUND);
        }
        etags.validateIfMatch(ifMatch, "tag", tagId, current.version());
        TagRecord updated;
        try {
            updated = tags.update(tenantId, actorId, tagId,
                    new UpdateTagCommand(body.name(), body.color()), current.version());
        } catch (DuplicateKeyException e) {
            throw new CrmContractException(CrmErrorCode.CRM_DUPLICATE_TAG);
        }
        TagResponse response = mapper.toTagResponse(updated);
        return withEtag(response, "tag", tagId, response.version(), request);
    }

    // ────────────────────────────────────────────────────────────────────
    // Tag definitions — DELETE
    // ────────────────────────────────────────────────────────────────────

    @RequireCapability("CRM.TAG.WRITE")
    @DeleteMapping("/{tagId}")
    public ResponseEntity<Void> deleteTag(
            Authentication auth,
            @PathVariable UUID tagId) {
        UUID tenantId = tenantId(auth);
        UUID actorId = userId(auth);
        tags.delete(tenantId, actorId, tagId);
        return ResponseEntity.noContent().build();
    }

    // ────────────────────────────────────────────────────────────────────
    // Tag assignments — GET by tag
    // ────────────────────────────────────────────────────────────────────

    @RequireCapability("CRM.TAG.READ")
    @GetMapping("/{tagId}/assignments")
    public ListResponse<TagAssignmentResponse> listAssignmentsByTag(
            Authentication auth,
            @PathVariable UUID tagId,
            @RequestParam(defaultValue = "50") Integer limit,
            HttpServletRequest request) {
        UUID tenantId = tenantId(auth);
        int safeLimit = Math.max(1, Math.min(limit != null ? limit : 50, 200));
        TagRecord tag = tags.getById(tenantId, tagId);
        if (tag == null) {
            throw new CrmContractException(CrmErrorCode.CRM_TAG_NOT_FOUND);
        }
        List<TagAssignmentResponse> data = tags.listAssignmentsByTag(tenantId, tagId, safeLimit).stream()
                .map(a -> mapper.toTagAssignmentResponse(a, tag.name(), tag.color()))
                .toList();
        return ListResponse.of(data, CrmEnvelopes.Page.empty(safeLimit), requestId(request));
    }

    // ────────────────────────────────────────────────────────────────────
    // Tag assignments — GET by subject
    // ────────────────────────────────────────────────────────────────────

    @RequireCapability("CRM.TAG.READ")
    @GetMapping("/assignments/by-subject")
    public ListResponse<TagAssignmentResponse> listAssignmentsBySubject(
            Authentication auth,
            @RequestParam String subjectType,
            @RequestParam UUID subjectId,
            HttpServletRequest request) {
        UUID tenantId = tenantId(auth);
        List<TagAssignmentRecord> assignments = tags.listAssignmentsBySubject(
                tenantId, subjectType, subjectId);
        List<TagAssignmentResponse> data = assignments.stream().map(a -> {
            TagRecord tag = tags.getById(tenantId, a.tagId());
            return mapper.toTagAssignmentResponse(a, tag.name(), tag.color());
        }).toList();
        return ListResponse.of(data, CrmEnvelopes.Page.empty(data.size()), requestId(request));
    }

    // ────────────────────────────────────────────────────────────────────
    // Tag assignments — POST (assign, idempotent)
    // ────────────────────────────────────────────────────────────────────

    @RequireCapability("CRM.TAG.WRITE")
    @PostMapping("/{tagId}/assignments")
    public ResponseEntity<SingleResponse<TagAssignmentResponse>> assignTag(
            Authentication auth,
            @PathVariable UUID tagId,
            @Valid @RequestBody AssignTagRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            HttpServletRequest request) {
        String endpoint = "POST:/api/v2/crm/tags/" + tagId + "/assignments";
        var guard = idempotency.begin(auth, endpoint, key, body, request);
        if (guard.isReplay()) return idempotency.replay(guard, TagAssignmentResponse.class);
        try {
            UUID tenantId = tenantId(auth);
            UUID actorId = userId(auth);
            TagRecord tag = tags.getById(tenantId, tagId);
            if (tag == null) {
                idempotency.fail(guard);
                throw new CrmContractException(CrmErrorCode.CRM_TAG_NOT_FOUND);
            }
            TagAssignmentRecord assignment = tags.assign(
                    tenantId, actorId, tagId, body.subjectType(), body.subjectId());
            TagAssignmentResponse response = mapper.toTagAssignmentResponse(
                    assignment, tag.name(), tag.color());
            return idempotency.complete(
                    guard, response, "tag-assignment", 0L, HttpStatus.CREATED);
        } catch (RuntimeException e) {
            if (!(e instanceof CrmContractException)) idempotency.fail(guard);
            throw e;
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // Tag assignments — DELETE (unassign)
    // ────────────────────────────────────────────────────────────────────

    @RequireCapability("CRM.TAG.WRITE")
    @DeleteMapping("/{tagId}/assignments")
    public ResponseEntity<Void> unassignTag(
            Authentication auth,
            @PathVariable UUID tagId,
            @RequestParam String subjectType,
            @RequestParam UUID subjectId) {
        UUID tenantId = tenantId(auth);
        UUID actorId = userId(auth);
        tags.unassign(tenantId, actorId, tagId, subjectType, subjectId);
        return ResponseEntity.noContent().build();
    }

    // ────────────────────────────────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────────────────────────────────

    private <T> ResponseEntity<SingleResponse<T>> withEtag(
            T body, String entityType, UUID id, long version, HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setETag(etags.etag(entityType, id, version));
        return ResponseEntity.ok()
                .headers(headers)
                .body(SingleResponse.of(body, requestId(request)));
    }

    private static UUID requestId(HttpServletRequest request) {
        if (request != null) {
            String value = request.getHeader("X-Request-ID");
            if (value != null && !value.isBlank()) {
                try {
                    return UUID.fromString(value);
                } catch (IllegalArgumentException ignored) {
                    // Generate a valid request id below.
                }
            }
        }
        return UUID.randomUUID();
    }

    private static UUID tenantId(Authentication auth) {
        return contextValue(auth, "tenant_id");
    }

    private static UUID userId(Authentication auth) {
        return contextValue(auth, "user_id");
    }

    @SuppressWarnings("unchecked")
    private static UUID contextValue(Authentication auth, String key) {
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getDetails() instanceof Map<?, ?> details)
                || details.get(key) == null) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR,
                    "Authenticated CRM context is required");
        }
        return UUID.fromString(details.get(key).toString());
    }
}
