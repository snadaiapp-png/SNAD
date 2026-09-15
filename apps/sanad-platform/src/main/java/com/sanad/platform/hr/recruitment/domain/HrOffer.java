package com.sanad.platform.hr.recruitment.domain;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * HRM-G1 T7 — HrOffer aggregate (design §5.1/§6.3, restored semantics).
 *
 * <p>The aggregate owns: offer identity, tenant identity, the candidate's
 * application relationship, the current offer state, the current offer
 * version reference, the open approval workflow correlation, optimistic
 * concurrency metadata and expiry. The lifecycle AUTHORITY is the
 * application service ({@code HrOfferService}) + the domain guard
 * ({@code HrOfferTransitions}) — never the controller, never the JDBC
 * repository. Every state change is validated by
 * {@link HrOfferTransitions#check} before persistence; invalid transitions
 * fail closed.</p>
 *
 * <p>EXTENDED is reachable ONLY from PENDING_APPROVAL and only behind the
 * authoritative Workflow Y2 APPROVED outcome. Terminal states remain
 * terminal.</p>
 *
 * @param id                                  offer identity
 * @param tenantId                            tenant identity (FORCE RLS)
 * @param applicationId                       candidate application relationship
 * @param offerNumber                         tenant-unique business number
 * @param state                               current lifecycle state
 * @param currentVersionId                    current offer version reference
 * @param pendingOfferVersionId               version frozen for the open approval cycle (nullable)
 * @param pendingWorkflowInstanceId           authoritative Workflow Y2 instance of the open cycle (nullable)
 * @param pendingWorkflowDefinitionVersionId  pinned workflow definition version of the open cycle (nullable)
 * @param expiresAt                           offer expiry (nullable until set)
 * @param positionId                          optional position target
 * @param version                             optimistic concurrency token
 */
public record HrOffer(
        UUID id,
        UUID tenantId,
        UUID applicationId,
        String offerNumber,
        HrOfferState state,
        UUID currentVersionId,
        UUID pendingOfferVersionId,
        UUID pendingWorkflowInstanceId,
        UUID pendingWorkflowDefinitionVersionId,
        OffsetDateTime expiresAt,
        UUID positionId,
        long version) {

    public HrOffer {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(applicationId, "applicationId");
        Objects.requireNonNull(offerNumber, "offerNumber");
        Objects.requireNonNull(state, "state");
    }

    /** Static view of the open approval cycle; absent when none is open. */
    public boolean hasOpenApproval() {
        return pendingWorkflowInstanceId != null;
    }
}
