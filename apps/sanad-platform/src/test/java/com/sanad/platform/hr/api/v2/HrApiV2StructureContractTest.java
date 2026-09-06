package com.sanad.platform.hr.api.v2;

import com.sanad.platform.access.AccessDecisionResponse;
import com.sanad.platform.access.evaluation.CapabilityEvaluationService;
import com.sanad.platform.security.authorization.CapabilityAuthorizationBypass;
import com.sanad.platform.security.service.JwtTokenProvider;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HRM-G0 / WS5 Task 4 — Structure v2 contract (14 canonical operations:
 * 4 Org Unit + 4 Job + 6 Position).
 *
 * <p>Locks the Structure slice of the canonical 58-operation surface:
 *
 * <ul>
 *   <li>reads gate on HRM.ORG_STRUCTURE.VIEW, mutations on
 *       HRM.ORG_STRUCTURE.MANAGE (assignment capabilities do NOT authorize
 *       structure reads and vice versa)</li>
 *   <li>org unit / job / position revisions are effective-dated: a new
 *       version row is created and the open version closes the day before;
 *       history is preserved</li>
 *   <li>position freeze/close act on STAFFABILITY (root status), never on
 *       occupancy and never on version history; illegal staffability
 *       transitions yield 409</li>
 *   <li>effective dates are required and never server-defaulted</li>
 *   <li>cross-tenant reads fail closed as 404</li>
 * </ul>
 *
 * <p>PostgreSQL Direct only — structural rows are seeded over real JDBC
 * with the tenant GUC (fail-closed FORCE RLS), never mocked.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(HrApiV2StructureContractTest.AuthProbeConfig.class)
class HrApiV2StructureContractTest {

    private static final String DB_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://127.0.0.1:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "sanad_pass");

    static final Set<String> GRANTED = new HashSet<>();

    static final UUID TENANT = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    static final UUID OTHER_TENANT = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    static final UUID USER = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    @Autowired private MockMvc mockMvc;

    @BeforeAll
    static void requirePostgreSql() {
        boolean available = false;
        try (Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            available = c.isValid(5);
        } catch (Exception ignored) {
        }
        Assumptions.assumeTrue(available, "PostgreSQL Direct is not available");
    }

    @BeforeEach
    void resetGrants() {
        GRANTED.clear();
    }

    private UsernamePasswordAuthenticationToken principal() {
        return principal(TENANT);
    }

    private UsernamePasswordAuthenticationToken principal(UUID tenantId) {
        var token = new UsernamePasswordAuthenticationToken(
                "test-principal", "n/a", List.of(new SimpleGrantedAuthority("ROLE_TEST")));
        token.setDetails(Map.of("tenant_id", tenantId.toString(), "user_id", USER.toString()));
        return token;
    }

    // ==================== CAPABILITY BOUNDARIES ====================

