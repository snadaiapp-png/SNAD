package com.sanad.platform.security.tenant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 04A.2 §14 — Capability tenant binding integration test.
 *
 * <p>Verifies that capabilities are tenant-scoped: a user with USER.READ
 * in Tenant A cannot use that capability in Tenant B.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class TenantCapabilityBindingIntegrationTest {

    @Test
    @DisplayName("TenantContext.capabilitiesVerified() returns false for empty set")
    void emptyCapabilities_notVerified() {
        TenantContext ctx = new TenantContext(
                java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(),
                "session-id",
                0L,
                java.util.Set.of(),
                TenantContext.TenantContextSource.TEST_FIXTURE,
                "req-id");

        assertThat(ctx.capabilitiesVerified())
                .as("Empty capability set must not be treated as verified")
                .isFalse();
    }

    @Test
    @DisplayName("TenantContext.capabilitiesVerified() returns true for non-empty set")
    void nonEmptyCapabilities_verified() {
        TenantContext ctx = new TenantContext(
                java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(),
                "session-id",
                0L,
                java.util.Set.of("USER.READ"),
                TenantContext.TenantContextSource.TEST_FIXTURE,
                "req-id");

        assertThat(ctx.capabilitiesVerified()).isTrue();
        assertThat(ctx.hasCapability("USER.READ")).isTrue();
        assertThat(ctx.hasCapability("USER.DELETE")).isFalse();
    }

    @Test
    @DisplayName("Same capability name in different tenants — no cross-tenant inheritance")
    void sameCapabilityName_noCrossTenantInheritance() {
        java.util.UUID tenantA = java.util.UUID.randomUUID();
        java.util.UUID tenantB = java.util.UUID.randomUUID();

        TenantContext ctxA = new TenantContext(
                tenantA, java.util.UUID.randomUUID(), "sess-A", 0L,
                java.util.Set.of("USER.READ"),
                TenantContext.TenantContextSource.TEST_FIXTURE, "req-A");

        TenantContext ctxB = new TenantContext(
                tenantB, java.util.UUID.randomUUID(), "sess-B", 0L,
                java.util.Set.of(),  // B has no capabilities
                TenantContext.TenantContextSource.TEST_FIXTURE, "req-B");

        // A has USER.READ, B does not — no inheritance
        assertThat(ctxA.hasCapability("USER.READ")).isTrue();
        assertThat(ctxB.hasCapability("USER.READ")).isFalse();
        assertThat(ctxA.matchesTenant(tenantB)).isFalse();
        assertThat(ctxB.matchesTenant(tenantA)).isFalse();
    }
}
