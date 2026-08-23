package com.sanad.platform.crm.party.application;

import com.sanad.platform.crm.collaboration.application.CollaborationMembershipService;
import com.sanad.platform.crm.collaboration.application.CollaborationMembershipService.AddParticipantCommand;
import com.sanad.platform.crm.collaboration.application.CollaborationMembershipService.EligibilityPolicy;
import com.sanad.platform.crm.collaboration.application.CollaborationMembershipService.RemoveParticipantCommand;
import com.sanad.platform.crm.collaboration.domain.CollaborationEntityType;
import com.sanad.platform.crm.collaboration.domain.EntityParticipant;
import com.sanad.platform.crm.collaboration.domain.ParticipantRole;
import com.sanad.platform.crm.party.domain.ContactRepository;
import com.sanad.platform.crm.party.domain.ContactRepository.ContactRecord;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Contact-specific collaboration orchestration over the generic
 * {@link CollaborationMembershipService}.
 *
 * <p>This service is the CONTACT-only façade that the contact domain
 * uses to add/remove/list participants on a {@code crm_contacts} row.
 * It enforces two application-layer invariants on top of the C3 database
 * triggers:</p>
 *
 * <ul>
 *   <li><strong>Owner cannot be a participant</strong> — if the target
 *       user is the same as {@code crm_contacts.owner_user_id}, the
 *       mutation is rejected at the application layer. The C3 database
 *       trigger {@code trg_contact_owner_not_participant} remains the
 *       defense-in-depth backstop for race conditions.</li>
 *   <li><strong>Archived contacts are immutable</strong> — share, watch,
 *       and removeParticipant reject when {@code lifecycleStatus = 'ARCHIVED'}.
 *       Listing participants is allowed on archived contacts (read-only).</li>
 * </ul>
 *
 * <h3>W2 role-switch normalization</h3>
 *
 * <p>The C3 partial unique index {@code uk_crm_contact_participant_active_user}
 * enforces one active participant row per user per CONTACT regardless of
 * role. To switch a user from COLLABORATOR → WATCHER (or vice versa),
 * the service:</p>
 * <ol>
 *   <li>loads the Contact and validates it is mutable;</li>
 *   <li>rejects if target user == contact owner;</li>
 *   <li>lists current CONTACT participants;</li>
 *   <li>finds the active opposite-role participant for the target user;</li>
 *   <li>if it exists, removes it using its participant version
 *       (delegated to {@link CollaborationMembershipService#removeParticipant});
 *       the {@code @Transactional} boundary guarantees that if the
 *       subsequent add fails, the removal is rolled back — no manual
 *       compensating writes;</li>
 *   <li>adds the desired role through
 *       {@link CollaborationMembershipService#addParticipant};</li>
 *   <li>returns the desired participant.</li>
 * </ol>
 *
 * <h3>Dependency boundary</h3>
 *
 * <p>This service injects ONLY {@link ContactRepository} and
 * {@link CollaborationMembershipService}. It MUST NOT inject
 * {@code TimelineEventPort}, {@code AuditPort},
 * {@code CrmEventOutboxPort}, {@code CapabilityEvaluationService},
 * {@code SecurityContextHolder}, {@code TenantRlsTransactionContext},
 * {@code JdbcTemplate}, or {@code NamedParameterJdbcTemplate} — those
 * concerns are owned by C7 (RBAC), C8 (events/audit/outbox), and the
 * HTTP layer (C11).</p>
 *
 * <p>The recipient capability required for Contact participation is
 * {@code CRM.CONTACT.READ}. The {@link EligibilityPolicy} is constructed
 * inline with {@code organizationId = null} (tenant-wide scope). C7 owns
 * the actor SHARE/WATCH capability gate.</p>
 */
public class ContactCollaborationService {

    private static final CollaborationEntityType CONTACT = CollaborationEntityType.CONTACT;
    private static final String REQUIRED_RECIPIENT_CAPABILITY = "CRM.CONTACT.READ";
    private static final String ARCHIVED_STATUS = "ARCHIVED";

    private final ContactRepository contactRepository;
    private final CollaborationMembershipService membershipService;

    public ContactCollaborationService(ContactRepository contactRepository,
                                       CollaborationMembershipService membershipService) {
        this.contactRepository = Objects.requireNonNull(contactRepository, "contactRepository");
        this.membershipService = Objects.requireNonNull(membershipService, "membershipService");
    }

    /**
     * Add (or normalize to) a COLLABORATOR participant on the Contact.
     *
     * <p>If the target user already holds an active WATCHER role, it is
     * removed first (within the same transaction). If the target user
     * already holds an active COLLABORATOR role, the request is
     * idempotent (delegated to the membership service's fast path).</p>
     */
    @Transactional
    public EntityParticipant shareContact(UUID tenantId,
                                          UUID contactId,
                                          UUID targetUserId,
                                          UUID actorId,
                                          Instant occurredAt) {
        return addParticipantInternal(
                tenantId, contactId, targetUserId, actorId, occurredAt, ParticipantRole.COLLABORATOR);
    }

    /**
     * Add (or normalize to) a WATCHER participant on the Contact.
     *
     * <p>If the target user already holds an active COLLABORATOR role,
     * it is removed first (within the same transaction). If the target
     * user already holds an active WATCHER role, the request is
     * idempotent (delegated to the membership service's fast path).</p>
     */
    @Transactional
    public EntityParticipant watchContact(UUID tenantId,
                                          UUID contactId,
                                          UUID targetUserId,
                                          UUID actorId,
                                          Instant occurredAt) {
        return addParticipantInternal(
                tenantId, contactId, targetUserId, actorId, occurredAt, ParticipantRole.WATCHER);
    }

    /**
     * Remove a participant from the Contact.
     *
     * <p>Validates that the participantId belongs to an active
     * participant on the requested Contact (tenantId + entityType=CONTACT
     * + entityId=contactId) before delegating to
     * {@link CollaborationMembershipService#removeParticipant}. This
     * prevents a caller from mutating another Contact's participant
     * row by passing an out-of-context participantId.</p>
     *
     * <p>The {@code expectedVersion} is the participant row's version,
     * NOT the contact's version. The membership service propagates
     * {@code CollaborationConflictException} on optimistic-lock failure.</p>
     */
    @Transactional
    public EntityParticipant removeParticipant(UUID tenantId,
                                                UUID contactId,
                                                UUID participantId,
                                                long expectedVersion,
                                                UUID actorId,
                                                Instant occurredAt) {
        ContactRecord contact = contactRepository.findById(tenantId, contactId);
        assertContactMutable(contact);

        // Verify participantId belongs to an active participant on the
        // requested Contact. We list active CONTACT participants and
        // match by id; if no match, the caller supplied a participant
        // that does not belong to this Contact.
        EntityParticipant target = null;
        for (EntityParticipant p : membershipService.listParticipants(tenantId, CONTACT, contactId)) {
            if (p.id().equals(participantId)) {
                target = p;
                break;
            }
        }
        if (target == null) {
            throw new IllegalArgumentException(
                    "participant does not belong to contact " + contactId
                            + " (tenant=" + tenantId + ", participantId=" + participantId + ")");
        }
        if (!target.isActive()) {
            throw new IllegalArgumentException(
                    "participant " + participantId + " is not active on contact " + contactId);
        }

        return membershipService.removeParticipant(new RemoveParticipantCommand(
                tenantId, participantId, expectedVersion, actorId, occurredAt));
    }

    /**
     * List active CONTACT participants on the Contact.
     *
     * <p>Read-only — no eligibility mutation, no RBAC write, no event.
     * Allowed on archived contacts.</p>
     *
     * <p><strong>C4-R1 transaction contract:</strong> annotated
     * {@code @Transactional(readOnly = true)} so the production
     * {@code TenantRlsConnectionHandler} applies
     * {@code SET LOCAL app.tenant_id} to the underlying physical
     * connection before any SELECT. The handler only activates when
     * {@code autoCommit == false} (i.e. inside a Spring transaction
     * boundary). Without {@code @Transactional}, listParticipants
     * would run in autoCommit=true mode and the FORCE RLS predicate
     * on {@code crm_contacts} and {@code crm_entity_participants}
     * would fail closed (0 rows returned) — even for an
     * authenticated principal with a valid tenant context.</p>
     *
     * <p>The {@code readOnly = true} hint signals to the transaction
     * manager that no writes are issued, allowing read-optimized
     * routing when configured. It does NOT bypass the GUC application
     * — the GUC is still applied transaction-locally by
     * {@code TenantRlsConnectionHandler}.</p>
     */
    @Transactional(readOnly = true)
    public List<EntityParticipant> listParticipants(UUID tenantId, UUID contactId) {
        // Verify the Contact exists in the tenant (will throw
        // CrmContractException if not found).
        contactRepository.findById(tenantId, contactId);
        return membershipService.listParticipants(tenantId, CONTACT, contactId);
    }

    // ── internal ──────────────────────────────────────────────────────────

    private EntityParticipant addParticipantInternal(UUID tenantId,
                                                     UUID contactId,
                                                     UUID targetUserId,
                                                     UUID actorId,
                                                     Instant occurredAt,
                                                     ParticipantRole desiredRole) {
        // 1. Load Contact and validate mutable.
        ContactRecord contact = contactRepository.findById(tenantId, contactId);
        assertContactMutable(contact);

        // 2. Reject if target user == contact owner (application guard).
        if (contact.ownerUserId() != null && contact.ownerUserId().equals(targetUserId)) {
            throw new IllegalStateException(
                    "contact owner cannot be a participant on the same contact"
                            + " (contact=" + contactId + ", owner=" + contact.ownerUserId() + ")");
        }

        // 3. List current CONTACT participants to find the opposite role.
        ParticipantRole oppositeRole = (desiredRole == ParticipantRole.COLLABORATOR)
                ? ParticipantRole.WATCHER
                : ParticipantRole.COLLABORATOR;

        for (EntityParticipant p : membershipService.listParticipants(tenantId, CONTACT, contactId)) {
            if (p.userId().equals(targetUserId)
                    && p.role() == oppositeRole
                    && p.isActive()) {
                // 4. Remove the opposite-role participant using its version.
                //    The @Transactional boundary guarantees that if the
                //    subsequent addParticipant fails, this removal is
                //    rolled back — no manual compensating write.
                membershipService.removeParticipant(new RemoveParticipantCommand(
                        tenantId, p.id(), p.version(), actorId, occurredAt));
                break;
            }
        }

        // 5. Add the desired role via the membership service.
        return membershipService.addParticipant(
                new AddParticipantCommand(
                        tenantId, CONTACT, contactId, targetUserId, desiredRole, actorId, occurredAt),
                new EligibilityPolicy(null, REQUIRED_RECIPIENT_CAPABILITY));
    }

    private static void assertContactMutable(ContactRecord contact) {
        if (ARCHIVED_STATUS.equals(contact.lifecycleStatus())) {
            throw new IllegalStateException(
                    "archived contacts cannot be mutated without restoration"
                            + " (contact=" + contact.id() + ", status=" + contact.lifecycleStatus() + ")");
        }
    }
}
