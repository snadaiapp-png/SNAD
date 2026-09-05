package com.sanad.platform.hr.contract.domain;

import com.sanad.platform.hr.audit.HrAuditRecord;
import com.sanad.platform.integration.events.DomainEventEnvelope;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository contract for employment contracts (WS6 Task 2).
 *
 * <p>Deliberately NO {@code updateTerms(versionId, ...)} operation exists:
 * historical effective terms are immutable. Every mutation method executes
 * ALL of its statements AND its transactional evidence (audit fact + delivery
 * state + outbox event) in ONE short tenant-scoped transaction — any evidence
 * failure rolls the whole mutation back (no REQUIRES_NEW).</p>
 */
public interface EmploymentContractRepository {

    /** Creates the contract plus its first version (one transaction + evidence). */
    void createContractWithEvidence(EmploymentContract contract, EmploymentContractVersion firstVersion,
                                    HrAuditRecord auditRecord, DomainEventEnvelope event);

    /** Amendment: supersedes the ACTIVE version and inserts the next version (one transaction + evidence). */
    void amendVersionWithEvidence(UUID tenantId, UUID contractId, EmploymentContractVersion newVersion,
                                  java.time.LocalDate supersedeEffectiveTo,
                                  HrAuditRecord auditRecord, DomainEventEnvelope event);

    /** Activates the given version (one transaction + evidence). */
    void activateVersionWithEvidence(UUID tenantId, UUID contractId, int versionNumber,
                                     java.time.LocalDate effectiveFrom, java.time.LocalDate effectiveTo,
                                     HrAuditRecord auditRecord, DomainEventEnvelope event);

    /** Terminates the ACTIVE version (one transaction + evidence). */
    void terminateVersionWithEvidence(UUID tenantId, UUID contractId, java.time.LocalDate effectiveDate,
                                      HrAuditRecord auditRecord, DomainEventEnvelope event);

    Optional<EmploymentContract> findContract(UUID tenantId, UUID contractId);

    Optional<EmploymentContractVersion> findVersion(UUID tenantId, UUID versionId);

    Optional<EmploymentContractVersion> findVersionByNumber(UUID tenantId, UUID contractId, int versionNumber);

    Optional<EmploymentContractVersion> findActivePrimaryVersion(UUID tenantId, UUID employmentId, java.time.LocalDate asOf);

    java.util.List<EmploymentContractVersion> findVersions(UUID tenantId, UUID contractId);
}
