package com.sanad.platform.crm.caller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanad.platform.crm.party.domain.PhoneNumberNormalizer;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NORMALIZATION_PARITY gate (G8-03 §69): the backend shares ONE authority and
 * the same test vectors as the mobile normalizer
 * ({@code docs/crm/g8/caller-phone-normalization-vectors.json}). Any change
 * to normalization rules must update the vectors and BOTH consumers.
 */
public final class CallerPhoneVectorsParityTest {

    private static final Path VECTORS = Path.of(System.getProperty("user.dir")).getParent().getParent()
            .resolve("docs/crm/g8/caller-phone-normalization-vectors.json");

    @Test
    void backendMatchesEverySharedNormalizationVector() throws Exception {
        JsonNode doc = new ObjectMapper().readTree(Files.readAllBytes(VECTORS));
        JsonNode vectors = doc.path("normalization");
        assertThat(vectors.isArray()).isTrue();
        for (JsonNode vector : vectors) {
            String input = vector.path("input").isNull() ? null : vector.path("input").asText();
            String hint = vector.path("countryHint").isNull() ? null : vector.path("countryHint").asText();
            String expected = vector.path("expected").isNull() ? null : vector.path("expected").asText();
            assertThat(PhoneNumberNormalizer.normalizePhone(input, hint))
                    .as("vector input=%s hint=%s", input, hint)
                    .isEqualTo(expected);
        }
    }

    @Test
    void backendHmacDerivationMatchesTheSharedTokenVector() throws Exception {
        JsonNode vector = new ObjectMapper().readTree(Files.readAllBytes(VECTORS)).path("tokenVector");
        String masterKey = vector.path("masterKey").asText();
        String tenantId = vector.path("tenantId").asText();
        String message = vector.path("message").asText();

        String tenantDatasetKey = hmacSha256Hex(masterKey, tenantId);
        String lookupToken = hmacSha256Hex(tenantDatasetKey, message);

        assertThat(tenantDatasetKey).isEqualTo(vector.path("tenantDatasetKey").asText());
        assertThat(lookupToken).isEqualTo(vector.path("lookupToken").asText());
    }

    public static String hmacSha256Hex(String key, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
