package com.sanad.platform.organization.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Structured error body returned by all Organization REST API error responses.
 *
 * <p>Designed for machine-parseable client error handling. All fields are
 * populated by {@link OrganizationApiExceptionHandler}.</p>
 *
 * <p>{@link JsonInclude.Include#NON_NULL} is set so that fields with no
 * value (e.g. {@code path} when the request URL cannot be determined)
 * are omitted from the JSON output rather than serialized as {@code null}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiErrorResponse {

    /** ISO-8601 timestamp when the error was generated. */
    private Instant timestamp;

    /** HTTP status code (e.g. 400, 404, 409). */
    private int status;

    /** HTTP reason phrase (e.g. "Bad Request", "Not Found", "Conflict"). */
    private String error;

    /** Human-readable explanation of the error. */
    private String message;

    /** Request path that triggered the error (best-effort). */
    private String path;

    // ------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------

    public ApiErrorResponse() {
    }

    public ApiErrorResponse(Instant timestamp, int status, String error, String message, String path) {
        this.timestamp = timestamp;
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
    }

    // ------------------------------------------------------------
    // Getters / Setters
    // ------------------------------------------------------------

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    @Override
    public String toString() {
        return "ApiErrorResponse{" +
                "timestamp=" + timestamp +
                ", status=" + status +
                ", error='" + error + '\'' +
                ", message='" + message + '\'' +
                ", path='" + path + '\'' +
                '}';
    }
}
