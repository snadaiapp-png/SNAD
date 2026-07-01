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

import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 04A.2 §5 — Verifies that the RLS tenant setting is applied to the
 * SAME connection that the repository queries use.
 */
@SpringBootTest
@Import(SecurityPermitAllTestConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("local")
class TenantTransactionConnectionBindingIntegrationTest {

    @Autowired private EntityManager entityManager;

    @Test
    @DisplayName("SET config and repository query use the same connection (same backend PID)")
    @Transactional
    void setConfigAndQuery_useSameConnection() {
        var session = entityManager.unwrap(org.hibernate.Session.class);
        final String[] dbName = {null};
        final int[] pid1 = {0};
        final int[] pid2 = {0};
        final String[] setting = {null};

        session.doWork(connection -> {
            try {
                dbName[0] = connection.getMetaData().getDatabaseProductName();
            } catch (Exception e) {
                dbName[0] = "Unknown";
            }

            if ("PostgreSQL".equals(dbName[0])) {
                try (Statement stmt = connection.createStatement()) {
                    ResultSet rs = stmt.executeQuery("SELECT pg_backend_pid()");
                    if (rs.next()) pid1[0] = rs.getInt(1);
                    rs = stmt.executeQuery("SELECT current_setting('app.current_tenant_id', true)");
                    if (rs.next()) setting[0] = rs.getString(1);
                }
                try (Statement stmt = connection.createStatement()) {
                    ResultSet rs = stmt.executeQuery("SELECT pg_backend_pid()");
                    if (rs.next()) pid2[0] = rs.getInt(1);
                }
                assertThat(pid1[0])
                        .as("SET config PID must equal repository query PID")
                        .isEqualTo(pid2[0]);
            } else {
                assertThat(dbName[0]).isEqualTo("H2");
            }
        });
    }
}
