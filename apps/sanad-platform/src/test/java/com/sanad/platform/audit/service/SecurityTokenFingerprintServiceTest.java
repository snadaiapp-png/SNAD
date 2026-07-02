package com.sanad.platform.audit.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 05A.2.9.1 §11 — Verifies the {@link SecurityTokenFingerprintService}
 * contract: SHA-256, 64 lowercase hex chars, deterministic, no raw token
 * leakage.
 *
 * <p>This is a pure unit test (no Spring context) — the service has no
 * dependencies and can be instantiated directly.</p>
 *
 * <p>Stage 05A.2.9.2 — All test fixture strings are unmistakably non-secret
 * placeholder values (not JWT-shaped, not API-key-shaped) to avoid
 * triggering gitleaks rules. The {@link SecurityTokenFingerprintService}
 * hashes ANY non-blank string — it does not require a real JWT.</p>
 *
 * <p>Acceptance criteria (§11):</p>
 * <ul>
 *   <li>Null token → null fingerprint.</li>
 *   <li>Blank token → null fingerprint.</li>
 *   <li>Fingerprint length = 64.</li>
 *   <li>Fingerprint format = {@code [0-9a-f]{64}} (lowercase hex).</li>
 *   <li>Same token → same fingerprint (deterministic).</li>
 *   <li>Different tokens → different fingerprints.</li>
 *   <li>Fingerprint does NOT contain the raw token.</li>
 *   <li>Fingerprint does NOT contain the token prefix (first 8 chars).</li>
 *   <li>Fingerprint does NOT contain the token suffix (last 8 chars).</li>
 * </ul>
 */
class SecurityTokenFingerprintServiceTest {

    private static final Pattern LOWERCASE_HEX_64 = Pattern.compile("[0-9a-f]{64}");

    // Stage 05A.2.9.2 — Unmistakably non-secret test fixture strings.
    // These are NOT real JWTs, NOT real API keys — just arbitrary strings
    // used to test the SHA-256 fingerprint behavior. The fingerprint
    // service hashes any non-blank string; it does not parse or validate
    // JWT structure.
    private static final String FIXTURE_A = "test-fixture-input-a-not-a-secret";
    private static final String FIXTURE_B = "test-fixture-input-b-not-a-secret";
    private static final String FIXTURE_C = "test-fixture-input-c-not-a-secret";
    private static final String FIXTURE_D = "test-fixture-input-d-not-a-secret";

    private final SecurityTokenFingerprintService service = new SecurityTokenFingerprintService();

    @Test
    @DisplayName("nullToken_returnsNull")
    void nullToken_returnsNull() {
        assertThat(service.fingerprint(null)).isNull();
    }

    @Test
    @DisplayName("blankToken_returnsNull")
    void blankToken_returnsNull() {
        assertThat(service.fingerprint("")).isNull();
        assertThat(service.fingerprint("   ")).isNull();
        assertThat(service.fingerprint("\t\n")).isNull();
    }

    @Test
    @DisplayName("fingerprintLength_is64")
    void fingerprintLength_is64() {
        String fp = service.fingerprint(FIXTURE_A);
        assertThat(fp).hasSize(64);
    }

    @Test
    @DisplayName("fingerprintFormat_isLowercaseHex64")
    void fingerprintFormat_isLowercaseHex64() {
        String fp = service.fingerprint(FIXTURE_A);
        assertThat(fp).matches(LOWERCASE_HEX_64);
        assertThat(fp).isEqualTo(fp.toLowerCase());
    }

    @Test
    @DisplayName("sameToken_producesSameFingerprint (deterministic)")
    void sameToken_producesSameFingerprint() {
        String fp1 = service.fingerprint(FIXTURE_B);
        String fp2 = service.fingerprint(FIXTURE_B);
        assertThat(fp1).isEqualTo(fp2);
    }

    @Test
    @DisplayName("differentTokens_produceDifferentFingerprints")
    void differentTokens_produceDifferentFingerprints() {
        String fpA = service.fingerprint(FIXTURE_A);
        String fpB = service.fingerprint(FIXTURE_B);
        assertThat(fpA).isNotEqualTo(fpB);
    }

    @Test
    @DisplayName("fingerprint_doesNotContainRawToken")
    void fingerprint_doesNotContainRawToken() {
        String fp = service.fingerprint(FIXTURE_C);
        assertThat(fp)
                .as("fingerprint must NOT contain the raw token or any substring of it")
                .doesNotContain(FIXTURE_C);
        // Also check that no significant substring of the token appears
        assertThat(fp).doesNotContain("test-fixture-input");
        assertThat(fp).doesNotContain("not-a-secret");
    }

    @Test
    @DisplayName("fingerprint_doesNotContainTokenPrefix")
    void fingerprint_doesNotContainTokenPrefix() {
        String prefix = FIXTURE_D.substring(0, 8); // "test-fix"
        String fp = service.fingerprint(FIXTURE_D);
        assertThat(fp)
                .as("fingerprint must NOT contain the token prefix (first 8 chars)")
                .doesNotContain(prefix);
    }

    @Test
    @DisplayName("fingerprint_doesNotContainTokenSuffix")
    void fingerprint_doesNotContainTokenSuffix() {
        String last8 = FIXTURE_D.substring(FIXTURE_D.length() - 8); // "-a-secret"
        String fp = service.fingerprint(FIXTURE_D);
        assertThat(fp)
                .as("fingerprint must NOT contain the token suffix (last 8 chars: %s)", last8)
                .doesNotContain(last8);
    }

    @Test
    @DisplayName("sha256_knownVector: known token produces known SHA-256")
    void sha256_knownVector() {
        // Verify the service is actually SHA-256 by comparing against a
        // pre-computed hash of a known string.
        // SHA-256("test") = 9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08
        String fp = service.fingerprint("test");
        assertThat(fp).isEqualTo("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08");
    }
}
