package com.sanad.platform.hr.employment;

import com.sanad.platform.hr.api.v2.HrApiErrorCode;
import com.sanad.platform.hr.api.v2.HrDomainException;
import com.sanad.platform.hr.api.v2.dto.CreateEmploymentRequest;
import com.sanad.platform.hr.api.v2.dto.EmploymentResponse;
import com.sanad.platform.hr.identity.HrPersonRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * HRM-G0 / WS5 Task 3 — v2 application facade for People→Employment
 * operations (Employment lifecycle slice).
 *
 * <p>Responsibilities, in order:
 * <ol>
 *   <li>resolve canonical resources (404 HRM_EMPLOYMENT_NOT_FOUND when
 *       absent — never fabricated)</li>
 *   <li>enforce the client-supplied optimistic concurrency version
 *       (409 HRM_CONCURRENCY_CONFLICT when stale)</li>
 *   <li>enforce canonical creation invariants (max one non-terminal
 *       Employment per Person+Legal Entity → 409 HRM_EMPLOYMENT_CONFLICT;
 *       explicit legal entity/person — never guessed)</li>
 *   <li>delegate transitions to {@link EmploymentCommandService} and translate
 *       legacy text markers to canonical typed errors</li>
 * </ol>
 *
 * <p>Coarse capability gating happens at the controller boundary; tenant
 * isolation is enforced underneath by RLS fail-closed repositories.
 */
@Service
public class HrEmploymentV2Service {

    private final EmploymentRepository repository;
    private final EmploymentCommandService commands;
    private final HrPersonRepository personRepository;

    public HrEmploymentV2Service(EmploymentRepository repository,
                                 EmploymentCommandService commands,
                                 HrPersonRepository personRepository) {
        this.repository = Objects.requireNonNull(repository);
        this.commands = Objects.requireNonNull(commands);
        this.personRepository = Objects.requireNonNull(personRepository);
    }

    // ==================== READS ====================

    public EmploymentResponse get(UUID tenantId, UUID employmentId) {
        Employment employment = repository.findEmploymentById(tenantId, employmentId)
                .orElseThrow(() -> new HrDomainException(HrApiErrorCode.HRM_EMPLOYMENT_NOT_FOUND,
                        "Employment " + employmentId + " not found"));
        return toResponse(employment);
    }

    public List<EmploymentResponse> list(UUID tenantId) {
        return repository.listEmployments(tenantId).stream().map(this::toResponse).toList();
    }

    // ==================== CREATE ====================

    public EmploymentResponse create(UUID tenantId, CreateEmploymentRequest request) {
        if (personRepository.findPersonById(tenantId, request.personId()).isEmpty()) {
            throw new HrDomainException(HrApiErrorCode.HRM_PERSON_NOT_FOUND,
                    "Person " + request.personId() + " not found");
        }
        if (repository.countNonTerminalEmploymentsForPersonInLegalEntity(
                tenantId, request.personId(), request.legalEntityId()) > 0) {
            throw new HrDomainException(HrApiErrorCode.HRM_EMPLOYMENT_CONFLICT,
                    "Person " + request.personId() + " already has a non-terminal employment in legal entity "
                            + request.legalEntityId());
        }
        Employment employment = new Employment(
                UUID.randomUUID(), tenantId, request.personId(), request.legalEntityId(),
                request.employeeNumber(), request.workerClassificationCode(),
                EmploymentStatus.DRAFT, request.employmentStartDate(), null, null, 0L);
        repository.saveEmployment(employment);
        return toResponse(employment);
    }

    // ==================== LIFECYCLE ====================

    public LifecycleOutcome submitOnboarding(UUID tenantId, UUID employmentId, Long expectedVersion,
                                             java.time.LocalDate effectiveDate, String reasonCode) {
        return transition(tenantId, employmentId, expectedVersion, effectiveDate, reasonCode,
                cmd -> cmd.submitOnboarding(tenantId, employmentId, effectiveDate, reasonCode));
    }

    public LifecycleOutcome activate(UUID tenantId, UUID employmentId, Long expectedVersion,
                                     java.time.LocalDate effectiveDate, String reasonCode) {
        return transition(tenantId, employmentId, expectedVersion, effectiveDate, reasonCode,
                cmd -> cmd.activate(tenantId, employmentId, effectiveDate, reasonCode));
    }

    public LifecycleOutcome startLeave(UUID tenantId, UUID employmentId, Long expectedVersion,
                                       java.time.LocalDate effectiveDate, String reasonCode) {
        return transition(tenantId, employmentId, expectedVersion, effectiveDate, reasonCode,
                cmd -> cmd.startLeave(tenantId, employmentId, effectiveDate, reasonCode));
    }

