package com.sanad.platform.crm.web;

import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class CrmContactRelationshipMigrationUpgradeTest {

    private static final String PREVIOUS_VERSION = "20260716.4";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll
    static void requireDocker() {
        boolean dockerAvailable;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable ignored) {
            dockerAvailable = false;
        }
        Assumptions.assumeTrue(dockerAvailable,
                "Docker is required for the CRM contact relationship upgrade test.");
    }

    @Test
    void upgradesLegacyAccountIdWithoutLossOrDuplicates() {
        Flyway previous = flyway(MigrationVersion.fromVersion(PREVIOUS_VERSION));
        previous.clean();
        previous.migrate();
        JdbcTemplate jdbc = jdbc();

        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID linkedContactId = UUID.randomUUID();
        UUID standaloneContactId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-17T00:00:00Z");

        jdbc.update(
                "INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) VALUES (?,?,?,?,?,?)",
                tenantId, "Migration Tenant", "migration-" + tenantId.toString().substring(0, 8),
                "ACTIVE", now, now);
        jdbc.update(
                """
                INSERT INTO users
                    (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,?)
                """,
                userId, tenantId, "migration@example.test", "Migration User",
                "ACTIVE", "dummy", now, now);
        jdbc.update(
                """
                INSERT INTO crm_accounts
                    (id,tenant_id,version,display_name,normalized_name,account_type,lifecycle_status,
                     primary_currency_code,preferred_locale,time_zone,owner_user_id,
                     created_by,updated_by,created_at,updated_at)
                VALUES (?,?,?,?,?,'BUSINESS','ACTIVE','SAR','ar-SA','Asia/Riyadh',?,?,?,?,?)
                """,
                accountId, tenantId, 0L, "Legacy Account", "legacy account",
                userId, userId, userId, now, now);
        insertLegacyContact(jdbc, linkedContactId, tenantId, accountId, userId,
                "Linked", "Person", "linked@example.test", now);
        insertLegacyContact(jdbc, standaloneContactId, tenantId, null, userId,
                "Standalone", "Person", "standalone@example.test", now);

        Flyway upgrade = flyway(null);
        upgrade.migrate();
        upgrade.validate();

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_contacts WHERE tenant_id=?",
                Long.class, tenantId)).isEqualTo(2L);
        assertThat(jdbc.queryForObject(
                "SELECT account_id FROM crm_contacts WHERE tenant_id=? AND id=?",
                UUID.class, tenantId, linkedContactId)).isEqualTo(accountId);
        assertThat(jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM crm_contact_account_relationships
                WHERE tenant_id=? AND contact_id=? AND account_id=?
                  AND role_key='LEGACY_ACCOUNT' AND primary_relationship=TRUE
                """,
                Long.class, tenantId, linkedContactId, accountId)).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_contact_account_relationships WHERE tenant_id=? AND contact_id=?",
                Long.class, tenantId, standaloneContactId)).isZero();
        assertThat(jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM crm_contact_relationship_history
                WHERE tenant_id=? AND relationship_id=? AND event_type='MIGRATED'
                """,
                Long.class, tenantId, linkedContactId)).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT legal_name FROM crm_contacts WHERE tenant_id=? AND id=?",
                String.class, tenantId, linkedContactId)).isEqualTo("Linked Person");

        // A second Flyway invocation must not duplicate the deterministic backfill.
        upgrade.migrate();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_contact_account_relationships WHERE tenant_id=? AND contact_id=?",
                Long.class, tenantId, linkedContactId)).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_contact_relationship_history WHERE tenant_id=? AND relationship_id=?",
                Long.class, tenantId, linkedContactId)).isOne();
    }

    @Test
    void databaseCompositeKeysRejectCrossTenantLinking() {
        Flyway flyway = flyway(null);
        flyway.clean();
        flyway.migrate();
        JdbcTemplate jdbc = jdbc();

        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        UUID contactA = UUID.randomUUID();
        UUID accountB = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-17T00:00:00Z");

        insertTenantAndUser(jdbc, tenantA, userA, "a", now);
        insertTenantAndUser(jdbc, tenantB, userB, "b", now);
        jdbc.update(
                """
                INSERT INTO crm_accounts
                    (id,tenant_id,version,display_name,normalized_name,account_type,lifecycle_status,
                     primary_currency_code,preferred_locale,time_zone,owner_user_id,
                     created_by,updated_by,created_at,updated_at)
                VALUES (?,?,?,?,?,'BUSINESS','ACTIVE','SAR','ar-SA','Asia/Riyadh',?,?,?,?,?)
                """,
                accountB, tenantB, 0L, "Tenant B", "tenant b",
                userB, userB, userB, now, now);
        insertLegacyContact(jdbc, contactA, tenantA, null, userA,
                "Tenant", "A", "tenant-a@example.test", now);

        assertThatThrownBy(() -> jdbc.update(
                """
                INSERT INTO crm_contact_account_relationships
                    (id,tenant_id,contact_id,account_id,version,role_code,role_key,status,
                     primary_relationship,decision_authority,created_by,updated_by,created_at,updated_at)
                VALUES (?,?,?,?,0,'EMPLOYEE','EMPLOYEE','ACTIVE',FALSE,'NONE',?,?,?,?)
                """,
                UUID.randomUUID(), tenantA, contactA, accountB,
                userA, userA, now, now))
                .hasMessageContaining("fk_crm_contact_relationship_account_same_tenant");
    }

    private static void insertLegacyContact(
            JdbcTemplate jdbc,
            UUID contactId,
            UUID tenantId,
            UUID accountId,
            UUID userId,
            String givenName,
            String familyName,
            String email,
            Instant now) {
        String displayName = givenName + " " + familyName;
        jdbc.update(
                """
                INSERT INTO crm_contacts
                    (id,tenant_id,version,account_id,given_name,family_name,display_name,normalized_name,
                     primary_email,normalized_email,preferred_locale,time_zone,lifecycle_status,
                     owner_user_id,created_by,updated_by,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                contactId, tenantId, 0L, accountId, givenName, familyName, displayName,
                displayName.toLowerCase(), email, email.toLowerCase(), "ar-SA", "Asia/Riyadh",
                "ACTIVE", userId, userId, userId, now, now);
    }

    private static void insertTenantAndUser(
            JdbcTemplate jdbc,
            UUID tenantId,
            UUID userId,
            String suffix,
            Instant now) {
        jdbc.update(
                "INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) VALUES (?,?,?,?,?,?)",
                tenantId, "Tenant " + suffix, "tenant-" + suffix + "-" + tenantId.toString().substring(0, 8),
                "ACTIVE", now, now);
        jdbc.update(
                """
                INSERT INTO users
                    (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,?)
                """,
                userId, tenantId, suffix + "-migration@example.test", "User " + suffix,
                "ACTIVE", "dummy", now, now);
    }

    private Flyway flyway(MigrationVersion target) {
        var configuration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .javaMigrations(new V15__seed_rbac_roles_and_capabilities())
                .cleanDisabled(false)
                .validateOnMigrate(true);
        if (target != null) configuration.target(target);
        return configuration.load();
    }

    private JdbcTemplate jdbc() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        dataSource.setDriverClassName(POSTGRES.getDriverClassName());
        return new JdbcTemplate(dataSource);
    }
}
