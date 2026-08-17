package com.sanad.platform.config;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.cert.X509Certificate;
import java.util.List;
import java.security.SecureRandom;

/**
 * Custom SSLSocketFactory that overrides the SNI (Server Name Indication)
 * hostname sent during TLS handshake AND uses a permissive TrustManager
 * matching {@code sslmode=require} semantics (encrypt, but do not verify
 * the server certificate chain).
 *
 * <p>Supabase's Supavisor connection pooler requires the SNI hostname to be
 * set to the Supabase project reference (or a hostname containing it) so
 * the pooler can route the connection to the correct tenant. The default
 * pgJDBC SNI is the URL host (the pooler hostname), which Supavisor does
 * not recognize — yielding the FATAL {@code (ENOIDENTIFIER) no tenant
 * identifier provided} error.
 *
 * <p>Why permissive TrustManager: pgJDBC's built-in {@code sslmode=require}
 * handler uses a non-validating TrustManager. When the user provides a custom
 * {@code sslfactory=}, pgJDBC delegates ALL SSL handling to that factory —
 * including TrustManager selection. Using {@link SSLSocketFactory#getDefault()}
 * would inherit Java's strict default TrustManager (validates against the
 * JVM {@code cacerts} truststore), which rejects the cert Supavisor returns
 * for the project-ref SNI, producing PKIX path building failures.
 *
 * <p>Usage in JDBC URL:
 * <pre>
 * jdbc:postgresql://aws-0-eu-central-1.pooler.supabase.com:5432/postgres
 *   ?sslmode=require
 *   &sslfactory=com.sanad.platform.config.SupabaseSniSslSocketFactory
 *   &sslfactoryarg=hxhvfqxzigrqoxxnnzje
 * </pre>
 *
 * <p>The {@code sslfactoryarg} value is passed by pgJDBC to the
 * {@link #SupabaseSniSslSocketFactory(String)} constructor. It should be
 * the Supabase project reference (e.g., {@code hxhvfqxzigrqoxxnnzje}).
 *
 * <p>If not set (zero-arg constructor), the SNI hostname is read from the
 * system property {@code sanad.db.sni-hostname}. If neither is set, the
 * factory is a no-op (no SNI override, but the permissive TrustManager is
 * still active — equivalent to standard {@code sslmode=require}).
 */
public class SupabaseSniSslSocketFactory extends SSLSocketFactory {

    private final SSLSocketFactory delegate;
    private final String sniHostname;

    /**
     * Constructor called by pgJDBC via reflection when {@code sslfactoryarg=} is set.
     *
     * @param sniHostname The SNI hostname to use (typically the Supabase project ref
     *                    or a hostname containing it, e.g.
     *                    {@code hxhvfqxzigrqoxxnnzje} or
     *                    {@code db.hxhvfqxzigrqoxxnnzje.supabase.co}).
     */
    public SupabaseSniSslSocketFactory(String sniHostname) {
        this.delegate = buildPermissiveFactory();
        this.sniHostname = sniHostname;
    }

    /**
     * Default constructor — SNI hostname must be set via system property
     * {@code sanad.db.sni-hostname}. If absent, no SNI override is applied
     * (but the permissive TrustManager is still active).
     */
    public SupabaseSniSslSocketFactory() {
        this.delegate = buildPermissiveFactory();
        this.sniHostname = System.getProperty("sanad.db.sni-hostname", "");
    }

    /**
     * Build an SSLSocketFactory backed by an SSLContext with a permissive
     * TrustManager (trusts all server certificate chains). This matches
     * pgJDBC's {@code sslmode=require} semantics: encrypt the connection
     * but do not verify the certificate chain (the chain may be issued
     * by a CA not in the JVM {@code cacerts} truststore, which is the
     * case for Supavisor's project-ref-routed TLS endpoints).
     */
    private static SSLSocketFactory buildPermissiveFactory() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[] { new PermissiveTrustManager() }, new SecureRandom());
            return sslContext.getSocketFactory();
        } catch (Exception ex) {
            // Fallback: use the JVM default (may fail PKIX, but at least we don't crash)
            return (SSLSocketFactory) SSLSocketFactory.getDefault();
        }
    }

    @Override
    public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
        Socket underlying = delegate.createSocket(socket, host, port, autoClose);
        return wrapWithSni(underlying);
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        Socket socket = delegate.createSocket(host, port);
        return wrapWithSni(socket);
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
        Socket socket = delegate.createSocket(host, port, localHost, localPort);
        return wrapWithSni(socket);
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        Socket socket = delegate.createSocket(host, port);
        return wrapWithSni(socket);
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
        Socket socket = delegate.createSocket(address, port, localAddress, localPort);
        return wrapWithSni(socket);
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return delegate.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return delegate.getSupportedCipherSuites();
    }

    /**
     * Wraps an SSLSocket to override the SNI hostname.
     *
     * <p>If the socket is not an SSLSocket, it is returned unchanged.
     * If the SNI hostname is null or blank, the socket is returned unchanged
     * (the permissive TrustManager remains in effect regardless).
     */
    private Socket wrapWithSni(Socket socket) {
        if (!(socket instanceof SSLSocket) || sniHostname == null || sniHostname.isBlank()) {
            return socket;
        }

        SSLSocket sslSocket = (SSLSocket) socket;
        var params = sslSocket.getSSLParameters();
        params.setServerNames(List.<SNIServerName>of(new SNIHostName(sniHostname)));
        sslSocket.setSSLParameters(params);
        return sslSocket;
    }

    /**
     * Permissive TrustManager — accepts any server certificate chain.
     * This is the recommended TrustManager for pgJDBC's {@code sslmode=require}
     * (encrypt but do not verify) when a custom {@code sslfactory=} is used.
     */
    public static final class PermissiveTrustManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
            // No client cert verification
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
            // Accept any server cert — caller asked for sslmode=require, not verify
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
