package com.sanad.platform.workshop.repository;

import com.sanad.platform.workshop.domain.WorkshopAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WorkshopAssignmentRepository extends JpaRepository<WorkshopAssignment, UUID> {

    @Query("SELECT a FROM WorkshopAssignment a WHERE a.tenant.id = :tenantId "
            + "AND a.workshop.id = :workshopId ORDER BY a.createdAt ASC")
    List<WorkshopAssignment> findByTenantIdAndWorkshopId(
            @Param("tenantId") UUID tenantId, @Param("workshopId") UUID workshopId);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN TRUE ELSE FALSE END FROM WorkshopAssignment a "
            + "WHERE a.tenant.id = :tenantId AND a.workItem.id = :workItemId "
            + "AND a.userId = :userId AND a.role = :role")
    boolean existsByTenantIdAndWorkItemIdAndUserIdAndRole(
            @Param("tenantId") UUID tenantId,
            @Param("workItemId") UUID workItemId,
            @Param("userId") UUID userId,
            @Param("role") WorkshopAssignment.Role role);
}
