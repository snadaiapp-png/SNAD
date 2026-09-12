package com.sanad.platform.workflow.notification;

/**
 * R2 notification channels (GATE R2.3/R2.4). IN_APP remains the primary
 * qualifying channel; EMAIL/PUSH/WHATSAPP/WEBHOOK are governed external
 * channels. Parsing fails closed on unknown values.
 */
public enum WorkflowChannel {
    IN_APP,
    EMAIL,
    PUSH,
    WHATSAPP,
    WEBHOOK;

    /**
     * Fail-closed parse: null/blank maps to IN_APP (the primary qualifying
     * channel convention carried from Y2); any unknown value is rejected —
     * never silently coerced.
     */
    public static WorkflowChannel parse(String value) {
        if (value == null || value.isBlank()) {
            return IN_APP;
        }
        return switch (value.trim().toUpperCase()) {
            case "IN_APP" -> IN_APP;
            case "EMAIL" -> EMAIL;
            case "PUSH" -> PUSH;
            case "WHATSAPP" -> WHATSAPP;
            case "WEBHOOK" -> WEBHOOK;
            default -> throw new IllegalArgumentException(
                    "Unknown notification channel (fail-closed): " + value);
        };
    }
}
