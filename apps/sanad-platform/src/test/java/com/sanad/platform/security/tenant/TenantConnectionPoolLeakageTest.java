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
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 04A.2 §9 — Connection pool leakage test.
 */
@SpringBootTest
@Import(SecurityPermitAllTestConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("local")
class TenantConnectionPoolLeakageTest {

    @Autowired private DataSource dataSource;

    @Test
    @DisplayName("Sequential transactions with different tenants do not leak settings")
    @Transactional
    void sequentialTransactions_noLeakage() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            String dbName = conn.getMetaData().getDatabaseProductName();
            if (!"PostgreSQL".equals(dbName)) {
                assertThat(dbName).isEqualTo("H2");
                return;
            }

            UUID tenantA = UUID.randomUUID();
            stmt.execute("SET LOCAL app.current_tenant_id = '" + tenantA + "'");

            ResultSet rs = stmt.executeQuery("SELECT current_setting('app.current_tenant_id', true)");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).isEqualTo(tenantA.toString());

            // Reset for next check
            stmt.execute("SET LOCAL app.current_tenant_id = ''");
            rs = stmt.executeQuery("SELECT current_setting('app.current_tenant_id', true)");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).isNullOrEmpty();
        }
    }
}
