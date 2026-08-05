package com.sanad.platform.crm.email.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Request DTOs for CRM email API.
 */
public final class EmailModels {

    private EmailModels() {}

    /**
     * Request body for POST /api/v2/crm/email/send.
     */
    public record SendEmailRequest(
            @NotBlank String from,
            @NotEmpty List<@Email String> to,
            List<@Email String> cc,
            List<@Email String> bcc,
            @NotBlank String subject,
            String textBody,
            String htmlBody,
            String templateName,
            Map<String, Object> templateVariables,
            String relatedEntityType,
            UUID relatedEntityId,
            Map<String, String> metadata
    ) {}
}
