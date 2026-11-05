package com.sanad.platform.integration.zid;

import com.sanad.platform.integration.zid.config.ZidIntegrationProperties;
import com.sanad.platform.integration.zid.domain.ZidIntegrationException;
import com.sanad.platform.integration.zid.infrastructure.ZidWebhookVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the fail-closed Zid webhook signature verifier.
 * Uses an ephemeral runtime secret — never a real credential.
 */
class ZidWebhookVerifierTest {

    private static final String SECRET = "test-secret-" + java.util.UUID.randomUUID();

    private ZidWebhookVerifier verifier;
    private ZidIntegrationProperties properties;

    @BeforeEach
    void setUp() {
        properties = new ZidIntegrationProperties();
        properties.setWebhookSecret(SECRET);
        verifier = new ZidWebhookVerifier(properties);
    }

    private static byte[] hmac(String secret, byte[] payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(payload);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void acceptsHexSignature() {
        byte[] body = "{\"event\":\"order.created\"}".getBytes(StandardCharsets.UTF_8);
        String signature = HexFormat.of().formatHex(hmac(SECRET, body));
        assertThatCode(() -> verifier.verify(body, signature)).doesNotThrowAnyException();
    }

    @Test
    void acceptsPrefixedSha256Signature() {
        byte[] body = "{\"event\":\"product.updated\"}".getBytes(StandardCharsets.UTF_8);
        String signature = "sha256=" + HexFormat.of().formatHex(hmac(SECRET, body));
        assertThatCode(() -> verifier.verify(body, signature)).doesNotThrowAnyException();
    }

    @Test
    void acceptsBase64Signature() {
        byte[] body = "{\"event\":\"customer.created\"}".getBytes(StandardCharsets.UTF_8);
        String signature = java.util.Base64.getEncoder().encodeToString(hmac(SECRET, body));
        assertThatCode(() -> verifier.verify(body, signature)).doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingHeader() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> verifier.verify(body, null))
                .isInstanceOf(ZidIntegrationException.class)
                .satisfies(e -> assertThat(((ZidIntegrationException) e).reason())
                        .isEqualTo(ZidIntegrationException.Reason.INVALID_WEBHOOK_SIGNATURE));
    }

    @Test
    void rejectsTamperedPayload() {
        byte[] body = "{\"event\":\"order.created\"}".getBytes(StandardCharsets.UTF_8);
        byte[] tampered = "{\"event\":\"order.deleted\",\"amount\":999999}".getBytes(StandardCharsets.UTF_8);
        String signature = HexFormat.of().formatHex(hmac(SECRET, body));
        assertThatThrownBy(() -> verifier.verify(tampered, signature))
                .isInstanceOf(ZidIntegrationException.class);
    }

    @Test
    void rejectsWrongSecret() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        String signature = HexFormat.of().formatHex(hmac("attacker-secret", body));
        assertThatThrownBy(() -> verifier.verify(body, signature))
                .isInstanceOf(ZidIntegrationException.class)
                .satisfies(e -> assertThat(((ZidIntegrationException) e).reason())
                        .isEqualTo(ZidIntegrationException.Reason.INVALID_WEBHOOK_SIGNATURE));
    }

    @Test
    void failsClosedWhenNotConfigured() {
        properties.setWebhookSecret("");
        assertThatThrownBy(() -> verifier.verify("{}".getBytes(StandardCharsets.UTF_8), "sha256=abc"))
                .isInstanceOf(ZidIntegrationException.class)
                .satisfies(e -> assertThat(((ZidIntegrationException) e).reason())
                        .isEqualTo(ZidIntegrationException.Reason.NOT_CONFIGURED));
        assertThat(verifier.isConfigured()).isFalse();
    }
}
