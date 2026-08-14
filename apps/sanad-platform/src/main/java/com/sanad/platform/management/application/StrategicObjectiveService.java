package com.sanad.platform.management.application;

import com.sanad.platform.management.domain.KeyResult;
import com.sanad.platform.management.domain.KeyResultRepository;
import com.sanad.platform.management.domain.StrategicInitiative;
import com.sanad.platform.management.domain.StrategicInitiativeRepository;
import com.sanad.platform.management.domain.StrategicObjective;
import com.sanad.platform.management.domain.StrategicObjectiveRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service for {@link StrategicObjective} lifecycle management.
 *
 * <p>Encapsulates business rules for objective state transitions, progress
 * aggregation from Key Results, and cascade operations. Controllers should
 * never call repositories directly — they go through this service.
 */
@Service
public class StrategicObjectiveService {

    private final StrategicObjectiveRepository objectiveRepo;
    private final KeyResultRepository keyResultRepo;
    private final StrategicInitiativeRepository initiativeRepo;

    public StrategicObjectiveService(
            StrategicObjectiveRepository objectiveRepo,
            KeyResultRepository keyResultRepo,
            StrategicInitiativeRepository initiativeRepo) {
        this.objectiveRepo = objectiveRepo;
        this.keyResultRepo = keyResultRepo;
        this.initiativeRepo = initiativeRepo;
    }

    @Transactional
    public StrategicObjective createObjective(StrategicObjective objective) {
        return objectiveRepo.save(objective);
    }

    @Transactional(readOnly = true)
    public Optional<StrategicObjective> findById(UUID tenantId, UUID id) {
        return objectiveRepo.findById(tenantId, id);
    }

    @Transactional(readOnly = true)
    public List<StrategicObjective> findByTenant(UUID tenantId, int limit) {
        return objectiveRepo.findByTenant(tenantId, limit);
    }

    @Transactional(readOnly = true)
    public List<StrategicObjective> findActiveForPeriod(UUID tenantId, LocalDate asOf) {
        return objectiveRepo.findActiveObjectivesForPeriod(tenantId, asOf);
    }

    @Transactional
    public StrategicObjective activate(UUID tenantId, UUID objectiveId) {
        var objective = objectiveRepo.findById(tenantId, objectiveId)
                .orElseThrow(() -> new IllegalArgumentException("Objective not found: " + objectiveId));
        return objectiveRepo.save(objective.activate());
    }

    @Transactional
    public StrategicObjective markAtRisk(UUID tenantId, UUID objectiveId) {
        var objective = objectiveRepo.findById(tenantId, objectiveId)
                .orElseThrow(() -> new IllegalArgumentException("Objective not found: " + objectiveId));
        return objectiveRepo.save(objective.markAtRisk());
    }

    @Transactional
    public StrategicObjective markOffTrack(UUID tenantId, UUID objectiveId) {
        var objective = objectiveRepo.findById(tenantId, objectiveId)
                .orElseThrow(() -> new IllegalArgumentException("Objective not found: " + objectiveId));
        return objectiveRepo.save(objective.markOffTrack());
    }

    @Transactional
    public StrategicObjective achieve(UUID tenantId, UUID objectiveId) {
        var objective = objectiveRepo.findById(tenantId, objectiveId)
                .orElseThrow(() -> new IllegalArgumentException("Objective not found: " + objectiveId));
        return objectiveRepo.save(objective.achieve());
    }

    @Transactional
    public StrategicObjective close(UUID tenantId, UUID objectiveId) {
        var objective = objectiveRepo.findById(tenantId, objectiveId)
                .orElseThrow(() -> new IllegalArgumentException("Objective not found: " + objectiveId));
        return objectiveRepo.save(objective.close());
    }

    @Transactional
    public StrategicObjective cancel(UUID tenantId, UUID objectiveId) {
        var objective = objectiveRepo.findById(tenantId, objectiveId)
                .orElseThrow(() -> new IllegalArgumentException("Objective not found: " + objectiveId));
        return objectiveRepo.save(objective.cancel());
    }

    /**
     * Recompute the objective's progress from its Key Results.
     *
     * <p>Progress is the weighted average of Key Result progress percentages.
     * Weight is the {@code weight_pct} field on each Key Result.
     */
    @Transactional
    public StrategicObjective recomputeProgress(UUID tenantId, UUID objectiveId) {
        var objective = objectiveRepo.findById(tenantId, objectiveId)
                .orElseThrow(() -> new IllegalArgumentException("Objective not found: " + objectiveId));
        var keyResults = keyResultRepo.findByObjective(tenantId, objectiveId);
        if (keyResults.isEmpty()) {
            return objective;
        }
        int totalWeight = keyResults.stream().mapToInt(KeyResult::weightPct).sum();
        if (totalWeight == 0) {
            return objective;
        }
        int weightedProgress = keyResults.stream()
                .mapToInt(kr -> kr.computeStatus(kr.currentValue()) == KeyResult.Status.ACHIEVED
                        ? 100 * kr.weightPct()
                        : (int) (kr.currentValue().doubleValue()
                                / Math.max(kr.targetValue().doubleValue(), 0.0001)
                                * 100 * kr.weightPct()))
                .sum() / totalWeight;
        weightedProgress = Math.max(0, Math.min(100, weightedProgress));
        return objectiveRepo.save(objective.withProgress(weightedProgress));
    }

    @Transactional
    public void delete(UUID tenantId, UUID objectiveId) {
        // Cascade: delete Key Results and Initiatives first
        var keyResults = keyResultRepo.findByObjective(tenantId, objectiveId);
        keyResults.forEach(kr -> keyResultRepo.deleteById(tenantId, kr.id()));
        var initiatives = initiativeRepo.findByObjective(tenantId, objectiveId);
        initiatives.forEach(i -> initiativeRepo.deleteById(tenantId, i.id()));
        objectiveRepo.deleteById(tenantId, objectiveId);
    }
}
