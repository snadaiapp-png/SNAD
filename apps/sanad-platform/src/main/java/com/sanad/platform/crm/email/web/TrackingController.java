package com.sanad.platform.crm.email.web;

import com.sanad.platform.crm.email.application.EmailUseCases;
import com.sanad.platform.crm.email.domain.EmailLogPort.EmailLogEntry;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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
        emailUseCases.recordClick(entry.tenantId(), logId, url);

        // Redirect to original URL
        return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", url)
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .build();
    }
}
