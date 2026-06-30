package com.sanad.platform.access.capability;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccessCapabilityRepository extends JpaRepository<AccessCapability, UUID> {
    List<AccessCapability> findAllByOrderByCodeAsc();
    List<AccessCapability> findByStatusOrderByCodeAsc(CapabilityStatus status);
    Optional<AccessCapability> findByCode(String code);
    boolean existsByCode(String code);

    /**
     * Stage 03A — Paginated capability query. Capabilities are global
     * (not tenant-scoped) so no tenant filter is needed.
     * {@code findAll(Pageable)} is inherited from JpaRepository; we
     * declare it here only to make the intent explicit.
     */
    Page<AccessCapability> findAll(Pageable pageable);
}
