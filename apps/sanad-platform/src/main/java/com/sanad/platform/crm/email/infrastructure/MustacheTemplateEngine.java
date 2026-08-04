package com.sanad.platform.crm.email.infrastructure;

import com.sanad.platform.crm.email.domain.EmailTemplatePort;
import com.sanad.platform.crm.email.domain.EmailTemplatePort.RenderedEmail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mustache-based email template engine.
 * <p>
 * Uses simple string-based template rendering with variable interpolation.
 * Templates are registered in a concurrent map for fast lookup.
 * Supports bilingual templates (English/Arabic).
 */
@Component
public class MustacheTemplateEngine implements EmailTemplatePort {

    private static final Logger log = LoggerFactory.getLogger(MustacheTemplateEngine.class);

    private final Map<String, TemplateDefinition> templates = new ConcurrentHashMap<>();

    public MustacheTemplateEngine() {
        registerBuiltInTemplates();
    }

    @Override
    public RenderedEmail render(String templateName, Map<String, Object> variables, String locale) {
        TemplateDefinition template = templates.get(templateName);
        if (template == null) {
            throw new IllegalArgumentException("Template not found: " + templateName);
        }

        String resolvedLocale = resolveLocale(templateName, locale);
        String subject = renderString(template.subject(resolvedLocale), variables);
        String textBody = renderString(template.textBody(resolvedLocale), variables);
        String htmlBody = renderString(template.htmlBody(resolvedLocale), variables);

        log.debug("Rendered template {} (locale={}): subject={}", templateName, resolvedLocale, subject);
        return new RenderedEmail(subject, textBody, htmlBody);
    }

    @Override
    public boolean exists(String templateName) {
        return templates.containsKey(templateName);
    }

    /**
     * Register a custom template at runtime.
     */
    public void register(String name, TemplateDefinition definition) {
        templates.put(name, definition);
    }

