package com.sanad.platform.hr.compatibility;

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
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HRM-G0 / WS5 Task 7 — v1 compatibility semantics.
 *
 * <p>Locks the safe v1 behavior after the canonical cutover work:
 *
 * <ul>
 *   <li>v1 DELETE never physically deletes employment data — it answers
 *       409 HRM_MIGRATION_REQUIRED and the row survives (verifiable via the
 *       read path)</li>
 *   <li>v1 create in an ambiguous employer context (zero active legal
 *       entities / organizations) returns 409 HRM_MIGRATION_REQUIRED
 *       instead of guessing</li>
 *   <li>v1 create with exactly one active legal entity + one active
 *       organization proceeds</li>
 *   <li>v1 PATCH accepts profile-only edits and rejects lifecycle fields
 *       (status / employment type) as untranslatable</li>
 * </ul>
 *
 * <p>PostgreSQL Direct only.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(HrV1CompatibilityIntegrationTest.AuthProbeConfig.class)
class HrV1CompatibilityIntegrationTest {

    private static final String DB_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://127.0.0.1:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "sanad_pass");

    static final Set<String> GRANTED = new HashSet<>();

    static final UUID TENANT = UUID.fromString("12121212-1212-1212-1212-121212121212");
    static final UUID AMBIGUOUS_TENANT = UUID.fromString("14141414-1414-1414-1414-141414141414");
    static final UUID USER = UUID.fromString("13131313-1313-1313-1313-131313131313");

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
        GRANTED.add("HR.EMPLOYEE.READ");
        GRANTED.add("HR.EMPLOYEE.WRITE");
        GRANTED.add("HR.EMPLOYEE.ARCHIVE");
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

    @Test
    void v1DeleteNeverPhysicallyDeletesEmployment() throws Exception {
        UUID employeeId = seedLegacyEmployee();

        mockMvc.perform(delete("/api/v1/hr/employees/{id}", employeeId).with(authentication(principal())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HRM_MIGRATION_REQUIRED"));

        // The employment row must still exist — verifiable via the v1 read path.
        mockMvc.perform(get("/api/v1/hr/employees/{id}", employeeId).with(authentication(principal())))
                .andExpect(status().isOk());
    }

    @Test
    void ambiguousV1CreateReturnsMigrationRequiredInsteadOfGuessing() throws Exception {
        // Dedicated tenant with NO active legal entity / organization.
        seedTenant(AMBIGUOUS_TENANT);
        mockMvc.perform(post("/api/v1/hr/employees")
                        .with(authentication(principal(AMBIGUOUS_TENANT)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Legacy\",\"lastName\":\"Create\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HRM_MIGRATION_REQUIRED"));
    }

    @Test
    void unambiguousV1CreateSucceeds() throws Exception {
        // Fresh tenant per run: the unambiguity contract counts exactly one
        // active legal entity and one active organization.
        UUID freshTenant = UUID.randomUUID();
        seedTenant(freshTenant);
        seedSingleEmployerContextFor(freshTenant);

        mockMvc.perform(post("/api/v1/hr/employees")
                        .with(authentication(principal(freshTenant)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Legacy\",\"lastName\":\"Create\",\"employmentType\":\"FULL_TIME\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.firstName").value("Legacy"))
                .andExpect(jsonPath("$.lastName").value("Create"));
    }

    @Test
    void v1PatchAcceptsProfileOnlyEdits() throws Exception {
        UUID employeeId = seedLegacyEmployee();

        mockMvc.perform(patch("/api/v1/hr/employees/{id}", employeeId)
                        .with(authentication(principal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"legacy-patch@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("legacy-patch@example.com"));
    }

    @Test
    void v1PatchRejectsLifecycleStatusChanges() throws Exception {
        UUID employeeId = seedLegacyEmployee();

        mockMvc.perform(patch("/api/v1/hr/employees/{id}", employeeId)
                        .with(authentication(principal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"TERMINATED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HRM_MIGRATION_REQUIRED"));
    }

    // ==================== seed helpers (PostgreSQL Direct) ====================

    private UUID seedLegacyEmployee() {
        seedTenant(TENANT);
        UUID id = UUID.randomUUID();
        executeAsTenant(TENANT, "INSERT INTO hr_employees (id, tenant_id, person_id, legal_entity_id, employee_number, " +
                        "first_name, last_name, display_name, employment_type, status, hire_date, version) " +
                        "VALUES (?, ?, NULL, NULL, ?, 'Legacy', 'Emp', 'Legacy Emp', 'FULL_TIME', 'ACTIVE', ?, 0)",
                ps -> {
                    ps.setObject(1, id);
                    ps.setObject(2, TENANT);
                    ps.setString(3, "EMP-LEG-" + UUID.randomUUID().toString().substring(0, 8));
                    ps.setObject(4, LocalDate.of(2026, 1, 1));
                });
        return id;
    }

    private void seedTenant(UUID tenantId) {
        executePlain("INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) " +
                        "VALUES (?, 'V1 Compat Tenant', ?, 'ACTIVE', NOW(), NOW()) ON CONFLICT (id) DO NOTHING",
                ps -> {
                    ps.setObject(1, tenantId);
                    ps.setString(2, "t-" + tenantId.toString().substring(0, 8));
                });
    }

    private void seedSingleEmployerContext() {
        seedSingleEmployerContextFor(TENANT);
    }

    private void seedSingleEmployerContextFor(UUID tenantId) {
        executeAsTenant(tenantId, "INSERT INTO organizations (id, tenant_id, name, status, created_at, updated_at) " +
                "VALUES (?, ?, 'Compat Org ' || ?, 'ACTIVE', NOW(), NOW())", ps -> {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenantId);
            ps.setString(3, UUID.randomUUID().toString().substring(0, 8));
        });
        executeAsTenant(tenantId, "INSERT INTO legal_entities (id, tenant_id, code, name, registered_country_code, " +
                        "statutory_country_code, status, created_at, updated_at) " +
                        "VALUES (?, ?, ?, 'Compat LE', 'AE', 'AE', 'ACTIVE', NOW(), NOW())", ps -> {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenantId);
            ps.setString(3, "LE-" + UUID.randomUUID().toString().substring(0, 8));
        });
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

    /** Per-test capability control. */
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
        CapabilityEvaluationService v1CompatCapabilityEvaluationService() {
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
