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
 * HRM-G0 / WS5 Task 5 — sensitive-surface authorization contract
 * (Contract / Compensation / Compliance / Audit v2 endpoints).
 *
 * <p>Locks the restricted-surface boundaries of the canonical 58-operation
 * surface: employee-directory capability alone must NEVER reach
 * compensation, audit, contract, or override-approval operations; every
 * restricted endpoint produces the canonical 403 HRM_SCOPE_DENIED envelope
 * for missing capabilities; compliance context metadata is the only
 * employee-readable compliance surface. Deep behavioral coverage for these
 * services already exists in the WS3/WS6 suites — this class locks the
 * HTTP capability boundaries.
 *
 * <p>PostgreSQL Direct only.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(HrApiV2SensitiveContractTest.AuthProbeConfig.class)
class HrApiV2SensitiveContractTest {

    private static final String DB_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://127.0.0.1:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "sanad_pass");

    static final Set<String> GRANTED = new HashSet<>();

    static final UUID TENANT = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
    static final UUID USER = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

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
        var token = new UsernamePasswordAuthenticationToken(
                "test-principal", "n/a", List.of(new SimpleGrantedAuthority("ROLE_TEST")));
        token.setDetails(Map.of("tenant_id", TENANT.toString(), "user_id", USER.toString()));
        return token;
    }

    // ==================== COMPENSATION GATING ====================

    @Test
    void compensationList_withoutCapability_scopeDenied() throws Exception {
        mockMvc.perform(get("/api/v2/hr/compensation-packages?employmentId=" + UUID.randomUUID())
                        .with(authentication(principal())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HRM_SCOPE_DENIED"));
    }

    @Test
    void compensationList_employeeViewAlone_scopeDenied() throws Exception {
        // Employee directory capability must NOT reach compensation data.
        GRANTED.add("HRM.EMPLOYEE.VIEW");
        mockMvc.perform(get("/api/v2/hr/compensation-packages?employmentId=" + UUID.randomUUID())
                        .with(authentication(principal())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HRM_SCOPE_DENIED"));
    }

    @Test
    void compensationGet_compensationViewRequired() throws Exception {
        GRANTED.add("HRM.EMPLOYEE.VIEW");
        mockMvc.perform(get("/api/v2/hr/compensation-packages/{id}", UUID.randomUUID())
                        .with(authentication(principal())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HRM_SCOPE_DENIED"));
    }

    @Test
    void compensationCreate_viewAlone_scopeDenied() throws Exception {
        // Read capability must NOT authorize compensation mutation.
        GRANTED.add("HRM.COMPENSATION.VIEW");
        mockMvc.perform(post("/api/v2/hr/compensation-packages")
                        .with(authentication(principal()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employmentId\":\"" + UUID.randomUUID() + "\",\"currencyCode\":\"SAR\"," +
                                "\"effectiveFrom\":\"2026-09-01\",\"components\":[]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HRM_SCOPE_DENIED"));
    }

    // ==================== CONTRACT GATING ====================

    @Test
    void contractList_withoutCapability_scopeDenied() throws Exception {
        mockMvc.perform(get("/api/v2/hr/contracts").with(authentication(principal())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HRM_SCOPE_DENIED"));
    }

    @Test
    void contractCreate_viewAlone_scopeDenied() throws Exception {
        GRANTED.add("HRM.CONTRACT.VIEW");
        mockMvc.perform(post("/api/v2/hr/contracts")
                        .with(authentication(principal()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employmentId\":\"" + UUID.randomUUID() + "\",\"contractNumber\":\"C-1\"," +
                                "\"effectiveDate\":\"2026-09-01\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HRM_SCOPE_DENIED"));
    }

    // ==================== AUDIT GATING ====================

    @Test
    void auditRead_withoutCapability_scopeDenied() throws Exception {
        mockMvc.perform(get("/api/v2/hr/audit").with(authentication(principal())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HRM_SCOPE_DENIED"));
    }

    @Test
    void auditRead_employeeViewAlone_scopeDenied() throws Exception {
        GRANTED.add("HRM.EMPLOYEE.VIEW");
        mockMvc.perform(get("/api/v2/hr/audit").with(authentication(principal())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HRM_SCOPE_DENIED"));
    }

    // ==================== COMPLIANCE OVERRIDE GATING ====================

    @Test
    void overrideList_withoutRequestCapability_scopeDenied() throws Exception {
        GRANTED.add("HRM.COMPLIANCE_OVERRIDE.APPROVE");
        mockMvc.perform(get("/api/v2/hr/compliance/overrides").with(authentication(principal())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HRM_SCOPE_DENIED"));
    }

    @Test
    void overrideApprove_requiresApproveCapability_requestInsufficient() throws Exception {
        // Request capability must NOT authorize approval — four-eyes starts here.
        GRANTED.add("HRM.COMPLIANCE_OVERRIDE.REQUEST");
        mockMvc.perform(post("/api/v2/hr/compliance/overrides/{id}/approve", UUID.randomUUID())
                        .with(authentication(principal()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"self-approval attempt\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HRM_SCOPE_DENIED"));
    }

    @Test
    void complianceContext_employeeViewReadable() throws Exception {
        // Policy MODE metadata (Global Mode warning source) is employee-readable.
        GRANTED.add("HRM.EMPLOYEE.VIEW");
        UUID employmentId = seedEmployment();
        mockMvc.perform(get("/api/v2/hr/compliance/context?employmentId=" + employmentId)
                        .with(authentication(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("GLOBAL"));
    }

    private UUID seedEmployment() {
        executePlain("INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) " +
                        "VALUES (?, 'Sensitive V2 Tenant', ?, 'ACTIVE', NOW(), NOW()) ON CONFLICT (id) DO NOTHING",
                ps -> {
                    ps.setObject(1, TENANT);
                    ps.setString(2, "t-" + TENANT.toString().substring(0, 8));
                });
        UUID legalEntityId = UUID.randomUUID();
        executeAsTenant("INSERT INTO legal_entities (id, tenant_id, code, name, registered_country_code, " +
                        "statutory_country_code, status, created_at, updated_at) " +
                        "VALUES (?, ?, ?, 'Sensitive LE', 'AE', 'AE', 'ACTIVE', NOW(), NOW())",
                ps -> {
                    ps.setObject(1, legalEntityId);
                    ps.setObject(2, TENANT);
                    ps.setString(3, "LE-" + UUID.randomUUID().toString().substring(0, 8));
                });
        UUID personId = UUID.randomUUID();
        executeAsTenant("INSERT INTO hr_people (id, tenant_id, user_id, first_name, last_name, display_name, version) " +
                "VALUES (?, ?, NULL, 'Sens', 'Seed', 'Sens Seed', 0)", ps -> {
            ps.setObject(1, personId);
            ps.setObject(2, TENANT);
        });
        UUID employmentId = UUID.randomUUID();
        executeAsTenant("INSERT INTO hr_employees (id, tenant_id, person_id, legal_entity_id, employee_number, " +
                        "first_name, last_name, display_name, employment_type, status, hire_date, version) " +
                        "VALUES (?, ?, ?, ?, ?, 'Sens', 'Seed', 'Sens Seed', 'FULL_TIME', 'ACTIVE', ?, 0)",
                ps -> {
                    ps.setObject(1, employmentId);
                    ps.setObject(2, TENANT);
                    ps.setObject(3, personId);
                    ps.setObject(4, legalEntityId);
                    ps.setString(5, "EMP-SS-" + UUID.randomUUID().toString().substring(0, 8));
                    ps.setObject(6, java.time.LocalDate.of(2026, 1, 1));
                });
        executeAsTenant("INSERT INTO hr_employment_jurisdiction_periods (id, tenant_id, employment_id, " +
                        "labor_jurisdiction, approval_status, approval_reference, approved_by, approved_at, effective_from, effective_to, created_at) " +
                        "VALUES (?, ?, ?, 'AE', 'APPROVED', 'WS5-TEST-APPROVED', ?, NOW(), ?::date, NULL, NOW())",
                ps -> {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, TENANT);
                    ps.setObject(3, employmentId);
                    ps.setObject(4, USER);
                    ps.setString(5, "2026-01-01");
                });
        return employmentId;
    }

    private void executeAsTenant(String sql, SqlBinder binder) {
        try (Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             java.sql.Statement st = c.createStatement()) {
            st.execute("SET app.tenant_id = '" + TENANT + "'");
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

    @FunctionalInterface
    interface SqlBinder {
        void bind(PreparedStatement ps) throws Exception;
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
        CapabilityEvaluationService v2SensitiveCapabilityEvaluationService() {
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
