package com.sanad.platform.organization.membership.service;

import com.sanad.platform.organization.membership.domain.MembershipStatus;
import com.sanad.platform.organization.membership.domain.OrganizationMembership;
import com.sanad.platform.organization.membership.dto.OrganizationMembershipResponse;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationMembershipUserUnlinkAndLookupServiceTest {

    @Mock private OrganizationMembershipRepository membershipRepository;
    @Mock private UserRepository userRepository;
    @Mock private OrganizationMembershipMapper membershipMapper;

    private OrganizationMembershipUserLinkService service;
    private UUID tenantId;
    private UUID organizationId;
    private UUID membershipId;
    private UUID userId;
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
        membership = new OrganizationMembership(
                tenantId, organizationId, "alice@example.com", "Alice", MembershipStatus.INVITED);
        user = new User(tenantId, "alice@example.com", "Alice", UserStatus.ACTIVE);
        response = new OrganizationMembershipResponse(
                membershipId, tenantId, organizationId, userId,
                "alice@example.com", "Alice", MembershipStatus.INVITED,
                Instant.now(), Instant.now());
    }

    @Test
    void unlinkUserClearsAndPersistsLink() {
        membership.setUserId(userId);
        foundMembership();
        when(membershipRepository.save(membership)).thenReturn(membership);
        when(membershipMapper.toResponse(membership)).thenReturn(response);

        service.unlinkUser(tenantId, organizationId, membershipId);

        assertThat(membership.getUserId()).isNull();
        verify(membershipRepository).save(membership);
    }

    @Test
    void unlinkUserIsIdempotentWhenAlreadyUnlinked() {
        foundMembership();
        when(membershipMapper.toResponse(membership)).thenReturn(response);

        service.unlinkUser(tenantId, organizationId, membershipId);

        verify(membershipRepository, never()).save(any());
    }

    @Test
    void listMembershipsByUserReturnsTenantScopedResults() {
        membership.setUserId(userId);
        when(userRepository.findByTenantIdAndId(tenantId, userId)).thenReturn(Optional.of(user));
        when(membershipRepository.findByTenantIdAndUserId(tenantId, userId))
                .thenReturn(List.of(membership));
        when(membershipMapper.toResponse(membership)).thenReturn(response);

        assertThat(service.listMembershipsByUser(tenantId, userId))
                .containsExactly(response);
    }

    @Test
    void listMembershipsByUserRejectsUnknownTenantScopedUser() {
        when(userRepository.findByTenantIdAndId(tenantId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listMembershipsByUser(tenantId, userId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("User not found");
        verify(membershipRepository, never()).findByTenantIdAndUserId(any(), any());
    }

    private void foundMembership() {
        when(membershipRepository.findByTenantIdAndOrganizationIdAndId(
                tenantId, organizationId, membershipId)).thenReturn(Optional.of(membership));
    }
}
