package com.sanad.platform.organization.membership.service;

import com.sanad.platform.organization.domain.Organization;
import com.sanad.platform.organization.domain.OrganizationStatus;
import com.sanad.platform.organization.membership.domain.MembershipStatus;
import com.sanad.platform.organization.membership.domain.OrganizationMembership;
import com.sanad.platform.organization.membership.dto.OrganizationMembershipResponse;
import com.sanad.platform.organization.membership.repository.OrganizationMembershipRepository;
import com.sanad.platform.organization.repository.OrganizationRepository;
import com.sanad.platform.security.SecurityPermitAllTestConfig;
import com.sanad.platform.tenant.domain.Tenant;
import com.sanad.platform.tenant.domain.TenantStatus;
import com.sanad.platform.tenant.repository.TenantRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * REGRESSION TEST — Users Module Closure (PR #1414, CI run 39069551140).
 *
 * <p>{@code listMembershipsByUser} previously used
 * {@code Propagation.SUPPORTS}. Invoked by {@code UserMembershipController}
 * with no outer transaction, the JDBC connection stayed in
 * {@code autoCommit=true}, so {@code TenantRlsConnectionHandler} never applied
 * {@code app.tenant_id}, and under FORCE ROW LEVEL SECURITY on
 * {@code public.users} (V20261011_3) the mandatory
 * {@code userRepository.findByTenantIdAndId(...)} existence check returned
 * 0 rows and threw {@link EntityNotFoundException}. The Users detail page
 * therefore lost its whole payload ({@code #user-memberships-heading} /
 * {@code management-user-identity} never rendered) and the Users Module
 * Closure Playwright gate failed.</p>
 *
 * <p>This test calls the service WITHOUT any outer transaction (no class-level
 * {@code @Transactional}) — the exact production condition. The REQUIRED
 * propagation fix must open its own transaction so the RLS handler applies
 * the tenant GUC and the read succeeds. Cross-tenant lookups must remain
 * fail-closed.</p>
 *
 * <p>Fixture writes follow the established RLS-scoped pattern: each users-row
 * INSERT carries an explicit transaction-local {@code app.tenant_id} GUC. No
 * role, grant, or policy is weakened by this test.</p>
 */
@SpringBootTest
@Import(SecurityPermitAllTestConfig.class)
@ActiveProfiles("local")
class OrganizationMembershipUserLinkRlsPropagationTest {

    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private OrganizationMembershipRepository membershipRepository;
    @Autowired
    private OrganizationMembershipUserLinkService service;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;
    private UUID tenantAId;
    private UUID tenantBId;
    private UUID orgA1Id;
    private UUID userAId;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);

        Tenant tenantA = tx.execute(s -> tenantRepository.saveAndFlush(
                new Tenant("Rls Prop A", "rls-prop-a-" + UUID.randomUUID(), TenantStatus.ACTIVE)));
        Tenant tenantB = tx.execute(s -> tenantRepository.saveAndFlush(
                new Tenant("Rls Prop B", "rls-prop-b-" + UUID.randomUUID(), TenantStatus.ACTIVE)));
        tenantAId = tenantA.getId();
        tenantBId = tenantB.getId();

        bindTenantContext(tenantAId);
        Organization orgA1 = tx.execute(s -> organizationRepository.saveAndFlush(
                new Organization(tenantRepository.getReferenceById(tenantAId),
                        "Org A1", "desc", OrganizationStatus.ACTIVE)));
        orgA1Id = orgA1.getId();

        // public.users is FORCE RLS (V20261011_3): seed with an explicit
        // transaction-local GUC on its own committed transaction.
        userAId = seedUserRaw(tenantAId, "rls-prop-alice-" + UUID.randomUUID() + "@example.com");

        OrganizationMembership membership = tx.execute(s ->
                membershipRepository.saveAndFlush(
                        membership("rls-prop-alice-linked@example.com", orgA1Id, tenantAId, userAId)));
        assertThat(membership.getUserId()).isEqualTo(userAId);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        // FK-safe cleanup of committed fixture rows: memberships → organizations
        // → users (per-tenant GUC under FORCE RLS) → tenants.
        try {
            tx.executeWithoutResult(s -> jdbc.update(
                    "DELETE FROM organization_memberships WHERE tenant_id IN (?, ?)",
                    tenantAId, tenantBId));
            tx.executeWithoutResult(s -> jdbc.update(
                    "DELETE FROM organizations WHERE tenant_id = ?", tenantAId));
            deleteUserScoped(tenantAId);
            deleteUserScoped(tenantBId);
            tx.executeWithoutResult(s -> jdbc.update(
                    "DELETE FROM tenants WHERE id IN (?, ?)", tenantAId, tenantBId));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void listMembershipsByUserResolvesUnderForceRlsWithoutOuterTransaction() {
        // Production condition: NO outer transaction. The service itself must
        // open one (REQUIRED) so TenantRlsConnectionHandler applies
        // app.tenant_id before the RLS-guarded user existence check runs.
        bindTenantContext(tenantAId);

        List<OrganizationMembershipResponse> result = service.listMembershipsByUser(tenantAId, userAId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOrganizationId()).isEqualTo(orgA1Id);
        assertThat(result.get(0).getUserId()).isEqualTo(userAId);
        assertThat(result.get(0).getStatus()).isEqualTo(MembershipStatus.ACTIVE);
    }

    @Test
    void listMembershipsByUserStaysFailClosedAcrossTenants() {
        // Security invariant preserved by the fix: a tenant-B-scoped lookup of
        // a tenant-A user must still be denied under FORCE RLS.
        bindTenantContext(tenantBId);

        assertThatThrownBy(() -> service.listMembershipsByUser(tenantBId, userAId))
                .isInstanceOf(EntityNotFoundException.class);
    }

    /**
     * Insert a user under an explicit transaction-local app.tenant_id GUC on
     * its own committed transaction. SecurityContextHolder is bound to the
     * same tenant, so TenantRlsConnectionHandler state stays consistent.
     */
    private UUID seedUserRaw(UUID scopedTenantId, String email) {
        UUID userId = UUID.randomUUID();
        tx.executeWithoutResult(s -> {
            jdbc.queryForObject(
                    "SELECT set_config('app.tenant_id', ?, true)", String.class,
                    scopedTenantId.toString());
            jdbc.update(
                    "INSERT INTO users (id, tenant_id, email, display_name, status, "
                            + "password_hash, must_change_password, platform_admin, session_version, "
                            + "created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, 'ACTIVE', 'dummy', false, false, 0, NOW(), NOW())",
                    userId, scopedTenantId, email, "Alice");
        });
        return userId;
    }

    private void deleteUserScoped(UUID scopedTenantId) {
        tx.executeWithoutResult(s -> {
            jdbc.queryForObject(
                    "SELECT set_config('app.tenant_id', ?, true)", String.class,
                    scopedTenantId.toString());
            jdbc.update("DELETE FROM users WHERE tenant_id = ?", scopedTenantId);
        });
    }

    private OrganizationMembership membership(String email, UUID organizationId,
                                              UUID tenantId, UUID userId) {
        OrganizationMembership membership = new OrganizationMembership(
                tenantId, organizationId, email, "Member", MembershipStatus.ACTIVE);
        membership.setUserId(userId);
        return membership;
    }

    /**
     * Bind tenant_id into SecurityContextHolder so TenantRlsConnectionHandler
     * applies SET LOCAL app.tenant_id inside the transaction boundary.
     */
    private void bindTenantContext(UUID tenantId) {
        var token = new UsernamePasswordAuthenticationToken(
                "test-" + tenantId, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        token.setDetails(Map.of("tenant_id", tenantId.toString(),
                "user_id", UUID.randomUUID().toString()));
        SecurityContextHolder.getContext().setAuthentication(token);
    }
}
