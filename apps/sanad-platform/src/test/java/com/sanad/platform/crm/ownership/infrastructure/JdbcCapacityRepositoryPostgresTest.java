package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.crm.ownership.domain.capacity.CapacityPlan;
import com.sanad.platform.crm.ownership.domain.capacity.CapacityRepository.CreateCapacityPlanCommand;
import com.sanad.platform.crm.ownership.domain.capacity.CapacityRepository.UpdateCapacityPlanCommand;
import com.sanad.platform.crm.ownership.domain.capacity.CapacityStatus;
import com.sanad.platform.crm.testsupport.CrmRepositoryPostgresTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testcontainers PostgreSQL integration tests for {@link JdbcCapacityRepository}.
 *
 * <p>Covers CRUD lifecycle, findByTeamId, findActiveByTeamAndPeriod, optimistic concurrency,
 * audit columns, and tenant isolation.
 */
class JdbcCapacityRepositoryPostgresTest extends CrmRepositoryPostgresTestBase {

    private JdbcCapacityRepository repo;
    private UUID tenantId;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        repo = new JdbcCapacityRepository(jdbc());
        tenantId = newTenant();
        actorId = UUID.randomUUID();
    }

    // ── CREATE ─────────────────────────────────────────────────────────────

    @Test
    void create_persistsAllFields() {
        UUID teamId = UUID.randomUUID();
        CapacityPlan saved = inTransaction(() -> repo.create(
                new CreateCapacityPlanCommand(tenantId, teamId,
                        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                        100, actorId)));

        assertThat(saved.id()).isNotNull();
        assertThat(saved.tenantId()).isEqualTo(tenantId);
        assertThat(saved.teamId()).isEqualTo(teamId);
        assertThat(saved.periodStart()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(saved.periodEnd()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(saved.maxCapacity()).isEqualTo(100);
        assertThat(saved.allocatedCapacity()).isEqualTo(0);
        assertThat(saved.status()).isEqualTo(CapacityStatus.DRAFT);
        assertThat(saved.version()).isEqualTo(1);
    }

    @Test
    void createfindById_roundTrip() {
        UUID teamId = UUID.randomUUID();
        CapacityPlan saved = inTransaction(() -> repo.create(
                new CreateCapacityPlanCommand(tenantId, teamId,
                        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                        100, actorId)));

        Optional<CapacityPlan> found = repo.findById(tenantId, saved.id());
        assertThat(found).isPresent();
        assertThat(found.get()).isEqualTo(saved);
    }

    // ── READ ───────────────────────────────────────────────────────────────

    @Test
    void findById_returnsEmpty_whenMissing() {
        assertThat(repo.findById(tenantId, UUID.randomUUID())).isEmpty();
    }

    @Test
    void findByTeamId_returnsOrderedByPeriodStartDesc() {
        UUID teamId = UUID.randomUUID();

        inTransaction(() -> repo.create(new CreateCapacityPlanCommand(
                tenantId, teamId, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31),
                50, actorId)));
        inTransaction(() -> repo.create(new CreateCapacityPlanCommand(
                tenantId, teamId, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                100, actorId)));

        List<CapacityPlan> plans = repo.findByTeamId(tenantId, teamId);
        assertThat(plans).hasSize(2);
        // ordered by period_start DESC
        assertThat(plans.get(0).periodStart()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(plans.get(1).periodStart()).isEqualTo(LocalDate.of(2026, 7, 1));
    }

    @Test
    void findActiveByTeamAndPeriod_returnsMatchingActivePlan() {
        UUID teamId = UUID.randomUUID();

        // create and activate a plan for August
        CapacityPlan draft = inTransaction(() -> repo.create(
                new CreateCapacityPlanCommand(tenantId, teamId,
                        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                        100, actorId)));
        inTransaction(() -> repo.update(tenantId, draft.id(),
                new UpdateCapacityPlanCommand(100, 0, CapacityStatus.ACTIVE, actorId, 1)));

        // query mid-August -> should find it
        Optional<CapacityPlan> found = repo.findActiveByTeamAndPeriod(
                tenantId, teamId, LocalDate.of(2026, 8, 15));
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(draft.id());
    }

    @Test
    void findActiveByTeamAndPeriod_returnsEmpty_whenNoActivePlan() {
        UUID teamId = UUID.randomUUID();
        inTransaction(() -> repo.create(
                new CreateCapacityPlanCommand(tenantId, teamId,
                        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                        100, actorId)));

        // still DRAFT, not ACTIVE
        assertThat(repo.findActiveByTeamAndPeriod(
                tenantId, teamId, LocalDate.of(2026, 8, 15))).isEmpty();
    }

    @Test
    void findActiveByTeamAndPeriod_returnsEmpty_whenDateOutsideRange() {
        UUID teamId = UUID.randomUUID();
        CapacityPlan draft = inTransaction(() -> repo.create(
                new CreateCapacityPlanCommand(tenantId, teamId,
                        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                        100, actorId)));
        inTransaction(() -> repo.update(tenantId, draft.id(),
                new UpdateCapacityPlanCommand(100, 0, CapacityStatus.ACTIVE, actorId, 1)));

        // query September -> no match
        assertThat(repo.findActiveByTeamAndPeriod(
                tenantId, teamId, LocalDate.of(2026, 9, 15))).isEmpty();
    }

    // ── UPDATE ─────────────────────────────────────────────────────────────

    @Test
    void update_bumpsVersionAndAppliesChanges() {
        UUID teamId = UUID.randomUUID();
        CapacityPlan created = inTransaction(() -> repo.create(
                new CreateCapacityPlanCommand(tenantId, teamId,
                        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                        100, actorId)));

        Optional<CapacityPlan> updated = inTransaction(() -> repo.update(
                tenantId, created.id(), new UpdateCapacityPlanCommand(
                        200, 50, CapacityStatus.ACTIVE, actorId, 1)));

        assertThat(updated).isPresent();
        assertThat(updated.get().version()).isEqualTo(2);
        assertThat(updated.get().maxCapacity()).isEqualTo(200);
        assertThat(updated.get().allocatedCapacity()).isEqualTo(50);
        assertThat(updated.get().status()).isEqualTo(CapacityStatus.ACTIVE);
    }

    @Test
    void update_returnsEmpty_whenVersionConflict() {
        UUID teamId = UUID.randomUUID();
        CapacityPlan created = inTransaction(() -> repo.create(
                new CreateCapacityPlanCommand(tenantId, teamId,
                        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                        100, actorId)));

        // first update (v1 -> v2)
        inTransaction(() -> repo.update(tenantId, created.id(),
                new UpdateCapacityPlanCommand(150, 0, CapacityStatus.ACTIVE, actorId, 1)));

        // stale version
        Optional<CapacityPlan> conflict = inTransaction(() -> repo.update(
                tenantId, created.id(),
                new UpdateCapacityPlanCommand(200, 0, CapacityStatus.COMPLETED, actorId, 0)));
        assertThat(conflict).isEmpty();
    }

    // ── TENANT ISOLATION ───────────────────────────────────────────────────

    @Test
    void findById_isolatedByTenant() {
        UUID teamId = UUID.randomUUID();
        UUID otherTenant = newTenant();
        CapacityPlan saved = inTransaction(() -> repo.create(
                new CreateCapacityPlanCommand(tenantId, teamId,
                        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                        100, actorId)));

        assertThat(repo.findById(otherTenant, saved.id())).isEmpty();
        assertThat(repo.findById(tenantId, saved.id())).isPresent();
    }

    @Test
    void findByTeamId_isolatedByTenant() {
        UUID teamId = UUID.randomUUID();
        UUID otherTenant = newTenant();
        inTransaction(() -> repo.create(new CreateCapacityPlanCommand(
                tenantId, teamId, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                100, actorId)));

        assertThat(repo.findByTeamId(otherTenant, teamId)).isEmpty();
        assertThat(repo.findByTeamId(tenantId, teamId)).hasSize(1);
    }
}
