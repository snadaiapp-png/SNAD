package com.sanad.platform.access.capability;

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
}
