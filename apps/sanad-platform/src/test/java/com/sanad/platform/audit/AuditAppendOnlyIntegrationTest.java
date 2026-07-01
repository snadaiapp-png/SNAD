package com.sanad.platform.audit;

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
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Stage 05 §7 — Verifies the PostgreSQL append-only immutability triggers
 * on audit_events (V23 migration).
 *
 * <p>Uses the fixture DataSource (BYPASSRLS) so that RLS does not block
 * the DML — we want to verify that the TRIGGER itself fires and rejects
 * UPDATE, DELETE, and TRUNCATE. INSERT must succeed (append-only means
 * new rows are allowed; mutations are not).</p>
 */
@SpringBootTest
@Import({TenantFixtureDataSourceConfig.class, TenantFixtureSeederConfig.class})
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class AuditAppendOnlyIntegrationTest {

    @Autowired private TenantFixtureSeeder fixtureSeeder;

    @Autowired
    @Qualifier("tenantFixtureDataSource")
    private DataSource fixtureDataSource;

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

    /**
     * Inserts a minimal audit_events row directly via the fixture DS.
     * Returns the generated UUID so individual tests can target it.
     */
    private UUID insertAuditRow() {
        UUID id = UUID.randomUUID();
        fixtureJdbc.update(
                "INSERT INTO audit_events (id, tenant_id, actor_type, action, " +
                "resource_type, operation, outcome, occurred_at, recorded_at, " +
                "created_at, event_hash, previous_hash, hash_algorithm, schema_version) " +
                "VALUES (?, ?, 'USER', 'TEST.ACTION', 'TestResource', 'TEST', " +
                "'SUCCESS', NOW(), NOW(), NOW(), ?, '0000000000000000000000000000000000000000000000000000000000000000', 'SHA-256', 1)",
                id, fixture.tenantAId(), "a".repeat(64));
        return id;
    }

    @Test
    @DisplayName("auditInsert_succeeds: fixture DS can INSERT a new audit_events row")
    void auditInsert_succeeds() {
        UUID id = insertAuditRow();
        Integer count = fixtureJdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE id = ?", Integer.class, id);
        assertThat(count).as("inserted row must be visible").isEqualTo(1);
    }

    @Test
    @DisplayName("auditUpdate_rejected: fixture DS UPDATE on audit_events → exception containing 'append-only'")
    void auditUpdate_rejected() {
        UUID id = insertAuditRow();
        assertThatThrownBy(() ->
                fixtureJdbc.update(
                        "UPDATE audit_events SET action = 'TAMPERED' WHERE id = ?",
                        id))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    @DisplayName("auditDelete_rejected: fixture DS DELETE on audit_events → exception containing 'append-only'")
    void auditDelete_rejected() {
        UUID id = insertAuditRow();
        assertThatThrownBy(() ->
                fixtureJdbc.update("DELETE FROM audit_events WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    @DisplayName("auditTruncate_rejected: fixture DS TRUNCATE on audit_events → exception containing 'append-only'")
    void auditTruncate_rejected() {
        insertAuditRow();
        assertThatThrownBy(() ->
                fixtureJdbc.execute("TRUNCATE TABLE audit_events"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }
}
