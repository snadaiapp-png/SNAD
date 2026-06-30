package com.sanad.platform.organization.membership.service;

import com.sanad.platform.organization.membership.domain.MembershipStatus;
import com.sanad.platform.organization.membership.domain.OrganizationMembership;
import com.sanad.platform.organization.membership.dto.OrganizationMembershipResponse;
import com.sanad.platform.organization.membership.exception.OrganizationMembershipNotFoundException;
import com.sanad.platform.organization.membership.exception.OrganizationMembershipUserLinkConflictException;
import com.sanad.platform.organization.membership.mapper.OrganizationMembershipMapper;
import com.sanad.platform.organization.membership.repository.OrganizationMembershipRepository;
import com.sanad.platform.user.domain.User;
import com.sanad.platform.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class OrganizationMembershipUserLinkService {

    private final OrganizationMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final OrganizationMembershipMapper membershipMapper;

    public OrganizationMembershipUserLinkService(
            OrganizationMembershipRepository membershipRepository,
            UserRepository userRepository,
            OrganizationMembershipMapper membershipMapper) {
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.membershipMapper = membershipMapper;
    }

    @Transactional
    public OrganizationMembershipResponse linkUser(
            UUID tenantId, UUID organizationId, UUID membershipId, UUID userId) {
        requireIds(tenantId, organizationId, membershipId);
        Objects.requireNonNull(userId, "userId must not be null");

        OrganizationMembership membership = loadMembership(tenantId, organizationId, membershipId);
        User user = userRepository.findByTenantIdAndId(tenantId, userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        if (membership.getStatus() == MembershipStatus.REMOVED) {
            throw conflict(membershipId, userId, "Removed membership cannot be linked to a user");
        }

        if (membership.getUserId() != null) {
            if (membership.getUserId().equals(userId)) {
                return membershipMapper.toResponse(membership);
            }
            throw conflict(membershipId, userId, "Membership is already linked to another user");
        }

        if (!membership.getEmail().equalsIgnoreCase(user.getEmail())) {
            throw conflict(membershipId, userId,
                    "Membership email must match the user email before linking");
        }

        if (membershipRepository.existsOtherByTenantIdAndOrganizationIdAndUserId(
                tenantId, organizationId, userId, membershipId)) {
            throw conflict(membershipId, userId,
                    "User is already linked to another membership in this organization");
        }

        membership.setUserId(userId);
        return membershipMapper.toResponse(membershipRepository.save(membership));
    }

    @Transactional
    public OrganizationMembershipResponse unlinkUser(
            UUID tenantId, UUID organizationId, UUID membershipId) {
        requireIds(tenantId, organizationId, membershipId);
        OrganizationMembership membership = loadMembership(tenantId, organizationId, membershipId);

        if (membership.getUserId() == null) {
            return membershipMapper.toResponse(membership);
        }

        membership.setUserId(null);
        return membershipMapper.toResponse(membershipRepository.save(membership));
    }

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public List<OrganizationMembershipResponse> listMembershipsByUser(
            UUID tenantId, UUID userId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");

        userRepository.findByTenantIdAndId(tenantId, userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        return membershipRepository.findByTenantIdAndUserId(tenantId, userId).stream()
                .map(membershipMapper::toResponse)
                .toList();
    }

    /** Stage 03A — Paginated user-membership query. */
    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public org.springframework.data.domain.Page<OrganizationMembershipResponse> listMembershipsByUser(
            UUID tenantId, UUID userId, org.springframework.data.domain.Pageable pageable) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(pageable, "pageable must not be null");

        userRepository.findByTenantIdAndId(tenantId, userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        return membershipRepository.findByTenantIdAndUserId(tenantId, userId, pageable)
                .map(membershipMapper::toResponse);
    }

    private OrganizationMembership loadMembership(
            UUID tenantId, UUID organizationId, UUID membershipId) {
        return membershipRepository.findByTenantIdAndOrganizationIdAndId(
                        tenantId, organizationId, membershipId)
                .orElseThrow(() -> new OrganizationMembershipNotFoundException(
                        tenantId, organizationId, membershipId));
    }

    private static void requireIds(
            UUID tenantId, UUID organizationId, UUID membershipId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(membershipId, "membershipId must not be null");
    }

    private static OrganizationMembershipUserLinkConflictException conflict(
            UUID membershipId, UUID userId, String message) {
        return new OrganizationMembershipUserLinkConflictException(
                membershipId, userId, message);
    }
}
