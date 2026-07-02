package com.sanad.platform.workshop.service;

import com.sanad.platform.workshop.domain.WorkshopDependency;
import com.sanad.platform.workshop.domain.WorkshopWorkItem;
import com.sanad.platform.workshop.repository.WorkshopDependencyRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class WorkshopGraphPolicy {

    private final WorkshopDependencyRepository dependencyRepository;

    public WorkshopGraphPolicy(WorkshopDependencyRepository dependencyRepository) {
        this.dependencyRepository = dependencyRepository;
    }

    public boolean dependenciesSatisfied(UUID tenantId, UUID workshopId, UUID itemId,
                                         WorkshopWorkItem.Status target) {
        for (WorkshopDependency dependency : dependencyRepository
                .findByTenantIdAndWorkshopIdAndSuccessorId(tenantId, workshopId, itemId)) {
            WorkshopWorkItem.Status predecessor = dependency.getPredecessor().getStatus();
            boolean satisfied = switch (dependency.getType()) {
                case FINISH_TO_START -> predecessor == WorkshopWorkItem.Status.DONE;
                case START_TO_START -> EnumSet.of(WorkshopWorkItem.Status.IN_PROGRESS,
                        WorkshopWorkItem.Status.IN_REVIEW, WorkshopWorkItem.Status.DONE)
                        .contains(predecessor);
                case FINISH_TO_FINISH -> target != WorkshopWorkItem.Status.DONE
                        || predecessor == WorkshopWorkItem.Status.DONE;
            };
            if (!satisfied) return false;
        }
        return true;
    }

    public boolean wouldCreateCycle(UUID tenantId, UUID workshopId,
                                    UUID newPredecessor, UUID newSuccessor) {
        List<WorkshopDependency> dependencies = dependencyRepository
                .findByTenantIdAndWorkshopId(tenantId, workshopId);
        Map<UUID, Set<UUID>> graph = new HashMap<>();
        for (WorkshopDependency dependency : dependencies) {
            graph.computeIfAbsent(dependency.getPredecessor().getId(), key -> new HashSet<>())
                    .add(dependency.getSuccessor().getId());
        }
        graph.computeIfAbsent(newPredecessor, key -> new HashSet<>()).add(newSuccessor);

        ArrayDeque<UUID> pending = new ArrayDeque<>();
        Set<UUID> visited = new HashSet<>();
        pending.push(newSuccessor);
        while (!pending.isEmpty()) {
            UUID current = pending.pop();
            if (current.equals(newPredecessor)) return true;
            if (visited.add(current)) {
                graph.getOrDefault(current, Set.of()).forEach(pending::push);
            }
        }
        return false;
    }
}
