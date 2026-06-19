package com.sanad.platform.organization.membership.service;

import com.sanad.platform.organization.membership.domain.MembershipStatus;
import com.sanad.platform.organization.membership.domain.OrganizationMembership;
import com.sanad.platform.organization.membership.dto.OrganizationMembershipResponse;
import com.sanad.platform.organization.membership.exception.OrganizationMembershipNotFoundException;
import com.sanad.platform.organization.membership.exception.OrganizationMembershipUserLinkConflictException;
import com.sanad.platform.organization.membership.mapper.OrganizationMembershipMapper;
import com.sanad.platform.organization.membership.repository.OrganizationMembershipRepository;
import com.sanad.platform.user.domain.User;
import com.sanad.platform.user.domain.UserStatus;
import com.sanad.platform.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrganizationMembershipUserLinkServiceTest {

    @Mock private OrganizationMembershipRepository membershipRepository;
    @Mock private UserRepository userRepository;
    @Mock private OrganizationMembershipMapper membershipMapper;

    private OrganizationMembershipUserLinkService service;
    private UUID tenantId;
    private UUID organizationId;
    private UUID membershipId;
    private UUID userId;
    private UUID otherUserId;
    private OrganizationMembership membership;
    private User user;
    private OrganizationMembershipResponse response;

    @BeforeEach
    void setUp() {
        service = new OrganizationMembershipUserLinkService(
                membershipRepository, userRepository, membershipMapper);
        tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        organizationId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        membershipId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        userId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        otherUserId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        membership = new OrganizationMembership(
                tenantId, organizationId, "alice@example.com", "Alice", MembershipStatus.INVITED);
        user = new User(tenantId, "alice@example.com", "Alice", UserStatus.ACTIVE);
        response = new OrganizationMembershipResponse(
                membershipId, tenantId, organizationId, userId,
                "alice@example.com", "Alice", MembershipStatus.INVITED,
                Instant.now(), Instant.now());
    }

    @Test
    void linkUserPersistsMatchingTenantUser() {
        foundMembership();
        when(userRepository.findByTenantIdAndId(tenantId, userId)).thenReturn(Optional.of(user));
        when(membershipRepository.existsOtherByTenantIdAndOrganizationIdAndUserId(
                tenantId, organizationId, userId, membershipId)).thenReturn(false);
        when(membershipRepository.save(membership)).thenReturn(membership);
        when(membershipMapper.toResponse(membership)).thenReturn(response);

        assertThat(service.linkUser(tenantId, organizationId, membershipId, userId))
                .isSameAs(response);
        assertThat(membership.getUserId()).isEqualTo(userId);
        verify(membershipRepository).save(membership);
    }

    @Test
    void linkUserIsIdempotentForSameUser() {
        membership.setUserId(userId);
        foundMembership();
        when(userRepository.findByTenantIdAndId(tenantId, userId)).thenReturn(Optional.of(user));
        when(membershipMapper.toResponse(membership)).thenReturn(response);

        assertThat(service.linkUser(tenantId, organizationId, membershipId, userId))
                .isSameAs(response);
        verify(membershipRepository, never()).save(any());
    }

    @Test
    void linkUserRejectsDifferentExistingUser() {
        membership.setUserId(otherUserId);
        foundMembership();
        when(userRepository.findByTenantIdAndId(tenantId, userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.linkUser(
                tenantId, organizationId, membershipId, userId))
                .isInstanceOf(OrganizationMembershipUserLinkConflictException.class)
                .hasMessageContaining("already linked");
    }

    @Test
    void linkUserRejectsEmailMismatch() {
        foundMembership();
        when(userRepository.findByTenantIdAndId(tenantId, userId)).thenReturn(Optional.of(
                new User(tenantId, "bob@example.com", "Bob", UserStatus.ACTIVE)));

        assertThatThrownBy(() -> service.linkUser(
                tenantId, organizationId, membershipId, userId))
                .isInstanceOf(OrganizationMembershipUserLinkConflictException.class)
                .hasMessageContaining("email must match");
    }

    @Test
    void linkUserRejectsRemovedMembership() {
        membership.setStatus(MembershipStatus.REMOVED);
        foundMembership();
        when(userRepository.findByTenantIdAndId(tenantId, userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.linkUser(
                tenantId, organizationId, membershipId, userId))
                .isInstanceOf(OrganizationMembershipUserLinkConflictException.class)
                .hasMessageContaining("Removed membership");
    }

    @Test
    void linkUserRejectsDuplicateUserMembershipInOrganization() {
        foundMembership();
        when(userRepository.findByTenantIdAndId(tenantId, userId)).thenReturn(Optional.of(user));
        when(membershipRepository.existsOtherByTenantIdAndOrganizationIdAndUserId(
                tenantId, organizationId, userId, membershipId)).thenReturn(true);

        assertThatThrownBy(() -> service.linkUser(
                tenantId, organizationId, membershipId, userId))
                .isInstanceOf(OrganizationMembershipUserLinkConflictException.class)
                .hasMessageContaining("another membership");
    }

    @Test
    void linkUserRejectsUnknownUserWithoutCrossTenantDisclosure() {
        foundMembership();
        when(userRepository.findByTenantIdAndId(tenantId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.linkUser(
                tenantId, organizationId, membershipId, userId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("User not found");
    }

    @Test
    void linkUserRejectsUnknownMembership() {
        when(membershipRepository.findByTenantIdAndOrganizationIdAndId(
                tenantId, organizationId, membershipId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.linkUser(
                tenantId, organizationId, membershipId, userId))
                .isInstanceOf(OrganizationMembershipNotFoundException.class);
        verifyNoInteractions(userRepository);
    }

    private void foundMembership() {
        when(membershipRepository.findByTenantIdAndOrganizationIdAndId(
                tenantId, organizationId, membershipId)).thenReturn(Optional.of(membership));
    }
}
