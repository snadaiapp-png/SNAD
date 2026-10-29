package com.sanad.platform.security.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.access.grant.UserRoleGrant;
import com.sanad.platform.access.grant.UserRoleGrantRepository;
import com.sanad.platform.access.role.Role;
import com.sanad.platform.access.role.RoleRepository;
import com.sanad.platform.security.dto.LoginRequest;
import com.sanad.platform.tenant.domain.Tenant;
import com.sanad.platform.tenant.domain.TenantStatus;
import com.sanad.platform.tenant.repository.TenantRepository;
import com.sanad.platform.user.domain.User;
import com.sanad.platform.user.domain.UserStatus;
import com.sanad.platform.user.repository.UserRepository;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AuthBootstrapIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRoleGrantRepository roleGrantRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private PlatformTransactionManager transactionManager;
    private TransactionTemplate tx;

    @Test
    void loginReturnsCompleteBootstrapWithoutSecondProfileRequest() throws Exception {
        if (tx == null) {
            tx = new TransactionTemplate(transactionManager);
        }
        Tenant tenant = tenantRepository.save(new Tenant(
                "Auth Bootstrap Tenant",
                "auth-bootstrap-" + UUID.randomUUID(),
                TenantStatus.ACTIVE));
        seedLoginEligibleSubscription(tenant.getId());
        String email = "bootstrap-" + UUID.randomUUID() + "@example.com";
        String password = "Valid-Password-" + UUID.randomUUID();
        User user = new User(tenant.getId(), email, "Bootstrap User", UserStatus.ACTIVE);
        user.setPasswordHash(passwordEncoder.encode(password));
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

        // V20261029_1: roles / user_role_assignments are FORCE RLS — the VIEWER
        // fixture seeding must run inside a transaction bound to the fixture
        // tenant, same contract as the user save above.
        final UUID bootstrapUserId = user.getId();
        bindTenantContext(tenant.getId());
        try {
            tx.executeWithoutResult(txStatus -> {
                Role viewer = roleRepository.save(new Role(
                        tenant.getId(), "VIEWER", "Viewer", "Read-only access"));
                roleGrantRepository.save(new UserRoleGrant(tenant.getId(), bootstrapUserId, viewer.getId(), null));
            });
        } finally {
            clearTenantContext();
        }
        LoginRequest loginRequest = new LoginRequest(email, password);
        loginRequest.setTenantId(tenant.getId());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.credentialRotationRequired").value(false))
                .andExpect(jsonPath("$.memberships").isArray())
                .andExpect(jsonPath("$.effectiveRoleGrants[0].roleCode").value("VIEWER"))
                // EXEC-PROMPT-SANAD-FULLSTACK-REMEDIATION-010:
                // The VIEWER role granted here has NO capabilities, so the new
                // capability-derived LoginDestinationResolver must return only
                // the safe /workspace destination (not the legacy hard-coded
                // /crm). The previous behavior leaked UI destinations the user
                // had no authorization to use.
                .andExpect(jsonPath("$.defaultDestination").value("/workspace"))
                .andExpect(jsonPath("$.availableDestinations").isArray())
                .andExpect(jsonPath("$.availableDestinations[0]").value("/workspace"))
                .andExpect(jsonPath("$.tenantContext.tenantId").value(tenant.getId().toString()));
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
        jdbc.update("""
                        INSERT INTO tenant_subscriptions (id, tenant_id, plan_id, status,
                                                          billing_cycle, seat_quantity, credit_balance_minor,
                                                          started_at, current_period_start, current_period_end,
                                                          cancel_at_period_end, billing_state, created_at, updated_at)
                        VALUES (?, ?, ?, 'ACTIVE', 'MONTHLY', 1, 0,
                                NOW(), NOW(), NOW() + INTERVAL '30 days', false, 'CURRENT', NOW(), NOW())
                        """,
                UUID.randomUUID(), tenantId, planId);
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
