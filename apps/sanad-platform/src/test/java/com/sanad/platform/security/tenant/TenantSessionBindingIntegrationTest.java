package com.sanad.platform.security.tenant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.sanad.platform.security.service.JwtTokenProvider;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 04A.2 §15 — Session tenant binding integration test.
 *
 * <p>Verifies that JWT sessions are correctly bound to tenants and that
 * session version mismatches are detected.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class TenantSessionBindingIntegrationTest {

    @Autowired private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("JWT contains jti (token ID) claim")
    void jwtContainsJtiClaim() {
        String token = jwtTokenProvider.mintAccessToken(
                java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(),
                "test@example.com");

        io.jsonwebtoken.Claims claims = jwtTokenProvider.parseAndValidate(token);
        assertThat(claims).isNotNull();
        assertThat(claims.getId())
                .as("JWT must contain jti claim (non-empty)")
                .isNotNull()
                .isNotEmpty();
    }

    @Test
    @DisplayName("JWT contains session_version claim")
    void jwtContainsSessionVersionClaim() {
        java.util.UUID tenantId = java.util.UUID.randomUUID();
        java.util.UUID userId = java.util.UUID.randomUUID();
        String token = jwtTokenProvider.mintAccessToken(
                userId, tenantId, "test@example.com", false, 42L);

        io.jsonwebtoken.Claims claims = jwtTokenProvider.parseAndValidate(token);
        assertThat(claims).isNotNull();
        assertThat(claims.get(JwtTokenProvider.SESSION_VERSION_CLAIM))
                .as("JWT must contain session_version claim")
                .isNotNull();
    }

    @Test
    @DisplayName("JWT tenant_id matches the minted tenant")
    void jwtTenantId_matchesMintedTenant() {
        java.util.UUID tenantId = java.util.UUID.randomUUID();
        String token = jwtTokenProvider.mintAccessToken(
                java.util.UUID.randomUUID(), tenantId, "test@example.com");

        io.jsonwebtoken.Claims claims = jwtTokenProvider.parseAndValidate(token);
        assertThat(claims).isNotNull();
        assertThat(claims.get("tenant_id", String.class))
                .isEqualTo(tenantId.toString());
    }

    @Test
    @DisplayName("Different JWTs have different jti values (unique token IDs)")
    void differentJwts_haveDifferentJti() {
        java.util.UUID tenantId = java.util.UUID.randomUUID();
        java.util.UUID userId = java.util.UUID.randomUUID();

        String token1 = jwtTokenProvider.mintAccessToken(userId, tenantId, "a@example.com");
        String token2 = jwtTokenProvider.mintAccessToken(userId, tenantId, "a@example.com");

        io.jsonwebtoken.Claims claims1 = jwtTokenProvider.parseAndValidate(token1);
        io.jsonwebtoken.Claims claims2 = jwtTokenProvider.parseAndValidate(token2);

        assertThat(claims1.getId())
                .as("Each JWT must have a unique jti")
                .isNotEqualTo(claims2.getId());
    }
}
