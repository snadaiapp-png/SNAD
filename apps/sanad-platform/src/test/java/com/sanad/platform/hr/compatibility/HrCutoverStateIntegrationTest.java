package com.sanad.platform.hr.compatibility;

import com.sanad.platform.access.AccessDecisionResponse;
import com.sanad.platform.access.evaluation.CapabilityEvaluationService;
import com.sanad.platform.security.authorization.CapabilityAuthorizationBypass;
import com.sanad.platform.security.service.JwtTokenProvider;
import org.junit.jupiter.api.AfterAll;
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
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HRM-G0 / WS5 Task 11 — deterministic tenant cutover state gates.
 *
 * <p>Locks the migration-phase contract of the cutover procedure:
 *
 * <ul>
 *   <li>a MIGRATING tenant can still READ through v1 but CANNOT write
 *       through v1 (write freeze) — GET 200, POST/PATCH 409
 *       HRM_MIGRATION_REQUIRED</li>
 *   <li>the transition to CANONICAL requires zero unresolved migration
 *       rows — a tenant with unresolved rows is BLOCKED by
 *       hr_reconcile_tenant and never reaches CANONICAL</li>
 * </ul>
 *
 * <p>PostgreSQL Direct only. The cutover SQL scripts
 * (scripts/hrm/g0-cutover-tenant.sql, g0-rollback-tenant.sql) are
 * rehearsed against this same fixture class of disposable tenants.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(HrCutoverStateIntegrationTest.CutoverProbeConfig.class)
class HrCutoverStateIntegrationTest {

    private static final String DB_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://127.0.0.1:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "sanad_pass");

    static final Set<String> GRANTED = new HashSet<>();

    static final UUID TENANT = UUID.fromString("16161616-1616-1616-1616-161616161616");
    static final UUID RECONCILE_TENANT = UUID.fromString("17171717-1717-1717-1717-171717171717");
    static final UUID USER = UUID.fromString("18181818-1818-1818-1818-181818181818");

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

    @AfterAll
    static void releaseTenants() {
        // Restore both disposable tenants to LEGACY so repeated runs stay
        // deterministic and the write freeze never leaks between runs.
        executeAsTenant(TENANT, "UPDATE hr_migration_tenant_state SET state = 'LEGACY', updated_at = NOW() " +
                "WHERE tenant_id = ?", ps -> ps.setObject(1, TENANT));
        executeAsTenant(RECONCILE_TENANT, "UPDATE hr_migration_tenant_state SET state = 'LEGACY', updated_at = NOW() " +
                "WHERE tenant_id = ?", ps -> ps.setObject(1, RECONCILE_TENANT));
    }

    @BeforeEach
    void resetGrants() {
        GRANTED.clear();
        GRANTED.add("HR.EMPLOYEE.READ");
        GRANTED.add("HR.EMPLOYEE.WRITE");
        GRANTED.add("HR.EMPLOYEE.ARCHIVE");
    }

    private UsernamePasswordAuthenticationToken principal(UUID tenantId) {
        var token = new UsernamePasswordAuthenticationToken(
                "test-principal", "n/a", List.of(new SimpleGrantedAuthority("ROLE_TEST")));
        token.setDetails(Map.of("tenant_id", tenantId.toString(), "user_id", USER.toString()));
        return token;
    }

