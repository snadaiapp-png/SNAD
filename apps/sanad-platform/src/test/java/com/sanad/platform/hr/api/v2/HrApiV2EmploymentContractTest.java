package com.sanad.platform.hr.api.v2;

import com.sanad.platform.access.AccessDecisionResponse;
import com.sanad.platform.access.evaluation.CapabilityEvaluationService;
import com.sanad.platform.security.authorization.CapabilityAuthorizationBypass;
import com.sanad.platform.security.service.JwtTokenProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HRM-G0 / WS5 Task 3 RED contract — People and Employment v2 endpoints
 * (Employment lifecycle slice of the canonical 58-operation surface).
 *
 * <p>Locks:
 *
 * <ul>
 *   <li>independent coarse capabilities per operation (HRM.EMPLOYEE.CREATE /
 *       VIEW / UPDATE / TERMINATE), enforced server-side; denial produces the
 *       canonical HRM_SCOPE_DENIED envelope with HTTP 403</li>
 *   <li>critical lifecycle POSTs require an explicit {@code Idempotency-Key}
 *       header and an explicit {@code effectiveDate} + {@code expectedVersion}
 *       (no server-side defaulting to "now"/current version)</li>
 *   <li>duplicate request with the same key + fingerprint replays the SAME
 *       response; same key with a different fingerprint yields 409
 *       HRM_IDEMPOTENCY_CONFLICT; a stale expectedVersion yields 409
 *       HRM_CONCURRENCY_CONFLICT</li>
 * </ul>
 *
 * <p>PostgreSQL Direct only — canonical rows are seeded over real JDBC with
 * tenant GUC (fail-closed RLS), never mocked.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(HrApiV2EmploymentContractTest.AuthProbeConfig.class)
class HrApiV2EmploymentContractTest {

