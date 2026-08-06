package com.sanad.platform.crm.email.web;

import com.sanad.platform.crm.email.application.EmailUseCases;
import com.sanad.platform.crm.email.domain.EmailLogPort.EmailLogEntry;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.Set;
import java.util.UUID;

/**
 * Email tracking controller for open pixel and click redirect endpoints.
 * <p>
 * These endpoints are public (no authentication required) and are invoked
 * by email clients to track opens and clicks. The log entry ID is globally
 * unique so we can look up the tenant from the log entry itself.
 */
@RestController
@RequestMapping("/api/v2/crm/email/track")
public class TrackingController {

    private final EmailUseCases emailUseCases;

    /** Allowed redirect hosts — only first-party paths are permitted. */
    private static final Set<String> ALLOWED_REDIRECT_HOSTS = Set.of(
            "snad-app.vercel.app",
            "snad.vercel.app",
            "localhost"
    );

    public TrackingController(EmailUseCases emailUseCases) {
        this.emailUseCases = emailUseCases;
    }

    @GetMapping("/open/{logId}")
    public ResponseEntity<byte[]> trackOpen(@PathVariable UUID logId) {
        EmailLogEntry entry = emailUseCases.findByLogId(logId);
        if (entry == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Email log not found");
        }
        emailUseCases.recordOpen(entry.tenantId(), logId);

        // 1x1 transparent GIF
        byte[] pixel = {
                0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00,
                0x01, 0x00, (byte) 0x80, 0x00, 0x00, (byte) 0xFF,
                (byte) 0xFF, (byte) 0xFF, 0x00, 0x00, 0x00,
                0x21, (byte) 0xF9, 0x04, 0x01, 0x00, 0x00,
                0x00, 0x00, 0x2C, 0x00, 0x00, 0x00, 0x00,
                0x01, 0x00, 0x01, 0x00, 0x00, 0x02, 0x02,
                0x44, 0x01, 0x00, 0x3B
        };

        return ResponseEntity.ok()
                .header("Content-Type", "image/gif")
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .body(pixel);
    }

    @GetMapping("/click/{logId}")
    public ResponseEntity<Void> trackClick(
            @PathVariable UUID logId,
            @RequestParam(defaultValue = "/") String url) {
        EmailLogEntry entry = emailUseCases.findByLogId(logId);
        if (entry == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Email log not found");
        }

        // Validate redirect URL to prevent open redirect attacks
        String safeUrl = sanitizeRedirectUrl(url);
        emailUseCases.recordClick(entry.tenantId(), logId, safeUrl);

        // Redirect to validated URL
        return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", safeUrl)
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .build();
    }

    /**
     * Sanitize redirect URL to prevent open redirect attacks.
     * Only allows relative paths or URLs pointing to allowed hosts.
     */
    private String sanitizeRedirectUrl(String url) {
        if (url == null || url.isBlank()) {
            return "/";
        }

        // Allow relative paths (starting with /)
        if (url.startsWith("/") && !url.startsWith("//")) {
            return url;
        }

        // Block protocol-relative URLs (//evil.com)
        if (url.startsWith("//")) {
            return "/";
        }

        // Validate absolute URLs against allowlist
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host != null && ALLOWED_REDIRECT_HOSTS.contains(host.toLowerCase())) {
                return url;
            }
        } catch (IllegalArgumentException e) {
            // Invalid URL — fallback to root
        }

        // Default: redirect to root to prevent phishing
        return "/";
    }
}
