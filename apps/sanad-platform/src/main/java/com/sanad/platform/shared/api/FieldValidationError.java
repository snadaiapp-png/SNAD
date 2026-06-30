package com.sanad.platform.shared.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Field-level validation error for use within ApiErrorResponse.
 */
public record FieldValidationError(
    @JsonProperty("field") String field,
    @JsonProperty("code") String code,
    @JsonProperty("message") String message
) {}
