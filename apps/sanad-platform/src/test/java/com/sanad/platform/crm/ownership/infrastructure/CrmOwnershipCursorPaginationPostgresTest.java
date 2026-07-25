package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.pagination.CursorCodec;
import com.sanad.platform.crm.web.CrmOwnershipResourceController;
import com.sanad.platform.security.authorization.CapabilityAuthorizationAspect;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers
class CrmOwnershipCursorPaginationPostgresTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static NamedParameterJdbcTemplate jdbc;
    private static CrmOwnershipCursorPaginationAspect aspect;
    private static Method listTeamsMethod;

    private UUID tenantId;
    private UUID userId;

    @BeforeAll
    static void migrateAndCreateAspect() throws Exception {
        boolean dockerAvailable;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable ignored) {
            dockerAvailable = false;
        }
        Assumptions.assumeTrue(
                dockerAvailable,
                "Docker is required for CRM-008R cursor pagination acceptance.");

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .javaMigrations(new V15__seed_rbac_roles_and_capabilities())
                .cleanDisabled(false)
                .validateOnMigrate(true)
                .load()
                .migrate();

        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new NamedParameterJdbcTemplate(dataSource);
        CapabilityAuthorizationAspect authorization = mock(CapabilityAuthorizationAspect.class);
        doNothing().when(authorization).checkCapability(any(), any());
        aspect = new CrmOwnershipCursorPaginationAspect(jdbc, new CursorCodec(), authorization);
        listTeamsMethod = CrmOwnershipResourceController.class.getMethod(
                "listTeams",
                Authentication.class,
                com.sanad.platform.crm.ownership.domain.TeamStatus.class,
                int.class,
                jakarta.servlet.http.HttpServletRequest.class);
    }

    @BeforeEach
    void createTenantAndTeams() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at)
                VALUES (:id, :name, :subdomain, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, new MapSqlParameterSource()
                .addValue("id", tenantId)
                .addValue("name", "CRM-008R Cursor")
                .addValue("subdomain", "crm008r-page-" + tenantId.toString().substring(0, 8)));
        insertTeam(UUID.fromString("00000000-0000-0000-0000-000000000001"), "PAGE-1");
        insertTeam(UUID.fromString("00000000-0000-0000-0000-000000000002"), "PAGE-2");
        insertTeam(UUID.fromString("00000000-0000-0000-0000-000000000003"), "PAGE-3");
    }

    @Test
    void firstMiddleAndFinalPages_areBoundedAndStable() throws Throwable {
        ResponseEntity<Map<String, Object>> first = invoke(request(null, null));
        Map<String, Object> firstBody = first.getBody();
        List<?> firstData = (List<?>) firstBody.get("data");
        Map<?, ?> firstPage = (Map<?, ?>) firstBody.get("page");
        assertThat(firstData).hasSize(2);
        assertThat(firstPage.get("hasMore")).isEqualTo(true);
        assertThat(firstPage.get("nextCursor")).isInstanceOf(String.class);

        String cursor = (String) firstPage.get("nextCursor");
        ResponseEntity<Map<String, Object>> second = invoke(request(cursor, null));
        Map<String, Object> secondBody = second.getBody();
        List<?> secondData = (List<?>) secondBody.get("data");
        Map<?, ?> secondPage = (Map<?, ?>) secondBody.get("page");
        assertThat(secondData).hasSize(1);
        assertThat(secondPage.get("hasMore")).isEqualTo(false);
        assertThat(secondPage.get("nextCursor")).isNull();
    }

    @Test
    void cursorFromAnotherTenant_isRejected() throws Throwable {
        String cursor = firstCursor();
        UUID otherTenant = UUID.randomUUID();
        ProceedingJoinPoint otherJoinPoint = joinPoint(
                request(cursor, null), authentication(otherTenant));
        assertThatThrownBy(() -> aspect.pageTeams(otherJoinPoint))
                .isInstanceOf(CrmContractException.class)
                .hasMessageContaining("tenant");
    }

    @Test
    void tamperedCursor_isRejected() throws Throwable {
        String cursor = firstCursor();
        char replacement = cursor.charAt(cursor.length() - 1) == 'A' ? 'B' : 'A';
        String tampered = cursor.substring(0, cursor.length() - 1) + replacement;
        assertThatThrownBy(() -> invoke(request(tampered, null)))
                .isInstanceOf(CrmContractException.class)
                .hasMessageContaining("invalid");
    }

    @Test
    void cursorCannotBeReusedWithDifferentFilter() throws Throwable {
        String cursor = firstCursor();
        assertThatThrownBy(() -> invoke(request(cursor, "ARCHIVED")))
                .isInstanceOf(CrmContractException.class)
                .hasMessageContaining("filter");
    }

    private String firstCursor() throws Throwable {
        ResponseEntity<Map<String, Object>> response = invoke(request(null, null));
        Map<?, ?> page = (Map<?, ?>) response.getBody().get("page");
        return (String) page.get("nextCursor");
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map<String, Object>> invoke(MockHttpServletRequest request) throws Throwable {
        return (ResponseEntity<Map<String, Object>>) aspect.pageTeams(
                joinPoint(request, authentication(tenantId)));
    }

    private ProceedingJoinPoint joinPoint(
            MockHttpServletRequest request,
            Authentication authentication) {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(listTeamsMethod);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[]{authentication, null, 2, request});
        return joinPoint;
    }

    private Authentication authentication(UUID tenant) {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                userId.toString(), "n/a", List.of());
        authentication.setDetails(Map.of(
                "tenant_id", tenant.toString(),
                "user_id", userId.toString()));
        return authentication;
    }

    private MockHttpServletRequest request(String cursor, String status) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v2/crm/teams");
        request.addParameter("pageSize", "2");
        if (cursor != null) request.addParameter("cursor", cursor);
        if (status != null) request.addParameter("status", status);
        return request;
    }

    private void insertTeam(UUID id, String code) {
        jdbc.update("""
                INSERT INTO crm_sales_teams
                  (id, tenant_id, code, display_name, status,
                   created_at, updated_at, created_by, updated_by)
                VALUES
                  (:id, :tenantId, :code, :code, 'ACTIVE',
                   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, :userId, :userId)
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("tenantId", tenantId)
                .addValue("code", code)
                .addValue("userId", userId));
    }
}
