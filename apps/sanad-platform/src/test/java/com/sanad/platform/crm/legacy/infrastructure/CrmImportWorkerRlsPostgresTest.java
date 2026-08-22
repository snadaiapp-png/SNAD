package com.sanad.platform.crm.legacy.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CRM import worker RLS regression (Commit 2).
 *
 * <p>Seeds an import job + file, invokes {@code processNextImportNow()},
 * and verifies the job reaches COMPLETED with a timeline event visible
 * under the import tenant's RLS scope — even though the worker has no
 * SecurityContextHolder tenant (it runs as a background scheduled task).
 *
 * <p>Pre-fix: with crm_timeline_events FORCE-RLS and no GUC set inside
 * the worker transaction, the INSERT was rejected and the worker left
 * the job stuck RUNNING. Post-fix: {@link TenantRlsTransactionContext}
 * scopes the transaction to {@code payload.tenantId()} and the timeline
 * row is persisted.
 */
@DisplayName("CRM import worker — RLS regression (PostgreSQL Direct)")
class CrmImportWorkerRlsPostgresTest {

    private static final String JDBC_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String USERNAME = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");

    private LegacyCrmInfrastructureService service;
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;

    @BeforeAll
    static void requirePostgreSql() {
        boolean postgresAvailable;
        try {
            postgresAvailable = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "CrmImportWorkerRlsPostgresTest");
        } catch (Throwable ignored) {
            postgresAvailable = false;
        }
        Assumptions.assumeTrue(postgresAvailable,
                "PostgreSQL Direct is not available — skipping CrmImportWorkerRlsPostgresTest.");
    }

    @BeforeEach
    void migrateSchema() {
        Flyway flyway = Flyway.configure()
                .dataSource(JDBC_URL, USERNAME, PASSWORD)
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .javaMigrations(new V15__seed_rbac_roles_and_capabilities())
                .cleanDisabled(false)
                .validateOnMigrate(true)
                .load();
        flyway.clean();
        flyway.migrate();
        flyway.validate();

        SingleConnectionDataSource ds = new SingleConnectionDataSource(JDBC_URL, USERNAME, PASSWORD, true);
        ds.setDriverClassName("org.postgresql.Driver");
        jdbc = new JdbcTemplate(ds);
        PlatformTransactionManager tm = new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds);
        tx = new TransactionTemplate(tm);
        NamedParameterJdbcTemplate namedJdbc = new NamedParameterJdbcTemplate(ds);
        com.sanad.platform.security.rls.TenantRlsTransactionContext rlsContext =
                new com.sanad.platform.security.rls.TenantRlsTransactionContext(jdbc);
        // Empty Environment + a valid base64 AES-256 key for the import worker —
        // the worker only needs the RLS context fix to be exercised here.
        // The key below is a non-trivial test key (not the well-known default
        // rejected by CrmEncryptionKeyValidator).
        org.springframework.core.env.Environment env =
                new org.springframework.mock.env.MockEnvironment();
        service = new LegacyCrmInfrastructureService(
                namedJdbc, new ObjectMapper(), tm, env, true,
                validTestEncryptionKey(),
                rlsContext);
    }

    @Test
    @DisplayName("import worker writes timeline event without SecurityContextHolder tenant")
    void importWorkerMustWriteTimelineEventWithoutSecurityContext() {
        // Ensure no Spring Security context is present (the worker runs as a
        // background scheduled task with no Authentication).
        org.springframework.security.core.context.SecurityContextHolder.clearContext();

        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        seedTenant(tenantId);
        seedPipeline(tenantId, actorId);
        seedAccount(tenantId, actorId);

        String csv = "displayName,name\r\nAcme Import Corp,Acme Import Corp\r\n";
        byte[] content = csv.getBytes(StandardCharsets.UTF_8);
        String sha256 = sha256(content);
        String mappingJson = "{\"displayName\":\"displayName\",\"name\":\"displayName\"}";
        UUID jobId = UUID.randomUUID();
        seedImportJob(jobId, tenantId, actorId, "ACCOUNT", content, sha256, mappingJson, 1);

        // Worker invocation — must complete without throwing and leave the
        // job in COMPLETED state.
        boolean claimed = service.processNextImportNow();
        assertThat(claimed).isTrue();

        // The job reached COMPLETED.
        String jobStatus = jdbc.queryForObject(
                "SELECT status FROM crm_import_jobs WHERE id = ?",
                String.class, jobId);
        assertThat(jobStatus).isEqualTo("COMPLETED");

        // A timeline event was written for the import. crm_timeline_events is
        // FORCE-RLS fail-closed — verify it under the import tenant's scope by
        // setting the GUC via set_config.
        jdbc.queryForObject(
                "SELECT set_config('app.tenant_id', ?, false)", String.class, tenantId.toString());
        Long timelineCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM crm_timeline_events "
                        + "WHERE tenant_id = ? AND source_type = 'CRM_IMPORT' "
                        + "AND source_id = ?",
                Long.class, tenantId, jobId);
        assertThat(timelineCount)
                .as("import worker must write a timeline event for the import job "
                        + "(fail-closed RLS on crm_timeline_events requires the worker "
                        + "to apply the trusted tenant scope via TenantRlsTransactionContext)")
                .isEqualTo(1L);
    }

    // ---------- helpers ----------

    private void seedTenant(UUID tenantId) {
        jdbc.update("INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                tenantId, "import-rls-" + tenantId.toString().substring(0, 8),
                "imp-" + tenantId.toString().substring(0, 8));
    }

    private void seedPipeline(UUID tenantId, UUID actorId) {
        jdbc.update("INSERT INTO crm_pipelines "
                        + "(id, tenant_id, name, currency_code, active, created_by, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'SAR', TRUE, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                UUID.randomUUID(), tenantId, "Default", actorId);
    }

    private void seedAccount(UUID tenantId, UUID actorId) {
        jdbc.update("INSERT INTO crm_accounts "
                        + "(id, tenant_id, version, display_name, normalized_name, account_type, lifecycle_status, "
                        + "created_by, updated_by, created_at, updated_at) "
                        + "VALUES (?, ?, 0, ?, ?, 'BUSINESS', 'ACTIVE', ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                UUID.randomUUID(), tenantId, "Parent Acct", "parent-acct", actorId, actorId);
    }

    private void seedImportJob(UUID jobId, UUID tenantId, UUID actorId, String entityType,
                                byte[] content, String sha256, String mappingJson, int totalRows) {
        UUID fileId = UUID.randomUUID();
        jdbc.update("INSERT INTO crm_import_jobs "
                        + "(id, tenant_id, entity_type, status, total_rows, processed_rows, succeeded_rows, failed_rows, "
                        + "requested_by, created_at, updated_at, original_filename, content_type, file_size_bytes, "
                        + "file_sha256, mapping_json) "
                        + "VALUES (?, ?, ?, 'READY', ?, 0, 0, 0, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, "
                        + "'import.csv', 'text/csv', ?, ?, ?)",
                jobId, tenantId, entityType, totalRows, actorId, content.length, sha256, mappingJson);
        jdbc.update("INSERT INTO crm_import_files "
                        + "(id, tenant_id, import_job_id, content_base64, created_at) "
                        + "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)",
                fileId, tenantId, jobId, Base64.getEncoder().encodeToString(content));
    }

    private static String sha256(byte[] content) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 32-byte AES-256 key that is NOT the well-known test default rejected by CrmEncryptionKeyValidator. */
    private static String validTestEncryptionKey() {
        byte[] key = new byte[32];
        for (int i = 0; i < key.length; i++) {
            key[i] = (byte) (i + 1);
        }
        return Base64.getEncoder().encodeToString(key);
    }
}
