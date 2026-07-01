package com.sanad.platform.crm.contact.repository;

import com.sanad.platform.crm.contact.domain.CrmContact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CrmContactRepository extends JpaRepository<CrmContact, UUID> {
    Optional<CrmContact> findByTenant_IdAndId(UUID tenantId, UUID contactId);
    List<CrmContact> findByTenant_IdOrderByDisplayNameAsc(UUID tenantId);
}
