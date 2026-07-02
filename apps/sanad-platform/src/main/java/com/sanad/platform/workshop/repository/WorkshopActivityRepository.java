package com.sanad.platform.workshop.repository;

import com.sanad.platform.workshop.domain.WorkshopActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkshopActivityRepository extends JpaRepository<WorkshopActivity, UUID> {

    @Query("SELECT a FROM WorkshopActivity a WHERE a.tenant.id = :tenantId "
            + "AND a.workshop.id = :workshopId ORDER BY a.createdAt ASC")
    List<WorkshopActivity> findByTenantIdAndWorkshopId(
            @Param("tenantId") UUID tenantId, @Param("workshopId") UUID workshopId);

    @Query("SELECT a FROM WorkshopActivity a WHERE a.tenant.id = :tenantId "
            + "AND a.workshop.id = :workshopId AND a.workItem.id = :workItemId AND a.id = :id")
    Optional<WorkshopActivity> findByTenantIdAndWorkshopIdAndWorkItemIdAndId(
            @Param("tenantId") UUID tenantId,
            @Param("workshopId") UUID workshopId,
            @Param("workItemId") UUID workItemId,
            @Param("id") UUID id);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN TRUE ELSE FALSE END FROM WorkshopActivity a "
            + "WHERE a.tenant.id = :tenantId AND a.workItem.id = :workItemId "
            + "AND a.type = com.sanad.platform.workshop.domain.WorkshopActivity$Type.CHECKLIST "
            + "AND a.completed = false")
    boolean existsIncompleteChecklistByTenantIdAndWorkItemId(
            @Param("tenantId") UUID tenantId, @Param("workItemId") UUID workItemId);
}
