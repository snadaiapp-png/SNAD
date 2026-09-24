package com.sanad.platform.management.application;

import com.sanad.platform.management.domain.StrategicInitiative;
import com.sanad.platform.management.domain.StrategicInitiativeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class StrategicInitiativeService {

    private final StrategicInitiativeRepository repo;

    public StrategicInitiativeService(StrategicInitiativeRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public StrategicInitiative create(StrategicInitiative initiative) {
        return repo.save(initiative);
    }

    @Transactional(readOnly = true)
    public Optional<StrategicInitiative> findById(UUID tenantId, UUID id) {
        return repo.findById(tenantId, id);
    }

    @Transactional(readOnly = true)
    public List<StrategicInitiative> findByObjective(UUID tenantId, UUID objectiveId) {
        return repo.findByObjective(tenantId, objectiveId);
    }

    @Transactional
    public StrategicInitiative start(UUID tenantId, UUID id) {
        var initiative = repo.findById(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Initiative not found: " + id));
        return repo.save(initiative.start());
    }

    @Transactional
    public StrategicInitiative hold(UUID tenantId, UUID id) {
        var initiative = repo.findById(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Initiative not found: " + id));
        return repo.save(initiative.hold());
    }

    @Transactional
    public StrategicInitiative resume(UUID tenantId, UUID id) {
        var initiative = repo.findById(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Initiative not found: " + id));
        return repo.save(initiative.resume());
    }

    @Transactional
    public StrategicInitiative complete(UUID tenantId, UUID id) {
        var initiative = repo.findById(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Initiative not found: " + id));
        return repo.save(initiative.complete());
    }

    @Transactional
    public StrategicInitiative cancel(UUID tenantId, UUID id) {
        var initiative = repo.findById(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Initiative not found: " + id));
        return repo.save(initiative.cancel());
    }

    @Transactional
    public StrategicInitiative fail(UUID tenantId, UUID id) {
        var initiative = repo.findById(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Initiative not found: " + id));
        return repo.save(initiative.fail());
    }

    @Transactional
    public StrategicInitiative updateProgress(UUID tenantId, UUID id, int progressPct) {
        var initiative = repo.findById(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Initiative not found: " + id));
        return repo.save(initiative.updateProgress(progressPct));
    }

    @Transactional
    public StrategicInitiative recordSpend(UUID tenantId, UUID id, long additionalSpendMinor) {
        var initiative = repo.findById(tenantId, id)
                .orElseThrow(() -> new IllegalArgumentException("Initiative not found: " + id));
        return repo.save(initiative.recordSpend(additionalSpendMinor));
    }
}
