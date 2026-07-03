package com.sanad.platform.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.validation.annotation.Validated;

/**
 * Validates that mandatory database environment variables are present
 * when the application runs with the {@code prod} profile.
 *
 * <p>Uses {@code @ConfigurationProperties} with {@code @Validated} so
 * Spring Boot fails startup with a clear message if any required
 * property is blank. Credential values are never logged.</p>
 */
@Configuration
@Profile("prod")
@ConfigurationProperties(prefix = "sanad.database")
@Validated
public class ProductionDatabaseProperties {

    @NotBlank(message = "DATABASE_URL must be set in production")
    private String url;

    @NotBlank(message = "DATABASE_USERNAME must be set in production")
    private String username;

    @NotBlank(message = "DATABASE_PASSWORD must be set in production")
    private String password;

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
