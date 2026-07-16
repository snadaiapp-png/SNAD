package com.sanad.platform.crm.activity.application;

import com.sanad.platform.crm.activity.domain.ActivityRepository;
import com.sanad.platform.crm.activity.domain.ActivityRepository.ActivityRecord;
import com.sanad.platform.crm.activity.domain.ActivityRepository.CreateActivityCommand;
import com.sanad.platform.crm.activity.domain.ActivityRepository.UpdateActivityCommand;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ActivityUseCases {
    private final ActivityRepository repo;
    private final TimelineEventPort timeline;

    public ActivityUseCases(ActivityRepository repo, TimelineEventPort timeline) {
        this.repo = repo;
        this.timeline = timeline;
    }

    @Transactional
    public ActivityRecord create(UUID tenantId, UUID actorId, CreateActivityCommand cmd) {
        ActivityRecord created = repo.create(tenantId, actorId, cmd);
        if (created.relatedType() != null && created.relatedId() != null) {
            timeline.record(tenantId, created.relatedType(), created.relatedId(), "crm.activity.created",
                    "Activity created", "CRM_ACTIVITY", created.id(), actorId, Instant.now());
        }
        return created;
    }

    public ActivityRecord getById(UUID tenantId, UUID id) {
        return repo.findById(tenantId, id);
    }

    public List<ActivityRecord> list(UUID tenantId, int limit, String relatedType,
                                     UUID relatedId, String status) {
        return repo.findAll(tenantId, limit, relatedType, relatedId, status);
    }

    @Transactional
    public ActivityRecord update(UUID tenantId, UUID actorId, UUID id,
                                 UpdateActivityCommand cmd, long expectedVersion) {
        return repo.update(tenantId, actorId, id, cmd, expectedVersion);
    }

    @Transactional
    public ActivityRecord complete(UUID tenantId, UUID actorId, UUID id,
                                   String result, long expectedVersion) {
        return repo.complete(tenantId, actorId, id, result, expectedVersion);
    }
}
