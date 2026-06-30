package com.sanad.platform.shared.api.exceptions;

import com.sanad.platform.shared.api.ErrorCode;

/**
 * Base class for all typed business exceptions raised by the SNAD platform.
 *
 * <p>Each subclass carries:</p>
 * <ul>
 *   <li>An {@link ErrorCode} (mapped to a stable {@code SANAD-XXX-NNN} code, HTTP status, and title)</li>
 *   <li>A <strong>safe client detail</strong> — a static, message-catalog-style string that is
 *       safe to return to the API caller. This MUST NOT be derived from
 *       {@code ex.getMessage()}, JDBC error messages, or any external provider text.</li>
 *   <li>An <strong>internal diagnostic context</strong> — structured key/value pairs logged
 *       server-side with the requestId but never sent to the client.</li>
 * </ul>
 *
 * <p>Concrete subclasses: {@link ResourceNotFoundException}, {@link ConflictException},
 * {@link BusinessRuleException}, {@link TenantContextException}, {@link InvalidPaginationException}.</p>
 */
public abstract class TypedBusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String safeClientDetail;
    private final java.util.Map<String, String> diagnostics;

    protected TypedBusinessException(ErrorCode errorCode,
                                      String safeClientDetail,
                                      java.util.Map<String, String> diagnostics) {
        super(safeClientDetail);  // super message is the SAFE detail only
        this.errorCode = errorCode;
        this.safeClientDetail = safeClientDetail;
        this.diagnostics = diagnostics == null
                ? java.util.Collections.emptyMap()
                : java.util.Collections.unmodifiableMap(diagnostics);
    }

    protected TypedBusinessException(ErrorCode errorCode,
                                      String safeClientDetail,
                                      java.util.Map<String, String> diagnostics,
                                      Throwable cause) {
        super(safeClientDetail, cause);
        this.errorCode = errorCode;
        this.safeClientDetail = safeClientDetail;
        this.diagnostics = diagnostics == null
                ? java.util.Collections.emptyMap()
                : java.util.Collections.unmodifiableMap(diagnostics);
    }

    public ErrorCode errorCode() { return errorCode; }
    public String safeClientDetail() { return safeClientDetail; }
    public java.util.Map<String, String> diagnostics() { return diagnostics; }
}
