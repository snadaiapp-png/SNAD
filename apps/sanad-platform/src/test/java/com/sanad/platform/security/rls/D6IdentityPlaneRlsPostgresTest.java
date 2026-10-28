package com.sanad.platform.security.rls;

import com.sanad.platform.tenant.domain.Tenant;
import com.sanad.platform.tenant.domain.TenantStatus;
import com.sanad.platform.tenant.repository.TenantRepository;
import com.sanad.platform.user.domain.User;
import com.sanad.platform.user.domain.UserStatus;
import com.sanad.platform.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * D6 closure (SANAD-G1): PostgreSQL Direct acceptance for the identity-plane
 * fail-closed tenant RLS introduced by V20261029_1 on
 * roles, role_capabilities, user_role_assignments,
 * organizations, organization_memberships.
 *
 * Proves (per the D6 gate):
 *   1. A/B cross-tenant isolation for SELECT/UPDATE/DELETE on every table.
 *   2. WITH CHECK denies cross-tenant INSERT and tenant-moving UPDATE.
 *   3. Missing tenant GUC reveals nothing (fail-closed, no cast error).
 *   4. FORCE RLS is enabled while the runtime role owns the tables
 *      (owner==runtime, so FORCE is load-bearing).
 *   5. The runtime role is non-superuser and holds no BYPASSRLS.
 *   6. Reserved zero-UUID scope and unknown tenant GUC grant nothing.
 *
 * Must run against an EPHEMERAL local PostgreSQL database, never production.
 * All fixtures roll back with the surrounding Spring test transaction.
 */
@SpringBootTest
@ActiveProfiles("local")
class D6IdentityPlaneRlsPostgresTest {

    private static final List<String> TABLES = List.of(
            "roles", "role_capabilities", "user_role_assignments",
            "organizations", "organization_memberships");

    @Autowired TenantRepository tenants;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired TenantRlsTransactionContext scope;

    private String capsId() {
        return jdbc.queryForObject(
                "SELECT id FROM public.access_capabilities ORDER BY code LIMIT 1", String.class);
    }

    private void seedRole(String tenant, UUID roleId, String code) {
        jdbc.update("""
                INSERT INTO public.roles
                (id, tenant_id, code, name, status, created_at, updated_at)
                VALUES (?, ?::uuid, ?, ?, 'ACTIVE', NOW(), NOW())
                """, roleId, tenant, code, "D6 " + code);
    }

