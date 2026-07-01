package com.sanad.platform.organization.membership.service;

import com.sanad.platform.organization.domain.Organization;
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
import com.sanad.platform.tenant.repository.TenantRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Application service for the OrganizationMembership aggregate.
 *
 * <p>This service orchestrates the {@link OrganizationMembershipRepository},
 * {@link OrganizationRepository}, and {@link TenantRepository} ports to
 * implement the membership lifecycle use cases: invite, get, list,
 * activate, deactivate, and remove (soft delete).</p>
 *
 * <h2>Soft Delete Policy</h2>
 * <p>The {@link #removeMembership} use case sets status = REMOVED rather
 * than physically deleting the row. No {@code delete()} / {@code deleteById()}
 * / {@code deleteAll()} calls exist in this service.</p>
 *
 * <h2>Tenant Isolation</h2>
 * <p>Every method is tenant-scoped: callers MUST pass a {@code tenantId}
 * and the repository queries filter on it. Cross-tenant lookups return
 * empty (and therefore throw {@link OrganizationMembershipNotFoundException}),
 * never leaking existence information.</p>
 */
@Service
public class OrganizationMembershipService {

    private final TenantRepository tenantRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final OrganizationMembershipMapper membershipMapper;

    public OrganizationMembershipService(TenantRepository tenantRepository,
                                          OrganizationRepository organizationRepository,
                                          OrganizationMembershipRepository membershipRepository,
                                          OrganizationMembershipMapper membershipMapper) {
        this.tenantRepository = tenantRepository;
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.membershipMapper = membershipMapper;
    }

    // ============================================================
    // Use Case: inviteMember
    // ============================================================

    /**
     * Invite a new member to an organization. The membership is created
     * with status {@link MembershipStatus#INVITED INVITED}.
     *
     * @throws EntityNotFoundException                       if the Tenant or Organization does not exist
     * @throws OrganizationMembershipAlreadyExistsException  if a membership with the same email already exists
     *                                                       for this (tenant, organization) pair
     */
    @Transactional
    public OrganizationMembershipResponse inviteMember(UUID tenantId, InviteOrganizationMemberRequest request) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(request, "InviteOrganizationMemberRequest must not be null");
        Objects.requireNonNull(request.getOrganizationId(), "organizationId must not be null");
        Objects.requireNonNull(request.getEmail(), "email must not be null");

        // --- Validate Tenant exists ---
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Tenant not found with id: " + tenantId));

        // --- Validate Organization exists within the tenant ---
        Organization organization = organizationRepository
                .findByTenantIdAndId(tenantId, request.getOrganizationId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Organization not found with id: " + request.getOrganizationId()
                                + " for tenant: " + tenantId));

        // --- Normalize email to lowercase (defensive; entity also normalizes) ---
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        // --- Prevent duplicate membership ---
        if (membershipRepository.existsByTenantIdAndOrganizationIdAndEmail(
                tenantId, request.getOrganizationId(), normalizedEmail)) {
            throw new OrganizationMembershipAlreadyExistsException(
                    tenantId, request.getOrganizationId(), normalizedEmail);
        }

        // --- Create + persist the membership with status INVITED ---
        OrganizationMembership membership = new OrganizationMembership(
                tenantId,
                request.getOrganizationId(),
                normalizedEmail,
                request.getDisplayName(),
                MembershipStatus.INVITED
        );

        OrganizationMembership saved = membershipRepository.save(membership);
        return membershipMapper.toResponse(saved);
    }

    // ============================================================
    // Use Case: getMembership
    // ============================================================

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public OrganizationMembershipResponse getMembership(UUID tenantId, UUID organizationId, UUID membershipId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(membershipId, "membershipId must not be null");

        return membershipRepository
                .findByTenantIdAndOrganizationIdAndId(tenantId, organizationId, membershipId)
                .map(membershipMapper::toResponse)
                .orElseThrow(() -> new OrganizationMembershipNotFoundException(
                        tenantId, organizationId, membershipId));
    }

    // ============================================================
    // Use Case: listMemberships
    // ============================================================

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public List<OrganizationMembershipResponse> listMemberships(UUID tenantId, UUID organizationId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(organizationId, "organizationId must not be null");

        // Validate organization exists within the tenant
        organizationRepository.findByTenantIdAndId(tenantId, organizationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Organization not found with id: " + organizationId
                                + " for tenant: " + tenantId));

        return membershipRepository.findByTenantIdAndOrganizationId(tenantId, organizationId).stream()
                .map(membershipMapper::toResponse)
                .toList();
    }

    /** Stage 03A — Paginated organization-membership query. */
    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public org.springframework.data.domain.Page<OrganizationMembershipResponse> listMemberships(
            UUID tenantId, UUID organizationId, org.springframework.data.domain.Pageable pageable) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(pageable, "pageable must not be null");

        organizationRepository.findByTenantIdAndId(tenantId, organizationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Organization not found with id: " + organizationId
                                + " for tenant: " + tenantId));

        return membershipRepository
                .findByTenantIdAndOrganizationId(tenantId, organizationId, pageable)
                .map(membershipMapper::toResponse);
    }

    // ============================================================
    // Use Case: activateMembership
    // ============================================================

    @Transactional
    public OrganizationMembershipResponse activateMembership(UUID tenantId, UUID organizationId, UUID membershipId) {
        return setStatus(tenantId, organizationId, membershipId, MembershipStatus.ACTIVE);
    }

    // ============================================================
    // Use Case: deactivateMembership
    // ============================================================

    @Transactional
    public OrganizationMembershipResponse deactivateMembership(UUID tenantId, UUID organizationId, UUID membershipId) {
        return setStatus(tenantId, organizationId, membershipId, MembershipStatus.INACTIVE);
    }

    // ============================================================
    // Use Case: removeMembership (soft delete)
    // ============================================================

    /**
     * Soft-remove a membership by setting status = REMOVED.
     *
     * <p>The row is NOT physically deleted from the database. No
     * {@code delete()} / {@code deleteById()} is ever called. This
     * preserves audit history and referential integrity.</p>
     */
    @Transactional
    public OrganizationMembershipResponse removeMembership(UUID tenantId, UUID organizationId, UUID membershipId) {
        return setStatus(tenantId, organizationId, membershipId, MembershipStatus.REMOVED);
    }

    // ============================================================
    // Internal helper
    // ============================================================

    /**
     * Load a membership (tenant-scoped), set its status, persist, and return
     * the response. Used by activate / deactivate / remove use cases.
     *
     * <p>Throws {@link OrganizationMembershipNotFoundException} if the
     * (tenantId, organizationId, membershipId) tuple does not match any row.</p>
     */
    private OrganizationMembershipResponse setStatus(UUID tenantId, UUID organizationId,
                                                      UUID membershipId, MembershipStatus newStatus) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(organizationId, "organizationId must not be null");
        Objects.requireNonNull(membershipId, "membershipId must not be null");
        Objects.requireNonNull(newStatus, "newStatus must not be null");

        OrganizationMembership membership = membershipRepository
                .findByTenantIdAndOrganizationIdAndId(tenantId, organizationId, membershipId)
                .orElseThrow(() -> new OrganizationMembershipNotFoundException(
                        tenantId, organizationId, membershipId));

        membership.setStatus(newStatus);
        OrganizationMembership saved = membershipRepository.save(membership);
        return membershipMapper.toResponse(saved);
    }
}
