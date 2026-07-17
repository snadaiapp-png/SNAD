package com.sanad.platform.crm.web;

import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class CrmG1TenantIsolationPostgresTest {

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
                "Docker is not available — skipping CrmG1TenantIsolationPostgresTest. " +
                        "Run on a CI runner with Docker to exercise PostgreSQL tenant isolation.");
    }

    @Test
    void rejectsCrossTenantContactLookupReferenceAndAcceptsSameTenantReference() {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .javaMigrations(new V15__seed_rbac_roles_and_capabilities())
                .cleanDisabled(false)
                .validateOnMigrate(true)
                .load();
        flyway.clean();
        flyway.migrate();
        flyway.validate();

        JdbcTemplate jdbc = jdbc();
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        UUID accountA = UUID.randomUUID();
        UUID contactA = UUID.randomUUID();

        insertTenant(jdbc, tenantA, "Tenant A", "g1-a-" + tenantA);
        insertTenant(jdbc, tenantB, "Tenant B", "g1-b-" + tenantB);

        jdbc.update("""
                INSERT INTO crm_accounts (
                    id, tenant_id, version, display_name, normalized_name, account_type,
                    lifecycle_status, created_by, updated_by, created_at, updated_at
                ) VALUES (?, ?, 0, ?, ?, 'BUSINESS', 'ACTIVE', ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, accountA, tenantA, "Account A", "account a", actor, actor);

        jdbc.update("""
                INSERT INTO crm_contacts (
                    id, tenant_id, version, account_id, given_name, display_name,
                    normalized_name, lifecycle_status, consent_summary,
                    created_by, updated_by, created_at, updated_at
                ) VALUES (?, ?, 0, ?, 'Contact', 'Contact A', 'contact a',
                          'ACTIVE', 'UNKNOWN', ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, contactA, tenantA, accountA, actor, actor);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO crm_contact_lookup_index (
                    id, tenant_id, contact_id, version, normalized_phone,
                    normalized_email, normalized_name, searchable_text,
                    source_updated_at, active, created_at, updated_at
                ) VALUES (?, ?, ?, 0, '+966500000001', 'a@example.test',
                          'contact a', 'contact a a@example.test +966500000001',
                          CURRENT_TIMESTAMP, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, UUID.randomUUID(), tenantB, contactA))
                .isInstanceOf(DataIntegrityViolationException.class);

        UUID sameTenantLookupId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO crm_contact_lookup_index (
                    id, tenant_id, contact_id, version, normalized_phone,
                    normalized_email, normalized_name, searchable_text,
                    source_updated_at, active, created_at, updated_at
                ) VALUES (?, ?, ?, 0, '+966500000001', 'a@example.test',
                          'contact a', 'contact a a@example.test +966500000001',
                          CURRENT_TIMESTAMP, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, sameTenantLookupId, tenantA, contactA);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_contact_lookup_index WHERE id=? AND tenant_id=? AND contact_id=?",
                Long.class, sameTenantLookupId, tenantA, contactA)).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_contact_lookup_index WHERE tenant_id=? AND contact_id=?",
                Long.class, tenantB, contactA)).isZero();
    }

    private JdbcTemplate jdbc() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        dataSource.setDriverClassName(POSTGRES.getDriverClassName());
        return new JdbcTemplate(dataSource);
    }

    private void insertTenant(JdbcTemplate jdbc, UUID id, String name, String subdomain) {
        jdbc.update("""
                INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, name, subdomain);
    }
}
