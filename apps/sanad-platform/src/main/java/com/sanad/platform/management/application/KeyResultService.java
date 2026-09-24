package com.sanad.platform.management.application;

import com.sanad.platform.management.domain.KeyResult;
import com.sanad.platform.management.domain.KeyResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service for {@link KeyResult} management.
 */
@Service
public class KeyResultService {

    private final KeyResultRepository repo;

    public KeyResultService(KeyResultRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public KeyResult create(KeyResult keyResult) {
        return repo.save(keyResult);
    }

    @Transactional(readOnly = true)
    public Optional<KeyResult> findById(UUID tenantId, UUID id) {
        return repo.findById(tenantId, id);
    }

    @Transactional(readOnly = true)
    public List<KeyResult> findByObjective(UUID tenantId, UUID objectiveId) {
        return repo.findByObjective(tenantId, objectiveId);
    }

    @Transactional
    public KeyResult recordMeasurement(UUID tenantId, UUID keyResultId, BigDecimal newValue) {
        var kr = repo.findById(tenantId, keyResultId)
                .orElseThrow(() -> new IllegalArgumentException("Key Result not found: " + keyResultId));
        return repo.save(kr.recordMeasurement(newValue));
    }

    @Transactional
    public KeyResult markMissed(UUID tenantId, UUID keyResultId) {
        var kr = repo.findById(tenantId, keyResultId)
                .orElseThrow(() -> new IllegalArgumentException("Key Result not found: " + keyResultId));
        return repo.save(kr.markMissed());
    }

    @Transactional
    public void delete(UUID tenantId, UUID keyResultId) {
        repo.deleteById(tenantId, keyResultId);
    }
}
