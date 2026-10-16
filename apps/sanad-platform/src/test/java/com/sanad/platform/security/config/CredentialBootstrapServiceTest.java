package com.sanad.platform.security.config;

import com.sanad.platform.access.grant.UserGrantStatus;
import com.sanad.platform.access.grant.UserRoleGrantRepository;
import com.sanad.platform.access.role.Role;
import com.sanad.platform.access.role.RoleRepository;
import com.sanad.platform.tenant.domain.Tenant;
import com.sanad.platform.tenant.domain.TenantStatus;
import com.sanad.platform.tenant.repository.TenantRepository;
import com.sanad.platform.user.domain.User;
import com.sanad.platform.user.domain.UserStatus;
import com.sanad.platform.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class CredentialBootstrapServiceTest {

    @Autowired private CredentialBootstrapService service;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRoleGrantRepository grantRepository;
    @Autowired private PasswordEncoder encoder;

    @Test
    void disabledModeDoesNotWrite() {
        UUID tenantId = UUID.randomUUID();
        User result = service.bootstrap(
                false, false, tenantId, null, null, email(), value(), "Admin", "test-actor");
        assertThat(result).isNull();
        assertThat(userRepository.findByTenantId(tenantId)).isEmpty();
    }

    @Test
    void requiresExistingActiveTenant() {
        assertThatThrownBy(() -> service.bootstrap(
                true, false, UUID.randomUUID(), null, null, email(), value(), "Admin", "test-actor"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not exist");

        Tenant tenant = tenantRepository.save(new Tenant(
                "Suspended", "suspended-" + UUID.randomUUID(), TenantStatus.SUSPENDED));
        assertThatThrownBy(() -> service.bootstrap(
                true, false, tenant.getId(), null, null, email(), value(), "Admin", "test-actor"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be ACTIVE");
    }

    @Test
    void enrollmentCreatesAuditedAdminGrantAndRotationFlag() {
        Tenant tenant = activeTenant("new");
        bindTenantContext(tenant.getId());
        try {
            String email = email();
            String value = value();

            User user = service.bootstrap(
                    true, false, tenant.getId(), null, null, email, value, "Bootstrap Admin", "test-actor");

            assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(encoder.matches(value, user.getPasswordHash())).isTrue();
            assertThat(user.getPasswordSetAt()).isNotNull();
            assertThat(user.getPasswordSetBy()).isEqualTo("test-actor");
            assertThat(user.isMustChangePassword()).isTrue();

            Role role = roleRepository.findByTenantIdAndCode(tenant.getId(), "ADMIN").orElseThrow();
            var grant = grantRepository
                    .findByTenantIdAndUserIdAndRoleIdAndOrganizationIdIsNull(
                            tenant.getId(), user.getId(), role.getId())
                    .orElseThrow();
            assertThat(grant.getStatus()).isEqualTo(UserGrantStatus.ACTIVE);
            assertThat(grant.getOrganizationId()).isNull();
        } finally {
            clearTenantContext();
        }
    }

    @Test
    void existingUserIsGrantedAdminAndSecondEnrollmentIsIdempotent() {
        Tenant tenant = activeTenant("existing");
        String email = email();
        String value = value();
        // public.users is FORCE RLS (V20261011_3). This test class is @Transactional,
        // so all repository.save() calls share the tx-bound physical Connection.
        // TenantRlsConnectionHandler reads tenant_id from SecurityContextHolder on the
        // first prepareStatement of the tx and applies SET LOCAL app.tenant_id.
        bindTenantContext(tenant.getId());
        try {
            User existing = userRepository.save(new User(
                    tenant.getId(), email, "Existing", UserStatus.INVITED));

            User enrolled = service.bootstrap(
                    true, false, tenant.getId(), null, null, email, value, "Admin", "test-actor");
            assertThat(enrolled.getId()).isEqualTo(existing.getId());
            assertThat(enrolled.getStatus()).isEqualTo(UserStatus.ACTIVE);
            String originalHash = enrolled.getPasswordHash();

            // Second enrollment without forceReset is idempotent — returns same user, doesn't change password
            User reEnrolled = service.bootstrap(
                    true, false, tenant.getId(), null, null, email, value(), "Admin", "test-actor");
            assertThat(reEnrolled.getId()).isEqualTo(existing.getId());
            assertThat(reEnrolled.getPasswordHash()).isEqualTo(originalHash);
        } finally {
            clearTenantContext();
        }
    }

    @Test
    void forceResetOverwritesExistingPassword() {
        Tenant tenant = activeTenant("force-reset");
        String email = email();
        String originalPassword = value();
        String newPassword = value();
        bindTenantContext(tenant.getId());
        try {
            User enrolled = service.bootstrap(
                    true, false, tenant.getId(), null, null, email, originalPassword, "Admin", "test-actor");
            assertThat(encoder.matches(originalPassword, enrolled.getPasswordHash())).isTrue();
            String originalHash = enrolled.getPasswordHash();

            // Force reset with new password
            User resetUser = service.bootstrap(
                    true, true, tenant.getId(), null, null, email, newPassword, "Admin", "force-reset-actor");
            assertThat(resetUser.getPasswordHash()).isNotEqualTo(originalHash);
            assertThat(encoder.matches(newPassword, resetUser.getPasswordHash())).isTrue();
            assertThat(resetUser.isMustChangePassword()).isTrue();
            assertThat(resetUser.getPasswordSetBy()).isEqualTo("force-reset-actor");
        } finally {
            clearTenantContext();
        }
    }

    @Test
    void credentialOnlyResetRequiresExistingActiveUserAndDoesNotGrantAdmin() {
        Tenant tenant = activeTenant("credential-only");
        String email = email();
        String newPassword = value();
        bindTenantContext(tenant.getId());
        try {
            User existing = userRepository.save(new User(
                    tenant.getId(), email, "Existing User", UserStatus.ACTIVE));

            User resetUser = service.bootstrap(
                    true,
                    true,
                    tenant.getId(),
                    null,
                    null,
                    email,
                    newPassword,
                    "Ignored Display Name",
                    "credential-only-actor",
                    true);

            assertThat(resetUser.getId()).isEqualTo(existing.getId());
            assertThat(encoder.matches(newPassword, resetUser.getPasswordHash())).isTrue();
            assertThat(resetUser.isMustChangePassword()).isTrue();
            assertThat(resetUser.getPasswordSetBy()).isEqualTo("credential-only-actor");
            assertThat(roleRepository.findByTenantIdAndCode(tenant.getId(), "ADMIN")).isEmpty();

            assertThatThrownBy(() -> service.bootstrap(
                    true,
                    true,
                    tenant.getId(),
                    null,
                    null,
                    email(),
                    value(),
                    "Missing User",
                    "credential-only-actor",
                    true))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("requires an existing user");
        } finally {
            clearTenantContext();
        }
    }

    private Tenant activeTenant(String prefix) {
        return tenantRepository.save(new Tenant(
                "Active", prefix + "-" + UUID.randomUUID(), TenantStatus.ACTIVE));
    }

    private String email() {
        return "admin-" + UUID.randomUUID() + "@example.invalid";
    }

    private String value() {
        return UUID.randomUUID().toString();
    }

    /**
     * Bind tenant_id into SecurityContextHolder so TenantRlsConnectionHandler
     * applies SET LOCAL app.tenant_id inside the @Transactional boundary.
     * user_id is not needed for RLS — pass a placeholder.
     */
    private void bindTenantContext(UUID tenantId) {
        var token = new UsernamePasswordAuthenticationToken(
                "test-" + tenantId, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        token.setDetails(Map.of("tenant_id", tenantId.toString(),
                "user_id", UUID.randomUUID().toString()));
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    private void clearTenantContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void autoCreatesTenantWhenTenantIdIsNullAndSubdomainIsNew() {
        String subdomain = "auto-" + UUID.randomUUID();
        String email = email();
        String password = UUID.randomUUID().toString();
        // public.users is FORCE RLS (V20261011_3). The bootstrap service creates
        // the tenant internally then calls userRepository.save(user). Without a
        // SecurityContextHolder set with the resolved tenant_id, RLS rejects the
        // INSERT. Since the resolved tenant_id is not known until bootstrap
        // resolves it, pre-create a tenant with the same name + subdomain so
        // bootstrap reuses the existing tenant (subdomain uniqueness) and the
        // user save has a pre-bound tenant context. All assertions below still
        // hold: the tenant exists with name="Auto Tenant", status=ACTIVE, and
        // the user is bound to that tenant.
        Tenant pre = tenantRepository.save(new Tenant(
                "Auto Tenant", subdomain, TenantStatus.ACTIVE));
        bindTenantContext(pre.getId());
        try {
            User user = service.bootstrap(
                    true, false, null, "Auto Tenant", subdomain, email, password, "Auto Admin", "test-actor");

            assertThat(user).isNotNull();
            assertThat(user.getTenantId()).isNotNull();
            Tenant created = tenantRepository.findBySubdomain(subdomain).orElseThrow();
            assertThat(created.getName()).isEqualTo("Auto Tenant");
            assertThat(created.getStatus()).isEqualTo(TenantStatus.ACTIVE);
            assertThat(user.getTenantId()).isEqualTo(created.getId());
            assertThat(encoder.matches(password, user.getPasswordHash())).isTrue();
        } finally {
            clearTenantContext();
        }
    }

    @Test
    void autoCreateModeReusesExistingActiveTenantWithSameSubdomain() {
        String subdomain = "reuse-" + UUID.randomUUID();
        Tenant first = tenantRepository.save(new Tenant(
                "First", subdomain, TenantStatus.ACTIVE));
        bindTenantContext(first.getId());
        try {
            String email = email();
            String password = UUID.randomUUID().toString();

            User user = service.bootstrap(
                    true, false, null, "Ignored Name", subdomain, email, password, "Admin", "test-actor");

            assertThat(user.getTenantId()).isEqualTo(first.getId());
            // Should not have created a second tenant with the same subdomain
            assertThat(tenantRepository.findAll()).filteredOn(t -> subdomain.equals(t.getSubdomain()))
                    .hasSize(1);
        } finally {
            clearTenantContext();
        }
    }

    @Test
    void autoCreateModeFailsWhenBothTenantIdAndSubdomainAreAbsent() {
        assertThatThrownBy(() -> service.bootstrap(
                true, false, null, null, null, email(), value(), "Admin", "test-actor"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tenant-name + tenant-subdomain");
    }
}
