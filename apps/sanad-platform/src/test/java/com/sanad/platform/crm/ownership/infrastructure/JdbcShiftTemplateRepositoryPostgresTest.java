package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.crm.ownership.domain.scheduling.ShiftTemplate;
import com.sanad.platform.crm.ownership.domain.scheduling.ShiftTemplateRepository.CreateShiftTemplateCommand;
import com.sanad.platform.crm.ownership.domain.scheduling.ShiftTemplateRepository.UpdateShiftTemplateCommand;
import com.sanad.platform.crm.ownership.domain.scheduling.ShiftTemplateStatus;
import com.sanad.platform.crm.testsupport.CrmRepositoryPostgresTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testcontainers PostgreSQL integration tests for {@link JdbcShiftTemplateRepository}.
 *
 * <p>Covers CRUD lifecycle, findAll pagination, existsByName constraint check,
 * optimistic concurrency, audit columns, and tenant isolation.
 */
class JdbcShiftTemplateRepositoryPostgresTest extends CrmRepositoryPostgresTestBase {

    private JdbcShiftTemplateRepository repo;
    private UUID tenantId;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        repo = new JdbcShiftTemplateRepository(jdbc());
        tenantId = newTenant();
        actorId = UUID.randomUUID();
    }

    // ── CREATE ─────────────────────────────────────────────────────────────

    @Test
    void create_persistsAllFields() {
        ShiftTemplate saved = inTransaction(() -> repo.create(
                new CreateShiftTemplateCommand(tenantId, "Morning Shift",
                        LocalTime.of(8, 0), LocalTime.of(16, 0),
                        List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), actorId)));

        assertThat(saved.id()).isNotNull();
        assertThat(saved.tenantId()).isEqualTo(tenantId);
        assertThat(saved.name()).isEqualTo("Morning Shift");
        assertThat(saved.startTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(saved.endTime()).isEqualTo(LocalTime.of(16, 0));
        assertThat(saved.daysOfWeek()).containsExactly(DayOfWeek.MONDAY, DayOfWeek.TUESDAY);
        assertThat(saved.status()).isEqualTo(ShiftTemplateStatus.ACTIVE);
        assertThat(saved.createdBy()).isEqualTo(actorId);
        assertThat(saved.updatedBy()).isEqualTo(actorId);
        assertThat(saved.version()).isEqualTo(1);
        assertThat(saved.createdAt()).isNotNull();
        assertThat(saved.updatedAt()).isNotNull();
    }

    @Test
    void createfindById_roundTrip() {
        ShiftTemplate saved = inTransaction(() -> repo.create(
                new CreateShiftTemplateCommand(tenantId, "Evening Shift",
                        LocalTime.of(16, 0), LocalTime.of(0, 0),
                        List.of(DayOfWeek.WEDNESDAY), actorId)));

        Optional<ShiftTemplate> found = repo.findById(tenantId, saved.id());
        assertThat(found).isPresent();
        assertThat(found.get()).isEqualTo(saved);
    }

    // ── READ ───────────────────────────────────────────────────────────────

    @Test
    void findById_returnsEmpty_whenMissing() {
        assertThat(repo.findById(tenantId, UUID.randomUUID())).isEmpty();
    }

    @Test
    void findAll_returnsOrderedByName() {
        inTransaction(() -> repo.create(new CreateShiftTemplateCommand(
                tenantId, "Zebra Shift", LocalTime.of(8, 0), LocalTime.of(16, 0),
                List.of(DayOfWeek.MONDAY), actorId)));
        inTransaction(() -> repo.create(new CreateShiftTemplateCommand(
                tenantId, "Alpha Shift", LocalTime.of(9, 0), LocalTime.of(17, 0),
                List.of(DayOfWeek.TUESDAY), actorId)));

        List<ShiftTemplate> all = repo.findAll(tenantId, 50, 0);
        assertThat(all).hasSize(2);
        assertThat(all.get(0).name()).isEqualTo("Alpha Shift");
        assertThat(all.get(1).name()).isEqualTo("Zebra Shift");
    }

    @Test
    void findAll_respectsLimitAndOffset() {
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            inTransaction(() -> repo.create(new CreateShiftTemplateCommand(
                    tenantId, "Shift-" + idx, LocalTime.of(8, 0), LocalTime.of(16, 0),
                    List.of(DayOfWeek.MONDAY), actorId)));
        }

        List<ShiftTemplate> page = repo.findAll(tenantId, 2, 1);
        assertThat(page).hasSize(2);
        assertThat(page.get(0).name()).isEqualTo("Shift-1");
        assertThat(page.get(1).name()).isEqualTo("Shift-2");
    }

    // ── UPDATE ─────────────────────────────────────────────────────────────

    @Test
    void update_bumpsVersionAndAppliesChanges() {
        ShiftTemplate created = inTransaction(() -> repo.create(
                new CreateShiftTemplateCommand(tenantId, "Morning",
                        LocalTime.of(8, 0), LocalTime.of(16, 0),
                        List.of(DayOfWeek.MONDAY), actorId)));

        Optional<ShiftTemplate> updated = inTransaction(() -> repo.update(
                tenantId, created.id(), new UpdateShiftTemplateCommand(
                        "Afternoon", LocalTime.of(12, 0), LocalTime.of(20, 0),
                        List.of(DayOfWeek.FRIDAY), ShiftTemplateStatus.INACTIVE,
                        actorId, 0)));

        assertThat(updated).isPresent();
        assertThat(updated.get().version()).isEqualTo(2);
        assertThat(updated.get().name()).isEqualTo("Afternoon");
        assertThat(updated.get().startTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(updated.get().endTime()).isEqualTo(LocalTime.of(20, 0));
        assertThat(updated.get().daysOfWeek()).containsExactly(DayOfWeek.FRIDAY);
        assertThat(updated.get().status()).isEqualTo(ShiftTemplateStatus.INACTIVE);
    }

    @Test
    void update_returnsEmpty_whenVersionConflict() {
        ShiftTemplate created = inTransaction(() -> repo.create(
                new CreateShiftTemplateCommand(tenantId, "Morning",
                        LocalTime.of(8, 0), LocalTime.of(16, 0),
                        List.of(DayOfWeek.MONDAY), actorId)));

        // first update succeeds (v0 -> v1)
        inTransaction(() -> repo.update(tenantId, created.id(), new UpdateShiftTemplateCommand(
                "Afternoon", LocalTime.of(12, 0), LocalTime.of(20, 0),
                List.of(DayOfWeek.FRIDAY), ShiftTemplateStatus.INACTIVE,
                actorId, 0)));

        // stale version returns Optional.empty()
        Optional<ShiftTemplate> conflict = inTransaction(() -> repo.update(
                tenantId, created.id(), new UpdateShiftTemplateCommand(
                        "Night", LocalTime.of(22, 0), LocalTime.of(6, 0),
                        List.of(DayOfWeek.SATURDAY), ShiftTemplateStatus.ACTIVE,
                        actorId, 0)));
        assertThat(conflict).isEmpty();
    }

    // ── EXISTS BY NAME ─────────────────────────────────────────────────────

    @Test
    void existsByName_trueWhenPresent() {
        inTransaction(() -> repo.create(new CreateShiftTemplateCommand(
                tenantId, "Morning", LocalTime.of(8, 0), LocalTime.of(16, 0),
                List.of(DayOfWeek.MONDAY), actorId)));
        assertThat(repo.existsByName(tenantId, "Morning", null)).isTrue();
    }

    @Test
    void existsByName_falseWhenAbsent() {
        assertThat(repo.existsByName(tenantId, "Nonexistent", null)).isFalse();
    }

    @Test
    void existsByName_excludesSpecifiedId() {
        ShiftTemplate created = inTransaction(() -> repo.create(new CreateShiftTemplateCommand(
                tenantId, "Morning", LocalTime.of(8, 0), LocalTime.of(16, 0),
                List.of(DayOfWeek.MONDAY), actorId)));
        // excludes itself -> false
        assertThat(repo.existsByName(tenantId, "Morning", created.id())).isFalse();
    }

    // ── TENANT ISOLATION ───────────────────────────────────────────────────

    @Test
    void findById_isolatedByTenant() {
        UUID otherTenant = newTenant();
        ShiftTemplate saved = inTransaction(() -> repo.create(
                new CreateShiftTemplateCommand(tenantId, "Morning",
                        LocalTime.of(8, 0), LocalTime.of(16, 0),
                        List.of(DayOfWeek.MONDAY), actorId)));

        assertThat(repo.findById(otherTenant, saved.id())).isEmpty();
        assertThat(repo.findById(tenantId, saved.id())).isPresent();
    }

    @Test
    void existsByName_isolatedByTenant() {
        inTransaction(() -> repo.create(new CreateShiftTemplateCommand(
                tenantId, "Morning", LocalTime.of(8, 0), LocalTime.of(16, 0),
                List.of(DayOfWeek.MONDAY), actorId)));
        UUID otherTenant = newTenant();
        assertThat(repo.existsByName(otherTenant, "Morning", null)).isFalse();
    }
}
