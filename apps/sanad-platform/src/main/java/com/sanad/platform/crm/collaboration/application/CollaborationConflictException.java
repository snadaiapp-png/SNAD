package com.sanad.platform.crm.collaboration.application;

/**
 * Raised by {@link CollaborationMembershipService} when an optimistic-lock
 * update fails because the participant's state has been mutated concurrently,
 * or when an inactive participant is targeted for removal.
 *
 * <p>The exception is intentionally a {@link RuntimeException} so callers
 * do not need to declare it; transactional boundaries can roll back and
 * surface the conflict to the higher-level command orchestrator.
 *
 * <p>No HTTP / web annotations — this is a pure domain exception. Web
 * layer translation is owned by the controller advice in a separate module.
 */
public final class CollaborationConflictException extends RuntimeException {

    public CollaborationConflictException(String message) {
        super(message);
    }
}
