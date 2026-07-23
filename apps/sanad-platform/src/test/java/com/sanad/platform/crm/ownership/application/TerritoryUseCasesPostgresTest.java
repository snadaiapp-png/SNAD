package com.sanad.platform.crm.ownership.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import com.sanad.platform.crm.integration.domain.AuditPort;
import com.sanad.platform.crm.integration.domain.TimelineEventPort;
import com.sanad.platform.crm.ownership.domain.*;
import com.sanad.platform.crm.ownership.infrastructure.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class TerritoryUseCasesPostgresTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static NamedParameterJdbcTemplate jdbc;
    private static TransactionTemplate transactions;
    private static TerritoryUseCases useCases;
    private static JdbcTerritoryRepository territoryRepository;
    private static JdbcTerritoryAssignmentRepository assignmentRepository;
    private static JdbcSalesTeamRepository teamRepository;

    private UUID tenantId;
    private UUID actorId;
    private UUID userId;

    @BeforeAll
    static void setup() {
        boolean docker;
        try { docker = DockerClientFactory.instance().isDockerAvailable(); }
        catch (Throwable ignored) { docker = false; }
        Assumptions.assumeTrue(docker, "Docker required for WP-05 PostgreSQL acceptance");

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .javaMigrations(new V15__seed_rbac_roles_and_capabilities())
                .cleanDisabled(false).validateOnMigrate(true).load().migrate();

        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new NamedParameterJdbcTemplate(dataSource);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        territoryRepository = new JdbcTerritoryRepository(jdbc);
        assignmentRepository = new JdbcTerritoryAssignmentRepository(jdbc);
        teamRepository = new JdbcSalesTeamRepository(jdbc);
        AuditPort audit = (tenant, actor, action, type, id, change, time) -> {};
        TimelineEventPort timeline = (tenant, subjectType, subjectId, eventType, summary,
                                      sourceType, sourceId, actor, occurredAt) -> {};
        useCases = new TerritoryUseCases(
                territoryRepository, assignmentRepository, teamRepository,
                new JdbcOwnershipUserValidationAdapter(jdbc), audit, timeline, new ObjectMapper());
    }

    @BeforeEach
    void seed() {
        tenantId = createTenant("territory");
        actorId = createUser(tenantId, "actor");
        userId = createUser(tenantId, "member");
    }

    @Test
    void hierarchyLifecycleMoveCycleAndArchive_areGoverned() {
        Territory root = tx(() -> create("ROOT", null, 10));
        Territory child = tx(() -> create("CHILD", root.id(), 20));
        Territory grandchild = tx(() -> create("GRAND", child.id(), 30));

        assertThat(territoryRepository.findAncestors(tenantId, grandchild.id()))
                .extracting(Territory::id).containsExactly(root.id(), child.id());
        assertThat(territoryRepository.findDescendants(tenantId, root.id()))
                .extracting(Territory::id).contains(child.id(), grandchild.id());

        Territory updated = tx(() -> useCases.update(
                tenantId, actorId, child.id(),
                new TerritoryUseCases.UpdateCommand(
                        "Child Updated", false, null, false, null,
                        TerritoryRuleType.SEGMENT, true, "{\"segment\":\"enterprise\"}", 55)));
        assertThat(updated.displayName()).isEqualTo("Child Updated");
        assertThat(updated.priority()).isEqualTo(55);
        assertThat(updated.ruleType()).isEqualTo(TerritoryRuleType.SEGMENT);

        Territory moved = tx(() -> useCases.move(tenantId, actorId, grandchild.id(), root.id()));
        assertThat(moved.parentId()).isEqualTo(root.id());
        assertThat(territoryRepository.findAncestors(tenantId, grandchild.id()))
                .extracting(Territory::id).containsExactly(root.id());

        assertThatThrownBy(() -> tx(() -> useCases.move(
                tenantId, actorId, root.id(), child.id())))
                .isInstanceOf(TerritoryCycleException.class);
        assertThatThrownBy(() -> tx(() -> useCases.move(
                tenantId, actorId, child.id(), child.id())))
                .isInstanceOf(TerritoryCycleException.class);

        assertThatThrownBy(() -> tx(() -> useCases.archive(tenantId, actorId, root.id())))
                .isInstanceOf(OwnershipDomainException.class)
                .hasMessageContaining("child territories");

        tx(() -> useCases.archive(tenantId, actorId, child.id()));
        tx(() -> useCases.archive(tenantId, actorId, grandchild.id()));
        Territory archivedRoot = tx(() -> useCases.archive(tenantId, actorId, root.id()));
        assertThat(archivedRoot.status()).isEqualTo(TerritoryStatus.ARCHIVED);
        assertThatThrownBy(() -> tx(() -> useCases.update(
                tenantId, actorId, root.id(),
                new TerritoryUseCases.UpdateCommand(
                        "No", false, null, false, null, null, false, null, null))))
                .isInstanceOf(OwnershipDomainException.class)
                .hasMessageContaining("immutable");
    }

    @Test
    void assignmentsResolveHighestPriorityAndEqualAmbiguityFailsClosed() {
        Territory first = tx(() -> create("FIRST", null, 10));
        Territory second = tx(() -> create("SECOND", null, 20));
        SalesTeam team = tx(() -> teamRepository.save(new SalesTeam(
                null, tenantId, "TEAM-" + shortId(), "Enterprise Team", null,
                TeamStatus.ACTIVE, actorId, null, null, null, null, actorId, actorId)));

        TerritoryAssignment userAssignment = tx(() -> useCases.assign(
                tenantId, actorId, first.id(),
                new TerritoryUseCases.AssignCommand(
                        AssigneeType.USER, userId, TerritoryAssignmentRole.PRIMARY,
                        100, null, null)));
        TerritoryAssignment teamAssignment = tx(() -> useCases.assign(
                tenantId, actorId, second.id(),
                new TerritoryUseCases.AssignCommand(
                        AssigneeType.TEAM, team.id(), TerritoryAssignmentRole.PRIMARY,
                        200, null, null)));

        assertThat(useCases.resolve(tenantId, List.of(first.id(), second.id())))
                .contains(teamAssignment);

        TerritoryAssignment equalUser = tx(() -> useCases.assign(
                tenantId, actorId, first.id(),
                new TerritoryUseCases.AssignCommand(
                        AssigneeType.USER, userId, TerritoryAssignmentRole.BACKUP,
                        200, null, null)));
        assertThat(equalUser.priority()).isEqualTo(200);
        assertThatThrownBy(() -> useCases.resolve(tenantId, List.of(first.id(), second.id())))
                .isInstanceOf(TerritoryAssignmentAmbiguityException.class);

        tx(() -> useCases.deactivate(tenantId, actorId, first.id(), equalUser.id()));
        assertThat(useCases.resolve(tenantId, List.of(first.id(), second.id())))
                .contains(teamAssignment);

        assertThatThrownBy(() -> tx(() -> useCases.archive(tenantId, actorId, first.id())))
                .isInstanceOf(OwnershipDomainException.class)
                .hasMessageContaining("assignments");
        tx(() -> useCases.deactivate(tenantId, actorId, first.id(), userAssignment.id()));
        assertThat(tx(() -> useCases.archive(tenantId, actorId, first.id())).status())
                .isEqualTo(TerritoryStatus.ARCHIVED);
    }

    @Test
    void assigneesAndParentsAreTenantScopedAndRulesAreJsonObjects() {
        Territory root = tx(() -> create("TENANT", null, 10));
        UUID otherTenant = createTenant("other");
        UUID otherUser = createUser(otherTenant, "other");

        assertThatThrownBy(() -> tx(() -> useCases.assign(
                tenantId, actorId, root.id(),
                new TerritoryUseCases.AssignCommand(
                        AssigneeType.USER, otherUser, TerritoryAssignmentRole.PRIMARY,
                        1, null, null))))
                .isInstanceOf(OwnershipDomainException.class)
                .hasMessageContaining("same tenant");

        Territory otherTerritory = tx(() -> new TerritoryUseCases(
                territoryRepository, assignmentRepository, teamRepository,
                new JdbcOwnershipUserValidationAdapter(jdbc),
                (t,a,ac,ty,id,ch,at) -> {},
                (t,st,sid,et,s,src,rid,a,at) -> {},
                new ObjectMapper()).create(
                        otherTenant, otherUser,
                        new TerritoryUseCases.CreateCommand(
                                "OTHER", "Other", null, null,
                                TerritoryRuleType.GEOGRAPHIC, "{}", 1)));
        assertThatThrownBy(() -> tx(() -> useCases.move(
                tenantId, actorId, root.id(), otherTerritory.id())))
                .isInstanceOf(TerritoryNotFoundException.class);
        assertThatThrownBy(() -> tx(() -> useCases.create(
                tenantId, actorId,
                new TerritoryUseCases.CreateCommand(
                        "BAD-" + shortId(), "Bad", null, null,
                        TerritoryRuleType.GEOGRAPHIC, "[]", 1))))
                .isInstanceOf(OwnershipDomainException.class)
                .hasMessageContaining("JSON object");
    }

    private Territory create(String prefix, UUID parent, int priority) {
        return useCases.create(
                tenantId, actorId,
                new TerritoryUseCases.CreateCommand(
                        prefix + "-" + shortId(), prefix + " Territory", parent, null,
                        TerritoryRuleType.GEOGRAPHIC, "{\"country\":\"SA\"}", priority));
    }

    private UUID createTenant(String prefix) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at)
                VALUES (:id, :name, :subdomain, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, new MapSqlParameterSource()
                .addValue("id", id).addValue("name", prefix)
                .addValue("subdomain", prefix + "-" + shortId()));
        return id;
    }

    private UUID createUser(UUID tenant, String prefix) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users
                  (id, tenant_id, email, display_name, status, password_hash, created_at, updated_at)
                VALUES (:id, :tenantId, :email, :name, 'ACTIVE', 'dummy', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, new MapSqlParameterSource()
                .addValue("id", id).addValue("tenantId", tenant)
                .addValue("email", prefix + "-" + shortId() + "@test.example")
                .addValue("name", prefix));
        return id;
    }

    private <T> T tx(java.util.concurrent.Callable<T> callable) {
        return transactions.execute(status -> {
            try { return callable.call(); }
            catch (RuntimeException runtime) { throw runtime; }
            catch (Exception checked) { throw new IllegalStateException(checked); }
        });
    }

    private String shortId() { return UUID.randomUUID().toString().substring(0, 8); }
}
