package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.crm.ownership.domain.availability.AvailabilityRepository.CreateAvailabilityCommand;
import com.sanad.platform.crm.ownership.domain.availability.AvailabilityRepository.UpdateAvailabilityCommand;
import com.sanad.platform.crm.ownership.domain.availability.AvailabilityType;
import com.sanad.platform.crm.ownership.domain.availability.StaffAvailability;
import com.sanad.platform.crm.testsupport.CrmRepositoryPostgresTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testcontainers PostgreSQL integration tests for {@link JdbcAvailabilityRepository}.
 *
 * <p>Covers CRUD lifecycle, nullable fields (start_time, end_time, reason),
 * findByStaffId date filtering, delete, optimistic concurrency, and tenant isolation.
 */
class JdbcAvailabilityRepositoryPostgresTest extends CrmRepositoryPostgresTestBase {

    private JdbcAvailabilityRepository repo;
    private UUID tenantId;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        repo = new JdbcAvailabilityRepository(jdbc());
        tenantId = newTenant();
        actorId = UUID.randomUUID();
    }

    // ── CREATE ─────────────────────────────────────────────────────────────

    @Test
    void create_persistsAllFields() {
        UUID staffId = UUID.randomUUID();
        StaffAvailability saved = inTransaction(() -> repo.create(
                new CreateAvailabilityCommand(tenantId, staffId, AvailabilityType.AVAILABLE,
                        LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 14),
                        LocalTime.of(9, 0), LocalTime.of(17, 0), "Working from office", actorId)));

        assertThat(saved.id()).isNotNull();
        assertThat(saved.tenantId()).isEqualTo(tenantId);
        assertThat(saved.staffId()).isEqualTo(staffId);
        assertThat(saved.type()).isEqualTo(AvailabilityType.AVAILABLE);
        assertThat(saved.startDate()).isEqualTo(LocalDate.of(2026, 8, 10));
        assertThat(saved.endDate()).isEqualTo(LocalDate.of(2026, 8, 14));
        assertThat(saved.startTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(saved.endTime()).isEqualTo(LocalTime.of(17, 0));
        assertThat(saved.reason()).isEqualTo("Working from office");
        assertThat(saved.version()).isEqualTo(1);
    }

    @Test
    void create_persistsNullOptionalFields() {
        UUID staffId = UUID.randomUUID();
        StaffAvailability saved = inTransaction(() -> repo.create(
                new CreateAvailabilityCommand(tenantId, staffId, AvailabilityType.UNAVAILABLE,
                        LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 10),
                        null, null, null, actorId)));

        assertThat(saved.startTime()).isNull();
        assertThat(saved.endTime()).isNull();
        assertThat(saved.reason()).isNull();
    }

    @Test
    void createfindById_roundTrip() {
        UUID staffId = UUID.randomUUID();
        StaffAvailability saved = inTransaction(() -> repo.create(
                new CreateAvailabilityCommand(tenantId, staffId, AvailabilityType.ON_LEAVE,
                        LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 20),
                        null, null, "Vacation", actorId)));

        Optional<StaffAvailability> found = repo.findById(tenantId, saved.id());
        assertThat(found).isPresent();
        assertThat(found.get()).isEqualTo(saved);
    }

    // ── READ ───────────────────────────────────────────────────────────────

    @Test
    void findById_returnsEmpty_whenMissing() {
        assertThat(repo.findById(tenantId, UUID.randomUUID())).isEmpty();
    }

    @Test
    void findByStaffId_filtersByDateRange() {
        UUID staffId = UUID.randomUUID();

        // availability in August
        inTransaction(() -> repo.create(new CreateAvailabilityCommand(
                tenantId, staffId, AvailabilityType.AVAILABLE,
                LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 14),
                null, null, null, actorId)));
        // availability in September
        inTransaction(() -> repo.create(new CreateAvailabilityCommand(
                tenantId, staffId, AvailabilityType.ON_LEAVE,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5),
                null, null, "Holiday", actorId)));

        List<StaffAvailability> aug = repo.findByStaffId(tenantId, staffId,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        assertThat(aug).hasSize(1);
        assertThat(aug.get(0).type()).isEqualTo(AvailabilityType.AVAILABLE);

        List<StaffAvailability> sep = repo.findByStaffId(tenantId, staffId,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        assertThat(sep).hasSize(1);
        assertThat(sep.get(0).type()).isEqualTo(AvailabilityType.ON_LEAVE);
    }

    // ── UPDATE ─────────────────────────────────────────────────────────────

    @Test
    void update_bumpsVersionAndAppliesChanges() {
        UUID staffId = UUID.randomUUID();
        StaffAvailability created = inTransaction(() -> repo.create(
                new CreateAvailabilityCommand(tenantId, staffId, AvailabilityType.AVAILABLE,
                        LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 14),
                        LocalTime.of(9, 0), LocalTime.of(17, 0), null, actorId)));

        Optional<StaffAvailability> updated = inTransaction(() -> repo.update(
                tenantId, created.id(), new UpdateAvailabilityCommand(
                        AvailabilityType.ON_LEAVE, LocalDate.of(2026, 8, 12),
                        LocalDate.of(2026, 8, 16), null, null,
                        "Sick leave", actorId, 1)));

        assertThat(updated).isPresent();
        assertThat(updated.get().version()).isEqualTo(2);
        assertThat(updated.get().type()).isEqualTo(AvailabilityType.ON_LEAVE);
        assertThat(updated.get().reason()).isEqualTo("Sick leave");
    }

    @Test
    void update_returnsEmpty_whenVersionConflict() {
        UUID staffId = UUID.randomUUID();
        StaffAvailability created = inTransaction(() -> repo.create(
                new CreateAvailabilityCommand(tenantId, staffId, AvailabilityType.AVAILABLE,
                        LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 14),
                        null, null, null, actorId)));

        // first update (v1 -> v2)
        inTransaction(() -> repo.update(tenantId, created.id(), new UpdateAvailabilityCommand(
                AvailabilityType.UNAVAILABLE, LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 14), null, null, "Busy", actorId, 1)));

        // stale version
        Optional<StaffAvailability> conflict = inTransaction(() -> repo.update(
                tenantId, created.id(), new UpdateAvailabilityCommand(
                        AvailabilityType.ON_LEAVE, LocalDate.of(2026, 8, 12),
                        LocalDate.of(2026, 8, 16), null, null, "Gone", actorId, 0)));
        assertThat(conflict).isEmpty();
    }

    // ── DELETE ─────────────────────────────────────────────────────────────

    @Test
    void delete_removesRecord() {
        UUID staffId = UUID.randomUUID();
        StaffAvailability created = inTransaction(() -> repo.create(
                new CreateAvailabilityCommand(tenantId, staffId, AvailabilityType.AVAILABLE,
                        LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 14),
                        null, null, null, actorId)));

        boolean deleted = inTransaction(() -> repo.delete(tenantId, created.id()));
        assertThat(deleted).isTrue();
        assertThat(repo.findById(tenantId, created.id())).isEmpty();
    }

    @Test
    void delete_returnsFalse_whenAbsent() {
        assertThat(inTransaction(() -> repo.delete(tenantId, UUID.randomUUID()))).isFalse();
    }

    // ── TENANT ISOLATION ───────────────────────────────────────────────────

    @Test
    void findById_isolatedByTenant() {
        UUID staffId = UUID.randomUUID();
        UUID otherTenant = newTenant();
        StaffAvailability saved = inTransaction(() -> repo.create(
                new CreateAvailabilityCommand(tenantId, staffId, AvailabilityType.AVAILABLE,
                        LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 14),
                        null, null, null, actorId)));

        assertThat(repo.findById(otherTenant, saved.id())).isEmpty();
        assertThat(repo.findById(tenantId, saved.id())).isPresent();
    }
}
