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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PostgreSQL Direct RED→GREEN acceptance: populated tenant A/B fixtures,
 * non-bypass runtime, isolated SELECT/UPDATE/DELETE, absent GUC fail-closed.
 *
 * Must run against an EPHEMERAL local PostgreSQL database, never production.
 * All fixtures roll back with the surrounding Spring test transaction.
 */
@SpringBootTest
@ActiveProfiles("local")
class UsersRlsTenantABPostgresTest {
    @Autowired TenantRepository tenants;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired TenantRlsTransactionContext scope;

    @Test
    @Transactional
    void populatedTenantsCannotObserveOrMutateEachOther() {
        // Infrastructure phase (catalog probe + tenant fixture seeding) runs
        // WITHOUT any tenant context — explicitly classified per the strict
        // signed-binding contract. All tenant-isolation assertions below are
        // unchanged and now execute under HMAC-verified signed bindings.
        Tenant a;
        Tenant b;
        var both = new java.util.ArrayList<Tenant>();
        TenantRlsPreAuthScope.run(() -> {
            Boolean bypass = jdbc.queryForObject(
                    "SELECT rolsuper OR rolbypassrls FROM pg_roles WHERE rolname=current_user",
                    Boolean.class);
            assertThat(bypass).as("Test must use a non-bypass database role").isFalse();
            both.add(tenants.save(new Tenant("G1 A", "g1-a-" + UUID.randomUUID(), TenantStatus.ACTIVE)));
            both.add(tenants.save(new Tenant("G1 B", "g1-b-" + UUID.randomUUID(), TenantStatus.ACTIVE)));
            tenants.flush();
        });
        a = both.get(0);
        b = both.get(1);

        scope.applyForCurrentTransaction(a.getId());
        User userA = users.saveAndFlush(
                new User(a.getId(), "g1-a-" + UUID.randomUUID() + "@example.test",
                        "G1 User A", UserStatus.ACTIVE));

        scope.applyForCurrentTransaction(b.getId());
        User userB = users.saveAndFlush(
                new User(b.getId(), "g1-b-" + UUID.randomUUID() + "@example.test",
                        "G1 User B", UserStatus.ACTIVE));

        scope.applyForCurrentTransaction(a.getId());
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM public.users WHERE id = ?", Long.class, userA.getId()))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM public.users WHERE id = ?", Long.class, userB.getId()))
                .isZero();
        assertThat(jdbc.update("UPDATE public.users SET display_name = display_name WHERE id = ?",
                userB.getId())).isZero();
        assertThat(jdbc.update("DELETE FROM public.users WHERE id = ?",
                userB.getId())).isZero();

        // Unbound-session contract: the transaction re-scopes back to the
        // prestate (raw GUC cleared WITHOUT a mac) and must reveal zero users.
        // The re-scoping statement itself is binding infrastructure (explicitly
        // classified); the zero-row assertion runs on the tenant-data path.
        TenantRlsPreAuthScope.run(() -> {
            jdbc.execute("SELECT set_config('app.tenant_id', '', true)");
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.users", Long.class))
                .as("Missing tenant context must deny all populated users")
                .isZero();

        scope.applyForCurrentTransaction(b.getId());
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM public.users WHERE id = ?", Long.class, userB.getId()))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM public.users WHERE id = ?", Long.class, userA.getId()))
                .isZero();
    }
}
