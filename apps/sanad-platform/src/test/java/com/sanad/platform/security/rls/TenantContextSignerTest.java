package com.sanad.platform.security.rls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TenantContextSigner} — the HMAC key custody and mac
 * minting contract of the signed tenant-context bind protocol
 * (V20261124_1/V20261124_2, G1 P0-B F-DB-07/RT-01).
 *
 * <p>The expected mac values are independently computed with Python's
 * hmac/hashlib (the same construction the certification's
 * tests/security/lib/tenant-ctx-bind.sh computes via pgcrypto.hmac) — the
 * vectors below pin the exact byte-level protocol so the Java signer, the
 * shell certification helper and the SQL-side validator agree.</p>
 */
@DisplayName("TenantContextSigner — signed tenant-context bind macs")
class TenantContextSignerTest {

    private static final String KEY = com.sanad.platform.test.TenantCtxTestSupport.FIXTURE_KEY_HEX;

    @Test
    @DisplayName("macFor matches the independently computed HMAC-SHA256 vector")
    void macMatchesIndependentVector() {
        TenantContextSigner signer = TenantContextSigner.fromHexKey(KEY);
        UUID tenant = UUID.fromString("11111111-1111-1111-1111-111111111111");
        // Vector 1: fixture key, tenant 1111..., txid "42:1", pid 1000.
        String mac1 = signer.macFor(tenant, "42:1", 1000);
        assertThat(mac1)
                .isEqualTo("42f1a5c1135b588c9292237bf05af5cc7ed27d84940d22b3af53e45248b7caf2");
        // Vector 2: same inputs, different pid → different mac.
        String mac2 = signer.macFor(tenant, "42:1", 1001);
        assertThat(mac2)
                .isEqualTo("e13ba9ea81b9ab2a56317f5c5d900b532923a20ffb33fc005296786fbb7d87aa");
        // Vector 3: different txid → different mac.
        String mac3 = signer.macFor(tenant, "43:2", 1000);
        assertThat(mac3)
                .isEqualTo("116987f3107ba0b09f8c6ea6e8783fd9ae7250af4f12e733336be749edf6291c");
    }

    @Test
    @DisplayName("disabled signer refuses mac computation (fail closed)")
    void disabledSignerRefuses() {
        TenantContextSigner signer = TenantContextSigner.disabled();
        assertThat(signer.isActive()).isFalse();
        assertThatThrownBy(() -> signer.macFor(UUID.randomUUID(), "42:1", 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("inactive");
    }

    @Test
    @DisplayName("malformed keys are rejected at construction (fail closed)")
    void malformedKeysRejected() {
        // Malformed keys fail closed at construction (IllegalStateException —
        // a configuration error must refuse to start, never degrade silently).
        assertThatThrownBy(() -> TenantContextSigner.fromHexKey("abcd"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("64 hex");
        assertThatThrownBy(() -> TenantContextSigner.fromHexKey(
                "zzzz456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("64 hex");
        // 63 chars: one byte short.
        assertThatThrownBy(() -> TenantContextSigner.fromHexKey(
                KEY.substring(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("64 hex");
        // A blank key constructs a DISABLED signer (fromHexKey then refuses it).
        assertThatThrownBy(() -> TenantContextSigner.fromHexKey("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fromHexKey");
    }

    @Test
    @DisplayName("null or blank tenant/txid inputs are refused")
    void invalidInputsRefused() {
        TenantContextSigner signer = TenantContextSigner.fromHexKey(KEY);
        assertThatThrownBy(() -> signer.macFor(null, "42:1", 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> signer.macFor(UUID.randomUUID(), "", 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
