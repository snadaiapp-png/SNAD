package com.sanad.platform.security.rls;

import com.sanad.platform.security.SecurityPermitAllTestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Cause B verification — Spring Security's STATELESS session policy clears
 * {@link SecurityContextHolder} after {@code mockMvc.perform()} returns, but
 * Hibernate's deferred INSERT flushes LATER (during a subsequent query).
 * The {@link TenantRlsConnectionHandler} then sees
 * {@code tenantAppliedFor=tenantId} (set during mockMvc.perform) !=
 * {@code currentTenantId()=NULL} (cleared by STATELESS filter) and throws
 * "Tenant identity changed inside an active RLS transaction".
 *
 * <h3>Hypothesis to prove</h3>
 * <ol>
 *   <li>{@code mockMvc.perform(get(...).with(authentication(auth(tenantId, userId))))}
 *       sets SecurityContextHolder=tenantId BEFORE the request.
 *   <li>During the request, the handler fires and sets
 *       {@code tenantAppliedFor=tenantId} on the tx-bound Connection.
 *   <li>AFTER {@code mockMvc.perform()} returns, Spring Security's STATELESS
 *       session policy clears SecurityContextHolder (NullSecurityContextRepository
 *       saves NULL).
 *   <li>The next JdbcTemplate.query(...) call (in the same @Transactional tx)
 *       triggers Hibernate's auto-flush of pending INSERTs. The handler fires:
 *       {@code tenantAppliedFor=tenantId} != {@code currentTenantId()=NULL} →
 *       THROWS "Tenant identity changed".
 *   <li>This proves: re-binding SecurityContextHolder is necessary BEFORE any
 *       post-mockMvc DB query to restore the context that the STATELESS filter
 *       cleared.
 * </ol>
 *
 * <p>The test uses real PostgreSQL + real Spring Security filter chain +
 * {@link SecurityPermitAllTestConfig} (the same config used by
 * {@code UserRepositoryTest}, {@code AccessPersistenceIntegrationTest}, etc.).
 * No mocks of the handler or filter chain.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
