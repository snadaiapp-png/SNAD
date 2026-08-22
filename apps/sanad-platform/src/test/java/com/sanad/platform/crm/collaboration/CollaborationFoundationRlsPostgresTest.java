package com.sanad.platform.crm.collaboration;

import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 8 — Collaboration foundation RLS proof (PostgreSQL Direct).
 *
 * <p>Verifies that the three collaboration tables —
 * {@code crm_entity_participants}, {@code crm_timeline_events},
 * {@code crm_event_outbox} — all enforce FORCE RLS + fail-closed
 * tenant isolation as defined by V20260822_2.
 *
 * <p>Three independent proofs:
 * <ol>
 *   <li><b>Cross-tenant read isolation</b>: under tenant-A GUC,
 *       querying by tenant-B IDs returns 0 rows — even when the SQL
 *       explicitly says {@code WHERE id = :tenantBId}.</li>
 *   <li><b>Cross-tenant update isolation</b>: under tenant-A GUC,
 *       harmless UPDATEs against tenant-B IDs affect 0 rows.</li>
 *   <li><b>Missing context fail-closed</b>: with the GUC unset
 *       (fresh connection / {@code RESET app.tenant_id}), SELECTs
 *       of known tenant rows return 0 rows or throw — never leak data.</li>
 * </ol>
 *
 * <p>Catalog proofs: pg_class.relrowsecurity = true AND
 * pg_class.relforcerowsecurity = true for all three tables; pg_policies
 * qual must contain {@code tenant_id = current_setting('app.tenant_id', true)::UUID}
 * and must NOT contain a permissive-when-unset pattern.
 */
@DisplayName("Task 8 — Collaboration foundation RLS (PostgreSQL Direct)")
class CollaborationFoundationRlsPostgresTest {

    private static NamedParameterJdbcTemplate jdbc;
    private static TransactionTemplate transactions;

    private static final UUID TENANT_A = UUID.fromString("aaaaaaaa-0000-4000-8000-00000000a001");
    private static final UUID TENANT_B = UUID.fromString("bbbbbbbb-0000-4000-8000-00000000b001");

    // Tenant A fixtures
    private UUID tenantAParticipantId;
    private UUID tenantATimelineId;
    private UUID tenantAOutboxId;
    private UUID tenantATaskId;

    // Tenant B fixtures
    private UUID tenantBParticipantId;
    private UUID tenantBTimelineId;
    private UUID tenantBOutboxId;
    private UUID tenantBTaskId;

