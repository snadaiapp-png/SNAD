package com.sanad.platform.crm.note.application;

import com.sanad.platform.crm.note.domain.NoteRepository;
import com.sanad.platform.crm.note.domain.NoteRepository.CreateNoteCommand;
import com.sanad.platform.crm.note.domain.NoteRepository.NoteRecord;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Use-case façade for the Note bounded context.
 * <p>
 * Thin orchestration layer — applies {@code @Transactional} boundaries and
 * delegates to {@link NoteRepository}.
 * <p>
 * Branch: feature/crm-notes
 */
public class NoteUseCases {
    private final NoteRepository repo;

    public NoteUseCases(NoteRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public NoteRecord create(UUID tenantId, UUID actorId, CreateNoteCommand cmd) {
        return repo.create(tenantId, actorId, cmd);
    }

    public NoteRecord getById(UUID tenantId, UUID id) {
        return repo.findById(tenantId, id);
    }

    public List<NoteRecord> listBySubject(UUID tenantId, String subjectType, UUID subjectId, int limit, boolean includeArchived) {
        return repo.findAllBySubject(tenantId, subjectType, subjectId, limit, includeArchived);
    }

    @Transactional
    public NoteRecord archive(UUID tenantId, UUID actorId, UUID id, long expectedVersion) {
        return repo.archive(tenantId, actorId, id, expectedVersion);
    }
}
