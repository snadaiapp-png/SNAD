package com.sanad.platform.crm.pagination;

import com.sanad.platform.crm.error.CrmContractException;
import com.sanad.platform.crm.error.CrmErrorCode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * CRM API Contract — Opaque Cursor Codec.
 * <p>
 * Cursors are Base64-URL-safe encoded JSON containing:
 *   - {@code t}: a short, opaque hash of the tenant ID (NOT the tenant ID
 *     itself — never disclose the owning tenant).
 *   - {@code s}: the sort field value (e.g. an ISO-8601 timestamp).
 *   - {@code i}: the tie-breaker ID (UUID) — required for stable sorting.
 * <p>
 * Properties:
 *   - Opaque to the consumer (no document of the internal shape).
 *   - Cannot be used to identify a record directly.
 *   - Does NOT reveal the tenant ID.
 *   - Verifiable: a cursor whose {@code t} does not match the requesting
 *     tenant's hash is rejected with {@code VALIDATION_ERROR} (so Tenant B
 *     cannot use Tenant A's cursor — see AC-04).
 *   - Tied to a stable sort: the codec requires the caller to pass the sort
 *     field and direction; the encoded cursor also stores them so a
 *     subsequent page with a different sort/direction is rejected.
 * <p>
 * Branch: crm/003-stable-api-contracts
 */
