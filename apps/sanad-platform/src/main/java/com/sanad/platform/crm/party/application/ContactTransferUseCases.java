package com.sanad.platform.crm.party.application;

import com.sanad.platform.crm.collaboration.application.CollaborationMembershipService;
import com.sanad.platform.crm.collaboration.application.CollaborationMembershipService.AddParticipantCommand;
import com.sanad.platform.crm.collaboration.application.CollaborationMembershipService.EligibilityPolicy;
import com.sanad.platform.crm.collaboration.application.CollaborationMembershipService.RemoveParticipantCommand;
import com.sanad.platform.crm.collaboration.domain.CollaborationEntityType;
import com.sanad.platform.crm.collaboration.domain.EntityParticipant;
import com.sanad.platform.crm.collaboration.domain.ParticipantRole;
import com.sanad.platform.crm.collaboration.domain.RecipientEligibilityPort;
import com.sanad.platform.crm.collaboration.domain.RecipientEligibilityPort.EligibilityDecision;
import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import com.sanad.platform.crm.party.domain.ContactRepository;
import com.sanad.platform.crm.party.domain.ContactRepository.ContactRecord;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Canonical Contact owner-transfer orchestration (Task C5).
 *
 * <p>This service is the single authoritative path for transferring
 * ownership of a {@code crm_contacts} row. The operation is atomic and
 * performs the following ordered steps inside ONE {@code @Transactional}
 * boundary:</p>
 *
 * <ol>
 *   <li><strong>Lock Contact row FIRST</strong> via
 *       {@link ContactRepository#findByIdForUpdate}. Lock order is
 *       <em>CONTACT_ROW_FIRST</em> — participant mutations must always
 *       occur after this lock is held.</li>
 *   <li>Reject when the Contact is in ARCHIVED lifecycle state.</li>
 *   <li>Enforce {@code expectedVersion} — reject with
 *       {@code CRM_CONCURRENCY_CONFLICT} if the locked row's version
 *       does not match.</li>
 *   <li>Capture {@code previousOwnerId = contact.ownerUserId()}.</li>
 *   <li>If {@code previousOwnerId == newOwnerUserId}, return the current
 *       Contact as an idempotent no-op — no version increment, no
 *       participant mutation.</li>
 *   <li>Evaluate new-owner eligibility via
 *       {@link RecipientEligibilityPort#evaluate} with
 *       {@code organizationId=null}, {@code requiredCapability="CRM.CONTACT.READ"}.
 *       Reject with {@code IllegalArgumentException} if ineligible.</li>
 *   <li>Normalize the proposed owner's active participant membership:
 *       list current CONTACT participants; if the new owner already holds
 *       an active COLLABORATOR or WATCHER row, remove it (using its actual
 *       participant version) so the C3 DB trigger does not reject the
 *       subsequent owner update.</li>
 *   <li>Update the Contact owner via
 *       {@link ContactRepository#transferOwner} — narrow UPDATE touching
 *       only {@code owner_user_id}, {@code updated_by}, {@code updated_at},
 *       {@code version = version + 1}.</li>
 *   <li>If {@code retainPreviousOwnerAsWatcher == true} AND
 *       {@code previousOwnerId != null} AND
 *       {@code previousOwnerId != newOwnerUserId}: ensure the previous
 *       owner ends as an active WATCHER via the membership service
 *       (which itself enforces eligibility and W2 role-switch
 *       normalization — if the previous owner already holds an active
 *       COLLABORATOR, the service will remove it before adding WATCHER).</li>
 *   <li>Return the refreshed {@link ContactRecord}.</li>
 * </ol>
 *
 * <h3>Atomicity</h3>
 *
 * <p>The entire sequence runs in ONE Spring {@code @Transactional} boundary.
 * If any step fails — eligibility denial, participant optimistic conflict,
 * DB invariant violation, insert failure — the entire transaction rolls
 * back: the Contact owner is restored to its original value, the Contact
 * version is NOT incremented, the participant row that was removed during
 * normalization is restored, and no partial WATCHER insertion is left
 * behind.</p>
 *
 * <h3>Dependency boundary</h3>
 *
 * <p>This service injects ONLY:</p>
 * <ul>
 *   <li>{@link ContactRepository} — for the contact row lock + versioned
 *       owner update primitive.</li>
 *   <li>{@link CollaborationMembershipService} — for participant
 *       normalization and previous-owner WATCHER retention.</li>
 *   <li>{@link RecipientEligibilityPort} — for target-owner eligibility
 *       evaluation (read-only, no RBAC mutation).</li>
 * </ul>
 *
 * <p>It MUST NOT inject:</p>
 * <ul>
 *   <li>{@code CapabilityEvaluationService} — actor transfer capability
 *       (CRM.CONTACT.TRANSFER) is owned by C7.</li>
 *   <li>{@code TimelineEventPort}, {@code AuditPort},
 *       {@code CrmEventOutboxPort} — structured events are owned by C8.</li>
 *   <li>{@code SecurityContextHolder} — tenant propagation is owned by
 *       the production TenantRlsConnectionHandler under the
 *       {@code @Transactional} boundary.</li>
 *   <li>{@code TenantRlsTransactionContext} — same reason.</li>
 *   <li>{@code JdbcTemplate}, {@code NamedParameterJdbcTemplate} — direct
 *       JDBC is owned by {@link ContactRepository}.</li>
 *   <li>{@code OwnershipCommandUseCases}, {@code TransferUseCases} —
 *       generic ownership infrastructure does not provide the Contact-specific
 *       contract required by C5 (no expectedVersion, no row-lock, no
 *       participant normalization, wrong side-effect profile).</li>
 * </ul>
 *
 * <h3>Actor RBAC boundary</h3>
 *
 * <p>This service validates TARGET OWNER eligibility only. It does NOT
 * evaluate the ACTOR's CRM.CONTACT.TRANSFER capability — that is owned by
 * C7. Until C7 lands, the actor boundary must be reported as
 * {@code ACTOR_TRANSFER_RBAC=DEFERRED_C7}.</p>
 *
 * <h3>Events / audit / outbox boundary</h3>
 *
 * <p>This service emits NO {@code crm.contact.owner.transferred} timeline
 * event, NO {@code OWNER_TRANSFER} audit row, NO
 * {@code contact.owner.transferred} outbox row. Those concerns are owned
 * by C8. Final report must state
 * {@code TRANSFER_EVENTS=DEFERRED_C8},
 * {@code TRANSFER_AUDIT=DEFERRED_C8},
 * {@code TRANSFER_OUTBOX=DEFERRED_C8}.</p>
 */
public class ContactTransferUseCases {

    private static final CollaborationEntityType CONTACT = CollaborationEntityType.CONTACT;
    private static final String REQUIRED_RECIPIENT_CAPABILITY = "CRM.CONTACT.READ";
    private static final String ARCHIVED_STATUS = "ARCHIVED";
    private static final boolean DEFAULT_RETAIN_PREVIOUS_OWNER_AS_WATCHER = true;

    private final ContactRepository contactRepository;
    private final CollaborationMembershipService membershipService;
    private final RecipientEligibilityPort eligibilityPort;

    public ContactTransferUseCases(ContactRepository contactRepository,
                                   CollaborationMembershipService membershipService,
                                   RecipientEligibilityPort eligibilityPort) {
        this.contactRepository = Objects.requireNonNull(contactRepository, "contactRepository");
        this.membershipService = Objects.requireNonNull(membershipService, "membershipService");
        this.eligibilityPort = Objects.requireNonNull(eligibilityPort, "eligibilityPort");
    }

    /**
     * Default-on entry point: equivalent to
     * {@code transferContact(new TransferContactCommand(..., true))}.
     *
     * <p><strong>C5-R1 transaction boundary contract:</strong> this default
     * overload is annotated {@code @Transactional} so that an external
     * caller enters a Spring-managed transaction before the delegating
     * call to {@link #transferContact(TransferContactCommand)}. Without
     * this annotation, the Spring AOP proxy would NOT intercept the
     * default overload — the internal self-call to the command overload
     * would also bypass proxy interception (Spring self-invocation),
     * leaving the entire transfer execution without a transaction
     * boundary. That breaks:</p>
     * <ul>
     *   <li>CONTACT_ROW_FIRST lock semantics (FOR UPDATE lifetime not
     *       transaction-scoped)</li>
     *   <li>TenantRlsConnectionHandler activation (GUC only applied when
     *       autoCommit == false)</li>
     *   <li>SET LOCAL app.tenant_id application (no transaction → no GUC)</li>
     *   <li>Participant normalization atomicity (no rollback on failure)</li>
     *   <li>Owner UPDATE rollback (no transaction = no rollback)</li>
     *   <li>Previous-owner WATCHER rollback (no transaction = no rollback)</li>
     * </ul>
     *
     * <p>After this annotation, the external call enters a transaction via
     * the proxy; the internal command-overload self-call joins the SAME
     * transaction via Spring's default PROPAGATION_REQUIRED semantics
     * (self-invocation does NOT re-intercept the proxy, but that is safe
     * because the outer default overload already owns an active
     * transaction).</p>
     *
     * <p><strong>Self-invocation contract:</strong>
     * SELF_INVOCATION_REMAINS=YES (the inner call to
     * {@code transferContact(TransferContactCommand)} is still a
     * self-invocation and is NOT re-intercepted by the Spring proxy).
     * SELF_INVOCATION_UNSAFE=NO (because the externally-callable
     * delegating entrypoint — this default overload — is itself
     * transactional, the inner self-call executes inside the
     * already-active outer transaction).</p>
     */
    @Transactional
    public ContactRecord transferContact(UUID tenantId,
                                         UUID contactId,
                                         UUID newOwnerUserId,
                                         long expectedVersion,
                                         UUID actorId,
                                         Instant occurredAt) {
        return transferContact(new TransferContactCommand(
                tenantId, contactId, newOwnerUserId, expectedVersion,
                actorId, occurredAt, DEFAULT_RETAIN_PREVIOUS_OWNER_AS_WATCHER));
    }

    /**
     * Canonical Contact owner-transfer mutation. See class Javadoc for the
     * ordered sequence and atomicity contract.
     */
    @Transactional
    public ContactRecord transferContact(TransferContactCommand command) {
        // 1. Lock Contact FIRST.
        ContactRecord contact = contactRepository.findByIdForUpdate(
                command.tenantId(), command.contactId());

        // 2. Reject archived Contact.
        if (ARCHIVED_STATUS.equals(contact.lifecycleStatus())) {
            throw new CrmContractException(CrmErrorCode.CONFLICT,
                    "Archived contacts cannot be transferred without restoration.");
        }

        // 3. Enforce expectedVersion.
        if (contact.version() != command.expectedVersion()) {
            throw new CrmContractException(CrmErrorCode.CRM_CONCURRENCY_CONFLICT);
        }

        // 4. Capture previous owner.
        UUID previousOwnerId = contact.ownerUserId();

        // 5. Idempotent no-op when target == current owner.
        if (command.newOwnerUserId().equals(previousOwnerId)) {
            return contact;
        }

        // 6. Evaluate new-owner eligibility (target only — actor RBAC is C7).
        EligibilityDecision decision = eligibilityPort.evaluate(
                command.tenantId(),
                command.newOwnerUserId(),
                null,
                REQUIRED_RECIPIENT_CAPABILITY);
        if (!decision.eligible()) {
            throw new IllegalArgumentException(
                    "recipient not eligible: " + decision.reason());
        }

        // 7. Normalize proposed owner's active participant membership.
        for (EntityParticipant p : membershipService.listParticipants(
                command.tenantId(), CONTACT, command.contactId())) {
            if (p.userId().equals(command.newOwnerUserId()) && p.isActive()) {
                membershipService.removeParticipant(new RemoveParticipantCommand(
                        command.tenantId(), p.id(), p.version(),
                        command.actorId(), command.occurredAt()));
                // C3 W2 invariant guarantees at most one active participant
                // per user/contact, so we can break after the first removal.
                break;
            }
        }

        // 8. Update Contact owner (narrow versioned UPDATE — version+1 exactly once).
        ContactRecord transferred = contactRepository.transferOwner(
                command.tenantId(), command.actorId(), command.contactId(),
                command.newOwnerUserId(), command.expectedVersion(),
                command.occurredAt());

        // 9. Retain previous owner as WATCHER when configured.
        if (command.retainPreviousOwnerAsWatcher()
                && previousOwnerId != null
                && !previousOwnerId.equals(command.newOwnerUserId())) {
            ensureWatcher(command.tenantId(), command.contactId(),
                    previousOwnerId, command.actorId(), command.occurredAt());
        }

        // 10. Return refreshed Contact.
        return transferred;
    }

    /**
     * Ensure {@code userId} ends as an active WATCHER on the Contact.
     *
     * <p>Performs explicit W2 normalization: lists current CONTACT
     * participants, and if {@code userId} already holds an active
     * COLLABORATOR row, removes it (using its actual participant version)
     * before adding the WATCHER. This mirrors the
     * {@code ContactCollaborationService.addParticipantInternal} role-switch
     * normalization contract so the C3 DB invariant is satisfied without
     * relying on the membership service's internal idempotent fast path
     * (which only handles same-role duplicates, not opposite-role).</p>
     *
     * <p>The membership service's {@code addParticipant} will still fire
     * eligibility evaluation (read-only RBAC check) for the WATCHER role
     * — both the previous-owner COLLABORATOR removal AND the WATCHER add
     * are inside the outer C5 {@code @Transactional} boundary, so an
     * eligibility denial on the WATCHER add rolls back the removal.</p>
     */
    private void ensureWatcher(UUID tenantId, UUID contactId, UUID userId,
                                UUID actorId, Instant occurredAt) {
        // W2 normalization: remove active COLLABORATOR if it exists.
        for (EntityParticipant p : membershipService.listParticipants(
                tenantId, CONTACT, contactId)) {
            if (p.userId().equals(userId)
                    && p.role() == ParticipantRole.COLLABORATOR
                    && p.isActive()) {
                membershipService.removeParticipant(new RemoveParticipantCommand(
                        tenantId, p.id(), p.version(),
                        actorId, occurredAt));
                break; // C3 W2 guarantees at most one active participant per user/contact
            }
        }
        // Add WATCHER (idempotent if same role already exists).
        membershipService.addParticipant(
                new AddParticipantCommand(
                        tenantId, CONTACT, contactId, userId,
                        ParticipantRole.WATCHER, actorId, occurredAt),
                new EligibilityPolicy(null, REQUIRED_RECIPIENT_CAPABILITY));
    }

    /**
     * Canonical transfer command. Compact constructor enforces all
     * required non-null + non-negative invariants.
     */
    public record TransferContactCommand(
            UUID tenantId,
            UUID contactId,
            UUID newOwnerUserId,
            long expectedVersion,
            UUID actorId,
            Instant occurredAt,
            boolean retainPreviousOwnerAsWatcher) {

        public TransferContactCommand {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(contactId, "contactId");
            Objects.requireNonNull(newOwnerUserId, "newOwnerUserId");
            Objects.requireNonNull(actorId, "actorId");
            Objects.requireNonNull(occurredAt, "occurredAt");
            if (expectedVersion < 0) {
                throw new IllegalArgumentException(
                        "expectedVersion must be >= 0, got " + expectedVersion);
            }
        }
    }
}
