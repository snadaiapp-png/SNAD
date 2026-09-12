package com.sanad.platform.workflow.notification;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fail-closed provider registry (GATE R2.4). Exactly one provider per
 * channel; a duplicate registration fails startup (CONFIGURATION_ERROR).
 * Dispatch for a channel without a provider fails closed at the intent
 * (CONFIG_ERROR terminal failure) — never a silent drop.
 */
@Component
public class WorkflowChannelRegistry {

    private final Map<WorkflowChannel, WorkflowChannelProvider> byChannel =
            new EnumMap<>(WorkflowChannel.class);

    public WorkflowChannelRegistry(List<WorkflowChannelProvider> providers) {
        for (WorkflowChannelProvider provider : providers) {
            WorkflowChannelProvider existing =
                    byChannel.putIfAbsent(provider.channel(), provider);
            if (existing != null) {
                throw new IllegalStateException(
                        "FAIL_STARTUP duplicate notification provider for channel "
                                + provider.channel() + ": "
                                + existing.providerType() + " vs " + provider.providerType());
            }
        }
    }

    public Optional<WorkflowChannelProvider> find(WorkflowChannel channel) {
        return Optional.ofNullable(byChannel.get(channel));
    }

    /** Fail-closed lookup: dispatch requires a registered provider. */
    public WorkflowChannelProvider require(WorkflowChannel channel) {
        WorkflowChannelProvider provider = byChannel.get(channel);
        if (provider == null) {
            throw new IllegalStateException(
                    "UNKNOWN_PROVIDER_FAIL_CLOSED for channel " + channel);
        }
        return provider;
    }

    public boolean has(WorkflowChannel channel) {
        return byChannel.containsKey(channel);
    }
}
