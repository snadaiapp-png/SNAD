package com.sanad.platform.crm.mobile.sync.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request model for delta sync pull.
 * Client sends cursor to receive only changed entities.
 *
 * Requirements: API-003 (Delta Sync Pull API), SYNC-002 (Delta Pull)
 */
public record DeltaSyncRequest(
    @NotBlank String entityType,
    String cursor,
    Integer limit
) {
    public DeltaSyncRequest {
        if (limit == null || limit <= 0) {
            limit = 100;
        }
        if (limit > 500) {
            limit = 500;
        }
    }
}