    private static final String DB_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "");

    /** Capabilities the fake principal holds for the current test (default: none). */
    static final Set<String> GRANTED = new HashSet<>();

    static final UUID TENANT = UUID.fromString("33333333-3333-3333-3333-333333333333");
    static final UUID USER = UUID.fromString("44444444-4444-4444-4444-444444444444");

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

    // ==================== CAPABILITY BOUNDARIES ====================

    @Test
    void createEmployment_withoutCapability_scopeDenied() throws Exception {
        mockMvc.perform(post("/api/v2/hr/employments")
                        .with(authentication(principal()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HRM_SCOPE_DENIED"));
    }

    @Test
    void listEmployments_withoutCapability_scopeDenied() throws Exception {
        mockMvc.perform(get("/api/v2/hr/employments").with(authentication(principal())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HRM_SCOPE_DENIED"));
    }

    @Test
    void activate_withoutCapability_scopeDenied() throws Exception {
        mockMvc.perform(post("/api/v2/hr/employments/{id}/activate", UUID.randomUUID())
                        .with(authentication(principal()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effectiveDate\":\"2026-09-04\",\"expectedVersion\":0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HRM_SCOPE_DENIED"));
    }

    @Test
    void terminate_requiresDedicatedTerminateCapability() throws Exception {
        // HRM.EMPLOYEE.UPDATE alone must NOT authorize termination.
        GRANTED.add("HRM.EMPLOYEE.UPDATE");
        mockMvc.perform(post("/api/v2/hr/employments/{id}/terminate", UUID.randomUUID())
                        .with(authentication(principal()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effectiveDate\":\"2026-09-04\",\"expectedVersion\":0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HRM_SCOPE_DENIED"));
    }

    // ==================== CRUD + LIFECYCLE (granted path) ====================

    @Test
    void createEmployment_thenReadBack() throws Exception {
        GRANTED.add("HRM.EMPLOYEE.CREATE");
        GRANTED.add("HRM.EMPLOYEE.VIEW");
        seedTenant();

        String created = mockMvc.perform(post("/api/v2/hr/employments")
                        .with(authentication(principal()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.employmentId").isNotEmpty())
                .andExpect(jsonPath("$.currentStatus").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();

        String employmentId = com.jayway.jsonpath.JsonPath.read(created, "$.employmentId");
        mockMvc.perform(get("/api/v2/hr/employments/{id}", employmentId).with(authentication(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employmentId").value(employmentId))
                .andExpect(jsonPath("$.currentStatus").value("DRAFT"))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void missingEffectiveDate_isValidationError_notServerDefaulted() throws Exception {
        GRANTED.add("HRM.EMPLOYEE.UPDATE");
        UUID employmentId = seedEmploymentViaSql();
        mockMvc.perform(post("/api/v2/hr/employments/{id}/activate", employmentId)
                        .with(authentication(principal()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HRM_VALIDATION_FAILED"));
    }

    @Test
    void missingIdempotencyKey_isRejected() throws Exception {
        GRANTED.add("HRM.EMPLOYEE.UPDATE");
        UUID employmentId = seedEmploymentViaSql();
        mockMvc.perform(post("/api/v2/hr/employments/{id}/activate", employmentId)
                        .with(authentication(principal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effectiveDate\":\"2026-09-04\",\"expectedVersion\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void lifecycle_transition_producesTypedResult() throws Exception {
        GRANTED.add("HRM.EMPLOYEE.UPDATE");
        UUID employmentId = seedEmploymentViaSql();
        mockMvc.perform(post("/api/v2/hr/employments/{id}/submit-onboarding", employmentId)
                        .with(authentication(principal()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effectiveDate\":\"2026-09-04\",\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employmentId").value(employmentId.toString()))
                .andExpect(jsonPath("$.previousStatus").value("DRAFT"))
                .andExpect(jsonPath("$.newStatus").value("PENDING_ONBOARDING"));
    }

    // ==================== IDEMPOTENCY + CONCURRENCY ====================

    @Test
    void duplicateActivateRequest_replaysSameResponse() throws Exception {
        GRANTED.add("HRM.EMPLOYEE.UPDATE");
        UUID employmentId = seedEmploymentViaSql();
        String key = UUID.randomUUID().toString();
        String body = "{\"effectiveDate\":\"2026-09-04\",\"expectedVersion\":0}";

        String first = mockMvc.perform(post("/api/v2/hr/employments/{id}/submit-onboarding", employmentId)
                        .with(authentication(principal()))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String second = mockMvc.perform(post("/api/v2/hr/employments/{id}/submit-onboarding", employmentId)
                        .with(authentication(principal()))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(second).isEqualTo(first);
    }

    @Test
    void sameKey_differentFingerprint_conflict409() throws Exception {
        GRANTED.add("HRM.EMPLOYEE.UPDATE");
        UUID employmentId = seedEmploymentViaSql();
        String key = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v2/hr/employments/{id}/submit-onboarding", employmentId)
                        .with(authentication(principal()))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effectiveDate\":\"2026-09-04\",\"expectedVersion\":0}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v2/hr/employments/{id}/submit-onboarding", employmentId)
                        .with(authentication(principal()))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effectiveDate\":\"2026-10-01\",\"expectedVersion\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HRM_IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void staleExpectedVersion_returns409ConcurrencyConflict() throws Exception {
        GRANTED.add("HRM.EMPLOYEE.UPDATE");
        UUID employmentId = seedEmploymentViaSql();
        mockMvc.perform(post("/api/v2/hr/employments/{id}/submit-onboarding", employmentId)
                        .with(authentication(principal()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effectiveDate\":\"2026-09-04\",\"expectedVersion\":0}"))
                .andExpect(status().isOk());

        // version is now 1; sending expectedVersion=0 is stale.
        mockMvc.perform(post("/api/v2/hr/employments/{id}/activate", employmentId)
                        .with(authentication(principal()))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effectiveDate\":\"2026-09-05\",\"expectedVersion\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HRM_CONCURRENCY_CONFLICT"));
    }

    // ==================== SEED HELPERS (PostgreSQL Direct) ====================

    private String validCreateBody() {
        UUID personId = seedPerson();
        UUID legalEntityId = seedLegalEntity();
        return """
                {"personId":"%s","legalEntityId":"%s","employeeNumber":"EMP-V2-%s",
                 "employmentStartDate":"2026-09-04","laborJurisdictionCode":"SA",
                 "workerClassificationCode":"FULL_TIME"}"""
                .formatted(personId, legalEntityId, UUID.randomUUID().toString().substring(0, 8));
    }

    private UUID seedEmploymentViaSql() {
        seedTenant();
        UUID personId = seedPerson();
        UUID legalEntityId = seedLegalEntity();
        UUID employmentId = UUID.randomUUID();
        executeAsTenant("INSERT INTO hr_employees (id, tenant_id, person_id, legal_entity_id, employee_number, " +
                        "first_name, last_name, display_name, employment_type, status, hire_date, version) " +
                        "VALUES (?, ?, ?, ?, ?, 'Test', 'Employee', 'Test Employee', 'FULL_TIME', 'DRAFT', ?, 0)",
                ps -> {
                    ps.setObject(1, employmentId);
                    ps.setObject(2, TENANT);
                    ps.setObject(3, personId);
                    ps.setObject(4, legalEntityId);
                    ps.setString(5, "EMP-SQL-" + UUID.randomUUID().toString().substring(0, 8));
                    ps.setObject(6, LocalDate.of(2026, 1, 1));
                });
        return employmentId;
    }

    private void seedTenant() {
        executePlain("INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) " +
                        "VALUES (?, 'V2 Contract Tenant', ?, 'ACTIVE', NOW(), NOW()) " +
                        "ON CONFLICT (id) DO NOTHING",
                ps -> {
                    ps.setObject(1, TENANT);
                    ps.setString(2, "t-" + TENANT.toString().substring(0, 8));
                });
    }

    private UUID seedPerson() {
        UUID personId = UUID.randomUUID();
        executeAsTenant("INSERT INTO hr_people (id, tenant_id, user_id, first_name, last_name, display_name, version) " +
                        "VALUES (?, ?, NULL, 'Test', 'Person', 'Test Person', 0)",
                ps -> {
                    ps.setObject(1, personId);
                    ps.setObject(2, TENANT);
                });
        return personId;
    }

    private UUID seedLegalEntity() {
        UUID legalEntityId = UUID.randomUUID();
        executeAsTenant("INSERT INTO legal_entities (id, tenant_id, code, name, registered_country_code, " +
                        "statutory_country_code, status, created_at, updated_at) " +
                        "VALUES (?, ?, ?, 'V2 Contract Legal Entity', 'SA', 'SA', 'ACTIVE', NOW(), NOW())",
                ps -> {
                    ps.setObject(1, legalEntityId);
                    ps.setObject(2, TENANT);
                    ps.setString(3, "LE-" + UUID.randomUUID().toString().substring(0, 8));
                });
        return legalEntityId;
    }

    @FunctionalInterface
    interface SqlBinder {
        void bind(PreparedStatement ps) throws Exception;
    }

    /** Session GUC + autocommit insert (proven HrEmploymentLifecycleIntegrationTest pattern). */
    private void executeAsTenant(String sql, SqlBinder binder) {
        try (Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement st = c.createStatement()) {
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
        CapabilityEvaluationService v2ContractCapabilityEvaluationService() {
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
