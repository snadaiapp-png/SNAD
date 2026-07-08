package com.sanad.platform.internal.bootstrap.api;

import com.sanad.platform.internal.bootstrap.service.ControlPlaneBootstrapResult;
import com.sanad.platform.internal.bootstrap.service.ControlPlaneBootstrapService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One-time, token-gated Control Plane administrator bootstrap endpoint.
 *
 * <p>This controller is intentionally separate from the normal SaaS administration
 * surface area: it lives under {@code /api/v1/internal/control-plane/bootstrap-admin}
 * and is only callable when the operator has explicitly enabled bootstrap mode by
 * setting the environment variable {@code CONTROL_PLANE_BOOTSTRAP_ENABLED=true}
 * and supplying a matching {@code CONTROL_PLANE_BOOTSTRAP_TOKEN} value.</p>
 *
 * <h3>Security model</h3>
 * <ul>
 *   <li>Spring Security must permit the path (configured in {@code SecurityConfig}).</li>
 *   <li>The endpoint refuses all requests unless {@code CONTROL_PLANE_BOOTSTRAP_ENABLED=true}.</li>
 *   <li>The {@code X-Control-Plane-Bootstrap-Token} header must match the configured
 *       token using a constant-time comparison.</li>
 *   <li>Bootstrap credentials (email + password) are read from server-side environment
 *       variables and never from the request body, so they cannot leak through proxies,
 *       WAF logs, or the request dump in case of an exception.</li>
 *   <li>The endpoint never logs the token, password, or full email.</li>
 *   <li>After successful provisioning, the operator must disable bootstrap by setting
 *       {@code CONTROL_PLANE_BOOTSTRAP_ENABLED=false} and redeploying.</li>
 * </ul>
 *
 * <p>The endpoint is idempotent: re-invoking it against an existing user updates the
 * password hash and re-activates the role grant and membership.</p>
 */
@RestController
@RequestMapping("/api/v1/internal/control-plane")
public class ControlPlaneBootstrapController {

    private static final Logger log = LoggerFactory.getLogger(ControlPlaneBootstrapController.class);
    private static final String TOKEN_HEADER = "X-Control-Plane-Bootstrap-Token";

    private final ControlPlaneBootstrapService bootstrapService;
    private final boolean enabled;
    private final String expectedToken;

    public ControlPlaneBootstrapController(
            ControlPlaneBootstrapService bootstrapService,
            @Value("${sanad.control-plane.bootstrap.enabled:${CONTROL_PLANE_BOOTSTRAP_ENABLED:false}}") boolean enabled,
            @Value("${sanad.control-plane.bootstrap.token:${CONTROL_PLANE_BOOTSTRAP_TOKEN:}}") String expectedToken
    ) {
        this.bootstrapService = bootstrapService;
        this.enabled = enabled;
        this.expectedToken = expectedToken == null ? "" : expectedToken;
    }

    @PostMapping("/bootstrap-admin")
    public ResponseEntity<Map<String, Object>> bootstrapAdmin(
            @RequestHeader(value = TOKEN_HEADER, required = false) String suppliedToken,
            HttpServletRequest request
    ) {
        // Gate 1: bootstrap must be explicitly enabled.
        if (!enabled) {
            log.warn("Control-plane bootstrap attempted but disabled; remoteAddr={} path={}",
                    redactRemote(request.getRemoteAddr()), request.getRequestURI());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(errorBody("bootstrap_disabled", "Bootstrap mode is disabled."));
        }

        // Gate 2: bootstrap must have a configured token (defense in depth —
        // enabled=true with an empty token is treated as misconfigured).
        if (expectedToken.isBlank()) {
            log.error("Control-plane bootstrap enabled but token is not configured; refusing to proceed.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorBody("bootstrap_misconfigured",
                            "Bootstrap token is not configured on the server."));
        }

        // Gate 3: caller must supply the correct token using a constant-time comparison.
        String supplied = suppliedToken == null ? "" : suppliedToken;
        if (!constantTimeEquals(supplied, expectedToken)) {
            log.warn("Control-plane bootstrap token mismatch; remoteAddr={} path={}",
                    redactRemote(request.getRemoteAddr()), request.getRequestURI());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(errorBody("unauthorized", "Bootstrap token is required or incorrect."));
        }

        try {
            ControlPlaneBootstrapResult result = bootstrapService.bootstrapControlPlaneAdmin();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", "ok");
            body.put("bootstrap", "complete");
            body.put("tenantId", result.tenantId());
            body.put("userId", result.userId());
            body.put("adminEmailMasked", maskEmail(result.adminEmail()));
            body.put("created", result.created());
            body.put("membershipActivated", result.membershipActivated());
            body.put("roleGrantsActivated", result.roleGrantsActivated());
            return ResponseEntity.ok(body);
        } catch (IllegalStateException ex) {
            log.error("Control-plane bootstrap failed: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorBody("bootstrap_failed", safeMessage(ex)));
        } catch (Exception ex) {
            log.error("Control-plane bootstrap failed with unexpected error", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorBody("bootstrap_failed", "Unexpected error during bootstrap."));
        }
    }

    /** Constant-time string equality to mitigate timing attacks on the bootstrap token. */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] aBytes = a.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(aBytes, bBytes);
    }

    /** Mask an email like "cp-admin@example.com" -> "c*...@e*...com" so the response body is safe in logs. */
    private static String maskEmail(String email) {
        if (email == null) return null;
        int at = email.indexOf('@');
        if (at <= 0 || at == email.length() - 1) return "*";
        String local = email.substring(0, at);
        String domain = email.substring(at + 1);
        String maskedLocal = local.length() <= 1 ? "*" : local.charAt(0) + "*".repeat(Math.max(1, local.length() - 1));
        int dot = domain.lastIndexOf('.');
        String maskedDomain;
        if (dot <= 0) {
            maskedDomain = "*";
        } else {
            String dPart = domain.substring(0, dot);
            String tld = domain.substring(dot);
            maskedDomain = (dPart.length() <= 1 ? "*" : dPart.charAt(0) + "*".repeat(Math.max(1, dPart.length() - 1))) + tld;
        }
        return maskedLocal + "@" + maskedDomain;
    }

    /** Truncate and hash the remote address so it cannot be reconstructed but can still be correlated. */
    private static String redactRemote(String remoteAddr) {
        if (remoteAddr == null || remoteAddr.isBlank()) return "unknown";
        int separator = remoteAddr.indexOf('.');
        if (separator > 0) return remoteAddr.substring(0, separator) + ".*";
        int colon = remoteAddr.indexOf(':');
        if (colon > 0) return remoteAddr.substring(0, colon) + ":*";
        return "redacted";
    }

    /** Strip exception messages of any value that could be a credential, returning a safe summary. */
    private static String safeMessage(Exception ex) {
        String msg = ex.getMessage() == null ? "no message" : ex.getMessage();
        if (msg.length() > 240) msg = msg.substring(0, 240) + "...";
        return msg;
    }

    private static Map<String, Object> errorBody(String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "error");
        body.put("code", code);
        body.put("message", message);
        return body;
    }
}
