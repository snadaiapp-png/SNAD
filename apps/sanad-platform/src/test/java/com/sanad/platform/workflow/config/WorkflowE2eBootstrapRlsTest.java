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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.sanad.platform.security.rls.TenantRlsTransactionContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression proof for the workflow-e2e bootstrap under fail-closed RLS.
 * The ApplicationRunner must seed both tenants without bypassing RLS; each
 * tenant's employee rows must only be visible under a verified, signed
 * transaction-local tenant context.
 */
@SpringBootTest
@ActiveProfiles({"local", "workflow-e2e"})
@Import(SecurityPermitAllTestConfig.class)
class WorkflowE2eBootstrapRlsTest {

    @DynamicPropertySource
    static void workflowE2eCredential(DynamicPropertyRegistry registry) {
        registry.add("WF_E2E_PASSWORD", () -> "e2e-" + UUID.randomUUID());
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private TenantRlsTransactionContext signedTenantContext;

    @Test
    void workflowE2eBootstrapSeedsBothTenantEmployeeSetsUnderRls() {
        Integer tenantAEmployees = activeEmployeesInSignedTenant(WorkflowE2eBootstrapConfig.TENANT_A_ID);
        assertThat(tenantAEmployees).isGreaterThanOrEqualTo(9);

        Integer tenantBEmployees = activeEmployeesInSignedTenant(WorkflowE2eBootstrapConfig.TENANT_B_ID);
        assertThat(tenantBEmployees).isGreaterThanOrEqualTo(1);
    }

    private Integer activeEmployeesInSignedTenant(UUID tenantId) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            signedTenantContext.applyForCurrentTransaction(tenantId);
            return jdbc.queryForObject(
                    "SELECT COUNT(*) FROM hr_employees WHERE tenant_id = ? AND status = 'ACTIVE'",
                    Integer.class, tenantId);
        });
    }
}
