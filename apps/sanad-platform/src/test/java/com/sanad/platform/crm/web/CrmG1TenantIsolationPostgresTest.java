package com.sanad.platform.crm.web;

import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;

import java.util.UUID;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;

class CrmG1TenantIsolationPostgresTest {


    @BeforeAll
    static void requirePostgreSql() {
        boolean postgresAvailable;
        try {
            postgresAvailable = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip("testClassName");
        } catch (Throwable ignored) {
            postgresAvailable = false;
        }
        Assumptions.assumeTrue(postgresAvailable,
                "PostgreSQL Direct is not available — skipping CrmG1TenantIsolationPostgresTest. " +
                        "Run with PostgreSQL Direct to exercise PostgreSQL tenant isolation.");
    }

    @Test
    void rejectsCrossTenantContactLookupReferenceAndAcceptsSameTenantReference() {
        Flyway flyway = Flyway.configure()
                .dataSource(System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad"), System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"), System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""))
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
                System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad"), System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"), System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""));
        dataSource.setDriverClassName("org.postgresql.Driver");
        return new JdbcTemplate(dataSource);
    }

    private void insertTenant(JdbcTemplate jdbc, UUID id, String name, String subdomain) {
        jdbc.update("""
                INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, name, subdomain);
    }
}