    @BeforeAll
    static void setup() {
        boolean ok;
        try {
            ok = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "CollaborationFoundationRlsPostgresTest");
        } catch (Throwable ignored) {
            ok = false;
        }
        Assumptions.assumeTrue(ok, "PostgreSQL Direct required");
        Flyway.configure()
                .dataSource(System.getenv().getOrDefault("SPRING_DATASOURCE_URL",
                                "jdbc:postgresql://localhost:5432/sanad"),
                        System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"),
                        System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""))
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .javaMigrations(new V15__seed_rbac_roles_and_capabilities())
                .cleanDisabled(false)
                .validateOnMigrate(true)
                .load()
                .migrate();
        var ds = new DriverManagerDataSource(
                System.getenv().getOrDefault("SPRING_DATASOURCE_URL",
                        "jdbc:postgresql://localhost:5432/sanad"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""));
        jdbc = new NamedParameterJdbcTemplate(ds);
        transactions = new TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds));
    }

    @BeforeEach
    void seed() {
        // Generate fresh unique IDs per test invocation to avoid cross-test contamination.
        tenantAParticipantId = UUID.randomUUID();
        tenantATimelineId = UUID.randomUUID();
        tenantAOutboxId = UUID.randomUUID();
        tenantATaskId = UUID.randomUUID();
        tenantBParticipantId = UUID.randomUUID();
        tenantBTimelineId = UUID.randomUUID();
        tenantBOutboxId = UUID.randomUUID();
        tenantBTaskId = UUID.randomUUID();

        for (UUID t : new UUID[]{TENANT_A, TENANT_B}) {
            transactions.executeWithoutResult(s -> {
                setGuc(t);
                jdbc.update("DELETE FROM crm_entity_participants WHERE tenant_id = :t",
                        p("t", t));
            });
            transactions.executeWithoutResult(s -> {
                setGuc(t);
                jdbc.update("DELETE FROM crm_timeline_events WHERE tenant_id = :t",
                        p("t", t));
            });
            transactions.executeWithoutResult(s -> {
                setGuc(t);
                jdbc.update("DELETE FROM crm_event_outbox WHERE tenant_id = :t",
                        p("t", t));
            });
            transactions.executeWithoutResult(s -> {
                setGuc(t);
                jdbc.update("DELETE FROM crm_tasks WHERE tenant_id = :t",
                        p("t", t));
            });
        }
        jdbc.update("DELETE FROM users WHERE tenant_id IN (:a,:b)",
                new MapSqlParameterSource().addValue("a", TENANT_A).addValue("b", TENANT_B));
        jdbc.update("DELETE FROM tenants WHERE id IN (:a,:b)",
                new MapSqlParameterSource().addValue("a", TENANT_A).addValue("b", TENANT_B));
        ensureTenant(TENANT_A);
        ensureTenant(TENANT_B);
        ensureUser(UUID.randomUUID(), TENANT_A);
        ensureUser(UUID.randomUUID(), TENANT_B);

        // Seed tenant-A fixtures under tenant-A GUC.
        seedTask(TENANT_A, tenantATaskId);
        seedParticipant(TENANT_A, tenantAParticipantId, tenantATaskId);
        seedTimeline(TENANT_A, tenantATimelineId, tenantATaskId);
        seedOutbox(TENANT_A, tenantAOutboxId, tenantATaskId);

        // Seed tenant-B fixtures under tenant-B GUC.
        seedTask(TENANT_B, tenantBTaskId);
        seedParticipant(TENANT_B, tenantBParticipantId, tenantBTaskId);
        seedTimeline(TENANT_B, tenantBTimelineId, tenantBTaskId);
        seedOutbox(TENANT_B, tenantBOutboxId, tenantBTaskId);
    }

    // ===========================================================
    //  Cross-tenant READ isolation
    // ===========================================================

    @Test
    @DisplayName("under tenant-A GUC: SELECT by tenant-B participant id returns 0 rows")
    void tenantACannotReadTenantBParticipant() {
        Integer count = transactions.execute(s -> {
            setGuc(TENANT_A);
            return jdbc.queryForObject(
                    "SELECT COUNT(*) FROM crm_entity_participants WHERE id = :id",
                    p("id", tenantBParticipantId),
                    Integer.class);
        });
        assertThat(count).as("tenant A must NOT see tenant B participant row").isZero();
    }

    @Test
    @DisplayName("under tenant-A GUC: SELECT by tenant-B timeline id returns 0 rows")
    void tenantACannotReadTenantBTimeline() {
        Integer count = transactions.execute(s -> {
            setGuc(TENANT_A);
            return jdbc.queryForObject(
                    "SELECT COUNT(*) FROM crm_timeline_events WHERE id = :id",
                    p("id", tenantBTimelineId),
                    Integer.class);
        });
        assertThat(count).as("tenant A must NOT see tenant B timeline row").isZero();
    }

    @Test
    @DisplayName("under tenant-A GUC: SELECT by tenant-B outbox id returns 0 rows")
    void tenantACannotReadTenantBOutbox() {
        Integer count = transactions.execute(s -> {
            setGuc(TENANT_A);
            return jdbc.queryForObject(
                    "SELECT COUNT(*) FROM crm_event_outbox WHERE id = :id",
                    p("id", tenantBOutboxId),
                    Integer.class);
        });
        assertThat(count).as("tenant A must NOT see tenant B outbox row").isZero();
    }

    // ===========================================================
    //  Cross-tenant UPDATE isolation
    // ===========================================================

    @Test
    @DisplayName("under tenant-A GUC: UPDATE against tenant-B participant id affects 0 rows")
    void tenantACannotUpdateTenantBParticipant() {
        Integer updated = transactions.execute(s -> {
            setGuc(TENANT_A);
            return jdbc.update(
                    "UPDATE crm_entity_participants SET version = version " +
                            "WHERE id = :id",
                    p("id", tenantBParticipantId));
        });
        assertThat(updated).as("tenant A must NOT update tenant B participant row").isZero();
    }

    @Test
    @DisplayName("under tenant-A GUC: UPDATE against tenant-B outbox id affects 0 rows")
    void tenantACannotUpdateTenantBOutbox() {
        Integer updated = transactions.execute(s -> {
            setGuc(TENANT_A);
            return jdbc.update(
                    "UPDATE crm_event_outbox SET updated_at = updated_at " +
                            "WHERE id = :id",
                    p("id", tenantBOutboxId));
        });
        assertThat(updated).as("tenant A must NOT update tenant B outbox row").isZero();
    }

    // ===========================================================
    //  Missing context fail-closed
    // ===========================================================

    @Test
    @DisplayName("missing GUC: crm_entity_participants SELECT returns 0 rows (or throws) for known tenant A row")
    void missingContextParticipantFailClosed() {
        // Use a fresh transaction with NO setGuc call — the GUC is unset
        // so the fail-closed policy returns 0 rows.
        Integer count = transactions.execute(s -> {
            // Explicitly RESET in case any prior test set it on this connection
            // (DriverManagerDataSource may pool connections).
            jdbc.queryForObject("SELECT set_config('app.tenant_id', '', false)",
                    Map.of(), String.class);
            try {
                return jdbc.queryForObject(
                        "SELECT COUNT(*) FROM crm_entity_participants WHERE id = :id",
                        p("id", tenantAParticipantId),
                        Integer.class);
            } catch (org.springframework.dao.DataAccessException e) {
                // Acceptable fail-closed behavior: PostgreSQL error from
                // casting empty string to UUID.
                return -1;
            }
        });
        assertThat(count == 0 || count == -1)
                .as("missing GUC must NOT see tenant A participant row (count=" + count + ")")
                .isTrue();
    }

    @Test
    @DisplayName("missing GUC: crm_timeline_events SELECT returns 0 rows (or throws) for known tenant A row")
    void missingContextTimelineFailClosed() {
        Integer count = transactions.execute(s -> {
            jdbc.queryForObject("SELECT set_config('app.tenant_id', '', false)",
                    Map.of(), String.class);
            try {
                return jdbc.queryForObject(
                        "SELECT COUNT(*) FROM crm_timeline_events WHERE id = :id",
                        p("id", tenantATimelineId),
                        Integer.class);
            } catch (org.springframework.dao.DataAccessException e) {
                return -1;
            }
        });
        assertThat(count == 0 || count == -1)
                .as("missing GUC must NOT see tenant A timeline row (count=" + count + ")")
                .isTrue();
    }

    @Test
    @DisplayName("missing GUC: crm_event_outbox SELECT returns 0 rows (or throws) for known tenant A row")
    void missingContextOutboxFailClosed() {
        Integer count = transactions.execute(s -> {
            jdbc.queryForObject("SELECT set_config('app.tenant_id', '', false)",
                    Map.of(), String.class);
            try {
                return jdbc.queryForObject(
                        "SELECT COUNT(*) FROM crm_event_outbox WHERE id = :id",
                        p("id", tenantAOutboxId),
                        Integer.class);
            } catch (org.springframework.dao.DataAccessException e) {
                return -1;
            }
        });
        assertThat(count == 0 || count == -1)
                .as("missing GUC must NOT see tenant A outbox row (count=" + count + ")")
                .isTrue();
    }

    // ===========================================================
    //  Catalog proofs
    // ===========================================================

    @Test
    @DisplayName("catalog: crm_entity_participants has relrowsecurity + relforcerowsecurity = true")
    void participantForceRls() {
        verifyForceRls("crm_entity_participants");
    }

    @Test
    @DisplayName("catalog: crm_timeline_events has relrowsecurity + relforcerowsecurity = true")
    void timelineForceRls() {
        verifyForceRls("crm_timeline_events");
    }

    @Test
    @DisplayName("catalog: crm_event_outbox has relrowsecurity + relforcerowsecurity = true")
    void outboxForceRls() {
        verifyForceRls("crm_event_outbox");
    }

    @Test
    @DisplayName("catalog: crm_entity_participants fail-closed policy (no permissive-when-unset pattern)")
    void participantFailClosed() {
        verifyFailClosedPolicy("crm_entity_participants");
    }

    @Test
    @DisplayName("catalog: crm_timeline_events fail-closed policy (no permissive-when-unset pattern)")
    void timelineFailClosed() {
        verifyFailClosedPolicy("crm_timeline_events");
    }

    @Test
    @DisplayName("catalog: crm_event_outbox fail-closed policy (no permissive-when-unset pattern)")
    void outboxFailClosed() {
        verifyFailClosedPolicy("crm_event_outbox");
    }

    // ---------- helpers ----------

    private void setGuc(UUID t) {
        jdbc.queryForObject("SELECT set_config('app.tenant_id', :t, true)",
                p("t", t.toString()), String.class);
    }

    private MapSqlParameterSource p(String k, Object v) {
        return new MapSqlParameterSource().addValue(k, v);
    }

    private void ensureTenant(UUID id) {
        jdbc.update("""
                INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at)
                VALUES (:id, :name, :sub, 'ACTIVE', NOW(), NOW())
                ON CONFLICT (id) DO NOTHING
                """, p("id", id).addValue("name", "Tenant " + id).addValue("sub", "rls-" + id));
    }

    private void ensureUser(UUID id, UUID t) {
        jdbc.update("""
                INSERT INTO users (id, tenant_id, email, display_name, status, password_hash, created_at, updated_at)
                VALUES (:id, :t, :email, :name, 'ACTIVE', 'x', NOW(), NOW())
                ON CONFLICT (id) DO NOTHING
                """, p("id", id).addValue("t", t)
                .addValue("email", "rls-" + id + "@snad.test")
                .addValue("name", "RLS User"));
    }

    private void seedTask(UUID tenantId, UUID taskId) {
        transactions.executeWithoutResult(s -> {
            setGuc(tenantId);
            jdbc.update("""
                    INSERT INTO crm_tasks (id, tenant_id, version, title, status, priority,
                        created_by, updated_by, created_at, updated_at)
                    VALUES (:id, :t, 0, 'rls task', 'OPEN', 50,
                        :actor, :actor, NOW(), NOW())
                    """, p("id", taskId).addValue("t", tenantId).addValue("actor", tenantId));
        });
    }

    private void seedParticipant(UUID tenantId, UUID participantId, UUID entityId) {
        transactions.executeWithoutResult(s -> {
            setGuc(tenantId);
            jdbc.update("""
                    INSERT INTO crm_entity_participants
                        (id, tenant_id, version, entity_type, entity_id, user_id, role,
                         added_at, added_by)
                    VALUES (:id, :t, 0, 'TASK', :entityId, :userId, 'COLLABORATOR',
                        NOW(), :userId)
                    """, p("id", participantId)
                    .addValue("t", tenantId)
                    .addValue("entityId", entityId)
                    .addValue("userId", tenantId));
        });
    }

    private void seedTimeline(UUID tenantId, UUID timelineId, UUID subjectId) {
        transactions.executeWithoutResult(s -> {
            setGuc(tenantId);
            jdbc.update("""
                    INSERT INTO crm_timeline_events (id, tenant_id, subject_type, subject_id, event_type,
                        summary, source_type, source_id, occurred_at, created_by)
                    VALUES (:id, :t, 'TASK', :subjectId, 'rls.event',
                        'rls summary', 'COLLABORATION_PARTICIPANT', :subjectId, NOW(), :actor)
                    """, p("id", timelineId)
                    .addValue("t", tenantId)
                    .addValue("subjectId", subjectId)
                    .addValue("actor", tenantId));
        });
    }

    private void seedOutbox(UUID tenantId, UUID outboxId, UUID aggregateId) {
        transactions.executeWithoutResult(s -> {
            setGuc(tenantId);
            jdbc.update("""
                    INSERT INTO crm_event_outbox
                        (id, tenant_id, version, event_type, payload_json, schema_version,
                         status, attempt_count, available_at, created_at, updated_at,
                         aggregate_type, aggregate_id)
                    VALUES (:id, :t, 0, 'rls.outbox.event', '{}', 1,
                        'PENDING', 0, NOW(), NOW(), NOW(),
                        'TASK', :aggregateId)
                    """, p("id", outboxId)
                    .addValue("t", tenantId)
                    .addValue("aggregateId", aggregateId));
        });
    }

    private void verifyForceRls(String tableName) {
        Map<String, Object> row = jdbc.queryForObject("""
                SELECT c.relrowsecurity, c.relforcerowsecurity
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE c.relname = :table AND n.nspname = 'public'
                """, p("table", tableName),
                (rs, rowNum) -> {
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("rls", rs.getBoolean("relrowsecurity"));
                    m.put("force", rs.getBoolean("relforcerowsecurity"));
                    return m;
                });
        assertThat((Boolean) row.get("rls"))
                .as(tableName + " must have relrowsecurity=true")
                .isTrue();
        assertThat((Boolean) row.get("force"))
                .as(tableName + " must have relforcerowsecurity=true")
                .isTrue();
    }

    private void verifyFailClosedPolicy(String tableName) {
        List<String> policyQuals = jdbc.query("""
                SELECT qual FROM pg_policies
                WHERE schemaname = 'public' AND tablename = :table
                """, p("table", tableName),
                (rs, rowNum) -> rs.getString("qual"));
        assertThat(policyQuals)
                .as(tableName + " must have at least one RLS policy")
                .isNotEmpty();
        // Every policy qual must contain the fail-closed pattern.
        // (For these tables there's exactly one policy: tenant_isolation.)
        boolean allFailClosed = true;
        for (String qual : policyQuals) {
            if (qual == null) {
                allFailClosed = false;
                break;
            }
            // Must contain the canonical pattern.
            if (!qual.contains("tenant_id") || !qual.contains("current_setting")
                    || !qual.contains("app.tenant_id")) {
                allFailClosed = false;
                break;
            }
            // Must NOT contain the permissive-when-unset pattern
            // "IS NULL OR ..." which the legacy V20260730_1 used to allow.
            if (qual.toUpperCase().contains("IS NULL OR")) {
                allFailClosed = false;
                break;
            }
        }
        assertThat(allFailClosed)
                .as(tableName + " RLS policy must be fail-closed " +
                        "(tenant_id = current_setting('app.tenant_id', true)::UUID, " +
                        "no permissive-when-unset pattern). policies=" + policyQuals)
                .isTrue();
    }
}
