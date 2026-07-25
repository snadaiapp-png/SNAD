package com.sanad.platform.crm.ownership.infrastructure;

import com.sanad.platform.config.migration.V15__seed_rbac_roles_and_capabilities;
import com.sanad.platform.crm.concurrency.ETagService;
import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import org.aspectj.lang.ProceedingJoinPoint;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers
class CrmOwnershipAtomicIfMatchPostgresTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static NamedParameterJdbcTemplate jdbc;
    private static CrmOwnershipAtomicIfMatchAspect aspect;

    private UUID tenantId;
    private UUID actorId;
    private UUID teamId;

    @BeforeAll
    static void migrateAndCreateAspect() {
        boolean dockerAvailable;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable ignored) {
            dockerAvailable = false;
        }
        Assumptions.assumeTrue(
                dockerAvailable,
                "Docker is required for CRM-008R atomic If-Match acceptance.");

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
        aspect = new CrmOwnershipAtomicIfMatchAspect(
                jdbc,
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
                new ETagService());
    }

    @BeforeEach
    void createTeam() {
        tenantId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        teamId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO tenants (id, name, subdomain, status, created_at, updated_at)
                VALUES (:id, :name, :subdomain, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, new MapSqlParameterSource()
                .addValue("id", tenantId)
                .addValue("name", "CRM-008R Atomic ETag")
                .addValue("subdomain", "crm008r-etag-" + tenantId.toString().substring(0, 8)));
        jdbc.update("""
                INSERT INTO crm_sales_teams
                  (id, tenant_id, code, display_name, status,
                   created_at, updated_at, created_by, updated_by)
                VALUES
                  (:id, :tenantId, :code, 'Original', 'ACTIVE',
                   CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, :actorId, :actorId)
                """, new MapSqlParameterSource()
                .addValue("id", teamId)
                .addValue("tenantId", tenantId)
                .addValue("code", "RACE-" + teamId.toString().substring(0, 8))
                .addValue("actorId", actorId));
    }

    @Test
    void concurrentRequestsWithSameEtag_haveExactlyOneWinner() throws Exception {
        String etag = currentEtag();
        CyclicBarrier start = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> mutate(start, etag, "First"));
            Future<Boolean> second = executor.submit(() -> mutate(start, etag, "Second"));
            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
        }

        String finalName = jdbc.queryForObject("""
                SELECT display_name FROM crm_sales_teams
                 WHERE tenant_id=:tenantId AND id=:teamId
                """, params(), String.class);
        assertThat(finalName).isIn("First", "Second");
    }

    @Test
    void staleEtag_isRejectedBeforeMutationExecutes() throws Throwable {
        String stale = currentEtag();
        jdbc.update("""
                UPDATE crm_sales_teams
                   SET updated_at=updated_at + INTERVAL '1 second'
                 WHERE tenant_id=:tenantId AND id=:teamId
                """, params());

        AtomicBoolean invoked = new AtomicBoolean(false);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenAnswer(invocation -> {
            invoked.set(true);
            return null;
        });

        bindRequest(stale);
        try {
            assertThatThrownBy(() -> aspect.enforceAtomicIfMatch(joinPoint))
                    .isInstanceOf(CrmContractException.class)
                    .satisfies(error -> assertThat(((CrmContractException) error).code())
                            .isEqualTo(CrmErrorCode.CRM_CONCURRENCY_CONFLICT));
            assertThat(invoked).isFalse();
        } finally {
            clearRequest();
        }
    }

    @Test
    void missingIfMatch_isRejectedBeforeMutationExecutes() throws Throwable {
        AtomicBoolean invoked = new AtomicBoolean(false);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenAnswer(invocation -> {
            invoked.set(true);
            return null;
        });

        bindRequest(null);
        try {
            assertThatThrownBy(() -> aspect.enforceAtomicIfMatch(joinPoint))
                    .isInstanceOf(CrmContractException.class)
                    .satisfies(error -> assertThat(((CrmContractException) error).code())
                            .isEqualTo(CrmErrorCode.CRM_CONCURRENCY_PRECONDITION_REQUIRED));
            assertThat(invoked).isFalse();
        } finally {
            clearRequest();
        }
    }

    private boolean mutate(CyclicBarrier start, String etag, String displayName) throws Exception {
        start.await();
        bindRequest(etag);
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        try {
            when(joinPoint.proceed()).thenAnswer(invocation -> {
                jdbc.update("""
                        UPDATE crm_sales_teams
                           SET display_name=:displayName,
                               updated_at=updated_at + INTERVAL '1 second',
                               updated_by=:actorId
                         WHERE tenant_id=:tenantId AND id=:teamId
                        """, params().addValue("displayName", displayName));
                return displayName;
            });
        } catch (Throwable setupFailure) {
            throw new RuntimeException("Unable to configure race mutation", setupFailure);
        }

        try {
            aspect.enforceAtomicIfMatch(joinPoint);
            return true;
        } catch (CrmContractException conflict) {
            assertThat(conflict.code()).isEqualTo(CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
            return false;
        } catch (Throwable unexpected) {
            throw new RuntimeException(unexpected);
        } finally {
            clearRequest();
        }
    }

    private String currentEtag() {
        Timestamp timestamp = jdbc.queryForObject("""
                SELECT updated_at FROM crm_sales_teams
                 WHERE tenant_id=:tenantId AND id=:teamId
                """, params(), Timestamp.class);
        Instant value = timestamp.toInstant();
        return new ETagService().etag(
                "sales-team", teamId,
                CrmOwnershipAtomicIfMatchAspect.timestampVersion(value));
    }

    private void bindRequest(String etag) {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                actorId.toString(), "n/a", List.of());
        authentication.setDetails(Map.of(
                "tenant_id", tenantId.toString(),
                "user_id", actorId.toString()));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        MockHttpServletRequest request = new MockHttpServletRequest(
                "PATCH", "/api/v2/crm/teams/" + teamId);
        if (etag != null) request.addHeader("If-Match", etag);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    private MapSqlParameterSource params() {
        return new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("teamId", teamId)
                .addValue("actorId", actorId);
    }
}
