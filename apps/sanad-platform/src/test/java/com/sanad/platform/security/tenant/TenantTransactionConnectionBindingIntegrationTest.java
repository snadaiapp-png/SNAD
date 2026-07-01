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
 *
 * Note: Uses @Transactional + EntityManager.doWork() to avoid the maximumPoolSize=1
 * deadlock (PostgresTestUtil.assertPostgreSQL would try to get a 2nd connection).
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
    @DisplayName("Database is PostgreSQL (non-skippable) — via EntityManager connection")
    @Transactional
    void databaseIsPostgreSQL() {
        var session = entityManager.unwrap(org.hibernate.Session.class);
        session.doWork(connection -> {
            String productName = connection.getMetaData().getDatabaseProductName();
            assertThat(productName)
                    .as("Mandatory tenant test requires PostgreSQL but found: " + productName)
                    .isEqualTo("PostgreSQL");
        });
    }

    @Test
    @DisplayName("SET config and repository query use same connection (same PID)")
    @Transactional
    void setConfigAndQuery_sameConnection() {
        UUID tenantId = UUID.randomUUID();

        var session = entityManager.unwrap(org.hibernate.Session.class);
        final String[] dbName = {null};
        final int[] pid1 = {0};
        final int[] pid2 = {0};
        final String[] setting = {null};

        session.doWork(connection -> {
            try {
                dbName[0] = connection.getMetaData().getDatabaseProductName();
            } catch (Exception e) {
                throw new AssertionError("Failed to get database product name", e);
            }

            assertThat(dbName[0])
                    .as("Mandatory tenant test requires PostgreSQL but found: " + dbName[0])
                    .isEqualTo("PostgreSQL");

            try (Statement stmt = connection.createStatement()) {
                stmt.execute("SET LOCAL app.current_tenant_id = '" + tenantId + "'");

                ResultSet rs = stmt.executeQuery("SELECT pg_backend_pid()");
                if (rs.next()) pid1[0] = rs.getInt(1);

                rs = stmt.executeQuery("SELECT current_setting('app.current_tenant_id', true)");
                if (rs.next()) setting[0] = rs.getString(1);

                rs = stmt.executeQuery("SELECT pg_backend_pid()");
                if (rs.next()) pid2[0] = rs.getInt(1);
            }
        });

        assertThat(pid1[0])
                .as("SET config PID must equal repository query PID (same connection)")
                .isEqualTo(pid2[0]);
        assertThat(setting[0])
                .as("current_setting must match TenantContext tenantId")
                .isEqualTo(tenantId.toString());
    }
}
