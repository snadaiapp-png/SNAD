package com.sanad.platform.partner;

import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W2-T1 Partner Control Plane Foundation — partner principal schema contract.
 *
 * RED-FIRST TDD: This test is written BEFORE the V20261002_2 migration that
 * creates the `partners` table. When run against the current main (which
 * does NOT have the partners table), every test method should fail with
 * "relation does not exist" (PSQLSTATE 42P01). This is the expected RED
 * state — the failure is feature-missing, not a harness error.
 *
 * After V20261002_2 is applied, all tests should pass (GREEN).
 */
class PartnerPrincipalSchemaPostgresTest {

    private static final String SOURCE_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");

    @BeforeAll
    static void requirePostgreSqlDirect() {
        boolean available;
        try {
            available = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "PartnerPrincipalSchemaPostgresTest");
        } catch (Throwable ignored) {
            available = false;
        }
        Assumptions.assumeTrue(available,
                "PostgreSQL Direct is required for PartnerPrincipalSchemaPostgresTest");
        MigrationTestSchemaSupport.ensureDatabase(SOURCE_URL, USER, PASSWORD);
    }

    private void migrate() {
        Flyway.configure()
                .dataSource(isolatedUrl(), USER, PASSWORD)
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .baselineOnMigrate(true)
                .outOfOrder(true)
                .load()
                .migrate();
    }

    private String isolatedUrl() {
        return MigrationTestSchemaSupport.getIsolatedJdbcUrl(SOURCE_URL);
    }

    // ========================================================================
    // TEST 1: partners table exists
    // ========================================================================
    @Test
    void partnersTableExists() throws Exception {
        migrate();
        try (Connection connection = DriverManager.getConnection(isolatedUrl(), USER, PASSWORD);
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT to_regclass('public.partners') AS exists")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("exists"))
                    .as("partners table must exist after W2-T1 migration")
                    .isEqualTo("partners");
        }
    }

    // ========================================================================
    // TEST 2: partner is NOT represented as tenant
    //         (partners is a platform-level table, not tenant-scoped)
    // ========================================================================
    @Test
    void partnerIsNotRepresentedAsTenant() throws Exception {
        migrate();
        try (Connection connection = DriverManager.getConnection(isolatedUrl(), USER, PASSWORD);
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT column_name FROM information_schema.columns " +
                     "WHERE table_name = 'partners' AND column_name = 'tenant_id'")) {
            assertThat(rs.next())
                    .as("partners table must NOT have tenant_id column — " +
                        "partner is an independent principal, not a tenant")
                    .isFalse();
        }
    }

    // ========================================================================
    // TEST 3: valid partner statuses
    // ========================================================================
    @Test
    void validPartnerStatusesAccepted() throws Exception {
        migrate();
        try (Connection connection = DriverManager.getConnection(isolatedUrl(), USER, PASSWORD)) {
            connection.setAutoCommit(false);
            UUID created = UUID.randomUUID();
            for (String status : new String[]{"ACTIVE", "INACTIVE", "SUSPENDED", "TERMINATED"}) {
                // Valid construction: SUSPENDED/TERMINATED rows must carry the
                // lifecycle timestamp + reason required by the migration's CHECK
                // constraints (ck_partners_suspended_when_suspended_status and
                // ck_partners_terminated_when_terminated_status).
                String sql;
                if ("SUSPENDED".equals(status)) {
                    sql = "INSERT INTO partners (id, partner_type, status, created_by, " +
                          "suspended_at, suspended_reason) " +
                          "VALUES (?, 'PARTNER', ?, ?, NOW(), 'valid suspended state')";
                } else if ("TERMINATED".equals(status)) {
                    sql = "INSERT INTO partners (id, partner_type, status, created_by, " +
                          "terminated_at, terminated_reason) " +
                          "VALUES (?, 'PARTNER', ?, ?, NOW(), 'valid terminated state')";
                } else {
                    sql = "INSERT INTO partners (id, partner_type, status, created_by) " +
                          "VALUES (?, 'PARTNER', ?, ?)";
                }
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setString(2, status);
                    ps.setObject(3, created);
                    ps.executeUpdate();
                }
            }
            connection.rollback(); // clean up
        }
    }

    @Test
    void invalidPartnerStatusRejected() throws Exception {
        migrate();
        try (Connection connection = DriverManager.getConnection(isolatedUrl(), USER, PASSWORD)) {
            connection.setAutoCommit(false);
            assertThatThrownBy(() -> {
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO partners (id, partner_type, status, created_by) " +
                        "VALUES (?, 'PARTNER', 'FROZEN', ?)")) {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, UUID.randomUUID());
                    ps.executeUpdate();
                }
            })
                    .as("invalid status 'FROZEN' must be rejected by CHECK constraint")
                    .isInstanceOf(SQLException.class);
            connection.rollback();
        }
    }

    // ========================================================================
    // TEST 4: valid partner types
    // ========================================================================
    @Test
    void validPartnerTypesAccepted() throws Exception {
        migrate();
        try (Connection connection = DriverManager.getConnection(isolatedUrl(), USER, PASSWORD)) {
            connection.setAutoCommit(false);
            for (String type : new String[]{
                    "PARTNER", "AGENT", "RESELLER", "DISTRIBUTOR", "SELLER"}) {
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO partners (id, partner_type, status, created_by) " +
                        "VALUES (?, ?, 'ACTIVE', ?)")) {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setString(2, type);
                    ps.setObject(3, UUID.randomUUID());
                    ps.executeUpdate();
                }
            }
            connection.rollback();
        }
    }

    @Test
    void invalidPartnerTypeRejected() throws Exception {
        migrate();
        try (Connection connection = DriverManager.getConnection(isolatedUrl(), USER, PASSWORD)) {
            connection.setAutoCommit(false);
            assertThatThrownBy(() -> {
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO partners (id, partner_type, status, created_by) " +
                        "VALUES (?, 'VENDOR', 'ACTIVE', ?)")) {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, UUID.randomUUID());
                    ps.executeUpdate();
                }
            })
                    .as("invalid partner_type 'VENDOR' must be rejected")
                    .isInstanceOf(SQLException.class);
            connection.rollback();
        }
    }

    // ========================================================================
    // TEST 5: canonical UUID identity
    // ========================================================================
    @Test
    void canonicalUuidIdentity() throws Exception {
        migrate();
        try (Connection connection = DriverManager.getConnection(isolatedUrl(), USER, PASSWORD)) {
            connection.setAutoCommit(false);
            UUID partnerId = UUID.randomUUID();
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO partners (id, partner_type, status, created_by) " +
                    "VALUES (?, 'PARTNER', 'ACTIVE', ?)")) {
                ps.setObject(1, partnerId);
                ps.setObject(2, UUID.randomUUID());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT id FROM partners WHERE id = ?")) {
                ps.setObject(1, partnerId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat((UUID) rs.getObject("id"))
                            .as("canonical UUID identity is preserved")
                            .isEqualTo(partnerId);
                }
            }
            connection.rollback();
        }
    }

    // ========================================================================
    // TEST 6: authority version field exists and defaults to 0
    // ========================================================================
    @Test
    void authorityVersionFieldExistsAndDefaultsToZero() throws Exception {
        migrate();
        try (Connection connection = DriverManager.getConnection(isolatedUrl(), USER, PASSWORD)) {
            connection.setAutoCommit(false);
            UUID partnerId = UUID.randomUUID();
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO partners (id, partner_type, status, created_by) " +
                    "VALUES (?, 'PARTNER', 'ACTIVE', ?)")) {
                ps.setObject(1, partnerId);
                ps.setObject(2, UUID.randomUUID());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT version FROM partners WHERE id = ?")) {
                ps.setObject(1, partnerId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt("version"))
                            .as("version must default to 0")
                            .isEqualTo(0);
                }
            }
            connection.rollback();
        }
    }

    // ========================================================================
    // TEST 7: no unsupported global legal-name uniqueness
    //         (W2-T1 uses UUID identity, NOT human-readable name uniqueness)
    // ========================================================================
    @Test
    void noGlobalLegalNameUniquenessConstraint() throws Exception {
        migrate();
        try (Connection connection = DriverManager.getConnection(isolatedUrl(), USER, PASSWORD)) {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery(
                    "SELECT conname FROM pg_constraint " +
                    "WHERE conrelid = 'partners'::regclass " +
                    "AND contype = 'u' " +
                    "AND conname LIKE '%legal_name%'");
            assertThat(rs.next())
                    .as("partners table must NOT have a UNIQUE constraint on legal_name " +
                        "— W2-T1 uses UUID identity, not human-readable name uniqueness")
                    .isFalse();
        }
    }

    // ========================================================================
    // TEST 8: audit/invalidation contract
    //         (authorization_change_events emitted on status change — reuses
    //          W1 V20261001_1 audit infra with target_type='PARTNER')
    // ========================================================================
    @Test
    void auditEventEmittedOnStatusChange() throws Exception {
        migrate();
        try (Connection connection = DriverManager.getConnection(isolatedUrl(), USER, PASSWORD)) {
            connection.setAutoCommit(false);
            // Platform-level audit context: the W1 RLS policy on
            // authorization_change_events (V20261001_1) admits tenant_id IS NULL
            // rows only when app.tenant_id is the platform sentinel and
            // app.partner_id is unset. The partner audit trigger inserts
            // NULL-tenant platform events, so the session must carry the
            // platform context before triggering the audit write.
            try (Statement guc = connection.createStatement()) {
                guc.execute("SELECT set_config('app.tenant_id', " +
                            "'00000000-0000-0000-0000-000000000001', false)");
            }
            UUID partnerId = UUID.randomUUID();
            UUID actorId = UUID.randomUUID();
            // Create partner (ACTIVE)
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO partners (id, partner_type, status, created_by, updated_by) " +
                    "VALUES (?, 'PARTNER', 'ACTIVE', ?, ?)")) {
                ps.setObject(1, partnerId);
                ps.setObject(2, actorId);
                ps.setObject(3, actorId);
                ps.executeUpdate();
            }
            // Suspend partner
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE partners SET status = 'SUSPENDED', suspended_at = NOW(), " +
                    "suspended_reason = 'test suspension', updated_by = ?, version = version + 1 " +
                    "WHERE id = ?")) {
                ps.setObject(1, actorId);
                ps.setObject(2, partnerId);
                ps.executeUpdate();
            }
            // Verify audit event was emitted
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT event_type, target_type, target_id FROM authorization_change_events " +
                    "WHERE target_id = ? AND target_type = 'PARTNER' ORDER BY created_at DESC LIMIT 1")) {
                ps.setObject(1, partnerId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next())
                            .as("authorization_change_events must have an entry for the " +
                                "partner status change with target_type='PARTNER'")
                            .isTrue();
                    assertThat(rs.getString("event_type"))
                            .as("event_type must be PARTNER_SUSPENDED")
                            .isEqualTo("PARTNER_SUSPENDED");
                }
            }
            connection.rollback();
        }
    }

    // ========================================================================
    // TEST 9: security access model — fail-closed
    //         (partners table is platform-level — tenant GUC must NOT grant
    //          access. This is a negative test: even with app.tenant_id set,
    //          the partners table is accessible (because no RLS), but the
    //          APPLICATION LAYER enforces @RequireCapability. This test
    //          verifies the schema-level decision: no RLS, no tenant_id column.)
    // ========================================================================
    @Test
    void noTenantIdColumnAndNoRlsPolicy() throws Exception {
        migrate();
        try (Connection connection = DriverManager.getConnection(isolatedUrl(), USER, PASSWORD)) {
            // Verify no tenant_id column
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery(
                    "SELECT count(*) FROM information_schema.columns " +
                    "WHERE table_name = 'partners' AND column_name = 'tenant_id'");
            rs.next();
            assertThat(rs.getInt(1))
                    .as("partners table must NOT have tenant_id column — " +
                        "partner is platform-level, not tenant-scoped")
                    .isZero();

            // Verify no RLS policy (partners is platform catalog, like access_capabilities)
            rs = stmt.executeQuery(
                    "SELECT count(*) FROM pg_policies WHERE tablename = 'partners'");
            rs.next();
            assertThat(rs.getInt(1))
                    .as("partners table must NOT have RLS policies — " +
                        "platform-level catalog (mirrors access_capabilities). " +
                        "Application-layer @RequireCapability is authoritative.")
                    .isZero();
        }
    }

    // ========================================================================
    // TEST 10: suspended/terminated timestamp alignment
    //          (CHECK constraints enforce consistency)
    // ========================================================================
    @Test
    void suspendedTimestampRequiredWhenStatusSuspended() throws Exception {
        migrate();
        try (Connection connection = DriverManager.getConnection(isolatedUrl(), USER, PASSWORD)) {
            connection.setAutoCommit(false);
            assertThatThrownBy(() -> {
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO partners (id, partner_type, status, created_by) " +
                        "VALUES (?, 'PARTNER', 'SUSPENDED', ?)")) {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, UUID.randomUUID());
                    ps.executeUpdate();
                }
            })
                    .as("status=SUSPENDED requires suspended_at to be set (CHECK constraint)")
                    .isInstanceOf(SQLException.class);
            connection.rollback();
        }
    }
}
