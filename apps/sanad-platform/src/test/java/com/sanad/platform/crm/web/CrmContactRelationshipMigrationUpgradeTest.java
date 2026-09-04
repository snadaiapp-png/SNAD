package com.sanad.platform.crm.web;

import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;

import java.time.OffsetDateTime;
import java.util.UUID;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;

class CrmContactRelationshipMigrationUpgradeTest {

    private static final String PREVIOUS_VERSION = "20260716.4";


    @BeforeAll
    static void requirePostgreSql() {
        boolean postgresAvailable;
        try {
            postgresAvailable = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip("testClassName");
        } catch (Throwable ignored) {
            postgresAvailable = false;
        }
        Assumptions.assumeTrue(postgresAvailable,
                "PostgreSQL Direct is required for the CRM contact relationship upgrade test.");
        // Ensure the disposable test_migration database exists so that flyway.clean()
        // below only affects this isolated database (not the shared sanad database
        // that other @SpringBootTest contexts depend on).
        MigrationTestSchemaSupport.ensureDatabase(
                System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""));
    }

    @Test
    void upgradesLegacyAccountIdWithoutLossOrDuplicates() {
        Flyway previous = flyway(MigrationVersion.fromVersion(PREVIOUS_VERSION));
        previous.clean();
        previous.migrate();
        DriverManagerDataSource dataSource = dataSource();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        TransactionTemplate transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID linkedContactId = UUID.randomUUID();
        UUID standaloneContactId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.parse("2026-07-17T00:00:00Z");

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
        // Set tenant GUC + INSERT crm_accounts + crm_contacts inside a single
        // tenant-scoped transaction so set_config('app.tenant_id', tenantId, true)
        // (transaction-local) actually persists on the same Connection used by
        // the INSERTs. Outside an explicit transaction, set_config(...,true) is
        // a no-op on autoCommit, leaving the GUC unset and triggering FORCE RLS
        // rejection on crm_contacts (V20260823_1).
        transactions.executeWithoutResult(s -> {
            jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)", String.class, tenantId.toString());
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
        });

        Flyway upgrade = flyway(null);
        upgrade.migrate();
        upgrade.validate();

        // Verification SELECTs query crm_contacts (FORCE RLS under V20260823_1).
        // Wrap each in its own tenant-scoped transaction so the GUC is set on
        // the same Connection used by the SELECT — otherwise RLS hides every
        // row and the count returns 0.
        Long contactCount = transactions.execute(s -> {
            jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)", String.class, tenantId.toString());
            return jdbc.queryForObject(
                    "SELECT COUNT(*) FROM crm_contacts WHERE tenant_id=?",
                    Long.class, tenantId);
        });
        assertThat(contactCount).isEqualTo(2L);
        UUID linkedAccountId = transactions.execute(s -> {
            jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)", String.class, tenantId.toString());
            return jdbc.queryForObject(
                    "SELECT account_id FROM crm_contacts WHERE tenant_id=? AND id=?",
                    UUID.class, tenantId, linkedContactId);
        });
        assertThat(linkedAccountId).isEqualTo(accountId);
        Long legacyRelCount = transactions.execute(s -> {
            jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)", String.class, tenantId.toString());
            return jdbc.queryForObject(
                    """
                    SELECT COUNT(*) FROM crm_contact_account_relationships
                    WHERE tenant_id=? AND contact_id=? AND account_id=?
                      AND role_key='LEGACY_ACCOUNT' AND primary_relationship=TRUE
                    """,
                    Long.class, tenantId, linkedContactId, accountId);
        });
        assertThat(legacyRelCount).isOne();
        Long standaloneRelCount = transactions.execute(s -> {
            jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)", String.class, tenantId.toString());
            return jdbc.queryForObject(
                    "SELECT COUNT(*) FROM crm_contact_account_relationships WHERE tenant_id=? AND contact_id=?",
                    Long.class, tenantId, standaloneContactId);
        });
        assertThat(standaloneRelCount).isZero();
        Long migratedHistoryCount = transactions.execute(s -> {
            jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)", String.class, tenantId.toString());
            return jdbc.queryForObject(
                    """
                    SELECT COUNT(*) FROM crm_contact_relationship_history
                    WHERE tenant_id=? AND relationship_id=? AND event_type='MIGRATED'
                    """,
                    Long.class, tenantId, linkedContactId);
        });
        assertThat(migratedHistoryCount).isOne();
        String legalName = transactions.execute(s -> {
            jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)", String.class, tenantId.toString());
            return jdbc.queryForObject(
                    "SELECT legal_name FROM crm_contacts WHERE tenant_id=? AND id=?",
                    String.class, tenantId, linkedContactId);
        });
        assertThat(legalName).isEqualTo("Linked Person");

        upgrade.migrate();
        Long linkedRelCountAfterRerun = transactions.execute(s -> {
            jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)", String.class, tenantId.toString());
            return jdbc.queryForObject(
                    "SELECT COUNT(*) FROM crm_contact_account_relationships WHERE tenant_id=? AND contact_id=?",
                    Long.class, tenantId, linkedContactId);
        });
        assertThat(linkedRelCountAfterRerun).isOne();
        Long linkedHistoryCountAfterRerun = transactions.execute(s -> {
            jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)", String.class, tenantId.toString());
            return jdbc.queryForObject(
                    "SELECT COUNT(*) FROM crm_contact_relationship_history WHERE tenant_id=? AND relationship_id=?",
                    Long.class, tenantId, linkedContactId);
        });
        assertThat(linkedHistoryCountAfterRerun).isOne();
    }

    @Test
    void databaseCompositeKeysRejectCrossTenantLinking() {
        Flyway flyway = flyway(null);
        flyway.clean();
        flyway.migrate();
        DriverManagerDataSource dataSource = dataSource();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        TransactionTemplate transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        UUID contactA = UUID.randomUUID();
        UUID accountB = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.parse("2026-07-17T00:00:00Z");

        insertTenantAndUser(jdbc, tenantA, userA, "a", now);
        insertTenantAndUser(jdbc, tenantB, userB, "b", now);
        // Set tenant GUC + INSERT crm_accounts + crm_contacts inside a single
        // tenant-scoped transaction (per tenant) so set_config(...,true)
        // (transaction-local) persists on the same Connection used by the
        // INSERTs. Outside an explicit transaction, set_config has no effect,
        // leaving the GUC unset and triggering FORCE RLS rejection on
        // crm_contacts (V20260823_1).
        transactions.executeWithoutResult(s -> {
            jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)", String.class, tenantB.toString());
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
        });
        transactions.executeWithoutResult(s -> {
            // insertLegacyContact requires the caller to have set the tenant
            // GUC on the same Connection (i.e. inside this tenant-scoped
            // transaction) before invoking the helper.
            jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)", String.class, tenantA.toString());
            insertLegacyContact(jdbc, contactA, tenantA, null, userA,
                    "Tenant", "A", "tenant-a@example.test", now);
        });

        // Cross-tenant INSERT attempt: tenantA lookup pointing at tenantB's
        // account — must be rejected by the composite-key FK constraint.
        // Wrap in tenant-scoped transaction so the GUC is set on the same
        // Connection used by the INSERT.
        assertThatThrownBy(() -> transactions.executeWithoutResult(s -> {
            jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)", String.class, tenantA.toString());
            jdbc.update(
                    """
                    INSERT INTO crm_contact_account_relationships
                        (id,tenant_id,contact_id,account_id,version,role_code,role_key,status,
                         primary_relationship,decision_authority,created_by,updated_by,created_at,updated_at)
                    VALUES (?,?,?,?,0,'EMPLOYEE','EMPLOYEE','ACTIVE',FALSE,'NONE',?,?,?,?)
                    """,
                    UUID.randomUUID(), tenantA, contactA, accountB,
                    userA, userA, now, now);
        }))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class)
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
            OffsetDateTime now) {
        // Caller MUST have set the tenant GUC on the same Connection (i.e. inside
        // a tenant-scoped transaction) before calling this helper.
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
            OffsetDateTime now) {
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
                userId, tenantId, suffix + "-migration@example.test", "User " + suffix,
                "ACTIVE", "dummy", now, now);
    }

    private Flyway flyway(MigrationVersion target) {
        var configuration = Flyway.configure()
                .dataSource(MigrationTestSchemaSupport.getIsolatedJdbcUrl(System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad")), System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"), System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""))
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .validateOnMigrate(true);
        if (target != null) configuration.target(target);
        return configuration.load();
    }

    private DriverManagerDataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                MigrationTestSchemaSupport.getIsolatedJdbcUrl(System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad")), System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"), System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""));
        dataSource.setDriverClassName("org.postgresql.Driver");
        return dataSource;
    }
}
