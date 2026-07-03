package com.sanad.platform.organization.membership.repository;

import com.sanad.platform.organization.membership.domain.OrganizationMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrganizationMembershipRepository extends JpaRepository<OrganizationMembership, UUID> {

    @Query("SELECT m FROM OrganizationMembership m WHERE m.tenantId = :tenantId")
    List<OrganizationMembership> findByTenantId(@Param("tenantId") UUID tenantId);

    @Query("SELECT m FROM OrganizationMembership m "
            + "WHERE m.tenantId = :tenantId AND m.organizationId = :organizationId")
    List<OrganizationMembership> findByTenantIdAndOrganizationId(
            @Param("tenantId") UUID tenantId,
            @Param("organizationId") UUID organizationId);

    @Query("SELECT m FROM OrganizationMembership m "
            + "WHERE m.tenantId = :tenantId "
            + "AND m.organizationId = :organizationId "
            + "AND m.id = :id")
    Optional<OrganizationMembership> findByTenantIdAndOrganizationIdAndId(
            @Param("tenantId") UUID tenantId,
            @Param("organizationId") UUID organizationId,
            @Param("id") UUID id);

    @Query("SELECT m FROM OrganizationMembership m "
            + "WHERE m.tenantId = :tenantId AND m.userId = :userId")
    List<OrganizationMembership> findByTenantIdAndUserId(
            @Param("tenantId") UUID tenantId,
            @Param("userId") UUID userId);

    @Query("SELECT m FROM OrganizationMembership m "
            + "WHERE m.tenantId = :tenantId "
            + "AND m.organizationId = :organizationId "
            + "AND m.userId = :userId")
    Optional<OrganizationMembership> findByTenantIdAndOrganizationIdAndUserId(
            @Param("tenantId") UUID tenantId,
            @Param("organizationId") UUID organizationId,
            @Param("userId") UUID userId);

    @Query("SELECT CASE WHEN COUNT(m) > 0 THEN TRUE ELSE FALSE END "
            + "FROM OrganizationMembership m "
            + "WHERE m.tenantId = :tenantId "
            + "AND m.organizationId = :organizationId "
            + "AND m.userId = :userId "
            + "AND m.id <> :membershipId")
    boolean existsOtherByTenantIdAndOrganizationIdAndUserId(
            @Param("tenantId") UUID tenantId,
            @Param("organizationId") UUID organizationId,
            @Param("userId") UUID userId,
            @Param("membershipId") UUID membershipId);

    @Query("SELECT CASE WHEN COUNT(m) > 0 THEN TRUE ELSE FALSE END "
            + "FROM OrganizationMembership m "
            + "WHERE m.tenantId = :tenantId "
            + "AND m.organizationId = :organizationId "
            + "AND m.email = :email")
    boolean existsByTenantIdAndOrganizationIdAndEmail(
            @Param("tenantId") UUID tenantId,
            @Param("organizationId") UUID organizationId,
            @Param("email") String email);
}
