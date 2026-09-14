package com.sanad.platform.workflow.notification;

/**
 * R2 Provider SPI (GATE R2.4). Every channel provider exposes a bounded
 * contract: channel identity, provider type, and a single send operation
 * returning a classified result. Providers must NOT expose raw database
 * access, arbitrary URL execution, shell, reflection, dynamic class
 * loading, or unbounded scripts (AD-2).
 *
 * <p>Registry invariants: an unknown channel fails closed at dispatch; a
 * duplicate provider for one channel fails startup (configuration error).</p>
 */
public interface WorkflowChannelProvider {

    /** The single channel this provider serves. */
    WorkflowChannel channel();

    /**
     * Bounded provider identity (e.g. {@code expo-push}, {@code platform-smtp},
     * {@code governed-webhook}). Reported on the durable intent for audit.
     */
    String providerType();

    /**
     * One delivery attempt. Implementations must be side-effect bounded:
     * exactly one outbound operation per call, no retries inside the
     * provider (the dispatcher owns retry/backoff policy).
     */
    WorkflowDeliveryResult deliver(WorkflowDeliveryRequest request);
}
