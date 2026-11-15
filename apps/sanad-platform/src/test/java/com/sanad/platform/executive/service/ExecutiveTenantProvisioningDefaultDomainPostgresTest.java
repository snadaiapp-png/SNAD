package com.sanad.platform.executive.service;

import com.sanad.platform.admin.api.AdminDtos.CreateTenantRequest;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RED/GREEN regression for the WS6 acceptance bullet "Auto-create tenant
 * default subdomain from SANAD_BASE_DOMAIN".
 *
 * <p>Defect (UAT-13, 2026-11-16): {@code ensureDefaultDomain(...)} existed on
 * {@code TenantDomainService} but was never invoked by the atomic executive
 * provisioning flow, so newly provisioned tenants got no DEFAULT_GENERATED
 * application domain even with {@code sanad.tenancy.domains.base-domain}
 * configured. This test pins the contract: provisioning must compose the
 * default application domain row (origin DEFAULT_GENERATED, ACTIVE, primary)
 * from the configured base domain, exactly as the
 * {@code ExecutivePlatformService.createTenant} javadoc promises.</p>
 */
@SpringBootTest
@ActiveProfiles("local")
class ExecutiveTenantProvisioningDefaultDomainPostgresTest {

    /**
     * HostRoutingService.configuredBaseDomain() reads the raw system property
     * / environment variable (deliberately outside the Spring Environment),
     * so the base domain is pinned as a JVM system property here.
     */
    @BeforeAll
    static void pinBaseDomainSystemProperty() {
        System.setProperty("sanad.tenancy.domains.base-domain", "ws6-regression.example");
    }

    @Autowired private ExecutiveTenantProvisioningService provisioning;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;

    /** tenant_domains is fail-closed FORCE RLS: bind the tenant GUC before asserting. */
    private <T> T inTenant(UUID tenantId, java.util.function.Supplier<T> action) {
        return new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(status -> {
            jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)",
                    String.class, tenantId.toString());
            return action.get();
        });
    }

    @BeforeAll
    static void requirePostgreSqlDirect() {
        boolean available = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                "ExecutiveTenantProvisioningDefaultDomainPostgresTest");
        Assumptions.assumeTrue(available,
                "PostgreSQL Direct is required for ExecutiveTenantProvisioningDefaultDomainPostgresTest");
    }

    private static CreateTenantRequest request(String subdomain, String adminEmail) {
        return new CreateTenantRequest(
                "WS6 Default Domain Tenant " + subdomain,
                null,
                subdomain,
                null,
                adminEmail,
                "WS6 Admin",
                "SA",
                "ar-SA",
                "Asia/Riyadh",
                "SAR",
                0,
                "STARTER",
                null,
                "MONTHLY",
                1,
                Boolean.TRUE);
    }

    @Test
    void provisioningComposesDefaultGeneratedApplicationDomainFromBaseDomain() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String subdomain = "ws6-dom-" + suffix;

        var result = provisioning.provision(request(subdomain, "ws6-" + suffix + "@example.test"), null);

        var domain = inTenant(result.tenant().id(), () -> jdbc.queryForMap(
                "SELECT hostname, domain_type, origin, status, is_primary FROM tenant_domains "
                        + "WHERE tenant_id = ? AND origin = 'DEFAULT_GENERATED' AND domain_type = 'APPLICATION'",
                result.tenant().id()));

        assertThat(domain.get("hostname")).isEqualTo(subdomain + ".ws6-regression.example");
        assertThat(domain.get("status")).isEqualTo("ACTIVE");
        assertThat(domain.get("is_primary")).isEqualTo(Boolean.TRUE);
    }

    @Test
    void provisioningIsIdempotentOnTheDefaultDomainRow() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        var result = provisioning.provision(request("ws6-idem-" + suffix, "ws6-idem-" + suffix + "@example.test"), null);

        Integer rows = inTenant(result.tenant().id(), () -> jdbc.queryForObject(
                "SELECT COUNT(*) FROM tenant_domains WHERE tenant_id = ? AND origin = 'DEFAULT_GENERATED' "
                        + "AND domain_type = 'APPLICATION'",
                Integer.class, result.tenant().id()));
        assertThat(rows).isEqualTo(1);
    }
}
