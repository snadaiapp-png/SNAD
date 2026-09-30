package com.sanad.platform.hr.time;

import com.sanad.platform.hr.time.application.HrEmploymentScopeResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PostgreSQL Direct acceptance contract for {@link HrEmploymentScopeResolver}.
 *
 * <p>This test intentionally runs only in the dedicated G2 scope gate, after
 * Flyway and {@code g2-acceptance-seed.sql} have provisioned the disposable
 * database. The normal Maven suite does not own that deterministic fixture, so
 * it reports these cases as profile-gated rather than silently connecting to a
 * developer database.</p>
 *
 * <p>No Docker/Testcontainers. The application role is least privilege and
 * NOBYPASSRLS. Every assertion executes inside one real PostgreSQL transaction
 * with {@code app.tenant_id} set transaction-locally so FORCE RLS remains in
 * the path. Negative mutations are rolled back.</p>
 */
@EnabledIfEnvironmentVariable(named = "G2_SCOPE_FIXTURE_READY", matches = "true")
class HrEmploymentScopeResolverPostgresTest {

    private static final UUID TENANT_ID = UUID.fromString("33333333-3333-4333-8333-333333333331");
    private static final UUID EMPLOYEE_USER_ID = UUID.fromString("33333333-3333-4333-8333-333333333341");
    private static final UUID MANAGER_USER_ID = UUID.fromString("33333333-3333-4333-8333-333333333342");
    private static final UUID HR_USER_ID = UUID.fromString("33333333-3333-4333-8333-333333333343");

    private static final UUID EMPLOYEE_EMPLOYMENT_ID = UUID.fromString("33333333-3333-4333-8333-333333333361");
    private static final UUID MANAGER_EMPLOYMENT_ID = UUID.fromString("33333333-3333-4333-8333-333333333362");
    private static final UUID HR_EMPLOYMENT_ID = UUID.fromString("33333333-3333-4333-8333-333333333363");
    private static final UUID EMPLOYEE_ASSIGNMENT_ID = UUID.fromString("33333333-3333-4333-8333-333333333368");

    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private HrEmploymentScopeResolver resolver;

    @BeforeEach
    void setUp() {
        String url = requireEnvironment("SPRING_DATASOURCE_URL");
        String username = requireEnvironment("SPRING_DATASOURCE_USERNAME");
        String password = System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "");

        DataSource dataSource = new DriverManagerDataSource(url, username, password);
        jdbc = new JdbcTemplate(dataSource);
        tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        resolver = new HrEmploymentScopeResolver(jdbc);
    }

    @Test
    void selfScopeResolvesCanonicalEmploymentForEveryG2Principal() {
        tx.executeWithoutResult(status -> {
            setTenantContext();

            assertThat(resolver.requireSelfEmployment(TENANT_ID, EMPLOYEE_USER_ID))
                    .isEqualTo(EMPLOYEE_EMPLOYMENT_ID);
            assertThat(resolver.requireSelfEmployment(TENANT_ID, MANAGER_USER_ID))
                    .isEqualTo(MANAGER_EMPLOYMENT_ID);
            assertThat(resolver.requireSelfEmployment(TENANT_ID, HR_USER_ID))
                    .isEqualTo(HR_EMPLOYMENT_ID);
        });
    }

    @Test
    void teamScopeUsesCanonicalPrimaryAssignmentReportingAndRejectsNonReports() {
        tx.executeWithoutResult(status -> {
            setTenantContext();

            assertThatCode(() -> resolver.requireManagedEmployment(
                    TENANT_ID, MANAGER_USER_ID, EMPLOYEE_EMPLOYMENT_ID))
                    .doesNotThrowAnyException();

            assertThatThrownBy(() -> resolver.requireManagedEmployment(
                    TENANT_ID, EMPLOYEE_USER_ID, MANAGER_EMPLOYMENT_ID))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("not an active direct report");

            assertThatThrownBy(() -> resolver.requireManagedEmployment(
                    TENANT_ID, MANAGER_USER_ID, HR_EMPLOYMENT_ID))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("not an active direct report");
        });
    }

    @Test
    void selfScopeFailsClosedWhenCanonicalPersonLinkIsMissingEvenIfLegacyUserIdRemains() {
        tx.executeWithoutResult(status -> {
            setTenantContext();

            int updated = jdbc.update(
                    "UPDATE hr_employees SET person_id = NULL WHERE tenant_id = ? AND id = ?",
                    TENANT_ID,
                    EMPLOYEE_EMPLOYMENT_ID);
            assertThat(updated).isEqualTo(1);

            assertThatThrownBy(() -> resolver.requireSelfEmployment(TENANT_ID, EMPLOYEE_USER_ID))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("no active HR employment");

            status.setRollbackOnly();
        });
    }

    @Test
    void teamScopeFailsClosedWhenCanonicalReportingLinkIsMissingEvenIfLegacyManagerIdRemains() {
        tx.executeWithoutResult(status -> {
            setTenantContext();

            int updated = jdbc.update(
                    "UPDATE hr_employee_assignments "
                            + "SET reports_to_assignment_id = NULL "
                            + "WHERE tenant_id = ? AND id = ?",
                    TENANT_ID,
                    EMPLOYEE_ASSIGNMENT_ID);
            assertThat(updated).isEqualTo(1);

            assertThatThrownBy(() -> resolver.requireManagedEmployment(
                    TENANT_ID, MANAGER_USER_ID, EMPLOYEE_EMPLOYMENT_ID))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("not an active direct report");

            status.setRollbackOnly();
        });
    }

    private void setTenantContext() {
        String applied = jdbc.queryForObject(
                "SELECT set_config('app.tenant_id', ?, true)",
                String.class,
                TENANT_ID.toString());
        assertThat(applied).isEqualTo(TENANT_ID.toString());
    }

    private static String requireEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required by the PostgreSQL Direct G2 scope gate");
        }
        return value;
    }
}
