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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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

    @BeforeEach
    void setUp() {
        Tenant tenantA = tenantRepository.save(
                new Tenant("Tenant A", "link-a-" + UUID.randomUUID(), TenantStatus.ACTIVE));
        Tenant tenantB = tenantRepository.save(
                new Tenant("Tenant B", "link-b-" + UUID.randomUUID(), TenantStatus.ACTIVE));
        tenantAId = tenantA.getId();
        tenantBId = tenantB.getId();

        orgA1Id = organizationRepository.save(new Organization(
                tenantA, "Org A1", "desc", OrganizationStatus.ACTIVE)).getId();
        orgA2Id = organizationRepository.save(new Organization(
                tenantA, "Org A2", "desc", OrganizationStatus.ACTIVE)).getId();

        userAId = userRepository.save(new User(
                tenantAId, "alice@example.com", "Alice", UserStatus.ACTIVE)).getId();
        userBId = userRepository.save(new User(
                tenantBId, "alice@example.com", "Alice B", UserStatus.ACTIVE)).getId();
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
