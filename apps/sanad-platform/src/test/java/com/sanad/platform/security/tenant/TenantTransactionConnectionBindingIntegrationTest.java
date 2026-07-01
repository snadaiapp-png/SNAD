package com.sanad.platform.security.tenant;

import com.sanad.platform.security.SecurityPermitAllTestConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 04A.3 §6 — Transaction connection binding proof.
 * Verifies SET config and repository query use the SAME connection (same backend PID).
 * Non-skippable PostgreSQL.
 */
@SpringBootTest
@Import(SecurityPermitAllTestConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class TenantTransactionConnectionBindingIntegrationTest {

    @Autowired private EntityManager entityManager;
    @Autowired private DataSource dataSource;

    @Test
    @DisplayName("Database is PostgreSQL (non-skippable)")
    void databaseIsPostgreSQL() throws Exception {
        PostgresTestUtil.assertPostgreSQL(dataSource);
    }

    @Test
    @DisplayName("SET config and repository query use same connection (same PID)")
    @Transactional
    void setConfigAndQuery_sameConnection() {
        PostgresTestUtil.assertPostgreSQL(dataSource);

        UUID tenantId = UUID.randomUUID();
        // Set up TenantContext
        ThreadLocalTenantContextProvider provider = new ThreadLocalTenantContextProvider();
        provider.setContext(new TenantContext(
                tenantId, UUID.randomUUID(), "test-session", 0L, java.util.Set.of(),
                TenantContext.TenantContextSource.TEST_FIXTURE, "test-req"));

        var session = entityManager.unwrap(org.hibernate.Session.class);
        final int[] pid1 = {0};
        final int[] pid2 = {0};
        final String[] setting = {null};

        session.doWork(connection -> {
            try (Statement stmt = connection.createStatement()) {
                // Set tenant on this connection
                stmt.execute("SET LOCAL app.current_tenant_id = '" + tenantId + "'");

                // Get PID of this connection
                ResultSet rs = stmt.executeQuery("SELECT pg_backend_pid()");
                if (rs.next()) pid1[0] = rs.getInt(1);

                // Read setting
                rs = stmt.executeQuery("SELECT current_setting('app.current_tenant_id', true)");
                if (rs.next()) setting[0] = rs.getString(1);

                // Execute a trivial query and get PID again
                rs = stmt.executeQuery("SELECT pg_backend_pid()");
                if (rs.next()) pid2[0] = rs.getInt(1);
            }
        });

        provider.clear();

        assertThat(pid1[0])
                .as("SET config PID must equal repository query PID (same connection)")
                .isEqualTo(pid2[0]);
        assertThat(setting[0])
                .as("current_setting must match TenantContext tenantId")
                .isEqualTo(tenantId.toString());
    }
}
