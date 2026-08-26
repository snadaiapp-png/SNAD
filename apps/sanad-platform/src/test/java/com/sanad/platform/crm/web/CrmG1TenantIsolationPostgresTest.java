package com.sanad.platform.crm.web;

import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
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
        // Ensure the disposable test_migration database exists so that flyway.clean()
        // below only affects this isolated database (not the shared sanad database
        // that other @SpringBootTest contexts depend on).
        MigrationTestSchemaSupport.ensureDatabase(
                System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""));
    }

    @Test
    void rejectsCrossTenantContactLookupReferenceAndAcceptsSameTenantReference() {
        Flyway flyway = Flyway.configure()
                .dataSource(MigrationTestSchemaSupport.getIsolatedJdbcUrl(System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad")), System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"), System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""))
                .locations("classpath:db/migration")
                .javaMigrations(new V15__seed_rbac_roles_and_capabilities())
                .cleanDisabled(false)
                .validateOnMigrate(true)
                .load();
        flyway.clean();
        flyway.migrate();
        flyway.validate();

        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                MigrationTestSchemaSupport.getIsolatedJdbcUrl(System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad")),
                System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""));
        dataSource.setDriverClassName("org.postgresql.Driver");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        TransactionTemplate transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        UUID accountA = UUID.randomUUID();
        UUID contactA = UUID.randomUUID();

        insertTenant(jdbc, tenantA, "Tenant A", "g1-a-" + tenantA);
        insertTenant(jdbc, tenantB, "Tenant B", "g1-b-" + tenantB);

        // Set tenant GUC + INSERT crm_accounts + crm_contacts inside a single
        // tenant-scoped transaction so set_config('app.tenant_id', tenantA, true)
        // (transaction-local) actually persists on the same Connection used by
        // the INSERTs. Outside an explicit transaction, set_config(...,true) is
        // a no-op on autoCommit, leaving the GUC unset and triggering FORCE RLS
        // rejection on crm_contacts (V20260823_1).
        transactions.executeWithoutResult(s -> {
            jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)", String.class, tenantA.toString());
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
        });

        assertThatThrownBy(() -> transactions.executeWithoutResult(s2 -> {
            // Cross-tenant INSERT attempt: tenantB lookup pointing at tenantA's
            // contact — must be rejected by the composite-key FK constraint.
            jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)", String.class, tenantB.toString());
            jdbc.update("""
                    INSERT INTO crm_contact_lookup_index (
                        id, tenant_id, contact_id, version, normalized_phone,
                        normalized_email, normalized_name, searchable_text,
                        source_updated_at, active, created_at, updated_at
                    ) VALUES (?, ?, ?, 0, '+966500000001', 'a@example.test',
                              'contact a', 'contact a a@example.test +966500000001',
                              CURRENT_TIMESTAMP, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, UUID.randomUUID(), tenantB, contactA);
        }))
                // TransactionTemplate.executeWithoutResult rethrows the original
                // DataAccessException (RuntimeException subclass) after rolling back
                // the transaction. The cross-tenant FK violation surfaces as a
                // DataIntegrityViolationException.
                .isInstanceOf(DataIntegrityViolationException.class);

        // Same-tenant INSERT must succeed — wrap in its own tenant-scoped
        // transaction so the GUC is set on the same Connection used by the INSERT
        // and the subsequent verification SELECT.
        UUID sameTenantLookupId = UUID.randomUUID();
        transactions.executeWithoutResult(s -> {
            jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)", String.class, tenantA.toString());
            jdbc.update("""
                    INSERT INTO crm_contact_lookup_index (
                        id, tenant_id, contact_id, version, normalized_phone,
                        normalized_email, normalized_name, searchable_text,
                        source_updated_at, active, created_at, updated_at
                    ) VALUES (?, ?, ?, 0, '+966500000001', 'a@example.test',
                              'contact a', 'contact a a@example.test +966500000001',
                              CURRENT_TIMESTAMP, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, sameTenantLookupId, tenantA, contactA);
        });

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_contact_lookup_index WHERE id=? AND tenant_id=? AND contact_id=?",
                Long.class, sameTenantLookupId, tenantA, contactA)).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_contact_lookup_index WHERE tenant_id=? AND contact_id=?",
                Long.class, tenantB, contactA)).isZero();
    }

    private void insertTenant(JdbcTemplate jdbc, UUID id, String name, String subdomain) {
        jdbc.update("""
                INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, name, subdomain);
    }
}
