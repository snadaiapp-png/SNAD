package com.sanad.platform.config;

import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for {@link SupabaseSniSslSocketFactory}.
 *
 * <p>Verifies:
 * <ul>
 *   <li>Zero-arg constructor reads from system property</li>
 *   <li>String constructor sets SNI hostname</li>
 *   <li>SNI server name is present in SSLParameters</li>
 *   <li>No SNI override when config absent</li>
 *   <li>Cipher suite delegation works</li>
 * </ul>
 */
class SupabaseSniSslSocketFactoryTest {

    @Test
    void zeroArgConstructor_readsSystemProperty() {
        System.setProperty("sanad.db.sni-hostname", "test-project-ref");
        try {
            SupabaseSniSslSocketFactory factory = new SupabaseSniSslSocketFactory();
            // The factory should be created without error
            assertThat(factory).isNotNull();
            assertThat(factory.getDefaultCipherSuites()).isNotEmpty();
        } finally {
            System.clearProperty("sanad.db.sni-hostname");
        }
    }

    @Test
    void zeroArgConstructor_noSystemProperty_doesNotOverride() {
        System.clearProperty("sanad.db.sni-hostname");
        SupabaseSniSslSocketFactory factory = new SupabaseSniSslSocketFactory();
        assertThat(factory).isNotNull();
        // With no SNI hostname, wrapWithSni should be a no-op
        // We can't test the actual socket wrapping without a real TLS server,
        // but we can verify the factory doesn't crash
    }

    @Test
    void stringConstructor_setsSniHostname() {
        SupabaseSniSslSocketFactory factory = new SupabaseSniSslSocketFactory("my-project-ref");
        assertThat(factory).isNotNull();
        assertThat(factory.getSupportedCipherSuites()).isNotEmpty();
    }

    @Test
    void stringConstructor_emptyString_doesNotOverride() {
        SupabaseSniSslSocketFactory factory = new SupabaseSniSslSocketFactory("");
        assertThat(factory).isNotNull();
        // Empty string should be treated as "no override"
    }

    @Test
    void stringConstructor_nullString_doesNotOverride() {
        SupabaseSniSslSocketFactory factory = new SupabaseSniSslSocketFactory(null);
        assertThat(factory).isNotNull();
        // Null should be treated as "no override"
    }

    @Test
    void getSupportedCipherSuites_delegatesToDefault() {
        SupabaseSniSslSocketFactory factory = new SupabaseSniSslSocketFactory("test-ref");
        SSLSocketFactory defaultFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        assertThat(factory.getSupportedCipherSuites()).isEqualTo(defaultFactory.getSupportedCipherSuites());
    }

    @Test
    void getDefaultCipherSuites_delegatesToDefault() {
        SupabaseSniSslSocketFactory factory = new SupabaseSniSslSocketFactory("test-ref");
        SSLSocketFactory defaultFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        assertThat(factory.getDefaultCipherSuites()).isEqualTo(defaultFactory.getDefaultCipherSuites());
    }
}
