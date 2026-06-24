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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
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

    @AfterEach
    void cleanUp() {
        roleCapabilityRepository.deleteAll();
        userRoleGrantRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        membershipRepository.deleteAll();
        roleRepository.deleteAll();
        userRepository.deleteAll();
        organizationRepository.deleteAll();
        tenantRepository.deleteAll();
    }

    @Test
    void bootstrapSessionIsRestrictedUntilCredentialRotation() throws Exception {
        Tenant tenant = tenantRepository.save(new Tenant(
                "Rotation Tenant", "rotation-" + UUID.randomUUID(), TenantStatus.ACTIVE));
        String email = "rotation-" + UUID.randomUUID() + "@example.invalid";
        String initialValue = UUID.randomUUID().toString();
        String replacementValue = UUID.randomUUID().toString();

        User user = new User(tenant.getId(), email, "Rotation User", UserStatus.ACTIVE);
        user.setPasswordHash(encoder.encode(initialValue));
        user.setPasswordSetAt(Instant.now());
        user.setPasswordSetBy("test-bootstrap");
        user.setMustChangePassword(true);
        userRepository.save(user);

        LoginRequest login = new LoginRequest(email, initialValue);
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

        User persisted = userRepository.findByTenantIdAndEmail(tenant.getId(), email).orElseThrow();
        assertThat(persisted.isMustChangePassword()).isFalse();
        assertThat(persisted.getPasswordSetBy()).isEqualTo("self-service");
        assertThat(refreshTokenRepository
                .findAllByTenantIdAndUserId(tenant.getId(), persisted.getId()))
                .isNotEmpty();
    }
}
