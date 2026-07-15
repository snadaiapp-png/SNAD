package com.sanad.platform.crm.note.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request DTOs for the Note bounded context.
 * <p>
 * Branch: feature/crm-notes
 */
final class NoteModels {

    private NoteModels() {}

    record CreateNoteRequest(
            @NotBlank @Pattern(regexp = "ACCOUNT|CONTACT|LEAD|OPPORTUNITY|ACTIVITY|TASK", flags = Pattern.Flag.CASE_INSENSITIVE) String subjectType,
            @NotNull UUID subjectId,
            @NotBlank @Size(max = 10000) String body,
            UUID authorUserId) {}

    record ArchiveNoteRequest(@Size(max = 500) String reason) {}
}
