package com.sanad.platform.hr.integration;

/**
 * Registered consumer of claimed HR domain events (WS4 Task 6).
 *
 * <p>Consumers are invoked OUTSIDE any database transaction (the worker's
 * claim transaction has already committed and the finalize transaction has
 * not started). Delivery is AT_LEAST_ONCE — implementations MUST be
 * idempotent.</p>
 */
public interface HrOutboxEventConsumer {

    void onEvent(HrOutboxEvent event);
}
