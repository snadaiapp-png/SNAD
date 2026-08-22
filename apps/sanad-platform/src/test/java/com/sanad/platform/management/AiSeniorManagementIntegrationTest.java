package com.sanad.platform.management;

import com.sanad.platform.management.application.ManagementGovernanceModuleContract;
import com.sanad.platform.management.application.ManagementGovernanceModuleRegistry;
import com.sanad.platform.management.health.SystemHealthContributorRegistry;
import com.sanad.platform.security.SecurityPermitAllTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Senior Management integration contract for the certified AI module.
 *
 * <p>The AI module is already registered and tenant-scoped. This test defines
 * the missing executive visibility contract: AI must auto-register with both
 * ManagementGovernanceModuleRegistry and Central System Health without any
 * hard-coded change in either core registry.</p>
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(SecurityPermitAllTestConfig.class)
class AiSeniorManagementIntegrationTest {

    @Autowired private ManagementGovernanceModuleRegistry governanceRegistry;
    @Autowired private SystemHealthContributorRegistry healthRegistry;
    @Autowired private JdbcTemplate jdbc;

    private UUID tenantA;
    private UUID tenantB;
    private UUID userA;
    private UUID userB;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE TABLE ai_inference_log, ai_agents RESTART IDENTITY CASCADE");

        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();
        userA = UUID.randomUUID();
        userB = UUID.randomUUID();
        var now = Timestamp.from(Instant.now());

        insertTenantAndUser(tenantA, userA, "ai-sm-a", now);
        insertTenantAndUser(tenantB, userB, "ai-sm-b", now);
    }

    @Test
    void governanceRegistry_autoDiscoversAiModule() {
        List<String> codes = governanceRegistry.allModules().stream()
                .map(ManagementGovernanceModuleContract::moduleCode)
                .toList();

        assertThat(codes).contains("AI");
    }

    @Test
    void systemHealthRegistry_autoDiscoversAiContributor() {
        assertThat(healthRegistry.allContributorIds()).contains("ai");
    }

    @Test
    void aiGovernanceKpis_areStrictlyTenantScoped() {
        insertActiveAgent(tenantB, userB, "B-ONLY");

        var ai = governanceRegistry.allModules().stream()
                .filter(m -> "AI".equals(m.moduleCode()))
                .findFirst();
        assertThat(ai).isPresent();

        Map<String, Object> tenantAKpis = ai.orElseThrow().kpiSummary(tenantA);
        Map<String, Object> tenantBKpis = ai.orElseThrow().kpiSummary(tenantB);

        assertThat(number(tenantAKpis, "totalAgents")).isZero();
        assertThat(number(tenantBKpis, "totalAgents")).isEqualTo(1);
        assertThat(number(tenantAKpis, "activeAgents")).isZero();
        assertThat(number(tenantBKpis, "activeAgents")).isEqualTo(1);
    }

    @Test
    void aiGovernance_exposesOnlyRegisteredAiCapabilities() {
        var ai = governanceRegistry.allModules().stream()
                .filter(m -> "AI".equals(m.moduleCode()))
                .findFirst();
        assertThat(ai).isPresent();

        assertThat(ai.orElseThrow().capabilities(tenantA))
                .containsExactlyInAnyOrder("AI.VIEW", "AI.WRITE", "AI.ADMIN", "AI.EXECUTE");
        assertThat(ai.orElseThrow().metadata()).containsKeys("version", "maturity", "since");
    }

    @Test
    void aiSystemHealth_doesNotLeakAnotherTenantsAgentCounts() {
        insertActiveAgent(tenantB, userB, "B-HEALTH");

        var contributor = healthRegistry.find("ai");
        assertThat(contributor).isPresent();

        var healthA = contributor.orElseThrow().checkHealth(tenantA);
        var healthB = contributor.orElseThrow().checkHealth(tenantB);

        assertThat(healthA.details()).containsEntry("totalAgents", 0);
        assertThat(healthB.details()).containsEntry("totalAgents", 1);
        assertThat(healthA.details()).doesNotContainKey("tenantId");
        assertThat(healthB.details()).doesNotContainKey("tenantId");
    }

    private void insertTenantAndUser(UUID tenantId, UUID userId, String prefix, Timestamp now) {
        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                        + "VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                tenantId, prefix, prefix + "-" + tenantId.toString().substring(0, 8), now, now);
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) "
                        + "VALUES (?, ?, ?, 'AI SM User', 'ACTIVE', 'dummy', ?, ?)",
                userId, tenantId, prefix + "-" + userId.toString().substring(0, 8) + "@test", now, now);
    }

    private void insertActiveAgent(UUID tenantId, UUID userId, String code) {
        var now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO ai_agents "
                        + "(id,tenant_id,code,name,provider,status,created_by,version_lock,version,created_at,updated_at) "
                        + "VALUES (?, ?, ?, ?, 'DETERMINISTIC', 'ACTIVE', ?, 0, 0, ?, ?)",
                UUID.randomUUID(), tenantId, code, code, userId, now, now);
    }

    private int number(Map<String, Object> map, String key) {
        Object value = map.get(key);
        assertThat(value).as(key).isInstanceOf(Number.class);
        return ((Number) value).intValue();
    }
}
