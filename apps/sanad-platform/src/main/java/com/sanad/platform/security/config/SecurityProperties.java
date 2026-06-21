package com.sanad.platform.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * JWT and refresh token configuration.
 *
 * <p>Binds to {@code sanad.security.*} properties. In production,
 * {@code sanad.security.jwt.secret} must be set via the {@code JWT_SECRET}
 * environment variable. The {@link JwtTokenProvider} validates the secret
 * at startup and logs a warning if it's too short.</p>
 */
@ConfigurationProperties(prefix = "sanad.security")
@Validated
public class SecurityProperties {

    private Jwt jwt = new Jwt();
    private Refresh refresh = new Refresh();
    private LoginRateLimit loginRateLimit = new LoginRateLimit();
    private Cookie cookie = new Cookie();

    public Jwt getJwt() { return jwt; }
    public void setJwt(Jwt jwt) { this.jwt = jwt; }

    public Refresh getRefresh() { return refresh; }
    public void setRefresh(Refresh refresh) { this.refresh = refresh; }

    public LoginRateLimit getLoginRateLimit() { return loginRateLimit; }
    public void setLoginRateLimit(LoginRateLimit loginRateLimit) { this.loginRateLimit = loginRateLimit; }

    public Cookie getCookie() { return cookie; }
    public void setCookie(Cookie cookie) { this.cookie = cookie; }

    /** Access JWT configuration. */
    public static class Jwt {
        /** Secret key for signing JWTs. Must be set in production via JWT_SECRET env var. */
        private String secret = "";

        /** Access token time-to-live. Default: 15 minutes. */
        private Duration accessTokenTtl = Duration.ofMinutes(15);

        /** JWT issuer claim. */
        private String issuer = "sanad-platform";

        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }

        public Duration getAccessTokenTtl() { return accessTokenTtl; }
        public void setAccessTokenTtl(Duration accessTokenTtl) { this.accessTokenTtl = accessTokenTtl; }

        public String getIssuer() { return issuer; }
        public void setIssuer(String issuer) { this.issuer = issuer; }
    }

    /** Refresh token configuration. */
    public static class Refresh {
        /** Refresh token time-to-live. Default: 7 days. */
        private Duration refreshTokenTtl = Duration.ofDays(7);

        public Duration getRefreshTokenTtl() { return refreshTokenTtl; }
        public void setRefreshTokenTtl(Duration refreshTokenTtl) { this.refreshTokenTtl = refreshTokenTtl; }
    }

    /** Login brute-force protection configuration. */
    public static class LoginRateLimit {
        /** Max failed login attempts before rate limiting kicks in. */
        private int maxAttempts = 5;

        /** Sliding window for failed attempts. Default: 5 minutes. */
        private Duration window = Duration.ofMinutes(5);

        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }

        public Duration getWindow() { return window; }
        public void setWindow(Duration window) { this.window = window; }
    }

    /** Cookie configuration for refresh token (BFF pattern). */
    public static class Cookie {
        /** Whether to set the Secure flag on the cookie. Default: true. */
        private boolean secure = true;

        /** SameSite attribute. Default: lax. */
        private String sameSite = "lax";

        /** Domain for the cookie. Empty = host-only. */
        private String domain = "";

        public boolean isSecure() { return secure; }
        public void setSecure(boolean secure) { this.secure = secure; }

        public String getSameSite() { return sameSite; }
        public void setSameSite(String sameSite) { this.sameSite = sameSite; }

        public String getDomain() { return domain; }
        public void setDomain(String domain) { this.domain = domain; }
    }
}
