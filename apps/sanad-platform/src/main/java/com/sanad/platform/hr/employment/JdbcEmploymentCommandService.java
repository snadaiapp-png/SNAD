package com.sanad.platform.hr.employment;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * JDBC implementation of {@link EmploymentCommandService}.
 *
 * <p>Each lifecycle command atomically:
 * <ol>
 *   <li>Loads Employment (with optimistic version check)</li>
 *   <li>Validates transition via {@link #validateTransition}</li>
 *   <li>Closes the open status period (if any)</li>
 *   <li>Inserts new status period</li>
 *   <li>Updates current_status projection + increments version</li>
 * </ol>
 * </p>
 */
public final class JdbcEmploymentCommandService implements EmploymentCommandService {

    private final EmploymentRepository repository;

    private static final Set<EmploymentStatus> TERMINAL_STATES = Set.of(
            EmploymentStatus.TERMINATED, EmploymentStatus.VOIDED);

    private static final java.util.Map<EmploymentStatus, Set<EmploymentStatus>> ALLOWED_TRANSITIONS =
            java.util.Map.of(
                    EmploymentStatus.DRAFT, Set.of(EmploymentStatus.PENDING_ONBOARDING, EmploymentStatus.VOIDED),
                    EmploymentStatus.PENDING_ONBOARDING, Set.of(EmploymentStatus.ACTIVE, EmploymentStatus.VOIDED),
                    EmploymentStatus.ACTIVE, Set.of(EmploymentStatus.ON_LEAVE, EmploymentStatus.SUSPENDED, EmploymentStatus.TERMINATED),
                    EmploymentStatus.ON_LEAVE, Set.of(EmploymentStatus.ACTIVE, EmploymentStatus.SUSPENDED, EmploymentStatus.TERMINATED),
                    EmploymentStatus.SUSPENDED, Set.of(EmploymentStatus.ACTIVE, EmploymentStatus.TERMINATED),
                    EmploymentStatus.TERMINATED, Set.of(),
                    EmploymentStatus.VOIDED, Set.of()
            );

    public JdbcEmploymentCommandService(EmploymentRepository repository) {
        this.repository = repository;
    }

    @Override
    public EmploymentTransitionResult submitOnboarding(UUID tenantId, UUID employmentId,
                                                        LocalDate effectiveDate, String reasonCode) {
        return transition(tenantId, employmentId, effectiveDate, reasonCode,
                EmploymentStatus.PENDING_ONBOARDING);
    }

    @Override
    public EmploymentTransitionResult activate(UUID tenantId, UUID employmentId,
                                                LocalDate effectiveDate, String reasonCode) {
        return transition(tenantId, employmentId, effectiveDate, reasonCode,
                EmploymentStatus.ACTIVE);
    }

    @Override
    public EmploymentTransitionResult startLeave(UUID tenantId, UUID employmentId,
                                                  LocalDate effectiveDate, String reasonCode) {
        return transition(tenantId, employmentId, effectiveDate, reasonCode,
                EmploymentStatus.ON_LEAVE);
    }

    @Override
    public EmploymentTransitionResult returnFromLeave(UUID tenantId, UUID employmentId,
                                                       LocalDate effectiveDate, String reasonCode) {
        return transition(tenantId, employmentId, effectiveDate, reasonCode,
                EmploymentStatus.ACTIVE);
    }

    @Override
    public EmploymentTransitionResult suspend(UUID tenantId, UUID employmentId,
                                               LocalDate effectiveDate, String reasonCode) {
        return transition(tenantId, employmentId, effectiveDate, reasonCode,
                EmploymentStatus.SUSPENDED);
    }

    @Override
    public EmploymentTransitionResult reinstate(UUID tenantId, UUID employmentId,
                                                 LocalDate effectiveDate, String reasonCode) {
        return transition(tenantId, employmentId, effectiveDate, reasonCode,
                EmploymentStatus.ACTIVE);
    }

    @Override
    public EmploymentTransitionResult terminate(UUID tenantId, UUID employmentId,
                                                  LocalDate effectiveDate, String reasonCode) {
        return transition(tenantId, employmentId, effectiveDate, reasonCode,
                EmploymentStatus.TERMINATED);
    }

    @Override
    public EmploymentTransitionResult void_(UUID tenantId, UUID employmentId,
                                              LocalDate effectiveDate, String reasonCode) {
        return transition(tenantId, employmentId, effectiveDate, reasonCode,
                EmploymentStatus.VOIDED);
    }

    @Override
    public Employment rehire(UUID tenantId, UUID priorEmploymentId,
                              UUID personId, UUID legalEntityId,
                              String employeeNumber, String workerClassificationCode,
                              LocalDate effectiveDate, String reasonCode) {
        // Verify prior employment exists and is TERMINATED.
        Optional<Employment> prior = repository.findEmploymentById(tenantId, priorEmploymentId);
        if (prior.isEmpty()) {
            throw new IllegalStateException("Prior employment not found: " + priorEmploymentId);
        }
        if (prior.get().currentStatus() != EmploymentStatus.TERMINATED) {
            throw new IllegalStateException(
                "Rehire requires prior employment to be TERMINATED, was: " + prior.get().currentStatus());
        }

        // Create NEW Employment row — does NOT reactivate the prior.
        Employment newEmployment = new Employment(
                UUID.randomUUID(),
                tenantId,
                personId,
                legalEntityId,
                employeeNumber,
                workerClassificationCode,
                EmploymentStatus.DRAFT,
                effectiveDate,
                null,
                priorEmploymentId,
                0L);
        repository.saveEmployment(newEmployment);
        return newEmployment;
    }

    // --- internal ---

    private EmploymentTransitionResult transition(UUID tenantId, UUID employmentId,
                                                    LocalDate effectiveDate, String reasonCode,
                                                    EmploymentStatus targetStatus) {
        Employment employment = repository.findEmploymentById(tenantId, employmentId)
                .orElseThrow(() -> new IllegalStateException("Employment not found: " + employmentId));

        EmploymentStatus currentStatus = employment.currentStatus();

        // Terminal state enforcement.
        if (TERMINAL_STATES.contains(currentStatus)) {
            throw new IllegalStateException(
                "INVALID_STATE_TRANSITION: " + currentStatus + " is terminal — cannot transition to " + targetStatus);
        }

        // Validate transition is allowed.
        Set<EmploymentStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(currentStatus, Set.of());
        if (!allowed.contains(targetStatus)) {
            throw new IllegalStateException(
                "INVALID_STATE_TRANSITION: " + currentStatus + " → " + targetStatus + " is not allowed");
        }

        // Execute the transition atomically on a SINGLE connection.
        // This guarantees: close period + insert new period + update projection
        // all commit or all rollback together (LIFECYCLE_TRANSACTION_ATOMIC = YES).
        if (repository instanceof JdbcEmploymentRepository jdbcRepo) {
            return jdbcRepo.executeTransition(
                    tenantId, employmentId, currentStatus, targetStatus,
                    employment.version(), effectiveDate, reasonCode);
        }

        throw new IllegalStateException(
            "Repository must be JdbcEmploymentRepository for atomic transitions");
    }
}
