package com.sanad.platform.idempotency;

import java.util.UUID;

/**
 * Result of beginning an idempotent operation.
 *
 * @param operationId   the unique operation ID
 * @param alreadyExists true if a prior operation with the same key was found
 * @param priorStatus   the prior operation's status code (if alreadyExists)
 * @param priorResponse the prior operation's response body (if alreadyExists)
 */
public record IdempotencyBeginResult(
        UUID operationId,
        boolean alreadyExists,
        Integer priorStatus,
        String priorResponse
) {}
