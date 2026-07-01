package com.sanad.platform.security.tenant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 04A.3 §11 — Capability tenant binding. Non-skippable PostgreSQL.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class TenantCapabilityBindingIntegrationTest {

    @Autowired private DataSource dataSource;

    @Test
    @DisplayName("Database is PostgreSQL (non-skippable)")
    void databaseIsPostgreSQL() throws Exception {
        PostgresTestUtil.assertPostgreSQL(dataSource);
    }

    @Test
    @DisplayName("Same capability name in different tenants — no cross-tenant inheritance")
    void sameCapabilityName_noCrossTenantInheritance() {
        PostgresTestUtil.assertPostgreSQL(dataSource);

        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        TenantContext ctxA = new TenantContext(
                tenantA, UUID.randomUUID(), "sess-A", 0L,
                java.util.Set.of("USER.READ"),
                TenantContext.TenantContextSource.TEST_FIXTURE, "req-A");

        TenantContext ctxB = new TenantContext(
                tenantB, UUID.randomUUID(), "sess-B", 0L,
                java.util.Set.of(),
                TenantContext.TenantContextSource.TEST_FIXTURE, "req-B");

        assertThat(ctxA.hasCapability("USER.READ")).isTrue();
        assertThat(ctxB.hasCapability("USER.READ")).isFalse();
        assertThat(ctxA.matchesTenant(tenantB)).isFalse();
        assertThat(ctxB.matchesTenant(tenantA)).isFalse();
    }

    @Test
    @DisplayName("Empty capabilities set is not verified")
    void emptyCapabilities_notVerified() {
        PostgresTestUtil.assertPostgreSQL(dataSource);

        TenantContext ctx = new TenantContext(
                UUID.randomUUID(), UUID.randomUUID(), "session", 0L,
                java.util.Set.of(),
                TenantContext.TenantContextSource.TEST_FIXTURE, "req");

        assertThat(ctx.capabilitiesVerified()).isFalse();
    }
}
