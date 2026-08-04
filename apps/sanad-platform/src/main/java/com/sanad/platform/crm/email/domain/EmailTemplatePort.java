package com.sanad.platform.crm.email.domain;

import java.util.Map;

/**
 * Email template port — bounded context for email template rendering.
 * <p>
 * Provides a contract for rendering email templates with variable
 * interpolation. Implementations are provider-specific (Mustache, etc.).
 */
public interface EmailTemplatePort {

    /**
     * Render an email template with the given variables.
     *
     * @param templateName the template identifier
     * @param variables    the template variables
     * @param locale       the locale for i18n (e.g., "en", "ar")
     * @return the rendered email content with subject, textBody, htmlBody
     */
    RenderedEmail render(String templateName, Map<String, Object> variables, String locale);

    /**
     * Check if a template exists.
     *
     * @param templateName the template identifier
     * @return true if the template is registered
     */
    boolean exists(String templateName);

    /**
     * Rendered email content.
     */
    record RenderedEmail(String subject, String textBody, String htmlBody) {}
}
