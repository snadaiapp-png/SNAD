package com.sanad.platform.security.rls;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RED baseline for the proposed users RLS cutover.
 *
 * Runs against the project's isolated PostgreSQL Direct local test database,
 * never against production. No DDL, data edits, or session-wide SET commands.
 * Until authentication/registration compatibility and the reviewed Flyway
 * migration exist, these assertions intentionally fail.
 */
@SpringBootTest
@ActiveProfiles("local")
class UsersRlsPostgresContractTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("public.users must enforce database-level row security")
    void usersTableHasRlsEnabled() {
        Boolean enabled = jdbc.queryForObject(
                "SELECT c.relrowsecurity FROM pg_class c " +
                "JOIN pg_namespace n ON n.oid=c.relnamespace " +
                "WHERE n.nspname='public' AND c.relname='users' AND c.relkind='r'",
                Boolean.class);
        assertThat(enabled)
                .as("G1 users_rls_enabled — do not pass with application filtering alone")
                .isTrue();
    }

    @Test
    @DisplayName("users RLS must cover read and write, not SELECT alone")
    void usersPoliciesCoverAllMutationPaths() {
        Long permissiveAll = jdbc.queryForObject(
                "SELECT count(*) FROM pg_policies WHERE schemaname='public' " +
                "AND tablename='users' AND cmd='ALL' AND with_check IS NOT NULL",
                Long.class);
        Long select = jdbc.queryForObject(
                "SELECT count(*) FROM pg_policies WHERE schemaname='public' " +
                "AND tablename='users' AND cmd='SELECT'", Long.class);
        Long insert = jdbc.queryForObject(
                "SELECT count(*) FROM pg_policies WHERE schemaname='public' " +
                "AND tablename='users' AND cmd='INSERT' AND with_check IS NOT NULL",
                Long.class);
        Long update = jdbc.queryForObject(
                "SELECT count(*) FROM pg_policies WHERE schemaname='public' " +
                "AND tablename='users' AND cmd='UPDATE' AND with_check IS NOT NULL",
                Long.class);
        Long delete = jdbc.queryForObject(
                "SELECT count(*) FROM pg_policies WHERE schemaname='public' " +
                "AND tablename='users' AND cmd='DELETE'", Long.class);

        assertThat(permissiveAll > 0 || (select > 0 && insert > 0 && update > 0 && delete > 0))
                .as("Every CRUD operation requires an explicit tenant-safe RLS policy")
                .isTrue();
    }

    @Test
    @DisplayName("users RLS policy must fail closed when tenant GUC is absent")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    void usersWithoutTenantContextAreInvisible() {
        // This zero-row contract must be paired with seeded tenant A/B test
        // fixtures before it can constitute RLS certification evidence.
        jdbc.execute("SELECT set_config('app.tenant_id', '', true)");
        Long visible = jdbc.queryForObject(
                "SELECT count(*) FROM public.users", Long.class);
        assertThat(visible)
                .as("No authenticated tenant context must expose zero users")
                .isZero();
    }

    @Test
    @DisplayName("pre-auth resolution function must exist before releasing AuthService")
    void preAuthResolverIsInstalled() {
        String signature = jdbc.queryForObject(
                "SELECT to_regprocedure('public.g1_resolve_login_tenants(text,text)')::text",
                String.class);
        assertThat(signature)
                .as("AuthService must not reference an uninstalled pre-auth resolver")
                .isNotBlank();
    }

    @Test
    @DisplayName("pre-auth discovery must not grant execute to PUBLIC")
    void preAuthResolverHasNoPublicExecute() {
        Long publicExecuteGrants = jdbc.queryForObject("""
                SELECT count(*)
                FROM pg_proc p
                JOIN pg_namespace n ON n.oid = p.pronamespace
                CROSS JOIN LATERAL aclexplode(COALESCE(
                    p.proacl, acldefault('f', p.proowner))) acl
                WHERE n.nspname = 'public'
                  AND p.proname = 'g1_resolve_login_tenants'
                  AND pg_get_function_identity_arguments(p.oid) = 'p_kind text, p_identifier text'
                  AND acl.grantee = 0
                  AND acl.privilege_type = 'EXECUTE'
                """, Long.class);
        assertThat(publicExecuteGrants)
                .as("PUBLIC must never receive access to pre-auth tenant discovery")
                .isZero();
    }

    @Test
    @DisplayName("pre-auth resolver must be security-definer with a fixed search path")
    void preAuthResolverHasHardenedExecutionBoundary() {
        Boolean hardened = jdbc.queryForObject("""
                SELECT p.prosecdef
                       AND EXISTS (
                           SELECT 1 FROM unnest(COALESCE(p.proconfig, ARRAY[]::text[])) cfg
                           WHERE cfg = 'search_path=pg_catalog'
                       )
                       AND NOT owner.rolcanlogin
                       AND NOT owner.rolsuper
                  FROM pg_proc p
                  JOIN pg_namespace ns ON ns.oid = p.pronamespace
                  JOIN pg_roles owner ON owner.oid = p.proowner
                 WHERE ns.nspname='public'
                   AND p.oid = to_regprocedure('public.g1_resolve_login_tenants(text,text)')
                """, Boolean.class);
        assertThat(hardened)
                .as("Resolver must have pinned search_path and controlled NOLOGIN, non-superuser owner")
                .isTrue();
    }

    @Test
    @DisplayName("sanad runtime must not bypass table row security")
    void restrictedRuntimeHasNoBypassPrivilege() {
        Boolean bypass = jdbc.queryForObject(
                "SELECT rolbypassrls OR rolsuper FROM pg_roles WHERE rolname='sanad'",
                Boolean.class);
        assertThat(bypass)
                .as("G1 restricted database identity must not have superuser/BYPASSRLS")
                .isFalse();
    }
}
