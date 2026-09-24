package com.sanad.platform.crm.mobile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.mobile.conflict.service.ConflictService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G7 runtime test — OFF-006 / CONFLICT-006 (conflict retention: 1 year).
 *
 * Verifies against a live PostgreSQL that {@link ConflictService#expireOldConflicts()}
 * auto-resolves (SERVER_WINS) only the conflicts whose retention window has elapsed,
 * leaving fresh conflicts OPEN. Runs under the live app.tenant_id GUC so the FORCE
 * RLS tenant boundary (DEF-008 fix) is respected.
 *
 * <p>Env-gated: requires {@code SPRING_DATASOURCE_USERNAME}/{@code SPRING_DATASOURCE_PASSWORD}
 * (local PostgreSQL). In CI without these it is skipped — it never uses H2/Testcontainers/Docker.
 */
class G7ConflictRetentionRuntimeTest {

    private static final String URL =
        System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String USER = System.getenv("SPRING_DATASOURCE_USERNAME");
    private static final String PW = System.getenv("SPRING_DATASOURCE_PASSWORD");

    private SingleConnectionDataSource ds;
    private JdbcTemplate jdbc;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID deviceId = UUID.randomUUID();
    private final UUID expiredConflictId = UUID.randomUUID();
    private final UUID freshConflictId = UUID.randomUUID();

    private void connect() {
        Assumptions.assumeTrue(USER != null && PW != null,
            "Local PostgreSQL credentials not provided — skipping OFF-006/CONFLICT-006 runtime test");
        ds = new SingleConnectionDataSource();
        ds.setUrl(URL);
        ds.setUsername(USER);
        ds.setPassword(PW);
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setSuppressClose(true);
        jdbc = new JdbcTemplate(ds);
        // FORCE RLS scoping (DEF-008): set the tenant GUC on this connection so inserts/updates
        // are visible to the retention operation.
        jdbc.execute("SET app.tenant_id = '" + tenantId + "'");
    }

    @AfterEach
    void tearDown() {
        if (jdbc == null) return;
        try {
            jdbc.update("DELETE FROM mobile_conflict_log WHERE tenant_id = ?::uuid", tenantId.toString());
            jdbc.update("DELETE FROM mobile_device_registry WHERE tenant_id = ?::uuid", tenantId.toString());
            jdbc.update("DELETE FROM users WHERE tenant_id = ?::uuid", tenantId.toString());
            jdbc.update("DELETE FROM tenants WHERE id = ?::uuid", tenantId.toString());
        } catch (Exception ignored) {
        } finally {
            if (ds != null) ds.close();
        }
    }

    @Test
    void expireOldConflictsAutoResolvesOnlyExpiredOnes() {
        connect();
        // fixtures
        jdbc.update("""
            INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at,locale,timezone,currency_code)
            VALUES (?::uuid,'retention_probe','retention_probe','ACTIVE',NOW(),NOW(),'en','UTC','USD')
            """, tenantId.toString());
        jdbc.update("""
            INSERT INTO users (id,tenant_id,email,status,created_at,updated_at,must_change_password,session_version,platform_admin)
            VALUES (?::uuid,?::uuid,'retention@example.test','ACTIVE',NOW(),NOW(),false,0,false)
            """, userId.toString(), tenantId.toString());
        jdbc.update("""
            INSERT INTO mobile_device_registry (device_id,tenant_id,user_id,device_name,device_platform,registered_at,updated_at,is_active)
            VALUES (?::uuid,?::uuid,?::uuid,'d','ios',NOW(),NOW(),true)
            """, deviceId.toString(), tenantId.toString(), userId.toString());

        Timestamp expired = Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS));
        Timestamp fresh = Timestamp.from(Instant.now().plus(400, ChronoUnit.DAYS));
        insertConflict(expiredConflictId, expired);
        insertConflict(freshConflictId, fresh);

        int expiredCount = new ConflictService(jdbc, new ObjectMapper()).expireOldConflicts();

        assertTrue(expiredCount >= 1, "at least the expired probe conflict must be auto-resolved");

        Map<String, Object> expiredRow = jdbc.queryForMap(
            "SELECT status, resolution FROM mobile_conflict_log WHERE conflict_id = ?::uuid",
            expiredConflictId.toString());
        assertEquals("EXPIRED", expiredRow.get("status"), "expired conflict must be auto-resolved");
        assertEquals("SERVER_WINS", expiredRow.get("resolution"), "retention resolution must be SERVER_WINS");

        Map<String, Object> freshRow = jdbc.queryForMap(
            "SELECT status FROM mobile_conflict_log WHERE conflict_id = ?::uuid",
            freshConflictId.toString());
        assertEquals("OPEN", freshRow.get("status"), "fresh conflict must remain OPEN (within 1-year retention)");
    }

    private void insertConflict(UUID conflictId, Timestamp retentionExpiresAt) {
        jdbc.update("""
            INSERT INTO mobile_conflict_log
              (conflict_id, tenant_id, device_id, user_id, entity_type, entity_id, base_version,
               client_mutation, server_version, server_state, conflict_type, conflict_class,
               status, retention_expires_at, created_at, updated_at)
            VALUES (?::uuid, ?::uuid, ?::uuid, ?::uuid, 'account', ?::uuid, 1,
                    '{}'::jsonb, 2, '{}'::jsonb, 'FIELD_CONFLICT', 'C1',
                    'OPEN', ?, NOW(), NOW())
            """, conflictId.toString(), tenantId.toString(), deviceId.toString(), userId.toString(),
               conflictId.toString(), retentionExpiresAt);
    }
}
