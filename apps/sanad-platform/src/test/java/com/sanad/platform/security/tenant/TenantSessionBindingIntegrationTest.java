package com.sanad.platform.security.tenant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.sanad.platform.security.service.JwtTokenProvider;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 04A.3 §12 — Session tenant binding. Non-skippable PostgreSQL.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class TenantSessionBindingIntegrationTest {

    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private DataSource dataSource;

    @Test
    @DisplayName("Database is PostgreSQL (non-skippable)")
    void databaseIsPostgreSQL() throws Exception {
        PostgresTestUtil.assertPostgreSQL(dataSource);
    }

    @Test
    @DisplayName("JWT contains jti (token ID) claim — non-empty")
    void jwtContainsJtiClaim() {
        PostgresTestUtil.assertPostgreSQL(dataSource);

        String token = jwtTokenProvider.mintAccessToken(
                UUID.randomUUID(), UUID.randomUUID(), "test@example.com");
        io.jsonwebtoken.Claims claims = jwtTokenProvider.parseAndValidate(token);
        assertThat(claims).isNotNull();
        assertThat(claims.getId())
                .as("JWT must contain non-empty jti")
                .isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("JWT session_version matches minted value")
    void jwtSessionVersion_matches() {
        PostgresTestUtil.assertPostgreSQL(dataSource);

        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String token = jwtTokenProvider.mintAccessToken(userId, tenantId, "t@e.com", false, 42L);
        io.jsonwebtoken.Claims claims = jwtTokenProvider.parseAndValidate(token);
        assertThat(claims).isNotNull();
        assertThat(claims.get(JwtTokenProvider.SESSION_VERSION_CLAIM))
                .as("session_version must be 42").isEqualTo(42);
    }

    @Test
    @DisplayName("JWT tenant_id matches minted tenant")
    void jwtTenantId_matches() {
        PostgresTestUtil.assertPostgreSQL(dataSource);

        UUID tenantId = UUID.randomUUID();
        String token = jwtTokenProvider.mintAccessToken(UUID.randomUUID(), tenantId, "t@e.com");
        io.jsonwebtoken.Claims claims = jwtTokenProvider.parseAndValidate(token);
        assertThat(claims).isNotNull();
        assertThat(claims.get("tenant_id", String.class)).isEqualTo(tenantId.toString());
    }

    @Test
    @DisplayName("Different JWTs have different jti values")
    void differentJwts_differentJti() {
        PostgresTestUtil.assertPostgreSQL(dataSource);

        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String t1 = jwtTokenProvider.mintAccessToken(userId, tenantId, "a@e.com");
        String t2 = jwtTokenProvider.mintAccessToken(userId, tenantId, "a@e.com");
        io.jsonwebtoken.Claims c1 = jwtTokenProvider.parseAndValidate(t1);
        io.jsonwebtoken.Claims c2 = jwtTokenProvider.parseAndValidate(t2);
        assertThat(c1.getId()).isNotEqualTo(c2.getId());
    }
}
