package com.sanad.platform.workflow;

import com.sanad.platform.security.SecurityPermitAllTestConfig;
import com.sanad.platform.workflow.application.WorkflowActionabilityService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression for the real HTTP actionability boundary under fail-closed RLS.
 * The service call itself must open a transaction so TenantRlsDataSource can
 * apply the authenticated tenant with SET LOCAL before repository reads.
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
class WorkflowActionabilityRlsTransactionBoundaryTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private WorkflowActionabilityService actionability;
    @Autowired private PlatformTransactionManager transactionManager;

    private UUID tenantId;
    private UUID userId;
    private UUID employeeId;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void actionableEmployeeResolvesWhenCallerHasOnlyJwtTenantContextAndNoOuterTransaction() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        employeeId = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                    tenantId, "Workflow RLS Boundary", "wf-rls-" + tenantId.toString().substring(0, 8), now, now);
            String applied = jdbc.queryForObject(
                    "SELECT set_config('app.tenant_id', ?, true)", String.class, tenantId.toString());
            assertThat(applied).isEqualTo(tenantId.toString());
            jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) VALUES (?, ?, ?, ?, 'ACTIVE', 'fixture-hash', ?, ?)",
                    userId, tenantId, "rls-" + userId.toString().substring(0, 8) + "@test", "Workflow RLS User", now, now);
            jdbc.update("""
                    INSERT INTO hr_employees (
                        id, tenant_id, user_id, employee_number, first_name, last_name,
                        display_name, employment_type, status, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, 'Workflow', 'RLS', 'Workflow RLS Employee',
                              'FULL_TIME', 'ACTIVE', ?, ?)
                    """, employeeId, tenantId, userId, "E-RLS-" + userId.toString().substring(0, 8), now, now);
        });

        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                userId.toString(), null, List.of());
        authentication.setDetails(Map.of(
                "tenant_id", tenantId.toString(),
                "user_id", userId.toString()));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        var employee = actionability.requireActionableEmployee(tenantId, userId);
        assertThat(employee.id()).isEqualTo(employeeId);
        assertThat(employee.userId()).isEqualTo(userId);
    }
}
