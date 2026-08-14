package com.sanad.platform.management;

import com.sanad.platform.management.application.KpiService;
import com.sanad.platform.management.application.StrategicInitiativeService;
import com.sanad.platform.management.application.StrategicObjectiveService;
import com.sanad.platform.management.domain.KpiDefinition;
import com.sanad.platform.management.domain.KpiMeasurement;
import com.sanad.platform.management.domain.KpiTarget;
import com.sanad.platform.management.domain.KeyResult;
import com.sanad.platform.management.domain.StrategicInitiative;
import com.sanad.platform.management.domain.StrategicObjective;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for the Senior Management Operating Layer.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ManagementIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private StrategicObjectiveService objectiveService;
    @Autowired private KpiService kpiService;
    @Autowired private StrategicInitiativeService initiativeService;
    @Autowired private JdbcTemplate jdbc;

    private UUID tenantId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE TABLE strategic_initiatives, kpi_measurements, kpi_targets, "
                + "kpi_definitions, key_results, strategic_objectives RESTART IDENTITY CASCADE");

        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        var now = java.sql.Timestamp.from(java.time.Instant.now());

        jdbc.update("INSERT INTO tenants (id,name,subdomain,status,created_at,updated_at) "
                + "VALUES (?, 'Test Tenant', ?, 'ACTIVE', ?, ?)",
                tenantId, "mgmt-" + tenantId.toString().substring(0, 8), now, now);
        jdbc.update("INSERT INTO users (id,tenant_id,email,display_name,status,password_hash,created_at,updated_at) "
                + "VALUES (?, ?, ?, 'Test User', 'ACTIVE', 'dummy', ?, ?)",
                userId, tenantId, "test-" + userId.toString().substring(0, 8) + "@example.test", now, now);

        var roleId = UUID.randomUUID();
        jdbc.update("INSERT INTO roles (id,tenant_id,code,name,description,status,created_at,updated_at) "
                + "VALUES (?, ?, 'ADMIN', 'Administrator', 'Test admin', 'ACTIVE', ?, ?)",
                roleId, tenantId, now, now);

        var caps = jdbc.queryForList(
                "SELECT id FROM access_capabilities WHERE code IN (?, ?, ?)",
                "EXECUTIVE_MANAGEMENT.VIEW", "EXECUTIVE_MANAGEMENT.WRITE", "EXECUTIVE_MANAGEMENT.ADMIN");
        for (var cap : caps) {
            var capId = (UUID) cap.get("id");
            jdbc.update("INSERT INTO role_capabilities (id,tenant_id,role_id,capability_id,created_at) "
                    + "VALUES (?, ?, ?, ?, ?)",
                    UUID.randomUUID(), tenantId, roleId, capId, now);
        }
    }

    private Authentication auth() {
        var token = new UsernamePasswordAuthenticationToken(
                userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        token.setDetails(Map.of("tenant_id", tenantId.toString(), "user_id", userId.toString()));
        return token;
    }

    @Test
    void strategicObjectiveLifecycle_createActivateAchieveClose() {
        var objective = StrategicObjective.create(
                tenantId, "OBJ-001", "Increase Revenue",
                "Grow ARR by 20%", StrategicObjective.Priority.HIGH,
                userId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)
        );
        var created = objectiveService.createObjective(objective);
        assertThat(created.status()).isEqualTo(StrategicObjective.Status.DRAFT);
        assertThat(created.progressPct()).isEqualTo(0);

        var activated = objectiveService.activate(tenantId, created.id());
        assertThat(activated.status()).isEqualTo(StrategicObjective.Status.ACTIVE);

        var achieved = objectiveService.achieve(tenantId, created.id());
        assertThat(achieved.status()).isEqualTo(StrategicObjective.Status.ACHIEVED);
        assertThat(achieved.progressPct()).isEqualTo(100);

        var closed = objectiveService.close(tenantId, created.id());
        assertThat(closed.status()).isEqualTo(StrategicObjective.Status.CLOSED);
    }

    @Test
    void keyResultStatusComputation_upDirection() {
        var objective = StrategicObjective.create(
                tenantId, "OBJ-KR-1", "Test Objective", "Test",
                StrategicObjective.Priority.NORMAL, userId,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)
        );
        objectiveService.createObjective(objective);

        var kr = KeyResult.create(
                tenantId, objective.id(), "Revenue Target", "Reach 100K SAR",
                KeyResult.MetricUnit.CURRENCY,
                new BigDecimal("50000"), new BigDecimal("100000"),
                KeyResult.Direction.UP, 100, userId, LocalDate.of(2026, 12, 31)
        );

        assertThat(kr.status()).isEqualTo(KeyResult.Status.NOT_STARTED);

        var kr2 = kr.recordMeasurement(new BigDecimal("60000"));
        assertThat(kr2.status()).isEqualTo(KeyResult.Status.OFF_TRACK);

        var kr3 = kr.recordMeasurement(new BigDecimal("80000"));
        assertThat(kr3.status()).isEqualTo(KeyResult.Status.AT_RISK);

        var kr4 = kr.recordMeasurement(new BigDecimal("95000"));
        assertThat(kr4.status()).isEqualTo(KeyResult.Status.ON_TRACK);

        var kr5 = kr.recordMeasurement(new BigDecimal("100000"));
        assertThat(kr5.status()).isEqualTo(KeyResult.Status.ACHIEVED);
    }

    @Test
    void kpiMeasurement_statusAndVarianceComputation() {
        var def = KpiDefinition.create(
                tenantId, "KPI-001", "Monthly Recurring Revenue",
                "Total MRR", "FINANCIAL",
                KeyResult.MetricUnit.CURRENCY, KeyResult.Direction.UP,
                "SUM(subscriptions.monthly_fee)", "BILLING", userId
        );
        kpiService.createDefinition(def);

        var target = KpiTarget.create(
                tenantId, def.id(),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                new BigDecimal("100000"), new BigDecimal("50000"),
                new BigDecimal("150000"), userId
        );
        kpiService.createTarget(target);

        var m1 = kpiService.recordMeasurement(
                tenantId, def.id(), LocalDate.of(2026, 6, 30),
                new BigDecimal("90000"), "Billing export 2026-06", userId
        );
        assertThat(m1.status()).isEqualTo(KpiMeasurement.Status.ON_TRACK);
        assertThat(m1.variancePct()).isEqualByComparingTo(new BigDecimal("-10.0000"));

        var m2 = kpiService.recordMeasurement(
                tenantId, def.id(), LocalDate.of(2026, 7, 31),
                new BigDecimal("40000"), "Billing export 2026-07", userId
        );
        assertThat(m2.status()).isEqualTo(KpiMeasurement.Status.OFF_TRACK);
        assertThat(m2.previousValue()).isEqualByComparingTo(new BigDecimal("90000"));

        var m3 = kpiService.recordMeasurement(
                tenantId, def.id(), LocalDate.of(2026, 8, 31),
                new BigDecimal("120000"), "Billing export 2026-08", userId
        );
        assertThat(m3.status()).isEqualTo(KpiMeasurement.Status.ACHIEVED);
    }

    @Test
    void strategicInitiative_lifecycleAndSpend() {
        var objective = StrategicObjective.create(
                tenantId, "OBJ-INIT-1", "Test Objective", "Test",
                StrategicObjective.Priority.NORMAL, userId,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)
        );
        objectiveService.createObjective(objective);

        var initiative = StrategicInitiative.create(
                tenantId, objective.id(), "INIT-001", "Marketing Campaign",
                "Q3 marketing campaign", userId,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30),
                500000L
        );
        var created = initiativeService.create(initiative);
        assertThat(created.status()).isEqualTo(StrategicInitiative.Status.PLANNED);
        assertThat(created.spentMinor()).isEqualTo(0);

        var started = initiativeService.start(tenantId, created.id());
        assertThat(started.status()).isEqualTo(StrategicInitiative.Status.IN_PROGRESS);

        var withSpend = initiativeService.recordSpend(tenantId, created.id(), 150000L);
        assertThat(withSpend.spentMinor()).isEqualTo(150000L);

        var withProgress = initiativeService.updateProgress(tenantId, created.id(), 60);
        assertThat(withProgress.progressPct()).isEqualTo(60);

        var completed = initiativeService.complete(tenantId, created.id());
        assertThat(completed.status()).isEqualTo(StrategicInitiative.Status.COMPLETED);
        assertThat(completed.progressPct()).isEqualTo(100);
        assertThat(completed.actualEndDate()).isNotNull();
    }

    @Test
    void dashboardEndpoint_returnsAggregatedData() throws Exception {
        var objective = StrategicObjective.create(
                tenantId, "OBJ-DASH-1", "Dashboard Test Objective",
                "Test", StrategicObjective.Priority.HIGH, userId,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)
        );
        objectiveService.createObjective(objective);
        objectiveService.activate(tenantId, objective.id());

        var def = KpiDefinition.create(
                tenantId, "KPI-DASH-1", "Dashboard KPI", "Test KPI",
                "OPERATIONAL", KeyResult.MetricUnit.COUNT,
                KeyResult.Direction.UP, "COUNT(*)", "CRM", userId
        );
        kpiService.createDefinition(def);

        mockMvc.perform(get("/api/v1/management/dashboard")
                        .with(authentication(auth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalObjectives").value(1))
                .andExpect(jsonPath("$.activeObjectives").value(1))
                .andExpect(jsonPath("$.totalKpis").value(1))
                .andExpect(jsonPath("$.kpisNoData").value(1))
                .andExpect(jsonPath("$.topObjectives[0].code").value("OBJ-DASH-1"));
    }

    @Test
    void createObjectiveEndpoint_returnsOk() throws Exception {
        mockMvc.perform(post("/api/v1/management/objectives")
                        .with(authentication(auth()))
                        .contentType("application/json")
                        .content("""
                                {
                                    "code": "OBJ-API-1",
                                    "title": "API Created Objective",
                                    "description": "Created via REST API",
                                    "priority": "HIGH",
                                    "periodStart": "2026-01-01",
                                    "periodEnd": "2026-12-31"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OBJ-API-1"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.AuthenticationRequestPostProcessor authentication(
            Authentication auth) {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(auth);
    }
}
