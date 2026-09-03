package com.sanad.platform.hr.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Central redaction guard (WS4 Task 4).
 *
 * <p>Before JSON serialization into audit before/after states, outbox
 * payloads or compliance event metadata, every object key is normalized
 * (lowercase, separators stripped) and matched against the sensitive-key
 * denylist — covering camelCase, SCREAMING_CASE, snake_case and nested JSON
 * structures. Sensitive keys are renamed with an explicit {@code _redacted}
 * suffix and their values MASKED with {@code [REDACTED]} (never dropped
 * silently) so audit/outbox evidence keeps its structure and forensic
 * usefulness, raw PII/secrets never reach durable storage, and the
 * database-level no_raw_secrets guards (which reject raw sensitive key
 * names outright) are satisfied. Business context (names, ids, codes,
 * statuses, timestamps) is preserved.</p>
 */
@Component
public class HrRedactionGuard {

    public static final String MASK = "[REDACTED]";

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "nationalid",
            "iqama",
            "passport",
            "passportnumber",
            "identifierciphertext",
            "identifierblindindex",
            "bankaccount",
            "bankiban",
            "iban",
            "password",
            "token",
            "jwt",
            "cookie",
            "secret",
            "clientsecret",
            "apikey",
            "encryptionkey",
            "blindindexkey",
            "databasepassword",
            "privatekey",
            "authorization",
            "accesstoken",
            "refreshtoken",
            "sessionid",
            "cardnumber",
            "cvv",
            "ssn");

    /** Returns a redacted copy; null input yields null. */
    public JsonNode redact(JsonNode input) {
        if (input == null || input.isNull()) {
            return null;
        }
        JsonNode copy = input.deepCopy();
        if (copy.isObject()) {
            maskObject((ObjectNode) copy);
        } else if (copy.isArray()) {
            maskArray((ArrayNode) copy);
        }
        return copy;
    }

    private void maskObject(ObjectNode node) {
        List<Map.Entry<String, JsonNode>> fields = new ArrayList<>();
        node.fields().forEachRemaining(fields::add);
        for (Map.Entry<String, JsonNode> field : fields) {
            String key = field.getKey();
            if (isSensitive(key)) {
                // The durable-storage guard (Task 3) forbids raw sensitive KEY
                // NAMES outright, so masking the value alone is insufficient:
                // the key is renamed with an explicit _redacted suffix and the
                // value masked — structure and provenance stay intact.
                node.remove(key);
                node.set(key + "_redacted", TextNode.valueOf(MASK));
            } else {
                maskChild(field.getValue());
            }
        }
    }

    private void maskArray(ArrayNode array) {
        for (int i = 0; i < array.size(); i++) {
            maskChild(array.get(i));
        }
    }

    private void maskChild(JsonNode child) {
        if (child.isObject()) {
            maskObject((ObjectNode) child);
        } else if (child.isArray()) {
            maskArray((ArrayNode) child);
        }
    }

    private boolean isSensitive(String key) {
        return SENSITIVE_KEYS.contains(normalize(key));
    }

    private String normalize(String key) {
        return key.toLowerCase(Locale.ROOT)
                .replace("_", "")
                .replace("-", "")
                .replace(" ", "");
    }
}
