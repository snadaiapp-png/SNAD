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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

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
        User result = service.bootstrap(false, tenantId, email(), value(), "Admin", "test-actor");
        assertThat(result).isNull();
        assertThat(userRepository.findByTenantId(tenantId)).isEmpty();
    }

    @Test
    void requiresExistingActiveTenant() {
        assertThatThrownBy(() -> service.bootstrap(
                true, UUID.randomUUID(), email(), value(), "Admin", "test-actor"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not exist");

        Tenant tenant = tenantRepository.save(new Tenant(
                "Suspended", "suspended-" + UUID.randomUUID(), TenantStatus.SUSPENDED));
        assertThatThrownBy(() -> service.bootstrap(
                true, tenant.getId(), email(), value(), "Admin", "test-actor"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be ACTIVE");
    }

    @Test
    void enrollmentCreatesAuditedAdminGrantAndRotationFlag() {
        Tenant tenant = activeTenant("new");
        String email = email();
        String value = value();

        User user = service.bootstrap(
                true, tenant.getId(), email, value, "Bootstrap Admin", "test-actor");

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
    }

    @Test
    void existingUserIsGrantedAdminAndSecondEnrollmentFailsClosed() {
        Tenant tenant = activeTenant("existing");
        String email = email();
        String value = value();
        User existing = userRepository.save(new User(
                tenant.getId(), email, "Existing", UserStatus.INVITED));

        User enrolled = service.bootstrap(
                true, tenant.getId(), email, value, "Admin", "test-actor");
        assertThat(enrolled.getId()).isEqualTo(existing.getId());
        assertThat(enrolled.getStatus()).isEqualTo(UserStatus.ACTIVE);
        String originalHash = enrolled.getPasswordHash();

        assertThatThrownBy(() -> service.bootstrap(
                true, tenant.getId(), email, value(), "Admin", "test-actor"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already enrolled");
        assertThat(userRepository.findByTenantIdAndEmail(tenant.getId(), email)
                .orElseThrow().getPasswordHash()).isEqualTo(originalHash);
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
}
