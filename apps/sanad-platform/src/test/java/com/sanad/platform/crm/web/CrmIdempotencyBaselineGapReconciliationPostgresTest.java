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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class CrmIdempotencyBaselineGapReconciliationPostgresTest {

    private static final String HISTORICAL_VERSION = "20260713.1";
    private static final String PRE_RECONCILIATION_VERSION = "20260717.101";
    private static final String RECONCILIATION_VERSION = "20260721.2";

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
                "Docker is required to verify the CRM idempotency baseline reconciliation.");
    }

    @Test
    void recreatesMissingIdempotencyTableWithoutEditingHistoricalFlywayState() {
        Flyway historical = mainMigrations(MigrationVersion.fromVersion(PRE_RECONCILIATION_VERSION));
        historical.clean();
        historical.migrate();

        JdbcTemplate jdbc = jdbc();
        assertThat(historyCount(jdbc, HISTORICAL_VERSION)).isOne();
        assertThat(tableExists(jdbc, "crm_idempotency_records")).isTrue();

        jdbc.execute("DROP TABLE crm_idempotency_records");
        assertThat(tableExists(jdbc, "crm_idempotency_records")).isFalse();
        assertThat(historyCount(jdbc, HISTORICAL_VERSION)).isOne();

        Flyway complete = allProductionMigrations();
        complete.migrate();
        complete.validate();

        assertThat(historyCount(jdbc, HISTORICAL_VERSION)).isOne();
        assertThat(historyCount(jdbc, RECONCILIATION_VERSION)).isOne();
        assertThat(tableExists(jdbc, "crm_idempotency_records")).isTrue();
        assertThat(constraintExists(jdbc, "crm_idempotency_records_unique")).isTrue();
        assertThat(indexExists(jdbc, "idx_crm_idempotency_records_tenant")).isTrue();
        assertThat(indexExists(jdbc, "idx_crm_idempotency_records_expires")).isTrue();

        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID principalId = UUID.randomUUID();
        Instant now = Instant.now();
        int inserted = jdbc.update("""
                INSERT INTO crm_idempotency_records (
                    id, tenant_id, principal_id, endpoint, idempotency_key,
                    request_fingerprint_sha256, response_status,
                    response_body_json, response_headers_json, content_type,
                    created_at, expires_at
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                id, tenantId, principalId, "/api/v2/crm/accounts/test/addresses", "gap-test-key",
                "a".repeat(64), 201, "{}", "{}", "application/json",
                Timestamp.from(now), Timestamp.from(now.plusSeconds(3600)));

        assertThat(inserted).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT response_status FROM crm_idempotency_records WHERE id=?",
                Integer.class, id)).isEqualTo(201);

        complete.migrate();
        complete.validate();
        assertThat(historyCount(jdbc, RECONCILIATION_VERSION)).isOne();
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

    private long historyCount(JdbcTemplate jdbc, String version) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version=? AND type='SQL' AND success=TRUE",
                Long.class, version);
        return count == null ? 0L : count;
    }

    private boolean tableExists(JdbcTemplate jdbc, String table) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables " +
                        "WHERE table_schema='public' AND table_name=?",
                Long.class, table);
        return count != null && count == 1L;
    }

    private boolean constraintExists(JdbcTemplate jdbc, String constraint) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_constraint WHERE conname=?",
                Long.class, constraint);
        return count != null && count == 1L;
    }

    private boolean indexExists(JdbcTemplate jdbc, String index) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE schemaname='public' AND indexname=?",
                Long.class, index);
        return count != null && count == 1L;
    }
}
