package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.crm.ownership.domain.scheduling.ShiftAssignment;
import com.sanad.platform.crm.ownership.domain.scheduling.ShiftAssignmentRepository.CreateShiftAssignmentCommand;
import com.sanad.platform.crm.ownership.domain.scheduling.ShiftAssignmentRepository.UpdateShiftAssignmentCommand;
import com.sanad.platform.crm.ownership.domain.scheduling.ShiftAssignmentStatus;
import com.sanad.platform.crm.ownership.domain.scheduling.ShiftTemplateRepository.CreateShiftTemplateCommand;
import com.sanad.platform.crm.testsupport.CrmRepositoryPostgresTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testcontainers PostgreSQL integration tests for {@link JdbcShiftAssignmentRepository}.
 *
 * <p>Covers CRUD lifecycle, FK to shift templates, findByTeamId, findByStaffId date
 * range filtering, overlap detection, optimistic concurrency, and tenant isolation.
 */
class JdbcShiftAssignmentRepositoryPostgresTest extends CrmRepositoryPostgresTestBase {

    private JdbcShiftAssignmentRepository repo;
    private JdbcShiftTemplateRepository templateRepo;
    private UUID tenantId;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        repo = new JdbcShiftAssignmentRepository(jdbc());
        templateRepo = new JdbcShiftTemplateRepository(jdbc());
        tenantId = newTenant();
        actorId = UUID.randomUUID();
    }

    private UUID createTemplate() {
        return inTransaction(() -> templateRepo.create(
                new CreateShiftTemplateCommand(tenantId, "Standard",
                        LocalTime.of(8, 0), LocalTime.of(16, 0),
                        List.of(DayOfWeek.MONDAY), actorId))).id();
    }

    // ── CREATE ─────────────────────────────────────────────────────────────

    @Test
    void create_persistsAllFields() {
        UUID templateId = createTemplate();
        UUID teamId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();

        ShiftAssignment saved = inTransaction(() -> repo.create(
                new CreateShiftAssignmentCommand(tenantId, teamId, staffId, templateId,
                        LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 14), actorId)));

        assertThat(saved.id()).isNotNull();
        assertThat(saved.tenantId()).isEqualTo(tenantId);
        assertThat(saved.teamId()).isEqualTo(teamId);
        assertThat(saved.staffId()).isEqualTo(staffId);
        assertThat(saved.shiftTemplateId()).isEqualTo(templateId);
        assertThat(saved.startDate()).isEqualTo(LocalDate.of(2026, 8, 10));
        assertThat(saved.endDate()).isEqualTo(LocalDate.of(2026, 8, 14));
        assertThat(saved.status()).isEqualTo(ShiftAssignmentStatus.SCHEDULED);
        assertThat(saved.version()).isEqualTo(1);
    }

    @Test
    void createfindById_roundTrip() {
        UUID templateId = createTemplate();
        ShiftAssignment saved = inTransaction(() -> repo.create(
                new CreateShiftAssignmentCommand(tenantId, UUID.randomUUID(),
                        UUID.randomUUID(), templateId,
                        LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 14), actorId)));

        Optional<ShiftAssignment> found = repo.findById(tenantId, saved.id());
        assertThat(found).isPresent();
        assertThat(found.get()).isEqualTo(saved);
    }

    // ── READ ───────────────────────────────────────────────────────────────

    @Test
    void findById_returnsEmpty_whenMissing() {
        assertThat(repo.findById(tenantId, UUID.randomUUID())).isEmpty();
    }

    @Test
    void findByTeamId_returnsAssignmentsForTeam() {
        UUID templateId = createTemplate();
        UUID teamA = UUID.randomUUID();
        UUID teamB = UUID.randomUUID();

        inTransaction(() -> repo.create(new CreateShiftAssignmentCommand(
                tenantId, teamA, UUID.randomUUID(), templateId,
                LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 14), actorId)));
        inTransaction(() -> repo.create(new CreateShiftAssignmentCommand(
                tenantId, teamB, UUID.randomUUID(), templateId,
                LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 14), actorId)));

        assertThat(repo.findByTeamId(tenantId, teamA, 50, 0)).hasSize(1);
        assertThat(repo.findByTeamId(tenantId, teamB, 50, 0)).hasSize(1);
    }

    @Test
    void findByStaffId_filtersByDateRange() {
        UUID templateId = createTemplate();
        UUID staffId = UUID.randomUUID();

        // assignment starts Aug 10
        inTransaction(() -> repo.create(new CreateShiftAssignmentCommand(
                tenantId, UUID.randomUUID(), staffId, templateId,
                LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 14), actorId)));
        // assignment starts Sep 1
        inTransaction(() -> repo.create(new CreateShiftAssignmentCommand(
                tenantId, UUID.randomUUID(), staffId, templateId,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5), actorId)));

        // query Aug 1-31: only first assignment
        List<ShiftAssignment> aug = repo.findByStaffId(tenantId, staffId,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        assertThat(aug).hasSize(1);
        assertThat(aug.get(0).startDate()).isEqualTo(LocalDate.of(2026, 8, 10));

        // query Sep 1-30: only second assignment
        List<ShiftAssignment> sep = repo.findByStaffId(tenantId, staffId,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        assertThat(sep).hasSize(1);
    }

    // ── UPDATE ─────────────────────────────────────────────────────────────

    @Test
    void update_bumpsVersionAndAppliesChanges() {
        UUID templateId = createTemplate();
        ShiftAssignment created = inTransaction(() -> repo.create(
                new CreateShiftAssignmentCommand(tenantId, UUID.randomUUID(),
                        UUID.randomUUID(), templateId,
                        LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 14), actorId)));

        UUID newTemplateId = inTransaction(() -> templateRepo.create(
                new CreateShiftTemplateCommand(tenantId, "Night",
                        LocalTime.of(22, 0), LocalTime.of(6, 0),
                        List.of(DayOfWeek.WEDNESDAY), actorId))).id();

        Optional<ShiftAssignment> updated = inTransaction(() -> repo.update(
                tenantId, created.id(), new UpdateShiftAssignmentCommand(
                        newTemplateId, LocalDate.of(2026, 8, 11), LocalDate.of(2026, 8, 15),
                        ShiftAssignmentStatus.ACTIVE, actorId, 1)));

        assertThat(updated).isPresent();
        assertThat(updated.get().version()).isEqualTo(2);
        assertThat(updated.get().shiftTemplateId()).isEqualTo(newTemplateId);
        assertThat(updated.get().startDate()).isEqualTo(LocalDate.of(2026, 8, 11));
        assertThat(updated.get().status()).isEqualTo(ShiftAssignmentStatus.ACTIVE);
    }

    @Test
    void update_returnsEmpty_whenVersionConflict() {
        UUID templateId = createTemplate();
        ShiftAssignment created = inTransaction(() -> repo.create(
                new CreateShiftAssignmentCommand(tenantId, UUID.randomUUID(),
                        UUID.randomUUID(), templateId,
                        LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 14), actorId)));

        // first update (v1 -> v2)
        inTransaction(() -> repo.update(tenantId, created.id(), new UpdateShiftAssignmentCommand(
                templateId, LocalDate.of(2026, 8, 11), LocalDate.of(2026, 8, 15),
                ShiftAssignmentStatus.ACTIVE, actorId, 1)));

        // stale version returns Optional.empty()
        Optional<ShiftAssignment> conflict = inTransaction(() -> repo.update(
                tenantId, created.id(), new UpdateShiftAssignmentCommand(
                        templateId, LocalDate.of(2026, 8, 12), LocalDate.of(2026, 8, 16),
                        ShiftAssignmentStatus.COMPLETED, actorId, 0)));
        assertThat(conflict).isEmpty();
    }

    // ── OVERLAP DETECTION ──────────────────────────────────────────────────

    @Test
    void hasOverlap_detectsOverlappingRanges() {
        UUID templateId = createTemplate();
        UUID staffId = UUID.randomUUID();

        inTransaction(() -> repo.create(new CreateShiftAssignmentCommand(
                tenantId, UUID.randomUUID(), staffId, templateId,
                LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 14), actorId)));

        // fully contained overlap
        assertThat(repo.hasOverlap(tenantId, staffId,
                LocalDate.of(2026, 8, 11), LocalDate.of(2026, 8, 13), null)).isTrue();

        // partial overlap (starts before, ends within)
        assertThat(repo.hasOverlap(tenantId, staffId,
                LocalDate.of(2026, 8, 8), LocalDate.of(2026, 8, 11), null)).isTrue();

        // no overlap (adjacent ranges)
        assertThat(repo.hasOverlap(tenantId, staffId,
                LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 20), null)).isFalse();
    }

    @Test
    void hasOverlap_excludesCancelledAssignments() {
        UUID templateId = createTemplate();
        UUID staffId = UUID.randomUUID();

        ShiftAssignment cancelled = inTransaction(() -> repo.create(
                new CreateShiftAssignmentCommand(tenantId, UUID.randomUUID(), staffId, templateId,
                        LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 14), actorId)));

        // cancel it
        inTransaction(() -> repo.update(tenantId, cancelled.id(),
                new UpdateShiftAssignmentCommand(templateId,
                        LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 14),
                        ShiftAssignmentStatus.CANCELLED, actorId, 1)));

        // cancelled assignment should not be detected as overlapping
        assertThat(repo.hasOverlap(tenantId, staffId,
                LocalDate.of(2026, 8, 11), LocalDate.of(2026, 8, 13), null)).isFalse();
    }

    @Test
    void hasOverlap_excludesSpecifiedId() {
        UUID templateId = createTemplate();
        UUID staffId = UUID.randomUUID();

        ShiftAssignment existing = inTransaction(() -> repo.create(
                new CreateShiftAssignmentCommand(tenantId, UUID.randomUUID(), staffId, templateId,
                        LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 14), actorId)));

        // excluding its own id -> no overlap
        assertThat(repo.hasOverlap(tenantId, staffId,
                LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 14), existing.id())).isFalse();
    }

    // ── TENANT ISOLATION ───────────────────────────────────────────────────

    @Test
    void findById_isolatedByTenant() {
        UUID templateId = createTemplate();
        UUID otherTenant = newTenant();
        ShiftAssignment saved = inTransaction(() -> repo.create(
                new CreateShiftAssignmentCommand(tenantId, UUID.randomUUID(),
                        UUID.randomUUID(), templateId,
                        LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 14), actorId)));

        assertThat(repo.findById(otherTenant, saved.id())).isEmpty();
        assertThat(repo.findById(tenantId, saved.id())).isPresent();
    }

    @Test
    void hasOverlap_isolatedByTenant() {
        UUID templateId = createTemplate();
        UUID staffId = UUID.randomUUID();
        UUID otherTenant = newTenant();

        inTransaction(() -> repo.create(new CreateShiftAssignmentCommand(
                tenantId, UUID.randomUUID(), staffId, templateId,
                LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 14), actorId)));

        // same staffId in different tenant -> no overlap
        assertThat(repo.hasOverlap(otherTenant, staffId,
                LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 14), null)).isFalse();
    }
}
