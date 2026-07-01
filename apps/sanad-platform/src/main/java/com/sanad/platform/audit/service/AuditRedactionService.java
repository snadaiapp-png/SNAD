package com.sanad.platform.audit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Stage 05 §10 — Centralized sensitive-data redaction for audit payloads.
 *
 * <p>Before any JSON state (before/after/metadata) is persisted to
 * {@code audit_events}, it passes through this service. The service
 * recursively walks the JSON tree and replaces the value of any field
 * whose name matches a sensitive-field pattern with the literal
 * {@code "[REDACTED]"}.</p>
 *
 * <h2>Sensitive fields (always redacted)</h2>
 * <ul>
 *   <li>password, passwordHash, currentPassword, newPassword</li>
 *   <li>token, accessToken, refreshToken, authorization</li>
 *   <li>cookie, secret, clientSecret, apiKey, privateKey</li>
 *   <li>credential, otp, verificationCode, resetCode</li>
 * </ul>
 *
 * <p>The match is case-insensitive and uses substring matching (so
 * {@code userPassword} and {@code password_confirmation} are both
 * caught). This is intentionally broad — false positives (redacting
 * a field that happens to contain "secret" in its name but holds no
 * secret) are acceptable; false negatives (leaking a real secret)
 * are not.</p>
 *
 * <h2>Never recorded</h2>
 * <p>The following are NEVER written to audit_events, even as
 * {@code [REDACTED]}:</p>
 * <ul>
 *   <li>Raw JWT</li>
 *   <li>Raw Authorization header</li>
 *   <li>Password hashes</li>
 *   <li>Refresh-token hashes</li>
 *   <li>Database credentials</li>
 *   <li>Full session cookies</li>
 * </ul>
 *
 * <p>Callers must NOT pass these to {@link #redactJson(String)} —
 * they should be omitted entirely from the state payload.</p>
 */
@Service
public class AuditRedactionService {

    private static final Logger log = LoggerFactory.getLogger(AuditRedactionService.class);
    private static final String REDACTED = "[REDACTED]";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Sensitive field-name patterns (case-insensitive substring match).
     * Order does not matter; the regex uses alternation.
     */
    private static final Set<String> SENSITIVE_SUBSTRINGS = Set.of(
            "password", "passwordhash", "currentpassword", "newpassword",
            "token", "accesstoken", "refreshtoken",
            "authorization", "cookie",
            "secret", "clientsecret",
            "apikey", "privatekey",
            "credential",
            "otp", "verificationcode", "resetcode"
    );

    private static final Pattern SENSITIVE_PATTERN = buildPattern();

    private static Pattern buildPattern() {
        // Build a case-insensitive regex that matches if any sensitive
        // substring appears in the field name.
        String joined = String.join("|", SENSITIVE_SUBSTRINGS);
        return Pattern.compile("(?i).*(" + joined + ").*");
    }

    /**
     * Returns true if the given field name should be redacted.
     * Package-private for testing.
     */
    boolean isSensitive(String fieldName) {
        if (fieldName == null || fieldName.isBlank()) {
            return false;
        }
        return SENSITIVE_PATTERN.matcher(fieldName).matches();
    }

    /**
     * Recursively redacts sensitive fields in a JSON string.
     *
     * <p>If the input is not valid JSON, returns the string
     * {@code "[REDACTED:unparseable]"} — we never persist raw
     * untrusted text that might contain secrets.</p>
     *
     * @param json the raw JSON string (may be null)
     * @return the redacted JSON string, or null if input was null
     */
    public String redactJson(String json) {
        if (json == null || json.isBlank()) {
            return json;
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode redacted = redactNode(root);
            return MAPPER.writeValueAsString(redacted);
        } catch (Exception e) {
            log.warn("Audit redaction: unparseable JSON, returning sentinel — {}", e.getClass().getSimpleName());
            return "[REDACTED:unparseable]";
        }
    }

    /**
     * Redacts a single string value if it looks like a credential.
     * Used for top-level string fields that are not part of a JSON
     * tree (e.g. a header value captured in metadata).
     */
    public String redactStringValue(String value) {
        if (value == null) {
            return null;
        }
        return REDACTED;
    }

    String getRedactedSentinel() {
        return REDACTED;
    }

    private JsonNode redactNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return node;
        }
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            ObjectNode copy = obj.objectNode();
            var fields = obj.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                String name = entry.getKey();
                JsonNode value = entry.getValue();
                if (isSensitive(name)) {
                    copy.set(name, TextNode.valueOf(REDACTED));
                } else {
                    copy.set(name, redactNode(value));
                }
            }
            return copy;
        }
        if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            ArrayNode copy = arr.arrayNode();
            for (JsonNode element : arr) {
                copy.add(redactNode(element));
            }
            return copy;
        }
        // Scalar — return as-is.
        return node;
    }
}