    @Test
    @Transactional
    void runtimeRoleIsNonBypassAndTablesAreForced() {
        Boolean bypass = jdbc.queryForObject(
                "SELECT rolsuper OR rolbypassrls FROM pg_roles WHERE rolname=current_user",
                Boolean.class);
        assertThat(bypass).as("Test must use a non-bypass database role").isFalse();

        Map<String, Object> id = jdbc.queryForMap(
                "SELECT current_user AS usr, (SELECT rolsuper FROM pg_roles WHERE rolname=current_user) AS su");
        String runtime = String.valueOf(id.get("usr"));
        for (String t : TABLES) {
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT c.relrowsecurity AS en, c.relforcerowsecurity AS fc, "
                    + "pg_get_userbyid(c.relowner) AS owner "
                    + "FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace "
                    + "WHERE n.nspname='public' AND c.relname=?", t);
            assertThat(Boolean.TRUE.equals(row.get("en")))
                    .as("%s must have RLS enabled", t).isTrue();
            assertThat(Boolean.TRUE.equals(row.get("fc")))
                    .as("%s must FORCE RLS (owner == runtime role %s)", t, runtime).isTrue();
            assertThat(row.get("owner")).as("%s owner", t).isEqualTo(runtime);
        }
    }

    @Test
    @Transactional
    void populatedTenantsCannotObserveOrMutateEachOther() {
        Tenant a = tenants.save(new Tenant("D6 A", "d6-a-" + UUID.randomUUID(), TenantStatus.ACTIVE));
        Tenant b = tenants.save(new Tenant("D6 B", "d6-b-" + UUID.randomUUID(), TenantStatus.ACTIVE));

        UUID roleA = UUID.randomUUID(), roleB = UUID.randomUUID();
        UUID orgA = UUID.randomUUID(), orgB = UUID.randomUUID();
        String cap = capsId();

        scope.applyForCurrentTransaction(a.getId());
        User userA = users.saveAndFlush(new User(a.getId(),
                "d6-a-" + UUID.randomUUID() + "@example.test", "D6 User A", UserStatus.ACTIVE));
        seedRole(a.getId().toString(), roleA, "D6_ROLE_A");
        jdbc.update("""
                INSERT INTO public.role_capabilities (id, tenant_id, role_id, capability_id, created_at)
                VALUES (?, ?::uuid, ?::uuid, ?::uuid, NOW())
                """, UUID.randomUUID(), a.getId(), roleA, UUID.fromString(cap));
        jdbc.update("""
                INSERT INTO public.organizations (id, tenant_id, name, status, created_at, updated_at)
                VALUES (?, ?::uuid, ?, 'ACTIVE', NOW(), NOW())
                """, orgA, a.getId(), "D6 Org A");
        jdbc.update("""
                INSERT INTO public.organization_memberships
                (id, tenant_id, organization_id, user_id, email, status, created_at, updated_at)
                VALUES (?, ?::uuid, ?::uuid, ?::uuid, ?, 'ACTIVE', NOW(), NOW())
                """, UUID.randomUUID(), a.getId(), orgA, userA.getId(), userA.getEmail());
        jdbc.update("""
                INSERT INTO public.user_role_assignments
                (id, tenant_id, user_id, role_id, created_at, updated_at)
                VALUES (?, ?::uuid, ?::uuid, ?::uuid, NOW(), NOW())
                """, UUID.randomUUID(), a.getId(), userA.getId(), roleA);

        scope.applyForCurrentTransaction(b.getId());
        User userB = users.saveAndFlush(new User(b.getId(),
                "d6-b-" + UUID.randomUUID() + "@example.test", "D6 User B", UserStatus.ACTIVE));
        seedRole(b.getId().toString(), roleB, "D6_ROLE_B");
        jdbc.update("""
                INSERT INTO public.role_capabilities (id, tenant_id, role_id, capability_id, created_at)
                VALUES (?, ?::uuid, ?::uuid, ?::uuid, NOW())
                """, UUID.randomUUID(), b.getId(), roleB, UUID.fromString(cap));
        jdbc.update("""
                INSERT INTO public.organizations (id, tenant_id, name, status, created_at, updated_at)
                VALUES (?, ?::uuid, ?, 'ACTIVE', NOW(), NOW())
                """, orgB, b.getId(), "D6 Org B");
        jdbc.update("""
                INSERT INTO public.organization_memberships
                (id, tenant_id, organization_id, user_id, email, status, created_at, updated_at)
                VALUES (?, ?::uuid, ?::uuid, ?::uuid, ?, 'ACTIVE', NOW(), NOW())
                """, UUID.randomUUID(), b.getId(), orgB, userB.getId(), userB.getEmail());
        jdbc.update("""
                INSERT INTO public.user_role_assignments
                (id, tenant_id, user_id, role_id, created_at, updated_at)
                VALUES (?, ?::uuid, ?::uuid, ?::uuid, NOW(), NOW())
                """, UUID.randomUUID(), b.getId(), userB.getId(), roleB);

        // Tenant A must see only its own rows on every identity-plane table.
        scope.applyForCurrentTransaction(a.getId());
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM public.roles WHERE id=?::uuid", Long.class, roleA)).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM public.roles WHERE id=?::uuid", Long.class, roleB)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM public.role_capabilities WHERE role_id=?::uuid", Long.class, roleB)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM public.user_role_assignments WHERE user_id=?::uuid",
                Long.class, userB.getId())).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM public.organizations WHERE id=?::uuid", Long.class, orgB)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM public.organization_memberships WHERE user_id=?::uuid",
                Long.class, userB.getId())).isZero();

        // Tenant A must not mutate or delete tenant B rows on any table.
        assertThat(jdbc.update("UPDATE public.roles SET name=name WHERE id=?::uuid", roleB)).isZero();
        assertThat(jdbc.update("DELETE FROM public.roles WHERE id=?::uuid", roleB)).isZero();
        assertThat(jdbc.update(
                "UPDATE public.role_capabilities SET role_id=role_id WHERE role_id=?::uuid", roleB)).isZero();
        assertThat(jdbc.update(
                "DELETE FROM public.role_capabilities WHERE role_id=?::uuid", roleB)).isZero();
        assertThat(jdbc.update(
                "UPDATE public.user_role_assignments SET role_id=role_id WHERE user_id=?::uuid",
                userB.getId())).isZero();
        assertThat(jdbc.update(
                "DELETE FROM public.user_role_assignments WHERE user_id=?::uuid", userB.getId())).isZero();
        assertThat(jdbc.update(
                "UPDATE public.organizations SET name=name WHERE id=?::uuid", orgB)).isZero();
        assertThat(jdbc.update(
                "DELETE FROM public.organizations WHERE id=?::uuid", orgB)).isZero();
        assertThat(jdbc.update(
                "UPDATE public.organization_memberships SET status=status WHERE user_id=?::uuid",
                userB.getId())).isZero();
        assertThat(jdbc.update(
                "DELETE FROM public.organization_memberships WHERE user_id=?::uuid", userB.getId())).isZero();

        // Tenant B symmetric.
        scope.applyForCurrentTransaction(b.getId());
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM public.roles WHERE id=?::uuid", Long.class, roleB)).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM public.roles WHERE id=?::uuid", Long.class, roleA)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM public.organizations WHERE id=?::uuid", Long.class, orgA)).isZero();
    }

    @Test
    @Transactional
    void withCheckBlocksCrossTenantInsertAndTenantMove() {
        Tenant a = tenants.saveAndFlush(new Tenant("D6 WC A", "d6-wc-a-" + UUID.randomUUID(), TenantStatus.ACTIVE));
        Tenant b = tenants.saveAndFlush(new Tenant("D6 WC B", "d6-wc-b-" + UUID.randomUUID(), TenantStatus.ACTIVE));
        UUID roleA = UUID.randomUUID();
        scope.applyForCurrentTransaction(a.getId());
        seedRole(a.getId().toString(), roleA, "D6_WC_A");

        // INSERT claiming tenant B while running under tenant A must be rejected.
        scope.applyForCurrentTransaction(a.getId());
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO public.organizations (id, tenant_id, name, status, created_at, updated_at)
                VALUES (?, ?::uuid, 'Cross Tenant Org', 'ACTIVE', NOW(), NOW())
                """, UUID.randomUUID(), b.getId()))
                .as("WITH CHECK must deny inserting a row for tenant B under tenant A context")
                .isInstanceOf(DataAccessException.class);

        // UPDATE moving a tenant A role into tenant B must be rejected.
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE public.roles SET tenant_id=?::uuid WHERE id=?::uuid", b.getId(), roleA))
                .as("WITH CHECK must deny moving a row across tenants")
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @Transactional
    void missingTenantContextRevealsNothingAndResetsCleanly() {
        Tenant a = tenants.saveAndFlush(new Tenant("D6 NC A", "d6-nc-a-" + UUID.randomUUID(), TenantStatus.ACTIVE));
        UUID roleA = UUID.randomUUID();
        scope.applyForCurrentTransaction(a.getId());
        seedRole(a.getId().toString(), roleA, "D6_NC_A");

        scope.applyForCurrentTransaction(a.getId());
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM public.roles WHERE id=?::uuid", Long.class, roleA)).isEqualTo(1L);

        // Missing/empty tenant context: fail closed on every table (NULLIF form
        // must NOT raise a uuid cast error).
        jdbc.execute("SELECT set_config('app.tenant_id', '', true)");
        for (String t : TABLES) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM public." + t, Long.class))
                    .as("Empty tenant GUC must reveal zero rows in %s", t).isZero();
        }

        // Unknown tenant context: still nothing.
        jdbc.execute("SELECT set_config('app.tenant_id', '" + UUID.randomUUID() + "', true)");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.roles", Long.class)).isZero();

        // Reserved zero-UUID scope (fail-closed handler value): still nothing.
        jdbc.execute("SELECT set_config('app.tenant_id', '00000000-0000-0000-0000-000000000000', true)");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.roles", Long.class)).isZero();
    }

    @Test
    @Transactional
    void pooledConnectionContextResetDoesNotLeakAcrossTransactions() {
        Tenant a = tenants.saveAndFlush(new Tenant("D6 Pool A", "d6-pool-a-" + UUID.randomUUID(), TenantStatus.ACTIVE));
        Tenant b = tenants.saveAndFlush(new Tenant("D6 Pool B", "d6-pool-b-" + UUID.randomUUID(), TenantStatus.ACTIVE));
        UUID roleA = UUID.randomUUID(), roleB = UUID.randomUUID();
        scope.applyForCurrentTransaction(a.getId());
        seedRole(a.getId().toString(), roleA, "D6_POOL_A");
        scope.applyForCurrentTransaction(b.getId());
        seedRole(b.getId().toString(), roleB, "D6_POOL_B");

        // Simulate a pooled connection that previously served tenant A, then a
        // context reset. The stale SET LOCAL scope must not leak: zero rows.
        scope.applyForCurrentTransaction(a.getId());
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM public.roles WHERE id=?::uuid", Long.class, roleA)).isEqualTo(1L);
        jdbc.execute("SELECT set_config('app.tenant_id', '', true)");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.roles", Long.class))
                .as("A pooled connection with a reset context must not leak tenant A rows")
                .isZero();
    }
}
