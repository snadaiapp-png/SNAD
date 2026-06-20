package com.sanad.platform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * CORS configuration for the SANAD Platform API.
 *
 * <p>Reads allowed origins from {@code cors.allowed-origins} property
 * (backed by {@code CORS_ALLOWED_ORIGINS} environment variable).
 * Multiple origins are comma-separated. Only {@code /api/**} routes
 * are CORS-enabled; actuator and other paths are not.</p>
 *
 * <p>Production default: {@code https://snad-app.vercel.app}.</p>
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${cors.allowed-origins:https://snad-app.vercel.app}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);

        registry.addMapping("/api/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders(
                        "Authorization",
                        "Content-Type",
                        "Accept",
                        "X-Requested-With"
                )
                .allowCredentials(true)
                .maxAge(3600);
    }
}
