package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
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
import com.sanad.platform.crm.integration.Crm009TestEnvironment;

import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.sanad.platform.crm.integration.Crm009TestEnvironment;

class CrmOwnershipCursorPaginationPostgresTest {

    private static NamedParameterJdbcTemplate jdbc;
    private static CrmOwnershipCursorPaginationAspect aspect;
    private static Method listTeamsMethod;

    private UUID tenantId;
    private UUID userId;
    private List<UUID> teamIds;

    @BeforeAll
    static void migrateAndCreateAspect() throws Exception {
        boolean postgresAvailable;
        try {
            postgresAvailable = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip("testClassName");
        } catch (Throwable ignored) {
            postgresAvailable = false;
        }
        Assumptions.assumeTrue(
                postgresAvailable,
                "PostgreSQL Direct is required for CRM-008R cursor pagination acceptance.");

        Flyway.configure()
                .dataSource(System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad"), System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"), System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""))
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .javaMigrations(new V15__seed_rbac_roles_and_capabilities())
                .cleanDisabled(false)
                .validateOnMigrate(true)
                .load()
                .migrate();

        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                System.getenv().getOrDefault("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad"), System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "sanad"), System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", ""));
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
        teamIds = Stream.generate(UUID::randomUUID)
                .limit(3)
                .sorted(Comparator.naturalOrder())
                .toList();
        jdbc.update("""
                INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at)
                VALUES (:id, :name, :subdomain, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, new MapSqlParameterSource()
                .addValue("id", tenantId)
                .addValue("name", "CRM-008R Cursor")
                .addValue("subdomain", "crm008r-page-" + tenantId.toString().substring(0, 8)));
        insertTeam(teamIds.get(0), "PAGE-1");
        insertTeam(teamIds.get(1), "PAGE-2");
        insertTeam(teamIds.get(2), "PAGE-3");
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
        assertValidationError(() -> aspect.pageTeams(otherJoinPoint));
    }

    @Test
    void tamperedCursor_isRejected() throws Throwable {
        String cursor = firstCursor();
        char replacement = cursor.charAt(cursor.length() - 1) == 'A' ? 'B' : 'A';
        String tampered = cursor.substring(0, cursor.length() - 1) + replacement;
        assertValidationError(() -> invoke(request(tampered, null)));
    }

    @Test
    void cursorCannotBeReusedWithDifferentFilter() throws Throwable {
        String cursor = firstCursor();
        assertValidationError(() -> invoke(request(cursor, "ARCHIVED")));
    }

    private void assertValidationError(ThrowingCall call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(CrmContractException.class)
                .satisfies(error -> assertThat(((CrmContractException) error).code())
                        .isEqualTo(CrmErrorCode.VALIDATION_ERROR));
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

    @FunctionalInterface
    private interface ThrowingCall {
        void run() throws Throwable;
    }
}
