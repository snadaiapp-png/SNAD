package com.sanad.platform.hr.api.v2;

import com.sanad.platform.access.AccessDecisionResponse;
import com.sanad.platform.access.evaluation.CapabilityEvaluationService;
import com.sanad.platform.security.authorization.CapabilityAuthorizationBypass;
import com.sanad.platform.security.service.JwtTokenProvider;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression contract for the human-preview compliance failure.
 *
 * The override list is backed by a FORCE-RLS table. An authorized request
 * must establish transaction-scoped tenant context before JdbcTemplate reads,
 * otherwise PostgreSQL correctly hides the tenant row and the UI cannot load
 * the compliance workspace.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(HrComplianceOverrideListRlsContractTest.AuthProbeConfig.class)
class HrComplianceOverrideListRlsContractTest {

    private static final String DB_URL = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_URL", "jdbc:postgresql://127.0.0.1:5432/sanad");
    private static final String DB_USER = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_USERNAME", "sanad");
    private static final String DB_PASSWORD = System.getenv().getOrDefault(
            "SPRING_DATASOURCE_PASSWORD", "sanad_pass");

    private static final UUID TENANT = UUID.fromString("db37f49a-f965-4e5d-b98b-31efe1cc7042");
    private static final UUID USER = UUID.fromString("24fdf61d-d5b2-41a2-983c-d34888e05949");

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

    @Test
    void authorizedOverrideList_readsTheCurrentTenantsRlsProtectedRows() throws Exception {
        UUID requestId = seedOverrideRequest();

        mockMvc.perform(get("/api/v2/hr/compliance/overrides")
                        .with(authentication(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].requestId").value(requestId.toString()))
                .andExpect(jsonPath("$[0].status").value("PENDING_APPROVAL"));
    }

    private UsernamePasswordAuthenticationToken principal() {
        var token = new UsernamePasswordAuthenticationToken(
                "compliance-list-regression", "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_TEST")));
        token.setDetails(Map.of("tenant_id", TENANT.toString(), "user_id", USER.toString()));
        return token;
    }

    private UUID seedOverrideRequest() {
        executePlain("INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at) " +
                        "VALUES (?, 'Compliance List Regression', ?, 'ACTIVE', NOW(), NOW()) " +
                        "ON CONFLICT (id) DO NOTHING",
                ps -> {
                    ps.setObject(1, TENANT);
                    ps.setString(2, "compliance-list-" + TENANT.toString().substring(0, 8));
                });

        UUID requestId = UUID.randomUUID();
        executeAsTenant("DELETE FROM hr_compliance_override_requests WHERE tenant_id = ?", ps ->
                ps.setObject(1, TENANT));
        executeAsTenant("INSERT INTO hr_compliance_override_requests " +
                        "(id, tenant_id, compliance_rule_id, resource_type, resource_id, requester_user_id, " +
                        "justification, valid_from, status, created_at, updated_at) " +
                        "VALUES (?, ?, ?, 'EMPLOYMENT', NULL, ?, 'Preview RLS regression', CURRENT_DATE, " +
                        "'PENDING_APPROVAL', NOW(), NOW())",
                ps -> {
                    ps.setObject(1, requestId);
                    ps.setObject(2, TENANT);
                    ps.setObject(3, UUID.randomUUID());
                    ps.setObject(4, USER);
                });
        return requestId;
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
            throw new IllegalStateException("Tenant seed failed: " + e.getMessage(), e);
        }
    }

    private void executePlain(String sql, SqlBinder binder) {
        try (Connection c = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement ps = c.prepareStatement(sql)) {
            binder.bind(ps);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("Seed failed: " + e.getMessage(), e);
        }
    }

    @FunctionalInterface
    interface SqlBinder {
        void bind(PreparedStatement ps) throws Exception;
    }

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
            http.securityMatcher("/**")
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
            return http.build();
        }

        @Bean
        @Primary
        CapabilityEvaluationService complianceListCapabilityEvaluationService() {
            CapabilityEvaluationService mock = org.mockito.Mockito.mock(CapabilityEvaluationService.class);
            org.mockito.Mockito.when(mock.evaluate(
                            org.mockito.ArgumentMatchers.any(UUID.class),
                            org.mockito.ArgumentMatchers.any(UUID.class),
                            org.mockito.ArgumentMatchers.anyString(),
                            org.mockito.ArgumentMatchers.any()))
                    .thenAnswer(invocation -> {
                        String capability = invocation.getArgument(2);
                        boolean allowed = "HRM.COMPLIANCE_OVERRIDE.REQUEST".equals(capability);
                        return new AccessDecisionResponse(
                                invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(3),
                                capability, allowed, allowed ? "ALLOW" : "DENY", UUID.randomUUID(),
                                allowed ? "TEST_ROLE" : null);
                    });
            return mock;
        }
    }
}