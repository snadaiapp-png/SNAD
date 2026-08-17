package com.sanad.platform.config;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

/**
 * Custom SSLSocketFactory that overrides the SNI (Server Name Indication)
 * hostname sent during TLS handshake.
 *
 * <p>Supabase's Supavisor connection pooler requires the SNI hostname to be
 * set to the Supabase project reference (e.g., {@code hxhvfqxzigrqoxxnnzje}),
 * not the pooler hostname ({@code aws-0-eu-central-1.pooler.supabase.com}).
 *
 * <p>The PostgreSQL JDBC driver sets SNI based on the hostname in the JDBC URL,
 * which is the pooler hostname. This factory wraps the default SSLSocketFactory
 * and overrides the SNI hostname to the project reference via
 * {@link javax.net.ssl.SSLParameters#setServerNames}.
 *
 * <p>Usage in JDBC URL:
 * <pre>
 * jdbc:postgresql://aws-0-eu-central-1.pooler.supabase.com:6543/postgres
 *   ?sslmode=require
 *   &sslfactory=com.sanad.platform.config.SupabaseSniSslSocketFactory
 *   &sniHostname=hxhvfqxzigrqoxxnnzje
 * </pre>
 *
 * <p>The {@code sniHostname} parameter is read from the JDBC URL and used as
 * the SNI hostname. If not set, the factory falls back to the default behavior
 * (no SNI override).
 */
public class SupabaseSniSslSocketFactory extends SSLSocketFactory {

    private final SSLSocketFactory delegate;
    private final String sniHostname;

    /**
     * Constructor called by the PostgreSQL JDBC driver via reflection.
     * The driver passes properties from the JDBC URL as arguments.
     *
     * @param sniHostname The SNI hostname to use (typically the Supabase project ref)
     */
    public SupabaseSniSslSocketFactory(String sniHostname) {
        this.delegate = (SSLSocketFactory) SSLSocketFactory.getDefault();
        this.sniHostname = sniHostname;
    }

    /**
     * Default constructor — SNI hostname must be set via system property
     * {@code sanad.db.sni-hostname}.
     */
    public SupabaseSniSslSocketFactory() {
        this.delegate = (SSLSocketFactory) SSLSocketFactory.getDefault();
        this.sniHostname = System.getProperty("sanad.db.sni-hostname", "");
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
     * If the SNI hostname is not set, the socket is returned unchanged.
     */
    private Socket wrapWithSni(Socket socket) {
        if (!(socket instanceof SSLSocket) || sniHostname == null || sniHostname.isBlank()) {
            return socket;
        }

        SSLSocket sslSocket = (SSLSocket) socket;
        var params = sslSocket.getSSLParameters();

        // Set the SNI hostname
        var serverNames = new javax.net.ssl.SNIHostName(sniHostname);
        params.setServerNames(java.util.List.of(serverNames));

        sslSocket.setSSLParameters(params);
        return sslSocket;
    }
}
