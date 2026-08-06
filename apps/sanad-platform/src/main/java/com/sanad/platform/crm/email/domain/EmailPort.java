package com.sanad.platform.crm.email.domain;

import java.util.UUID;

/**
 * Email sending port — bounded context for CRM email delivery.
 * <p>
 * This port defines the contract for sending emails. Implementations
 * are provided by provider-specific adapters (Resend, SMTP, HTTP proxy).
 * The application layer depends only on this interface.
 * <p>
 * All methods are tenant-scoped for multi-tenant isolation.
 */
public interface EmailPort {

    /**
     * Send an email message via the configured provider.
     *
     * @param tenantId  the tenant scope
     * @param message   the email message to send
     * @return the send result with provider message ID and status
     */
    EmailSendResult send(UUID tenantId, EmailMessage message);

    /**
     * Check if the email provider is available and configured.
     *
     * @return true if the provider can accept send requests
     */
    boolean isAvailable();

    /**
     * Return the provider name for logging and audit purposes.
     *
     * @return provider identifier (e.g., "resend", "smtp", "http-proxy", "local")
     */
    String providerName();
}
