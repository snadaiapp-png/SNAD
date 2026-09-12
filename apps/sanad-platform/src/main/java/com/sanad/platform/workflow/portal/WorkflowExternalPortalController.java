package com.sanad.platform.workflow.portal;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Map;

/**
 * Token-scoped External Action Portal HTTP surface (GATE R2.11/R2.12).
 * Unauthenticated by design — the opaque one-time token IS the capability;
 * the path is permitted in SecurityConfig and every operation is bounded,
 * rate-limited, replay-protected, and audit-logged server-side.
 * No sensitive data beyond the bounded action descriptor is ever returned.
 */
@RestController
@RequestMapping("/api/v1/workflows/external/portal")
public class WorkflowExternalPortalController {

    private final WorkflowExternalPortalService portalService;

    public WorkflowExternalPortalController(WorkflowExternalPortalService portalService) {
        this.portalService = portalService;
    }

    private static String fingerprint(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded != null && !forwarded.isBlank()
                ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
        return ip == null ? "unknown" : ip;
    }

    private static ResponseEntity<Map<String, Object>> denied(
            WorkflowExternalPortalService.PortalDeniedException e) {
        int status = switch (e.getMessage()) {
            case "RATE_LIMITED" -> 429;
            case "INVALID_TOKEN", "EXPIRED_TOKEN", "REVOKED_TOKEN" -> 410;
            case "OTP_REQUIRED", "OTP_INVALID", "OTP_EXPIRED", "OTP_DISABLED" -> 401;
            default -> 403;
        };
        return ResponseEntity.status(status).body(Map.of(
                "status", status,
                "error", e.getMessage()));
    }

    @PostMapping("/view")
    public ResponseEntity<?> view(HttpServletRequest request,
                                  @RequestBody Map<String, String> body) {
        try {
            WorkflowExternalPortalService.PortalView view =
                    portalService.view(body.get("token"), fingerprint(request));
            return ResponseEntity.ok(Map.of(
                    "actionId", String.valueOf(view.actionId()),
                    "actionType", view.actionType(),
                    "status", view.status(),
                    "expiresAt", String.valueOf(view.tokenExpiresAt()),
                    "respondedAt", String.valueOf(view.respondedAt()),
                    "participantType", view.participantType() == null
                            ? "" : view.participantType(),
                    "displayReference", view.displayReference() == null
                            ? "" : view.displayReference(),
                    "otpRequired", view.otpRequired(),
                    "response", view.responsePayload()));
        } catch (WorkflowExternalPortalService.PortalDeniedException e) {
            return denied(e);
        }
    }

    @PostMapping("/respond")
    public ResponseEntity<?> respond(HttpServletRequest request,
                                     @RequestBody Map<String, Object> body) {
        try {
            Object payloadObj = body.get("payload");
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = payloadObj instanceof Map<?, ?> m
                    ? (Map<String, Object>) m : Map.of();
            WorkflowExternalPortalService.PortalResponse response =
                    portalService.respond(
                            (String) body.get("token"),
                            (String) body.getOrDefault("action", ""),
                            payload,
                            fingerprint(request),
                            (String) body.get("otp"));
            return ResponseEntity.ok(Map.of(
                    "actionId", String.valueOf(response.actionId()),
                    "status", response.status(),
                    "respondedAt", String.valueOf(response.respondedAt()),
                    "response", response.responsePayload()));
        } catch (WorkflowExternalPortalService.PortalDeniedException e) {
            return denied(e);
        }
    }

    @PostMapping("/otp")
    public ResponseEntity<?> issueOtp(HttpServletRequest request,
                                      @RequestBody Map<String, String> body) {
        try {
            portalService.issueOtp(body.get("token"), fingerprint(request));
            return ResponseEntity.ok(Map.of("issued", true));
        } catch (WorkflowExternalPortalService.PortalDeniedException e) {
            return denied(e);
        }
    }

    /** Tenant-side listing of portal-relevant actions (authenticated API). */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> health() {
        return ResponseEntity.ok(List.of(Map.of("portal", "READY")));
    }
}
