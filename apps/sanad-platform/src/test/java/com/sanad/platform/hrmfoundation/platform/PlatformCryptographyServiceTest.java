package com.sanad.platform.hrmfoundation.platform;

import com.sanad.platform.security.crypto.BlindIndex;
import com.sanad.platform.security.crypto.EncryptedValue;
import com.sanad.platform.security.crypto.EnvironmentKeyMaterialProvider;
import com.sanad.platform.security.crypto.JcePlatformCryptographyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformCryptographyServiceTest {

    private JcePlatformCryptographyService crypto;
    private static final UUID TENANT_A = UUID.randomUUID();
    private static final UUID TENANT_B = UUID.randomUUID();
    private static final String PURPOSE = "HR_PERSON_IDENTIFIER";
    private static final String BLIND_PURPOSE = "NATIONAL_ID";
    private static final String PLAINTEXT = "1234567890";

    @BeforeEach
    void setUp() {
        // Generate test keys (32 bytes each, base64 encoded)
        String encKey = Base64.getEncoder().encodeToString(new byte[32]);
        String blindKey = Base64.getEncoder().encodeToString(new byte[32]);
        EnvironmentKeyMaterialProvider provider = new EnvironmentKeyMaterialProvider(
                "v1", encKey, "v1", blindKey);
        crypto = new JcePlatformCryptographyService(provider);
    }

    @Test
    void encryptionIsRandomizedButDecrypts() {
        EncryptedValue a = crypto.encrypt(TENANT_A, PURPOSE, PLAINTEXT);
        EncryptedValue b = crypto.encrypt(TENANT_A, PURPOSE, PLAINTEXT);
        assertThat(a.ciphertext()).isNotEqualTo(b.ciphertext());
        assertThat(crypto.decrypt(TENANT_A, PURPOSE, a)).isEqualTo(PLAINTEXT);
        assertThat(crypto.decrypt(TENANT_A, PURPOSE, b)).isEqualTo(PLAINTEXT);
    }

    @Test
    void tamperDetectionRejectsModifiedCiphertext() {
        EncryptedValue enc = crypto.encrypt(TENANT_A, PURPOSE, PLAINTEXT);
        String tampered = enc.ciphertext().substring(0, enc.ciphertext().length() - 5) + "XXXXX";
        EncryptedValue tamperedValue = new EncryptedValue(tampered, enc.keyVersion(), enc.algorithm());
        assertThatThrownBy(() -> crypto.decrypt(TENANT_A, PURPOSE, tamperedValue))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void wrongTenantCannotDecrypt() {
        EncryptedValue enc = crypto.encrypt(TENANT_A, PURPOSE, PLAINTEXT);
        assertThatThrownBy(() -> crypto.decrypt(TENANT_B, PURPOSE, enc))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void wrongPurposeCannotDecrypt() {
        EncryptedValue enc = crypto.encrypt(TENANT_A, PURPOSE, PLAINTEXT);
        assertThatThrownBy(() -> crypto.decrypt(TENANT_A, "WRONG_PURPOSE", enc))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void blindIndexIsDeterministicWithinTenant() {
        BlindIndex a1 = crypto.blindIndex(TENANT_A, BLIND_PURPOSE, PLAINTEXT);
        BlindIndex a2 = crypto.blindIndex(TENANT_A, BLIND_PURPOSE, PLAINTEXT);
        assertThat(a1.value()).isEqualTo(a2.value());
    }

    @Test
    void blindIndexDiffersAcrossTenants() {
        BlindIndex a = crypto.blindIndex(TENANT_A, BLIND_PURPOSE, PLAINTEXT);
        BlindIndex b = crypto.blindIndex(TENANT_B, BLIND_PURPOSE, PLAINTEXT);
        assertThat(a.value()).isNotEqualTo(b.value());
    }

    @Test
    void missingKeyFailsClosed() {
        EnvironmentKeyMaterialProvider emptyProvider = new EnvironmentKeyMaterialProvider(
                "v1", "", "v1", "");
        JcePlatformCryptographyService failCrypto = new JcePlatformCryptographyService(emptyProvider);
        assertThatThrownBy(() -> failCrypto.encrypt(TENANT_A, PURPOSE, PLAINTEXT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not configured");
    }
}
