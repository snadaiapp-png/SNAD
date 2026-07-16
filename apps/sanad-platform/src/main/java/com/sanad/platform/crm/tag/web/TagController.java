package com.sanad.platform.crm.tag.web;

import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.tag.application.TagUseCases;
import com.sanad.platform.crm.tag.domain.TagRepository.CreateTagCommand;
import com.sanad.platform.crm.tag.domain.TagRepository.TagAssignmentRecord;
import com.sanad.platform.crm.tag.domain.TagRepository.TagRecord;
import com.sanad.platform.crm.tag.domain.TagRepository.UpdateTagCommand;
import com.sanad.platform.crm.tag.web.TagModels.AssignTagRequest;
import com.sanad.platform.crm.tag.web.TagModels.CreateTagRequest;
import com.sanad.platform.crm.tag.web.TagModels.UpdateTagRequest;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * V1 REST controller for CRM Tags.
 * <p>
 * Mounted under {@code /api/v1/crm/tags}. Two sub-resources:
 *   - Tag definitions (CRUD)
 *   - Tag assignments (assign/unassign to any CRM entity)
 * <p>
 * Branch: feature/crm-tags
 */
@RestController
@RequestMapping("/api/v1/crm/tags")
public class TagController {

    private final TagUseCases tags;

    public TagController(TagUseCases tags) {
        this.tags = tags;
    }

    // ────────────────────────────────────────────────────────────────────
    // Tag definitions CRUD
    // ────────────────────────────────────────────────────────────────────

    @RequireCapability("CRM.TAG.READ")
    @GetMapping
    public List<Map<String, Object>> listTags(
            Authentication authentication,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String search) {
        UUID tenantId = tenantId(authentication);
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return tags.list(tenantId, safeLimit, search).stream().map(this::toTagRow).toList();
    }

    @RequireCapability("CRM.TAG.READ")
    @GetMapping("/{tagId}")
    public Map<String, Object> getTag(Authentication authentication, @PathVariable UUID tagId) {
        return toTagRow(tags.getById(tenantId(authentication), tagId));
    }

    @RequireCapability("CRM.TAG.WRITE")
    @PostMapping
    public ResponseEntity<Map<String, Object>> createTag(
            Authentication authentication,
            @Valid @RequestBody CreateTagRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        try {
            TagRecord created = tags.create(tenantId, actorId, new CreateTagCommand(request.name(), request.color()));
            return ResponseEntity.status(HttpStatus.CREATED).body(toTagRow(created));
        } catch (DuplicateKeyException e) {
            throw new CrmContractException(CrmErrorCode.CRM_DUPLICATE_TAG);
        }
    }

    @RequireCapability("CRM.TAG.WRITE")
    @PatchMapping("/{tagId}")
    public Map<String, Object> updateTag(
            Authentication authentication,
            @PathVariable UUID tagId,
            @Valid @RequestBody UpdateTagRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        TagRecord current = tags.getById(tenantId, tagId);
        try {
            TagRecord updated = tags.update(tenantId, actorId, tagId,
                    new UpdateTagCommand(request.name(), request.color()), current.version());
            return toTagRow(updated);
        } catch (DuplicateKeyException e) {
            throw new CrmContractException(CrmErrorCode.CRM_DUPLICATE_TAG);
        }
    }

    @RequireCapability("CRM.TAG.WRITE")
    @DeleteMapping("/{tagId}")
    public ResponseEntity<Void> deleteTag(Authentication authentication, @PathVariable UUID tagId) {
        tags.delete(tenantId(authentication), userId(authentication), tagId);
        return ResponseEntity.noContent().build();
    }

    // ────────────────────────────────────────────────────────────────────
    // Tag assignments
    // ────────────────────────────────────────────────────────────────────

    @RequireCapability("CRM.TAG.READ")
    @GetMapping("/{tagId}/assignments")
    public List<Map<String, Object>> listAssignmentsByTag(
            Authentication authentication,
            @PathVariable UUID tagId,
            @RequestParam(defaultValue = "50") int limit) {
        UUID tenantId = tenantId(authentication);
        int safeLimit = Math.max(1, Math.min(limit, 200));
        TagRecord tag = tags.getById(tenantId, tagId);
        return tags.listAssignmentsByTag(tenantId, tagId, safeLimit).stream()
                .map(a -> toAssignmentRow(a, tag.name(), tag.color())).toList();
    }

    @RequireCapability("CRM.TAG.READ")
    @GetMapping("/assignments/by-subject")
    public List<Map<String, Object>> listAssignmentsBySubject(
            Authentication authentication,
            @RequestParam String subjectType,
            @RequestParam UUID subjectId) {
        UUID tenantId = tenantId(authentication);
        List<TagAssignmentRecord> assignments = tags.listAssignmentsBySubject(tenantId, subjectType, subjectId);
        return assignments.stream().map(a -> {
            TagRecord tag = tags.getById(tenantId, a.tagId());
            return toAssignmentRow(a, tag.name(), tag.color());
        }).toList();
    }

    @RequireCapability("CRM.TAG.WRITE")
    @PostMapping("/{tagId}/assignments")
    public ResponseEntity<Map<String, Object>> assignTag(
            Authentication authentication,
            @PathVariable UUID tagId,
            @Valid @RequestBody AssignTagRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        TagRecord tag = tags.getById(tenantId, tagId);
        TagAssignmentRecord assignment = tags.assign(tenantId, actorId, tagId, request.subjectType(), request.subjectId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toAssignmentRow(assignment, tag.name(), tag.color()));
    }

    @RequireCapability("CRM.TAG.WRITE")
    @DeleteMapping("/{tagId}/assignments")
    public ResponseEntity<Void> unassignTag(
            Authentication authentication,
            @PathVariable UUID tagId,
            @RequestParam @Pattern(regexp = "ACCOUNT|CONTACT|LEAD|OPPORTUNITY|ACTIVITY|TASK|NOTE", flags = Pattern.Flag.CASE_INSENSITIVE) String subjectType,
            @RequestParam UUID subjectId) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        tags.unassign(tenantId, actorId, tagId, subjectType, subjectId);
        return ResponseEntity.noContent().build();
    }

    // ────────────────────────────────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────────────────────────────────

    private Map<String, Object> toTagRow(TagRecord r) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", r.id());
        row.put("version", r.version());
        row.put("name", r.name());
        row.put("color", r.color());
        row.put("created_at", toIso(r.createdAt()));
        row.put("updated_at", toIso(r.updatedAt()));
        return row;
    }

    private Map<String, Object> toAssignmentRow(TagAssignmentRecord a, String tagName, String tagColor) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", a.id());
        row.put("tag_id", a.tagId());
        row.put("tag_name", tagName);
        row.put("tag_color", tagColor);
        row.put("subject_type", a.subjectType());
        row.put("subject_id", a.subjectId());
        row.put("assigned_by", a.assignedBy());
        row.put("assigned_at", toIso(a.assignedAt()));
        return row;
    }

    private static String toIso(Instant v) {
        return v == null ? null : v.toString();
    }

    private static UUID tenantId(Authentication authentication) {
        return context(authentication, "tenant_id");
    }

    private static UUID userId(Authentication authentication) {
        return context(authentication, "user_id");
    }

    private static UUID context(Authentication authentication, String key) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getDetails() instanceof Map<?, ?> details)
                || details.get(key) == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated CRM context is required");
        }
        try {
            return UUID.fromString(details.get(key).toString());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authenticated CRM context", exception);
        }
    }
}