    /**
     * Simple Mustache-like variable rendering: replaces {{key}} with value.
     */
    private String renderString(String template, Map<String, Object> variables) {
        if (template == null) return "";
        String result = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            result = result.replace(placeholder, value);
        }
        return result;
    }

    /**
     * Resolve locale: fall back to "en" if the requested locale is not available.
     */
    private String resolveLocale(String templateName, String requestedLocale) {
        TemplateDefinition template = templates.get(templateName);
        if (template != null && template.supportedLocales().contains(requestedLocale)) {
            return requestedLocale;
        }
        return "en";
    }

    /**
     * Register built-in CRM email templates.
     */
    private void registerBuiltInTemplates() {
        // Case assignment notification
        templates.put("case-assigned", new TemplateDefinition(
                Map.of(
                        "en", "[CRM] Case Assigned: {{caseSubject}}",
                        "ar", "[CRM] تم تعيين حالة: {{caseSubject}}"
                ),
                Map.of(
                        "en", "You have been assigned to case: {{caseSubject}}\n\nPriority: {{priority}}\nStatus: {{status}}\n\nPlease review and take action.",
                        "ar", "تم تعيينك إلى الحالة: {{caseSubject}}\n\nالأولوية: {{priority}}\nالحالة: {{status}}\n\nيرجى المراجعة والاتخاذ."
                ),
                Map.of(
                        "en", "<h2>Case Assigned</h2><p>You have been assigned to case: <strong>{{caseSubject}}</strong></p><p>Priority: {{priority}}<br>Status: {{status}}</p><p>Please review and take action.</p>",
                        "ar", "<h2 dir=\"rtl\">تم تعيين حالة</h2><p dir=\"rtl\">تم تعيينك إلى الحالة: <strong>{{caseSubject}}</strong></p><p dir=\"rtl\">الأولوية: {{priority}}<br>الحالة: {{status}}</p><p dir=\"rtl\">يرجى المراجعة والاتخاذ.</p>"
                ),
                java.util.Set.of("en", "ar")
        ));

        // Case status change notification
        templates.put("case-status-changed", new TemplateDefinition(
                Map.of(
                        "en", "[CRM] Case Status Changed: {{caseSubject}}",
                        "ar", "[CRM] تم تغيير حالة: {{caseSubject}}"
                ),
                Map.of(
                        "en", "Case status has been updated:\n\nCase: {{caseSubject}}\nPrevious: {{previousStatus}}\nNew: {{newStatus}}\n\nUpdated by: {{updatedBy}}",
                        "ar", "تم تحديث حالة الحالة:\n\nالحالة: {{caseSubject}}\nالسابق: {{previousStatus}}\nالجديد: {{newStatus}}\n\nتم التحديث بواسطة: {{updatedBy}}"
                ),
                Map.of(
                        "en", "<h2>Case Status Changed</h2><p>Case: <strong>{{caseSubject}}</strong></p><p>Previous: {{previousStatus}} → New: {{newStatus}}</p><p>Updated by: {{updatedBy}}</p>",
                        "ar", "<h2 dir=\"rtl\">تم تغيير حالة</h2><p dir=\"rtl\">الحالة: <strong>{{caseSubject}}</strong></p><p dir=\"rtl\">السابق: {{previousStatus}} → الجديد: {{newStatus}}</p><p dir=\"rtl\">تم التحديث بواسطة: {{updatedBy}}</p>"
                ),
                java.util.Set.of("en", "ar")
        ));

        // Case resolution notification
        templates.put("case-resolved", new TemplateDefinition(
                Map.of(
                        "en", "[CRM] Case Resolved: {{caseSubject}}",
                        "ar", "[CRM] تم حل الحالة: {{caseSubject}}"
                ),
                Map.of(
                        "en", "Case has been resolved:\n\nCase: {{caseSubject}}\nResolution: {{resolution}}\n\nResolved by: {{resolvedBy}}",
                        "ar", "تم حل الحالة:\n\nالحالة: {{caseSubject}}\nالحل: {{resolution}}\n\nتم الحل بواسطة: {{resolvedBy}}"
                ),
                Map.of(
                        "en", "<h2>Case Resolved</h2><p>Case: <strong>{{caseSubject}}</strong></p><p>Resolution: {{resolution}}</p><p>Resolved by: {{resolvedBy}}</p>",
                        "ar", "<h2 dir=\"rtl\">تم حل الحالة</h2><p dir=\"rtl\">الحالة: <strong>{{caseSubject}}</strong></p><p dir=\"rtl\">الحل: {{resolution}}</p><p dir=\"rtl\">تم الحل بواسطة: {{resolvedBy}}</p>"
                ),
                java.util.Set.of("en", "ar")
        ));

        // Generic CRM notification
        templates.put("crm-notification", new TemplateDefinition(
                Map.of(
                        "en", "[CRM] {{title}}",
                        "ar", "[CRM] {{title}}"
                ),
                Map.of(
                        "en", "{{message}}",
                        "ar", "{{message}}"
                ),
                Map.of(
                        "en", "<h2>{{title}}</h2><p>{{message}}</p>",
                        "ar", "<h2 dir=\"rtl\">{{title}}</h2><p dir=\"rtl\">{{message}}</p>"
                ),
                java.util.Set.of("en", "ar")
        ));

        log.info("Registered {} built-in email templates", templates.size());
    }

    /**
     * Template definition with bilingual support.
     */
    public record TemplateDefinition(
            Map<String, String> subjects,
            Map<String, String> textBodies,
            Map<String, String> htmlBodies,
            java.util.Set<String> supportedLocales
    ) {
        public String subject(String locale) {
            return subjects.getOrDefault(locale, subjects.get("en"));
        }

        public String textBody(String locale) {
            return textBodies.getOrDefault(locale, textBodies.get("en"));
        }

        public String htmlBody(String locale) {
            return htmlBodies.getOrDefault(locale, htmlBodies.get("en"));
        }
    }
}
