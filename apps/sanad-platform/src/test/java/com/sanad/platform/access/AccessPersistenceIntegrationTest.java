package com.sanad.platform.access;

import com.sanad.platform.access.capability.*;
import com.sanad.platform.access.grant.*;
import com.sanad.platform.access.role.*;
import com.sanad.platform.organization.domain.Organization;
import com.sanad.platform.organization.domain.OrganizationStatus;
import com.sanad.platform.organization.repository.OrganizationRepository;
import com.sanad.platform.tenant.domain.Tenant;
import com.sanad.platform.tenant.domain.TenantStatus;
import com.sanad.platform.tenant.repository.TenantRepository;
import com.sanad.platform.user.domain.User;
import com.sanad.platform.user.domain.UserStatus;
import com.sanad.platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class AccessPersistenceIntegrationTest {

    @Autowired private TenantRepository tenantRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private AccessCapabilityRepository capabilityRepository;
    @Autowired private RoleCapabilityRepository roleCapabilityRepository;
    @Autowired private UserRoleGrantRepository grantRepository;

    private UUID tenantA;
    private UUID tenantB;
    private UUID organizationA;
    private UUID userA;
    private UUID userB;
    private UUID roleA;
    private UUID roleB;
    private UUID capabilityId;

    @BeforeEach
    void setUp() {
        tenantA = saveTenant("Access A");
        tenantB = saveTenant("Access B");
        organizationA = organizationRepository.save(new Organization(
                tenantRepository.findById(tenantA).orElseThrow(),
                "Org A", "desc", OrganizationStatus.ACTIVE)).getId();
        userA = userRepository.save(new User(
                tenantA, "a@example.com", "A", UserStatus.ACTIVE)).getId();
        userB = userRepository.save(new User(
                tenantB, "b@example.com", "B", UserStatus.ACTIVE)).getId();
        roleA = roleRepository.save(new Role(
                tenantA, "ADMIN", "Admin A", null)).getId();
        roleB = roleRepository.save(new Role(
                tenantB, "ADMIN", "Admin B", null)).getId();
        capabilityId = capabilityRepository.save(new AccessCapability(
                "USER.READ", "Read users", null)).getId();
    }

    @Test
    void sameRoleCodeIsAllowedAcrossTenants() {
        assertThat(roleRepository.findByTenantIdAndCode(tenantA, "ADMIN")).isPresent();
        assertThat(roleRepository.findByTenantIdAndCode(tenantB, "ADMIN")).isPresent();
    }

    @Test
    void duplicateRoleCodeInsideTenantIsRejected() {
        assertThatThrownBy(() -> roleRepository.saveAndFlush(
                new Role(tenantA, "ADMIN", "Duplicate", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void roleCapabilityMappingPersists() {
        roleCapabilityRepository.saveAndFlush(
                new RoleCapability(tenantA, roleA, capabilityId));
        assertThat(roleCapabilityRepository.existsByTenantIdAndRoleIdAndCapabilityId(
                tenantA, roleA, capabilityId)).isTrue();
    }

    @Test
    void crossTenantRoleCapabilityMappingIsRejected() {
        assertThatThrownBy(() -> roleCapabilityRepository.saveAndFlush(
                new RoleCapability(tenantA, roleB, capabilityId)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void tenantWideGrantPersists() {
        UserRoleGrant saved = grantRepository.saveAndFlush(
                new UserRoleGrant(tenantA, userA, roleA, null));
        assertThat(saved.isTenantWide()).isTrue();
    }

    @Test
    void organizationScopedGrantPersists() {
        UserRoleGrant saved = grantRepository.saveAndFlush(
                new UserRoleGrant(tenantA, userA, roleA, organizationA));
        assertThat(saved.getOrganizationId()).isEqualTo(organizationA);
    }

    @Test
    void crossTenantUserGrantIsRejected() {
        assertThatThrownBy(() -> grantRepository.saveAndFlush(
                new UserRoleGrant(tenantA, userB, roleA, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void crossTenantRoleGrantIsRejected() {
        assertThatThrownBy(() -> grantRepository.saveAndFlush(
                new UserRoleGrant(tenantA, userA, roleB, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void tenantScopedGrantQueryDoesNotLeak() {
        grantRepository.saveAndFlush(new UserRoleGrant(tenantA, userA, roleA, null));
        assertThat(grantRepository.findByTenantIdAndUserIdOrderByCreatedAtAsc(
                tenantB, userA)).isEmpty();
    }

    @Test
    void duplicateOrganizationScopedGrantIsRejected() {
        grantRepository.saveAndFlush(
                new UserRoleGrant(tenantA, userA, roleA, organizationA));
        assertThatThrownBy(() -> grantRepository.saveAndFlush(
                new UserRoleGrant(tenantA, userA, roleA, organizationA)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID saveTenant(String name) {
        return tenantRepository.save(new Tenant(
                name, name.toLowerCase().replace(' ', '-') + "-" + UUID.randomUUID(),
                TenantStatus.ACTIVE)).getId();
    }
}
