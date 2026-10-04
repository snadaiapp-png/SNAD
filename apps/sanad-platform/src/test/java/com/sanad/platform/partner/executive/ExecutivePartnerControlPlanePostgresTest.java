package com.sanad.platform.partner.executive;

import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import com.sanad.platform.test.MigrationTestSchemaSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutivePartnerControlPlanePostgresTest {

    private static final String SOURCE_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");
    private static final String PLATFORM_TENANT_ID =
            "00000000-0000-0000-0000-000000000001";

    private static DriverManagerDataSource dataSource;
    private static JdbcTemplate jdbc;
    private static TransactionTemplate tx;
    private static ExecutivePartnerService service;

    @BeforeAll
    static void requirePostgreSqlDirect() {
        boolean available;
        try {
            available = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "ExecutivePartnerControlPlanePostgresTest");
        } catch (Throwable ignored) {
            available = false;
        }
        Assumptions.assumeTrue(available,
                "PostgreSQL Direct is required for ExecutivePartnerControlPlanePostgresTest");

        MigrationTestSchemaSupport.ensureDatabase(SOURCE_URL, USER, PASSWORD);
        String isolatedUrl = MigrationTestSchemaSupport.getIsolatedJdbcUrl(SOURCE_URL);

        Flyway.configure()
                .dataSource(isolatedUrl, USER, PASSWORD)
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .baselineOnMigrate(true)
                .outOfOrder(true)
                .load()
                .migrate();

        dataSource = new DriverManagerDataSource(isolatedUrl, USER, PASSWORD);
        jdbc = new JdbcTemplate(dataSource);
        tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        service = new ExecutivePartnerService(jdbc);
    }

    @Test
    void lifecycleIsAuditedVersionedAndTerminatedIsTerminal() {
        var auth = auth();
        ExecutivePartnerService.PartnerView created =
                inTx(() -> service.createPartner("PARTNER", auth));

        assertThat(created.status()).isEqualTo("ACTIVE");
        assertThat(created.version()).isZero();

        assertThatThrownBy(() ->
                inTx(() -> service.changeStatus(created.id(), "SUSPENDED", null, auth)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getReason())
                        .isEqualTo("PARTNER_STATUS_REASON_REQUIRED"));

        ExecutivePartnerService.PartnerView suspended =
                inTx(() -> service.changeStatus(created.id(), "SUSPENDED", "risk-review", auth));
        assertThat(suspended.status()).isEqualTo("SUSPENDED");
        assertThat(suspended.suspendedAt()).isNotNull();
        assertThat(suspended.suspendedReason()).isEqualTo("risk-review");
        assertThat(suspended.version()).isEqualTo(1);

        ExecutivePartnerService.PartnerView reactivated =
                inTx(() -> service.changeStatus(created.id(), "ACTIVE", null, auth));
        assertThat(reactivated.status()).isEqualTo("ACTIVE");
        assertThat(reactivated.suspendedAt()).isNull();
        assertThat(reactivated.version()).isEqualTo(2);

        ExecutivePartnerService.PartnerView terminated =
                inTx(() -> service.changeStatus(created.id(), "TERMINATED", "contract-ended", auth));
        assertThat(terminated.status()).isEqualTo("TERMINATED");
        assertThat(terminated.terminatedAt()).isNotNull();
        assertThat(terminated.terminatedReason()).isEqualTo("contract-ended");
        assertThat(terminated.version()).isEqualTo(3);

        assertThatThrownBy(() ->
                inTx(() -> service.changeStatus(created.id(), "ACTIVE", null, auth)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getReason())
                        .isEqualTo("PARTNER_TERMINATED_TERMINAL"));

        long auditCount = inTx(() -> {
            setPlatformScope();
            Long count = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM authorization_change_events
                     WHERE target_type='PARTNER' AND target_id=?
                    """, Long.class, created.id());
            return count == null ? 0L : count;
        });
        assertThat(auditCount).isGreaterThanOrEqualTo(4L);
    }

    @Test
    void controlPlaneReadsBindingsAndDelegationsForExplicitPartnerOnly() {
        var auth = auth();
        ExecutivePartnerService.PartnerView partnerA =
                inTx(() -> service.createPartner("PARTNER", auth));
        ExecutivePartnerService.PartnerView partnerB =
                inTx(() -> service.createPartner("RESELLER", auth));

        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID bindingA = UUID.randomUUID();
        UUID bindingB = UUID.randomUUID();

        inTx(() -> {
            setPlatformScope();
            insertTenant(tenantA);
            insertTenant(tenantB);
            jdbc.update("""
                    INSERT INTO partner_tenant_bindings
                        (id,partner_id,tenant_id,created_by,updated_by)
                    VALUES (?,?,?,?,?)
                    """, bindingA, partnerA.id(), tenantA, userId(auth), userId(auth));
            jdbc.update("""
                    INSERT INTO partner_tenant_bindings
                        (id,partner_id,tenant_id,created_by,updated_by)
                    VALUES (?,?,?,?,?)
                    """, bindingB, partnerB.id(), tenantB, userId(auth), userId(auth));
            jdbc.update("""
                    INSERT INTO partner_delegation_grants
                        (id,partner_id,tenant_id,capability_code,status,created_by,updated_by)
                    VALUES (?,?,?,'TENANT.SUSPEND','ACTIVE',?,?)
                    """, UUID.randomUUID(), partnerA.id(), tenantA, userId(auth), userId(auth));
            return null;
        });

        List<ExecutivePartnerService.PartnerTenantView> aTenants =
                inTx(() -> service.tenants(partnerA.id()));
        List<ExecutivePartnerService.PartnerTenantView> bTenants =
                inTx(() -> service.tenants(partnerB.id()));
        List<ExecutivePartnerService.PartnerDelegationView> aDelegations =
                inTx(() -> service.delegations(partnerA.id()));
        List<ExecutivePartnerService.PartnerDelegationView> bDelegations =
                inTx(() -> service.delegations(partnerB.id()));

        assertThat(aTenants).extracting(ExecutivePartnerService.PartnerTenantView::tenantId)
                .containsExactly(tenantA);
        assertThat(bTenants).extracting(ExecutivePartnerService.PartnerTenantView::tenantId)
                .containsExactly(tenantB);
        assertThat(aDelegations).extracting(ExecutivePartnerService.PartnerDelegationView::tenantId)
                .containsExactly(tenantA);
        assertThat(bDelegations).isEmpty();
    }

    @Test
    void unknownPartnerAndInvalidLifecycleValuesFailClosed() {
        var auth = auth();

        assertThatThrownBy(() -> inTx(() -> service.getPartner(UUID.randomUUID())))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getReason())
                        .isEqualTo("PARTNER_NOT_FOUND"));

        assertThatThrownBy(() -> inTx(() -> service.createPartner("ROOT", auth)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getReason())
                        .isEqualTo("PARTNER_TYPE_INVALID"));

        ExecutivePartnerService.PartnerView partner =
                inTx(() -> service.createPartner("AGENT", auth));

        assertThatThrownBy(() ->
                inTx(() -> service.changeStatus(partner.id(), "BROKEN", "x", auth)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getReason())
                        .isEqualTo("PARTNER_STATUS_INVALID"));
    }

    private static void insertTenant(UUID tenantId) {
        jdbc.update("""
                INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at)
                VALUES (?,?,?,'ACTIVE',NOW(),NOW())
                """,
                tenantId,
                "W2-T7 " + tenantId,
                "w2t7-" + tenantId.toString().substring(0, 12));
    }

    private static void setPlatformScope() {
        jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)",
                String.class, PLATFORM_TENANT_ID);
    }

    private static UUID userId(UsernamePasswordAuthenticationToken auth) {
        @SuppressWarnings("unchecked")
        Map<String,Object> details = (Map<String,Object>) auth.getDetails();
        return UUID.fromString(details.get("user_id").toString());
    }

    private static UsernamePasswordAuthenticationToken auth() {
        UUID user = UUID.randomUUID();
        Map<String,Object> details = new HashMap<>();
        details.put("user_id", user.toString());
        details.put("tenant_id", PLATFORM_TENANT_ID);
        var auth = new UsernamePasswordAuthenticationToken(user.toString(), null, List.of());
        auth.setDetails(details);
        return auth;
    }

    private static <T> T inTx(java.util.function.Supplier<T> supplier) {
        return tx.execute(status -> supplier.get());
    }
}
