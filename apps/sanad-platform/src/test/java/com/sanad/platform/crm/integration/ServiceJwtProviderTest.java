package com.sanad.platform.crm.integration;

import com.sanad.platform.crm.integration.security.ServiceJwtProvider;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceJwtProviderTest {

    private static final String SECRET = "crm-009-service-auth-test-secret-0123456789";

    @Test
    void mintsAndValidatesTenantBoundClaims() {
        ServiceJwtProvider provider = new ServiceJwtProvider(
                SECRET, "sanad-platform", "sanad-crm", 60);
        UUID tenantId = UUID.randomUUID();

        String token = provider.mint(tenantId, "corr-1", "1.0", "workflow-engine");
        ServiceJwtProvider.ValidatedServiceToken validated =
                provider.validate(token, "workflow-engine");

        assertThat(validated.serviceName()).isEqualTo("sanad-crm");
        assertThat(validated.tenantId()).isEqualTo(tenantId);
        assertThat(validated.correlationId()).isEqualTo("corr-1");
        assertThat(validated.contractVersion()).isEqualTo("1.0");
        assertThat(validated.audience()).isEqualTo("workflow-engine");
        assertThat(validated.jti()).isNotBlank();
        assertThat(validated.expiresAt()).isAfter(validated.issuedAt());
    }

    @Test
    void rejectsWrongAudience() {
        ServiceJwtProvider provider = new ServiceJwtProvider(
                SECRET, "sanad-platform", "sanad-crm", 60);
        String token = provider.mint(
                UUID.randomUUID(), "corr-1", "1.0", "workflow-engine");

        assertThatThrownBy(() -> provider.validate(token, "ai-gateway"))
                .isInstanceOf(ServiceJwtProvider.ServiceAuthenticationException.class)
                .hasMessageContaining("SERVICE_TOKEN_AUDIENCE_INVALID");
    }

    @Test
    void rejectsTamperedToken() {
        ServiceJwtProvider provider = new ServiceJwtProvider(
                SECRET, "sanad-platform", "sanad-crm", 60);
        String token = provider.mint(
                UUID.randomUUID(), "corr-1", "1.0", "workflow-engine");

        assertThatThrownBy(() -> provider.validate(token + "x", "workflow-engine"))
                .isInstanceOf(ServiceJwtProvider.ServiceAuthenticationException.class)
                .hasMessageContaining("SERVICE_TOKEN_INVALID");
    }

    @Test
    void rejectsShortConfiguredSecret() {
        assertThatThrownBy(() -> new ServiceJwtProvider(
                "short", "sanad-platform", "sanad-crm", 60))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }
}
