package com.sanad.platform.workshop.repository;

import com.sanad.platform.workshop.domain.Workshop;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkshopRepository extends JpaRepository<Workshop, UUID> {

    @Query(value = "SELECT w FROM Workshop w WHERE w.tenant.id = :tenantId",
            countQuery = "SELECT COUNT(w) FROM Workshop w WHERE w.tenant.id = :tenantId")
    Page<Workshop> findByTenantId(@Param("tenantId") UUID tenantId, Pageable pageable);

    @Query("SELECT w FROM Workshop w WHERE w.tenant.id = :tenantId AND w.id = :id")
    Optional<Workshop> findByTenantIdAndId(@Param("tenantId") UUID tenantId,
                                           @Param("id") UUID id);

    @Query("SELECT CASE WHEN COUNT(w) > 0 THEN TRUE ELSE FALSE END FROM Workshop w "
            + "WHERE w.tenant.id = :tenantId AND w.code = :code")
    boolean existsByTenantIdAndCode(@Param("tenantId") UUID tenantId,
                                    @Param("code") String code);
}
