package com.sanad.platform.crm.collaboration.application;

import com.sanad.platform.crm.collaboration.domain.CollaborationEntityType;
import com.sanad.platform.crm.collaboration.domain.EntityParticipant;
import com.sanad.platform.crm.collaboration.domain.EntityParticipantRepository;
import com.sanad.platform.crm.collaboration.domain.ParticipantRole;
import com.sanad.platform.crm.collaboration.domain.RecipientEligibilityPort;
import com.sanad.platform.crm.collaboration.domain.RecipientEligibilityPort.EligibilityDecision;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Collaboration membership operations for CRM participants.
 *
 * <p>This service is intentionally a PLAIN CLASS — it is NOT annotated
 * {@code @Service} or {@code @Component}. Bean wiring is owned by
 * {@code CollaborationModuleConfiguration} in Task 8. This prevents the
 * historical Task 7A mistake where the service was Spring-scanned before
 * the explicit configuration was ready.
 *
 * <p>Side-effect boundary — the service MUST NOT:
 * <ul>
 *   <li>Reference {@code TimelineEventPort}, {@code CrmEventOutboxPort},
 *       {@code AuditPort}, {@code PlatformAuditWriter},
 *       {@code CapabilityEvaluationService}, {@code OwnershipCommandUseCases},
 *       {@code TransferUseCases}, {@code SecurityContextHolder},
 *       {@code TenantRlsTransactionContext}, {@code JdbcTemplate}, or
 *       {@code NamedParameterJdbcTemplate}.</li>
 *   <li>Mutate ownership, emit timeline events, write audit, append outbox,
 *       write RBAC, set the tenant GUC, or read the security context.</li>
 * </ul>
 *
 * <p>The only dependencies are {@link EntityParticipantRepository} (persistence)
 * and {@link RecipientEligibilityPort} (read-only eligibility check).
 *
 * <p>Concurrency note: the partial unique index
 * {@code uk_crm_entity_participants_active} remains the authoritative
 * race-condition guard. The service-level idempotency check (findActive)
 * is a fast-path optimisation; it does NOT catch concurrent duplicate
 * INSERTs and the service does NOT translate
 * {@code DataIntegrityViolationException} into idempotency.
 */
public class CollaborationMembershipService {

    private final EntityParticipantRepository participants;
    private final RecipientEligibilityPort eligibility;

    public CollaborationMembershipService(EntityParticipantRepository participants,
                                          RecipientEligibilityPort eligibility) {
        this.participants = participants;
        this.eligibility = eligibility;
    }

    /**
     * Add a participant to a CRM entity.
     *
     * <ol>
     *   <li>Validate command + policy.</li>
     *   <li>Evaluate eligibility — ineligible →
     *       {@link IllegalArgumentException} containing the denial reason.</li>
     *   <li>If an active same-role relation already exists → return it
     *       unchanged (idempotent).</li>
     *   <li>Otherwise insert a new {@link EntityParticipant#active}.</li>
     * </ol>
     */
    public EntityParticipant addParticipant(AddParticipantCommand command, EligibilityPolicy policy) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(policy, "policy");
        command.validate();
        policy.validate();

        EligibilityDecision decision = eligibility.evaluate(
                command.tenantId(),
                command.userId(),
                policy.organizationId(),
                policy.requiredCapability());

        if (!decision.eligible()) {
            throw new IllegalArgumentException(
                    "recipient not eligible: " + decision.reason());
        }

        // Idempotent fast path — same (tenant, entity, user, role) active relation.
        var existing = participants.findActive(
                command.tenantId(),
                command.entityType(),
                command.entityId(),
                command.userId(),
                command.role());
        if (existing.isPresent()) {
            return existing.get();
        }

        EntityParticipant candidate = EntityParticipant.active(
                UUID.randomUUID(),
                command.tenantId(),
                command.entityType(),
                command.entityId(),
                command.userId(),
                command.role(),
                command.actorId(),
                command.occurredAt());
        return participants.insert(candidate);
    }

    /**
     * Remove a participant.
     *
     * <ol>
     *   <li>Validate command.</li>
     *   <li>Load by (tenant, participantId) — absent →
     *       {@link IllegalArgumentException}("participant not found").</li>
     *   <li>If inactive → {@link CollaborationConflictException}
     *       ("participant already removed").</li>
     *   <li>Call {@code markRemoved} with the COMMAND's expectedVersion
     *       (NOT existing.version()).</li>
     *   <li>If markRemoved returns false →
     *       {@link CollaborationConflictException}("participant state conflict").</li>
     *   <li>Return {@code existing.remove(actorId, occurredAt)} — the
     *       in-memory snapshot of the removed state.</li>
     * </ol>
     */
    public EntityParticipant removeParticipant(RemoveParticipantCommand command) {
        Objects.requireNonNull(command, "command");
        command.validate();

        var existingOpt = participants.findById(command.tenantId(), command.participantId());
        if (existingOpt.isEmpty()) {
            throw new IllegalArgumentException("participant not found");
        }
        EntityParticipant existing = existingOpt.get();

        if (!existing.isActive()) {
            throw new CollaborationConflictException("participant already removed");
        }

        boolean updated = participants.markRemoved(
                command.tenantId(),
                command.participantId(),
                command.expectedVersion(),
                command.actorId(),
                command.occurredAt());

        if (!updated) {
            throw new CollaborationConflictException("participant state conflict");
        }

        return existing.remove(command.actorId(), command.occurredAt());
    }

    /**
     * List active participants for a CRM entity. Delegates to
     * {@link EntityParticipantRepository#listActive} — does NOT evaluate
     * eligibility (no side-effect on RBAC).
     */
    public List<EntityParticipant> listParticipants(UUID tenantId,
                                                     CollaborationEntityType entityType,
                                                     UUID entityId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(entityId, "entityId");
        return participants.listActive(tenantId, entityType, entityId);
    }

    // ---------- commands + policy ----------

    public record AddParticipantCommand(
            UUID tenantId,
            CollaborationEntityType entityType,
            UUID entityId,
            UUID userId,
            ParticipantRole role,
            UUID actorId,
            Instant occurredAt) {

        public AddParticipantCommand {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(entityType, "entityType");
            Objects.requireNonNull(entityId, "entityId");
            Objects.requireNonNull(userId, "userId");
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(actorId, "actorId");
            Objects.requireNonNull(occurredAt, "occurredAt");
        }

        void validate() {
            // Compact constructor already enforces non-null — explicit
            // validate() method exists for symmetric API ergonomics with
            // EligibilityPolicy.validate().
        }
    }

    public record RemoveParticipantCommand(
            UUID tenantId,
            UUID participantId,
            long expectedVersion,
            UUID actorId,
            Instant occurredAt) {

        public RemoveParticipantCommand {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(participantId, "participantId");
            Objects.requireNonNull(actorId, "actorId");
            Objects.requireNonNull(occurredAt, "occurredAt");
            if (expectedVersion < 0) {
                throw new IllegalArgumentException(
                        "expectedVersion must be >= 0, got " + expectedVersion);
            }
        }

        void validate() {
            // Compact constructor already enforces non-null + non-negative
            // expectedVersion — explicit validate() exists for API symmetry.
        }
    }

    public record EligibilityPolicy(UUID organizationId, String requiredCapability) {

        public EligibilityPolicy {
            Objects.requireNonNull(requiredCapability, "requiredCapability");
            // organizationId may be null (tenant-wide scope).
        }

        void validate() {
            if (requiredCapability.isBlank()) {
                throw new IllegalArgumentException("requiredCapability is required");
            }
        }
    }
}
