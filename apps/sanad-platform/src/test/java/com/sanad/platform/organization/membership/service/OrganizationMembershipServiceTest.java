package com.sanad.platform.organization.membership.service;

import com.sanad.platform.organization.domain.Organization;
import com.sanad.platform.organization.domain.OrganizationStatus;
import com.sanad.platform.organization.membership.domain.MembershipStatus;
import com.sanad.platform.organization.membership.domain.OrganizationMembership;
import com.sanad.platform.organization.membership.dto.InviteOrganizationMemberRequest;
import com.sanad.platform.organization.membership.dto.OrganizationMembershipResponse;
import com.sanad.platform.organization.membership.exception.OrganizationMembershipAlreadyExistsException;
import com.sanad.platform.organization.membership.exception.OrganizationMembershipNotFoundException;
import com.sanad.platform.organization.membership.mapper.OrganizationMembershipMapper;
import com.sanad.platform.organization.membership.repository.OrganizationMembershipRepository;
import com.sanad.platform.organization.repository.OrganizationRepository;
import com.sanad.platform.tenant.domain.Tenant;
import com.sanad.platform.tenant.domain.TenantStatus;
import com.sanad.platform.tenant.repository.TenantRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for {@link OrganizationMembershipService} using Mockito.
 *
 * <p>All repositories and the mapper are mocked; the service's business
 * logic is tested in isolation without touching a database.</p>
 */
