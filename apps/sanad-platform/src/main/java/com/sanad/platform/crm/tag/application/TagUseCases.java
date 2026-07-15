package com.sanad.platform.crm.tag.application;

import com.sanad.platform.crm.tag.domain.TagRepository;
import com.sanad.platform.crm.tag.domain.TagRepository.CreateTagCommand;
import com.sanad.platform.crm.tag.domain.TagRepository.TagAssignmentRecord;
import com.sanad.platform.crm.tag.domain.TagRepository.TagRecord;
import com.sanad.platform.crm.tag.domain.TagRepository.UpdateTagCommand;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Use-case façade for the Tag bounded context.
 * <p>
 * Branch: feature/crm-tags
 */
public class TagUseCases {
    private final TagRepository repo;

    public TagUseCases(TagRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public TagRecord create(UUID tenantId, UUID actorId, CreateTagCommand cmd) {
        return repo.create(tenantId, actorId, cmd);
    }

    public TagRecord getById(UUID tenantId, UUID id) {
        return repo.findById(tenantId, id);
    }

    public List<TagRecord> list(UUID tenantId, int limit, String search) {
        return repo.findAll(tenantId, limit, search);
    }

    @Transactional
    public TagRecord update(UUID tenantId, UUID actorId, UUID id, UpdateTagCommand cmd, long expectedVersion) {
        return repo.update(tenantId, actorId, id, cmd, expectedVersion);
    }

    @Transactional
    public void delete(UUID tenantId, UUID actorId, UUID id) {
        repo.delete(tenantId, actorId, id);
    }

    public List<TagAssignmentRecord> listAssignmentsBySubject(UUID tenantId, String subjectType, UUID subjectId) {
        return repo.findAssignmentsBySubject(tenantId, subjectType, subjectId);
    }

    public List<TagAssignmentRecord> listAssignmentsByTag(UUID tenantId, UUID tagId, int limit) {
        return repo.findAssignmentsByTag(tenantId, tagId, limit);
    }

    @Transactional
    public TagAssignmentRecord assign(UUID tenantId, UUID actorId, UUID tagId, String subjectType, UUID subjectId) {
        return repo.assign(tenantId, actorId, tagId, subjectType, subjectId);
    }

    @Transactional
    public void unassign(UUID tenantId, UUID actorId, UUID tagId, String subjectType, UUID subjectId) {
        repo.unassign(tenantId, actorId, tagId, subjectType, subjectId);
    }
}
