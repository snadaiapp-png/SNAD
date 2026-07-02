package com.sanad.platform.access;

import com.sanad.platform.access.api.AccessApiExceptionHandler;
import com.sanad.platform.access.api.CapabilityEvaluationController;
import com.sanad.platform.access.evaluation.CapabilityEvaluationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CapabilityEvaluationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(AccessApiExceptionHandler.class)
class CapabilityEvaluationControllerTest {

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.sanad.platform.audit.service.TenantSecurityDenialAuditService tenantSecurityDenialAuditService;

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.sanad.platform.security.denial.SecurityDenialCoordinator securityDenialCoordinator;
    @org.springframework.boot.test.mock.mockito.MockBean
    private com.sanad.platform.security.tenant.TenantContextProvider tenantContextProvider;
    @Autowired private MockMvc mockMvc;
    @MockBean private CapabilityEvaluationService evaluationService;     @MockBean private com.sanad.platform.security.tenant.TenantResolver tenantResolver;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID organizationId = UUID.randomUUID();
    private final UUID roleId = UUID.randomUUID();

    @Test
    void allowedEvaluationReturnsMatchedRole() throws Exception {
        when(evaluationService.evaluate(tenantId, userId, "USER.READ", organizationId))
                .thenReturn(new AccessDecisionResponse(tenantId, userId, organizationId,
                        "USER.READ", true, "ROLE_CAPABILITY_MATCH", roleId, "ADMIN"));

        mockMvc.perform(get("/api/v1/access/evaluation")
                        .param("tenantId", tenantId.toString())
                        .param("userId", userId.toString())
                        .param("capabilityCode", "USER.READ")
                        .param("organizationId", organizationId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.matchedRoleCode").value("ADMIN"));
    }

    @Test
    void deniedEvaluationReturnsReason() throws Exception {
        when(evaluationService.evaluate(tenantId, userId, "USER.WRITE", null))
                .thenReturn(new AccessDecisionResponse(tenantId, userId, null,
                        "USER.WRITE", false, "NO_MATCHING_ACTIVE_ROLE", null, null));

        mockMvc.perform(get("/api/v1/access/evaluation")
                        .param("tenantId", tenantId.toString())
                        .param("userId", userId.toString())
                        .param("capabilityCode", "USER.WRITE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.reason").value("NO_MATCHING_ACTIVE_ROLE"));
    }

    @Test
    void missingCapabilityCodeReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/access/evaluation")
                        .param("tenantId", tenantId.toString())
                        .param("userId", userId.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownOrganizationReturns404() throws Exception {
        when(evaluationService.evaluate(tenantId, userId, "USER.READ", organizationId))
                .thenThrow(new AccessResourceNotFoundException("Organization not found"));

        mockMvc.perform(get("/api/v1/access/evaluation")
                        .param("tenantId", tenantId.toString())
                        .param("userId", userId.toString())
                        .param("capabilityCode", "USER.READ")
                        .param("organizationId", organizationId.toString()))
                .andExpect(status().isNotFound());
    }
}
