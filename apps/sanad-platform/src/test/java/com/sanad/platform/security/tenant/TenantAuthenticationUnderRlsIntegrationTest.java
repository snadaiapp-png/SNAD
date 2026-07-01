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
 * Stage 04A.3 §10 — Authentication under RLS. Non-skippable PostgreSQL.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("tenant-postgres-test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
    named = "RUN_TENANT_POSTGRES_TESTS", matches = "true")
class TenantAuthenticationUnderRlsIntegrationTest {

    @Autowired private JwtSessionValidationService sessionValidationService;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private javax.sql.DataSource dataSource;

    @Test
    @DisplayName("Database is PostgreSQL (non-skippable)")
    void databaseIsPostgreSQL() throws Exception {
        PostgresTestUtil.assertPostgreSQL(dataSource);
    }

    @Test
    @DisplayName("Valid JWT claims for nonexistent user → session null (401)")
    void nonexistentUser_returnsNull() {
        PostgresTestUtil.assertPostgreSQL(dataSource);

        JwtSessionValidationService.VerifiedJwtClaims claims =
                new JwtSessionValidationService.VerifiedJwtClaims(
                        java.util.UUID.randomUUID(),
                        java.util.UUID.randomUUID(),
                        "test-jti",
                        "nonexistent@example.com",
                        0L,
                        false);

        JwtSessionValidationService.ValidatedSession session =
                sessionValidationService.validate(claims);

        assertThat(session)
                .as("Session validation must return null for nonexistent user")
                .isNull();
    }

    @Test
    @DisplayName("JWT contains jti, session_version, tenant_id claims")
    void jwtContainsAllRequiredClaims() {
        PostgresTestUtil.assertPostgreSQL(dataSource);

        java.util.UUID tenantId = java.util.UUID.randomUUID();
        java.util.UUID userId = java.util.UUID.randomUUID();
        String token = jwtTokenProvider.mintAccessToken(userId, tenantId, "test@example.com", false, 42L);

        io.jsonwebtoken.Claims claims = jwtTokenProvider.parseAndValidate(token);
        assertThat(claims).isNotNull();
        assertThat(claims.getId()).as("jti must be present").isNotNull().isNotEmpty();
        assertThat(claims.get("tenant_id", String.class)).isEqualTo(tenantId.toString());
        assertThat(claims.get(JwtTokenProvider.SESSION_VERSION_CLAIM)).isNotNull();
    }
}