    @Test
    void listOrgUnits_withoutCapability_scopeDenied() throws Exception {
        mockMvc.perform(get("/api/v2/hr/org-units").with(authentication(principal())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HRM_SCOPE_DENIED"));
    }

    @Test
    void structureRead_requiresOrgStructureCapability_notAssignmentCapability() throws Exception {
        GRANTED.add("HRM.ASSIGNMENT.VIEW");
        mockMvc.perform(get("/api/v2/hr/jobs").with(authentication(principal())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HRM_SCOPE_DENIED"));
    }

    @Test
    void structureMutation_requiresManageCapability_viewInsufficient() throws Exception {
        GRANTED.add("HRM.ORG_STRUCTURE.VIEW");
        mockMvc.perform(post("/api/v2/hr/org-units")
                        .with(authentication(principal()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"" + UUID.randomUUID() + "\"," +
                                "\"name\":\"New Unit\",\"code\":\"NU\",\"unitType\":\"DEPARTMENT\"," +
                                "\"effectiveFrom\":\"2026-09-01\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HRM_SCOPE_DENIED"));
    }

    // ==================== ORG UNITS ====================

    @Test
    void createOrgUnit_thenReadBack() throws Exception {
        GRANTED.add("HRM.ORG_STRUCTURE.MANAGE");
        GRANTED.add("HRM.ORG_STRUCTURE.VIEW");
        UUID orgId = seedOrganization(TENANT);

        String created = mockMvc.perform(post("/api/v2/hr/org-units")
                        .with(authentication(principal()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"" + orgId + "\",\"name\":\"Engineering\"," +
                                "\"code\":\"ENG\",\"unitType\":\"DEPARTMENT\",\"effectiveFrom\":\"2026-09-01\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orgUnitId").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Engineering"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();

        String orgUnitId = com.jayway.jsonpath.JsonPath.read(created, "$.orgUnitId");
        mockMvc.perform(get("/api/v2/hr/org-units/{id}", orgUnitId).with(authentication(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orgUnitId").value(orgUnitId))
                .andExpect(jsonPath("$.name").value("Engineering"));
    }

    @Test
    void reviseOrgUnit_createsNewEffectiveVersion() throws Exception {
        GRANTED.add("HRM.ORG_STRUCTURE.MANAGE");
        GRANTED.add("HRM.ORG_STRUCTURE.VIEW");
        UUID orgId = seedOrganization(TENANT);
        UUID orgUnitId = createOrgUnitViaApi(orgId);
        UUID childId = createOrgUnitViaApi(orgId);

        mockMvc.perform(post("/api/v2/hr/org-units/{id}/revise", childId)
                        .with(authentication(principal()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentOrgUnitId\":\"" + orgUnitId + "\"," +
                                "\"name\":\"Engineering South\",\"effectiveDate\":\"2026-10-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Engineering South"))
                .andExpect(jsonPath("$.parentOrgUnitId").value(orgUnitId.toString()))
                .andExpect(jsonPath("$.effectiveFrom").value("2026-10-01"));

        // The revise must NOT have created a cycle: re-revising with the child
        // as parent must be rejected as a cycle.
        mockMvc.perform(post("/api/v2/hr/org-units/{id}/revise", orgUnitId)
                        .with(authentication(principal()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentOrgUnitId\":\"" + childId + "\"," +
                                "\"name\":\"Engineering Root\",\"effectiveDate\":\"2026-11-01\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HRM_INVALID_STATE_TRANSITION"));
    }

    @Test
    void createOrgUnit_missingEffectiveFrom_isValidationError() throws Exception {
        GRANTED.add("HRM.ORG_STRUCTURE.MANAGE");
        UUID orgId = seedOrganization(TENANT);
        mockMvc.perform(post("/api/v2/hr/org-units")
                        .with(authentication(principal()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"" + orgId + "\",\"name\":\"No Date Unit\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HRM_VALIDATION_FAILED"));
    }

    @Test
    void getOrgUnit_unknownId_notFound404() throws Exception {
        GRANTED.add("HRM.ORG_STRUCTURE.VIEW");
        mockMvc.perform(get("/api/v2/hr/org-units/{id}", UUID.randomUUID()).with(authentication(principal())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HRM_ORG_UNIT_NOT_FOUND"));
    }

    // ==================== JOBS ====================

    @Test
    void createJob_thenRevise_createsVersions() throws Exception {
        GRANTED.add("HRM.ORG_STRUCTURE.MANAGE");
        GRANTED.add("HRM.ORG_STRUCTURE.VIEW");
        UUID orgId = seedOrganization(TENANT);

        String created = mockMvc.perform(post("/api/v2/hr/jobs")
                        .with(authentication(principal()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"" + orgId + "\",\"title\":\"Senior Engineer\"," +
                                "\"grade\":\"G5\",\"effectiveFrom\":\"2026-09-01\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.jobId").isNotEmpty())
                .andExpect(jsonPath("$.title").value("Senior Engineer"))
                .andReturn().getResponse().getContentAsString();

        String jobId = com.jayway.jsonpath.JsonPath.read(created, "$.jobId");
        mockMvc.perform(post("/api/v2/hr/jobs/{id}/revise", jobId)
                        .with(authentication(principal()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Staff Engineer\",\"grade\":\"G6\",\"effectiveDate\":\"2026-12-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Staff Engineer"))
                .andExpect(jsonPath("$.grade").value("G6"))
                .andExpect(jsonPath("$.effectiveFrom").value("2026-12-01"));

        mockMvc.perform(get("/api/v2/hr/jobs/{id}", jobId).with(authentication(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Staff Engineer"));
    }

    // ==================== POSITIONS ====================

    @Test
    void createPosition_thenFreeze_thenClose_staffabilityOnly() throws Exception {
        GRANTED.add("HRM.ORG_STRUCTURE.MANAGE");
        GRANTED.add("HRM.ORG_STRUCTURE.VIEW");
        UUID orgId = seedOrganization(TENANT);

        String created = mockMvc.perform(post("/api/v2/hr/positions")
                        .with(authentication(principal()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Backend Position\",\"code\":\"POS-BE-1\"," +
                                "\"effectiveFrom\":\"2026-09-01\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.positionId").isNotEmpty())
                .andExpect(jsonPath("$.staffability").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();

        String positionId = com.jayway.jsonpath.JsonPath.read(created, "$.positionId");

        mockMvc.perform(post("/api/v2/hr/positions/{id}/freeze", positionId)
                        .with(authentication(principal()))
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.staffability").value("INACTIVE"));

        mockMvc.perform(post("/api/v2/hr/positions/{id}/close", positionId)
                        .with(authentication(principal()))
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.staffability").value("ARCHIVED"));

        // Close on an ARCHIVED position is an illegal transition.
        mockMvc.perform(post("/api/v2/hr/positions/{id}/close", positionId)
                        .with(authentication(principal()))
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HRM_INVALID_STATE_TRANSITION"));

        // Staffability changed — version history untouched.
        mockMvc.perform(get("/api/v2/hr/positions/{id}", positionId).with(authentication(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.staffability").value("ARCHIVED"))
                .andExpect(jsonPath("$.title").value("Backend Position"));
    }

    @Test
    void revisePosition_updatesVersionHistory() throws Exception {
        GRANTED.add("HRM.ORG_STRUCTURE.MANAGE");
        GRANTED.add("HRM.ORG_STRUCTURE.VIEW");
        UUID orgId = seedOrganization(TENANT);
        UUID positionId = createPositionViaApi();

        mockMvc.perform(post("/api/v2/hr/positions/{id}/revise", positionId)
                        .with(authentication(principal()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Renamed Position\",\"effectiveDate\":\"2026-10-15\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Renamed Position"))
                .andExpect(jsonPath("$.effectiveFrom").value("2026-10-15"));

        mockMvc.perform(get("/api/v2/hr/positions/{id}", positionId).with(authentication(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Renamed Position"))
                .andExpect(jsonPath("$.effectiveFrom").value("2026-10-15"));
    }

    @Test
    void getPosition_unknownId_notFound404() throws Exception {
        GRANTED.add("HRM.ORG_STRUCTURE.VIEW");
        mockMvc.perform(get("/api/v2/hr/positions/{id}", UUID.randomUUID()).with(authentication(principal())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HRM_POSITION_NOT_FOUND"));
    }

    @Test
    void crossTenant_structureRead_failsClosedAs404() throws Exception {
        GRANTED.add("HRM.ORG_STRUCTURE.VIEW");
        UUID orgId = seedOrganization(OTHER_TENANT);
        UUID foreignOrgUnitId = createOrgUnitViaApiForTenant(OTHER_TENANT, orgId);
        mockMvc.perform(get("/api/v2/hr/org-units/{id}", foreignOrgUnitId).with(authentication(principal())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HRM_ORG_UNIT_NOT_FOUND"));
    }

    // ==================== SEED HELPERS (PostgreSQL Direct) ====================

    private UUID seedOrganization(UUID tenantId) {
        executePlain("INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) " +
                        "VALUES (?, 'Structure V2 Tenant', ?, 'ACTIVE', NOW(), NOW()) ON CONFLICT (id) DO NOTHING",
                ps -> {
                    ps.setObject(1, tenantId);
                    ps.setString(2, "t-" + tenantId.toString().substring(0, 8));
                });
        UUID id = UUID.randomUUID();
        executeAsTenant(tenantId, "INSERT INTO organizations (id, tenant_id, name, status, created_at, updated_at) " +
                "VALUES (?, ?, 'Structure Org ' || ?, 'ACTIVE', NOW(), NOW())", ps -> {
            ps.setObject(1, id);
            ps.setObject(2, tenantId);
            ps.setString(3, UUID.randomUUID().toString().substring(0, 8));
        });
        return id;
    }

    private UUID createOrgUnitViaApi(UUID organizationId) {
        return createOrgUnitViaApiForTenant(TENANT, organizationId);
    }

    private UUID createOrgUnitViaApiForTenant(UUID tenantId, UUID organizationId) {
        GRANTED.add("HRM.ORG_STRUCTURE.MANAGE");
        try {
            String created = mockMvc.perform(post("/api/v2/hr/org-units")
                            .with(authentication(principal(tenantId)))
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"organizationId\":\"" + organizationId + "\",\"name\":\"Seed Unit\"," +
                                    "\"code\":\"SEED-OU\",\"unitType\":\"DEPARTMENT\",\"effectiveFrom\":\"2026-09-01\"}"))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            return UUID.fromString(com.jayway.jsonpath.JsonPath.read(created, "$.orgUnitId"));
        } catch (Exception e) {
            throw new IllegalStateException("Org unit seed via API failed", e);
        }
    }

    private UUID createPositionViaApi() {
        GRANTED.add("HRM.ORG_STRUCTURE.MANAGE");
        try {
            String created = mockMvc.perform(post("/api/v2/hr/positions")
                            .with(authentication(principal()))
                            .header("Idempotency-Key", UUID.randomUUID().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Seed Position\",\"effectiveFrom\":\"2026-09-01\"}"))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            return UUID.fromString(com.jayway.jsonpath.JsonPath.read(created, "$.positionId"));
        } catch (Exception e) {
            throw new IllegalStateException("Position seed via API failed", e);
        }
    }

    @FunctionalInterface
    interface SqlBinder {
        void bind(PreparedStatement ps) throws Exception;
    }

    private void executeAsTenant(UUID tenantId, String sql, SqlBinder binder) {
        try (Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement st = c.createStatement()) {
            st.execute("SET app.tenant_id = '" + tenantId + "'");
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                binder.bind(ps);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Seed failed: " + e.getMessage(), e);
        }
    }

    private void executePlain(String sql, SqlBinder binder) {
        try (Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                binder.bind(ps);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Seed failed: " + e.getMessage(), e);
        }
    }

    /** Per-test capability control: allow exactly what GRANTED holds, deny the rest. */
    @TestConfiguration
    static class AuthProbeConfig {

        @Bean
        public static BeanDefinitionRegistryPostProcessor removeRealJwtProvider() {
            return new BeanDefinitionRegistryPostProcessor() {
                @Override
                public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
                    if (registry.containsBeanDefinition("jwtTokenProvider")) {
                        registry.removeBeanDefinition("jwtTokenProvider");
                    }
                }
            };
        }

        @Bean
        @Primary
        JwtTokenProvider testJwtTokenProvider() {
            return org.mockito.Mockito.mock(JwtTokenProvider.class);
        }

        @Bean
        CapabilityAuthorizationBypass capabilityAuthorizationBypass() {
            return () -> true;
        }

        @Bean
        @org.springframework.core.annotation.Order(-100)
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            http
                    .securityMatcher("/**")
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
            return http.build();
        }

        @Bean
        @Primary
        CapabilityEvaluationService v2StructureCapabilityEvaluationService() {
            CapabilityEvaluationService mock = org.mockito.Mockito.mock(CapabilityEvaluationService.class);
            org.mockito.Mockito.when(mock.evaluate(org.mockito.ArgumentMatchers.any(UUID.class),
                            org.mockito.ArgumentMatchers.any(UUID.class),
                            org.mockito.ArgumentMatchers.anyString(),
                            org.mockito.ArgumentMatchers.any()))
                    .thenAnswer(invocation -> {
                        String capability = invocation.getArgument(2);
                        boolean allowed = GRANTED.contains(capability);
                        return new AccessDecisionResponse(
                                invocation.getArgument(0), invocation.getArgument(1),
                                invocation.getArgument(3), capability,
                                allowed, allowed ? "ALLOW" : "DENY", UUID.randomUUID(),
                                allowed ? "TEST_ROLE" : null);
                    });
            return mock;
        }
    }
}
