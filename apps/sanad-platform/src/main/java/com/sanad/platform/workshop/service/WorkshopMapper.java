package com.sanad.platform.workshop.service;

import com.sanad.platform.workshop.domain.Workshop;
import com.sanad.platform.workshop.domain.WorkshopActivity;
import com.sanad.platform.workshop.domain.WorkshopAssignment;
import com.sanad.platform.workshop.domain.WorkshopDependency;
import com.sanad.platform.workshop.domain.WorkshopWorkItem;
import com.sanad.platform.workshop.dto.WorkshopDtos;
import org.springframework.stereotype.Component;

@Component
public class WorkshopMapper {

    public WorkshopDtos.WorkshopResponse toResponse(Workshop value) {
        return new WorkshopDtos.WorkshopResponse(value.getId(), value.getOrganization().getId(),
                value.getCode(), value.getName(), value.getDescription(), value.getStatus(),
                value.getPlannedStart(), value.getPlannedEnd(), value.getActualStart(),
                value.getActualEnd(), value.getCreatedBy(), value.getCreatedAt(),
                value.getUpdatedAt(), value.getVersion());
    }

    public WorkshopDtos.WorkItemResponse toResponse(WorkshopWorkItem value) {
        return new WorkshopDtos.WorkItemResponse(value.getId(), value.getWorkshop().getId(),
                value.getParentItem() == null ? null : value.getParentItem().getId(), value.getCode(),
                value.getTitle(), value.getDescription(), value.getStatus(), value.getPriority(),
                value.getPrimaryAssigneeUserId(), value.getDueAt(), value.getEstimatedMinutes(),
                value.getActualMinutes(), value.getSequenceNo(), value.getBlockedReason(), value.getCreatedBy(),
                value.getCreatedAt(), value.getUpdatedAt(), value.getVersion());
    }

    public WorkshopDtos.AssignmentResponse toResponse(WorkshopAssignment value) {
        return new WorkshopDtos.AssignmentResponse(value.getId(), value.getWorkshop().getId(),
                value.getWorkItem().getId(), value.getUserId(), value.getRole(),
                value.getCreatedBy(), value.getCreatedAt());
    }

    public WorkshopDtos.DependencyResponse toResponse(WorkshopDependency value) {
        return new WorkshopDtos.DependencyResponse(value.getId(), value.getWorkshop().getId(),
                value.getPredecessor().getId(), value.getSuccessor().getId(), value.getType(),
                value.getCreatedBy(), value.getCreatedAt());
    }

    public WorkshopDtos.ActivityResponse toResponse(WorkshopActivity value) {
        return new WorkshopDtos.ActivityResponse(value.getId(), value.getWorkshop().getId(),
                value.getWorkItem().getId(), value.getType(), value.getBody(), value.getMinutes(),
                value.getExternalUri(), value.getStartedAt(), value.getEndedAt(), value.isCompleted(),
                value.getCompletedBy(), value.getCompletedAt(), value.getCreatedBy(), value.getCreatedAt());
    }
}