    @Test
    void migratingTenantCanReadV1ButCannotWriteV1() throws Exception {
        // Fresh disposable tenant with an unambiguous employer context and
        // one legacy employee.
        UUID tenant = UUID.randomUUID();
        seedTenant(tenant);
        seedSingleEmployerContextFor(tenant);
        UUID employeeId = seedLegacyEmployee(tenant);

        // Freeze the tenant (the cutover script's Phase 1-3 state).
        setState(tenant, "MIGRATING");
        assertEquals("MIGRATING", stateOf(tenant));

        // Reads remain available through v1 during the write freeze.
        mockMvc.perform(get("/api/v1/hr/employees/{id}", employeeId)
                        .with(authentication(principal(tenant))))
                .andExpect(status().isOk());

        // Writes are frozen: create is rejected with the migration gate.
        mockMvc.perform(post("/api/v1/hr/employees")
                        .with(authentication(principal(tenant)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Frozen\",\"lastName\":\"Write\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HRM_MIGRATION_REQUIRED"));

        // Writes are frozen: profile patch is rejected with the same gate.
        mockMvc.perform(patch("/api/v1/hr/employees/{id}", employeeId)
                        .with(authentication(principal(tenant)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"frozen@example.com\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HRM_MIGRATION_REQUIRED"));
    }

    @Test
    void canonicalTransitionRequiresZeroUnresolvedRows() {
        // Disposable tenant with one legacy employee whose mapping is
        // unresolved (review required) — the reconciliation must BLOCK the
        // transition and never grant CANONICAL.
        UUID tenant = UUID.randomUUID();
        seedTenant(tenant);
        UUID employeeId = seedLegacyEmployee(tenant);
        seedUnresolvedMapping(tenant, employeeId);

        reconcileTenant(tenant);
        String stateAfterUnresolved = stateOf(tenant);
        assertEquals("BLOCKED", stateAfterUnresolved,
                "a tenant with unresolved migration rows must be BLOCKED");
        assertNotEquals("CANONICAL", stateAfterUnresolved);

        // Resolve every row (here: the fixture tenant has zero remaining
        // legacy employees after cleanup) and reconcile again — the
        // authoritative WS2 semantics then resolve the final state without
        // unresolved rows. Zero legacy rows resolve to LEGACY (nothing was
        // migrated); the invariant under test is that an unresolved state
        // can never finalize as CANONICAL.
        executeAsTenant(tenant, "DELETE FROM hr_legacy_employee_mappings WHERE tenant_id = ?",
                ps -> ps.setObject(1, tenant));
        // Children first: the employment status history references the
        // legacy employee row (fk_hr_employment_status_periods_employment).
        executeAsTenant(tenant, "DELETE FROM hr_employment_status_periods WHERE tenant_id = ?",
                ps -> ps.setObject(1, tenant));
        executeAsTenant(tenant, "DELETE FROM hr_employees WHERE tenant_id = ?",
                ps -> ps.setObject(1, tenant));

        reconcileTenant(tenant);
        String stateAfterClean = stateOf(tenant);
        assertNotEquals("CANONICAL", stateAfterClean,
                "an empty tenant must not finalize as CANONICAL");
        assertEquals("LEGACY", stateAfterClean,
                "zero legacy employees resolve to LEGACY (authoritative WS2 reconcile semantics)");
    }

    // ==================== migration state helpers (PostgreSQL Direct) ====================

    private void setState(UUID tenantId, String state) {
        executeAsTenant(tenantId,
                "INSERT INTO hr_migration_tenant_state (tenant_id, state, updated_at) VALUES (?, ?, NOW()) " +
                        "ON CONFLICT (tenant_id) DO UPDATE SET state = EXCLUDED.state, updated_at = NOW()",
                ps -> {
                    ps.setObject(1, tenantId);
                    ps.setString(2, state);
                });
    }

    private String stateOf(UUID tenantId) {
        try (Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement st = c.createStatement()) {
            // Session-scoped binding (same pattern as executeAsTenant):
            // set_config(..., is_local => true) evaporates in autocommit
            // mode, so RLS would hide the row and the lookup would
            // fall back to LEGACY regardless of the real state.
            st.execute("SET app.tenant_id = '" + tenantId + "'");
            try (PreparedStatement q = c.prepareStatement(
                    "SELECT state FROM hr_migration_tenant_state WHERE tenant_id = ?")) {
                q.setObject(1, tenantId);
                try (ResultSet rs = q.executeQuery()) {
                    return rs.next() ? rs.getString(1) : "LEGACY";
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("state lookup failed: " + e.getMessage(), e);
        }
    }

    private void reconcileTenant(UUID tenantId) {
        // hr_reconcile_tenant RETURNS the resolved state, so this must go
        // through execute() (executeUpdate rejects statements that return
        // a result set).
        executeResultingAsTenant(tenantId, "SELECT hr_reconcile_tenant(?)", ps -> ps.setObject(1, tenantId));
    }

    private void seedUnresolvedMapping(UUID tenantId, UUID employeeId) {
        executeAsTenant(tenantId,
                "INSERT INTO hr_legacy_employee_mappings (id, tenant_id, legacy_employee_id, classification, review_reason) " +
                        "VALUES (?, ?, ?, 'MIGRATION_REVIEW_REQUIRED', 'ambiguous employer context: fixture')",
                ps -> {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, tenantId);
                    ps.setObject(3, employeeId);
                });
    }

    // ==================== seed helpers (PostgreSQL Direct) ====================

    private void seedTenant(UUID tenantId) {
        executePlain("INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) " +
                        "VALUES (?, 'Cutover Tenant', ?, 'ACTIVE', NOW(), NOW()) ON CONFLICT (id) DO NOTHING",
                ps -> {
                    ps.setObject(1, tenantId);
                    ps.setString(2, "t-" + tenantId.toString().substring(0, 8));
                });
    }

    private void seedSingleEmployerContextFor(UUID tenantId) {
        executeAsTenant(tenantId, "INSERT INTO organizations (id, tenant_id, name, status, created_at, updated_at) " +
                "VALUES (?, ?, 'Cutover Org ' || ?, 'ACTIVE', NOW(), NOW())", ps -> {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenantId);
            ps.setString(3, UUID.randomUUID().toString().substring(0, 8));
        });
        executeAsTenant(tenantId, "INSERT INTO legal_entities (id, tenant_id, code, name, registered_country_code, " +
                        "statutory_country_code, status, created_at, updated_at) " +
                        "VALUES (?, ?, ?, 'Cutover LE', 'AE', 'AE', 'ACTIVE', NOW(), NOW())", ps -> {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenantId);
            ps.setString(3, "LE-" + UUID.randomUUID().toString().substring(0, 8));
        });
    }

    private UUID seedLegacyEmployee(UUID tenantId) {
        UUID id = UUID.randomUUID();
        executeAsTenant(tenantId, "INSERT INTO hr_employees (id, tenant_id, person_id, legal_entity_id, employee_number, " +
                        "first_name, last_name, display_name, employment_type, status, hire_date, version) " +
                        "VALUES (?, ?, NULL, NULL, ?, 'Cutover', 'Emp', 'Cutover Emp', 'FULL_TIME', 'ACTIVE', ?, 0)",
                ps -> {
                    ps.setObject(1, id);
                    ps.setObject(2, tenantId);
                    ps.setString(3, "EMP-CUT-" + UUID.randomUUID().toString().substring(0, 8));
                    ps.setObject(4, LocalDate.of(2026, 1, 1));
                });
        return id;
    }

    @FunctionalInterface
    interface SqlBinder {
        void bind(PreparedStatement ps) throws Exception;
    }

    private static void executeAsTenant(UUID tenantId, String sql, SqlBinder binder) {
        try (Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            try (Statement st = c.createStatement()) {
                st.execute("SET app.tenant_id = '" + tenantId + "'");
            }
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                binder.bind(ps);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Seed failed: " + e.getMessage(), e);
        }
    }

    /** For statements that RETURN a result (e.g. hr_reconcile_tenant). */
    private static void executeResultingAsTenant(UUID tenantId, String sql, SqlBinder binder) {
        try (Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            try (Statement st = c.createStatement()) {
                st.execute("SET app.tenant_id = '" + tenantId + "'");
            }
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                binder.bind(ps);
                ps.execute();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Execution failed: " + e.getMessage(), e);
        }
    }

    private static void executePlain(String sql, SqlBinder binder) {
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
    static class CutoverProbeConfig {

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
        CapabilityEvaluationService cutoverCapabilityEvaluationService() {
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
