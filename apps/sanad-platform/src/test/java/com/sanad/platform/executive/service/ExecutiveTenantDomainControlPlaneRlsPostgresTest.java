package com.sanad.platform.executive.service;

import com.sanad.platform.admin.api.TenantDomainDtos.CreateDomainRequest;
import com.sanad.platform.admin.service.TenantDomainService;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.security.rls.TenantRlsTransactionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RED/GREEN regression for control-plane custom-domain management across
 * tenants (defect discovered by automated UAT-14, 2026-11-16).
 *
 * <p>Defect: every HTTP request binds {@code app.tenant_id} to the ACTING
 * principal's tenant ({@code TenantRlsConnectionHandler}), while the executive
 * domain controller operates on the PATH tenant
 * ({@code /api/v1/executive/tenants/{tenantId}/domains}). Because
 * {@code tenant_domains} is fail-closed FORCE RLS, every control-plane domain
 * mutation/read for any tenant other than the actor's own failed with
 * {@code new row violates row-level security policy for table "tenant_domains"}
 * (or silently returned zero rows for reads). The service now re-scopes the
 * transaction to the path tenant through the trusted
 * {@link TenantRlsTransactionContext} (the documented control-plane mechanism,
 * already used by atomic provisioning).</p>
 */
@SpringBootTest
@ActiveProfiles("local")
class ExecutiveTenantDomainControlPlaneRlsPostgresTest {

    private static final UUID CONTROL_PLANE_TENANT = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @Autowired private TenantDomainService domainService;
    @Autowired private TenantRlsTransactionContext tenantRlsContext;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    @BeforeAll
    static void requirePostgreSqlDirect() {
        boolean available = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                "ExecutiveTenantDomainControlPlaneRlsPostgresTest");
        Assumptions.assumeTrue(available,
                "PostgreSQL Direct is required for ExecutiveTenantDomainControlPlaneRlsPostgresTest");
    }

    @AfterEach
    void resetScope() {
        // Detach from any tenant scope so later assertions are not accidentally filtered.
        jdbc.execute("SELECT set_config('app.tenant_id', '', false)");
    }

    @Test
    void controlPlaneActorCanManageAnotherTenantsDomainLifecycle() {
        // A target tenant that is NOT the acting principal's tenant.
        UUID targetTenant = createBareTenant("rls-dom-" + UUID.randomUUID().toString().substring(0, 8));
        java.util.concurrent.atomic.AtomicReference<String> hostRef = new java.util.concurrent.atomic.AtomicReference<>();

        // Simulate the control-plane HTTP request: the transaction is bound to
        // the ACTING tenant (control plane), not the path tenant.
        new TransactionTemplate(transactionManager).execute(status -> {
            tenantRlsContext.applyForCurrentTransaction(CONTROL_PLANE_TENANT);

            String host = "rls-probe-" + UUID.randomUUID().toString().substring(0, 8)
                    + ".ws6-regression.example";
            hostRef.set(host);

            // 1) create a custom domain for the TARGET tenant — previously
            //    rejected by FORCE RLS ("new row violates row-level security
            //    policy for table \"tenant_domains\"").
            var created = domainService.createDomain(targetTenant,
                    new CreateDomainRequest(host, com.sanad.platform.admin.api.TenantDomainDtos.DomainType.APPLICATION, null, null), null);
            assertThat(created.hostname()).isEqualTo(host);

            // 2) read back through the control-plane path — previously zero rows.
            var listed = domainService.listDomains(targetTenant, null);
            assertThat(listed).anyMatch(d -> d.hostname().equals(host));

            // 3) summary sees the domain as well.
            var summary = domainService.summarize(targetTenant);
            assertThat(summary.totalCount()).isGreaterThanOrEqualTo(1);
            return null;
        });

        // 4) GLOBAL hostname uniqueness (no outer transaction: createDomain is
        //    @Transactional and re-scopes the RLS context internally now, so
        //    the 409 surfaces directly without poisoning an enclosing tx).
        //    Another tenant claiming the same hostname must be rejected even
        //    though the application-level availability check is RLS-scoped and
        //    cannot see the first tenant's row — the global unique index
        //    (V20261116_1) is the backstop.
        UUID thirdTenant = createBareTenant("rls-dup-" + UUID.randomUUID().toString().substring(0, 8));
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        domainService.createDomain(thirdTenant,
                                new CreateDomainRequest(hostRef.get(),
                                        com.sanad.platform.admin.api.TenantDomainDtos.DomainType.APPLICATION,
                                        null, null), null))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
    }

    private UUID createBareTenant(String subdomain) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())",
                id, "RLS Domain Probe " + subdomain, subdomain);
        return id;
    }
}
