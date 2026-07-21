package com.sanad.platform.crm.web;

import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class CrmContactBaselineGapReconciliationPostgresTest {

    private static final String PRE_CONTACT_RELATIONSHIP_VERSION = "20260716.4";
    private static final String RECONCILIATION_VERSION = "20260721.1";
    private static final String RECONCILIATION_RESOURCE =
            "db/vendor/postgresql/V20260721_1__reconcile_crm_contact_relationship_model_after_baseline_gap.sql";

    private static final List<String> REQUIRED_COLUMNS = List.of(
            "legal_name", "preferred_name", "middle_name", "pronouns", "source");

    private static final List<String> REQUIRED_TABLES = List.of(
            "crm_contact_relationship_roles",
            "crm_contact_account_relationships",
            "crm_contact_relationship_history",
            "crm_contact_ownership_history");

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
                "Docker is required to verify the PostgreSQL Contact schema reconciliation.");
    }

    @Test
    void repairsSkippedContactSchemaIdempotentlyAndAllowsCurrentInsertContract() {
        Flyway preGap = mainMigrations(MigrationVersion.fromVersion(PRE_CONTACT_RELATIONSHIP_VERSION));
        preGap.clean();
        preGap.migrate();

        JdbcTemplate jdbc = jdbc();
        REQUIRED_COLUMNS.forEach(column ->
                assertThat(columnExists(jdbc, "crm_contacts", column)).as(column).isFalse());
        REQUIRED_TABLES.forEach(table ->
                assertThat(tableExists(jdbc, table)).as(table).isFalse());

        ResourceDatabasePopulator reconciliation = new ResourceDatabasePopulator(
                new ClassPathResource(RECONCILIATION_RESOURCE));
        reconciliation.setContinueOnError(false);
        reconciliation.execute(dataSource());
        reconciliation.execute(dataSource());

        REQUIRED_COLUMNS.forEach(column ->
                assertThat(columnExists(jdbc, "crm_contacts", column)).as(column).isTrue());
        REQUIRED_TABLES.forEach(table ->
                assertThat(tableExists(jdbc, table)).as(table).isTrue());

        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        Instant now = Instant.now();

        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) VALUES (?,?,?,?,?,?)",
                tenantId, "CRM Gap Test", "crm-gap-" + tenantId.toString().substring(0, 8), "ACTIVE",
                Timestamp.from(now), Timestamp.from(now));

        int inserted = jdbc.update("""
                INSERT INTO crm_contacts (
                    id, tenant_id, legal_name, preferred_name,
                    given_name, family_name, display_name, normalized_name,
                    preferred_locale, time_zone, lifecycle_status, consent_summary,
                    created_by, updated_by, created_at, updated_at
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                contactId, tenantId, "Diagnostic Contact", "Diagnostic",
                "Diagnostic", "Contact", "Diagnostic Contact", "diagnostic contact",
                "ar-SA", "Asia/Riyadh", "ACTIVE", "UNKNOWN",
                actorId, actorId, Timestamp.from(now), Timestamp.from(now));

        assertThat(inserted).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT legal_name FROM crm_contacts WHERE tenant_id=? AND id=?",
                String.class, tenantId, contactId)).isEqualTo("Diagnostic Contact");

        Flyway complete = allProductionMigrations();
        complete.clean();
        complete.migrate();
        complete.validate();

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version=? AND type='SQL' AND success=TRUE",
                Long.class, RECONCILIATION_VERSION)).isOne();
        REQUIRED_COLUMNS.forEach(column ->
                assertThat(columnExists(jdbc, "crm_contacts", column)).as(column).isTrue());
        REQUIRED_TABLES.forEach(table ->
                assertThat(tableExists(jdbc, table)).as(table).isTrue());
    }

    private Flyway mainMigrations(MigrationVersion target) {
        return Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .javaMigrations(new V15__seed_rbac_roles_and_capabilities())
                .target(target)
                .cleanDisabled(false)
                .validateOnMigrate(true)
                .load();
    }

    private Flyway allProductionMigrations() {
        return Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .javaMigrations(new V15__seed_rbac_roles_and_capabilities())
                .outOfOrder(true)
                .cleanDisabled(false)
                .validateOnMigrate(true)
                .load();
    }

    private DriverManagerDataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        dataSource.setDriverClassName(POSTGRES.getDriverClassName());
        return dataSource;
    }

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(dataSource());
    }

    private boolean columnExists(JdbcTemplate jdbc, String table, String column) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                        "WHERE table_schema='public' AND table_name=? AND column_name=?",
                Long.class, table, column);
        return count != null && count == 1L;
    }

    private boolean tableExists(JdbcTemplate jdbc, String table) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables " +
                        "WHERE table_schema='public' AND table_name=?",
                Long.class, table);
        return count != null && count == 1L;
    }
}
