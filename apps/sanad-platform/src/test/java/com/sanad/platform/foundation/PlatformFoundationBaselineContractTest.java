package com.sanad.platform.foundation;

import com.sanad.platform.access.api.RoleController;
import com.sanad.platform.access.api.UserAccessController;
import com.sanad.platform.access.evaluation.CapabilityEvaluationService;
import com.sanad.platform.access.grant.UserRoleGrant;
import com.sanad.platform.access.role.Role;
import com.sanad.platform.hr.api.HrController;
import com.sanad.platform.hr.domain.HrEmployee;
import com.sanad.platform.module.entitlement.EntitlementResolver;
import com.sanad.platform.organization.membership.api.OrganizationMembershipController;
import com.sanad.platform.user.api.UserController;
import com.sanad.platform.user.domain.User;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.web.bind.annotation.RequestMapping;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformFoundationBaselineContractTest {

    @Test
    void keepsAuthoritativeIdentityAccessAndWorkforceBuildingBlocks() {
        assertThat(User.class).isNotNull();
        assertThat(Role.class).isNotNull();
        assertThat(UserRoleGrant.class).isNotNull();
        assertThat(CapabilityEvaluationService.class).isNotNull();
        assertThat(EntitlementResolver.class).isNotNull();
        assertThat(HrEmployee.class).isNotNull();
    }

    @Test
    void keepsExistingTenantAdministrationControllerRoots() {
        assertControllerRoot(UserController.class, "/api/v1/users");
        assertControllerRoot(RoleController.class, "/api/v1/access/roles");
        assertControllerRoot(UserAccessController.class, "/api/v1/access/users");
        assertControllerRoot(
                OrganizationMembershipController.class,
                "/api/v1/organizations/{organizationId}/memberships");
        assertControllerRoot(HrController.class, "/api/v1/hr");
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_URL", matches = "jdbc:postgresql:.*")
    void pinsPostgresDirectFoundationSchemaBeforeRemediation() throws Exception {
        String jdbcUrl = System.getenv("SPRING_DATASOURCE_URL");
        String username = System.getenv("SPRING_DATASOURCE_USERNAME");
        String password = System.getenv("SPRING_DATASOURCE_PASSWORD");

        assertThat(jdbcUrl).startsWith("jdbc:postgresql:");
        assertThat(username).isNotBlank();
        assertThat(password).isNotNull();

        Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .baselineOnMigrate(true)
                .validateOnMigrate(false)
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");

            Map<String, Boolean> observations = new LinkedHashMap<>();
            observations.put("USERS_TENANT_COMPOSITE_KEY", queryBoolean(connection, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_constraint c
                        JOIN pg_class t ON t.oid = c.conrelid
                        JOIN pg_namespace n ON n.oid = t.relnamespace
                        WHERE n.nspname = 'public'
                          AND t.relname = 'users'
                          AND c.contype IN ('u', 'p')
                          AND (
                              SELECT array_agg(a.attname::text ORDER BY k.ordinality)
                              FROM unnest(c.conkey) WITH ORDINALITY AS k(attnum, ordinality)
                              JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = k.attnum
                          ) = ARRAY['tenant_id', 'id']::text[]
                    )
                    """));
            observations.put("MEMBERSHIP_TENANT_SAFE_USER_FK", queryBoolean(connection, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_constraint c
                        JOIN pg_class child ON child.oid = c.conrelid
                        JOIN pg_namespace n ON n.oid = child.relnamespace
                        JOIN pg_class parent ON parent.oid = c.confrelid
                        WHERE n.nspname = 'public'
                          AND child.relname = 'organization_memberships'
                          AND parent.relname = 'users'
                          AND c.contype = 'f'
                          AND (
                              SELECT array_agg(a.attname::text ORDER BY k.ordinality)
                              FROM unnest(c.conkey) WITH ORDINALITY AS k(attnum, ordinality)
                              JOIN pg_attribute a ON a.attrelid = child.oid AND a.attnum = k.attnum
                          ) = ARRAY['tenant_id', 'user_id']::text[]
                          AND (
                              SELECT array_agg(a.attname::text ORDER BY k.ordinality)
                              FROM unnest(c.confkey) WITH ORDINALITY AS k(attnum, ordinality)
                              JOIN pg_attribute a ON a.attrelid = parent.oid AND a.attnum = k.attnum
                          ) = ARRAY['tenant_id', 'id']::text[]
                    )
                    """));
            observations.put("ROLE_PROVENANCE_COLUMNS", queryBoolean(connection, """
                    SELECT COUNT(*) = 4
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name = 'roles'
                      AND column_name IN ('is_system_managed', 'role_origin', 'template_key', 'template_version')
                    """));
            observations.put("TENANT_WIDE_GRANT_PARTIAL_UNIQUE", queryBoolean(connection, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_index i
                        JOIN pg_class t ON t.oid = i.indrelid
                        JOIN pg_namespace n ON n.oid = t.relnamespace
                        WHERE n.nspname = 'public'
                          AND t.relname = 'user_role_assignments'
                          AND i.indisunique
                          AND pg_get_expr(i.indpred, i.indrelid) ILIKE '%organization_id%IS NULL%'
                          AND (
                              SELECT array_agg(a.attname::text ORDER BY k.ordinality)
                              FROM unnest(i.indkey) WITH ORDINALITY AS k(attnum, ordinality)
                              JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = k.attnum
                          ) = ARRAY['tenant_id', 'user_id', 'role_id']::text[]
                    )
                    """));
            observations.put("GRANT_TEMPORAL_COLUMNS", queryBoolean(connection, """
                    SELECT COUNT(*) = 4
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name = 'user_role_assignments'
                      AND column_name IN ('effective_from', 'effective_until', 'revoked_at', 'revoked_by')
                    """));
            observations.put("EMPLOYEE_USER_COMPOSITE_FK", queryBoolean(connection, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_constraint c
                        JOIN pg_class child ON child.oid = c.conrelid
                        JOIN pg_namespace n ON n.oid = child.relnamespace
                        JOIN pg_class parent ON parent.oid = c.confrelid
                        WHERE n.nspname = 'public'
                          AND child.relname = 'hr_employees'
                          AND parent.relname = 'users'
                          AND c.contype = 'f'
                          AND (
                              SELECT array_agg(a.attname::text ORDER BY k.ordinality)
                              FROM unnest(c.conkey) WITH ORDINALITY AS k(attnum, ordinality)
                              JOIN pg_attribute a ON a.attrelid = child.oid AND a.attnum = k.attnum
                          ) = ARRAY['tenant_id', 'user_id']::text[]
                          AND (
                              SELECT array_agg(a.attname::text ORDER BY k.ordinality)
                              FROM unnest(c.confkey) WITH ORDINALITY AS k(attnum, ordinality)
                              JOIN pg_attribute a ON a.attrelid = parent.oid AND a.attnum = k.attnum
                          ) = ARRAY['tenant_id', 'id']::text[]
                    )
                    """));
            observations.put("EMPLOYEE_USER_PARTIAL_UNIQUE", queryBoolean(connection, """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_index i
                        JOIN pg_class t ON t.oid = i.indrelid
                        JOIN pg_namespace n ON n.oid = t.relnamespace
                        WHERE n.nspname = 'public'
                          AND t.relname = 'hr_employees'
                          AND i.indisunique
                          AND pg_get_expr(i.indpred, i.indrelid) ILIKE '%user_id%IS NOT NULL%'
                          AND (
                              SELECT array_agg(a.attname::text ORDER BY k.ordinality)
                              FROM unnest(i.indkey) WITH ORDINALITY AS k(attnum, ordinality)
                              JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = k.attnum
                          ) = ARRAY['tenant_id', 'user_id']::text[]
                    )
                    """));
            observations.put("WORKFORCE_COMPOSITE_FKS", queryBoolean(connection, """
                    WITH expected(child_table, child_columns, parent_table, parent_columns) AS (
                        VALUES
                            ('hr_employees', ARRAY['tenant_id','department_id']::text[], 'hr_departments', ARRAY['tenant_id','id']::text[]),
                            ('hr_employees', ARRAY['tenant_id','position_id']::text[], 'hr_positions', ARRAY['tenant_id','id']::text[]),
                            ('hr_employees', ARRAY['tenant_id','manager_id']::text[], 'hr_employees', ARRAY['tenant_id','id']::text[]),
                            ('hr_positions', ARRAY['tenant_id','department_id']::text[], 'hr_departments', ARRAY['tenant_id','id']::text[]),
                            ('hr_departments', ARRAY['tenant_id','parent_department_id']::text[], 'hr_departments', ARRAY['tenant_id','id']::text[])
                    ), actual AS (
                        SELECT child.relname AS child_table,
                               (
                                   SELECT array_agg(a.attname::text ORDER BY k.ordinality)
                                   FROM unnest(c.conkey) WITH ORDINALITY AS k(attnum, ordinality)
                                   JOIN pg_attribute a ON a.attrelid = child.oid AND a.attnum = k.attnum
                               ) AS child_columns,
                               parent.relname AS parent_table,
                               (
                                   SELECT array_agg(a.attname::text ORDER BY k.ordinality)
                                   FROM unnest(c.confkey) WITH ORDINALITY AS k(attnum, ordinality)
                                   JOIN pg_attribute a ON a.attrelid = parent.oid AND a.attnum = k.attnum
                               ) AS parent_columns
                        FROM pg_constraint c
                        JOIN pg_class child ON child.oid = c.conrelid
                        JOIN pg_namespace n ON n.oid = child.relnamespace
                        JOIN pg_class parent ON parent.oid = c.confrelid
                        WHERE n.nspname = 'public' AND c.contype = 'f'
                    )
                    SELECT NOT EXISTS (
                        SELECT 1 FROM expected e
                        WHERE NOT EXISTS (
                            SELECT 1 FROM actual a
                            WHERE a.child_table = e.child_table
                              AND a.child_columns = e.child_columns
                              AND a.parent_table = e.parent_table
                              AND a.parent_columns = e.parent_columns
                        )
                    )
                    """));
            observations.put("HR_FORCE_RLS", queryBoolean(connection, """
                    SELECT COUNT(*) = 3 AND BOOL_AND(c.relforcerowsecurity)
                    FROM pg_class c
                    JOIN pg_namespace n ON n.oid = c.relnamespace
                    WHERE n.nspname = 'public'
                      AND c.relname IN ('hr_departments', 'hr_positions', 'hr_employees')
                    """));
            observations.put("HR_FAIL_CLOSED_POLICY", queryBoolean(connection, """
                    SELECT COUNT(*) = 3
                    FROM pg_policies
                    WHERE schemaname = 'public'
                      AND tablename IN ('hr_departments', 'hr_positions', 'hr_employees')
                      AND qual ILIKE '%app.tenant_id%'
                      AND qual NOT ILIKE '%IS NULL%'
                    """));

            observations.forEach((key, present) ->
                    System.out.printf("PF01_%s=%s%n", key, present ? "PRESENT" : "ABSENT"));

            assertThat(observations)
                    .containsEntry("USERS_TENANT_COMPOSITE_KEY", true)
                    .containsEntry("MEMBERSHIP_TENANT_SAFE_USER_FK", true)
                    .containsEntry("ROLE_PROVENANCE_COLUMNS", true)
                    .containsEntry("TENANT_WIDE_GRANT_PARTIAL_UNIQUE", false)
                    .containsEntry("GRANT_TEMPORAL_COLUMNS", false)
                    .containsEntry("EMPLOYEE_USER_COMPOSITE_FK", false)
                    .containsEntry("EMPLOYEE_USER_PARTIAL_UNIQUE", false)
                    .containsEntry("WORKFORCE_COMPOSITE_FKS", false)
                    .containsEntry("HR_FORCE_RLS", false)
                    .containsEntry("HR_FAIL_CLOSED_POLICY", false);
        }
    }

    private static boolean queryBoolean(Connection connection, String sql) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            assertThat(result.next()).as("catalog query must return one row").isTrue();
            return result.getBoolean(1);
        }
    }

    private static void assertControllerRoot(Class<?> controllerType, String expectedRoot) {
        RequestMapping mapping = AnnotationUtils.findAnnotation(controllerType, RequestMapping.class);
        assertThat(mapping)
                .as("%s must retain a class-level @RequestMapping", controllerType.getSimpleName())
                .isNotNull();
        assertThat(mapping.value())
                .as("%s controller root", controllerType.getSimpleName())
                .containsExactly(expectedRoot);
    }
}
