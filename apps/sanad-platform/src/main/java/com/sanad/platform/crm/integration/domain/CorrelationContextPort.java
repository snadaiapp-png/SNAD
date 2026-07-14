package com.sanad.platform.crm.integration.domain;

/**
 * Port for obtaining or generating correlation IDs.
 * Implementation extracts from request header or generates a new UUID.
 */
public interface CorrelationContextPort {
    String currentCorrelationId();
}