@ExtendWith(MockitoExtension.class)
class OrganizationMembershipServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private OrganizationMembershipRepository membershipRepository;

    @Mock
    private OrganizationMembershipMapper membershipMapper;

    @InjectMocks
    private OrganizationMembershipService service;

    // Common fixtures
    private UUID tenantId;
    private UUID organizationId;
    private UUID membershipId;
    private Tenant tenant;
    private Organization organization;
    private OrganizationMembership savedMembership;
    private OrganizationMembershipResponse expectedResponse;

    @BeforeEach
    void setUp() {
        tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        organizationId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        membershipId = UUID.fromString("33333333-3333-3333-3333-333333333333");

        tenant = new Tenant("Acme Corp", "acme", TenantStatus.ACTIVE);
        reflectSet(tenant, "id", tenantId);

        organization = new Organization(tenant, "Acme Riyadh", "desc", OrganizationStatus.ACTIVE);
        reflectSet(organization, "id", organizationId);

        savedMembership = new OrganizationMembership(
                tenantId, organizationId, "alice@example.com", "Alice", MembershipStatus.INVITED);
        reflectSet(savedMembership, "id", membershipId);
        Instant now = Instant.now();
        reflectSet(savedMembership, "createdAt", now);
        reflectSet(savedMembership, "updatedAt", now);

        expectedResponse = new OrganizationMembershipResponse(
                membershipId, tenantId, organizationId, "alice@example.com", "Alice",
                MembershipStatus.INVITED, now, now);
    }

    // ============================================================
    // inviteMember
    // ============================================================

    @Test
    @DisplayName("inviteMember: success - creates membership with status INVITED")
    void inviteMember_success_createsInvitedMembership() {
        InviteOrganizationMemberRequest request = new InviteOrganizationMemberRequest(
                tenantId, organizationId, "alice@example.com", "Alice");

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(organizationRepository.findByTenantIdAndId(tenantId, organizationId))
                .thenReturn(Optional.of(organization));
        when(membershipRepository.existsByTenantIdAndOrganizationIdAndEmail(
                tenantId, organizationId, "alice@example.com")).thenReturn(false);
        when(membershipRepository.save(any(OrganizationMembership.class))).thenReturn(savedMembership);
        when(membershipMapper.toResponse(savedMembership)).thenReturn(expectedResponse);

        OrganizationMembershipResponse result = service.inviteMember(request);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(MembershipStatus.INVITED);
        assertThat(result.getEmail()).isEqualTo("alice@example.com");

        // Verify the membership passed to save() has INVITED status
        ArgumentCaptor<OrganizationMembership> captor =
                ArgumentCaptor.forClass(OrganizationMembership.class);
        verify(membershipRepository).save(captor.capture());
        OrganizationMembership captured = captor.getValue();
        assertThat(captured.getStatus()).isEqualTo(MembershipStatus.INVITED);
        assertThat(captured.getTenantId()).isEqualTo(tenantId);
        assertThat(captured.getOrganizationId()).isEqualTo(organizationId);
        assertThat(captured.getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    @DisplayName("inviteMember: tenant not found -> EntityNotFoundException")
    void inviteMember_tenantNotFound_throwsException() {
        InviteOrganizationMemberRequest request = new InviteOrganizationMemberRequest(
                tenantId, organizationId, "alice@example.com", "Alice");

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.inviteMember(request))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Tenant not found with id");

        verify(organizationRepository, never()).findByTenantIdAndId(any(), any());
        verify(membershipRepository, never()).save(any());
    }

    @Test
    @DisplayName("inviteMember: organization not found -> EntityNotFoundException")
    void inviteMember_organizationNotFound_throwsException() {
        InviteOrganizationMemberRequest request = new InviteOrganizationMemberRequest(
                tenantId, organizationId, "alice@example.com", "Alice");

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(organizationRepository.findByTenantIdAndId(tenantId, organizationId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.inviteMember(request))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Organization not found with id");

        verify(membershipRepository, never()).save(any());
    }

    @Test
    @DisplayName("inviteMember: duplicate email -> OrganizationMembershipAlreadyExistsException")
    void inviteMember_duplicateEmail_throwsException() {
        InviteOrganizationMemberRequest request = new InviteOrganizationMemberRequest(
                tenantId, organizationId, "alice@example.com", "Alice");

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(organizationRepository.findByTenantIdAndId(tenantId, organizationId))
                .thenReturn(Optional.of(organization));
        when(membershipRepository.existsByTenantIdAndOrganizationIdAndEmail(
                tenantId, organizationId, "alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.inviteMember(request))
                .isInstanceOf(OrganizationMembershipAlreadyExistsException.class)
                .hasMessage("Organization membership already exists for this email");

        verify(membershipRepository, never()).save(any());
    }

    @Test
    @DisplayName("inviteMember: normalizes email to lowercase before dedup check and save")
    void inviteMember_normalizesEmailToLowerCase() {
        InviteOrganizationMemberRequest request = new InviteOrganizationMemberRequest(
                tenantId, organizationId, "Alice.Black@Example.COM", "Alice");

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(organizationRepository.findByTenantIdAndId(tenantId, organizationId))
                .thenReturn(Optional.of(organization));
        when(membershipRepository.existsByTenantIdAndOrganizationIdAndEmail(
                tenantId, organizationId, "alice.black@example.com")).thenReturn(false);
        when(membershipRepository.save(any(OrganizationMembership.class))).thenReturn(savedMembership);
        when(membershipMapper.toResponse(savedMembership)).thenReturn(expectedResponse);

        service.inviteMember(request);

        // Verify the dedup check used the lowercased email
        verify(membershipRepository).existsByTenantIdAndOrganizationIdAndEmail(
                tenantId, organizationId, "alice.black@example.com");

        // Verify the membership passed to save() also has the lowercased email
        ArgumentCaptor<OrganizationMembership> captor =
                ArgumentCaptor.forClass(OrganizationMembership.class);
        verify(membershipRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("alice.black@example.com");
    }

    // ============================================================
    // getMembership
    // ============================================================

    @Test
    @DisplayName("getMembership: success - returns response")
    void getMembership_success_returnsResponse() {
        when(membershipRepository.findByTenantIdAndOrganizationIdAndId(
                tenantId, organizationId, membershipId))
                .thenReturn(Optional.of(savedMembership));
        when(membershipMapper.toResponse(savedMembership)).thenReturn(expectedResponse);

        OrganizationMembershipResponse result =
                service.getMembership(tenantId, organizationId, membershipId);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(membershipId);
        verify(membershipRepository).findByTenantIdAndOrganizationIdAndId(
                tenantId, organizationId, membershipId);
    }

    @Test
    @DisplayName("getMembership: not found -> OrganizationMembershipNotFoundException")
    void getMembership_notFound_throwsException() {
        when(membershipRepository.findByTenantIdAndOrganizationIdAndId(
                tenantId, organizationId, membershipId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMembership(tenantId, organizationId, membershipId))
                .isInstanceOf(OrganizationMembershipNotFoundException.class)
                .hasMessage("Organization membership not found");
    }

    // ============================================================
    // listMemberships
    // ============================================================

    @Test
    @DisplayName("listMemberships: success - returns list of responses")
    void listMemberships_success_returnsList() {
        OrganizationMembership second = new OrganizationMembership(
                tenantId, organizationId, "bob@example.com", "Bob", MembershipStatus.ACTIVE);
        reflectSet(second, "id", UUID.fromString("44444444-4444-4444-4444-444444444444"));

        when(organizationRepository.findByTenantIdAndId(tenantId, organizationId))
                .thenReturn(Optional.of(organization));
        when(membershipRepository.findByTenantIdAndOrganizationId(tenantId, organizationId))
                .thenReturn(List.of(savedMembership, second));
        when(membershipMapper.toResponse(savedMembership)).thenReturn(expectedResponse);
        when(membershipMapper.toResponse(second)).thenReturn(
                new OrganizationMembershipResponse(second.getId(), tenantId, organizationId,
                        "bob@example.com", "Bob", MembershipStatus.ACTIVE, Instant.now(), Instant.now()));

        List<OrganizationMembershipResponse> result =
                service.listMemberships(tenantId, organizationId);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(OrganizationMembershipResponse::getEmail)
                .containsExactlyInAnyOrder("alice@example.com", "bob@example.com");
    }

    // ============================================================
    // activateMembership
    // ============================================================

    @Test
    @DisplayName("activateMembership: success - sets status to ACTIVE")
    void activateMembership_success_setsActive() {
        when(membershipRepository.findByTenantIdAndOrganizationIdAndId(
                tenantId, organizationId, membershipId))
                .thenReturn(Optional.of(savedMembership));
        when(membershipRepository.save(savedMembership)).thenReturn(savedMembership);
        when(membershipMapper.toResponse(savedMembership)).thenReturn(
                new OrganizationMembershipResponse(membershipId, tenantId, organizationId,
                        "alice@example.com", "Alice", MembershipStatus.ACTIVE, Instant.now(), Instant.now()));

        OrganizationMembershipResponse result =
                service.activateMembership(tenantId, organizationId, membershipId);

        assertThat(result.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(savedMembership.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
        verify(membershipRepository).save(savedMembership);
    }

    // ============================================================
    // deactivateMembership
    // ============================================================

    @Test
    @DisplayName("deactivateMembership: success - sets status to INACTIVE")
    void deactivateMembership_success_setsInactive() {
        when(membershipRepository.findByTenantIdAndOrganizationIdAndId(
                tenantId, organizationId, membershipId))
                .thenReturn(Optional.of(savedMembership));
        when(membershipRepository.save(savedMembership)).thenReturn(savedMembership);
        when(membershipMapper.toResponse(savedMembership)).thenReturn(
                new OrganizationMembershipResponse(membershipId, tenantId, organizationId,
                        "alice@example.com", "Alice", MembershipStatus.INACTIVE, Instant.now(), Instant.now()));

        OrganizationMembershipResponse result =
                service.deactivateMembership(tenantId, organizationId, membershipId);

        assertThat(result.getStatus()).isEqualTo(MembershipStatus.INACTIVE);
        assertThat(savedMembership.getStatus()).isEqualTo(MembershipStatus.INACTIVE);
        verify(membershipRepository).save(savedMembership);
    }

    // ============================================================
    // removeMembership
    // ============================================================

    @Test
    @DisplayName("removeMembership: success - sets status to REMOVED (soft delete)")
    void removeMembership_success_setsRemoved() {
        when(membershipRepository.findByTenantIdAndOrganizationIdAndId(
                tenantId, organizationId, membershipId))
                .thenReturn(Optional.of(savedMembership));
        when(membershipRepository.save(savedMembership)).thenReturn(savedMembership);
        when(membershipMapper.toResponse(savedMembership)).thenReturn(
                new OrganizationMembershipResponse(membershipId, tenantId, organizationId,
                        "alice@example.com", "Alice", MembershipStatus.REMOVED, Instant.now(), Instant.now()));

        OrganizationMembershipResponse result =
                service.removeMembership(tenantId, organizationId, membershipId);

        assertThat(result.getStatus()).isEqualTo(MembershipStatus.REMOVED);
        assertThat(savedMembership.getStatus()).isEqualTo(MembershipStatus.REMOVED);
        verify(membershipRepository).save(savedMembership);
    }

    @Test
    @DisplayName("removeMembership: does NOT call delete() (soft delete only)")
    void removeMembership_doesNotCallDelete() {
        when(membershipRepository.findByTenantIdAndOrganizationIdAndId(
                tenantId, organizationId, membershipId))
                .thenReturn(Optional.of(savedMembership));
        when(membershipRepository.save(savedMembership)).thenReturn(savedMembership);
        when(membershipMapper.toResponse(savedMembership)).thenReturn(expectedResponse);

        service.removeMembership(tenantId, organizationId, membershipId);

        // Verify save() WAS called (status update)
        verify(membershipRepository).save(savedMembership);

        // Verify NO delete methods were ever called
        verify(membershipRepository, never()).delete(any());
        verify(membershipRepository, never()).deleteById(any());
        verify(membershipRepository, never()).deleteAll();
        verify(membershipRepository, never()).deleteAll(any());
    }

    // ============================================================
    // Helper
    // ============================================================

    private static void reflectSet(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
