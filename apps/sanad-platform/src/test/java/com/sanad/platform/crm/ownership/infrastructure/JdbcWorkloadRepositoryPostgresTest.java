package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.crm.ownership.domain.workload.WorkloadAssignment;
import com.sanad.platform.crm.ownership.domain.workload.WorkloadRepository.CreateWorkloadCommand;
import com.sanad.platform.crm.ownership.domain.workload.WorkloadRepository.UpdateWorkloadCommand;
import com.sanad.platform.crm.ownership.domain.workload.WorkloadStatus;
import com.sanad.platform.crm.testsupport.CrmRepositoryPostgresTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testcontainers PostgreSQL integration tests for {@link JdbcWorkloadRepository}.
 *
 * <p>Covers CRUD lifecycle, findByStaffId with status filter, findByServiceId,
 * sumEstimatedHoursByStaff, sumActualHoursByStaff, nullable fields (serviceId, jobId,
 * actualHours, endDate), delete, optimistic concurrency, and tenant isolation.
 */
class JdbcWorkloadRepositoryPostgresTest extends CrmRepositoryPostgresTestBase {

    private JdbcWorkloadRepository repo;
    private UUID tenantId;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        repo = new JdbcWorkloadRepository(jdbc());
        tenantId = newTenant();
        actorId = UUID.randomUUID();
    }

    // ── CREATE ─────────────────────────────────────────────────────────────

    @Test
    void create_persistsAllFields() {
        UUID staffId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();

        WorkloadAssignment saved = inTransaction(() -> repo.create(
                new CreateWorkloadCommand(tenantId, staffId, serviceId, jobId,
                        40, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 14), actorId)));

        assertThat(saved.id()).isNotNull();
        assertThat(saved.tenantId()).isEqualTo(tenantId);
        assertThat(saved.staffId()).isEqualTo(staffId);
        assertThat(saved.serviceId()).isEqualTo(serviceId);
        assertThat(saved.jobId()).isEqualTo(jobId);
        assertThat(saved.estimatedHours()).isEqualTo(40);
        assertThat(saved.actualHours()).isNull();
        assertThat(saved.status()).isEqualTo(WorkloadStatus.PLANNED);
        assertThat(saved.startDate()).isEqualTo(LocalDate.of(2026, 8, 10));
        assertThat(saved.endDate()).isEqualTo(LocalDate.of(2026, 8, 14));
        assertThat(saved.version()).isEqualTo(1);
    }

    @Test
    void create_persistsNullOptionalFields() {
        UUID staffId = UUID.randomUUID();

        WorkloadAssignment saved = inTransaction(() -> repo.create(
                new CreateWorkloadCommand(tenantId, staffId, null, null,
                        20, LocalDate.of(2026, 8, 10), null, actorId)));

        assertThat(saved.serviceId()).isNull();
        assertThat(saved.jobId()).isNull();
        assertThat(saved.endDate()).isNull();
    }

    @Test
    void createfindById_roundTrip() {
        UUID staffId = UUID.randomUUID();
        WorkloadAssignment saved = inTransaction(() -> repo.create(
                new CreateWorkloadCommand(tenantId, staffId, null, null,
                        40, LocalDate.of(2026, 8, 10), null, actorId)));

        Optional<WorkloadAssignment> found = repo.findById(tenantId, saved.id());
        assertThat(found).isPresent();
        assertThat(found.get()).isEqualTo(saved);
    }

    // ── READ ───────────────────────────────────────────────────────────────

    @Test
    void findById_returnsEmpty_whenMissing() {
        assertThat(repo.findById(tenantId, UUID.randomUUID())).isEmpty();
    }

    @Test
    void findByStaffId_filtersByStatus() {
        UUID staffId = UUID.randomUUID();

        inTransaction(() -> repo.create(new CreateWorkloadCommand(
                tenantId, staffId, null, null, 20,
                LocalDate.of(2026, 8, 10), null, actorId)));
        // create another and update to IN_PROGRESS
        WorkloadAssignment w2 = inTransaction(() -> repo.create(new CreateWorkloadCommand(
                tenantId, staffId, null, null, 30,
                LocalDate.of(2026, 8, 11), null, actorId)));
        inTransaction(() -> repo.update(tenantId, w2.id(),
                new UpdateWorkloadCommand(null, WorkloadStatus.IN_PROGRESS, null, actorId, 1)));

        List<WorkloadAssignment> planned = repo.findByStaffId(tenantId, staffId, WorkloadStatus.PLANNED);
        assertThat(planned).hasSize(1);
        assertThat(planned.get(0).estimatedHours()).isEqualTo(20);

        List<WorkloadAssignment> inProgress = repo.findByStaffId(tenantId, staffId, WorkloadStatus.IN_PROGRESS);
        assertThat(inProgress).hasSize(1);
        assertThat(inProgress.get(0).estimatedHours()).isEqualTo(30);
    }

    @Test
    void findByServiceId_returnsAssignmentsForService() {
        UUID staffId = UUID.randomUUID();
        UUID serviceA = UUID.randomUUID();
        UUID serviceB = UUID.randomUUID();

        inTransaction(() -> repo.create(new CreateWorkloadCommand(
                tenantId, staffId, serviceA, null, 20,
                LocalDate.of(2026, 8, 10), null, actorId)));
        inTransaction(() -> repo.create(new CreateWorkloadCommand(
                tenantId, staffId, serviceB, null, 30,
                LocalDate.of(2026, 8, 11), null, actorId)));

        assertThat(repo.findByServiceId(tenantId, serviceA)).hasSize(1);
        assertThat(repo.findByServiceId(tenantId, serviceB)).hasSize(1);
    }

    // ── UPDATE ─────────────────────────────────────────────────────────────

    @Test
    void update_bumpsVersionAndAppliesChanges() {
        UUID staffId = UUID.randomUUID();
        WorkloadAssignment created = inTransaction(() -> repo.create(
                new CreateWorkloadCommand(tenantId, staffId, null, null,
                        40, LocalDate.of(2026, 8, 10), null, actorId)));

        Optional<WorkloadAssignment> updated = inTransaction(() -> repo.update(
                tenantId, created.id(), new UpdateWorkloadCommand(
                        35, WorkloadStatus.COMPLETED, LocalDate.of(2026, 8, 14), actorId, 1)));

        assertThat(updated).isPresent();
        assertThat(updated.get().version()).isEqualTo(2);
        assertThat(updated.get().actualHours()).isEqualTo(35);
        assertThat(updated.get().status()).isEqualTo(WorkloadStatus.COMPLETED);
        assertThat(updated.get().endDate()).isEqualTo(LocalDate.of(2026, 8, 14));
    }

    @Test
    void update_returnsEmpty_whenVersionConflict() {
        UUID staffId = UUID.randomUUID();
        WorkloadAssignment created = inTransaction(() -> repo.create(
                new CreateWorkloadCommand(tenantId, staffId, null, null,
                        40, LocalDate.of(2026, 8, 10), null, actorId)));

        // first update (v1 -> v2)
        inTransaction(() -> repo.update(tenantId, created.id(),
                new UpdateWorkloadCommand(20, WorkloadStatus.IN_PROGRESS, null, actorId, 1)));

        // stale version
        Optional<WorkloadAssignment> conflict = inTransaction(() -> repo.update(
                tenantId, created.id(),
                new UpdateWorkloadCommand(40, WorkloadStatus.COMPLETED, null, actorId, 0)));
        assertThat(conflict).isEmpty();
    }

    // ── DELETE ─────────────────────────────────────────────────────────────

    @Test
    void delete_removesRecord() {
        UUID staffId = UUID.randomUUID();
        WorkloadAssignment created = inTransaction(() -> repo.create(
                new CreateWorkloadCommand(tenantId, staffId, null, null,
                        20, LocalDate.of(2026, 8, 10), null, actorId)));

        assertThat(inTransaction(() -> repo.delete(tenantId, created.id()))).isTrue();
        assertThat(repo.findById(tenantId, created.id())).isEmpty();
    }

    @Test
    void delete_returnsFalse_whenAbsent() {
        assertThat(inTransaction(() -> repo.delete(tenantId, UUID.randomUUID()))).isFalse();
    }

    // ── AGGREGATE QUERIES ──────────────────────────────────────────────────

    @Test
    void sumEstimatedHoursByStaff_sumsPlannedAndInProgress() {
        UUID staffId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();

        // PLANNED: 20h
        inTransaction(() -> repo.create(new CreateWorkloadCommand(
                tenantId, staffId, serviceId, null, 20,
                LocalDate.of(2026, 8, 10), null, actorId)));

        // IN_PROGRESS: 30h
        WorkloadAssignment w2 = inTransaction(() -> repo.create(new CreateWorkloadCommand(
                tenantId, staffId, serviceId, null, 30,
                LocalDate.of(2026, 8, 11), null, actorId)));
        inTransaction(() -> repo.update(tenantId, w2.id(),
                new UpdateWorkloadCommand(null, WorkloadStatus.IN_PROGRESS, null, actorId, 1)));

        // COMPLETED: 40h (should not be counted)
        WorkloadAssignment w3 = inTransaction(() -> repo.create(new CreateWorkloadCommand(
                tenantId, staffId, serviceId, null, 40,
                LocalDate.of(2026, 8, 12), null, actorId)));
        inTransaction(() -> repo.update(tenantId, w3.id(),
                new UpdateWorkloadCommand(40, WorkloadStatus.COMPLETED,
                        LocalDate.of(2026, 8, 14), actorId, 1)));

        int sum = repo.sumEstimatedHoursByStaff(tenantId, staffId,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        assertThat(sum).isEqualTo(50); // 20 + 30
    }

    @Test
    void sumActualHoursByStaff_sumsInProgressAndCompleted() {
        UUID staffId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();

        // IN_PROGRESS with actualHours=25
        WorkloadAssignment w1 = inTransaction(() -> repo.create(new CreateWorkloadCommand(
                tenantId, staffId, serviceId, null, 20,
                LocalDate.of(2026, 8, 10), null, actorId)));
        inTransaction(() -> repo.update(tenantId, w1.id(),
                new UpdateWorkloadCommand(25, WorkloadStatus.IN_PROGRESS, null, actorId, 1)));

        // COMPLETED with actualHours=35
        WorkloadAssignment w2 = inTransaction(() -> repo.create(new CreateWorkloadCommand(
                tenantId, staffId, serviceId, null, 30,
                LocalDate.of(2026, 8, 11), null, actorId)));
        inTransaction(() -> repo.update(tenantId, w2.id(),
                new UpdateWorkloadCommand(35, WorkloadStatus.COMPLETED,
                        LocalDate.of(2026, 8, 14), actorId, 1)));

        // PLANNED (should not be counted)
        inTransaction(() -> repo.create(new CreateWorkloadCommand(
                tenantId, staffId, serviceId, null, 40,
                LocalDate.of(2026, 8, 12), null, actorId)));

        int sum = repo.sumActualHoursByStaff(tenantId, staffId,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        assertThat(sum).isEqualTo(60); // 25 + 35
    }

    @Test
    void sumEstimatedHoursByStaff_returnsZero_whenNoData() {
        assertThat(repo.sumEstimatedHoursByStaff(tenantId, UUID.randomUUID(),
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))).isZero();
    }

    // ── TENANT ISOLATION ───────────────────────────────────────────────────

    @Test
    void findById_isolatedByTenant() {
        UUID staffId = UUID.randomUUID();
        UUID otherTenant = newTenant();
        WorkloadAssignment saved = inTransaction(() -> repo.create(
                new CreateWorkloadCommand(tenantId, staffId, null, null,
                        20, LocalDate.of(2026, 8, 10), null, actorId)));

        assertThat(repo.findById(otherTenant, saved.id())).isEmpty();
        assertThat(repo.findById(tenantId, saved.id())).isPresent();
    }

    @Test
    void sumEstimatedHoursByStaff_isolatedByTenant() {
        UUID staffId = UUID.randomUUID();
        UUID otherTenant = newTenant();
        inTransaction(() -> repo.create(new CreateWorkloadCommand(
                tenantId, staffId, null, null, 40,
                LocalDate.of(2026, 8, 10), null, actorId)));

        assertThat(repo.sumEstimatedHoursByStaff(otherTenant, staffId,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))).isZero();
        assertThat(repo.sumEstimatedHoursByStaff(tenantId, staffId,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))).isEqualTo(40);
    }
}
