package com.sanad.platform.hr.structure.application;

import com.sanad.platform.hr.structure.domain.HrOrgUnitVersion;
import com.sanad.platform.hr.structure.infrastructure.JdbcHrStructureRepository;

import java.time.LocalDate;
import java.util.UUID;

/**
 * HR Structure service — application-layer facade for Org Unit, Job, and
 * Position versioning operations.
 *
 * <p>Task 3 RED skeleton — methods throw UnsupportedOperationException.
 * GREEN replaces with real implementation using JdbcHrStructureRepository.</p>
 */
public final class HrStructureService {

    private final JdbcHrStructureRepository repository;

    public HrStructureService(JdbcHrStructureRepository repository) {
        this.repository = repository;
    }

    /**
     * Revise an Org Unit: create a new effective-dated version. If the
     * proposed parent creates a cycle during the overlapping effective
     * period, reject with a cycle error.
     */
    public HrOrgUnitVersion reviseOrgUnit(UUID tenantId, UUID orgUnitId,
                                            LocalDate effectiveFrom,
                                            UUID parentOrgUnitId,
                                            String name, String code, String unitType) {
        throw new UnsupportedOperationException("HrStructureService.reviseOrgUnit — Task 3 RED skeleton");
    }
}