@Component
public class CursorCodec {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    /**
     * Encode a cursor for the given tenant, sort field, direction, sort
     * value, and tie-breaker ID.
     */
    public String encode(UUID tenantId, String sortField, String direction, String sortValue, UUID tieBreakerId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("t", tenantHash(tenantId));
        payload.put("s", sortValue);
        payload.put("i", tieBreakerId == null ? null : tieBreakerId.toString());
        payload.put("f", sortField);
        payload.put("d", direction == null ? "desc" : direction.toLowerCase());
        String json = toJson(payload);
        return ENCODER.encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decode and validate a cursor against the requesting tenant. Throws
     * {@link CrmContractException} with {@link CrmErrorCode#VALIDATION_ERROR}
     * if the cursor is malformed, was issued for a different tenant, or
     * was issued for a different sort/direction.
     */
    public DecodedCursor decode(String cursor, UUID tenantId, String expectedSortField, String expectedDirection) {
        if (cursor == null || cursor.isBlank()) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR, "Cursor is required.");
        }
        Map<String, Object> payload;
        try {
            byte[] raw = DECODER.decode(cursor);
            payload = fromJson(new String(raw, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException ex) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR, "Cursor is malformed.");
        }
        String expectedTenantHash = tenantHash(tenantId);
        Object cursorTenantHash = payload.get("t");
        if (!expectedTenantHash.equals(cursorTenantHash)) {
            // Cross-tenant cursor reuse — reject without disclosing who
            // the cursor actually belongs to (AC-04).
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR, "Cursor is invalid for this tenant.");
        }
        String cursorSort = String.valueOf(payload.getOrDefault("f", ""));
        String cursorDir = String.valueOf(payload.getOrDefault("d", "desc"));
        String wantSort = expectedSortField == null ? "updatedAt" : expectedSortField;
        String wantDir = expectedDirection == null ? "desc" : expectedDirection.toLowerCase();
        if (!wantSort.equals(cursorSort) || !wantDir.equals(cursorDir)) {
            throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR,
                    "Cursor was issued for a different sort or direction.");
        }
        String sortValue = payload.get("s") == null ? null : String.valueOf(payload.get("s"));
        UUID tieBreakerId = null;
        Object id = payload.get("i");
        if (id != null && !"null".equals(String.valueOf(id))) {
            try {
                tieBreakerId = UUID.fromString(String.valueOf(id));
            } catch (IllegalArgumentException ex) {
                throw new CrmContractException(CrmErrorCode.VALIDATION_ERROR, "Cursor is malformed.");
            }
        }
        return new DecodedCursor(sortValue, tieBreakerId, cursorSort, cursorDir);
    }

    /**
     * Short, opaque, irreversible hash of the tenant ID. Uses FNV-1a so we
     * do not depend on a cryptographic provider; the goal is only to make
     * the tenant unidentifiable from the cursor, not to defend against an
     * attacker who already knows the tenant ID.
     */
    static String tenantHash(UUID tenantId) {
        if (tenantId == null) return "00000000";
        long h = 0xcbf29ce484222325L;
        for (byte b : tenantId.toString().getBytes(StandardCharsets.UTF_8)) {
            h ^= (b & 0xff);
            h *= 0x100000001b3L;
        }
        return Long.toHexString(h);
    }

    private static String toJson(Map<String, Object> payload) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : payload.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(escape(e.getKey())).append("\":");
            Object v = e.getValue();
            if (v == null) sb.append("null");
            else if (v instanceof Number) sb.append(v);
            else sb.append('"').append(escape(String.valueOf(v))).append('"');
        }
        return sb.append('}').toString();
    }

    private static Map<String, Object> fromJson(String json) {
        // Minimal JSON object parser — we control the input shape, so a
        // hand-rolled parser is enough and avoids a runtime JSON dependency.
        Map<String, Object> result = new LinkedHashMap<>();
        int i = skipWs(json, 0);
        if (i >= json.length() || json.charAt(i) != '{') throw new IllegalArgumentException("not an object");
        i = skipWs(json, i + 1);
        while (i < json.length()) {
            i = skipWs(json, i);
            if (i < json.length() && json.charAt(i) == '}') { i++; break; }
            if (json.charAt(i) != '"') throw new IllegalArgumentException("expected key string");
            int[] keyEnd = new int[1];
            String key = readString(json, i, keyEnd);
            i = skipWs(json, keyEnd[0]);
            if (i >= json.length() || json.charAt(i) != ':') throw new IllegalArgumentException("expected ':'");
            i = skipWs(json, i + 1);
            int[] valEnd = new int[1];
            Object value = readValue(json, i, valEnd);
            result.put(key, value);
            i = skipWs(json, valEnd[0]);
            if (i < json.length() && json.charAt(i) == ',') { i = skipWs(json, i + 1); continue; }
            if (i < json.length() && json.charAt(i) == '}') { i++; break; }
        }
        return result;
    }

    private static int skipWs(String s, int i) {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        return i;
    }

    private static String readString(String s, int i, int[] endOut) {
        StringBuilder sb = new StringBuilder();
        i++; // opening quote
        while (i < s.length()) {
            char c = s.charAt(i++);
            if (c == '\\') {
                if (i >= s.length()) break;
                char e = s.charAt(i++);
                sb.append(switch (e) {
                    case '"' -> '"';
                    case '\\' -> '\\';
                    case '/' -> '/';
                    case 'n' -> '\n';
                    case 't' -> '\t';
                    case 'r' -> '\r';
                    default -> e;
                });
            } else if (c == '"') {
                endOut[0] = i;
                return sb.toString();
            } else {
                sb.append(c);
            }
        }
        throw new IllegalArgumentException("unterminated string");
    }

    private static Object readValue(String s, int i, int[] endOut) {
        if (i >= s.length()) throw new IllegalArgumentException("eof");
        char c = s.charAt(i);
        if (c == '"') return readString(s, i, endOut);
        if (c == 'n' && s.startsWith("null", i)) { endOut[0] = i + 4; return null; }
        if (c == 't' && s.startsWith("true", i)) { endOut[0] = i + 4; return true; }
        if (c == 'f' && s.startsWith("false", i)) { endOut[0] = i + 5; return false; }
        if (c == '-' || Character.isDigit(c)) {
            int j = i;
            while (j < s.length() && "-+.0123456789eE".indexOf(s.charAt(j)) >= 0) j++;
            String num = s.substring(i, j);
            endOut[0] = j;
            try { return Long.parseLong(num); } catch (NumberFormatException ignored) { return Double.parseDouble(num); }
        }
        throw new IllegalArgumentException("unexpected value char: " + c);
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    public record DecodedCursor(String sortValue, UUID tieBreakerId, String sortField, String direction) {}
}
