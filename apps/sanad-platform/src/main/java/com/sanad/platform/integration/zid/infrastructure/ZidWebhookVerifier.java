package com.sanad.platform.integration.zid.infrastructure;

import com.sanad.platform.integration.zid.config.ZidIntegrationProperties;
import com.sanad.platform.integration.zid.domain.ZidIntegrationException;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Verifies inbound Zid webhook signatures over the RAW request body.
 *
 * <p><b>Status: MECHANISM VERIFIED-BY-PATTERN, FORMAT UNVERIFIED.</b>
 * Zid's documentation confirms webhooks exist (Order / Product /
 * Customer / Abandoned Cart / Product Category event families and a
 * webhook management API) but the exact signature header name and
 * digest encoding were not retrievable from the public docs at
 * implementation time. This verifier therefore implements the
 * industry-standard HMAC-SHA256 over the raw body with a shared
 * secret, accepting the three common encodings ({@code sha256=<hex>},
 * bare hex, base64), compares in constant time, and FAILS CLOSED:</p>
 * <ul>
 *   <li>no secret configured → integration refuses events (503),</li>
 *   <li>missing signature header → 401,</li>
 *   <li>signature mismatch → 401.</li>
 * </ul>
 *
 * <p>The header name is configurable ({@code snad.zid.webhook-signature-header})
 * so confirming Zid's actual format before production is a config
 * change. Until then the endpoint must be treated as unverified
 * plumbing and the reconciliation pull remains the source of truth.
 * See docs/zid-integration.md.</p>
 */
@Component
public class ZidWebhookVerifier {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final ZidIntegrationProperties properties;

    public ZidWebhookVerifier(ZidIntegrationProperties properties) {
        this.properties = properties;
    }

    public boolean isConfigured() {
        return properties.getWebhookSecret() != null && !properties.getWebhookSecret().isBlank();
    }

    public String signatureHeaderName() {
        return properties.getWebhookSignatureHeader();
    }

    /**
     * @throws ZidIntegrationException reason INVALID_WEBHOOK_SIGNATURE
     *         when the header is absent or the digest does not match.
     */
    public void verify(byte[] rawBody, String signatureHeader) {
        if (!isConfigured()) {
            throw new ZidIntegrationException(ZidIntegrationException.Reason.NOT_CONFIGURED,
                    "Zid webhook verification is not configured");
        }
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new ZidIntegrationException(ZidIntegrationException.Reason.INVALID_WEBHOOK_SIGNATURE,
                    "Missing webhook signature header");
        }
        byte[] expected = hmacSha256(properties.getWebhookSecret(), rawBody);
        String provided = signatureHeader.trim();

        String hexExpected = HexFormat.of().formatHex(expected);
        String base64Expected = java.util.Base64.getEncoder().encodeToString(expected);

        // Strip the common "sha256=" / "v1=" style prefixes before comparing.
        String bare = provided;
        int eq = provided.indexOf('=');
        if (eq > 0 && eq <= 8 && provided.matches("^[a-zA-Z0-9]{1,8}=.*")) {
            bare = provided.substring(eq + 1);
        }
        boolean matches = MessageDigest.isEqual(
                bare.toLowerCase().getBytes(StandardCharsets.US_ASCII),
                hexExpected.getBytes(StandardCharsets.US_ASCII))
                || MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.US_ASCII),
                base64Expected.getBytes(StandardCharsets.US_ASCII));

        if (!matches) {
            throw new ZidIntegrationException(ZidIntegrationException.Reason.INVALID_WEBHOOK_SIGNATURE,
                    "Webhook signature verification failed");
        }
    }

    public static byte[] hmacSha256(String secret, byte[] payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return mac.doFinal(payload);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }
}
