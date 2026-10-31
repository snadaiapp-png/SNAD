package com.sanad.platform.security.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.access.grant.UserRoleGrantRepository;
import com.sanad.platform.access.role.RoleCapabilityRepository;
import com.sanad.platform.access.role.RoleRepository;
import com.sanad.platform.organization.membership.repository.OrganizationMembershipRepository;
import com.sanad.platform.organization.repository.OrganizationRepository;
import com.sanad.platform.security.domain.RefreshTokenRepository;
import com.sanad.platform.security.dto.ChangeCredentialRequest;
import com.sanad.platform.security.dto.LoginRequest;
import com.sanad.platform.security.dto.RefreshRequest;
import com.sanad.platform.tenant.domain.Tenant;
import com.sanad.platform.tenant.domain.TenantStatus;
import com.sanad.platform.tenant.repository.TenantRepository;
import com.sanad.platform.user.domain.User;
import com.sanad.platform.user.domain.UserStatus;
import com.sanad.platform.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class CredentialRotationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private OrganizationMembershipRepository membershipRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private PasswordEncoder encoder;
    @Autowired private UserRoleGrantRepository userRoleGrantRepository;
    @Autowired private RoleCapabilityRepository roleCapabilityRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    private TransactionTemplate tx;

    @AfterEach
    void cleanUp() {
        // Fixtures use unique tenant and user identifiers. Do not delete global
        // platform seed data from a shared integration-test application context.
    }

    @Test
    void bootstrapSessionIsRestrictedUntilCredentialRotation() throws Exception {
        if (tx == null) {
            tx = new TransactionTemplate(transactionManager);
        }
        Tenant tenant = tenantRepository.save(new Tenant(
                "Rotation Tenant", "rotation-" + UUID.randomUUID(), TenantStatus.ACTIVE));
        seedLoginEligibleSubscription(tenant.getId());
        String email = "rotation-" + UUID.randomUUID() + "@example.invalid";
        String initialValue = UUID.randomUUID().toString();
        String replacementValue = UUID.randomUUID().toString();

        User user = new User(tenant.getId(), email, "Rotation User", UserStatus.ACTIVE);
        user.setPasswordHash(encoder.encode(initialValue));
        user.setPasswordSetAt(Instant.now());
        user.setPasswordSetBy("test-bootstrap");
        user.setMustChangePassword(true);
        // public.users is FORCE RLS (V20261011_3). This test class is NOT @Transactional,
        // so HikariCP returns a different physical connection per repository.save() call.
        // Wrap the User save() in ONE tx.executeWithoutResult block and bind
        // SecurityContextHolder to the tenant BEFORE the tx so TenantRlsConnectionHandler
        // reads tenant_id from the context and applies SET LOCAL app.tenant_id on the
        // tx-bound physical Connection.
        bindTenantContext(tenant.getId());
        try {
            final User[] userHolder = new User[]{user};
            tx.executeWithoutResult(txStatus -> {
                userHolder[0] = userRepository.save(userHolder[0]);
            });
            user = userHolder[0];
        } finally {
            clearTenantContext();
        }

        LoginRequest login = new LoginRequest(email, initialValue);
        login.setTenantId(tenant.getId());
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();

        JsonNode loginBody = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String access = loginBody.path("accessToken").asText();
        String refresh = loginResult.getResponse().getHeader(AuthController.REFRESH_TOKEN_HEADER);

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.credentialRotationRequired").value(true));

        mockMvc.perform(get("/api/v1/users")
                        .param("tenantId", tenant.getId().toString())
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isForbidden());

        ChangeCredentialRequest change = new ChangeCredentialRequest(initialValue, replacementValue);
        mockMvc.perform(post("/api/v1/auth/change-credential")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(change)))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(refresh))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isUnauthorized());

        LoginRequest replacementLogin = new LoginRequest(email, replacementValue);
        replacementLogin.setTenantId(tenant.getId());
        MvcResult replacementResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(replacementLogin)))
                .andExpect(status().isOk())
                .andReturn();
        String replacementAccess = objectMapper.readTree(
                        replacementResult.getResponse().getContentAsString())
                .path("accessToken").asText();

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + replacementAccess))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.credentialRotationRequired").value(false));

        // public.users is FORCE RLS (V20261011_3): the verification read must
        // run inside a transaction scoped to the fixture tenant, otherwise the
        // fail-closed policy hides the row and orElseThrow() fires.
        bindTenantContext(tenant.getId());
        final User[] persistedHolder = new User[1];
        try {
            tx.executeWithoutResult(txStatus -> persistedHolder[0] =
                    userRepository.findByTenantIdAndEmail(tenant.getId(), email).orElseThrow());
        } finally {
            clearTenantContext();
        }
        User persisted = persistedHolder[0];
        assertThat(persisted.isMustChangePassword()).isFalse();
        assertThat(persisted.getPasswordSetBy()).isEqualTo("self-service");
        assertThat(refreshTokenRepository
                .findAllByTenantIdAndUserId(tenant.getId(), persisted.getId()))
                .isNotEmpty();
    }

    /**
     * Canonical login-eligibility fixture: authentication gates require an
     * ACTIVE tenant with exactly one login-eligible effective subscription
     * (fail-closed). Tests seed that subscription alongside the tenant.
     */
    private void seedLoginEligibleSubscription(UUID tenantId) {
        UUID planId = UUID.randomUUID();
        jdbc.update("""
                        INSERT INTO saas_plans (id, code, name, status, currency_code,
                                                monthly_price_minor, annual_price_minor, trial_days,
                                                max_users, max_organizations, storage_mb, created_at, updated_at)
                        VALUES (?, ?, 'Auth Test Plan', 'ACTIVE', 'SAR', 1000, 10000, 0, 10, 5, 1024, NOW(), NOW())
                        """,
                planId, "login-" + tenantId.toString().substring(0, 8));
        // V20261029_1: tenant_subscriptions is FORCE RLS fail-closed — bind the
        // fixture tenant transaction-locally for this seed (same contract as the
        // bindTenantContext+tx user save). Not a bypass: the policy predicate
        // is unchanged.
        TransactionTemplate seedTx = (tx != null) ? tx : new TransactionTemplate(transactionManager);
        seedTx.executeWithoutResult(txStatus -> {
            jdbc.queryForObject(
                    "SELECT set_config('app.tenant_id', ?, true)",
                    String.class, tenantId.toString());
            jdbc.update("""
                    INSERT INTO tenant_subscriptions (id, tenant_id, plan_id, status,
                                                      billing_cycle, seat_quantity, credit_balance_minor,
                                                      started_at, current_period_start, current_period_end,
                                                      cancel_at_period_end, billing_state, created_at, updated_at)
                    VALUES (?, ?, ?, 'ACTIVE', 'MONTHLY', 1, 0,
                            NOW(), NOW(), NOW() + INTERVAL '30 days', false, 'CURRENT', NOW(), NOW())
                    """,
                    UUID.randomUUID(), tenantId, planId);
        });
    }

    /**
     * Bind tenant_id into SecurityContextHolder so TenantRlsConnectionHandler
     * applies SET LOCAL app.tenant_id inside a tx.executeWithoutResult block.
     * user_id is not needed for RLS — pass a placeholder.
     */
    private void bindTenantContext(UUID tenantId) {
        var token = new UsernamePasswordAuthenticationToken(
                "test-" + tenantId, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        token.setDetails(Map.of("tenant_id", tenantId.toString(),
                "user_id", UUID.randomUUID().toString()));
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    private void clearTenantContext() {
        SecurityContextHolder.clearContext();
    }
}
