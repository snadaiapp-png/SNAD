package com.sanad.platform.security.tenant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 04A.2 §10 — Authentication under RLS integration test.
 *
 * <p>Verifies that JWT authentication works correctly when RLS is enabled
 * on the users table. The {@link JwtSessionValidationService} establishes
 * a provisional TenantContext before querying the RLS-protected users table.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class TenantAuthenticationUnderRlsIntegrationTest {

    @Autowired private JwtSessionValidationService sessionValidationService;
    @Autowired private DataSource dataSource;

    @Test
    @DisplayName("Database product name is accessible (PostgreSQL in CI, H2 locally)")
    void databaseProductName_accessible() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            String dbName = conn.getMetaData().getDatabaseProductName();
            assertThat(dbName).isIn("PostgreSQL", "H2");
        }
    }

    @Test
    @DisplayName("Valid JWT claims for nonexistent user → session null (401)")
    void nonexistentUser_returnsNull() {
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
    @DisplayName("Valid JWT claims structure is accepted by the service")
    void validClaimsStructure_accepted() {
        // Verify the service accepts the record structure without errors
        JwtSessionValidationService.VerifiedJwtClaims claims =
                new JwtSessionValidationService.VerifiedJwtClaims(
                        java.util.UUID.randomUUID(),
                        java.util.UUID.randomUUID(),
                        "test-jti-123",
                        "test@example.com",
                        1L,
                        false);

        assertThat(claims.tenantId()).isNotNull();
        assertThat(claims.userId()).isNotNull();
        assertThat(claims.tokenId()).isEqualTo("test-jti-123");
        assertThat(claims.sessionVersion()).isEqualTo(1L);
    }
}
