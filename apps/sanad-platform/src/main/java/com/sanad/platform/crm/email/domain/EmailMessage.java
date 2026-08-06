package com.sanad.platform.crm.email.domain;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Email message value object — represents a complete email ready for delivery.
 * <p>
 * Contains all information needed by any email provider adapter
 * to send the message. Provider-specific details (API keys, endpoints)
 * are injected by the adapter, not the message.
 */
public record EmailMessage(
        EmailAddress from,
        List<EmailAddress> to,
        List<EmailAddress> cc,
        List<EmailAddress> bcc,
        String subject,
        String textBody,
        String htmlBody,
        String templateName,
        TemplateVariables templateVariables,
        String tenantId,
        String relatedEntityType,
        String relatedEntityId,
        Map<String, String> metadata
) {

    /** Compact constructor with defaults. */
    public EmailMessage {
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        if (to.isEmpty()) throw new IllegalArgumentException("to must not be empty");
        subject = subject != null ? subject : "";
        textBody = textBody != null ? textBody : "";
        htmlBody = htmlBody != null ? htmlBody : "";
        templateName = templateName != null ? templateName : "";
        templateVariables = templateVariables != null ? templateVariables : TemplateVariables.EMPTY;
        tenantId = tenantId != null ? tenantId : "";
        relatedEntityType = relatedEntityType != null ? relatedEntityType : "";
        relatedEntityId = relatedEntityId != null ? relatedEntityId : "";
        metadata = metadata != null ? metadata : Map.of();
    }

    /** Builder for fluent construction. */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private EmailAddress from;
        private List<EmailAddress> to = List.of();
        private List<EmailAddress> cc = List.of();
        private List<EmailAddress> bcc = List.of();
        private String subject;
        private String textBody;
        private String htmlBody;
        private String templateName;
        private TemplateVariables templateVariables;
        private String tenantId;
        private String relatedEntityType;
        private String relatedEntityId;
        private Map<String, String> metadata;

        public Builder from(EmailAddress from) { this.from = from; return this; }
        public Builder to(EmailAddress... to) { this.to = List.of(to); return this; }
        public Builder to(List<EmailAddress> to) { this.to = to; return this; }
        public Builder cc(EmailAddress... cc) { this.cc = List.of(cc); return this; }
        public Builder bcc(EmailAddress... bcc) { this.bcc = List.of(bcc); return this; }
        public Builder subject(String subject) { this.subject = subject; return this; }
        public Builder textBody(String textBody) { this.textBody = textBody; return this; }
        public Builder htmlBody(String htmlBody) { this.htmlBody = htmlBody; return this; }
        public Builder templateName(String templateName) { this.templateName = templateName; return this; }
        public Builder templateVariables(TemplateVariables vars) { this.templateVariables = vars; return this; }
        public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public Builder relatedEntityType(String type) { this.relatedEntityType = type; return this; }
        public Builder relatedEntityId(String id) { this.relatedEntityId = id; return this; }
        public Builder metadata(Map<String, String> metadata) { this.metadata = metadata; return this; }

        public EmailMessage build() {
            return new EmailMessage(from, to, cc, bcc, subject, textBody, htmlBody,
                    templateName, templateVariables, tenantId, relatedEntityType,
                    relatedEntityId, metadata);
        }
    }
}
