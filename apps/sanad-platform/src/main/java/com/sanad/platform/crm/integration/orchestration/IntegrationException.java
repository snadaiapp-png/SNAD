package com.sanad.platform.crm.integration.orchestration;

/**
 * Typed application exception carrying an {@link IntegrationErrorCode}.
 *
 * <p>Never stores raw provider errors, raw stack traces, or unstructured
 * exception messages. Every failure path that crosses the integration
 * boundary must produce this exception (or a subclass) so the controller
 * layer can map it to a deterministic HTTP status and error code.</p>
 *
 * <p>Mapping table (error code → HTTP status):</p>
 * <ul>
 *   <li>{@link IntegrationErrorCode#INVALID_CONTRACT}              → 400</li>
 *   <li>{@link IntegrationErrorCode#INVALID_SIGNATURE}             → 400</li>
 *   <li>{@link IntegrationErrorCode#INVALID_TENANT}                → 400</li>
 *   <li>{@link IntegrationErrorCode#STALE_RECOMMENDATION}          → 409</li>
 *   <li>{@link IntegrationErrorCode#ENTITY_NOT_FOUND}              → 404</li>
 *   <li>{@link IntegrationErrorCode#ENTITY_STATE_CONFLICT}         → 409</li>
 *   <li>{@link IntegrationErrorCode#OBJECT_ACCESS_DENIED}          → 403</li>
 *   <li>{@link IntegrationErrorCode#UNAUTHORIZED_SERVICE}          → 401</li>
 *   <li>{@link IntegrationErrorCode#IDEMPOTENCY_KEY_REUSED}        → 409</li>
 *   <li>{@link IntegrationErrorCode#INTEGRATION_VERSION_MISMATCH}  → 412</li>
 *   <li>{@link IntegrationErrorCode#AI_GATEWAY_TIMEOUT}            → 504</li>
 *   <li>{@link IntegrationErrorCode#AI_GATEWAY_UNAVAILABLE}        → 503</li>
 *   <li>{@link IntegrationErrorCode#WORKFLOW_ENGINE_TIMEOUT}       → 504</li>
 *   <li>{@link IntegrationErrorCode#WORKFLOW_ENGINE_UNAVAILABLE}   → 503</li>
 *   <li>{@link IntegrationErrorCode#AI_POLICY_DENIED}              → 422</li>
 *   <li>{@link IntegrationErrorCode#AI_UNSAFE_OUTPUT}              → 422</li>
 *   <li>{@link IntegrationErrorCode#WORKFLOW_POLICY_DENIED}        → 422</li>
 *   <li>{@link IntegrationErrorCode#UNKNOWN_ERROR}                 → 500</li>
 * </ul>
 */
public class IntegrationException extends RuntimeException {

    private final IntegrationErrorCode errorCode;
    private final String detail;        // safe, non-sensitive, never raw exception text

    public IntegrationException(IntegrationErrorCode errorCode, String detail) {
        super(errorCode.name() + (detail == null || detail.isBlank() ? "" : ": " + detail));
        this.errorCode = errorCode;
        this.detail = detail == null ? "" : detail;
    }

    public IntegrationException(IntegrationErrorCode errorCode, String detail, Throwable cause) {
        super(errorCode.name() + (detail == null || detail.isBlank() ? "" : ": " + detail), cause);
        this.errorCode = errorCode;
        this.detail = detail == null ? "" : detail;
    }

    public IntegrationErrorCode errorCode() {
        return errorCode;
    }

    public String detail() {
        return detail;
    }

    /**
     * HTTP status derived from {@link #errorCode}. Centralised so controllers
     * do not re-implement the mapping.
     */
    public int httpStatus() {
        return switch (errorCode) {
            case INVALID_CONTRACT, INVALID_SIGNATURE, INVALID_TENANT -> 400;
            case UNAUTHORIZED_SERVICE -> 401;
            case OBJECT_ACCESS_DENIED -> 403;
            case ENTITY_NOT_FOUND -> 404;
            case STALE_RECOMMENDATION, ENTITY_STATE_CONFLICT,
                 IDEMPOTENCY_KEY_REUSED -> 409;
            case INTEGRATION_VERSION_MISMATCH -> 412;
            case AI_POLICY_DENIED, AI_UNSAFE_OUTPUT, WORKFLOW_POLICY_DENIED -> 422;
            case AI_GATEWAY_TIMEOUT, WORKFLOW_ENGINE_TIMEOUT -> 504;
            case AI_GATEWAY_UNAVAILABLE, WORKFLOW_ENGINE_UNAVAILABLE -> 503;
            default -> 500;
        };
    }
}
