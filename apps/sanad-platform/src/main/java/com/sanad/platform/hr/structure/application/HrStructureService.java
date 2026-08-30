package com.sanad.platform.hr.structure.application;

import com.sanad.platform.hr.structure.domain.HrOrgUnitVersion;
import com.sanad.platform.hr.structure.infrastructure.JdbcHrStructureRepository;

import java.time.LocalDate;
import java.util.UUID;

/**
 * HR Structure service — application-layer facade for Org Unit, Job, and
 * Position versioning operations.
 *
 * <p>Period-aware cycle detection: when revising an Org Unit's parent,
 * the service checks whether the proposed parent relationship would
 * create a cycle DURING the candidate effective period. Historical
 * non-overlapping relationships do NOT trigger false positives.</p>
 *
 * <p>Atomic revision: validation (cycle check) happens BEFORE any
 * mutation (close/insert). If the cycle check rejects the revision,
 * ZERO state changes are persisted. All three operations (validate,
 * close, insert) execute on ONE JDBC connection/transaction.</p>
 */
public final class HrStructureService {

    private final JdbcHrStructureRepository repository;

    public HrStructureService(JdbcHrStructureRepository repository) {
        this.repository = repository;
    }

    /**
     * Revise an Org Unit: atomically validate cycle → close old version →
     * insert new version, all on ONE connection/transaction.
     *
     * <p>If the proposed parent creates a cycle during the overlapping
     * effective period, reject with a cycle error. NO state is mutated
     * on rejection (the old open version remains unchanged).</p>
     */
    public HrOrgUnitVersion reviseOrgUnit(UUID tenantId, UUID orgUnitId,
                                            LocalDate effectiveFrom,
                                            UUID parentOrgUnitId,
                                            String name, String code, String unitType) {
        return repository.reviseOrgUnitAtomically(
                tenantId, orgUnitId, effectiveFrom,
                parentOrgUnitId, name, code, unitType);
    }
}
