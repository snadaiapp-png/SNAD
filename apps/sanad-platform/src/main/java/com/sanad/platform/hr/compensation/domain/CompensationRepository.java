package com.sanad.platform.hr.compensation.domain;

import com.sanad.platform.hr.audit.HrAuditRecord;
import com.sanad.platform.integration.events.DomainEventEnvelope;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository contract for compensation packages (WS6 Task 3).
 *
 * <p>Every mutation executes its statements AND its transactional evidence in
 * ONE short tenant-scoped transaction (no REQUIRES_NEW). Read methods that
 * return component amounts are NOT exposed here — the service gates those
 * through the sensitive-read audit.</p>
 */
public interface CompensationRepository {

    void createPackageWithEvidence(CompensationPackage pkg, HrAuditRecord auditRecord, DomainEventEnvelope event);

    /** Revision: closes the current package (SUPERSEDED, effective_to closed) and inserts the successor. */
    void revisePackageWithEvidence(UUID tenantId, UUID currentPackageId, CompensationPackage successor,
                                   LocalDate supersedeEffectiveTo, HrAuditRecord auditRecord, DomainEventEnvelope event);

    void endPackageWithEvidence(UUID tenantId, UUID packageId, LocalDate effectiveTo,
                                HrAuditRecord auditRecord, DomainEventEnvelope event);

    Optional<CompensationPackage> findPackage(UUID tenantId, UUID packageId);

    Optional<CompensationPackage> findActivePackage(UUID tenantId, UUID employmentId, LocalDate asOf);

    List<CompensationPackage> findPackageHistory(UUID tenantId, UUID employmentId);
}
