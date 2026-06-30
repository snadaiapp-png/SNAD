package com.sanad.platform.shared.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

/**
 * Unified API error response following RFC 9457 (Problem Details for HTTP APIs).
 * Content-Type: application/problem+json
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
    @JsonProperty("type") String type,
    @JsonProperty("title") String title,
    @JsonProperty("status") int status,
    @JsonProperty("detail") String detail,
    @JsonProperty("instance") String instance,
    @JsonProperty("code") String code,
    @JsonProperty("requestId") String requestId,
    @JsonProperty("timestamp") Instant timestamp,
    @JsonProperty("errors") List<FieldValidationError> errors
) {
    public ApiErrorResponse(String code, String title, int status, String detail,
                             String instance, String requestId) {
        this("https://snad.ai/errors/" + code.toLowerCase().replace("sanad-", ""),
             title, status, detail, instance, code, requestId, Instant.now(), null);
    }

    public ApiErrorResponse(String code, String title, int status, String detail,
                             String instance, String requestId, List<FieldValidationError> errors) {
        this("https://snad.ai/errors/" + code.toLowerCase().replace("sanad-", ""),
             title, status, detail, instance, code, requestId, Instant.now(), errors);
    }
}
