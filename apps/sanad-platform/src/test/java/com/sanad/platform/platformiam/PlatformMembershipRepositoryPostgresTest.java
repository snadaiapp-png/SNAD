package com.sanad.platform.platformiam;

import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.platformiam.domain.PlatformMembership;
import com.sanad.platform.platformiam.domain.PlatformMembershipStatus;
import com.sanad.platform.platformiam.domain.PlatformRoleMetadata;
import com.sanad.platform.platformiam.repository.JdbcPlatformMembershipRepository;
import com.sanad.platform.platformiam.repository.JdbcPlatformRoleMetadataRepository;
import com.sanad.platform.platformiam.repository.PlatformMembershipRepository;
import com.sanad.platform.platformiam.repository.PlatformRoleMetadataRepository;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Task 3 PostgreSQL Direct persistence acceptance for Platform IAM.
 * No Docker/Testcontainers/H2. FORCE-RLS tables are exercised with a
 * transaction-local app.tenant_id context, matching the established CRM
 * repository test pattern.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlatformMembershipRepositoryPostgresTest {

    private static final UUID CONTROL_TENANT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OWNER_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000010");

    private JdbcTemplate jdbc;
    private NamedParameterJdbcTemplate namedJdbc;
    private TransactionTemplate transactions;
    private PlatformMembershipRepository memberships;
    private PlatformRoleMetadataRepository roleMetadata;

    @BeforeAll
    void migrateAndCreateRepositories() {
        boolean available;
        try {
            available = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip("PlatformMembershipRepositoryPostgresTest");
        } catch (Throwable ignored) {
            available = false;
        }
        Assumptions.assumeTrue(available,
                "PostgreSQL Direct is not available — skipping PlatformMembershipRepositoryPostgresTest.");

        String baseUrl = System.getenv().getOrDefault(
                "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
        String username = System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad");
        String password = System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "");

        MigrationTestSchemaSupport.ensureDatabase(baseUrl, username, password);
        String isolatedUrl = MigrationTestSchemaSupport.getIsolatedJdbcUrl(baseUrl);

        Flyway flyway = Flyway.configure()
                .dataSource(isolatedUrl, username, password)
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .cleanDisabled(false)
                .validateOnMigrate(true)
                .load();
        flyway.clean();
        flyway.migrate();
        flyway.validate();

        DriverManagerDataSource dataSource = new DriverManagerDataSource(isolatedUrl, username, password);
        dataSource.setDriverClassName("org.postgresql.Driver");
        jdbc = new JdbcTemplate(dataSource);
        namedJdbc = new NamedParameterJdbcTemplate(dataSource);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        memberships = new JdbcPlatformMembershipRepository(namedJdbc);
        roleMetadata = new JdbcPlatformRoleMetadataRepository(namedJdbc);
    }

    @Test
    void membershipStatusValuesMatchApprovedLifecycleExactly() {
        assertThat(PlatformMembershipStatus.values())
                .extracting(Enum::name)
                .containsExactly("INVITED", "ACTIVE", "SUSPENDED", "LOCKED", "DISABLED");
    }

    @Test
    void createReadUpdateAndListMembershipsAreTenantScoped() {
        UUID userId = createUser(CONTROL_TENANT_ID, "task3-member");
        Instant now = Instant.parse("2026-09-26T18:00:00Z");
        PlatformMembership invited = membership(
                UUID.randomUUID(), CONTROL_TENANT_ID, userId, PlatformMembershipStatus.INVITED,
                now, null, "initial invitation", now);

        PlatformMembership created = inTenant(CONTROL_TENANT_ID, () -> memberships.save(invited));
        assertThat(created.id()).isEqualTo(invited.id());
        assertThat(created.status()).isEqualTo(PlatformMembershipStatus.INVITED);

        PlatformMembership found = inTenant(CONTROL_TENANT_ID,
                () -> memberships.findByControlTenantIdAndUserId(CONTROL_TENANT_ID, userId).orElseThrow());
        assertThat(found.userId()).isEqualTo(userId);
        assertThat(found.controlTenantId()).isEqualTo(CONTROL_TENANT_ID);

        Instant activatedAt = now.plusSeconds(60);
        PlatformMembership activated = new PlatformMembership(
                found.id(), found.controlTenantId(), found.userId(), PlatformMembershipStatus.ACTIVE,
                found.invitedAt(), activatedAt, null, null, null,
                found.createdBy(), OWNER_USER_ID, null, found.createdAt(), activatedAt);

        PlatformMembership updated = inTenant(CONTROL_TENANT_ID, () -> memberships.save(activated));
        assertThat(updated.status()).isEqualTo(PlatformMembershipStatus.ACTIVE);
        assertThat(updated.activatedAt()).isEqualTo(activatedAt);
        assertThat(updated.updatedBy()).isEqualTo(OWNER_USER_ID);

        List<PlatformMembership> listed = inTenant(CONTROL_TENANT_ID,
                () -> memberships.findByControlTenantId(CONTROL_TENANT_ID));
        assertThat(listed).extracting(PlatformMembership::userId).contains(userId);
    }

    @Test
    void uniqueMembershipConstraintRejectsSecondMembershipForSameControlTenantUser() {
        UUID userId = createUser(CONTROL_TENANT_ID, "task3-duplicate");
        Instant now = Instant.parse("2026-09-26T18:10:00Z");

        inTenant(CONTROL_TENANT_ID, () -> memberships.save(membership(
                UUID.randomUUID(), CONTROL_TENANT_ID, userId, PlatformMembershipStatus.INVITED,
                now, null, null, now)));

        PlatformMembership duplicate = membership(
                UUID.randomUUID(), CONTROL_TENANT_ID, userId, PlatformMembershipStatus.ACTIVE,
                now, now, null, now);

        assertThatThrownBy(() -> inTenant(CONTROL_TENANT_ID, () -> memberships.save(duplicate)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void compositeForeignKeyRejectsCrossTenantUserMembership() {
        UUID otherTenant = createTenant("task3-cross-tenant");
        UUID foreignUser = createUser(otherTenant, "task3-foreign-user");
        Instant now = Instant.parse("2026-09-26T18:20:00Z");

        PlatformMembership invalid = membership(
                UUID.randomUUID(), CONTROL_TENANT_ID, foreignUser, PlatformMembershipStatus.ACTIVE,
                now, now, "must fail", now);

        assertThatThrownBy(() -> inTenant(CONTROL_TENANT_ID, () -> memberships.save(invalid)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void roleMetadataCanBeReadAndUpdatedWithoutBecomingAuthorizationLogic() {
        UUID roleId = createRole(CONTROL_TENANT_ID, "TASK3_CUSTOM_ROLE");
        Instant now = Instant.parse("2026-09-26T18:30:00Z");
        PlatformRoleMetadata custom = new PlatformRoleMetadata(
                CONTROL_TENANT_ID, roleId, PlatformRoleMetadata.RoleType.CUSTOM,
                false, false, now, now);

        PlatformRoleMetadata created = inTenant(CONTROL_TENANT_ID, () -> roleMetadata.save(custom));
        assertThat(created.roleType()).isEqualTo(PlatformRoleMetadata.RoleType.CUSTOM);
        assertThat(created.protectedRole()).isFalse();
        assertThat(created.ownerRole()).isFalse();

        PlatformRoleMetadata changed = new PlatformRoleMetadata(
                CONTROL_TENANT_ID, roleId, PlatformRoleMetadata.RoleType.CUSTOM,
                true, false, created.createdAt(), now.plusSeconds(60));
        PlatformRoleMetadata updated = inTenant(CONTROL_TENANT_ID, () -> roleMetadata.save(changed));
        assertThat(updated.protectedRole()).isTrue();

        assertThat(inTenant(CONTROL_TENANT_ID,
                () -> roleMetadata.findByControlTenantIdAndRoleId(CONTROL_TENANT_ID, roleId)))
                .contains(updated);
    }

    @Test
    void rowLockQueryReturnsOnlyActiveMembershipsAssignedToRequestedRole() {
        List<PlatformMembership> owners = inTenant(CONTROL_TENANT_ID,
                () -> memberships.lockActiveMembershipsByRoleCode(CONTROL_TENANT_ID, "PLATFORM_OWNER"));

        assertThat(owners)
                .extracting(PlatformMembership::userId)
                .contains(OWNER_USER_ID);
        assertThat(owners).allMatch(m -> m.status() == PlatformMembershipStatus.ACTIVE);
    }

    private PlatformMembership membership(
            UUID id,
            UUID tenantId,
            UUID userId,
            PlatformMembershipStatus status,
            Instant invitedAt,
            Instant activatedAt,
            String reason,
            Instant now) {
        return new PlatformMembership(
                id, tenantId, userId, status,
                invitedAt, activatedAt, null, null, null,
                OWNER_USER_ID, OWNER_USER_ID, reason, now, now);
    }

    private UUID createTenant(String key) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update(
                "INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) VALUES (?,?,?,'ACTIVE',?,?)",
                id, key, key + "-" + id.toString().substring(0, 8), Timestamp.from(now), Timestamp.from(now));
        return id;
    }

    private UUID createUser(UUID tenantId, String key) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        inTenantVoid(tenantId, () -> jdbc.update(
                "INSERT INTO users (id,tenant_id,email,display_name,status,created_at,updated_at) " +
                        "VALUES (?,?,?,?,'ACTIVE',?,?)",
                id, tenantId, key + "+" + id + "@example.test", key,
                Timestamp.from(now), Timestamp.from(now)));
        return id;
    }

    private UUID createRole(UUID tenantId, String code) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        inTenantVoid(tenantId, () -> jdbc.update(
                "INSERT INTO roles (id,tenant_id,code,name,status,created_at,updated_at) " +
                        "VALUES (?,?,?,?, 'ACTIVE', ?, ?)",
                id, tenantId, code, code, Timestamp.from(now), Timestamp.from(now)));
        return id;
    }

    private <T> T inTenant(UUID tenantId, Supplier<T> work) {
        return transactions.execute(status -> {
            namedJdbc.queryForObject(
                    "SELECT set_config('app.tenant_id', :tenantId, true)",
                    new MapSqlParameterSource("tenantId", tenantId.toString()),
                    String.class);
            return work.get();
        });
    }

    private void inTenantVoid(UUID tenantId, Runnable work) {
        transactions.executeWithoutResult(status -> {
            namedJdbc.queryForObject(
                    "SELECT set_config('app.tenant_id', :tenantId, true)",
                    new MapSqlParameterSource("tenantId", tenantId.toString()),
                    String.class);
            work.run();
        });
    }
}
