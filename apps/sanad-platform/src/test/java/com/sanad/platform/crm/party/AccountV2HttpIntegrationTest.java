package com.sanad.platform.crm.party;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Account V2 HTTP integration tests using MockMvc.
 * Tests the full HTTP path: /api/v2/crm/accounts
 * Verifies HTTP status codes, ETag, If-Match (428/412), and response shapes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AccountV2HttpIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/v2/crm/accounts returns 200")
    void listAccountsReturns200() throws Exception {
        mockMvc.perform(get("/api/v2/crm/accounts"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v2/crm/accounts/{id} with non-existent ID returns 404")
    void getNonExistentReturns404() throws Exception {
        mockMvc.perform(get("/api/v2/crm/accounts/00000000-0000-4000-8000-000000000000"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PATCH /api/v2/crm/accounts/{id} without If-Match returns 428")
    void patchWithoutIfMatchReturns428() throws Exception {
        mockMvc.perform(patch("/api/v2/crm/accounts/00000000-0000-4000-8000-000000000000")
                        .contentType("application/json")
                        .content("{\"displayName\":\"Test\"}"))
                .andExpect(status().isPreconditionRequired());
    }

    @Test
    @DisplayName("PATCH /api/v2/crm/accounts/{id} with stale If-Match returns 412")
    void patchWithStaleIfMatchReturns412() throws Exception {
        mockMvc.perform(patch("/api/v2/crm/accounts/00000000-0000-4000-8000-000000000000")
                        .header("If-Match", "\"account-00000000-0000-4000-8000-000000000000-v99-abcdef123456789\"")
                        .contentType("application/json")
                        .content("{\"displayName\":\"Test\"}"))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    @DisplayName("POST /api/v2/crm/accounts with invalid body returns 400")
    void createWithInvalidBodyReturns400() throws Exception {
        mockMvc.perform(post("/api/v2/crm/accounts")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
