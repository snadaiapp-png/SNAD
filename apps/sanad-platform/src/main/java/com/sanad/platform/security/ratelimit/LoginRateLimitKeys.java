package com.sanad.platform.security.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Builds the composite rate-limit keys used by {@link LoginRateLimiter}.
 *
 * <p>Three independent keys are produced so the limiter can enforce per-IP,
 * per-account, and combined buckets simultaneously:
 * <ul>
 *   <li>{@code ip:&lt;addr&gt;} — caps abuse from a single source against many accounts.</li>
 *   <li>{@code acct:&lt;email&gt;} — caps guesses against one account from many IPs.</li>
 *   <li>{@code acctip:&lt;email&gt;|&lt;addr&gt;} — caps the combined honest-user case.</li>
 * </ul>
 *
 * <p><b>Trusted-proxy policy:</b> the {@code X-Forwarded-For} header is ONLY
 * consulted when the immediate TCP peer ({@code request.getRemoteAddr()})
 * matches the configured {@code sanad.security.rate-limit.trusted-proxies}
 * allowlist. Without a match, the peer address itself is used. This prevents
 * arbitrary clients from spoofing their IP via a forged header (which was the
 * previous latent gap).
 */
@Component
public class LoginRateLimitKeys {

    private final Set<String> trustedProxies;

    public LoginRateLimitKeys(
            @Value("${sanad.security.rate-limit.trusted-proxies:127.0.0.1,::1}") String trustedProxiesCsv) {
        this.trustedProxies = parseProxies(trustedProxiesCsv);
    }

    /**
     * Returns the three keys that should be checked/incremented together for a
     * single login attempt. Order is deterministic: ip, account, account+ip.
     */
    public String[] keysFor(String normalizedEmail, HttpServletRequest request) {
        String clientIp = extractClientIp(request);
        return new String[] {
                "ip:" + clientIp,
                "acct:" + normalizedEmail,
                "acctip:" + normalizedEmail + "|" + clientIp
        };
    }

    /** Visible for tests. */
    public String extractClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String remote = request.getRemoteAddr();
        if (isTrusted(remote)) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                // Left-most entry is the original client. Trim and validate.
                String first = forwarded.split(",")[0].trim();
                if (isValidIp(first)) {
                    return first;
                }
            }
        }
        return remote == null ? "unknown" : remote;
    }

    private boolean isTrusted(String addr) {
        if (addr == null) {
            return false;
        }
        return trustedProxies.contains(addr);
    }

    private static boolean isValidIp(String value) {
        if (value == null || value.isBlank() || value.length() > 45) {
            return false;
        }
        try {
            InetAddress.getByName(value);
            return true;
        } catch (UnknownHostException exception) {
            return false;
        }
    }

    private static Set<String> parseProxies(String csv) {
        Set<String> result = new LinkedHashSet<>();
        if (csv == null || csv.isBlank()) {
            return result;
        }
        Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(result::add);
        return result;
    }
}
