package com.sanad.platform.crm.integration.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Verifies workflow callbacks using both a signed service JWT and a
 * timestamped HMAC body signature, then atomically consumes JWT JTI and nonce.
 */
@Component
public class WorkflowCallbackSecurity {

    private final ServiceJwtProvider jwtProvider;
    private final CallbackReplayStore replayStore;
    private final byte[] hmacSecret;
    private final String callbackAudience;
    private final long maxClockSkewSeconds;

    public WorkflowCallbackSecurity(
            ServiceJwtProvider jwtProvider,
            CallbackReplayStore replayStore,
            @Value("${sanad.service-auth.jwt-secret:}") String secret,
            @Value("${sanad.service-auth.callback-audience:sanad-crm}") String callbackAudience,
            @Value("${sanad.service-auth.callback-max-skew-seconds:300}") long maxClockSkewSeconds) {
        this.jwtProvider = jwtProvider;
        this.replayStore = replayStore;
        this.hmacSecret = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        this.callbackAudience = callbackAudience == null || callbackAudience.isBlank()
                ? "sanad-crm" : callbackAudience.strip();
        this.maxClockSkewSeconds = Math.max(30, Math.min(maxClockSkewSeconds, 900));
    }

    public ServiceJwtProvider.ValidatedServiceToken verify(
            String authorization,
            String signature,
            String timestampHeader,
            String nonce,
            String rawBody,
            UUID expectedTenantId,
            String expectedCorrelationId,
            String expectedContractVersion) {
        if (hmacSecret.length < 32) {
            throw new CallbackSecurityException("SERVICE_AUTH_NOT_CONFIGURED");
        }
        String token = bearerToken(authorization);
        ServiceJwtProvider.ValidatedServiceToken validated =
                jwtProvider.validate(token, callbackAudience);

        if (!validated.tenantId().equals(expectedTenantId)) {
            throw new CallbackSecurityException("CALLBACK_TENANT_MISMATCH");
        }
        if (!validated.correlationId().equals(required(expectedCorrelationId, "correlationId"))) {
            throw new CallbackSecurityException("CALLBACK_CORRELATION_MISMATCH");
        }
        if (!validated.contractVersion().equals(required(expectedContractVersion, "contractVersion"))) {
            throw new CallbackSecurityException("CALLBACK_CONTRACT_VERSION_MISMATCH");
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(required(timestampHeader, "timestamp"));
        } catch (NumberFormatException error) {
            throw new CallbackSecurityException("CALLBACK_TIMESTAMP_INVALID", error);
        }
        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - timestamp) > maxClockSkewSeconds) {
            throw new CallbackSecurityException("CALLBACK_TIMESTAMP_OUT_OF_RANGE");
        }

        String normalizedNonce = required(nonce, "nonce");
        String body = rawBody == null ? "" : rawBody;
        String bodyDigest = sha256Hex(body);
        String signingInput = timestamp + "." + normalizedNonce + "." + bodyDigest;
        String expectedSignature = hmacSha256Hex(signingInput);
        if (!constantTimeEquals(expectedSignature, required(signature, "signature"))) {
            throw new CallbackSecurityException("CALLBACK_SIGNATURE_INVALID");
        }

        boolean consumed = replayStore.consume(
                validated.tenantId(),
                validated.serviceName(),
                validated.jti(),
                normalizedNonce,
                validated.correlationId(),
                validated.expiresAt());
        if (!consumed) {
            throw new CallbackSecurityException("CALLBACK_REPLAY_DETECTED");
        }
        return validated;
    }

    public String signForTest(String timestamp, String nonce, String rawBody) {
        String digest = sha256Hex(rawBody == null ? "" : rawBody);
        return hmacSha256Hex(required(timestamp, "timestamp") + "." + required(nonce, "nonce") + "." + digest);
    }

    private String hmacSha256Hex(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacSecret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new CallbackSecurityException("CALLBACK_SIGNATURE_FAILURE", error);
        }
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new CallbackSecurityException("CALLBACK_DIGEST_FAILURE", error);
        }
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.toLowerCase().getBytes(StandardCharsets.US_ASCII),
                actual.toLowerCase().getBytes(StandardCharsets.US_ASCII));
    }

    private static String bearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new CallbackSecurityException("SERVICE_TOKEN_MISSING");
        }
        return required(authorization.substring("Bearer ".length()), "serviceToken");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new CallbackSecurityException("CALLBACK_" + name.toUpperCase() + "_MISSING");
        }
        return value.strip();
    }

    public static final class CallbackSecurityException extends RuntimeException {
        private final String code;

        public CallbackSecurityException(String code) {
            super(code);
            this.code = code;
        }

        public CallbackSecurityException(String code, Throwable cause) {
            super(code, cause);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
