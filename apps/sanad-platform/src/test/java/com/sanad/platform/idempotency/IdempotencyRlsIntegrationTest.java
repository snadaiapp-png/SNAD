package com.sanad.platform.idempotency;

import com.sanad.platform.security.tenant.support.TenantFixtureDataSourceConfig;
import com.sanad.platform.security.tenant.support.TenantFixtureSeeder;
import com.sanad.platform.security.tenant.support.TenantFixtureSeederConfig;
import com.sanad.platform.security.tenant.support.TenantTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Stage 05A.1 §14 + Stage 05A.2.1 §17 — Verifies Row-Level Security on
 * {@code idempotency_records}.
 *
 * <p>RLS isolates idempotency records per tenant. The runtime role
 * {@code sanad_runtime_app} (subject to FORCE RLS) sees only rows whose
 * {@code tenant_id} matches the {@code app.current_tenant_id} session
 * setting. A missing context yields zero rows, and cross-tenant INSERT
 * or UPDATE is rejected.</p>
 *
 * <p>Each RLS assertion uses a SINGLE JDBC transaction so that the
 * {@code set_config} call and the subsequent query are bound to the
 * same connection. The transaction is rolled back at the end so the
 * session setting does not leak into subsequent tests.</p>
 *
 * <p>Stage 05A.2 §3 — The fixture cleanup does NOT physically delete
 * tenants (FK RESTRICT from audit_events). Each test uses the fixture's
 * unique tenant IDs (UUID.randomUUID per test invocation).</p>
 *
 * <p>All DB reads/writes use {@link PreparedStatement}. Timestamps use
 * {@link Timestamp#from(Instant)}.</p>
 */
@SpringBootTest
@Import({TenantFixtureDataSourceConfig.class, TenantFixtureSeederConfig.class})
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class IdempotencyRlsIntegrationTest {

    @Autowired
    @Qualifier("tenantFixtureDataSource")
    private DataSource fixtureDataSource;

    /** Runtime DataSource — subject to RLS (sanad_runtime_app, no BYPASSRLS). */
    @Autowired
    private DataSource runtimeDataSource;

    @Autowired private TenantFixtureSeeder fixtureSeeder;

    private TenantTestFixture fixture;
    private JdbcTemplate fixtureJdbc;

    @BeforeEach
    void setUp() {
        fixture = fixtureSeeder.seedCrudFixture();
        fixtureJdbc = new JdbcTemplate(fixtureDataSource);
    }

    @AfterEach
    void tearDown() {
        fixtureSeeder.cleanup(fixture);
    }

    private UUID insertRecord(UUID tenantId, String key) {
        UUID id = UUID.randomUUID();
        String sql = "INSERT INTO idempotency_records (id, tenant_id, idempotency_key, "
                + "operation, route, request_fingerprint, status, expires_at, "
                + "created_at, updated_at, lease_owner_request_id, lease_expires_at, "
                + "attempt_count, last_attempt_at, lease_version) VALUES (?, ?, ?, "
                + "'ORGANIZATION.CREATE', '/api/v1/organizations', ?, 'COMPLETED', "
                + "?, ?, ?, ?, ?, 1, ?, 1)";
        Timestamp now = Timestamp.from(Instant.now());
        Timestamp expiresAt = Timestamp.from(Instant.now().plusSeconds(3600));
        fixtureJdbc.update(sql, id, tenantId, key, "a".repeat(64),
                expiresAt, now, now, "owner-" + UUID.randomUUID(), expiresAt, now);
        return id;
    }

    /**
     * Asserts that the runtime role is {@code sanad_runtime_app} and that
     * the {@code idempotency_records} table has both {@code relrowsecurity}
     * and {@code relforcerowsecurity} set to {@code true}.
     */
    @Test
    @DisplayName("rlsConfigured_currentUserIsRuntimeApp_andForceRlsEnabled: current_user=sanad_runtime_app, relrowsecurity=true, relforcerowsecurity=true")
    void rlsConfigured_currentUserIsRuntimeApp_andForceRlsEnabled() throws Exception {
        try (Connection conn = runtimeDataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // current_user must be sanad_runtime_app (the runtime role,
                // which is subject to RLS — no BYPASSRLS).
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT current_user")) {
                    try (ResultSet rs = ps.executeQuery()) {
                        assertThat(rs.next()).isTrue();
                        assertThat(rs.getString(1))
                                .as("runtime DataSource must connect as sanad_runtime_app "
                                        + "(no BYPASSRLS — subject to FORCE RLS)")
                                .isEqualTo("sanad_runtime_app");
                    }
                }

                // pg_class must show relrowsecurity=true AND relforcerowsecurity=true.
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT relrowsecurity, relforcerowsecurity "
                                + "FROM pg_class WHERE relname = 'idempotency_records'")) {
                    try (ResultSet rs = ps.executeQuery()) {
                        assertThat(rs.next())
                                .as("pg_class row for idempotency_records must exist")
                                .isTrue();
                        assertThat(rs.getBoolean("relrowsecurity"))
                                .as("idempotency_records must have relrowsecurity=true (RLS enabled)")
                                .isTrue();
                        assertThat(rs.getBoolean("relforcerowsecurity"))
                                .as("idempotency_records must have relforcerowsecurity=true "
                                        + "(FORCE RLS — even table owner is subject to RLS)")
                                .isTrue();
                    }
                }
            } finally {
                conn.rollback();
            }
        }
    }

    /**
     * Tenant A sees its own records but not Tenant B's records, when
     * querying via the runtime DataSource with {@code app.current_tenant_id}
     * set to Tenant A.
     */
    @Test
    @DisplayName("tenantA_seesOwnRecords_notTenantB: runtime DS with tenant=A → A visible, B invisible")
    void tenantA_seesOwnRecords_notTenantB() throws Exception {
        UUID recordA = insertRecord(fixture.tenantAId(), "rls-key-a");
        UUID recordB = insertRecord(fixture.tenantBId(), "rls-key-b");

        try (Connection conn = runtimeDataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Set tenant context to A.
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT set_config('app.current_tenant_id', ?, true)")) {
                    ps.setString(1, fixture.tenantAId().toString());
                    ps.executeQuery();
                }

                // A's record is visible.
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT COUNT(*) FROM idempotency_records WHERE id = ?")) {
                    ps.setObject(1, recordA);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        assertThat(rs.getInt(1))
                                .as("Tenant A's record must be visible under tenant A")
                                .isEqualTo(1);
                    }
                }

                // B's record is invisible.
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT COUNT(*) FROM idempotency_records WHERE id = ?")) {
                    ps.setObject(1, recordB);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        assertThat(rs.getInt(1))
                                .as("Tenant B's record must NOT be visible under tenant A (RLS)")
                                .isEqualTo(0);
                    }
                }
            } finally {
                conn.rollback();
            }
        }
    }

    /**
     * Without {@code app.current_tenant_id} set, RLS must return zero rows
     * (fail-closed) — no records leak when the context is missing.
     */
    @Test
    @DisplayName("missingTenantContext_zeroRows: runtime DS without tenant config → 0 rows visible (fail-closed)")
    void missingTenantContext_zeroRows() throws Exception {
        UUID recordA = insertRecord(fixture.tenantAId(), "rls-key-no-context");

        try (Connection conn = runtimeDataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Query WITHOUT setting app.current_tenant_id.
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT COUNT(*) FROM idempotency_records WHERE id = ?")) {
                    ps.setObject(1, recordA);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        int count = rs.getInt(1);
                        assertThat(count)
                                .as("without tenant context, 0 records must be visible (RLS fail-closed)")
                                .isEqualTo(0);
                    }
                }
            } finally {
                conn.rollback();
            }
        }
    }

    /**
     * Cross-tenant INSERT must be rejected by the RLS WITH CHECK clause.
     * A runtime connection scoped to Tenant A cannot INSERT a row whose
     * {@code tenant_id} is Tenant B.
     */
    @Test
    @DisplayName("crossTenantInsert_denied: runtime DS with tenant=A → INSERT with tenant_id=B rejected (RLS WITH CHECK)")
    void crossTenantInsert_denied() throws Exception {
        try (Connection conn = runtimeDataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Set tenant context to A.
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT set_config('app.current_tenant_id', ?, true)")) {
                    ps.setString(1, fixture.tenantAId().toString());
                    ps.executeQuery();
                }

                // Attempt to INSERT a row with tenant_id = B (cross-tenant).
                UUID crossId = UUID.randomUUID();
                Timestamp now = Timestamp.from(Instant.now());
                Timestamp expiresAt = Timestamp.from(Instant.now().plusSeconds(3600));
                String insertSql = "INSERT INTO idempotency_records (id, tenant_id, "
                        + "idempotency_key, operation, route, request_fingerprint, "
                        + "status, expires_at, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'ORGANIZATION.CREATE', '/api/v1/organizations', "
                        + "?, 'PROCESSING', ?, ?, ?)";
                assertThatThrownBy(() -> {
                    try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                        ps.setObject(1, crossId);
                        ps.setObject(2, fixture.tenantBId()); // Wrong tenant!
                        ps.setString(3, "cross-tenant-insert-" + UUID.randomUUID());
                        ps.setString(4, "a".repeat(64));
                        ps.setTimestamp(5, expiresAt);
                        ps.setTimestamp(6, now);
                        ps.setTimestamp(7, now);
                        ps.executeUpdate();
                    }
                })
                        .as("cross-tenant INSERT must be denied by RLS WITH CHECK clause")
                        .isInstanceOf(SQLException.class);
            } finally {
                conn.rollback();
            }
        }
    }

    /**
     * Cross-tenant UPDATE must be rejected by RLS. A runtime connection
     * scoped to Tenant A cannot UPDATE a row whose {@code tenant_id} is
     * Tenant B (the row is invisible, so the UPDATE matches 0 rows; the
     * WITH CHECK clause also enforces this).
     */
    @Test
    @DisplayName("crossTenantUpdate_denied: runtime DS with tenant=A → UPDATE on tenant B row matches 0 rows (invisible)")
    void crossTenantUpdate_denied() throws Exception {
        UUID recordB = insertRecord(fixture.tenantBId(), "rls-key-update-b");

        try (Connection conn = runtimeDataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Set tenant context to A.
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT set_config('app.current_tenant_id', ?, true)")) {
                    ps.setString(1, fixture.tenantAId().toString());
                    ps.executeQuery();
                }

                // Attempt to UPDATE Tenant B's record (invisible under tenant A).
                String updateSql = "UPDATE idempotency_records SET status = 'FAILED_FINAL' "
                        + "WHERE id = ?";
                int updated;
                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setObject(1, recordB);
                    updated = ps.executeUpdate();
                }
                assertThat(updated)
                        .as("cross-tenant UPDATE must match 0 rows (RLS hides Tenant B's record from Tenant A)")
                        .isEqualTo(0);

                // Verify the record was NOT modified (still COMPLETED).
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT status FROM idempotency_records WHERE id = ?")) {
                    ps.setObject(1, recordB);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        assertThat(rs.getString("status"))
                                .as("Tenant B's record must remain COMPLETED after cross-tenant UPDATE attempt")
                                .isEqualTo("COMPLETED");
                    }
                }
            } finally {
                conn.rollback();
            }
        }
    }
}