    public LifecycleOutcome returnFromLeave(UUID tenantId, UUID employmentId, Long expectedVersion,
                                            java.time.LocalDate effectiveDate, String reasonCode) {
        return transition(tenantId, employmentId, expectedVersion, effectiveDate, reasonCode,
                cmd -> cmd.returnFromLeave(tenantId, employmentId, effectiveDate, reasonCode));
    }

    public LifecycleOutcome suspend(UUID tenantId, UUID employmentId, Long expectedVersion,
                                    java.time.LocalDate effectiveDate, String reasonCode) {
        return transition(tenantId, employmentId, expectedVersion, effectiveDate, reasonCode,
                cmd -> cmd.suspend(tenantId, employmentId, effectiveDate, reasonCode));
    }

    public LifecycleOutcome reinstate(UUID tenantId, UUID employmentId, Long expectedVersion,
                                      java.time.LocalDate effectiveDate, String reasonCode) {
        return transition(tenantId, employmentId, expectedVersion, effectiveDate, reasonCode,
                cmd -> cmd.reinstate(tenantId, employmentId, effectiveDate, reasonCode));
    }

    public LifecycleOutcome terminate(UUID tenantId, UUID employmentId, Long expectedVersion,
                                      java.time.LocalDate effectiveDate, String reasonCode) {
        return transition(tenantId, employmentId, expectedVersion, effectiveDate, reasonCode,
                cmd -> cmd.terminate(tenantId, employmentId, effectiveDate, reasonCode));
    }

    public LifecycleOutcome voidEmployment(UUID tenantId, UUID employmentId, Long expectedVersion,
                                           java.time.LocalDate effectiveDate, String reasonCode) {
        return transition(tenantId, employmentId, expectedVersion, effectiveDate, reasonCode,
                cmd -> cmd.void_(tenantId, employmentId, effectiveDate, reasonCode));
    }

    private LifecycleOutcome transition(UUID tenantId, UUID employmentId, Long expectedVersion,
                                        java.time.LocalDate effectiveDate, String reasonCode,
                                        Function<EmploymentCommandService, EmploymentTransitionResult> dispatch) {
        Employment current = repository.findEmploymentById(tenantId, employmentId)
                .orElseThrow(() -> new HrDomainException(HrApiErrorCode.HRM_EMPLOYMENT_NOT_FOUND,
                        "Employment " + employmentId + " not found"));
        if (current.version() != expectedVersion) {
            throw new HrDomainException(HrApiErrorCode.HRM_CONCURRENCY_CONFLICT,
                    "Employment " + employmentId + " version " + current.version()
                            + " does not match expected " + expectedVersion);
        }
        try {
            EmploymentTransitionResult result = dispatch.apply(commands);
            return new LifecycleOutcome(result.employmentId(),
                    result.previousStatus().name(), result.newStatus().name(),
                    result.closedPeriodId(), result.newPeriodId());
        } catch (IllegalStateException legacy) {
            throw translateLegacy(legacy.getMessage(), legacy);
        }
    }

    /**
     * Translation point for WS2 legacy text markers — confined here so the
     * canonical envelope stays stable without modifying certified WS2 code.
     */
    private HrDomainException translateLegacy(String message, IllegalStateException original) {
        String normalized = message == null ? "" : message;
        if (normalized.contains("INVALID_STATE_TRANSITION") || normalized.contains("is terminal")) {
            return new HrDomainException(HrApiErrorCode.HRM_INVALID_STATE_TRANSITION, normalized);
        }
        if (normalized.contains("not found")) {
            return new HrDomainException(HrApiErrorCode.HRM_EMPLOYMENT_NOT_FOUND, normalized);
        }
        return new HrDomainException(HrApiErrorCode.HRM_EMPLOYMENT_CONFLICT, normalized);
    }

    public record LifecycleOutcome(UUID employmentId, String previousStatus, String newStatus,
                                   UUID closedPeriodId, UUID newPeriodId) {
    }

    private EmploymentResponse toResponse(Employment employment) {
        return new EmploymentResponse(
                employment.id(), employment.personId(), employment.legalEntityId(),
                employment.employeeNumber(), employment.workerClassificationCode(),
                employment.currentStatus().name(), employment.employmentStartDate(),
                employment.terminationDate(), employment.rehireOfEmployeeId(),
                employment.version());
    }
}
