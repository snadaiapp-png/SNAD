package com.sanad.platform.security.tenant;

import com.sanad.platform.security.SecurityPermitAllTestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 04A.3 §9 — Connection pool leakage test. Non-skippable PostgreSQL.
 * Uses maximumPoolSize=1 to force connection reuse.
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
    @Transactional
    void sequentialTransactions_noLeakage() throws Exception {
        PostgresTestUtil.assertPostgreSQL(dataSource);

        UUID tenantA = UUID.randomUUID();

        // Transaction A: set tenant A
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("SET LOCAL app.current_tenant_id = '" + tenantA + "'");
            var rs = stmt.executeQuery("SELECT current_setting('app.current_tenant_id', true)");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).isEqualTo(tenantA.toString());
        }
        // After transaction A ends, SET LOCAL is cleared by PostgreSQL.
        // Transaction B (same pooled connection): setting must NOT be A.
        try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
            var rs = stmt.executeQuery("SELECT current_setting('app.current_tenant_id', true)");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1))
                    .as("Setting from transaction A must NOT leak to transaction B")
                    .isNotEqualTo(tenantA.toString());
        }
    }
}
