package com.sanad.platform.security.api;

import com.sanad.platform.security.service.JwtTokenProvider;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class TenantBindingSecurityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/organizations",
            "/api/v1/users",
            "/api/v1/organization-memberships",
            "/api/v1/access/roles",
            "/api/v1/access/user-role-grants",
            "/api/v1/access/capabilities"
    })
    void mismatchedTenantIsRejectedBeforeControllerDispatch(String path) throws Exception {
        UUID authenticatedTenant = UUID.randomUUID();
        String accessToken = jwtTokenProvider.mintAccessToken(
                UUID.randomUUID(), authenticatedTenant, "isolation-test@example.invalid");

        mockMvc.perform(get(path)
                        .param("tenantId", UUID.randomUUID().toString())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }
}
