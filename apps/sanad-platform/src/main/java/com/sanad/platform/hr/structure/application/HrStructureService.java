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
        // Close any existing open version for this org unit before the new
        // effective date. This prevents the EXCLUDE constraint from rejecting
        // the new open version AND ensures the cycle check only considers
        // versions that are actually active during the candidate period.
        repository.closeOpenOrgUnitVersion(tenantId, orgUnitId, effectiveFrom);

        // Period-aware cycle check: traverse the parent chain from parentOrgUnitId
        // and check if orgUnitId appears as a parent anywhere in the chain
        // during the candidate effective period [effectiveFrom, ∞).
        if (parentOrgUnitId != null && !parentOrgUnitId.equals(orgUnitId)) {
            boolean cycle = repository.createsCycle(
                    tenantId, orgUnitId, parentOrgUnitId, effectiveFrom, null);
            if (cycle) {
                throw new IllegalStateException(
                    "ORG_CYCLE: setting parent " + parentOrgUnitId +
                    " for org unit " + orgUnitId +
                    " creates a cycle during effective period from " + effectiveFrom);
            }
        }

        HrOrgUnitVersion version = new HrOrgUnitVersion(
                UUID.randomUUID(), tenantId, orgUnitId, name, code, unitType,
                parentOrgUnitId, effectiveFrom, null, "ACTIVE");
        repository.saveOrgUnitVersion(version);
        return version;
    }
}
