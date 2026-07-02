package com.sanad.platform.workshop.repository;

import com.sanad.platform.workshop.domain.WorkshopWorkItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkshopWorkItemRepository extends JpaRepository<WorkshopWorkItem, UUID> {

    @Query("SELECT i FROM WorkshopWorkItem i WHERE i.tenant.id = :tenantId "
            + "AND i.workshop.id = :workshopId ORDER BY i.sequenceNo ASC, i.createdAt ASC")
    List<WorkshopWorkItem> findByTenantIdAndWorkshopIdOrderBySequenceNoAscCreatedAtAsc(
            @Param("tenantId") UUID tenantId, @Param("workshopId") UUID workshopId);

    @Query("SELECT i FROM WorkshopWorkItem i WHERE i.tenant.id = :tenantId "
            + "AND i.workshop.id = :workshopId AND i.id = :id")
    Optional<WorkshopWorkItem> findByTenantIdAndWorkshopIdAndId(
            @Param("tenantId") UUID tenantId,
            @Param("workshopId") UUID workshopId,
            @Param("id") UUID id);

    @Query("SELECT CASE WHEN COUNT(i) > 0 THEN TRUE ELSE FALSE END FROM WorkshopWorkItem i "
            + "WHERE i.tenant.id = :tenantId AND i.workshop.id = :workshopId AND i.code = :code")
    boolean existsByTenantIdAndWorkshopIdAndCode(
            @Param("tenantId") UUID tenantId,
            @Param("workshopId") UUID workshopId,
            @Param("code") String code);

    @Query("SELECT COUNT(i) FROM WorkshopWorkItem i WHERE i.tenant.id = :tenantId "
            + "AND i.workshop.id = :workshopId AND i.status NOT IN :terminalStatuses")
    long countOpenByTenantIdAndWorkshopId(
            @Param("tenantId") UUID tenantId,
            @Param("workshopId") UUID workshopId,
            @Param("terminalStatuses") Collection<WorkshopWorkItem.Status> terminalStatuses);
}
