package com.sanad.platform.workflow.config;

import com.sanad.platform.security.SecurityPermitAllTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression proof for the workflow-e2e bootstrap under fail-closed RLS.
 * The ApplicationRunner must seed both tenants without bypassing RLS; each
 * tenant's employee rows are then visible only after setting that tenant's
 * transaction-local app.tenant_id context.
 */
@SpringBootTest
@ActiveProfiles({"local", "workflow-e2e"})
@Import(SecurityPermitAllTestConfig.class)
@Transactional
class WorkflowE2eBootstrapRlsTest {

    @DynamicPropertySource
    static void workflowE2eCredential(DynamicPropertyRegistry registry) {
        registry.add("WF_E2E_PASSWORD", () -> "e2e-" + UUID.randomUUID());
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void workflowE2eBootstrapSeedsBothTenantEmployeeSetsUnderRls() {
        setTenant(WorkflowE2eBootstrapConfig.TENANT_A_ID);
        Integer tenantAEmployees = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_employees WHERE tenant_id = ? AND status = 'ACTIVE'",
                Integer.class, WorkflowE2eBootstrapConfig.TENANT_A_ID);
        assertThat(tenantAEmployees).isGreaterThanOrEqualTo(9);

        setTenant(WorkflowE2eBootstrapConfig.TENANT_B_ID);
        Integer tenantBEmployees = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hr_employees WHERE tenant_id = ? AND status = 'ACTIVE'",
                Integer.class, WorkflowE2eBootstrapConfig.TENANT_B_ID);
        assertThat(tenantBEmployees).isGreaterThanOrEqualTo(1);
    }

    private void setTenant(UUID tenantId) {
        String applied = jdbc.queryForObject(
                "SELECT set_config('app.tenant_id', ?, true)",
                String.class, tenantId.toString());
        assertThat(applied).isEqualTo(tenantId.toString());
    }
}
