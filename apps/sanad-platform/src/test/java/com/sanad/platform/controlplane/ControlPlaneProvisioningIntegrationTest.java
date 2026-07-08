package com.sanad.platform.controlplane;

import com.sanad.platform.admin.api.AdminDtos.CreateTenantRequest;
import com.sanad.platform.admin.api.AdminDtos.TenantResponse;
import com.sanad.platform.admin.service.AdminPlatformService;
import com.sanad.platform.security.SecurityPermitAllTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(SecurityPermitAllTestConfig.class)
@ActiveProfiles("local")
@Transactional
class ControlPlaneProvisioningIntegrationTest {

    @Autowired private AdminPlatformService platformService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void createsTenantOrganizationAdministratorRoleCapabilitiesAndAuditInOneTransaction() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        CreateTenantRequest request = new CreateTenantRequest(
                "منشأة اختبار " + suffix,
                "Test Legal Entity " + suffix,
                "tenant-" + suffix,
                "billing-" + suffix + "@example.test",
                "admin-" + suffix + "@example.test",
                "مسؤول الاختبار",
                "SA",
                "ar-SA",
                "Asia/Riyadh",
                "SAR",
                14,
                "STARTER",
                null,
                "MONTHLY",
                1,
                true
        );

        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated("operator", "n/a", List.of());
        authentication.setDetails(Map.of(
                "tenant_id", UUID.randomUUID().toString(),
                "user_id", UUID.randomUUID().toString()
        ));

        TenantResponse tenant = platformService.createTenant(request, authentication);

        assertThat(tenant.status()).isEqualTo("TRIAL");
        assertThat(tenant.trialEndsAt()).isNotNull();
        assertThat(count("organizations", tenant.id())).isEqualTo(1);
        assertThat(count("users", tenant.id())).isEqualTo(1);
        assertThat(count("roles", tenant.id())).isEqualTo(1);
        assertThat(count("organization_memberships", tenant.id())).isEqualTo(1);
        assertThat(count("user_role_assignments", tenant.id())).isEqualTo(1);

        Long roleCapabilityCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM role_capabilities WHERE tenant_id = ?",
                Long.class,
                tenant.id()
        );
        assertThat(roleCapabilityCount).isNotNull().isGreaterThan(0);

        Long auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM platform_audit_logs WHERE target_tenant_id = ? AND action = 'TENANT.PROVISION'",
                Long.class,
                tenant.id()
        );
        assertThat(auditCount).isEqualTo(1);
    }

    private long count(String tableName, UUID tenantId) {
        if (!List.of(
                "organizations", "users", "roles", "organization_memberships", "user_role_assignments")
                .contains(tableName)) {
            throw new IllegalArgumentException("Unsupported table");
        }
        Long value = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName + " WHERE tenant_id = ?",
                Long.class,
                tenantId
        );
        return value == null ? 0 : value;
    }
}
