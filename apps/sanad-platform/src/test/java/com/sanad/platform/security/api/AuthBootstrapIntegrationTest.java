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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AuthBootstrapIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRoleGrantRepository roleGrantRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void loginReturnsCompleteBootstrapWithoutSecondProfileRequest() throws Exception {
        Tenant tenant = tenantRepository.save(new Tenant(
                "Auth Bootstrap Tenant",
                "auth-bootstrap-" + UUID.randomUUID(),
                TenantStatus.ACTIVE));
        String email = "bootstrap-" + UUID.randomUUID() + "@example.com";
        String password = "Valid-Password-" + UUID.randomUUID();
        User user = new User(tenant.getId(), email, "Bootstrap User", UserStatus.ACTIVE);
        user.setPasswordHash(passwordEncoder.encode(password));
        user = userRepository.save(user);

        Role viewer = roleRepository.save(new Role(
                tenant.getId(), "VIEWER", "Viewer", "Read-only access"));
        roleGrantRepository.save(new UserRoleGrant(tenant.getId(), user.getId(), viewer.getId(), null));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.credentialRotationRequired").value(false))
                .andExpect(jsonPath("$.memberships").isArray())
                .andExpect(jsonPath("$.effectiveRoleGrants[0].roleCode").value("VIEWER"))
                .andExpect(jsonPath("$.defaultDestination").value("/crm"))
                .andExpect(jsonPath("$.availableDestinations").isArray())
                .andExpect(jsonPath("$.tenantContext.tenantId").value(tenant.getId().toString()));
    }
}
