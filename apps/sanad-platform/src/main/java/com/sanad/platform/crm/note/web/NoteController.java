package com.sanad.platform.crm.note.web;

import com.sanad.platform.crm.note.application.NoteUseCases;
import com.sanad.platform.crm.note.domain.NoteRepository.CreateNoteCommand;
import com.sanad.platform.crm.note.domain.NoteRepository.NoteRecord;
import com.sanad.platform.crm.note.web.NoteModels.CreateNoteRequest;
import com.sanad.platform.security.authorization.RequireCapability;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
 * V1 REST controller for CRM Notes.
 * <p>
 * Mounted under {@code /api/v1/crm/notes}. Returns plain {@code Map<String,Object>}
 * with snake_case keys (consistent with V1 contract).
 * <p>
 * Branch: feature/crm-notes
 */
@RestController
@RequestMapping("/api/v1/crm/notes")
public class NoteController {

    private final NoteUseCases notes;

    public NoteController(NoteUseCases notes) {
        this.notes = notes;
    }

    @RequireCapability("CRM.NOTE.READ")
    @GetMapping
    public List<Map<String, Object>> listNotes(
            Authentication authentication,
            @RequestParam UUID subjectId,
            @RequestParam String subjectType,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "false") boolean includeArchived) {
        UUID tenantId = tenantId(authentication);
        int safeLimit = Math.max(1, Math.min(limit, 200));
        List<NoteRecord> rows = notes.listBySubject(tenantId, subjectType, subjectId, safeLimit, includeArchived);
        return rows.stream().map(this::toRow).toList();
    }

    @RequireCapability("CRM.NOTE.READ")
    @GetMapping("/{noteId}")
    public Map<String, Object> getNote(Authentication authentication, @PathVariable UUID noteId) {
        return toRow(notes.getById(tenantId(authentication), noteId));
    }

    @RequireCapability("CRM.NOTE.WRITE")
    @PostMapping
    public ResponseEntity<Map<String, Object>> createNote(
            Authentication authentication,
            @Valid @RequestBody CreateNoteRequest request) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);

        CreateNoteCommand cmd = new CreateNoteCommand(
                request.subjectType(),
                request.subjectId(),
                request.body(),
                request.authorUserId());

        NoteRecord created = notes.create(tenantId, actorId, cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(toRow(created));
    }

    @RequireCapability("CRM.NOTE.WRITE")
    @PatchMapping("/{noteId}/archive")
    public Map<String, Object> archiveNote(Authentication authentication, @PathVariable UUID noteId) {
        UUID tenantId = tenantId(authentication);
        UUID actorId = userId(authentication);
        NoteRecord current = notes.getById(tenantId, noteId);
        return toRow(notes.archive(tenantId, actorId, noteId, current.version()));
    }

    private Map<String, Object> toRow(NoteRecord r) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", r.id());
        row.put("version", r.version());
        row.put("subject_type", r.subjectType());
        row.put("subject_id", r.subjectId());
        row.put("body", r.body());
        row.put("author_user_id", r.authorUserId());
        row.put("archived", r.archived());
        row.put("created_at", toIso(r.createdAt()));
        row.put("updated_at", toIso(r.updatedAt()));
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
