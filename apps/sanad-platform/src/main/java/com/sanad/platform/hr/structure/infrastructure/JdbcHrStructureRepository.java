package com.sanad.platform.hr.structure.infrastructure;

import com.sanad.platform.hr.structure.domain.HrJob;
import com.sanad.platform.hr.structure.domain.HrJobVersion;
import com.sanad.platform.hr.structure.domain.HrOrgUnit;
import com.sanad.platform.hr.structure.domain.HrOrgUnitVersion;
import com.sanad.platform.hr.structure.domain.HrPositionVersion;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC implementation of HR structure repository. Every operation sets
 * tenant context on the same connection used for the query.
 *
 * <p>Task 3 RED skeleton — methods throw UnsupportedOperationException.
 * GREEN replaces with real JDBC.</p>
 */
public final class JdbcHrStructureRepository {

    private final DataSource dataSource;

    public JdbcHrStructureRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // --- Org Unit ---

    public void saveOrgUnit(HrOrgUnit orgUnit) {
        throw new UnsupportedOperationException("JdbcHrStructureRepository.saveOrgUnit — Task 3 RED skeleton");
    }

    public Optional<HrOrgUnit> findOrgUnitById(UUID tenantId, UUID orgUnitId) {
        throw new UnsupportedOperationException("JdbcHrStructureRepository.findOrgUnitById — Task 3 RED skeleton");
    }

    public void saveOrgUnitVersion(HrOrgUnitVersion version) {
        throw new UnsupportedOperationException("JdbcHrStructureRepository.saveOrgUnitVersion — Task 3 RED skeleton");
    }

    public List<HrOrgUnitVersion> orgUnitVersions(UUID tenantId, UUID orgUnitId) {
        throw new UnsupportedOperationException("JdbcHrStructureRepository.orgUnitVersions — Task 3 RED skeleton");
    }

    // --- Job ---

    public void saveJob(HrJob job) {
        throw new UnsupportedOperationException("JdbcHrStructureRepository.saveJob — Task 3 RED skeleton");
    }

    public void saveJobVersion(HrJobVersion version) {
        throw new UnsupportedOperationException("JdbcHrStructureRepository.saveJobVersion — Task 3 RED skeleton");
    }

    public List<HrJobVersion> jobVersions(UUID tenantId, UUID jobId) {
        throw new UnsupportedOperationException("JdbcHrStructureRepository.jobVersions — Task 3 RED skeleton");
    }

    // --- Position ---

    public void savePositionVersion(HrPositionVersion version) {
        throw new UnsupportedOperationException("JdbcHrStructureRepository.savePositionVersion — Task 3 RED skeleton");
    }

    public List<HrPositionVersion> positionVersions(UUID tenantId, UUID positionId) {
        throw new UnsupportedOperationException("JdbcHrStructureRepository.positionVersions — Task 3 RED skeleton");
    }
}
