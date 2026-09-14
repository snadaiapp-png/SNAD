package com.sanad.platform.subscription.billing.api;

import com.sanad.platform.subscription.billing.application.BillingWebhookService;
import com.sanad.platform.subscription.billing.domain.ProviderEventVerificationException;
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
 * Provider webhook ingress. JWT is intentionally not used on this endpoint;
 * the configured provider adapter must cryptographically verify the raw body
 * before any database lookup or mutation occurs.
 */
@RestController
@RequestMapping("/api/v1/billing/provider")
public class BillingProviderWebhookController {

    private final BillingWebhookService webhookService;

    public BillingProviderWebhookController(BillingWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping(
            value = "/webhook",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> webhook(
            @RequestBody byte[] payload,
            @RequestHeader(name = "X-Billing-Signature", required = false)
            String signatureHeader
    ) {
        try {
            return ResponseEntity.accepted().body(
                    webhookService.receive(payload, signatureHeader));
        } catch (ProviderEventVerificationException e) {
            if (e.reason()
                    == ProviderEventVerificationException.Reason.INVALID_SIGNATURE) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of(
                                "code", "INVALID_SIGNATURE",
                                "message", "Provider webhook signature verification failed"));
            }
            return ResponseEntity.badRequest().body(Map.of(
                    "code", "INVALID_PROVIDER_PAYLOAD",
                    "message", "Provider webhook payload is invalid"));
        } catch (BillingWebhookService.BindingNotFoundException e) {
            return ResponseEntity.unprocessableEntity().body(Map.of(
                    "code", "TRUSTED_BINDING_NOT_FOUND",
                    "message", "Provider webhook could not be resolved to a stored payment binding"));
        } catch (BillingWebhookService.ReplayMismatchException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "code", "REPLAY_MISMATCH",
                    "message", "Provider event replay does not match the original event"));
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().contains("DISABLED")) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                        "code", "PROVIDER_DISABLED",
                        "message", "Billing payment provider is disabled"));
            }
            throw e;
        }
    }
}
