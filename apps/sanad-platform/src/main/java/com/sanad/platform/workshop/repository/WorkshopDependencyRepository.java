package com.sanad.platform.workshop.repository;

import com.sanad.platform.workshop.domain.WorkshopDependency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WorkshopDependencyRepository extends JpaRepository<WorkshopDependency, UUID> {

    @Query("SELECT d FROM WorkshopDependency d WHERE d.tenant.id = :tenantId "
            + "AND d.workshop.id = :workshopId ORDER BY d.createdAt ASC")
    List<WorkshopDependency> findByTenantIdAndWorkshopId(
            @Param("tenantId") UUID tenantId, @Param("workshopId") UUID workshopId);

    @Query("SELECT d FROM WorkshopDependency d WHERE d.tenant.id = :tenantId "
            + "AND d.workshop.id = :workshopId AND d.successor.id = :successorId")
    List<WorkshopDependency> findByTenantIdAndWorkshopIdAndSuccessorId(
            @Param("tenantId") UUID tenantId,
            @Param("workshopId") UUID workshopId,
            @Param("successorId") UUID successorId);

    @Query("SELECT CASE WHEN COUNT(d) > 0 THEN TRUE ELSE FALSE END FROM WorkshopDependency d "
            + "WHERE d.tenant.id = :tenantId AND d.predecessor.id = :predecessorId "
            + "AND d.successor.id = :successorId")
    boolean existsByTenantIdAndPredecessorIdAndSuccessorId(
            @Param("tenantId") UUID tenantId,
            @Param("predecessorId") UUID predecessorId,
            @Param("successorId") UUID successorId);
}
