package com.sanad.platform.organization.membership.repository;

import com.sanad.platform.organization.domain.Organization;
import com.sanad.platform.organization.domain.OrganizationStatus;
import com.sanad.platform.organization.membership.domain.MembershipStatus;
import com.sanad.platform.organization.membership.domain.OrganizationMembership;
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
import com.sanad.platform.security.SecurityPermitAllTestConfig;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(SecurityPermitAllTestConfig.class)
@ActiveProfiles("local")
@Transactional
class OrganizationMembershipUserLinkRepositoryTest {

    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private OrganizationMembershipRepository membershipRepository;

    private UUID tenantAId;
    private UUID tenantBId;
    private UUID orgA1Id;
    private UUID orgA2Id;
    private UUID userAId;
    private UUID userBId;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        // saveAndFlush: the raw users INSERT below runs BEFORE any auto-flush
        // point, so the tenant rows must physically exist first or fk_users_tenant
        // would reject the fixture users.
        Tenant tenantA = tenantRepository.saveAndFlush(
                new Tenant("Tenant A", "link-a-" + UUID.randomUUID(), TenantStatus.ACTIVE));
        Tenant tenantB = tenantRepository.saveAndFlush(
                new Tenant("Tenant B", "link-b-" + UUID.randomUUID(), TenantStatus.ACTIVE));
        tenantAId = tenantA.getId();
        tenantBId = tenantB.getId();

        orgA1Id = organizationRepository.save(new Organization(
                tenantA, "Org A1", "desc", OrganizationStatus.ACTIVE)).getId();
        orgA2Id = organizationRepository.save(new Organization(
                tenantA, "Org A2", "desc", OrganizationStatus.ACTIVE)).getId();

        // public.users is FORCE RLS (V20261011_3). Both fixture users are seeded
        // with an explicit transaction-local set_config per INSERT (the same
        // contract as AccessPersistenceIntegrationTest#seedUserRaw). The
        // SecurityContextHolder is deliberately NOT bound: JPA save() defers the
        // INSERT until flush, by which time a bound context may already have been
        // switched for the second tenant, and TenantRlsConnectionHandler would
        // reject the mid-transaction identity change. The explicit GUC keeps each
        // INSERT fail-closed-scoped to its own tenant without guard state.
        userAId = seedUserRaw(tenantAId, "alice@example.com", "Alice");
        userBId = seedUserRaw(tenantBId, "alice@example.com", "Alice B");

        // The test bodies operate on tenant A (orgA1/orgA2 memberships); restore
        // the transaction's GUC to tenant A after seeding the foreign tenant.
        jdbc.queryForObject(
                "SELECT set_config('app.tenant_id', ?, true)", String.class,
                tenantAId.toString());
    }

    /**
     * Insert a user under an explicit transaction-local app.tenant_id GUC.
     * Runs on the shared @Transactional test connection, so the row participates
     * in the test's rollback. SecurityContextHolder stays unbound: the
     * TenantRlsConnectionHandler remains passive (no guard state) while the
     * fail-closed users_tenant_isolation policy still scopes the INSERT.
     */
    private UUID seedUserRaw(UUID scopedTenantId, String email, String displayName) {
        UUID userId = UUID.randomUUID();
        jdbc.queryForObject(
                "SELECT set_config('app.tenant_id', ?, true)", String.class,
                scopedTenantId.toString());
        jdbc.update(
                "INSERT INTO users (id, tenant_id, email, display_name, status, "
                        + "password_hash, must_change_password, platform_admin, session_version, "
                        + "created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 'ACTIVE', 'dummy', false, false, 0, NOW(), NOW())",
                userId, scopedTenantId, email, displayName);
        return userId;
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
    void invitationWithoutUserLinkRemainsValid() {
        OrganizationMembership saved = membershipRepository.saveAndFlush(
                membership("invite@example.com", orgA1Id));
        assertThat(saved.getUserId()).isNull();
    }

    @Test
    void linkedMembershipCanBeFoundByTenantAndUser() {
        OrganizationMembership membership = membership("alice@example.com", orgA1Id);
        membership.setUserId(userAId);
        membershipRepository.saveAndFlush(membership);

        List<OrganizationMembership> result =
                membershipRepository.findByTenantIdAndUserId(tenantAId, userAId);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo(userAId);
    }

    @Test
    void linkedMembershipCanBeFoundByOrganizationAndUser() {
        OrganizationMembership membership = membership("alice@example.com", orgA1Id);
        membership.setUserId(userAId);
        membershipRepository.saveAndFlush(membership);

        assertThat(membershipRepository.findByTenantIdAndOrganizationIdAndUserId(
                tenantAId, orgA1Id, userAId)).isPresent();
    }

    @Test
    void tenantScopedUserLookupDoesNotLeakAcrossTenants() {
        OrganizationMembership membership = membership("alice@example.com", orgA1Id);
        membership.setUserId(userAId);
        membershipRepository.saveAndFlush(membership);

        assertThat(membershipRepository.findByTenantIdAndUserId(tenantBId, userAId))
                .isEmpty();
    }

    @Test
    void compositeForeignKeyRejectsCrossTenantUserLink() {
        OrganizationMembership membership = membership("alice@example.com", orgA1Id);
        membership.setUserId(userBId);

        assertThatThrownBy(() -> membershipRepository.saveAndFlush(membership))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameUserCannotHaveTwoMembershipRowsInSameOrganization() {
        OrganizationMembership first = membership("alice@example.com", orgA1Id);
        first.setUserId(userAId);
        membershipRepository.saveAndFlush(first);

        OrganizationMembership duplicate = membership("alias@example.com", orgA1Id);
        duplicate.setUserId(userAId);
        assertThatThrownBy(() -> membershipRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameUserMayBelongToDifferentOrganizations() {
        OrganizationMembership first = membership("alice@example.com", orgA1Id);
        first.setUserId(userAId);
        membershipRepository.save(first);

        OrganizationMembership second = membership("alice@example.com", orgA2Id);
        second.setUserId(userAId);
        membershipRepository.saveAndFlush(second);

        assertThat(membershipRepository.findByTenantIdAndUserId(tenantAId, userAId))
                .hasSize(2);
    }

    @Test
    void duplicateLinkPrecheckExcludesCurrentMembership() {
        OrganizationMembership first = membership("alice@example.com", orgA1Id);
        first.setUserId(userAId);
        first = membershipRepository.saveAndFlush(first);

        assertThat(membershipRepository.existsOtherByTenantIdAndOrganizationIdAndUserId(
                tenantAId, orgA1Id, userAId, first.getId())).isFalse();

        OrganizationMembership second = membership("other@example.com", orgA1Id);
        second = membershipRepository.saveAndFlush(second);
        assertThat(membershipRepository.existsOtherByTenantIdAndOrganizationIdAndUserId(
                tenantAId, orgA1Id, userAId, second.getId())).isTrue();
    }

    private OrganizationMembership membership(String email, UUID organizationId) {
        return new OrganizationMembership(
                tenantAId, organizationId, email, "Member", MembershipStatus.INVITED);
    }
}
