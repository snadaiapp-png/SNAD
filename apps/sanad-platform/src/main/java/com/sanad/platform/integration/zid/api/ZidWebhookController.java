package com.sanad.platform.integration.zid.api;

import com.sanad.platform.integration.zid.application.ZidWebhookIngestService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Zid webhook ingress. JWT is intentionally not used on this endpoint
 * (Zid's servers cannot present a SANAD session); the request is
 * verified cryptographically against the RAW body BEFORE any database
 * lookup — mirroring the R0C13 billing provider webhook contract.
 *
 * <p>Response contract (fail-closed):</p>
 * <ul>
 *   <li>503 NOT_CONFIGURED — no secret provisioned (endpoint inert).</li>
 *   <li>401 INVALID_SIGNATURE — header missing/mismatched; nothing
 *       persisted.</li>
 *   <li>422 TRUSTED_BINDING_NOT_FOUND — store unknown; nothing
 *       persisted.</li>
 *   <li>409 REPLAY_MISMATCH — same event id, different payload.</li>
 *   <li>202 ACCEPTED — stored in the idempotent inbox for background
 *       processing (ingestion is deliberately decoupled from the
 *       processing work).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/integrations/zid")
public class ZidWebhookController {

    private final ZidWebhookIngestService ingestService;

    public ZidWebhookController(ZidWebhookIngestService ingestService) {
        this.ingestService = ingestService;
    }

    @PostMapping(
            value = "/webhook",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> webhook(
            @RequestBody byte[] payload,
            @RequestHeader(name = "X-Zid-Signature", required = false) String signatureHeader
    ) {
        ZidWebhookIngestService.IngestResult result = ingestService.ingest(payload, signatureHeader);
        return switch (result.outcome()) {
            case NOT_CONFIGURED -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "code", "ZID_WEBHOOK_NOT_CONFIGURED",
                    "message", "Zid webhook verification is not configured"));
            case INVALID_SIGNATURE -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "code", "INVALID_SIGNATURE",
                    "message", "Zid webhook signature verification failed"));
            case TRUSTED_BINDING_NOT_FOUND -> ResponseEntity.unprocessableEntity().body(Map.of(
                    "code", "TRUSTED_BINDING_NOT_FOUND",
                    "message", "Zid webhook could not be resolved to a trusted store binding"));
            case REPLAY_MISMATCH -> ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "code", "REPLAY_MISMATCH",
                    "message", "Zid event replay does not match the original payload"));
            case ACCEPTED -> ResponseEntity.accepted().body(Map.of(
                    "code", "ACCEPTED",
                    "duplicate", result.receipt() != null && result.receipt().duplicate(),
                    "status", result.receipt() == null ? "RECEIVED" : result.receipt().status()));
        };
    }
}
