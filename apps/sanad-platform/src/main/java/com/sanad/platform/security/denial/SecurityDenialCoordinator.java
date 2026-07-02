package com.sanad.platform.security.denial;

import com.sanad.platform.audit.service.PlatformSecurityDenialAuditService;
import com.sanad.platform.audit.service.TenantSecurityDenialAuditService;
import com.sanad.platform.shared.api.RequestIdFilter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

/**
 * Stage 05A.2.9.1 §8 — Central coordinator for every security denial path.
 *
 * <p>This is the <strong>only</strong> component permitted to write the
 * standardized HTTP denial response and to invoke the denial audit
 * services. Direct {@code writeError()} calls inside
 * {@code JwtAuthenticationFilter} are forbidden — all denial paths must
 * delegate here.</p>
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li><b>Store</b> the {@link SecurityDenialContext} as a request
 *       attribute for downstream inspection.</li>
 *   <li><b>Prevent duplicate recording</b> via the
 *       {@link SecurityDenialAttributes#DENIAL_RECORDED} flag. If the
 *       filter already recorded the denial, Spring Security's
 *       AuthenticationEntryPoint re-entry will not double-count.</li>
 *   <li><b>Select the correct audit table</b>:
 *       <ul>
 *         <li>{@code tenantVerified=false} → {@link PlatformSecurityDenialAuditService}
 *             ({@code platform_security_audit_events}).</li>
 *         <li>{@code tenantVerified=true} → {@link TenantSecurityDenialAuditService}
 *             ({@code audit_events} via verified tenant/user IDs).</li>
 *       </ul>
 *   </li>
 *   <li><b>Record the exact category</b> — never collapse to a default.</li>
 *   <li><b>Preserve the central request ID</b> — read from MDC, never
 *       re-generate inside the filter chain.</li>
 *   <li><b>Write the standardized HTTP response</b> as
 *       {@code application/problem+json} with the canonical error code,
 *       status, path, and timestamp.</li>
 *   <li><b>Increment the failure metric</b>
 *       {@code sanad.security.denial.audit.failures} when the audit
 *       write itself throws. The security response is still sent.</li>
 * </ul>
 *
 * <p>Audit-failure logging rule (Stage 05A.2.9.1 §15): the log line
 * records only the category, audit scope, request ID, and exception
 * class. It never records the raw token, the Authorization header, or
 * any token prefix/suffix.</p>
 */
@Component
public class SecurityDenialCoordinator {

    private static final Logger log = LoggerFactory.getLogger(SecurityDenialCoordinator.class);

    private final PlatformSecurityDenialAuditService platformDenialAuditService;
    private final TenantSecurityDenialAuditService tenantDenialAuditService;
    private final MeterRegistry meterRegistry;

