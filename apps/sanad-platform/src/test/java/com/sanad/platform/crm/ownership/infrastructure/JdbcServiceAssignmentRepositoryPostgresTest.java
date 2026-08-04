package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.crm.ownership.domain.service.ServiceAssignment;
import com.sanad.platform.crm.ownership.domain.service.ServiceAssignmentRepository.CreateServiceAssignmentCommand;
import com.sanad.platform.crm.ownership.domain.service.ServiceAssignmentRepository.UpdateServiceAssignmentCommand;
import com.sanad.platform.crm.ownership.domain.service.ServiceAssignmentStatus;
import com.sanad.platform.crm.testsupport.CrmRepositoryPostgresTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testcontainers PostgreSQL integration tests for {@link JdbcServiceAssignmentRepository}.
 *
 * <p>Covers CRUD lifecycle, findByTeamId, findByServiceId, existsByTeamAndService,
 * delete, optimistic concurrency, and tenant isolation.
 */
class JdbcServiceAssignmentRepositoryPostgresTest extends CrmRepositoryPostgresTestBase {

    private JdbcServiceAssignmentRepository repo;
    private UUID tenantId;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        repo = new JdbcServiceAssignmentRepository(jdbc());
        tenantId = newTenant();
        actorId = UUID.randomUUID();
    }

    // ── CREATE ─────────────────────────────────────────────────────────────

    @Test
    void create_persistsAllFields() {
        UUID teamId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();

        ServiceAssignment saved = inTransaction(() -> repo.create(
                new CreateServiceAssignmentCommand(tenantId, teamId, serviceId, actorId)));

        assertThat(saved.id()).isNotNull();
        assertThat(saved.tenantId()).isEqualTo(tenantId);
        assertThat(saved.teamId()).isEqualTo(teamId);
        assertThat(saved.serviceId()).isEqualTo(serviceId);
        assertThat(saved.status()).isEqualTo(ServiceAssignmentStatus.ACTIVE);
        assertThat(saved.version()).isEqualTo(1);
    }

    @Test
    void createfindById_roundTrip() {
        UUID teamId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        ServiceAssignment saved = inTransaction(() -> repo.create(
                new CreateServiceAssignmentCommand(tenantId, teamId, serviceId, actorId)));

        Optional<ServiceAssignment> found = repo.findById(tenantId, saved.id());
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
        UUID teamA = UUID.randomUUID();
        UUID teamB = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();

        inTransaction(() -> repo.create(new CreateServiceAssignmentCommand(
                tenantId, teamA, serviceId, actorId)));
        inTransaction(() -> repo.create(new CreateServiceAssignmentCommand(
                tenantId, teamB, UUID.randomUUID(), actorId)));

        assertThat(repo.findByTeamId(tenantId, teamA)).hasSize(1);
        assertThat(repo.findByTeamId(tenantId, teamB)).hasSize(1);
    }

    @Test
    void findByServiceId_returnsAssignmentsForService() {
        UUID teamId = UUID.randomUUID();
        UUID serviceA = UUID.randomUUID();
        UUID serviceB = UUID.randomUUID();

        inTransaction(() -> repo.create(new CreateServiceAssignmentCommand(
                tenantId, teamId, serviceA, actorId)));
        inTransaction(() -> repo.create(new CreateServiceAssignmentCommand(
                tenantId, UUID.randomUUID(), serviceB, actorId)));

        assertThat(repo.findByServiceId(tenantId, serviceA)).hasSize(1);
        assertThat(repo.findByServiceId(tenantId, serviceB)).hasSize(1);
    }

    // ── UPDATE ─────────────────────────────────────────────────────────────

    @Test
    void update_bumpsVersionAndAppliesChanges() {
        UUID teamId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        ServiceAssignment created = inTransaction(() -> repo.create(
                new CreateServiceAssignmentCommand(tenantId, teamId, serviceId, actorId)));

        Optional<ServiceAssignment> updated = inTransaction(() -> repo.update(
                tenantId, created.id(), new UpdateServiceAssignmentCommand(
                        ServiceAssignmentStatus.INACTIVE, actorId, 0)));

        assertThat(updated).isPresent();
        assertThat(updated.get().version()).isEqualTo(2);
        assertThat(updated.get().status()).isEqualTo(ServiceAssignmentStatus.INACTIVE);
    }

    @Test
    void update_returnsEmpty_whenVersionConflict() {
        UUID teamId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        ServiceAssignment created = inTransaction(() -> repo.create(
                new CreateServiceAssignmentCommand(tenantId, teamId, serviceId, actorId)));

        // first update (v0 -> v1)
        inTransaction(() -> repo.update(tenantId, created.id(),
                new UpdateServiceAssignmentCommand(ServiceAssignmentStatus.INACTIVE, actorId, 0)));

        // stale version
        Optional<ServiceAssignment> conflict = inTransaction(() -> repo.update(
                tenantId, created.id(),
                new UpdateServiceAssignmentCommand(ServiceAssignmentStatus.ACTIVE, actorId, 0)));
        assertThat(conflict).isEmpty();
    }

    // ── DELETE ─────────────────────────────────────────────────────────────

    @Test
    void delete_removesRecord() {
        UUID teamId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        ServiceAssignment created = inTransaction(() -> repo.create(
                new CreateServiceAssignmentCommand(tenantId, teamId, serviceId, actorId)));

        assertThat(inTransaction(() -> repo.delete(tenantId, created.id()))).isTrue();
        assertThat(repo.findById(tenantId, created.id())).isEmpty();
    }

    @Test
    void delete_returnsFalse_whenAbsent() {
        assertThat(inTransaction(() -> repo.delete(tenantId, UUID.randomUUID()))).isFalse();
    }

    // ── EXISTS BY TEAM AND SERVICE ─────────────────────────────────────────

    @Test
    void existsByTeamAndService_trueWhenPresent() {
        UUID teamId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        inTransaction(() -> repo.create(new CreateServiceAssignmentCommand(
                tenantId, teamId, serviceId, actorId)));
        assertThat(repo.existsByTeamAndService(tenantId, teamId, serviceId, null)).isTrue();
    }

    @Test
    void existsByTeamAndService_falseWhenAbsent() {
        assertThat(repo.existsByTeamAndService(tenantId, UUID.randomUUID(),
                UUID.randomUUID(), null)).isFalse();
    }

    @Test
    void existsByTeamAndService_excludesSpecifiedId() {
        UUID teamId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        ServiceAssignment created = inTransaction(() -> repo.create(
                new CreateServiceAssignmentCommand(tenantId, teamId, serviceId, actorId)));
        // excludes itself -> false
        assertThat(repo.existsByTeamAndService(tenantId, teamId, serviceId, created.id())).isFalse();
    }

    // ── TENANT ISOLATION ───────────────────────────────────────────────────

    @Test
    void findById_isolatedByTenant() {
        UUID teamId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID otherTenant = newTenant();
        ServiceAssignment saved = inTransaction(() -> repo.create(
                new CreateServiceAssignmentCommand(tenantId, teamId, serviceId, actorId)));

        assertThat(repo.findById(otherTenant, saved.id())).isEmpty();
        assertThat(repo.findById(tenantId, saved.id())).isPresent();
    }

    @Test
    void existsByTeamAndService_isolatedByTenant() {
        UUID teamId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        inTransaction(() -> repo.create(new CreateServiceAssignmentCommand(
                tenantId, teamId, serviceId, actorId)));
        UUID otherTenant = newTenant();
        assertThat(repo.existsByTeamAndService(otherTenant, teamId, serviceId, null)).isFalse();
    }
}
