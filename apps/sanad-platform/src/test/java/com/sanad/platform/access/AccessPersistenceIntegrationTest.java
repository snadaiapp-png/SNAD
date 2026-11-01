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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.sanad.platform.security.SecurityPermitAllTestConfig;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(SecurityPermitAllTestConfig.class)
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
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    private TransactionTemplate tx;

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
        if (tx == null) {
            tx = new TransactionTemplate(transactionManager);
        }
        // Cause A fix: use saveAndFlush for tenants to force INSERTs immediately
        // (FK constraint fk_users_tenant requires tenant rows to exist before
        // seedUserRaw's raw JdbcTemplate INSERT INTO users).
        tenantA = saveTenant("Access A");
        tenantB = saveTenant("Access B");
        // V20261029_1: organizations / roles / role_capabilities /
        // user_role_assignments are FORCE RLS. Seed via raw JDBC with an inline
        // set_config per seed (the established seedUserRaw pattern) — repository
        // saves would desync TenantRlsConnectionHandler's per-transaction
        // bookkeeping after an external set_config flips the GUC mid-transaction.
        organizationA = seedOrgRaw(tenantA, "Org A");
        // Cause A fix: use seedUserRaw (raw JdbcTemplate set_config + INSERT) for
        // multi-tenant user INSERTs. JPA userRepository.save() defers INSERTs via
        // Hibernate; at flush time all deferred INSERTs run with the LAST set GUC,
        // causing RLS rejection for userB (tenant_id=tenantB mismatches GUC=tenantA).
        // seedUserRaw executes each INSERT immediately with the correct GUC.
        userA = seedUserRaw(tenantA, "a@example.com", "A");
        userB = seedUserRaw(tenantB, "b@example.com", "B");
        // V20261029_1: raw per-tenant seeding for the RBAC rows.
        roleA = seedRoleRaw(tenantA, "ADMIN", "Admin A");
        roleB = seedRoleRaw(tenantB, "ADMIN", "Admin B");
        capabilityId = capabilityRepository.save(new AccessCapability(
                "TEST_PERSISTENCE.READ", "Test persistence read", null)).getId();
        // Most test bodies act on tenantA: leave the transaction GUC scoped to
        // tenantA so tenant-scoped body writes pass RLS WITH CHECK.
        jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)",
                String.class, tenantA.toString());
    }

    @AfterEach
    void clearBoundTenantContext() {
        clearTenantContext();
        org.springframework.security.test.context.TestSecurityContextHolder.clearContext();
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
    void sameRoleCodeIsAllowedAcrossTenants() {
        assertThat(roleRepository.findByTenantIdAndCode(tenantA, "ADMIN")).isPresent();
        // V20261029_1: roles is FORCE RLS — the tenantB read must run under
        // tenant B's own GUC (this intentionally proves the SAME code is
        // legibly provisioned in BOTH tenants, each within its own scope).
        jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)",
                String.class, tenantB.toString());
        try {
            assertThat(roleRepository.findByTenantIdAndCode(tenantB, "ADMIN")).isPresent();
        } finally {
            jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)",
                    String.class, tenantA.toString());
        }
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
        // Cause A fix: use saveAndFlush to force tenant INSERT immediately
        return tenantRepository.saveAndFlush(new Tenant(
                name, name.toLowerCase().replace(' ', '-') + "-" + UUID.randomUUID(),
                TenantStatus.ACTIVE)).getId();
    }

    /** Tenant-scoped org seeding: raw JDBC with an inline set_config, mirroring
     *  seedUserRaw — required under FORCE RLS (V20261029_1). */
    private UUID seedOrgRaw(UUID tenantId, String name) {
        UUID orgId = UUID.randomUUID();
        tx.executeWithoutResult(s -> {
            jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)",
                    String.class, tenantId.toString());
            jdbc.update(
                    "INSERT INTO organizations (id, tenant_id, name, status, created_at, updated_at) "
                            + "VALUES (?, ?, ?, 'ACTIVE', NOW(), NOW())",
                    orgId, tenantId, name);
        });
        return orgId;
    }

    /** Tenant-scoped role seeding: raw JDBC with an inline set_config, mirroring
     *  seedUserRaw — required under FORCE RLS (V20261029_1). */
    private UUID seedRoleRaw(UUID tenantId, String code, String name) {
        UUID roleId = UUID.randomUUID();
        tx.executeWithoutResult(s -> {
            jdbc.queryForObject("SELECT set_config('app.tenant_id', ?, true)",
                    String.class, tenantId.toString());
            jdbc.update(
                    "INSERT INTO roles (id, tenant_id, code, name, status, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW())",
                    roleId, tenantId, code, name);
        });
        return roleId;
    }

    /**
     * Cause A fix helper — raw JdbcTemplate set_config + INSERT INTO users per
     * tenant, so each INSERT runs immediately with the correct GUC. Bypasses
     * Hibernate's deferred-flush + handler's per-tx GUC model.
     */
    private UUID seedUserRaw(UUID tenantId, String email, String displayName) {
        UUID userId = UUID.randomUUID();
        tx.executeWithoutResult(s -> {
            jdbc.queryForObject(
                    "SELECT set_config('app.tenant_id', ?, true)",
                    String.class, tenantId.toString());
            jdbc.update(
                    "INSERT INTO users (id, tenant_id, email, display_name, status, "
                            + "password_hash, must_change_password, platform_admin, session_version, "
                            + "created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, 'ACTIVE', 'dummy', false, false, 0, NOW(), NOW())",
                    userId, tenantId, email, displayName);
        });
        return userId;
    }
}