@org.springframework.transaction.annotation.Transactional
class CauseBSecurityContextClearingTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;

    /**
     * Prove B1: a post-{@code mockMvc.perform} DB query in the SAME @Transactional
     * tx throws "Tenant identity changed" because the STATELESS filter cleared
     * SecurityContextHolder after mockMvc.perform returned.
     *
     * <p>This is the structural cause for the failures in
     * {@code SalesQualificationBusinessProcessE2ETest},
     * {@code UserApiMutationIntegrationTest},
     * {@code UserApiPersistenceIntegrationTest},
     * {@code UserPhase7AuditPersistenceIntegrationTest},
     * {@code AuthApiIntegrationTest} (test method failures),
     * {@code CredentialRotationIntegrationTest}.
     */
    @Test
    @DisplayName("B1: post-mockMvc DB query throws \"Tenant identity changed\" because STATELESS filter cleared SecurityContextHolder")
    void postMockMvcDbQueryThrowsTenantIdentityChanged() throws Exception {
        TransactionTemplate tx = tx();
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        try {
            // Seed tenant
            jdbc.update(
                    "INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) "
                            + "VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                    tenantId, "Cause B Tenant",
                    "cause-b-" + tenantId.toString().substring(0, 8),
                    Timestamp.from(now), Timestamp.from(now));

            // Seed user with right GUC (Pattern A2)
            tx.executeWithoutResult(s -> {
                jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)",
                        String.class, tenantId.toString());
                jdbc.update(
                        "INSERT INTO users (id, tenant_id, email, display_name, status, "
                                + "password_hash, created_at, updated_at) "
                                + "VALUES (?, ?, ?, 'Cause B User', 'ACTIVE', 'dummy', ?, ?)",
                        userId, tenantId,
                        "cause-b-" + userId.toString().substring(0, 8) + "@example.test",
                        Timestamp.from(now), Timestamp.from(now));
            });

            // Pre-mockMvc: bind SecurityContextHolder = tenantId
            bindTenantContext(tenantId);

            // Capture SecurityContextHolder state BEFORE mockMvc.perform
            var preMockCtx = SecurityContextHolder.getContext().getAuthentication();
            assertThat(preMockCtx).as("pre-mockMvc SecurityContext must be set").isNotNull();

            // mockMvc.perform a simple read-only GET (no DB write needed here —
            // we just want to trigger the Spring Security filter chain)
            MockHttpServletResponse response = mockMvc.perform(
                            get("/api/v1/users/{userId}", userId)
                                    .param("tenantId", tenantId.toString())
                                    .with(authentication(auth(tenantId, userId))))
                    .andReturn().getResponse();

            // Capture SecurityContextHolder state AFTER mockMvc.perform
            var postMockCtx = SecurityContextHolder.getContext().getAuthentication();

            // B1 assertion: SecurityContextHolder is NULL after mockMvc.perform
            // (Spring Security STATELESS filter cleared it)
            assertThat(postMockCtx)
                    .as("post-mockMvc SecurityContext must be cleared by STATELESS filter "
                            + "(NullSecurityContextRepository saves NULL)")
                    .isNull();

            // B2 assertion: the next DB query throws "Tenant identity changed"
            // because tenantAppliedFor=tenantId (set during mockMvc.perform) !=
            // currentTenantId()=NULL (cleared by filter).
            // Use platform_audit_logs (no RLS) to prove the handler throws regardless
            // of whether the target table has RLS.
            assertThatThrownBy(() -> {
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM platform_audit_logs WHERE target_tenant_id = ?",
                        Integer.class, tenantId);
            })
                    .as("post-mockMvc query must throw \"Tenant identity changed\"")
                    .hasMessageContaining("Tenant identity changed inside an active RLS transaction");
        } finally {
            SecurityContextHolder.clearContext();
            // Cleanup — the parent @Transactional will roll back, but the tenant INSERT
            // (via raw jdbc.update) doesn't roll back. Need explicit cleanup.
            tx.executeWithoutResult(s -> {
                jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)",
                        String.class, tenantId.toString());
                jdbc.update("DELETE FROM users WHERE id = ?", userId);
            });
            jdbc.update("DELETE FROM tenants WHERE id = ?", tenantId);
        }
    }

    /**
     * Prove B2: re-binding SecurityContextHolder BEFORE the post-mockMvc DB query
     * restores the context that the STATELESS filter cleared. The handler then
     * sees {@code tenantAppliedFor==currentTenantId()} and returns early without
     * throwing. The query succeeds.
     *
     * <p>This is NOT a blanket fix — it's a targeted re-bind that compensates for
     * the Spring Security STATELESS filter's clearing behavior. The re-bind
     * establishes the SAME tenant context the test already had (the test is
     * verifying data for tenantId, not switching tenants).
     */
    @Test
    @DisplayName("B2: re-binding SecurityContextHolder before post-mockMvc DB query restores context and avoids \"Tenant identity changed\"")
    void rebindBeforePostMockMvcDbQueryRestoresContext() throws Exception {
        TransactionTemplate tx = tx();
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        try {
            // Seed tenant + user with right GUC
            jdbc.update(
                    "INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) "
                            + "VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                    tenantId, "Cause B2 Tenant",
                    "cause-b2-" + tenantId.toString().substring(0, 8),
                    Timestamp.from(now), Timestamp.from(now));
            tx.executeWithoutResult(s -> {
                jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)",
                        String.class, tenantId.toString());
                jdbc.update(
                        "INSERT INTO users (id, tenant_id, email, display_name, status, "
                                + "password_hash, created_at, updated_at) "
                                + "VALUES (?, ?, ?, 'Cause B2 User', 'ACTIVE', 'dummy', ?, ?)",
                        userId, tenantId,
                        "cause-b2-" + userId.toString().substring(0, 8) + "@example.test",
                        Timestamp.from(now), Timestamp.from(now));
            });

            // Bind SecurityContextHolder = tenantId, mockMvc.perform, then RE-BIND
            bindTenantContext(tenantId);
            mockMvc.perform(
                    get("/api/v1/users/{userId}", userId)
                            .param("tenantId", tenantId.toString())
                            .with(authentication(auth(tenantId, userId))));

            // Capture: post-mockMvc SecurityContext is cleared
            var postMockCtx = SecurityContextHolder.getContext().getAuthentication();
            assertThat(postMockCtx).as("STATELESS filter cleared context").isNull();

            // RE-BIND before the post-mockMvc DB query
            bindTenantContext(tenantId);

            // The query should NOT throw — tenantAppliedFor=tenantId (from mockMvc.perform),
            // currentTenantId()=tenantId (from re-bind) → match, no throw.
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, userId);
            assertThat(count).as("post-mockMvc query succeeds with re-bind").isEqualTo(1);
        } finally {
            SecurityContextHolder.clearContext();
            tx.executeWithoutResult(s -> {
                jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)",
                        String.class, tenantId.toString());
                jdbc.update("DELETE FROM users WHERE id = ?", userId);
            });
            jdbc.update("DELETE FROM tenants WHERE id = ?", tenantId);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private TransactionTemplate tx() {
        if (tx == null) {
            tx = new TransactionTemplate(transactionManager);
        }
        return tx;
    }

    private void bindTenantContext(UUID tenantId) {
        var token = new UsernamePasswordAuthenticationToken(
                "test-" + tenantId, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        token.setDetails(Map.of(
                "tenant_id", tenantId.toString(),
                "user_id", UUID.randomUUID().toString()));
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    private org.springframework.security.core.Authentication auth(UUID tenantId, UUID userId) {
        var token = new UsernamePasswordAuthenticationToken(
                userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        token.setDetails(Map.of(
                "tenant_id", tenantId.toString(),
                "user_id", userId.toString()));
        return token;
    }
}
