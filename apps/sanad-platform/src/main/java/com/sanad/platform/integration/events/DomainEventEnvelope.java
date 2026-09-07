package com.sanad.platform.integration.events;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/**
 * Versioned domain event envelope for cross-module integration.
 *
 * <p>Shared Platform contract. Producer-local durable storage remains
 * in future workstreams. This envelope is transport/storage-neutral.</p>
 *
 * <p>Never place raw PII (National ID, passport, bank secret, crypto key)
 * in the generic payload metadata.</p>
 */
public record DomainEventEnvelope(
        UUID eventId,
        String eventType,
        int eventVersion,
        String aggregateType,
        UUID aggregateId,
        UUID tenantId,
        UUID organizationId,
        UUID actorUserId,
        Instant occurredAt,
        UUID correlationId,
        UUID causationId,
        String idempotencyKey,
        String dataClassification,
        JsonNode payload
) {}
