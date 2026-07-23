package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import com.sanad.platform.crm.ownership.domain.AssignmentRecordType;
import com.sanad.platform.crm.ownership.domain.AssignmentRule;
import com.sanad.platform.crm.ownership.domain.AssignmentRuleVersion;
import com.sanad.platform.crm.ownership.domain.ConcurrentRuleActivationConflictException;
import com.sanad.platform.crm.ownership.domain.DistributionMethod;
import com.sanad.platform.crm.ownership.domain.MembershipRole;
import com.sanad.platform.crm.ownership.domain.MembershipStatus;
import com.sanad.platform.crm.ownership.domain.PrimaryMembershipConflictException;
import com.sanad.platform.crm.ownership.domain.RuleStatus;
import com.sanad.platform.crm.ownership.domain.SalesTeam;
import com.sanad.platform.crm.ownership.domain.TeamMembership;
import com.sanad.platform.crm.ownership.domain.TeamStatus;
import com.sanad.platform.crm.ownership.domain.Territory;
import com.sanad.platform.crm.ownership.domain.TerritoryRuleType;
import com.sanad.platform.crm.ownership.domain.TerritoryStatus;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class OwnershipAdditionalConcurrencyPostgresTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static NamedParameterJdbcTemplate jdbc;
    private static TransactionTemplate transactions;
    private static JdbcSalesTeamRepository teams;
    private static JdbcTeamMembershipRepository memberships;
    private static JdbcAssignmentRuleRepository rules;
    private static JdbcTerritoryRepository territories;

    private UUID tenantId;
    private UUID actorId;

    @BeforeAll
    static void migrateAndCreateAdapters() {
        boolean dockerAvailable;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable ignored) {
            dockerAvailable = false;
        }
        Assumptions.assumeTrue(
                dockerAvailable,
                "Docker is required for CRM-008B additional concurrency acceptance.");

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .javaMigrations(new V15__seed_rbac_roles_and_capabilities())
                .cleanDisabled(false)
                .validateOnMigrate(true)
                .load()
                .migrate();

        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new NamedParameterJdbcTemplate(dataSource);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        teams = new JdbcSalesTeamRepository(jdbc);
        memberships = new JdbcTeamMembershipRepository(jdbc);
        rules = new JdbcAssignmentRuleRepository(jdbc);
        territories = new JdbcTerritoryRepository(jdbc);
    }

    @BeforeEach
    void createTenant() {
        tenantId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at)
                VALUES (:id, :name, :subdomain, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, new MapSqlParameterSource()
                .addValue("id", tenantId)
                .addValue("name", "CRM-008B Additional Concurrency")
                .addValue("subdomain", "crm008b-conc-" + tenantId.toString().substring(0, 8)));
    }

    @Test
    void concurrentPrimaryMembership_hasOneWinner() throws Exception {
        UUID firstTeam = createTeam("PRIMARY-A");
        UUID secondTeam = createTeam("PRIMARY-B");
        UUID userId = UUID.randomUUID();
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> savePrimary(barrier, firstTeam, userId));
            Future<Boolean> second = executor.submit(() -> savePrimary(barrier, secondTeam, userId));
            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
        }

        Long primaryCount = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM crm_team_memberships
                 WHERE tenant_id=:tenantId
                   AND user_id=:userId
                   AND status='ACTIVE'
                   AND is_primary=true
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("userId", userId), Long.class);
        assertThat(primaryCount).isEqualTo(1L);
    }

    @Test
    void concurrentRuleActivation_returnsDomainConflictAndOneActiveVersion() throws Exception {
        UUID ruleId = UUID.randomUUID();
        createRuleAndVersions(ruleId);

        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch allowFirstToContinue = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Void> first = executor.submit(() -> inTransaction(() -> {
                jdbc.queryForObject("""
                        SELECT current_version
                          FROM crm_assignment_rules
                         WHERE tenant_id=:tenantId
                           AND id=:ruleId
                         FOR UPDATE
                        """, new MapSqlParameterSource()
                        .addValue("tenantId", tenantId)
                        .addValue("ruleId", ruleId), Integer.class);
                lockAcquired.countDown();
                await(allowFirstToContinue);
                rules.activateVersion(tenantId, ruleId, 1, actorId);
                return null;
            }));

            lockAcquired.await();
            Future<Boolean> conflicting = executor.submit(() -> {
                try {
                    inTransaction(() -> {
                        rules.activateVersion(tenantId, ruleId, 2, actorId);
                        return null;
                    });
                    return false;
                } catch (ConcurrentRuleActivationConflictException expected) {
                    return true;
                }
            });

            assertThat(conflicting.get()).isTrue();
            allowFirstToContinue.countDown();
            first.get();
        } finally {
            allowFirstToContinue.countDown();
            executor.shutdownNow();
        }

        Integer activeCount = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM crm_assignment_rule_versions
                 WHERE tenant_id=:tenantId
                   AND rule_id=:ruleId
                   AND status='ACTIVE'
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("ruleId", ruleId), Integer.class);
        assertThat(activeCount).isEqualTo(1);
        assertThat(rules.findActiveVersion(tenantId, ruleId).orElseThrow().version())
                .isEqualTo(1);
    }

    @Test
    void concurrentTerritoryMoves_leaveClosureConsistent() throws Exception {
        UUID firstParent = createTerritory("PARENT-A", null);
        UUID secondParent = createTerritory("PARENT-B", null);
        UUID child = createTerritory("CHILD", firstParent);

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Void> first = executor.submit(() -> inTransaction(() -> {
                await(barrier);
                territories.updateParent(tenantId, child, firstParent, actorId);
                return null;
            }));
            Future<Void> second = executor.submit(() -> inTransaction(() -> {
                await(barrier);
                territories.updateParent(tenantId, child, secondParent, actorId);
                return null;
            }));
            first.get();
            second.get();
        } finally {
            executor.shutdownNow();
        }

        UUID finalParent = territories.findById(tenantId, child).orElseThrow().parentId();
        assertThat(finalParent).isIn(firstParent, secondParent);
        Integer selfRows = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM crm_territory_closure
                 WHERE tenant_id=:tenantId
                   AND descendant_id=:child
                   AND ancestor_id=:child
                   AND depth=0
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("child", child), Integer.class);
        Integer directParentRows = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM crm_territory_closure
                 WHERE tenant_id=:tenantId
                   AND descendant_id=:child
                   AND ancestor_id=:parent
                   AND depth=1
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("child", child)
                .addValue("parent", finalParent), Integer.class);
        Integer totalRows = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM crm_territory_closure
                 WHERE tenant_id=:tenantId
                   AND descendant_id=:child
                """, new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("child", child), Integer.class);
        assertThat(selfRows).isEqualTo(1);
        assertThat(directParentRows).isEqualTo(1);
        assertThat(totalRows).isEqualTo(2);
    }

    private boolean savePrimary(CyclicBarrier barrier, UUID teamId, UUID userId) {
        await(barrier);
        try {
            inTransaction(() -> memberships.save(new TeamMembership(
                    null, tenantId, teamId, userId,
                    MembershipRole.SALES_REPRESENTATIVE, true,
                    MembershipStatus.ACTIVE, Instant.now(), null, null,
                    50, "{}", null, null, actorId, actorId)));
            return true;
        } catch (PrimaryMembershipConflictException expected) {
            return false;
        }
    }

    private UUID createTeam(String codePrefix) {
        UUID id = UUID.randomUUID();
        inTransaction(() -> teams.save(new SalesTeam(
                id, tenantId, codePrefix + "-" + id.toString().substring(0, 6),
                codePrefix, null, TeamStatus.ACTIVE, actorId,
                null, null, null, null, actorId, actorId)));
        return id;
    }

    private void createRuleAndVersions(UUID ruleId) {
        inTransaction(() -> {
            rules.save(new AssignmentRule(
                    ruleId, tenantId, "RULE-" + ruleId.toString().substring(0, 6),
                    1, RuleStatus.ACTIVE, null, null, actorId, actorId));
            rules.saveVersion(version(ruleId, 1));
            rules.saveVersion(version(ruleId, 2));
            return null;
        });
    }

    private AssignmentRuleVersion version(UUID ruleId, int version) {
        return new AssignmentRuleVersion(
                null, tenantId, ruleId, version,
                "Version " + version, null, AssignmentRecordType.LEAD,
                version, "{}", DistributionMethod.DIRECT_OWNER,
                actorId, null, null, null,
                Instant.now(), null, RuleStatus.INACTIVE, actorId, null);
    }

    private UUID createTerritory(String prefix, UUID parentId) {
        UUID id = UUID.randomUUID();
        inTransaction(() -> territories.save(new Territory(
                id, tenantId, prefix + "-" + id.toString().substring(0, 6),
                prefix, parentId, null, TerritoryStatus.ACTIVE,
                TerritoryRuleType.GEOGRAPHIC, "{}", 1,
                null, null, actorId, actorId)));
        return id;
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while coordinating concurrency test", interrupted);
        }
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await();
        } catch (Exception coordinationFailure) {
            throw new IllegalStateException("Concurrency test coordination failed", coordinationFailure);
        }
    }

    private static <T> T inTransaction(Supplier<T> operation) {
        return transactions.execute(status -> operation.get());
    }
}
