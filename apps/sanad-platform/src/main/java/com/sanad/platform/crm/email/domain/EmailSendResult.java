package com.sanad.platform.crm.email.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Email send result — returned after attempting to send an email.
 * <p>
 * Contains the provider-assigned message ID, delivery status,
 * and timestamp for audit trail purposes.
 */
public record EmailSendResult(
        UUID emailLogId,
        String providerMessageId,
        String status,
        String provider,
        Instant sentAt,
        String errorMessage
) {

    /** Factory for a successful result. */
    public static EmailSendResult success(UUID emailLogId, String providerMessageId, String provider) {
        return new EmailSendResult(emailLogId, providerMessageId, "SENT", provider, Instant.now(), null);
    }

    /** Factory for a failed result. */
    public static EmailSendResult failure(UUID emailLogId, String provider, String errorMessage) {
        return new EmailSendResult(emailLogId, null, "FAILED", provider, Instant.now(), errorMessage);
    }

    /** Factory for a queued result (async providers). */
    public static EmailSendResult queued(UUID emailLogId, String providerMessageId, String provider) {
        return new EmailSendResult(emailLogId, providerMessageId, "QUEUED", provider, Instant.now(), null);
    }

    /** Whether the email was successfully sent or queued. */
    public boolean isSuccess() {
        return "SENT".equals(status) || "QUEUED".equals(status);
    }
}
