package com.sanad.platform.e2e;

import com.sanad.platform.businessprocess.BusinessProcessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "sanad.cors.allowed-origins=https://snad-app.vercel.app",
        "sanad.production-guard.enabled=false",
        "management.health.mail.enabled=false",
        "spring.datasource.url=jdbc:postgresql://localhost:5432/sanad_test_guard_placeholder",
        "spring.datasource.username=sanad_test",
        "spring.datasource.password=sanad_test"
})
@ActiveProfiles("prod")
@DisabledIf("dockerNotAvailable")
@Transactional
class IntegratedBusinessProcessesPostgresE2ETest {

    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("sanad_process_e2e")
            .withUsername("sanad_process_e2e")
            .withPassword(UUID.randomUUID().toString());

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        postgres.start();
        registry.add("sanad.database.url", postgres::getJdbcUrl);
        registry.add("sanad.database.username", postgres::getUsername);
        registry.add("sanad.database.password", postgres::getPassword);
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    static boolean dockerNotAvailable() {
        return !DockerClientFactory.instance().isDockerAvailable();
    }

    @Autowired BusinessProcessService service;
    @Autowired JdbcTemplate jdbc;

    @Test
    void executesAllGovernedProcessesAgainstRealPostgres() {
        UUID tenantId = UUID.fromString("91000000-0000-0000-0000-000000000001");
        UUID actorId = UUID.fromString("92000000-0000-0000-0000-000000000001");
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO tenants (id,subdomain,name,status,locale,timezone,currency_code,created_at,updated_at) VALUES (?,?,?,'ACTIVE','ar-SA','Asia/Riyadh','SAR',?,?)",
                tenantId, "postgres-process-e2e", "PostgreSQL Process E2E", now, now);
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,created_at,updated_at) VALUES (?,?,?,?,'ACTIVE',?,?)",
                actorId, tenantId, "postgres-process-e2e@example.test", "PostgreSQL Process Actor", now, now);

        List<BusinessProcessService.ExecutionResult> results = List.of(
                service.execute(tenantId, actorId, BusinessProcessService.SALES_ORDER_TO_CASH,
                        command("pg-sales", "1000", "150", "2", "PG-SALES-SKU")),
                service.execute(tenantId, actorId, BusinessProcessService.PROCURE_TO_PAY,
                        command("pg-procure", "500", "0", "5", "PG-PROCURE-SKU")),
                service.execute(tenantId, actorId, BusinessProcessService.HIRE_TO_PAY,
                        command("pg-hire", "1000", "0", "1", null)),
                service.execute(tenantId, actorId, BusinessProcessService.ORDER_TO_REFUND,
                        command("pg-commerce", "100", "15", "1", "PG-COMMERCE-SKU"))
        );

        assertThat(results).hasSize(4);
        assertThat(results).allSatisfy(result -> {
            assertThat(result.status()).isEqualTo("COMPLETED");
            assertThat(result.financialReconciled()).isTrue();
            assertThat(result.inventoryReconciled()).isTrue();
            assertThat(result.analyticsConsistent()).isTrue();
            assertThat(result.blockedSteps()).isEmpty();
            assertThat(result.debitTotal()).isEqualByComparingTo(result.creditTotal());
            assertThat(result.auditCount()).isGreaterThan(result.verifiedSteps().size());
        });

        Long completed = jdbc.queryForObject(
                "SELECT COUNT(*) FROM bp_process_runs WHERE tenant_id=? AND status='COMPLETED'",
                Long.class, tenantId);
        assertThat(completed).isEqualTo(4L);
    }

    private BusinessProcessService.ExecuteCommand command(String reference, String gross,
                                                           String tax, String quantity, String sku) {
        return new BusinessProcessService.ExecuteCommand(reference,
                new BigDecimal(gross), new BigDecimal(tax), new BigDecimal(quantity),
                "SAR", sku, null);
    }
}
