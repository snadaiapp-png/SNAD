package com.sanad.platform.crm.collaboration;

import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Task 1 — CRM Collaboration & Event Foundation schema verification.
 *
 * <p>PostgreSQL Direct test that migrates the full CRM schema (including
 * V20260822_1 / V20260822_2) and verifies the foundation columns, CHECK
 * constraints, and FORCE RLS posture required by the collaboration
 * specification.
 *
 * <p>Uses the Crm009TestEnvironment PostgreSQL-Direct gate
 * (Docker/Testcontainers OUT_OF_SCOPE) and operates directly on the
 * configured {@code sanad} database (per the established
 * {@code CrmRepositoryPostgresTestBase} pattern). Flyway {@code clean()}
 * is enabled so each test class starts from a fully migrated schema.
 */
@DisplayName("Task 1 — CRM collaboration event foundation schema (PostgreSQL Direct)")
class CrmCollaborationSchemaPostgresTest {

    private static final String JDBC_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String USERNAME = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");

    private NamedParameterJdbcTemplate jdbc;

    @BeforeAll
    static void requirePostgreSql() {
        boolean postgresAvailable;
        try {
            postgresAvailable = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "CrmCollaborationSchemaPostgresTest");
        } catch (Throwable ignored) {
            postgresAvailable = false;
        }
        Assumptions.assumeTrue(postgresAvailable,
                "PostgreSQL Direct is not available — skipping CrmCollaborationSchemaPostgresTest.");
    }

    @BeforeEach
    void migrateSchema() throws java.sql.SQLException {
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

        // Use a SingleConnectionDataSource so the session-level GUC set below
        // survives across every JdbcTemplate operation in the test (a fresh
        // DriverManagerDataSource would return a new physical Connection for
        // every query and lose the GUC).
        SingleConnectionDataSource ds = new SingleConnectionDataSource(JDBC_URL, USERNAME, PASSWORD, true);
        ds.setDriverClassName("org.postgresql.Driver");
        jdbc = new NamedParameterJdbcTemplate(ds);
        // crm_entity_participants and crm_event_outbox are FORCE RLS with a
        // fail-closed policy (V20260822_2). Seed the session-level GUC to a
        // real tenant row so CHECK-constraint assertions can still issue
        // INSERTs without per-statement SET LOCAL blocks. The Connection is
        // intentionally NOT closed — SingleConnectionDataSource keeps the same
        // physical Connection for the lifetime of the DataSource, so the GUC
        // set on this Connection persists for every subsequent JdbcTemplate op.
        seedTenantId = java.util.UUID.randomUUID();
        java.sql.Connection conn = ds.getConnection();
        try (java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at)
                    VALUES ('""" + seedTenantId + "', 'rls-seed', 'rls-seed', 'ACTIVE', "
                    + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
            stmt.execute("SET app.tenant_id = '" + seedTenantId + "'");
        }
        // do NOT close conn — SingleConnectionDataSource + suppressClose
        // means close() is a no-op on the proxy anyway, and we want every
        // subsequent jdbc operation to share this Connection.
    }

    private java.util.UUID seedTenantId;

    @Test
    @DisplayName("foundation schema has required columns and role constraint")
    void foundationSchemaHasRequiredColumnsAndRoleConstraint() {
        // role column on crm_entity_participants
        List<String> participantColumns = columnsOf("crm_entity_participants");
        assertThat(participantColumns).contains(
                "id", "tenant_id", "version", "entity_type", "entity_id",
                "user_id", "role", "added_at", "added_by",
                "removed_at", "removed_by");

        // CHECK constraint allows only COLLABORATOR / WATCHER — not OWNER / REVIEWER.
        java.util.UUID tenant = insertTenant();
        java.util.UUID entity = java.util.UUID.randomUUID();
        java.util.UUID user = java.util.UUID.randomUUID();
        insertActiveParticipant(tenant, entity, "CONTACT", user, "COLLABORATOR");
        insertActiveParticipant(tenant, entity, "CONTACT", user, "WATCHER");
        assertThatThrownBy(() -> insertActiveParticipant(tenant, entity, "CONTACT", user, "OWNER"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertActiveParticipant(tenant, entity, "CONTACT", user, "REVIEWER"))
                .isInstanceOf(DataIntegrityViolationException.class);

        // entity_type CHECK — CONTACT / TASK / CASE only.
        assertThatThrownBy(() -> insertActiveParticipant(tenant, entity, "LEAD", user, "COLLABORATOR"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertActiveParticipant(tenant, entity, "ACCOUNT", user, "COLLABORATOR"))
                .isInstanceOf(DataIntegrityViolationException.class);

        // crm_event_outbox.correlation_id present.
        List<String> outboxColumns = columnsOf("crm_event_outbox");
        assertThat(outboxColumns).contains(
                "id", "tenant_id", "version", "event_type", "summary_key",
                "payload_json", "metadata_json", "correlation_id", "causation_id",
                "schema_version", "status", "attempt_count", "last_error",
                "available_at", "claimed_at", "published_at",
                "created_at", "updated_at");

        // status CHECK — PENDING / PROCESSING / PUBLISHED / FAILED only.
        assertThatThrownBy(() -> insertOutboxRow(tenant, "ZOMBIE"))
                .isInstanceOf(DataIntegrityViolationException.class);

        // schema_version CHECK — must be > 0.
        assertThatThrownBy(() -> insertOutboxRowWithSchemaVersion(tenant, 0))
                .isInstanceOf(DataIntegrityViolationException.class);

        // attempt_count CHECK — must be >= 0.
        assertThatThrownBy(() -> insertOutboxRowWithAttemptCount(tenant, -1))
                .isInstanceOf(DataIntegrityViolationException.class);

        // New crm_timeline_events structured columns.
        List<String> timelineColumns = columnsOf("crm_timeline_events");
        assertThat(timelineColumns).contains(
                "summary_key", "metadata_json", "correlation_id", "causation_id", "schema_version");

        // Legacy columns preserved (the existing JdbcTimelineEventAdapter INSERT must not break).
        assertThat(timelineColumns).contains(
                "id", "tenant_id", "subject_type", "subject_id", "event_type",
                "summary", "source_type", "source_id", "occurred_at", "created_by");

        // schema_version has a NOT NULL DEFAULT 1 — legacy INSERTs that omit it
        // must still succeed.
        java.util.UUID timelineId = java.util.UUID.randomUUID();
        jdbc.update("""
                INSERT INTO crm_timeline_events (id, tenant_id, subject_type, subject_id, event_type,
                    summary, source_type, source_id, occurred_at, created_by)
                VALUES (:id, :tenantId, 'CONTACT', :subjectId, 'crm.contact.created',
                    'Contact created', 'CRM_CONTACT', :subjectId, CURRENT_TIMESTAMP, :actorId)
                """, new MapSqlParameterSource()
                .addValue("id", timelineId)
                .addValue("tenantId", tenant)
                .addValue("subjectId", entity)
                .addValue("actorId", user));
        Integer legacySchemaVersion = jdbc.queryForObject(
                "SELECT schema_version FROM crm_timeline_events WHERE id = :id",
                new MapSqlParameterSource("id", timelineId), Integer.class);
        assertThat(legacySchemaVersion).isEqualTo(1);
    }

    @Test
    @DisplayName("participant and outbox tables use forced RLS")
    void participantAndOutboxTablesUseForcedRls() {
        // COALESCE + LIMIT 1 — safe pattern that returns a single row even when
        // no pg_class entry is found, so queryForObject cannot throw
        // EmptyResultDataAccessException on a freshly migrated schema.
        Boolean participantForced = jdbc.queryForObject("""
                SELECT COALESCE(
                    (SELECT relforcerowsecurity
                       FROM pg_class
                      WHERE relname = 'crm_entity_participants'
                        AND relkind = 'r'
                      LIMIT 1),
                    false)
                """, Map.of(), Boolean.class);
        assertThat(participantForced)
                .as("crm_entity_participants must have FORCE ROW LEVEL SECURITY")
                .isTrue();

        Boolean outboxForced = jdbc.queryForObject("""
                SELECT COALESCE(
                    (SELECT relforcerowsecurity
                       FROM pg_class
                      WHERE relname = 'crm_event_outbox'
                        AND relkind = 'r'
                      LIMIT 1),
                    false)
                """, Map.of(), Boolean.class);
        assertThat(outboxForced)
                .as("crm_event_outbox must have FORCE ROW LEVEL SECURITY")
                .isTrue();

        Boolean timelineForced = jdbc.queryForObject("""
                SELECT COALESCE(
                    (SELECT relforcerowsecurity
                       FROM pg_class
                      WHERE relname = 'crm_timeline_events'
                        AND relkind = 'r'
                      LIMIT 1),
                    false)
                """, Map.of(), Boolean.class);
        assertThat(timelineForced)
                .as("crm_timeline_events must have FORCE ROW LEVEL SECURITY (legacy policy replaced)")
                .isTrue();
    }

    // ---------- helpers ----------

    private List<String> columnsOf(String tableName) {
        return jdbc.query("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = :table
                ORDER BY ordinal_position
                """, new MapSqlParameterSource("table", tableName),
                (rs, rowNum) -> rs.getString("column_name"));
    }

    private java.util.UUID insertTenant() {
        java.util.UUID tenantId = java.util.UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at)
                VALUES (:id, :name, :subdomain, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, new MapSqlParameterSource()
                .addValue("id", tenantId)
                .addValue("name", "collab-" + tenantId.toString().substring(0, 8))
                .addValue("subdomain", "col-" + tenantId.toString().substring(0, 8)));
        // Make this tenant the active RLS scope so subsequent INSERTs on
        // FORCE-RLS tables (crm_entity_participants / crm_event_outbox)
        // pass the WITH CHECK clause. SingleConnectionDataSource keeps the
        // same physical Connection so the SET persists across statements.
        jdbc.queryForObject("SELECT set_config('app.tenant_id', :tenantId, false)",
                new MapSqlParameterSource("tenantId", tenantId.toString()), String.class);
        return tenantId;
    }

    private void insertActiveParticipant(java.util.UUID tenantId, java.util.UUID entityId,
                                          String entityType, java.util.UUID userId, String role) {
        jdbc.update("""
                INSERT INTO crm_entity_participants
                    (id, tenant_id, version, entity_type, entity_id, user_id, role,
                     added_at, added_by)
                VALUES (:id, :tenantId, 0, :entityType, :entityId, :userId, :role,
                    CURRENT_TIMESTAMP, :userId)
                """, new MapSqlParameterSource()
                .addValue("id", java.util.UUID.randomUUID())
                .addValue("tenantId", tenantId)
                .addValue("entityType", entityType)
                .addValue("entityId", entityId)
                .addValue("userId", userId)
                .addValue("role", role));
    }

    private void insertOutboxRow(java.util.UUID tenantId, String status) {
        insertOutboxRowWithSchemaVersion(tenantId, status, 1, 0);
    }

    private void insertOutboxRowWithSchemaVersion(java.util.UUID tenantId, int schemaVersion) {
        insertOutboxRowWithSchemaVersion(tenantId, "PENDING", schemaVersion, 0);
    }

    private void insertOutboxRowWithAttemptCount(java.util.UUID tenantId, int attemptCount) {
        insertOutboxRowWithSchemaVersion(tenantId, "PENDING", 1, attemptCount);
    }

    private void insertOutboxRowWithSchemaVersion(java.util.UUID tenantId, String status,
                                                   int schemaVersion, int attemptCount) {
        jdbc.update("""
                INSERT INTO crm_event_outbox
                    (id, tenant_id, version, event_type, payload_json,
                     schema_version, status, attempt_count,
                     available_at, created_at, updated_at)
                VALUES (:id, :tenantId, 0, 'crm.collaboration.participant.added', '{}',
                    :schemaVersion, :status, :attemptCount,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, new MapSqlParameterSource()
                .addValue("id", java.util.UUID.randomUUID())
                .addValue("tenantId", tenantId)
                .addValue("schemaVersion", schemaVersion)
                .addValue("status", status)
                .addValue("attemptCount", attemptCount));
    }
}
