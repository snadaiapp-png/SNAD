package com.sanad.platform.security.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.access.capability.AccessCapability;
import com.sanad.platform.access.capability.AccessCapabilityRepository;
import com.sanad.platform.access.grant.UserRoleGrant;
import com.sanad.platform.access.grant.UserRoleGrantRepository;
import com.sanad.platform.access.role.Role;
import com.sanad.platform.access.role.RoleCapability;
import com.sanad.platform.access.role.RoleCapabilityRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AdminBootstrapCredentialIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRoleGrantRepository roleGrantRepository;
    @Autowired private AccessCapabilityRepository accessCapabilityRepository;
    @Autowired private RoleCapabilityRepository roleCapabilityRepository;

    @Test
    void credentiallessSameTenantUserCanBeBootstrappedAndMustRotateCredential() throws Exception {
        Tenant tenant = tenantRepository.save(new Tenant(
                "Bootstrap Credential Tenant",
                "bootstrap-credential-" + UUID.randomUUID(),
                TenantStatus.ACTIVE));
        seedLoginEligibleSubscription(tenant.getId());

        String adminEmail = "bootstrap-admin-" + UUID.randomUUID() + "@example.com";
        String adminPassword = "Admin-" + UUID.randomUUID() + "!Aa1";
        User admin = new User(tenant.getId(), adminEmail, "Bootstrap Admin", UserStatus.ACTIVE);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin = userRepository.save(admin);
        grantCapability(tenant.getId(), admin.getId(), "USER.WRITE");

        User target = new User(
                tenant.getId(),
                "bootstrap-target-" + UUID.randomUUID() + "@example.com",
                "Bootstrap Target",
                UserStatus.ACTIVE);
        target = userRepository.save(target);
        long previousSessionVersion = target.getSessionVersion();

        LoginRequest login = new LoginRequest(adminEmail, adminPassword);
        login.setTenantId(tenant.getId());
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String accessToken = loginJson.path("accessToken").asText();

        String bootstrapCredential = "Bootstrap-" + UUID.randomUUID() + "!Aa1";
        mockMvc.perform(post("/api/v1/auth/admin-bootstrap-credential/{userId}", target.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("newCredential", bootstrapCredential))))
                .andExpect(status().isNoContent());

        User bootstrapped = userRepository.findByTenantIdAndId(tenant.getId(), target.getId()).orElseThrow();
        assertNotNull(bootstrapped.getPasswordHash());
        assertTrue(passwordEncoder.matches(bootstrapCredential, bootstrapped.getPasswordHash()));
        assertTrue(bootstrapped.isMustChangePassword());
        assertEquals(previousSessionVersion + 1, bootstrapped.getSessionVersion());
        assertNotNull(bootstrapped.getPasswordSetAt());
        assertEquals("admin-bootstrap", bootstrapped.getPasswordSetBy());
    }

    private void grantCapability(UUID tenantId, UUID userId, String capabilityCode) {
        Role role = roleRepository.save(new Role(
                tenantId,
                "BOOTSTRAP_ADMIN",
                "Bootstrap Admin",
                "Credential bootstrap test role"));
        AccessCapability capability = accessCapabilityRepository.findByCode(capabilityCode)
                .orElseGet(() -> accessCapabilityRepository.save(new AccessCapability(
                        capabilityCode,
                        capabilityCode,
                        null)));
        roleCapabilityRepository.save(new RoleCapability(tenantId, role.getId(), capability.getId()));
        roleGrantRepository.save(new UserRoleGrant(tenantId, userId, role.getId(), null));
    }

    private void seedLoginEligibleSubscription(UUID tenantId) {
        UUID planId = UUID.randomUUID();
        jdbc.update("""
                        INSERT INTO saas_plans (id, code, name, status, currency_code,
                                                monthly_price_minor, annual_price_minor, trial_days,
                                                max_users, max_organizations, storage_mb, created_at, updated_at)
                        VALUES (?, ?, 'Bootstrap Credential Test Plan', 'ACTIVE', 'SAR',
                                1000, 10000, 0, 10, 5, 1024, NOW(), NOW())
                        """,
                planId, "bootstrap-" + tenantId.toString().substring(0, 8));
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
}
