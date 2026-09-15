package com.sanad.platform.subscription.provisioning;

/**
 * PATH-B G1 — explicit NON_RETRYABLE failure disposition for a provisioning
 * step.
 *
 * <p>Thrown when a step failed for a reason a blind re-run of the same job can
 * never repair, so the job must be persisted FAILED and reported FAILED
 * immediately instead of being parked in RETRYING:</p>
 * <ul>
 *   <li>the subscription is in a terminal state (CANCELLED / EXPIRED /
 *       TERMINATED);</li>
 *   <li>the canonical lifecycle authority rejected the activation because the
 *       durable state is not one this provisioning job can legally activate
 *       without another governed business action (e.g. PAUSED needs RESUME);</li>
 *   <li>the provisioning contract itself is malformed / non-recoverable
 *       (unknown step, unknown subscription).</li>
 * </ul>
 *
 * <p>PROVISIONING_STATUS_TRUTH_INVARIANT: the final job status is derived from
 * the failure disposition (RETRYABLE vs NON_RETRYABLE) plus the retry policy —
 * never from the attempt count alone — and the returned {@code JobOutcome}
 * always carries the same status string that was persisted.</p>
 */
public class NonRetryableProvisioningException extends RuntimeException {

    public NonRetryableProvisioningException(String message) {
        super(message);
    }

    public NonRetryableProvisioningException(String message, Throwable cause) {
        super(message, cause);
    }
}
