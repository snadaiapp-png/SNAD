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

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class CrmAddressCommunicationMigrationUpgradeTest {
    private static final String PREVIOUS_VERSION = "20260717.3";

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
                "Docker is required for the CRM address and communication upgrade test.");
    }

    @Test
    void upgradesLegacyAddressesAndPrimaryCommunicationWithoutLoss() {
        Flyway previous = flyway(MigrationVersion.fromVersion(PREVIOUS_VERSION));
        previous.clean();
        previous.migrate();
        JdbcTemplate jdbc = jdbc();

        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.parse("2026-07-17T10:00:00Z");
        insertTenantAndUser(jdbc, tenantId, userId, "upgrade", now);
        jdbc.update(
                """
                INSERT INTO crm_accounts
                    (id,tenant_id,version,display_name,normalized_name,account_type,lifecycle_status,
                     primary_currency_code,preferred_locale,time_zone,source,owner_user_id,
                     primary_email,primary_phone,created_by,updated_by,created_at,updated_at)
                VALUES (?,?,?,?,?,'BUSINESS','ACTIVE','SAR','ar-SA','Asia/Riyadh','CRM006',?,?,?,?,?,?,?)
                """,
                accountId, tenantId, 0L, "شركة عربية", "شركة عربية", userId,
                "INFO@ARABIC.EXAMPLE", "+966501112233", userId, userId, now, now);
        jdbc.update(
                """
                INSERT INTO crm_contacts
                    (id,tenant_id,version,account_id,given_name,family_name,display_name,normalized_name,
                     primary_email,normalized_email,primary_phone,preferred_locale,time_zone,lifecycle_status,
                     owner_user_id,consent_summary,created_by,updated_by,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,'ACTIVE',?,'GRANTED',?,?,?,?)
                """,
                contactId, tenantId, 0L, accountId, "سارة", "العربية", "سارة العربية", "سارة العربية",
                "Sara@Arabic.Example", "sara@arabic.example", "+966551234567",
                "ar-SA", "Asia/Riyadh", userId, userId, userId, now, now);
        jdbc.update(
                """
                INSERT INTO crm_account_addresses
                    (id,tenant_id,account_id,version,address_type,label,line1,line2,city,state_region,
                     postal_code,country_code,primary_address,active,created_by,updated_by,created_at,updated_at)
                VALUES (?,?,?,0,'REGISTERED','المقر الرئيسي','٢٥ طريق الملك فهد',NULL,'الرياض','منطقة الرياض',
                        '١٢٣٤٥','SA',TRUE,TRUE,?,?,?,?)
                """,
                addressId, tenantId, accountId, userId, userId, now, now);

        Flyway upgrade = flyway(null);
        upgrade.migrate();
        upgrade.validate();

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_party_addresses WHERE tenant_id=?", Long.class, tenantId)).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT line1 FROM crm_party_addresses WHERE tenant_id=? AND id=?", String.class,
                tenantId, addressId)).isEqualTo("٢٥ طريق الملك فهد");
        assertThat(jdbc.queryForObject(
                "SELECT city FROM crm_party_addresses WHERE tenant_id=? AND id=?", String.class,
                tenantId, addressId)).isEqualTo("الرياض");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_account_addresses WHERE tenant_id=?", Long.class, tenantId)).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_communication_methods WHERE tenant_id=?", Long.class, tenantId)).isEqualTo(4L);
        assertThat(jdbc.queryForObject(
                """
                SELECT normalized_value FROM crm_communication_methods
                WHERE tenant_id=? AND owner_type='ACCOUNT' AND owner_id=? AND method_type='EMAIL'
                """, String.class, tenantId, accountId)).isEqualTo("info@arabic.example");
        assertThat(jdbc.queryForObject(
                """
                SELECT consent_state_reference FROM crm_communication_methods
                WHERE tenant_id=? AND owner_type='PERSON' AND owner_id=? AND method_type='EMAIL'
                """, String.class, tenantId, contactId)).isEqualTo("GRANTED");

        upgrade.migrate();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_party_addresses WHERE tenant_id=?", Long.class, tenantId)).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_communication_methods WHERE tenant_id=?", Long.class, tenantId)).isEqualTo(4L);
    }

    @Test
    void databaseRejectsCrossTenantOwnerLinking() {
        Flyway flyway = flyway(null);
        flyway.clean();
        flyway.migrate();
        JdbcTemplate jdbc = jdbc();

        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        UUID accountB = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.parse("2026-07-17T10:00:00Z");
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
                accountB, tenantB, 0L, "Tenant B Account", "tenant b account",
                userB, userB, userB, now, now);

        assertThatThrownBy(() -> jdbc.update(
                """
                INSERT INTO crm_party_addresses
                    (id,tenant_id,version,owner_type,owner_id,account_id,contact_id,address_type,
                     line1,city,country_code,primary_address,primary_slot,verified,status,
                     created_by,updated_by,created_at,updated_at)
                VALUES (?, ?, 0, 'ACCOUNT', ?, ?, NULL, 'OFFICE',
                        'Cross Tenant', 'Riyadh', 'SA', FALSE, NULL, FALSE, 'ACTIVE', ?, ?, ?, ?)
                """,
                UUID.randomUUID(), tenantA, accountB, accountB, userA, userA, now, now))
                .hasMessageContaining("fk_crm_party_addresses_account");
    }

    private static void insertTenantAndUser(
            JdbcTemplate jdbc, UUID tenantId, UUID userId, String suffix, OffsetDateTime now) {
        jdbc.update(
                "INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) VALUES (?,?,?,?,?,?)",
                tenantId, "Tenant " + suffix,
                "tenant-" + suffix + "-" + tenantId.toString().substring(0, 8),
                "ACTIVE", now, now);
        jdbc.update(
                """
                INSERT INTO users
                    (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,?)
                """,
                userId, tenantId, suffix + "-crm007@example.test", "User " + suffix,
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