    public SecurityDenialCoordinator(
            PlatformSecurityDenialAuditService platformDenialAuditService,
            TenantSecurityDenialAuditService tenantDenialAuditService,
            MeterRegistry meterRegistry) {
        this.platformDenialAuditService = platformDenialAuditService;
        this.tenantDenialAuditService = tenantDenialAuditService;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Records the denial audit row (if not already recorded) and writes
     * the standardized HTTP denial response.
     *
     * <p>This overload is used by pre-auth denial paths where the JWT
     * tenant/user IDs are not yet known (or are unverified). The
     * coordinator routes the row to
     * {@code platform_security_audit_events}.</p>
     *
     * @param request     the HTTP request (used for path, method, IP, UA,
     *                    and request-id correlation)
     * @param response    the HTTP response (the coordinator writes the
     *                    body — callers must not write it themselves)
     * @param context     the typed denial context (category, errorCode,
     *                    httpStatus, tenantVerified, tokenFingerprint)
     */
    public void deny(
            HttpServletRequest request,
            HttpServletResponse response,
            SecurityDenialContext context) throws IOException {
        deny(request, response, context, null, null);
    }

    /**
     * Records the denial audit row (if not already recorded) and writes
     * the standardized HTTP denial response.
     *
     * <p>This overload is used by post-auth denial paths
     * (TENANT_SELECTOR_MISMATCH, ROTATION_REQUIRED, CAPABILITY_DENIED)
     * where the JWT tenant/user IDs are cryptographically verified. The
     * coordinator routes the row to {@code audit_events} via the
     * tenant denial service.</p>
     *
     * @param request     the HTTP request
     * @param response    the HTTP response (coordinator writes the body)
     * @param context     the typed denial context
     * @param tenantId    verified tenant ID from JWT (null for pre-auth)
     * @param userId      verified user ID from JWT (null for pre-auth)
     */
    public void deny(
            HttpServletRequest request,
            HttpServletResponse response,
            SecurityDenialContext context,
            UUID tenantId,
            UUID userId) throws IOException {

        // 1. Store the context for downstream inspection / tests.
        request.setAttribute(SecurityDenialAttributes.DENIAL_CONTEXT, context);

        // 2. Idempotent recording — prevent double-counting if Spring
        //    Security's AuthenticationEntryPoint re-fires after the
        //    filter already recorded the denial.
        Boolean alreadyRecorded = (Boolean) request.getAttribute(
                SecurityDenialAttributes.DENIAL_RECORDED);
        if (!Boolean.TRUE.equals(alreadyRecorded)) {
            recordAudit(request, context, tenantId, userId);
            request.setAttribute(SecurityDenialAttributes.DENIAL_RECORDED,
                    Boolean.TRUE);
        }

        // 3. Write the standardized HTTP response. The request ID is read
        //    from MDC — never re-generated here.
        writeDenialResponse(request, response, context);
    }

    /**
     * Records a tenant-verified denial audit row WITHOUT writing the HTTP
     * response. Used by the {@link com.sanad.platform.shared.api.GlobalExceptionHandler}
     * for {@link com.sanad.platform.security.authorization.CapabilityDeniedException}
     * — the handler builds its own {@code ResponseEntity<ApiErrorResponse>}
     * and only needs the audit row recorded.
     *
     * <p>Idempotent: if {@link SecurityDenialAttributes#DENIAL_RECORDED}
     * is already {@code TRUE}, this method is a no-op.</p>
     */
    public void recordTenantDenial(
            HttpServletRequest request,
            SecurityDenialContext context,
            UUID tenantId,
            UUID userId) {

        request.setAttribute(SecurityDenialAttributes.DENIAL_CONTEXT, context);
        Boolean alreadyRecorded = (Boolean) request.getAttribute(
                SecurityDenialAttributes.DENIAL_RECORDED);
        if (Boolean.TRUE.equals(alreadyRecorded)) {
            return;
        }
        recordAudit(request, context, tenantId, userId);
        request.setAttribute(SecurityDenialAttributes.DENIAL_RECORDED,
                Boolean.TRUE);
    }

    private void recordAudit(
            HttpServletRequest request,
            SecurityDenialContext context,
            UUID tenantId,
            UUID userId) {

        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        String path = request.getRequestURI();
        String method = request.getMethod();
        String sourceIp = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");

        try {
            if (context.tenantVerified()) {
                tenantDenialAuditService.recordDenial(context, tenantId, userId, requestId);
            } else {
                platformDenialAuditService.recordDenial(
                        context.category().name(),
                        context.errorCode(),
                        requestId,
                        path,
                        method,
                        sourceIp,
                        userAgent,
                        context.tokenFingerprint(),
                        null);
            }
        } catch (Exception auditEx) {
            // Stage 05A.2.9.1 §15 — ERROR log + metric increment.
            // Never log the raw token, Authorization header, or token
            // prefix/suffix — only the category, audit scope, request ID,
            // and exception class.
            String auditScope = context.tenantVerified() ? "tenant" : "platform";
            log.error(
                    "Failed to record security denial audit: category={} auditScope={} "
                            + "requestId={} path={} exception={}",
                    context.category(),
                    auditScope,
                    requestId,
                    path,
                    auditEx.getClass().getSimpleName());
            // Tagged counter per §15: category, audit_scope, exception_type.
            Counter.builder("sanad.security.denial.audit.failures")
                    .tag("category", context.category().name())
                    .tag("audit_scope", auditScope)
                    .tag("exception_type", auditEx.getClass().getSimpleName())
                    .register(meterRegistry)
                    .increment();
        }
    }

    private void writeDenialResponse(
            HttpServletRequest request,
            HttpServletResponse response,
            SecurityDenialContext context) throws IOException {

        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        if (requestId == null || requestId.isBlank()) {
            // Fall back to the request's X-Request-Id header — never
            // generate a new one here. RequestIdFilter is responsible
            // for ID assignment.
            requestId = request.getHeader(RequestIdFilter.REQUEST_ID_HEADER);
        }
        if (requestId == null || requestId.isBlank()) {
            // Last-resort fallback: keep the response correlated even if
            // the filter was bypassed. This branch must not fire in
            // normal operation.
            requestId = "unknown";
        }

        response.setStatus(context.httpStatus());
        response.setContentType("application/problem+json");
        if (requestId != null && !"unknown".equals(requestId)) {
            response.setHeader(RequestIdFilter.REQUEST_ID_HEADER, requestId);
        }

        String typeSlug = context.errorCode().toLowerCase().replace("sanad-", "");
        String title = switch (context.httpStatus()) {
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            default -> "Error";
        };

        String detail = switch (context.category()) {
            case MISSING_JWT -> "Authentication is required";
            case MALFORMED_JWT, INVALID_SIGNATURE, EXPIRED_JWT, INVALID_SUBJECT,
                 UNVERIFIED_TENANT, UNKNOWN_SESSION, REVOKED_SESSION ->
                    "Authentication credentials are invalid or expired";
            case TENANT_SELECTOR_MISMATCH -> "Tenant identity in the request does not match the authenticated session";
            case ROTATION_REQUIRED -> "Credential rotation is required before using this API";
            case CAPABILITY_DENIED -> "Access is denied for this resource";
        };

        response.getWriter().write(
                "{\"type\":\"https://snad.ai/errors/" + typeSlug + "\","
                        + "\"title\":\"" + title + "\","
                        + "\"status\":" + context.httpStatus() + ","
                        + "\"detail\":\"" + detail + "\","
                        + "\"instance\":\"" + request.getRequestURI() + "\","
                        + "\"code\":\"" + context.errorCode() + "\","
                        + "\"requestId\":\"" + requestId + "\","
                        + "\"timestamp\":\"" + Instant.now() + "\"}");
    }
}
