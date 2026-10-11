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
        // A newly scoped transaction must deny absent tenant context even when
        // the table contains actual users; a zero-row fixture is insufficient proof.
        jdbc.execute("SELECT set_config('app.tenant_id', '', true)");
        Long rowsInDatabase = jdbc.queryForObject(
                "SELECT reltuples::bigint FROM pg_class WHERE oid='public.users'::regclass",
                Long.class);
        assertThat(rowsInDatabase).as("RLS visibility test needs populated fixtures")
                .isGreaterThan(0L);
        Long visible = jdbc.queryForObject(
                "SELECT count(*) FROM public.users", Long.class);
        assertThat(visible)
                .as("No authenticated tenant context must expose zero users")
                .isZero();
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
