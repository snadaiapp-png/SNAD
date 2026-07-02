package com.sanad.platform.crm.platform;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "sanad.crm.platform.antivirus", name = "enabled", havingValue = "true")
public class ClamAvClient {

    private static final byte[] INSTREAM = "zINSTREAM\0".getBytes(StandardCharsets.US_ASCII);

    private final CrmPlatformProperties properties;

    public ClamAvClient(CrmPlatformProperties properties) {
        this.properties = properties;
    }

    public ScanResult scan(InputStream source) {
        var antivirus = properties.getAntivirus();
        int timeoutMillis = Math.toIntExact(antivirus.getTimeout().toMillis());
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(antivirus.getHost(), antivirus.getPort()), timeoutMillis);
            socket.setSoTimeout(timeoutMillis);
            try (DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                 BufferedInputStream input = new BufferedInputStream(source)) {
                output.write(INSTREAM);
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read == 0) {
                        continue;
                    }
                    output.writeInt(read);
                    output.write(buffer, 0, read);
                }
                output.writeInt(0);
                output.flush();

                ByteArrayOutputStream response = new ByteArrayOutputStream();
                byte[] responseBuffer = new byte[2048];
                int responseRead = socket.getInputStream().read(responseBuffer);
                if (responseRead > 0) {
                    response.write(responseBuffer, 0, responseRead);
                }
                String result = response.toString(StandardCharsets.UTF_8).trim();
                if (result.endsWith("OK")) {
                    return new ScanResult(true, false, result);
                }
                if (result.contains("FOUND")) {
                    return new ScanResult(false, true, result);
                }
                return new ScanResult(false, false, result);
            }
        } catch (IOException failure) {
            throw new IllegalStateException("ClamAV scan failed closed", failure);
        }
    }

    public record ScanResult(boolean clean, boolean infected, String engineResponse) {}
}
