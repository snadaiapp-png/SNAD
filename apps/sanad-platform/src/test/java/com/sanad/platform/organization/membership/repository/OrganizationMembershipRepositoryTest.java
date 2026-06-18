package com.sanad.platform.organization.membership.repository;

import com.sanad.platform.organization.domain.Organization;
import com.sanad.platform.organization.domain.OrganizationStatus;
import com.sanad.platform.organization.repository.OrganizationRepository;
import com.sanad.platform.organization.membership.domain.MembershipStatus;
import com.sanad.platform.organization.membership.domain.OrganizationMembership;
import com.sanad.platform.tenant.domain.Tenant;
import com.sanad.platform.tenant.domain.TenantStatus;
import com.sanad.platform.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Repository-level integration tests for {@link OrganizationMembershipRepository}.
 *
 * <p>Uses {@link DataJpaTest @DataJpaTest} to boot a real JPA context
 * against the H2 in-memory database (with Flyway V1+V2+V3 applied). No
 * service layer, no controllers — just the repository and the database.</p>
 *
 * <p>Each test creates the prerequisite Tenant and Organization rows so
 * the membership FK constraints are satisfied.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class OrganizationMembershipRepositoryTest {

    @Autowired
    private OrganizationMembershipRepository membershipRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    private UUID tenantAId;
    private UUID tenantBId;
    private UUID orgA1Id;
    private UUID orgA2Id;
    private UUID orgB1Id;

    @BeforeEach
    void setUp() {
        // Create two tenants
        Tenant tenantA = tenantRepository.save(
                new Tenant("Tenant A", "tenant-a-" + UUID.randomUUID(), TenantStatus.ACTIVE));
        Tenant tenantB = tenantRepository.save(
                new Tenant("Tenant B", "tenant-b-" + UUID.randomUUID(), TenantStatus.ACTIVE));
        tenantAId = tenantA.getId();
        tenantBId = tenantB.getId();

        // Create organizations under each tenant
        Organization orgA1 = new Organization(tenantA, "Org A1", "desc", OrganizationStatus.ACTIVE);
        Organization orgA2 = new Organization(tenantA, "Org A2", "desc", OrganizationStatus.ACTIVE);
        Organization orgB1 = new Organization(tenantB, "Org B1", "desc", OrganizationStatus.ACTIVE);
        orgA1Id = organizationRepository.save(orgA1).getId();
        orgA2Id = organizationRepository.save(orgA2).getId();
        orgB1Id = organizationRepository.save(orgB1).getId();
    }

    // ============================================================
    // TEST 1: save membership
    // ============================================================
    @Test
    @DisplayName("TEST 1: save membership - persists with generated id + timestamps")
    void save_persistsWithIdAndTimestamps() {
        OrganizationMembership m = new OrganizationMembership(
                tenantAId, orgA1Id, "Alice@Example.COM", "Alice", MembershipStatus.ACTIVE);

        OrganizationMembership saved = membershipRepository.save(m);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getEmail()).isEqualTo("alice@example.com"); // normalized lowercase
        assertThat(saved.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
    }

    // ============================================================
    // TEST 2: findByTenantId
    // ============================================================
    @Test
    @DisplayName("TEST 2: findByTenantId returns all memberships for the tenant")
    void findByTenantId_returnsAllForTenant() {
        membershipRepository.save(new OrganizationMembership(
                tenantAId, orgA1Id, "alice@a.com", "Alice", MembershipStatus.ACTIVE));
        membershipRepository.save(new OrganizationMembership(
                tenantAId, orgA2Id, "bob@a.com", "Bob", MembershipStatus.INVITED));
        membershipRepository.save(new OrganizationMembership(
                tenantBId, orgB1Id, "carol@b.com", "Carol", MembershipStatus.ACTIVE));

        List<OrganizationMembership> result = membershipRepository.findByTenantId(tenantAId);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(OrganizationMembership::getEmail)
                .containsExactlyInAnyOrder("alice@a.com", "bob@a.com");
    }

    // ============================================================
    // TEST 3: findByTenantIdAndOrganizationId
    // ============================================================
    @Test
    @DisplayName("TEST 3: findByTenantIdAndOrganizationId returns only org-scoped memberships")
    void findByTenantIdAndOrganizationId_returnsOrgScoped() {
        membershipRepository.save(new OrganizationMembership(
                tenantAId, orgA1Id, "alice@a.com", "Alice", MembershipStatus.ACTIVE));
        membershipRepository.save(new OrganizationMembership(
                tenantAId, orgA1Id, "bob@a.com", "Bob", MembershipStatus.INVITED));
        membershipRepository.save(new OrganizationMembership(
                tenantAId, orgA2Id, "carol@a.com", "Carol", MembershipStatus.ACTIVE));

        List<OrganizationMembership> result =
                membershipRepository.findByTenantIdAndOrganizationId(tenantAId, orgA1Id);

        assertThat(result).hasSize(2);
        assertThat(result).allSatisfy(m -> assertThat(m.getOrganizationId()).isEqualTo(orgA1Id));
    }

    // ============================================================
    // TEST 4: findByTenantIdAndOrganizationIdAndId
    // ============================================================
    @Test
    @DisplayName("TEST 4: findByTenantIdAndOrganizationIdAndId returns the specific membership")
    void findByTenantIdAndOrganizationIdAndId_returnsSpecific() {
        OrganizationMembership saved = membershipRepository.save(new OrganizationMembership(
                tenantAId, orgA1Id, "alice@a.com", "Alice", MembershipStatus.ACTIVE));

        Optional<OrganizationMembership> found =
                membershipRepository.findByTenantIdAndOrganizationIdAndId(
                        tenantAId, orgA1Id, saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("alice@a.com");
    }

    // ============================================================
    // TEST 5: existsByTenantIdAndOrganizationIdAndEmail
    // ============================================================
    @Test
    @DisplayName("TEST 5: existsByTenantIdAndOrganizationIdAndEmail detects duplicates")
    void existsByTenantIdAndOrganizationIdAndEmail_detectsDuplicates() {
        membershipRepository.save(new OrganizationMembership(
                tenantAId, orgA1Id, "Alice@Example.COM", "Alice", MembershipStatus.ACTIVE));

        // Same email lowercased should be detected (normalization)
        assertThat(membershipRepository.existsByTenantIdAndOrganizationIdAndEmail(
                tenantAId, orgA1Id, "alice@example.com")).isTrue();

        // Different email should not be detected
        assertThat(membershipRepository.existsByTenantIdAndOrganizationIdAndEmail(
                tenantAId, orgA1Id, "bob@example.com")).isFalse();
    }

    // ============================================================
    // TEST 6: Tenant isolation — Tenant A membership must not appear in Tenant B queries
    // ============================================================
    @Test
    @DisplayName("TEST 6: Tenant isolation - Tenant A membership NOT in Tenant B queries")
    void tenantIsolation_membershipNotVisibleAcrossTenants() {
        membershipRepository.save(new OrganizationMembership(
                tenantAId, orgA1Id, "alice@a.com", "Alice", MembershipStatus.ACTIVE));

        // findByTenantId: Tenant B sees zero memberships
        assertThat(membershipRepository.findByTenantId(tenantBId)).isEmpty();

        // findByTenantIdAndOrganizationId: cross-tenant lookup returns empty
        assertThat(membershipRepository.findByTenantIdAndOrganizationId(tenantBId, orgA1Id))
                .isEmpty();

        // existsByTenantIdAndOrganizationIdAndEmail: cross-tenant returns false
        assertThat(membershipRepository.existsByTenantIdAndOrganizationIdAndEmail(
                tenantBId, orgA1Id, "alice@a.com")).isFalse();
    }

    // ============================================================
    // TEST 7: Duplicate email inside same organization should fail
    // ============================================================
    @Test
    @DisplayName("TEST 7: Duplicate email inside same organization fails (unique constraint)")
    void duplicateEmail_sameOrganization_fails() {
        membershipRepository.save(new OrganizationMembership(
                tenantAId, orgA1Id, "alice@a.com", "Alice", MembershipStatus.ACTIVE));

        // Attempting to save a second membership with the same (tenant, org, email)
        assertThatThrownBy(() -> membershipRepository.saveAndFlush(new OrganizationMembership(
                tenantAId, orgA1Id, "ALICE@a.com", "Alice Duplicate", MembershipStatus.INVITED)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    // ============================================================
    // TEST 8: Same email in different organizations should be allowed
    // ============================================================
    @Test
    @DisplayName("TEST 8: Same email in different organizations is allowed")
    void sameEmail_differentOrganizations_allowed() {
        membershipRepository.save(new OrganizationMembership(
                tenantAId, orgA1Id, "shared@a.com", "Shared A1", MembershipStatus.ACTIVE));
        membershipRepository.saveAndFlush(new OrganizationMembership(
                tenantAId, orgA2Id, "shared@a.com", "Shared A2", MembershipStatus.ACTIVE));

        // Both should be retrievable from their respective organizations
        List<OrganizationMembership> a1Members =
                membershipRepository.findByTenantIdAndOrganizationId(tenantAId, orgA1Id);
        List<OrganizationMembership> a2Members =
                membershipRepository.findByTenantIdAndOrganizationId(tenantAId, orgA2Id);

        assertThat(a1Members).hasSize(1);
        assertThat(a2Members).hasSize(1);
        assertThat(a1Members.get(0).getEmail()).isEqualTo("shared@a.com");
        assertThat(a2Members.get(0).getEmail()).isEqualTo("shared@a.com");
    }

    // ============================================================
    // TEST 9: Email normalization is case-insensitive
    // ============================================================
    @Test
    @DisplayName("TEST 9: Email is normalized to lowercase on save")
    void emailNormalizedToLowerCase() {
        OrganizationMembership saved = membershipRepository.save(new OrganizationMembership(
                tenantAId, orgA1Id, "Alice.Black@Example.COM", "Alice", MembershipStatus.ACTIVE));

        assertThat(saved.getEmail()).isEqualTo("alice.black@example.com");
    }

    // ============================================================
    // TEST 10: findByTenantIdAndOrganizationIdAndId cross-tenant returns empty
    // ============================================================
    @Test
    @DisplayName("TEST 10: findByTenantIdAndOrganizationIdAndId cross-tenant returns empty")
    void findById_crossTenant_returnsEmpty() {
        OrganizationMembership saved = membershipRepository.save(new OrganizationMembership(
                tenantAId, orgA1Id, "alice@a.com", "Alice", MembershipStatus.ACTIVE));

        // Lookup with wrong tenant returns empty (not the actual membership)
        Optional<OrganizationMembership> found =
                membershipRepository.findByTenantIdAndOrganizationIdAndId(
                        tenantBId, orgA1Id, saved.getId());

        assertThat(found).isEmpty();
    }
}
