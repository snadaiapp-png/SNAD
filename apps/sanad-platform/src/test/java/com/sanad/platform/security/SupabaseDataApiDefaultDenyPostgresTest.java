package com.sanad.platform.security;

import com.sanad.platform.crm.integration.Crm009TestEnvironment;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Supabase Data API default-deny hardening (PostgreSQL Direct)")
class SupabaseDataApiDefaultDenyPostgresTest {

    private static NamedParameterJdbcTemplate jdbc;

    @BeforeAll
    static void setup() {
        boolean ok;
        try {
            ok = Crm009TestEnvironment.requirePostgreSqlDirectOrSkip(
                    "SupabaseDataApiDefaultDenyPostgresTest");
        } catch (Throwable ignored) {
            ok = false;
        }
        Assumptions.assumeTrue(ok, "PostgreSQL Direct required");

        String url = System.getenv().getOrDefault(
                "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/sanad");
        String username = System.getenv().getOrDefault(
                "SPRING_DATASOURCE_USERNAME", "sanad");
        String password = System.getenv().getOrDefault(
                "SPRING_DATASOURCE_PASSWORD", "");

        Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration", "classpath:db/vendor/postgresql")
                .cleanDisabled(false)
                .validateOnMigrate(true)
                .load()
                .migrate();

        jdbc = new NamedParameterJdbcTemplate(
                new DriverManagerDataSource(url, username, password));
    }

    @Test
    void publicHasNoApplicationTableDml() {
        Integer exposed = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.table_privileges
                WHERE table_schema = 'public'
                  AND grantee = 'PUBLIC'
                  AND privilege_type IN ('SELECT', 'INSERT', 'UPDATE', 'DELETE', 'TRUNCATE')
                """, new MapSqlParameterSource(), Integer.class);

        assertThat(exposed).isZero();
    }

    @Test
    void applicationFunctionsAreNotPublicExecutable() {
        Integer exposed = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM pg_proc p
                JOIN pg_namespace n ON n.oid = p.pronamespace
                JOIN LATERAL aclexplode(
                    COALESCE(p.proacl, acldefault('f', p.proowner))
                ) acl ON TRUE
                WHERE n.nspname = 'public'
                  AND acl.grantee = 0
                  AND acl.privilege_type = 'EXECUTE'
                  AND NOT EXISTS (
                      SELECT 1
                      FROM pg_depend d
                      WHERE d.classid = 'pg_proc'::regclass
                        AND d.objid = p.oid
                        AND d.deptype = 'e'
                  )
                """, new MapSqlParameterSource(), Integer.class);

        assertThat(exposed).isZero();
    }

    @Test
    void applicationFunctionsUsePinnedSearchPath() {
        Integer mutable = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM pg_proc p
                JOIN pg_namespace n ON n.oid = p.pronamespace
                WHERE n.nspname = 'public'
                  AND NOT EXISTS (
                      SELECT 1
                      FROM pg_depend d
                      WHERE d.classid = 'pg_proc'::regclass
                        AND d.objid = p.oid
                        AND d.deptype = 'e'
                  )
                  AND NOT EXISTS (
                      SELECT 1
                      FROM unnest(COALESCE(p.proconfig, ARRAY[]::TEXT[])) cfg
                      WHERE cfg LIKE 'search_path=%'
                  )
                """, new MapSqlParameterSource(), Integer.class);

        assertThat(mutable).isZero();
    }

    @Test
    void supabaseDataApiRolesHaveNoTableDmlWhenPresent() {
        Integer exposed = jdbc.queryForObject("""
                WITH api_roles AS (
                    SELECT rolname
                    FROM pg_roles
                    WHERE rolname IN ('anon', 'authenticated')
                ),
                application_tables AS (
                    SELECT c.oid
                    FROM pg_class c
                    JOIN pg_namespace n ON n.oid = c.relnamespace
                    WHERE n.nspname = 'public'
                      AND c.relkind IN ('r', 'p', 'v', 'm')
                )
                SELECT COUNT(*)
                FROM api_roles r
                CROSS JOIN application_tables t
                WHERE has_table_privilege(r.rolname, t.oid, 'SELECT')
                   OR has_table_privilege(r.rolname, t.oid, 'INSERT')
                   OR has_table_privilege(r.rolname, t.oid, 'UPDATE')
                   OR has_table_privilege(r.rolname, t.oid, 'DELETE')
                   OR has_table_privilege(r.rolname, t.oid, 'TRUNCATE')
                """, new MapSqlParameterSource(), Integer.class);

        assertThat(exposed).isZero();
    }

    @Test
    void securityDefinerFunctionsAreNotExecutableByDataApiRolesWhenPresent() {
        Integer exposed = jdbc.queryForObject("""
                WITH api_roles AS (
                    SELECT rolname
                    FROM pg_roles
                    WHERE rolname IN ('anon', 'authenticated')
                ),
                security_definers AS (
                    SELECT p.oid
                    FROM pg_proc p
                    JOIN pg_namespace n ON n.oid = p.pronamespace
                    WHERE n.nspname = 'public'
                      AND p.prosecdef
                      AND NOT EXISTS (
                          SELECT 1
                          FROM pg_depend d
                          WHERE d.classid = 'pg_proc'::regclass
                            AND d.objid = p.oid
                            AND d.deptype = 'e'
                      )
                )
                SELECT COUNT(*)
                FROM api_roles r
                CROSS JOIN security_definers f
                WHERE has_function_privilege(r.rolname, f.oid, 'EXECUTE')
                """, new MapSqlParameterSource(), Integer.class);

        assertThat(exposed).isZero();
    }

    @Test
    void migrationOwnerDefaultPrivilegesAreFailClosed() {
        Integer unsafeDefaults = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM pg_default_acl d
                JOIN pg_namespace n ON n.oid = d.defaclnamespace
                JOIN LATERAL aclexplode(d.defaclacl) acl ON TRUE
                LEFT JOIN pg_roles grantee ON grantee.oid = acl.grantee
                WHERE n.nspname = 'public'
                  AND pg_get_userbyid(d.defaclrole) = current_user
                  AND (
                      acl.grantee = 0
                      OR grantee.rolname IN ('anon', 'authenticated')
                  )
                  AND (
                      (d.defaclobjtype IN ('r', 'S')
                       AND acl.privilege_type IN (
                           'SELECT', 'INSERT', 'UPDATE', 'DELETE',
                           'TRUNCATE', 'USAGE', 'UPDATE'
                       ))
                      OR
                      (d.defaclobjtype = 'f' AND acl.privilege_type = 'EXECUTE')
                  )
                """, new MapSqlParameterSource(), Integer.class);

        assertThat(unsafeDefaults).isZero();
    }

    @Test
    void databaseRuntimeRoleRetainsApplicationAccess() {
        Boolean canReadUsers = jdbc.queryForObject(
                "SELECT has_table_privilege(current_user, 'public.users', 'SELECT')",
                new MapSqlParameterSource(),
                Boolean.class);

        assertThat(canReadUsers).isTrue();
    }
}
