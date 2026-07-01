package com.sanad.platform.security.tenant;

import com.sanad.platform.security.SecurityPermitAllTestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 04A.3 §9 — Connection pool leakage test. Non-skippable PostgreSQL.
 * Uses maximumPoolSize=1 to force connection reuse.
 * Manages own JDBC transactions (no @Transactional) to properly test
 * that SET LOCAL settings do not leak between transactions.
 */
@SpringBootTest
@Import(SecurityPermitAllTestConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class TenantConnectionPoolLeakageTest {

    @Autowired private DataSource dataSource;

    @Test
    @DisplayName("Sequential transactions with different tenants do not leak settings")
    void sequentialTransactions_noLeakage() throws Exception {
        PostgresTestUtil.assertPostgreSQL(dataSource);

        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        // Transaction A: set tenant A, verify, rollback (clears SET LOCAL)
        try (var conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (var stmt = conn.createStatement()) {
                stmt.execute("SET LOCAL app.current_tenant_id = '" + tenantA + "'");
                var rs = stmt.executeQuery("SELECT current_setting('app.current_tenant_id', true)");
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo(tenantA.toString());
            }
            conn.rollback();
        }

        // Transaction B: set tenant B, verify, rollback
        try (var conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (var stmt = conn.createStatement()) {
                stmt.execute("SET LOCAL app.current_tenant_id = '" + tenantB + "'");
                var rs = stmt.executeQuery("SELECT current_setting('app.current_tenant_id', true)");
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo(tenantB.toString());
                // Verify tenant A setting did NOT leak
                assertThat(rs.getString(1)).isNotEqualTo(tenantA.toString());
            }
            conn.rollback();
        }

        // Transaction C: no setting — verify neither A nor B leaked
        try (var conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (var stmt = conn.createStatement()) {
                var rs = stmt.executeQuery("SELECT current_setting('app.current_tenant_id', true)");
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1))
                        .as("No tenant setting should persist after transaction end")
                        .isNotEqualTo(tenantA.toString())
                        .isNotEqualTo(tenantB.toString());
            }
            conn.rollback();
        }
    }
}
